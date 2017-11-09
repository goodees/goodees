package io.github.goodees.ese;

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

/**
 *
 */
public class EventType {
    private EventType() {
        
    }
    
    public static String fromClassStripping(Class<?> clazz, String stripPrefix, String stripSuffix) {
        return fromSimpleClassnameStripping(clazz.getSimpleName(), stripPrefix, stripSuffix);
    }
            
    /**
     * Default implementation of type name. Strips Immutable prefix, and suffix Event.
     * @param simpleClassname the name of the event class
     * @return Simple name. ImmutableThingHappenedEvent becomes ThingHappened.
     */
    public static String defaultTypeName(String simpleClassname) {
        return fromSimpleClassnameStripping(simpleClassname, "", "Event");
    }
    
    public static String defaultTypeName(Class<?> clazz) {
        return defaultTypeName(clazz.getSimpleName());
    }
    
    
    public static String fromSimpleClassnameStripping(String simpleClassName, String prefix, String suffix) {
        int start = simpleClassName.startsWith(prefix) ? prefix.length() : 0;
        int end = simpleClassName.endsWith(suffix) ? simpleClassName.length() - suffix.length() : simpleClassName.length();
        return simpleClassName.substring(start, end);
    
    }
}
