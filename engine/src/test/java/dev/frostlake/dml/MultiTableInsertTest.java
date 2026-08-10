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

/**
 * Snowflake multi-table INSERT — INSERT [OVERWRITE] ALL (unconditional) and
 * INSERT [OVERWRITE] {FIRST | ALL} WHEN … THEN INTO … [ELSE INTO …].
 */
public class MultiTableInsertTest extends BaseDatabaseTest {




    private int count(final String table) {
        return engine.executeQuery("SELECT * FROM " + table).getRows().size();
    }

    private String val(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0).toString();
    }

    @Test
    public void unconditionalAllFansOutToEveryTarget() {
        engine.execute("CREATE TABLE src (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO src VALUES (1, 10), (2, 20)");
        engine.execute("CREATE TABLE a (id INTEGER, v INTEGER)");
        engine.execute("CREATE TABLE b (id INTEGER, v INTEGER)");
        engine.execute("INSERT ALL INTO a INTO b SELECT id, v FROM src");
        assertEquals(2, count("a"));   // every source row goes into a …
        assertEquals(2, count("b"));   // … and into b
    }

    @Test
    public void firstRoutesToFirstMatchingWhen() {
        engine.execute("CREATE TABLE n (x INTEGER)");
        engine.execute("INSERT INTO n VALUES (5), (50), (500)");
        engine.execute("CREATE TABLE big (x INTEGER)");
        engine.execute("CREATE TABLE mid (x INTEGER)");
        engine.execute("CREATE TABLE small (x INTEGER)");
        engine.execute(
            "INSERT FIRST "
            + "WHEN x > 100 THEN INTO big "
            + "WHEN x > 10 THEN INTO mid "
            + "ELSE INTO small "
            + "SELECT x FROM n");
        assertEquals(1, count("big"));     // 500
        assertEquals(1, count("mid"));     // 50 (NOT 500 — FIRST stopped at big)
        assertEquals(1, count("small"));   // 5
        assertEquals("500", val("SELECT x FROM big"));
    }

    @Test
    public void allAppliesEveryMatchingWhen() {
        engine.execute("CREATE TABLE n (x INTEGER)");
        engine.execute("INSERT INTO n VALUES (5), (50), (500)");
        engine.execute("CREATE TABLE big (x INTEGER)");
        engine.execute("CREATE TABLE mid (x INTEGER)");
        engine.execute(
            "INSERT ALL "
            + "WHEN x > 100 THEN INTO big "
            + "WHEN x > 10 THEN INTO mid "
            + "SELECT x FROM n");
        assertEquals(1, count("big"));   // 500
        assertEquals(2, count("mid"));   // 50 and 500 (500 matched BOTH WHENs)
    }

    @Test
    public void valuesClauseEvaluatesExpressionsAgainstSourceRow() {
        engine.execute("CREATE TABLE s2 (a INTEGER, b INTEGER)");
        engine.execute("INSERT INTO s2 VALUES (1, 2)");
        engine.execute("CREATE TABLE t3 (total INTEGER)");
        engine.execute("INSERT ALL INTO t3 (total) VALUES (a + b) SELECT a, b FROM s2");
        assertEquals("3", val("SELECT total FROM t3"));
    }

    @Test
    public void overwriteTruncatesTargetFirst() {
        engine.execute("CREATE TABLE ov (x INTEGER)");
        engine.execute("INSERT INTO ov VALUES (99)");
        engine.execute("CREATE TABLE src2 (x INTEGER)");
        engine.execute("INSERT INTO src2 VALUES (1), (2)");
        engine.execute("INSERT OVERWRITE ALL INTO ov SELECT x FROM src2");
        assertEquals(2, count("ov"));                                  // only the new rows
        assertEquals("0", val("SELECT COUNT(*) FROM ov WHERE x = 99")); // the pre-existing row is gone
    }
}
