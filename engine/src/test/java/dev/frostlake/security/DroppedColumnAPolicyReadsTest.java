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

package dev.frostlake.security;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ALTER TABLE ... DROP COLUMN keeps a column a policy still reads: a column in the ON list of the table's row access
 * policy, or one a conditional masking policy reads through USING while the column it masks stays. Dropped, the
 * column left the policy naming nothing, and every read of the table failed on it (FL-440). Neither the rule nor
 * the account's sentence is documented or measured, so the refusal is the conservative choice; the column goes once
 * the policy is detached.
 */
public class DroppedColumnAPolicyReadsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE dc_db");
        engine.execute("USE SCHEMA dc_db.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? "; " : "").append(row.getValues());
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return refused.getMessage();
        }
    }

    @Test
    public void aColumnTheRowAccessPolicyReadsIsNotDropped() {
        engine.execute("CREATE TABLE t (id INT, ssn STRING, region STRING)");
        engine.execute("INSERT INTO t VALUES (1, '111', 'EU'), (2, '222', 'US')");
        engine.execute("CREATE ROW ACCESS POLICY rp AS (r STRING) RETURNS BOOLEAN -> r = 'EU'");
        engine.execute("ALTER TABLE t ADD ROW ACCESS POLICY rp ON (region)");
        // A rename is followed, and the renamed column is the one kept.
        engine.execute("ALTER TABLE t RENAME COLUMN region TO reg");
        assertTrue(refusal("ALTER TABLE t DROP COLUMN reg")
            .contains("Cannot drop column 'REG' because it is referenced by row access policy 'DC_DB.PUBLIC.RP'"));
        assertTrue(refusal("ALTER TABLE t DROP COLUMN IF EXISTS nosuch, reg").contains("row access policy"));
        // Nothing went: the table still reads, filtered, with all its columns.
        assertEquals("[1, 111, EU]", rows("SELECT * FROM t"));
        // Another column goes, and the policy's own column does once the policy is detached.
        engine.execute("ALTER TABLE t DROP COLUMN ssn");
        engine.execute("ALTER TABLE t DROP ROW ACCESS POLICY rp");
        engine.execute("ALTER TABLE t DROP COLUMN reg");
        assertEquals("[1]; [2]", rows("SELECT * FROM t ORDER BY id"));
    }

    @Test
    public void aColumnAConditionalMaskingPolicyReadsIsNotDropped() {
        engine.execute("CREATE TABLE m (id INT, ssn STRING, region STRING)");
        engine.execute("INSERT INTO m VALUES (1, '111', 'EU')");
        engine.execute("CREATE MASKING POLICY cmp AS (v STRING, r STRING) RETURNS STRING"
            + " -> CASE WHEN r = 'EU' THEN v ELSE '***' END");
        engine.execute("ALTER TABLE m MODIFY COLUMN ssn SET MASKING POLICY cmp USING (ssn, region)");
        assertTrue(refusal("ALTER TABLE m DROP COLUMN region")
            .contains("Cannot drop column 'REGION' because it is referenced by masking policy"));
        assertEquals("[1, 111, EU]", rows("SELECT * FROM m"));
        // The masked column and the column it reads may go together, and the masked column alone.
        engine.execute("ALTER TABLE m DROP COLUMN ssn, region");
        assertEquals("[1]", rows("SELECT * FROM m"));
    }
}
