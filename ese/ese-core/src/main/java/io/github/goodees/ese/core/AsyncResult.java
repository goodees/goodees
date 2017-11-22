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

import io.github.goodees.ese.core.dispatch.Dispatcher;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Helper class for constructing asynchronous responses. It offers convenience methods for the constructs, that
 * are common in AsyncEntity.
 *
 * @param <T> the type of result
 */
public class AsyncResult<T> extends CompletableFuture<T> {
    private AsyncResult() {
        super();
    }

    private AsyncResult(CompletionStage<T> parent) {
        this(parent, false);
    }

    private AsyncResult(CompletionStage<T> parent, boolean unwrapException) {
        super();
        subscribe(parent, unwrapException);
    }

    protected void subscribe(CompletionStage<T> parent, boolean unwrapException) {
        if (unwrapException) {
            parent.whenComplete((r, t) -> {
                if (t == null) {
                    complete(r);
                } else {
                    completeExceptionally(Dispatcher.unwrapCompletionException(t));
                }
            });
        } else {
            parent.whenComplete((r, t) -> {
                if (t == null) {
                    complete(r);
                } else {
                    completeExceptionally(t);
                }
            });
        }
    }

    private BiConsumer<T, Throwable> asCallback() {
        return (r,t) -> {
            if (t == null) {
                complete(r);
            } else {
                completeExceptionally(t);
            }
        };
    }

    /**
     * A variant of {@link CompletionStage#thenCompose(Function)}, that allows for specific code path when the passed
     * composition fails.
     * @param composition the stage to invoke
     * @param <U> type of response
     * @return stage allowing for recovery
     * @see WithRecovery#onFailure(Function) the recovery stage
     */
    public <U> WithRecovery<U> thenTryAsync(Function<? super T, ? extends CompletionStage<U>> composition) {
        return new WithRecovery<>(this, composition);
    }

    @Override
    public AsyncResult<T> whenComplete(BiConsumer<? super T, ? super Throwable> action) {
        return new AsyncResult<>(super.whenComplete(action));
    }

    /**
     * Execute a side effect, and allow for compensation logic should it throw an exception.
     * @param sideEffect side effect to perform
     * @param <U> type or response
     * @return WithRecovery stage
     * @see WithRecovery#onFailure(Function)
     */
    public <U> WithRecovery<U> thenTry(SideEffect<T, U> sideEffect) {
        return new WithRecovery<>(this, sideEffect);
    }

    /**
     * Execute a side effect in form of callable and allow for compensation logic should it throw an exception
     * @param sideEffect side effect to perform
     * @param <U> the type of response
     * @return WithRecovery stage
     * @see WithRecovery#onFailure(Function)
     */
    public <U> WithRecovery<U> thenTry(Callable<U> sideEffect) {
        return new WithRecovery<>(this, (SideEffect<T, U>) (e) -> sideEffect.call());
    }

    @Override
    public <U> AsyncResult<U> thenApply(Function<? super T, ? extends U> transformation) {
        return new AsyncResult<>(super.thenApply(transformation));
    }

    @Override
    public <U> AsyncResult<U> thenCompose(Function<? super T, ? extends CompletionStage<U>> fn) {
        return new AsyncResult<>(super.thenCompose(fn));
    }

    /**
     * Execute a side effect, and allow for compensation logic should it throw an exception.
     * @param sideEffect side effect to perform
     * @return WithRecovery stage, that will repeat the result of this stage if side effect doesn't fail
     * @see WithRecovery#onFailure(Function)
     */
    public WithRecovery<T> thenTry(VoidSideEffect sideEffect) {
        return new WithRecovery<>(this, (SideEffect<T, T>) (r) -> {
            sideEffect.doSideEffect();
            return r;
        });
    }

    /**
     * A variant of {@link CompletionStage#thenCompose(Function)} that is executed when this results completes exceptionally.
     * @param compensationLogic the compensation logic to execute. If the throwable is an {@link CompletionException}, it will be unwrapped
     * @return a stage that will compose given logic when this stage completes exceptionally.
     */
    public AsyncResult<T> onFailure(Function<Throwable, AsyncResult<T>> compensationLogic) {
        return new CompensationStage<>(this, compensationLogic);
    }

    /**
     * A side effect that may throw an exception
     * @param <T> type of input
     * @param <U> type of output
     */
    @FunctionalInterface
    public interface SideEffect<T, U> {
        U doSideEffect(T input) throws Exception;
    }

    /**
     * A side effect that returns void
     */
    public interface VoidSideEffect {
        void doSideEffect() throws Exception;
    }

    /**
     * Wrap a result of callable. The callable is invoked immediately.
     * @param action The action to perform
     * @param <V> type of result
     * @return Async result wrapping the return value or exception thrown from a callable
     */
    public static <V> AsyncResult<V> invoke(Callable<V> action) {
        AsyncResult<V> result = new AsyncResult<>();
        try {
            result.complete(action.call());
        } catch (Exception e) {
            result.completeExceptionally(e);
        }
        return result;
    }

    /**
     * Wrap a value.
     * @param result value to wrap
     * @param <V> type of result
     * @return an AsyncResult that completed successfully with the result.
     */
    public static <V> AsyncResult<V> returning(V result) {
        AsyncResult<V> r = new AsyncResult<>();
        r.complete(result);
        return r;
    }

    /**
     * Wrap an exception
     * @param t Throwable to wrap.
     * @param <V> The original return type of the throwable.
     * @return an AsyncResult that completed exceptionally with given throwable.
     */
    public static <V> AsyncResult<V> throwing(Throwable t) {
        AsyncResult<V> r = new AsyncResult<>();
        r.completeExceptionally(t);
        return r;
    }

    /**
     * Create an AsyncResult bound to other object. For example to bind to a CompletionStage you may use:
     * {@code asyncResult = AsyncResult.bindTo(completionStage::whenComplete);}
     * @param callbackConsumer the method accepting a callback for result and throwable
     * @param <V> type of result
     * @return new AsyncResult that completes when its callback is called
     */
    public static <V> AsyncResult<V> bindTo(Consumer<BiConsumer<V, Throwable>> callbackConsumer) {
        AsyncResult<V> r = new AsyncResult<>();
        callbackConsumer.accept(r.asCallback());
        return r;
    }

    /**
     * Stage allowing for composition when parent stage fails.
     * @param <U> The type of response.
     */
    public static class WithRecovery<U> {
        private AsyncResult<U> stage = new AsyncResult<>();

        private <T> WithRecovery(AsyncResult<T> parent, SideEffect<T, U> sideEffect) {
            parent.whenComplete((r,t) -> {
               if (t == null) {
                   try {
                       sideEffect.doSideEffect(r);
                   } catch (Exception e) {
                       stage.completeExceptionally(e);
                   }
               } else {
                   // previous stage failed, therefore we now wrap.
                   stage.completeExceptionally(new CompletionException(t));
               }
            });
        }

        private <T> WithRecovery(AsyncResult<T> parent, Function<? super T, ? extends CompletionStage<U>> sideEffect) {
            parent.whenComplete((r,t) -> {
                if (t == null) {
                    sideEffect.apply(r).whenComplete((rr,tt) -> {
                        if (tt == null) {
                            stage.complete(rr);
                        } else {
                            stage.completeExceptionally(tt);
                        }
                    });
                } else {
                    // previous stage failed, therefore we now wrap.
                    stage.completeExceptionally(new CompletionException(t));
                }
            });
        }

        /**
         * Logic to execute on failure.
         * @param recoveryLogic the composition to apply in case of exceptional completion
         * @return stage that will apply the recovery logic when this stage fails with exception
         * @see AsyncResult#onFailure(Function) for description of the functionality
         */
        public AsyncResult<U> onFailure(Function<Throwable, AsyncResult<U>> recoveryLogic) {
            return new CompensationStage<>(stage, recoveryLogic);
        }

        /**
         * When there's no recovery, you don't need thenTry, and your IDE will tell you.
         * @return this
         */
        public AsyncResult<U> i_shouldnt_have_used_thenTry_but_whenComplete() {
            return stage;
        }

    }

    private static class CompensationStage<T> extends AsyncResult<T> {
        private final Function<Throwable, AsyncResult<T>> compensationLogic;

        private CompensationStage(AsyncResult<T> parent, Function<Throwable, AsyncResult<T>> compensationLogic) {
            this.compensationLogic = compensationLogic;
            subscribe(parent, false);
        }

        @Override
        public boolean completeExceptionally(Throwable ex) {
            if (ex instanceof CompletionException) {
                // if the exception is wrapped inside completion exception, it did not happen immediately in previous
                // stage, as described in CompletionStage documentation:
                // " if a stage's computation terminates abruptly with an (unchecked) exception or error,
                // then all dependent stages requiring its completion complete exceptionally as well,
                // with a CompletionException holding the exception as its cause."
                // notice how it doesn't say that this doesn't hold for first dependent, because that doesn't yet form
                // a dependent stage, only the result of the callback forms a dependent stage.
                // There's a discussion when this was closed as not a bug
                // http://bugs.java.com/view_bug.do?bug_id=8068432
                //
                super.completeExceptionally(ex);
            } else {
                compensationLogic.apply(ex).whenComplete((r,t) -> {
                        if (t == null) {
                            complete(r);
                        } else {
                            super.completeExceptionally(t);
                        }
                    });
                }
            return false;
        }
    }

}
