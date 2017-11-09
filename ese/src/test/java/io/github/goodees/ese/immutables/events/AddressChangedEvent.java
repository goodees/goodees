package io.github.goodees.ese.immutables.events;

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


import io.github.goodees.ese.EventSourcedEntity;
import static io.github.goodees.ese.immutables.ImmutableEvent.builderForEntity;
import java.util.Optional;
import org.immutables.value.Value.Immutable;

/**
 *
 */
@Immutable
public interface AddressChangedEvent extends OrderEvent {
    String getStreet();
    String getCity();
    Optional<String> getState();   
    String getCountry();

    public static Builder builder(EventSourcedEntity source) {
        return builderForEntity(source, new Builder()::from);
    }

    public static class Builder extends ImmutableAddressChangedEvent.Builder {

    }
}
