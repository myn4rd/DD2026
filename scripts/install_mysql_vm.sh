#!/usr/bin/env bash
# =====================================================================
# Ameribank - Instalador de MySQL para VM dedicada (VM-DB)
#
# Arquitectura objetivo (3 VMs):
#   VM-Principal : Core bancario principal  → --principal-host <IP>
#   VM-Backup    : Core bancario de respaldo → --backup-host <IP>
#   VM-DB        : Esta VM — MySQL solo acepta conexiones de las dos anteriores
#
# Diferencias vs install_mysql.sh (LXC):
#   - --principal-host / --backup-host reemplazan el CIDR genérico '%'
#     Los usuarios MySQL se crean solo para esas IPs exactas
#   - Sin NFS: logs locales en /var/log/mysql/
#   - Al menos uno de los dos hosts es obligatorio
#
# Uso básico (3 VMs completo):
#   sudo ./install_mysql_vm.sh \
#       --principal-host 192.168.1.10 \
#       --backup-host    192.168.1.11 \
#       --db-pass 'SuperSecret' --root-pass 'RootSecret'
#
# Solo principal (sin backup):
#   sudo ./install_mysql_vm.sh --principal-host 192.168.1.10
#
# NOTA: --principal-host y --backup-host deben ser IPs exactas (ej. 192.168.1.10).
#       MySQL NO acepta notación CIDR en CREATE USER — usa IPs individuales.
# =====================================================================

set -euo pipefail

# ---------- Defaults ----------
DB_NAME="Ameribank"
DB_USER="ameribank"
DB_PASS=""
ROOT_PASS=""
PRINCIPAL_HOST=""    # IP de VM-Principal (obligatorio si no se pasa --backup-host)
BACKUP_HOST=""       # IP de VM-Backup    (opcional, pero recomendado)
SQL_FILE=""          # Se auto-detecta si no se pasa
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---------- Helpers ----------
log()  { printf '\033[1;34m[INFO]\033[0m  %s\n' "$*"; }
warn() { printf '\033[1;33m[WARN]\033[0m  %s\n' "$*"; }
err()  { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; }
die()  { err "$*"; exit 1; }

require_root() { [[ $EUID -eq 0 ]] || die "Este script debe ejecutarse como root (usa sudo)."; }

usage() {
    sed -n '2,22p' "$0"
    exit 0
}

# ---------- Parse args ----------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --principal-host) PRINCIPAL_HOST="$2"; shift 2 ;;
        --backup-host)    BACKUP_HOST="$2";    shift 2 ;;
        --db-name)        DB_NAME="$2";        shift 2 ;;
        --db-user)        DB_USER="$2";        shift 2 ;;
        --db-pass)        DB_PASS="$2";        shift 2 ;;
        --root-pass)      ROOT_PASS="$2";      shift 2 ;;
        --sql-file)       SQL_FILE="$2";       shift 2 ;;
        -h|--help)        usage ;;
        *) die "Argumento desconocido: $1" ;;
    esac
done

require_root

[[ -n "$PRINCIPAL_HOST" || -n "$BACKUP_HOST" ]] \
    || die "Se requiere al menos --principal-host o --backup-host"

# Auto-detectar SQL si no se especificó
if [[ -z "$SQL_FILE" ]]; then
    # Buscar en el directorio padre del script o en el directorio actual
    if [[ -f "${SCRIPT_DIR}/../ameribank_full_db.sql" ]]; then
        SQL_FILE="${SCRIPT_DIR}/../ameribank_full_db.sql"
    elif [[ -f "${SCRIPT_DIR}/ameribank_full_db.sql" ]]; then
        SQL_FILE="${SCRIPT_DIR}/ameribank_full_db.sql"
    elif [[ -f "./ameribank_full_db.sql" ]]; then
        SQL_FILE="./ameribank_full_db.sql"
    else
        die "No se encontró ameribank_full_db.sql. Usa --sql-file para especificarlo."
    fi
fi

# ---------- Detectar OS ----------
detect_os() {
    [[ -f /etc/os-release ]] || die "No se pudo leer /etc/os-release"
    # shellcheck disable=SC1091
    . /etc/os-release
    local id="${ID:-unknown}" like="${ID_LIKE:-}"
    case "$id" in
        rocky|rhel|almalinux|centos) OS_FAMILY="rhel" ;;
        ubuntu|debian)               OS_FAMILY="debian" ;;
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

# ---------- Pedir passwords si faltan ----------
prompt_passwords() {
    if [[ -z "$ROOT_PASS" ]]; then
        read -srp "Password nuevo para root de MySQL: " ROOT_PASS; echo
        [[ -n "$ROOT_PASS" ]] || die "Password root vacío"
    fi
    if [[ -z "$DB_PASS" ]]; then
        read -srp "Password para usuario '${DB_USER}': " DB_PASS; echo
        [[ -n "$DB_PASS" ]] || die "Password de aplicación vacío"
    fi
}

# ---------- Instalar paquetes ----------
install_rhel() {
    log "Instalando mysql-server en Rocky/RHEL..."
    dnf install -y mysql-server mysql firewalld
    systemctl enable --now mysqld
    systemctl enable --now firewalld || warn "firewalld no se pudo iniciar"
}

install_debian() {
    log "Instalando MySQL 8.0 en Ubuntu/Debian..."
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -q
    apt-get install -y gnupg curl ufw

    # En Debian puro, 'apt install mysql-server' instala MariaDB, no MySQL.
    # mysql-connector-j >=9.0 (Spring Boot 3.5) eliminó soporte para MariaDB.
    # Solo en Debian (no Ubuntu) se agrega el repo oficial de MySQL.
    if [[ "${ID:-}" == "debian" ]]; then
        log "Debian detectado — configurando repo oficial de MySQL 8.0 via mysql-apt-config..."
        local tmpdir; tmpdir=$(mktemp -d)

        # mysql-apt-config gestiona llaves y fuentes de MySQL automáticamente.
        # RPM-GPG-KEY-mysql-2023 expiró oct-2025; este paquete incluye la llave vigente.
        local mysql_apt_config_url="https://dev.mysql.com/get/mysql-apt-config_0.8.39-1_all.deb"
        log "Descargando mysql-apt-config 0.8.39-1..."
        curl -fsSL --connect-timeout 15 --max-time 60 \
            -o "${tmpdir}/mysql-apt-config.deb" \
            "$mysql_apt_config_url" \
            || { rm -rf "$tmpdir"; die "No se pudo descargar mysql-apt-config desde ${mysql_apt_config_url}. Verifica conectividad o actualiza la URL en https://dev.mysql.com/downloads/repo/apt/"; }

        # Pre-seleccionar MySQL 8.0 para evitar el menú interactivo de debconf
        echo "mysql-apt-config mysql-apt-config/select-server select mysql-8.0" \
            | debconf-set-selections
        DEBIAN_FRONTEND=noninteractive dpkg -i "${tmpdir}/mysql-apt-config.deb" || true
        rm -rf "$tmpdir"
        apt-get update -q
    fi

    DEBIAN_FRONTEND=noninteractive apt-get install -y mysql-server mysql-client
    systemctl enable --now mysql
}

# ---------- Configurar MySQL ----------
# bind-address 0.0.0.0 + logs locales (sin NFS en VMs)
configure_mysql_rhel() {
    local cnf="/etc/my.cnf.d/ameribank.cnf"
    log "Escribiendo $cnf"
    cat > "$cnf" <<EOF
[mysqld]
bind-address      = 0.0.0.0
event_scheduler   = ON
general_log       = 1
general_log_file  = /var/log/mysql/mysql-general.log
slow_query_log    = 1
slow_query_log_file = /var/log/mysql/mysql-slow.log
long_query_time   = 1
EOF
    mkdir -p /var/log/mysql
    chown mysql:mysql /var/log/mysql
    systemctl restart mysqld
}

configure_mysql_debian() {
    local cnf="/etc/mysql/mysql.conf.d/ameribank.cnf"
    log "Escribiendo $cnf"
    cat > "$cnf" <<EOF
[mysqld]
bind-address      = 0.0.0.0
event_scheduler   = ON
general_log       = 1
general_log_file  = /var/log/mysql/mysql-general.log
slow_query_log    = 1
slow_query_log_file = /var/log/mysql/mysql-slow.log
long_query_time   = 1
EOF
    mkdir -p /var/log/mysql
    chown mysql:mysql /var/log/mysql
    systemctl restart mysql
}

# ---------- Asegurar root ----------
secure_root() {
    log "Configurando password de root MySQL..."
    mysql --protocol=socket -uroot <<SQL || warn "No se pudo cambiar el plugin de root (puede ya estar configurado)"
ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY '${ROOT_PASS}';
FLUSH PRIVILEGES;
SQL
}

# ---------- Cargar esquema y crear usuarios de app ----------
# Se crea un usuario MySQL por cada host de VM-App que se haya indicado.
# Esto permite que tanto el principal como el backup conecten con las mismas credenciales
# pero cada uno solo desde su propia IP (no '%').
load_schema_and_user() {
    [[ -f "$SQL_FILE" ]] || die "No existe el archivo SQL: $SQL_FILE"
    log "Cargando esquema desde $SQL_FILE"
    mysql -uroot -p"${ROOT_PASS}" < "$SQL_FILE"

    grant_host() {
        local host="$1" label="$2"
        log "Creando usuario '${DB_USER}'@'${host}' (${label})..."
        mysql -uroot -p"${ROOT_PASS}" <<SQL
CREATE USER IF NOT EXISTS '${DB_USER}'@'${host}' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'${host}' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'${host}';
SQL
    }

    [[ -n "$PRINCIPAL_HOST" ]] && grant_host "$PRINCIPAL_HOST" "principal"
    [[ -n "$BACKUP_HOST"    ]] && grant_host "$BACKUP_HOST"    "backup"

    mysql -uroot -p"${ROOT_PASS}" -e "FLUSH PRIVILEGES;"
    log "Usuarios creados. Accesos: ${PRINCIPAL_HOST:-—} / ${BACKUP_HOST:-—}"
}

# ---------- Firewall ----------
# Abre 3306 para cada host de VM-App configurado.
_fw_allow_host_rhel() {
    local host="$1"
    firewall-cmd --permanent --zone=public \
        --add-rich-rule="rule family=ipv4 source address=${host} port port=3306 protocol=tcp accept" \
        || warn "Regla para ${host} duplicada o con error"
}

configure_firewall_rhel() {
    if ! systemctl is-active --quiet firewalld; then
        warn "firewalld no está activo, saltando"; return
    fi
    [[ -n "$PRINCIPAL_HOST" ]] && { log "Abriendo 3306 para principal (${PRINCIPAL_HOST})"; _fw_allow_host_rhel "$PRINCIPAL_HOST"; }
    [[ -n "$BACKUP_HOST"    ]] && { log "Abriendo 3306 para backup    (${BACKUP_HOST})";    _fw_allow_host_rhel "$BACKUP_HOST"; }
    firewall-cmd --reload
}

configure_firewall_debian() {
    if ! command -v ufw >/dev/null 2>&1; then
        warn "ufw no instalado, saltando"; return
    fi
    [[ -n "$PRINCIPAL_HOST" ]] && { log "Abriendo 3306 para principal (${PRINCIPAL_HOST})"; ufw allow from "$PRINCIPAL_HOST" to any port 3306 proto tcp || true; }
    [[ -n "$BACKUP_HOST"    ]] && { log "Abriendo 3306 para backup    (${BACKUP_HOST})";    ufw allow from "$BACKUP_HOST"    to any port 3306 proto tcp || true; }
    ufw status verbose || true
}

# ---------- Verificación ----------
verify() {
    log "Verificando tablas cargadas en ${DB_NAME}..."
    # Verificamos con root (local) que el esquema se cargó correctamente.
    # El usuario de app no puede conectar desde 127.0.0.1 porque fue creado
    # solo para las IPs de las VMs de app — esa prueba la hace cada VM-App.
    mysql -uroot -p"${ROOT_PASS}" \
        -e "USE ${DB_NAME}; SELECT COUNT(*) AS tablas FROM information_schema.tables WHERE table_schema='${DB_NAME}';" \
        2>/dev/null || warn "No se pudo verificar el esquema con root"

    log "Verificando event_scheduler..."
    local sched
    sched=$(mysql -uroot -p"${ROOT_PASS}" -Nse "SHOW VARIABLES LIKE 'event_scheduler';" | awk '{print $2}')
    [[ "$sched" == "ON" ]] || warn "event_scheduler=${sched} (debería ser ON)"

    log "Verificando que MySQL escucha en 3306..."
    ss -tlnp | grep -E ':3306\s' || warn "MySQL no aparece escuchando en 3306"

    log "Usuarios creados en MySQL:"
    mysql -uroot -p"${ROOT_PASS}" -Nse \
        "SELECT user, host FROM mysql.user WHERE user='${DB_USER}';" \
        | awk '{printf "[INFO]    usuario=%-12s host=%s\n", $1, $2}'
}

# ---------- Main ----------
main() {
    detect_os
    prompt_passwords

    case "$OS_FAMILY" in
        rhel)
            install_rhel
            configure_mysql_rhel
            secure_root
            load_schema_and_user
            configure_firewall_rhel
            ;;
        debian)
            install_debian
            configure_mysql_debian
            secure_root
            load_schema_and_user
            configure_firewall_debian
            ;;
    esac

    verify

    local my_ip
    my_ip=$(hostname -I | awk '{print $1}')

    cat <<EOF

==========================================================
  MySQL instalado — VM-DB lista (arquitectura 3 VMs)
==========================================================
  Base de datos    : ${DB_NAME}
  Usuario app      : ${DB_USER}
  VM Principal     : ${PRINCIPAL_HOST:-no configurada}
  VM Backup        : ${BACKUP_HOST:-no configurada}
  Esta VM-DB (IP)  : ${my_ip}
  Logs             : /var/log/mysql/
==========================================================

--- Paso siguiente: ejecutar en VM-Principal ---
  sudo ./deploy_vm_app.sh \\
      --role principal \\
      --db-host ${my_ip} \\
      --db-user ${DB_USER} \\
      --db-name ${DB_NAME}

--- Paso siguiente: ejecutar en VM-Backup ---
  sudo ./deploy_vm_app.sh \\
      --role backup \\
      --db-host ${my_ip} \\
      --db-user ${DB_USER} \\
      --db-name ${DB_NAME}

--- Probar conectividad desde cada VM-App ---
  mysql -h ${my_ip} -u ${DB_USER} -p -e "USE ${DB_NAME}; SHOW TABLES;"

EOF
}

main "$@"
