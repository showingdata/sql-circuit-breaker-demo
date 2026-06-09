package io.github.showingdata.sql.circuit.breaker.service;


import io.github.showingdata.sql.circuit.breaker.entity.Order;


public class DefaultOrderSummaryStrategy implements OrderSummaryStrategy {

    @Override
    public String summarize(Order order) {
        if (order == null) {
            return "empty-order";
        }
        return "order#" + order.getId();
    }
}
