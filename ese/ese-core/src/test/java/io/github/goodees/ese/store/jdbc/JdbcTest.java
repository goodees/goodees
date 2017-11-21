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

import org.h2.jdbcx.JdbcDataSource;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;


public class JdbcTest {
    protected static JdbcDataSource ds;
    protected static JdbcTemplate template;
    @Rule
    public TestName testName = new TestName();
    protected JdbcEventStore<JdbcTestEvent> eventStore;
    protected DefaultJdbcSchema schema;
    protected TestEventSerialization serialization;
    protected JdbcEventLog<JdbcTestEvent> eventLog;
    protected JdbcSnapshotStore<JdbcTestSnapshot> snapshotStore;
    protected TestSnapshotSerialization snapshotSerialization;

    @BeforeClass
    public static void initDb() throws SQLException {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:estest;DB_CLOSE_DELAY=-1;MVCC=true");
        ds.setUser("sa");
        try (Connection con = ds.getConnection()) {
            executeSql(
                con,
                "create table event (ID varchar, VERSION int, TIMESTAMP timestamp, TYPE varchar, PAYLOAD_VERSION int, PAYLOAD clob, primary key (ID, VERSION))",
                " create table version (ID varchar, VERSION int)",
                " create table snapshot(ID varchar primary key, VERSION int, TIMESTAMP timestamp, PAYLOAD_VERSION int, PAYLOAD clob)",
                // we will not use primary key check to escalate optimistic lock exception in very last step
                "create table event_collision (ID varchar, VERSION int, TIMESTAMP timestamp, TYPE varchar, PAYLOAD_VERSION int, PAYLOAD clob)");
        }
        template = new JdbcTemplate(ds);
    }

    @AfterClass
    public static void dropDb() throws SQLException {
        try (Connection con = ds.getConnection()) {
            executeSql(con, "drop table event", " drop table version", " drop table snapshot",
                "drop table event_collision");
        }
    }

    static void executeSql(Connection con, String... statements) throws SQLException {
        for (String statement : statements) {
            try (CallableStatement cst = con.prepareCall(statement)) {
                cst.execute();
            }
        }
    }

    @Before
    public void setUp() {
        this.schema = new DefaultJdbcSchema("event", "version", "snapshot");
        this.serialization = new TestEventSerialization();
        this.eventLog = new JdbcEventLog<>(ds, schema, serialization, false);
        this.eventStore = new JdbcEventStore<>(ds, schema, serialization);
        this.snapshotSerialization = new TestSnapshotSerialization();
        this.snapshotStore = new JdbcSnapshotStore<>(ds, schema, snapshotSerialization);
    }

    protected String name() {
        return testName.getMethodName();
    }

    protected void assertDb(int expected, String sql, Object... params) {
        assertEquals(expected, template.queryForInt(sql, params));
    }
}
