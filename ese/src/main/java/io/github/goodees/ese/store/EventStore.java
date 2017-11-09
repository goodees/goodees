package io.github.goodees.ese.store;

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

import io.github.goodees.ese.Event;

/**
 * Storage for entity's events.
 *
 * <p>This storage will be created per entity to tailor its storage and serialization scenarios</p>
 * <p>Storing an event constitutes a separate non-distributed transaction. In case side effects would fail, the entity
 * implementation must generate a compensating event to restore its state.</p>
 * <p>Event store needs also guarantee the consistency of event log across processes, e. g. by employing optimistic locks
 * to prevent two processes to store new events for single entity </p>
 */
public interface EventStore {

    /**
     * Persist events synchronously. Callers may invoke side effects when this method completes without exception.
     * Any exception thrown from this method must be treated as non-recoverable and passed to runtime, which then
     * can retry the command.
     * @param event event to store
     * @throws EventStoreException when storing fails, or if the entity was out of date
     */
    void persist(Event event) throws EventStoreException;

    /**
     * Persist events synchronously. Callers may invoke side effects when this method completes without exception.
     * Any exception thrown from this method must be treated as non-recoverable and passed to runtime, which then
     * can retry the command.
     * @param events event to store
     * @throws EventStoreException when storing fails, or if the entity was out of date
     */
    void persist(Event... events) throws EventStoreException;

    /**
     * Persist events synchronously. Callers may invoke side effects when this method completes without exception.
     * Any exception thrown from this method must be treated as non-recoverable and passed to runtime, which then
     * can retry the command.
     * @param events event to store
     * @throws EventStoreException when storing fails, or if the entity was out of date
     */
    void persist(Iterable<? extends Event> events) throws EventStoreException;
}
