/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A privacy policy and a storage lifecycle policy, and the tables that hold them, survive a snapshot: an engine's
 * copy lists, describes and writes them as the engine did.
 */
public class PrivacyLifecyclePolicySnapshotTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE plp_db");
        engine.execute("USE SCHEMA plp_db.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private static String single(final DatabaseEngine on, final String sql) {
        final ResultSet rs = on.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            for (int c = 0; c < rs.getColumns().size(); c++) {
                out.append(c == 0 ? "" : "|").append(row.getValue(c));
            }
        }
        return out.toString();
    }

    @Test
    public void thePoliciesAndTheirAttachmentsSurviveACopy() throws Exception {
        engine.execute("CREATE PRIVACY POLICY pv AS () RETURNS PRIVACY_BUDGET -> NO_PRIVACY_POLICY() COMMENT = 'pc'");
        engine.execute("CREATE STORAGE LIFECYCLE POLICY sl AS (a INT, b VARCHAR) RETURNS BOOLEAN -> a > 0 "
            + "ARCHIVE_TIER = COLD ARCHIVE_FOR_DAYS = 180 COMMENT = 'sc'");
        engine.execute("CREATE TABLE t (a INT, b VARCHAR)");
        engine.execute("ALTER TABLE t ADD PRIVACY POLICY pv ENTITY KEY (a)");
        engine.execute("ALTER TABLE t ADD STORAGE LIFECYCLE POLICY sl ON (a, b)");
        final String policyDdl = single(engine, "SELECT GET_DDL('POLICY', 'sl')");
        final String tableDdl = single(engine, "SELECT GET_DDL('TABLE', 't')");
        final DatabaseEngine copy = engine.cloneInstance();
        try {
            copy.execute("USE SCHEMA plp_db.PUBLIC");
            assertEquals("PV|()|PRIVACY_BUDGET|NO_PRIVACY_POLICY()", single(copy, "DESCRIBE PRIVACY POLICY pv"));
            assertEquals("SL|(A NUMBER, B VARCHAR)|BOOLEAN|a > 0|180|COLD",
                single(copy, "DESCRIBE STORAGE LIFECYCLE POLICY sl"));
            assertEquals(policyDdl, single(copy, "SELECT GET_DDL('POLICY', 'sl')"));
            assertEquals(tableDdl, single(copy, "SELECT GET_DDL('TABLE', 't')"));
            assertEquals("PV|PRIVACY_POLICY|[ \"A\" ] ;; SL|STORAGE_LIFECYCLE_POLICY|[ \"A\", \"B\" ]",
                single(copy, "SELECT LISTAGG(POLICY_NAME || '|' || POLICY_KIND || '|' || REF_ARG_COLUMN_NAMES, "
                    + "' ;; ') WITHIN GROUP (ORDER BY POLICY_NAME) FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES("
                    + "REF_ENTITY_NAME => 'PLP_DB.PUBLIC.T', REF_ENTITY_DOMAIN => 'TABLE'))"));
        } finally {
            copy.shutdown();
        }
    }
}
