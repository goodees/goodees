package io.github.goodees.ese.store.jdbc;

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

import io.github.goodees.ese.store.EventLog;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;


public class JdbcEventLogTest extends JdbcTest {

    void insert(JdbcTestEvent event) {
        template.update(
            "insert into event (id, version, timestamp, type, payload_version, payload) values (?,?,?,?,?,?)",
            event.entityId(), event.entityStateVersion(), new Date(), event.getType(),
            serialization.payloadVersion(event), serialization.serialize(event));
    }

    List<JdbcTestEvent> generate(int size) {
        return generate(size, 1);
    }

    List<JdbcTestEvent> generate(int size, int offset) {
        List<JdbcTestEvent> result = new ArrayList<>();
        for (int i = offset; i < size + offset; i++) {
            JdbcTestEvent e = new JdbcTestEvent(name(), i, i * 10);
            result.add(e);
            insert(e);
        }
        return result;
    }

    @Test
    public void foreach_delivers_results() {
        List<JdbcTestEvent> events = generate(3);
        List<JdbcTestEvent> delivered = new ArrayList<>();
        try (EventLog.StoredEvents<JdbcTestEvent> ev = eventLog.readEvents(name(), 0)) {
            ev.foreach(delivered::add);
        }
        assertEquals(events, delivered);
    }

    @Test
    public void stop_stops_foreach() {
        List<JdbcTestEvent> events = generate(3);
        AtomicInteger counter = new AtomicInteger();
        List<JdbcTestEvent> delivered = new ArrayList<>();
        try (EventLog.StoredEvents<JdbcTestEvent> ev = eventLog.readEvents(name(), 0)) {
            ev.foreach((e) -> {
                if (counter.incrementAndGet() == 2) {
                    ev.stop();
                }
                delivered.add(e);
            });
        }
        assertEquals(events.subList(0,2), delivered);
    }

    @Test
    public void reduce_delivers_results() {
        int size = 30;
        List<JdbcTestEvent> events = generate(size);
        try (EventLog.StoredEvents<JdbcTestEvent> ev = eventLog.readEvents(name(), 0)) {
            int result = ev.reduce(0, (a, e) -> a + e.getPayload());
            // expected sum of 10+20+30+....(size)*10 : sigma = size*(a0+aN)/2, a0=1, aN=size, multiplied by 10
            assertEquals(size*(1+size)*10/2, result);
        }
    }

    @Test
    public void stop_stops_reduce() {
        int size = 30;
        int stopAt = 20;
        List<JdbcTestEvent> events = generate(size);
        try (EventLog.StoredEvents<JdbcTestEvent> ev = eventLog.readEvents(name(), 0)) {
            int result = ev.reduce(0, (a, e) -> {if (e.entityStateVersion() == stopAt) ev.stop(); return a + e.getPayload();});
            // expected sum of 10+20+30+....(size)*10 : sigma = size*(a0+aN)/2, a0=1, aN=size, multiplied by 10
            assertEquals(stopAt*(1+stopAt)*10/2, result);
        }
    }

    @Test
    public void read_events_delivers_entities_after_specified_version() {
        List<JdbcTestEvent> events = generate(30);
        List<JdbcTestEvent> delivered = new ArrayList<>();
        try (EventLog.StoredEvents<JdbcTestEvent> ev = eventLog.readEvents(name(), 10)) {
            ev.foreach(delivered::add);
        }
        assertEquals(events.subList(10, events.size()), delivered);
        assertTrue("Returned entity version should be strictly greater than afterVersion argument",
                delivered.stream().allMatch(e -> e.entityStateVersion() > 10));
    }

    @Test
    public void no_events_returned_for_nonexisting_entity() {
        List<JdbcTestEvent> delivered = new ArrayList<>();
        try (EventLog.StoredEvents<JdbcTestEvent> ev = eventLog.readEvents(name(), 0)) {
            ev.foreach(delivered::add);
        }
        assertThat(delivered, empty());
    }

    @Test
    public void different_payload_versions_properly_deserialized() {
        List<JdbcTestEvent> oldPayloads = generate(10);
        serialization.setStoreHex(true);
        List<JdbcTestEvent> newPayloads = generate(10, 11);
        List<JdbcTestEvent> delivered = new ArrayList<>();
        serialization.setStoreHex(false);
        // let's test that e.g event 12 with payload 120 was encoded in hex
        assertDb(1, "select count(*) from event where id = ? and payload like '%|78'", name());
        try (EventLog.StoredEvents<JdbcTestEvent> ev = eventLog.readEvents(name(), 0)) {
            ev.foreach(delivered::add);
        }
        assertEquals(20, delivered.size());
        int i = 1;
        for (JdbcTestEvent event : delivered) {
            assertEquals(i, event.entityStateVersion());
            assertEquals(i*10, event.getPayload());
            i++;
        }
    }
}
