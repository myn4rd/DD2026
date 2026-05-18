# Ameribank — Flujo de Ataque DDoS y Tipo de Vector

> Descripción del ataque que se ejecuta durante la demo del DemoDay Cybersecurity Amerike 2025. Cubre el tipo de DDoS empleado, las herramientas, el paso a paso del flujo (ataque → detección → failover → bloqueo), y cómo cada capa de defensa reacciona.
>
> Documentos relacionados:
> - `INFRAESTRUCTURA.md` — arquitectura física y de red
> - `HARDENING.md` — configuración y defensas aplicadas
> - `VULNERABILIDADES.md` — catálogo de vulnerabilidades

---

## Tabla de Contenidos

- [Tipo de DDoS empleado](#tipo-de-ddos-empleado)
- [Clasificación por capa](#clasificación-por-capa)
- [Vector específico: HTTP Flood](#vector-específico-http-flood)
- [Herramientas y comandos del atacante](#herramientas-y-comandos-del-atacante)
- [Flujo de ataque paso a paso](#flujo-de-ataque-paso-a-paso)
- [Reacción de cada capa de defensa](#reacción-de-cada-capa-de-defensa)
- [Indicadores observables (IoC)](#indicadores-observables-ioc)
- [Por qué funciona la mitigación](#por-qué-funciona-la-mitigación)
- [Limitaciones del escenario](#limitaciones-del-escenario)

---

## Tipo de DDoS empleado

El ataque demostrado es un **HTTP Flood (Denial of Service de capa de aplicación)**, también conocido como **Layer 7 DDoS**.

### Definiciones rápidas

- **DoS (Denial of Service):** ataque que busca interrumpir la disponibilidad de un servicio para sus usuarios legítimos.
- **DDoS (Distributed Denial of Service):** variante donde el ataque proviene de múltiples orígenes coordinados, dificultando el filtrado por IP.
- **HTTP Flood:** subtipo de DoS que envía un volumen alto de peticiones HTTP válidas y completas (no malformadas) hacia el servidor objetivo, agotando recursos de cómputo, threads del servidor de aplicaciones, conexiones a base de datos y memoria.

### Por qué se eligió HTTP Flood para la demo

| Razón | Justificación |
|-------|---------------|
| **Realismo** | El 70% de los ataques DDoS modernos contra aplicaciones financieras son de capa 7, según reportes anuales de Cloudflare y Akamai. |
| **Demuestra valor de las capas** | Permite mostrar que un firewall o IDS de red tradicional NO basta, y que se requiere defensa en proxy + aplicación. |
| **Reproducible en entorno controlado** | Una sola máquina atacante puede generar el volumen suficiente; no requiere botnet. |
| **Detectable y bloqueable** | Las reglas de Snort y los timeouts de Nginx pueden razonablemente filtrarlo, lo que da una narrativa de "defensa exitosa". |
| **Tráfico visualmente claro en Splunk** | Genera spikes evidentes en gráficas de requests/segundo, fáciles de presentar al jurado. |

---

## Clasificación por capa

Para contexto, así se ubica el ataque dentro de las taxonomías estándar de DDoS:

| Capa OSI | Ejemplo de ataque | ¿Es el de la demo? | Bloqueo típico |
|----------|-------------------|--------------------|----------------|
| 3 — Red | ICMP flood, IP fragmentation | No | Firewall, BGP blackhole |
| 4 — Transporte | SYN flood, UDP flood, TCP reset | No (Snort tiene regla preventiva) | IPS, SYN cookies |
| 7 — Aplicación | **HTTP flood**, Slowloris, RUDY, cache busting | **Sí** | WAF, rate limiting, IPS con inspección DPI |

Adicionalmente, las reglas de Snort cubren protección contra **slowloris** (capa 7 lento) y **SYN flood** (capa 4), aunque no son los vectores de la demo, sí son defensas activas durante el evento.

---

## Vector específico: HTTP Flood

### Características del tráfico atacante

- **Volumen:** cientos a miles de peticiones por segundo desde un solo origen.
- **Forma:** peticiones HTTP/1.1 válidas (`GET /` o `POST /login`).
- **Completitud:** cada request se completa correctamente (headers `\r\n\r\n`, body si aplica).
- **Diferencia con tráfico legítimo:** la **frecuencia** y la **concentración temporal** son anómalas para un solo cliente.

### Por qué el aplicativo sin hardening es vulnerable

El backend Spring Boot original (antes del hardening descrito en `HARDENING.md`) tenía estas debilidades amplificadoras:

1. **Sin pool de conexiones (V-004):** cada request abría una conexión TCP nueva a MySQL.
2. **Sin timeouts en Tomcat (V-005):** una conexión podía mantenerse abierta indefinidamente.
3. **Stored procedures pesados:** `login` valida credenciales contra `usuarios`, requiere I/O de disco.
4. **`event_scheduler` cada 5s (V-012):** compite por locks con los logins simultáneos.

Bajo estas condiciones, **500 peticiones concurrentes por segundo** son suficientes para:
- Agotar los 200 hilos worker de Tomcat.
- Llenar `max_connections=151` de MySQL.
- Saturar el CPU del LXC del aplicativo.
- Causar timeouts en cascada.

---

## Herramientas y comandos del atacante

Para la demo se recomienda **`wrk`** por su capacidad de generar carga sostenida desde una sola máquina con control fino de threads, conexiones y duración.

### Comando principal — HTTP GET flood

```bash
wrk -t12 -c500 -d60s --latency http://<ip-nginx>/
```

| Flag | Valor | Significado |
|------|-------|-------------|
| `-t12` | 12 | Hilos del atacante |
| `-c500` | 500 | Conexiones concurrentes |
| `-d60s` | 60 segundos | Duración del ataque |
| `--latency` | — | Reporta distribución de latencia |

**Volumen esperado:** ~3,000-5,000 req/s desde una máquina moderna.

### Comando alternativo — Apache Bench (más simple, menos volumen)

```bash
ab -n 100000 -c 500 http://<ip-nginx>/
```

- `-n 100000`: número total de peticiones.
- `-c 500`: nivel de concurrencia.
- Útil si `wrk` no está disponible. Genera menos carga pero es más portable.

### Comando opcional — POST flood al login (más costoso)

```bash
wrk -t8 -c300 -d60s \
    -s post_login.lua \
    http://<ip-nginx>/login
```

Donde `post_login.lua` contiene:

```lua
wrk.method = "POST"
wrk.body   = "usr=admin&pwd=wrong"
wrk.headers["Content-Type"] = "application/x-www-form-urlencoded"
```

Este vector es más dañino porque cada request dispara el stored procedure `login` y un INSERT en `blacklisted_totps`.

---

## Flujo de ataque paso a paso

```
   ATACANTE                NGINX               PRINCIPAL          SNORT             BACKUP            MYSQL
   (wrk x500)               │                    │                  │                  │                │
       │                    │                    │                  │                  │                │
   t=0 │ ─── GET / ──────► │                    │                  │                  │                │
       │   (×500/s)         │                    │                  │                  │                │
       │                    │ ─── proxy_pass ──►│                  │                  │                │
       │                    │                    │ ── getConn ─────────────────────────────────────► │
       │                    │                    │ ◄── conn ────────────────────────────────────────│
       │                    │                    │  saturando pool                                    │
       │                    │                    │                                                    │
   t=2 │                    │                    │ ── 502/timeout                                     │
       │                    │ ◄── error/timeout ─┤                                                    │
       │                    │ retry #1 → error                                                        │
       │                    │ retry #2 → error                                                        │
       │                    │                                                                         │
   t=5 │                    │ marca principal=DOWN                                                    │
       │                    │ (max_fails=2, fail_timeout=5s)                                          │
       │                    │                                                                         │
       │                    │ ─── proxy_pass (al backup) ─────►│                                     │
       │                    │                                   │ inspección DPI                      │
       │                    │                                   │                                     │
   t=7 │                    │                                   │ cuenta GETs/IP                      │
       │                    │                                   │ supera threshold 30/5s              │
       │                    │                                   │                                     │
   t=8 │                    │                                   │ ── DROP ──► (paquete descartado)    │
       │                    │ ◄── timeout (nada llega)                                                │
       │                    │                                                                         │
   t=10+                    │ tráfico legítimo (mínimo)                                               │
       │ (cliente normal) ► │ ─── proxy_pass ──────────────────►│ ─── permitido (bajo threshold) ─►│ ── OK ─►│
       │                    │ ◄── 200 OK                        │                                     │
```

### Cronología detallada

| t (s) | Evento | Componente activo | Estado del sistema |
|------:|--------|-------------------|---------------------|
| **0** | El atacante ejecuta `wrk -t12 -c500 -d60s http://nginx/` | Atacante | El ataque comienza |
| **0-1** | Nginx recibe ~500 conexiones simultáneas. El `limit_req zone=antiddos rate=500r/s burst=1000` (laxo para demo) las deja pasar | Nginx | Conexiones reenviadas al principal |
| **1-3** | El principal recibe el flood. Tomcat asigna threads, los DAOs piden conexiones al pool Hikari, el pool de 30 se agota | Aplicativo principal | Errores 5xx empiezan a aparecer |
| **3-5** | Nginx registra 2 timeouts/errores en menos de 5s, satisface el criterio `max_fails=2 fail_timeout=5s` | Nginx | Principal marcado como `DOWN` |
| **5** | Nginx aplica `proxy_next_upstream`, redirige todo tráfico nuevo a la IP del backup | Nginx | **FAILOVER ACTIVADO** |
| **5-7** | El tráfico hacia el backup atraviesa Snort vía NFQUEUE inline | Snort | Inspección DPI activa |
| **7-8** | Snort cuenta paquetes con `content:"GET"; http_method` desde la IP atacante. Supera `count 30, seconds 5` | Snort | Regla `sid:1000003` dispara |
| **8+** | Snort hace `drop` de cada paquete adicional desde la IP atacante. El backup deja de recibirlos | Snort | **ATAQUE NEUTRALIZADO** |
| **8-60** | El backup procesa solo tráfico legítimo (incluso si hubiera ataque, ya no llega). Hikari mantiene el pool estable | Aplicativo backup | Operación normal |
| **60+** | El atacante termina su comando `wrk`. Tras 5s sin fallas, Nginx empieza a reintentar el principal | Nginx | Health del principal en recuperación |

---

## Reacción de cada capa de defensa

### Capa 1 — Nginx (reverse proxy)

**Lo que ve:** picos de conexiones por IP, errores 5xx del upstream principal.

**Lo que hace:**
- Aplica `limit_conn 500` y `limit_req burst=1000` (límites laxos en demo, agresivos en producción).
- Detecta `max_fails=2` y aplica `proxy_next_upstream`.
- Reenruta al backup en ~4 segundos.

**Lo que NO hace:**
- No reintenta POST en el backup (sin `non_idempotent`), evitando dobles transferencias.
- No filtra HTTP flood por contenido (esa labor es de Snort).

### Capa 2 — Snort (IPS inline)

**Lo que ve:** todos los paquetes destinados al backup vía NFQUEUE.

**Lo que hace:**
- Inspecciona métodos HTTP, conteo por IP origen.
- Aplica las reglas `drop`:
  - `sid:1000003` — HTTP GET flood: >30 GETs/IP en 5s.
  - `sid:1000004` — HTTP POST flood: >20 POSTs/IP en 5s.
  - `sid:1000005` — Slowloris: GETs sin `\r\n\r\n` (defensa redundante).
  - `sid:1000010` — SYN flood (defensa preventiva, no aplica al ataque actual).

**Lo que NO hace:**
- No defiende al principal (Snort está físicamente entre Nginx y el backup únicamente).
- No detecta ataques distribuidos desde >50 IPs distintas (umbral por IP no se alcanza).

### Capa 3 — Tomcat (servlet embebido)

**Lo que ve:** conexiones aceptadas del proxy.

**Lo que hace:**
- Aplica `max-connections=400` y `accept-count=100` (defensa en profundidad).
- Cierra conexiones que no envían headers en 5s (`connection-timeout`).
- Cierra keep-alive después de 100 requests (`max-keep-alive-requests`).

### Capa 4 — HikariCP (pool de conexiones)

**Lo que ve:** llamadas a `getConnection()` desde los DAOs.

**Lo que hace:**
- Limita a 30 conexiones físicas a MySQL.
- Falla con `SQLTransientConnectionException` si el pool está agotado tras 3s (`connectionTimeout=3000`).
- Recicla conexiones tras 30 min (`maxLifetime`) o tras 10s sin uso (`leakDetectionThreshold`).

**Efecto neto:** aun si el ataque pasa todas las capas anteriores, MySQL nunca recibe más de 30 conexiones desde el aplicativo, protegiendo la base de datos del efecto cascada.

---

## Indicadores observables (IoC)

Durante la demo, estos son los indicadores que se ven en **Splunk** y permiten contar la historia al jurado:

### En `sourcetype=nginx:access`

```
40.0.4.1 - - [t=0s] "GET / HTTP/1.1" 200 1234 upstream=40.0.4.10:8081 rt=0.012
40.0.4.1 - - [t=2s] "GET / HTTP/1.1" 502  127 upstream=40.0.4.10:8081 rt=2.003
40.0.4.1 - - [t=3s] "GET / HTTP/1.1" 502  127 upstream=40.0.4.10:8081 rt=2.001
40.0.4.1 - - [t=5s] "GET / HTTP/1.1" 200 1234 upstream=40.0.4.11:8081 rt=0.015
                                                        ^^^^^^^^^^
                                                        FAILOVER al backup
```

### En `sourcetype=nginx:error`

```
[error] upstream timed out (110: Connection timed out) while connecting to upstream,
        upstream: "http://40.0.4.10:8081/"
[warn]  upstream server temporarily disabled while connecting to upstream
```

### En `sourcetype=spring:boot` (host=core-principal)

```
HikariPool-1 - Connection is not available, request timed out after 3001ms
java.sql.SQLTransientConnectionException
o.a.c.c.C.[.[.[/].[dispatcherServlet] - Servlet.service() threw exception
```

### En `sourcetype=snort:alert`

```
[**] [1:1000003:2] "HTTP GET Flood bloqueado" [**]
[Priority: 1] {TCP} 192.168.x.x:54321 -> 40.0.4.11:8081
[**] [1:1000003:2] "HTTP GET Flood bloqueado" [**]
... (cientos de eventos por segundo durante el ataque)
```

### Dashboard sugerido en Splunk

Panel principal con cuatro paneles temporales sincronizados (timeline 0-90s):

1. **Requests/segundo a Nginx** — muestra el spike del ataque.
2. **Upstream destination over time** — barras stacked por `upstream_addr`, muestra el cambio de IP en t=5s.
3. **Errores 5xx por host** — muestra el principal con todos los errores hasta t=5s, después el backup con cero.
4. **Eventos Snort por sid** — muestra el conteo de drops creciendo desde t=8s.

---

## Por qué funciona la mitigación

La demo se basa en la **secuencia precisa** de estas tres condiciones, todas necesarias:

1. **El principal cae rápido** — gracias a que NO tiene Snort delante y a que el HikariCP, aunque protege a MySQL, no impide que Tomcat se sature.
2. **Nginx detecta y redirige antes de que el cliente abandone** — gracias a `max_fails=2 fail_timeout=5s` y `proxy_next_upstream`.
3. **Snort bloquea por umbral de IP única** — gracias a que el ataque proviene de un solo origen y las reglas `drop` están realmente activas (no `alert`).

Si cualquiera de las tres falla, el demo se rompe:

| Condición que falla | Consecuencia visible |
|---------------------|----------------------|
| Principal no cae (por ejemplo, si Nginx `limit_req` lo absorbe) | No hay failover, el demo no muestra nada interesante |
| Nginx no detecta a tiempo | El cliente ve errores prolongados, no se demuestra continuidad |
| Snort no bloquea (regla `alert` en lugar de `drop`) | El backup también cae, el demo muestra que el sistema no resiste |

---

## Limitaciones del escenario

Es importante reconocer estas limitaciones del setup para mantener la integridad técnica de la presentación:

### El ataque NO es realmente distribuido

Es un **DoS** (Denial of Service) técnicamente, no un **DDoS** (Distributed). El tráfico viene de una sola IP (la máquina con `wrk`). Las reglas de Snort usan `track by_src`, que es exactamente la defensa apropiada para un atacante único, pero **ineficaz** contra una botnet real con miles de IPs.

**Implicación para producción:** se requeriría adicionalmente:
- Threat intelligence feed (listas de IPs maliciosas conocidas).
- Behavior-based detection (análisis de patrones de navegación).
- Anycast / scrubbing centers (Cloudflare, AWS Shield).

### Snort solo protege al backup

El principal NO está protegido por Snort en esta topología. Cae a propósito para demostrar el valor del IPS por contraste. En producción real, **ambos** backends deberían estar detrás de un IPS.

### El volumen es modesto comparado con ataques reales

Un ataque DDoS contra una institución financiera real maneja Tbps de tráfico. La demo genera ~10-50 Mbps. La diferencia se aborda en producción con:
- Mitigación upstream en ISP/CDN.
- BGP blackhole automático.
- Anycast distribuido geográficamente.

### El "tráfico legítimo" durante el ataque es escaso

La demo asume que durante los 60s del ataque hay pocos usuarios legítimos navegando. En realidad, las reglas de Snort con threshold `count 30/5s` también afectarían a un usuario muy activo legítimo. Para producción se usan thresholds más altos combinados con análisis comportamental.

### La defensa requiere que el ataque concentre tráfico

Si el atacante diluye el tráfico (ej. 25 GETs/5s sostenido, justo bajo el umbral), Snort no dispara y el backup eventualmente cae. La defensa robusta requiere thresholds adaptativos o machine learning, fuera del alcance de la demo.

---

## Resumen para el guion de presentación

> "El ataque que vamos a ejecutar es un HTTP Flood de capa 7, el tipo más común contra aplicaciones financieras modernas. Vamos a lanzar 500 conexiones concurrentes con `wrk` contra Ameribank durante 60 segundos. En los primeros segundos van a ver cómo el sitio principal cae porque no tiene protección de IPS. Aproximadamente en el segundo 5, Nginx detecta la caída y hace failover automático al backup. El backup está protegido por Snort en modo inline, que detecta el patrón del ataque y bloquea el tráfico malicioso, dejando pasar solo las peticiones legítimas. En Splunk pueden ver, en tiempo real, el spike del ataque, el cambio de upstream y los eventos de bloqueo del IPS, todo correlacionado en una línea de tiempo."
