package com.traffic.api;

/**
 * Starts the HTTP API for the React UI.
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=com.traffic.api.ApiMain
 * </pre>
 */
public final class ApiMain {

    private ApiMain() {
    }

    public static void main(String[] args) throws Exception {
        int port = 8080;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        new ApiServer(port).start();
        Thread.currentThread().join();
    }
}
