package com.chagok.infrastructure.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Component
public class AesEncryptionUtil {

	private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
	private static final int IV_LENGTH = 16;

	private final SecretKey secretKey;

	public AesEncryptionUtil(@Value("${encryption.aes-key}") String key) {
		byte[] keyBytes = Base64.getDecoder().decode(key);
		this.secretKey = new SecretKeySpec(keyBytes, "AES");
	}

	public String encrypt(String plainText) {
		try {
			byte[] iv = new byte[IV_LENGTH];
			new SecureRandom().nextBytes(iv);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(iv));
			byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

			// IV + 암호문을 함께 저장해야 복호화 시 IV를 복원할 수 있다
			byte[] combined = new byte[iv.length + encrypted.length];
			System.arraycopy(iv, 0, combined, 0, iv.length);
			System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
			return Base64.getEncoder().encodeToString(combined);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("암호화 실패", e);
		}
	}

	public String decrypt(String encryptedText) {
		try {
			byte[] combined = Base64.getDecoder().decode(encryptedText);
			byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
			byte[] encrypted = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
			return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("복호화 실패", e);
		}
	}
}
