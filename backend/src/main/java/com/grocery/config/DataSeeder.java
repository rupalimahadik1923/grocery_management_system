package com.grocery.config;

import com.grocery.model.*;
import com.grocery.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    private final StoreSettingsRepository storeSettingsRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      CategoryRepository categoryRepository,
                      ProductRepository productRepository,
                      CustomerRepository customerRepository,
                      StoreSettingsRepository storeSettingsRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.customerRepository = customerRepository;
        this.storeSettingsRepository = storeSettingsRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        seedSettings();
        seedCategoriesAndProducts();
        seedCustomers();
    }

    private void seedUsers() {
        if (userRepository.count() == 0) {
            logger.info("Database empty. Seeding initial staff and admin accounts...");

            userRepository.save(new User(
                    "admin",
                    passwordEncoder.encode("admin123"),
                    "Store Administrator",
                    "admin@grocery.local",
                    "9876543210",
                    UserRole.ADMIN
            ));

            userRepository.save(new User(
                    "manager",
                    passwordEncoder.encode("manager123"),
                    "Operations Manager",
                    "manager@grocery.local",
                    "9876543211",
                    UserRole.STORE_MANAGER
            ));

            userRepository.save(new User(
                    "cashier",
                    passwordEncoder.encode("cashier123"),
                    "Main Cashier",
                    "cashier@grocery.local",
                    "9876543212",
                    UserRole.CASHIER
            ));

            userRepository.save(new User(
                    "inventory",
                    passwordEncoder.encode("inventory123"),
                    "Inventory Supervisor",
                    "inventory@grocery.local",
                    "9876543213",
                    UserRole.INVENTORY_STAFF
            ));

            logger.info("Successfully seeded 4 initial staff accounts (admin, manager, cashier, inventory).");
        }
    }

    private void seedSettings() {
        if (storeSettingsRepository.count() == 0) {
            StoreSettings settings = new StoreSettings();
            storeSettingsRepository.save(settings);
            logger.info("Seeded default Store Settings.");
        }
    }

    private void seedCategoriesAndProducts() {
        if (categoryRepository.count() == 0) {
            logger.info("Seeding categories and grocery products...");

            Category dairy = categoryRepository.save(new Category("Dairy & Milk", "dairy-milk", "🥛", "Milk, butter, cheese, and curd", 1));
            Category produce = categoryRepository.save(new Category("Fresh Produce", "fresh-produce", "🍎", "Fresh fruits and daily vegetables", 2));
            Category bakery = categoryRepository.save(new Category("Bakery & Bread", "bakery-bread", "🍞", "Fresh breads, buns, and morning bakes", 3));
            Category staples = categoryRepository.save(new Category("Staples & Grains", "staples-grains", "🌾", "Rice, flour, pulses, and spices", 4));
            Category beverages = categoryRepository.save(new Category("Beverages", "beverages", "🧃", "Juices, soft drinks, tea, and coffee", 5));
            Category snacks = categoryRepository.save(new Category("Snacks & Munchies", "snacks", "🍿", "Chips, biscuits, and chocolates", 6));
            Category household = categoryRepository.save(new Category("Household & Cleaning", "household", "🧼", "Detergents, cleaners, and hygiene", 7));

            if (productRepository.count() == 0) {
                productRepository.save(new Product("Amul Taaza Milk 500ml", "SKU-MILK-01", "890126201001", dairy.getId(), dairy.getName(), "PACK", 24.0, 27.0, 50.0, 10.0));
                productRepository.save(new Product("Amul Butter 500g", "SKU-BTR-01", "890126201002", dairy.getId(), dairy.getName(), "PACK", 235.0, 275.0, 30.0, 8.0));
                productRepository.save(new Product("Farm Fresh Apples 1kg", "SKU-APL-01", "890126202001", produce.getId(), produce.getName(), "KG", 120.0, 160.0, 45.0, 12.0));
                productRepository.save(new Product("Fresh Bananas 1 Dozen", "SKU-BAN-01", "890126202002", produce.getId(), produce.getName(), "PCS", 40.0, 60.0, 35.0, 10.0));
                productRepository.save(new Product("Britannia Brown Bread 400g", "SKU-BRD-01", "890126203001", bakery.getId(), bakery.getName(), "PACK", 38.0, 50.0, 25.0, 5.0));
                productRepository.save(new Product("Aashirvaad Shudh Chakki Atta 5kg", "SKU-ATT-01", "890126204001", staples.getId(), staples.getName(), "PACK", 210.0, 260.0, 40.0, 10.0));
                productRepository.save(new Product("Tata Salt Iodized 1kg", "SKU-SLT-01", "890126204002", staples.getId(), staples.getName(), "PACK", 20.0, 28.0, 80.0, 15.0));
                productRepository.save(new Product("Fortune Sunlite Oil 1L", "SKU-OIL-01", "890126204003", staples.getId(), staples.getName(), "PACK", 115.0, 145.0, 35.0, 10.0));
                productRepository.save(new Product("Coca-Cola 750ml", "SKU-COKE-01", "890126205001", beverages.getId(), beverages.getName(), "PACK", 32.0, 40.0, 60.0, 15.0));
                productRepository.save(new Product("Lay's Classic Salted 50g", "SKU-LAYS-01", "890126206001", snacks.getId(), snacks.getName(), "PACK", 16.0, 20.0, 75.0, 20.0));
                productRepository.save(new Product("Surf Excel Easy Wash 1kg", "SKU-SRF-01", "890126207001", household.getId(), household.getName(), "PACK", 120.0, 150.0, 20.0, 5.0));

                logger.info("Successfully seeded 11 starter grocery products.");
            }
        }
    }

    private void seedCustomers() {
        if (customerRepository.count() == 0) {
            Customer c1 = new Customer("Rahul Sharma", "9876501234", "rahul.sharma@example.com", "Indiranagar, Bengaluru");
            c1.setLoyaltyPoints(140);
            c1.setTotalSpent(14200.0);
            c1.setOrdersCount(8);
            c1.setTier("SILVER");
            customerRepository.save(c1);

            Customer c2 = new Customer("Priya Patel", "9876505678", "priya.patel@example.com", "Koramangala, Bengaluru");
            c2.setLoyaltyPoints(320);
            c2.setTotalSpent(32500.0);
            c2.setOrdersCount(19);
            c2.setTier("GOLD");
            customerRepository.save(c2);

            logger.info("Seeded 2 sample customer loyalty accounts.");
        }
    }
}
