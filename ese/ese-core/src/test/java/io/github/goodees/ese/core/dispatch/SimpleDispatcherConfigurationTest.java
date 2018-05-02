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
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.junit.Assert.assertEquals;

public class SimpleDispatcherConfigurationTest {
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private String invokedId;

    @Test
    public void can_be_instantiated_with_a_lambda() {
        SimpleDispatcherConfiguration conf = new SimpleDispatcherConfiguration("test", executorService,
                scheduler, this::execute);
        conf.execute("test", null, null);
        assertEquals("test", this.invokedId);
    }

    @Test
    public void does_not_retry_by_default() {
        SimpleDispatcherConfiguration conf = new SimpleDispatcherConfiguration("test", executorService,
                scheduler, this::execute);
        assertEquals(-1, conf.retryDelay("test", null, null, 1));
    }

    @Test
    public void delays_by_100ms_with_fixed_retry() {
        SimpleDispatcherConfiguration conf = new SimpleDispatcherConfiguration("test", executorService,
                scheduler, this::execute, SimpleDispatcherConfiguration.fixedRetries(5));
        assertEquals(100, conf.retryDelay("test", null, null, 1));
        assertEquals(100, conf.retryDelay("test", null, null, 2));
        assertEquals(100, conf.retryDelay("test", null, null, 3));
        assertEquals(100, conf.retryDelay("test", null, null, 4));
        assertEquals(-1, conf.retryDelay("test", null, null, 5));
    }

    @Test
    public void delays_converted_to_milliseconds_with_fixed_retry() {
        SimpleDispatcherConfiguration conf = new SimpleDispatcherConfiguration("test", executorService,
                scheduler, this::execute, SimpleDispatcherConfiguration.fixedRetries(5, 1, TimeUnit.MINUTES));
        assertEquals(60000, conf.retryDelay("test", null, null, 1));
        assertEquals(60000, conf.retryDelay("test", null, null, 2));
        assertEquals(60000, conf.retryDelay("test", null, null, 3));
        assertEquals(60000, conf.retryDelay("test", null, null, 4));
        assertEquals(-1, conf.retryDelay("test", null, null, 5));
    }


    private <R extends Request<RS>,RS> void execute(String id, R request, BiConsumer<RS, Throwable> callback) {
        this.invokedId = id;
    }

}
