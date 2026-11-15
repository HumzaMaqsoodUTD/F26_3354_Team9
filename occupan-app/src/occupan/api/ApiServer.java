package occupan.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import occupan.application.AssignmentWorkflow;
import occupan.application.AuthService;
import occupan.application.QueryService;
import occupan.application.ResourceService;
import occupan.application.ResourceView;
import occupan.domain.AssignmentRecord;
import occupan.domain.DomainException;
import occupan.domain.User;

/**
 * Presentation/API layer: HTTP routes under /api plus the browser UI files in static/.
 * Every route except login needs a valid session token (FR-1).
 */
public final class ApiServer {
    private static final Pattern RESOURCE = Pattern.compile("^/api/resources/([^/]+)$");
    private static final Pattern RESOURCE_HISTORY = Pattern.compile("^/api/resources/([^/]+)/history$");
    private static final Pattern RESOURCE_STATUS = Pattern.compile("^/api/resources/([^/]+)/status$");
    private static final Pattern ASSIGNMENT_RELEASE = Pattern.compile("^/api/assignments/([^/]+)/release$");

    private final AuthService auth;
    private final ResourceService resources;
    private final AssignmentWorkflow assignments;
    private final QueryService queries;
    private final Path staticDir;

    private record Reply(int status, Object body) {}

    public ApiServer(AuthService auth, ResourceService resources, AssignmentWorkflow assignments,
                     QueryService queries, Path staticDir) {
        this.auth = auth;
        this.resources = resources;
        this.assignments = assignments;
        this.queries = queries;
        this.staticDir = staticDir.toAbsolutePath().normalize();
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        return server;
    }

    // ---------- dispatch ----------
    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getRawPath();
            if (path.equals("/api") || path.startsWith("/api/")) {
                Reply reply;
                try {
                    reply = route(exchange, path);
                } catch (DomainException e) {
                    reply = new Reply(e.statusCode(), Map.of("detail", e.getMessage()));
                } catch (Json.ParseException | ClassCastException e) {
                    reply = new Reply(400, Map.of("detail", "Invalid request."));
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    reply = new Reply(500, Map.of("detail", "Something went wrong."));
                }
                send(exchange, reply.status(), "application/json; charset=utf-8",
                        Json.write(reply.body()).getBytes(StandardCharsets.UTF_8));
            } else {
                serveStatic(exchange, path);
            }
        } finally {
            exchange.close();
        }
    }

    private Reply route(HttpExchange ex, String path) throws IOException {
        String method = ex.getRequestMethod();
        if (path.equals("/api/login")) {
            if (!method.equals("POST")) return new Reply(405, Map.of("detail", "Method Not Allowed"));
            Map<String, Object> body = body(ex);
            AuthService.LoginResult result = auth.login(text(body, "username"), text(body, "password"));
            return new Reply(200, Map.of("token", result.token(), "user", userJson(result.user())));
        }

        String token = bearerToken(ex);
        User user = auth.userForToken(token); // 401 for everything below if there is no valid session
        Map<String, String> query = queryParams(ex);
        Matcher m;

        if (path.equals("/api/logout") && method.equals("POST")) {
            auth.logout(token);
            return new Reply(200, Map.of("ok", true));
        }
        if (path.equals("/api/me") && method.equals("GET")) return new Reply(200, userJson(user));
        if (path.equals("/api/resources")) {
            if (method.equals("GET")) {
                return new Reply(200, queries.search(user, query.get("q"), query.get("type"), query.get("status"),
                        query.get("location"), query.getOrDefault("sort", "resource_id"),
                        query.getOrDefault("order", "asc")));
            }
            if (method.equals("POST")) {
                Map<String, Object> b = body(ex);
                String status = optionalText(b, "status");
                return new Reply(201, resourceJson(resources.create(user, text(b, "resource_id"), text(b, "type"),
                        text(b, "location"), status == null ? "Available" : status)));
            }
        }
        if ((m = RESOURCE_HISTORY.matcher(path)).matches() && method.equals("GET")) {
            return new Reply(200, queries.history(user, decode(m.group(1))));
        }
        if ((m = RESOURCE_STATUS.matcher(path)).matches() && method.equals("POST")) {
            return new Reply(200, resourceJson(resources.changeStatus(user, decode(m.group(1)),
                    text(body(ex), "status"))));
        }
        if ((m = RESOURCE.matcher(path)).matches()) {
            if (method.equals("PATCH")) {
                return new Reply(200, resourceJson(resources.updateLocation(user, decode(m.group(1)),
                        text(body(ex), "location"))));
            }
            if (method.equals("DELETE")) return new Reply(200, resourceJson(resources.retire(user, decode(m.group(1)))));
        }
        if (path.equals("/api/reports/utilization") && method.equals("GET")) {
            return new Reply(200, queries.utilization(user));
        }
        if (path.equals("/api/assignments") && method.equals("POST")) {
            Map<String, Object> b = body(ex);
            return new Reply(201, assignmentJson(assignments.assign(user, text(b, "resource_id"),
                    optionalText(b, "patient_identifier"), optionalText(b, "operational_need"))));
        }
        if ((m = ASSIGNMENT_RELEASE.matcher(path)).matches() && method.equals("POST")) {
            int id;
            try {
                id = Integer.parseInt(decode(m.group(1)));
            } catch (NumberFormatException e) {
                throw DomainException.validation("Assignment id must be a number.");
            }
            return new Reply(200, assignmentJson(assignments.release(user, id, text(body(ex), "next_status"))));
        }
        return new Reply(404, Map.of("detail", "Not Found"));
    }

    // ---------- request helpers ----------
    private static String bearerToken(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        if (header == null || header.length() < 7 || !header.regionMatches(true, 0, "bearer ", 0, 7)) return "";
        return header.substring(7).strip();
    }

    private static Map<String, Object> body(HttpExchange ex) throws IOException {
        String text = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return Json.parseObject(text);
    }

    /** A required string field. A missing or non-string value is an invalid request. */
    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (!(value instanceof String s)) throw new Json.ParseException("Missing " + key);
        return s;
    }

    private static String optionalText(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) return null;
        if (!(value instanceof String s)) throw new Json.ParseException("Bad " + key);
        return s;
    }

    private static String decode(String s) { return URLDecoder.decode(s, StandardCharsets.UTF_8); }

    private static Map<String, String> queryParams(HttpExchange ex) {
        Map<String, String> params = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) return params;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = decode(eq < 0 ? pair : pair.substring(0, eq));
            String value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            params.put(key, value);
        }
        return params;
    }

    // ---------- JSON shapes ----------
    private static Map<String, Object> userJson(User user) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("user_id", user.userId());
        m.put("username", user.username());
        m.put("name", user.name());
        m.put("role", user.role().wire());
        return m;
    }

    private static Map<String, Object> resourceJson(ResourceView r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resource_id", r.resourceId());
        m.put("type", r.type().label());
        m.put("location", r.location());
        m.put("status", occupan.domain.Statuses.label(r.status()));
        m.put("retired", r.retired());
        return m;
    }

    private Map<String, Object> assignmentJson(AssignmentRecord a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("assignment_id", a.assignmentId());
        m.put("resource_id", a.resourceId());
        m.put("patient_id", a.patientId());
        m.put("operational_need", a.operationalNeed());
        m.put("assigned_by", a.assignedBy());
        m.put("assigned_at", a.assignedAt());
        m.put("released_at", a.releasedAt());
        m.put("released_by", a.releasedBy());
        return m;
    }

    // ---------- static files (the browser UI) ----------
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8", "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8", "json", "application/json; charset=utf-8",
            "svg", "image/svg+xml", "png", "image/png", "ico", "image/x-icon");

    private void serveStatic(HttpExchange ex, String rawPath) throws IOException {
        String method = ex.getRequestMethod();
        if (!method.equals("GET") && !method.equals("HEAD")) {
            send(ex, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String path = decode(rawPath);
        if (path.equals("/") || path.isEmpty()) path = "/index.html";
        Path file = staticDir.resolve(path.substring(1)).normalize();
        if (!file.startsWith(staticDir) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String name = file.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        send(ex, 200, TYPES.getOrDefault(ext, "application/octet-stream"), Files.readAllBytes(file));
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        boolean head = ex.getRequestMethod().equals("HEAD");
        ex.sendResponseHeaders(status, head ? -1 : bytes.length);
        if (!head) {
            try (OutputStream out = ex.getResponseBody()) { out.write(bytes); }
        }
    }
}
