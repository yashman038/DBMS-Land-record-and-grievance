package util;

import java.lang.reflect.RecordComponent;
import java.util.Map;

/**
 * Minimal JSON writer (no external library).
 * Supports String, numbers, booleans, null, Map, Iterable/arrays of those, and Java records.
 */
public final class JsonUtil {

    private JsonUtil() { }

    public static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            quote(sb, s);
        } else if (o instanceof Boolean || o instanceof Integer || o instanceof Long) {
            sb.append(o);
        } else if (o instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else {
                sb.append(n);
            }
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                quote(sb, String.valueOf(e.getKey()));
                sb.append(':');
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object item : it) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(sb, item);
            }
            sb.append(']');
        } else if (o.getClass().isRecord()) {
            sb.append('{');
            boolean first = true;
            try {
                for (RecordComponent rc : o.getClass().getRecordComponents()) {
                    if (!first) {
                        sb.append(',');
                    }
                    first = false;
                    quote(sb, rc.getName());
                    sb.append(':');
                    write(sb, rc.getAccessor().invoke(o));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Cannot serialise " + o.getClass(), e);
            }
            sb.append('}');
        } else {
            quote(sb, o.toString());
        }
    }

    private static void quote(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<', '>', '&', '\'' -> sb.append(String.format("\\u%04x", (int) c)); // safe inside HTML too
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
