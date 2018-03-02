package io.github.goodees.ese.core.dispatch;

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

import io.github.goodees.ese.core.EntityInvocationHandler;
import io.github.goodees.ese.core.EventSourcedEntity;
import io.github.goodees.ese.core.EventSourcingRuntimeBase;
import io.github.goodees.ese.core.Request;
import io.github.goodees.ese.core.store.EventStoreException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * Common base for runtimes that dispatch requests using {@link Dispatcher}. In addition to
 * basic execute method, dispatching runtimes support retries of requests, timeouts and scheduled request dispatching.
 */
public abstract class DispatchingEventSourcingRuntime<E extends EventSourcedEntity> extends EventSourcingRuntimeBase<E> {
    public final static long RETRY_NEVER = -1;
    public final static long RETRY_NOW = 0;

    /**
     * ExecutorService, that will handle entity invocations. Should usually be backed by multiple threads, or can use
     * Java EE 7 Concurrency Utilities' managed resources from application server.
     * @return instance of executor service
     */
    protected abstract ExecutorService getExecutorService();

    /**
     * ScheduledExecutorService, that will handle delayed delivery and timeouts of the requests. Must not use same
     * thread pool like {@link #getExecutorService()}, because in case that pool is overflowing, the timeouts would
     * not get executed
     * @return instance of ScheduledExecutorService
     */
    protected abstract ScheduledExecutorService getScheduler();

    /**
     * The abstract name for the entities the runtime is servicing. Used for better log messages, and log destinations.
     * @return short name describing the entities
     */
    protected abstract String getEntityName();

    /**
     * Pass the request to the entity. When the call completes, {@code callback} should be invoked with either the
     * result value, or the thrown exception.
     *
     * @param entity entity instance
     * @param request the request to pass
     * @param callback callback to return the control back to the runtime for the post invocation tasks
     * @param <RS> type of response
     * @param <R> type of request
     * @throws Exception when anything goes wrong in invocation at level of runtime. Any exception from the entity should
     * be reported via second parameter of the callback
     */
    protected abstract <RS, R extends Request<RS>> void invokeEntity(E entity, R request,
                                                                     BiConsumer<RS, Throwable> callback) throws Exception;

    /**
     * Specify whether and when the request should be retried in case of failure.
     * Should return redelivery delay in milliseconds. Returning {@code 0} means to retry immediately, returning less
     * than {@code 0} means not to retry.
     * <p>If retry is greater than zero, the runtime may execute other requests before the wait period expires</p>
     * @param id identity of the entity
     * @param request request that failed
     * @param error the throwable the request failed with
     * @param attempts number of attempts for execution of that request. Greater than {@code 1}.
     * @return negative for failing the request, zero for immediate retry, positive for delay in ms until next attempt
     * @see DispatcherConfiguration#retryDelay(String, Request, Throwable, int)  for exactly the same information
     */
    protected long retryDelay(String id, Request<?> request, Throwable error, int attempts) {
        if (attempts < 5) {
            return RETRY_NOW;
        } else {
            logger.warn("Entity {} failed processing request {} after {} attempts", id, request, attempts, error);
            return RETRY_NEVER;
        }
    }

    @Override
    public <R extends Request<RS>, RS> CompletableFuture<RS> execute(String id, R request) {
        return getDispatcher().execute(id, request);
    }

    /**
     * Execute the request with given timeout. If the request has not yet executed until timeout expires, it will finish
     * with {@link java.util.concurrent.CancellationException}.
     * <p>The runtime cannot guarantee, that currently executed request will be interrupted in any way, so timeouts will
     * not resolve thread starvation in case of some blocking I/O.</p>
     * @param id identity of an entity
     * @param request request to execute
     * @param timeout timeout for completion
     * @param unit unit of timeout
     * @param <R> type of request
     * @param <RS> type of response
     * @see #execute(String, Request) for general contract of execute method
     * @return CompletableFuture of the response. May complete with {@code CancellationException} if the requests time out
     */
    public <R extends Request<RS>, RS> CompletableFuture<RS> executeWithTimeout(String id, R request, long timeout,
            TimeUnit unit) {
        return getDispatcher().executeWithTimeout(id, request, timeout, unit);
    }

    /**
     * Execute the request after specified delay. When the delay timer expires, the request would be put in the queue.
     * Will be usually used by entities themselves to trigger some timed task as a reaction to other request.
     * @param id identity of an entity
     * @param request request to execute
     * @param delay amount of wait before request delivery
     * @param unit unit of delay
     * @param <R> type of request
     * @param <RS> type of response
     * @return CompletableFuture of the response
     * @see #execute(String, Request) Gneral contract of execute method
     */
    public <R extends Request<RS>, RS> CompletableFuture<RS> executeLater(String id, R request, long delay,
            TimeUnit unit) {
        return getDispatcher().executeLater(id, request, delay, unit);
    }

    //TODO not used nor tested
    /**
     * Execute the request after specified delay, but requiring it to complete within given timeout.
     * The timeout starts from the moment this method finishes, therefore timeout must be greater than the delay.
     * @param id the identity of the entity
     * @param request the request to execute
     * @param delay amount of wait before request delivery
     * @param dUnit unit of delay
     * @param timeout timeout for completion
     * @param tUnit unit of timeout
     * @param <R> request type
     * @param <RS> response type*
     * @return CompletableFuture of the response. May complete with {@code CancellationException} if the requests time out
     */
    public <R extends Request<RS>, RS> CompletableFuture<RS> executeLaterWithTimeout(String id, R request, long delay,
            TimeUnit dUnit, long timeout, TimeUnit tUnit) {
        return getDispatcher().executeLaterWithTimeout(id, request, delay, dUnit, timeout, tUnit);
    }

    private volatile Dispatcher dispatcher;

    /**
     * Lazily initialized {@link Dispatcher} to use for the callbacks.
     * @return the dispatcher of the runtime
     * @see #createDispatcher()
     */
    protected Dispatcher getDispatcher() {
        if (dispatcher == null) {
            initialize();
        }
        return dispatcher;
    }

    // The only lock in entire implementation prevents double initialization of Dispatcher;
    private synchronized void initialize() {
        if (dispatcher == null) {
            this.dispatcher = createDispatcher();
        }
    }

    /**
     * Create dispatcher before first use.
     * @return dispatcher instance
     * @see #getDispatcherConfiguration() Default configuration of the dispatcher
     */
    protected Dispatcher createDispatcher() {
        return new Dispatcher(getDispatcherConfiguration());
    }

    /**
     * Configuration of the dispatcher. Default implementation delegates to methods of this class:
     * <ul>
     *     <li>executor service is determined by {@link #getExecutorService()}</li>
     *     <li>scheduler service is determined by {@link #getScheduler()}</li>
     *     <li>dispatcher name is determined by {@link #getEntityName()}</li>
     *     <li>retry delay is determined by {@link #retryDelay(String, Request, Throwable, int)}</li>
     * </ul>
     * Actual execution flow is following:
     * <ol>
     *     <li>Entity instance is obtained via {@link EntityInvocationHandler.Lifecycle}</li>
     *     <li>Request is invoked by passing control to {@link #invokeEntity(EventSourcedEntity, Request, BiConsumer)}</li>
     *     <li>The passed callback with call {@link EntityInvocationHandler#handleCompletion(String, EventSourcedEntity, Throwable)} and then pass
     *         control back to dispatcher</li>
     * </ol>
     * If any exception happens during these steps, there response completes exceptionally with that exception.
     * @return the dispatcher configuration.
     */
    protected DispatcherConfiguration getDispatcherConfiguration() {
        return new RuntimeDispatcherConfiguration();
    }

    protected class RuntimeDispatcherConfiguration implements DispatcherConfiguration {

        @Override
        public ExecutorService executorService() {
            return getExecutorService();
        }

        @Override
        public ScheduledExecutorService schedulerService() {
            return getScheduler();
        }

        @Override
        public String dispatcherName() {
            return getEntityName();
        }

        @Override
        public <R extends Request<RS>, RS> void execute(String entityId, R request, BiConsumer<RS, Throwable> callback) {
            invocationHandler.<RS>invokeWithCallback(entityId, (entity, handleCompletion) -> {
                try {
                    invokeEntity(entity, request, (rs, t) -> {
                        try {
                            Throwable unwrapped = Dispatcher.unwrapCompletionException(t);
                            handleCompletion.completed(rs, unwrapped);
                            callback.accept(rs, unwrapped);
                        } catch (Exception e) {
                            logger.error("Entity with ID {} failed on completion of request {} ", entityId, request, e);
                            callback.accept(null, e);
                        }
                    });
                } catch (Exception e) {
                    logger.error("Entity with ID {} failed on invocation of request {}. Entity: {}", entityId, request, entity, e);
                    try {
                        handleCompletion.completed(null, e);
                        callback.accept(null, e);
                    } catch (EventStoreException ese) {
                        callback.accept(null, ese);
                    }
                }
            });
        }

        @Override
        public long retryDelay(String id, Request<?> request, Throwable t, int completedAttempts) {
            return DispatchingEventSourcingRuntime.this.retryDelay(id, request, t, completedAttempts);
        }
    }

}
