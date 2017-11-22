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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.github.goodees.ese.core.Event;
import io.github.goodees.ese.core.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common logic for storing snapshots. This class has access to entity's internal state that we do not want to expose
 * otherwise.
 *
 * @param <P> the type of payload. Most likely String.
 */
public abstract class SnapshotStore<P> {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    public static class Snapshot {
        private final long entityVersion;
        private final Object snapshot;

        Snapshot(long entityVersion, Object snapshot) {
            this.entityVersion = entityVersion;
            this.snapshot = snapshot;
        }

        public long getEntityVersion() {
            return entityVersion;
        }

        public Object getSnapshot() {
            return snapshot;
        }
    }

    public Optional<Snapshot> readSnapshot(String entityId) {
        SnapshotRecord snapshotRecord = retrieveSnapshotRecord(entityId);
        if (snapshotRecord != null) {
            try {
                Object snapshot = deserializeSnapshot(snapshotRecord);
                if (snapshot != null) {
                    return Optional.of(new Snapshot(snapshotRecord.header.entityStateVersion(), snapshot));
                }
            } catch (Exception e) {
                logger.error("Failure during deserialization of snapshot of {}", entityId, e);
            }
        }
        return Optional.empty();
    }

    /**
     * Obtain a snapshot of the entity and store it.
     *
     * @param entity entity to be snapshotted
     * @return true is entity returned a snapshot and it was serialized
     * @see EventSourcedEntity#createSnapshot()
     * @see #serializeSnapshot(String, long, Object)
     * @see #storeSnapshotRecord(SnapshotStore.SnapshotRecord)
     */
    public boolean store(EventSourcedEntity entity, Supplier<Object> snapshotSupplier) {
        try {
            Object snapshot = snapshotSupplier.get();
            SnapshotRecord snapshotRecord = serializeSnapshot(entity.getIdentity(), entity.getStateVersion(), snapshot);
            if (snapshotRecord != null) {
                storeSnapshotRecord(snapshotRecord);
                return true;
            }
        } catch (Exception e) {
            logger.error("Creating snapshot of entity {} failed", entity.getIdentity(), e);
        }
        return false;
    }

    /**
     * Transform stored payload into snapshot to be consumed by entity. Implementation will decide on header value, most
     * notably {@link SnapshotMetadata#payloadVersion()} on how to deserialize it.
     *
     * @param snapshotRecord the retrieved snapshot record
     * @return snapshot for deserialization
     */
    protected abstract Object deserializeSnapshot(SnapshotRecord snapshotRecord);

    /**
     * Serialize a snapshot of an entity.
     *
     * @param entityId     the identity of the entity
     * @param stateVersion version of the entity
     * @param snapshot     snapshot returned from {@link EventSourcedEntity#createSnapshot()}
     * @return header data and payload of the snapshot
     */
    protected abstract SnapshotRecord serializeSnapshot(String entityId, long stateVersion, Object snapshot);

    /**
     * Retrieve most recent snapshot for an entity from store.
     *
     * @param entityId the identity of an entity
     * @return header and payload of the snapshot
     */
    protected abstract SnapshotRecord retrieveSnapshotRecord(String entityId);

    /**
     * Actually commit the snapshot record into underlying storage.
     *
     * @param snapshotRecord the record to store.
     */
    protected abstract void storeSnapshotRecord(SnapshotRecord snapshotRecord);

    /**
     * The record about a snapshot.
     */
    protected class SnapshotRecord {
        protected final SnapshotMetadata header;
        protected final P payload;

        /**
         * Header data.
         *
         * @return the header data
         */
        public SnapshotMetadata getHeader() {
            return header;
        }

        /**
         * Payload.
         *
         * @return the payload.
         */
        public P getPayload() {
            return payload;
        }

        /**
         * Create new record
         *
         * @param header  header
         * @param payload payload
         */
        public SnapshotRecord(SnapshotMetadata header, P payload) {
            this.header = header;
            this.payload = payload;
        }
    }

}
