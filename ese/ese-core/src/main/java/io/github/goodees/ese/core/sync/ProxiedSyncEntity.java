/*
 * Copyright 2017 Patrik Duditš.
 *
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
 */
package io.github.goodees.ese.core.sync;

import io.github.goodees.ese.core.Event;
import io.github.goodees.ese.core.EventSourcedEntity;
import io.github.goodees.ese.core.Request;
import io.github.goodees.ese.core.store.EventStore;
import io.github.goodees.ese.core.store.EventStoreException;
import java.util.Arrays;
import java.util.Collection;

/**
 *
 * @author patrik
 */
public abstract class ProxiedSyncEntity<R> extends EventSourcedEntity {

    private final EventStore store;

    protected ProxiedSyncEntity(String id, EventStore store) {
        super(id);
        this.store = store;
    }

    protected abstract R requestHandler();

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
