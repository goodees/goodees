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

import io.github.goodees.ese.core.EventSourcedEntity;
import io.github.goodees.ese.core.MockEntities;
import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class JdbcSnapshotStoreTest extends JdbcTest {

    @Test
    public void unsupported_snapshots_are_ignored() {
        // the payload is not supported, but it just fails silently
        store(mockEntity(name(), 1, 3));
    }

    @Test
    public void first_snapshot_inserted() {
        store(mockEntity(name(), 1, new JdbcTestSnapshot(10)));
        assertDb(1, "select count(*) from snapshot where id = ? and version = ?", name(), 1);
    }

    @Test
    public void next_snapshot_updated() {
        store(mockEntity(name(), 1, new JdbcTestSnapshot(10)));
        store(mockEntity(name(), 2, new JdbcTestSnapshot(20)));
        assertDb(0, "select count(*) from snapshot where id = ? and version = ?", name(), 1);
        assertDb(1, "select count(*) from snapshot where id = ? and version = ?", name(), 2);
    }

    @Test
    public void snapshot_deserialized() {
        store(mockEntity(name(), 8, new JdbcTestSnapshot(10)));
        JdbcTestEntity entity = new JdbcTestEntity(name());
        assertEquals(0, recover(entity));
        assertEquals(10, entity.getPayload());
        assertEquals(8L, entity.getStateVersion());
    }

    @Test
    public void multiple_versions_deserialized() {
        store(mockEntity(name(), 8, new JdbcTestSnapshot(10)));
        JdbcTestEntity entity = new JdbcTestEntity(name());
        assertEquals(0, recover(entity));
        assertEquals(10, entity.getPayload());
        assertEquals(8L, entity.getStateVersion());
        snapshotSerialization.setStoreHex(true);
        store(mockEntity(name(), 9, new JdbcTestSnapshot(12)));
        assertEquals(0, recover(entity));
        assertEquals(12, entity.getPayload());
        assertEquals(9L, entity.getStateVersion());
        snapshotSerialization.setStoreHex(false);
    }

    private void store(EventSourcedEntity entity) {
        snapshotStore.store(entity, MockEntities.handler());
    }

    private int recover(EventSourcedEntity entity) {
        return snapshotStore.recover(entity, eventLog, MockEntities.handler());
    }

    private EventSourcedEntity mockEntity(String entityId, int stateVersion, Object snapshot) {
        return MockEntities.entityWithSnapshot(eventStore, entityId, stateVersion, snapshot);
    }

}
