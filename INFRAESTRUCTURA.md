# Ameribank - Infraestructura de Alta Disponibilidad con DDoS Demo

> Documentacion de la arquitectura de infraestructura para el DemoDay Cybersecurity Amerike 2025.
> Este documento cubre el despliegue del core bancario en contenedores LXC con failover automatico,
> simulacion de ataque DDoS con proteccion IPS (Snort) y centralizacion de logs con Splunk.

---

## Tabla de Contenidos
- [Arquitectura General](#arquitectura-general)
- [Diseno de Red - VLANs](#diseno-de-red---vlans)
- [Inventario de LXCs](#inventario-de-lxcs)
- [LXC 3 - Base de Datos MySQL](#lxc-3---base-de-datos-mysql)
- [LXC 1 y 2 - Spring Boot](#lxc-1-y-2---spring-boot)
- [Nginx - Reverse Proxy con Failover](#nginx---reverse-proxy-con-failover)
- [Snort - IPS para el Backup](#snort---ips-para-el-backup)
- [LXC 4 - Splunk (SIEM)](#lxc-4---splunk-siem)
- [NFS - Centralizacion de Logs](#nfs---centralizacion-de-logs)
- [Flujo de la Demo DDoS](#flujo-de-la-demo-ddos)
- [Cambios realizados al codigo](#cambios-realizados-al-codigo)
- [Datos de prueba](#datos-de-prueba)
- [Troubleshooting](#troubleshooting)

---

## Arquitectura General

```
    INTERNET
       │
       │  DDoS + Trafico legitimo
       │
       ▼
┌─────────────────────────────────────────────────────────────────────┐
│ VLAN CORE (40.0.4.0/24)                                            │
│                                                                     │
│   ┌──────────────┐                                                  │
│   │    Nginx     │                                                  │
│   │ Reverse Proxy│                                                  │
│   └──┬───────┬───┘                                                  │
│      │       │                                                      │
│      │       │  (failover)                                          │
│      │       │                                                      │
│ ┌────▼────┐  │  ┌─────────┐    ┌──────────┐                        │
│ │  LXC 1  │  │  │  Snort  │    │  LXC 3   │                        │
│ │Principal│  └─►│  (IPS)  │    │  MySQL   │                        │
│ │         │     └────┬────┘    │40.0.4.12 │                        │
│ └─────────┘          │         └─────┬────┘                        │
│                ┌─────▼──────┐        │                              │
│                │   LXC 2    │        │                              │
│                │  Backup    ├────────┘                              │
│                └────────────┘                                       │
│                                                                     │
│        Todos los servicios envian logs via NFS ──────────┐          │
│                                                          │          │
└──────────────────────────────────────────────────────────┼──────────┘
                                                           │
                                                    ┌──────▼──────┐
                                                    │   LXC 4     │
                                                    │   Splunk    │
                                              ┌─────┤  (SIEM)     │
                                              │     │             │
                                              │     │ eth1: CORE  │
                                              │     │ eth0: MGMT  │
                                              │     └─────────────┘
                                              │
┌─────────────────────────────────────────────┼───────────────────────┐
│ VLAN MGMT (40.0.5.0/24)                    │                        │
│                                              │                        │
│   Dashboard Splunk ◄─────────────────────────┘                        │
│   http://40.0.5.X:8000                                               │
│                                                                      │
│   Solo accesible desde esta VLAN                                     │
│   NO accesible desde Internet ni desde VLAN CORE                     │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

**Concepto de la demo:**
1. El DDoS llega desde Internet, pasa por Nginx hacia la VLAN CORE
2. El sitio principal (LXC 1) no tiene IPS — cae
3. Nginx detecta la caida y hace failover al backup (LXC 2), protegido por Snort
4. Snort filtra el trafico malicioso — el backup sobrevive
5. Todos los logs se centralizan via NFS en Splunk (LXC 4)
6. Splunk vive en la VLAN MGMT — inaccesible desde la red atacada

---

## Diseno de Red - VLANs

### Topologia de red

```
                    INTERNET
                        │
                   ┌────▼────┐
                   │ Router/ │
                   │ Firewall│
                   └──┬───┬──┘
                      │   │
           ┌──────────┘   └──────────┐
           │                         │
    ┌──────▼──────┐          ┌───────▼─────┐
    │  VLAN CORE  │          │  VLAN MGMT  │
    │ 40.0.4.0/24 │          │ 40.0.5.0/24 │
    │             │          │             │
    │ - Nginx     │          │ - Splunk    │
    │ - LXC 1     │          │   Dashboard │
    │ - LXC 2     │          │             │
    │ - LXC 3     │          │ Sin acceso  │
    │ - Snort     │          │ a Internet  │
    │ - LXC 4     │          │             │
    │   (eth1)    │          │   LXC 4     │
    │             │          │   (eth0)    │
    └─────────────┘          └─────────────┘
```

### VLAN CORE (40.0.4.0/24)

Red de produccion donde vive todo el core bancario. Es la red expuesta a Internet a traves de Nginx.

| Servicio | IP | Puerto | Funcion |
|----------|----|--------|---------|
| Nginx | 40.0.4.1 | 80 | Reverse proxy, punto de entrada |
| LXC 1 - Principal | 40.0.4.10 | 8081 | Core bancario principal |
| LXC 2 - Backup | 40.0.4.11 | 8081 | Core bancario redundante |
| LXC 3 - MySQL | 40.0.4.12 | 3306 | Base de datos |
| Snort (IPS) | 40.0.4.13 | inline | Filtra trafico al backup |
| LXC 4 - Splunk (eth1) | 40.0.4.14 | NFS | Recibe logs via NFS |

### VLAN MGMT (40.0.5.0/24)

Red de gestion/monitoreo. No tiene acceso a Internet. Solo sirve para consultar dashboards y administrar.

| Servicio | IP | Puerto | Funcion |
|----------|----|--------|---------|
| LXC 4 - Splunk (eth0) | 40.0.5.10 | 8000 | Dashboard web de Splunk |

### Por que dos VLANs

- Si el DDoS compromete la VLAN CORE, **Splunk sigue intacto** en la VLAN MGMT
- Los logs se siguen recolectando porque NFS va por la interfaz CORE (eth1)
- Pero el dashboard de Splunk **solo es accesible** desde la VLAN MGMT (eth0)
- Un atacante en la VLAN CORE no puede ver, modificar ni borrar los logs del SIEM

---

## Inventario de LXCs

| LXC | Funcion | VLAN | IP | OS | Specs Minimos | Puertos |
|-----|---------|------|----|----|---------------|---------|
| 1 | Core principal | CORE | 40.0.4.10 | Rocky Linux | 1GB RAM, 1 core | 8081 |
| 2 | Core backup | CORE | 40.0.4.11 | Rocky Linux | 1GB RAM, 1 core | 8081 |
| 3 | MySQL | CORE | 40.0.4.12 | Rocky Linux | 1-2GB RAM, 1 core | 3306 |
| 4 | Splunk (SIEM) | CORE + MGMT | 40.0.4.14 / 40.0.5.10 | Rocky Linux | 2-4GB RAM, 2 cores | 8000 (MGMT), NFS (CORE) |
| - | Nginx | CORE | 40.0.4.1 | Rocky Linux | 512MB RAM, 1 core | 80 |
| - | Snort (IPS) | CORE | 40.0.4.13 | Rocky Linux | 1GB RAM, 1 core | inline |

---

## LXC 3 - Base de Datos MySQL

### Instalacion

```bash
dnf install mysql-server -y
systemctl start mysqld
systemctl enable mysqld
mysql_secure_installation
```

### Configurar conexiones remotas

Editar `/etc/my.cnf.d/mysql-server.cnf`:

```ini
[mysqld]
bind-address = 0.0.0.0
event_scheduler = ON
general_log = 1
general_log_file = /mnt/splunk-logs/mysql-general.log
slow_query_log = 1
slow_query_log_file = /mnt/splunk-logs/mysql-slow.log
```

```bash
systemctl restart mysqld
```

### Firewall

```bash
firewall-cmd --permanent --add-port=3306/tcp
firewall-cmd --reload
```

### Crear la base de datos

```bash
mysql -u root -p < ameribank_full_db.sql
```

### Crear usuario remoto para los LXC

```sql
CREATE USER 'ameribank'@'%' IDENTIFIED BY 'TuPasswordSeguro';
GRANT ALL PRIVILEGES ON Ameribank.* TO 'ameribank'@'%';
FLUSH PRIVILEGES;
```

### Verificar

```bash
# Desde el LXC de MySQL
mysql -u ameribank -p -e "USE Ameribank; SHOW TABLES;"

# Desde otro LXC (para probar conectividad)
mysql -h 40.0.4.12 -u ameribank -p -e "SELECT 1;"
```

---

## LXC 1 y 2 - Spring Boot

Los dos LXC corren exactamente el mismo codigo. Ambos apuntan a la DB en 40.0.4.12.

### Instalacion

```bash
dnf install java-17-openjdk java-17-openjdk-devel git -y
```

### Clonar y compilar

```bash
cd /opt
git clone https://github.com/DaFrik19/DDNOV2025.git
cd DDNOV2025
chmod +x mvnw
./mvnw clean package -DskipTests
```

### Primera ejecucion (configurar credenciales)

La primera vez se debe ejecutar manualmente porque pide las credenciales de la DB de forma interactiva:

```bash
java -jar target/Ameribank-0.0.1-SNAPSHOT.jar
```

El sistema pedira:

```
Creando archivos de cifrado.
LLaves generadas...
Cifrando credenciales.
Nombre de la Base de Datos: Ameribank
Nombre de usuario: ameribank
Password: TuPasswordSeguro
```

Las credenciales se cifran con RSA 2048-bit y se guardan en `~/.config/ameribank/secrets/`.
Solo se pide una vez. Las siguientes ejecuciones leen las credenciales cifradas automaticamente.

Verificar que arranco:

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

Detener con `Ctrl+C`.

### Configurar como servicio systemd

```bash
cat > /etc/systemd/system/ameribank.service << 'EOF'
[Unit]
Description=Ameribank Spring Boot
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/DDNOV2025
ExecStart=/usr/bin/java -jar /opt/DDNOV2025/target/Ameribank-0.0.1-SNAPSHOT.jar
StandardOutput=append:/mnt/splunk-logs/ameribank.log
StandardError=append:/mnt/splunk-logs/ameribank.log
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable ameribank
systemctl start ameribank
```

### Verificar el servicio

```bash
systemctl status ameribank
curl http://localhost:8081/actuator/health
```

---

## Nginx - Reverse Proxy con Failover

### Configuracion del proxy

```nginx
upstream ameribank {
    server 40.0.4.10:8081 max_fails=3 fail_timeout=10s;
    server 40.0.4.13:8081 backup;
}

server {
    listen 80;
    server_name ameribank.local;

    access_log /mnt/splunk-logs/nginx-access.log;
    error_log /mnt/splunk-logs/nginx-error.log;

    # Bloquear acceso externo al health check
    location /actuator {
        return 404;
    }

    location / {
        proxy_pass http://ameribank;
        proxy_next_upstream error timeout http_502 http_503;
        proxy_connect_timeout 3s;
        proxy_read_timeout 5s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

**Funcionamiento del failover:**
- Nginx envia trafico al LXC 1 (40.0.4.10 - principal)
- Si el principal falla 3 veces en 10 segundos, redirige al backup via Snort (40.0.4.13)
- El endpoint `/actuator/health` queda bloqueado para usuarios externos (devuelve 404)
- El health check interno de Nginx va directo a la IP del backend, no pasa por sus location blocks

### Con rate limiting (proteccion basica en Nginx)

```nginx
limit_req_zone $binary_remote_addr zone=antiddos:10m rate=10r/s;
limit_conn_zone $binary_remote_addr zone=connlimit:10m;

upstream ameribank {
    server 40.0.4.10:8081 max_fails=3 fail_timeout=10s;
    server 40.0.4.13:8081 backup;
}

server {
    listen 80;
    server_name ameribank.local;

    access_log /mnt/splunk-logs/nginx-access.log;
    error_log /mnt/splunk-logs/nginx-error.log;

    limit_conn connlimit 20;
    limit_req zone=antiddos burst=20 nodelay;

    client_body_timeout 5s;
    client_header_timeout 5s;
    keepalive_timeout 10s;

    location /actuator {
        return 404;
    }

    location / {
        proxy_pass http://ameribank;
        proxy_next_upstream error timeout http_502 http_503;
        proxy_connect_timeout 3s;
        proxy_read_timeout 5s;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## Snort - IPS para el Backup

Snort se coloca **entre** Nginx y el LXC 2 (backup). Todo el trafico hacia el backup pasa primero por Snort, que filtra paquetes maliciosos.

```
Nginx (40.0.4.1) → Snort (40.0.4.13 - IPS inline) → LXC 2 (40.0.4.11 - backup)
```

### Reglas de deteccion DDoS

Archivo `/etc/snort/rules/local.rules`:

```
# Detectar SYN flood
alert tcp any any -> $HOME_NET 8081 (msg:"SYN Flood detectado"; \
    flags:S; threshold:type both, track by_src, count 100, seconds 10; \
    sid:1000001; rev:1;)

# Detectar HTTP flood
alert tcp any any -> $HOME_NET 8081 (msg:"HTTP Flood detectado"; \
    content:"GET"; http_method; threshold:type both, track by_src, \
    count 50, seconds 5; sid:1000002; rev:1;)

# DROP en modo IPS - bloquear DDoS
drop tcp any any -> $HOME_NET 8081 (msg:"DDoS bloqueado por IPS"; \
    flags:S; threshold:type both, track by_src, count 100, seconds 10; \
    sid:1000003; rev:1;)
```

### Ejecutar Snort en modo IPS (inline)

```bash
# Redirigir trafico por NFQueue
iptables -I FORWARD -j NFQUEUE --queue-num 0

# Iniciar Snort con logs al NFS
snort -Q --daq nfq --daq-var queue=0 -c /etc/snort/snort.conf -l /mnt/splunk-logs/
```

---

## LXC 4 - Splunk (SIEM)

### Interfaces de red

El LXC de Splunk tiene **dos interfaces**, una en cada VLAN:

| Interfaz | VLAN | IP | Funcion |
|----------|------|----|---------|
| eth0 | MGMT (40.0.5.0/24) | 40.0.5.10 | Dashboard web (puerto 8000) |
| eth1 | CORE (40.0.4.0/24) | 40.0.4.14 | Recibir logs via NFS |

### Instalacion de Splunk

```bash
# Descargar Splunk (requiere cuenta en splunk.com)
rpm -i splunk-<version>.rpm

# Iniciar y habilitar
/opt/splunk/bin/splunk start --accept-license
/opt/splunk/bin/splunk enable boot-start
```

### Configurar inputs de logs

Archivo `/opt/splunk/etc/system/local/inputs.conf`:

```ini
[monitor:///splunk-ingesta/nginx/nginx-access.log]
sourcetype = nginx:access
index = ameribank
host = nginx-proxy

[monitor:///splunk-ingesta/nginx/nginx-error.log]
sourcetype = nginx:error
index = ameribank
host = nginx-proxy

[monitor:///splunk-ingesta/snort/alert]
sourcetype = snort:alert
index = ameribank
host = snort-ips

[monitor:///splunk-ingesta/core-principal/ameribank.log]
sourcetype = spring:boot
index = ameribank
host = core-principal

[monitor:///splunk-ingesta/core-backup/ameribank.log]
sourcetype = spring:boot
index = ameribank
host = core-backup

[monitor:///splunk-ingesta/mysql/mysql-general.log]
sourcetype = mysql:general
index = ameribank
host = mysql-db

[monitor:///splunk-ingesta/mysql/mysql-slow.log]
sourcetype = mysql:slow
index = ameribank
host = mysql-db
```

### Crear el index

```bash
/opt/splunk/bin/splunk add index ameribank
/opt/splunk/bin/splunk restart
```

### Firewall del LXC de Splunk

```bash
# eth0 (VLAN MGMT) - Dashboard de Splunk
firewall-cmd --zone=public --add-interface=eth0 --permanent
firewall-cmd --zone=public --add-port=8000/tcp --permanent

# eth1 (VLAN CORE) - Solo NFS, nada mas
firewall-cmd --zone=internal --add-interface=eth1 --permanent
firewall-cmd --zone=internal --add-service=nfs --permanent
firewall-cmd --zone=internal --add-service=mountd --permanent
firewall-cmd --zone=internal --add-service=rpc-bind --permanent

# Bloquear todo lo demas en eth1
firewall-cmd --zone=internal --set-target=DROP --permanent

firewall-cmd --reload
```

### Acceder al dashboard

Desde cualquier maquina en la VLAN MGMT:

```
http://40.0.5.10:8000
```

**NO es accesible desde la VLAN CORE ni desde Internet.**

---

## NFS - Centralizacion de Logs

### Servidor NFS (en LXC 4 - Splunk)

```bash
dnf install nfs-utils -y
systemctl enable --now nfs-server

# Crear directorios por servicio
mkdir -p /splunk-ingesta/{nginx,snort,core-principal,core-backup,mysql}

# Configurar exports (solo para la VLAN CORE)
cat > /etc/exports << 'EOF'
/splunk-ingesta/nginx           40.0.4.0/24(rw,sync,no_subtree_check)
/splunk-ingesta/snort           40.0.4.0/24(rw,sync,no_subtree_check)
/splunk-ingesta/core-principal  40.0.4.0/24(rw,sync,no_subtree_check)
/splunk-ingesta/core-backup     40.0.4.0/24(rw,sync,no_subtree_check)
/splunk-ingesta/mysql           40.0.4.0/24(rw,sync,no_subtree_check)
EOF

exportfs -ra
systemctl restart nfs-server
```

### Cliente NFS (en cada LXC que genera logs)

```bash
dnf install nfs-utils -y
mkdir -p /mnt/splunk-logs
```

Montar el directorio correspondiente segun el servicio:

**LXC 1 (Core principal):**
```bash
echo "40.0.4.14:/splunk-ingesta/core-principal /mnt/splunk-logs nfs defaults 0 0" >> /etc/fstab
mount -a
```

**LXC 2 (Core backup):**
```bash
echo "40.0.4.14:/splunk-ingesta/core-backup /mnt/splunk-logs nfs defaults 0 0" >> /etc/fstab
mount -a
```

**LXC 3 (MySQL):**
```bash
echo "40.0.4.14:/splunk-ingesta/mysql /mnt/splunk-logs nfs defaults 0 0" >> /etc/fstab
mount -a
```

**Nginx:**
```bash
echo "40.0.4.14:/splunk-ingesta/nginx /mnt/splunk-logs nfs defaults 0 0" >> /etc/fstab
mount -a
```

**Snort:**
```bash
echo "40.0.4.14:/splunk-ingesta/snort /mnt/splunk-logs nfs defaults 0 0" >> /etc/fstab
mount -a
```

### Verificar NFS

```bash
# Desde cualquier cliente
df -h | grep splunk
# Debe mostrar el mount a 40.0.4.14:/splunk-ingesta/...

# Probar escritura
echo "test" > /mnt/splunk-logs/test.txt

# Desde Splunk verificar que llego
cat /splunk-ingesta/<servicio>/test.txt
```

### Flujo de logs

```
LXC 1 (Spring Boot) ──► /mnt/splunk-logs/ameribank.log ──► NFS ──► /splunk-ingesta/core-principal/
LXC 2 (Spring Boot) ──► /mnt/splunk-logs/ameribank.log ──► NFS ──► /splunk-ingesta/core-backup/
LXC 3 (MySQL)       ──► /mnt/splunk-logs/mysql-*.log   ──► NFS ──► /splunk-ingesta/mysql/
Nginx                ──► /mnt/splunk-logs/nginx-*.log   ──► NFS ──► /splunk-ingesta/nginx/
Snort                ──► /mnt/splunk-logs/alert          ──► NFS ──► /splunk-ingesta/snort/
                                                                          │
                                                                          ▼
                                                                    Splunk indexa
                                                                    todo en el
                                                                    index "ameribank"
                                                                          │
                                                                          ▼
                                                                  Dashboard en
                                                                  40.0.5.10:8000
                                                                  (VLAN MGMT)
```

---

## Flujo de la Demo DDoS

### Guion paso a paso

| Paso | Accion | Que se ve | Resultado |
|------|--------|-----------|-----------|
| 1 | Abrir el sitio en navegador | Ameribank funciona normal | Todo OK |
| 2 | Hacer login, operar cuentas | Transacciones exitosas | Demostrar funcionalidad |
| 3 | Abrir Splunk en otra pantalla | Dashboard con logs normales | Monitoreo activo |
| 4 | Lanzar DDoS contra Nginx | Trafico masivo al proxy | Comienza el ataque |
| 5 | El principal se satura | LXC 1 deja de responder | Sitio principal cae |
| 6 | Splunk muestra el spike | Graficas de trafico disparadas | Evidencia en tiempo real |
| 7 | Nginx detecta la caida | Failover al path con Snort | Automatico en ~10s |
| 8 | Snort filtra trafico malicioso | Logs muestran paquetes bloqueados | IPS en accion |
| 9 | Abrir sitio de nuevo | Ameribank sigue funcionando | Backup responde |
| 10 | Operar cuentas en backup | Transacciones exitosas | Misma DB, mismos datos |
| 11 | Mostrar Splunk: logs de Snort | Paquetes DDoS bloqueados | Evidencia del IPS |
| 12 | Mostrar que Splunk sobrevivio | Dashboard intacto en VLAN MGMT | SIEM protegido |

### Herramientas de ataque (entorno controlado)

- `hping3` - SYN flood
- `slowloris` - HTTP slow attack
- Scripts con `ab` (Apache Bench) o `wrk` - HTTP flood

### Puntos clave de la demo

- **Sin IPS:** El sitio cae ante el DDoS
- **Con IPS (Snort):** El sitio sobrevive el mismo ataque
- **La DB no se ve afectada:** Esta aislada en su propio LXC
- **Failover automatico:** Nginx redirige sin intervencion manual
- **Logs centralizados:** Splunk captura toda la evidencia del ataque
- **SIEM inaccesible:** La VLAN MGMT protege los logs del atacante

---

## Cambios realizados al codigo

### 1. `src/main/java/org/amerike/ameribank/config/security.java` (linea 71)

La URL de conexion a MySQL se cambio de `localhost` a la IP fija del LXC de la base de datos:

```java
// Antes
String datos = String.format("\"jdbc:mysql://localhost:3306/%s\"|\"%s\"|\"%s\"", ...);

// Despues
String datos = String.format("\"jdbc:mysql://40.0.4.12:3306/%s\"|\"%s\"|\"%s\"", ...);
```

### 2. `pom.xml`

Se agrego la dependencia de Spring Boot Actuator para exponer el endpoint `/actuator/health`
que usa Nginx para verificar si el backend esta vivo:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

### 3. `src/main/resources/application.properties`

Se habilito el endpoint de health check:

```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
```

### 4. `ameribank_full_db.sql` (archivo nuevo)

Script SQL completo para inicializar la base de datos con:
- 8 tablas
- 19 stored procedures
- 1 evento programado (expiracion 2FA)
- Datos de prueba

---

## Datos de prueba

### Usuarios de login

| Usuario | Password | Rol | Flujo de login |
|---------|----------|-----|----------------|
| admin | admin123 | Administrador | Directo a panel admin |
| cliente1 | cliente123 | Cliente | Requiere 2FA |
| cliente2 | cliente456 | Cliente | Requiere 2FA |

### Clientes registrados

| Numero | Nombre | Ciudad | Estatus |
|--------|--------|--------|---------|
| CLI-001 | Juan Garcia Lopez | CDMX | ACTIVO |
| CLI-002 | Maria Hernandez Martinez | Guadalajara | ACTIVO |
| CLI-003 | Carlos Ramirez Soto | Monterrey | ACTIVO |

### Cuentas bancarias

| Numero Cuenta | Tipo | Saldo | Cliente |
|---------------|------|-------|---------|
| 1000000001 | AHORRO | $50,000.00 | Juan Garcia |
| 1000000002 | CHEQUES | $120,000.00 | Juan Garcia |
| 1000000003 | AHORRO | $75,000.00 | Maria Hernandez |
| 1000000004 | AHORRO | $30,000.00 | Carlos Ramirez |

### Tarjetas de credito

| Numero Tarjeta | Limite | Saldo Usado | Cliente |
|----------------|--------|-------------|---------|
| 4000000000000001 | $50,000 | $12,000 | Juan Garcia |
| 4000000000000002 | $100,000 | $85,000 (alerta 80%+) | Maria Hernandez |
| 4000000000000003 | $30,000 | $5,000 | Carlos Ramirez |

---

## Troubleshooting

### Spring Boot no conecta a MySQL

```bash
# Verificar conectividad desde el LXC de Spring Boot
mysql -h 40.0.4.12 -u ameribank -p -e "SELECT 1;"

# Si falla, verificar en el LXC de MySQL:
# 1. bind-address = 0.0.0.0 en my.cnf
# 2. Firewall abierto en puerto 3306
# 3. Usuario creado con '%' (no solo localhost)
```

### Regenerar credenciales cifradas

Si las credenciales se configuraron mal:

```bash
rm -rf ~/.config/ameribank/secrets/
java -jar target/Ameribank-0.0.1-SNAPSHOT.jar
# Volvera a pedir las credenciales
```

### El health check no responde

```bash
curl -v http://localhost:8081/actuator/health

# Si devuelve 404, verificar application.properties:
# management.endpoints.web.exposure.include=health
```

### El event scheduler de MySQL no esta activo

```sql
SHOW VARIABLES LIKE 'event_scheduler';
-- Si esta OFF:
SET GLOBAL event_scheduler = ON;

-- Para que persista, agregar en my.cnf:
-- [mysqld]
-- event_scheduler = ON
```

### Nginx no hace failover

```bash
# Verificar que Nginx puede alcanzar los backends
curl http://40.0.4.10:8081/actuator/health
curl http://40.0.4.11:8081/actuator/health

# Ver logs de Nginx
tail -f /mnt/splunk-logs/nginx-error.log
```

### NFS no monta

```bash
# Verificar que el servidor NFS esta corriendo
showmount -e 40.0.4.14

# Si no muestra exports:
# 1. Verificar /etc/exports en el LXC de Splunk
# 2. exportfs -ra
# 3. systemctl restart nfs-server

# Si monta pero no escribe:
# Verificar permisos en /splunk-ingesta/<directorio>
chmod 777 /splunk-ingesta/*
```

### Splunk no indexa los logs

```bash
# Verificar que los archivos existen
ls -la /splunk-ingesta/*/

# Verificar inputs
/opt/splunk/bin/splunk list monitor

# Revisar logs de Splunk
tail -f /opt/splunk/var/log/splunk/splunkd.log
```

### Splunk no es accesible

```bash
# Verificar que escucha en la interfaz correcta
ss -tlnp | grep 8000

# Debe estar en 40.0.5.10:8000 (VLAN MGMT), no en 0.0.0.0
# Configurar en /opt/splunk/etc/system/local/web.conf:
# [settings]
# server.socket_host = 40.0.5.10
```
