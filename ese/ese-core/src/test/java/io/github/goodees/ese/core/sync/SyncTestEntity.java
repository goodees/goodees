package io.github.goodees.ese.core.sync;

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
import io.github.goodees.ese.core.Request;
import io.github.goodees.ese.core.TestEntity;
import io.github.goodees.ese.core.TestRequests;
import io.github.goodees.ese.core.sync.SyncEntity;
import io.github.goodees.ese.core.store.EventStore;

import java.util.List;


public class SyncTestEntity extends SyncEntity implements TestEntity {

    private final boolean acceptSnapshot;
    private boolean wasOfferedSnapshot;
    private boolean sideEffectRequested;
    private boolean sideEffectCompensated;
    boolean eventHandlerCalled;
    Throwable eventHandlerThrowable;
    private int initialized;

    protected SyncTestEntity(EventStore store, String id, boolean acceptSnapshot) {
        super(id, store);
        this.acceptSnapshot = acceptSnapshot;
    }

    @Override
    protected <R extends Request<RS>, RS> RS execute(R request) throws Exception {
        this.eventHandlerCalled = false;
        if (request instanceof TestRequests.GetProbe) {
            // 1. validate the outcome
            TestRequests.StatusProbe result = TestRequests.StatusProbe.create(this);
            // 2. produce events
            persistAndUpdate(new TestRequests.ProbeExecutedEvent(this, result));
            // 4. runtime calls updateState
            // 5. (no side effects here)
            // 6. return result
            return (RS) result;
        } else if (request instanceof TestRequests.DoSideEffect) {
            // 1. validate
            // there is no validation for this specific request
            // 2. produce events
            TestRequests.SideEffectRequestedEvent events = new TestRequests.SideEffectRequestedEvent(this);
            // 3. persistAndUpdate events
            persistAndUpdate(events);
            // 3.x. if Optimistic lock is thrown from persistAndUpdate. Runtime will onFailure and retry the request
            // 4. base class calls update state when persistAndUpdate succeeds

            try {
                // 5. try side effect
                ((TestRequests.DoSideEffect) request).invokeSideEffect();
            } catch (Exception e) {
                // 5.x.2 on failure produce compensation event
                TestRequests.SideEffectCompensated compensationEvent = new TestRequests.SideEffectCompensated(this);
                // 5.x.3 persistAndUpdate compensation events
                persistAndUpdate(compensationEvent);
            }
            // 6. return response to client
            return (RS) TestRequests.StatusProbe.create(this);
        } else if (request instanceof TestRequests.SwallowException) {
            // 1. validate the outcome
            TestRequests.StatusProbe result = TestRequests.StatusProbe.create(this);
            // 2. produce events
            try {
                persistAndUpdate(new TestRequests.ProbeExecutedEvent(this, result));
            } catch (Exception e) {
                e.printStackTrace();
            }
            // 4. runtime calls updateState
            // 5. (no side effects here)
            // 6. return result
            return (RS) result;
        }
        throw new IllegalArgumentException("Unsupported Request type " + request);
    }

    @Override
    protected void updateState(Event event) {
        // 4. update state
        if (event instanceof TestRequests.SideEffectRequestedEvent) {
            this.sideEffectRequested = true;
        } else if (event instanceof TestRequests.SideEffectCompensated) {
            this.sideEffectCompensated = true;
        }
    }

    @Override
    protected void performPostInvocationActions(List<Event> events, Throwable thrownException) {
        // 7. perform fire-and-forget side effects
        this.eventHandlerCalled = true;
        eventHandlerThrowable = thrownException;
    }

    @Override
    protected boolean restoreFromSnapshot(Object snapshot) {
        this.wasOfferedSnapshot = true;
        return this.acceptSnapshot;
    }

    @Override
    public int numberOfInitializations() {
        return initialized;
    }

    @Override
    protected void initialize() {
        this.initialized++;
    }

    @Override
    public boolean wasOfferedSnapshot() {
        return this.wasOfferedSnapshot;
    }

    @Override
    public boolean didAcceptSnapshot() {
        return this.wasOfferedSnapshot && this.acceptSnapshot;
    }

    @Override
    public boolean didTransitionToSideEffect() {
        return this.sideEffectRequested;
    }

    @Override
    public boolean didFireCompensatingEvent() {
        return this.sideEffectCompensated;
    }

    @Override
    protected Object createSnapshot() {
        return new Object();
    }
}
