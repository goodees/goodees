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

import io.github.goodees.ese.immutables.events.JsonTestEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.goodees.ese.immutables.events.AddressChangedEvent;
import io.github.goodees.ese.immutables.events.OrderEvent;
import io.github.goodees.ese.immutables.events.OrderShippedEvent;
import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 */
public class ImmutableEventTest {

    @Test
    public void constructEvent() {
        JsonTestEntity testEntity = new JsonTestEntity(null, "constructEvent");
        AddressChangedEvent event1 = AddressChangedEvent.builder(testEntity).city("city").country("country").street("street").build();
        OrderShippedEvent event2 = OrderShippedEvent.builder(testEntity)
                .logisticsPartner("Daddy's Van")
                .trackingNumber("2017/23")
                .build();
    }

    @Test
    public void events_serialize_to_json() throws JsonProcessingException {
        JsonTestEntity testEntity = new JsonTestEntity(null, "constructEvent");
        AddressChangedEvent event1 = AddressChangedEvent.builder(testEntity).city("city").country("contry").street("street").build();
        ObjectMapper mapper = createMapper();
        String json = mapper.writeValueAsString(event1);
        System.out.println(json);
    }

    ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(new Jdk8Module(), new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    @Test
    public void events_deserialize_from_json() throws IOException {
        String json = "{\n"
                + "  \"type\" : \"AddressChanged\",\n"
                + "  \"entityId\" : \"constructEvent\",\n"
                + "  \"entityStateVersion\" : 1,\n"
                + "  \"timestamp\" : \"2017-11-13T20:18:40.439Z\",\n"
                + "  \"street\" : \"street\",\n"
                + "  \"city\" : \"city\",\n"
                + "  \"country\" : \"contry\"\n"
                + "}";
        ObjectMapper mapper = createMapper();
        OrderEvent event = mapper.readValue(json, OrderEvent.class);
        assertTrue(event instanceof AddressChangedEvent);
        assertFalse(((AddressChangedEvent) event).getState().isPresent());
    }
}
