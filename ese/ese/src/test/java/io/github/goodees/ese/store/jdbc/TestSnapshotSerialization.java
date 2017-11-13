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


public class TestSnapshotSerialization implements Serialization<JdbcTestSnapshot> {

    private boolean storeHex;

    @Override
    public int payloadVersion(JdbcTestSnapshot object) {
        return storeHex ? 1 : 2;
    }

    @Override
    public String serialize(JdbcTestSnapshot object) {
        return storeHex ? Integer.toHexString(object.payload) : Integer.toString(object.payload);
    }

    @Override
    public JdbcTestSnapshot deserialize(int payloadVersion, String payload, String type) {
        int p = payloadVersion == 1 ? Integer.parseInt(payload, 16) : Integer.parseInt(payload);
        return new JdbcTestSnapshot(p);
    }

    @Override
    public JdbcTestSnapshot toSerializable(Object o) {
        return o instanceof JdbcTestSnapshot ? (JdbcTestSnapshot) o : null;
    }

    public void setStoreHex(boolean storeHex) {
        this.storeHex = storeHex;
    }
}
