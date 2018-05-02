package io.github.goodees.ese.core.dispatch;

/*-
 * #%L
 * ese-core
 * %%
 * Copyright (C) 2017 - 2018 Patrik Duditš
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

import io.github.goodees.ese.core.Request;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * General Dispatcher configuration implementation, as alternative to defining own subclass. Its dependencies are
 * passed to constructor, and {@linkplain RequestExecutor execution} and {@linkplain RetryStrategy retries} are separated
 * into strategies represented by functional interfaces.
 */
public class SimpleDispatcherConfiguration implements DispatcherConfiguration {
    private String name;
    private ExecutorService executorService;
    private ScheduledExecutorService schedulerService;
    private RequestExecutor requestExecutor;
    private RetryStrategy retryStrategy;

    /**
     * Create dispatcher configuration.
     * @param name The name of the dispatcher
     * @param executorService executor service to use
     * @param schedulerService scheduler service to use
     * @param requestExecutor executor to delegate request execution to
     * @param retryStrategy retry strategy to delegate retryDelay to
     */
    public SimpleDispatcherConfiguration(String name, ExecutorService executorService,
                                         ScheduledExecutorService schedulerService, RequestExecutor requestExecutor,
                                         RetryStrategy retryStrategy) {
        this.name = Objects.requireNonNull(name, "Name must be specified");
        this.executorService = Objects.requireNonNull(executorService, "Executor service must be specified");
        this.schedulerService = Objects.requireNonNull(schedulerService, "Scheduled executor must be specified");
        this.requestExecutor = Objects.requireNonNull(requestExecutor, "Request executor must be specified");
        this.retryStrategy = Objects.requireNonNull(retryStrategy, "Retry strategy must be specified");
    }

    /**
     * Create dispatcher configuration without retries.
     * @param name the name of the dispatcher
     * @param executorService executor service to use
     * @param schedulerService scheduler service to use
     * @param requestExecutor executor to delegate request execution to
     */
    public SimpleDispatcherConfiguration(String name, ExecutorService executorService,
                                         ScheduledExecutorService schedulerService, RequestExecutor requestExecutor) {
        this(name, executorService, schedulerService, requestExecutor, noRetries());
    }

    @Override
    public String dispatcherName() {
        return this.name;
    }

    @Override
    public ExecutorService executorService() {
        return executorService;
    }

    @Override
    public ScheduledExecutorService schedulerService() {
        return schedulerService;
    }

    @Override
    public <R extends Request<RS>, RS> void execute(String id, R request, BiConsumer<RS, Throwable> callback) {
        requestExecutor.execute(id, request, callback);
    }

    @Override
    public long retryDelay(String id, Request<?> request, Throwable t, int completedAttempts) {
        return retryStrategy.retryDelay(id, request, t, completedAttempts);
    }

    /**
     * Strategy for executing a request
     * @see DispatcherConfiguration#execute(String, Request, BiConsumer)
     */
    @FunctionalInterface
    public interface RequestExecutor {
        <R extends Request<RS>, RS> void execute(String id, R request, BiConsumer<RS, Throwable> callback);
    }

    /**
     * Strategy for retrying a request
     * @see DispatcherConfiguration#retryDelay(String, Request, Throwable, int)
     */
    @FunctionalInterface
    public interface RetryStrategy {
        long DO_NOT_RETRY = -1;
        long RETRY_NOW = 0;
        long retryDelay(String id, Request<?> request, Throwable t, int completedAttempts);
    }

    final static RetryStrategy NO_RETRIES = (id, request, t, attempts) -> RetryStrategy.DO_NOT_RETRY;

    /**
     * Retry strategy that doesn't retry any failed request.
     * @return a retry strategy
     */
    public static RetryStrategy noRetries() {
        return NO_RETRIES;
    }

    /**
     * Create retry strategy that allows fix number of attempts before failing the request, with delay of 100 milliseconds.
     * @param attempts number of attempts to allow
     * @return a retry strategy
     */
    public static RetryStrategy fixedRetries(int attempts) {
        return new FixedRepeat(attempts, 100);
    }

    /**
     * Create retry strategy that allows fix number of attempts with defined retry delay.
     * @param attempts number of attempt to allow
     * @param delay delay before retrying the request
     * @param unit unit of delay
     * @return a retry strategy
     */
    public static RetryStrategy fixedRetries(int attempts, long delay, TimeUnit unit) {
        return new FixedRepeat(attempts, unit.toMillis(delay));
    }

    static class FixedRepeat implements RetryStrategy {
        final int attempts;
        final long delay;

        FixedRepeat(int attempts, long delay) {
            this.attempts = attempts;
            this.delay = delay;
        }

        @Override
        public long retryDelay(String id, Request<?> request, Throwable t, int completedAttempts) {
            return completedAttempts < attempts ? delay : DO_NOT_RETRY;
        }
    }
}
