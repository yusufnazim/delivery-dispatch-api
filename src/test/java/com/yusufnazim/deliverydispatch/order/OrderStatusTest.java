package com.yusufnazim.deliverydispatch.order;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void pendingCanOnlyMoveToAssignedOrCancelled() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.ASSIGNED)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PICKED_UP)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void assignedCanOnlyMoveToPickedUp() {
        assertThat(OrderStatus.ASSIGNED.canTransitionTo(OrderStatus.PICKED_UP)).isTrue();
        assertThat(OrderStatus.ASSIGNED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
        assertThat(OrderStatus.ASSIGNED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void pickedUpCanOnlyMoveToDelivered() {
        assertThat(OrderStatus.PICKED_UP.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStatus.PICKED_UP.canTransitionTo(OrderStatus.ASSIGNED)).isFalse();
        assertThat(OrderStatus.PICKED_UP.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void deliveredAndCancelledAreTerminalStatuses() {
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.PICKED_UP)).isFalse();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.ASSIGNED)).isFalse();
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void nullTransitionTargetIsRejected() {
        assertThat(OrderStatus.PENDING.canTransitionTo(null)).isFalse();
    }
}
