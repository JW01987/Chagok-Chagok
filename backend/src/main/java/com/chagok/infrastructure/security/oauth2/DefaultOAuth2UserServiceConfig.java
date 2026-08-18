package com.chagok.infrastructure.security.oauth2;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;

// CustomOAuth2UserService가 위임(delegate)하는 기본 조회 로직을 별도 설정 클래스로 분리한다.
// SecurityConfig 안에 두면 SecurityConfig -> CustomOAuth2UserService -> (이 빈을 정의하는) SecurityConfig
// 순환 참조가 발생하므로, 다른 빈에 의존하지 않는 이 클래스로 독립시킨다.
@Configuration
public class DefaultOAuth2UserServiceConfig {

	@Bean
	public OAuth2UserService<OAuth2UserRequest, OAuth2User> defaultOAuth2UserService() {
		return new DefaultOAuth2UserService();
	}
}
