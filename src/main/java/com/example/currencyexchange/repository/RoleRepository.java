package com.example.currencyexchange.repository;

import com.example.currencyexchange.model.AppRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for security roles.
 */
public interface RoleRepository extends JpaRepository<AppRole, Long> {

    Optional<AppRole> findByName(String name);
}
