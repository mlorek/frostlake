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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every value a statement WRITES meets the compile-time type match against the column it feeds. Two
 * kinds of value never reached it: one a MERGE reads through a SUBQUERY source, whose columns carried
 * no declared type, and the values of a multi-table INSERT, which never ran the match at all. A BOOLEAN
 * or a TIMESTAMP_TZ was written into a TIMESTAMP_LTZ column.
 */
public class RoutedValueTypeMatchTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE it (ltz TIMESTAMP_LTZ, tz TIMESTAMP_TZ, n INT, s VARCHAR)");
        engine.execute("INSERT INTO it SELECT '2026-08-13 12:34:56'::TIMESTAMP_LTZ, "
            + "'2026-08-13 12:34:56 +02:00'::TIMESTAMP_TZ, 1, 'a'");
    }

    /** The message of the refusal a statement raises. */
    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                final ResultSet rs = engine.executeQuery(sql);
                while (rs.next()) {
                    continue;
                }
            }
        }).getMessage().replace("\n", " ");
    }

    /** A value read through a MERGE's subquery source is typed by that source's column. */
    @Test
    public void aMergeTypesItsSubquerySourcesColumns() {
        assertEquals("SQL compilation error: Expression type does not match column data type, "
            + "expecting TIMESTAMP_LTZ(9) but got BOOLEAN for column LTZ",
            refusal("MERGE INTO it USING (SELECT TRUE AS x) s ON TRUE "
                + "WHEN MATCHED THEN UPDATE SET ltz = s.x"));
        assertEquals("SQL compilation error: Expression type does not match column data type, "
            + "expecting TIMESTAMP_LTZ(9) but got TIMESTAMP_TZ(9) for column LTZ",
            refusal("MERGE INTO it USING (SELECT '2026-08-13 12:34:56 +02:00'::TIMESTAMP_TZ AS x) s "
                + "ON TRUE WHEN MATCHED THEN UPDATE SET ltz = s.x"),
            "the zoned flavours are different families, not one");
    }

    /** A MERGE's INSERT branch types its values the same way. */
    @Test
    public void aMergeInsertBranchTypesThemToo() {
        assertEquals("SQL compilation error: Expression type does not match column data type, "
            + "expecting TIMESTAMP_LTZ(9) but got BOOLEAN for column LTZ",
            refusal("MERGE INTO it USING (SELECT TRUE AS x) s ON FALSE "
                + "WHEN NOT MATCHED THEN INSERT (ltz) VALUES (s.x)"));
    }

    /** A value of a matching family still goes through. */
    @Test
    public void aMatchingValueStillWrites() {
        engine.execute("MERGE INTO it USING (SELECT '2026-08-13 12:34:56'::TIMESTAMP_LTZ AS x) s "
            + "ON TRUE WHEN MATCHED THEN UPDATE SET ltz = s.x");
        engine.execute("MERGE INTO it USING (SELECT 1 AS x) s ON TRUE WHEN MATCHED THEN UPDATE SET s = s.x");
        final ResultSet rs = engine.executeQuery("SELECT s FROM it");
        rs.next();
        assertEquals("1", String.valueOf(rs.getValue(0)), "a number still writes into a VARCHAR");
    }

    /** INSERT ALL and INSERT FIRST type each INTO's values against the column it feeds. */
    @Test
    public void aMultiTableInsertTypesItsValues() {
        assertEquals("SQL compilation error: Expression type does not match column data type, "
            + "expecting TIMESTAMP_LTZ(9) but got BOOLEAN for column LTZ",
            refusal("INSERT ALL INTO it (ltz) VALUES (b) SELECT TRUE AS b"));
        assertEquals("SQL compilation error: Expression type does not match column data type, "
            + "expecting TIMESTAMP_LTZ(9) but got TIMESTAMP_TZ(9) for column LTZ",
            refusal("INSERT ALL INTO it (ltz) VALUES (tz) SELECT tz FROM it"));
        assertEquals("SQL compilation error: Expression type does not match column data type, "
            + "expecting TIMESTAMP_LTZ(9) but got TIMESTAMP_TZ(9) for column LTZ",
            refusal("INSERT FIRST WHEN TRUE THEN INTO it (ltz) VALUES (tz) SELECT tz FROM it"));
    }

    /** A source item of several columns is a ROW the column cannot take, on either INSERT shape. */
    @Test
    public void aRowValuedSourceItemIsNamedAsARow() {
        assertEquals("SQL compilation error: Expression type does not match column data type, "
            + "expecting NUMBER(38,0) but got ROW(NUMBER(1,0), NUMBER(1,0)) for column N",
            refusal("INSERT ALL INTO it (n) SELECT (SELECT 1, 2)"));
        assertEquals("SQL compilation error: Expression type does not match column data type, "
            + "expecting NUMBER(38,0) but got ROW(NUMBER(1,0), NUMBER(1,0)) for column N",
            refusal("INSERT INTO it (n) SELECT (SELECT 1, 2)"),
            "the single-table shape already read it that way");
    }

    /** A projected bare NULL carries no type, and an untyped NULL is assignable to every column. */
    @Test
    public void anUntypedNullIsAssignableAnywhere() {
        engine.execute("MERGE INTO it USING (SELECT NULL AS x) s ON TRUE "
            + "WHEN MATCHED THEN UPDATE SET ltz = s.x");
        engine.execute("INSERT ALL INTO it (ltz) VALUES (c) SELECT NULL AS c");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM it WHERE ltz IS NULL");
        rs.next();
        assertEquals("2", String.valueOf(rs.getValue(0)));
    }

    /** A VARCHAR is no VARIANT: the semi-structured columns refuse it as any other family would. */
    @Test
    public void aVarcharIsNoVariant() {
        engine.execute("CREATE OR REPLACE TABLE vt (v VARIANT, o OBJECT, s VARCHAR)");
        assertEquals("SQL compilation error: Expression type does not match column data type, "
            + "expecting VARIANT but got VARCHAR(3) for column V",
            refusal("INSERT ALL INTO vt (v) VALUES (c) SELECT 'abc' AS c"));
        assertEquals("SQL compilation error: Expression type does not match column data type, "
            + "expecting VARIANT but got VARCHAR(3) for column V",
            refusal("MERGE INTO vt USING (SELECT 'abc' AS x) s ON TRUE "
                + "WHEN MATCHED THEN UPDATE SET v = s.x"));
        assertEquals("SQL compilation error: Expression type does not match column data type, "
            + "expecting OBJECT but got VARCHAR(3) for column O",
            refusal("MERGE INTO vt USING (SELECT 'abc' AS x) s ON TRUE "
                + "WHEN MATCHED THEN UPDATE SET o = s.x"));
    }

    /** A number writes into a VARIANT, and a VARIANT into a VARCHAR: those families do meet. */
    @Test
    public void theFamiliesThatDoMeetStillWrite() {
        engine.execute("CREATE OR REPLACE TABLE vt (v VARIANT, s VARCHAR)");
        engine.execute("INSERT INTO vt SELECT TO_VARIANT('x'), 'text'");
        engine.execute("INSERT ALL INTO vt (v) VALUES (c) SELECT 1 AS c");
        engine.execute("MERGE INTO vt USING (SELECT TO_VARIANT('q') AS x) s ON TRUE "
            + "WHEN MATCHED THEN UPDATE SET s = s.x");
        engine.execute("MERGE INTO vt USING (SELECT 1 AS x) s ON TRUE WHEN MATCHED THEN UPDATE SET s = s.x");
    }

    /** A multi-table INSERT whose values do match still writes them. */
    @Test
    public void aMatchingMultiTableInsertStillWrites() {
        engine.execute("INSERT ALL INTO it (n) VALUES (b) SELECT 5 AS b");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM it WHERE n = 5");
        rs.next();
        assertEquals("1", String.valueOf(rs.getValue(0)));
    }
}
