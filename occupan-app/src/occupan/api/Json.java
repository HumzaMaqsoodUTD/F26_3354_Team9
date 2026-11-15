package occupan.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JSON reader/writer so the app needs nothing beyond the JDK. */
public final class Json {
    private Json() {}

    public static final class ParseException extends RuntimeException {
        ParseException(String message) { super(message); }
    }

    // ---------- writing ----------
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(s, sb);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            sb.append(value);
        } else if (value instanceof Double d) {
            sb.append(d.isNaN() || d.isInfinite() ? "null" : d.toString());
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Collection<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                write(item, sb);
            }
            sb.append(']');
        } else {
            writeString(String.valueOf(value), sb);
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ---------- reading ----------
    /** Parses JSON into Map, List, String, Long, Double, Boolean or null. */
    public static Object parse(String text) {
        Reader r = new Reader(text);
        r.skipSpace();
        Object value = r.value();
        r.skipSpace();
        if (r.pos != text.length()) throw new ParseException("Unexpected text after JSON value");
        return value;
    }

    /** Parses a JSON object body, or throws ParseException. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) throw new ParseException("Expected a JSON object");
        return (Map<String, Object>) value;
    }

    private static final class Reader {
        final String s;
        int pos;

        Reader(String s) { this.s = s; }

        void skipSpace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char peek() {
            if (pos >= s.length()) throw new ParseException("Unexpected end of JSON");
            return s.charAt(pos);
        }

        void expect(char c) {
            if (peek() != c) throw new ParseException("Expected '" + c + "'");
            pos++;
        }

        Object value() {
            char c = peek();
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (s.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            if (s.startsWith("null", pos)) { pos += 4; return null; }
            return number();
        }

        Map<String, Object> object() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipSpace();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipSpace();
                String key = string();
                skipSpace();
                expect(':');
                skipSpace();
                map.put(key, value());
                skipSpace();
                if (peek() == ',') { pos++; continue; }
                expect('}');
                return map;
            }
        }

        List<Object> array() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipSpace();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                skipSpace();
                list.add(value());
                skipSpace();
                if (peek() == ',') { pos++; continue; }
                expect(']');
                return list;
            }
        }

        String string() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = peek();
                pos++;
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = peek();
                pos++;
                switch (e) {
                    case '"', '\\', '/' -> sb.append(e);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > s.length()) throw new ParseException("Bad unicode escape");
                        sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new ParseException("Bad escape");
                }
            }
        }

        Object number() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) pos++;
            String text = s.substring(start, pos);
            if (text.isEmpty()) throw new ParseException("Unexpected character");
            try {
                if (text.contains(".") || text.contains("e") || text.contains("E")) return Double.parseDouble(text);
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                throw new ParseException("Bad number");
            }
        }
    }
}
