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

import io.github.goodees.ese.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asynchronous message dispatcher. Guarantees to handle at most one message at time per entity. Internally, the dispatcher
 * maintains a queue of invocations for every entity id. Whenever a new request should be invoked, the dispatcher checks
 * if it is not invoking a request already.
 */
public class Dispatcher {

    private final DispatcherConfiguration conf;
    private final ConcurrentMap<String, Mailbox> mailboxes = new ConcurrentHashMap<>();
    private final Logger logger;

    public Dispatcher(DispatcherConfiguration conf) {
        this.conf = conf;
        this.logger = LoggerFactory.getLogger(getClass().getName()+"."+conf.dispatcherName());
    }

    /**
     * Schedule a request.
     * The request is added to entity's mailbox, and that mailbox is scheduled for dequeue. The response is returned
     * immediately, as it is just a holder for future result that completes when the the request invocation completes.
     *
     * @param id      entity id
     * @param request request to pass
     * @param <R>     type of request
     * @param <RS>    type of response
     * @return the promise for the response
     */
    public <R extends Request<RS>, RS> CompletableFuture<RS> execute(String id, R request) {
        Mailbox mailbox = mailboxes.computeIfAbsent(id, Mailbox::new);
        CompletableFuture<RS> result = mailbox.enqueue(request);
        return result;
    }

    /**
     * Schedule a request with timeout. If invocation doesn't finish until timeout, the result completes exceptionally
     * with {@code TimeoutException}
     * @param id entity id
     * @param request the request
     * @param timeout timeout for completion
     * @param unit timeout unit
     * @param <R> request type
     * @param <RS> response type
     * @return the promise for the result
     */
    public <R extends Request<RS>, RS> CompletableFuture<RS> executeWithTimeout(String id, R request, long timeout, TimeUnit unit) {
        Mailbox mailbox = mailboxes.computeIfAbsent(id, Mailbox::new);
        CompletableFuture<RS> result = mailbox.enqueueWithTimeout(request, timeout, unit);
        return result;
    }

    /**
     * Schedule a request for future execution.
     * @param id entity id
     * @param request request
     * @param delay delay for invocation
     * @param unit delay unit
     * @param <R> request type
     * @param <RS> response type
     * @return the promise for the result
     */
    public <R extends Request<RS>, RS> CompletableFuture<RS> executeLater(String id, R request, long delay, TimeUnit unit) {
        Mailbox mailbox = mailboxes.computeIfAbsent(id, Mailbox::new);
        CompletableFuture<RS> result = mailbox.enqueueLater(request, delay, unit);
        return result;
    }

    /**
     * Schedule a request for future execution with timeout. The timeout is counted from the call of this method.
     * @param id entity id
     * @param request request
     * @param delay delay for invocation
     * @param dUnit delay unit
     * @param timeout timeout for invocation
     * @param tUnit timeout unit
     * @param <R> type of request
     * @param <RS> type of response
     * @return the promise for the result
     */
    public <R extends Request<RS>, RS> CompletableFuture<RS> executeLaterWithTimeout(String id, R request, long delay, TimeUnit dUnit, long timeout, TimeUnit tUnit) {
        Mailbox mailbox = mailboxes.computeIfAbsent(id, Mailbox::new);
        CompletableFuture<RS> result = mailbox.enqueueLaterWithTimeout(request, delay, dUnit, timeout, tUnit);
        return result;
    }

    public static Throwable unwrapCompletionException(Throwable ex) {
        while (ex != null && ex.getCause() != null && ex instanceof CompletionException) {
            ex = ex.getCause();
        }
        return ex;
    }
    /**
     * Queue of messages for single entity. At this level we're handling the concurrency between adding new request,
     * and executing only single request.
     */
    class Mailbox implements Runnable {
        private final String id;
        private final Deque<Invocation<?,?>> queue = new ConcurrentLinkedDeque<>();
        private final AtomicInteger enqueuesWhileBusy = new AtomicInteger();
        private final AtomicReference currentInvocation = new AtomicReference<>();

        public Mailbox(String id) {
            this.id = id;
        }

        /**
         * Add a request to entity queue, and try to process it, if it is the first one.
         *
         * @param request
         * @param <R>
         * @param <RS>
         * @return
         */
        <R extends Request<RS>, RS> CompletableFuture<RS> enqueue(R request) {
            Invocation<R, RS> inv = new Invocation<>(id, request);
            return enqueueInvocation(inv);
        }
        <R extends Request<RS>, RS> CompletableFuture<RS> enqueueWithTimeout(R request, long timeout, TimeUnit unit) {
            Invocation<R, RS> inv = new Invocation<>(id, request, timeout, unit);
            return enqueueInvocation(inv);
        }

        <R extends Request<RS>, RS> CompletableFuture<RS> enqueueLater(R request, long delay, TimeUnit unit) {
            Invocation<R, RS> inv = new Invocation<>(id, request);
            return enqueueInvocationLater(inv, delay, unit);
        }
        <R extends Request<RS>, RS> CompletableFuture<RS> enqueueLaterWithTimeout(R request, long delay, TimeUnit dUnit, long timeout, TimeUnit tUnit) {
            Invocation<R, RS> inv = new Invocation<>(id, request, timeout, tUnit);
            return enqueueInvocationLater(inv, delay, dUnit);
        }

        <R extends Request<RS>, RS> CompletableFuture<RS> enqueueInvocationLater(Invocation<R, RS> inv, long delay, TimeUnit unit) {
            conf.schedulerService().schedule(() -> this.enqueueInvocation(inv), delay, unit);
            return inv.result;
        }

        /**
         * Add an invocation to queue, and process it if it is the first one.
         * @param inv
         * @param <R>
         * @param <RS>
         * @return
         */
        <R extends Request<RS>, RS> CompletableFuture<RS> enqueueInvocation(Invocation<R, RS> inv) {
            queue.add(inv);
            if (canStartProcessing()) {
                conf.executorService().submit(this);
            }
            return inv.result;
        }

        private boolean canStartProcessing() {
            int queueSize = enqueuesWhileBusy.getAndIncrement();
            if (queueSize == 0) {
                logger.debug("Will start processing queue for {}",id);
                return true;
            } else {
                logger.debug("Will not start processing the queue for {}, {} requests enqueued during current execution", id, queueSize);
                return false;
            }
        }

        private boolean canStopProcessing(int observedEnqueues) {
            return enqueuesWhileBusy.compareAndSet(observedEnqueues, 0);
        }

        /**
         * Process single message.
         * Called when Mailbox is submitted for execution and not processing, but also at end
         * of processing. This way even messages that arrives during processing (or are retried) will be processed.
         */
        @Override
        public void run() {
            Invocation<?,?> inv = nextInvocation();
            if (inv != null) {
                if (currentInvocation.compareAndSet(null, inv)) {
                    inv.run();
                } else {
                    logger.error("Submit has run while invocation is is progress. Current invocation: {}, " +
                            "dequeued invocation: {}", currentInvocation, inv);
                    queue.addFirst(inv);
                }
            }
        }

        private Invocation<?,?> nextInvocation() {
            while (true) {
                int enqueues = enqueuesWhileBusy.get();
                Invocation<?,?> inv = queue.poll();
                if (inv == null) {
                    // An invocation might have been queued between previous line, and this decision point.
                    // Therefore we check, if canStartProcessing was called in between, and try polling the
                    // queue again, or we guarantee, that canStartProcessing will return true past the next statement.
                    if (canStopProcessing(enqueues)) {
                        logger.debug("Stopping processing of message queue for {}", id);
                        return null;
                    }
                } else {
                    return inv;
                }
            }
        }

        /**
         * Put invocation back to queue.
         *
         * @param inv
         */
        private void putBack(Invocation<?, ?> inv) {
            queue.add(inv);
        }

        /**
         * Encapsulation of request processing. Represents the request as well as actual response given to client.
         * On this level we are handling the concurrency between invocation of the request, and cancellation of it.
         * Also retries of the invocations are handled here.
         * @param <R>
         * @param <RS>
         */
        class Invocation<R extends Request<RS>, RS> implements Runnable {

            private final R request;
            private final String entityId;
            private final FutureResponse<RS> result = new FutureResponse<>(this::cancelled);
            private int completedAttempts = 0;
            private final ScheduledFuture<?> timeout;
            private final Instant submission = Instant.now();
            private Instant executionStart;

            public Invocation(String entityId, R request) {
                this.entityId = entityId;
                this.request = request;
                this.timeout = null;
            }

            public Invocation(String entityId, R request, long timeout, TimeUnit unit) {
                this.entityId = entityId;
                this.request = request;
                this.timeout = conf.schedulerService().schedule(this::timeout, timeout, unit);
            }

            @Override
            public void run() {
                if (result.couldStart()) {
                    completedAttempts++;
                    executionStart = Instant.now();
                    conf.execute(entityId, request, this::handleCompletion);
                    // There is a chance for probably not so rare situation, where the request in fact completes
                    // synchronously and right after execute finishes we are already completed. In such case we could
                    // spin another request if the previous one was not that slow. That's what Akka's dispatcher does,
                    // but we're not going to complicate the matters here.
                } else {
                    logger.info("Invocation attempted to run after it was cancelled: {}", this);
                    finish();
                }
            }

            void finish() {
                if (currentInvocation.compareAndSet(this,null)) {
                    executionStart = null;
                    result.stoppedExecuting();
                    conf.executorService().submit(Mailbox.this);
                } else {
                    logger.error("Invocation finished, but wasn't current invocation: {}", this);
                }
            }

            void cancelled() {
                queue.remove(this);
            }

            void timeout() {
                if (result.cancel(true)) {
                    logger.info("Invocation timed out: {}. Current invocation is {}", this, currentInvocation.get());
                } else {
                    if (!result.isDone()) {
                        logger.warn("ACTIVE invocation timed out: {}" , this);
                        /*
                        There is not much we can do here, as the request is already executing, and we have no way
                        of telling it to stop, we only passed completion handler.

                        Even if there would be a way, it would create lots of hard to test conditions, where we would
                        need to guarantee in every stage of asynchronous execution, that it is indeed ok.

                        Entity itself need impose timeouts for any asynchronous operations it will invoke and handle
                        them explicitly.
                         */
                    }
                }
            }

            private void handleCompletion(RS response, Throwable throwable) {
                if (throwable == null) {
                    cancelTimeout();
                    result.doComplete(response);
                } else {
                    long delay = conf.retryDelay(id, request, throwable, completedAttempts);
                    if (delay == 0) {
                        putBack(this);
                    } else if (delay > 0) {
                        conf.schedulerService().schedule(() -> enqueueInvocation(this), delay, TimeUnit.MILLISECONDS);
                    } else {
                        cancelTimeout();
                        result.doCompleteExceptionally(unwrapCompletionException(throwable));
                    }
                }
                finish();
            }

            private void cancelTimeout() {
                if (timeout != null && !timeout.isDone()) {
                    timeout.cancel(false);
                }
            }

            @Override
            public String toString() {
                return "Invocation[entity="+entityId+", request="+request +
                        ", submissionTime=" + submission +
                        ", attempts="+completedAttempts+", executionStart="+executionStart+"]";
            }
        }
    }

}
