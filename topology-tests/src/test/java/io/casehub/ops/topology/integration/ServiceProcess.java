package io.casehub.ops.topology.integration;

import io.casehub.ops.testing.env.ServiceHealthWaiter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ServiceProcess {

    private final String name;
    private final Path jarPath;
    private final int port;
    private final Map<String, String> systemProperties;
    private volatile Process process;

    public ServiceProcess(String name, Path jarPath, int port,
                          Map<String, String> systemProperties) {
        this.name = name;
        this.jarPath = jarPath;
        this.port = port;
        this.systemProperties = systemProperties;
    }

    public void start() {
        var cmd = new ArrayList<String>();
        cmd.add("java");
        for (var entry : systemProperties.entrySet()) {
            cmd.add("-D" + entry.getKey() + "=" + entry.getValue());
        }
        cmd.add("-Dquarkus.http.port=" + port);
        cmd.add("-jar");
        cmd.add(jarPath.toString());

        Path logDir = Path.of("target/service-logs");
        try {
            Files.createDirectories(logDir);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectOutput(logDir.resolve(name + ".log").toFile());
            pb.redirectErrorStream(true);
            process = pb.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start " + name, e);
        }

        waitForHealth();
    }

    public void stop() {
        if (process == null || !process.isAlive()) return;
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    public int port() { return port; }

    public String baseUrl() { return "http://localhost:" + port; }

    public boolean isRunning() { return process != null && process.isAlive(); }

    public static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to find free port", e);
        }
    }

    public static Path resolveJar(String repo) {
        String familyRoot = System.getProperty("casehub.family.root");
        Path base;
        if (familyRoot != null) {
            base = Path.of(familyRoot);
        } else {
            base = Path.of("").toAbsolutePath().getParent().getParent();
        }
        Path jar = base.resolve(repo).resolve("target/quarkus-app/quarkus-run.jar");
        if (!Files.exists(jar)) {
            String module = repo.contains("/") ? repo.substring(repo.lastIndexOf('/') + 1) : repo;
            String project = repo.contains("/") ? repo.substring(0, repo.indexOf('/')) : repo;
            throw new IllegalStateException(
                    "Service jar not found at " + jar +
                    ". Run 'mvn -pl " + module + " package -DskipTests' in " + base.resolve(project));
        }
        return jar;
    }

    private void waitForHealth() {
        try {
            ServiceHealthWaiter.waitForReady(baseUrl(), "/q/health/ready",
                                             java.time.Duration.ofSeconds(60));
        } catch (IllegalStateException e) {
            if (!process.isAlive()) {
                stop();
                throw new IllegalStateException(
                        name + " process died during startup. Check target/service-logs/"
                        + name + ".log");
            }
            stop();
            throw new IllegalStateException(
                    name + " did not become healthy within 60s. Check target/service-logs/"
                    + name + ".log");
        }
    }
}
