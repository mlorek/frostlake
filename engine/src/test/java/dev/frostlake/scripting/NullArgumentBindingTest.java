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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Passing {@code NULL} as a CALL argument (or SET value) must bind a real SQL null — not the string
 * {@code "NULL"} — so that {@code :param IS NULL} is true and {@code :param IS NOT NULL} is false. A common
 * guard {@code IF (:x IS NOT NULL AND NOT is_allowed(:x))} must therefore not fire when a NULL is passed.
 */
public class NullArgumentBindingTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void nullArgumentBindsRealNull() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE nat(p VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS
            $$ BEGIN RETURN IFF(:p IS NULL, 'NULL', 'NOT_NULL'); END $$
            """);
        assertEquals("NULL", scalar("CALL nat(NULL)"));
        assertEquals("NOT_NULL", scalar("CALL nat('x')"));
    }

    @Test
    public void isNotNullGuardDoesNotFireForNullArgument() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION is_allowed(x VARCHAR)
            RETURNS BOOLEAN LANGUAGE SQL AS $$ UPPER(x) IN ('A','B','C','D','E') $$
            """);
        engine.execute("""
            CREATE OR REPLACE PROCEDURE save(p_a VARCHAR, p_b VARCHAR, p_c VARCHAR)
            RETURNS VARCHAR LANGUAGE SQL AS
            $$ DECLARE bad EXCEPTION (-20001, 'not allowed');
               BEGIN
                 IF (NOT is_allowed(:p_b)) THEN RAISE bad; END IF;
                 IF (:p_a IS NOT NULL AND NOT is_allowed(:p_a)) THEN RAISE bad; END IF;
                 IF (:p_c IS NOT NULL AND NOT is_allowed(:p_c)) THEN RAISE bad; END IF;
                 RETURN 'ok';
               END $$
            """);
        // A NULL optional value (last of three args) must not trip the IS NOT NULL guard.
        assertEquals("ok", scalar("CALL save('B', 'A', NULL)"));
        assertEquals("ok", scalar("CALL save('B', 'A', 'C')"));
    }

    @Test
    public void nullFromSessionVariableBindsRealNull() {
        engine.execute("CREATE OR REPLACE PROCEDURE echo(p VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS "
            + "$$ BEGIN RETURN IFF(:p IS NULL, 'NULL', 'NOT_NULL'); END $$");
        engine.execute("SET v = (SELECT CASE WHEN 1 = 2 THEN 'x' END)"); // NULL
        assertEquals("NULL", scalar("CALL echo($v)"));
    }

    @Test
    public void defaultNullColumnStillWorks() {
        engine.execute("CREATE TABLE t (a INT, b VARCHAR DEFAULT NULL)");
        engine.execute("INSERT INTO t (a) VALUES (1)");
        assertEquals(null, scalar("SELECT b FROM t"));
    }
}
