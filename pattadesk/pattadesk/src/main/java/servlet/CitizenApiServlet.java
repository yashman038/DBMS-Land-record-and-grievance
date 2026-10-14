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
 * Everything a logged-in citizen can do. Mapped to /api/citizen/*.
 * The citizen's id always comes from the session, never from the browser.
 */
@WebServlet("/api/citizen/*")
public class CitizenApiServlet extends HttpServlet {

    static final Set<String> CATEGORIES =
            Set.of("encroachment", "boundary_dispute", "incorrect_record", "mutation_delay", "other");
    static final Set<String> PRIORITIES = Set.of("low", "medium", "high");

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
                case "/dashboard" -> Http.json(resp, 200, dashboard(me(req)));
                case "/profile" -> Http.json(resp, 200, users.findById(me(req).userId()));
                case "/parcels" -> Http.json(resp, 200,
                        parcels.search(me(req).userId(), Http.str(req, "q"), null, null, null));
                case "/parcel" -> Http.json(resp, 200, parcelDetail(req));
                case "/grievances" -> Http.json(resp, 200,
                        grievances.search(me(req).userId(), Http.str(req, "status"), Http.str(req, "q")));
                case "/grievance" -> Http.json(resp, 200, grievanceDetail(req));
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
            if ("/grievance".equals(path)) {
                Http.json(resp, 201, submitGrievance(req));
            } else {
                Http.error(resp, 404, "Unknown request.");
            }
        } catch (ApiException e) {
            Http.error(resp, e.status, e.getMessage());
        } catch (SQLException e) {
            Http.sqlError(resp, getServletContext(), e);
        }
    }

    private Map<String, Object> dashboard(User user) throws SQLException {
        List<LandParcel> mine = parcels.search(user.userId(), null, null, null, null);
        List<Grievance> filed = grievances.search(user.userId(), null, null);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", user.name());
        out.put("parcelCount", parcels.countByOwner(user.userId()));
        out.put("grievanceTotal", grievances.countByCitizen(user.userId(), false));
        out.put("grievanceOpen", grievances.countByCitizen(user.userId(), true));
        out.put("recentParcels", mine.subList(0, Math.min(3, mine.size())));
        out.put("recentGrievances", filed.subList(0, Math.min(3, filed.size())));
        return out;
    }

    /** Private parcel details: only the current owner may open them. */
    private Map<String, Object> parcelDetail(HttpServletRequest req) throws SQLException {
        int id = Http.requireInt(req, "id");
        LandParcel p = parcels.findById(id);
        if (p == null || p.ownerId() != me(req).userId()) {
            throw new ApiException(403, "You are not authorised to view this land record.");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("parcel", p);
        out.put("history", parcels.history(id));
        out.put("prices", parcels.prices(id));
        return out;
    }

    private Grievance grievanceDetail(HttpServletRequest req) throws SQLException {
        Grievance g = grievances.findById(Http.requireInt(req, "id"));
        if (g == null || g.citizenId() != me(req).userId()) {
            throw new ApiException(404, "Grievance not found.");
        }
        return g;
    }

    private Map<String, Object> submitGrievance(HttpServletRequest req) throws SQLException {
        User user = me(req);
        int parcelId = Http.requireInt(req, "parcelId");
        String category = Http.requireOneOf(req, "category", "category", CATEGORIES);
        String priority = Http.requireOneOf(req, "priority", "priority", PRIORITIES);
        String subject = Http.requireText(req, "subject", "Subject", 150);
        String description = Http.requireText(req, "description", "Description", 1000);

        LandParcel p = parcels.findById(parcelId);
        if (p == null || p.ownerId() != user.userId()) {
            throw new ApiException(403, "You can only file a grievance about land registered in your name.");
        }
        if (grievances.recentDuplicate(user.userId(), parcelId, subject)) {
            throw new ApiException(409, "This grievance was just submitted. Please wait a moment.");
        }
        String ticket = grievances.create(user.userId(), parcelId, category, subject, description, priority);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ticketId", ticket);
        out.put("message", "Grievance submitted.");
        return out;
    }
}
