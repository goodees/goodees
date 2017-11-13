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

import io.github.goodees.ese.Event;

import java.time.Instant;


public class JdbcTestEvent implements Event {
    private final String entityId;
    private final int version;
    private final Instant timestamp;
    private final int payload;

    public JdbcTestEvent(String entityId, int version, Instant timestamp, int payload) {
        this.entityId = entityId;
        this.version = version;
        this.timestamp = timestamp;
        this.payload = payload;
    }

    public JdbcTestEvent(String entityId, int version, int payload) {
        this(entityId, version, Instant.now(), payload);
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
    public long entityStateVersion() {
        return version;
    }

    public int getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        JdbcTestEvent that = (JdbcTestEvent) o;

        if (version != that.version)
            return false;
        if (payload != that.payload)
            return false;
        if (!entityId.equals(that.entityId))
            return false;
        return timestamp.equals(that.timestamp);
    }

    @Override
    public int hashCode() {
        int result = entityId.hashCode();
        result = 31 * result + version;
        return result;
    }
}
