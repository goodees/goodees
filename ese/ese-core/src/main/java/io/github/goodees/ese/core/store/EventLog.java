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
import io.github.goodees.ese.core.EventSourcedEntity;
import io.github.goodees.ese.core.EventSourcingRuntimeBase;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Reads persisted logs.
 * This interface is usually implemented along with EventStore, and it defines entity-specific (de)serialization routines.
 */
public interface EventLog {
    /**
     * Read all events for an entity that happened after specified version.
     * @param entityId the id of an entity
     * @param afterVersion events that happened after this version. 0 stands for uninitialized, will therefore return entire history
     * @return accessor for the events in order they appeared in history
     */
    StoredEvents<? extends Event> readEvents(String entityId, long afterVersion);

    /**
     * Compare the version of an entity with the state known in the log. This is used by {@link EventSourcingRuntimeBase}
     * to prevent passing requests to an entity that is stale.
     * @param entity entity instance
     * @return true if no newer events are known for the entity
     * @see EventSourcedEntity#getIdentity()
     * @see EventSourcedEntity#getStateVersion()
     */
    boolean confirmsEntityReflectsCurrentState(EventSourcedEntity entity);

    /**
     * Accessor that enables single iteration over found events.
     * The underlying idea is, that the events needs not to be materialized at once, rather it could for example wrap a JDBC
     * ResultSet. This also means that only one of methods foreach and reduce may be called on single instance, and
     * only once.
     */
    interface StoredEvents<E extends Event> extends AutoCloseable {
        /**
         * Iterate over all found events. Consumer may call {@link #stop()} to stop the iteration.
         * @param consumer consumer that will receive the events
         */
        void foreach(Consumer<? super E> consumer);

        /**
         * Perform a reduction over all found events. Reducer may call {@link #stop()} to stop the process.
         * @param initial Initial value for reduction
         * @param reducer the reducer function
         * @param <R> type of result
         * @return result of reduction.
         */
        <R> R reduce(R initial, BiFunction<R, ? super E, R> reducer);

        /**
         * Can be called from within the lambda functions to stop the iteration after current step.
         */
        void stop();

        // will not throw exception
        @Override
        void close();
    }
}
