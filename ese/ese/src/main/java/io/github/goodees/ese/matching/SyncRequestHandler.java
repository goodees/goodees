package io.github.goodees.ese.matching;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Special kind of type switch, to support Request&lt;RS&gt; for synchronous entities (that may throw checked exceptions).
 */
public class SyncRequestHandler {

    private final List<RequestMatch<?, ?>> requestMatchers;

    private SyncRequestHandler(Builder builder) {
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
     * Match the request against all the matches and return the result, or null if none matched.
     * @param request request to match against
     * @param <R> type of request
     * @param <RESPONSE> type of response
     * @return completion stage corresponding to the response or null if nothing matched.
     */
    public <R extends Request<RESPONSE>, RESPONSE> RESPONSE handle(R request) throws Exception {
        if (request == null) {
            return null;
        }
        for (RequestMatch<?, ?> requestMatcher : requestMatchers) {
            if (requestMatcher.matches(request)) {
                return (RESPONSE) requestMatcher.invoke(request);
            }
        }
        return null;
    }

    /**
     * Create builder, that handles unmatched request with {@link #defaultUnsupportedRequestHandler(Request) }
     * @return a builder
     */
    public static Builder withDefaultFallback() {
        return new Builder(SyncRequestHandler::defaultUnsupportedRequestHandler);
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
         * @param <RESPONSE> type of response
         * @return this builder
         */
        public <R extends Request<RESPONSE>, RESPONSE> Builder on(Class<R> clazz, Handler<R, RESPONSE> callback) {
            return on(clazz, null, callback);
        }

        /**
         * When request matches class, transform it to intermediate representation, and invoke callback with this
         * representation as first argument instead of original request.
         * @param clazz request class
         * @param unwrap transformation function
         * @param callback function to invoke
         * @param <R> type of request
         * @param <RESPONSE> type of response
         * @param <X> type of intermediate representation
         * @return this builder
         */
        public <R extends Request<RESPONSE>, RESPONSE, X> Builder unwrapping(Class<R> clazz, Function<R, X> unwrap,
                                                                             Handler<X, RESPONSE> callback) {
            return on(clazz, null, (r) -> callback.apply(unwrap.apply(r)));
        }

        /**
         * When request matches class, transform it to intermediate representation, and invoke callback with this
         * representation if the resulting class is Y as first argument instead of original request.
         * @param clazz request class
         * @param unwrap transformation function
         * @param callback function to invoke
         * @param <R> type of request
         * @param <RESPONSE> type of response
         * @param <X> type of intermediate representation
         * @param <Y> more specific type that should be extracted
         * @return this builder
         */
        public <R extends Request<RESPONSE>, RESPONSE, X, Y extends X> Builder unwrapping(Class<R> clazz,
                                                                             Function<R, X> unwrap,
                                                                             Class<Y> constraint,
                                                                             Handler<Y, RESPONSE> callback) {
            return on(clazz,
                    //we want to apply class constraint on class of object inside wrapper, not on wrapper itself
                    (i)->(constraint.isInstance(unwrap.apply(i))),
                    (r) -> callback.apply(constraint.cast(unwrap.apply(r))));
        }

        /**
        /**
         * When request matches class and predicate, invoke the function
         * @param clazz request class
         * @param check further test of the request
         * @param callback function to execute
         * @param <R> type of request
         * @param <RESPONSE> type of response
         * @return this builder
         */
        public <R extends Request<RESPONSE>, RESPONSE> Builder on(Class<R> clazz, Predicate<R> check,
                Handler<R, RESPONSE> callback) {
            this.requestMatchers.add(new RequestMatch<>(clazz, check, callback));
            return this;
        }

        private Builder onUnsupportedRequestThrow(Function<Request<?>, Exception> thrower) {
            Objects.requireNonNull(thrower, "Default handler for unsupported requests needs to be defined");
            this.requestFallback = new RequestMatch(Request.class, null, (r) -> { throw thrower.apply((Request) r); });
            return this;
        }

        /**
         * Build the handler
         * @return initialized request handler
         */
        public SyncRequestHandler build() {
            return new SyncRequestHandler(this);
        }
    }

    @FunctionalInterface
    public interface Handler<T, U> {
        U apply(T argument) throws Exception;
    }

    static class RequestMatch<R extends Request<RESPONSE>, RESPONSE> {
        private final Class<R> requestClass;
        private final Predicate<R> check;

        private final Handler<R, RESPONSE> callback;

        RequestMatch(Class<R> requestClass, Predicate<R> check, Handler<R, RESPONSE> callback) {
            this.requestClass = Objects.requireNonNull(requestClass, "Request class must be defined");
            this.check = check;
            this.callback = Objects.requireNonNull(callback, "Callback must be defined)");
        }

        R castIfMatches(Request<?> r) {
            if (r != null && requestClass.isInstance(r)) {
                R inst = requestClass.cast(r);
                if (check == null || check.test(inst)) {
                    return inst;
                }
            }
            return null;
        }

        boolean matches(Request<?> r) {
            return castIfMatches(r) != null;
        }

        RESPONSE invoke(Request<?> r) throws Exception {
            R inst = castIfMatches(r);
            if (r != null) {
                return callback.apply(inst);
            }
            throw new IllegalArgumentException("You need to call matches(r) before calling invoke");
        }

        @Override
        public String toString() {
            return "RequestMatch{" + "requestClass=" + requestClass + ", check=" + check + ", callback=" + callback
                    + '}';
        }
    }

}
