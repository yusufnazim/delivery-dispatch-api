package com.yusufnazim.deliverydispatch.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class JwtPropertiesTest {

    private final JwtProperties jwtProperties;

    @Autowired
    JwtPropertiesTest(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    @Test
    void loadsJwtPropertiesFromConfiguration() {
        assertThat(jwtProperties.issuer()).isEqualTo("delivery-dispatch-api-test");
        assertThat(jwtProperties.secret()).isEqualTo("test-jwt-secret-at-least-32-characters");
        assertThat(jwtProperties.expiration()).isEqualTo(Duration.ofMinutes(30));
    }
}
