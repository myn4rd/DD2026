USE Ameribank



CREATE TABLE blacklisted_totps (
id INT AUTO_INCREMENT PRIMARY KEY,
totp VARCHAR(255) NOT NULL,
usuario_id INT,
blacklisted_at TIMESTAMP NOT NULL,
UNIQUE KEY uq_totp_usuario (totp, usuario_id),
INDEX idx_totp (totp),
INDEX idx_usuario (usuario_id),
CONSTRAINT fk_blacklisted_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(usuario_id) ON DELETE SET NULL
);



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
SELECT codigo_secreto, habilitado, fecha_activ FROM autenticacion_2fa WHERE usuario_id = p_usuario_id LIMIT 1;
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

-- Event 30s 
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