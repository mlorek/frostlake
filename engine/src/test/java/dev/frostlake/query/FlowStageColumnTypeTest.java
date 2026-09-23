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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code ->>} chain's {@code $n} is a relation in its own right, so the types the prior stage
 * REPORTS are the types its columns are DECLARED with. Frostlake built that relation without them, so
 * every column of a listing read through a stage typed as the untyped NULL tag — including the ones a
 * SHOW listing declares precisely, like {@code created_on}.
 *
 * <p>What does not survive the stage is the value INTERVAL: a NUMBER(38,0) that came through a
 * {@code $n} carries its family's full storage width, SB16, not the narrow SB1 the plan had for it on
 * the other side.
 */
public class FlowStageColumnTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ft (a INT, b VARCHAR(7))");
        engine.execute("INSERT INTO ft VALUES (1, 'x')");
    }

    /** The first cell of the first row. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** A SHOW listing's columns type as the listing declares them. */
    @Test
    public void aListingsColumnsKeepTheirDeclaredTypes() {
        assertEquals("TIMESTAMP_LTZ(3)[SB8]",
            answer("SHOW TABLES LIKE 'ft' ->> SELECT SYSTEM$TYPEOF(\"created_on\") FROM $1"));
        assertEquals("VARCHAR(16777216)[LOB]",
            answer("SHOW TABLES LIKE 'ft' ->> SELECT SYSTEM$TYPEOF(\"name\") FROM $1"));
        assertEquals("NUMBER(38,0)[SB16]",
            answer("SHOW TABLES LIKE 'ft' ->> SELECT SYSTEM$TYPEOF(\"rows\") FROM $1"));
        assertEquals("VARCHAR(16777216)[LOB]",
            answer("SHOW TABLES LIKE 'ft' ->> SELECT SYSTEM$TYPEOF(\"is_dynamic\") FROM $1"));
    }

    /** The other listings answer the same way — their own declared types, not a guess. */
    @Test
    public void theOtherListingsDoToo() {
        assertEquals("VARCHAR(16777216)[LOB]", answer(
            "SHOW COLUMNS IN TABLE ft ->> SELECT SYSTEM$TYPEOF(\"data_type\") FROM $1 LIMIT 1"));
        assertEquals("TIMESTAMP_LTZ(3)[SB8]", answer(
            "SHOW GRANTS TO ROLE ACCOUNTADMIN"
                + " ->> SELECT SYSTEM$TYPEOF(\"created_on\") FROM $1 LIMIT 1"));
    }

    /** A SELECT stage carries its own column types — and loses the plan's value interval. */
    @Test
    public void aSelectStageKeepsItsTypesAndDropsTheInterval() {
        assertEquals("NUMBER(38,0)[SB16]",
            answer("SELECT a, b FROM ft ->> SELECT SYSTEM$TYPEOF(a) FROM $1"),
            "SB16, not the SB1 the column has before the stage");
        assertEquals("VARCHAR(7)[LOB]",
            answer("SELECT a, b FROM ft ->> SELECT SYSTEM$TYPEOF(b) FROM $1"),
            "the declared width rides along");
        assertEquals("NUMBER(38,0)[SB16]",
            answer("SELECT a + 1 AS c FROM ft ->> SELECT SYSTEM$TYPEOF(c) FROM $1"));
    }

    /** And it holds through a second stage. */
    @Test
    public void itHoldsThroughASecondStage() {
        assertEquals("TIMESTAMP_LTZ(3)[SB8]", answer(
            "SHOW TABLES LIKE 'ft' ->> SELECT \"created_on\" AS c FROM $1"
                + " ->> SELECT SYSTEM$TYPEOF(c) FROM $1"));
    }

    /** The values themselves were always right, and still are. */
    @Test
    public void theValuesStillRead() {
        assertEquals("true", answer(
            "SHOW TABLES LIKE 'ft' ->> SELECT \"created_on\" <= CURRENT_TIMESTAMP() FROM $1"));
    }
}
