-- =====================================================
-- AMERIBANK - Script completo de base de datos
-- Ejecutar en MySQL 8.0+ en el LXC de la DB (40.0.4.12)
-- =====================================================

CREATE DATABASE IF NOT EXISTS Ameribank;
USE Ameribank;

-- Habilitar el event scheduler para 2FA
SET GLOBAL event_scheduler = ON;

-- =====================================================
-- TABLAS
-- =====================================================

-- 1. USUARIOS (autenticacion)
CREATE TABLE IF NOT EXISTS usuarios (
    usuario_id INT AUTO_INCREMENT PRIMARY KEY,
    usr VARCHAR(100) NOT NULL UNIQUE,
    pwd VARCHAR(255) NOT NULL,
    es_admin BOOLEAN NOT NULL DEFAULT FALSE
);

-- 2. CLIENTES
CREATE TABLE IF NOT EXISTS clientes (
    cliente_id INT AUTO_INCREMENT PRIMARY KEY,
    numero_cliente VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    apellido_pat VARCHAR(100) NOT NULL,
    apellido_mat VARCHAR(100),
    fecha_nac DATE,
    rfc VARCHAR(13),
    curp VARCHAR(18),
    email VARCHAR(150),
    celular VARCHAR(15),
    direccion VARCHAR(255),
    ciudad VARCHAR(100),
    estado VARCHAR(100),
    cp VARCHAR(10),
    estatus VARCHAR(20) NOT NULL DEFAULT 'ACTIVO'
);

-- 3. CUENTAS
CREATE TABLE IF NOT EXISTS cuentas (
    id INT AUTO_INCREMENT PRIMARY KEY,
    numero_cuenta VARCHAR(20) NOT NULL UNIQUE,
    tipo_cuenta VARCHAR(50) NOT NULL,
    saldo DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVO',
    cliente_id INT NOT NULL,
    fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cuentas_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(cliente_id)
);

-- 4. MOVIMIENTOS
CREATE TABLE IF NOT EXISTS movimientos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cuenta_id BIGINT NOT NULL,
    tipo_movimiento VARCHAR(50) NOT NULL,
    monto DECIMAL(15,2) NOT NULL,
    descripcion VARCHAR(255),
    fecha_movimiento DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cuenta_remitente VARCHAR(20),
    cuenta_receptora VARCHAR(20)
);

-- 5. PRODUCTOS FINANCIEROS (tarjetas de credito)
CREATE TABLE IF NOT EXISTS productos_financieros (
    id INT AUTO_INCREMENT PRIMARY KEY,
    tipo_producto VARCHAR(50) NOT NULL,
    numero_tarjeta VARCHAR(20) NOT NULL UNIQUE,
    limite_credito DECIMAL(15,2) NOT NULL,
    saldo_actual DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    fecha_emision DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    cliente_id INT NOT NULL,
    CONSTRAINT fk_productos_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(cliente_id)
);

-- 6. COBROS DE TARJETA
CREATE TABLE IF NOT EXISTS cobros_tarjeta (
    id INT AUTO_INCREMENT PRIMARY KEY,
    tarjeta_id INT NOT NULL,
    comercio VARCHAR(150) NOT NULL,
    monto DOUBLE NOT NULL,
    fecha_cobro TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado VARCHAR(20) NOT NULL DEFAULT 'SIMULADO',
    CONSTRAINT fk_cobros_tarjeta FOREIGN KEY (tarjeta_id) REFERENCES productos_financieros(id)
);

-- 7. AUTENTICACION 2FA
CREATE TABLE IF NOT EXISTS autenticacion_2fa (
    usuario_id INT PRIMARY KEY,
    tipo_2fa ENUM('SMS','EMAIL','APP') NOT NULL DEFAULT 'APP',
    habilitado BOOLEAN NOT NULL DEFAULT FALSE,
    codigo_secreto VARCHAR(255),
    telefono_verif BOOLEAN DEFAULT FALSE,
    email_verif BOOLEAN DEFAULT FALSE,
    fecha_activ TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_2fa_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(usuario_id)
);

-- 8. BLACKLIST 2FA (codigos expirados)
CREATE TABLE IF NOT EXISTS blacklisted_totps (
    id INT AUTO_INCREMENT PRIMARY KEY,
    totp VARCHAR(255) NOT NULL,
    usuario_id INT,
    blacklisted_at TIMESTAMP NOT NULL,
    UNIQUE KEY uq_totp_usuario (totp, usuario_id),
    INDEX idx_totp (totp),
    INDEX idx_usuario (usuario_id),
    CONSTRAINT fk_blacklisted_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(usuario_id) ON DELETE SET NULL
);

-- =====================================================
-- STORED PROCEDURES
-- =====================================================

-- LOGIN
DELIMITER //
CREATE PROCEDURE login(
    IN p_usr VARCHAR(100),
    IN p_pwd VARCHAR(255)
)
BEGIN
    SELECT usuario_id AS usuarioId, es_admin AS esAdmin
    FROM usuarios
    WHERE usr = p_usr AND pwd = p_pwd
    LIMIT 1;
END //
DELIMITER ;

-- REGISTRAR CLIENTE
DELIMITER //
CREATE PROCEDURE RegistraCliente(
    IN p_numero_cliente VARCHAR(20),
    IN p_nombre VARCHAR(100),
    IN p_apellido_pat VARCHAR(100),
    IN p_apellido_mat VARCHAR(100),
    IN p_fecha_nac DATE,
    IN p_rfc VARCHAR(13),
    IN p_curp VARCHAR(18),
    IN p_email VARCHAR(150),
    IN p_celular VARCHAR(15),
    IN p_direccion VARCHAR(255),
    IN p_ciudad VARCHAR(100),
    IN p_estado VARCHAR(100),
    IN p_cp VARCHAR(10),
    IN p_estatus VARCHAR(20)
)
BEGIN
    INSERT INTO clientes (numero_cliente, nombre, apellido_pat, apellido_mat, fecha_nac, rfc, curp, email, celular, direccion, ciudad, estado, cp, estatus)
    VALUES (p_numero_cliente, p_nombre, p_apellido_pat, p_apellido_mat, p_fecha_nac, p_rfc, p_curp, p_email, p_celular, p_direccion, p_ciudad, p_estado, p_cp, p_estatus);
END //
DELIMITER ;

-- REGISTRAR CUENTA
DELIMITER //
CREATE PROCEDURE sp_registrar_cuenta(
    IN p_numero_cuenta VARCHAR(20),
    IN p_tipo_cuenta VARCHAR(50),
    IN p_saldo DECIMAL(15,2),
    IN p_estado VARCHAR(20),
    IN p_cliente_id INT
)
BEGIN
    INSERT INTO cuentas (numero_cuenta, tipo_cuenta, saldo, estado, cliente_id)
    VALUES (p_numero_cuenta, p_tipo_cuenta, p_saldo, p_estado, p_cliente_id);
END //
DELIMITER ;

-- CONSULTAR SALDO
DELIMITER //
CREATE PROCEDURE sp_consultar_saldo(
    IN p_numero_cuenta VARCHAR(20)
)
BEGIN
    SELECT saldo FROM cuentas WHERE numero_cuenta = p_numero_cuenta;
END //
DELIMITER ;

-- ESTADO DE CUENTA
DELIMITER //
CREATE PROCEDURE sp_estado_cuenta(
    IN p_numero_cuenta VARCHAR(20)
)
BEGIN
    SELECT numero_cuenta, tipo_cuenta, saldo, estado
    FROM cuentas
    WHERE numero_cuenta = p_numero_cuenta;
END //
DELIMITER ;

-- REGISTRAR DEPOSITO
DELIMITER //
CREATE PROCEDURE RegistrarDeposito(
    IN p_numero_cuenta VARCHAR(20),
    IN p_monto DECIMAL(15,2),
    IN p_descripcion VARCHAR(255),
    IN p_cuenta_remitente VARCHAR(20)
)
BEGIN
    DECLARE v_cuenta_id BIGINT;

    SELECT id INTO v_cuenta_id FROM cuentas WHERE numero_cuenta = p_numero_cuenta;

    UPDATE cuentas SET saldo = saldo + p_monto WHERE numero_cuenta = p_numero_cuenta;

    INSERT INTO movimientos (cuenta_id, tipo_movimiento, monto, descripcion, cuenta_remitente, cuenta_receptora)
    VALUES (v_cuenta_id, 'DEPOSITO', p_monto, p_descripcion, p_cuenta_remitente, p_numero_cuenta);
END //
DELIMITER ;

-- REGISTRAR RETIRO
DELIMITER //
CREATE PROCEDURE RegistrarRetiro(
    IN p_numero_cuenta VARCHAR(20),
    IN p_monto DECIMAL(15,2),
    IN p_descripcion VARCHAR(255)
)
BEGIN
    DECLARE v_cuenta_id BIGINT;
    DECLARE v_saldo DECIMAL(15,2);

    SELECT id, saldo INTO v_cuenta_id, v_saldo FROM cuentas WHERE numero_cuenta = p_numero_cuenta;

    IF v_saldo >= p_monto THEN
        UPDATE cuentas SET saldo = saldo - p_monto WHERE numero_cuenta = p_numero_cuenta;

        INSERT INTO movimientos (cuenta_id, tipo_movimiento, monto, descripcion, cuenta_remitente, cuenta_receptora)
        VALUES (v_cuenta_id, 'RETIRO', p_monto, p_descripcion, p_numero_cuenta, NULL);
    ELSE
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Saldo insuficiente';
    END IF;
END //
DELIMITER ;

-- REGISTRAR TRANSFERENCIA
DELIMITER //
CREATE PROCEDURE RegistrarTransferencia(
    IN p_cuenta_origen VARCHAR(20),
    IN p_cuenta_destino VARCHAR(20),
    IN p_monto DECIMAL(15,2),
    IN p_descripcion VARCHAR(255)
)
BEGIN
    DECLARE v_cuenta_origen_id BIGINT;
    DECLARE v_saldo_origen DECIMAL(15,2);

    SELECT id, saldo INTO v_cuenta_origen_id, v_saldo_origen FROM cuentas WHERE numero_cuenta = p_cuenta_origen;

    IF v_saldo_origen >= p_monto THEN
        UPDATE cuentas SET saldo = saldo - p_monto WHERE numero_cuenta = p_cuenta_origen;
        UPDATE cuentas SET saldo = saldo + p_monto WHERE numero_cuenta = p_cuenta_destino;

        INSERT INTO movimientos (cuenta_id, tipo_movimiento, monto, descripcion, cuenta_remitente, cuenta_receptora)
        VALUES (v_cuenta_origen_id, 'TRANSFERENCIA', p_monto, p_descripcion, p_cuenta_origen, p_cuenta_destino);
    ELSE
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Saldo insuficiente para transferencia';
    END IF;
END //
DELIMITER ;

-- OBTENER MOVIMIENTOS POR CUENTA
DELIMITER //
CREATE PROCEDURE ObtenerMovimientosPorCuenta(
    IN p_numero_cuenta VARCHAR(20),
    IN p_limite INT
)
BEGIN
    DECLARE v_cuenta_id BIGINT;

    SELECT id INTO v_cuenta_id FROM cuentas WHERE numero_cuenta = p_numero_cuenta;

    SELECT id, tipo_movimiento, monto, descripcion, fecha_movimiento, cuenta_remitente, cuenta_receptora
    FROM movimientos
    WHERE cuenta_id = v_cuenta_id
    ORDER BY fecha_movimiento DESC
    LIMIT p_limite;
END //
DELIMITER ;

-- OBTENER MOVIMIENTO POR ID
DELIMITER //
CREATE PROCEDURE ObtenerMovimientoPorId(
    IN p_movimiento_id BIGINT
)
BEGIN
    SELECT id, tipo_movimiento, monto, descripcion, fecha_movimiento, cuenta_remitente, cuenta_receptora
    FROM movimientos
    WHERE id = p_movimiento_id;
END //
DELIMITER ;

-- REGISTRAR PRODUCTO FINANCIERO
DELIMITER //
CREATE PROCEDURE sp_registrar_producto(
    IN p_tipo_producto VARCHAR(50),
    IN p_numero_tarjeta VARCHAR(20),
    IN p_limite_credito DECIMAL(15,2),
    IN p_saldo_actual DECIMAL(15,2),
    IN p_fecha_emision DATE,
    IN p_fecha_vencimiento DATE,
    IN p_cliente_id INT
)
BEGIN
    INSERT INTO productos_financieros (tipo_producto, numero_tarjeta, limite_credito, saldo_actual, fecha_emision, fecha_vencimiento, cliente_id)
    VALUES (p_tipo_producto, p_numero_tarjeta, p_limite_credito, p_saldo_actual, p_fecha_emision, p_fecha_vencimiento, p_cliente_id);
END //
DELIMITER ;

-- OBTENER PRODUCTO POR NUMERO DE TARJETA
DELIMITER //
CREATE PROCEDURE sp_obtener_producto_por_tarjeta(
    IN p_numero_tarjeta VARCHAR(20)
)
BEGIN
    SELECT id, tipo_producto, numero_tarjeta, limite_credito, saldo_actual, fecha_emision, fecha_vencimiento, cliente_id
    FROM productos_financieros
    WHERE numero_tarjeta = p_numero_tarjeta;
END //
DELIMITER ;

-- ACTUALIZAR SALDO DE PRODUCTO
DELIMITER //
CREATE PROCEDURE sp_actualizar_saldo(
    IN p_saldo DECIMAL(15,2),
    IN p_numero_tarjeta VARCHAR(20)
)
BEGIN
    UPDATE productos_financieros
    SET saldo_actual = p_saldo
    WHERE numero_tarjeta = p_numero_tarjeta;
END //
DELIMITER ;

-- OBTENER TODAS LAS TARJETAS
DELIMITER //
CREATE PROCEDURE sp_obtener_todas_tarjetas()
BEGIN
    SELECT id, tipo_producto, numero_tarjeta, limite_credito, saldo_actual, fecha_emision, fecha_vencimiento, cliente_id
    FROM productos_financieros;
END //
DELIMITER ;

-- OBTENER TARJETA POR ID
DELIMITER //
CREATE PROCEDURE sp_obtener_tarjeta_por_id(
    IN p_id INT
)
BEGIN
    SELECT id, tipo_producto, numero_tarjeta, limite_credito, saldo_actual, fecha_emision, fecha_vencimiento, cliente_id
    FROM productos_financieros
    WHERE id = p_id;
END //
DELIMITER ;

-- ACTUALIZAR SALDO DE TARJETA (para cobros)
DELIMITER //
CREATE PROCEDURE sp_actualizar_saldo_tarjeta(
    IN p_id INT,
    IN p_nuevo_saldo DECIMAL(15,2)
)
BEGIN
    UPDATE productos_financieros
    SET saldo_actual = p_nuevo_saldo
    WHERE id = p_id;
    SELECT ROW_COUNT() AS filas_afectadas;
END //
DELIMITER ;

-- OBTENER HISTORIAL DE COBROS
DELIMITER //
CREATE PROCEDURE sp_obtener_historial_cobros(
    IN p_tarjeta_id INT
)
BEGIN
    SELECT id, tarjeta_id, comercio, monto, fecha_cobro, estado
    FROM cobros_tarjeta
    WHERE tarjeta_id = p_tarjeta_id
    ORDER BY fecha_cobro DESC;
END //
DELIMITER ;

-- OBTENER TARJETAS CON ALERTA (80%+ del limite)
DELIMITER //
CREATE PROCEDURE sp_obtener_tarjetas_con_alerta()
BEGIN
    SELECT id, tipo_producto, numero_tarjeta, limite_credito, saldo_actual, fecha_emision, fecha_vencimiento, cliente_id
    FROM productos_financieros
    WHERE saldo_actual >= (limite_credito * 0.80);
END //
DELIMITER ;

-- =====================================================
-- STORED PROCEDURES - 2FA
-- =====================================================

DELIMITER //
CREATE PROCEDURE sp_upsert_2fa(
    IN p_usuario_id INT,
    IN p_tipo_2fa ENUM('SMS','EMAIL','APP'),
    IN p_habilitado BOOLEAN,
    IN p_codigo_secreto VARCHAR(255),
    IN p_telefono_verif BOOLEAN,
    IN p_email_verif BOOLEAN
)
BEGIN
    IF EXISTS(SELECT 1 FROM autenticacion_2fa WHERE usuario_id = p_usuario_id) THEN
        UPDATE autenticacion_2fa
        SET tipo_2fa = p_tipo_2fa,
            habilitado = p_habilitado,
            codigo_secreto = p_codigo_secreto,
            telefono_verif = p_telefono_verif,
            email_verif = p_email_verif,
            fecha_activ = CURRENT_TIMESTAMP
        WHERE usuario_id = p_usuario_id;
    ELSE
        INSERT INTO autenticacion_2fa (usuario_id, tipo_2fa, habilitado, codigo_secreto, telefono_verif, email_verif)
        VALUES (p_usuario_id, p_tipo_2fa, p_habilitado, p_codigo_secreto, p_telefono_verif, p_email_verif);
    END IF;
END //
DELIMITER ;

DELIMITER //
CREATE PROCEDURE sp_get_codigo_secreto(
    IN p_usuario_id INT
)
BEGIN
    SELECT codigo_secreto, habilitado, fecha_activ
    FROM autenticacion_2fa
    WHERE usuario_id = p_usuario_id
    LIMIT 1;
END //
DELIMITER ;

DELIMITER //
CREATE PROCEDURE sp_check_blacklist(
    IN p_totp VARCHAR(255),
    IN p_usuario_id INT
)
BEGIN
    SELECT totp FROM blacklisted_totps
    WHERE totp = p_totp AND (p_usuario_id IS NULL OR usuario_id = p_usuario_id)
    LIMIT 1;
END //
DELIMITER ;

DELIMITER //
CREATE PROCEDURE sp_insert_blacklist(
    IN p_totp VARCHAR(255),
    IN p_blacklisted_at TIMESTAMP,
    IN p_usuario_id INT
)
BEGIN
    INSERT IGNORE INTO blacklisted_totps (totp, usuario_id, blacklisted_at)
    VALUES (p_totp, p_usuario_id, p_blacklisted_at);
    UPDATE autenticacion_2fa
    SET habilitado = FALSE, fecha_activ = p_blacklisted_at
    WHERE usuario_id = p_usuario_id AND codigo_secreto = p_totp AND habilitado = TRUE;
END //
DELIMITER ;

-- =====================================================
-- EVENT - Expiracion automatica de codigos 2FA (30s)
-- =====================================================

DELIMITER //
CREATE EVENT IF NOT EXISTS ev_expire_2fa_codes
ON SCHEDULE EVERY 5 SECOND
DO
BEGIN
    INSERT IGNORE INTO blacklisted_totps (totp, usuario_id, blacklisted_at)
    SELECT codigo_secreto, usuario_id, NOW()
    FROM autenticacion_2fa
    WHERE habilitado = TRUE AND fecha_activ <= NOW() - INTERVAL 30 SECOND;

    UPDATE autenticacion_2fa a
    JOIN (SELECT totp, usuario_id FROM blacklisted_totps) b
    ON a.codigo_secreto = b.totp AND a.usuario_id = b.usuario_id
    SET a.habilitado = FALSE, a.fecha_activ = NOW()
    WHERE a.habilitado = TRUE;
END //
DELIMITER ;

-- =====================================================
-- DATOS DE PRUEBA
-- =====================================================

-- Usuarios (admin y cliente)
INSERT INTO usuarios (usr, pwd, es_admin) VALUES
('admin', 'admin123', TRUE),
('cliente1', 'cliente123', FALSE),
('cliente2', 'cliente456', FALSE);

-- Clientes
INSERT INTO clientes (numero_cliente, nombre, apellido_pat, apellido_mat, fecha_nac, rfc, curp, email, celular, direccion, ciudad, estado, cp, estatus) VALUES
('CLI-001', 'Juan', 'Garcia', 'Lopez', '1990-05-15', 'GALJ900515ABC', 'GALJ900515HDFRPN01', 'juan.garcia@email.com', '5551234567', 'Av. Reforma 100', 'CDMX', 'CDMX', '06600', 'ACTIVO'),
('CLI-002', 'Maria', 'Hernandez', 'Martinez', '1985-08-22', 'HEMM850822DEF', 'HEMM850822MDFRRT02', 'maria.hdz@email.com', '5559876543', 'Calle Juarez 200', 'Guadalajara', 'Jalisco', '44100', 'ACTIVO'),
('CLI-003', 'Carlos', 'Ramirez', 'Soto', '1995-01-10', 'RASC950110GHI', 'RASC950110HDFMRS03', 'carlos.ram@email.com', '5554567890', 'Blvd. Insurgentes 300', 'Monterrey', 'Nuevo Leon', '64000', 'ACTIVO');

-- Cuentas
INSERT INTO cuentas (numero_cuenta, tipo_cuenta, saldo, estado, cliente_id) VALUES
('1000000001', 'AHORRO', 50000.00, 'ACTIVO', 1),
('1000000002', 'CHEQUES', 120000.00, 'ACTIVO', 1),
('1000000003', 'AHORRO', 75000.00, 'ACTIVO', 2),
('1000000004', 'AHORRO', 30000.00, 'ACTIVO', 3);

-- Tarjetas de credito
INSERT INTO productos_financieros (tipo_producto, numero_tarjeta, limite_credito, saldo_actual, fecha_emision, fecha_vencimiento, cliente_id) VALUES
('TARJETA DE CREDITO', '4000000000000001', 50000.00, 12000.00, '2025-01-01', '2028-01-01', 1),
('TARJETA DE CREDITO', '4000000000000002', 100000.00, 85000.00, '2025-03-15', '2028-03-15', 2),
('TARJETA DE CREDITO', '4000000000000003', 30000.00, 5000.00, '2025-06-01', '2028-06-01', 3);

-- Movimientos de ejemplo
INSERT INTO movimientos (cuenta_id, tipo_movimiento, monto, descripcion, cuenta_remitente, cuenta_receptora) VALUES
(1, 'DEPOSITO', 10000.00, 'Deposito inicial', NULL, '1000000001'),
(1, 'RETIRO', 2000.00, 'Retiro cajero', '1000000001', NULL),
(1, 'TRANSFERENCIA', 5000.00, 'Pago renta', '1000000001', '1000000003');

-- =====================================================
-- USUARIO REMOTO PARA LOS LXC
-- =====================================================
-- Ejecutar esto por separado como root de MySQL:
--
-- CREATE USER 'ameribank'@'%' IDENTIFIED BY 'TuPasswordSeguro';
-- GRANT ALL PRIVILEGES ON Ameribank.* TO 'ameribank'@'%';
-- FLUSH PRIVILEGES;
