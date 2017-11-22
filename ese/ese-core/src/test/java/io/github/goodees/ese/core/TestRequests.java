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

import io.github.goodees.ese.core.EventHeader;
import io.github.goodees.ese.core.EventSourcedEntity;
import io.github.goodees.ese.core.Request;
import java.util.concurrent.Callable;

import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.*;


public class TestRequests {

    public static class GetProbe implements Request<StatusProbe> {
    }

    public static class SwallowException implements Request<SwallowException> {
    }

    public static class DoSideEffect implements Request<StatusProbe> {
        private final Callable<Void> sideefffect;

        public DoSideEffect(Callable<Void> sideeffect) {
            this.sideefffect = sideeffect;
        }

        public void invokeSideEffect() throws Exception {
            sideefffect.call();
        }
    }

    public static class StatusProbe {
        public long entityVersion;
        public int eventsSinceSnapshot;
        public boolean offeredSnapshot;
        public boolean acceptedSnapshot;
        public boolean appliedTransitionToSideEffect;
        public boolean appliedCompensatingEvent;
        private int initialized;

        public static <E extends EventSourcedEntity & TestEntity> StatusProbe create(E inst) {
            StatusProbe result = new StatusProbe();
            result.entityVersion = inst.getStateVersion();
            result.eventsSinceSnapshot = inst.getEventsSinceSnapshot();
            result.offeredSnapshot = inst.wasOfferedSnapshot();
            result.acceptedSnapshot = inst.didAcceptSnapshot();
            result.appliedTransitionToSideEffect = inst.didTransitionToSideEffect();
            result.appliedCompensatingEvent = inst.didFireCompensatingEvent();
            result.initialized = inst.numberOfInitializations();
            return result;
        }

        public StatusProbe assertInitialVersion(boolean exp) {
            if (exp) {
                assertEquals("Entity should be without any modifications", 0, entityVersion);
            } else {
                assertThat("Entity should be in non-initial version", entityVersion, greaterThan(0L));
            }
            return this;
        }

        public StatusProbe assertOfferedSnapshot(boolean exp) {
            if (exp) {
                assertTrue("Entity should have been offered a snapshot", offeredSnapshot);
            } else {
                assertFalse("Entity should not have been offered a snapshot", offeredSnapshot);
            }
            return this;
        }

        public StatusProbe assertAcceptedSnapshot(boolean exp) {
            if (exp) {
                assertTrue("Entity should have accepted a snapshot", acceptedSnapshot);
            } else {
                assertFalse("Entity should not have accepted a snapshot", acceptedSnapshot);
            }
            return this;
        }

        public StatusProbe assertEventsPastSnapshot(boolean exp) {
            if (exp) {
                assertThat("Entity should have received additional events", eventsSinceSnapshot, greaterThan(0));
            } else {
                assertEquals("Entity should have receive no additional events", 0, eventsSinceSnapshot);
            }
            return this;
        }

        public StatusProbe assertTransitionedToSideEffect(boolean exp) {
            assertEquals("Side effect fired", exp, appliedTransitionToSideEffect);
            return this;
        }

        public StatusProbe assertCompensatingEvent(boolean exp) {
            assertEquals("Compensated for failed side effect", exp, appliedCompensatingEvent);
            return this;
        }

        public StatusProbe assertInitialized(int exp) {
            assertEquals("initialize should be called this often", exp, initialized);
            return this;
        }
    }

    public static class SideEffectRequestedEvent extends EventHeader {

        public SideEffectRequestedEvent(EventSourcedEntity source) {
            super(source);
        }
    }

    public static class SideEffectCompensated extends EventHeader {

        public SideEffectCompensated(EventSourcedEntity source) {
            super(source);
        }
    }

    public static class DummyRecoveredEvent extends EventHeader {

        public DummyRecoveredEvent(EventSourcedEntity source) {
            super(source);
        }

        public DummyRecoveredEvent(String entityId, long entityVersion) {
            super(entityId, entityVersion);
        }
    }

    public static class ProbeExecutedEvent extends EventHeader {
        final StatusProbe status;

        public ProbeExecutedEvent(EventSourcedEntity source, StatusProbe status) {
            super(source);
            this.status = status;
        }

        public StatusProbe getStatus() {
            return status;
        }
    }
}
