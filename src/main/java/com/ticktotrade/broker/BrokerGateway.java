package com.ticktotrade.broker;

import com.ticktotrade.model.Order;
import com.ticktotrade.model.OrderResult;

/**
 * Sends an order to a broker/exchange and returns the acknowledgement.
 * A real implementation would adapt a FIX session, a broker REST/websocket API,
 * or an exchange gateway. Tests replace this with an in-memory fake or a mock.
 */
public interface BrokerGateway {
    OrderResult send(Order order);
}
