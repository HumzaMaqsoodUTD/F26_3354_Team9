package occupan.api;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import occupan.App;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** End-to-end tests through the real HTTP server using the demo data. */
public class ApiFlowTest {
    private HttpServer server;
    private String base;
    private final HttpClient client = HttpClient.newHttpClient();

    private record Resp(int status, String body) {
        Map<String, Object> json() { return Json.parseObject(body); }
    }

    @BeforeEach void start() throws Exception {
        server = App.start(0, Path.of("static"));
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach void stop() { server.stop(0); }

    private Resp call(String method, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                                             : HttpRequest.BodyPublishers.ofString(body))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(response.statusCode(), response.body());
    }

    private String login(String user) throws Exception {
        var r = call("POST", "/api/login", null, "{\"username\":\"" + user + "\",\"password\":\"occupan-demo\"}");
        assertEquals(200, r.status());
        return (String) r.json().get("token");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> items(String token) throws Exception {
        var out = new java.util.LinkedHashMap<String, Map<String, Object>>();
        for (Object o : (List<Object>) call("GET", "/api/resources", token, null).json().get("items")) {
            var item = (Map<String, Object>) o;
            out.put((String) item.get("resource_id"), item);
        }
        return out;
    }

    @Test void A01_loginRejectsWrongPassword() throws Exception {
        assertEquals(401, call("POST", "/api/login", null, "{\"username\":\"nurse\",\"password\":\"wrong\"}").status());
    }

    @Test void A02_endpointsRequireASession() throws Exception {
        assertEquals(401, call("GET", "/api/resources", null, null).status());
        assertEquals(401, call("POST", "/api/assignments", null,
                "{\"resource_id\":\"BED-101\",\"operational_need\":\"x\"}").status());
        assertEquals(401, call("GET", "/api/reports/utilization", null, null).status());
        assertEquals(401, call("GET", "/api/resources", "not-a-token", null).status());
    }

    @Test void A03_logoutEndsTheSession() throws Exception {
        String token = login("nurse");
        assertEquals("clinical_staff", call("GET", "/api/me", token, null).json().get("role"));
        call("POST", "/api/logout", token, "{}");
        assertEquals(401, call("GET", "/api/me", token, null).status());
    }

    @Test void A04_onlyAdminCanAddResources() throws Exception {
        String body = "{\"resource_id\":\"BED-900\",\"type\":\"Bed\",\"location\":\"Ward C\"}";
        assertEquals(403, call("POST", "/api/resources", login("nurse"), body).status());
        assertEquals(201, call("POST", "/api/resources", login("admin"), body).status());
    }

    @Test void A05_assignThenReleaseFlow() throws Exception {
        String token = login("nurse");
        var assigned = call("POST", "/api/assignments", token,
                "{\"resource_id\":\"BED-101\",\"patient_identifier\":\"SIM-55555\"}");
        assertEquals(201, assigned.status());
        assertEquals(409, call("POST", "/api/assignments", token,
                "{\"resource_id\":\"BED-101\",\"patient_identifier\":\"SIM-66666\"}").status());
        long id = (Long) assigned.json().get("assignment_id");
        var released = call("POST", "/api/assignments/" + id + "/release", token, "{\"next_status\":\"Cleaning\"}");
        assertEquals(200, released.status());
        assertNotNull(released.json().get("released_at"));
        assertEquals("Cleaning", items(token).get("BED-101").get("status"));
    }

    @Test void A06_badInputReturnsAReadableError() throws Exception {
        var r = call("POST", "/api/assignments", login("nurse"), "{\"resource_id\":\"BED-105\",\"patient_identifier\":\"Jane Doe\"}");
        assertEquals(400, r.status());
        assertTrue(((String) r.json().get("detail")).toLowerCase().contains("simulated"));
        assertEquals(400, call("POST", "/api/assignments", login("nurse"), "not json").status());
    }

    @Test void A07_searchHistoryAndReport() throws Exception {
        String token = login("admin");
        assertEquals(11, items(token).size());
        var filtered = call("GET", "/api/resources?type=Bed&status=Available&location=ward%20a", token, null).json();
        assertEquals(1L, filtered.get("total"));
        var history = call("GET", "/api/resources/BED-102/history", token, null).json();
        assertEquals(1, ((List<?>) history.get("assignments")).size());
        var report = call("GET", "/api/reports/utilization", token, null).json();
        assertTrue(report.containsKey("by_type") && report.containsKey("overall"));
        assertEquals(404, call("GET", "/api/resources/NOPE-1/history", token, null).status());
    }

    @Test void A08_editRetireAndStatusChange() throws Exception {
        String token = login("admin");
        assertEquals(200, call("PATCH", "/api/resources/BED-101", token, "{\"location\":\"Ward Q\"}").status());
        assertEquals("Ward Q", items(token).get("BED-101").get("location"));
        assertEquals(200, call("POST", "/api/resources/BED-101/status", token, "{\"status\":\"Maintenance\"}").status());
        assertEquals(400, call("POST", "/api/resources/BED-101/status", token, "{\"status\":\"Occupied\"}").status());
        assertEquals(409, call("DELETE", "/api/resources/BED-102", token, null).status());
        assertEquals(200, call("DELETE", "/api/resources/BED-101", token, null).status());
        assertFalse(items(token).containsKey("BED-101"));
    }

    @Test void A09_twoRequestsForOneResourceHaveOneWinner() throws Exception {
        String token = login("nurse");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> call("POST", "/api/assignments", token,
                    "{\"resource_id\":\"EQ-301\",\"patient_identifier\":\"SIM-30001\"}").status());
            var b = pool.submit(() -> call("POST", "/api/assignments", token,
                    "{\"resource_id\":\"EQ-301\",\"patient_identifier\":\"SIM-30002\"}").status());
            var codes = List.of(a.get(), b.get());
            assertTrue(codes.contains(201) && codes.contains(409), codes.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void A10_browserUiIsServed() throws Exception {
        var page = call("GET", "/", null, null);
        assertEquals(200, page.status());
        assertTrue(page.body().contains("Occupan"));
        assertEquals(200, call("GET", "/app.js", null, null).status());
        assertEquals(404, call("GET", "/../README.md", null, null).status());
        assertEquals(404, call("GET", "/nope.html", null, null).status());
    }

    @Test void A11_jsonRoundTrip() {
        var parsed = Json.parseObject("{\"a\":[1,2.5,\"x\\n\\u0041\",null,true],\"b\":{\"c\":\"\\\"\"}}");
        assertEquals("{\"a\":[1,2.5,\"x\\nA\",null,true],\"b\":{\"c\":\"\\\"\"}}", Json.write(parsed));
        assertThrows(Json.ParseException.class, () -> Json.parse("{\"a\":"));
    }
}
