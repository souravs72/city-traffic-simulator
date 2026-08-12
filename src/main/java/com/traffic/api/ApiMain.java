package com.traffic.api;

/**
 * Starts the HTTP API for the React UI (and optional static UI bundle).
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=com.traffic.api.ApiMain
 * </pre>
 * Env: {@link ApiConfig#fromEnvironment()}.
 */
public final class ApiMain {

    private ApiMain() {
    }

    public static void main(String[] args) throws Exception {
        ApiConfig config = ApiConfig.fromEnvironment();
        if (args.length > 0) {
            int port = Integer.parseInt(args[0]);
            config = new ApiConfig(
                    port,
                    config.dataDir(),
                    config.corsOrigins(),
                    config.apiKey(),
                    config.staticDir(),
                    config.maxBodyBytes(),
                    config.maxRunTicks(),
                    config.maxCompareTicks(),
                    config.maxRows(),
                    config.maxCols(),
                    config.maxFleet(),
                    config.maxRush(),
                    config.workerThreads()
            );
        }
        new ApiServer(config).start();
        Thread.currentThread().join();
    }
}
