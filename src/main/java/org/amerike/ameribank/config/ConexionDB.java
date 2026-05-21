package org.amerike.ameribank.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

public class ConexionDB {

    private static volatile HikariDataSource dataSource;

    private static HikariDataSource getDataSource() throws SQLException {
        HikariDataSource ds = dataSource;
        if (ds == null) {
            synchronized (ConexionDB.class) {
                ds = dataSource;
                if (ds == null) {
                    ds = build();
                    dataSource = ds;
                }
            }
        }
        return ds;
    }

    private static HikariDataSource build() throws SQLException {
        String[] creds;
        try {
            creds = security.obtenerCredenciales();
        } catch (Exception e) {
            throw new SQLException("No se pudieron descifrar las credenciales de BD", e);
        }
        if (creds == null || creds.length < 3) {
            throw new SQLException("Credenciales descifradas con formato invalido");
        }

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(creds[0].replace("\"", ""));
        cfg.setUsername(creds[1].replace("\"", ""));
        cfg.setPassword(creds[2].replace("\"", ""));
        cfg.setDriverClassName("org.mariadb.jdbc.Driver");
        cfg.setPoolName("AmeribankCP");

        cfg.setMaximumPoolSize(30);
        cfg.setMinimumIdle(5);
        cfg.setConnectionTimeout(3_000);
        cfg.setValidationTimeout(2_000);
        cfg.setIdleTimeout(60_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setLeakDetectionThreshold(10_000);

        cfg.addDataSourceProperty("cachePrepStmts", "true");
        cfg.addDataSourceProperty("prepStmtCacheSize", "250");
        cfg.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        cfg.addDataSourceProperty("useServerPrepStmts", "true");
        cfg.addDataSourceProperty("useLocalSessionState", "true");
        cfg.addDataSourceProperty("rewriteBatchedStatements", "true");

        return new HikariDataSource(cfg);
    }

    public static Connection conectar() throws SQLException {
        return getDataSource().getConnection();
    }

    public static void cerrar() {
        HikariDataSource ds = dataSource;
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }
}
