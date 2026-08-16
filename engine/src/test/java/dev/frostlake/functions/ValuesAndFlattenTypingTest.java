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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A VALUES relation's column is declared like a set operation's — the fold of its rows' expression
 * types, a bare NULL row contributing nothing — and tagged by its rows' interval; FLATTEN's SEQ and
 * INDEX are NUMBER(38,0), its KEY and PATH a VARCHAR the plan spells bare, its VALUE and THIS
 * VARIANT; SPLIT_TO_TABLE's SEQ and INDEX are NUMBER(38,0) and its VALUE the bare VARCHAR. A table
 * built over any of them declares those types, the bare VARCHAR at its full width.
 */
public class ValuesAndFlattenTypingTest extends BaseDatabaseTest {

    private void assertCells(final String sql, final String... expected) {
        final Row values = engine.executeQuery(sql).getRows().get(0);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], String.valueOf(values.getValue(i)), sql + " cell " + (i + 1));
        }
    }

    @Test
    public void aValuesColumnIsTheFoldOfItsRows() {
        assertCells("SELECT SYSTEM$TYPEOF(n) FROM (VALUES (1), (100000)) AS v(n) LIMIT 1", "NUMBER(6,0)[SB4]");
        assertCells("SELECT SYSTEM$TYPEOF($1), SYSTEM$TYPEOF(column1) FROM VALUES (1), (2), (3) LIMIT 1",
            "NUMBER(1,0)[SB1]", "NUMBER(1,0)[SB1]");
        assertCells("SELECT SYSTEM$TYPEOF(SUM($1)) FROM VALUES (1), (2), (3) LIMIT 1", "NUMBER(13,0)[SB8]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (1.5), (2)) AS t(v) LIMIT 1", "NUMBER(2,1)[SB1]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (1.5), (200.25)) AS t(v) LIMIT 1", "NUMBER(5,2)[SB4]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES ('a'), ('bb')) AS t(v) LIMIT 1", "VARCHAR(2)[LOB]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (NULL), (1)) AS t(v) LIMIT 1", "NUMBER(1,0)[SB1]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (1), (NULL)) AS t(v) LIMIT 1", "NUMBER(1,0)[SB1]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (NULL)) AS t(v) LIMIT 1", "NULL[LOB]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (TRUE), (FALSE)) AS t(v) LIMIT 1", "BOOLEAN[SB1]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (CURRENT_DATE)) AS t(v) LIMIT 1", "DATE[SB4]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES ('2020-01-01'::DATE), (CURRENT_DATE)) AS t(v) LIMIT 1", "DATE[SB4]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (1::NUMBER(10,2)), (2)) AS t(v) LIMIT 1", "NUMBER(10,2)[SB8]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (1)) AS t(v) LIMIT 1", "NUMBER(1,0)[SB1]");
        assertCells("SELECT SYSTEM$TYPEOF(v), SYSTEM$TYPEOF(w) FROM (VALUES (1, 'x'), (22, 'yyy')) AS t(v, w) LIMIT 1",
            "NUMBER(2,0)[SB1]", "VARCHAR(3)[LOB]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (-1), (99)) AS t(v) LIMIT 1", "NUMBER(2,0)[SB1]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (1 + 1), (2 * 3)) AS t(v) LIMIT 1", "NUMBER(2,0)[SB1]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (12345678901234567890123)) AS t(v) LIMIT 1", "NUMBER(23,0)[SB16]");
        assertCells("SELECT SYSTEM$TYPEOF(v + 1) FROM (VALUES (1), (100000)) AS t(v) LIMIT 1", "NUMBER(7,0)[SB4]");
        assertCells("SELECT SYSTEM$TYPEOF(v) FROM (VALUES (1), ('a')) AS t(v) LIMIT 1", "NUMBER(18,5)[SB8]");
        engine.execute("CREATE TABLE tv AS SELECT * FROM (VALUES (1, 'a'), (100000, 'bb')) AS t(v, w)");
        final ResultSet described = engine.executeQuery("DESC TABLE tv");
        assertEquals("NUMBER(6,0)", String.valueOf(described.getRows().get(0).getValue(1)));
        assertEquals("VARCHAR(2)", String.valueOf(described.getRows().get(1).getValue(1)));
    }

    @Test
    public void theTableFunctionsDeclareTheirOutputColumns() {
        assertCells("SELECT SYSTEM$TYPEOF(INDEX), SYSTEM$TYPEOF(SEQ), SYSTEM$TYPEOF(KEY), SYSTEM$TYPEOF(PATH), "
            + "SYSTEM$TYPEOF(VALUE), SYSTEM$TYPEOF(THIS) FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(1,2))) LIMIT 1",
            "NUMBER(38,0)[SB16]", "NUMBER(38,0)[SB16]", "VARCHAR[LOB]", "VARCHAR[LOB]", "VARIANT[LOB]", "VARIANT[LOB]");
        assertCells("SELECT SYSTEM$TYPEOF(INDEX), SYSTEM$TYPEOF(KEY) FROM TABLE(FLATTEN(OBJECT_CONSTRUCT('a', 1))) LIMIT 1",
            "NUMBER(38,0)[SB16]", "VARCHAR[LOB]");
        assertCells("SELECT SYSTEM$TYPEOF(f.index), SYSTEM$TYPEOF(f.value) FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(1,2))) f LIMIT 1",
            "NUMBER(38,0)[SB16]", "VARIANT[LOB]");
        assertCells("SELECT SYSTEM$TYPEOF(VALUE::NUMBER(10,0)) FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(1,2))) LIMIT 1",
            "NUMBER(10,0)[SB8]");
        assertCells("SELECT SYSTEM$TYPEOF(SEQ), SYSTEM$TYPEOF(INDEX), SYSTEM$TYPEOF(VALUE) FROM TABLE(SPLIT_TO_TABLE('a,b', ',')) LIMIT 1",
            "NUMBER(38,0)[SB16]", "NUMBER(38,0)[SB16]", "VARCHAR[LOB]");
        engine.execute("CREATE TABLE tf AS SELECT seq, key, path, index, value, this FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(1,2)))");
        final ResultSet described = engine.executeQuery("DESC TABLE tf");
        final String[] expected = {"NUMBER(38,0)", "VARCHAR(16777216)", "VARCHAR(16777216)", "NUMBER(38,0)", "VARIANT", "VARIANT"};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], String.valueOf(described.getRows().get(i).getValue(1)), "column " + (i + 1));
        }
    }
}
