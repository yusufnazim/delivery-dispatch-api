package com.yusufnazim.deliverydispatch.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesStableErrorShape() throws Exception {
        ApiErrorResponse response = new ApiErrorResponse("VALIDATION_FAILED", "Request validation failed");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).isEqualTo("{\"code\":\"VALIDATION_FAILED\",\"message\":\"Request validation failed\"}");
    }
}
