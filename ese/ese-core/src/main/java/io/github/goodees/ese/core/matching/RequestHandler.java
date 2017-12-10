package io.github.goodees.ese.core.matching;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Special kind of type switch, which maps to {@code CompletionStage<RS>} for many different subclasses of
 * {@code Request<RS>}, all with different response types.
 */
public class RequestHandler {

    private final List<RequestMatch<?, ?>> requestMatchers;

    private RequestHandler(Builder builder) {
        this.requestMatchers = new ArrayList<>(builder.requestMatchers);
        requestMatchers.add(builder.requestFallback);
    }

    /**
     * Default request handler that completes exceptionally with {@link UnsupportedOperationException}.
     * @param request unmatched request
     * @return UnsupportedOperationException describing that the request is not supported
     */
    public static Exception defaultUnsupportedRequestHandler(Request<?> request) {
        return new UnsupportedOperationException("Request of type " + request.getClass().getSimpleName()
                + " is not supported");
    }

    /**
     * Helper class for returning completion stage that completed exceptionally
     * @param e exception to complete with
     * @param <U> type of response
     * @return CompletableFuture that completed exceptionally.
     */
    public static <U> CompletionStage<U> throwingException(Exception e) {
        CompletableFuture<U> result = new CompletableFuture<>();
        result.completeExceptionally(e);
        return result;
    }

    /**
     * Match the request against all the matches and return the result, or null if none matched.
     * @param request request to match against
     * @param <R> type of request
     * @param <Response> type of response
     * @return completion stage corresponding to the response or null if nothing matched.
     */
    public <R extends Request<Response>, Response> CompletionStage<Response> handle(R request) {
        if (request == null) {
            return null;
        }
        for (RequestMatch<?, ?> requestMatcher : requestMatchers) {
            CompletionStage<?> result = requestMatcher.match(request);
            if (result != null) {
                return (CompletionStage<Response>) result;
            }
        }
        return null;
    }

    /**
     * Create builder, that handles unmatched request with {@link #defaultUnsupportedRequestHandler(Request)}  }
     * @return a builder
     */
    public static Builder withDefaultFallback() {
        return new Builder(RequestHandler::defaultUnsupportedRequestHandler);
    }

    /**
     * Create a builder, that uses custom fallback exception mapper.
     * @param unsupportedRequestHandler a mapper for an exception given the request
     * @return a builder
     */
    public static Builder withFallbackException(Function<Request<?>, Exception> unsupportedRequestHandler) {
        return new Builder(unsupportedRequestHandler);
    }

    /**
     * Builder for request handler.
     */
    public static class Builder {

        private List<RequestMatch<?, ?>> requestMatchers = new ArrayList<>();
        private RequestMatch<Request<?>, ?> requestFallback;

        public Builder(Function<Request<?>, Exception> unsupportedRequestHandler) {
            onUnsupportedRequestThrow(unsupportedRequestHandler);
        }

        /**
         * When request matches class, invoke the callback function
         * @param clazz request class
         * @param callback function to invoke
         * @param <R> type of request
         * @param <Response> type of response
         * @return this builder
         */
        public <R extends Request<Response>, Response> Builder on(Class<R> clazz,
                Function<R, CompletionStage<Response>> callback) {
            return on(clazz, null, callback);
        }

        /**
         * When request matches class, transform it to intermediate representation, and invoke callback with this
         * representation as first argument instead of original request
         * @param clazz request class
         * @param unwrap transformation function
         * @param callback function to invoke
         * @param <R> type of request
         * @param <Response> type of response
         * @param <X> type of intermediate representation
         * @return this builder
         */
        public <R extends Request<Response>, Response, X> Builder unwrapping(Class<R> clazz, Function<R, X> unwrap,
                Function<X, CompletionStage<Response>> callback) {
            return on(clazz, null, unwrap.andThen(callback));
        }

        /**
         * When request matches class and predicate, invoke the function
         * @param clazz request class
         * @param check further test of the request
         * @param callback function to execute
         * @param <R> type of request
         * @param <Response> type of response
         * @return this builder
         */
        public <R extends Request<Response>, Response> Builder on(Class<R> clazz, Predicate<R> check,
                Function<R, CompletionStage<Response>> callback) {
            this.requestMatchers.add(new RequestMatch<>(clazz, check, callback));
            return this;
        }

        private Builder onUnsupportedRequestThrow(Function<Request<?>, Exception> thrower) {
            Objects.requireNonNull(thrower, "Default handler for unsupported requests needs to be defined");
            this.requestFallback = new RequestMatch(Request.class, null, (r) -> throwingException(thrower.apply((Request) r)));
            return this;
        }

        /**
         * Build the handler
         * @return initialized request handler
         */
        public RequestHandler build() {
            return new RequestHandler(this);
        }
    }

    static class RequestMatch<R extends Request<Response>, Response> {
        private final Class<R> requestClass;
        private final Predicate<R> check;

        private final Function<R, CompletionStage<Response>> callback;

        RequestMatch(Class<R> requestClass, Predicate<R> check, Function<R, CompletionStage<Response>> callback) {
            this.requestClass = Objects.requireNonNull(requestClass, "Request class must be defined");
            this.check = check;
            this.callback = Objects.requireNonNull(callback, "Callback must be defined)");
        }

        CompletionStage<Response> match(Request<?> r) {
            if (r != null && requestClass.isInstance(r)) {
                R inst = requestClass.cast(r);
                if (check == null || check.test(inst)) {
                    return callback.apply(inst);
                }
            }
            return null;
        }
    }

}
