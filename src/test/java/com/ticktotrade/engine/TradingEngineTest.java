package com.ticktotrade.engine;

import com.ticktotrade.alpha.AlphaStrategy;
import com.ticktotrade.broker.BrokerGateway;
import com.ticktotrade.model.Order;
import com.ticktotrade.model.OrderResult;
import com.ticktotrade.model.Side;
import com.ticktotrade.model.Signal;
import com.ticktotrade.model.Tick;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Neither the market data feed nor a real broker connection is needed here:
 * the alpha strategy and broker gateway are mocked so this test exercises only
 * the engine's wiring and latency measurement, in isolation and deterministically.
 */
@ExtendWith(MockitoExtension.class)
class TradingEngineTest {

    @Mock
    private AlphaStrategy alphaStrategy;

    @Mock
    private BrokerGateway brokerGateway;

    @Test
    void sendsOrderToBrokerWhenSignalIsActionable() {
        Tick tick = tick("AAPL", 100.0);
        when(alphaStrategy.evaluate(tick)).thenReturn(Signal.of("AAPL", Side.BUY, 10));
        when(brokerGateway.send(any())).thenReturn(new OrderResult("id-1", OrderResult.OrderStatus.ACCEPTED, "ok"));

        TradingEngine engine = new TradingEngine(alphaStrategy, brokerGateway, new LatencyRecorder(16));
        engine.onTick(tick);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(brokerGateway).send(orderCaptor.capture());
        Order sentOrder = orderCaptor.getValue();
        assertEquals("AAPL", sentOrder.symbol());
        assertEquals(Side.BUY, sentOrder.side());
        assertEquals(10, sentOrder.quantity());
    }

    @Test
    void doesNotSendOrderWhenSignalIsNotActionable() {
        Tick tick = tick("AAPL", 100.0);
        when(alphaStrategy.evaluate(tick)).thenReturn(Signal.none());

        TradingEngine engine = new TradingEngine(alphaStrategy, brokerGateway, new LatencyRecorder(16));
        engine.onTick(tick);

        verify(brokerGateway, never()).send(any());
    }

    @Test
    void recordsTickToOrderLatencyUnder100Millis() {
        Tick tick = tick("AAPL", 100.0);
        when(alphaStrategy.evaluate(tick)).thenReturn(Signal.of("AAPL", Side.BUY, 10));
        when(brokerGateway.send(any())).thenReturn(new OrderResult("id-1", OrderResult.OrderStatus.ACCEPTED, "ok"));

        LatencyRecorder latencyRecorder = new LatencyRecorder(16);
        TradingEngine engine = new TradingEngine(alphaStrategy, brokerGateway, latencyRecorder);
        engine.onTick(tick);

        assertEquals(1, latencyRecorder.count());
        assertTrue(latencyRecorder.maxNanos() < TimeUnit.MILLISECONDS.toNanos(100));
    }

    private static Tick tick(String symbol, double price) {
        long now = System.nanoTime();
        return new Tick(symbol, price, 100, now, now);
    }
}
