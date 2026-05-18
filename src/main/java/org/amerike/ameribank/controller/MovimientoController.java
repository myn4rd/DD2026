package org.amerike.ameribank.controller;

import org.amerike.ameribank.service.MovimientoService;
import org.amerike.ameribank.model.Movimiento;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/movimientos")
public class MovimientoController {

    private final MovimientoService movimientoService;

    public MovimientoController(MovimientoService movimientoService) {
        this.movimientoService = movimientoService;
    }

    @PostMapping("/deposito")
    public Map<String, String> registrarDeposito(
            @RequestParam String numeroCuenta, // CAMBIO: String
            @RequestParam BigDecimal monto,
            @RequestParam String descripcion,
            @RequestParam String cuentaRemitente) {

        Map<String, String> response = new HashMap<>();
        try {
            movimientoService.realizarDeposito(numeroCuenta, monto, descripcion, cuentaRemitente);
            response.put("status", "success");
            response.put("message", "Depósito realizado exitosamente");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/retiro")
    public Map<String, String> registrarRetiro(
            @RequestParam String numeroCuenta, // CAMBIO: String
            @RequestParam BigDecimal monto,
            @RequestParam String descripcion) {

        Map<String, String> response = new HashMap<>();
        try {
            movimientoService.realizarRetiro(numeroCuenta, monto, descripcion);
            response.put("status", "success");
            response.put("message", "Retiro realizado exitosamente");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/transferencia")
    public Map<String, String> registrarTransferencia(
            @RequestParam String cuentaOrigen, // CAMBIO: String
            @RequestParam String cuentaDestino,
            @RequestParam BigDecimal monto,
            @RequestParam String descripcion) {

        Map<String, String> response = new HashMap<>();
        try {
            movimientoService.realizarTransferencia(cuentaOrigen, cuentaDestino, monto, descripcion);
            response.put("status", "success");
            response.put("message", "Transferencia realizada exitosamente");
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "Error: " + e.getMessage());
        }
        return response;
    }

    @GetMapping("/consulta")
    public List<Movimiento> consultarMovimientos(
            @RequestParam String numeroCuenta, // CAMBIO: String
            @RequestParam(defaultValue = "10") int limite) {
        try {
            return movimientoService.obtenerMovimientosPorCuenta(numeroCuenta, limite);
        } catch (Exception e) {
            return List.of();
        }
    }

    @GetMapping("/consulta/por-id")
    public List<Movimiento> consultarMovimientoPorId(@RequestParam Long movimientoId) {
        try {
            return movimientoService.obtenerMovimientoPorId(movimientoId);
        } catch (Exception e) {
            return List.of();
        }
    }
}