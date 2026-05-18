#!/usr/bin/env bash
# =====================================================================
# Ameribank - Deploy del aplicativo Spring Boot en LXC 1 (principal) o LXC 2 (backup)
#
# Qué hace:
#   1. Detecta Rocky/RHEL vs Ubuntu/Debian
#   2. Instala Java 17, git, nfs-utils
#   3. Monta /mnt/splunk-logs vía NFS (export depende del rol)
#   4. Clona/actualiza el repo y compila con mvnw
#   5. Configura credenciales cifradas RSA (interactivo o desde flags)
#   6. Instala servicio systemd con logs hacia NFS
#   7. Abre firewall puerto 8081 sólo desde la VLAN CORE
#   8. Verifica /actuator/health
#
# Uso:
#   sudo ./deploy_ameribank.sh --role principal
#   sudo ./deploy_ameribank.sh --role backup --db-pass 'PasswordDB' --db-name Ameribank
#
# Reejecutable: si las llaves ya existen, salta el cifrado.
#               Si el repo ya está, hace git pull.
# =====================================================================

set -euo pipefail

# ---------- Defaults ----------
ROLE=""
REPO_URL="https://github.com/myn4rd/DD2026.git"
APP_DIR="/opt/DD2026"
DB_NAME="Ameribank"
DB_USER="ameribank"
DB_PASS=""
NFS_SERVER="40.0.4.14"
ALLOWED_CIDR="40.0.4.0/24"
APP_PORT="8081"
SVC_USER="root"
SKIP_NFS=false
SKIP_FIREWALL=false
SKIP_BUILD=false

# ---------- Helpers ----------
log()  { printf '\033[1;34m[INFO]\033[0m  %s\n' "$*"; }
warn() { printf '\033[1;33m[WARN]\033[0m  %s\n' "$*"; }
err()  { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; }
die()  { err "$*"; exit 1; }

# Devuelve la ruta absoluta del jar generado por Maven, o cadena vacía
find_jar() {
    local matches=( "$APP_DIR"/target/Ameribank-*.jar )
    [[ -f "${matches[0]:-}" ]] && printf '%s\n' "${matches[0]}"
}

require_root() { [[ $EUID -eq 0 ]] || die "Ejecuta con sudo."; }

usage() {
    sed -n '2,20p' "$0"
    exit 0
}

# ---------- Parse args ----------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --role)          ROLE="$2"; shift 2 ;;
        --repo-url)      REPO_URL="$2"; shift 2 ;;
        --app-dir)       APP_DIR="$2"; shift 2 ;;
        --db-name)       DB_NAME="$2"; shift 2 ;;
        --db-user)       DB_USER="$2"; shift 2 ;;
        --db-pass)       DB_PASS="$2"; shift 2 ;;
        --nfs-server)    NFS_SERVER="$2"; shift 2 ;;
        --allowed-cidr)  ALLOWED_CIDR="$2"; shift 2 ;;
        --skip-nfs)      SKIP_NFS=true; shift ;;
        --skip-firewall) SKIP_FIREWALL=true; shift ;;
        --skip-build)    SKIP_BUILD=true; shift ;;
        -h|--help)       usage ;;
        *) die "Argumento desconocido: $1" ;;
    esac
done

require_root

[[ "$ROLE" == "principal" || "$ROLE" == "backup" ]] \
    || die "--role debe ser 'principal' o 'backup' (recibido: '${ROLE:-vacío}')"

case "$ROLE" in
    principal) NFS_EXPORT="/splunk-ingesta/core-principal" ;;
    backup)    NFS_EXPORT="/splunk-ingesta/core-backup"    ;;
esac

# ---------- Detect OS ----------
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

# ---------- Install packages ----------
install_pkgs() {
    case "$OS_FAMILY" in
        rhel)
            log "Instalando java-17-openjdk, git, nfs-utils, curl..."
            dnf install -y java-17-openjdk java-17-openjdk-devel git nfs-utils curl firewalld
            systemctl enable --now firewalld || warn "firewalld no arrancó"
            ;;
        debian)
            log "Instalando openjdk-17-jdk, git, nfs-common, curl..."
            export DEBIAN_FRONTEND=noninteractive
            apt-get update
            apt-get install -y openjdk-17-jdk git nfs-common curl ufw
            ;;
    esac
    java -version 2>&1 | head -1 | sed 's/^/[INFO]  Java: /'
}

# ---------- Mount NFS ----------
mount_nfs() {
    if $SKIP_NFS; then warn "Saltando NFS (--skip-nfs)"; mkdir -p /mnt/splunk-logs; return; fi

    local mnt="/mnt/splunk-logs"
    mkdir -p "$mnt"

    local fstab_line="${NFS_SERVER}:${NFS_EXPORT} ${mnt} nfs _netdev,nofail,defaults 0 0"
    if grep -qF "$mnt" /etc/fstab; then
        log "Entry de NFS ya existe en /etc/fstab, actualizándola..."
        sed -i.bak "\#${mnt}#d" /etc/fstab
    fi
    echo "$fstab_line" >> /etc/fstab

    log "Montando ${NFS_SERVER}:${NFS_EXPORT} en ${mnt}"
    if ! mount -a; then
        warn "NFS no montó automáticamente. Verifica que el servidor esté arriba."
        warn "El servicio puede arrancar igual; los logs se quedarán locales hasta que monte."
    fi
    df -h | grep "$mnt" || warn "NFS no montado (puede arrancar luego)"
}

# ---------- Clone / update repo ----------
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
}

# ---------- Build ----------
build() {
    if $SKIP_BUILD; then warn "Saltando build (--skip-build)"; return; fi
    log "Compilando con mvnw (esto tarda 1-3 min la primera vez)..."
    pushd "$APP_DIR" >/dev/null
    ./mvnw -B -DskipTests clean package
    popd >/dev/null

    local jar
    jar=$(find_jar)
    [[ -n "$jar" ]] || die "No se encontró el .jar después del build"
    log "Build OK: $jar"
}

# ---------- Configure credenciales cifradas ----------
setup_credentials() {
    local secrets_dir="/${SVC_USER}/.config/ameribank/secrets"
    [[ "$SVC_USER" == "root" ]] && secrets_dir="/root/.config/ameribank/secrets"
    local enc_file="${secrets_dir}/accesodbjava.enc"

    if [[ -f "$enc_file" ]]; then
        log "Credenciales cifradas ya existen en $enc_file (skip)"
        return
    fi

    local jar
    jar=$(find_jar)
    [[ -n "$jar" ]] || die "No hay .jar para configurar credenciales"

    if [[ -z "$DB_PASS" ]]; then
        # Modo interactivo: el usuario teclea las creds en este TTY
        log "Configurando credenciales (interactivo). Cuando aparezca 'Cifrado finalizado',"
        log "espera ~5s y presiona Ctrl+C para detener el arranque de Spring."
        cd "$APP_DIR"
        java -jar "$jar" || true
    else
        # Modo no-interactivo: piped stdin + polling del archivo cifrado + kill
        log "Configurando credenciales (no-interactivo, desde flags)..."
        local tmplog
        tmplog=$(mktemp)
        (
            cd "$APP_DIR"
            printf '%s\n%s\n%s\n' "$DB_NAME" "$DB_USER" "$DB_PASS" \
                | java -jar "$jar" > "$tmplog" 2>&1 &
            echo $! > /tmp/ameribank-setup.pid
        )
        local pid; pid=$(cat /tmp/ameribank-setup.pid)
        local waited=0
        while [[ ! -f "$enc_file" && $waited -lt 60 ]]; do
            sleep 1; ((waited++))
        done
        kill "$pid" 2>/dev/null || true
        wait "$pid" 2>/dev/null || true
        rm -f /tmp/ameribank-setup.pid

        if [[ -f "$enc_file" ]]; then
            log "Credenciales cifradas en $enc_file"
        else
            err "No se generó el archivo cifrado. Log del intento:"
            cat "$tmplog" >&2
            rm -f "$tmplog"
            die "Falló el setup no-interactivo"
        fi
        rm -f "$tmplog"
    fi

    chmod 700 "$secrets_dir"
    chmod 600 "${secrets_dir}"/* || true
    log "Permisos endurecidos en $secrets_dir"
}

# ---------- systemd ----------
install_systemd() {
    local jar
    jar=$(find_jar)
    [[ -n "$jar" ]] || die "No hay .jar para el servicio"

    local unit="/etc/systemd/system/ameribank.service"
    log "Escribiendo $unit"
    cat > "$unit" <<EOF
[Unit]
Description=Ameribank Spring Boot (${ROLE})
After=network-online.target remote-fs.target
Wants=network-online.target
RequiresMountsFor=/mnt/splunk-logs

[Service]
Type=simple
User=${SVC_USER}
WorkingDirectory=${APP_DIR}
ExecStart=/usr/bin/java -jar ${jar}
StandardOutput=append:/mnt/splunk-logs/ameribank.log
StandardError=append:/mnt/splunk-logs/ameribank.log
Restart=on-failure
RestartSec=5
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
EOF

    systemctl daemon-reload
    systemctl enable ameribank
    systemctl restart ameribank
    log "Servicio ameribank iniciado"
}

# ---------- Firewall ----------
configure_firewall() {
    if $SKIP_FIREWALL; then warn "Saltando firewall (--skip-firewall)"; return; fi
    case "$OS_FAMILY" in
        rhel)
            if systemctl is-active --quiet firewalld; then
                log "Abriendo ${APP_PORT}/tcp desde ${ALLOWED_CIDR} en firewalld"
                firewall-cmd --permanent --zone=public \
                    --add-rich-rule="rule family=ipv4 source address=${ALLOWED_CIDR} port port=${APP_PORT} protocol=tcp accept" \
                    || warn "regla duplicada o falló"
                firewall-cmd --reload
            else
                warn "firewalld inactivo, saltando"
            fi
            ;;
        debian)
            if command -v ufw >/dev/null 2>&1; then
                log "Abriendo ${APP_PORT}/tcp desde ${ALLOWED_CIDR} en ufw"
                ufw allow from "${ALLOWED_CIDR}" to any port "${APP_PORT}" proto tcp || true
            fi
            ;;
    esac
}

# ---------- Verificación ----------
verify() {
    log "Esperando que el servicio responda en /actuator/health..."
    local tries=0
    until curl -sf "http://localhost:${APP_PORT}/actuator/health" >/dev/null 2>&1; do
        ((tries++))
        if (( tries > 30 )); then
            err "El health check no respondió en 30s. Revisa logs:"
            err "  journalctl -u ameribank -n 50"
            err "  tail -50 /mnt/splunk-logs/ameribank.log"
            return 1
        fi
        sleep 1
    done
    log "Health OK: $(curl -s http://localhost:${APP_PORT}/actuator/health)"
}

# ---------- Main ----------
main() {
    detect_os
    install_pkgs
    mount_nfs
    clone_repo
    build
    setup_credentials
    install_systemd
    configure_firewall
    verify

    cat <<EOF

==========================================================
  Deploy completado — rol: ${ROLE}
==========================================================
  App     : http://$(hostname -I | awk '{print $1}'):${APP_PORT}
  Service : systemctl status ameribank
  Logs    : tail -f /mnt/splunk-logs/ameribank.log
  NFS     : ${NFS_SERVER}:${NFS_EXPORT}
==========================================================

Próximos pasos:
  - Asegúrate de que Nginx (40.0.4.1) apunte al upstream correcto:
      LXC 1 (principal) → 40.0.4.10:${APP_PORT}
      LXC 2 (backup)    → 40.0.4.11:${APP_PORT}  (vía Snort en 40.0.4.13)
  - Verifica conectividad a MySQL desde este LXC:
      mysql -h 40.0.4.12 -u ${DB_USER} -p -e "USE ${DB_NAME}; SHOW TABLES;"

EOF
}

main "$@"
