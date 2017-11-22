package io.github.goodees.ese.store.jdbc;

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

import io.github.goodees.ese.core.Event;
import io.github.goodees.ese.core.store.EventLog;
import io.github.goodees.ese.core.EventSourcedEntity;
import io.github.goodees.ese.core.store.Serialization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Event log backend by schema and serialization.
 */
public class JdbcEventLog<E extends Event> implements EventLog {
    private static final Logger logger = LoggerFactory.getLogger(JdbcEventLog.class);

    private final DataSource ds;
    private final JdbcSchema schema;
    private final Serialization<E> serialization;
    private final boolean strict;

    /**
     * Create instance that will read from provided datasource, delegating queries to JdbcSchema, deserializing events
     * by serialization, while being or not being strict.
     *
     * <p>When event log is in strict mode, it will throw an exception when an event being read cannot be deserialized.
     * This can usually happen in two cases: Either there was an error in payload serialization, or an event could have
     * belong to a future version of the system, code was rolled back and currently running code doesn't yet know such event.
     * <p>When {@code strict} is false, such event is skipped.
     *
     * <p>In case the entity needs very strong state consistency guarantees, strict mode should be used.
     *
     * @param ds
     * @param schema
     * @param serialization
     * @param strict
     */
    public JdbcEventLog(DataSource ds, JdbcSchema schema, Serialization<E> serialization, boolean strict) {
        this.ds = ds;
        this.schema = schema;
        this.serialization = serialization;
        this.strict = strict;
    }

    @Override
    public StoredEvents<E> readEvents(String entityId, long afterVersion) {
        try {
            return new JdbcStoredEvents(entityId, afterVersion);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot access storage", e);
        }
    }

    @Override
    public boolean confirmsEntityReflectsCurrentState(EventSourcedEntity entity) {
        String entityId = entity.getIdentity();
        long expectedVersion = entity.getStateVersion();
        try (Connection connection = ds.getConnection();
                PreparedStatement entityVersion = schema.selectEntityVersion(connection, entityId);
                ResultSet rs = entityVersion.executeQuery()) {
            if (rs.next()) {
                return expectedVersion >= schema.readEntityVersion(rs);
            } else {
                return true;
            }
        } catch (SQLException sqle) {
            logger.error("Could not determine current version of an entity", sqle);
            // we cannot read, so we'll see if we will be able to write...
            return true;
        }
    }

    /**
     * Indicate whether failure to deserialize event causes exception to be thrown.
     * @return
     */
    public boolean isStrict() {
        return strict;
    }

    class JdbcStoredEvents implements EventLog.StoredEvents<E> {
        private final String entityId;
        private Connection connection;
        private PreparedStatement statement;
        private ResultSet resultSet;
        private boolean iterating;
        private boolean stop;

        JdbcStoredEvents(String entityId, long afterVersion) throws SQLException {
            try {
                connection = ds.getConnection();
                this.entityId = entityId;
                statement = schema.selectEvents(connection, entityId, afterVersion);
                resultSet = statement.executeQuery();
            } catch (SQLException e) {
                close();
                throw e;
            }
        }

        @Override
        public void foreach(Consumer<? super E> consumer) {
            if (iterating) {
                throw new IllegalStateException("Iteration has already been done");
            }
            iterating = true;
            try {
                while (resultSet.next() && !stop) {
                    String type = schema.readEventType(resultSet);
                    int payloadVersion = schema.readEventPayloadVersion(resultSet);
                    E event = serialization.deserialize(payloadVersion, schema.readEventPayload(resultSet), type);
                    if (event != null) {
                        consumer.accept(event);
                    } else {
                        long entityVersion = schema.readEntityVersion(resultSet);

                        if (isStrict()) {
                            throw new IllegalArgumentException(entityId + " Could not deserialize event "
                                    + entityVersion);
                        } else {
                            logger.error("{} Could not deserialize event {}", entityId, entityVersion);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot access datastore", e);
            }
        }

        @Override
        public <R> R reduce(R initial, BiFunction<R, ? super E, R> reducer) {
            if (iterating) {
                throw new IllegalStateException("Iteration has already been done");
            }
            iterating = true;
            try {
                R result = initial;
                while (resultSet.next() && !stop) {
                    E event = serialization.deserialize(schema.readEventPayloadVersion(resultSet),
                        schema.readEventPayload(resultSet), schema.readEventType(resultSet));
                    result = reducer.apply(result, event);
                }
                return result;
            } catch (SQLException e) {
                throw new IllegalStateException("Cannot access datastore", e);
            }
        }

        @Override
        public void stop() {
            stop = true;
        }

        @Override
        public void close() {
            cleanup(resultSet);
            cleanup(statement);
            cleanup(connection);
        }

        protected void cleanup(AutoCloseable resource) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception e) {
                    logger.warn("Suppressing cleanup exception", e);
                }
            }
        }
    }

}
