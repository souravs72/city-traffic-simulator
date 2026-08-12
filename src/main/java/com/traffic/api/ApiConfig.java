package com.traffic.api;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Runtime knobs for the HTTP API. Prefer env vars so containers and local
 * labs share one binary.
 *
 * <pre>
 *   CITYFLOW_PORT / PORT
 *   CITYFLOW_DATA_DIR
 *   CITYFLOW_CORS_ORIGINS   (comma-separated; empty = defaults for local UI)
 *   CITYFLOW_API_KEY        (empty = open lab mode; set for demos)
 *   CITYFLOW_STATIC_DIR     (optional Vite dist/ for one-process serve)
 *   CITYFLOW_MAX_BODY_BYTES
 *   CITYFLOW_MAX_RUN_TICKS
 *   CITYFLOW_WORKER_THREADS
 * </pre>
 */
public final class ApiConfig {

    public static final int SCHEMA_VERSION = 1;

    private final int port;
    private final Path dataDir;
    private final Path savePath;
    private final Set<String> corsOrigins;
    private final Optional<String> apiKey;
    private final Optional<Path> staticDir;
    private final int maxBodyBytes;
    private final int maxRunTicks;
    private final int maxCompareTicks;
    private final int maxRows;
    private final int maxCols;
    private final int maxFleet;
    private final int maxRush;
    private final int workerThreads;

    public ApiConfig(
            int port,
            Path dataDir,
            Set<String> corsOrigins,
            Optional<String> apiKey,
            Optional<Path> staticDir,
            int maxBodyBytes,
            int maxRunTicks,
            int maxCompareTicks,
            int maxRows,
            int maxCols,
            int maxFleet,
            int maxRush,
            int workerThreads
    ) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port out of range");
        }
        this.port = port;
        this.dataDir = dataDir;
        this.savePath = dataDir.resolve("city-flow.json");
        this.corsOrigins = Set.copyOf(corsOrigins);
        this.apiKey = apiKey.filter(s -> !s.isBlank());
        this.staticDir = staticDir;
        this.maxBodyBytes = Math.max(4_096, maxBodyBytes);
        this.maxRunTicks = Math.max(1, maxRunTicks);
        this.maxCompareTicks = Math.max(1, maxCompareTicks);
        this.maxRows = Math.max(1, maxRows);
        this.maxCols = Math.max(1, maxCols);
        this.maxFleet = Math.max(1, maxFleet);
        this.maxRush = Math.max(1, maxRush);
        this.workerThreads = Math.max(2, workerThreads);
    }

    public static ApiConfig fromEnvironment() {
        int port = envInt("CITYFLOW_PORT", envInt("PORT", 8080));
        Path dataDir = Path.of(env("CITYFLOW_DATA_DIR", "data"));
        Optional<String> apiKey = Optional.ofNullable(System.getenv("CITYFLOW_API_KEY"))
                .map(String::trim)
                .filter(s -> !s.isEmpty());

        Optional<Path> staticDir = Optional.ofNullable(System.getenv("CITYFLOW_STATIC_DIR"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Path::of);

        Set<String> cors = parseCors(System.getenv("CITYFLOW_CORS_ORIGINS"), port);

        return new ApiConfig(
                port,
                dataDir,
                cors,
                apiKey,
                staticDir,
                envInt("CITYFLOW_MAX_BODY_BYTES", 1_048_576),
                envInt("CITYFLOW_MAX_RUN_TICKS", 200),
                envInt("CITYFLOW_MAX_COMPARE_TICKS", 500),
                envInt("CITYFLOW_MAX_ROWS", 32),
                envInt("CITYFLOW_MAX_COLS", 32),
                envInt("CITYFLOW_MAX_FLEET", 2_000),
                envInt("CITYFLOW_MAX_RUSH", 40),
                envInt("CITYFLOW_WORKER_THREADS", 16)
        );
    }

    /** Local-dev defaults used by unit tests. */
    public static ApiConfig localLab(int port, Path dataDir) {
        return new ApiConfig(
                port,
                dataDir,
                defaultCors(port),
                Optional.empty(),
                Optional.empty(),
                1_048_576,
                200,
                500,
                32,
                32,
                2_000,
                40,
                4
        );
    }

    public static ApiConfig withApiKey(ApiConfig base, String key) {
        return new ApiConfig(
                base.port,
                base.dataDir,
                base.corsOrigins,
                Optional.ofNullable(key),
                base.staticDir,
                base.maxBodyBytes,
                base.maxRunTicks,
                base.maxCompareTicks,
                base.maxRows,
                base.maxCols,
                base.maxFleet,
                base.maxRush,
                base.workerThreads
        );
    }

    private static Set<String> parseCors(String raw, int port) {
        if (raw == null || raw.isBlank()) {
            return defaultCors(port);
        }
        Set<String> out = new LinkedHashSet<>();
        for (String part : raw.split(",")) {
            String o = part.trim();
            if (!o.isEmpty()) {
                out.add(o);
            }
        }
        return out.isEmpty() ? defaultCors(port) : out;
    }

    private static Set<String> defaultCors(int port) {
        return new LinkedHashSet<>(Arrays.asList(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:" + port,
                "http://127.0.0.1:" + port
        ));
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v.trim();
    }

    private static int envInt(String key, int fallback) {
        String v = System.getenv(key);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(key + " must be an integer, got: " + v);
        }
    }

    public int port() {
        return port;
    }

    public Path dataDir() {
        return dataDir;
    }

    public Path savePath() {
        return savePath;
    }

    public Set<String> corsOrigins() {
        return corsOrigins;
    }

    public boolean isOriginAllowed(String origin) {
        if (origin == null || origin.isBlank()) {
            return true;
        }
        return corsOrigins.contains(origin.trim());
    }

    public Optional<String> apiKey() {
        return apiKey;
    }

    public boolean authRequired() {
        return apiKey.isPresent();
    }

    public Optional<Path> staticDir() {
        return staticDir;
    }

    public int maxBodyBytes() {
        return maxBodyBytes;
    }

    public int maxRunTicks() {
        return maxRunTicks;
    }

    public int maxCompareTicks() {
        return maxCompareTicks;
    }

    public int maxRows() {
        return maxRows;
    }

    public int maxCols() {
        return maxCols;
    }

    public int maxFleet() {
        return maxFleet;
    }

    public int maxRush() {
        return maxRush;
    }

    public int workerThreads() {
        return workerThreads;
    }

    public int clampRunTicks(int ticks) {
        if (ticks <= 0) {
            return 10;
        }
        return Math.min(ticks, maxRunTicks);
    }

    public int clampRush(int count) {
        if (count <= 0) {
            return 8;
        }
        return Math.min(count, maxRush);
    }

    public int clampCompareTicks(int ticks) {
        if (ticks <= 0) {
            return 80;
        }
        return Math.min(ticks, maxCompareTicks);
    }

    public void validateCreate(int rows, int cols, int fleetSize) {
        if (rows < 0 || cols < 0) {
            throw new IllegalArgumentException("rows/cols must be >= 0");
        }
        if (rows > maxRows || cols > maxCols) {
            throw new IllegalArgumentException(
                    "Grid too large — max " + maxRows + "×" + maxCols
                            + " (got " + rows + "×" + cols + ")");
        }
        if (fleetSize > maxFleet) {
            throw new IllegalArgumentException(
                    "Fleet too large — max " + maxFleet + " (got " + fleetSize + ")");
        }
    }

    @Override
    public String toString() {
        return "ApiConfig{port=" + port
                + ", dataDir=" + dataDir
                + ", auth=" + (authRequired() ? "on" : "off")
                + ", static=" + staticDir.map(Path::toString).orElse("-")
                + ", cors=" + corsOrigins.size()
                + ", workers=" + workerThreads
                + '}';
    }
}
