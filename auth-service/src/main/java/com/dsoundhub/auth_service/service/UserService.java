package com.dsoundhub.auth_service.service;

import com.dsoundhub.auth_service.entity.User;
import com.dsoundhub.auth_service.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Mendapatkan daftar semua user.
     */
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Ban user — update flag di database DAN tulis ke Redis untuk real-time enforcement.
     * @param userId UUID user yang akan di-ban
     * @return User yang sudah di-ban
     */
    @Transactional
    public User banUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getRole().name().equals("ADMIN")) {
            throw new RuntimeException("Cannot ban an Admin user");
        }

        if (Boolean.TRUE.equals(user.getIsBanned())) {
            throw new RuntimeException("User is already banned");
        }

        // Update database
        user.setIsBanned(true);
        userRepository.save(user);

        return user;
    }

    /**
     * Unban user — hapus flag di database DAN hapus dari Redis.
     * @param userId UUID user yang akan di-unban
     * @return User yang sudah di-unban
     */
    @Transactional
    public User unbanUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!Boolean.TRUE.equals(user.getIsBanned())) {
            throw new RuntimeException("User is not banned");
        }

        // Update database
        user.setIsBanned(false);
        userRepository.save(user);

        return user;
    }
}
