package com.grocery.config;

import com.grocery.model.*;
import com.grocery.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.CompoundIndexDefinition;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final StoreSettingsRepository storeSettingsRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;

    public DataSeeder(UserRepository userRepository,
                      CategoryRepository categoryRepository,
                      ProductRepository productRepository,
                      CustomerRepository customerRepository,
                      StoreSettingsRepository storeSettingsRepository,
                      StoreRepository storeRepository,
                      PasswordEncoder passwordEncoder,
                      MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.storeSettingsRepository = storeSettingsRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(String... args) {
        Store defaultStore = seedDefaultStore();
        seedUsers(defaultStore);
        seedSettings(defaultStore);
        seedCategoriesAndProducts(defaultStore);
        migrateLegacyProducts(defaultStore);
        seedCustomers(defaultStore);
        ensureIndexes();
    }

    private Store seedDefaultStore() {
        Store defaultStore = storeRepository.findByStoreCode("freshgrocery").orElseGet(() -> {
            Store store = new Store(
                    "freshgrocery",
                    "FreshGrocery Supermarket",
                    "Store Administrator",
                    "admin@grocery.local",
                    "+91 98765 43210",
                    "Bengaluru"
            );
            store.setAddress("Shop 12-14, Green Valley Commercial Complex");
            store.setGstin("29ABCDE1234F1Z5");
            return storeRepository.save(store);
        });

        // Ensure all legacy users have storeCode and storeId
        userRepository.findAll().forEach(u -> {
            if (u.getStoreCode() == null || u.getStoreId() == null) {
                u.setStoreId(defaultStore.getId());
                u.setStoreCode("freshgrocery");
                userRepository.save(u);
            }
        });

        return defaultStore;
    }

    private void seedUsers(Store defaultStore) {
        if (userRepository.count() == 0) {
            logger.info("Database empty. Seeding initial staff and admin accounts...");

            userRepository.save(new User(
                    "admin",
                    passwordEncoder.encode("admin123"),
                    "Store Administrator",
                    "admin@grocery.local",
                    "9876543210",
                    UserRole.ADMIN,
                    defaultStore.getId(),
                    defaultStore.getStoreCode()
            ));

            userRepository.save(new User(
                    "manager",
                    passwordEncoder.encode("manager123"),
                    "Operations Manager",
                    "manager@grocery.local",
                    "9876543211",
                    UserRole.STORE_MANAGER,
                    defaultStore.getId(),
                    defaultStore.getStoreCode()
            ));

            userRepository.save(new User(
                    "cashier",
                    passwordEncoder.encode("cashier123"),
                    "Main Cashier",
                    "cashier@grocery.local",
                    "9876543212",
                    UserRole.CASHIER,
                    defaultStore.getId(),
                    defaultStore.getStoreCode()
            ));

            userRepository.save(new User(
                    "inventory",
                    passwordEncoder.encode("inventory123"),
                    "Inventory Supervisor",
                    "inventory@grocery.local",
                    "9876543213",
                    UserRole.INVENTORY_STAFF,
                    defaultStore.getId(),
                    defaultStore.getStoreCode()
            ));

            logger.info("Successfully seeded 4 initial staff accounts (admin, manager, cashier, inventory).");
        }
    }

    private void seedSettings(Store defaultStore) {
        if (storeSettingsRepository.count() == 0) {
            StoreSettings settings = new StoreSettings();
            settings.setStoreId(defaultStore.getId());
            settings.setStoreName(defaultStore.getStoreName());
            storeSettingsRepository.save(settings);
            logger.info("Seeded default Store Settings.");
        }
    }

    private void seedCategoriesAndProducts(Store defaultStore) {
        if (categoryRepository.count() == 0) {
            logger.info("Seeding categories and grocery products...");

            Category dairy = categoryRepository.save(new Category("Dairy & Milk", "dairy-milk", "🥛", "Milk, butter, cheese, and curd", 1, defaultStore.getId()));
            Category produce = categoryRepository.save(new Category("Fresh Produce", "fresh-produce", "🍎", "Fresh fruits and daily vegetables", 2, defaultStore.getId()));
            Category bakery = categoryRepository.save(new Category("Bakery & Bread", "bakery-bread", "🍞", "Fresh breads, buns, and morning bakes", 3, defaultStore.getId()));
            Category staples = categoryRepository.save(new Category("Staples & Grains", "staples-grains", "🌾", "Rice, flour, pulses, and spices", 4, defaultStore.getId()));
            Category beverages = categoryRepository.save(new Category("Beverages", "beverages", "🧃", "Juices, soft drinks, tea, and coffee", 5, defaultStore.getId()));
            Category snacks = categoryRepository.save(new Category("Snacks & Munchies", "snacks", "🍿", "Chips, biscuits, and chocolates", 6, defaultStore.getId()));
            Category household = categoryRepository.save(new Category("Household & Cleaning", "household", "🧼", "Detergents, cleaners, and hygiene", 7, defaultStore.getId()));

            if (productRepository.count() == 0) {
                saveProductWithStore("Amul Taaza Milk 500ml", "SKU-MILK-01", "890126201001", dairy, "PACK", 24.0, 27.0, 50.0, 10.0, defaultStore.getId());
                saveProductWithStore("Amul Butter 500g", "SKU-BTR-01", "890126201002", dairy, "PACK", 235.0, 275.0, 30.0, 8.0, defaultStore.getId());
                saveProductWithStore("Farm Fresh Apples 1kg", "SKU-APL-01", "890126202001", produce, "KG", 120.0, 160.0, 45.0, 12.0, defaultStore.getId());
                saveProductWithStore("Fresh Bananas 1 Dozen", "SKU-BAN-01", "890126202002", produce, "PCS", 40.0, 60.0, 35.0, 10.0, defaultStore.getId());
                saveProductWithStore("Britannia Brown Bread 400g", "SKU-BRD-01", "890126203001", bakery, "PACK", 38.0, 50.0, 25.0, 5.0, defaultStore.getId());
                saveProductWithStore("Aashirvaad Shudh Chakki Atta 5kg", "SKU-ATT-01", "890126204001", staples, "PACK", 210.0, 260.0, 40.0, 10.0, defaultStore.getId());
                saveProductWithStore("Tata Salt Iodized 1kg", "SKU-SLT-01", "890126204002", staples, "PACK", 20.0, 28.0, 80.0, 15.0, defaultStore.getId());
                saveProductWithStore("Fortune Sunlite Oil 1L", "SKU-OIL-01", "890126204003", staples, "PACK", 115.0, 145.0, 35.0, 10.0, defaultStore.getId());
                saveProductWithStore("Coca-Cola 750ml", "SKU-COKE-01", "890126205001", beverages, "PACK", 32.0, 40.0, 60.0, 15.0, defaultStore.getId());
                saveProductWithStore("Lay's Classic Salted 50g", "SKU-LAYS-01", "890126206001", snacks, "PACK", 16.0, 20.0, 75.0, 20.0, defaultStore.getId());
                saveProductWithStore("Surf Excel Easy Wash 1kg", "SKU-SRF-01", "890126207001", household, "PACK", 120.0, 150.0, 20.0, 5.0, defaultStore.getId());

                logger.info("Successfully seeded 11 starter grocery products with storeId: {}", defaultStore.getId());
            }
        }
    }

    private void saveProductWithStore(String name, String sku, String barcode, Category cat, String unit,
                                      double cost, double selling, double stock, double minStock, String storeId) {
        Product p = new Product(name, sku, barcode, cat.getId(), cat.getName(), unit, cost, selling, stock, minStock);
        p.setStoreId(storeId);
        productRepository.save(p);
    }

    /**
     * Idempotent migration to backfill legacy products without storeId.
     */
    private void migrateLegacyProducts(Store defaultStore) {
        List<Product> legacy = productRepository.findAll().stream()
                .filter(p -> p.getStoreId() == null || p.getStoreId().trim().isEmpty())
                .toList();

        if (!legacy.isEmpty()) {
            logger.info("Found {} legacy products without storeId. Backfilling to store '{}' ({})...",
                    legacy.size(), defaultStore.getStoreName(), defaultStore.getId());
            for (Product p : legacy) {
                p.setStoreId(defaultStore.getId());
                productRepository.save(p);
            }
            logger.info("Successfully backfilled {} legacy products.", legacy.size());
        }
    }

    /**
     * Ensure compound unique indexes safely without losing records or crashing startup.
     */
    private void ensureIndexes() {
        try {
            mongoTemplate.indexOps(Product.class).ensureIndex(
                    new CompoundIndexDefinition(new org.bson.Document("storeId", 1).append("sku", 1))
                            .unique()
                            .named("store_sku_unique_idx")
            );
            logger.info("Verified compound unique index on Product collection: store_sku_unique_idx (storeId, sku)");
        } catch (Exception ex) {
            logger.warn("Could not ensure compound index on products: {}", ex.getMessage());
        }

        migrateCustomerPhoneIndex();
        migrateSupplierCodeIndex();
        migrateReturnIdempotencyIndex();
    }

    private void migrateReturnIdempotencyIndex() {
        try {
            var indexOps = mongoTemplate.indexOps(com.grocery.model.ReturnRecord.class);
            boolean hasLegacyIdx = indexOps.getIndexInfo().stream()
                    .anyMatch(idx -> "storeId_idempotencyKey_idx".equals(idx.getName()));
            if (hasLegacyIdx) {
                indexOps.dropIndex("storeId_idempotencyKey_idx");
                logger.info("Dropped legacy non-partial index 'storeId_idempotencyKey_idx' on returns collection.");
            }
        } catch (Exception ex) {
            logger.warn("Could not migrate return idempotency index: {}", ex.getMessage());
        }
    }

    private void migrateCustomerPhoneIndex() {
        try {
            var indexOps = mongoTemplate.indexOps(Customer.class);
            boolean hasLegacyGlobalPhone = indexOps.getIndexInfo().stream()
                    .anyMatch(idx -> "phone".equals(idx.getName()) && idx.isUnique());

            List<Customer> all = customerRepository.findAll();
            boolean hasMissingStore = all.stream().anyMatch(c -> c.getStoreId() == null || c.getStoreId().trim().isEmpty());
            long distinctPairs = all.stream()
                    .filter(c -> c.getStoreId() != null && c.getPhone() != null)
                    .map(c -> c.getStoreId() + "#" + c.getPhone())
                    .distinct().count();
            long nonNullPairs = all.stream()
                    .filter(c -> c.getStoreId() != null && c.getPhone() != null)
                    .count();

            if (hasMissingStore || distinctPairs < nonNullPairs) {
                logger.warn("Skipping Customer compound index migration: missing storeId or intra-store duplicate phone detected.");
                return;
            }
            if (hasLegacyGlobalPhone) {
                indexOps.dropIndex("phone");
                logger.info("Dropped legacy global unique index 'phone' on customers collection.");
            }
            indexOps.ensureIndex(
                    new CompoundIndexDefinition(new org.bson.Document("storeId", 1).append("phone", 1))
                            .unique()
                            .named("storeId_phone_unique_idx")
            );
            logger.info("Verified compound unique index on Customer collection: storeId_phone_unique_idx (storeId, phone)");
        } catch (Exception ex) {
            logger.warn("Customer index migration warning: {}", ex.getMessage());
        }
    }

    private void migrateSupplierCodeIndex() {
        try {
            var indexOps = mongoTemplate.indexOps(Supplier.class);
            boolean hasLegacyGlobalCode = indexOps.getIndexInfo().stream()
                    .anyMatch(idx -> "code".equals(idx.getName()) && idx.isUnique());

            List<Supplier> all = mongoTemplate.findAll(Supplier.class);
            boolean hasMissingStore = all.stream().anyMatch(s -> s.getStoreId() == null || s.getStoreId().trim().isEmpty());
            long distinctPairs = all.stream()
                    .filter(s -> s.getStoreId() != null && s.getCode() != null)
                    .map(s -> s.getStoreId() + "#" + s.getCode())
                    .distinct().count();
            long nonNullPairs = all.stream()
                    .filter(s -> s.getStoreId() != null && s.getCode() != null)
                    .count();

            if (hasMissingStore || distinctPairs < nonNullPairs) {
                logger.warn("Skipping Supplier compound index migration: missing storeId or intra-store duplicate code detected.");
                return;
            }
            if (hasLegacyGlobalCode) {
                indexOps.dropIndex("code");
                logger.info("Dropped legacy global unique index 'code' on suppliers collection.");
            }
            indexOps.ensureIndex(
                    new CompoundIndexDefinition(new org.bson.Document("storeId", 1).append("code", 1))
                            .unique()
                            .named("storeId_code_unique_idx")
            );
            logger.info("Verified compound unique index on Supplier collection: storeId_code_unique_idx (storeId, code)");
        } catch (Exception ex) {
            logger.warn("Supplier index migration warning: {}", ex.getMessage());
        }
    }

    private void seedCustomers(Store defaultStore) {
        if (customerRepository.count() == 0) {
            Customer c1 = new Customer("Rahul Sharma", "9876501234", "rahul.sharma@example.com", "Indiranagar, Bengaluru");
            c1.setStoreId(defaultStore.getId());
            c1.setLoyaltyPoints(140);
            c1.setTotalSpent(14500.0);
            c1.setOrdersCount(8);
            c1.setTier("SILVER");
            customerRepository.save(c1);

            Customer c2 = new Customer("Priya Patel", "9876505678", "priya.patel@example.com", "Koramangala, Bengaluru");
            c2.setStoreId(defaultStore.getId());
            c2.setLoyaltyPoints(35);
            c2.setTotalSpent(3800.0);
            c2.setOrdersCount(3);
            c2.setTier("BRONZE");
            customerRepository.save(c2);

            logger.info("Seeded 2 default Customer profiles.");
        } else {
            // Backfill only the 2 DataSeeder demo customers if they were seeded prior to storeId
            for (String demoPhone : List.of("9876501234", "9876505678")) {
                customerRepository.findByPhone(demoPhone).ifPresent(c -> {
                    if (c.getStoreId() == null || c.getStoreId().trim().isEmpty()) {
                        c.setStoreId(defaultStore.getId());
                        customerRepository.save(c);
                    }
                });
            }
        }
    }
}
