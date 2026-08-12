package com.traffic.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.traffic.api.dto.AccidentRequest;
import com.traffic.api.dto.AddNodeRequest;
import com.traffic.api.dto.CompareRequest;
import com.traffic.api.dto.ConnectEdgeRequest;
import com.traffic.api.dto.CreateSessionRequest;
import com.traffic.api.dto.DispatchRequest;
import com.traffic.api.dto.FacilityRequest;
import com.traffic.api.dto.IdRequest;
import com.traffic.api.dto.PolicyRequest;
import com.traffic.api.dto.RushRequest;
import com.traffic.api.dto.TripRequest;
import com.traffic.api.dto.VipConvoyRequest;
import com.traffic.persist.CityStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JDK HTTP server for {@link SessionHub}. Hardened for single-operator demos:
 * CORS allowlist, optional API key, body/size/tick clamps, bounded workers,
 * optional static UI from {@code CITYFLOW_STATIC_DIR}.
 */
public final class ApiServer {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private static final String SESSION_HEADER = "X-Session-Id";

    private final ApiConfig config;
    private final SessionHub hub;
    private final ObjectMapper json = new ObjectMapper();
    private final Instant startedAt = Instant.now();
    private HttpServer server;
    private ExecutorService workers;

    public ApiServer(int port) {
        this(ApiConfig.localLab(port, Path.of("data")));
    }

    public ApiServer(ApiConfig config) {
        this(config, new SessionHub(new CityStore(config.savePath()), config));
    }

    public ApiServer(ApiConfig config, SessionHub hub) {
        this.config = config;
        this.hub = hub;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/api/health", this::health);
        server.createContext("/api/metrics", this::metrics);
        server.createContext("/api/session", this::session);
        server.createContext("/api/session/new", ex -> mutate(ex, hub::newCity));
        server.createContext("/api/session/export", this::exportSession);
        server.createContext("/api/session/build", ex -> mutate(ex, hub::build));
        server.createContext("/api/session/play", ex -> mutate(ex, hub::play));
        server.createContext("/api/session/apply", ex -> mutate(ex, hub::apply));
        server.createContext("/api/session/step", ex -> mutate(ex, hub::step));
        server.createContext("/api/session/run", this::run);
        server.createContext("/api/city/edges", this::connectEdge);
        server.createContext("/api/city/edges/delete", this::deleteEdge);
        server.createContext("/api/city/nodes", this::addNode);
        server.createContext("/api/city/facility", this::setFacility);
        server.createContext("/api/session/policy", this::setPolicy);
        server.createContext("/api/city/nodes/delete", this::deleteNode);
        server.createContext("/api/accidents", this::accident);
        server.createContext("/api/fleet/trips", this::addTrip);
        server.createContext("/api/fleet/dispatch", this::dispatch);
        server.createContext("/api/fleet/vip-convoy", this::vipConvoy);
        server.createContext("/api/session/compare", this::compare);
        server.createContext("/api/city/facilities/seed", this::seedFacilities);
        server.createContext("/api/fleet/trips/random", ex -> mutate(ex, hub::addRandomTrip));
        server.createContext("/api/fleet/rush", this::addRush);
        server.createContext("/", this::rootOrStatic);

        workers = Executors.newFixedThreadPool(config.workerThreads(), namedDaemonFactory("api-worker"));
        server.setExecutor(workers);
        server.start();

        System.out.println("API listening on http://localhost:" + server.getAddress().getPort());
        System.out.println("Config: " + config);
        if (!config.authRequired()) {
            System.out.println("WARN: CITYFLOW_API_KEY unset — open lab mode (do not expose publicly)");
        }
        if (config.staticDir().isPresent()) {
            System.out.println("Serving UI from " + config.staticDir().get().toAbsolutePath());
        } else {
            System.out.println("React: cd web && npm run dev  (proxies /api)");
        }
    }

    public void stop(int delaySeconds) {
        if (server != null) {
            server.stop(delaySeconds);
        }
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    int port() {
        return server != null ? server.getAddress().getPort() : config.port();
    }

    private void rootOrStatic(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, false)) {
            return;
        }
        String path = ex.getRequestURI().getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            if (serveStatic(ex, "index.html")) {
                return;
            }
            writeJson(ex, 200, Map.of(
                    "service", "city-traffic-simulator-api",
                    "health", "/api/health",
                    "mode", "single-operator",
                    "ui", config.staticDir().isPresent()
                            ? "bundled at /"
                            : "cd web && npm run dev → http://localhost:5173"
            ));
            return;
        }
        if (path.startsWith("/api/")) {
            writeJson(ex, 404, Map.of("error", "Not found"));
            return;
        }
        String relative = path.startsWith("/") ? path.substring(1) : path;
        if (serveStatic(ex, relative) || serveStatic(ex, "index.html")) {
            return;
        }
        writeJson(ex, 404, Map.of("error", "Not found"));
    }

    private boolean serveStatic(HttpExchange ex, String relative) throws IOException {
        Optional<Path> rootOpt = config.staticDir();
        if (rootOpt.isEmpty()) {
            return false;
        }
        Path root = rootOpt.get().toAbsolutePath().normalize();
        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            return false;
        }
        byte[] bytes = Files.readAllBytes(resolved);
        Headers h = ex.getResponseHeaders();
        applyCors(ex, h);
        h.set("Content-Type", contentType(resolved.getFileName().toString()));
        h.set("Cache-Control", relative.equals("index.html") ? "no-cache" : "public, max-age=3600");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
        return true;
    }

    private static String contentType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }
        return "application/octet-stream";
    }

    private void metrics(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, false)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.putAll(hub.healthDetails());
        body.put("workerThreads", config.workerThreads());
        body.put("maxRunTicks", config.maxRunTicks());
        body.put("authRequired", config.authRequired());
        body.put("eval", Map.of(
                "mechanisms", List.of(
                        "priorityDeparture", "signalPreemption", "corridorBlocking", "softBufferRouting"),
                "suites", List.of("default", "ablation", "signals"),
                "docs", "docs/RESEARCH.md"
        ));
        writeJson(ex, 200, body);
    }

    private void health(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, false)) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("service", "city-traffic-simulator-api");
        body.put("version", "0.1.0-SNAPSHOT");
        body.put("mode", "single-operator");
        body.put("uptimeMs", Duration.between(startedAt, Instant.now()).toMillis());
        body.put("authRequired", config.authRequired());
        body.put("schemaVersion", ApiConfig.SCHEMA_VERSION);
        body.putAll(hub.healthDetails());
        writeJson(ex, 200, body);
    }

    private void session(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                if (!hub.hasSession()) {
                    writeJson(ex, 404, Map.of("error", "No saved city"));
                    return;
                }
                writeJson(ex, 200, hub.snapshot());
                return;
            }
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                CreateSessionRequest req = readJson(ex, CreateSessionRequest.class);
                if (req == null) {
                    req = new CreateSessionRequest("BLANK", 0, 0, 0, 42L, 0, 300, true);
                }
                writeJson(ex, 200, hub.create(req));
                return;
            }
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void exportSession(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, Map.of("error", "GET required"));
                return;
            }
            writeJson(ex, 200, hub.exportBlueprint());
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void run(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = readJson(ex, Map.class);
            int ticks = 10;
            if (body != null && body.get("ticks") instanceof Number n) {
                ticks = n.intValue();
            }
            writeJson(ex, 200, hub.run(config.clampRunTicks(ticks)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void connectEdge(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.connect(readJson(ex, ConnectEdgeRequest.class)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void setFacility(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.setFacility(readJson(ex, FacilityRequest.class)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void setPolicy(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.setPolicy(readJson(ex, PolicyRequest.class)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void addNode(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.addNode(readJson(ex, AddNodeRequest.class)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void deleteNode(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.removeNode(readJson(ex, IdRequest.class)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void deleteEdge(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.removeEdge(readJson(ex, IdRequest.class)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void accident(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.accident(readJson(ex, AccidentRequest.class)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void dispatch(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.dispatch(readJson(ex, DispatchRequest.class)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void vipConvoy(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.vipConvoy(readJson(ex, VipConvoyRequest.class)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void compare(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            CompareRequest req = readJson(ex, CompareRequest.class);
            if (req == null) {
                req = new CompareRequest(80);
            }
            int ticks = config.clampCompareTicks(req.ticks());
            writeJson(ex, 200, hub.compare(new CompareRequest(ticks)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void seedFacilities(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.seedFacilities());
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void addTrip(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.addTrip(readJson(ex, TripRequest.class)));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void addRush(HttpExchange ex) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            RushRequest req;
            try {
                req = readJson(ex, RushRequest.class);
            } catch (Exception ignored) {
                req = new RushRequest(8);
            }
            if (req == null) {
                req = new RushRequest(8);
            }
            writeJson(ex, 200, hub.addRushHour(new RushRequest(config.clampRush(req.count()))));
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void mutate(HttpExchange ex, SupplierWithException action) throws IOException {
        if (preflightOrReject(ex, true)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, action.get());
        } catch (Exception e) {
            writeError(ex, e);
        }
    }

    private void requirePost(HttpExchange ex) {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            throw new IllegalStateException("POST required");
        }
    }

    private boolean preflightOrReject(HttpExchange ex, boolean requireAuth) throws IOException {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && !origin.isBlank() && !config.isOriginAllowed(origin)) {
            writeJson(ex, 403, Map.of("error", "Origin not allowed"));
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            Headers h = ex.getResponseHeaders();
            applyCors(ex, h);
            h.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
            h.set("Access-Control-Allow-Headers", "Content-Type, " + API_KEY_HEADER + ", " + SESSION_HEADER);
            h.set("Access-Control-Max-Age", "600");
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        if (requireAuth && !authorize(ex)) {
            writeJson(ex, 401, Map.of("error", "Unauthorized — set X-Api-Key"));
            return true;
        }
        String sid = ex.getRequestHeaders().getFirst(SESSION_HEADER);
        hub.bind(sid == null || sid.isBlank() ? SessionHub.DEFAULT_SESSION : sid);
        return false;
    }

    private boolean authorize(HttpExchange ex) {
        Optional<String> expected = config.apiKey();
        if (expected.isEmpty()) {
            return true;
        }
        String provided = ex.getRequestHeaders().getFirst(API_KEY_HEADER);
        if (provided == null) {
            return false;
        }
        byte[] a = expected.get().getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private void applyCors(HttpExchange ex, Headers h) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && config.isOriginAllowed(origin)) {
            h.set("Access-Control-Allow-Origin", origin);
            h.set("Vary", "Origin");
        }
    }

    private <T> T readJson(HttpExchange ex, Class<T> type) throws IOException {
        byte[] bytes = readBodyLimited(ex);
        if (bytes.length == 0) {
            return null;
        }
        return json.readValue(bytes, type);
    }

    private byte[] readBodyLimited(HttpExchange ex) throws IOException {
        if (ex.getRequestHeaders().containsKey("Content-Length")) {
            long declared = Long.parseLong(ex.getRequestHeaders().getFirst("Content-Length"));
            if (declared > config.maxBodyBytes()) {
                throw new IllegalArgumentException(
                        "Request body too large (max " + config.maxBodyBytes() + " bytes)");
            }
        }
        try (InputStream in = ex.getRequestBody()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int total = 0;
            int n;
            while ((n = in.read(chunk)) >= 0) {
                total += n;
                if (total > config.maxBodyBytes()) {
                    throw new IllegalArgumentException(
                            "Request body too large (max " + config.maxBodyBytes() + " bytes)");
                }
                buf.write(chunk, 0, n);
            }
            return buf.toByteArray();
        }
    }

    private void writeError(HttpExchange ex, Exception e) throws IOException {
        String msg = e.getMessage() == null ? "error" : e.getMessage();
        int status = msg.toLowerCase(Locale.ROOT).contains("unauthorized") ? 401 : 400;
        writeJson(ex, status, Map.of("error", msg));
    }

    private void writeJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        applyCors(ex, h);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    @FunctionalInterface
    private interface SupplierWithException {
        Object get() throws Exception;
    }
}
