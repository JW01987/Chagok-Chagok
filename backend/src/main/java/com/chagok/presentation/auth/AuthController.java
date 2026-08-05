package com.chagok.presentation.auth;

import com.chagok.application.auth.AuthService;
import com.chagok.presentation.auth.dto.LoginRequest;
import com.chagok.presentation.auth.dto.LoginResponse;
import com.chagok.presentation.auth.dto.LogoutRequest;
import com.chagok.presentation.auth.dto.ReissueRequest;
import com.chagok.presentation.auth.dto.ReissueResponse;
import com.chagok.presentation.auth.dto.SignupRequest;
import com.chagok.presentation.auth.dto.SignupResponse;
import com.chagok.presentation.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "인증 API")
public class AuthController {

	private final AuthService authService;

	@PostMapping("/signup")
	@Operation(summary = "회원가입")
	public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(authService.signup(request)));
	}

	@PostMapping("/login")
	@Operation(summary = "로그인")
	public ResponseEntity<ApiResponse<LoginResponse>> login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest) {
		String ip = servletRequest.getRemoteAddr();
		String deviceInfo = servletRequest.getHeader("User-Agent");
		return ResponseEntity.ok(ApiResponse.ok(authService.login(request, ip, deviceInfo)));
	}

	@PostMapping("/reissue")
	@Operation(summary = "Access Token 재발급")
	public ResponseEntity<ApiResponse<ReissueResponse>> reissue(@Valid @RequestBody ReissueRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(authService.reissue(request)));
	}

	@PostMapping("/logout")
	@Operation(summary = "로그아웃")
	public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequest request) {
		authService.logout(request);
		return ResponseEntity.ok(ApiResponse.ok(null));
	}
}
