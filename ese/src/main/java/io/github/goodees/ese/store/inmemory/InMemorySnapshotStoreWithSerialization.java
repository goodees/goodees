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

import io.github.goodees.ese.store.Serialization;
import io.github.goodees.ese.store.SnapshotMetadata;
import io.github.goodees.ese.store.SnapshotStore;
import io.github.goodees.ese.store.SnapshotStoreWithSerialization;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * InMemoryStore, that also exercises serialization.
 */
public class InMemorySnapshotStoreWithSerialization<T> extends SnapshotStoreWithSerialization<T> {
    private ConcurrentMap<String, SnapshotRecord> snapshotRecords = new ConcurrentHashMap<>();

    public InMemorySnapshotStoreWithSerialization(Serialization<T> serialization) {
        super(serialization);
    }

    @Override
    public SnapshotRecord retrieveSnapshotRecord(String entityId) {
        return snapshotRecords.get(entityId);
    }

    @Override
    public void storeSnapshotRecord(SnapshotRecord snapshotRecord) {
        snapshotRecords.put(snapshotRecord.getHeader().entityId(), snapshotRecord);
    }

    public Optional<String> getSerializedSnapshot(String entityId) {
        return Optional.ofNullable(retrieveSnapshotRecord(entityId)).map(SnapshotRecord::getPayload);
    }

    public OptionalInt getSerializedSnapshotVersion(String entityId) {
        SnapshotRecord record = retrieveSnapshotRecord(entityId);
        if (record != null) {
            return OptionalInt.of(record.getHeader().payloadVersion());
        } else {
            return OptionalInt.empty();
        }
    }

    public void storeSnapshot(String entityId, long stateVersion, int payloadVersion, String payload) {
        storeSnapshotRecord(new SnapshotRecord(new SnapshotMetadata.Default(entityId, Instant.now(), payloadVersion,
            stateVersion), payload));
    }
}
