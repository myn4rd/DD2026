package org.amerike.ameribank.controller;

import org.amerike.ameribank.dao.productosDAO;
import org.amerike.ameribank.model.productos_financieros;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.SQLException;

@Service
public class ProductoFinancieroService {

    private final productosDAO productosDAO;

    public ProductoFinancieroService(productosDAO productosDAO) {
        this.productosDAO = productosDAO;
    }

    public productos_financieros registrarProducto(productos_financieros producto) throws Exception {
        // Lógica de validación antes de guardar
        if (producto.getNumeroTarjeta() == null || producto.getLimiteCredito() == null) {
            throw new IllegalArgumentException("Datos incompletos para el registro.");
        }
        productosDAO.registrarProducto(producto);
        return producto;
    }



    public productos_financieros buscarPorNumeroTarjeta(String numeroTarjeta) throws SQLException {
        // Delega la responsabilidad de la búsqueda al DAO.
        return productosDAO.buscarPorNumeroTarjeta(numeroTarjeta);
    }

    // El método clave que hace el CÁLCULO DE SALDO y VERIFICACIÓN DE LÍMITE
    public boolean procesarTransaccion(String numeroTarjeta, BigDecimal monto) throws Exception {
        // ⚠️ Nota: Esta línea ahora llama al método de arriba y ya no dará error.
        productos_financieros producto = productosDAO.buscarPorNumeroTarjeta(numeroTarjeta);

        if (producto == null) {
            throw new RuntimeException("Tarjeta no encontrada.");
        }

        if (monto.compareTo(BigDecimal.ZERO) > 0) { // COMPRA/GASTO
            if (verificarLimite(producto, monto)) {
                BigDecimal nuevoSaldo = producto.getSaldoActual().add(monto);
                producto.setSaldoActual(nuevoSaldo);
                productosDAO.actualizarSaldo(producto);
                return true;
            } else {
                return false; // Límite excedido
            }
        } else if (monto.compareTo(BigDecimal.ZERO) < 0) { // PAGO
            // Garantiza que el saldo no baje de cero con un pago excesivo
            BigDecimal nuevoSaldo = producto.getSaldoActual().add(monto);
            producto.setSaldoActual(nuevoSaldo.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : nuevoSaldo);
            productosDAO.actualizarSaldo(producto);
            return true;
        }
        return false;
    }

    private boolean verificarLimite(productos_financieros producto, BigDecimal montoCompra) {
        // Lógica correcta: Límite - Saldo Actual = Crédito Disponible
        BigDecimal creditoDisponible = producto.getLimiteCredito().subtract(producto.getSaldoActual());
        // Comprueba si el monto de la compra es menor o igual al crédito disponible
        return montoCompra.compareTo(creditoDisponible) <= 0;
    }
}
