package util;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/** Request/response helpers shared by the servlets and the filter. */
public final class Http {

    private Http() { }

    // ---------- responses ----------

    public static void json(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.setHeader("Cache-Control", "no-store");
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.getOutputStream().write(JsonUtil.toJson(body).getBytes(StandardCharsets.UTF_8));
    }

    public static void error(HttpServletResponse resp, int status, String message) throws IOException {
        json(resp, status, Map.of("error", message));
    }

    /** Turns a SQLException into a safe message. Business-rule errors (ORA-20xxx) are shown as-is. */
    public static void sqlError(HttpServletResponse resp, ServletContext ctx, SQLException e) throws IOException {
        int code = e.getErrorCode();
        if (code >= 20000 && code <= 20999) {
            String first = e.getMessage() == null ? "" : e.getMessage().split("\n")[0];
            error(resp, 400, first.replaceFirst("^ORA-\\d+:\\s*", ""));
            return;
        }
        switch (code) {
            case 1 -> error(resp, 409, "That record already exists.");
            case 1400 -> error(resp, 400, "A required value is missing.");
            case 2290 -> error(resp, 400, "That value is not allowed by the database rules.");
            case 2291 -> error(resp, 400, "The referenced record does not exist.");
            case 2292 -> error(resp, 409, "This record is in use and cannot be removed.");
            default -> {
                ctx.log("Database error (ORA-" + code + ")", e);
                error(resp, 500, "A database error occurred. Check the server log.");
            }
        }
    }

    // ---------- request parameters ----------

    /** Trimmed parameter or null when missing/blank. */
    public static String str(HttpServletRequest req, String name) {
        String v = req.getParameter(name);
        if (v == null) {
            return null;
        }
        v = v.trim();
        return v.isEmpty() ? null : v;
    }

    public static int requireInt(HttpServletRequest req, String name) {
        String v = str(req, name);
        if (v == null) {
            throw new ApiException(400, "Missing value: " + name);
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Invalid number: " + name);
        }
    }

    public static String requireText(HttpServletRequest req, String name, String label, int maxLen) {
        String v = str(req, name);
        if (v == null) {
            throw new ApiException(400, label + " is required.");
        }
        if (v.length() > maxLen) {
            throw new ApiException(400, label + " must be at most " + maxLen + " characters.");
        }
        return v;
    }

    public static String optionalText(HttpServletRequest req, String name, String label, int maxLen) {
        String v = str(req, name);
        if (v != null && v.length() > maxLen) {
            throw new ApiException(400, label + " must be at most " + maxLen + " characters.");
        }
        return v;
    }

    public static String requireOneOf(HttpServletRequest req, String name, String label, Set<String> allowed) {
        String v = str(req, name);
        if (v == null || !allowed.contains(v)) {
            throw new ApiException(400, "Choose a valid " + label + ".");
        }
        return v;
    }

    public static Double optionalAmount(HttpServletRequest req, String name) {
        String v = str(req, name);
        if (v == null) {
            return null;
        }
        try {
            double d = Double.parseDouble(v);
            if (d < 0 || Double.isNaN(d) || Double.isInfinite(d)) {
                throw new NumberFormatException();
            }
            return d;
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Amount must be a positive number.");
        }
    }

    public static LocalDate optionalDate(HttpServletRequest req, String name) {
        String v = str(req, name);
        if (v == null) {
            return null;
        }
        try {
            return LocalDate.parse(v);
        } catch (Exception e) {
            throw new ApiException(400, "Date must look like 2026-10-01.");
        }
    }
}
