package org.example.connectcg_be.config;

import org.example.connectcg_be.entity.User;
import org.example.connectcg_be.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // Đảm bảo tất cả tài khoản mẫu có password là 'password123' và được kích hoạt
        List<String> usernames = List.of("admin", "john_doe", "jane_smith", "bob_wilson", "alice_brown", "charlie_davis");
        String defaultPasswordHash = passwordEncoder.encode("password123");

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
            System.out.println(">>> Đã tạo tài khoản Admin: admin / password123");
        } else {
            System.out.println(">>> Đã đồng bộ mật khẩu tài khoản Admin và Users mẫu: password123");
        }
    }
}
