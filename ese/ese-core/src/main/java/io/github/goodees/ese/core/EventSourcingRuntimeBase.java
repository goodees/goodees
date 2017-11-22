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

import io.github.goodees.ese.core.async.AsyncEventSourcingRuntime;
import io.github.goodees.ese.core.dispatch.DispatchingEventSourcingRuntime;
import io.github.goodees.ese.core.store.EventLog;
import io.github.goodees.ese.core.store.EventStore;
import io.github.goodees.ese.core.store.SnapshotStore;
import io.github.goodees.ese.core.sync.SyncEventSourcingRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Common logic for facade to speaking with entities. It instantiates the entities, recovers their state, manages their snapshots
 * and invocation lifecycle. In order for recovery to work, the runtime needs {@link EventLog} to see the past events,
 * and {@link SnapshotStore} for storing the snapshots. Entities will then need to be created with an {@link EventStore}
 * that uses as is consistent with the EventLog.
 * <p>This class does not prescribe any specific execution and dispatching methods, this is left to subclasses.</p>
 * <h2>Lifecycles</h2>
 * {@link #execute(String, Request)} describes the lifecycle of single request execution.
 * {@link #lookup(String)} describes the process of obtaining an initialized entity
 * @see AsyncEventSourcingRuntime
 * @see SyncEventSourcingRuntime
 * @param <E> the type of entity this runtime handles
 */
public abstract class EventSourcingRuntimeBase<E extends EventSourcedEntity> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    /**
     * Accessor to internal entity state for snapshot store. Pass it as argument to
     * {@link SnapshotStore#recover(EventSourcedEntity, EventLog, SnapshotStore.EntityStateHandler)} to initialize
     * an EventSourcedEntity.
     */
    protected static final SnapshotStore.EntityStateHandler RECOVERY_STATE_HANDLER = new SnapshotStore.EntityStateHandler() {
        @Override
        public void updateStateVersion(EventSourcedEntity entity, long version) {
            entity.updateStateVersion(version);
        }

        @Override
        public void replayEvent(EventSourcedEntity entity, Event event) {
            entity.applyEvent(event);
        }

        @Override
        public void startRecovery(EventSourcedEntity entity) {
            entity.getInvocationState().recovering();
        }

        @Override
        public void finishRecover(EventSourcedEntity entity) {
            entity.initialize();
            entity.getInvocationState().initialized();
        }

        @Override
        public boolean restoreFromSnapshot(EventSourcedEntity entity, Object snapshot) {
            return entity.restoreFromSnapshot(snapshot);
        }

        @Override
        public Object createSnapshot(EventSourcedEntity entity) {
            return entity.createSnapshot();
        }
    };

    private final EntityInvocationHandler.Lifecycle<E> lifecycleAdapter;
    private final EntityInvocationHandler.Persistence<E> persistenceAdapter;
    private final EntityInvocationHandler.WorkingMemory<E> workingMemory = new MapBasedWorkingMemory<>();
    protected final EntityInvocationHandler<E> invocationHandler;

    protected EventSourcingRuntimeBase() {
        lifecycleAdapter = new EntityInvocationHandler.Lifecycle<E>() {
            @Override
            public E instantiate(String id) {
                return EventSourcingRuntimeBase.this.instantiate(id);
            }

            @Override
            public void dispose(E entity) {
                EventSourcingRuntimeBase.this.dispose(entity);
            }

            @Override
            public boolean shouldStoreSnapshot(E entity, int eventsSinceSnapshot) {
                return EventSourcingRuntimeBase.this.shouldStoreSnapshot(entity, eventsSinceSnapshot);
            }
        };
        persistenceAdapter = new EntityInvocationHandler.Persistence<E>() {
            @Override
            public EventLog getEventLog() {
                return EventSourcingRuntimeBase.this.getEventLog();
            }

            @Override
            public SnapshotStore<?> getSnapshotStore() {
                return EventSourcingRuntimeBase.this.getSnapshotStore();
            }

            @Override
            public boolean isInLatestKnownState(E entity) {
                return EventSourcingRuntimeBase.this.isInLatestKnownState(entity);
            }
        };
        invocationHandler = new EntityInvocationHandler<>(new EntityInvocationHandler.Configuration<E>() {
            @Override
            public EntityInvocationHandler.WorkingMemory<E> memory() {
                return workingMemory;
            }

            @Override
            public EntityInvocationHandler.Persistence persistence() {
                return persistenceAdapter;
            }

            @Override
            public EntityInvocationHandler.Lifecycle<E> lifecycle() {
                return lifecycleAdapter;
            }
        });
    }

    /**
     * Create a new uninitialized instance for given id. Serves for creating the entity with reference to the
     * EventStore, correct identity (as required by {@link EventSourcedEntity#EventSourcedEntity(String)}
     * and any other dependencies the entity might need to execute request, e. g. references to stateless ejbs, singletons,
     * or this runtime. Runtime will restore the state from snapshot and journal afterwards.
     * @param entityId the identity of the entity
     * @return freshly instantiated entity object
     */
    protected abstract E instantiate(String entityId);

    /**
     * Perform clean up before removing an entity instance. This instance will no longer be used by the runtime, and
     * if there are any steps to release its resources, they should be performed here. The method will be called by
     * the processes within this package, it should not be invoked by runtime implementations itself.
     * @param entity entity to dispose
     */
    protected abstract void dispose(E entity);

    /**
     * The SnapshotStore of this runtime. Snapshot store will be called to store a snapshot of an entity whenever
     * method {@link #shouldStoreSnapshot(EventSourcedEntity, int)} will return true.
     * @return SnapshotStore of this runtime
     * @see #lookup(String)
     * @see #execute(String, Request)
     */
    protected abstract SnapshotStore getSnapshotStore();

    /**
     * Event log of this runtime. EventLog must be consistent with EventStore used for this runtime, so it can always
     * return consistent set of events for an entity past specific version. It is used for recovery of an entity
     * @return event log of this runtime
     * @see #lookup(String)
     */
    protected abstract EventLog getEventLog();

    /**
     * Execute a request and return future result. This is the entry point for passing request to the entity and getting results from it.
     * <p>The runtime guarantees, that for given {@code entityId}, there is only one entity instance in the memory and it will
     * only execute single request at time.</p>
     *
     * <p>When request is due for invocation, the runtime will perform following steps:
     * <ol>
     *     <li>Obtain an up-to-date instance, as described by {@link #lookup(String)}</li>
     *     <li>Pass the request to the instance. Subclasses of runtime define the contract between runtime and entity</li>
     *     <li>When call completes, {@link EntityInvocationHandler#handleCompletion(String, EventSourcedEntity, Throwable)} executes following logic:
     *      <ol>
     *          <li>Entities {@linkplain EventSourcedEntity#getInvocationState() invocation state} will reflect successful
     *              or unsuccessful completion</li>
     *          <li>Method {@link EventSourcedEntity#performPostInvocationActions(List, Throwable)} is called to handle post invocation side effects}</li>
     *     <li>If the call completes exceptionally:
     *     <ol>
     *         <li>When it was due to exception from storage processing, the entity will be removed from cache, so it would
     *             be recovered into fresh state on next request</li>
     *         <li>A runtime can optionally choose to retry the request, e. g. like implemented in {@link DispatchingEventSourcingRuntime}</li>
     *         <li>The returned future completes exceptionally</li>
     *     </ol>
     *     Otherwise, the future completes successfully with the value returned by entity</li>
     *     <li>if runtime decides it {@linkplain #shouldStoreSnapshot(EventSourcedEntity, int) should store snapshot of entity state},
     *         and entity provides a snapshot, it will be stored.</li>
     *     </ol>
     * </ol>
     *
     *
     * <p>We are returning completable future, so that it is easy to either chain the calls, or synchronously wait.
     * However the clients should not call any mutation methods of the CompletableFuture, such as {@linkplain CompletableFuture#complete(Object)}.
     * They may, and in current implementations will, throw an UnsupportedOperationException.</p>
     * @param entityId the identity of the entity to be called
     * @param request the request to perform
     * @param <R> Type of request
     * @param <RS> Response type matching to the request
     * @return CompletableFuture of the result.
     * @see #lookup(String)
     */
    public abstract <R extends Request<RS>, RS> CompletableFuture<RS> execute(String entityId, R request);

    /**
     * Decide if snapshot should be stored for given instance. The decision, and snapshot is done after request has
     * been invoked.
     * @param entity instance to snapshot
     * @param eventsSinceSnapshot events applied since last snapshot
     * @return true if storage of snapshot should be attempted.
     */
    protected abstract boolean shouldStoreSnapshot(E entity, int eventsSinceSnapshot);

    /**
     * Compare current current instance to latest known state.
     *
     * @param entity the instance of an entity
     * @return false if state of the instance is not the last known
     */
    protected boolean isInLatestKnownState(E entity) {
        return getEventLog().confirmsEntityReflectsCurrentState(entity);
    }

}
