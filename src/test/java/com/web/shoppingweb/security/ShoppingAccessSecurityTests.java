package com.web.shoppingweb.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.web.shoppingweb.entity.user.User;
import com.web.shoppingweb.entity.user.UserStatus;
import com.web.shoppingweb.repository.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ShoppingAccessSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void configureActiveAdmin() {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setStatus(UserStatus.ACTIVE);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
    }

    @Test
    void adminCannotAccessWebCart() throws Exception {
        mockMvc.perform(get("/cart").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCannotAccessApiCart() throws Exception {
        mockMvc.perform(get("/api/cart").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoleRemainsDeniedWhenAccountAlsoHasSellerRole() throws Exception {
        mockMvc.perform(get("/cart").with(user("admin").roles("ADMIN", "SELLER")))
                .andExpect(status().isForbidden());
    }
}
