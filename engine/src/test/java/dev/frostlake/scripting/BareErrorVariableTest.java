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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake Scripting exposes {@code SQLERRM} / {@code SQLCODE} / {@code SQLSTATE} as <b>bare</b> identifiers
 * (no {@code :} prefix) inside an exception handler. The evaluator must resolve those to the procedural
 * variables the handler bound, rather than treating them as (missing) column references — a common pattern
 * is {@code err_msg('OTHER', SQLERRM, SQLCODE, SQLSTATE)}.
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
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p_args() RETURNS VARCHAR LANGUAGE SQL AS
            $$ BEGIN
                 r := (SELECT 1 / 0);
                 RETURN 'x';
               EXCEPTION WHEN OTHER THEN
                 RETURN (SELECT emsg('ERR', SQLERRM, SQLCODE, SQLSTATE));
               END $$
            """);
        final Object v = scalar("CALL p_args()");
        assertTrue(v != null && v.toString().startsWith("ERR:") && v.toString().length() > 4,
            "SQLERRM/SQLCODE/SQLSTATE should resolve as bare function arguments, got: " + v);
    }
}
