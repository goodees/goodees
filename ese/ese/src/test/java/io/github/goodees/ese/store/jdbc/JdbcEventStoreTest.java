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

import io.github.goodees.ese.EventHeader;
import io.github.goodees.ese.store.EventStoreException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


public class JdbcEventStoreTest extends JdbcTest {
    private static final Logger logger = LoggerFactory.getLogger(JdbcEventStoreTest.class);
    @Rule
    public ErrorCollector collector = new ErrorCollector();

    @Test
    public void events_for_new_entity_are_persisted() throws EventStoreException {
        eventStore.persist(new JdbcTestEvent(name(), 1, 100), new JdbcTestEvent(name(), 2, 200));
        assertDb(2, "select count(*) from event where id = ?", name());
        assertDb(2, "select version from version where id = ?", name());
    }

    @Test
    public void events_for_existing_entity_are_persisted() throws EventStoreException {
        eventStore.persist(new JdbcTestEvent(name(), 1, 100), new JdbcTestEvent(name(), 2, 200));
        eventStore.persist(new JdbcTestEvent(name(), 3, 100), new JdbcTestEvent(name(), 4, 200));
        assertDb(4, "select count(*) from event where id = ?", name());
        assertDb(4, "select version from version where id = ?", name());
    }

    @Test
    public void mixing_entities_fails() {
        try {
            eventStore.persist(new JdbcTestEvent(name(), 1, 100), new JdbcTestEvent(name() + "!", 2, 200));
            fail("should have failed");
        } catch (EventStoreException e) {
            assertEquals(EventStoreException.Fault.PROGRAMMATIC_ERROR, e.getFault());
            assertDb(0, "select count(*) from event where id like 'mixing%'");
        }
    }

    @Test
    public void skipping_versions_fails() {
        try {
            eventStore.persist(new JdbcTestEvent(name(), 1, 100), new JdbcTestEvent(name(), 3, 200));
            fail("should have failed");
        } catch (EventStoreException e) {
            assertEquals(EventStoreException.Fault.PROGRAMMATIC_ERROR, e.getFault());
            assertDb(0, "select count(*) from event where id = ?", name());
        }
    }

    @Test
    public void persisting_unsupported_events_fails() {
        try {
            eventStore.persist(new EventHeader(name(), 1));
            fail("should have failed");
        } catch (EventStoreException e) {
            assertEquals(EventStoreException.Fault.PROGRAMMATIC_ERROR, e.getFault());
        }
    }

    @Test
    public void persisting_stale_events_throws_early() {
        try {
            template.update("insert into version (id, version) values(?, 10)", name());
            eventStore.persist(new JdbcTestEvent(name(), 10, 100), new JdbcTestEvent(name(), 11, 200));
            fail("should have failed");
        } catch (EventStoreException e) {
            assertEquals(EventStoreException.Fault.OPTIMISTIC_LOCK, e.getFault());
            assertDb(0, "select count(*) from event where id = ?", name());
            assertDb(10, "select version from version where id = ?", name());
        }
    }

    @Test
    public void persisting_stale_events_fails_optimistic_lock() throws InterruptedException {
        // so how do we make a race condition?
        // basically we will need to inject some latches into JdbcSchema impl
        /*
            THREAD 1                 THREAD 2

            checkSourceVersion
            < release "T1 hasEntityVersion" >
                                     < wait for "T1 hasEntityVersion" >
                                    checkSourceVersion
            persist "10"            persist "20"
                                    < release "T2 has persisted events >
            < wait for "T2 has persisted events">
            updateVersion
            < release "T1 has updated Version" >
                                    < wait for "T1 has updated version" >
                                    updateVersion

            Thread 1 wins, thread 2 fails.

            We do not have PK constraint on events table, so that we'd fail the optimistic lock check, rather than unique
            constraint check for primary key.
            In reality, the runtime may report TX_FAILURE event when in fact the underlying cause is optimistic lock.
         */
        CountDownLatch thread1hasEntityVersion = new CountDownLatch(1);
        CountDownLatch thread2hasPersistedEvents = new CountDownLatch(1);
        CountDownLatch thread1updatedEntityVersion = new CountDownLatch(1);

        JdbcSchema race1 = new DefaultJdbcSchema("event_collision", "version", "snapshot") {
            @Override
            protected long readEntityVersion(ResultSet rs) throws SQLException {
                try {
                    return super.readEntityVersion(rs);
                } finally {
                    logger.info("Thread 1 has read entity version");
                    thread1hasEntityVersion.countDown();
                }
            }

            @Override
            protected PreparedStatement updateEventVersion(Connection connection, String entityId, long startVersion, long endVersion) throws SQLException {
                PreparedStatement delegate = super.updateEventVersion(connection, entityId, startVersion, endVersion);
                return (PreparedStatement)Proxy.newProxyInstance(delegate.getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                        (p,m,a) -> {
                            if ("executeUpdate".equals(m.getName())) {
                                logger.info("Waiting for thread 2 to persist events");
                                thread2hasPersistedEvents.await();
                            }
                            Object result = m.invoke(delegate, a);
                            if ("executeUpdate".equals(m.getName())) {
                                logger.info("Thread 1 updated entity version");
                                thread1updatedEntityVersion.countDown();
                            }
                            return result;
                        });
            }
        };

        JdbcSchema race2 = new DefaultJdbcSchema("event_collision", "version", "snapshot") {
            @Override
            protected long readEntityVersion(ResultSet rs) throws SQLException {
                try {
                    logger.info("Thread 2 waits for thread 1 to read entity version");
                    thread1hasEntityVersion.await();
                } catch (InterruptedException e) {
                   collector.addError(e);
                }
                return super.readEntityVersion(rs);
            }

            @Override
            protected PreparedStatement insertEvent(Connection connection, String entityId) throws SQLException {
                PreparedStatement delegate = super.insertEvent(connection, entityId);
                return (PreparedStatement)Proxy.newProxyInstance(delegate.getClass().getClassLoader(), new Class[]{PreparedStatement.class},
                        (p,m,a) -> {

                            try {
                                return m.invoke(delegate, a);
                            } finally {
                                if (m.getName().equals("executeBatch")) {
                                    logger.info("Thread 2 has persisted events");
                                    thread2hasPersistedEvents.countDown();
                                }
                            }
                        });
            }

            @Override
            protected PreparedStatement updateEventVersion(Connection connection, String entityId, long startVersion, long endVersion) throws SQLException {
                PreparedStatement delegate = super.updateEventVersion(connection, entityId, startVersion, endVersion);
                return (PreparedStatement)Proxy.newProxyInstance(delegate.getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
                        (p,m,a) -> {
                            if ("executeUpdate".equals(m.getName())) {
                                logger.info("Waiting for thread 1 to update entity version");
                                thread1updatedEntityVersion.await();
                            }
                            return m.invoke(delegate, a);
                        });
            }
        };

        // so far we used autoCommit in our tests. Now we simulate actual container, where throwing EventStoreException
        // rolls back the connection.
        JdbcEventStore.TxHandler localTxHandler = new JdbcEventStore.TxHandler() {
            @Override
            public Connection enroll(Connection connection) throws SQLException {
                connection.setAutoCommit(false);
                return connection;
            }

            @Override
            public void commit(Connection connection) throws SQLException {
                connection.commit();
            }

            @Override
            public void rollback(Connection connection) throws SQLException {
                connection.rollback();
            }
        };
        JdbcEventStore<JdbcTestEvent> store1 = new JdbcEventStore<JdbcTestEvent>(ds, race1, serialization, localTxHandler);
        JdbcEventStore<JdbcTestEvent> store2 = new JdbcEventStore<JdbcTestEvent>(ds, race2, serialization, localTxHandler);

        template.update("insert into version (id, version) values (?,1)",name());
        Thread thread1 = new Thread(() -> {
            try {
                store1.persist(new JdbcTestEvent(name(), 2, 10));
            } catch (Exception e) {
                logger.error("Thread 1 failed",e);
                collector.addError(e);
            } finally {
                // release all latches in case we failed:
                thread1hasEntityVersion.countDown();
                thread1updatedEntityVersion.countDown();
            }
        });
        thread1.setName("Thread 1");
        thread1.start();
        try {
            store2.persist(new JdbcTestEvent(name(), 2, 20));
            fail("Should have failed");
        } catch (EventStoreException e) {
            logger.info("Thread 2 got (expected) event store exception",e);
            assertEquals(EventStoreException.Fault.OPTIMISTIC_LOCK, e.getFault());
        }
        thread1.join();
        assertDb(1, "select count(*) from event_collision where id = ?",name());
        assertDb(2, "select version from version where id = ?",name());
        assertDb(1, "select count(*) from event_collision where id = ? and payload like '%|10'", name());
        assertDb(0, "select count(*) from event_collision where id = ? and payload like '%|20'", name());
    }
}
