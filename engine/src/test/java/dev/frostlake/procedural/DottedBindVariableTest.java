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

package dev.frostlake.procedural;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A bind variable naming a field ({@code :r.a}) is a syntax error at the '.', wherever the block writes
 * it, and the whole block is refused while it compiles: nothing in it runs, and a stored procedure is
 * never created. The coordinates are the body's own. Live lists its parser's recovery lines after the
 * first; the first line is the one asserted. Every cell is live-verified.
 */
public class DottedBindVariableTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t373b (c NUMBER(5,2))");
        engine.execute("CREATE OR REPLACE TABLE side (v INT)");
    }

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
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

    /** In an embedded statement of a cursor loop: a CTAS, an INSERT, a SELECT INTO and an UPDATE. */
    @Test
    public void aDottedBindInAnEmbeddedStatementIsASyntaxErrorAtTheDot() {
        assertRefused("EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1.5::NUMBER(5,2) AS a; x NUMBER(5,2); BEGIN FOR r IN c DO CREATE OR REPLACE TABLE t373 AS SELECT :r.a AS c; END FOR; RETURN 'ok'; END; $$",
            "SQL compilation error:\nsyntax error line 1 at position 128 unexpected '.'.");
        assertRefused("EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1.5::NUMBER(5,2) AS a; x NUMBER(5,2); BEGIN FOR r IN c DO INSERT INTO t373b (c) VALUES (:r.a); END FOR; RETURN 'ok'; END; $$",
            "SQL compilation error:\nsyntax error line 1 at position 119 unexpected '.'.");
        assertRefused("EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1.5::NUMBER(5,2) AS a; x NUMBER(5,2); BEGIN FOR r IN c DO SELECT :r.a INTO :x; END FOR; RETURN 'ok'; END; $$",
            "SQL compilation error:\nsyntax error line 1 at position 96 unexpected '.'.");
        assertRefused("EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1.5::NUMBER(5,2) AS a; x NUMBER(5,2); BEGIN FOR r IN c DO UPDATE t373b SET c = :r.a WHERE c = 7; END FOR; RETURN 'ok'; END; $$",
            "SQL compilation error:\nsyntax error line 1 at position 110 unexpected '.'.");
        assertRefused("EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1.5::NUMBER(5,2) AS a; x NUMBER(5,2); BEGIN FOR r IN c DO INSERT INTO t373b (c) VALUES (:r . a); END FOR; RETURN 'ok'; END; $$",
            "SQL compilation error:\nsyntax error line 1 at position 120 unexpected '.'.");
        assertRefused("EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1.5::NUMBER(5,2) AS a; x NUMBER(5,2); BEGIN FOR r IN c DO INSERT INTO t373b (c) VALUES (:r.a + 1); END FOR; RETURN 'ok'; END; $$",
            "SQL compilation error:\nsyntax error line 1 at position 119 unexpected '.'.");
        assertRefused("EXECUTE IMMEDIATE $$\nDECLARE c CURSOR FOR SELECT 1 AS a;\nBEGIN\nFOR r IN c DO\nINSERT INTO t373b (c) VALUES (:r.a);\nEND FOR;\nEND;\n$$",
            "SQL compilation error:\nsyntax error line 5 at position 32 unexpected '.'.");
    }

    /** A stored procedure is refused at CREATE, a scripting expression the same way, and nothing runs. */
    @Test
    public void theWholeBlockIsRefusedBeforeAnythingRuns() {
        assertRefused("CREATE OR REPLACE PROCEDURE p5() RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE c CURSOR FOR SELECT 1.5::NUMBER(5,2) AS a; BEGIN FOR r IN c DO INSERT INTO t373b (c) VALUES (:r.a); END FOR; RETURN 'ok'; END; $$",
            "SQL compilation error:\nsyntax error line 1 at position 104 unexpected '.'.");
        assertRefused("CALL p5()",
            "SQL compilation error:\nUnknown function P5.");
        assertRefused("EXECUTE IMMEDIATE $$ BEGIN LET y := :z.a; RETURN 1; END; $$",
            "SQL compilation error:\nsyntax error line 1 at position 18 unexpected '.'.");
        assertRefused("EXECUTE IMMEDIATE $$ DECLARE c CURSOR FOR SELECT 1.5::NUMBER(5,2) AS a; BEGIN INSERT INTO side VALUES (1); FOR r IN c DO INSERT INTO t373b (c) VALUES (:r.a); END FOR; RETURN 'ok'; END; $$",
            "SQL compilation error:\nsyntax error line 1 at position 133 unexpected '.'.");
        assertEquals("0",
            rows("SELECT COUNT(*) FROM side"));
    }
}
