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

import io.github.goodees.ese.core.async.AsyncEntity;
import io.github.goodees.ese.core.store.EventStore;
import io.github.goodees.ese.core.sync.SyncEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.lang.Math.max;

/**
 * A single event sourced entity. The entity is bound to a {@linkplain EventSourcingRuntimeBase runtime}, that will
 * instantiate it, and a contract exists between the runtime and entity class to execute a {@link Request}.
 * <p>An entity is identified with unique String id. Usually the best in synthetic one (e. g. random uuid), but one take
 * the risk, that business key might change, then it also can be a business key (e. g. charge box name, ip address).
 *
 * <p>An entity preserves its internal state. This state can <strong>only</strong> change as result of reception of an
 * event in method {@link #updateState(Event)} (with exception of restoring a snapshot). The state of the entity,
 * and the system in general <strong>may not</strong> change in other methods, especially not in methods that execute
 * the request.
 *
 * <p>Any events that happen as consequence of executing event must be stored into provided {@link EventStore}. This
 * class does not define any specific method for that, it is left to subclasses to define API that is consistent with
 * execution style of the entities.</p>
 * @see AsyncEntity
 * @see SyncEntity
 */
public abstract class EventSourcedEntity {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final String identity;
    private long stateVersion;
    private int eventsSinceSnapshot;
    private long nextEventVersion;
    private InvocationState invocationState = new InvocationState();

    /**
     * Constructor for subclasses. Both parameters are essential for the entity, as it needs to know what is it called,
     * and where to store its events
     * @param identity the identity of the entity
     */
    protected EventSourcedEntity(String identity) {
        Objects.requireNonNull(identity);
        this.identity = identity;
    }

    /**
     * Return entity's identity.
     * @return entity's identity
     */
    public final String getIdentity() {
        return identity;
    }

    /**
     * Version of an entity. Each entity has monotonic growing version number that corresponds to number of events it
     * produced.
     * @return current entity version
     */
    public final long getStateVersion() {
        return stateVersion;
    }

    /**
     * Called by the runtime after snapshot has been restored to reset the state version.
     * @param recoveredVersion the version of the snapshot applied
     */
    final void updateStateVersion(long recoveredVersion) {
        this.stateVersion = recoveredVersion;
        this.nextEventVersion = this.stateVersion;
        this.eventsSinceSnapshot = 0;
    }

    /**
     * Called by the runtime after snapshot is saved to reset eventSinceSnapshot counter
     */
    final void snapshotStored() {
        this.eventsSinceSnapshot = 0;
    }

    /**
     * Update state after events are persisted. Entity implementations should call this method to have
     * their state updated via {@link #updateState(Event)} after events were persisted, as well as internal
     * bookeeping of the entity to be updated. When storing events fail, this method should also be called to
     * invalidate the entity.
     * @param event event that was stored
     * @param eventStoreError error that happened during persistence of event
     */
    protected final void handlePersistence(Event event, Throwable eventStoreError) {
        if (eventStoreError == null) {
            applyEvent(event);
            getInvocationState().eventPersisted(event);
        } else {
            handlePersistenceFailure(eventStoreError);
        }
    }

    /**
     * Update state after events are persisted. Entity implementations should call this method to have
     * their state updated via {@link #updateState(Event)} after events were persisted, as well as internal
     * bookeeping of the entity to be updated. When storing events fail, this method should also be called to
     * invalidate the entity.
     * @param events events that were stored
     * @param eventStoreError error that happened during persistence of event
     */
    protected final void handlePersistence(Collection<? extends Event> events, Throwable eventStoreError) {
        if (eventStoreError == null) {
            events.forEach(this::applyEvent);
            getInvocationState().eventsPersisted(events);
        } else {
            handlePersistenceFailure(eventStoreError);
        }
    }

    protected final void handlePersistenceFailure(Throwable eventStoreError) {
        getInvocationState().eventStoreFailed(eventStoreError);
    }

    /**
     * Called during state recovery for any event read from the event log.
     * @param event past event from the event log
     */
    void applyEvent(Event event) {
        updateState(event);
        // events should generally come in order, but just to be sure that we must arrive at greater version
        // or this could be a fatal error to receive an event with lower version. Or such event could be ignored.
        stateVersion = max(event.entityStateVersion(), stateVersion + 1);
        nextEventVersion = stateVersion;
        eventsSinceSnapshot++;
    }

    /**
     * Update the state as result of application of persisted event. This method must be very robust - it may not throw
     * an exception or break state invariants under any input. Failing to do so will make the entity irrecoverable and
     * the system will not be able to use it.
     *
     * <p>On rare occasions, the state might need to be handled in different way when past events are replayed during
     * recovery than when they are applied during request execution. In such case the method can query the current state
     * in {@link #getInvocationState()}</p>
     *
     * @param event event to apply
     */
    protected abstract void updateState(Event event);

    /**
     * Return (JSON) Serializable state of this entity. This method will be called by the runtime to store the snapshot
     * of the state in the database.
     * <p>No specific serialization is prescribed, the contract for that is between the implementation of entity, its
     * runtime, and the SnapshotStore that runtime uses to store the snapshot.</p>
     *
     * @return a snapshot in a payload the runtime can store in the database, null if snapshotting is not supported
     */
    protected Object createSnapshot() {
        return null;
    }

    /**
     * Initialize the state from stored snapshot. Since during the lifetime of the projects the state representation
     * changes, the entity may be required to support different versions of snapshots. That's why there is no restriction
     * on the type of the object passed to it. The state may also significantly change, or might need to capture other aspects of past events.
     * <p>If any of those conditions are true, the method should return {@code false}, and entity will get all of the
     * historical events passed to method {@link #updateState(Event)}</p>
     * <p>If the method returns true, all events that happened past the snapshot will be replayed</p>
     *
     * @param snapshot deserialized snapshot
     * @return true if state was restored from snapshot. Otherwise return false and the entire journal will be replayed
     */
    protected boolean restoreFromSnapshot(Object snapshot) {
        return false;
    }

    /**
     * Events applied since snapshot recovery. Can serve as information for the runtime on whether to make a new snapshot.
     * @return events since last snapshot was stored
     */
    protected final int getEventsSinceSnapshot() {
        return this.eventsSinceSnapshot;
    }

    /**
     * Entity's state within invocation lifecycle. Runtime uses this information to assert consistency of processing,
     * entity may use it to determine details about events resulting from an invocation, or whether the recovery is
     * underway
     * @return entity's invocation state.
     */
    protected final InvocationState getInvocationState() {
        return this.invocationState;
    }

    /**
     * A callback called after execution of request finishes. May be used to plug request-independent side effects that
     * can act of events generated during single execution run.
     * <p>This method must not change state or try to persistAndUpdate events.</p>
     * <p>Rather than called for each individual event (which is the most common scenario), all events are passed.
     * This is to allow suppress side effects if some compensation event is persisted after an event that would trigger
     * a side effect.</p>
     * @param events Events persisted during currently finished invocation
     * @param thrownException Thrown exception in case the processing did not complete successfully
     */
    protected void performPostInvocationActions(List<Event> events, Throwable thrownException) {

    }

    /**
     * Callback called after entity is recovered from store. It is called before request is executed when the instance
     * was created anew, or if snapshot store has some more events.
     */
    protected void initialize() {
    }

    /**
     * Offer next version for an event.
     * @return the version next produced event should have
     */
    protected final long nextEventVersion() {
        return ++nextEventVersion;
    }

    /**
     * The state entity is in. Serves for auditing purposes, on rare occasion could be used to determine whether
     * the entity is currently replaying past events, or executing a request.
     */
    public enum EntityInvocationState {
        INITIALIZING, IDLE, EXECUTING, SUCCESSFUL, EVENT_STORE_FAILED, FAILED
    }

    public final class InvocationState {
        private InvocationState() {

        }

        private EntityInvocationState state = EntityInvocationState.INITIALIZING;
        private long initialStateVersion;
        private final List<Event> committedEvents = new ArrayList<>();
        private final List<Event> readOnlyEventsView = Collections.unmodifiableList(committedEvents);
        private Throwable throwable;

        public EntityInvocationState getState() {
            return state;
        }

        public long getInitialStateVersion() {
            return initialStateVersion;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public List<Event> getEvents() {
            return readOnlyEventsView;
        }

        void recovering() {
            state = EntityInvocationState.INITIALIZING;
        }

        void initialized() {
            state = EntityInvocationState.IDLE;
        }

        void preInvocation() {
            initialStateVersion = getStateVersion();
            if (state != EntityInvocationState.IDLE) {
                logger.error("Broken runtime concurrency! Entity {} starts executing and while in state {}", identity,
                    state);
            }
            state = EntityInvocationState.EXECUTING;
        }

        void eventPersisted(Event event) {
            this.committedEvents.add(event);
        }

        void eventsPersisted(Collection<? extends Event> e) {
            this.committedEvents.addAll(e);
        }

        void eventStoreFailed(Throwable t) {
            this.throwable = t;
            this.state = EntityInvocationState.EVENT_STORE_FAILED;
        }

        void completed() {
            this.state = EntityInvocationState.SUCCESSFUL;
        }

        void failed(Throwable t) {
            this.throwable = t;
            this.state = EntityInvocationState.FAILED;
        }

        void postInvocation() {
            try {
                performPostInvocationActions(readOnlyEventsView, throwable);
            } catch (Exception e) {
                logger.error("Error in postInvocation phase of entity {}. Events: {}", identity, committedEvents, e);
            }
            state = EntityInvocationState.IDLE;
            throwable = null;
            committedEvents.clear();
        }

        @Override
        public String toString() {
            return "InvocationState{" + "state=" + state + ", initialStateVersion=" + initialStateVersion
                    + ", committedEvents=" + committedEvents + ", readOnlyEventsView=" + readOnlyEventsView
                    + ", throwable=" + throwable + '}';
        }
    }

}
