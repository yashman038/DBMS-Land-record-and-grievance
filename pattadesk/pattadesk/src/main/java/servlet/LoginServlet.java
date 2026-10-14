package servlet;

import dao.UserDAO;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import model.User;
import util.Http;

/** POST /api/login  (email, password)  ->  creates the session and tells the browser where to go. */
@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserDAO users = new UserDAO();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String email = Http.str(req, "email");
        String password = req.getParameter("password");
        if (email == null || password == null || password.isEmpty()) {
            Http.error(resp, 400, "Enter your email and password.");
            return;
        }
        try {
            User user = users.authenticate(email, password);
            if (user == null) {
                Http.error(resp, 401, "Invalid email or password.");
                return;
            }
            HttpSession old = req.getSession(false);
            if (old != null) {
                old.invalidate();                      // new session id after login
            }
            HttpSession session = req.getSession(true);
            session.setMaxInactiveInterval(30 * 60);   // 30 minutes
            session.setAttribute("user", user);
            session.setAttribute("csrf", newToken());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("role", user.role());
            result.put("name", user.name());
            result.put("redirect", "admin".equals(user.role()) ? "admin/portal.html" : "user/portal.html");
            Http.json(resp, 200, result);
        } catch (SQLException e) {
            getServletContext().log("Login failed", e);
            Http.error(resp, 500, "Cannot reach the database. Check db.properties and that Oracle XE is running.");
        }
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
