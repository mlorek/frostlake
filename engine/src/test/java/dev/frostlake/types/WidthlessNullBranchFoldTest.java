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

package dev.frostlake.types;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A conditional that folds a string of no width beside a NULL branch stays widthless, where a sized string beside
 * one widens to the length nothing bounds: {@code IFF(TRUE, NULL, NULL::VARCHAR)} is a bare VARCHAR while
 * {@code IFF(TRUE, NULL, NULL::VARCHAR(5))} is VARCHAR(134217728). A table built over either stores
 * VARCHAR(16777216). Every cell is live-verified.
 */
public class WidthlessNullBranchFoldTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (s VARCHAR(5), v VARCHAR, n INT)");
        engine.execute("INSERT INTO t VALUES ('ab', 'xyz', 1), ('cde', 'q', 2)");
    }

    /** The first row's first cell. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aWidthlessFoldBesideANullStaysWidthless() {
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, NULL::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL::VARCHAR, NULL))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE(NULL, NULL::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE(NULL, NULL, NULL::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NVL(NULL, NULL::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFNULL(NULL, NULL::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CASE WHEN TRUE THEN NULL::VARCHAR END)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CASE WHEN TRUE THEN NULL::VARCHAR ELSE NULL END)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(DECODE(1, 1, NULL, NULL::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, NULL::STRING))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, NULL::TEXT))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, 'abc'::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, TO_VARCHAR(NULL)))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, s::VARCHAR)) FROM t", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(n = 1, NULL, NULL::VARCHAR)) FROM t", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CASE WHEN n = 1 THEN NULL::VARCHAR END) FROM t", "VARCHAR[LOB]"},
        });
    }

    @Test
    public void aSizedFoldBesideANullWidensToTheUnknownLength() {
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, NULL::VARCHAR(5)))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, 'abc'))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, NULL::CHAR))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, v)) FROM t", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, s, NULL::VARCHAR)) FROM t", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL::VARCHAR, 'abc'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL::INT, NULL))", "NUMBER(38,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, NULL, NULL::BINARY))", "BINARY[LOB]"},
        });
    }

    @Test
    public void aTableOverEitherFoldStoresTheFullWidth() {
        engine.execute("CREATE TABLE c1 AS SELECT IFF(TRUE, NULL, NULL::VARCHAR) AS c,"
            + " COALESCE(NULL, NULL::VARCHAR) AS f, CASE WHEN TRUE THEN NULL::VARCHAR END AS g,"
            + " IFF(TRUE, NULL, 'abc') AS e");
        assertEquals("C:TEXT:16777216 F:TEXT:16777216 G:TEXT:16777216 E:TEXT:16777216",
            answer("SELECT LISTAGG(column_name || ':' || data_type || ':'"
                + " || COALESCE(character_maximum_length::VARCHAR, 'null'), ' ')"
                + " WITHIN GROUP (ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name = 'C1'"));
    }
}
