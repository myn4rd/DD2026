package org.amerike.ameribank.dao;

import org.amerike.ameribank.config.ConexionDB;
import org.amerike.ameribank.model.login;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Repository
public class loginDAO {

    public static class LoginResult {
        public final int usuarioId;
        public final boolean esAdmin;

        public LoginResult(int usuarioId, boolean esAdmin) {
            this.usuarioId = usuarioId;
            this.esAdmin = esAdmin;
        }
    }

    public LoginResult validarCredenciales(login l) {

        String sql = "call login (?, ?)";

        try (Connection conn = ConexionDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, l.getUsr());
            stmt.setString(2, l.getPwd());

            boolean hasResult = stmt.execute();

            if (hasResult) {
                try (java.sql.ResultSet rs = stmt.getResultSet()) {
                    if (rs.next()) {
                        int usuarioId = rs.getInt("usuarioId");
                        boolean esAdmin = rs.getBoolean("esAdmin");

                        return new LoginResult(usuarioId, esAdmin);
                    }
                }
            }

            throw new RuntimeException("Fallo de credenciales inesperado o credenciales no encontradas.");

        } catch (SQLException e) {

            String mensajeMySQL = e.getMessage();

            System.err.println("Error de BD (SQLState: " + e.getSQLState() + "): " + mensajeMySQL);
            throw new RuntimeException(
                    "Fallo en la validación de credenciales: " + mensajeMySQL, e
            );
        }
    }
}