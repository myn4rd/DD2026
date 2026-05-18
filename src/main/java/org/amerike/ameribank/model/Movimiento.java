package org.amerike.ameribank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Movimiento {
    private Long id;
    private Long cuentaId;
    private String tipoMovimiento;
    private BigDecimal monto;
    private String descripcion;
    private LocalDateTime fechaMovimiento;
    private String cuentaRemitente;
    private String cuentaReceptora;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getCuentaId() { return cuentaId; }
    public void setCuentaId(Long cuentaId) { this.cuentaId = cuentaId; }

    public String getTipoMovimiento() { return tipoMovimiento; }
    public void setTipoMovimiento(String tipoMovimiento) { this.tipoMovimiento = tipoMovimiento; }

    public BigDecimal getMonto() { return monto; }
    public void setMonto(BigDecimal monto) { this.monto = monto; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDateTime getFechaMovimiento() { return fechaMovimiento; }
    public void setFechaMovimiento(LocalDateTime fechaMovimiento) { this.fechaMovimiento = fechaMovimiento; }

    public String getCuentaRemitente() { return cuentaRemitente; }
    public void setCuentaRemitente(String cuentaRemitente) { this.cuentaRemitente = cuentaRemitente; }

    public String getCuentaReceptora() { return cuentaReceptora; }
    public void setCuentaReceptora(String cuentaReceptora) { this.cuentaReceptora = cuentaReceptora; }
}