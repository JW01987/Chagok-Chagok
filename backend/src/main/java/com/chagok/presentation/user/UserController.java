package com.chagok.presentation.user;

import com.chagok.application.user.OnboardingService;
import com.chagok.presentation.common.response.ApiResponse;
import com.chagok.presentation.user.dto.OnboardingRequest;
import com.chagok.presentation.user.dto.OnboardingResponse;
import com.chagok.presentation.user.dto.OnboardingUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User", description = "유저 API")
public class UserController {

	private final OnboardingService onboardingService;

	@PostMapping("/onboarding")
	@Operation(summary = "온보딩 저장")
	public ResponseEntity<ApiResponse<OnboardingResponse>> saveOnboarding(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody OnboardingRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(onboardingService.saveOnboarding(userId, request)));
	}

	@GetMapping("/onboarding")
	@Operation(summary = "온보딩 조회")
	public ResponseEntity<ApiResponse<OnboardingResponse>> getOnboarding(@AuthenticationPrincipal Long userId) {
		return ResponseEntity.ok(ApiResponse.ok(onboardingService.getOnboarding(userId)));
	}

	@PatchMapping("/onboarding")
	@Operation(summary = "온보딩 수정")
	public ResponseEntity<ApiResponse<OnboardingResponse>> updateOnboarding(
			@AuthenticationPrincipal Long userId,
			@Valid @RequestBody OnboardingUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.ok(onboardingService.updateOnboarding(userId, request)));
	}
}
