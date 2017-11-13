package io.github.goodees.ese.dispatch;

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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Read-only wrapper to CompletableFuture, used as return value from Dispatcher.
 */
final class FutureResponse<T> extends CompletableFuture<T> {
    private final AtomicBoolean notExecuting = new AtomicBoolean(true);
    private final Runnable cancelCallback;

    FutureResponse(Runnable cancelCallback) {
        this.cancelCallback = cancelCallback;
    }

    @Override
    public boolean complete(T value) {
        throw new UnsupportedOperationException("Modifying response from client is not allowed");
    }

    @Override
    public boolean completeExceptionally(Throwable ex) {
        throw new UnsupportedOperationException("Modifying response from client is not allowed");
    }

    /**
     * Whether execution could start. Serves for synchronizing with cancel calls, so that we wouldn't cancel a
     * request that was already dequeued.
     * @return
     */
    boolean couldStart() {
        return notExecuting.compareAndSet(true, false);
    }

    void doComplete(T value) {
        super.complete(value);
    }

    void doCompleteExceptionally(Throwable t) {
        super.completeExceptionally(t);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (couldStart()) {
            cancelCallback.run();
            return super.cancel(mayInterruptIfRunning);
        } else {
            return false;
        }
    }

    @Override
    public void obtrudeValue(T value) {
        throw new UnsupportedOperationException("Modifying response from client is not allowed");
    }

    @Override
    public void obtrudeException(Throwable ex) {
        throw new UnsupportedOperationException("Modifying response from client is not allowed");
    }

    public void stoppedExecuting() {
        notExecuting.set(true);
    }
}
