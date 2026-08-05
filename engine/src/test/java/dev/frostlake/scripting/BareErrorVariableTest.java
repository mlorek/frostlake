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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake Scripting exposes {@code SQLERRM} / {@code SQLCODE} / {@code SQLSTATE} as <b>bare</b> identifiers
 * (no {@code :} prefix) inside an exception handler, in a SCRIPTING expression — {@code RETURN 'ERR:' ||
 * SQLERRM}. The evaluator must resolve those to the procedural variables the handler bound, rather than
 * treating them as (missing) column references. Inside an EMBEDDED SQL statement they are ordinary
 * scripting names and reach it only as {@code :SQLERRM} / {@code :SQLCODE} / {@code :SQLSTATE}.
 */
public class BareErrorVariableTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void bareSqlerrmInHandlerResolves() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_errm() RETURNS VARCHAR LANGUAGE SQL AS
            $$ DECLARE r INT;
               BEGIN
                 r := (SELECT 1 / 0);
                 RETURN 'no-error';
               EXCEPTION WHEN OTHER THEN
                 RETURN 'ERR:' || SQLERRM;
               END $$
            """);
        final Object v = scalar("CALL p_errm()");
        assertTrue(v != null && v.toString().startsWith("ERR:") && v.toString().length() > 4,
            "SQLERRM should resolve to the error message, got: " + v);
    }

    @Test
    public void bareErrorVariablesAsFunctionArguments() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION emsg(t VARCHAR, m VARCHAR, c NUMBER, s VARCHAR)
            RETURNS VARCHAR LANGUAGE SQL AS $$ t || ':' || m $$
            """);
        // Bare in the RETURN expression (scripting), bound as :SQLERRM inside the SELECT (embedded SQL).
        // `LET r :=`, not a bare `r :=`: live-verified, assigning to an UNDECLARED name fails
        // "invalid identifier 'R'" before the block ever runs, so the handler never sees the division by
        // zero. With LET (or a DECLARE) the same block answers 'caught' / 'ERR:Division by zero'.
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_args() RETURNS VARCHAR LANGUAGE SQL AS
            $$ BEGIN
                 LET r := (SELECT 1 / 0);
                 RETURN 'x';
               EXCEPTION WHEN OTHER THEN
                 RETURN emsg('ERR', SQLERRM, SQLCODE, SQLSTATE);
               END $$
            """);
        final Object v = scalar("CALL p_args()");
        assertTrue(v != null && v.toString().startsWith("ERR:") && v.toString().length() > 4,
            "SQLERRM/SQLCODE/SQLSTATE should resolve as bare function arguments, got: " + v);
    }

    /** The same names inside an embedded SQL statement are identifiers, not the handler's variables. */
    @Test
    public void bareErrorVariableInsideSqlStatementIsRejected() {
        engine.execute("CREATE TABLE err_sink (msg VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_bare_sql() RETURNS VARCHAR LANGUAGE SQL AS
            $$ BEGIN
                 r := (SELECT 1 / 0);
                 RETURN 'x';
               EXCEPTION WHEN OTHER THEN
                 INSERT INTO err_sink VALUES (SQLERRM);
                 RETURN 'logged';
               END $$
            """);
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("CALL p_bare_sql()");
            }
        });
    }
}
