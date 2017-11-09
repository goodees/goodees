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

import io.github.goodees.ese.immutables.events.OrderShippedEvent;
import io.github.goodees.ese.immutables.events.OrderEvent;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.time.Instant;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import io.github.goodees.ese.immutables.events.AddressChangedEvent;

/**
 *
 */
public class EventTypeIdResolverTest {

    private ImmutableEventTypeResolver resolver;
    private TypeFactory tf;

    @Before
    public void setUp() {
        resolver = new ImmutableEventTypeResolver();
        tf = TypeFactory.defaultInstance();
        resolver.init(tf.constructType(OrderEvent.class));
    }

    @Test
    public void type_id_generated_for_supported_types() {
        AddressChangedEvent event1 = new AddressChangedEvent.Builder().entityId("test")
                .entityStateVersion(1)
                .timestamp(Instant.now())
                .street("Hlavná 34")
                .city("Košice")
                .country("Slovakia")
                .build();
        assertEquals("AddressChanged", resolver.idFromValueAndType(event1, AddressChangedEvent.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void type_id_generation_fails_on_unsupported_types() {
        resolver.idFromValue(13);
    }

    @Test
    public void class_is_instantiated_for_supported_types() {
        assertTrue(AddressChangedEvent.class.isAssignableFrom(resolver.typeFromId("AddressChanged", tf).getRawClass()));
        assertTrue(OrderShippedEvent.class.isAssignableFrom(resolver.typeFromId("OrderShipped", tf)
                .getRawClass()));
    }

}
