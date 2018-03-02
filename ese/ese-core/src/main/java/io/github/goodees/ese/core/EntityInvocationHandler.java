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
import io.github.goodees.ese.core.store.EventStoreException;
import io.github.goodees.ese.core.store.SnapshotStore;
import io.github.goodees.ese.core.sync.SyncEventSourcingRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Common logic for facade to speaking with entities. It instantiates the entities, recovers their state, manages their
 * snapshots and invocation lifecycle. In order for recovery to work, the runtime needs {@link EventLog} to see the past
 * events, and {@link SnapshotStore} for storing the snapshots. Entities will then need to be created with an
 * {@link EventStore} that uses as is consistent with the EventLog.
 * <p>
 * This class allows for many direct and indirect invocations of the entity. For all of them it assures correct
 * initialization of the entity, as well as its cleanup in case of event store errors.</p>
 *
 * <h2 id="request-lifecycle">Request lifecycle</h2>
 * <p>
 * When request is due for invocation, the runtime will perform following steps:
 * <ol>
 * <li>Obtain an up-to-date instance, as described by {@linkplain Lifecycle}</li>
 * <li>Pass the request to the instance. Subclasses of runtime define the contract between runtime and entity</li>
 * <li>When call completes, {@link #handleCompletion(String, EventSourcedEntity, Throwable)} executes following logic:
 * <ol>
 * <li>Entities {@linkplain EventSourcedEntity#getInvocationState() invocation state} will reflect successful or
 * unsuccessful completion</li>
 * <li>Method {@link EventSourcedEntity#performPostInvocationActions(List, Throwable)} is called to handle post
 * invocation side effects}</li>
 * <li>If the call completes exceptionally:
 * <ol>
 * <li>When it was due to exception from storage processing, the entity will be removed from cache, so it would be
 * recovered into fresh state on next request. The call with result in {@link EventStoreException}, regardless of
 * exception handling within the entity.</li>
 * <li>A runtime can optionally choose to retry the request, e. g. like implemented in
 * {@link DispatchingEventSourcingRuntime}</li>
 * <li>The returned future completes exceptionally</li>
 * </ol>
 * Otherwise, the future completes successfully with the value returned by entity</li>
 * <li>if runtime decides it {@linkplain Lifecycle#shouldStoreSnapshot(EventSourcedEntity, int)} should store snapshot
 * of entity state}, and entity provides a snapshot, it will be stored.</li>
 * </ol>
 * </ol>
 *
 * @param <E> the type of entity this runtime handles
 * @see AsyncEventSourcingRuntime
 * @see SyncEventSourcingRuntime
 */
public class EntityInvocationHandler<E extends EventSourcedEntity> {

    private final Configuration<E> conf;

    /**
     * Configuration aspect describing in-memory storage of entities.
     *
     * @param <E> the type of entity
     */
    public interface WorkingMemory<E extends EventSourcedEntity> {

        /**
         * Return stored entity, or create and store a new instance.
         *
         * @param id id of the entity
         * @param instantiator code to invoke for obtaining a fresh entity.
         * @return an instance of entity
         */
        E lookup(String id, Function<String, E> instantiator);

        /**
         * Remove entity from working memory.
         *
         * @param id id of the entity
         */
        void remove(String id);
    }

    /**
     * Configuration aspect describing reading and writing to persistent storage.
     *
     * @param <E>
     */
    public interface Persistence<E extends EventSourcedEntity> {

        /**
         * Event log of this runtime. EventLog must be consistent with EventStore used for this runtime, so it can
         * always return consistent set of events for an entity past specific version. It is used for recovery of an
         * entity
         *
         * @return event log of this runtime
         * @see EntityInvocationHandler.Lifecycle
         */
        EventLog getEventLog();

        /**
         * The SnapshotStore of this runtime. Snapshot store will be called to store a snapshot of an entity whenever
         * method {@link Lifecycle#shouldStoreSnapshot(EventSourcedEntity, int)} will return true.
         *
         * @return SnapshotStore of this runtime
         * @see EntityInvocationHandler.Lifecycle
         */
        SnapshotStore<?> getSnapshotStore();

        /**
         * Compare current current instance to latest known state.
         *
         * @param entity the instance of an entity
         * @return false if state of the instance is not the last known
         */
        default boolean isInLatestKnownState(E entity) {
            return getEventLog().confirmsEntityReflectsCurrentState(entity);
        }
    }

    /**
     * Configuration aspect describing lifecycle of entity instances.
     * <h2 id="entity-lifecycle">Entity lifecycle</h2>
     * <ol>
     * <li>If the {@linkplain WorkingMemory} contains an instance in its cache, it will be used</li>
     * <li>Otherwise it will call {@link Lifecycle#instantiate(String)} to create uninitialized instance</li>
     * <li>If snapshot exists in {@link Persistence#getSnapshotStore() SnapshotStore}, it will be offered to an entity
     * by invoking its method {@link EventSourcedEntity#restoreFromSnapshot(Object)}</li>
     * <li>If entity accepts the snapshot, all events from the history past the snapshot will be passed, in order they
     * were created, into method {@link EventSourcedEntity#updateState(Event)}. If entity did not accept the snapshots,
     * all events for the entity will be replayed.</li>
     * <li>{@link EventSourcedEntity#initialize()} is called to let entity initialize its internal processes.</li>
     * </ol>
     * After these steps the entity is initialized and requests will be passed to it.
     *
     * @param <E>
     */
    public interface Lifecycle<E extends EventSourcedEntity> {

        /**
         * Create a new uninitialized instance for given id. Serves for creating the entity with correct identity (as
         * required by {@link EventSourcedEntity#EventSourcedEntity(String)} and any other dependencies the entity might
         * need to execute request, e. g. references to stateless ejbs, singletons, or the dispatcher. Invocation
         * handler will restore the state from snapshot and journal afterwards.
         *
         * @param id id of the entity
         * @return instantiated entity
         */
        E instantiate(String id);

        /**
         * Perform clean up before removing an entity instance. This instance will no longer be used by the runtime, and
         * if there are any steps to release its resources, they should be performed here. The method will be called by
         * the processes within this package, it should not be invoked by runtime implementations itself.
         *
         * @param entity entity to dispose
         */
        void dispose(E entity);

        /**
         * Decide if snapshot should be stored for given instance. The decision, and snapshot is done after request has
         * been invoked.
         *
         * @param entity instance to snapshot
         * @param eventsSinceSnapshot events applied since last snapshot
         * @return true if storage of snapshot should be attempted.
         */
        boolean shouldStoreSnapshot(E entity, int eventsSinceSnapshot);
    }

    /**
     * Roll all configuration aspect into single interface. The plan is to create a builder that allow reasonable
     * combinations of all three aspects. For now the (older) runtime base classes require abstract methods to be
     * defined that define these aspects.
     *
     * @param <E>
     */
    public interface Configuration<E extends EventSourcedEntity> {

        WorkingMemory<E> memory();

        Persistence persistence();

        Lifecycle<E> lifecycle();
    }

    /**
     * Helper interface describing a function that may throw a checked exception.
     *
     * @param <E> target object type (entity)
     * @param <R> result type
     * @param <X> throwable type
     */
    @FunctionalInterface
    public interface ThrowingInvocation<E, R, X extends Throwable> {

        /**
         * Apply invocation, return result or throw exception.
         *
         * @param entity target of invocation
         * @return result
         * @throws X if necessary
         */
        R invoke(E entity) throws X;
    }

    /**
     * Create handler based on configuration.
     *
     * @param configuration configuration to use
     */
    public EntityInvocationHandler(Configuration<E> configuration) {
        conf = configuration;
    }

    /**
     * SLF4J logger for handler and its subclasses.
     */
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * Invoke an action on an entity. This is the most straightforward type of call.
     * <p>
     * We are returning completable future, so that it is easy to either chain the calls, or synchronously wait. However
     * the clients should not call any mutation methods of the CompletableFuture, such as
     * {@linkplain CompletableFuture#complete(Object)}. They may, and in current implementations will, throw an
     * UnsupportedOperationException.</p>
     *
     * @param entityId the identity of the entity to be called
     * @param action the action to perform on the entity
     * @param <R> Type of request
     * @param <X> Checked exception of the action
     * @return CompletableFuture of the result.
     * @see Lifecycle
     * @throws X if action throws.
     */
    public <R, X extends Exception> R invokeSync(String entityId, ThrowingInvocation<? super E, R, X> action) throws X {
        E entity = prepareInvocation(entityId);
        try {
            R result = action.invoke(entity);
            handleCompletion(entityId, entity, null);
            return result;
        } catch (Exception e) {
            logger.info("Entity with ID {} failed on invocation. Entity: {}", entityId, entity, e);
            handleCompletion(entityId, entity, e);
            throw e;
        }
    }

    private E prepareInvocation(String entityId) {
        E entity = lookup(entityId);
        Objects.requireNonNull(entity, () -> "Lookup returned null for entityId " + entityId);
        entity.getInvocationState().preInvocation();
        return entity;
    }

    /**
     * Invoke an action of an entity and pass the result to a callback. This is useful in dispatching scenarios, where
     * the completion stage returned to client must get completed with a result.
     * @param <R> result type
     * @param <X> exception type
     * @param entityId entity id
     * @param action the action to perform on an entity
     * @param callback pass the result or thrown exception
     */
    public <R, X extends Exception> void invokeSync(String entityId, ThrowingInvocation<? super E, R, X> action,
            BiConsumer<R, Throwable> callback) {
        try {
            R result = invokeSync(entityId, action);
            callback.accept(result, null);
        } catch (Throwable t) {
            callback.accept(null, t);
        }
    }

    /**
     * Invoke an asynchronous action on an entity. Asynchronous action return completion stage as result.
     * @param <R> type of response
     * @param entityId id of entity
     * @param action asynchronous action to invoke on an entity
     * @return completion stage of result
     */
    public <R> CompletionStage<R> invokeAsync(String entityId, Function<? super E, CompletionStage<R>> action) {
        E entity = prepareInvocation(entityId);
        return action.apply(entity).handle((r, t) -> {
            handleCompletion(entityId, entity, t);
            return r;
        });
    }

    /**
     * Invoke asynchronous action on the entity and pass the result to a callback.
     * @param <R> type of response
     * @param entityId id of entity
     * @param action asynchronous action
     * @param callback callback accepting the result of the call, which is either answer or a throwable
     * @return completion stage of result
     */
    public <R> CompletionStage<R> invokeAsync(String entityId, Function<? super E, CompletionStage<R>> action, BiConsumer<R, Throwable> callback) {
        return invokeAsync(entityId, action).whenComplete(callback);
    }

    /**
     * Interface describing completion handler
     */
    @FunctionalInterface
    public interface CompletionHandler {
        void completed(Object result, Throwable exceptionResult);
    }

    /**
     * Let client invoke the action, and transfer the result to invocation handler.
     * @param entityId id of entity
     * @param action an action that will call back to provided completion handler.
     */
    public void invokeWithCallback(String entityId, BiConsumer<E, CompletionHandler> action) {
        E entity = prepareInvocation(entityId);
        action.accept(entity, (r, t) -> handleCompletion(entityId, entity, t));
    }

    /**
     * Common logic to execute after the invocation of request completes. Handles:
     * <ul>
     * <li>Removal of entity if event storing failed (e. g. entity was stale)</li>
     * <li>Storing snapshots</li>
     * </ul>
     *
     * @param entityId identity of the entity
     * @param entity instance of the entity
     * @param t non-null, when invocation completed with an exception
     * @throws EventStoreException if it occured during
     */
    private void handleCompletion(String entityId, E entity, Throwable t) {
        if (entity.getInvocationState().getState() == EventSourcedEntity.EntityInvocationState.EVENT_STORE_FAILED) {
            // what a nice reference!
            EventStoreException ese;
            if ((entity.getInvocationState().getThrowable() instanceof EventStoreException)) {
                ese = (EventStoreException) entity.getInvocationState().getThrowable();
            } else {
                ese = EventStoreException.suppressed(entityId, t);
                entity.getInvocationState().eventStoreFailed(ese);
            }
            // Call so that post invocation actions can cleanup after EventStoreException.
            entity.getInvocationState().postInvocation();
            clearEntity(entityId, entity);
            if (ese != t) {
                // if Event store exception is not the current outcome of the invocation, it has to be.
                throw ese;
            }
        } else {
            if (t != null) {
                entity.getInvocationState().failed(t);
            } else {
                entity.getInvocationState().completed();
            }
            entity.getInvocationState().postInvocation();
            if (conf.lifecycle().shouldStoreSnapshot(entity, entity.getEventsSinceSnapshot())) {
                if (conf.persistence().getSnapshotStore().store(entity, entity::createSnapshot)) {
                    // reset eventsSinceSnapshot
                    entity.snapshotStored();
                }
            }
        }
    }

    private void clearEntity(String entityId, E entity) {
        conf.memory().remove(entityId);
        conf.lifecycle().dispose(entity);
    }

    /**
     * Common logic for obtaining entity instance from the cache.
     */
    private E lookup(String entityId) {
        // If instantiate and recover fails, then there is nothing you can do. So ex will just propagate to client.
        E entity = conf.memory().lookup(entityId, this::recoverEntity);
        if (!conf.persistence().isInLatestKnownState(entity)) {
            E recoveredEntity = recover(entity);
            if (recoveredEntity != entity) {
                // oh boy
                conf.memory().remove(entityId);
                conf.memory().lookup(entityId, (_id) -> recoveredEntity);
                return recoveredEntity;
            }
        }
        // assert invocation state is idle...
        return entity;
    }

    private E recoverEntity(String entityId) {
        E instance = conf.lifecycle().instantiate(entityId);
        instance = recover(instance);
        return instance;
    }

    private E recover(E entity) {
        long recoveryStart = System.currentTimeMillis();
        entity.getInvocationState().recovering();
        E recoveredEntity = recoverFromSnapshot(entity);
        try (EventLog.StoredEvents<? extends Event> events =
                conf.persistence().getEventLog().readEvents(entity.getIdentity(), entity.getStateVersion())) {
            AtomicInteger recoveredEventsCount = new AtomicInteger();
            events.foreach(event -> {
                try {
                    recoveredEntity.applyEvent(event);
                } catch (RuntimeException e) {
                    logger.error("Entity {} failed to replay event {}", entity.getIdentity(), event.entityStateVersion(), e);
                    throw e;
                }
                recoveredEventsCount.incrementAndGet();
            });
            recoveredEntity.getInvocationState().initialized();
            logger.info("Entity {} recovered in {} ms replaying {} events", entity.getIdentity(),
                    System.currentTimeMillis() - recoveryStart, recoveredEventsCount.get());
        }
        return recoveredEntity;
    }

    private E recoverFromSnapshot(E entity) {
        Optional<SnapshotStore.Snapshot> snapshot = conf.persistence().getSnapshotStore().readSnapshot(entity.getIdentity());
        if (snapshot.isPresent()) {
            try {
                if (entity.restoreFromSnapshot(snapshot.get().getSnapshot())) {
                    entity.updateStateVersion(snapshot.get().getEntityVersion());
                }
            } catch (Exception e) {
                logger.error("Error when trying to restore from snapshot", e);
                // and we need to throw away the entity now and replay from beginning.
                // we cannot call lookup, as we're part of it
                conf.lifecycle().dispose(entity);
                entity = conf.lifecycle().instantiate(entity.getIdentity());
            }
        }
        return entity;
    }
}
