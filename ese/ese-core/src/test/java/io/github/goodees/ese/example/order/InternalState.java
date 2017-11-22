package io.github.goodees.ese.example.order;

/*-
 * #%L
 * ese
 * %%
 * Copyright (C) 2017 Patrik Duditš
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import io.github.goodees.ese.core.Event;
import io.github.goodees.ese.example.order.request.OrderItem;
import io.github.goodees.ese.example.order.event.ItemPutEvent;
import io.github.goodees.ese.example.order.request.OrderTotal;
import io.github.goodees.ese.core.matching.TypeSwitch;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 */
class InternalState {
    Map<String, BigDecimal> items = new LinkedHashMap<>();
    
    OrderTotal getTotals() {
        return new OrderTotal(items.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }
    
    private Order.OrderStatus status = Order.OrderStatus.NEW;
    
    private TypeSwitch eventHandler = TypeSwitch.builder()
            .on(ItemPutEvent.class, this::itemPut)
            .build();

    Order.OrderStatus updateState(Event ev) {
       eventHandler.executeMatching(ev);
       return status;
    }
    
    void itemPut(ItemPutEvent ev) {
        status = Order.OrderStatus.OPEN;
        items.put(ev.productId(), ev.amount());
    }
    
    
}
