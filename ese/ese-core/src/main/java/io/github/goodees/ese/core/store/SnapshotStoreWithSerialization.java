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

import io.github.goodees.ese.store.jdbc.JdbcSnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;


public abstract class SnapshotStoreWithSerialization<S> extends SnapshotStore<String> {
    protected static final Logger logger = LoggerFactory.getLogger(SnapshotStoreWithSerialization.class);
    protected final Serialization<S> serialization;

    protected SnapshotStoreWithSerialization(Serialization<S> serialization) {
        this.serialization = serialization;
    }

    @Override
    protected Object deserializeSnapshot(SnapshotRecord snapshotRecord) {
        return serialization.deserialize(snapshotRecord.getHeader().payloadVersion(), snapshotRecord.getPayload(), null);
    }

    @Override
    protected SnapshotRecord serializeSnapshot(String entityId, long stateVersion, Object snapshot) {
        S cast = serialization.toSerializable(snapshot);
        if (cast == null) {
            logger.error("Snapshot is not supported for serialization: {}", snapshot);
            return null;
        }
        String payload = serialization.serialize(cast);
        int payloadVersion = serialization.payloadVersion(cast);
        return new SnapshotRecord(new SnapshotMetadata.Default(entityId, Instant.now(), payloadVersion, stateVersion),
            payload);
    }
}
