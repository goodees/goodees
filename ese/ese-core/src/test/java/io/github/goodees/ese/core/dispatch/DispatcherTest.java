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

import io.github.goodees.ese.core.Event;
import io.github.goodees.ese.core.EventHeader;
import io.github.goodees.ese.core.AsyncEntity;
import io.github.goodees.ese.core.Request;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.github.goodees.ese.core.store.EventStore;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import io.github.goodees.ese.core.MockEventStore;

import static io.github.goodees.ese.core.AsyncEventSourcingRuntime.RETRY_NEVER;
import static io.github.goodees.ese.core.AsyncEventSourcingRuntime.RETRY_NOW;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.*;


public class DispatcherTest {
    private static final Logger logger = LoggerFactory.getLogger(DispatcherTest.class);
    static final EventStore store = new MockEventStore();
    static ExecutorService dispatchExecutor = Executors.newFixedThreadPool(4);
    static ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    static boolean requestsMayFail = true;
    static boolean immediateRetries = true;

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    @Before
    public void setUp() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        // any errors logged by dispatcher are actually assertion errors
        ctx.getLogger(Dispatcher.class.getName() + ".Counter").addAppender(new AppenderBase<ILoggingEvent>() {
            @Override
            protected void append(ILoggingEvent event) {
                if (event.getLevel() == Level.ERROR) {
                    collector.addError(new AssertionError(event.getFormattedMessage()));
                }
            }
        });
        requestsMayFail = true;
        immediateRetries = true;
    }

    enum Command implements Request<Integer> {
        INCREMENT, DECREMENT, WAIT, FAIL
    }

    static class Incremented extends EventHeader {

        protected Incremented(Counter source) {
            super(source);
        }
    }

    static class Decremented extends EventHeader {

        protected Decremented(Counter source) {
            super(source);
        }
    }

    static class Counter extends AsyncEntity {
        private int state;
        private int waitCorrelation;
        private Random r = new Random();

        Counter(String id) {
            super(id, DispatcherTest.store);
        }

        @Override
        public <R extends Request<RS>, RS> CompletionStage<RS> execute(R request) {
            if (requestsMayFail && r.nextDouble() > 0.5) {
                return throwing(new IllegalStateException("I randomly chose to fail"));
            }
            Command c = (Command) request;
            switch(c) {
                case INCREMENT:
                    return (CompletionStage<RS>) increment();
                case DECREMENT:
                    return (CompletionStage<RS>) decrement();
                case WAIT:
                    int rel = waitCorrelation++;
                    logger.info("Received slow request {}",rel);
                    // we cannot timeout
                    return (CompletionStage<RS>) CompletableFuture.supplyAsync(() -> {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10 ));
                        logger.info("Slow request {} completing", rel);
                        return state;
                    },dispatchExecutor);
                case FAIL:
                    return throwing(new IllegalArgumentException("I always fail"));
            }
            return null;
        }

        private CompletionStage<Integer> increment() {
            return persistAndUpdate(new Incremented(this)).thenApply(e -> state);
        }

        private CompletionStage<Integer> decrement() {
            return persistAndUpdate(new Decremented(this)).thenApply(e -> state);
        }

        @Override
        protected void updateState(Event event) {
            if (event instanceof Incremented) {
                state++;
            } else if (event instanceof Decremented) {
                state--;
            }
        }
    }

    static class SimpleConfig implements DispatcherConfiguration {
        private ConcurrentMap<String, Counter> instances = new ConcurrentHashMap<>();

        public Counter lookup(String id) {
            return instances.computeIfAbsent(id, Counter::new);
        }

        @Override
        public String dispatcherName() {
            return "Counter";
        }

        @Override
        public ExecutorService executorService() {
            return dispatchExecutor;
        }

        @Override
        public ScheduledExecutorService schedulerService() {
            return scheduler;
        }

        @Override
        public <R extends Request<RS>, RS> void execute(String id, R request, BiConsumer<RS, Throwable> callback) {
            lookup(id).execute(request).whenComplete(callback);
        }

        @Override
        public long retryDelay(String id, Request<?> request, Throwable t, int completedAttempts) {
            return completedAttempts < 5 ? immediateRetries ? RETRY_NOW : 30 : RETRY_NEVER;
        }
    }

    private SimpleConfig conf = new SimpleConfig();
    private Dispatcher cut = new Dispatcher(conf);

    private String[] instances = { "A", "B", "C", "D", "E" };

    class Client implements Runnable {
        private Random r = new Random();
        private Map<String, Integer> localChanges = new HashMap<>();

        private int passedRuns;

        @Override
        public void run() {
            try {
                for (passedRuns = 0; passedRuns < 1000; passedRuns++) {
                    runSingle();
                }
            } catch (Exception e) {
                logger.error("Execution failed unexpectedly", e);
                collector.addError(e);
            }
        }

        private void runSingle() throws ExecutionException, InterruptedException, TimeoutException {
            String instance = instances[r.nextInt(instances.length)];
            Command c = r.nextBoolean() ? Command.INCREMENT : Command.DECREMENT;
            boolean willCancel = r.nextDouble() > 0.8;
            // execute command and wait for the result
            CompletableFuture<?> f = cut.execute(instance, c);
            CompletableFuture<Void> g = f.thenAccept((r) -> {
                localChanges.putIfAbsent(instance, 0);
                localChanges.compute(instance, (k, v) -> c == Command.INCREMENT ? v + 1 : v - 1);
            })
                    .exceptionally(t -> {
                        if (!(willCancel && isCancellationException(t))) {
                            logger.info("{} on {} failed", c, instance, t);
                        }
                        return null;
                    });

            // note the subtle difference - we are cancelling f (the execution), but waiting on g.
            // cancelling g would not notify f that it should cancel.
            if (!(willCancel && f.cancel(true))) {
                 g.get(5, TimeUnit.SECONDS);
             }
        }

        private boolean isCancellationException(Throwable t) {
            return t instanceof CancellationException
                    || (t instanceof CompletionException && t.getCause() instanceof CancellationException);
        }
    }

    @Test
    public void stressTest() throws InterruptedException {
        // we'll run 8 clients in parallel, changing state of fewer entities than there are clients
        // these entities are dispatched to even smaller thread pool.
        Client[] clients = new Client[8];
        for(int i=0; i<clients.length; i++) {
            clients[i] = new Client();
        }
        Thread[] threads = Stream.of(clients).map(Thread::new).toArray(Thread[]::new);
        Stream.of(threads).forEach(Thread::run);
        for (Thread thread : threads) {
            thread.join();
        }

        for (Client client : clients) {
            assertEquals(1000, client.passedRuns);
        }
        // now we will compare sum all client's changes
        Map<String, Integer> localStats = Stream.of(clients).flatMap(c -> c.localChanges.entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.summingInt(Map.Entry::getValue)));

        // and compare them to final entity states
        for (Map.Entry<String, Integer> locl : localStats.entrySet()) {
            assertEquals((int)locl.getValue(), conf.instances.get(locl.getKey()).state);
        }

    }

    @Test
    public void requests_that_timeout_before_execution_are_cancelled() throws InterruptedException, ExecutionException, TimeoutException {
        requestsMayFail = false;
        List<CompletableFuture<Integer>> result = Stream.generate(() -> cut.executeWithTimeout("timeout", Command.WAIT, 100, TimeUnit.MILLISECONDS))
                .limit(100)
                .collect(toList());
        int completed=0;
        int cancelled=0;
        for (CompletableFuture<Integer> r : result) {
            try {
                r.get(1, TimeUnit.SECONDS);
                completed++;
            } catch (CancellationException ce) {
                cancelled++;
            }
        }
        assertThat(completed, Matchers.greaterThanOrEqualTo(9));
        assertThat(cancelled, Matchers.greaterThanOrEqualTo(80));
        assertThat(completed+cancelled, Matchers.equalTo(100));
    }

    @Test
    public void scheduled_calls_are_executed_after_immediate_requests() throws InterruptedException,
            ExecutionException, TimeoutException {
        requestsMayFail = false;
        CompletableFuture<Integer> result = cut.executeLater("scheduled", Command.WAIT, 30, TimeUnit.MILLISECONDS);
        for (int i = 0; i < 10; i++) {
            cut.execute("scheduled", Command.INCREMENT);
        }
        Integer endState = result.get(100, TimeUnit.MILLISECONDS);
        assertEquals(10, endState.intValue());
    }

    @Test(expected = CancellationException.class)
    public void requests_that_retry_timeout_relative_to_submission_time() throws TimeoutException,
            InterruptedException, ExecutionException {
        immediateRetries = false;
        CompletableFuture<Integer> result = cut.executeWithTimeout("failing_timeout", Command.FAIL, 100,
            TimeUnit.MILLISECONDS);
        result.get(110, TimeUnit.MILLISECONDS);
    }

}
