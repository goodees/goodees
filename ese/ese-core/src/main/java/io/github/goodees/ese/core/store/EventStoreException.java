package io.github.goodees.ese.core.store;

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


/**
 * Exception generated when storing of event fails.
 */
public class EventStoreException extends Exception {
    private final Fault fault;

    public enum Fault {
        OPTIMISTIC_LOCK, TX_ERROR, PROGRAMMATIC_ERROR
    }

    protected EventStoreException(Fault type, String message, Throwable cause) {
        super(message, cause);
        this.fault = type;
    }

    public Fault getFault() {
        return fault;
    }

    public static EventStoreException optimisticLock(String entityId, long eventVersion) {
        return new EventStoreException(Fault.OPTIMISTIC_LOCK, "Known entity " + entityId + " version is higher than "
                + eventVersion, null);
    }

    public static EventStoreException optimisticLock(String entityId, long expectedVersion, long eventVersion) {
        return new EventStoreException(Fault.OPTIMISTIC_LOCK, "Entity " + entityId + " storing event version "
                + eventVersion + " attempted while last known version is " + expectedVersion, null);
    }

    public static EventStoreException storeFailed(String entityId, Throwable cause) {
        return new EventStoreException(Fault.TX_ERROR,
            "Store of entity " + entityId + " failed. " + cause.getMessage(), cause);
    }

    public static EventStoreException multlipleEntities(String expected, Event violating) {
        return new EventStoreException(Fault.PROGRAMMATIC_ERROR, "Stored events span multiple entities: " + expected
                + " and " + violating.entityId(), null);
    }

    public static EventStoreException nonMonotonic(String entityId, long expectedVersion, Event violating) {
        return new EventStoreException(Fault.PROGRAMMATIC_ERROR, "Event for entity " + entityId
                + " does not follow sequence. Expected: " + expectedVersion + " actual: "
                + violating.entityStateVersion(), null);
    }

    public static EventStoreException suppressed(String entityId) {
        return new EventStoreException(Fault.PROGRAMMATIC_ERROR, "Entity " + entityId
                + " suppressed event store exception.", null);
    }

    public static EventStoreException unsupported(Event event) {
        return new EventStoreException(Fault.PROGRAMMATIC_ERROR, "Unsupported event type: " + event, null);
    }
}
