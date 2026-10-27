package com.yusufnazim.deliverydispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        @Schema(description = "Items in the requested page", requiredMode = Schema.RequiredMode.REQUIRED)
        List<T> items,
        @Schema(description = "Requested zero-based page number", requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
        @Schema(description = "Requested page size", requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
        @Schema(description = "Total number of matching items", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalElements,
        @Schema(description = "Total number of pages; zero when no items match", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages
) {

    public PageResponse {
        items = List.copyOf(items);
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
