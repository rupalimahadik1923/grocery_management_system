package com.grocery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocery.model.*;
import com.grocery.repository.CategoryRepository;
import com.grocery.repository.ProductRepository;
import com.grocery.repository.StoreRepository;
import com.grocery.repository.UserRepository;
import com.grocery.security.JwtTokenProvider;
import com.grocery.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PosCatalogLoadingBrutalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Store store;
    private Store storeOther;
    private String cashierToken;
    private String adminToken;
    private String otherStoreToken;
    private Category dairyCat;
    private Category snacksCat;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // Store A
        store = storeRepository.save(new Store(
                "pos-store-" + suffix,
                "POS Test Store",
                "Owner POS",
                "pos_" + suffix + "@test.local",
                "98111" + suffix.substring(0, 5),
                "Mumbai"
        ));

        // Store B (Cross-tenant)
        storeOther = storeRepository.save(new Store(
                "other-store-" + suffix,
                "Other Store",
                "Owner Other",
                "other_" + suffix + "@test.local",
                "98222" + suffix.substring(0, 5),
                "Delhi"
        ));

        // Cashier for Store A
        User cashier = userRepository.save(new User(
                "cashier_" + suffix,
                passwordEncoder.encode("password123"),
                "Cashier User",
                "cashier_" + suffix + "@test.local",
                "98111" + suffix.substring(0, 5),
                UserRole.CASHIER,
                store.getId(),
                store.getStoreCode()
        ));
        UserPrincipal principalCashier = new UserPrincipal(cashier);
        cashierToken = jwtTokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principalCashier, null, principalCashier.getAuthorities()));

        // Admin for Store A
        User admin = userRepository.save(new User(
                "admin_" + suffix,
                passwordEncoder.encode("password123"),
                "Admin User",
                "admin_" + suffix + "@test.local",
                "98333" + suffix.substring(0, 5),
                UserRole.ADMIN,
                store.getId(),
                store.getStoreCode()
        ));
        UserPrincipal principalAdmin = new UserPrincipal(admin);
        adminToken = jwtTokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principalAdmin, null, principalAdmin.getAuthorities()));

        // Cashier for Store B
        User otherCashier = userRepository.save(new User(
                "other_" + suffix,
                passwordEncoder.encode("password123"),
                "Other Cashier",
                "other_" + suffix + "@test.local",
                "98222" + suffix.substring(0, 5),
                UserRole.CASHIER,
                storeOther.getId(),
                storeOther.getStoreCode()
        ));
        UserPrincipal principalOther = new UserPrincipal(otherCashier);
        otherStoreToken = jwtTokenProvider.generateToken(
                new UsernamePasswordAuthenticationToken(principalOther, null, principalOther.getAuthorities()));

        // Categories
        dairyCat = categoryRepository.save(new Category("Dairy & Milk", "dairy-milk", "🥛", "Milk items", 1, store.getId()));
        snacksCat = categoryRepository.save(new Category("Snacks", "snacks", "🍿", "Snack items", 2, store.getId()));

        // Products for Store A
        Product p1 = new Product("Amul Milk 500ml", "SKU-MILK-" + suffix, "BAR-MILK-" + suffix, dairyCat.getId(), dairyCat.getName(), "PACK", 24.0, 27.0, 50.0, 10.0);
        p1.setStoreId(store.getId());
        p1.setStatus("ACTIVE");
        productRepository.save(p1);

        Product p2 = new Product("Lay's Classic", "SKU-LAYS-" + suffix, "BAR-LAYS-" + suffix, snacksCat.getId(), snacksCat.getName(), "PACK", 15.0, 20.0, 30.0, 5.0);
        p2.setStoreId(store.getId());
        p2.setStatus("ACTIVE");
        productRepository.save(p2);

        // Inactive product
        Product pInactive = new Product("Discontinued Biscuit", "SKU-DISC-" + suffix, "BAR-DISC-" + suffix, snacksCat.getId(), snacksCat.getName(), "PACK", 10.0, 12.0, 0.0, 5.0);
        pInactive.setStoreId(store.getId());
        pInactive.setStatus("INACTIVE");
        productRepository.save(pInactive);

        // Product for Store B
        Product pOther = new Product("Store B Product", "SKU-OTHER-" + suffix, "BAR-OTHER-" + suffix, null, null, "PCS", 10.0, 15.0, 100.0, 10.0);
        pOther.setStoreId(storeOther.getId());
        pOther.setStatus("ACTIVE");
        productRepository.save(pOther);
    }

    @Test
    @DisplayName("Brutal Test 1: Cashier can retrieve active products via /api/v1/products?status=ACTIVE")
    void shouldReturnActiveProductsForCashier() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].status", everyItem(is("ACTIVE"))))
                .andExpect(jsonPath("$[*].name", hasItems("Amul Milk 500ml", "Lay's Classic")))
                .andExpect(jsonPath("$[*].name", not(hasItem("Discontinued Biscuit"))))
                .andExpect(jsonPath("$[*].name", not(hasItem("Store B Product"))));
    }

    @Test
    @DisplayName("Brutal Test 2: Cashier can retrieve categories via /api/v1/categories")
    void shouldReturnCategoriesForCashier() throws Exception {
        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].name", hasItems("Dairy & Milk", "Snacks")));
    }

    @Test
    @DisplayName("Brutal Test 3: Unauthenticated request to /api/v1/products must return 401")
    void shouldRejectUnauthenticatedProductsRequest() throws Exception {
        mockMvc.perform(get("/api/v1/products?status=ACTIVE")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Brutal Test 4: Strict Tenant Isolation on POS Catalog")
    void shouldIsolatePosCatalogBetweenStores() throws Exception {
        // Cashier for Store B must only see Store B's product
        mockMvc.perform(get("/api/v1/products")
                        .param("status", "ACTIVE")
                        .header("Authorization", "Bearer " + otherStoreToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name", is("Store B Product")));
    }

    @Test
    @DisplayName("Brutal Test 5: Fallback /api/v1/products without status parameter returns all store products")
    void shouldReturnAllProductsOnFallbackEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + cashierToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].name", hasItems("Amul Milk 500ml", "Lay's Classic", "Discontinued Biscuit")));
    }
}
