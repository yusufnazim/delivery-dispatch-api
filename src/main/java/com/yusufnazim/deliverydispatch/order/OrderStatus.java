package com.yusufnazim.deliverydispatch.order;

public enum OrderStatus {
    PENDING,
    ASSIGNED,
    PICKED_UP,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus nextStatus) {
        if (nextStatus == null) {
            return false;
        }

        return switch (this) {
            case PENDING -> nextStatus == ASSIGNED || nextStatus == CANCELLED;
            case ASSIGNED -> nextStatus == PICKED_UP;
            case PICKED_UP -> nextStatus == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
