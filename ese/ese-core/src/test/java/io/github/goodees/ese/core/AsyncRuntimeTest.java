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

import io.github.goodees.ese.core.EventSourcingRuntimeBase;
import io.github.goodees.ese.core.AsyncEventSourcingRuntime;
import io.github.goodees.ese.core.EventSourcedEntity;
import io.github.goodees.ese.core.Request;
import io.github.goodees.ese.core.store.EventLog;
import io.github.goodees.ese.core.store.EventStoreException;
import io.github.goodees.ese.core.store.SnapshotStore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class AsyncRuntimeTest extends GeneralRuntimeTest {

    static Map<String, AsyncTestEntity> disposed = new HashMap<>();
    private static AsyncEventSourcingRuntime<AsyncTestEntity> runtime = new AsyncEventSourcingRuntime<AsyncTestEntity>() {
        @Override
        protected ExecutorService getExecutorService() {
            return testExecutorService;
        }

        @Override
        protected ScheduledExecutorService getScheduler() {
            return testScheduler;
        }

        @Override
        protected String getEntityName() {
            return "AsyncTestEntity";
        }

        @Override
        protected AsyncTestEntity instantiate(String entityId) {
            return new AsyncTestEntity(eventStore, entityId, shouldEntityAcceptSnapshot(entityId));
        }

        @Override
        protected void dispose(AsyncTestEntity entity) {
            disposed.put(entity.getIdentity(), entity);
        }

        @Override
        protected SnapshotStore getSnapshotStore() {
            return snapshotStore;
        }

        @Override
        protected EventLog getEventLog() {
            return eventStore;
        }

        @Override
        protected boolean isInLatestKnownState(AsyncTestEntity entity) {
            return eventStore.confirmsEntityReflectsCurrentState(entity);
        }

        @Override
        protected long retryDelay(String id, Request<?> request, Throwable error, int attempts) {
            return RETRY_NEVER; // we only try once in test to inspect behaviour upon failure
        }

        @Override
        protected boolean shouldStoreSnapshot(AsyncTestEntity entity, int eventsSinceSnapshot) {
            return AsyncRuntimeTest.shouldStoreSnapshot(entity.getIdentity());
        }
    };

    @Override
    protected EventSourcingRuntimeBase runtime() {
        return runtime;
    }

    @Override
    protected String prepareSnapshot(String testcase) {
        EventSourcedEntity asyncTestEntity = mockEntity(testcase, 10, new Object());
        snapshotStore.store(asyncTestEntity, EventSourcingRuntimeBase.RECOVERY_STATE_HANDLER);
        return testcase;
    }

    @Override
    protected String prepareEvents(String testcase) throws EventStoreException {
        AsyncTestEntity inst = runtime.instantiate(testcase);
        snapshotStore.recover(inst, eventStore, EventSourcingRuntimeBase.RECOVERY_STATE_HANDLER);
        eventStore.persist(new TestRequests.DummyRecoveredEvent(inst));
        return testcase;
    }

    @Override
    protected void assertEventHandlerInvoked(String instance, Throwable exception) {
        // if the entity was disposed as result of invocation, we should test the previous instance
        // or we should actually write a test that does not test exceptional invocation via event store failure
        AsyncTestEntity inst = disposed.containsKey(instance) ? disposed.get(instance) : runtime.lookup(instance);
        assertTrue(inst.eventHandlerCalled);
        assertEquals(exception, inst.eventHandlerThrowable);
    }

    @Override
    protected void assertEntityDisposed(String instance) {
        assertTrue("Entity " + instance + " should have been disposed", disposed.containsKey(instance));
    }
}
