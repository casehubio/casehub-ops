package io.casehub.ops.testing.env;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;

public final class ServiceHealthWaiter {

    private ServiceHealthWaiter() {}

    public static void waitForReady(String serviceUrl, String healthPath,
                                     Duration timeout) {
        String url = serviceUrl + healthPath;
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                        URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(500);
                conn.setReadTimeout(500);
                try {
                    if (conn.getResponseCode() == 200) return;
                } finally {
                    conn.disconnect();
                }
            } catch (IOException ignored) {}
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted waiting for " + url);
            }
        }
        throw new IllegalStateException(
                serviceUrl + " did not become healthy within " + timeout
                + " (checked " + url + ")");
    }
}
