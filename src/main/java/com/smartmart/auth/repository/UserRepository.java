package com.smartmart.auth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartmart.auth.domain.RoleName;
import com.smartmart.auth.domain.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    List<User> findAllByOrderByUsernameAsc();

    long countByActiveTrueAndRoles_Name(RoleName role);
}
