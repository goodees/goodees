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

import io.github.goodees.ese.core.store.Serialization;
import io.github.goodees.ese.core.store.SnapshotMetadata;
import io.github.goodees.ese.core.store.SnapshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;


public class JdbcSnapshotStore<S> extends io.github.goodees.ese.core.store.SnapshotStoreWithSerialization<S> {
    private final DataSource ds;
    private final JdbcSchema schema;

    public JdbcSnapshotStore(DataSource ds, JdbcSchema schema, Serialization<S> serialization) {
        super(serialization);
        Objects.requireNonNull(serialization);
        this.ds = ds;
        this.schema = schema;
    }

    @Override
    protected SnapshotRecord retrieveSnapshotRecord(String entityId) {
        try (Connection connection = ds.getConnection();
                PreparedStatement st = schema.selectSnapshot(connection, entityId);
                ResultSet rs = st.executeQuery()) {
            if (rs.next()) {
                SnapshotMetadata header = schema.readSnapshotMetadata(rs);
                return new SnapshotRecord(header, schema.readSnapshotPayload(rs));
            } else {
                return null;
            }
        } catch (SQLException se) {
            logger.error("Cannot read snapshot of {}", entityId, se);
        }
        return null;
    }

    @Override
    protected void storeSnapshotRecord(SnapshotRecord snapshotRecord) {
        if (snapshotRecord != null) {
            SnapshotMetadata sm = snapshotRecord.getHeader();
            String entityId = sm.entityId();
            try (Connection connection = ds.getConnection();
                    PreparedStatement st = schema.selectSnapshot(connection, entityId);
                    ResultSet rs = st.executeQuery();
                    PreparedStatement store = rs.next()
                            ? schema.updateSnapshot(connection, entityId, sm.entityStateVersion(), sm.payloadVersion(),
                                snapshotRecord.getPayload()) : schema.insertSnapshot(connection, entityId,
                                sm.entityStateVersion(), sm.payloadVersion(), snapshotRecord.getPayload())) {
                int result = store.executeUpdate();
                if (result != 1) {
                    logger.error("Snapshot update did not create/update a row for entity {}", entityId);
                }
            } catch (SQLException se) {
                logger.error("Failed to store snapshot for " + entityId, se);
            }
        }

    }
}
