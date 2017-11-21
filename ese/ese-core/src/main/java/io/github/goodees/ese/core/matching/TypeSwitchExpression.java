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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Typesafe and boilerplate-free instance matching that returns a value. It's the same as {@link RequestHandler}
 * but without the request/response type safety.
 * @see RequestHandler for documentation
 */
public class TypeSwitchExpression<E> {
    private final List<SwitchBranch<?, ? extends E>> branches;

    private TypeSwitchExpression(Builder<E> b) {
        this.branches = new ArrayList<>(b.branches);
    }

    /**
     * Execute first matching consumer.
     * @param message to match against
     * @return the result of first matching branch, or null if nothing matches
     */
    public E match(Object message) {
        for (SwitchBranch<?, ? extends E> branch : branches) {
            if (branch.match(message)) {
                return branch.apply(message);
            }
        }
        return null;
    }

    public static <E> Builder<E> builder(Class<E> targetClass) {
        return new Builder<>();
    }

    public static class Builder<E> {

        protected List<SwitchBranch<?, ? extends E>> branches = new ArrayList<>();
        protected SwitchBranch<Object, ? extends E> fallback;

        public <T> Builder<E> on(Class<T> clazz, Function<T, ? extends E> callback) {
            return on(clazz, null, callback);
        }

        public <T> Builder<E> on(Class<T> clazz, Predicate<T> predicate, Function<T, ? extends E> callback) {
            this.branches.add(new SwitchBranch<>(clazz, predicate, callback));
            return this;
        }

        public <F extends E> Builder<E> otherwise(Function<Object, F> fallback) {
            this.fallback = new SwitchBranch<>(Object.class, null, fallback);
            return this;
        }

        public TypeSwitchExpression<E> build() {
            if (fallback != null) {
                branches.add(fallback);
            }
            return new TypeSwitchExpression(this);
        }

    }

    private static class SwitchBranch<T, E> {

        private final Class<T> caseClass;
        private final Predicate<T> check;
        private final Function<T, E> callback;

        SwitchBranch(Class<T> caseClass, Predicate<T> check, Function<T, E> callback) {
            this.caseClass = Objects.requireNonNull(caseClass, "Case class cannot be null");
            this.check = check;
            this.callback = Objects.requireNonNull(callback, "Callback cannot be null");
        }

        boolean match(Object obj) {
            if (obj != null && caseClass.isInstance(obj)) {
                T inst = caseClass.cast(obj);
                if (check == null || check.test(inst)) {
                    return true;
                }
            }
            return false;
        }

        E apply(Object input) {
            if (input != null && caseClass.isInstance(input)) {
                T inst = caseClass.cast(input);
                if (check == null || check.test(inst)) {
                    return callback.apply(inst);
                }
            }
            throw new IllegalArgumentException("Input does not meet the criteria of the branch");
        }
    }

}
