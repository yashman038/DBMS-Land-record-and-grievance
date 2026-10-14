package dao;

import config.DBConnection;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import model.LandParcel;
import model.OwnershipHistory;
import model.PriceRecord;
import util.Db;

/** SQL for LAND_PARCEL, OWNERSHIP_HISTORY and PRICE_HISTORY. */
public class LandParcelDAO {

    private static final String SELECT_PARCEL =
            "SELECT p.parcel_id, p.survey_no, p.district, p.taluk, p.village, p.area_acres, p.land_type, "
            + "p.land_use, p.latitude, p.longitude, p.market_value, p.status, p.encumbrance, "
            + "p.current_owner_id, u.name AS owner_name, p.created_at "
            + "FROM land_parcel p JOIN users u ON u.user_id = p.current_owner_id ";

    private static LandParcel map(ResultSet rs) throws SQLException {
        return new LandParcel(rs.getInt("parcel_id"), rs.getString("survey_no"), rs.getString("district"),
                rs.getString("taluk"), rs.getString("village"), rs.getDouble("area_acres"),
                rs.getString("land_type"), rs.getString("land_use"),
                Db.dbl(rs, "latitude"), Db.dbl(rs, "longitude"), Db.dbl(rs, "market_value"),
                rs.getString("status"), rs.getString("encumbrance"),
                rs.getInt("current_owner_id"), rs.getString("owner_name"), Db.date(rs, "created_at"));
    }

    /**
     * Searches parcels. ownerId = null means "all parcels" (admin); otherwise only that citizen's parcels.
     * Every filter is optional.
     */
    public List<LandParcel> search(Integer ownerId, String q, String district, String village, String status)
            throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT_PARCEL).append("WHERE 1 = 1 ");
        List<Object> params = new ArrayList<>();
        if (ownerId != null) {
            sql.append("AND p.current_owner_id = ? ");
            params.add(ownerId);
        }
        if (q != null) {
            sql.append("AND (LOWER(p.survey_no) LIKE ? OR LOWER(p.village) LIKE ? OR LOWER(p.district) LIKE ? "
                    + "OR LOWER(u.name) LIKE ?) ");
            String like = "%" + q.toLowerCase() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (district != null) {
            sql.append("AND p.district = ? ");
            params.add(district);
        }
        if (village != null) {
            sql.append("AND p.village = ? ");
            params.add(village);
        }
        if (status != null) {
            sql.append("AND p.status = ? ");
            params.add(status);
        }
        sql.append("ORDER BY p.parcel_id");

        List<LandParcel> out = new ArrayList<>();
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

    public LandParcel findById(int parcelId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SELECT_PARCEL + "WHERE p.parcel_id = ?")) {
            ps.setInt(1, parcelId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        }
    }

    public List<OwnershipHistory> history(int parcelId) throws SQLException {
        String sql = "SELECT ownership_id, owner_name, start_date, end_date, transfer_type, deed_no, "
                + "consideration_amount, remarks FROM ownership_history WHERE parcel_id = ? "
                + "ORDER BY start_date, ownership_id";
        List<OwnershipHistory> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, parcelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new OwnershipHistory(rs.getInt("ownership_id"), rs.getString("owner_name"),
                            Db.date(rs, "start_date"), Db.date(rs, "end_date"), rs.getString("transfer_type"),
                            rs.getString("deed_no"), Db.dbl(rs, "consideration_amount"), rs.getString("remarks")));
                }
            }
        }
        return out;
    }

    public List<PriceRecord> prices(int parcelId) throws SQLException {
        String sql = "SELECT rec_year, market_value, govt_value FROM price_history WHERE parcel_id = ? "
                + "ORDER BY rec_year DESC";
        List<PriceRecord> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, parcelId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PriceRecord(rs.getInt("rec_year"), rs.getDouble("market_value"),
                            rs.getDouble("govt_value")));
                }
            }
        }
        return out;
    }

    public List<String> districts() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT DISTINCT district FROM land_parcel ORDER BY district");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    public List<String> villages() throws SQLException {
        List<String> out = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT DISTINCT village FROM land_parcel ORDER BY village");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getString(1));
            }
        }
        return out;
    }

    public int countAll() throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM land_parcel");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public int countByOwner(int userId) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM land_parcel WHERE current_owner_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ---------- stored procedures (CallableStatement) ----------

    /** Calls SP_UPDATE_PARCEL_STATUS(p_admin_id, p_parcel_id, p_status, p_encumbrance). */
    public void updateStatus(int adminId, int parcelId, String status, String encumbrance) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (CallableStatement cs = con.prepareCall("{call sp_update_parcel_status(?, ?, ?, ?)}")) {
                cs.setInt(1, adminId);
                cs.setInt(2, parcelId);
                cs.setString(3, status);
                if (encumbrance == null) {
                    cs.setNull(4, Types.VARCHAR);
                } else {
                    cs.setString(4, encumbrance);
                }
                cs.execute();
                con.commit();
            } catch (SQLException e) {
                Db.rollbackQuietly(con);
                throw e;
            }
        }
    }

    /** Calls SP_TRANSFER_OWNERSHIP(admin, parcel, new owner, type, deed no, amount, date). */
    public void transferOwnership(int adminId, int parcelId, int newOwnerId, String transferType,
                                  String deedNo, Double amount, LocalDate date) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (CallableStatement cs = con.prepareCall("{call sp_transfer_ownership(?, ?, ?, ?, ?, ?, ?)}")) {
                cs.setInt(1, adminId);
                cs.setInt(2, parcelId);
                cs.setInt(3, newOwnerId);
                cs.setString(4, transferType);
                if (deedNo == null) {
                    cs.setNull(5, Types.VARCHAR);
                } else {
                    cs.setString(5, deedNo);
                }
                if (amount == null) {
                    cs.setNull(6, Types.NUMERIC);
                } else {
                    cs.setDouble(6, amount);
                }
                cs.setDate(7, java.sql.Date.valueOf(date));
                cs.execute();
                con.commit();
            } catch (SQLException e) {
                Db.rollbackQuietly(con);
                throw e;
            }
        }
    }
}
