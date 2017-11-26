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

import java.time.Instant;

/**
 * Immutable fact about business domain relevant fact that became true.
 *
 * <p>Every Event sourced entity class defines its own set of events.</p>
 *
 * <p>The serialization and deserialization format is not prescribed, but events may define annotations to support specific
 * serialization kinds, e. g. Jackson annotations. The actual serialization and deserialization process is tasks of
 * actual EventStore and EventLog implementations used by the runtime</p>
 *
 * <p>The methods provided in this interface define metadata that will be stored outside the journaled payload to enable
 * querying and deserialization.</p>
 *
 * Support for events based on <a href="http://immutables.github.io">Immutables</a> is in package {@link io.github.goodees.ese.immutables}.
 */
public interface Event {
    /**
     * The type of event. For every entity class this must uniquely identify the event to be created. If in future an
     * event is removed, the store must be able to translate it to an equivalent event in new model.
     * @return textual description of the type of event, uses class name by default, stripped from suffix Event, or 
     *         prefix Immutable
     */
    default String getType() {
        return EventType.defaultTypeName(getClass());
    }

    /**
     * The id of the entity this event relates to. All entities of a single class must have unique id.
     * @return the entity id
     * @see EventSourcedEntity#getIdentity()
     */
    String entityId();

    /**
     * The time when an event occurred.
     * @return the instant of event creation
     */
    Instant getTimestamp();

    /**
     * The version of the entity expected after this event is applied.
     * This allows for performing optimistic locking on an entity.
     * @return the version on an entity
     * @see EventSourcedEntity#nextEventVersion()
     */
    long entityStateVersion();
    
    
    interface FromEntity<T> {
        T from(Event event);
        
        default T from(EventSourcedEntity entity) {
            return from(new EventHeader(entity));
        }
    }

}
