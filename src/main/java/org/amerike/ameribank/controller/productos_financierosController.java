package org.amerike.ameribank.controller;

import org.amerike.ameribank.dao.productosDAO;
import org.amerike.ameribank.model.productos_financieros;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.SQLException; // Importamos para manejar errores específicos de DB

@RestController
@RequestMapping("/api/productos")
public class productos_financierosController {

    private final ProductoFinancieroService productoService;

    public productos_financierosController(ProductoFinancieroService productoService) {
        this.productoService = productoService;
    }

    // --- 1. ENDPOINT: REGISTRAR (ALTA DE TARJETAS) (POST) ---
    // URL: POST /api/productos/tarjetas/alta
    @PostMapping("/tarjetas/alta")
    public ResponseEntity<String> darDeAlta(@RequestBody productos_financieros nuevoProducto) {
        try {
            productoService.registrarProducto(nuevoProducto);
            return ResponseEntity.ok("Producto financiero (tarjeta) registrado exitosamente.");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error de validación: " + e.getMessage());

        } catch (SQLException e) {
            // Manejo específico del error de llave foránea (cliente_id no existe)
            if (e.getErrorCode() == 1452) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Error de Base de Datos: El ID del Cliente proporcionado NO EXISTE.");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error de base de datos al registrar: " + e.getMessage());

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno del servidor: " + e.getMessage());
        }
    }

    // --- 2. ENDPOINT: CONSULTAR SALDO Y LÍMITE (GET) ---
    // URL: GET /api/productos/consulta/{numeroTarjeta}
    @GetMapping("/consulta/{numeroTarjeta}")
    public ResponseEntity<?> consultarSaldo(@PathVariable String numeroTarjeta) {
        try {
            productos_financieros producto = productoService.buscarPorNumeroTarjeta(numeroTarjeta);

            if (producto == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Tarjeta no encontrada.");
            }

            // Retorna el objeto completo del producto
            return ResponseEntity.ok(producto);

        } catch (SQLException e) {
            // Captura errores de DB propagados desde el Service/DAO
            String errorMessage = "Error de base de datos al consultar: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
        } catch (Exception e) {
            String errorMessage = "Error al consultar la tarjeta: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
        }
    }

    // --- 3. ENDPOINT: PROCESAR TRANSACCIÓN (COMPRA/PAGO) (POST) ---
    // Este endpoint utiliza la lógica de CÁLCULO DE SALDO y VERIFICACIÓN DE LÍMITE del Service.
    // URL: POST /api/productos/transaccion?numeroTarjeta={num}&monto={monto}
    @PostMapping("/transaccion")
    public ResponseEntity<String> procesarTransaccion(
            @RequestParam String numeroTarjeta,
            // El monto debe ser positivo para COMPRA/CARGO y negativo para PAGO/ABONO
            @RequestParam BigDecimal monto) {
        try {
            boolean exito = productoService.procesarTransaccion(numeroTarjeta, monto);

            if (exito) {
                String tipo = monto.compareTo(BigDecimal.ZERO) > 0 ? "Compra/Cargo" : "Pago/Abono";
                return ResponseEntity.ok(tipo + " procesado exitosamente. Saldo actualizado.");
            } else {
                // Si retorna false del Service, significa que el límite fue excedido
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Transacción rechazada: Límite de crédito excedido.");
            }
        } catch (RuntimeException e) {
            // Captura "Tarjeta no encontrada" u otros errores de runtime del Service
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            String errorMessage = "Error interno del servidor al procesar la transacción: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorMessage);
        }
    }
}


