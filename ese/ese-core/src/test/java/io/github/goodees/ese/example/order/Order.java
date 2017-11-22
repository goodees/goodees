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
import io.github.goodees.ese.core.Request;
import io.github.goodees.ese.core.sync.SyncEntity;
import io.github.goodees.ese.example.order.event.*;
import io.github.goodees.ese.example.order.request.*;
import io.github.goodees.ese.core.matching.SyncRequestHandler;
import io.github.goodees.ese.core.store.EventStore;
import io.github.goodees.ese.core.store.EventStoreException;
import java.math.BigDecimal;
import static java.util.stream.Collectors.toList;

/**
 * The usual order entity
 */
public class Order extends SyncEntity {

    enum OrderStatus {
        NEW, OPEN, CLOSED, PAID
    }

    public Order(String id, EventStore store) {
        super(id, store);
    }


    private SyncRequestHandler requestHandler = updateRequestHandler(OrderStatus.NEW);
    private InternalState state = new InternalState();

    /**
     * Handle incoming requests. The Order might be in multiple states, and for each the available requests differ.
     * Therefore we delegate to a request handler.
     *
     * @throws Exception
     */
    @Override
    protected <R extends Request<RS>, RS> RS execute(R request) throws Exception {
        return requestHandler.handle(request);
    }

    private SyncRequestHandler handleStateNew() {
        return SyncRequestHandler.withDefaultFallback().on(CreateOrder.class, this::createOrder)
                .build();
    }
    
    private SyncRequestHandler handleStateOpen() {
        return SyncRequestHandler.withDefaultFallback().on(AddItem.class, this::addItem)
                .on(AdjustItemAmount.class, this::adjustItem)
                .on(RemoveItem.class, this::removeItem)
                .on(StartCheckout.class, this::startCheckout)
                .build();
    }
    
    private SyncRequestHandler handleStateClosed() {
        return SyncRequestHandler
                .withFallbackException((r) -> new IllegalStateException("Order is already closed and cannot be changed"))
                .on(ConfirmPayment.class, this::shipOrder)
                .build();
    }    
    
    private SyncRequestHandler handleStatePaid() {
        return SyncRequestHandler
                .withFallbackException((r) -> new IllegalStateException("Order is already shipped cannot be changed"))
                .build();
    }       
    
    private SyncRequestHandler updateRequestHandler(OrderStatus status) {
        switch (status) {
            case NEW:
                return handleStateNew();
            case OPEN:
                return handleStateOpen();
            case CLOSED:
                return handleStateClosed();
            case PAID:
                return handleStatePaid();
            default:
                throw new IllegalStateException("Unhandled order status "+status);
        }
    }
    

    @Override
    protected void updateState(Event event) {
        requestHandler = updateRequestHandler(state.updateState(event));
    }

    private OrderTotal createOrder(CreateOrder request) throws EventStoreException {
        persistAllAndUpdate(request.getItems().stream().map(
                it -> ItemPutEvent.builder(this)
                        .amount(it.getAmount())
                        .productId(it.getProductId()).build())
                .collect(toList()));
        return state.getTotals();
    }
    
    private OrderTotal addItem(AddItem request) throws EventStoreException {
        if (state.items.containsKey(request.getItem().getProductId())) {
            throw new IllegalArgumentException("Item already present");
        }
        OrderItem item = request.getItem();
        persistAndUpdate(ItemPutEvent.builder(this)
                .productId(item.getProductId())
                .amount(item.getAmount()).build());
        return state.getTotals();
    }
    
    private OrderTotal adjustItem(AdjustItemAmount request) throws EventStoreException {
        OrderItem item = request.getItem();
        if (!state.items.containsKey(item.getProductId())) {
            throw new IllegalArgumentException("Item not present");
        } 
        BigDecimal amount = state.items.get(item.getProductId());
        if (amount.add(item.getAmount()).compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Resulting amount cannot be negative");
        }
        persistAndUpdate(ItemAmountAdjustedEvent.builder(this)
                .itemId(item.getProductId()).adjustment(item.getAmount()).build());
        return state.getTotals();
    }
    
    private OrderTotal removeItem(RemoveItem request) throws EventStoreException {
        if (!state.items.containsKey(request.getProductId())) {
            throw new IllegalArgumentException("Item not present");
        }
        persistAndUpdate(ItemRemovedEvent.builder(this).productId(request.getProductId()).build());
        return state.getTotals();
    }
    
    private Void startCheckout(StartCheckout request) {
        return null;
    }
    
    private String shipOrder(ConfirmPayment request) {
        return "shippingNumber";
    }

}
