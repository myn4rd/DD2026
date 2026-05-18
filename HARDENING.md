# Ameribank — Configuración y Hardening

> Documento de referencia sobre el estado actual de las configuraciones (Nginx, Snort, MySQL, aplicativo Spring Boot) y las medidas de hardening aplicadas para sobrevivir un escenario de ataque DDoS controlado durante la demo del DemoDay Cybersecurity Amerike 2025.
>
> Este documento describe **el flujo lógico y la configuración**, no la topología física. Es independiente de si los componentes corren en LXC, contenedores o máquinas físicas.

---

## Tabla de Contenidos

- [Resumen ejecutivo](#resumen-ejecutivo)
- [Flujo extremo a extremo de una petición](#flujo-extremo-a-extremo-de-una-petición)
- [Hardening del aplicativo Spring Boot](#hardening-del-aplicativo-spring-boot)
- [Configuración de Nginx (reverse proxy + failover)](#configuración-de-nginx-reverse-proxy--failover)
- [Configuración de Snort (IPS)](#configuración-de-snort-ips)
- [Configuración de MySQL](#configuración-de-mysql)
- [Logs centralizados](#logs-centralizados)
- [Flujo de la demo paso a paso](#flujo-de-la-demo-paso-a-paso)
- [Resumen de defensas por capa](#resumen-de-defensas-por-capa)

---

## Resumen ejecutivo

El aplicativo bancario Ameribank originalmente era vulnerable a ataques de denegación de servicio incluso de baja sofisticación, debido a la ausencia de pool de conexiones, timeouts permisivos en Tomcat y reglas de detección de Snort que solamente alertaban sin bloquear. El hardening aplicado introduce defensa en profundidad en cuatro capas:

| Capa | Componente | Defensa aplicada |
|------|------------|------------------|
| 1 — Red | Snort IPS | Reglas en modo `drop` para SYN flood, HTTP flood y patrones de slowloris |
| 2 — Proxy | Nginx | Rate limiting por IP, timeouts agresivos contra slowloris, failover automático |
| 3 — Servlet | Tomcat embebido | Límites de conexiones, `keep-alive-timeout`, `accept-count` acotado |
| 4 — Datos | HikariCP | Pool con tamaño máximo, validación, detección de fugas |

El resultado es un sistema donde el aplicativo principal sigue siendo vulnerable a propósito (para demostrar el ataque), mientras que el backup —protegido por Snort y con el mismo hardening interno— absorbe el tráfico residual sin caer.

---

## Flujo extremo a extremo de una petición

```
                ┌──────────────┐
   Cliente ───► │    Nginx     │  ← rate limit, timeouts, headers
                │ Reverse Proxy│  ← decide upstream según salud
                └──┬───────┬───┘
                   │       │
       ┌───────────┘       └────────────┐
       ▼                                ▼
┌─────────────┐                  ┌──────────────┐
│  Principal  │                  │    Snort     │ ← inspecciona
│  (Tomcat)   │                  │  (IPS in-line)│   y filtra
└─────┬───────┘                  └──────┬───────┘
      │                                 ▼
      │                          ┌──────────────┐
      │                          │   Backup     │
      │                          │  (Tomcat)    │
      │                          └──────┬───────┘
      │                                 │
      └──────────────┬──────────────────┘
                     ▼
              ┌─────────────┐
              │   HikariCP  │ ← pool 30 conexiones
              │             │   con cache de prep stmts
              └──────┬──────┘
                     ▼
              ┌─────────────┐
              │    MySQL    │ ← bind-address 0.0.0.0
              │             │   usuario con privilegios
              │             │   acotados a Ameribank.*
              └─────────────┘
```

**Recorrido en condiciones normales:**

1. Cliente envía petición HTTP a Nginx.
2. Nginx aplica `limit_req` y `limit_conn` por IP, valida headers (timeout 5s), enruta al principal.
3. Tomcat acepta la conexión (límite 400 concurrentes, 200 hilos worker).
4. El servlet ejecuta el DAO, que pide una conexión al pool HikariCP.
5. HikariCP entrega una conexión reutilizada (sin handshake nuevo).
6. El stored procedure se ejecuta en MySQL; la respuesta sube por la misma pila.

**Recorrido durante un ataque al principal:**

1-2. Igual que arriba.
3. El principal se satura (depende del vector). Nginx detecta fallas de upstream (`max_fails=2`, ventana `5s`).
4. Nginx marca al principal como `down` durante `fail_timeout=5s` y reenvía a la dirección del backup.
5. Snort intercepta el tráfico hacia el backup, aplica las reglas y descarta los paquetes que cumplen umbral de DDoS.
6. El backup recibe únicamente tráfico filtrado, lo procesa normalmente.

---

## Hardening del aplicativo Spring Boot

### 1. `ConexionDB.java` — Pool de conexiones con HikariCP

**Archivo:** `src/main/java/org/amerike/ameribank/config/ConexionDB.java`

**Antes:** cada llamada a `conectar()` ejecutaba `DriverManager.getConnection()` abriendo una conexión TCP nueva a MySQL, realizando handshake completo, autenticación y validación por cada operación. Bajo carga, los descriptores de archivo se agotaban en segundos y MySQL alcanzaba su límite por defecto de `max_connections=151`.

**Después:** una única instancia `HikariDataSource` inicializada con doble-checked locking, que mantiene un pool reutilizable. Las credenciales se descifran **una sola vez** durante la primera invocación.

**Parámetros del pool:**

| Parámetro | Valor | Justificación |
|-----------|-------|---------------|
| `maximumPoolSize` | 30 | Cabe holgadamente en `max_connections=151` de MySQL aunque corran principal + backup |
| `minimumIdle` | 5 | Conexiones siempre tibias para latencia baja en arranque en frío |
| `connectionTimeout` | 3000 ms | Falla rápido si el pool está agotado, no encola peticiones indefinidamente |
| `validationTimeout` | 2000 ms | Verifica conexiones inactivas sin colgar el thread |
| `idleTimeout` | 60 000 ms | Libera conexiones no usadas en 1 minuto |
| `maxLifetime` | 1 800 000 ms | Reciclaje proactivo cada 30 min, evita `wait_timeout` de MySQL |
| `leakDetectionThreshold` | 10 000 ms | Genera warning si un DAO retiene una conexión más de 10s |

**Propiedades del driver MySQL:**

- `cachePrepStmts=true` con cache de 250 statements y SQL hasta 2048 caracteres: evita reparseo de los 19 stored procedures.
- `useServerPrepStmts=true`: ejecuta prepared statements en el servidor MySQL.
- `useLocalSessionState=true`: reduce roundtrips de validación de sesión.
- `rewriteBatchedStatements=true`: optimiza inserts batched (útil para el job de blacklist 2FA).

### 2. `security.java` — Manejo de credenciales sin matar la JVM

**Archivo:** `src/main/java/org/amerike/ameribank/config/security.java`

**Antes:** los métodos `obtenerUrl()`, `obtenerUsuario()` y `obtenerPassword()` ejecutaban `System.exit(1)` ante cualquier excepción. Un fallo transitorio de I/O en el archivo cifrado (NFS lento, permisos temporales) terminaba el proceso completo.

**Después:** las tres rutas de error lanzan `IllegalStateException`, permitiendo que Spring maneje la excepción, logue el error y eventualmente reintente o exponga el problema vía `/actuator/health`.

**Esquema de cifrado de credenciales:**

- Algoritmo: RSA-2048 con OAEP/SHA-256
- Llaves: `~/.config/ameribank/secrets/{private,public}_key.pem`
- Blob: `~/.config/ameribank/secrets/accesodbjava.enc`
- Formato del payload: `"jdbc:url"|"usuario"|"password"`
- Permisos endurecidos por el script de deploy: `700` en el directorio, `600` en los archivos.

### 3. Tomcat embebido — Límites para floods y slowloris

**Archivo:** `src/main/resources/application.properties`

| Propiedad | Valor | Función |
|-----------|-------|---------|
| `server.tomcat.threads.max` | 200 | Acota memoria por threads (cada thread ~512KB stack) |
| `server.tomcat.threads.min-spare` | 20 | Threads pre-calentados |
| `server.tomcat.max-connections` | 400 | Tope duro de conexiones aceptadas; el resto se rechaza con TCP RST |
| `server.tomcat.accept-count` | 100 | Cola de OS para conexiones pendientes |
| `server.tomcat.connection-timeout` | 5000 ms | Cierra sockets que no envían headers |
| `server.tomcat.keep-alive-timeout` | 10 000 ms | Cierra conexiones keep-alive inactivas |
| `server.tomcat.max-keep-alive-requests` | 100 | Recicla la conexión después de N requests |
| `server.tomcat.max-http-form-post-size` | 2 MB | Rechaza POSTs gigantes |
| `server.tomcat.max-swallow-size` | 2 MB | Limita bytes de body descartados al cerrar |

**Efecto combinado contra slowloris:** Un atacante que abra una conexión y mantenga headers incompletos por más de 5 segundos verá la conexión cerrada por el `connection-timeout`. Aun si el ataque pasa, está acotado a 400 conexiones simultáneas con cola adicional de 100.

### 4. Endpoint `/actuator/health`

Habilitado con detalles ocultos (`management.endpoint.health.show-details=never`) para que el reverse proxy pueda usarlo en pruebas internas sin filtrar información estructural a usuarios externos. Nginx lo bloquea para tráfico de Internet retornando 404 sobre `/actuator`.

---

## Configuración de Nginx (reverse proxy + failover)

**Archivo:** `scripts/nginx-ameribank.conf`

### Bloque `upstream`

```nginx
upstream ameribank {
    server <ip-principal>:8081 max_fails=2 fail_timeout=5s;
    server <ip-backup>:8081 backup max_fails=2 fail_timeout=5s;
    keepalive 32;
    keepalive_timeout 60s;
}
```

**Decisiones clave:**

- `max_fails=2 fail_timeout=5s`: detecta caída del principal en ~4 segundos (dos timeouts de `proxy_connect_timeout=2s`). El valor original de la documentación (`max_fails=3 fail_timeout=10s`) tardaba ~12 segundos, demasiado para una demo en vivo.
- `backup`: la segunda upstream solo recibe tráfico cuando el principal está marcado como caído. No hay balanceo proactivo.
- `keepalive 32`: mantiene 32 conexiones abiertas al backend Java, eliminando handshake TCP por request. Sin esto, cada petición HTTPS de un cliente generaría una conexión TCP nueva entre Nginx y Tomcat.

### Rate limiting

```nginx
limit_req_zone  $binary_remote_addr zone=antiddos:10m  rate=500r/s;
limit_conn_zone $binary_remote_addr zone=connlimit:10m;

# En el server:
limit_conn  connlimit 500;
limit_req   zone=antiddos burst=1000 nodelay;
```

**Nota importante para la demo:** el `rate=500r/s` está intencionalmente alto. Con un valor de producción típico (`rate=30r/s`), Nginx absorbería el HTTP flood antes de que llegara al principal y la demo no podría mostrar el failover. Para producción real, se documenta en el archivo bajar a `30r/s` y dejar a Nginx como primera línea de defensa.

### Timeouts globales (anti-slowloris)

```nginx
client_body_timeout    5s;
client_header_timeout  5s;
keepalive_timeout     10s;
send_timeout          10s;
```

Estos cuatro timeouts son la defensa primaria contra slowloris en el frontend. Una conexión que no completa headers en 5s o que envía body lento es cerrada por Nginx antes de llegar al backend.

### Proxy y failover

```nginx
proxy_next_upstream         error timeout http_502 http_503 http_504;
proxy_next_upstream_tries   2;
proxy_next_upstream_timeout 5s;
proxy_connect_timeout       2s;
proxy_send_timeout          5s;
proxy_read_timeout          5s;
proxy_http_version          1.1;
proxy_set_header            Connection "";
```

**`non_idempotent` omitido a propósito:** Por defecto Nginx no reintenta POST/PUT/DELETE en otro upstream aunque el primero falle, porque podría duplicar operaciones (ej. dos transferencias bancarias). Mantener este comportamiento es una decisión de seguridad de negocio: durante el failover, las transferencias en vuelo fallan visiblemente y el cliente decide si reintenta.

### Cabeceras hacia el backend

```nginx
proxy_set_header Host              $host;
proxy_set_header X-Real-IP         $remote_addr;
proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
```

El backend obtiene la IP real del cliente, indispensable para logs útiles en Splunk.

### Bloqueo de superficie expuesta

```nginx
location /actuator {
    return 404;
}
```

El endpoint de health del actuator solo debe responder a checks internos. Devolver `404` (en vez de `403`) evita confirmar la existencia del endpoint a un atacante.

### Endpoint de monitoreo interno

```nginx
location = /nginx-status {
    stub_status;
    access_log off;
    allow 40.0.4.0/24;
    deny all;
}
```

Permite consultar conexiones activas, requests por segundo y uso del worker desde la red interna. Útil durante la demo para mostrar en pantalla cómo el conteo de conexiones explota durante el ataque.

### Formato de log extendido

```nginx
log_format ameribank '... upstream=$upstream_addr '
                     'rt=$request_time urt=$upstream_response_time '
                     'tries=$upstream_status';
```

Incluir `$upstream_addr` y `$upstream_status` permite ver en Splunk en qué upstream se atendió cada request y si hubo reintentos por failover.

---

## Configuración de Snort (IPS)

**Archivo:** `scripts/snort-local.rules`

**Modo de operación:** Snort se ejecuta en modo IPS inline usando NFQUEUE de Netfilter:

```bash
iptables -I FORWARD -j NFQUEUE --queue-num 0
snort -Q --daq nfq --daq-var queue=0 -c /etc/snort/snort.conf -l /mnt/splunk-logs/
```

Esto significa que todo paquete que vaya hacia el backup pasa por Snort, y las reglas `drop` realmente bloquean (no solo alertan).

### Regla 1 — SYN flood (sid:1000010)

```
drop tcp any any -> $HOME_NET 8081 (msg:"SYN Flood bloqueado";
    flags:S; threshold:type both, track by_src, count 100, seconds 10;
    sid:1000010; rev:1;)
```

- Detecta más de 100 paquetes SYN del mismo origen en 10 segundos.
- Bloquea típicamente ataques con `hping3 -S --flood`.

### Regla 2 — HTTP GET flood (sid:1000003)

```
drop tcp any any -> $HOME_NET 8081 (msg:"HTTP GET Flood bloqueado";
    flow:to_server,established;
    content:"GET"; http_method;
    threshold:type both, track by_src, count 30, seconds 5;
    sid:1000003; rev:2;)
```

- Detecta más de 30 GETs del mismo origen en 5 segundos sobre conexiones ya establecidas (post-handshake).
- **Diferencia crítica vs. versión original:** la regla original era `alert` (solo registra). Cambiada a `drop` para que realmente bloquee.

### Regla 3 — HTTP POST flood (sid:1000004)

```
drop tcp any any -> $HOME_NET 8081 (msg:"HTTP POST Flood bloqueado";
    flow:to_server,established;
    content:"POST"; http_method;
    threshold:type both, track by_src, count 20, seconds 5;
    sid:1000004; rev:1;)
```

- Umbral más bajo (20/5s) porque los POSTs hacia `/login` son más costosos para el backend (validación + stored procedure).

### Regla 4 — Slowloris (sid:1000005)

```
drop tcp any any -> $HOME_NET 8081 (msg:"Slowloris bloqueado";
    flow:to_server,established;
    content:"GET"; http_method;
    content:!"|0d 0a 0d 0a|";
    threshold:type both, track by_src, count 30, seconds 30;
    sid:1000005; rev:1;)
```

- Detecta conexiones con `GET` que **no incluyen** la secuencia `\r\n\r\n` (fin de headers HTTP).
- Defensa redundante con los timeouts de Nginx y Tomcat (cinturón y tirantes).

### Reglas espejo en modo `alert`

Para cada `drop` existe una `alert` equivalente (sid:1000001, sid:1000002) que solo registra. Estas son útiles cuando se quiere ver el ataque pasar sin bloquearlo (escenario de prueba o ajuste de umbrales).

### Limitación conocida — `track by_src`

Todas las reglas usan `track by_src`, lo que significa que el conteo es por IP origen. Un atacante distribuido con miles de IPs (botnet real) evade los umbrales porque ninguna IP individual los alcanza. Para producción se requeriría `track by_dst` con umbrales más altos, o integración con un servicio externo de threat intelligence. Para la demo controlada con un solo origen de ataque, esto no es problema.

---

## Configuración de MySQL

**Archivo:** `scripts/install_mysql.sh` (genera `/etc/my.cnf.d/ameribank.cnf` o equivalente)

### Bloque `[mysqld]`

```ini
bind-address       = 0.0.0.0
event_scheduler    = ON
general_log        = 1
general_log_file   = /var/log/mysql/mysql-general.log
slow_query_log     = 1
slow_query_log_file = /var/log/mysql/mysql-slow.log
long_query_time    = 1
```

- `bind-address = 0.0.0.0`: requerido para que los backends de aplicación conecten desde otros hosts. La seguridad se delega al firewall (CIDR-restricted).
- `event_scheduler = ON`: requerido para el evento `ev_expire_2fa_codes` que expira códigos 2FA cada 5 segundos.
- `general_log` y `slow_query_log` habilitados para enviar todo a Splunk vía NFS.
- `long_query_time = 1`: cualquier query de más de 1s entra al slow log (umbral agresivo para captar problemas en demo).

### Usuario de aplicación

```sql
CREATE USER 'ameribank'@'%' IDENTIFIED BY '...';
GRANT ALL PRIVILEGES ON Ameribank.* TO 'ameribank'@'%';
```

- Privilegios acotados a la base `Ameribank` únicamente.
- No tiene `WITH GRANT OPTION`, no puede crear más usuarios.
- El `'%'` permite conexión desde cualquier IP; el firewall del LXC limita realmente quién puede llegar.

### Firewall sobre el puerto 3306

El script abre el puerto **solo desde el CIDR `40.0.4.0/24`** (la red de aplicación), bloqueado para cualquier otra red. Implementado con `firewalld rich rules` en Rocky o `ufw` en Ubuntu.

---

## Logs centralizados

### Destinos de log en cada componente

| Componente | Archivo local | Mount NFS hacia | Sourcetype en Splunk |
|------------|---------------|-----------------|----------------------|
| Aplicativo (principal) | `/mnt/splunk-logs/ameribank.log` | `core-principal/` | `spring:boot` |
| Aplicativo (backup) | `/mnt/splunk-logs/ameribank.log` | `core-backup/` | `spring:boot` |
| Nginx access | `/mnt/splunk-logs/nginx-access.log` | `nginx/` | `nginx:access` |
| Nginx error | `/mnt/splunk-logs/nginx-error.log` | `nginx/` | `nginx:error` |
| Snort alert | `/mnt/splunk-logs/alert` | `snort/` | `snort:alert` |
| MySQL general | `/mnt/splunk-logs/mysql-general.log` | `mysql/` | `mysql:general` |
| MySQL slow | `/mnt/splunk-logs/mysql-slow.log` | `mysql/` | `mysql:slow` |

### Resiliencia del mount NFS

El script de deploy monta NFS con opciones `_netdev,nofail` en `/etc/fstab`, y la unit de systemd del aplicativo declara `RequiresMountsFor=/mnt/splunk-logs`. Esto garantiza:

- El sistema arranca aunque NFS esté caído (`nofail`).
- El aplicativo no arranca hasta que NFS está montado (`RequiresMountsFor`).
- Los logs nunca se pierden ni causan que el servicio falle por I/O bloqueante.

---

## Flujo de la demo paso a paso

| t (s) | Evento | Componente | Visible en Splunk |
|------:|--------|------------|-------------------|
| 0 | Atacante lanza `wrk -t12 -c500 -d60s http://nginx/login` | Externo | Spike de requests en `nginx:access` |
| 0-1 | Nginx aplica `limit_req` (laxo en demo), enruta al principal | Nginx | — |
| 1-3 | Principal satura el pool Hikari (30 conn), responde con 5xx | App principal | Errores 5xx en `spring:boot` del principal |
| 3-5 | Nginx detecta 2 fallos en 5s, marca principal como `down` | Nginx | Mensajes "upstream timed out" en `nginx:error` |
| 5+ | Nginx redirige tráfico nuevo a la IP del backup | Nginx | Cambia `$upstream_addr` en `nginx:access` |
| 5-7 | Tráfico llega al backup pasando por Snort (NFQUEUE) | Snort | Primeras alertas en `snort:alert` |
| 7-10 | Snort cuenta 30 GETs / 5s de la IP atacante, activa `drop` (sid:1000003) | Snort | Eventos "HTTP GET Flood bloqueado" |
| 10+ | Backup recibe solo tráfico legítimo, responde 200 | App backup | Logs normales en `spring:boot` del backup |
| Continuo | Splunk visualiza correlación: ataque → failover → bloqueo | SIEM | Dashboard con timeline |

### Puntos demostrables al jurado

1. **Defensa por capas:** ningún componente individual sostiene la demo solo. Nginx hace el failover, Snort filtra el flood en el backup, HikariCP y Tomcat dan resiliencia interna.
2. **Detección y respuesta:** Splunk muestra el ataque, la decisión de failover y el bloqueo del IPS en tiempo real, sin intervención humana.
3. **Continuidad de negocio:** un cliente legítimo que abre el sitio durante el ataque puede seguir operando porque el backup sigue de pie.
4. **Trazabilidad:** todos los logs convergen en Splunk con `host` y `sourcetype` distintos, permitiendo reconstruir el ataque post-mortem.

---

## Resumen de defensas por capa

| Capa | Amenaza | Defensa | Archivo |
|------|---------|---------|---------|
| Red | SYN flood | Snort `drop` sid:1000010 | `snort-local.rules` |
| Red | HTTP flood | Snort `drop` sid:1000003/1000004 | `snort-local.rules` |
| Red | Slowloris | Snort `drop` sid:1000005 | `snort-local.rules` |
| Proxy | Rate por IP | `limit_req zone=antiddos` | `nginx-ameribank.conf` |
| Proxy | Conexiones concurrentes por IP | `limit_conn connlimit` | `nginx-ameribank.conf` |
| Proxy | Slowloris (headers/body lentos) | `client_*_timeout 5s` | `nginx-ameribank.conf` |
| Proxy | Backend caído | `max_fails`, `backup`, `proxy_next_upstream` | `nginx-ameribank.conf` |
| Servlet | Conexiones lentas en Tomcat | `connection-timeout`, `keep-alive-timeout` | `application.properties` |
| Servlet | Saturación de threads | `max-connections`, `accept-count` | `application.properties` |
| Servlet | POSTs gigantes | `max-http-form-post-size` | `application.properties` |
| Datos | Agotamiento de conexiones MySQL | HikariCP pool size=30 | `ConexionDB.java` |
| Datos | Fuga de conexiones por bugs en DAOs | `leakDetectionThreshold=10s` | `ConexionDB.java` |
| Datos | Reparseo de stored procs | `cachePrepStmts=true` | `ConexionDB.java` |
| Datos | Acceso externo a MySQL | Firewall CIDR-restricted | `install_mysql.sh` |
| Datos | Credenciales en disco | RSA-2048 + permisos 600 | `security.java` + deploy script |
