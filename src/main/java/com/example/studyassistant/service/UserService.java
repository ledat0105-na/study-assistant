package com.example.studyassistant.service;

import com.example.studyassistant.entity.User;
import com.example.studyassistant.repository.UserRepository;
import com.example.studyassistant.util.HashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public User register(User user) {
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        user.setPassword(HashUtil.hash(user.getPassword()));
        if (user.getRole() == null) {
            user.setRole("STUDENT");
        }
        return userRepository.save(user);
    }

    public Optional<User> login(String usernameOrEmail, String password) {
        Optional<User> userOpt = userRepository.findByUsername(usernameOrEmail);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmail(usernameOrEmail);
        }

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (HashUtil.verify(password, user.getPassword())) {
                // Auto-upgrade raw/legacy passwords to BCrypt
                if (!user.getPassword().startsWith("$2a$") && !user.getPassword().startsWith("$2b$")) {
                    user.setPassword(HashUtil.hash(password));
                    userRepository.save(user);
                }
                return Optional.of(user);
            }
        }
        return Optional.empty();
    }
}
