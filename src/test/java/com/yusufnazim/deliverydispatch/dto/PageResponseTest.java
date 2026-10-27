package com.yusufnazim.deliverydispatch.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesOnlyThePublicPageContract() throws Exception {
        var response = PageResponse.from(new PageImpl<>(List.of("order"), PageRequest.of(2, 2), 5));

        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(response)))
                .isEqualTo(objectMapper.readTree("""
                        {"items":["order"],"page":2,"size":2,"totalElements":5,"totalPages":3}
                        """));
    }

    @Test
    void serializesEmptyItemsAsAnArrayAndPreservesLongTotals() throws Exception {
        var response = new PageResponse<>(List.of(), 0, 100, 3000000000L, 30000000);

        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(response)))
                .isEqualTo(objectMapper.readTree("""
                        {"items":[],"page":0,"size":100,"totalElements":3000000000,"totalPages":30000000}
                        """));
    }

    @Test
    void itemsAreAnImmutableCopy() {
        var items = new ArrayList<>(List.of("order"));
        var response = new PageResponse<>(items, 0, 20, 1, 1);
        items.clear();

        assertThat(response.items()).containsExactly("order");
        assertThatThrownBy(() -> response.items().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
