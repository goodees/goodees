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
import io.github.goodees.ese.core.store.EventStoreException;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Event sourced entity that executes the request in synchronous manner.
 * The programming model is seemingly easier, but does not conceptually separate event handling from side effects.
 * <p>This class requires its runtime to extends from {@link SyncEventSourcingRuntime}.</p>
 * <p>Emitting events can be done by using methods {@link #persistAndUpdate(Event)}, {@link #persistAllAndUpdate(Event...)},
 * {@link #persistAllAndUpdate(Collection)}. Should these methods throw an exception the processing should not continue.</p>
 * @see AsyncEntity
 * @see SyncEventSourcingRuntime
 */
public abstract class SyncEntity extends EventSourcedEntity {

    private final EventStore store;

    protected SyncEntity(String id, EventStore store) {
        super(id);
        this.store = store;
    }

    /**
     * Execute the request. This model gives no direct limitations on how a request should be handled, however, it needs
     * to be carefully designed in following blocks:
     * <ol>
     *    <li> Validation (not changing state of entity, or the system) </li>
     *    <li> Persisting events. When EventStoreException is thrown, it must be rethrown immediately.</li>
     *    <li> {@link #updateState(Event)} will be called for every produced event after persistAndUpdate succeeds</li>
     *    <li> Performing side effects.</li>
     *    <li> If side effects fail, and are necessary for transition to new state, emitting compensating events.</li>
     *    <li> {@link #performPostInvocationActions(List, Throwable)} is called by the runtime with events produced and optionally thrown exception</li>
     * </ol>
     * @param request request to execute
     * @param <R> type of request
     * @param <RS> type of response
     * @return the response
     * @throws Exception when response doesn't complete successfully
     */
    protected abstract <R extends Request<RS>, RS> RS execute(R request) throws Exception;

    protected void persistAndUpdate(Event event) throws EventStoreException {
        try {
            store.persist(event);
            handlePersistence(event, null);
        } catch (EventStoreException e) {
            handlePersistenceFailure(e);
            throw e;
        }

    }

    protected void persistAllAndUpdate(Event... events) throws EventStoreException {
        try {
            store.persist(events);
            handlePersistence(Arrays.asList(events), null);
        } catch (EventStoreException e) {
            handlePersistenceFailure(e);
            throw e;
        }
    }

    protected void persistAllAndUpdate(Collection<? extends Event> events) throws EventStoreException {
        try {
            store.persist(events);
            handlePersistence(events, null);
        } catch (EventStoreException e) {
            handlePersistenceFailure(e);
            throw e;
        }
    }

}
