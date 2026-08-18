package com.chagok.domain.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserOauthRepository extends JpaRepository<UserOauth, Long> {

	Optional<UserOauth> findByProviderAndProviderUserId(OAuthProvider provider, String providerUserId);
}
