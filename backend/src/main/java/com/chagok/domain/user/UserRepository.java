package com.chagok.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	boolean existsByEmailAndDeletedAtIsNull(String email);

	Optional<User> findByEmailAndDeletedAtIsNull(String email);
}
