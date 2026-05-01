package com.awbd.cinema.repositories;

import com.awbd.cinema.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsUserByUsernameIgnoreCaseOrEmailIgnoreCase(String username,String email);
    Optional<User> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username,String email);
}
