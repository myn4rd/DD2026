package org.amerike.ameribank.model;

import java.time.LocalDate;

/**
 * Modelo que representa una tarjeta de crédito
 * Mapea la tabla productos_financieros (solo tipo TARJETA DE CREDITO)
 */
public class TarjetaModel {

    // Atributos que mapean la tabla productos_financieros

    // ID único de la tarjeta en la base de datos
    private int id;

    // Tipo de producto financiero (siempre "TARJETA DE CREDITO" en este modelo)
    private String tipoProducto;

    // Número de la tarjeta de crédito (16 dígitos generalmente)
    private String numeroTarjeta;

    // Límite máximo de crédito que tiene la tarjeta
    private double limiteCredito;

    // Saldo actual usado de la tarjeta
    private double saldoActual;

    // Fecha en que se emitió la tarjeta
    private LocalDate fechaEmision;

    // Fecha en que vence la tarjeta
    private LocalDate fechaVencimiento;

    // ID del cliente propietario de esta tarjeta
    private int clienteId;

    // Constructor vacío requerido por frameworks como Spring
    public TarjetaModel() {
    }

    // Constructor completo con todos los atributos
    // Se usa cuando se necesita crear un objeto con todos los datos de una vez
    public TarjetaModel(int id, String tipoProducto, String numeroTarjeta,
                        double limiteCredito, double saldoActual,
                        LocalDate fechaEmision, LocalDate fechaVencimiento,
                        int clienteId) {
        // Asigna el ID de la tarjeta
        this.id = id;
        // Asigna el tipo de producto
        this.tipoProducto = tipoProducto;
        // Asigna el número de tarjeta
        this.numeroTarjeta = numeroTarjeta;
        // Asigna el límite de crédito
        this.limiteCredito = limiteCredito;
        // Asigna el saldo actual usado
        this.saldoActual = saldoActual;
        // Asigna la fecha de emisión
        this.fechaEmision = fechaEmision;
        // Asigna la fecha de vencimiento
        this.fechaVencimiento = fechaVencimiento;
        // Asigna el ID del cliente propietario
        this.clienteId = clienteId;
    }

    // Métodos Getters y Setters
    // Permiten acceder y modificar los atributos privados de forma controlada

    // Obtiene el ID de la tarjeta
    public int getId() {
        return id;
    }

    // Establece el ID de la tarjeta
    public void setId(int id) {
        this.id = id;
    }

    // Obtiene el tipo de producto
    public String getTipoProducto() {
        return tipoProducto;
    }

    // Establece el tipo de producto
    public void setTipoProducto(String tipoProducto) {
        this.tipoProducto = tipoProducto;
    }

    // Obtiene el número de tarjeta
    public String getNumeroTarjeta() {
        return numeroTarjeta;
    }

    // Establece el número de tarjeta
    public void setNumeroTarjeta(String numeroTarjeta) {
        this.numeroTarjeta = numeroTarjeta;
    }

    // Obtiene el límite de crédito
    public double getLimiteCredito() {
        return limiteCredito;
    }

    // Establece el límite de crédito
    public void setLimiteCredito(double limiteCredito) {
        this.limiteCredito = limiteCredito;
    }

    // Obtiene el saldo actual usado
    public double getSaldoActual() {
        return saldoActual;
    }

    // Establece el saldo actual usado
    public void setSaldoActual(double saldoActual) {
        this.saldoActual = saldoActual;
    }

    // Obtiene la fecha de emisión
    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    // Establece la fecha de emisión
    public void setFechaEmision(LocalDate fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    // Obtiene la fecha de vencimiento
    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    // Establece la fecha de vencimiento
    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    // Obtiene el ID del cliente propietario
    public int getClienteId() {
        return clienteId;
    }

    // Establece el ID del cliente propietario
    public void setClienteId(int clienteId) {
        this.clienteId = clienteId;
    }

    // Métodos auxiliares útiles para validaciones y cálculos

    /**
     * Calcula el saldo disponible en la tarjeta
     * @return límite de crédito - saldo actual
     */
    public double calcularDisponible() {
        // Resta el saldo usado del límite total para obtener lo disponible
        return limiteCredito - saldoActual;
    }

    /**
     * Calcula el porcentaje de uso del crédito
     * @return porcentaje entre 0 y 100
     */
    public double calcularPorcentajeUso() {
        // Valida que el límite no sea cero para evitar división por cero
        if (limiteCredito == 0) return 0;
        // Calcula el porcentaje: (saldo usado / límite total) * 100
        return (saldoActual / limiteCredito) * 100;
    }

    /**
     * Verifica si la tarjeta está vencida
     * @return true si ya pasó la fecha de vencimiento
     */
    public boolean estaVencida() {
        // Compara la fecha de vencimiento con la fecha actual
        // isBefore devuelve true si la fecha de vencimiento es anterior a hoy
        return fechaVencimiento.isBefore(LocalDate.now());
    }

    /**
     * Verifica si la tarjeta está cerca del límite (80% o más)
     * @return true si el uso es >= 80%
     */
    public boolean estaCercaDelLimite() {
        // Usa el método calcularPorcentajeUso y verifica si es 80% o más
        return calcularPorcentajeUso() >= 80.0;
    }

    /**
     * Verifica si tiene saldo disponible suficiente para un monto
     * @param monto a validar
     * @return true si puede realizar el cargo
     */
    public boolean tieneSaldoDisponible(double monto) {
        // Compara el monto solicitado con el saldo disponible
        // Devuelve true si el monto es menor o igual a lo disponible
        return monto <= calcularDisponible();
    }

    // Sobrescribe el método toString para mostrar la información de la tarjeta
    // Útil para debugging y logging
    @Override
    public String toString() {
        // Construye una cadena con toda la información relevante de la tarjeta
        return "TarjetaModel{" +
                "id=" + id +
                ", numeroTarjeta='" + numeroTarjeta + '\'' +
                ", limiteCredito=" + limiteCredito +
                ", saldoActual=" + saldoActual +
                ", disponible=" + calcularDisponible() +
                ", porcentajeUso=" + String.format("%.2f%%", calcularPorcentajeUso()) +
                ", fechaVencimiento=" + fechaVencimiento +
                ", vencida=" + estaVencida() +
                '}';
    }
}