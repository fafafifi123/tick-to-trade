package com.ticktotrade.broker;

import com.ticktotrade.model.Order;
import com.ticktotrade.model.OrderResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory broker for tests and local demos: accepts every order immediately
 * and records it, with no network call. Swap for a real FIX/REST adapter in
 * production without touching the engine or strategy code.
 */
public final class SimulatedBrokerGateway implements BrokerGateway {

    private final AtomicLong orderIdSequence = new AtomicLong();
    private final List<Order> acceptedOrders = new ArrayList<>();

    @Override
    public synchronized OrderResult send(Order order) {
        acceptedOrders.add(order);
        String orderId = "SIM-" + orderIdSequence.incrementAndGet();
        return new OrderResult(orderId, OrderResult.OrderStatus.ACCEPTED, "ok");
    }

    public synchronized List<Order> acceptedOrders() {
        return List.copyOf(acceptedOrders);
    }
}
