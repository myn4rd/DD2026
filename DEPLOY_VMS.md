# Ameribank — Deploy en 3 VMs y Demo de Ataque DoS

> Guía de instalación y uso de los scripts `deploy_vm_app.sh` e `install_mysql_vm.sh`
> para el escenario de demo del DemoDay Cybersecurity Amerike 2025.
>
> Documentos relacionados:
> - `INFRAESTRUCTURA.md` — arquitectura LXC original y diseño de red
> - `ATAQUE_DDOS.md` — descripción técnica del vector HTTP Flood
> - `HARDENING.md` — configuraciones de seguridad aplicadas

---

## Tabla de Contenidos

- [Arquitectura de las 3 VMs](#arquitectura-de-las-3-vms)
- [Requisitos previos](#requisitos-previos)
- [Paso 1 — VM-DB: instalar MySQL](#paso-1--vm-db-instalar-mysql)
- [Paso 2 — VM-Principal: deploy del core](#paso-2--vm-principal-deploy-del-core)
- [Paso 3 — VM-Backup: deploy del core de respaldo](#paso-3--vm-backup-deploy-del-core-de-respaldo)
- [Paso 4 — Nginx: configurar el proxy con failover](#paso-4--nginx-configurar-el-proxy-con-failover)
- [Verificación del entorno completo](#verificación-del-entorno-completo)
- [Guion de la demo DoS](#guion-de-la-demo-dos)
- [Qué ve la audiencia en cada momento](#qué-ve-la-audiencia-en-cada-momento)
- [Troubleshooting](#troubleshooting)

---

## Arquitectura de las 3 VMs

```
          ATACANTE
          (wrk / ab)
              │
              ▼
     ┌─────────────────┐
     │      Nginx      │  ← Reverse proxy con failover automático
     │   (proxy VM)    │    Puede ser una 4ª VM o el host del hypervisor
     └───────┬─────────┘
             │
    ┌────────┴────────┐
    │                 │  failover automático (~4-5 s después de la caída)
    ▼                 ▼
┌──────────┐    ┌──────────┐
│VM-Princi-│    │VM-Backup │  ← Mismo código, mismo JAR
│   pal    │    │          │    comparte la MISMA base de datos
│ :8081    │    │  :8081   │
│ SIN IPS  │    │ CON IPS  │  ← Snort delante (opcional en la VM)
└────┬─────┘    └────┬─────┘
     │               │
     └───────┬────────┘
             │  ambas conectan a la misma VM-DB
             ▼
     ┌───────────────┐
     │    VM-DB      │
     │   MySQL :3306 │
     │               │
     └───────────────┘
```

### IPs de ejemplo usadas en esta guía

Ajusta estos valores a los de tu hypervisor (VirtualBox, VMware, Proxmox, etc.):

| VM | Rol | IP de ejemplo | Puerto |
|----|-----|---------------|--------|
| Nginx / proxy | Entrada de tráfico | `192.168.100.1` | 80 |
| VM-Principal | Core bancario principal | `192.168.100.10` | 8081 |
| VM-Backup | Core bancario de respaldo | `192.168.100.11` | 8081 |
| VM-DB | MySQL | `192.168.100.20` | 3306 |

### Por qué las dos VMs de app comparten la misma BD

Esto es el corazón de la demo: cuando el principal cae y Nginx activa el failover al backup,
**el backup ve exactamente los mismos datos** — cuentas, saldos, historial. Para la audiencia
esto demuestra que la continuidad del servicio es real y no solo visual.

El usuario puede iniciar sesión y hacer una transferencia antes del ataque. Después del failover,
al refrescar la página (ahora respondiendo el backup) el historial de esa transferencia sigue ahí.

---

## Requisitos previos

En **cada VM** antes de correr los scripts:

```bash
# Rocky Linux / RHEL
dnf install -y git

# Ubuntu / Debian
apt-get install -y git
```

Clona o copia los scripts al servidor:

```bash
git clone https://github.com/myn4rd/DD2026.git /opt/DD2026
cd /opt/DD2026/scripts
```

> Los scripts detectan automáticamente si el SO es Rocky/RHEL o Ubuntu/Debian
> y usan `dnf`/`firewalld` o `apt`/`ufw` según corresponda.

---

## Paso 1 — VM-DB: instalar MySQL

Este es el primer paso. Las VMs de app necesitan que la BD ya exista para poder
configurar sus credenciales cifradas.

### Comando

```bash
# En VM-DB (192.168.100.20):
sudo ./install_mysql_vm.sh \
    --principal-host 192.168.100.10 \
    --backup-host    192.168.100.11 \
    --db-name        Ameribank \
    --db-user        ameribank \
    --db-pass        'TuPasswordSeguro' \
    --root-pass      'PasswordDeRoot'
```

Si omites `--db-pass` y `--root-pass`, el script los pide de forma interactiva con input oculto.

### Qué hace el script

1. Detecta el OS e instala `mysql-server`
2. Escribe la configuración en `/etc/my.cnf.d/ameribank.cnf` (Rocky) o `/etc/mysql/mysql.conf.d/ameribank.cnf` (Ubuntu):
   - `bind-address = 0.0.0.0` — acepta conexiones remotas
   - `event_scheduler = ON` — necesario para la expiración de tokens 2FA
   - Logs en `/var/log/mysql/`
3. Asegura la cuenta root de MySQL
4. Carga el esquema completo desde `ameribank_full_db.sql`
5. Crea el usuario `ameribank` **dos veces**, una por cada VM de app:
   ```sql
   CREATE USER 'ameribank'@'192.168.100.10' ...;  -- solo VM-Principal
   CREATE USER 'ameribank'@'192.168.100.11' ...;  -- solo VM-Backup
   ```
6. Abre el firewall (puerto 3306) únicamente para esas dos IPs

### Verificar que quedó bien

```bash
# Desde VM-DB misma
mysql -uroot -p -e "SELECT user, host FROM mysql.user WHERE user='ameribank';"

# Resultado esperado:
# +-----------+----------------+
# | user      | host           |
# +-----------+----------------+
# | ameribank | 192.168.100.10 |
# | ameribank | 192.168.100.11 |
# +-----------+----------------+
```

```bash
# Desde VM-Principal (prueba de conectividad)
mysql -h 192.168.100.20 -u ameribank -p -e "USE Ameribank; SHOW TABLES;"
```

---

## Paso 2 — VM-Principal: deploy del core

### Comando

```bash
# En VM-Principal (192.168.100.10):
sudo ./deploy_vm_app.sh \
    --role      principal \
    --db-host   192.168.100.20 \
    --db-name   Ameribank \
    --db-user   ameribank \
    --db-pass   'TuPasswordSeguro' \
    --allowed-cidr 192.168.100.0/24
```

Si omites `--db-pass`, el script arranca el JAR en modo interactivo y pide
las credenciales por consola. Cuando aparezca `Cifrado finalizado`, espera
~3 segundos y presiona `Ctrl+C`.

### Qué hace el script

1. Instala Java 17 y Git
2. Crea el usuario de sistema `ameribank` (sin shell, sin home) — no corre como root
3. Clona el repo en `/opt/DD2026` y compila con `./mvnw`
4. Verifica conectividad TCP con `VM-DB:3306` antes de continuar
5. Lanza el JAR **una sola vez** con `-Ddb.host=192.168.100.20` para que el cifrador
   RSA incluya la IP correcta en las credenciales encriptadas
6. Instala el servicio `systemd` con la unidad:
   ```
   ExecStart=/usr/bin/java -Ddb.host=192.168.100.20 -Ddb.port=3306 -jar /opt/DD2026/target/Ameribank-*.jar
   StandardOutput=append:/var/log/ameribank/principal/ameribank.log
   ```
7. Abre el firewall en el puerto `8081` desde la subred indicada
8. Hace un health check en `http://localhost:8081/actuator/health`

### Por qué `-Ddb.host` es importante

El código original de `security.java` tenía la IP de MySQL hardcodeada en el cifrador RSA.
Se modificó para leer la propiedad del sistema `db.host` al momento de cifrar las credenciales.
Esto significa que **las credenciales cifradas ya incluyen la IP de la VM-DB** y no necesitan
configuración adicional en `application.properties`.

### Verificar que quedó bien

```bash
systemctl status ameribank
# Active: active (running) ...

curl http://localhost:8081/actuator/health
# {"status":"UP"}

tail -f /var/log/ameribank/principal/ameribank.log
```

---

## Paso 3 — VM-Backup: deploy del core de respaldo

El comando es idéntico al del paso 2, solo cambia `--role`:

```bash
# En VM-Backup (192.168.100.11):
sudo ./deploy_vm_app.sh \
    --role      backup \
    --db-host   192.168.100.20 \
    --db-name   Ameribank \
    --db-user   ameribank \
    --db-pass   'TuPasswordSeguro' \
    --allowed-cidr 192.168.100.0/24
```

### Diferencias respecto al principal

| Aspecto | VM-Principal | VM-Backup |
|---------|-------------|-----------|
| `--role` | `principal` | `backup` |
| Logs | `/var/log/ameribank/principal/` | `/var/log/ameribank/backup/` |
| Descripción systemd | `Ameribank Spring Boot (principal)` | `Ameribank Spring Boot (backup)` |
| IPS delante | No (cae ante el DoS) | Sí — Snort inline si está configurado |
| Código / JAR | Idéntico | Idéntico |
| BD a la que conecta | `192.168.100.20` | `192.168.100.20` — **la misma** |

> El backup corre exactamente el mismo código y apunta a la misma base de datos.
> Lo que diferencia el comportamiento en la demo es que Nginx solo le envía tráfico
> cuando el principal falla, y opcionalmente tiene Snort filtrando antes de él.

### Verificar que quedó bien

```bash
# En VM-Backup:
curl http://localhost:8081/actuator/health
# {"status":"UP"}

# Probar directamente (sin pasar por Nginx):
curl http://192.168.100.11:8081/actuator/health
```

---

## Paso 4 — Nginx: configurar el proxy con failover

Nginx puede vivir en una 4ª VM, en el host del hypervisor, o en cualquier máquina
accesible desde la red de las 3 VMs.

### Instalar Nginx

```bash
# Rocky / RHEL
dnf install -y nginx
systemctl enable --now nginx

# Ubuntu / Debian
apt-get install -y nginx
systemctl enable --now nginx
```

### Configuración del upstream

Adapta el archivo `scripts/nginx-ameribank.conf` con las IPs de tus VMs
y cópialo a `/etc/nginx/conf.d/ameribank.conf`:

```nginx
upstream ameribank {
    # Principal — sin IPS, cae ante el DoS (propósito de la demo)
    server 192.168.100.10:8081 max_fails=2 fail_timeout=5s;

    # Backup — Nginx le envía tráfico solo cuando el principal falla
    server 192.168.100.11:8081 backup max_fails=2 fail_timeout=5s;

    keepalive 32;
}

server {
    listen 80;
    server_name ameribank.local _;

    access_log /var/log/nginx/ameribank-access.log;
    error_log  /var/log/nginx/ameribank-error.log warn;

    location /actuator {
        return 404;
    }

    location / {
        proxy_pass http://ameribank;
        proxy_next_upstream error timeout http_502 http_503 http_504;
        proxy_next_upstream_tries   2;
        proxy_next_upstream_timeout 5s;
        proxy_connect_timeout 2s;
        proxy_send_timeout    5s;
        proxy_read_timeout    5s;
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    }
}
```

> **Nota sobre el rate limit de Nginx para la demo:**
> El archivo `nginx-ameribank.conf` tiene `rate=500r/s` a propósito. Si se baja a `30r/s`,
> Nginx absorbe el HTTP flood antes de que llegue al backend, el principal nunca cae y
> no hay nada que demostrar. El objetivo es que el ataque llegue al aplicativo.

```bash
nginx -t && systemctl reload nginx
```

### Verificar failover manualmente

```bash
# 1. Ambas VMs deben responder
curl http://192.168.100.10:8081/actuator/health  # principal
curl http://192.168.100.11:8081/actuator/health  # backup

# 2. Nginx enruta al principal normalmente
curl http://192.168.100.1/actuator/health  # debería devolver 404 (bloqueado por Nginx)
curl http://192.168.100.1/                 # devuelve la app sirvida por el principal

# 3. Simular caída del principal
systemctl stop ameribank   # en VM-Principal

# 4. Nginx detecta la caída en ~5s y redirige al backup
# Espera 5s y refresca el navegador — la app sigue respondiendo (backup)

# 5. Restaurar el principal
systemctl start ameribank  # en VM-Principal
```

---

## Verificación del entorno completo

Antes de la demo, ejecuta este checklist desde la máquina que hará el ataque
(o desde cualquier máquina con acceso a la red):

```bash
# VM-DB accesible
nc -zv 192.168.100.20 3306 && echo "DB OK"

# VM-Principal levantada
curl -sf http://192.168.100.10:8081/actuator/health && echo "Principal OK"

# VM-Backup levantada
curl -sf http://192.168.100.11:8081/actuator/health && echo "Backup OK"

# Nginx enruta al principal
curl -s http://192.168.100.1/ | grep -i ameribank && echo "Nginx OK"

# Login funciona (datos de prueba)
curl -s -X POST http://192.168.100.1/login \
    -d 'usr=admin&pwd=admin123' | grep -i "bienvenido\|dashboard\|index"
```

---

## Guion de la demo DoS

### Preparación (5 minutos antes)

1. Abre el navegador en `http://192.168.100.1` y verifica que carga la app
2. Inicia sesión como `admin` / `admin123` o como `cliente1` / `cliente123`
3. Si usas `cliente1`, completa el flujo de 2FA para tener la sesión activa
4. Abre una segunda pestaña con los logs del principal en tiempo real:
   ```bash
   # En VM-Principal:
   tail -f /var/log/ameribank/principal/ameribank.log
   ```
5. Opcionalmente, abre los logs de Nginx:
   ```bash
   tail -f /var/log/nginx/ameribank-error.log
   ```

### Paso a paso de la demo

**1. Mostrar que el sistema funciona**

Con el navegador en `http://192.168.100.1`:
- Navega a consulta de saldos
- Realiza una transferencia o consulta de movimientos
- Muestra que las operaciones responden en milisegundos

**2. Lanzar el ataque DoS**

Desde la máquina atacante (puede ser el host del hypervisor):

```bash
# Opción A — wrk (recomendado, genera más carga)
wrk -t12 -c500 -d60s --latency http://192.168.100.1/

# Opción B — Apache Bench (más simple, suficiente para la demo)
ab -n 100000 -c 500 http://192.168.100.1/

# Opción C — POST flood al login (más costoso para el aplicativo)
wrk -t8 -c300 -d60s http://192.168.100.1/login
```

**3. Observar la caída del principal (~segundos 1-5)**

- En el log del principal verás:
  ```
  HikariPool-1 - Connection is not available, request timed out after 3001ms
  java.sql.SQLTransientConnectionException
  ```
- En el navegador el sitio comienza a responder lento o con errores

**4. Observar el failover automático de Nginx (~segundo 5)**

- En el log de errores de Nginx:
  ```
  upstream timed out while connecting to upstream: "http://192.168.100.10:8081/"
  upstream server temporarily disabled while connecting to upstream
  ```
- Nginx detecta `max_fails=2` y activa el backup en `192.168.100.11`

**5. Mostrar que el backup responde**

- Refresca el navegador — la app vuelve a cargar
- Navega a la misma cuenta de antes — los saldos y el historial están intactos
  porque ambas VMs comparten la misma VM-DB

**6. Demostrar continuidad de datos**

Este es el punto clave para la audiencia:

> "El backup no tiene una copia de los datos. Está viendo la misma base de datos que
> el principal. La transferencia que hicimos hace un momento aparece aquí porque
> nunca estuvo en el servidor de aplicaciones — siempre estuvo en MySQL."

**7. Terminar el ataque y mostrar recuperación**

```bash
# Ctrl+C en la terminal del atacante para detener wrk/ab
```

- Tras ~5 segundos sin fallas, Nginx empieza a reenviar tráfico al principal de nuevo
- El sistema vuelve a operar normalmente sin intervención manual

---

## Qué ve la audiencia en cada momento

| Tiempo | Lo que pasa | Lo que muestra en pantalla |
|--------|-------------|---------------------------|
| t=0 s | Se lanza `wrk` | Terminal del atacante: requests por segundo subiendo |
| t=1-3 s | El principal satura su pool de conexiones | Log del principal: `SQLTransientConnectionException` |
| t=3-5 s | Nginx detecta errores y marca el principal como caído | Log de Nginx: `upstream timed out` |
| t=5 s | **Failover activado** | Log de Nginx: `upstream server temporarily disabled` |
| t=5+ s | El backup recibe el tráfico | Navegador: el sitio responde de nuevo |
| t=5+ s | La BD sigue siendo la misma | Navegador: los datos del usuario están intactos |
| t=60 s | El atacante termina | Terminal de `wrk`: reporte final de req/s y latencias |
| t=65 s | Nginx recupera el principal | El sistema vuelve a enrutar al principal automáticamente |

### Mensaje clave para comunicar a la audiencia

> "El principal cayó en menos de 5 segundos. El failover fue automático, sin intervención
> manual, y el usuario no tuvo que volver a iniciar sesión. Los datos nunca estuvieron en
> riesgo porque la base de datos vive en su propia VM, separada del aplicativo. Esta es la
> diferencia entre disponibilidad del servicio y seguridad de los datos."

---

## Troubleshooting

### El principal no cae durante el ataque

El rate limit de Nginx puede estar absorbiendo el flood. Verifica en
`/etc/nginx/conf.d/ameribank.conf` que el rate sea alto (500r/s o más) para la demo:

```nginx
limit_req_zone $binary_remote_addr zone=antiddos:10m rate=500r/s;
```

Si sigue sin caer, aumenta la carga del atacante:
```bash
wrk -t16 -c1000 -d60s http://192.168.100.1/
```

### Nginx no hace failover

```bash
# Verificar que Nginx puede alcanzar las VMs directamente
curl http://192.168.100.10:8081/actuator/health
curl http://192.168.100.11:8081/actuator/health

# Verificar la configuración de Nginx
nginx -t

# Ver el log de errores en tiempo real
tail -f /var/log/nginx/ameribank-error.log
```

### El backup no ve los datos del principal

La causa más común es que las credenciales del backup apuntan a una BD diferente.
Verifica el parámetro `-Ddb.host` en el servicio:

```bash
# En VM-Backup:
systemctl cat ameribank | grep db.host
# Debe mostrar: -Ddb.host=192.168.100.20

# Si está mal, regenerar credenciales:
systemctl stop ameribank
rm -rf /etc/ameribank/secrets/
sudo ./deploy_vm_app.sh --role backup --db-host 192.168.100.20 --db-pass '...'
```

### MySQL rechaza la conexión desde una VM de app

```bash
# Verificar usuarios en VM-DB:
mysql -uroot -p -e "SELECT user, host FROM mysql.user WHERE user='ameribank';"

# Si falta el host, agregarlo manualmente:
mysql -uroot -p <<SQL
CREATE USER 'ameribank'@'192.168.100.11' IDENTIFIED BY 'TuPassword';
GRANT ALL ON Ameribank.* TO 'ameribank'@'192.168.100.11';
FLUSH PRIVILEGES;
SQL

# Verificar firewall en VM-DB:
firewall-cmd --list-rich-rules | grep 3306
# O en Ubuntu:
ufw status | grep 3306
```

### El servicio ameribank no arranca

```bash
# Ver el error completo:
journalctl -u ameribank -n 50 --no-pager

# Errores comunes:
# 1. "No se pudieron descifrar las credenciales" → regenerar secrets
# 2. "Connection refused" a MySQL → verificar VM-DB encendida y firewall
# 3. "Port already in use" → otro proceso usa el 8081
lsof -i :8081
```

### Regenerar credenciales cifradas (en cualquier VM de app)

```bash
systemctl stop ameribank
rm -rf /etc/ameribank/secrets/

# Volver a correr el deploy (el build ya existe, usa --skip-build):
sudo ./deploy_vm_app.sh \
    --role    principal \       # o backup
    --db-host 192.168.100.20 \
    --db-pass 'TuPasswordSeguro' \
    --skip-build
```
