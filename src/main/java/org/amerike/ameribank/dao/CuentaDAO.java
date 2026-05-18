package org.amerike.ameribank.dao;

import org.springframework.stereotype.Repository;
import org.amerike.ameribank.config.ConexionDB;
import org.amerike.ameribank.model.Cuenta;

import java.sql.*;
import java.math.BigDecimal;

@Repository
public class CuentaDAO {

    public void registrarCuenta(Cuenta c) throws SQLException {
        try (Connection conn = ConexionDB.conectar();
             CallableStatement cs = conn.prepareCall("{ CALL sp_registrar_cuenta(?, ?, ?, ?, ?) }")) {

            cs.setString(1, c.getNumeroCuenta());
            cs.setString(2, c.getTipoCuenta());
            cs.setBigDecimal(3, c.getSaldo());
            cs.setString(4, c.getEstado());
            cs.setInt(5, c.getClienteId());

            cs.execute();
        }
    }

    // ===============================
    // 🔹 NUEVO MÉTODO: Actualizar Cuenta
    // ===============================
    public void actualizarCuenta(Cuenta c) throws SQLException {
        String sql = "UPDATE cuentas SET tipo_cuenta=?, saldo=?, estado=?, cliente_id=? WHERE numero_cuenta=?";

        try (Connection conn = ConexionDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, c.getTipoCuenta());
            stmt.setBigDecimal(2, c.getSaldo());
            stmt.setString(3, c.getEstado());
            stmt.setInt(4, c.getClienteId());
            stmt.setString(5, c.getNumeroCuenta());

            stmt.executeUpdate();
        }
    }

    public BigDecimal consultarSaldo(String numeroCuenta) throws SQLException {
        try (Connection conn = ConexionDB.conectar();
             CallableStatement cs = conn.prepareCall("{ CALL sp_consultar_saldo(?) }")) {

            cs.setString(1, numeroCuenta);

            boolean has = cs.execute();
            if (has) {
                try (ResultSet rs = cs.getResultSet()) {
                    if (rs.next()) return rs.getBigDecimal("saldo");
                }
            }
        }
        return null;
    }

    public String obtenerEstadoCuenta(String numeroCuenta) throws SQLException {
        try (Connection conn = ConexionDB.conectar();
             CallableStatement cs = conn.prepareCall("{ CALL sp_estado_cuenta(?) }")) {

            cs.setString(1, numeroCuenta);

            boolean has = cs.execute();
            if (has) {
                try (ResultSet rs = cs.getResultSet()) {
                    if (rs.next()) {
                        return "Número de cuenta: " + rs.getString("numero_cuenta") +
                                "\nTipo: " + rs.getString("tipo_cuenta") +
                                "\nSaldo: $" + rs.getBigDecimal("saldo") +
                                "\nEstado: " + rs.getString("estado");
                    }
                }
            }
        }
        return null;
    }
}