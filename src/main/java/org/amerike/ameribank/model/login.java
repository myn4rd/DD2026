package org.amerike.ameribank.model;

public class login {

    private String usr;
    private String pwd;

    public login() {
    }

    public login(String usr, String pwd) {
        this.usr = usr;
        this.pwd = pwd;
    }

    public String getUsr() {
        return usr;
    }
    public String getPwd() {
        return pwd;
    }
}

/*
El modelo funciona como el de los recados entre el cliente y el servidor.
En este caso se está definiendo el usuario(usr) y contraseña (pwd) que se ponen en el login de la página
y usa los get para que el controlador y el DAO puedan leer el valor sin tener acceso directo a usr y pwd
*/
