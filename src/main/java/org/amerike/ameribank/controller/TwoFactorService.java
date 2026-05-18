package org.amerike.ameribank.controller;

import org.amerike.ameribank.dao.TwoFactorDao;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TwoFactorService {
// keygen
    private static final String CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ123456789";
    private static final int CODE_LENGTH = 7;
    private static final int EXPIRY_SECONDS = 30;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TwoFactorDao dao = new TwoFactorDao();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CHARSET.charAt(RANDOM.nextInt(CHARSET.length())));
        }
        return sb.toString();
    }

    public String generateAndStore(int usuarioId, String tipo2Fa, boolean habilitado,
                                   boolean telefonoVerif, boolean emailVerif) throws SQLException {
        String code = generateCode();
        dao.upsert2Fa(usuarioId, tipo2Fa, habilitado, code, telefonoVerif, emailVerif);
        scheduleBlacklist(code, usuarioId);
        return code;
    }

    private void scheduleBlacklist(String code, Integer usuarioId) {
        scheduler.schedule(() -> {
            try {
                dao.insertBlacklist(code, Timestamp.from(Instant.now()), usuarioId);
            } catch (SQLException e) {
                // log;ignore
            }
        }, EXPIRY_SECONDS, TimeUnit.SECONDS);
    }

    public boolean verify(int usuarioId, String supplied) throws SQLException {
        if (dao.isBlacklisted(supplied, usuarioId)) return false;
        String stored = dao.getCodigoSecreto(usuarioId);
        if (stored != null && stored.equals(supplied)) {
            dao.insertBlacklist(supplied, Timestamp.from(Instant.now()), usuarioId);
            return true;
        }
        return false;
    }

    public void shutdown() { scheduler.shutdownNow(); }
// cli connection
    public void promptCli(int usuarioId, String tipo2Fa, boolean habilitado,
                          boolean telefonoVerif, boolean emailVerif) {
        try {
            String code = generateAndStore(usuarioId, tipo2Fa, habilitado, telefonoVerif, emailVerif);
            System.out.println("2FA code: " + code);
            System.out.println("Expires in " + EXPIRY_SECONDS + " seconds. Enter code to verify:");
            Scanner sc = new Scanner(System.in);
            String input = sc.nextLine().trim();
            boolean ok = verify(usuarioId, input);
            System.out.println(ok ? "2FA verified." : "Verification failed or expired.");
        } catch (SQLException ex) {
            System.err.println("DB error: " + ex.getMessage());
        }
    }
}
