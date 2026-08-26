package com.example.studyassistant.config;

import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.util.HashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setFullName("System Administrator");
            admin.setUsername("admin");
            admin.setEmail("admin@study.ai");
            admin.setPassword(HashUtil.hash("Admin123!"));
            admin.setRole("ADMIN");
            admin.setPlan("PREMIUM");
            admin.setStatus("ACTIVE");
            admin.setFailedLoginAttempts(0);
            userRepository.save(admin);
            System.out.println(">>> [INIT] Created default ADMIN account (Username: admin | Password: Admin123!)");
        }
    }
}
