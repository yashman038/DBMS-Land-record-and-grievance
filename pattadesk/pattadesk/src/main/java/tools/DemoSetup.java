package tools;

import config.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.mindrot.jbcrypt.BCrypt;

/**
 * One-time helper for the demo. The sample users in the SQL script only have dummy text in
 * PASSWORD_HASH, so nobody could log in. This program replaces any value that is not already a
 * BCrypt hash with a real BCrypt hash of the demo password below.
 *
 * Run:   mvn compile exec:java
 */
public class DemoSetup {

    private static final String DEMO_PASSWORD = "Demo@1234";

    public static void main(String[] args) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            int updated = 0;
            try (PreparedStatement sel = con.prepareStatement(
                         "SELECT user_id, email, role, password_hash FROM users ORDER BY user_id");
                 PreparedStatement upd = con.prepareStatement(
                         "UPDATE users SET password_hash = ? WHERE user_id = ?");
                 ResultSet rs = sel.executeQuery()) {
                System.out.println("USER_ID  ROLE      EMAIL");
                while (rs.next()) {
                    String hash = rs.getString("password_hash");
                    boolean isBcrypt = hash != null && hash.startsWith("$2");
                    if (!isBcrypt) {
                        upd.setString(1, BCrypt.hashpw(DEMO_PASSWORD, BCrypt.gensalt(10)));
                        upd.setInt(2, rs.getInt("user_id"));
                        upd.executeUpdate();
                        updated++;
                    }
                    System.out.printf("%-8d %-9s %s%n", rs.getInt("user_id"), rs.getString("role"),
                            rs.getString("email"));
                }
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
            con.commit();
            System.out.println();
            System.out.println(updated + " account(s) initialised. Demo password for those accounts: " + DEMO_PASSWORD);
        }
    }
}
