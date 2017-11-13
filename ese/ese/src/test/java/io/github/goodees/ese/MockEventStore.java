package io.github.goodees.ese;

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

import io.github.goodees.ese.store.inmemory.InMemoryEventStore;
import io.github.goodees.ese.store.EventStoreException;

import java.util.concurrent.atomic.AtomicReference;


public class MockEventStore extends InMemoryEventStore {
    private AtomicReference<EventStoreException> exception = new AtomicReference<>();

    @Override
    public void persist(Event event) throws EventStoreException {
        EventStoreException ex = exception.getAndSet(null);
        if (ex != null) {
            throw ex;
        }
        super.persist(event);
    }

    void throwExceptionOnce(EventStoreException ex) {
        exception.set(ex);
    }
}
