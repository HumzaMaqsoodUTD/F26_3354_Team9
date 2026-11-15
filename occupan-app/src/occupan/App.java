package occupan;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.nio.file.Path;
import occupan.api.ApiServer;
import occupan.application.AssignmentWorkflow;
import occupan.application.AuthService;
import occupan.application.DemoData;
import occupan.application.QueryService;
import occupan.application.ResourceService;
import occupan.data.AssignmentRepository;
import occupan.data.Database;
import occupan.data.HistoryRepository;
import occupan.data.ResourceRepository;
import occupan.data.UserRepository;

/**
 * Entry point: wires the layers together and starts the server.
 * api -> application -> (AssignmentService + domain) -> data -> in-memory Database
 */
public final class App {
    private App() {}

    /** Everything the API needs, built with fresh demo data. */
    public record Services(AuthService auth, ResourceService resources, AssignmentWorkflow assignments,
                           QueryService queries, AssignmentService core) {}

    public static Services build() {
        Database db = new Database();
        UserRepository users = new UserRepository(db);
        ResourceRepository resourceRepo = new ResourceRepository(db);
        AssignmentRepository assignmentRepo = new AssignmentRepository(db);
        HistoryRepository history = new HistoryRepository(db);
        // The unit that the JUnit tests cover. It decides whether an assignment is allowed.
        AssignmentService core = new AssignmentService(java.util.Map.of());

        AuthService auth = new AuthService(users);
        ResourceService resources = new ResourceService(db, core, resourceRepo, assignmentRepo, history);
        AssignmentWorkflow assignments = new AssignmentWorkflow(db, core, resourceRepo, assignmentRepo, history);
        QueryService queries = new QueryService(db, core, resourceRepo, assignmentRepo, history, users);
        DemoData.load(auth, resources, assignments);
        return new Services(auth, resources, assignments, queries, core);
    }

    /** Builds everything and starts listening (port 0 picks a free port). */
    public static HttpServer start(int port, Path staticDir) throws IOException {
        Services s = build();
        return new ApiServer(s.auth(), s.resources(), s.assignments(), s.queries(), staticDir).start(port);
    }

    public static void main(String[] args) throws IOException {
        int port = Config.port();
        HttpServer server = start(port, Config.staticDir());
        System.out.println("Occupan is running at http://127.0.0.1:" + server.getAddress().getPort());
        System.out.println("Demo logins: admin / nurse, password from OCCUPAN_DEMO_PASSWORD (default occupan-demo)");
        System.out.println("Press Ctrl+C to stop. Data is kept in memory and resets on restart.");
    }
}
