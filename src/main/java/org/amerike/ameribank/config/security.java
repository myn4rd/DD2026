package org.amerike.ameribank.config;

import javax.crypto.Cipher;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Scanner;

public class security {

    private static String homeDir = System.getProperty("user.home"); // establecemos el home PATH
    private static Path secretsDir = Paths.get(homeDir, ".config", "ameribank", "secrets");
    private static Path rutaCifrado = secretsDir.resolve("accesodbjava.enc");
    private static Path rutaPrivateKey = secretsDir.resolve("private_key.pem");
    private static Path rutaPublicKey = secretsDir.resolve("public_key.pem");



    private static boolean checkKeys() {
        try {
            Files.createDirectories(secretsDir); // se crean directorios
        }catch (Exception e) {
            System.err.println("Error al generar directorios");
        }

        if (Files.exists(rutaPrivateKey) && Files.exists(rutaPublicKey) && Files.exists(rutaCifrado)) { //verifica la existencia de los directorios
            System.out.println("Se encontraron los archivos de seguridad...");
            return true;
        } else {
            System.out.println("Creando archivos de cifrado.");
            return false;
        }
    }

    // Metodo generador de claves
    private static void generadorClaves() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        KeyPair parClaves = generador.generateKeyPair();

        // Guardar llave Privada
        guardarClavePEM(rutaPrivateKey.toString(), parClaves.getPrivate(), "PRIVATE KEY");

        // Guardar llave Pública
        guardarClavePEM(rutaPublicKey.toString(), parClaves.getPublic(), "PUBLIC KEY");
    }

    private static void guardarClavePEM(String archivo, Key clave, String tipo) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN " + tipo + "-----\n");
        sb.append(Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(clave.getEncoded()));
        sb.append("\n-----END " + tipo + "-----\n");

        try (FileWriter fw = new FileWriter(archivo)) {
            fw.write(sb.toString());
        }
    }
    /// Metodo cifrador de credenciales
    private static void cifrador(String NombreBaseDeDatos, String User, String Password) throws Exception {

        byte[] claveBytes = Files.readAllBytes(Paths.get(rutaPublicKey.toString()));
        PublicKey clavePublica = cargarClavePublica(claveBytes);


        String dbHost = System.getProperty("db.host", "40.0.4.12");
        String dbPort = System.getProperty("db.port", "3306");
        String datos = String.format("\"jdbc:mysql://%s:%s/%s\"|\"%s\"|\"%s\"", dbHost, dbPort, NombreBaseDeDatos, User, Password);
        Cipher cifrador = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cifrador.init(Cipher.ENCRYPT_MODE, clavePublica);
        byte[] cifrado = cifrador.doFinal(datos.getBytes());

        Files.write(rutaCifrado, cifrado);
    }

    private static PublicKey cargarClavePublica(byte[] pem) throws Exception {
        String contenido = new String(pem).replaceAll("-----.*-----", "").replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(contenido);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    /// Metodo descifrador de credenciales
    public static String[] obtenerCredenciales() throws Exception {
        byte[] claveBytes = Files.readAllBytes(Paths.get(rutaPrivateKey.toString()));
        PrivateKey clavePrivada = cargarClavePrivada(claveBytes);

        byte[] cifrado = Files.readAllBytes(rutaCifrado);
        Cipher descifrador = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        descifrador.init(Cipher.DECRYPT_MODE, clavePrivada);
        byte[] descifrado = descifrador.doFinal(cifrado);

        return new String(descifrado).split("\\|");
    }

    private static PrivateKey cargarClavePrivada(byte[] pem) throws Exception {
        String contenido = new String(pem).replaceAll("-----.*-----", "").replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(contenido);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        return KeyFactory.getInstance("RSA").generatePrivate(spec);
    }
    /// Metodo input del usuario
    private static void escribirCredenciales() {
        try {
            System.out.println("Cifrando credenciales.");
            Scanner sc = new Scanner(System.in);
            System.out.print("Nombre de la Base de Datos: ");
            String NombreBaseDeDatos = sc.nextLine();
            System.out.print("Nombre de usuario: ");
            String User = sc.nextLine();
            System.out.print("Password: ");
            String Password = sc.nextLine();
            cifrador(NombreBaseDeDatos, User, Password);
            System.out.println("Cifrado finalizado");
        } catch (Exception e){
            System.err.println("Error al cifrar credenciales");
            System.exit(1);
        }
    }
    // GetUrl
    public static String obtenerUrl() {
        try {
            return obtenerCredenciales()[0].replace("\"", "");
        } catch (Exception e) {
            throw new IllegalStateException("Error al obtener URL de credenciales", e);
        }
    }
    // GetUser
    public static String obtenerUsuario() {
        try {
            return obtenerCredenciales()[1].replace("\"", "");
        } catch (Exception e) {
            throw new IllegalStateException("Error al obtener usuario de credenciales", e);
        }
    }
    // getPassword
    public static String obtenerPassword() {
        try {
            return obtenerCredenciales()[2].replace("\"", "");
        } catch (Exception e) {
            throw new IllegalStateException("Error al obtener password de credenciales", e);
        }
    }
    ///
    // metodo inicializador
    public static void init() {
        if (checkKeys()) {
            System.out.println("Continuando...");
        } else {
           try {
               generadorClaves();
               System.out.println("LLaves generadas...");
           } catch (Exception e ){
               System.err.println("Error al generar las llaves");
               System.exit(1);
           }
           escribirCredenciales();
        }
    }

}
