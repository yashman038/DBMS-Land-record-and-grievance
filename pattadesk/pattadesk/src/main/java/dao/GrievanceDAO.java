package dao;

import config.DBConnection;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.Grievance;
import util.Db;

/** SQL for the GRIEVANCE table and its stored procedures. */
public class GrievanceDAO {

    private static final String SELECT_GRIEVANCE =
            "SELECT g.grievance_id, g.ticket_id, g.parcel_id, p.survey_no, p.village, g.category, g.subject, "
            + "g.description, g.status, g.priority, g.filed_by, c.name AS citizen_name, c.email AS citizen_email, "
            + "c.phone AS citizen_phone, g.assigned_to, a.name AS admin_name, g.admin_remarks, "
            + "g.created_at, g.assigned_at, g.resolved_at "
            + "FROM grievance g "
            + "JOIN land_parcel p ON p.parcel_id = g.parcel_id "
            + "JOIN users c ON c.user_id = g.filed_by "
            + "LEFT JOIN users a ON a.user_id = g.assigned_to ";

    private static Grievance map(ResultSet rs) throws SQLException {
        return new Grievance(rs.getInt("grievance_id"), rs.getString("ticket_id"), rs.getInt("parcel_id"),
                rs.getString("survey_no"), rs.getString("village"), rs.getString("category"),
                rs.getString("subject"), rs.getString("description"), rs.getString("status"),
                rs.getString("priority"), rs.getInt("filed_by"), rs.getString("citizen_name"),
                rs.getString("citizen_email"), rs.getString("citizen_phone"), Db.integer(rs, "assigned_to"),
                rs.getString("admin_name"), rs.getString("admin_remarks"), Db.dateTime(rs, "created_at"),
                Db.dateTime(rs, "assigned_at"), Db.dateTime(rs, "resolved_at"));
    }

    /** citizenId = null means all grievances (admin). status and q are optional filters. */
    public List<Grievance> search(Integer citizenId, String status, String q) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_GRIEVANCE).append("WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (citizenId != null) {
            sql.append("AND g.filed_by = ? ");
            params.add(citizenId);
        }
        if (status != null) {
            sql.append("AND g.status = ? ");
            params.add(status);
        }
        if (q != null) {
            sql.append("AND (LOWER(g.ticket_id) LIKE ? OR LOWER(p.survey_no) LIKE ? OR LOWER(g.subject) LIKE ? "
                    + "OR LOWER(c.name) LIKE ?) ");
            String like = "%" + q.toLowerCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append("ORDER BY g.created_at DESC, g.grievance_id DESC");

        List<Grievance> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            Db.bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        }
        return out;
    }

    public Grievance findById(int grievanceId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_GRIEVANCE + "WHERE g.grievance_id = ?")) {
            ps.setInt(1, grievanceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public int countByCitizen(int citizenId, boolean onlyOpen) throws SQLException {
        String sql = "SELECT COUNT(*) FROM grievance WHERE filed_by = ?"
                + (onlyOpen ? " AND status NOT IN ('resolved','rejected')" : "");
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, citizenId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Count per status, always containing all five statuses. */
    public Map<String, Integer> countsByStatus() throws SQLException {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String s : new String[] {"submitted", "assigned", "in_progress", "resolved", "rejected"}) {
            out.put(s, 0);
        }
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT status, COUNT(*) FROM grievance GROUP BY status");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getString(1), rs.getInt(2));
            }
        }
        return out;
    }

    /** True if the same citizen filed the same subject for the same parcel in the last minute. */
    public boolean recentDuplicate(int citizenId, int parcelId, String subject) throws SQLException {
        String sql = "SELECT COUNT(*) FROM grievance WHERE filed_by = ? AND parcel_id = ? "
                + "AND LOWER(subject) = ? AND created_at > SYSDATE - 1/1440";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, citizenId);
            ps.setInt(2, parcelId);
            ps.setString(3, subject.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Inserts a grievance. grievance_id and ticket_id are generated by the Oracle trigger
     * TRG_GRIEVANCE_BIU; RETURNING gives the ticket number back to Java.
     */
    public String create(int citizenId, int parcelId, String category, String subject,
                         String description, String priority) throws SQLException {
        String sql = "BEGIN INSERT INTO grievance (parcel_id, filed_by, category, subject, description, priority) "
                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING ticket_id INTO ?; END;";
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (CallableStatement cs = con.prepareCall(sql)) {
                cs.setInt(1, parcelId);
                cs.setInt(2, citizenId);
                cs.setString(3, category);
                cs.setString(4, subject);
                cs.setString(5, description);
                cs.setString(6, priority);
                cs.registerOutParameter(7, Types.VARCHAR);
                cs.execute();
                String ticket = cs.getString(7);
                con.commit();
                return ticket;
            } catch (SQLException e) {
                Db.rollbackQuietly(con);
                throw e;
            }
        }
    }

    /** Calls SP_ASSIGN_GRIEVANCE(p_admin_id, p_grievance_id, p_assignee_id). */
    public void assign(int adminId, int grievanceId, int assigneeId) throws SQLException {
        callProcedure("{call sp_assign_grievance(?, ?, ?)}", adminId, grievanceId, assigneeId);
    }

    private void callProcedure(String call, int a, int b, int c) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (CallableStatement cs = con.prepareCall(call)) {
                cs.setInt(1, a);
                cs.setInt(2, b);
                cs.setInt(3, c);
                cs.execute();
                con.commit();
            } catch (SQLException e) {
                Db.rollbackQuietly(con);
                throw e;
            }
        }
    }

    /** Assigned -> In Progress. (The schema has no procedure for this step, so it is one guarded UPDATE.) */
    public boolean markInProgress(int grievanceId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE grievance SET status = 'in_progress' WHERE grievance_id = ? AND status = 'assigned'")) {
                ps.setInt(1, grievanceId);
                int rows = ps.executeUpdate();
                con.commit();
                return rows == 1;
            } catch (SQLException e) {
                Db.rollbackQuietly(con);
                throw e;
            }
        }
    }

    /** Calls SP_RESOLVE_GRIEVANCE(p_admin_id, p_grievance_id, p_remarks, p_outcome). */
    public void resolve(int adminId, int grievanceId, String remarks, String outcome) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (CallableStatement cs = con.prepareCall("{call sp_resolve_grievance(?, ?, ?, ?)}")) {
                cs.setInt(1, adminId);
                cs.setInt(2, grievanceId);
                cs.setString(3, remarks);
                cs.setString(4, outcome);
                cs.execute();
                con.commit();
            } catch (SQLException e) {
                Db.rollbackQuietly(con);
                throw e;
            }
        }
    }
}
