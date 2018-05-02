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
package io.github.goodees.ese.core;

/*-
 * #%L
 * ese-core
 * %%
 * Copyright (C) 2017 - 2018 Patrik Duditš
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
import io.github.goodees.ese.core.store.EventStoreException;
import io.github.goodees.ese.store.inmemory.InMemorySnapshotStore;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

/**
 *
 * @author patrik
 */
public abstract class AbstractRuntimeTest {
    
    protected static MockEventStore eventStore = new MockEventStore();
    protected static InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
    protected static ExecutorService testExecutorService = Executors.newFixedThreadPool(3);
    protected static ScheduledExecutorService testScheduler = Executors.newSingleThreadScheduledExecutor();

    protected static boolean shouldEntityAcceptSnapshot(String id) {
        return !"journal_is_replayed_when_snapshot_is_ignored".equals(id);
    }

    protected static boolean shouldStoreSnapshot(String testCase) {
        return "entity_snapshot_is_stored_when_requested".equals(testCase);
    }

    @BeforeClass
    public static void initialize() {
        eventStore = new MockEventStore();
        snapshotStore = new InMemorySnapshotStore();
    }
    @Rule
    public TestName testName = new TestName();
    protected AtomicBoolean sideEffectFired = new AtomicBoolean();

    protected abstract TestRequests.StatusProbe probe(String id);

    @Test
    public void successful_call_will_give_results() throws InterruptedException, TimeoutException, ExecutionException {
        TestRequests.StatusProbe result = probe(testName.getMethodName());
        result.assertOfferedSnapshot(false).assertAcceptedSnapshot(false).assertEventsPastSnapshot(false).assertInitialVersion(true).assertInitialized(1);
    }

    @Test
    public void snapshot_is_restored_before_execution() {
        String snapshottedInstance = prepareSnapshot(testName.getMethodName());
        TestRequests.StatusProbe result = probe(snapshottedInstance);
        result.assertOfferedSnapshot(true).assertAcceptedSnapshot(true).assertInitialVersion(false).assertEventsPastSnapshot(false).assertInitialized(1);
    }

    @Test
    public void entity_is_initialized_once() throws EventStoreException {
        String instance = testName.getMethodName();
        TestRequests.StatusProbe result = probe(instance);
        result.assertOfferedSnapshot(false).assertAcceptedSnapshot(false).assertInitialVersion(true).assertEventsPastSnapshot(false).assertInitialized(1);
        result = probe(instance);
        result.assertInitialized(1);
    }

    @Test
    public void entity_is_reinitialized_when_not_in_last_state() throws EventStoreException {
        String instance = testName.getMethodName();
        TestRequests.StatusProbe result = probe(instance);
        result.assertOfferedSnapshot(false).assertAcceptedSnapshot(false).assertInitialVersion(true).assertEventsPastSnapshot(false).assertInitialized(1);
        String preparedInstance = prepareEvents(instance);
        assertEquals("The test requires prepareEvents to prepare events for exactly same instance", instance, preparedInstance);
        result = probe(instance);
        result.assertEventsPastSnapshot(true).assertEventsPastSnapshot(true).assertInitialized(2);
    }
    
    private void fireSideEffectWithOptimisticLock(String id) throws EventStoreException {
        EventStoreException ex = EventStoreException.optimisticLock("test", 1);
        eventStore.throwExceptionOnce(ex);
        expectExceptionOnSideEffect(id, this::successfulSideEffect, ex);
    }

    protected abstract String prepareSnapshot(String testcase);

    protected abstract String prepareEvents(String testcase) throws EventStoreException;

    @Test
    public void outstanding_events_are_replayed_before_execution() throws EventStoreException {
        String preparedInstance = prepareEvents(testName.getMethodName());
        TestRequests.StatusProbe result = probe(preparedInstance);
        result.assertOfferedSnapshot(false).assertAcceptedSnapshot(false).assertInitialVersion(false).assertEventsPastSnapshot(true).assertInitialized(1);
    }

    @Test
    public void events_past_snapshot_are_replayed_before_execution() throws EventStoreException {
        String snapshottedInstance = prepareSnapshot(testName.getMethodName());
        String preparedInstance = prepareEvents(testName.getMethodName());
        assertEquals("Test must be executed on single entity", snapshottedInstance, preparedInstance);
        TestRequests.StatusProbe result = probe(snapshottedInstance);
        result.assertOfferedSnapshot(true).assertAcceptedSnapshot(true).assertInitialVersion(false).assertEventsPastSnapshot(true).assertInitialized(1);
    }

    @Test
    public void journal_is_replayed_when_snapshot_is_ignored() throws EventStoreException {
        String preparedInstance = prepareEvents(testName.getMethodName());
        String snapshottedInstance = prepareSnapshot(testName.getMethodName());
        assertEquals("Test must be executed on single entity", preparedInstance, snapshottedInstance);
        TestRequests.StatusProbe result = probe(snapshottedInstance);
        result.assertOfferedSnapshot(true).assertAcceptedSnapshot(false).assertInitialVersion(false).assertEventsPastSnapshot(true).assertInitialized(1);
    }

    @Test
    public void journal_is_not_replayed_when_snapshot_is_accepted() throws EventStoreException {
        String preparedInstance = prepareEvents(testName.getMethodName());
        String snapshottedInstance = prepareSnapshot(testName.getMethodName());
        assertEquals("Test must be executed on single entity", preparedInstance, snapshottedInstance);
        TestRequests.StatusProbe result = probe(snapshottedInstance);
        result.assertOfferedSnapshot(true).assertAcceptedSnapshot(true).assertInitialVersion(false).assertEventsPastSnapshot(false).assertInitialized(1);
    }

    protected Void successfulSideEffect() throws Exception {
        this.sideEffectFired.set(true);
        return null;
    }

    protected Void failedSideEffect() throws Exception {
        this.sideEffectFired.set(true);
        throw new Exception("The side effect failed");
    }

    @Test(timeout = 2000)
    public void event_store_exception_will_not_run_side_effects() throws InterruptedException, EventStoreException {
        try {
            fireSideEffectWithOptimisticLock(testName.getMethodName());
            fail("Exceptionally completed result should throw on get");
        } catch (EventStoreException e) {
        }
        assertFalse("Side effect should not be invoked on event store exception", sideEffectFired.get());
    }

    protected void performRequestWithOptimisticLockButCatchException(String id) throws EventStoreException {
        EventStoreException ex = EventStoreException.optimisticLock("test", 1);
        eventStore.throwExceptionOnce(ex);
        swallowExpectedException(id, ex);
    }

    @Test
    public void compensating_transaction_is_persisted_when_side_effect_fails() throws Exception {
        TestRequests.StatusProbe result = executeSideEffect(testName.getMethodName(), this::failedSideEffect);
        assertTrue("Side effect should have fired", sideEffectFired.get());
        result.assertTransitionedToSideEffect(true).assertCompensatingEvent(true);
    }

    // this is tested indirectly with every request
    //public void entity_state_is_updated_after_event_is_applied()
    // this is tested indirectly with compensating event
    //public void event_counter_is_updated_after_event_is_persisted()
    // this is tested indirectly in snapshot tests
    // public void entity_state_is_updated_after_recovery()
    @Test
    public void event_handlers_are_invoked_after_successful_execution() {
        TestRequests.StatusProbe result = probe(testName.getMethodName());
        assertEventHandlerInvoked(testName.getMethodName(), null);
    }

    @Test(timeout = 2000)
    public void event_handlers_are_invoked_after_failed_execution() throws InterruptedException {
        try {
            fireSideEffectWithOptimisticLock(testName.getMethodName());
        } catch (EventStoreException e) {
            assertEventHandlerInvoked(testName.getMethodName(), e);
        }
    }

    protected abstract void assertEventHandlerInvoked(String instance, Throwable exception);

    @Test(timeout = 2000)
    public void entity_is_disposed_after_persistence_error() {
        try {
            fireSideEffectWithOptimisticLock(testName.getMethodName());
        } catch (EventStoreException e) {
            assertEntityDisposed(testName.getMethodName());
        }
    }

    @Test(timeout = 2000)
    public void entity_is_cleared_and_reinstantiated_after_persistence_error() throws EventStoreException {
        performRequestWithOptimisticLockButCatchException(testName.getMethodName());
        TestRequests.StatusProbe result = probe(testName.getMethodName());
    }

    @Test
    public void entity_snapshot_is_stored_when_requested() {
        TestRequests.StatusProbe result = probe(testName.getMethodName());
        // after returning result we stored an event, therefore version is higher
        assertEquals(result.entityVersion + 1, snapshotStore.getSnapshottedVersion(testName.getMethodName()));
        result = probe(testName.getMethodName());
        // the event counter is reset after saving a snapshot
        assertEquals(0, result.eventsSinceSnapshot);
    }

    protected abstract void assertEntityDisposed(String id);

    protected EventSourcedEntity mockEntity(String entityId, long stateVersion, Object snapshot) {
        return new EventSourcedEntity(entityId) {
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

    protected abstract TestRequests.StatusProbe executeSideEffect(String entityId, Callable<Void> callback) throws Exception;

    protected abstract void expectExceptionOnSideEffect(String id, Callable<Void> callback, EventStoreException ex);

    protected abstract void swallowExpectedException(String id, EventStoreException ex);
    
}
