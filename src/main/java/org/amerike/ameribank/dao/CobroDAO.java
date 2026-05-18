package org.amerike.ameribank.dao;

// Importa la clase de configuración para conectarse a la base de datos
import org.amerike.ameribank.config.ConexionDB;
// Importa el modelo de tarjeta
import org.amerike.ameribank.model.TarjetaModel;
// Importa el modelo de cobro
import org.amerike.ameribank.model.CobroTarjeta;

// Importa clases necesarias para trabajar con SQL
import java.sql.*;
// Importa clase para manejar fechas sin hora
import java.time.LocalDate;
// Importa clase para manejar fechas con hora
import java.time.LocalDateTime;
// Importa clase ArrayList para crear listas dinámicas
import java.util.ArrayList;
// Importa interfaz List
import java.util.List;

/**
 * DAO (Data Access Object) que maneja las operaciones de tarjetas de crédito y cobros.
 * VERSIÓN CON PROCEDIMIENTOS ALMACENADOS - Mayor seguridad y rendimiento
 * MODO SIMULACIÓN: No guarda cobros en la base de datos
 */
public class CobroDAO {

    // MÉTODOS DE TARJETAS

    /**
     * Obtiene todas las tarjetas de crédito usando procedimiento almacenado.
     * @return Una lista de objetos TarjetaModel con todas las tarjetas de crédito.
     */
    public List<TarjetaModel> obtenerTodasLasTarjetas() {
        // Crea una lista vacía para almacenar las tarjetas
        List<TarjetaModel> tarjetas = new ArrayList<>();

        // Try-with-resources: cierra automáticamente la conexión y el statement al terminar
        try (Connection conn = ConexionDB.conectar();
             // Prepara la llamada al procedimiento almacenado
             CallableStatement stmt = conn.prepareCall("{CALL sp_obtener_todas_tarjetas()}")) {

            // Ejecuta el procedimiento y obtiene los resultados
            ResultSet rs = stmt.executeQuery();

            // Itera sobre cada fila del resultado
            while (rs.next()) {
                // Convierte la fila actual en un objeto TarjetaModel
                TarjetaModel tarjeta = mapearTarjeta(rs);
                // Agrega la tarjeta a la lista
                tarjetas.add(tarjeta);
            }

        } catch (SQLException e) {
            // Imprime mensaje de error en la consola de errores
            System.err.println("Error al obtener todas las tarjetas: " + e.getMessage());
            // Imprime el stack trace completo para debugging
            e.printStackTrace();
        }

        // Retorna la lista de tarjetas (vacía si hubo error)
        return tarjetas;
    }

    /**
     * Obtiene una tarjeta de crédito por su ID usando procedimiento almacenado.
     * @param id El ID de la tarjeta a obtener.
     * @return Un objeto TarjetaModel si la tarjeta existe, o null si no se encuentra.
     */
    public TarjetaModel obtenerTarjetaPorId(int id) {
        // Try-with-resources para manejar recursos automáticamente
        try (Connection conn = ConexionDB.conectar();
             // Prepara la llamada al procedimiento con un parámetro
             CallableStatement stmt = conn.prepareCall("{CALL sp_obtener_tarjeta_por_id(?)}")) {

            // Establece el primer parámetro (?) con el ID recibido
            stmt.setInt(1, id);
            // Ejecuta el procedimiento y obtiene los resultados
            ResultSet rs = stmt.executeQuery();

            // Verifica si hay al menos una fila en el resultado
            if (rs.next()) {
                // Convierte la fila en un objeto TarjetaModel y lo retorna
                return mapearTarjeta(rs);
            }

        } catch (SQLException e) {
            // Imprime mensaje de error específico
            System.err.println("Error al obtener tarjeta por ID: " + e.getMessage());
            // Imprime el stack trace completo
            e.printStackTrace();
        }

        // Retorna null si no se encontró la tarjeta o hubo un error
        return null;
    }

    /**
     * Actualiza el saldo de una tarjeta usando procedimiento almacenado.
     * MODO SIMULACIÓN: Actualiza el saldo pero NO registra el cobro en historial.
     * @param id El ID de la tarjeta a actualizar.
     * @param nuevoSaldo El nuevo saldo de la tarjeta.
     * @return true si la actualización fue exitosa, false en caso contrario.
     */
    public boolean actualizarSaldo(int id, double nuevoSaldo) {
        // Try-with-resources para manejar la conexión
        try (Connection conn = ConexionDB.conectar();
             // Prepara la llamada al procedimiento con dos parámetros
             CallableStatement stmt = conn.prepareCall("{CALL sp_actualizar_saldo_tarjeta(?, ?)}")) {

            // Establece el primer parámetro: ID de la tarjeta
            stmt.setInt(1, id);
            // Establece el segundo parámetro: nuevo saldo
            stmt.setDouble(2, nuevoSaldo);

            // Ejecuta el procedimiento y obtiene el resultado
            ResultSet rs = stmt.executeQuery();

            // Verifica si hay resultado
            if (rs.next()) {
                // Obtiene el número de filas afectadas por la actualización
                int filasAfectadas = rs.getInt("filas_afectadas");
                // Retorna true si se actualizó al menos una fila
                return filasAfectadas > 0;
            }

        } catch (SQLException e) {
            // Imprime mensaje de error
            System.err.println("Error al actualizar saldo: " + e.getMessage());
            // Imprime el stack trace
            e.printStackTrace();
        }

        // Retorna false si hubo error o no se actualizó nada
        return false;
    }

    // MÉTODOS DE COBROS (MODO SIMULACIÓN)

    /**
     * SIMULA un cobro sin registrarlo en la base de datos.
     * Solo genera un ID ficticio para la respuesta.
     * @param cobro El objeto CobroTarjeta que contiene la información del cobro.
     * @return Un ID ficticio basado en timestamp.
     */
    public int registrarCobro(CobroTarjeta cobro) {
        // MODO SIMULACIÓN: No guarda nada en BD
        // Genera un ID ficticio usando el timestamp actual (milisegundos desde 1970)
        // El módulo 100000 asegura que el ID sea de máximo 5 dígitos
        int idFicticio = (int) (System.currentTimeMillis() % 100000);

        // Imprime en consola los detalles del cobro simulado para debugging
        System.out.println("SIMULACION - Cobro procesado (NO guardado en BD):");
        System.out.println("   Tarjeta ID: " + cobro.getTarjetaId());
        System.out.println("   Comercio: " + cobro.getComercio());
        System.out.println("   Monto: $" + cobro.getMonto());
        System.out.println("   Estado: " + cobro.getEstado());
        System.out.println("   ID Ficticio: " + idFicticio);

        // Retorna el ID ficticio generado
        return idFicticio;
    }

    /**
     * Obtiene el historial de cobros de una tarjeta usando procedimiento almacenado.
     * NOTA: En modo simulación, esto puede retornar vacío si no hay cobros reales.
     * @param tarjetaId El ID de la tarjeta.
     * @return Una lista de objetos CobroTarjeta con el historial de cobros.
     */
    public List<CobroTarjeta> obtenerHistorialCobros(int tarjetaId) {
        // Crea una lista vacía para almacenar los cobros
        List<CobroTarjeta> cobros = new ArrayList<>();

        // Try-with-resources para manejar recursos
        try (Connection conn = ConexionDB.conectar();
             // Prepara la llamada al procedimiento con un parámetro
             CallableStatement stmt = conn.prepareCall("{CALL sp_obtener_historial_cobros(?)}")) {

            // Establece el parámetro con el ID de la tarjeta
            stmt.setInt(1, tarjetaId);
            // Ejecuta el procedimiento y obtiene los resultados
            ResultSet rs = stmt.executeQuery();

            // Itera sobre cada fila del resultado
            while (rs.next()) {
                // Convierte la fila en un objeto CobroTarjeta
                CobroTarjeta cobro = mapearCobro(rs);
                // Agrega el cobro a la lista
                cobros.add(cobro);
            }

        } catch (SQLException e) {
            // Imprime mensaje de error
            System.err.println("Error al obtener historial de cobros: " + e.getMessage());
            // Imprime el stack trace
            e.printStackTrace();
        }

        // Retorna la lista de cobros
        return cobros;
    }

    /**
     * Obtiene tarjetas con alerta de límite usando procedimiento almacenado.
     * @return Una lista de tarjetas que superan el 80% de su límite de crédito.
     */
    public List<TarjetaModel> obtenerTarjetasConAlerta() {
        // Crea una lista vacía para almacenar las tarjetas con alerta
        List<TarjetaModel> tarjetas = new ArrayList<>();

        // Try-with-resources para manejar recursos
        try (Connection conn = ConexionDB.conectar();
             // Prepara la llamada al procedimiento sin parámetros
             CallableStatement stmt = conn.prepareCall("{CALL sp_obtener_tarjetas_con_alerta()}")) {

            // Ejecuta el procedimiento y obtiene los resultados
            ResultSet rs = stmt.executeQuery();

            // Itera sobre cada fila del resultado
            while (rs.next()) {
                // Convierte la fila en un objeto TarjetaModel
                TarjetaModel tarjeta = mapearTarjeta(rs);
                // Agrega la tarjeta a la lista
                tarjetas.add(tarjeta);
            }

        } catch (SQLException e) {
            // Imprime mensaje de error
            System.err.println("Error al obtener tarjetas con alerta: " + e.getMessage());
            // Imprime el stack trace
            e.printStackTrace();
        }

        // Retorna la lista de tarjetas con alerta
        return tarjetas;
    }

    // MÉTODOS AUXILIARES PRIVADOS

    /**
     * Mapea un ResultSet a un objeto TarjetaModel.
     * @param rs El ResultSet con los datos de la tarjeta.
     * @return Un objeto TarjetaModel con los datos mapeados.
     * @throws SQLException Si ocurre un error en el acceso a los datos.
     */
    private TarjetaModel mapearTarjeta(ResultSet rs) throws SQLException {
        // Crea un nuevo objeto TarjetaModel vacío
        TarjetaModel tarjeta = new TarjetaModel();

        // Mapea cada columna del ResultSet a los atributos del objeto
        // Obtiene el ID de la columna "id"
        tarjeta.setId(rs.getInt("id"));
        // Obtiene el tipo de producto de la columna "tipo_producto"
        tarjeta.setTipoProducto(rs.getString("tipo_producto"));
        // Obtiene el número de tarjeta de la columna "numero_tarjeta"
        tarjeta.setNumeroTarjeta(rs.getString("numero_tarjeta"));
        // Obtiene el límite de crédito de la columna "limite_credito"
        tarjeta.setLimiteCredito(rs.getDouble("limite_credito"));
        // Obtiene el saldo actual de la columna "saldo_actual"
        tarjeta.setSaldoActual(rs.getDouble("saldo_actual"));

        // Obtiene la fecha de emisión de la columna "fecha_emision"
        Date fechaEmision = rs.getDate("fecha_emision");
        // Verifica que la fecha no sea null antes de convertirla
        if (fechaEmision != null) {
            // Convierte java.sql.Date a LocalDate
            tarjeta.setFechaEmision(fechaEmision.toLocalDate());
        }

        // Obtiene la fecha de vencimiento de la columna "fecha_vencimiento"
        Date fechaVencimiento = rs.getDate("fecha_vencimiento");
        // Verifica que la fecha no sea null antes de convertirla
        if (fechaVencimiento != null) {
            // Convierte java.sql.Date a LocalDate
            tarjeta.setFechaVencimiento(fechaVencimiento.toLocalDate());
        }

        // Obtiene el ID del cliente de la columna "cliente_id"
        tarjeta.setClienteId(rs.getInt("cliente_id"));

        // Retorna el objeto TarjetaModel completamente mapeado
        return tarjeta;
    }

    /**
     * Mapea un ResultSet a un objeto CobroTarjeta.
     * @param rs El ResultSet con los datos del cobro.
     * @return Un objeto CobroTarjeta con los datos mapeados.
     * @throws SQLException Si ocurre un error en el acceso a los datos.
     */
    private CobroTarjeta mapearCobro(ResultSet rs) throws SQLException {
        // Crea un nuevo objeto CobroTarjeta vacío
        CobroTarjeta cobro = new CobroTarjeta();

        // Mapea cada columna del ResultSet a los atributos del objeto
        // Obtiene el ID del cobro de la columna "id"
        cobro.setId(rs.getInt("id"));
        // Obtiene el ID de la tarjeta de la columna "tarjeta_id"
        cobro.setTarjetaId(rs.getInt("tarjeta_id"));
        // Obtiene el nombre del comercio de la columna "comercio"
        cobro.setComercio(rs.getString("comercio"));
        // Obtiene el monto del cobro de la columna "monto"
        cobro.setMonto(rs.getDouble("monto"));

        // Obtiene la fecha y hora del cobro de la columna "fecha_cobro"
        Timestamp fechaCobro = rs.getTimestamp("fecha_cobro");
        // Verifica que la fecha no sea null antes de convertirla
        if (fechaCobro != null) {
            // Convierte java.sql.Timestamp a LocalDateTime
            cobro.setFechaCobro(fechaCobro.toLocalDateTime());
        }

        // Obtiene el estado del cobro de la columna "estado"
        cobro.setEstado(rs.getString("estado"));

        // Retorna el objeto CobroTarjeta completamente mapeado
        return cobro;
    }
}