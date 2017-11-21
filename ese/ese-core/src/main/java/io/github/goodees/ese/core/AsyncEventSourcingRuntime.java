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

import java.util.concurrent.*;
import java.util.function.BiConsumer;

/**
 * Facade to speaking with entities. It instantiates the entities, recovers their state, manages their snapshots,
 * guarantees thread safety of request executions and retries calls in face of error.
 * <p>This runtime is base for entities based on {@link AsyncEntity}, which execute their requests asynchronously.</p>
 * @param <E> type of entity this runtime works with
 * @see DispatchingEventSourcingRuntime for documentation of all methods.
 */
public abstract class AsyncEventSourcingRuntime<E extends AsyncEntity> extends DispatchingEventSourcingRuntime<E> {

    @Override
    protected <RS, R extends Request<RS>> void invokeEntity(E entity, R request, BiConsumer<RS, Throwable> callback)
            throws Exception {
        CompletionStage<RS> invocation = entity.execute(request);
        if (invocation == null) {
            logger.error("Entity with ID {} did not return any invocation for request {}. Entity: {}",
                entity.getIdentity(), request, entity);
            callback.accept(null, new IllegalStateException("Entity " + entity.getIdentity()
                    + " did not handle the request"));
        } else {
            invocation.whenComplete(callback);
        }
    }
}
