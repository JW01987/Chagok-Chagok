package com.chagok.infrastructure.security.oauth2;

import com.chagok.domain.auth.RefreshToken;
import com.chagok.domain.auth.RefreshTokenRepository;
import com.chagok.domain.user.User;
import com.chagok.infrastructure.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenRepository refreshTokenRepository;

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request,
			HttpServletResponse response,
			Authentication authentication) throws IOException {
		CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
		User user = oAuth2User.getUser();

		String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail());
		String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

		refreshTokenRepository.save(RefreshToken.of(
			user, hashToken(refreshToken),
			request.getRemoteAddr(), request.getHeader("User-Agent"),
			jwtTokenProvider.getExpiration(refreshToken)));

		response.setContentType("application/json;charset=UTF-8");
		response.getWriter().write(
			"{\"accessToken\":\"%s\",\"refreshToken\":\"%s\"}".formatted(accessToken, refreshToken));
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다", e);
		}
	}
}
