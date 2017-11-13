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

import io.github.goodees.ese.store.Serialization;

import java.time.Instant;

/**
 * Created by UI187816 on 03/05/2017.
 */
public class TestEventSerialization implements Serialization<JdbcTestEvent> {
    private boolean storeHex = false;

    public void setStoreHex(boolean storeHex) {
        this.storeHex = storeHex;
    }

    @Override
    public int payloadVersion(JdbcTestEvent object) {
        return storeHex ? 2 : 1;
    }

    @Override
    public String serialize(JdbcTestEvent object) {
        String payload = storeHex ? Integer.toHexString(object.getPayload()) : Integer.toString(object.getPayload());
        return String.format("%s|%d|%s|%s", object.entityId(), object.entityStateVersion(), object.getTimestamp()
                .toString(), payload);
    }

    @Override
    public JdbcTestEvent deserialize(int payloadVersion, String payload, String type) {
        String[] parts = payload.split("\\|");
        String entityId = parts[0];
        int stateVersion = Integer.parseInt(parts[1]);
        Instant timestamp = Instant.parse(parts[2]);
        int p = Integer.parseInt(parts[3], payloadVersion == 1 ? 10 : 16);
        return new JdbcTestEvent(entityId, stateVersion, timestamp, p);
    }

    @Override
    public JdbcTestEvent toSerializable(Object o) {
        return o instanceof JdbcTestEvent ? (JdbcTestEvent) o : null;
    }
}
