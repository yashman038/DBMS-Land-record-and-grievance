package filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import model.User;
import util.Http;

/**
 * Server-side access control for every API call and for the portal pages:
 *  - not logged in            -> 401 (API) or redirect to the login page
 *  - wrong role for the area  -> 403 (API) or redirect to the user's own portal
 *  - POST without valid CSRF  -> 403
 */
@WebFilter(urlPatterns = {"/api/*", "/user/*", "/admin/*"})
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        req.setCharacterEncoding("UTF-8");

        String path = req.getServletPath() + (req.getPathInfo() == null ? "" : req.getPathInfo());
        boolean isApi = path.startsWith("/api/");

        if (path.equals("/api/login")) {          // the only public endpoint
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = req.getSession(false);
        User user = session == null ? null : (User) session.getAttribute("user");

        if (user == null) {
            if (isApi) {
                Http.error(resp, 401, "Please log in.");
            } else {
                resp.sendRedirect(req.getContextPath() + "/index.html");
            }
            return;
        }

        String needRole = null;
        if (path.startsWith("/admin/") || path.startsWith("/api/admin/")) {
            needRole = "admin";
        } else if (path.startsWith("/user/") || path.startsWith("/api/citizen/")) {
            needRole = "citizen";
        }
        if (needRole != null && !needRole.equals(user.role())) {
            if (isApi) {
                Http.error(resp, 403, "Access denied.");
            } else {
                String home = "admin".equals(user.role()) ? "/admin/portal.html" : "/user/portal.html";
                resp.sendRedirect(req.getContextPath() + home);
            }
            return;
        }

        if (isApi && "POST".equals(req.getMethod())) {
            String expected = (String) session.getAttribute("csrf");
            String sent = req.getHeader("X-CSRF-Token");
            boolean valid = expected != null && sent != null && MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), sent.getBytes(StandardCharsets.UTF_8));
            if (!valid) {
                Http.error(resp, 403, "Invalid security token. Refresh the page and try again.");
                return;
            }
        }

        // The portal pages must not be cached, so the Back button after logout shows nothing private.
        resp.setHeader("Cache-Control", "no-store");
        chain.doFilter(request, response);
    }
}
