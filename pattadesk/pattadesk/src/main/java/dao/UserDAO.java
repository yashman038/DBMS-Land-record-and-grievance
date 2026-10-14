package dao;

import config.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.User;
import model.UserSummary;
import org.mindrot.jbcrypt.BCrypt;
import util.Db;

/** All SQL that touches the USERS table. */
public class UserDAO {

    private static String mask(String aadhaar) {
        if (aadhaar == null || aadhaar.length() < 4) {
            return "XXXX";
        }
        return "XXXXXXXX" + aadhaar.substring(aadhaar.length() - 4);
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        return new User(rs.getInt("user_id"), rs.getString("name"), rs.getString("email"),
                mask(rs.getString("aadhaar")), rs.getString("phone"), rs.getString("role"),
                Db.date(rs, "created_at"));
    }

    /** Returns the user when email and password match, otherwise null. */
    public User authenticate(String email, String password) throws SQLException {
        String sql = "SELECT user_id, name, email, aadhaar, phone, role, created_at, password_hash "
                + "FROM users WHERE LOWER(email) = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String hash = rs.getString("password_hash");
                boolean ok;
                try {
                    ok = BCrypt.checkpw(password, hash);
                } catch (IllegalArgumentException notABcryptHash) {
                    ok = false; // demo accounts must be initialised with tools.DemoSetup first
                }
                return ok ? mapUser(rs) : null;
            }
        }
    }

    public User findById(int userId) throws SQLException {
        String sql = "SELECT user_id, name, email, aadhaar, phone, role, created_at FROM users WHERE user_id = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        }
    }

    public List<UserSummary> listAll() throws SQLException {
        String sql = "SELECT u.user_id, u.name, u.email, u.phone, u.role, u.created_at, "
                + "(SELECT COUNT(*) FROM land_parcel p WHERE p.current_owner_id = u.user_id) AS parcel_count, "
                + "(SELECT COUNT(*) FROM grievance g WHERE g.filed_by = u.user_id) AS grievance_count "
                + "FROM users u ORDER BY u.role, u.user_id";
        List<UserSummary> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(new UserSummary(rs.getInt("user_id"), rs.getString("name"), rs.getString("email"),
                        rs.getString("phone"), rs.getString("role"), Db.date(rs, "created_at"),
                        rs.getInt("parcel_count"), rs.getInt("grievance_count")));
            }
        }
        return out;
    }

    /** Role of a user, or null if there is no such user. */
    public String roleOf(int userId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT role FROM users WHERE user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    public int countByRole(String role) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM users WHERE role = ?")) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Used by the admin "check database connection" button. */
    public Map<String, String> connectionInfo() throws SQLException {
        Map<String, String> info = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT USER, SYS_CONTEXT('USERENV','CON_NAME'), SYS_CONTEXT('USERENV','DB_NAME') FROM dual")) {
            rs.next();
            info.put("connected", "yes");
            info.put("schemaUser", rs.getString(1));
            info.put("service", rs.getString(2));
            info.put("database", rs.getString(3));
        }
        return info;
    }
}
