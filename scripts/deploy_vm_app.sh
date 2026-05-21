#!/usr/bin/env bash
# =====================================================================
# Ameribank - Deploy del core bancario en VM dedicada (principal o backup)
#
# Arquitectura objetivo (3 VMs):
#   VM-Principal : --role principal — core bancario sin IPS
#   VM-Backup    : --role backup    — core bancario de respaldo (Snort/IPS delante)
#   VM-DB        : MySQL — su IP se pasa con --db-host
#
# Este script se ejecuta tanto en VM-Principal como en VM-Backup.
# La diferencia entre roles es el nombre del servicio systemd y los logs.
#
# Diferencias vs deploy_ameribank.sh (LXC):
#   - --role y --db-host son obligatorios
#   - Logs locales en /var/log/ameribank/<role>/ (sin NFS)
#   - Crea usuario de sistema 'ameribank' (no corre como root)
#   - Pasa -Ddb.host al JVM en credenciales y en systemd
#   - Sin dependencia de VLAN LXC
#
# Uso — VM-Principal:
#   sudo ./deploy_vm_app.sh --role principal --db-host 192.168.1.30
#
# Uso — VM-Backup:
#   sudo ./deploy_vm_app.sh --role backup --db-host 192.168.1.30
#
# Con credenciales completas (no-interactivo):
#   sudo ./deploy_vm_app.sh --role principal --db-host 192.168.1.30 \
#       --db-pass 'SuperSecret' --db-name Ameribank
#
# Reejecutable: si las llaves ya existen, salta el cifrado.
#               Si el repo ya está, hace git pull.
# =====================================================================

set -euo pipefail

# ---------- Defaults ----------
ROLE=""              # Requerido: 'principal' o 'backup'
REPO_URL="https://github.com/myn4rd/DD2026.git"
APP_DIR="/opt/DD2026"
DB_HOST=""           # Requerido: IP de la VM de MySQL (VM-DB)
DB_PORT="3306"
DB_NAME="Ameribank"
DB_USER="ameribank"
DB_PASS=""
APP_PORT="8081"
SVC_USER="ameribank"
SVC_HOME="/etc/ameribank"    # home fijo para usuario de sistema (sin home real)
LOG_DIR=""                   # Se establece tras parsear --role (ver abajo)

# Rango de IPs con acceso permitido al puerto de la app.
# En VMs con NAT/bridge ajusta a la subred del hypervisor (ej. 192.168.1.0/24).
# Usa "0.0.0.0/0" solo si todas las interfaces ya están protegidas por firewall externo.
ALLOWED_CIDR=""      # Si queda vacío, abre el puerto a 0.0.0.0/0 (ver configure_firewall)

SKIP_FIREWALL=false
SKIP_BUILD=false

# ---------- Helpers ----------
log()  { printf '\033[1;34m[INFO]\033[0m  %s\n' "$*"; }
warn() { printf '\033[1;33m[WARN]\033[0m  %s\n' "$*"; }
err()  { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; }
die()  { err "$*"; exit 1; }

find_jar() {
    local matches=( "$APP_DIR"/target/Ameribank-*.jar )
    [[ -f "${matches[0]:-}" ]] && printf '%s\n' "${matches[0]}"
}

require_root() { [[ $EUID -eq 0 ]] || die "Ejecuta con sudo."; }

usage() {
    sed -n '2,22p' "$0"
    exit 0
}

# ---------- Parse args ----------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --role)           ROLE="$2";        shift 2 ;;
        --db-host)        DB_HOST="$2";     shift 2 ;;
        --db-port)        DB_PORT="$2";     shift 2 ;;
        --db-name)        DB_NAME="$2";     shift 2 ;;
        --db-user)        DB_USER="$2";     shift 2 ;;
        --db-pass)        DB_PASS="$2";     shift 2 ;;
        --repo-url)       REPO_URL="$2";    shift 2 ;;
        --app-dir)        APP_DIR="$2";     shift 2 ;;
        --allowed-cidr)   ALLOWED_CIDR="$2"; shift 2 ;;
        --skip-firewall)  SKIP_FIREWALL=true; shift ;;
        --skip-build)     SKIP_BUILD=true;  shift ;;
        -h|--help)        usage ;;
        *) die "Argumento desconocido: $1" ;;
    esac
done

require_root

[[ "$ROLE" == "principal" || "$ROLE" == "backup" ]] \
    || die "--role es obligatorio y debe ser 'principal' o 'backup' (recibido: '${ROLE:-vacío}')"

[[ -n "$DB_HOST" ]] \
    || die "--db-host es obligatorio: IP de la VM-DB con MySQL (ej: --db-host 192.168.1.30)"

# Directorio de logs separado por rol para distinguir en la misma VM de monitoreo
LOG_DIR="/var/log/ameribank/${ROLE}"

# ---------- Detectar OS ----------
detect_os() {
    [[ -f /etc/os-release ]] || die "No se pudo leer /etc/os-release"
    # shellcheck disable=SC1091
    . /etc/os-release
    local id="${ID:-unknown}" like="${ID_LIKE:-}"
    case "$id" in
        rocky|rhel|almalinux|centos|fedora) OS_FAMILY="rhel" ;;
        ubuntu|debian)                       OS_FAMILY="debian" ;;
        *)
            if [[ "$like" == *"rhel"* || "$like" == *"fedora"* ]]; then
                OS_FAMILY="rhel"
            elif [[ "$like" == *"debian"* ]]; then
                OS_FAMILY="debian"
            else
                die "OS no soportado: ID=$id ID_LIKE=$like"
            fi
            ;;
    esac
    log "Detectado: ${PRETTY_NAME:-$id} (familia=$OS_FAMILY)"
}

# ---------- Instalar paquetes ----------
install_pkgs() {
    case "$OS_FAMILY" in
        rhel)
            log "Instalando java-17-openjdk, git, curl..."
            dnf install -y java-17-openjdk java-17-openjdk-devel git curl firewalld
            systemctl enable --now firewalld || warn "firewalld no arrancó"
            ;;
        debian)
            log "Instalando openjdk-17-jdk, git, curl..."
            export DEBIAN_FRONTEND=noninteractive
            apt-get update -q
            apt-get install -y openjdk-17-jdk git curl ufw
            ;;
    esac
    java -version 2>&1 | head -1 | sed 's/^/[INFO]  Java: /'
}

# ---------- Crear usuario de servicio ----------
create_svc_user() {
    if id "$SVC_USER" &>/dev/null; then
        log "Usuario '$SVC_USER' ya existe, continuando..."
        return
    fi
    log "Creando usuario de sistema '$SVC_USER'..."
    useradd --system --no-create-home --shell /sbin/nologin "$SVC_USER"
}

# ---------- Preparar directorio de logs ----------
setup_logs() {
    mkdir -p "$LOG_DIR"
    chown "$SVC_USER":"$SVC_USER" "$LOG_DIR"
    chmod 750 "$LOG_DIR"
    log "Logs locales en $LOG_DIR"
}

# ---------- Clonar / actualizar repo ----------
clone_repo() {
    if [[ -d "$APP_DIR/.git" ]]; then
        log "Repo ya existe en $APP_DIR, haciendo git pull..."
        git -C "$APP_DIR" fetch --all
        git -C "$APP_DIR" pull --ff-only || warn "git pull falló (¿cambios locales?)"
    else
        log "Clonando $REPO_URL en $APP_DIR"
        git clone "$REPO_URL" "$APP_DIR"
    fi
    chmod +x "$APP_DIR/mvnw"
    chown -R "$SVC_USER":"$SVC_USER" "$APP_DIR"
}

# ---------- Build ----------
build() {
    if $SKIP_BUILD; then warn "Saltando build (--skip-build)"; return; fi
    log "Compilando con mvnw (1-3 min la primera vez)..."
    pushd "$APP_DIR" >/dev/null
    # -Dmaven.repo.local evita que Maven intente escribir en ~/.m2/ de un usuario sin home real
    sudo -u "$SVC_USER" \
        MAVEN_OPTS="-Duser.home=${SVC_HOME}" \
        ./mvnw -B -DskipTests -Dmaven.repo.local="${APP_DIR}/.m2" clean package
    popd >/dev/null

    local jar
    jar=$(find_jar)
    [[ -n "$jar" ]] || die "No se encontró el .jar después del build"
    log "Build OK: $jar"
}

# ---------- Verificar conectividad a MySQL ----------
check_db_connectivity() {
    log "Verificando conectividad con MySQL en ${DB_HOST}:${DB_PORT}..."
    # Usa /dev/tcp de bash como check rápido sin instalar cliente MySQL
    if ! timeout 5 bash -c ">/dev/tcp/${DB_HOST}/${DB_PORT}" 2>/dev/null; then
        warn "No se pudo conectar a ${DB_HOST}:${DB_PORT}."
        warn "Asegúrate de que:"
        warn "  1. La VM de MySQL está encendida"
        warn "  2. MySQL escucha en bind-address = 0.0.0.0 (o en la IP de esta VM)"
        warn "  3. El firewall de la VM de MySQL permite el puerto ${DB_PORT} desde esta IP"
        warn "Continuando de todas formas..."
    else
        log "Conectividad a MySQL OK (${DB_HOST}:${DB_PORT})"
    fi
}

# ---------- Configurar credenciales cifradas ----------
setup_credentials() {
    # Ruta fija derivada de SVC_HOME para que coincida con -Duser.home en systemd.
    # Java construye: System.getProperty("user.home") + "/.config/ameribank/secrets"
    local secrets_dir="${SVC_HOME}/.config/ameribank/secrets"
    local enc_file="${secrets_dir}/accesodbjava.enc"

    mkdir -p "$secrets_dir"
    chown -R "$SVC_USER":"$SVC_USER" "$SVC_HOME"
    chmod 700 "$secrets_dir"

    if [[ -f "$enc_file" ]]; then
        log "Credenciales cifradas ya existen en $enc_file (skip)"
        return
    fi

    local jar
    jar=$(find_jar)
    [[ -n "$jar" ]] || die "No hay .jar para configurar credenciales"

    log "Configurando credenciales — db.host=${DB_HOST} db.port=${DB_PORT}"

    if [[ -z "$DB_PASS" ]]; then
        log "Modo interactivo: se pedirán las credenciales de BD."
        log "Cuando aparezca 'Cifrado finalizado', espera ~3s y presiona Ctrl+C."
        sudo -u "$SVC_USER" \
            java -Duser.home="$SVC_HOME" \
                 -Ddb.host="$DB_HOST" -Ddb.port="$DB_PORT" \
                 -jar "$jar" || true
    else
        log "Modo no-interactivo, credenciales desde flags..."
        local tmplog
        tmplog=$(mktemp)

        # Exportar variables para evitar problemas con caracteres especiales (comillas, espacios)
        # dentro del heredoc del subshell de sudo.
        export _AMB_DB_NAME="$DB_NAME" _AMB_DB_USER="$DB_USER" _AMB_DB_PASS="$DB_PASS" \
               _AMB_DB_HOST="$DB_HOST" _AMB_DB_PORT="$DB_PORT" \
               _AMB_JAR="$jar" _AMB_LOG="$tmplog" _AMB_HOME="$SVC_HOME" _AMB_DIR="$APP_DIR"

        sudo -u "$SVC_USER" bash -s <<'INNER_SCRIPT'
            cd "$_AMB_DIR"
            printf '%s\n%s\n%s\n' "$_AMB_DB_NAME" "$_AMB_DB_USER" "$_AMB_DB_PASS" \
                | java -Duser.home="$_AMB_HOME" \
                       -Ddb.host="$_AMB_DB_HOST" -Ddb.port="$_AMB_DB_PORT" \
                       -jar "$_AMB_JAR" >> "$_AMB_LOG" 2>&1 &
            echo $! > /tmp/ameribank-setup.pid
INNER_SCRIPT

        # Esperar a que el subshell escriba el PID (evita race condition)
        local wait_pid=0
        while [[ ! -s /tmp/ameribank-setup.pid && $wait_pid -lt 10 ]]; do
            sleep 0.5
            wait_pid=$(( wait_pid + 1 ))
        done
        [[ -s /tmp/ameribank-setup.pid ]] || die "No se pudo obtener el PID del proceso Java"

        local pid; pid=$(cat /tmp/ameribank-setup.pid)
        local waited=0
        while [[ ! -f "$enc_file" && $waited -lt 60 ]]; do
            sleep 1
            waited=$(( waited + 1 ))
        done
        kill "$pid" 2>/dev/null || true
        rm -f /tmp/ameribank-setup.pid

        # Limpiar variables exportadas temporalmente
        unset _AMB_DB_NAME _AMB_DB_USER _AMB_DB_PASS _AMB_DB_HOST _AMB_DB_PORT \
              _AMB_JAR _AMB_LOG _AMB_HOME _AMB_DIR

        if [[ -f "$enc_file" ]]; then
            log "Credenciales cifradas en $enc_file"
        else
            err "No se generó el archivo cifrado. Log:"
            cat "$tmplog" >&2
            rm -f "$tmplog"
            die "Falló el setup no-interactivo"
        fi
        rm -f "$tmplog"
    fi

    chmod 700 "$secrets_dir"
    chmod 600 "${secrets_dir}"/* || true
}

# ---------- systemd ----------
install_systemd() {
    local jar
    jar=$(find_jar)
    [[ -n "$jar" ]] || die "No hay .jar para el servicio systemd"

    local unit="/etc/systemd/system/ameribank.service"
    log "Escribiendo $unit"
    cat > "$unit" <<EOF
[Unit]
Description=Ameribank Spring Boot (${ROLE})
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=${SVC_USER}
WorkingDirectory=${APP_DIR}
ExecStart=/usr/bin/java \\
    -Duser.home=${SVC_HOME} \\
    -Ddb.host=${DB_HOST} \\
    -Ddb.port=${DB_PORT} \\
    -jar ${jar}
StandardOutput=append:${LOG_DIR}/ameribank.log
StandardError=append:${LOG_DIR}/ameribank.log
Restart=on-failure
RestartSec=5
LimitNOFILE=65536

# Hardening básico del proceso
NoNewPrivileges=yes
PrivateTmp=yes

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable ameribank
    systemctl restart ameribank
    log "Servicio ameribank (${ROLE}) iniciado"
}

# ---------- Firewall ----------
configure_firewall() {
    if $SKIP_FIREWALL; then warn "Saltando firewall (--skip-firewall)"; return; fi

    local from_desc
    if [[ -n "$ALLOWED_CIDR" ]]; then
        from_desc="desde ${ALLOWED_CIDR}"
    else
        from_desc="desde cualquier IP (considera limitar con --allowed-cidr)"
        warn "No se especificó --allowed-cidr. Abriendo ${APP_PORT}/tcp a 0.0.0.0/0"
    fi

    case "$OS_FAMILY" in
        rhel)
            if systemctl is-active --quiet firewalld; then
                log "Abriendo ${APP_PORT}/tcp ${from_desc} en firewalld"
                if [[ -n "$ALLOWED_CIDR" ]]; then
                    firewall-cmd --permanent --zone=public \
                        --add-rich-rule="rule family=ipv4 source address=${ALLOWED_CIDR} port port=${APP_PORT} protocol=tcp accept" \
                        || warn "Regla duplicada o error al agregar"
                else
                    firewall-cmd --permanent --zone=public --add-port="${APP_PORT}/tcp" \
                        || warn "Regla duplicada o error al agregar"
                fi
                firewall-cmd --reload
            else
                warn "firewalld inactivo, saltando"
            fi
            ;;
        debian)
            if command -v ufw >/dev/null 2>&1; then
                log "Abriendo ${APP_PORT}/tcp ${from_desc} en ufw"
                if [[ -n "$ALLOWED_CIDR" ]]; then
                    ufw allow from "${ALLOWED_CIDR}" to any port "${APP_PORT}" proto tcp || true
                else
                    ufw allow "${APP_PORT}/tcp" || true
                fi
            fi
            ;;
    esac
}

# ---------- Verificación ----------
verify() {
    log "Esperando que el servicio responda en /actuator/health..."
    local tries=0
    until curl -sf "http://localhost:${APP_PORT}/actuator/health" >/dev/null 2>&1; do
        tries=$(( tries + 1 ))
        if (( tries > 30 )); then
            err "Health check sin respuesta después de 30s. Revisa:"
            err "  journalctl -u ameribank -n 50"
            err "  tail -50 ${LOG_DIR}/ameribank.log"
            return 1
        fi
        sleep 1
    done
    log "Health OK: $(curl -s "http://localhost:${APP_PORT}/actuator/health")"
}

# ---------- Main ----------
main() {
    detect_os
    install_pkgs
    create_svc_user
    setup_logs
    clone_repo
    build
    check_db_connectivity
    setup_credentials
    install_systemd
    configure_firewall
    verify

    local my_ip
    my_ip=$(hostname -I | awk '{print $1}')

    cat <<EOF

==========================================================
  Deploy completado — rol: ${ROLE}  (arquitectura 3 VMs)
==========================================================
  Esta VM           : http://${my_ip}:${APP_PORT}
  Rol               : ${ROLE}
  VM-DB (MySQL)     : ${DB_HOST}:${DB_PORT}
  Servicio          : systemctl status ameribank
  Logs              : tail -f ${LOG_DIR}/ameribank.log
==========================================================

Verificar conectividad a VM-DB:
  mysql -h ${DB_HOST} -P ${DB_PORT} -u ${DB_USER} -p \\
      -e "USE ${DB_NAME}; SHOW TABLES;"

Arquitectura 3 VMs — estado del deploy:
  [ ] VM-DB        → install_mysql_vm.sh  (--principal-host ... --backup-host ...)
  [ ] VM-Principal → deploy_vm_app.sh --role principal --db-host <VM-DB-IP>
  [ ] VM-Backup    → deploy_vm_app.sh --role backup    --db-host <VM-DB-IP>

Si las credenciales cifradas son incorrectas, regenerar:
  systemctl stop ameribank
  rm -rf /etc/ameribank/secrets/
  sudo ./deploy_vm_app.sh --role ${ROLE} --db-host ${DB_HOST} [--db-pass '...']

EOF
}

main "$@"
