package com.ticktotrade.broker;

import com.ticktotrade.model.Order;
import com.ticktotrade.model.OrderResult;
import com.ticktotrade.model.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedBrokerGatewayTest {

    @Test
    void acceptsOrderAndReturnsAcceptedResultWithGeneratedOrderId() {
        SimulatedBrokerGateway broker = new SimulatedBrokerGateway();
        Order order = new Order("AAPL", Side.BUY, 10, 100.0, 1);

        OrderResult result = broker.send(order);

        assertEquals(OrderResult.OrderStatus.ACCEPTED, result.status());
        assertTrue(result.orderId().startsWith("SIM-"));
    }

    @Test
    void generatesDistinctOrderIdsForSuccessiveOrders() {
        SimulatedBrokerGateway broker = new SimulatedBrokerGateway();
        Order order = new Order("AAPL", Side.BUY, 10, 100.0, 1);

        OrderResult first = broker.send(order);
        OrderResult second = broker.send(order);

        assertNotEquals(first.orderId(), second.orderId());
    }

    @Test
    void recordsAcceptedOrdersInSendOrder() {
        SimulatedBrokerGateway broker = new SimulatedBrokerGateway();
        Order buy = new Order("AAPL", Side.BUY, 10, 100.0, 1);
        Order sell = new Order("AAPL", Side.SELL, 5, 101.0, 2);

        broker.send(buy);
        broker.send(sell);

        List<Order> accepted = broker.acceptedOrders();
        assertEquals(List.of(buy, sell), accepted);
    }
}
