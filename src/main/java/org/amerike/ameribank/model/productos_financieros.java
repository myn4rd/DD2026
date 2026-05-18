package org.amerike.ameribank.model;

import java.time.LocalDate;
import java.math.BigDecimal;

// ¡Renombrado a CamelCase!
public class productos_financieros
{

    private int id;
    private String tipoProducto; // Cambiado a CamelCase
    private String numeroTarjeta;
    private BigDecimal limiteCredito;
    private BigDecimal saldoActual;
    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private int clienteId;

    // Constructor Vacío (Obligatorio para Spring/JSON)
    public productos_financieros() {
        // Inicialización automática de fechas y saldo
        this.saldoActual = BigDecimal.ZERO;
        this.fechaEmision = LocalDate.now();
        this.fechaVencimiento = this.fechaEmision.plusYears(5);
    }

    // --- Getters ---
    public int getId() { return id; }
    public String getTipoProducto() { return tipoProducto; }
    public String getNumeroTarjeta() { return numeroTarjeta; }
    public BigDecimal getLimiteCredito() { return limiteCredito; }
    public BigDecimal getSaldoActual() { return saldoActual; }
    public LocalDate getFechaEmision() { return fechaEmision; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public int getClienteId() { return clienteId; }

    // --- Setters (Obligatorio para Spring/JSON) ---
    public void setId(int id) { this.id = id; }
    public void setTipoProducto(String tipoProducto) { this.tipoProducto = tipoProducto; }
    public void setNumeroTarjeta(String numeroTarjeta) { this.numeroTarjeta = numeroTarjeta; }
    public void setLimiteCredito(BigDecimal limiteCredito) { this.limiteCredito = limiteCredito; }
    public void setSaldoActual(BigDecimal saldoActual) { this.saldoActual = saldoActual; }
    public void setFechaEmision(LocalDate fechaEmision) { this.fechaEmision = fechaEmision; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
    public void setClienteId(int clienteId) { this.clienteId = clienteId; }
}