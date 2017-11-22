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
import io.github.goodees.ese.core.store.EventStore;
import io.github.goodees.ese.core.store.EventStoreException;
import io.github.goodees.ese.core.store.Serialization;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JdbcEventStore<E> implements EventStore {
    private final DataSource dataSource;
    private final JdbcSchema schema;
    private final Serialization<E> serialization;
    private final TxHandler txHandler;

    public JdbcEventStore(DataSource dataSource, JdbcSchema schema, Serialization<E> serialization) {
        this(dataSource, schema, serialization, CONTAINER_HANDLER);
    }

    public JdbcEventStore(DataSource dataSource, JdbcSchema schema, Serialization<E> serialization, TxHandler handler) {
        this.dataSource = dataSource;
        this.schema = schema;
        this.serialization = serialization;
        this.txHandler = handler;

    }

    protected boolean isSupportedEvent(Event event) {
        return serialization.toSerializable(event) != null;
    }

    protected int determinePayloadVersion(Event event) throws EventStoreException {
        return serialization.payloadVersion(checkCast(event));
    }

    protected E checkCast(Event event) throws EventStoreException {
        E cast = serialization.toSerializable(event);
        if (cast == null) {
            throw EventStoreException.unsupported(event);
        } else {
            return cast;
        }
    }

    protected String serializePayload(Event event) throws EventStoreException {
        return serialization.serialize(checkCast(event));
    }

    @Override
    public void persist(Event event) throws EventStoreException {
        PersistTemplate template = createTemplate();
        template.addEvent(event);
        template.persist();
    }

    @Override
    public void persist(Event... events) throws EventStoreException {
        PersistTemplate template = createTemplate();
        for (Event event : events) {
            template.addEvent(event);
        }
        template.persist();
    }

    @Override
    public void persist(Iterable<? extends Event> events) throws EventStoreException {
        PersistTemplate template = createTemplate();
        for (Event event : events) {
            template.addEvent(event);
        }
        template.persist();
    }

    protected PersistTemplate createTemplate() {
        return new PersistTemplate();
    }

    protected void prepareInsert(PreparedStatement insertEvent, Event event) throws SQLException, EventStoreException {
        schema.prepareInsert(insertEvent, event, determinePayloadVersion(event), serializePayload(event));
    }

    protected class PersistTemplate {
        private List<Event> events = new ArrayList<>();
        private String entityId;
        private long startVersion;
        private long endVersion;

        void addEvent(Event event) throws EventStoreException {
            if (entityId == null) {
                entityId = event.entityId();
                startVersion = event.entityStateVersion() - 1;
            } else if (!entityId.equals(event.entityId())) {
                throw EventStoreException.multlipleEntities(entityId, event);
            } else if (event.entityStateVersion() != endVersion + 1) {
                throw EventStoreException.nonMonotonic(entityId, endVersion + 1, event);
            }

            if (!isSupportedEvent(event)) {
                throw EventStoreException.unsupported(event);
            }
            endVersion = event.entityStateVersion();
            events.add(event);
        }

        public void persist() throws EventStoreException {
            if (events.isEmpty()) {
                return;
            }
            try (Connection connection = txHandler.enroll(dataSource.getConnection());
                    PreparedStatement selectVersion = schema.selectEntityVersion(connection, entityId);
                    ResultSet rs = selectVersion.executeQuery()) {
                checkSourceVersion(connection, rs);
                try (PreparedStatement insertEvent = schema.insertEvent(connection, entityId);
                        PreparedStatement updateVersion = schema.updateEventVersion(connection, entityId, startVersion,
                            endVersion)) {
                    storeEvents(insertEvent);
                    updateVersion(updateVersion);
                    txHandler.commit(connection);
                } catch (SQLException | RuntimeException e) {
                    txHandler.rollback(connection);
                    throw e;
                }
            } catch (SQLException ex) {
                throw EventStoreException.storeFailed(entityId, ex);
            }
        }

        private void updateVersion(PreparedStatement updateVersion) throws SQLException, EventStoreException {
            int result = updateVersion.executeUpdate();
            if (result != 1) {
                throw EventStoreException.optimisticLock(entityId, startVersion);
            }
        }

        private void storeEvents(PreparedStatement insertEvent) throws SQLException, EventStoreException {
            for (Event event : events) {
                prepareInsert(insertEvent, event);
                insertEvent.addBatch();
            }
            insertEvent.executeBatch();
        }

        private void checkSourceVersion(Connection connection, ResultSet rs) throws SQLException, EventStoreException {
            if (!rs.next()) {
                // no entity version - create a new one.
                try (PreparedStatement createVersion = schema.createEntityVersion(connection, entityId, startVersion)) {
                    createVersion.executeUpdate();
                }
            } else {
                long version = schema.readEntityVersion(rs);
                if (version != startVersion) {
                    throw EventStoreException.optimisticLock(entityId, version, startVersion);
                }
            }
        }
    }

    interface TxHandler {

        Connection enroll(Connection connection) throws SQLException;

        void commit(Connection connection) throws SQLException;

        void rollback(Connection connection) throws SQLException;
    }

    private static TxHandler CONTAINER_HANDLER = new TxHandler() {
        @Override
        public Connection enroll(Connection connection) {
            return connection;
        }

        @Override
        public void commit(Connection connection) {
        }

        @Override
        public void rollback(Connection connection) {
        }
    };
}
