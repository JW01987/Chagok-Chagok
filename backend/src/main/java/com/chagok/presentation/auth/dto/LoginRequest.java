package com.chagok.presentation.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

	@Email(message = "이메일 형식이 올바르지 않습니다")
	@NotBlank
	private String email;

	@NotBlank
	private String password;
}
