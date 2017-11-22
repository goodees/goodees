package io.github.goodees.ese.core;

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
import io.github.goodees.ese.core.EventSourcingRuntimeBase;
import io.github.goodees.ese.core.EventSourcedEntity;
import io.github.goodees.ese.core.store.EventStore;
import io.github.goodees.ese.core.store.SnapshotStore;


public class MockEntities {
    public static EventSourcedEntity entityWithSnapshot(EventStore eventStore, String entityId, int stateVersion,
            Object snapshot) {
        return new EventSourcedEntity(entityId, eventStore) {
            {
                updateStateVersion(stateVersion);
            }

            @Override
            protected void updateState(Event event) {
            }

            @Override
            protected Object createSnapshot() {
                return snapshot;
            }
        };
    }

    public static SnapshotStore.EntityStateHandler handler() {
        return EventSourcingRuntimeBase.RECOVERY_STATE_HANDLER;
    }

}
