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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A cursor loop's record field is read by a scripting EXPRESSION only. Written bare inside a SQL
 * statement it is an ordinary column reference, so it fails the statement as an unresolvable name,
 * unless a table aliased like the record gives it a column of its own:
 *
 * <pre>
 *   FOR r IN c DO CREATE OR REPLACE TABLE t373 AS SELECT r.a AS c; END FOR;
 *       Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 72 :
 *       SQL compilation error: error line 1 at position 39
 *       invalid identifier 'R.A'
 * </pre>
 *
 * Every cell was measured on a real account.
 */
public class RecordFieldInSqlTest extends BaseDatabaseTest {

    private static final String CURSOR = "DECLARE c CURSOR FOR SELECT 1.5::NUMBER(5,2) AS a; ";
    private static final String CURSOR_AND_X = "DECLARE c CURSOR FOR SELECT 1.5::NUMBER(5,2) AS a; x NUMBER(5,2); ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t373b (c NUMBER(5,2), a NUMBER(5,2))");
        engine.execute("INSERT INTO t373b VALUES (7, 7)");
    }

    private String refusal(final String block) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("EXECUTE IMMEDIATE $$ " + block + " $$");
            }
        });
        return String.valueOf(refused.getMessage()).replace('\n', '|');
    }

    private String answer(final String block) {
        return String.valueOf(engine.executeQuery("EXECUTE IMMEDIATE $$ " + block + " $$")
            .getRows().get(0).getValue(0));
    }

    private static String unresolvedField(final int statementAt, final int fieldAt) {
        return "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position " + statementAt
            + " : SQL compilation error: error line 1 at position " + fieldAt + "|invalid identifier 'R.A'";
    }

    /** Inside a SQL statement the field is a column reference that resolves to nothing. */
    @Test
    public void aFieldInsideASqlStatementIsAnInvalidIdentifier() {
        assertEquals(unresolvedField(72, 39), refusal(CURSOR
            + "BEGIN FOR r IN c DO CREATE OR REPLACE TABLE t373 AS SELECT r.a AS c; END FOR; RETURN 'ok'; END;"));
        assertEquals(unresolvedField(72, 30), refusal(CURSOR
            + "BEGIN FOR r IN c DO INSERT INTO t373b (c) VALUES (r.a); END FOR; RETURN 'ok'; END;"));
        assertEquals(unresolvedField(87, 7), refusal(CURSOR_AND_X
            + "BEGIN FOR r IN c DO SELECT r.a INTO :x; END FOR; RETURN x; END;"));
        assertEquals(unresolvedField(82, 37), refusal("DECLARE c CURSOR FOR SELECT 1.5::NUMBER(5,2) AS a; n NUMBER;"
            + " BEGIN FOR r IN c DO SELECT COUNT(*) INTO :n FROM t373b WHERE c = r.a; END FOR; RETURN n; END;"));
        assertEquals(unresolvedField(101, 39), refusal("DECLARE res RESULTSET DEFAULT (SELECT 1.5::NUMBER(5,2) AS a);"
            + " c CURSOR FOR res; BEGIN FOR r IN c DO CREATE OR REPLACE TABLE t373 AS SELECT r.a AS c; END FOR;"
            + " RETURN 'ok'; END;"), "over a RESULTSET's cursor too");
    }

    /** A table aliased like the record gives the reference a column of its own, which is what it reads. */
    @Test
    public void aTableAliasedLikeTheRecordReadsItsOwnColumn() {
        assertEquals("7.00", answer(CURSOR_AND_X
            + "BEGIN FOR r IN c DO SELECT MAX(r.a) INTO :x FROM t373b r; END FOR; RETURN x; END;"));
    }

    /** A scripting expression reads the field: an assignment, a RETURN, a condition, a typed LET. */
    @Test
    public void aScriptingExpressionReadsTheField() {
        assertEquals("1.50", answer(CURSOR_AND_X + "BEGIN FOR r IN c DO x := r.a; END FOR; RETURN x; END;"));
        assertEquals("1.50", answer(CURSOR + "BEGIN FOR r IN c DO RETURN r.a; END FOR; END;"));
        assertEquals("big", answer(CURSOR
            + "BEGIN FOR r IN c DO IF (r.a > 1) THEN RETURN 'big'; END IF; END FOR; RETURN 'small'; END;"));
        assertEquals("1.50", answer(CURSOR + "BEGIN FOR r IN c DO LET v NUMBER(5,2) := r.a; RETURN v; END FOR; END;"));
        assertEquals("ok", answer(CURSOR
            + "BEGIN FOR r IN c DO EXECUTE IMMEDIATE 'SELECT ' || r.a; END FOR; RETURN 'ok'; END;"));
    }
}
