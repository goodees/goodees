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

import io.github.goodees.ese.core.Request;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

/**
 * Dependencies and strategies for Dispatcher.
 */
public interface DispatcherConfiguration {
    String dispatcherName();

    /**
     * The thread pool execution of request should run on. Agent invocations will be invoked within this thread pool.
     * @return the executor service instance
     */
    ExecutorService executorService();

    /**
     * Threadpool for scheduling requests to be delivered in the future and handling timeouts. <strong>Should be
     * different from executorService!</strong>. When same thread pools would be used, and the execution would block,
     * the unsatisfied requests would not be cancelled as there would be no free threads to perform the cancellation.
     * @return scheduled executor service instance
     */
    ScheduledExecutorService schedulerService();

    /**
     * Invoke request on the entity, pass the result to the callback.
     * <p>This method will be called on the executor thread when the request is due for execution</p>.
     * @param id id of the entity
     * @param request request to process
     * @param callback callback to call upon completion
     * @param <R> type of request
     * @param <RS> type of response
     */
    <R extends Request<RS>, RS> void execute(String id, R request, BiConsumer<RS, Throwable> callback);

    /**
     * Decide whether and when the request should be retried in case of failure.
     * Should return redelivery delay in milliseconds. Returning {@code 0} means to retry immediately, returning less
     * than {@code 0} means not to retry.
     * <p>If retry is greater than zero, the dispatcher may execute other requests before the wait period expires</p>
     * @param id identity of the entity
     * @param request request that failed
     * @param t the throwable the request failed with
     * @param completedAttempts number of attempts for execution of that request. Greater than {@code 1}.
     * @return negative in order to fail the request, zero to immediately retry ir, positive for delay in ms until next attempt

     */
    long retryDelay(String id, Request<?> request, Throwable t, int completedAttempts);
}
