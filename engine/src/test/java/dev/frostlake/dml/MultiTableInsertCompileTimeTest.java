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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A multi-table INSERT judges its WHEN conditions and INTO … VALUES expressions before any row is routed,
 * against the source query's output columns alone. A name the source does not carry is refused at the
 * place it is written, over an empty source too, and the refusals come in live's order: every INTO's value
 * count, every column list's names, the conditions' names, the values' names, then unknown function names.
 */
public class MultiTableInsertCompileTimeTest extends BaseDatabaseTest {

    private static final String COUNT_2_1 = "Insert value list does not match column list expecting 2 but got 1";

    private void tables() {
        engine.execute("CREATE OR REPLACE TABLE T (x INT)");
        engine.execute("CREATE OR REPLACE TABLE T2 (x INT, y INT)");
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    private void assertInvalid(final String sql, final int position, final String name) {
        assertRefused(sql, "error line 1 at position " + position + "\ninvalid identifier '" + name + "'");
    }

    @Test
    public void aMissingColumnIsRefusedWhereItIsWritten() {
        tables();
        assertInvalid("INSERT FIRST WHEN nosuchcol = 1 THEN INTO T SELECT 1", 18, "NOSUCHCOL");
        assertInvalid("INSERT ALL INTO T VALUES (nosuchcol) SELECT 1 AS x", 26, "NOSUCHCOL");
        assertInvalid("INSERT ALL WHEN x = 1 THEN INTO T VALUES (nosuchcol) SELECT 1 AS x", 42, "NOSUCHCOL");
        assertInvalid("INSERT ALL WHEN x = 1 THEN INTO T ELSE INTO T VALUES (nosuchcol) SELECT 1 AS x", 54, "NOSUCHCOL");
        assertInvalid("INSERT ALL INTO T VALUES (x + nosuchcol) SELECT 1 AS x", 30, "NOSUCHCOL");
        assertInvalid("INSERT ALL INTO T VALUES (ABS(nosuchcol)) SELECT 1 AS x", 30, "NOSUCHCOL");
        assertInvalid("INSERT ALL INTO T VALUES (IFF(TRUE, 1, nosuchcol)) SELECT 1 AS x", 39, "NOSUCHCOL");
        assertRefused("""
            INSERT ALL
              INTO T VALUES (
             nosuchcol) SELECT 1 AS x""",
            "error line 3 at position 1\ninvalid identifier 'NOSUCHCOL'");
        assertRefused("""
            INSERT FIRST
             WHEN
               nosuchcol = 1 THEN INTO T SELECT 1""",
            "error line 3 at position 3\ninvalid identifier 'NOSUCHCOL'");
    }

    /** Nothing is evaluated to judge a name: a branch no row takes, and a row no WHEN matches, are judged too. */
    @Test
    public void everyExpressionIsJudgedBeforeAnyRow() {
        tables();
        assertInvalid("INSERT ALL WHEN x = 2 THEN INTO T VALUES (nosuchcol) SELECT 1 AS x", 42, "NOSUCHCOL");
        assertInvalid("INSERT FIRST WHEN x = 1 THEN INTO T WHEN nosuchcol = 1 THEN INTO T SELECT 1 AS x", 41, "NOSUCHCOL");
        assertInvalid("INSERT ALL WHEN CASE WHEN TRUE THEN TRUE ELSE nosuchcol = 1 END THEN INTO T SELECT 1 AS x",
            46, "NOSUCHCOL");
        assertInvalid("INSERT ALL WHEN FALSE AND nosuchcol = 1 THEN INTO T SELECT 1 AS x", 26, "NOSUCHCOL");
        assertEquals("0", rows("SELECT COUNT(*) FROM T"));
    }

    @Test
    public void anEmptySourceIsRefusedToo() {
        tables();
        assertInvalid("INSERT FIRST WHEN nosuchcol = 1 THEN INTO T SELECT 1 WHERE FALSE", 18, "NOSUCHCOL");
        assertInvalid("INSERT ALL INTO T VALUES (nosuchcol) SELECT 1 AS x WHERE FALSE", 26, "NOSUCHCOL");
        assertInvalid("INSERT FIRST WHEN nosuchcol = 1 THEN INTO T SELECT x FROM T", 18, "NOSUCHCOL");
        assertInvalid("INSERT ALL INTO T VALUES (nosuchcol) SELECT x FROM T", 26, "NOSUCHCOL");
        assertRefused("INSERT ALL INTO T2 VALUES (1) SELECT 1 AS x WHERE FALSE", COUNT_2_1);
        assertRefused("INSERT ALL INTO T2 SELECT 1 AS x WHERE FALSE", COUNT_2_1);
        assertRefused("INSERT ALL WHEN x = 1 THEN INTO T2 VALUES (1) SELECT 1 AS x WHERE FALSE", COUNT_2_1);
        assertRefused("INSERT ALL INTO T VALUES (NOSUCHFN(x)) SELECT 1 AS x WHERE FALSE",
            "Unknown function NOSUCHFN.");
        assertRefused("INSERT ALL WHEN NOSUCHFN(x) = 1 THEN INTO T SELECT 1 AS x WHERE FALSE",
            "Unknown function NOSUCHFN.");
        assertEquals("0", rows("SELECT COUNT(*) FROM T"));
    }

    /** Targets, the source, every count, every column list, the conditions, the values, then function names. */
    @Test
    public void refusalsComeInLiveOrder() {
        tables();
        assertRefused("INSERT FIRST WHEN nosuchcol = 1 THEN INTO NOSUCHT SELECT 1",
            "Table 'NOSUCHT' does not exist or not authorized.");
        assertInvalid("INSERT FIRST WHEN nosuchcol = 1 THEN INTO T SELECT nosuch2", 51, "NOSUCH2");
        assertRefused("INSERT ALL WHEN nosuch2 = 1 THEN INTO T2 VALUES (1) SELECT 1 AS x", COUNT_2_1);
        assertRefused("INSERT ALL INTO T VALUES (nosuchcol) INTO T2 VALUES (1) SELECT 1 AS x", COUNT_2_1);
        assertRefused("INSERT ALL INTO T (nosuchcolumn) VALUES (1) INTO T2 VALUES (1) SELECT 1 AS x", COUNT_2_1);
        assertRefused("INSERT ALL INTO T (x, x) VALUES (1, 2) INTO T2 VALUES (1) SELECT 1 AS x", COUNT_2_1);
        assertRefused("INSERT ALL INTO T VALUES (nosuchcol, 1) SELECT 1 AS x",
            "Insert value list does not match column list expecting 1 but got 2");
        assertInvalid("INSERT ALL WHEN x = 1 THEN INTO T2 (nosuchcolumn) VALUES (1) WHEN nosuch2 = 1 THEN INTO T VALUES (1) SELECT 1 AS x",
            36, "NOSUCHCOLUMN");
        assertInvalid("INSERT ALL WHEN x = 1 THEN INTO T VALUES (nosuchcol) WHEN nosuch2 = 1 THEN INTO T VALUES (1) SELECT 1 AS x",
            58, "NOSUCH2");
        assertInvalid("INSERT FIRST WHEN x = 1 THEN INTO T VALUES (nosuchcol) WHEN nosuch2 = 1 THEN INTO T SELECT 1 AS x",
            60, "NOSUCH2");
        assertInvalid("INSERT FIRST WHEN nosuch2 = 1 THEN INTO T VALUES (nosuchcol) SELECT 1 AS x", 18, "NOSUCH2");
        assertInvalid("INSERT ALL WHEN x = 1 THEN INTO T VALUES (nosuchcol) ELSE INTO T VALUES (nosuch2) SELECT 1 AS x",
            42, "NOSUCHCOL");
        assertInvalid("INSERT ALL INTO T VALUES (nosuchcol) INTO T VALUES (nosuch2) SELECT 1 AS x", 26, "NOSUCHCOL");
        assertInvalid("INSERT ALL WHEN NOSUCHFN(x) = 1 THEN INTO T VALUES (nosuchcol) SELECT 1 AS x", 52, "NOSUCHCOL");
        assertInvalid("INSERT ALL WHEN nosuchcol = 1 THEN INTO T VALUES (NOSUCHFN(x)) SELECT 1 AS x", 16, "NOSUCHCOL");
        assertInvalid("INSERT ALL WHEN x = 1 THEN INTO T VALUES (NOSUCHFN(x)) WHEN nosuchcol = 1 THEN INTO T SELECT 1 AS x",
            60, "NOSUCHCOL");
        assertRefused("INSERT ALL INTO T VALUES (NOSUCHFN(x)) SELECT 1 AS x", "Unknown function NOSUCHFN.");
    }

    /** Only the source's output columns are in scope: its alias, a target's name and a quoted spelling name nothing. */
    @Test
    public void onlyTheSourceColumnsAreInScope() {
        tables();
        assertInvalid("INSERT ALL INTO T VALUES (s.x) SELECT 1 AS x FROM (SELECT 1 AS x) s", 26, "S.X");
        assertInvalid("INSERT ALL INTO T VALUES (s.nosuchcol) SELECT 1 AS x FROM (SELECT 1 AS x) s", 26, "S.NOSUCHCOL");
        assertInvalid("INSERT ALL WHEN s.x = 1 THEN INTO T SELECT 1 AS x FROM (SELECT 1 AS x) s", 16, "S.X");
        assertInvalid("INSERT ALL INTO T VALUES (t.x) SELECT 1 AS x", 26, "T.X");
        assertInvalid("INSERT ALL INTO T VALUES (T.x) SELECT 1 AS x", 26, "T.X");
        assertInvalid("INSERT ALL INTO T VALUES ($2) SELECT 1 AS x", 26, "$2");
        assertInvalid("INSERT ALL WHEN $2 = 1 THEN INTO T SELECT 1 AS x", 16, "$2");
        assertInvalid("INSERT ALL INTO T VALUES (\"x\") SELECT 1 AS x", 26, "\"x\"");
        assertInvalid("INSERT ALL INTO T VALUES (a) SELECT a AS x FROM (SELECT 1 AS a)", 26, "A");
        assertEquals("0", rows("SELECT COUNT(*) FROM T"));
        engine.execute("INSERT ALL WHEN x = 1 THEN INTO T VALUES (y) SELECT 1 AS x, 2 AS y");
        engine.execute("INSERT ALL INTO T VALUES ($1) SELECT 1 AS x");
        engine.execute("INSERT ALL INTO T VALUES (x) SELECT a AS x FROM (SELECT 1 AS a)");
        engine.execute("INSERT ALL INTO T VALUES (a) SELECT a FROM (SELECT 1 AS a)");
        engine.execute("INSERT ALL INTO T VALUES (\"X\") SELECT 1 AS x");
        assertEquals("1 | 1 | 1 | 1 | 2", rows("SELECT x FROM T ORDER BY x"));
    }

    /** A bare DEFAULT is the column's default, a sequence over no row draws nothing, and each row is routed. */
    @Test
    public void valuesStillRouteEveryRow() {
        tables();
        engine.execute("INSERT ALL INTO T VALUES (DEFAULT) SELECT 1 AS x");
        assertEquals("1, 0", rows("SELECT COUNT(*), COUNT(x) FROM T"));
        engine.execute("CREATE OR REPLACE SEQUENCE mti_sq ORDER");
        engine.execute("INSERT ALL INTO T VALUES (mti_sq.NEXTVAL) SELECT 1 AS x WHERE FALSE");
        assertEquals("1", rows("SELECT mti_sq.NEXTVAL"));
        engine.execute("INSERT ALL INTO T VALUES ($1) INTO T VALUES (x) SELECT 7 AS x UNION ALL SELECT 8");
        assertEquals("7 | 7 | 8 | 8", rows("SELECT x FROM T WHERE x IS NOT NULL ORDER BY x"));
    }
}
