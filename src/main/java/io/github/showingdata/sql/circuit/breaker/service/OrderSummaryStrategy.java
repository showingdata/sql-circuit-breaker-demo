package io.github.showingdata.sql.circuit.breaker.service;

import io.github.showingdata.sql.circuit.breaker.entity.Order;

public interface OrderSummaryStrategy {

    String summarize(Order order);
}
