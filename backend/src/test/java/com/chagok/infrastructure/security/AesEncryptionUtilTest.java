package com.chagok.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesEncryptionUtilTest {

	private static final String KEY = "8pxG7p9DiEoq2vmTMabg8FjwNElrauPYT6CZ3eggS30=";

	private AesEncryptionUtil aesEncryptionUtil;

	@BeforeEach
	void setUp() {
		aesEncryptionUtil = new AesEncryptionUtil(KEY);
	}

	@Test
	@DisplayName("암호화한 값을 복호화하면 원문과 같다")
	void should_returnOriginalText_when_decryptingEncryptedValue() {
		String plainText = "kakao-access-token-value";

		String encrypted = aesEncryptionUtil.encrypt(plainText);
		String decrypted = aesEncryptionUtil.decrypt(encrypted);

		assertThat(decrypted).isEqualTo(plainText);
	}

	@Test
	@DisplayName("같은 평문이라도 암호화할 때마다 다른 값이 생성된다")
	void should_produceDifferentCiphertext_when_encryptingSameTextTwice() {
		String plainText = "same-plain-text";

		String encryptedFirst = aesEncryptionUtil.encrypt(plainText);
		String encryptedSecond = aesEncryptionUtil.encrypt(plainText);

		assertThat(encryptedFirst).isNotEqualTo(encryptedSecond);
	}

	@Test
	@DisplayName("암호화된 값은 평문을 그대로 포함하지 않는다")
	void should_notContainPlainText_when_encrypted() {
		String plainText = "sensitive-oauth-token";

		String encrypted = aesEncryptionUtil.encrypt(plainText);

		assertThat(encrypted).doesNotContain(plainText);
	}

	@Test
	@DisplayName("잘못된 형식의 암호문을 복호화하면 예외가 발생한다")
	void should_throwException_when_decryptingMalformedText() {
		assertThatThrownBy(() -> aesEncryptionUtil.decrypt("not-a-valid-base64-cipher!!"))
			.isInstanceOf(RuntimeException.class);
	}
}
