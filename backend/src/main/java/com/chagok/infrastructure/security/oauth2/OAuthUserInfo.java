package com.chagok.infrastructure.security.oauth2;

public interface OAuthUserInfo {

	String getProviderId();

	String getEmail();

	String getNickname();
}
