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

import java.time.Instant;

/**
 * Header data of a snapshot.
 */
public interface SnapshotMetadata {
    /**
     * Identity of the entity.
     * @return identity
     */
    String entityId();

    /**
     * Timestamp of the snapshot
     * @return the time when snapshot was created
     */
    Instant getTimestamp();

    /**
     * Payload version
     * @return version of serialization used for the payload
     */
    int payloadVersion();

    /**
     * Entity version
     * @return the version entity was in when this snapshot was generated
     */
    long entityStateVersion();

    class Default implements SnapshotMetadata {

        private final String entityId;
        private final Instant timestamp;
        private final int payloadVersion;
        private final long stateVersion;

        public Default(String entityId, Instant timestamp, int payloadVersion, long stateVersion) {
            this.entityId = entityId;
            this.timestamp = timestamp;
            this.payloadVersion = payloadVersion;
            this.stateVersion = stateVersion;
        }

        @Override
        public String entityId() {
            return entityId;
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public int payloadVersion() {
            return payloadVersion;
        }

        @Override
        public long entityStateVersion() {
            return stateVersion;
        }
    }
}
