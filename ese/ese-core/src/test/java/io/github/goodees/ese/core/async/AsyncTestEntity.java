package io.github.goodees.ese.core.async;

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
import io.github.goodees.ese.core.async.AsyncEntity;
import io.github.goodees.ese.core.store.EventStore;

import java.util.List;
import java.util.concurrent.CompletionStage;


public class AsyncTestEntity extends AsyncEntity implements TestEntity {

    private final boolean acceptSnapshot;
    private boolean wasOfferedSnapshot;
    private boolean sideEffectRequested;
    private boolean sideEffectCompensated;
    private int initialized;
    boolean eventHandlerCalled;
    Throwable eventHandlerThrowable;

    protected AsyncTestEntity(EventStore store, String id, boolean acceptSnapshot) {
        super(id, store);
        this.acceptSnapshot = acceptSnapshot;
    }

    @Override
    protected <R extends Request<RS>, RS> CompletionStage<RS> execute(R request)  {
        this.eventHandlerCalled = false;
        if (request instanceof TestRequests.GetProbe) {
            return persistAndUpdate(new TestRequests.ProbeExecutedEvent(this, TestRequests.StatusProbe.create(this)))
                .thenApply(e -> (RS)e.getStatus());
        } else if (request instanceof TestRequests.DoSideEffect) {
            // 1. validate
            // there is no validation for this specific request
            // 2. produce events
            TestRequests.SideEffectRequestedEvent events = new TestRequests.SideEffectRequestedEvent(this);
            // 3. persistAndUpdate events
            return persistAndUpdate(events)
                    // 3.x When store fails, the result of completion stage is optimistic lock exception
                    // Runtime will onFailure and retry the request. Side effect will not be run (as it is not a successful
                    // outcome.
                    // 4. base class calls update state when persistAndUpdate succeeds
                    // or we can force programmer to not to forget add this line
                    // applyEvents(events)
                    .thenTry(() -> {
                            // 5. try side effect
                            ((TestRequests.DoSideEffect) request).invokeSideEffect();
                            // 6. return response
                            return (RS) TestRequests.StatusProbe.create(this);
                    }).onFailure(
                            // 5.x.2, 5.x.3 on failure produce and persistAndUpdate compensation events
                            (t) -> persistAndUpdate(new TestRequests.SideEffectCompensated(this))
                                // 5.x.6 return response
                                .thenApply((e) -> (RS) TestRequests.StatusProbe.create(this)));
        } else if (request instanceof TestRequests.SwallowException) {
            return persistAndUpdate(new TestRequests.ProbeExecutedEvent(this, TestRequests.StatusProbe.create(this)))
                    .thenApply(e -> (RS)e.getStatus()).onFailure(throwable -> null);
    }
        return null;
    }

    @Override
    protected void updateState(Event event) {
        // 4. update state from persisted events
        if (event instanceof TestRequests.SideEffectRequestedEvent) {
            this.sideEffectRequested = true;
        } else if (event instanceof TestRequests.SideEffectCompensated) {
            this.sideEffectCompensated = true;
        }
    }

    @Override
    protected void performPostInvocationActions(List<Event> events, Throwable thrownException) {
        // 7. handle fire and forget side effects.
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
