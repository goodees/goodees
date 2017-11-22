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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.github.goodees.ese.core.Event;
import java.io.IOException;

/**
 * Automatic JSON event type names and their instantiation for ImmutableEvent descendants.
 * 
 * The convention is consistent with default implementation of Event.getType and is following:
 * 
 * <ul>
 * <li>All events are defined in same package as parent class</li>
 * <li>All events have suffix Event</li>
 * </ul>
 * 
 * @see ImmutableEvent#getType()
 */
public class ImmutableEventTypeResolver extends TypeIdResolverBase {
    private final static String PREFIX = "Immutable";
    private final static String SUFFIX = "Event";

    private String basePackage;

    @Override
    public void init(JavaType bt) {
        String className = bt.getRawClass().getName();
        this.basePackage = className.substring(0, className.lastIndexOf("."));
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) throws IOException {
        TypeFactory typeFactory = context.getTypeFactory();
        return typeFromId(id, typeFactory);
    }

    JavaType typeFromId(String id, TypeFactory typeFactory) throws IllegalStateException {
        String className = basePackage + "." + generateClassName(id);
        try {
            return typeFactory.constructType(typeFactory.findClass(className));
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Could not find event class for type " + id, ex);
        }
    }

    @Override
    public String idFromValue(Object value) {
        if (value instanceof ImmutableEvent) {
            return ((Event) value).getType();
        } else {
            throw new IllegalArgumentException(
                "This type resolver is only for non-null descendants of ImmutableEvent, was given " + value);
        }
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        if (value instanceof ImmutableEvent && ImmutableEvent.class.isAssignableFrom(suggestedType)) {
            return ((Event) value).getType();
        } else {
            throw new IllegalArgumentException(
                "This type resolver is only for non-null descendants of ImmutableEvent, " + "was given "
                        + suggestedType.getName() + " " + value);
        }
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }

    private String generateClassName(String id) {
        return PREFIX + id + SUFFIX;
    }

}
