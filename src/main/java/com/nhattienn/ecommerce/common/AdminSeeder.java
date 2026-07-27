package com.nhattienn.ecommerce.common;

import com.nhattienn.ecommerce.user.User;
import com.nhattienn.ecommerce.user.UserRepository;
import com.nhattienn.ecommerce.user.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String adminEmail = "admin@ecommerce.com";

        if (userRepository.existsByEmail(adminEmail)) {
            return;
        }

        User admin = User.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode("Admin@123456"))
                .fullName("System Admin")
                .role(UserRole.ADMIN)
                .build();

        userRepository.save(admin);
        log.info("Admin account created: {}", adminEmail);
    }
}