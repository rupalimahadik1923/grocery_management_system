package com.grocery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grocery.model.*;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Store storeA;
    private Store storeB;
    private String tokenStoreA;
    private String tokenStoreB;

    @BeforeEach
    void setUp() {
        String testSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Create Store A
        storeA = storeRepository.save(new Store(
                "store-a-" + testSuffix,
                "Store A",
                "Owner A",
                "ownerA_" + testSuffix + "@test.local",
                "9800000001",
                "Bengaluru"
        ));

        User userA = userRepository.save(new User(
                "user_a_" + testSuffix,
                passwordEncoder.encode("password123"),
                "User A",
                "userA_" + testSuffix + "@test.local",
                "9800000001",
                UserRole.ADMIN,
                storeA.getId(),
                storeA.getStoreCode()
        ));

        UserPrincipal principalA = new UserPrincipal(userA);
        UsernamePasswordAuthenticationToken authA =
                new UsernamePasswordAuthenticationToken(principalA, null, principalA.getAuthorities());
        tokenStoreA = jwtTokenProvider.generateToken(authA);

        // Create Store B
        storeB = storeRepository.save(new Store(
                "store-b-" + testSuffix,
                "Store B",
                "Owner B",
                "ownerB_" + testSuffix + "@test.local",
                "9800000002",
                "Mumbai"
        ));

        User userB = userRepository.save(new User(
                "user_b_" + testSuffix,
                passwordEncoder.encode("password123"),
                "User B",
                "userB_" + testSuffix + "@test.local",
                "9800000002",
                UserRole.ADMIN,
                storeB.getId(),
                storeB.getStoreCode()
        ));

        UserPrincipal principalB = new UserPrincipal(userB);
        UsernamePasswordAuthenticationToken authB =
                new UsernamePasswordAuthenticationToken(principalB, null, principalB.getAuthorities());
        tokenStoreB = jwtTokenProvider.generateToken(authB);
    }

    @Test
    @DisplayName("Should reject unauthenticated requests with 401")
    void shouldRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isUnauthorized());

        Product p = new Product();
        p.setName("Unauthorized Item");
        p.setSku("UNAUTH-01");
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(p)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should strictly isolate product reads between Store A and Store B")
    void shouldIsolateProductsBetweenStoresOnRead() throws Exception {
        // 1. Store A creates Product A
        Product productA = new Product();
        productA.setName("Exclusive Store A Milk");
        productA.setSku("SKU-STORE-A-" + UUID.randomUUID().toString().substring(0, 6));
        productA.setCostPrice(20.0);
        productA.setSellingPrice(25.0);
        productA.setUnit("PACK");

        MvcResult resultA = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.storeId").value(storeA.getId()))
                .andReturn();

        String idA = objectMapper.readTree(resultA.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 2. Store A lists products -> Product A MUST be present
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(idA)));

        // 3. Store B lists products -> Product A MUST NOT be present
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(idA))));

        // 4. Store B attempts direct read by ID -> Must return 404 Not Found
        mockMvc.perform(get("/api/v1/products/" + idA)
                        .header("Authorization", "Bearer " + tokenStoreB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should block Store B from modifying Store A's product")
    void shouldBlockStoreBFromModifyingStoreAProduct() throws Exception {
        // 1. Store A creates product
        Product productA = new Product();
        productA.setName("Store A Original Item");
        productA.setSku("SKU-ORIG-" + UUID.randomUUID().toString().substring(0, 6));
        productA.setCostPrice(50.0);
        productA.setSellingPrice(75.0);
        productA.setUnit("PCS");

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productA)))
                .andExpect(status().isOk())
                .andReturn();

        String idA = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 2. Store B attempts to update Product A -> Must return 404 Not Found
        Product maliciousUpdate = new Product();
        maliciousUpdate.setName("Tampered Item Name");
        maliciousUpdate.setSku(productA.getSku());
        maliciousUpdate.setCostPrice(1.0);
        maliciousUpdate.setSellingPrice(2.0);

        mockMvc.perform(put("/api/v1/products/" + idA)
                        .header("Authorization", "Bearer " + tokenStoreB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousUpdate)))
                .andExpect(status().isNotFound());

        // 3. Verify Product A remains unchanged in Store A
        mockMvc.perform(get("/api/v1/products/" + idA)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Store A Original Item"))
                .andExpect(jsonPath("$.sellingPrice").value(75.0));
    }

    @Test
    @DisplayName("Should block Store B from deleting Store A's product")
    void shouldBlockStoreBFromDeletingStoreAProduct() throws Exception {
        // 1. Store A creates product
        Product productA = new Product();
        productA.setName("Store A Protected Item");
        productA.setSku("SKU-PROT-" + UUID.randomUUID().toString().substring(0, 6));
        productA.setCostPrice(10.0);
        productA.setSellingPrice(15.0);
        productA.setUnit("PACK");

        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(productA)))
                .andExpect(status().isOk())
                .andReturn();

        String idA = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // 2. Store B attempts to delete Product A -> Must return 404 Not Found
        mockMvc.perform(delete("/api/v1/products/" + idA)
                        .header("Authorization", "Bearer " + tokenStoreB))
                .andExpect(status().isNotFound());

        // 3. Verify Product A still exists in Store A
        mockMvc.perform(get("/api/v1/products/" + idA)
                        .header("Authorization", "Bearer " + tokenStoreA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idA));
    }

    @Test
    @DisplayName("Should permit same SKU in different stores, but reject duplicate within same store")
    void shouldAllowSameSkuInDifferentStoresButRejectWithinSameStore() throws Exception {
        String sharedSku = "SKU-SHARED-" + UUID.randomUUID().toString().substring(0, 6);

        // 1. Store A creates product with sharedSku -> 200 OK
        Product prodA1 = new Product();
        prodA1.setName("Store A Item");
        prodA1.setSku(sharedSku);
        prodA1.setCostPrice(10.0);
        prodA1.setSellingPrice(15.0);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodA1)))
                .andExpect(status().isOk());

        // 2. Store A attempts duplicate with same sharedSku -> 400 Bad Request
        Product prodA2 = new Product();
        prodA2.setName("Store A Duplicate Item");
        prodA2.setSku(sharedSku);
        prodA2.setCostPrice(12.0);
        prodA2.setSellingPrice(18.0);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodA2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsStringIgnoringCase("SKU already exists")));

        // 3. Store B creates product with SAME sharedSku -> MUST SUCCEED (200 OK)
        Product prodB = new Product();
        prodB.setName("Store B Item with Same SKU");
        prodB.setSku(sharedSku);
        prodB.setCostPrice(14.0);
        prodB.setSellingPrice(20.0);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prodB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(storeB.getId()))
                .andExpect(jsonPath("$.data.sku").value(sharedSku));
    }

    @Test
    @DisplayName("Should reject invalid negative prices with 400")
    void shouldRejectInvalidNegativePrices() throws Exception {
        Product invalidProd = new Product();
        invalidProd.setName("Negative Price Item");
        invalidProd.setSku("SKU-NEG-" + UUID.randomUUID().toString().substring(0, 6));
        invalidProd.setCostPrice(-10.0);
        invalidProd.setSellingPrice(20.0);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidProd)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        invalidProd.setCostPrice(10.0);
        invalidProd.setSellingPrice(-5.0);

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", "Bearer " + tokenStoreA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidProd)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
