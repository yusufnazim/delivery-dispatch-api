package com.yusufnazim.deliverydispatch.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesStableErrorShape() throws Exception {
        ApiErrorResponse response = new ApiErrorResponse("VALIDATION_FAILED", "Request validation failed");

        String json = objectMapper.writeValueAsString(response);

        assertThat(json).isEqualTo("{\"code\":\"VALIDATION_FAILED\",\"message\":\"Request validation failed\",\"details\":[]}");
    }

    @Test
    void serializesOnlyFieldAndMessageForValidationDetails() throws Exception {
        ApiErrorResponse response = new ApiErrorResponse("VALIDATION_FAILED", "Request validation failed",
                List.of(new ValidationErrorDetail("email", "must not be blank")));

        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(response)))
                .isEqualTo(objectMapper.readTree("""
                        {"code":"VALIDATION_FAILED","message":"Request validation failed",
                         "details":[{"field":"email","message":"must not be blank"}]}
                        """));
    }

    @Test
    void detailsAreAnImmutableCopy() {
        var details = new ArrayList<>(List.of(new ValidationErrorDetail("email", "must not be blank")));
        ApiErrorResponse response = new ApiErrorResponse("VALIDATION_FAILED", "Request validation failed", details);
        details.clear();

        assertThat(response.details()).containsExactly(new ValidationErrorDetail("email", "must not be blank"));
        assertThatThrownBy(() -> response.details().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullDetailsNormalizeToAnEmptyArray() throws Exception {
        ApiErrorResponse response = new ApiErrorResponse("ORDER_NOT_FOUND", "Order not found: 1", null);

        assertThat(response.details()).isEmpty();
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(response)).get("details").isArray()).isTrue();
    }

    @Test
    void emptyDetailsRemainPresentWhenMapperOmitsEmptyValues() throws Exception {
        ObjectMapper nonEmptyMapper = objectMapper.copy().setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

        String json = nonEmptyMapper.writeValueAsString(new ApiErrorResponse("ACCESS_DENIED", "Access is denied"));

        assertThat(nonEmptyMapper.readTree(json).get("details")).isEqualTo(nonEmptyMapper.readTree("[]"));
    }
}
