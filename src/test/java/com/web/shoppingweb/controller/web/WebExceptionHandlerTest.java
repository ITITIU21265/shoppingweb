package com.web.shoppingweb.controller.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import com.web.shoppingweb.exception.SelfPurchaseException;
import com.web.shoppingweb.exception.web.WebExceptionHandler;

class WebExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new WebExceptionHandler())
            .build();

    @Test
    void selfPurchaseReturnsBadRequestErrorView() throws Exception {
        mockMvc.perform(get("/self-purchase"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error"))
                .andExpect(model().attribute(
                        "errorMessage",
                        "Sellers cannot add their own products to the shopping cart."
                ));
    }

    @Controller
    private static class ThrowingController {

        @GetMapping("/self-purchase")
        String selfPurchase() {
            throw new SelfPurchaseException(
                    "Sellers cannot add their own products to the shopping cart."
            );
        }
    }
}
