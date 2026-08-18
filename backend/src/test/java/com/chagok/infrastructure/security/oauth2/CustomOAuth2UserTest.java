package com.chagok.infrastructure.security.oauth2;

import com.chagok.domain.user.User;
import com.chagok.domain.user.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CustomOAuth2UserTest {

	@Test
	@DisplayName("userId, email, attributes를 정확히 반환한다")
	void should_exposeUserIdEmailAndAttributes_when_created() {
		User user = withId(User.builder().email("test@test.com").status(UserStatus.ACTIVE).build(), 1L);
		Map<String, Object> attributes = Map.of("id", 12345L);

		CustomOAuth2User oAuth2User = new CustomOAuth2User(user, attributes);

		assertThat(oAuth2User.getUser()).isEqualTo(user);
		assertThat(oAuth2User.getUserId()).isEqualTo(1L);
		assertThat(oAuth2User.getEmail()).isEqualTo("test@test.com");
		assertThat(oAuth2User.getAttributes()).isEqualTo(attributes);
		assertThat(oAuth2User.getName()).isEqualTo("1");
		assertThat(oAuth2User.getAuthorities())
			.extracting("authority")
			.containsExactly("ROLE_USER");
	}

	private User withId(User user, Long id) {
		try {
			Field field = User.class.getDeclaredField("id");
			field.setAccessible(true);
			field.set(user, id);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		return user;
	}
}
