package com.ticktotrade.model;

public record OrderResult(
        String orderId,
        OrderStatus status,
        String message
) {
    public enum OrderStatus {
        ACCEPTED,
        REJECTED
    }
}
