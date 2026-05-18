package org.amerike.ameribank.dao;

import org.amerike.ameribank.config.ConexionDB;

import java.sql.*;

public class TwoFactorDao {

    public void upsert2Fa(int usuarioId, String tipo2Fa, boolean habilitado,
                          String codigoSecreto, boolean telefonoVerif, boolean emailVerif) throws SQLException {
        try (Connection conn = ConexionDB.conectar();
             CallableStatement cs = conn.prepareCall("{ CALL sp_upsert_2fa(?, ?, ?, ?, ?, ?) }")) {
            cs.setInt(1, usuarioId);
            cs.setString(2, tipo2Fa);
            cs.setBoolean(3, habilitado);
            cs.setString(4, codigoSecreto);
            cs.setBoolean(5, telefonoVerif);
            cs.setBoolean(6, emailVerif);
            cs.execute();
        }
    }

    // retornar codigo_secreto or null
    public String getCodigoSecreto(int usuarioId) throws SQLException {
        try (Connection conn = ConexionDB.conectar();
             CallableStatement cs = conn.prepareCall("{ CALL sp_get_codigo_secreto(?) }")) {
            cs.setInt(1, usuarioId);
            boolean has = cs.execute();
            if (has) {
                try (ResultSet rs = cs.getResultSet()) {
                    if (rs.next()) return rs.getString("codigo_secreto");
                }
            }
        }
        return null;
    }

    // sp_check_blacklist(p_totp, p_usuario_id)
    public boolean isBlacklisted(String totp, Integer usuarioId) throws SQLException {
        try (Connection conn = ConexionDB.conectar();
             CallableStatement cs = conn.prepareCall("{ CALL sp_check_blacklist(?, ?) }")) {
            cs.setString(1, totp);
            if (usuarioId == null) cs.setNull(2, Types.INTEGER);
            else cs.setInt(2, usuarioId);
            boolean has = cs.execute();
            if (has) {
                try (ResultSet rs = cs.getResultSet()) {
                    return rs.next();
                }
            }
        }
        return false;
    }

    public boolean isBlacklisted(String totp) throws SQLException {
        return isBlacklisted(totp, null);
    }

    // sp_insert_blacklist(p_totp, p_blacklisted_at, p_usuario_id)
    public void insertBlacklist(String totp, Timestamp when, Integer usuarioId) throws SQLException {
        try (Connection conn = ConexionDB.conectar();
             CallableStatement cs = conn.prepareCall("{ CALL sp_insert_blacklist(?, ?, ?) }")) {
            cs.setString(1, totp);
            cs.setTimestamp(2, when);
            if (usuarioId == null) cs.setNull(3, Types.INTEGER);
            else cs.setInt(3, usuarioId);
            cs.execute();
        }
    }

    public void insertBlacklist(String totp, Timestamp when) throws SQLException {
        insertBlacklist(totp, when, null);
    }
}
