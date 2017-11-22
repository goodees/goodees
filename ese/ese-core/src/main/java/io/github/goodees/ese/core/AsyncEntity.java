package io.github.goodees.ese.core;

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

import io.github.goodees.ese.core.store.EventStore;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * An entity whose request execution is asynchronous, a result of composing CompletableFutures.
 * The execution of request is implemented in method {@link #execute(Request)}.
 * <p>The runtime for this entity needs to extends from {@link AsyncEventSourcingRuntime}.</p>
 * <p>To emit an event the execute code will call one of persistAndUpdate methods - {@link #persistAndUpdate(Event)},
 * {@link #persistAllAndUpdate(Event[])}, {@link #persistAllAndUpdate(Collection)}. These methods will facilitate persisting the events and
 * updating the state. If the persistAndUpdate succeeds, side effects can be chained to returned {@link CompletionStage}.
 *
 * @see AsyncEventSourcingRuntime
 */
public abstract class AsyncEntity extends EventSourcedEntity {

    /**
     * Create the entity. The runtime will provide all necessary dependencies for the class.
     *
     * @param store the event store used to store events
     * @param id the identity of the entity
     * @see EventSourcingRuntimeBase#lookup(String) for instantiation lifecycle
     */
    protected AsyncEntity(String id, EventStore store) {
        super(id, store);
    }

    /**
     * Process incoming request, to which the caller expects a response. The implementation should
     * <ul>
     * <li>validate the preconditions for the command in the current state, e. g. sending <em>queries</em> to external systems,
     * but not changing the state of external systems</li>
     * <li>prepare events to be generated. The event's {@code entityStateVersion} needs to be equal to result of call to
     * {@link #getStateVersion()}</li>
     * <li>call persistAndUpdate methods to store the events. {@link #updateState(Event)} will be called when persist succeeds</li>
     * <li>chain other stages past the persistAndUpdate call to invoke side effects, like communication that changes state
     * of other parts of the system</li>
     * <li>complete the CompletionStage. {@link #performPostInvocationActions(List, Throwable)} will be called by the runtime to allow to perform any
     *     request-independent side effects</li>
     * </ul>
     *
     * @param request request to execute
     * @param <R> type of request
     * @param <RS> type of response
     * @return Completion stage that will either contain the result, or the exception the call resulted in.
     * @see #persistAndUpdate(Event)
     * @see #persistAllAndUpdate(Event[])
     * @see #persistAllAndUpdate(Collection)
     */
    protected abstract <R extends Request<RS>, RS> CompletionStage<RS> execute(R request);

    /**
     * Persist an event. After persist is successful the current state will be updated.
     * @param event event to persist
     * @param <E> type of event
     * @return AsyncResult that completes successfully with the event when persist succeeds.
     *    Completes with EventStoreException if it doesn't succeed.
     */
    protected <E extends Event> AsyncResult<E> persistAndUpdate(E event) {
        return AsyncResult.invoke(() -> {
            store.persist(event);
            return event;
        }).whenComplete((e,t) -> handleEventPersistence(Collections.singleton(e), t));
    }

    /**
     * Persist multiple events event. After persist is successful the current state will be updated.
     * @param events events to persist
     * @param <E> type of events
     * @return AsyncResult that completes successfully with the event when persist suceeds.
     *    Completes with EventStoreException if it doesn't succeed.
     */
    @SafeVarargs
    protected final <E extends Event> AsyncResult<List<E>> persistAllAndUpdate(E... events) {
        return AsyncResult.invoke(() -> {
            store.persist(events);
            return Arrays.asList(events);
        }).whenComplete(this::handleEventPersistence);
    }

    private void handleEventPersistence(Collection<? extends Event> events, Throwable t) {
        if (t == null) {
            applyEvents(events.stream());
            getInvocationState().eventsPersisted(events);
        } else {
            getInvocationState().eventStoreFailed(t);
        }
    }

    /**
     * Persist multiple events event. After persist is successful the current state will be updated.
     * @param events events to persist
     * @param <E> type of events
     * @return AsyncResult that completes successfully with the event when persist succeeds.
     *    Completes with EventStoreException if it doesn't succeed.
     */
    protected <E extends Event> AsyncResult<Collection<E>> persistAllAndUpdate(Collection<E> events) {
        return AsyncResult.invoke(() -> {
            store.persist(events);
            return events;
        }).whenComplete(this::handleEventPersistence);
    }

    /**
     * Helper method for returning a failed response.
     * @param e exception to complete with
     * @param <U> any type that fits the calling method
     * @return an AsyncResult that completed with given exception
     */
    protected static <U> AsyncResult<U> throwing(Exception e) {
        return AsyncResult.throwing(e);
    }

    /**
     * Helper method for returning a successful response
     * @param value response to return
     * @param <U> type of response
     * @return an AsyncResult that completed with given value
     */
    protected static <U> AsyncResult<U> returning(U value) {
        return AsyncResult.returning(value);
    }

}
