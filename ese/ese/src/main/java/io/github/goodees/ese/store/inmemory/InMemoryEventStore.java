package io.github.goodees.ese.store.inmemory;

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
import io.github.goodees.ese.store.EventLog;
import io.github.goodees.ese.EventSourcedEntity;
import io.github.goodees.ese.store.EventStore;
import io.github.goodees.ese.store.EventStoreException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;

/**
 * Created by UI187816 on 18/04/2017.
 */
public class InMemoryEventStore implements EventStore, EventLog {
    private ConcurrentMap<String, List<Event>> storage = new ConcurrentHashMap<>();

    private long lastVersionOf(String entityId) {
        List<Event> entityLog = entityLog(entityId);
        return entityLog.isEmpty() ? 0 : entityLog.get(entityLog.size() - 1).entityStateVersion();
    }

    @Override
    public void persist(Event event) throws EventStoreException {
        long lastVersion = lastVersionOf(event.entityId());
        if (event.entityStateVersion() <= lastVersion) {
            throw EventStoreException.optimisticLock(event.entityId(), lastVersion, event.entityStateVersion());
        }
        entityLog(event.entityId()).add(event);
    }

    @Override
    public boolean confirmsEntityReflectsCurrentState(EventSourcedEntity entity) {
        return lastVersionOf(entity.getIdentity()) <= entity.getStateVersion();
    }

    private List<Event> entityLog(String entityId) {
        return storage.computeIfAbsent(entityId, (i) -> Collections.synchronizedList(new ArrayList<>()));
    }

    @Override
    public void persist(Event... events) throws EventStoreException {
        for (Event event : events) {
            persist(event);
        }
    }

    @Override
    public void persist(Iterable<? extends Event> events) throws EventStoreException {
        for (Event event : events) {
            persist(event);
        }
    }

    @Override
    public StoredEvents<Event> readEvents(String entityId, long afterVersion) {
        return new StoredEvents<Event>() {
            final List<Event> filteredEvents;
            boolean stop = false;

            {
                List<Event> events = entityLog(entityId);
                //ad SynchronizedList - It is imperative that the user manually synchronize on the returned list when iterating over it.
                synchronized(events) {
                    filteredEvents = events.stream().filter(e -> e.entityStateVersion() > afterVersion).collect(toList());
                }
            }
            @Override
            public void foreach(Consumer<? super Event> consumer) {
                for (Event event : filteredEvents) {
                    if (stop) {
                        break;
                    }
                    consumer.accept(event);
                }
            }

            @Override
            public <R> R reduce(R initial, BiFunction<R, ? super Event, R> reducer) {
                R result = initial;
                for (Event event : filteredEvents) {
                    if (stop) {
                        break;
                    }
                    result = reducer.apply(result, event);
                }
                return result;
            }

            @Override
            public void stop() {
                stop = true;
            }

            @Override
            public void close() {
            }
        };
    }
}
