package org.example.connectcg_be.config;

import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.demo-accounts.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.demo-accounts.password:}")
    private String demoAccountsPassword;

    @Override
    public void run(String... args) {
        if (demoAccountsPassword == null || demoAccountsPassword.isBlank()) {
            throw new IllegalStateException(
                    "DEMO_ACCOUNTS_PASSWORD is required when demo accounts are enabled");
        }
        List<String> usernames = List.of("admin", "john_doe", "jane_smith", "bob_wilson", "alice_brown", "charlie_davis");
        String defaultPasswordHash = passwordEncoder.encode(demoAccountsPassword);

        for (String username : usernames) {
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setPasswordHash(defaultPasswordHash);
                user.setIsEnabled(true);
                user.setIsLocked(false);
                user.setIsDeleted(false);
                userRepository.save(user);
            });
        }

        // Tạo Admin nếu chưa có
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@connect.com");
            admin.setPasswordHash(defaultPasswordHash);
            admin.setRole("ADMIN");
            admin.setIsEnabled(true);
            admin.setIsLocked(false);
            admin.setIsDeleted(false);
            admin.setCreatedAt(Instant.now());
            userRepository.save(admin);
        }
    }
}
