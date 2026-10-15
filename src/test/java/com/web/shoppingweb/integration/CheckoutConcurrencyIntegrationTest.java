package com.web.shoppingweb.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.shoppingweb.entity.user.Role;
import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.entity.user.UserStatus;
import com.web.shoppingweb.repository.user.RoleRepository;
import com.web.shoppingweb.repository.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutConcurrencyIntegrationTest {

    private static final int CONCURRENT_BUYERS = 50;
    private static final int INITIAL_STOCK = 5;
    private static final String BUYER_PASSWORD = "123456";
    private static final long PRODUCT_ID = 189691075L;
    private static final long VARIANT_ID = 596L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void prepareConcurrencyData() {
        jdbcTemplate.update("""
                update products
                set active = true, status = 'ACTIVE'
                where id = ?
                """, PRODUCT_ID);

        int updatedVariants = jdbcTemplate.update("""
                update product_variants
                set stock_qty = ?, status = 'ACTIVE'
                where id = ? and product_id = ?
                """, INITIAL_STOCK, VARIANT_ID, PRODUCT_ID);
        assertEquals(1, updatedVariants, "Variant 596 for product 189691075 must exist.");

        Role customerRole = roleRepository.findByCode("CUSTOMER")
                .orElseThrow(() -> new IllegalStateException("CUSTOMER role is required."));

        for (int buyerNo = 1; buyerNo <= CONCURRENT_BUYERS; buyerNo++) {
            String username = buyerUsername(buyerNo);
            User buyer = userRepository.findByUsername(username).orElseGet(User::new);
            buyer.setUsername(username);
            buyer.setEmail(buyerEmail(buyerNo));
            buyer.setFullName("Concurrency Buyer " + buyerNo);
            buyer.setPassword(passwordEncoder.encode(BUYER_PASSWORD));
            buyer.setRoles(Set.of(customerRole));
            buyer.setStatus(UserStatus.ACTIVE);
            userRepository.save(buyer);
        }
    }

    @Test
    void fiftyConcurrentCheckoutRequestsDoNotOversellLockedInventory() throws Exception {
        List<String> bearerTokens = new ArrayList<>();
        for (int buyerNo = 1; buyerNo <= CONCURRENT_BUYERS; buyerNo++) {
            String bearerToken = "Bearer " + loginAndExtractJwtToken(buyerEmail(buyerNo));
            prepareCartForCheckout(bearerToken);
            bearerTokens.add(bearerToken);
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_BUYERS);
        CountDownLatch readyGate = new CountDownLatch(CONCURRENT_BUYERS);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<CheckoutResult>> futures = new ArrayList<>();

        for (int index = 0; index < CONCURRENT_BUYERS; index++) {
            int buyerNo = index + 1;
            String bearerToken = bearerTokens.get(index);
            futures.add(executor.submit(() -> {
                readyGate.countDown();
                if (!startGate.await(10, TimeUnit.SECONDS)) {
                    return new CheckoutResult(buyerNo, 598, "Timed out waiting for concurrent start.");
                }
                return performCheckout(buyerNo, bearerToken);
            }));
        }

        assertTrue(readyGate.await(10, TimeUnit.SECONDS), "All checkout workers must be ready.");
        startGate.countDown();

        List<CheckoutResult> results = new ArrayList<>();
        for (Future<CheckoutResult> future : futures) {
            results.add(future.get(60, TimeUnit.SECONDS));
        }
        executor.shutdownNow();

        results.stream()
                .sorted(Comparator.comparingInt(CheckoutResult::buyerNo))
                .forEach(result -> System.out.printf(
                        "Buyer %02d -> HTTP %d -> %s%n",
                        result.buyerNo(),
                        result.status(),
                        abbreviate(result.body())
                ));

        long createdOrders = results.stream()
                .filter(result -> result.status() == 201)
                .count();
        long businessFailures = results.stream()
                .filter(result -> result.status() == 400)
                .count();
        List<CheckoutResult> serverErrors = results.stream()
                .filter(result -> result.status() >= 500)
                .toList();
        Integer remainingStock = jdbcTemplate.queryForObject(
                "select stock_qty from product_variants where id = ?",
                Integer.class,
                VARIANT_ID
        );

        System.out.printf(
                "Summary -> created=%d, businessFailures=%d, serverErrors=%d, remainingStock=%d%n",
                createdOrders,
                businessFailures,
                serverErrors.size(),
                remainingStock
        );

        assertTrue(serverErrors.isEmpty(), "Checkout must not return 5xx errors: " + serverErrors);
        assertEquals(INITIAL_STOCK, createdOrders, "Only available stock units may become orders.");
        assertEquals(CONCURRENT_BUYERS - INITIAL_STOCK, businessFailures, "Excess buyers should fail with out-of-stock business errors.");
        assertNotNull(remainingStock, "Remaining stock must be readable.");
        assertEquals(0, remainingStock, "Stock must stop at zero and never become negative.");
    }

    private CheckoutResult performCheckout(int buyerNo, String bearerToken) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "selectedVariantIds", List.of(VARIANT_ID),
                                "shippingAddress", "Concurrency test address for buyer " + buyerNo
                        ))))
                .andReturn();

        return new CheckoutResult(
                buyerNo,
                result.getResponse().getStatus(),
                result.getResponse().getContentAsString()
        );
    }

    private void prepareCartForCheckout(String bearerToken) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "productId", PRODUCT_ID,
                                "quantity", 1
                        ))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/cart/items/{variantId}", VARIANT_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("quantity", 1))))
                .andExpect(status().isOk());
    }

    private String loginAndExtractJwtToken(String email) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "email", email,
                                "password", BUYER_PASSWORD
                        ))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.path("token").asText();
        assertFalse(token.isBlank(), "Login response must contain a non-empty JWT token.");
        return token;
    }

    private String buyerUsername(int buyerNo) {
        return "concurrency_buyer_" + String.format("%02d", buyerNo);
    }

    private String buyerEmail(int buyerNo) {
        return "concurrency.buyer." + String.format("%02d", buyerNo) + "@example.test";
    }

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 160) {
            return value;
        }
        return value.substring(0, 157) + "...";
    }

    private record CheckoutResult(int buyerNo, int status, String body) {
    }
}
