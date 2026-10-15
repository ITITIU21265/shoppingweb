package com.web.shoppingweb.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CheckoutConcurrencyStressTest {

    private static final String BASE_URL = "http://localhost:8080/api";
    private static final int BUYER_COUNT = 10;
    private static final long PRODUCT_ID = 189691075L;
    private static final long VARIANT_ID = 596L;
    private static final String PASSWORD = "password123";
    private static final Path OUTPUT_CSV = Path.of("buyers.csv");
    private static final int EXPECTED_SUCCESS_STATUS = 201;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static void main(String[] args) {
        try {
            new CheckoutConcurrencyStressTest().run();
        } catch (Exception ex) {
            System.err.println("Checkout concurrency stress test failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private void run() throws IOException, InterruptedException {
        List<String> preparedTokens = prepareBuyers();
        writeJwtTokens(preparedTokens);

        List<String> tokensFromCsv = readJwtTokens();
        executeStressTest(tokensFromCsv);
    }

    private List<String> prepareBuyers() throws IOException, InterruptedException {
        List<String> tokens = new ArrayList<>();

        for (int i = 1; i <= BUYER_COUNT; i++) {
            String username = "testuser_" + i;
            String email = "testuser_" + i + "@example.com";
            String fullName = "Test User " + i;

            System.out.println("\n=== Preparing buyer " + i + " (" + email + ") ===");
            registerUser(username, email, fullName);

            String token = loginAndGetToken(email);
            addProductToCart(token);
            forceCartQuantityToOne(token);
            tokens.add(token);

            System.out.println("Buyer " + i + " is ready.");
        }

        return tokens;
    }

    private void registerUser(String username, String email, String fullName) throws IOException, InterruptedException {
        String body = """
                {"username":"%s","email":"%s","password":"%s","fullName":"%s"}
                """.formatted(jsonEscape(username), jsonEscape(email), jsonEscape(PASSWORD), jsonEscape(fullName));

        HttpResponse<String> response = postJson("/auth/register", body, Optional.empty());
        System.out.println("Register status: " + response.statusCode());

        if (!isSuccessful(response.statusCode())) {
            System.out.println("Register response: " + response.body());
            System.out.println("Continuing to login in case this test user already exists.");
        }
    }

    private String loginAndGetToken(String email) throws IOException, InterruptedException {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(jsonEscape(email), jsonEscape(PASSWORD));

        HttpResponse<String> response = postJson("/auth/login", body, Optional.empty());
        System.out.println("Login status: " + response.statusCode());

        if (!isSuccessful(response.statusCode())) {
            throw new IllegalStateException("Login failed for " + email + ". Response: " + response.body());
        }

        Matcher matcher = TOKEN_PATTERN.matcher(response.body());
        if (!matcher.find()) {
            throw new IllegalStateException("Login response does not contain a token field: " + response.body());
        }

        return matcher.group(1);
    }

    private void addProductToCart(String jwtToken) throws IOException, InterruptedException {
        String body = """
                {"productId":%d,"quantity":1}
                """.formatted(PRODUCT_ID);

        HttpResponse<String> response = postJson("/cart/items", body, Optional.of(jwtToken));
        System.out.println("Add-to-cart status: " + response.statusCode());

        if (!isSuccessful(response.statusCode())) {
            throw new IllegalStateException("Add-to-cart failed. Response: " + response.body());
        }
    }

    private void forceCartQuantityToOne(String jwtToken) throws IOException, InterruptedException {
        String body = """
                {"quantity":1}
                """;

        HttpResponse<String> response = putJson("/cart/items/" + VARIANT_ID, body, jwtToken);
        System.out.println("Normalize cart quantity status: " + response.statusCode());

        if (!isSuccessful(response.statusCode())) {
            throw new IllegalStateException("Cart quantity normalization failed. Response: " + response.body());
        }
    }

    private void writeJwtTokens(List<String> tokens) throws IOException {
        List<String> csvLines = new ArrayList<>();
        csvLines.add("jwt_token");
        csvLines.addAll(tokens);
        Files.write(OUTPUT_CSV, csvLines, StandardCharsets.UTF_8);
        System.out.println("\nWrote " + tokens.size() + " JWT tokens to " + OUTPUT_CSV.toAbsolutePath());
    }

    private List<String> readJwtTokens() throws IOException {
        if (!Files.exists(OUTPUT_CSV)) {
            throw new IOException("CSV file not found: " + OUTPUT_CSV.toAbsolutePath());
        }

        List<String> lines = Files.readAllLines(OUTPUT_CSV, StandardCharsets.UTF_8);
        List<String> tokens = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String token = lines.get(i).trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }

        if (tokens.isEmpty()) {
            throw new IllegalStateException("No JWT tokens found in " + OUTPUT_CSV.toAbsolutePath());
        }

        return tokens;
    }

    private void executeStressTest(List<String> jwtTokens) throws InterruptedException {
        int threadCount = jwtTokens.size();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        System.out.println("\nLoaded " + threadCount + " JWT tokens from " + OUTPUT_CSV.toAbsolutePath());
        System.out.println("Preparing " + threadCount + " concurrent checkout requests...");

        for (int i = 0; i < jwtTokens.size(); i++) {
            int threadNumber = i + 1;
            String token = jwtTokens.get(i);

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    HttpResponse<String> response = executeCheckout(token);
                    int statusCode = response.statusCode();

                    if (statusCode == EXPECTED_SUCCESS_STATUS) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }

                    System.out.println("Thread " + threadNumber + " -> HTTP " + statusCode);
                    if (statusCode >= 400) {
                        System.out.println("Thread " + threadNumber + " response: " + response.body());
                    }
                } catch (Exception ex) {
                    failureCount.incrementAndGet();
                    System.err.println("Thread " + threadNumber + " failed: " + ex.getMessage());
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        if (!readyLatch.await(30, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            throw new IllegalStateException("Timed out while waiting for worker threads to become ready.");
        }

        long startedAt = System.nanoTime();
        System.out.println("All threads are ready. Firing checkout requests now...");
        startLatch.countDown();

        if (!doneLatch.await(60, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            throw new IllegalStateException("Timed out while waiting for checkout requests to finish.");
        }

        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        executor.shutdown();

        System.out.println("\n=== Stress Test Summary ===");
        System.out.println("Total requests: " + threadCount);
        System.out.println("Successful requests (HTTP 201): " + successCount.get());
        System.out.println("Failed requests (non-201 or exception): " + failureCount.get());
        System.out.println("Total execution time: " + elapsedMillis + " ms");
    }

    private HttpResponse<String> executeCheckout(String jwtToken) throws IOException, InterruptedException {
        String body = """
                {"selectedVariantIds":[596],"shippingAddress":"Stress Test Address"}
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/orders"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + jwtToken)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> postJson(String path, String body, Optional<String> jwtToken)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

        jwtToken.ifPresent(token -> builder.header("Authorization", "Bearer " + token));

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> putJson(String path, String body, String jwtToken)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + jwtToken)
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static boolean isSuccessful(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private static String jsonEscape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
