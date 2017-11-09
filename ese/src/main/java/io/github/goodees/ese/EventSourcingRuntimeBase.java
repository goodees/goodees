package io.github.goodees.ese;

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

import io.github.goodees.ese.store.EventLog;
import io.github.goodees.ese.store.EventStore;
import io.github.goodees.ese.store.SnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    private ConcurrentMap<String, E> entities = new ConcurrentHashMap<>();

    /**
     * Create a new uninitialized instance for given id. Serves for creating the entity with reference to the
     * EventStore, correct identity (as required by {@link EventSourcedEntity#EventSourcedEntity(String, EventStore)}
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
     *     <li>When call completes, {@link #handleCompletion(String, EventSourcedEntity, Throwable)} executes following logic:
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
     * Common logic to execute after the invocation of request completes.
     * Handles:
     * <ul>
     *     <li>Removal of entity if event storing failed (e. g. entity was stale)</li>
     *     <li>Storing snapshots</li>
     * </ul>
     * Subclasses should call this method as soon as entity invocation completes to handle these guarantees that are
     * promised by method {@link #execute(String, Request)}.
     * @param entityId identity of the entity
     * @param entity instance of the entity
     * @param t non-null, when invocation completed with an exception
     */
    protected void handleCompletion(String entityId, E entity, Throwable t) {
        if (entity.getInvocationState().getState() == EventSourcedEntity.EntityInvocationState.EVENT_STORE_FAILED) {
            entity.getInvocationState().postInvocation();
            clearEntity(entityId, entity);
        } else {
            if (t != null) {
                entity.getInvocationState().failed(t);
            } else if (entity.getInvocationState().getState() != EventSourcedEntity.EntityInvocationState.EVENT_STORE_FAILED) {
                entity.getInvocationState().completed();
            }
            entity.getInvocationState().postInvocation();
            if (shouldStoreSnapshot(entity, entity.getEventsSinceSnapshot())) {
                if (getSnapshotStore().store(entity, RECOVERY_STATE_HANDLER)) {
                    // reset eventsSinceSnapshot
                    entity.snapshotStored();
                }
            }
        }
    }

    private void clearEntity(String entityId, E entity) {
        entities.remove(entityId);
        dispose(entity);
    }

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

    /**
     * Common logic for obtaining entity instance from the cache.
     * <h1>Detailed flow of instantiation of an entity:</h1>
     * <ol>
     *     <li>If the runtime has an instance in its cache, it will used the cached entity</li>
     *     <li>Otherwise it will call {@link #instantiate(String)} to create uninitialized instance</li>
     *     <li>If snapshot exists in {@link #getSnapshotStore() SnapshotStore}, it will be offered to an entity by
     *         invoking its method {@link EventSourcedEntity#restoreFromSnapshot(Object)}</li>
     *     <li>If entity accepts the snapshot, all events from the history past the snapshot will be passed, in order
     *         they were created, into method {@link EventSourcedEntity#updateState(Event)}. If entity did not accept
     *         the snapshots, all events for the entity will be replayed.</li>
     *     <li>{@link EventSourcedEntity#initialize()} is called to let entity initialize its internal processes.</li>
     * </ol>
     * After these steps the entity is initialized and requests will be passed to it.
     *
     * @param entityId the identity of an entity
     * @return instance is latest known state
     * @see #instantiate(String) for the actual lookup
     */
    protected E lookup(String entityId) {
        //MP: If instantiate and recover fails, then there is nothing you can do. So ex will just propagate to client.
        E entity = entities.computeIfAbsent(entityId, this::recoverEntity);
        if (!isInLatestKnownState(entity)) {
            getSnapshotStore().recover(entity, getEventLog(), RECOVERY_STATE_HANDLER);
        }
        // assert invocation state is idle...
        return entity;
    }

    private E recoverEntity(String entityId) {
        E instance = instantiate(entityId);
        getSnapshotStore().recover(instance, getEventLog(), RECOVERY_STATE_HANDLER);
        return instance;
    }
}
