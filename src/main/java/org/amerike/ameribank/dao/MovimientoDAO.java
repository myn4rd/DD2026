package org.amerike.ameribank.dao;

import org.amerike.ameribank.config.ConexionDB;
import org.amerike.ameribank.model.Movimiento;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Repository
public class MovimientoDAO {

    public void registrarDeposito(String numeroCuenta, BigDecimal monto, String descripcion, String cuentaRemitente) throws Exception {
        String sql = "CALL RegistrarDeposito(?, ?, ?, ?)";
        try (Connection conn = ConexionDB.conectar(); CallableStatement stmt = conn.prepareCall(sql)) {
            stmt.setString(1, numeroCuenta); // setString
            stmt.setBigDecimal(2, monto);
            stmt.setString(3, descripcion);
            stmt.setString(4, cuentaRemitente);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    public void registrarRetiro(String numeroCuenta, BigDecimal monto, String descripcion) throws Exception {
        String sql = "CALL RegistrarRetiro(?, ?, ?)";
        try (Connection conn = ConexionDB.conectar(); CallableStatement stmt = conn.prepareCall(sql)) {
            stmt.setString(1, numeroCuenta); // setString
            stmt.setBigDecimal(2, monto);
            stmt.setString(3, descripcion);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    public void registrarTransferencia(String cuentaOrigen, String cuentaDestino, BigDecimal monto, String descripcion) throws Exception {
        String sql = "CALL RegistrarTransferencia(?, ?, ?, ?)";
        try (Connection conn = ConexionDB.conectar(); CallableStatement stmt = conn.prepareCall(sql)) {
            stmt.setString(1, cuentaOrigen); // setString
            stmt.setString(2, cuentaDestino); // setString
            stmt.setBigDecimal(3, monto);
            stmt.setString(4, descripcion);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new Exception(e.getMessage(), e);
        }
    }

    private Movimiento mapearResultSet(ResultSet rs) throws SQLException {
        Movimiento movimiento = new Movimiento();
        movimiento.setId(rs.getLong("id"));
        movimiento.setTipoMovimiento(rs.getString("tipo_movimiento"));
        movimiento.setMonto(rs.getBigDecimal("monto"));
        movimiento.setDescripcion(rs.getString("descripcion"));
        movimiento.setFechaMovimiento(rs.getTimestamp("fecha_movimiento").toLocalDateTime());
        movimiento.setCuentaRemitente(rs.getString("cuenta_remitente"));
        movimiento.setCuentaReceptora(rs.getString("cuenta_receptora"));
        return movimiento;
    }

    public List<Movimiento> obtenerMovimientosPorCuenta(String numeroCuenta, int limite) throws Exception {
        String sql = "CALL ObtenerMovimientosPorCuenta(?, ?)";
        List<Movimiento> movimientos = new ArrayList<>();
        try (Connection conn = ConexionDB.conectar(); CallableStatement stmt = conn.prepareCall(sql)) {
            stmt.setString(1, numeroCuenta); // setString
            stmt.setInt(2, limite);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                movimientos.add(mapearResultSet(rs));
            }
        } catch (SQLException e) {
            throw new Exception(e.getMessage(), e);
        }
        return movimientos;
    }

    public List<Movimiento> obtenerMovimientoPorId(Long movimientoId) throws Exception {
        String sql = "CALL ObtenerMovimientoPorId(?)";
        List<Movimiento> movimientos = new ArrayList<>();
        try (Connection conn = ConexionDB.conectar(); CallableStatement stmt = conn.prepareCall(sql)) {
            stmt.setLong(1, movimientoId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                movimientos.add(mapearResultSet(rs));
            }
        } catch (SQLException e) {
            throw new Exception(e.getMessage(), e);
        }
        return movimientos;
    }
}