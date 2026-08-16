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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code $name} session variable that no SET defined is refused while the statement compiles — in any
 * clause, in DML, a CTAS, a view body and a scripting block alike, over no rows too — at the variable's
 * own position, naming it upper-cased. A variable SET to NULL exists and reads NULL, UNSET removes it,
 * and a session PARAMETER is not a variable. Every cell is live-verified.
 */
public class UnsetSessionVariableTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT)");
    }

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void anUnsetVariableIsRefusedWhereverItIsRead() {
        assertRefused("SELECT $nosuch356",
            "SQL compilation error: error line 1 at position 7\nSession variable '$NOSUCH356' does not exist");
        assertRefused("SELECT ROUND(2.5, 0, $nosuch356)",
            "SQL compilation error: error line 1 at position 21\nSession variable '$NOSUCH356' does not exist");
        assertRefused("SELECT a FROM t WHERE a = $nosuch356",
            "SQL compilation error: error line 1 at position 26\nSession variable '$NOSUCH356' does not exist");
        assertRefused("SELECT 1 AS x, $nosuch356 AS y",
            "SQL compilation error: error line 1 at position 15\nSession variable '$NOSUCH356' does not exist");
        assertRefused("SELECT UPPER($nosuch356)",
            "SQL compilation error: error line 1 at position 13\nSession variable '$NOSUCH356' does not exist");
        assertRefused("SELECT a FROM t ORDER BY $nosuch356",
            "SQL compilation error: error line 1 at position 25\nSession variable '$NOSUCH356' does not exist");
        assertRefused("SELECT COUNT(*) FROM t GROUP BY $nosuch356",
            "SQL compilation error: error line 1 at position 32\nSession variable '$NOSUCH356' does not exist");
        assertRefused("SELECT IFF(TRUE, 1, $nosuch356)",
            "SQL compilation error: error line 1 at position 20\nSession variable '$NOSUCH356' does not exist");
        assertRefused("SELECT $nosuch356 FROM t",
            "SQL compilation error: error line 1 at position 7\nSession variable '$NOSUCH356' does not exist");
        assertRefused("SELECT $NoSuch356",
            "SQL compilation error: error line 1 at position 7\nSession variable '$NOSUCH356' does not exist");
        assertRefused("SELECT a FROM t WHERE FALSE AND a = $nosuch419",
            "SQL compilation error: error line 1 at position 36\nSession variable '$NOSUCH419' does not exist");
    }

    @Test
    public void dmlACtasAndAViewBodyAreRefusedToo() {
        assertRefused("INSERT INTO t VALUES ($nosuch356)",
            "SQL compilation error: error line 1 at position 22\nSession variable '$NOSUCH356' does not exist");
        assertRefused("UPDATE t SET a = $nosuch356",
            "SQL compilation error: error line 1 at position 17\nSession variable '$NOSUCH356' does not exist");
        assertRefused("UPDATE t SET a = $nosuch419",
            "SQL compilation error: error line 1 at position 17\nSession variable '$NOSUCH419' does not exist");
        assertRefused("DELETE FROM t WHERE a = $nosuch419",
            "SQL compilation error: error line 1 at position 24\nSession variable '$NOSUCH419' does not exist");
        assertRefused("MERGE INTO t USING (SELECT 1 AS a) s ON t.a = s.a WHEN MATCHED THEN UPDATE SET a = $nosuch419",
            "SQL compilation error: error line 1 at position 83\nSession variable '$NOSUCH419' does not exist");
        assertRefused("INSERT INTO t SELECT $nosuch419",
            "SQL compilation error: error line 1 at position 21\nSession variable '$NOSUCH419' does not exist");
        assertRefused("CREATE OR REPLACE TABLE t2 AS SELECT $nosuch419 AS a",
            "SQL compilation error: error line 1 at position 37\nSession variable '$NOSUCH419' does not exist");
        assertRefused("CREATE OR REPLACE VIEW vw AS SELECT $nosuch356 AS c",
            "SQL compilation error: error line 1 at position 36\nSession variable '$NOSUCH356' does not exist");
    }

    @Test
    public void aScriptingBlockIsRefusedWhenItCompiles() {
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  RETURN $nosuch356;\nEND;\n$$",
            "SQL compilation error: error line 3 at position 9\nSession variable '$NOSUCH356' does not exist");
        assertRefused("EXECUTE IMMEDIATE $$\nBEGIN\n  LET x := (SELECT $nosuch356);\n  RETURN x;\nEND;\n$$",
            "SQL compilation error: error line 3 at position 19\nSession variable '$NOSUCH356' does not exist");
    }

    @Test
    public void aSessionParameterIsNotAVariable() {
        assertRefused("SELECT $TIMEZONE",
            "SQL compilation error: error line 1 at position 7\nSession variable '$TIMEZONE' does not exist");
        assertRefused("SELECT $QUERY_TAG",
            "SQL compilation error: error line 1 at position 7\nSession variable '$QUERY_TAG' does not exist");
        assertRefused("SELECT $MULTI_STATEMENT_COUNT",
            "SQL compilation error: error line 1 at position 7\nSession variable '$MULTI_STATEMENT_COUNT' does not exist");
    }

    @Test
    public void setDefinesUnsetRemoves() {
        engine.execute("SET v419null = NULL");
        assertEquals("null", scalar("SELECT $v419null"));
        assertEquals("null", scalar("SELECT $V419NULL"));
        engine.execute("SET v419set = 5");
        assertEquals("6", scalar("SELECT $v419set + 1"));
        engine.execute("CREATE OR REPLACE VIEW vw2 AS SELECT $v419set AS c");
        assertEquals("5", scalar("SELECT * FROM vw2"));
        engine.execute("UNSET v419set");
        assertRefused("SELECT $v419set", "SQL compilation error: error line 1 at position 7\nSession variable '$V419SET' does not exist");
        engine.execute("UNSET v419null");
        assertEquals(0, engine.executeQuery("SELECT $1 FROM t").getRows().size());
    }
}
