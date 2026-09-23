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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A table read twice in one join is the same table under both aliases: the second alias's columns keep
 * their declared types and their storage tags, whatever the join kind and however many times the table
 * is repeated, and a value typed by that column — a COALESCE's fallback — is presented at its scale.
 * Live-verified.
 */
public class SelfJoinColumnTypeTest extends BaseDatabaseTest {

    private void createTable() {
        engine.execute("CREATE OR REPLACE TABLE vf2 (n NUMBER(10,2), s VARCHAR(5), d DATE)");
        engine.execute("INSERT INTO vf2 VALUES (1.5, 'a', '2024-01-01'), (2.25, 'bb', '2024-01-02')");
    }

    /** The one cell of the first row, as text. */
    private String first(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** Every join kind keeps the second alias typed and tagged. */
    @Test
    public void everyJoinKindTypesTheSecondAlias() {
        createTable();
        final String tag = "NUMBER(10,2)[SB2]";
        assertEquals(tag, first("SELECT SYSTEM$TYPEOF(b.n) FROM vf2 a JOIN vf2 b ON a.n = b.n LIMIT 1"));
        assertEquals(tag, first("SELECT SYSTEM$TYPEOF(b.n) FROM vf2 a LEFT JOIN vf2 b ON a.n = b.n LIMIT 1"));
        assertEquals(tag, first("SELECT SYSTEM$TYPEOF(b.n) FROM vf2 a FULL JOIN vf2 b ON a.n = b.n LIMIT 1"));
        assertEquals(tag, first("SELECT SYSTEM$TYPEOF(b.n) FROM vf2 a RIGHT JOIN vf2 b ON a.n = b.n LIMIT 1"));
        assertEquals(tag, first("SELECT SYSTEM$TYPEOF(b.n) FROM vf2 a CROSS JOIN vf2 b LIMIT 1"));
        assertEquals(tag, first("SELECT SYSTEM$TYPEOF(b.n) FROM vf2 a, vf2 b LIMIT 1"));
        assertEquals(tag, first("SELECT SYSTEM$TYPEOF(b.n) FROM vf2 a JOIN vf2 b USING (n) LIMIT 1"));
        assertEquals(tag, first("SELECT SYSTEM$TYPEOF(b.n) FROM vf2 JOIN vf2 b ON vf2.n = b.n LIMIT 1"));
    }

    /** Every column of the second alias keeps its type, and so does a third alias. */
    @Test
    public void everyColumnAndEveryRepetitionKeepsItsType() {
        createTable();
        assertEquals("VARCHAR(5)[LOB]", first("SELECT SYSTEM$TYPEOF(b.s) FROM vf2 a JOIN vf2 b ON a.n = b.n LIMIT 1"));
        assertEquals("DATE[SB4]", first("SELECT SYSTEM$TYPEOF(b.d) FROM vf2 a JOIN vf2 b ON a.n = b.n LIMIT 1"));
        assertEquals("NUMBER(10,2)[SB2]", first("SELECT SYSTEM$TYPEOF(c.n) FROM vf2 a JOIN vf2 b ON a.n = b.n"
            + " JOIN vf2 c ON b.n = c.n LIMIT 1"));
    }

    /** Expressions over the second alias's column are typed from it. */
    @Test
    public void expressionsOverTheColumnAreTypedFromIt() {
        createTable();
        assertEquals("NUMBER(11,2)[SB2]", first("SELECT SYSTEM$TYPEOF(b.n + 1) FROM vf2 a JOIN vf2 b ON a.n = b.n LIMIT 1"));
        assertEquals("NUMBER(10,2)[SB2]", first("SELECT SYSTEM$TYPEOF(MAX(b.n)) FROM vf2 a JOIN vf2 b ON a.n = b.n"));
    }

    /** A COALESCE falling back past the column presents the fallback at the column's scale. */
    @Test
    public void aFallbackTakesTheColumnsScale() {
        createTable();
        assertEquals("3000.50", first("SELECT COALESCE(b.n, 3000.5) FROM vf2 a LEFT JOIN vf2 b ON a.n = b.n + 100"
            + " ORDER BY 1 LIMIT 1"));
        assertEquals("1.50", first("SELECT COALESCE(b.n, 3000.5) FROM vf2 a LEFT JOIN vf2 b ON a.n = b.n ORDER BY 1 LIMIT 1"));
    }
}
