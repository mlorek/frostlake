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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * GROUPING and GROUPING_ID tell which keys of a GROUP BY a row was grouped by, so a query that groups by nothing is
 * refused for calling them, once its names have resolved.
 */
public class GroupingWithoutGroupByTest extends BaseDatabaseTest {

    private static final String NO_GROUP_BY = "SQL compilation error:\nGROUPING function without a GROUP BY.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5))");
        engine.execute("INSERT INTO rt VALUES (1, 1.5, 'x'), (2, 2.5, 'y')");
        engine.execute("CREATE OR REPLACE TABLE gt (a INT, b INT)");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    /** Each row's cells joined by bars, rows by semicolons. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (int r = 0; r < rs.getRowCount(); r++) {
            out.append(r > 0 ? " ; " : "");
            for (int c = 0; c < rs.getColumnCount(); c++) {
                out.append(c > 0 ? "|" : "").append(rs.getRows().get(r).getValue(c));
            }
        }
        return out.toString();
    }

    @Test
    public void aCallWithNoGroupByIsRefusedInEveryClause() {
        assertEquals(NO_GROUP_BY, refusalOf("SELECT GROUPING(n) FROM rt"));
        assertEquals(NO_GROUP_BY, refusalOf("SELECT GROUPING_ID(n) FROM rt"));
        assertEquals(NO_GROUP_BY, refusalOf("SELECT GROUPING(n) + 1 FROM rt"));
        assertEquals(NO_GROUP_BY, refusalOf("SELECT CASE WHEN GROUPING(n) = 0 THEN 1 END FROM rt"));
        assertEquals(NO_GROUP_BY, refusalOf("SELECT DISTINCT GROUPING(n) FROM rt"));
        assertEquals(NO_GROUP_BY, refusalOf("SELECT n FROM rt WHERE GROUPING(n) = 0"));
        assertEquals(NO_GROUP_BY, refusalOf("SELECT n FROM rt ORDER BY GROUPING(n)"));
        assertEquals(NO_GROUP_BY, refusalOf("SELECT GROUPING(n) FROM rt QUALIFY 1 = 1"));
        assertEquals(NO_GROUP_BY, refusalOf("SELECT GROUPING(n), COUNT(*) FROM rt"));
        assertEquals(NO_GROUP_BY, refusalOf("SELECT n FROM rt HAVING GROUPING(n) = 0"));
        assertEquals(NO_GROUP_BY, refusalOf("SELECT GROUPING(1)"));
        assertEquals(NO_GROUP_BY, refusalOf("SELECT (SELECT GROUPING(n)) FROM rt"));
    }

    @Test
    public void aStatementBuiltOverTheQueryIsRefusedWithIt() {
        assertEquals(NO_GROUP_BY, refusalOf("INSERT INTO gt SELECT GROUPING(n), 1 FROM rt"));
        assertEquals(NO_GROUP_BY, refusalOf("CREATE OR REPLACE TABLE gct AS SELECT GROUPING(n) AS x FROM rt"));
        assertEquals(NO_GROUP_BY, refusalOf("CREATE OR REPLACE VIEW gv AS SELECT GROUPING(n) AS x FROM rt"));
    }

    @Test
    public void aNameThatDoesNotResolveAndAGroupedRuleComeFirst() {
        assertEquals("SQL compilation error: error line 1 at position 16\ninvalid identifier 'NOSUCH'",
            refusalOf("SELECT GROUPING(nosuch) FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 20\ninvalid identifier 'NOSUCH'",
            refusalOf("SELECT GROUPING(n), nosuch FROM rt"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'NOSUCH'",
            refusalOf("SELECT nosuch, GROUPING(n) FROM rt"));
        assertEquals("SQL compilation error:\n[RT.N] is not a valid group by expression",
            refusalOf("SELECT COUNT(*) FROM rt HAVING GROUPING(n) = 0"));
    }

    @Test
    public void aGroupByAnswersItAndGroupByAllPutsItAmongTheKeys() {
        assertEquals("1|0 ; 2|0", rows("SELECT n, GROUPING(n) FROM rt GROUP BY n ORDER BY 1"));
        assertEquals("SQL compilation error:\nGROUPING function cannot appear outside of SELECT, HAVING, and ORDER BY"
            + " clauses.", refusalOf("SELECT n, GROUPING(n) FROM rt GROUP BY ALL ORDER BY 1"));
        assertEquals("SQL compilation error: error line 1 at position 19\n'RT.N' in select clause is neither an"
            + " aggregate nor in the group by clause.", refusalOf("SELECT g, GROUPING(n) FROM rt GROUP BY g"));
    }
}
