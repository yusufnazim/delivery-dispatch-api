package com.yusufnazim.deliverydispatch.timeline;

import com.yusufnazim.deliverydispatch.order.DeliveryOrder;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "delivery_events",
        indexes = {
                @Index(name = "idx_delivery_events_delivery_order_id", columnList = "delivery_order_id"),
                @Index(name = "idx_delivery_events_created_at", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "delivery_order_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_delivery_events_delivery_order")
    )
    private DeliveryOrder deliveryOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private DeliveryEventType eventType;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public DeliveryEvent(DeliveryOrder deliveryOrder, DeliveryEventType eventType, String description) {
        this.deliveryOrder = Objects.requireNonNull(deliveryOrder, "deliveryOrder must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.description = Objects.requireNonNull(description, "description must not be null");
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
