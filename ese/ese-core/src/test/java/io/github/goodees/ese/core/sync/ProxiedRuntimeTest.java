/*
 * Copyright 2017 Patrik Duditš.
 *
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
 */
package io.github.goodees.ese.core.sync;

import io.github.goodees.ese.core.AbstractRuntimeTest;
import io.github.goodees.ese.core.EventSourcedEntity;
import io.github.goodees.ese.core.EventSourcingRuntimeBase;
import io.github.goodees.ese.core.BaseRuntimeTest;
import io.github.goodees.ese.core.Request;
import io.github.goodees.ese.core.TestRequests;
import io.github.goodees.ese.core.store.EventLog;
import io.github.goodees.ese.core.store.EventStoreException;
import io.github.goodees.ese.core.store.SnapshotStore;
import static io.github.goodees.ese.core.sync.SyncRuntimeTest.disposed;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * @author patrik
 */
public class ProxiedRuntimeTest extends AbstractRuntimeTest {

    @Override
    protected TestRequests.StatusProbe probe(String id) {
        return runtime.execute(id, 1, TimeUnit.SECONDS).probe();
    }

    @Override
    protected TestRequests.StatusProbe executeSideEffect(String entityId, Callable<Void> callback) throws Exception {
        return runtime.execute(entityId, 1, TimeUnit.SECONDS).fireSideEffect(callback);
    }

    @Override
    protected void expectExceptionOnSideEffect(String id, Callable<Void> callback, EventStoreException ex) {
        try {
            runtime.execute(id).fireSideEffect(callback);
            fail("should have thrown an exception");
        } catch (EventStoreException e) {
            assertEquals(ex, e);
            throw e;
        }
    }

    @Override
    protected void swallowExpectedException(String id, EventStoreException ex) {
        try {
            runtime.execute(id).swallowException();
        } catch (Exception e) {
            assertEquals(ex, e);
        }
    }
    
    static TestRuntime runtime = new TestRuntime();
    static Map<String, ProxiedTestEntity> disposed = new ConcurrentHashMap<>();
    
    static class TestRuntime extends ProxiedSyncEventSourcingRuntime<ProxiedTestEntity, ProxyRequests> {
        TestRuntime() {
            super(ProxyRequests.class);
        }

        @Override
        protected ProxiedTestEntity instantiate(String entityId) {
            return new ProxiedTestEntity(entityId, eventStore, shouldEntityAcceptSnapshot(entityId));
        }

        @Override
        protected void dispose(ProxiedTestEntity entity) {
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
        protected boolean shouldStoreSnapshot(ProxiedTestEntity entity, int eventsSinceSnapshot) {
            return ProxiedRuntimeTest.shouldStoreSnapshot(entity.getIdentity());
        }

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
            return "Test";
        }

        @Override
        protected long retryDelay(String id, String methodName, Throwable error, int attempts) {
            return RETRY_NEVER;
        }
        
        
        
        ProxiedTestEntity lookup(String id) {
            return invocationHandler().invokeSync(id, e -> e);
        }
        
    }

    @Override
    protected String prepareSnapshot(String testcase) {
        Object snapshot = new Object();
        EventSourcedEntity syncTestEntity = mockEntity(testcase, 10, snapshot);
        snapshotStore.store(syncTestEntity, () -> snapshot);
        return testcase;
    }

    @Override
    protected String prepareEvents(String testcase) throws EventStoreException {
        Long maxId = eventStore.readEvents(testcase, -1).reduce(snapshotStore.getSnapshottedVersion(testcase), (version, e) -> Long.max(version, e.entityStateVersion()));
        eventStore.persist(new TestRequests.DummyRecoveredEvent(testcase, maxId+1));
        return testcase;
    }

    @Override
    protected void assertEventHandlerInvoked(String instance, Throwable exception) {
        // if the entity was disposed as result of invocation, we should test the previous instance
        ProxiedTestEntity inst = disposed.containsKey(instance) ? disposed.get(instance) : runtime.lookup(instance);
        assertTrue(inst.eventHandlerCalled);
        assertEquals(exception, inst.eventHandlerThrowable);
    }

    @Override
    protected void assertEntityDisposed(String instance) {
        assertTrue("Entity " + instance + " should have been disposed", disposed.containsKey(instance));
    }
    
}
