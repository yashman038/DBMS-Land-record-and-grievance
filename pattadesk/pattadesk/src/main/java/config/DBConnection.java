package config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Central place that opens Oracle connections through JDBC (DriverManager).
 * Settings come from db.properties, or from the environment variables
 * DB_URL / DB_USER / DB_PASSWORD when those are set.
 * The browser never sees these values.
 */
public final class DBConnection {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = DBConnection.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException e) {
            System.err.println("Could not read db.properties: " + e.getMessage());
        }
    }

    private DBConnection() { }

    private static String setting(String envName, String propName) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        String fromFile = PROPS.getProperty(propName);
        return fromFile == null ? null : fromFile.trim();
    }

    /** Opens a new connection. The caller must close it (use try-with-resources). */
    public static Connection getConnection() throws SQLException {
        String url = setting("DB_URL", "db.url");
        String user = setting("DB_USER", "db.user");
        String password = setting("DB_PASSWORD", "db.password");

        if (url == null || user == null || password == null
                || user.startsWith("YOUR_") || password.startsWith("YOUR_")) {
            throw new SQLException("Database settings are missing. Edit src/main/resources/db.properties.");
        }
        try {
            Class.forName("oracle.jdbc.OracleDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Oracle JDBC driver (ojdbc11) is not on the classpath.", e);
        }
        return DriverManager.getConnection(url, user, password);
    }
}
