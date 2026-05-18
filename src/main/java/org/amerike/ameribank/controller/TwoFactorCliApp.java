package org.amerike.ameribank.controller;

public class TwoFactorCliApp {
    public static void main(String[] args) {
        TwoFactorService svc = new TwoFactorService();
        try {
            svc.promptCli(1, "APP", true, false, false);
        } finally {
            svc.shutdown();
        }
    }
}
