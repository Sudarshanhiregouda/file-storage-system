package com.filestorage.config;

import com.filestorage.entity.AuthProvider;
import com.filestorage.entity.Role;
import com.filestorage.entity.User;
import com.filestorage.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds a single ADMIN account on first boot so RBAC-protected endpoints
 * (stats, audit log, deleted files) are reachable without manual SQL.
 * Override the defaults via ADMIN_USERNAME/ADMIN_PASSWORD/ADMIN_EMAIL env vars.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminEmail;

    public AdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${admin.username:admin}") String adminUsername,
            @Value("${admin.password:admin123}") String adminPassword,
            @Value("${admin.email:admin@filestorage.local}") String adminEmail) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminEmail = adminEmail;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ADMIN);
        admin.setAuthProvider(AuthProvider.LOCAL);
        userRepository.save(admin);

        log.warn("Seeded default ADMIN account '{}'. Change ADMIN_PASSWORD env var before deploying anywhere real.",
                adminUsername);
    }
}
