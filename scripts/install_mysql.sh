#!/usr/bin/env bash
# =====================================================================
# Ameribank - Instalador de MySQL para el LXC 3 (40.0.4.12)
# Detecta Rocky/RHEL o Ubuntu/Debian, instala MySQL 8, configura
# bind-address, firewall, carga el esquema y crea el usuario remoto.
#
# Uso:
#   sudo ./install_mysql.sh [--db-name Ameribank] \
#                           [--db-user ameribank] \
#                           [--db-pass 'TuPasswordSeguro'] \
#                           [--root-pass 'PasswordRoot'] \
#                           [--allowed-cidr 40.0.4.0/24] \
#                           [--sql-file ../ameribank_full_db.sql]
#
# Si no se pasan --db-pass / --root-pass, se piden interactivamente.
# =====================================================================

set -euo pipefail

# ---------- Defaults ----------
DB_NAME="Ameribank"
DB_USER="ameribank"
DB_PASS=""
ROOT_PASS=""
ALLOWED_CIDR="40.0.4.0/24"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/../ameribank_full_db.sql"

# ---------- Helpers ----------
log()   { printf '\033[1;34m[INFO]\033[0m  %s\n' "$*"; }
warn()  { printf '\033[1;33m[WARN]\033[0m  %s\n' "$*"; }
err()   { printf '\033[1;31m[ERROR]\033[0m %s\n' "$*" >&2; }
die()   { err "$*"; exit 1; }

require_root() {
    [[ $EUID -eq 0 ]] || die "Este script debe ejecutarse como root (usa sudo)."
}

# ---------- Parse args ----------
while [[ $# -gt 0 ]]; do
    case "$1" in
        --db-name)      DB_NAME="$2"; shift 2 ;;
        --db-user)      DB_USER="$2"; shift 2 ;;
        --db-pass)      DB_PASS="$2"; shift 2 ;;
        --root-pass)    ROOT_PASS="$2"; shift 2 ;;
        --allowed-cidr) ALLOWED_CIDR="$2"; shift 2 ;;
        --sql-file)     SQL_FILE="$2"; shift 2 ;;
        -h|--help)
            sed -n '2,15p' "$0"; exit 0 ;;
        *) die "Argumento desconocido: $1" ;;
    esac
done

require_root

# ---------- Detectar OS ----------
detect_os() {
    [[ -f /etc/os-release ]] || die "No se pudo leer /etc/os-release"
    # shellcheck disable=SC1091
    . /etc/os-release
    OS_ID="${ID:-unknown}"
    OS_LIKE="${ID_LIKE:-}"

    case "$OS_ID" in
        rocky|rhel|almalinux|centos)
            OS_FAMILY="rhel"
            ;;
        ubuntu|debian)
            OS_FAMILY="debian"
            ;;
        *)
            # Caer al ID_LIKE si el ID no es directo
            if [[ "$OS_LIKE" == *"rhel"* || "$OS_LIKE" == *"fedora"* ]]; then
                OS_FAMILY="rhel"
            elif [[ "$OS_LIKE" == *"debian"* ]]; then
                OS_FAMILY="debian"
            else
                die "OS no soportado: ID=$OS_ID ID_LIKE=$OS_LIKE"
            fi
            ;;
    esac
    log "Detectado: $PRETTY_NAME (familia=$OS_FAMILY)"
}

# ---------- Pedir passwords si faltan ----------
prompt_passwords() {
    if [[ -z "$ROOT_PASS" ]]; then
        read -srp "Password nuevo para root de MySQL: " ROOT_PASS; echo
        [[ -n "$ROOT_PASS" ]] || die "Password root vacío"
    fi
    if [[ -z "$DB_PASS" ]]; then
        read -srp "Password para usuario '$DB_USER': " DB_PASS; echo
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
    log "Instalando mysql-server en Ubuntu/Debian..."
    export DEBIAN_FRONTEND=noninteractive
    apt-get update
    apt-get install -y mysql-server mysql-client ufw
    systemctl enable --now mysql
}

# ---------- Configurar MySQL ----------
configure_mysql_rhel() {
    local cnf="/etc/my.cnf.d/ameribank.cnf"
    log "Escribiendo $cnf"
    cat > "$cnf" <<EOF
[mysqld]
bind-address = 0.0.0.0
event_scheduler = ON
general_log = 1
general_log_file = /var/log/mysql/mysql-general.log
slow_query_log = 1
slow_query_log_file = /var/log/mysql/mysql-slow.log
long_query_time = 1
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
bind-address = 0.0.0.0
event_scheduler = ON
general_log = 1
general_log_file = /var/log/mysql/mysql-general.log
slow_query_log = 1
slow_query_log_file = /var/log/mysql/mysql-slow.log
long_query_time = 1
EOF
    mkdir -p /var/log/mysql
    chown mysql:mysql /var/log/mysql
    systemctl restart mysql
}

# ---------- Asegurar root e instalar passwords ----------
secure_root() {
    log "Configurando password de root..."
    # En Rocky/Ubuntu modernos, root usa auth_socket por defecto y se conecta sin password
    # Cambiamos a caching_sha2_password con la contraseña proporcionada
    mysql --protocol=socket -uroot <<SQL || warn "No se pudo cambiar el plugin de root (puede ya estar configurado)"
ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY '${ROOT_PASS}';
FLUSH PRIVILEGES;
SQL
}

# ---------- Cargar esquema y crear usuario de app ----------
load_schema_and_user() {
    [[ -f "$SQL_FILE" ]] || die "No existe el archivo SQL: $SQL_FILE"
    log "Cargando esquema desde $SQL_FILE"
    mysql -uroot -p"${ROOT_PASS}" < "$SQL_FILE"

    log "Creando usuario remoto '${DB_USER}'@'%' con acceso a ${DB_NAME}"
    mysql -uroot -p"${ROOT_PASS}" <<SQL
CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASS}';
ALTER USER '${DB_USER}'@'%' IDENTIFIED BY '${DB_PASS}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'%';
FLUSH PRIVILEGES;
SQL
}

# ---------- Firewall ----------
configure_firewall_rhel() {
    if systemctl is-active --quiet firewalld; then
        log "Abriendo puerto 3306/tcp para ${ALLOWED_CIDR} en firewalld"
        firewall-cmd --permanent --zone=public \
            --add-rich-rule="rule family=ipv4 source address=${ALLOWED_CIDR} port port=3306 protocol=tcp accept"
        firewall-cmd --reload
    else
        warn "firewalld no está activo, saltando configuración de firewall"
    fi
}

configure_firewall_debian() {
    if command -v ufw >/dev/null 2>&1; then
        log "Abriendo puerto 3306/tcp para ${ALLOWED_CIDR} en ufw"
        ufw allow from "${ALLOWED_CIDR}" to any port 3306 proto tcp
        # No forzamos `ufw enable` para no cortar SSH si no estaba habilitado
        ufw status verbose || true
    else
        warn "ufw no instalado, saltando configuración de firewall"
    fi
}

# ---------- Verificación ----------
verify() {
    log "Verificando conectividad local..."
    mysql -u"${DB_USER}" -p"${DB_PASS}" -e "USE ${DB_NAME}; SHOW TABLES;" || \
        die "El usuario ${DB_USER} no puede conectarse"

    log "Verificando event_scheduler..."
    local sched
    sched=$(mysql -uroot -p"${ROOT_PASS}" -Nse "SHOW VARIABLES LIKE 'event_scheduler';" | awk '{print $2}')
    [[ "$sched" == "ON" ]] || warn "event_scheduler=$sched (debería ser ON)"

    log "Verificando bind-address..."
    ss -tlnp | grep -E ':3306\s' || warn "MySQL no parece estar escuchando en 3306"
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

    cat <<EOF

==========================================================
  Instalación completada
==========================================================
  Base de datos : ${DB_NAME}
  Usuario app   : ${DB_USER}
  Acceso desde  : ${ALLOWED_CIDR}
  Logs MySQL    : /var/log/mysql/
==========================================================

Para probar desde otro LXC:
  mysql -h \$(hostname -I | awk '{print \$1}') -u ${DB_USER} -p -e "USE ${DB_NAME}; SHOW TABLES;"

EOF
}

main "$@"
