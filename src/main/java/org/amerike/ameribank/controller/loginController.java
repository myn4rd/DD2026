package org.amerike.ameribank.controller;

import org.amerike.ameribank.dao.loginDAO;
import org.amerike.ameribank.model.login;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/login")
public class loginController {
    private final loginDAO lDAO;

    public loginController(loginDAO lDAO) {
        this.lDAO = lDAO;
    }

    @PostMapping("/loggear")
    public ResponseEntity<?> iniciarSesion(@RequestBody login l) {

        try {
            loginDAO.LoginResult resultado = lDAO.validarCredenciales(l);
            int usuarioId = resultado.usuarioId;
            boolean esAdmin = resultado.esAdmin;

            Map<String, Object> resp = new HashMap<>();

            if (esAdmin) {
                resp.put("status", "ADMIN_LOGIN");
                resp.put("usuarioId", usuarioId);
                resp.put("message", "Credenciales de Admin válidas. Requiere login en portal de administración.");

                return ResponseEntity.ok(resp);
            }

            resp.put("status", "2FA_REQUIRED");
            resp.put("usuarioId", usuarioId);
            resp.put("message", "Credenciales válidas. Requiere verificación 2FA.");

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(resp);

        } catch (RuntimeException e) {
            String mensajeError = e.getMessage();

            if (mensajeError != null && mensajeError.contains("Fallo en la validación de credenciales")) {

                String mensajeMySQL = mensajeError.substring("Fallo en la validación de credenciales: ".length());

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(mensajeMySQL);
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error interno del servidor. Inténtelo más tarde.");
        }
    }
}