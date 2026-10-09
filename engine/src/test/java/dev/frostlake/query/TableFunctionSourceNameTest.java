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
 * A table function written in FROM without an alias answers to its function's name: {@code flatten.value},
 * {@code split_to_table.*}, a user-defined function's own name. An alias takes the name's place.
 */
public class TableFunctionSourceNameTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE FUNCTION tfsn(n INT) RETURNS TABLE (v INT) AS 'SELECT n'");
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
    public void anUnaliasedFlattenIsReadByItsName() {
        assertEquals("1", rows("SELECT flatten.value FROM TABLE(FLATTEN(input => [1]))"));
        assertEquals("1 ; 1", rows("SELECT flatten.value FROM FZ, TABLE(FLATTEN(input => [1]))"));
        assertEquals("1 ; 1", rows("SELECT flatten.value FROM FZ, LATERAL FLATTEN(input => [1])"));
        assertEquals("1", rows("SELECT FLATTEN.VALUE FROM TABLE(FLATTEN(input => [1]))"));
        assertEquals("1", rows("SELECT \"FLATTEN\".value FROM TABLE(FLATTEN(input => [1]))"));
        assertEquals("null|[0]|0|1|[1]",
            rows("SELECT flatten.* EXCLUDE (seq) FROM TABLE(FLATTEN(input => [1]))"));
        assertEquals("2", rows("SELECT value FROM TABLE(FLATTEN(input => [1, 2])) WHERE flatten.index = 1"));
        assertEquals("2 ; 1", rows("SELECT value FROM TABLE(FLATTEN(input => [1, 2])) ORDER BY flatten.index DESC"));
    }

    @Test
    public void everyTableFunctionIsReadTheSameWay() {
        assertEquals("a ; b", rows("SELECT split_to_table.value FROM TABLE(SPLIT_TO_TABLE('a,b', ',')) ORDER BY 1"));
        assertEquals("1|1|a ; 1|2|b", rows("SELECT split_to_table.* FROM TABLE(SPLIT_TO_TABLE('a,b', ',')) ORDER BY 2"));
        assertEquals("1", rows("SELECT tfsn.v FROM TABLE(tfsn(1))"));
        assertEquals("1", rows("SELECT tfsn.* FROM TABLE(tfsn(1))"));
        engine.execute("SELECT 1 AS one");
        assertEquals("1", rows("SELECT result_scan.one FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        assertEquals("SQL compilation error: error line 1 at position 0\nSELECT with no columns",
            refusalOf("SELECT generator.* FROM TABLE(GENERATOR(ROWCOUNT => 1))"));
    }

    @Test
    public void aQuotedLowerCaseNameAndAnAliasedSourceAreNotReached() {
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier '\"flatten\".VALUE'",
            refusalOf("SELECT \"flatten\".value FROM TABLE(FLATTEN(input => [1]))"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'FLATTEN.VALUE'",
            refusalOf("SELECT flatten.value FROM TABLE(FLATTEN(input => [1])) f"));
        assertEquals("1 ; 1", rows("SELECT f.value FROM FZ, TABLE(FLATTEN(input => [1])) f"));
        assertEquals("SQL compilation error:\nduplicate alias 'FLATTEN'",
            refusalOf("SELECT COUNT(*) FROM TABLE(FLATTEN(input => [1])), TABLE(FLATTEN(input => [2]))"));
        assertEquals("SQL compilation error: error line 1 at position 18\ninvalid identifier 'NOSUCHCOL'",
            refusalOf("SELECT flatten.*, nosuchcol FROM TABLE(FLATTEN(input => [1]))"));
    }
}
