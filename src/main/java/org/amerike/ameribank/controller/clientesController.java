package org.amerike.ameribank.controller;

import org.amerike.ameribank.dao.ClienteDAO;
import org.amerike.ameribank.model.cliente;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/clientes")
public class clientesController {

    @PostMapping("/registrar")
    public String registrar(@RequestParam String numeroCliente,
                            @RequestParam String nombre,
                            @RequestParam String apellidoPat,
                            @RequestParam(required = false) String apellidoMat,
                            @RequestParam String fechaNac,
                            @RequestParam String rfc,
                            @RequestParam String curp,
                            @RequestParam String email,
                            @RequestParam String celular,
                            @RequestParam(required = false) String direccion,
                            @RequestParam(required = false) String ciudad,
                            @RequestParam(required = false) String estado,
                            @RequestParam(required = false) String cp,
                            @RequestParam String estatus) {
        try {
            cliente c = new cliente();
            c.setNumeroCliente(numeroCliente);
            c.setNombre(nombre);
            c.setApellidoPat(apellidoPat);
            c.setApellidoMat(apellidoMat);
            c.setFechaNac(LocalDate.parse(fechaNac));
            c.setRfc(rfc);
            c.setCurp(curp);
            c.setEmail(email);
            c.setCelular(celular);
            c.setDireccion(direccion);
            c.setCiudad(ciudad);
            c.setEstado(estado);
            c.setCp(cp);
            c.setEstatus(estatus);

            new ClienteDAO().registrarCliente(c);
            return "Cliente registrado correctamente";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error al registrar cliente: " + e.getMessage();
        }
    }


    @GetMapping("/todos")
    public List<cliente> obtenerTodos() {
        try {
            return new ClienteDAO().listarClientes();
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }



    @PostMapping("/actualizar")
    public String actualizar(@RequestParam int clienteId,
                             @RequestParam String numeroCliente,
                             @RequestParam String nombre,
                             @RequestParam String apellidoPat,
                             @RequestParam(required = false) String apellidoMat,
                             @RequestParam String fechaNac,
                             @RequestParam String rfc,
                             @RequestParam String curp,
                             @RequestParam String email,
                             @RequestParam String celular,
                             @RequestParam(required = false) String direccion,
                             @RequestParam(required = false) String ciudad,
                             @RequestParam(required = false) String estado,
                             @RequestParam(required = false) String cp,
                             @RequestParam String estatus) {
        try {
            cliente c = new cliente();
            c.setClienteId(clienteId);
            c.setNumeroCliente(numeroCliente);
            c.setNombre(nombre);
            c.setApellidoPat(apellidoPat);
            c.setApellidoMat(apellidoMat);
            c.setFechaNac(LocalDate.parse(fechaNac));
            c.setRfc(rfc);
            c.setCurp(curp);
            c.setEmail(email);
            c.setCelular(celular);
            c.setDireccion(direccion);
            c.setCiudad(ciudad);
            c.setEstado(estado);
            c.setCp(cp);
            c.setEstatus(estatus);

            new ClienteDAO().actualizarCliente(c);
            return "Cliente actualizado correctamente";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error al actualizar cliente: " + e.getMessage();
        }
    }
}

