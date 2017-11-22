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

import io.github.goodees.ese.core.Event;
import io.github.goodees.ese.core.EventSourcedEntity;
import io.github.goodees.ese.core.store.EventStore;


public class JdbcTestEntity extends EventSourcedEntity {
    private int payload;

    public JdbcTestEntity(String id) {
        super(id);
    }

    @Override
    protected void updateState(Event event) {
        if (event instanceof JdbcTestEvent) {
            this.payload = ((JdbcTestEvent) event).getPayload();
        }
    }

    @Override
    protected Object createSnapshot() {
        return new JdbcTestSnapshot(payload);
    }

    @Override
    protected boolean restoreFromSnapshot(Object snapshot) {
        if (snapshot instanceof JdbcTestSnapshot) {
            this.payload = ((JdbcTestSnapshot) snapshot).payload;
            return true;
        } else {
            return false;
        }
    }

    public int getPayload() {
        return payload;
    }
}
