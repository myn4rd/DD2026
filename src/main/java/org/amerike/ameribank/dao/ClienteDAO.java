package org.amerike.ameribank.dao;


import org.amerike.ameribank.config.ConexionDB;
import org.amerike.ameribank.model.cliente;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;


public class ClienteDAO {
    public void registrarCliente(cliente c) throws Exception {
        String sql = "CALL RegistraCliente(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";


        try (Connection conn = ConexionDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {


            stmt.setString(1, c.getNumeroCliente());
            stmt.setString(2, c.getNombre());
            stmt.setString(3, c.getApellidoPat());
            stmt.setString(4, c.getApellidoMat());
            stmt.setDate(5, java.sql.Date.valueOf(c.getFechaNac()));
            stmt.setString(6, c.getRfc());
            stmt.setString(7, c.getCurp());
            stmt.setString(8, c.getEmail());
            stmt.setString(9, c.getCelular());
            stmt.setString(10, c.getDireccion());
            stmt.setString(11, c.getCiudad());
            stmt.setString(12, c.getEstado());
            stmt.setString(13, c.getCp());
            stmt.setString(14, c.getEstatus());


            stmt.executeUpdate();
        }
    }

    public void actualizarCliente(cliente c) throws Exception {
        String sql = "UPDATE clientes SET numero_cliente=?, nombre=?, apellido_pat=?, apellido_mat=?, fecha_nac=?, rfc=?, curp=?, email=?, celular=?, direccion=?, ciudad=?, estado=?, cp=?, estatus=? WHERE cliente_id=?";
        try (Connection conn = ConexionDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, c.getNumeroCliente());
            stmt.setString(2, c.getNombre());
            stmt.setString(3, c.getApellidoPat());
            stmt.setString(4, c.getApellidoMat());
            stmt.setDate(5, java.sql.Date.valueOf(c.getFechaNac()));
            stmt.setString(6, c.getRfc());
            stmt.setString(7, c.getCurp());
            stmt.setString(8, c.getEmail());
            stmt.setString(9, c.getCelular());
            stmt.setString(10, c.getDireccion());
            stmt.setString(11, c.getCiudad());
            stmt.setString(12, c.getEstado());
            stmt.setString(13, c.getCp());
            stmt.setString(14, c.getEstatus());
            stmt.setInt(15, c.getClienteId());

            stmt.executeUpdate();
        }
    }


    public cliente buscarPorNumero(String numeroCliente) throws Exception {
        String sql = "SELECT * FROM clientes WHERE numero_cliente = ?";
        try (Connection conn = ConexionDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, numeroCliente);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                cliente c = new cliente();
                c.setClienteId(rs.getInt("cliente_id"));
                c.setNumeroCliente(rs.getString("numero_cliente"));
                c.setNombre(rs.getString("nombre"));
                c.setApellidoPat(rs.getString("apellido_pat"));
                c.setApellidoMat(rs.getString("apellido_mat"));
                c.setFechaNac(rs.getDate("fecha_nac").toLocalDate());
                c.setRfc(rs.getString("rfc"));
                c.setCurp(rs.getString("curp"));
                c.setEmail(rs.getString("email"));
                c.setCelular(rs.getString("celular"));
                c.setDireccion(rs.getString("direccion"));
                c.setCiudad(rs.getString("ciudad"));
                c.setEstado(rs.getString("estado"));
                c.setCp(rs.getString("cp"));
                c.setEstatus(rs.getString("estatus"));
                return c;
            }
            return null;
        }
    }

    public List<cliente> listarClientes() throws Exception {
        List<cliente> lista = new ArrayList<>();
        String sql = "SELECT * FROM clientes";

        try (Connection conn = ConexionDB.conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                cliente c = new cliente();
                c.setClienteId(rs.getInt("cliente_id"));
                c.setNumeroCliente(rs.getString("numero_cliente"));
                c.setNombre(rs.getString("nombre"));
                c.setApellidoPat(rs.getString("apellido_pat"));
                c.setApellidoMat(rs.getString("apellido_mat"));
                c.setFechaNac(rs.getDate("fecha_nac").toLocalDate());
                c.setRfc(rs.getString("rfc"));
                c.setCurp(rs.getString("curp"));
                c.setEmail(rs.getString("email"));
                c.setCelular(rs.getString("celular"));
                c.setDireccion(rs.getString("direccion"));
                c.setCiudad(rs.getString("ciudad"));
                c.setEstado(rs.getString("estado"));
                c.setCp(rs.getString("cp"));
                c.setEstatus(rs.getString("estatus"));
                lista.add(c);
            }

        } catch (Exception e) {
            throw new Exception("Error al listar clientes: " + e.getMessage(), e);
        }

        return lista;
    }






}

