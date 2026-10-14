package servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import model.User;
import util.Http;

/** GET /api/me  ->  who is logged in, plus the CSRF token the page must send with every POST. */
@WebServlet("/api/me")
public class MeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User user = (User) req.getSession().getAttribute("user");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("userId", user.userId());
        out.put("name", user.name());
        out.put("role", user.role());
        out.put("csrf", req.getSession().getAttribute("csrf"));
        Http.json(resp, 200, out);
    }
}
