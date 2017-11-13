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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Typesafe and boilerplate-free instance matching. The matcher serves for concise type-based switches, to spare
 * developer souls from writing visitors. It is also slower than visitor, as it is linear to number of choices.
 * Contrary to switch it terminates on first match.
 */
public class TypeSwitch {

    private final List<SwitchBranch<?>> branches;

    private TypeSwitch(Builder b) {
        this.branches = new ArrayList<>(b.branches);
    }

    /**
     * Execute first matching consumer.
     * @param message message to match against
     */
    public void executeMatching(Object message) {
        for (SwitchBranch<?> branch : branches) {
            if (branch.match(message)) {
                return;
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        protected List<SwitchBranch<?>> branches = new ArrayList<>();
        protected SwitchBranch<Object> fallback;

        public <T> Builder on(Class<T> clazz, Consumer<T> callback) {
            return on(clazz, null, callback);
        }

        public <T> Builder on(Class<T> clazz, Predicate<T> predicate, Consumer<T> callback) {
            this.branches.add(new SwitchBranch<>(clazz, predicate, callback));
            return this;
        }

        public Builder otherwise(Consumer<Object> fallback) {
            this.fallback = new SwitchBranch<>(Object.class, null, fallback);
            return this;
        }

        public TypeSwitch build() {
            if (fallback != null) {
                branches.add(fallback);
            }
            return new TypeSwitch(this);
        }

    }

    private static class SwitchBranch<T> {

        private final Class<T> caseClass;
        private final Predicate<T> check;
        private final Consumer<T> callback;

        SwitchBranch(Class<T> caseClass, Predicate<T> check, Consumer<T> callback) {
            this.caseClass = Objects.requireNonNull(caseClass, "Case class cannot be null");
            this.check = check;
            this.callback = Objects.requireNonNull(callback, "Callback cannot be null");
        }

        boolean match(Object obj) {
            if (obj != null && caseClass.isInstance(obj)) {
                T inst = caseClass.cast(obj);
                if (check == null || check.test(inst)) {
                    callback.accept(inst);
                    return true;
                }
            }
            return false;
        }
    }
}
