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

package dev.frostlake.ddl;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SET NOT NULL looks at the rows. Snowflake documents that it fails over a column holding NULLs, changing nothing,
 * and that a semi-structured column takes it only while the table is empty. The flag used to flip over any data,
 * so a column reported NOT NULL while it held NULLs.
 */
public class SetNotNullChecksTheRowsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE nn_db");
        engine.execute("USE SCHEMA nn_db.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String nullability(final String table) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT COLUMN_NAME, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE TABLE_NAME = '" + table + "' ORDER BY ORDINAL_POSITION").getRows()) {
            out.append(out.length() > 0 ? " " : "").append(row.getValue(0)).append('=').append(row.getValue(1));
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void aColumnHoldingNullsKeepsItsNullability() {
        engine.execute("CREATE TABLE t (a VARCHAR NULL, k INT)");
        engine.execute("INSERT INTO t VALUES (NULL, 1)");
        assertTrue(refusal("ALTER TABLE t ALTER COLUMN a SET NOT NULL").contains("contains NULL values"));
        assertTrue(refusal("ALTER TABLE t ALTER (COLUMN k SET NOT NULL, COLUMN a NOT NULL)")
            .contains("contains NULL values"));
        assertEquals("A=YES K=YES", nullability("T"), "a refused list changes nothing, its first item included");

        engine.execute("UPDATE t SET a = 'v'");
        engine.execute("ALTER TABLE t ALTER COLUMN a SET NOT NULL");
        assertEquals("A=NO K=YES", nullability("T"));
    }

    @Test
    public void aSemiStructuredColumnTakesNotNullOnlyWhileTheTableIsEmpty() {
        engine.execute("CREATE TABLE s (o OBJECT, v VARIANT)");
        engine.execute("ALTER TABLE s ALTER COLUMN o SET NOT NULL");
        engine.execute("INSERT INTO s SELECT OBJECT_CONSTRUCT('x', 1), PARSE_JSON('1')");
        assertTrue(refusal("ALTER TABLE s ALTER COLUMN v SET NOT NULL").contains("table is not empty"));
        assertEquals("O=NO V=YES", nullability("S"));
    }
}
