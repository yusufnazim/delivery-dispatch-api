package com.yusufnazim.deliverydispatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
class SecurityConfigTest {

	private final PasswordEncoder passwordEncoder;

	@Autowired
	SecurityConfigTest(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	@Test
	void passwordEncoderUsesBcrypt() {
		assertThat(passwordEncoder).isInstanceOf(BCryptPasswordEncoder.class);
	}

	@Test
	void passwordEncoderMatchesEncodedPassword() {
		String rawPassword = "correct-horse-battery-staple";

		String encodedPassword = passwordEncoder.encode(rawPassword);

		assertThat(encodedPassword).isNotEqualTo(rawPassword);
		assertThat(passwordEncoder.matches(rawPassword, encodedPassword)).isTrue();
	}
}
