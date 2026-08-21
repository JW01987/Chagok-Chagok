package com.chagok.presentation.auth.dto;

import com.chagok.presentation.user.dto.OnboardingRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SignupRequest {

	@Email(message = "이메일 형식이 올바르지 않습니다")
	@NotBlank
	private String email;

	@Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
	@NotBlank
	private String password;

	@Size(min = 1, max = 50, message = "닉네임은 1~50자 이내여야 합니다")
	@NotBlank
	private String nickname;

	// 게스트가 앱 로컬에 저장해둔 온보딩 데이터 (nullable — 있으면 회원가입 시 자동 마이그레이션)
	@Valid
	private OnboardingRequest onboarding;
}
