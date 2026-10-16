package com.web.shoppingweb.controller.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.validation.BindingResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import com.web.shoppingweb.dto.product.ProductFormDTO;
import com.web.shoppingweb.dto.user.UserResponseDTO;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.entity.product.ProductCategory;
import com.web.shoppingweb.service.ProductService;
import com.web.shoppingweb.service.ProductRecommendationService;
import com.web.shoppingweb.service.CartService;
import com.web.shoppingweb.service.SavedService;
import com.web.shoppingweb.dto.cart.CartSummaryDTO;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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

    @Test
    void productFormBindsMultipleVariantsAndIgnoresSellerFeaturedFlag() throws Exception {
        ProductService service = mock(ProductService.class);
        MockMvc mvc = productMvc(service, mock(JdbcTemplate.class), false);
        mvc.perform(post("/products")
                        .principal(sellerAuthentication())
                        .param("name", "Linen shirt").param("description", "Linen, relaxed fit")
                        .param("category", "T_SHIRTS").param("imageUrl", "https://example.com/shirt.jpg")
                        .param("price", "199000").param("featured", "true")
                        .param("variants[0].sizeId", "1").param("variants[0].colorId", "2")
                        .param("variants[0].price", "199000").param("variants[0].stockQty", "8")
                        .param("variants[1].sizeId", "3").param("variants[1].colorId", "2")
                        .param("variants[1].price", "209000").param("variants[1].stockQty", "5"))
                .andExpect(status().is3xxRedirection());
        ArgumentCaptor<ProductFormDTO> form = ArgumentCaptor.forClass(ProductFormDTO.class);
        verify(service).createProduct(form.capture(), eq("seller"));
        assertEquals(2, form.getValue().getVariants().size());
        assertEquals(3L, form.getValue().getVariants().get(1).getSizeId());
        assertEquals(new BigDecimal("209000"), form.getValue().getVariants().get(1).getPrice());
        assertEquals(8, form.getValue().getVariants().get(0).getStockQty());
        assertFalse(new org.springframework.beans.BeanWrapperImpl(form.getValue()).isWritableProperty("featured"));
    }

    @Test
    void invalidVariantReturnsNestedFieldErrorsWithoutSaving() throws Exception {
        ProductService service = mock(ProductService.class);
        var result = productMvc(service, mock(JdbcTemplate.class), false)
                .perform(post("/products").principal(sellerAuthentication())
                        .param("name", "Linen shirt").param("description", "Linen, relaxed fit")
                        .param("category", "T_SHIRTS").param("imageUrl", "https://example.com/shirt.jpg")
                        .param("price", "199000")
                        .param("variants[0].price", "19.99").param("variants[0].stockQty", "-1"))
                .andExpect(status().is3xxRedirection()).andReturn();
        BindingResult errors = (BindingResult) result.getFlashMap()
                .get("org.springframework.validation.BindingResult.productForm");
        assertNotNull(errors);
        assertTrue(errors.hasFieldErrors("variants[0].price"));
        assertTrue(errors.hasFieldErrors("variants[0].stockQty"));
        verifyNoInteractions(service);
    }

    @Test
    void catalogRendersIndexedVariantFieldsAndRetainsValidationMessages() throws Exception {
        ProductService service = mock(ProductService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(service.getCatalogPage(isNull(), isNull(), any(Pageable.class))).thenReturn(Page.empty());
        when(service.getAvailableCategories()).thenReturn(List.of(ProductCategory.T_SHIRTS));
        when(jdbc.queryForList(contains("FROM sizes"))).thenReturn(
                List.of(Map.<String, Object>of("id", 1L, "label", "M", "size_system", "INT")));
        when(jdbc.queryForList(contains("FROM colors"))).thenReturn(
                List.of(Map.<String, Object>of("id", 2L, "name", "White")));
        MockMvc mvc = productMvc(service, jdbc, true);
        var invalid = mvc.perform(post("/products").principal(sellerAuthentication())
                        .param("name", "Linen shirt").param("description", "Linen, relaxed fit")
                        .param("category", "T_SHIRTS").param("imageUrl", "https://example.com/shirt.jpg")
                        .param("price", "199000")
                        .param("variants[0].price", "19.99").param("variants[0].stockQty", "-1"))
                .andReturn();
        UserResponseDTO seller = new UserResponseDTO();
        seller.setUsername("seller"); seller.setFullName("Seller"); seller.setRole("SELLER");
        String html = mvc.perform(get("/catalog").flashAttrs(invalid.getFlashMap())
                        .flashAttr("canManageProducts", true).flashAttr("signedIn", true)
                        .flashAttr("hasDashboardAccess", true).flashAttr("dashboardUrl", "/dashboard")
                        .flashAttr("currentUser", seller).flashAttr("cartItemCount", 0)
                        .flashAttr("savedProductIds", Set.of())
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("name=\"variants[0].sizeId\""));
        assertTrue(html.contains("name=\"variants[0].colorId\""));
        assertTrue(html.contains("name=\"variants[0].price\""));
        assertTrue(html.contains("name=\"variants[0].stockQty\""));
        BindingResult errors = (BindingResult) invalid.getFlashMap()
                .get("org.springframework.validation.BindingResult.productForm");
        assertTrue(html.contains(errors.getFieldError("variants[0].stockQty").getDefaultMessage()));
        assertFalse(html.contains("name=\"featured\""));
        assertTrue(html.contains("<textarea"));
    }

    @Test
    void selectedSkuAjaxReturnsServerCartCountWithoutRedirect() throws Exception {
        CartService cart = mock(CartService.class);
        when(cart.addVariant("seller", 20L, 102L, 2))
                .thenReturn(new CartSummaryDTO(List.of(), 7, new BigDecimal("1234000")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CartController(cart, mock(SavedService.class))).build();
        mvc.perform(post("/cart/items/20").principal(sellerAuthentication())
                        .accept(MediaType.APPLICATION_JSON)
                        .param("variantId", "102").param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalQuantity").value(7));
        verify(cart).addVariant("seller", 20L, 102L, 2);
        verify(cart, never()).addProduct(anyString(), anyLong(), anyInt());
    }

    @Test
    void savedAjaxReturnsActualSavedStateRatherThanAnOpaqueRedirect() throws Exception {
        SavedService saved = mock(SavedService.class);
        when(saved.toggleSavedProduct("seller", 20L)).thenReturn(false);
        when(saved.countSavedItems("seller")).thenReturn(3L);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SavedController(saved)).build();
        mvc.perform(post("/saved/20/toggle").principal(sellerAuthentication())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saved").value(false))
                .andExpect(jsonPath("$.savedItemCount").value(3));
        verify(saved).toggleSavedProduct("seller", 20L);
    }

    @Test
    void selectedSkuFailureReturnsJsonBadRequestWithoutSuccessCount() throws Exception {
        CartService cart = mock(CartService.class);
        when(cart.addVariant("seller", 20L, 102L, 2))
                .thenThrow(new IllegalArgumentException("Not enough stock."));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CartController(cart, mock(SavedService.class))).build();
        mvc.perform(post("/cart/items/20").principal(sellerAuthentication())
                        .accept(MediaType.APPLICATION_JSON)
                        .param("variantId", "102").param("quantity", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Not enough stock."))
                .andExpect(jsonPath("$.totalQuantity").doesNotExist());
    }

    @Test
    @SuppressWarnings("unchecked")
    void productDetailRendersSkuStockGallerySellerAndAccessibleRecommendations() throws Exception {
        ProductService service = mock(ProductService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        Product product = new Product();
        product.setId(20L); product.setName("Linen shirt"); product.setSlug("linen-shirt");
        product.setPrice(new BigDecimal("199000")); product.setDescription("Linen, relaxed fit");
        product.setCategory(ProductCategory.T_SHIRTS); product.setImageUrl("https://example.com/front.jpg");
        com.web.shoppingweb.entity.user.User merchant = new com.web.shoppingweb.entity.user.User();
        merchant.setUsername("merchant"); merchant.setFullName("Linen Store"); product.setSeller(merchant);
        when(service.getProductDetail("linen-shirt")).thenReturn(product);
        List<Map<String, Object>> variants = List.of(
                Map.of("id", 101L, "sku", "P20-V001", "size_label", "M", "size_system", "INT",
                        "color_name", "White", "price_amount", 199000L, "stock_qty", 5),
                Map.of("id", 102L, "sku", "P20-V002", "size_label", "L", "size_system", "INT",
                        "color_name", "Black", "price_amount", 209000L, "stock_qty", 0));
        when(jdbc.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(20L)))
                .thenAnswer(invocation -> variants);
        when(jdbc.queryForList(anyString(), eq(String.class), eq(20L)))
                .thenReturn(List.of("https://example.com/front.jpg", "https://example.com/back.jpg"));
        UserResponseDTO buyer = new UserResponseDTO();
        buyer.setUsername("buyer"); buyer.setFullName("Buyer"); buyer.setRole("CUSTOMER");
        String html = productMvc(service, jdbc, true).perform(get("/products/linen-shirt")
                        .flashAttr("signedIn", true).flashAttr("hasDashboardAccess", false)
                        .flashAttr("currentUser", buyer).flashAttr("cartItemCount", 0)
                        .flashAttr("savedProductIds", Set.of())
                        .requestAttr("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "test-token")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("name=\"variantId\""));
        assertTrue(html.contains("data-stock=\"5\""));
        assertTrue(html.contains("data-stock=\"0\""));
        assertTrue(html.contains("Out of stock"));
        assertTrue(html.contains("Linen Store"));
        assertTrue(html.contains("https://example.com/back.jpg"));
        assertFalse(html.contains("cdn.tailwindcss.com"));
        assertFalse(html.contains("Catalog slug"));
    }

    private UsernamePasswordAuthenticationToken sellerAuthentication() {
        return new UsernamePasswordAuthenticationToken("seller", "unused",
                List.of(new SimpleGrantedAuthority("ROLE_SELLER")));
    }

    private MockMvc productMvc(ProductService service, JdbcTemplate jdbc, boolean renderTemplate) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        var builder = MockMvcBuilders.standaloneSetup(new ProductController(service,
                mock(ProductRecommendationService.class), jdbc)).setValidator(validator);
        if (renderTemplate) {
            ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
            resolver.setPrefix("templates/"); resolver.setSuffix(".html");
            resolver.setTemplateMode("HTML"); resolver.setCharacterEncoding("UTF-8");
            SpringTemplateEngine engine = new SpringTemplateEngine();
            engine.setTemplateResolver(resolver);
            ThymeleafViewResolver views = new ThymeleafViewResolver();
            views.setTemplateEngine(engine); views.setCharacterEncoding("UTF-8");
            builder.setViewResolvers(views);
        }
        return builder.build();
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
