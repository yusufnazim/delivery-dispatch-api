package com.yusufnazim.deliverydispatch.timeline.dto;

import com.yusufnazim.deliverydispatch.timeline.DeliveryEvent;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEventType;
import java.time.Instant;

public record DeliveryEventResponse(
        DeliveryEventType type,
        String description,
        Instant createdAt) {

    public static DeliveryEventResponse from(DeliveryEvent event) {
        return new DeliveryEventResponse(
                event.getEventType(),
                event.getDescription(),
                event.getCreatedAt());
    }
}
