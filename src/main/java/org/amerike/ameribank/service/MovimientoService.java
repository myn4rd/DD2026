package org.amerike.ameribank.service;

import org.amerike.ameribank.dao.MovimientoDAO;
import org.amerike.ameribank.model.Movimiento;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class MovimientoService {

    @Autowired
    private MovimientoDAO movimientoDAO;

    public void realizarDeposito(String numeroCuenta, BigDecimal monto, String descripcion, String cuentaRemitente) throws Exception {
        if (monto.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("El monto debe ser mayor a cero");
        if (numeroCuenta == null || numeroCuenta.isEmpty()) throw new IllegalArgumentException("El número de cuenta es requerido");

        movimientoDAO.registrarDeposito(numeroCuenta, monto, descripcion, cuentaRemitente);
    }

    public void realizarRetiro(String numeroCuenta, BigDecimal monto, String descripcion) throws Exception {
        if (monto.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("El monto debe ser mayor a cero");
        if (numeroCuenta == null || numeroCuenta.isEmpty()) throw new IllegalArgumentException("El número de cuenta es requerido");

        movimientoDAO.registrarRetiro(numeroCuenta, monto, descripcion);
    }

    public void realizarTransferencia(String cuentaOrigen, String cuentaDestino, BigDecimal monto, String descripcion) throws Exception {
        if (monto.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("El monto debe ser mayor a cero");
        if (cuentaOrigen == null || cuentaOrigen.isEmpty()) throw new IllegalArgumentException("La cuenta origen es requerida");
        if (cuentaDestino == null || cuentaDestino.isEmpty()) throw new IllegalArgumentException("La cuenta destino es requerida");

        movimientoDAO.registrarTransferencia(cuentaOrigen, cuentaDestino, monto, descripcion);
    }

    public List<Movimiento> obtenerMovimientosPorCuenta(String numeroCuenta, int limite) throws Exception {
        if (numeroCuenta == null || numeroCuenta.isEmpty()) throw new IllegalArgumentException("El número de cuenta es requerido");
        return movimientoDAO.obtenerMovimientosPorCuenta(numeroCuenta, limite);
    }

    public List<Movimiento> obtenerMovimientoPorId(Long movimientoId) throws Exception {
        return movimientoDAO.obtenerMovimientoPorId(movimientoId);
    }
}