package io.github.goodees.ese.immutables;

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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

/**
 * Recommended style for immutables-based events and snapshots. Use either on package-info, or on class enclosing 
 * classes annotated with {@code @Value.Immutable}
 */
@Value.Style(overshadowImplementation = true,//
        optionalAcceptNullable = true,//
        depluralize = true,//
        jdkOnly = true, //
        get = { "get*", "is*" },

        visibility = Value.Style.ImplementationVisibility.PACKAGE)
@JsonSerialize
public @interface ImmutablesSupport {

}
