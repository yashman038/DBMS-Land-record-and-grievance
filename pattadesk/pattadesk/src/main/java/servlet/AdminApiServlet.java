package servlet;

import dao.GrievanceDAO;
import dao.LandParcelDAO;
import dao.UserDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import model.Grievance;
import model.LandParcel;
import model.User;
import util.ApiException;
import util.Http;

/**
 * Everything an administrator can do. Mapped to /api/admin/*.
 * AuthFilter already blocks non-admins; the Oracle procedures check the role again (FN_IS_ADMIN).
 */
@WebServlet("/api/admin/*")
public class AdminApiServlet extends HttpServlet {

    private static final Set<String> PARCEL_STATUSES = Set.of("clear", "mortgaged", "disputed");
    private static final Set<String> GRIEVANCE_STATUSES =
            Set.of("submitted", "assigned", "in_progress", "resolved", "rejected");
    private static final Set<String> TRANSFER_TYPES =
            Set.of("sale", "inheritance", "gift", "partition", "court_order");
    private static final Set<String> OUTCOMES = Set.of("resolved", "rejected");

    private final UserDAO users = new UserDAO();
    private final LandParcelDAO parcels = new LandParcelDAO();
    private final GrievanceDAO grievances = new GrievanceDAO();

    private static User me(HttpServletRequest req) {
        return (User) req.getSession().getAttribute("user");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            switch (path) {
                case "/dashboard" -> Http.json(resp, 200, dashboard());
                case "/parcels" -> Http.json(resp, 200, parcelList(req));
                case "/parcel" -> Http.json(resp, 200, parcelDetail(req));
                case "/grievances" -> Http.json(resp, 200, grievances.search(null,
                        optionalStatus(req), Http.str(req, "q")));
                case "/grievance" -> Http.json(resp, 200, grievanceDetail(req));
                case "/users" -> Http.json(resp, 200, users.listAll());
                case "/dbtest" -> Http.json(resp, 200, users.connectionInfo());
                default -> Http.error(resp, 404, "Unknown request.");
            }
        } catch (ApiException e) {
            Http.error(resp, e.status, e.getMessage());
        } catch (SQLException e) {
            Http.sqlError(resp, getServletContext(), e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        try {
            switch (path) {
                case "/parcel/status" -> updateParcelStatus(req);
                case "/parcel/transfer" -> transferOwnership(req);
                case "/grievance/assign" -> assignGrievance(req);
                case "/grievance/progress" -> startProgress(req);
                case "/grievance/resolve" -> resolveGrievance(req);
                default -> {
                    Http.error(resp, 404, "Unknown request.");
                    return;
                }
            }
            Http.json(resp, 200, Map.of("ok", true));
        } catch (ApiException e) {
            Http.error(resp, e.status, e.getMessage());
        } catch (SQLException e) {
            Http.sqlError(resp, getServletContext(), e);
        }
    }

    // ---------- reads ----------

    private Map<String, Object> dashboard() throws SQLException {
        List<Grievance> all = grievances.search(null, null, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("citizens", users.countByRole("citizen"));
        out.put("parcels", parcels.countAll());
        out.put("byStatus", grievances.countsByStatus());
        out.put("recentGrievances", all.subList(0, Math.min(5, all.size())));
        return out;
    }

    private Map<String, Object> parcelList(HttpServletRequest req) throws SQLException {
        String status = Http.str(req, "status");
        if (status != null && !PARCEL_STATUSES.contains(status)) {
            throw new ApiException(400, "Unknown land status.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("parcels", parcels.search(null, Http.str(req, "q"), Http.str(req, "district"),
                Http.str(req, "village"), status));
        out.put("districts", parcels.districts());
        out.put("villages", parcels.villages());
        return out;
    }

    private Map<String, Object> parcelDetail(HttpServletRequest req) throws SQLException {
        int id = Http.requireInt(req, "id");
        LandParcel p = parcels.findById(id);
        if (p == null) {
            throw new ApiException(404, "Land record not found.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("parcel", p);
        out.put("history", parcels.history(id));
        out.put("prices", parcels.prices(id));
        return out;
    }

    private Grievance grievanceDetail(HttpServletRequest req) throws SQLException {
        Grievance g = grievances.findById(Http.requireInt(req, "id"));
        if (g == null) {
            throw new ApiException(404, "Grievance not found.");
        }
        return g;
    }

    private static String optionalStatus(HttpServletRequest req) {
        String s = Http.str(req, "status");
        if (s != null && !GRIEVANCE_STATUSES.contains(s)) {
            throw new ApiException(400, "Unknown grievance status.");
        }
        return s;
    }

    // ---------- actions ----------

    /** Land status (clear / mortgaged / disputed) via SP_UPDATE_PARCEL_STATUS. */
    private void updateParcelStatus(HttpServletRequest req) throws SQLException {
        int parcelId = Http.requireInt(req, "parcelId");
        String status = Http.requireOneOf(req, "status", "land status", PARCEL_STATUSES);
        String encumbrance = Http.optionalText(req, "encumbrance", "Encumbrance details", 300);
        if ("clear".equals(status)) {
            encumbrance = null;
        } else if (encumbrance == null) {
            throw new ApiException(400, "Enter the encumbrance / dispute details for a "
                    + status + " parcel.");
        }
        parcels.updateStatus(me(req).userId(), parcelId, status, encumbrance);
    }

    /** Ownership transfer via SP_TRANSFER_OWNERSHIP (history is closed and appended, never edited). */
    private void transferOwnership(HttpServletRequest req) throws SQLException {
        int parcelId = Http.requireInt(req, "parcelId");
        int newOwnerId = Http.requireInt(req, "newOwnerId");
        String type = Http.requireOneOf(req, "transferType", "transfer type", TRANSFER_TYPES);
        String deedNo = Http.optionalText(req, "deedNo", "Deed number", 40);
        Double amount = Http.optionalAmount(req, "amount");
        LocalDate date = Http.optionalDate(req, "transferDate");
        if (date == null) {
            date = LocalDate.now();
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ApiException(400, "The transfer date cannot be in the future.");
        }
        if (!"citizen".equals(users.roleOf(newOwnerId))) {
            throw new ApiException(400, "The new owner must be a registered citizen.");
        }
        parcels.transferOwnership(me(req).userId(), parcelId, newOwnerId, type, deedNo, amount, date);
    }

    private void assignGrievance(HttpServletRequest req) throws SQLException {
        int grievanceId = Http.requireInt(req, "grievanceId");
        int assigneeId = Http.requireInt(req, "assigneeId");
        Grievance g = grievanceDetail(grievanceId);
        if (!Set.of("submitted", "assigned", "in_progress").contains(g.status())) {
            throw new ApiException(409, "A closed grievance cannot be reassigned.");
        }
        if (!"admin".equals(users.roleOf(assigneeId))) {
            throw new ApiException(400, "A grievance can only be assigned to an admin.");
        }
        grievances.assign(me(req).userId(), grievanceId, assigneeId);
    }

    private void startProgress(HttpServletRequest req) throws SQLException {
        int grievanceId = Http.requireInt(req, "grievanceId");
        grievanceDetail(grievanceId);
        if (!grievances.markInProgress(grievanceId)) {
            throw new ApiException(409, "Only an assigned grievance can be moved to In Progress.");
        }
    }

    private void resolveGrievance(HttpServletRequest req) throws SQLException {
        int grievanceId = Http.requireInt(req, "grievanceId");
        String outcome = Http.requireOneOf(req, "outcome", "outcome", OUTCOMES);
        String remarks = Http.requireText(req, "remarks", "Admin remarks", 1000);
        Grievance g = grievanceDetail(grievanceId);
        if (!Set.of("assigned", "in_progress").contains(g.status())) {
            throw new ApiException(409, "A grievance must be assigned (and not closed) before it can be "
                    + outcome + ".");
        }
        grievances.resolve(me(req).userId(), grievanceId, remarks, outcome);
    }

    private Grievance grievanceDetail(int id) throws SQLException {
        Grievance g = grievances.findById(id);
        if (g == null) {
            throw new ApiException(404, "Grievance not found.");
        }
        return g;
    }
}
