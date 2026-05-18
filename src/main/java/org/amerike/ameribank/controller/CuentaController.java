package org.amerike.ameribank.controller;

import org.amerike.ameribank.dao.CuentaDAO;
import org.amerike.ameribank.model.Cuenta;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/cuentas")
public class CuentaController {

    @PostMapping("/registrar")
    public String registrarCuenta(@RequestParam String numeroCuenta,
                                  @RequestParam String tipoCuenta,
                                  @RequestParam BigDecimal saldo,
                                  @RequestParam String estado,
                                  @RequestParam int clienteId) {

        try {
            Cuenta nuevaCuenta = new Cuenta();
            nuevaCuenta.setNumeroCuenta(numeroCuenta);
            nuevaCuenta.setTipoCuenta(tipoCuenta);
            nuevaCuenta.setSaldo(saldo);
            nuevaCuenta.setEstado(estado);
            nuevaCuenta.setClienteId(clienteId);

            new CuentaDAO().registrarCuenta(nuevaCuenta);

            return "Cuenta registrada correctamente";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error al registrar la cuenta: " + e.getMessage();
        }
    }

    // =====================================
    // 🔹 NUEVO ENDPOINT: Actualizar Cuenta
    // =====================================
    @PutMapping("/actualizar")
    public String actualizarCuenta(@RequestParam String numeroCuenta,
                                   @RequestParam String tipoCuenta,
                                   @RequestParam BigDecimal saldo,
                                   @RequestParam String estado,
                                   @RequestParam int clienteId) {

        try {
            Cuenta cuenta = new Cuenta();
            cuenta.setNumeroCuenta(numeroCuenta);
            cuenta.setTipoCuenta(tipoCuenta);
            cuenta.setSaldo(saldo);
            cuenta.setEstado(estado);
            cuenta.setClienteId(clienteId);

            new CuentaDAO().actualizarCuenta(cuenta);

            return "Cuenta actualizada correctamente";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error al actualizar la cuenta: " + e.getMessage();
        }
    }

    @GetMapping("/saldo")
    public String consultarSaldo(@RequestParam String numeroCuenta) {
        try {
            BigDecimal saldo = new CuentaDAO().consultarSaldo(numeroCuenta);
            return "Saldo de la cuenta " + numeroCuenta + ": $" + saldo;
        } catch (Exception e) {
            return "Error al consultar saldo: " + e.getMessage();
        }
    }

    @GetMapping("/estado")
    public String obtenerEstadoCuenta(@RequestParam String numeroCuenta) {
        try {
            String estado = new CuentaDAO().obtenerEstadoCuenta(numeroCuenta);
            return "Estado de la cuenta " + numeroCuenta + ":\n\n" + estado;
        } catch (Exception e) {
            return "Error al obtener estado de cuenta: " + e.getMessage();
        }
    }
}
