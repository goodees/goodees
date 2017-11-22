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
import io.github.goodees.ese.core.store.SnapshotMetadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * JDBC schema for separate tables per entity. Following tables are expected to exist:
 * <ul>
 * <li><em>eventTable</em>(ID, VERSION, TIMESTAMP, TYPE, PAYLOAD_VERSION, PAYLOAD) primary key (ID, VERSION)</li>
 * <li><em>versionTable</em>(ID, VERSION)</li>
 * <li><em>snapshotTable</em>(ID, VERSION, TIMESTAMP, PAYLOAD_VERSION, PAYLOAD) primary key (ID)</li>
 * </ul>
 */
public class DefaultJdbcSchema extends JdbcSchema {

    private final String eventTable;
    private final String versionTable;
    private final String snapshotTable;

    public DefaultJdbcSchema(String eventTable, String versionTable, String snapshotTable) {
        this.eventTable = eventTable;
        this.versionTable = versionTable;
        this.snapshotTable = snapshotTable;
    }

    protected String getEventTable() {
        return eventTable;
    }

    protected String getVersionTable() {
        return versionTable;
    }

    protected String getSnapshotTable() {
        return snapshotTable;
    }

    @Override
    protected PreparedStatement selectEntityVersion(Connection connection, String entityId) throws SQLException {
        PreparedStatement st = connection.prepareStatement("SELECT VERSION FROM " + getVersionTable() + " WHERE ID=?");
        st.setString(1, entityId);
        return st;
    }

    @Override
    protected PreparedStatement createEntityVersion(Connection connection, String entityId, long startVersion)
            throws SQLException {
        PreparedStatement st = connection.prepareStatement("INSERT INTO " + getVersionTable()
                + " (ID, VERSION) VALUES (?, ?)");
        st.setString(1, entityId);
        st.setLong(2, startVersion);
        return st;
    }

    @Override
    protected long readEntityVersion(ResultSet rs) throws SQLException {
        return rs.getLong(1);
    }

    @Override
    protected PreparedStatement insertEvent(Connection connection, String entityId) throws SQLException {
        return connection.prepareStatement("INSERT INTO " + getEventTable()
                + " (ID, VERSION, TIMESTAMP, TYPE, PAYLOAD_VERSION, PAYLOAD) VALUES (?,?,?,?,?,?)");
    }

    @Override
    protected PreparedStatement updateEventVersion(Connection connection, String entityId, long startVersion,
            long endVersion) throws SQLException {
        PreparedStatement st = connection.prepareStatement("UPDATE " + getVersionTable()
                + " SET VERSION=? WHERE ID=? AND VERSION=?");
        st.setLong(1, endVersion);
        st.setString(2, entityId);
        st.setLong(3, startVersion);
        return st;
    }

    @Override
    protected void prepareInsert(PreparedStatement insertEvent, Event event, int payloadVersion, String payload)
            throws SQLException {
        insertEvent.setString(1, event.entityId());
        insertEvent.setLong(2, event.entityStateVersion());
        insertEvent.setTimestamp(3, new Timestamp(event.getTimestamp().toEpochMilli()));
        insertEvent.setString(4, event.getType());
        insertEvent.setInt(5, payloadVersion);
        insertEvent.setString(6, payload);
    }

    @Override
    protected PreparedStatement selectEvents(Connection connection, String entityId, long afterVersion)
            throws SQLException {
        PreparedStatement st = connection.prepareStatement("SELECT ID, VERSION, TYPE, PAYLOAD_VERSION, PAYLOAD "
                + "FROM " + getEventTable() + " WHERE ID=? AND VERSION > ? ORDER BY VERSION");
        st.setString(1, entityId);
        st.setLong(2, afterVersion);
        return st;
    }

    @Override
    protected String readEventType(ResultSet rs) throws SQLException {
        return rs.getString(3);
    }

    @Override
    protected int readEventPayloadVersion(ResultSet rs) throws SQLException {
        return rs.getInt(4);
    }

    @Override
    protected String readEventPayload(ResultSet rs) throws SQLException {
        return rs.getString(5);
    }

    @Override
    protected PreparedStatement selectSnapshot(Connection connection, String entityId) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("SELECT ID, VERSION, TIMESTAMP, PAYLOAD_VERSION, PAYLOAD FROM "
                + getSnapshotTable() + " WHERE id = ?");
        ps.setString(1, entityId);
        return ps;
    }

    @Override
    protected PreparedStatement updateSnapshot(Connection connection, String entityId, long stateVersion,
            int payloadVersion, String payload) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("UPDATE " + getSnapshotTable()
                + " SET VERSION=?, TIMESTAMP=?, PAYLOAD_VERSION=?, PAYLOAD=? WHERE ID = ?");
        ps.setLong(1, stateVersion);
        ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
        ps.setInt(3, payloadVersion);
        ps.setString(4, payload);
        ps.setString(5, entityId);
        return ps;
    }

    @Override
    protected PreparedStatement insertSnapshot(Connection connection, String entityId, long stateVersion,
            int payloadVersion, String payload) throws SQLException {
        PreparedStatement ps = connection.prepareStatement("INSERT INTO " + getSnapshotTable()
                + "(id, version, timestamp, payload_version, payload) values (?, ?, ?, ?, ?)");
        ps.setString(1, entityId);
        ps.setLong(2, stateVersion);
        ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
        ps.setInt(4, payloadVersion);
        ps.setString(5, payload);
        return ps;
    }

    @Override
    protected SnapshotMetadata readSnapshotMetadata(ResultSet rs) throws SQLException {
        return new SnapshotMetadata.Default(rs.getString(1), Instant.ofEpochMilli(rs.getTimestamp(3).getTime()),
            rs.getInt(4), rs.getInt(2));
    }

    @Override
    protected String readSnapshotPayload(ResultSet rs) throws SQLException {
        return rs.getString(5);
    }
}
