package com.web.shoppingweb.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutApiIntegrationTest {

    private static final String BUYER_EMAIL = "nguyennn@gmail.com";
    private static final String BUYER_PASSWORD = "123456";
    private static final long PRODUCT_ID = 189691075L;
    private static final long VARIANT_ID = 596L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkoutApiCreatesOrderWithBearerToken() throws Exception {
        String token = loginAndExtractJwtToken();
        String bearerToken = "Bearer " + token;

        mockMvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "productId", PRODUCT_ID,
                                "quantity", 1
                        ))))
                .andDo(print())
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/cart/items/{variantId}", VARIANT_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("quantity", 1))))
                .andDo(print())
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "selectedVariantIds", List.of(VARIANT_ID),
                                "shippingAddress", "Concurrency integration test address"
                        ))))
                .andDo(print())
                .andExpect(status().isCreated());
    }

    private String loginAndExtractJwtToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of(
                                "email", BUYER_EMAIL,
                                "password", BUYER_PASSWORD
                        ))))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.path("token").asText();
        assertFalse(token.isBlank(), "Login response must contain a non-empty JWT token");
        return token;
    }

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
