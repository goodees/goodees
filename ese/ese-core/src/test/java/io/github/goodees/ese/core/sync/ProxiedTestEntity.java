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

import io.github.goodees.ese.core.Event;
import io.github.goodees.ese.core.TestEntity;
import io.github.goodees.ese.core.TestRequests;
import io.github.goodees.ese.core.store.EventStore;
import java.util.List;
import java.util.concurrent.Callable;

/**
 *
 * @author patrik
 */
public class ProxiedTestEntity extends ProxiedSyncEntity<ProxyRequests> implements ProxyRequests, TestEntity {

    private final boolean acceptSnapshot;
    private boolean wasOfferedSnapshot;
    private boolean sideEffectRequested;
    private boolean sideEffectCompensated;
    boolean eventHandlerCalled;
    Throwable eventHandlerThrowable;
    private int initialized;

    public ProxiedTestEntity(String id, EventStore store, boolean shouldAcceptSnapshot) {
        super(id, store);
        this.acceptSnapshot = shouldAcceptSnapshot;
    }

    @Override
    protected ProxyRequests requestHandler() {
        return this;
    }

    @Override
    public TestRequests.StatusProbe probe() {
        TestRequests.StatusProbe result = TestRequests.StatusProbe.create(this);
        persistAndUpdate(new TestRequests.ProbeExecutedEvent(this, result));
        return result;
    }

    @Override
    public TestRequests.StatusProbe fireSideEffect(Callable<Void> sideeffect) {
        TestRequests.SideEffectRequestedEvent events = new TestRequests.SideEffectRequestedEvent(this);
        persistAndUpdate(events);
        try {
            sideeffect.call();
        } catch (Exception e) {
            TestRequests.SideEffectCompensated compensationEvent = new TestRequests.SideEffectCompensated(this);
            persistAndUpdate(compensationEvent);
        }
        return TestRequests.StatusProbe.create(this);
    }

    @Override
    public TestRequests.SwallowException swallowException() {
        TestRequests.StatusProbe result = TestRequests.StatusProbe.create(this);
        try {
            persistAndUpdate(new TestRequests.ProbeExecutedEvent(this, result));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // TODO: should return status probe
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
