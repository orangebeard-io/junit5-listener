package io.orangebeard.listener;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Runs the {@link Samples} through a real JUnit launcher with the extension and the real java-client, against
 * a fake Orangebeard server, and checks what arrives there.
 */
class OrangebeardExtensionIntegrationTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final String API = "/listener/v3/it-project";

    private record Request(String method, String path, JsonNode body) {
        String api() {
            return path.substring(API.length());
        }
    }

    private HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        OrangebeardContext.getInstance().reset();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handle);
        server.start();
        System.setProperty("orangebeard.endpoint", "http://127.0.0.1:" + server.getAddress().getPort());
        System.setProperty("orangebeard.accessToken", UUID.randomUUID().toString());
        System.setProperty("orangebeard.project", "it-project");
        System.setProperty("orangebeard.testset", "it-set");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        for (String property : new String[]{"endpoint", "accessToken", "project", "testset"}) {
            System.clearProperty("orangebeard." + property);
        }
        OrangebeardContext.getInstance().reset();
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                body.length == 0 ? null : MAPPER.readTree(body));
        requests.add(request);

        String response = "";
        if (request.method().equals("POST")) {
            response = request.api().equals("/suite/start") ? suites(request.body()) : "\"" + UUID.randomUUID() + "\"";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private static String suites(JsonNode startSuite) {
        List<String> suites = new ArrayList<>();
        for (JsonNode name : startSuite.get("suiteNames")) {
            suites.add("{\"suiteUUID\":\"" + UUID.randomUUID() + "\",\"localSuiteName\":\"" + name.asString() + "\"}");
        }
        return "[" + String.join(",", suites) + "]";
    }

    private void execute(Class<?>... classes) {
        LauncherDiscoveryRequestBuilder builder = LauncherDiscoveryRequestBuilder.request();
        for (Class<?> testClass : classes) {
            builder.selectors(selectClass(testClass));
        }
        LauncherDiscoveryRequest request = builder.build();
        LauncherFactory.create().execute(request);
    }

    private List<Request> requestsTo(String api) {
        return requests.stream().filter(r -> r.api().equals(api)).toList();
    }

    private List<String> finishedStatuses() {
        return requests.stream().filter(r -> r.api().startsWith("/test/finish/"))
                .map(r -> r.body().get("status").asString()).toList();
    }

    @Test
    void a_run_with_passing_failing_aborted_and_disabled_tests_is_reported_and_finished_when_the_plan_ends() {
        execute(Samples.Mixed.class);

        // one run, started first and finished last, without waiting for the JVM to exit
        assertThat(requestsTo("/test-run/start")).hasSize(1);
        assertThat(requests.get(0).api()).isEqualTo("/test-run/start");
        assertThat(requests.get(requests.size() - 1).api()).startsWith("/test-run/finish/");

        // the whole suite path is started in one request: package parts, outer class and the nested class
        List<String> suiteNames = new ArrayList<>();
        requestsTo("/suite/start").forEach(r -> r.body().get("suiteNames").forEach(n -> suiteNames.add(n.asString())));
        assertThat(suiteNames).containsExactly("io", "orangebeard", "listener", "Samples", "Mixed", "Inner");

        List<String> testNames = requestsTo("/test/start").stream().map(r -> r.body().get("testName").asString()).toList();
        assertThat(testNames).containsExactlyInAnyOrder("passes()", "fails()", "aborts()", "disabled()", "passesToo()");
        assertThat(finishedStatuses()).containsExactlyInAnyOrder("PASSED", "FAILED", "STOPPED", "SKIPPED", "PASSED");

        List<String> messages = requestsTo("/log").stream().map(r -> r.body().get("message").asString()).toList();
        assertThat(messages).anyMatch(m -> m.equals("boom")).anyMatch(m -> m.equals("not today"));
    }

    @Test
    void a_failing_beforeAll_shows_up_as_a_failed_setup_test() {
        execute(Samples.BrokenSetup.class);

        List<JsonNode> started = requestsTo("/test/start").stream().map(Request::body).toList();
        assertThat(started).hasSize(1);
        assertThat(started.get(0).get("testName").asString()).isEqualTo("Class setup (@BeforeAll)");
        assertThat(started.get(0).get("testType").asString()).isEqualTo("BEFORE");
        assertThat(finishedStatuses()).containsExactly("FAILED");
        assertThat(requestsTo("/log").stream().map(r -> r.body().get("message").asString())).contains("setup failed");
    }

    @Test
    void classes_that_run_in_parallel_start_every_suite_exactly_once() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(Samples.ParallelA.class), selectClass(Samples.ParallelB.class), selectClass(Samples.ParallelC.class))
                .configurationParameter("junit.jupiter.execution.parallel.enabled", "true")
                .configurationParameter("junit.jupiter.execution.parallel.mode.default", "concurrent")
                .configurationParameter("junit.jupiter.execution.parallel.mode.classes.default", "concurrent")
                .build();
        LauncherFactory.create().execute(request);

        List<String> suiteNames = new ArrayList<>();
        requestsTo("/suite/start").forEach(r -> r.body().get("suiteNames").forEach(n -> suiteNames.add(n.asString())));
        Set<String> unique = new HashSet<>(suiteNames);
        assertThat(suiteNames).as("no suite is started twice").hasSameSizeAs(unique);
        assertThat(unique).containsExactlyInAnyOrder("io", "orangebeard", "listener", "Samples", "ParallelA", "ParallelB", "ParallelC");
        assertThat(requestsTo("/test/start")).hasSize(6);
        assertThat(finishedStatuses()).containsOnly("PASSED").hasSize(6);
        assertThat(Collections.frequency(requests.stream().map(Request::api).toList(), "/test-run/start")).isEqualTo(1);
    }
}
