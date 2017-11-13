package io.github.goodees.ese.store;

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
 * Common interface for serialization and deserialization into String payload. Both event and snapshot stores might
 * utilize this to define entity-specific conversions.
 * <p>We expect that during lifetime of the project, the serialization scenarios might change. Whenever the serialized
 * object changes in incompatible manager, serialization should start using different unique payload version for it.</p>
 * <p>Payload version will be stored separately by the store, and will be provided to method {@code Serialization{@link #deserialize(int, String, String)}}</p>
 * <p> Usually an application writes into most recent payload version, however needs to be able to read the past versions
 * of the object.</p>
 */
public interface Serialization<T> {
    /**
     * Determine version of payload to be used for serialization.
     * @param object object to be serialized
     * @return payload version.
     */
    int payloadVersion(T object);

    /**
     * Serialize the object into a String payload.
     * @param object object to serialize
     * @return String serialization of the object
     */
    String serialize(T object);

    /**
     * Deserialize a payload given its version. As noted above, serialization must support reading all past versions
     * of payloads.
     *
     * @param payloadVersion the version of the payload as stored in the store
     * @param payload payload to deserialize
     * @param type a type discriminator if supported by underlying storage, <code>null</code> otherwise
     * @return deserialized object or null if makes sense for the usecase. Might be ok for snapshots, event recovery would
     * fail.
     */
    T deserialize(int payloadVersion, String payload, String type);

    /**
     * Return object of correct type, if its class is supported.
     *
     * <p>Even though entity APIs are quite generic in terms of produced types, they are in fact be constrained to more
     * specific parent classes. To support serialization in generic terms we need such helper method, that will guarantee
     * proper type that can be passed further.
     *
     * @param o object to cast
     * @return casted object, or <code>null</code> if instance is of unsupported type.
     */
    T toSerializable(Object o);

    Serialization<Object> NONE = new Serialization<Object>() {
        @Override
        public int payloadVersion(Object object) {
            return 0;
        }

        @Override
        public String serialize(Object object) {
            return null;
        }

        @Override
        public Object deserialize(int payloadVersion, String payload, String type) {
            return null;
        }

        @Override
        public Object toSerializable(Object o) {
            return null;
        }
    };

    static <T> Serialization<T> none() {
        return (Serialization<T>) NONE;
    }
}
