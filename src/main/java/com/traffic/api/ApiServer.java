package com.traffic.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.traffic.api.dto.AccidentRequest;
import com.traffic.api.dto.ConnectEdgeRequest;
import com.traffic.api.dto.CreateSessionRequest;

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
        server.createContext("/api/health", this::health);
        server.createContext("/api/session", this::session);
        server.createContext("/api/session/build", ex -> mutate(ex, hub::build));
        server.createContext("/api/session/play", ex -> mutate(ex, hub::play));
        server.createContext("/api/session/apply", ex -> mutate(ex, hub::apply));
        server.createContext("/api/session/step", ex -> mutate(ex, hub::step));
        server.createContext("/api/session/run", this::run);
        server.createContext("/api/city/edges", this::connectEdge);
        server.createContext("/api/accidents", this::accident);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("API listening on http://localhost:" + port);
        System.out.println("React: cd web && npm run dev  (proxies /api)");
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
                writeJson(ex, 200, hub.snapshot());
                return;
            }
            if ("POST".equalsIgnoreCase(ex.getRequestMethod())) {
                CreateSessionRequest req = readJson(ex, CreateSessionRequest.class);
                if (req == null) {
                    req = new CreateSessionRequest(8, 8, 8, 42L, 200, 300);
                }
                writeJson(ex, 200, hub.create(req));
                return;
            }
            writeJson(ex, 405, Map.of("error", "Method not allowed"));
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
