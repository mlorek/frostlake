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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CALL ... INTO :variable, which Snowflake Scripting uses to assign the value a stored procedure returns to a variable.
 * It was a syntax error, so a procedure whose body used it could not even be created. The call runs as a bare CALL
 * does, statement or nested in a loop or an IF, and the variable takes the returned value as an assignment would.
 * Outside a block the clause stays the syntax error it was.
 */
public class CallIntoTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE call_into_db");
        engine.execute("USE SCHEMA call_into_db.PUBLIC");
        engine.execute("CREATE PROCEDURE p1(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'got ' || a; END; $$");
        engine.execute("CREATE PROCEDURE p2() RETURNS OBJECT LANGUAGE SQL AS $$ BEGIN RETURN OBJECT_CONSTRUCT('n', 3); END; $$");
        engine.execute("CREATE PROCEDURE p3() RETURNS FLOAT LANGUAGE SQL AS $$ BEGIN RETURN 1.239; END; $$");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String first(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    @Test
    public void theVariableTakesTheValueTheProcedureReturns() {
        assertEquals("got x", first("EXECUTE IMMEDIATE $$ DECLARE s VARCHAR; BEGIN CALL p1('x') INTO :s; RETURN s; END; $$"));
        assertEquals("got named", first(
            "EXECUTE IMMEDIATE $$ DECLARE s VARCHAR; BEGIN CALL p1(a => 'named') INTO :s; RETURN s; END; $$"));
        assertEquals("3", first("EXECUTE IMMEDIATE $$ DECLARE o OBJECT; BEGIN CALL p2() INTO :o; RETURN o:n; END; $$"));
    }

    @Test
    public void aCallIntoInsideALoopOrAnIfAssignsToo() {
        assertEquals("got 1", first("EXECUTE IMMEDIATE $$ DECLARE s VARCHAR; i INT DEFAULT 0; BEGIN"
            + " WHILE (i < 2) DO CALL p1(:i) INTO :s; i := i + 1; END WHILE; RETURN s; END; $$"));
        assertEquals("got in if", first("EXECUTE IMMEDIATE $$ DECLARE s VARCHAR; BEGIN"
            + " IF (TRUE) THEN CALL p1('in if') INTO s; END IF; RETURN s; END; $$"));
    }

    @Test
    public void theValueIsCoercedToTheVariablesDeclaredType() {
        assertEquals("1.24", first("EXECUTE IMMEDIATE $$ DECLARE n NUMBER(10,2); BEGIN CALL p3() INTO :n; RETURN n; END; $$"));
    }

    @Test
    public void aProcedureBodyCanCallIntoAVariable() {
        engine.execute("CREATE PROCEDURE outer_proc() RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " DECLARE s VARCHAR; BEGIN CALL p1('inner') INTO :s; RETURN s; END; $$");
        assertEquals("got inner", first("CALL outer_proc()"));
    }

    @Test
    public void aSystemFunctionsAnswerCanBeCalledIntoAVariable() {
        assertEquals("waited 0 seconds", first(
            "EXECUTE IMMEDIATE $$ DECLARE w VARCHAR; BEGIN CALL SYSTEM$WAIT(0) INTO :w; RETURN w; END; $$"));
    }

    @Test
    public void anUndeclaredTargetIsAnInvalidIdentifier() {
        final String refused = refusal("EXECUTE IMMEDIATE $$ BEGIN CALL p1('x') INTO :undeclared; RETURN 1; END; $$");
        assertTrue(refused.contains("invalid identifier 'UNDECLARED'"), refused);
    }

    @Test
    public void outsideABlockTheIntoClauseIsStillASyntaxError() {
        final String refused = refusal("CALL p1('top') INTO :s");
        assertTrue(refused.contains("syntax error line 1 at position 15 unexpected 'INTO'"), refused);
    }
}
