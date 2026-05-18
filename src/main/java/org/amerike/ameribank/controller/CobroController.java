package org.amerike.ameribank.controller;

// Importa la clase DAO para acceder a datos
import org.amerike.ameribank.dao.CobroDAO;
// Importa el modelo de tarjeta
import org.amerike.ameribank.model.TarjetaModel;
// Importa el modelo de cobro
import org.amerike.ameribank.model.CobroTarjeta;
// Importa anotaciones de Spring para crear API REST
import org.springframework.web.bind.annotation.*;

// Importa HashMap para crear mapas de datos
import java.util.HashMap;
// Importa interfaz List
import java.util.List;
// Importa interfaz Map
import java.util.Map;

/**
 * Controlador REST para el módulo de cobros de tarjeta
 * MODO SIMULACIÓN: No registra cobros en base de datos
 */
// Indica que esta clase es un controlador REST (devuelve JSON)
@RestController
// Define la ruta base para todos los endpoints de este controlador
@RequestMapping("/api/cobros")
// Permite peticiones desde cualquier origen (CORS) - En producción se debe restringir
@CrossOrigin(origins = "*")
public class CobroController {

    // Crea una instancia del DAO para acceder a la base de datos
    // final: no se puede reasignar esta variable
    private final CobroDAO cobroDAO = new CobroDAO();

    // ENDPOINT 1: Obtener todas las tarjetas

    /**
     * GET /api/cobros/tarjetas
     * Retorna todas las tarjetas de crédito con sus datos
     */
    // Indica que este método responde a peticiones GET en la ruta /tarjetas
    @GetMapping("/tarjetas")
    // Método público que retorna una lista de TarjetaModel
    public List<TarjetaModel> obtenerTarjetas() {
        // Llama al método del DAO que obtiene todas las tarjetas
        // Spring convierte automáticamente la lista a JSON
        return cobroDAO.obtenerTodasLasTarjetas();
    }

    // ENDPOINT 2: Simular cobro

    /**
     * POST /api/cobros/simular
     * Procesa un cobro en una tarjeta (SIMULACIÓN - No guarda en BD)
     *
     * Body JSON esperado:
     * {
     *   "tarjetaId": 1,
     *   "comercio": "Netflix",
     *   "monto": 299.00
     * }
     */
    // Indica que este método responde a peticiones POST en la ruta /simular
    @PostMapping("/simular")
    // Método que retorna un Map (será convertido a JSON)
    // @RequestBody: indica que los datos vienen en el cuerpo de la petición como JSON
    public Map<String, Object> simularCobro(@RequestBody Map<String, Object> datos) {
        // Crea un mapa para almacenar la respuesta que se enviará al cliente
        Map<String, Object> respuesta = new HashMap<>();

        // Try-catch para manejar cualquier error que pueda ocurrir
        try {
            // Extrae el ID de la tarjeta del mapa de datos recibido
            // Cast a Integer porque viene como Object
            int tarjetaId = (Integer) datos.get("tarjetaId");
            // Extrae el nombre del comercio del mapa
            // Cast a String
            String comercio = (String) datos.get("comercio");
            // Extrae el monto y lo convierte a double
            // Cast a Number primero porque puede venir como Integer o Double
            double monto = ((Number) datos.get("monto")).doubleValue();

            // Busca la tarjeta en la base de datos usando su ID
            TarjetaModel tarjeta = cobroDAO.obtenerTarjetaPorId(tarjetaId);

            // Valida que la tarjeta existe
            if (tarjeta == null) {
                // Si no existe, crea respuesta de error
                respuesta.put("exito", false);
                respuesta.put("mensaje", "Tarjeta no encontrada");
                // Retorna la respuesta inmediatamente
                return respuesta;
            }

            // Valida que la tarjeta no esté vencida
            // Usa el método del modelo TarjetaModel
            if (tarjeta.estaVencida()) {
                // Si está vencida, crea respuesta de error
                respuesta.put("exito", false);
                respuesta.put("mensaje", "Tarjeta vencida");
                // Retorna la respuesta inmediatamente
                return respuesta;
            }

            // Calcula el saldo disponible de la tarjeta
            // Usa el método del modelo TarjetaModel
            double disponible = tarjeta.calcularDisponible();

            // Valida que haya saldo suficiente para el cobro
            if (monto > disponible) {
                // Si no hay saldo suficiente, crea respuesta de error
                respuesta.put("exito", false);
                // Incluye el saldo disponible en el mensaje
                respuesta.put("mensaje", "Saldo insuficiente. Disponible: $" + String.format("%.2f", disponible));
                // Retorna la respuesta inmediatamente
                return respuesta;
            }

            // APROBAR COBRO
            // Calcula el nuevo saldo sumando el monto del cobro al saldo actual
            double nuevoSaldo = tarjeta.getSaldoActual() + monto;

            // Actualiza el saldo en la base de datos
            boolean actualizado = cobroDAO.actualizarSaldo(tarjetaId, nuevoSaldo);

            // Valida que la actualización fue exitosa
            if (!actualizado) {
                // Si no se pudo actualizar, crea respuesta de error
                respuesta.put("exito", false);
                respuesta.put("mensaje", "Error al actualizar el saldo");
                // Retorna la respuesta inmediatamente
                return respuesta;
            }

            // Crea un objeto CobroTarjeta con los datos del cobro
            // Estado: "APROBADO" porque pasó todas las validaciones
            CobroTarjeta cobro = new CobroTarjeta(tarjetaId, comercio, monto, "APROBADO");
            // Registra el cobro (en modo simulación solo genera un ID ficticio)
            int cobroId = cobroDAO.registrarCobro(cobro);

            // Crea la respuesta exitosa con todos los datos
            respuesta.put("exito", true);
            respuesta.put("mensaje", "Cobro aprobado exitosamente");
            // Incluye el nuevo saldo en la respuesta
            respuesta.put("nuevoSaldo", nuevoSaldo);
            // Calcula y incluye el nuevo saldo disponible
            respuesta.put("disponible", tarjeta.getLimiteCredito() - nuevoSaldo);
            // Incluye el ID del cobro (ficticio en modo simulación)
            respuesta.put("cobroId", cobroId);

        } catch (Exception e) {
            // Si ocurre cualquier error inesperado, lo captura aquí
            respuesta.put("exito", false);
            // Incluye el mensaje de error en la respuesta
            respuesta.put("mensaje", "Error al procesar cobro: " + e.getMessage());
            // Imprime el stack trace en consola para debugging
            e.printStackTrace();
        }

        // Retorna el mapa de respuesta
        // Spring lo convierte automáticamente a JSON
        return respuesta;
    }

    // ENDPOINT 3: Obtener alertas de límite

    /**
     * GET /api/cobros/alertas
     * Retorna tarjetas que están al 80% o más de su límite
     */
    // Indica que este método responde a peticiones GET en la ruta /alertas
    @GetMapping("/alertas")
    // Método público que retorna una lista de TarjetaModel
    public List<TarjetaModel> obtenerAlertas() {
        // Llama al método del DAO que obtiene tarjetas con alerta
        // Spring convierte automáticamente la lista a JSON
        return cobroDAO.obtenerTarjetasConAlerta();
    }
}