package com.traffic.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.traffic.api.dto.AccidentRequest;
import com.traffic.api.dto.AddNodeRequest;
import com.traffic.api.dto.ConnectEdgeRequest;
import com.traffic.api.dto.CreateSessionRequest;
import com.traffic.api.dto.FacilityRequest;
import com.traffic.api.dto.PolicyRequest;
import com.traffic.api.dto.IdRequest;
import com.traffic.api.dto.RushRequest;
import com.traffic.api.dto.TripRequest;
import com.traffic.api.dto.DispatchRequest;
import com.traffic.api.dto.VipConvoyRequest;
import com.traffic.api.dto.CompareRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.Executors;

/** Tiny JDK HTTP server exposing {@link SessionHub} to the React UI. */
public final class ApiServer {

    private final int port;
    private final SessionHub hub = new SessionHub();
    private final ObjectMapper json = new ObjectMapper();

    public ApiServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::root);
        server.createContext("/api/health", this::health);
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
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("API listening on http://localhost:" + port);
        System.out.println("React: cd web && npm run dev  (proxies /api)");
    }

    private void root(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        // UI is Vite on :5173; this port is JSON API only.
        writeJson(ex, 200, Map.of(
                "service", "city-traffic-simulator-api",
                "health", "/api/health",
                "ui", "cd web && npm run dev → http://localhost:5173"
        ));
    }

    private void health(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        writeJson(ex, 200, Map.of("ok", true));
    }

    private void session(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
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
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void exportSession(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                writeJson(ex, 405, Map.of("error", "GET required"));
                return;
            }
            writeJson(ex, 200, hub.exportBlueprint());
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void run(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
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
            writeJson(ex, 200, hub.run(ticks));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void connectEdge(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            ConnectEdgeRequest req = readJson(ex, ConnectEdgeRequest.class);
            writeJson(ex, 200, hub.connect(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }


    private void setFacility(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            FacilityRequest req = readJson(ex, FacilityRequest.class);
            writeJson(ex, 200, hub.setFacility(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void setPolicy(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            PolicyRequest req = readJson(ex, PolicyRequest.class);
            writeJson(ex, 200, hub.setPolicy(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void addNode(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            AddNodeRequest req = readJson(ex, AddNodeRequest.class);
            writeJson(ex, 200, hub.addNode(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void deleteNode(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            IdRequest req = readJson(ex, IdRequest.class);
            writeJson(ex, 200, hub.removeNode(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void deleteEdge(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            IdRequest req = readJson(ex, IdRequest.class);
            writeJson(ex, 200, hub.removeEdge(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void accident(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            AccidentRequest req = readJson(ex, AccidentRequest.class);
            writeJson(ex, 200, hub.accident(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void dispatch(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            DispatchRequest req = readJson(ex, DispatchRequest.class);
            writeJson(ex, 200, hub.dispatch(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void vipConvoy(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            VipConvoyRequest req = readJson(ex, VipConvoyRequest.class);
            writeJson(ex, 200, hub.vipConvoy(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void compare(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            CompareRequest req = readJson(ex, CompareRequest.class);
            if (req == null) {
                req = new CompareRequest(80);
            }
            writeJson(ex, 200, hub.compare(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void seedFacilities(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, hub.seedFacilities());
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void addTrip(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            TripRequest req = readJson(ex, TripRequest.class);
            writeJson(ex, 200, hub.addTrip(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void addRush(HttpExchange ex) throws IOException {
        if (corsPreflight(ex)) {
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
            writeJson(ex, 200, hub.addRushHour(req));
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void mutate(HttpExchange ex, SupplierWithException action) throws IOException {
        if (corsPreflight(ex)) {
            return;
        }
        try {
            requirePost(ex);
            writeJson(ex, 200, action.get());
        } catch (Exception e) {
            writeJson(ex, 400, Map.of("error", e.getMessage() == null ? "error" : e.getMessage()));
        }
    }

    private void requirePost(HttpExchange ex) {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            throw new IllegalStateException("POST required");
        }
    }

    private boolean corsPreflight(HttpExchange ex) throws IOException {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    private <T> T readJson(HttpExchange ex, Class<T> type) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length == 0) {
                return null;
            }
            return json.readValue(bytes, type);
        }
    }

    private void writeJson(HttpExchange ex, int status, Object body) throws IOException {
        byte[] bytes = json.writeValueAsBytes(body);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    @FunctionalInterface
    private interface SupplierWithException {
        Object get() throws Exception;
    }
}
