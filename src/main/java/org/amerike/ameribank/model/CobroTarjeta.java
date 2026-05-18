package org.amerike.ameribank.model;

import java.time.LocalDateTime;

/**
 * Modelo que representa un cobro realizado en una tarjeta
 * Mapea la tabla cobros_tarjeta
 */
public class CobroTarjeta {

    // Atributos que mapean la tabla cobros_tarjeta

    // ID único del cobro en la base de datos
    private int id;

    // ID de la tarjeta sobre la cual se realizó el cobro
    private int tarjetaId;

    // Nombre del comercio donde se realizó el cobro (ejemplo: "Netflix", "Amazon")
    private String comercio;

    // Monto del cobro en pesos
    private double monto;

    // Fecha y hora exacta cuando se realizó el cobro
    private LocalDateTime fechaCobro;

    // Estado del cobro: puede ser APROBADO, RECHAZADO o SIMULADO
    private String estado;

    // Constructor vacío requerido por frameworks como Spring
    // Permite crear objetos sin pasar parámetros
    public CobroTarjeta() {
    }

    // Constructor sin id y fechaCobro
    // Se usa cuando se crea un nuevo cobro porque estos campos se generan automáticamente en BD
    public CobroTarjeta(int tarjetaId, String comercio, double monto, String estado) {
        // Asigna el ID de la tarjeta
        this.tarjetaId = tarjetaId;
        // Asigna el nombre del comercio
        this.comercio = comercio;
        // Asigna el monto del cobro
        this.monto = monto;
        // Asigna el estado del cobro (APROBADO, RECHAZADO, etc.)
        this.estado = estado;
    }

    // Constructor completo con todos los atributos
    // Se usa cuando se lee un cobro existente de la base de datos
    public CobroTarjeta(int id, int tarjetaId, String comercio, double monto,
                        LocalDateTime fechaCobro, String estado) {
        // Asigna el ID del cobro
        this.id = id;
        // Asigna el ID de la tarjeta
        this.tarjetaId = tarjetaId;
        // Asigna el nombre del comercio
        this.comercio = comercio;
        // Asigna el monto del cobro
        this.monto = monto;
        // Asigna la fecha y hora del cobro
        this.fechaCobro = fechaCobro;
        // Asigna el estado del cobro
        this.estado = estado;
    }

    // Métodos Getters y Setters
    // Permiten acceder y modificar los atributos privados de forma controlada

    // Obtiene el ID del cobro
    public int getId() {
        return id;
    }

    // Establece el ID del cobro
    public void setId(int id) {
        this.id = id;
    }

    // Obtiene el ID de la tarjeta
    public int getTarjetaId() {
        return tarjetaId;
    }

    // Establece el ID de la tarjeta
    public void setTarjetaId(int tarjetaId) {
        this.tarjetaId = tarjetaId;
    }

    // Obtiene el nombre del comercio
    public String getComercio() {
        return comercio;
    }

    // Establece el nombre del comercio
    public void setComercio(String comercio) {
        this.comercio = comercio;
    }

    // Obtiene el monto del cobro
    public double getMonto() {
        return monto;
    }

    // Establece el monto del cobro
    public void setMonto(double monto) {
        this.monto = monto;
    }

    // Obtiene la fecha y hora del cobro
    public LocalDateTime getFechaCobro() {
        return fechaCobro;
    }

    // Establece la fecha y hora del cobro
    public void setFechaCobro(LocalDateTime fechaCobro) {
        this.fechaCobro = fechaCobro;
    }

    // Obtiene el estado del cobro
    public String getEstado() {
        return estado;
    }

    // Establece el estado del cobro
    public void setEstado(String estado) {
        this.estado = estado;
    }

    // Métodos auxiliares para validar el estado del cobro

    /**
     * Verifica si el cobro fue aprobado
     * @return true si estado es APROBADO
     */
    public boolean fueAprobado() {
        // Compara el estado con "APROBADO" ignorando mayúsculas/minúsculas
        return "APROBADO".equalsIgnoreCase(estado);
    }

    /**
     * Verifica si el cobro fue rechazado
     * @return true si estado es RECHAZADO
     */
    public boolean fueRechazado() {
        // Compara el estado con "RECHAZADO" ignorando mayúsculas/minúsculas
        return "RECHAZADO".equalsIgnoreCase(estado);
    }

    // Sobrescribe el método toString para mostrar la información del cobro
    // Útil para debugging y logging en consola
    @Override
    public String toString() {
        // Construye una cadena con toda la información del cobro
        return "CobroTarjeta{" +
                "id=" + id +
                ", tarjetaId=" + tarjetaId +
                ", comercio='" + comercio + '\'' +
                ", monto=" + monto +
                ", fechaCobro=" + fechaCobro +
                ", estado='" + estado + '\'' +
                '}';
    }
}