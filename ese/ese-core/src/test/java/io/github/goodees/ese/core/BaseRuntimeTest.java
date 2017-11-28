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

import io.github.goodees.ese.core.store.EventStoreException;

import java.util.concurrent.*;

import static org.junit.Assert.*;


public abstract class BaseRuntimeTest<E extends EventSourcedEntity> extends AbstractRuntimeTest {

    protected abstract EventSourcingRuntimeBase<E> runtime();


    protected <R extends Request<RS>, RS> RS sync(String id, R request) {
        try {
            CompletableFuture<RS> resp = runtime().execute(id, request);
            // put breakpoint here if you want to debug requests ;)
            return resp.get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Request should have completed under 1 second");
        }
        return null; // unreachable anyway;
    }

    @Override
    protected TestRequests.StatusProbe probe(String id) {
        return sync(id, new TestRequests.GetProbe());
    }


    
    protected void expectExceptionOnSideEffect(String id, Callable<Void> callback, EventStoreException ex) {
        CompletableFuture<TestRequests.StatusProbe> result = runtime().execute(id, new TestRequests.DoSideEffect(callback));
        try {
            result.join();
            fail("Exceptionally completed result should throw on get");
        } catch (CompletionException e) {
            assertTrue(result.isCompletedExceptionally());
            assertEquals(ex, e.getCause());
            throw (EventStoreException)e.getCause();
        }
    }

    
    protected void swallowExpectedException(String id, EventStoreException ex) {
        CompletableFuture<TestRequests.SwallowException> result = runtime().execute(id,
            new TestRequests.SwallowException());
        try {
            result.join();
        } catch (Exception e) {
            assertTrue(result.isCompletedExceptionally());
            assertEquals(ex, e.getCause());
        }        
    }

    
    protected TestRequests.StatusProbe executeSideEffect(String entityId, Callable<Void> callback) throws Exception {
        return runtime().execute(
                entityId,
                new TestRequests.DoSideEffect(callback)).get(1, TimeUnit.SECONDS);
    }


}
