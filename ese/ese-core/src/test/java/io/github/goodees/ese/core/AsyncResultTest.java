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

import io.github.goodees.ese.core.AsyncResult;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AsyncResultTest {
    @Test
    public void compensation_is_executed_on_failure() throws ExecutionException, InterruptedException {
        AsyncResult<Boolean> result = AsyncResult.returning(true);

        // we have type inference issues here, so we cannot use lambda
        boolean r = result.thenTry((AsyncResult.VoidSideEffect) () -> {
            throw new UnsupportedOperationException("bam!");
        }).onFailure(t -> AsyncResult.returning(false)).get();
        assertFalse(r);
    }

    @Test
    public void compensation_is_not_executed_on_failure_in_previous_stage() throws ExecutionException, InterruptedException {
        AsyncResult<Boolean> result = AsyncResult.throwing(new UnsupportedOperationException("Failed before attempting side effect"));

        result = result.thenTry((AsyncResult.VoidSideEffect) () -> {
            throw new UnsupportedOperationException("bam!");
        }).onFailure(t -> AsyncResult.returning(false));
        assertTrue(result.isCompletedExceptionally());
    }

    @Test
    public void compensation_is_executed_when_async_side_effect_fails() throws ExecutionException, InterruptedException {
        boolean result = AsyncResult.returning(true)
                .thenTryAsync((x) -> AsyncResult.<Boolean>throwing(new UnsupportedOperationException("Async side effect fail")))
                .onFailure((t) -> AsyncResult.returning(false)).get();
        assertFalse(result);
    }

    @Test
    public void compensation_is_not_executed_previous_stage_failed() throws ExecutionException, InterruptedException {
        AsyncResult<Boolean> result = AsyncResult.throwing(new UnsupportedOperationException("Previous stage failed"))
                .thenTryAsync((x) -> AsyncResult.<Boolean>throwing(new UnsupportedOperationException("Async side effect fail")))
                .onFailure((t) -> AsyncResult.returning(false));
        assertTrue(result.isCompletedExceptionally());
    }
}
