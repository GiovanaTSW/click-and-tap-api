package mx.edu.clickandtap.api.web.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.edu.clickandtap.api.domain.Product;
import mx.edu.clickandtap.api.domain.ProductType;
import mx.edu.clickandtap.api.domain.Role;
import mx.edu.clickandtap.api.domain.User;
import mx.edu.clickandtap.api.domain.repository.ProductRepository;
import mx.edu.clickandtap.api.domain.repository.UserRepository;
import mx.edu.clickandtap.api.domain.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UserService userService;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Value("${app.admin.name:Admin}")
    private String adminName;
    @Value("${app.admin.email:}")
    private String adminEmail;
    @Value("${app.admin.password:}")
    private String adminPassword;
    @Value("${app.seed-sample-data:false}")
    private boolean seedSampleData;

    @Override
    public void run(String... args) {
        createAdmin();
        if (seedSampleData) createSampleProducts();
    }

    private void createAdmin() {
        if (adminEmail.isBlank() || adminPassword.isBlank()) return;
        if (userRepository.existsByEmail(adminEmail.trim().toLowerCase(Locale.ROOT))) return;
        User admin = userService.register(adminName, adminEmail, adminPassword, null);
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        log.info("Admin user created: {}", admin.getEmail());
    }

    private void createSampleProducts() {
        if (!productRepository.getAllActive().isEmpty()) return;
        save("Placa NFC personalizada", "Placa con chip NFC y grabado personalizado", ProductType.PLATE, "249.00", 30);
        save("Llavero NFC", "Llavero con chip NFC", ProductType.KEYCHAIN, "99.50", 50);
        save("Sticker QR", "Sticker resistente con código QR", ProductType.STICKER, "39.00", 100);
        save("Tag NFC adhesivo", "Tag NFC adhesivo para menús y perfiles", ProductType.NFC_TAG, "59.00", 80);
        log.info("Sample products created");
    }

    private void save(String name, String description, ProductType type, String price, int stock) {
        Product p = new Product();
        p.setName(name);
        p.setDescription(description);
        p.setType(type);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setCustomizable(true);
        p.setActive(true);
        productRepository.save(p);
    }
}