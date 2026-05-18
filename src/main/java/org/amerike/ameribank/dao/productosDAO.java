package org.amerike.ameribank.dao;

import org.amerike.ameribank.config.ConexionDB;
import org.amerike.ameribank.model.productos_financieros;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
public class productosDAO {

    // --- 1. Método para REGISTRAR (ALTA) ---
    public void registrarProducto(productos_financieros pf) throws SQLException {
        // Llama al SP: sp_registrar_producto (tipo, num_tarjeta, limite, saldo, fecha_emision, fecha_vencimiento, cliente_id)
        String sql = "{CALL sp_registrar_producto(?, ?, ?, ?, ?, ?, ?)}";

        try (Connection conn = ConexionDB.conectar();
             CallableStatement stmt = conn.prepareCall(sql)) {

            // Mapeo de la POO a la DB
            stmt.setString(1, pf.getTipoProducto());
            stmt.setString(2, pf.getNumeroTarjeta());
            stmt.setBigDecimal(3, pf.getLimiteCredito());
            stmt.setBigDecimal(4, pf.getSaldoActual());
            // Conversión de LocalDate a java.sql.Date
            stmt.setDate(5, Date.valueOf(pf.getFechaEmision()));
            stmt.setDate(6, Date.valueOf(pf.getFechaVencimiento()));
            stmt.setInt(7, pf.getClienteId());

            stmt.executeUpdate();
        }
    }

    // --- 2. Método para BUSCAR por número de tarjeta (Consulta) ---
    public productos_financieros buscarPorNumeroTarjeta(String numeroTarjeta) throws SQLException {
        // Llama al SP: sp_obtener_producto_por_tarjeta (numeroTarjeta)
        String sql = "{CALL sp_obtener_producto_por_tarjeta(?)}";
        productos_financieros pf = null;

        try (Connection conn = ConexionDB.conectar();
             CallableStatement stmt = conn.prepareCall(sql)) {

            stmt.setString(1, numeroTarjeta);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    pf = new productos_financieros();

                    pf.setId(rs.getInt("id"));
                    pf.setTipoProducto(rs.getString("tipo_producto"));
                    pf.setNumeroTarjeta(rs.getString("numero_tarjeta"));
                    pf.setLimiteCredito(rs.getBigDecimal("limite_credito"));
                    pf.setSaldoActual(rs.getBigDecimal("saldo_actual"));
                    // Conversión de java.sql.Date a LocalDate
                    pf.setFechaEmision(rs.getDate("fecha_emision").toLocalDate());
                    pf.setFechaVencimiento(rs.getDate("fecha_vencimiento").toLocalDate());
                    pf.setClienteId(rs.getInt("cliente_id"));
                }
            }
        }
        return pf;
    }

    // --- 3. Método para ACTUALIZAR el saldo (Para futuras transacciones) ---
    public void actualizarSaldo(productos_financieros pf) throws SQLException {
        // Llama al SP: sp_actualizar_saldo (saldo, numero_tarjeta)
        String sql = "{CALL sp_actualizar_saldo(?, ?)}";

        try (Connection conn = ConexionDB.conectar();
             CallableStatement stmt = conn.prepareCall(sql)) {

            stmt.setBigDecimal(1, pf.getSaldoActual());
            stmt.setString(2, pf.getNumeroTarjeta());

            stmt.executeUpdate();
        }
    }
}