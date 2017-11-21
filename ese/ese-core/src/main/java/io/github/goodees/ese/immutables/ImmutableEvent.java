package io.github.goodees.ese.immutables;

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

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.github.goodees.ese.core.Event;
import io.github.goodees.ese.core.EventHeader;
import io.github.goodees.ese.core.EventSourcedEntity;
import io.github.goodees.ese.core.EventType;
import java.util.function.Function;
import org.immutables.value.Value;

/**
 * Base class for entity events using <a href="http://immutables.github.io">Immutables library</a>.
 * When an entity wants to use this approach for event serialization, it shall define its base class of events, that
 * extends ImmutableEvent.
 * <p><strong>All events for an entity need to be defined in same package!</strong>
 * <p>Event should implement this class. The package they reside in must have annotation {@link ImmutablesSupport}
 * in their {@code package-info.java}
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CUSTOM, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonTypeIdResolver(ImmutableEventTypeResolver.class)
// allow for future changes in an event
@JsonIgnoreProperties(ignoreUnknown = true)
// Put key values at the front
@JsonPropertyOrder({ "entityId", "entityStateVersion", "timestamp" })
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public interface ImmutableEvent extends Event {

    @Override
    @Value.Auxiliary
    @JsonIgnore
    // type property is written by resolver, and is requires default naming scheme
    default String getType() {
        return EventType.fromClassStripping(getClass(), "Immutable", "Event");
    }

    static <T> T builderForEntity(EventSourcedEntity entity, Function<Event, T> buildFromEvent) {
        return buildFromEvent.apply(new EventHeader(entity));
    }

}
