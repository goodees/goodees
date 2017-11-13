package io.github.goodees.ese.store.inmemory;

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

import io.github.goodees.ese.store.SnapshotMetadata;
import io.github.goodees.ese.store.SnapshotStore;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stores snapshots in memory. It doesn't make much sense to use it outside tests, but when entity doesn't support snapshots
 * it's good one to use.
 */
public class InMemorySnapshotStore extends SnapshotStore<Object> {
    private ConcurrentMap<String, SnapshotRecord> snapshotRecords = new ConcurrentHashMap<>();

    @Override
    protected Object deserializeSnapshot(SnapshotRecord snapshotRecord) {
        return snapshotRecord.getPayload();
    }

    @Override
    protected SnapshotRecord serializeSnapshot(String entityId, long stateVersion, Object snapshot) {
        return new SnapshotRecord(new SnapshotMetadata.Default(entityId, Instant.now(), 1, stateVersion), snapshot);
    }

    @Override
    public SnapshotRecord retrieveSnapshotRecord(String entityId) {
        return snapshotRecords.get(entityId);
    }

    public long getSnapshottedVersion(String entityId) {
        SnapshotRecord record = retrieveSnapshotRecord(entityId);
        return record == null ? 0 : record.getHeader().entityStateVersion();
    }

    @Override
    protected void storeSnapshotRecord(SnapshotRecord snapshotRecord) {
        snapshotRecords.put(snapshotRecord.getHeader().entityId(), snapshotRecord);
    }
}
