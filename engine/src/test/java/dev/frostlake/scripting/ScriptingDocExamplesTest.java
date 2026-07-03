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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for Snowflake Scripting worked examples taken from the Snowflake Scripting
 * developer guide (https://docs.snowflake.com/en/developer-guide/snowflake-scripting): variables and
 * block scoping, branching (IF / CASE), loops (FOR / WHILE / REPEAT / LOOP), cursors, SELECT … INTO,
 * exceptions, and procedure bodies (both {@code $$}-quoted and unquoted {@code AS BEGIN … END}). Each
 * example is executed against a fresh engine and its returned/side-effect value is asserted, locking
 * in doc-example compatibility. Includes the nested-block shadow/restore and unquoted-body cases that
 * previously diverged from Snowflake.
 */
public class ScriptingDocExamplesTest {

    private static final Logger logger = LoggerFactory.getLogger(ScriptingDocExamplesTest.class);

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA test_schema");
        engine.execute("USE SCHEMA test_schema");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** Run a scripting block / CALL and return the first cell of its result as a String. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    /** Read the {@code v} column of table {@code r} ordered by {@code seq} into a list of Strings. */
    private List<String> seqRows() {
        final ResultSet rs = engine.executeQuery("SELECT v FROM r ORDER BY seq");
        final List<String> out = new ArrayList<String>();
        for (int i = 0; i < rs.getRowCount(); i++) {
            final Object value = rs.getRows().get(i).getValue(0);
            out.add(value == null ? "NULL" : value.toString());
        }
        return out;
    }

    private long asLong(final String s) {
        return Long.parseLong(s.trim());
    }

    // ─────────────────────────── Variables ───────────────────────────

    @Test
    public void variablesDeclareDefaultAndAssignment() {
        logger.info("Doc example: DECLARE + LET + assignment (profit)");
        // NUMBER(38, 2) preserves scale, so the result is '10.00' (Snowflake-faithful), not '10'.
        assertEquals("10.00", scalar("""
            DECLARE
              profit NUMBER(38, 2) DEFAULT 0.0;
            BEGIN
              LET revenue NUMBER(38, 2) DEFAULT 110.0;
              LET cost NUMBER(38, 2) DEFAULT 100.0;
              profit := revenue - cost;
              RETURN profit;
            END;
            """));
    }

    @Test
    public void variablesLetUntyped() {
        logger.info("Doc example: LET without an explicit type");
        assertEquals("14", scalar("""
            BEGIN
              LET a := 6;
              LET b := 8;
              RETURN a + b;
            END;
            """));
    }

    // ─────────────────────── Nested-block scoping ───────────────────────

    @Test
    public void nestedBlockDeclareSectionShadowRestore() {
        logger.info("Doc example: nested DECLARE sections shadow and restore the same-named variable");
        engine.execute("CREATE TABLE r (seq INTEGER, v VARCHAR)");
        engine.execute("""
            DECLARE
              PV_NAME VARCHAR DEFAULT 'outer';
            BEGIN
              INSERT INTO r VALUES (1, :PV_NAME);
              DECLARE
                PV_NAME VARCHAR DEFAULT 'middle';
              BEGIN
                INSERT INTO r VALUES (2, :PV_NAME);
                DECLARE
                  PV_NAME VARCHAR DEFAULT 'inner';
                BEGIN
                  INSERT INTO r VALUES (3, :PV_NAME);
                END;
                INSERT INTO r VALUES (4, :PV_NAME);
              END;
              INSERT INTO r VALUES (5, :PV_NAME);
            END;
            """);
        assertEquals(List.of("outer", "middle", "inner", "middle", "outer"), seqRows());
    }

    @Test
    public void procedureParameterShadowedByNestedDeclare() {
        logger.info("Doc example: a nested DECLARE shadows a procedure parameter of the same name");
        engine.execute("CREATE TABLE r (seq INTEGER, v VARCHAR)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE scope_test(PV_NAME VARCHAR)
            RETURNS VARCHAR
            LANGUAGE SQL
            AS $$
            BEGIN
              INSERT INTO r VALUES (1, :PV_NAME);
              DECLARE
                PV_NAME VARCHAR DEFAULT 'middle';
              BEGIN
                INSERT INTO r VALUES (2, :PV_NAME);
                DECLARE
                  PV_NAME VARCHAR DEFAULT 'inner';
                BEGIN
                  INSERT INTO r VALUES (3, :PV_NAME);
                END;
                INSERT INTO r VALUES (4, :PV_NAME);
              END;
              INSERT INTO r VALUES (5, :PV_NAME);
              RETURN 'done';
            END;
            $$
            """);
        engine.execute("CALL scope_test('parameter')");
        assertEquals(List.of("parameter", "middle", "inner", "middle", "parameter"), seqRows());
    }

    // ─────────────────────────── Procedure bodies ───────────────────────────

    @Test
    public void unquotedBeginEndProcedureBody() {
        logger.info("Doc example: CREATE PROCEDURE ... AS BEGIN ... END (no $$ quoting)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE greet()
            RETURNS VARCHAR
            LANGUAGE SQL
            AS
            BEGIN
              RETURN 'ok';
            END;
            """);
        assertEquals("ok", scalar("CALL greet()"));
    }

    @Test
    public void unquotedDeclareBeginEndProcedureBody() {
        logger.info("Doc example: CREATE PROCEDURE ... AS DECLARE ... BEGIN ... END (no $$ quoting)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE doubler(n INTEGER)
            RETURNS INTEGER
            LANGUAGE SQL
            AS
            DECLARE
              result INTEGER DEFAULT 0;
            BEGIN
              result := n * 2;
              RETURN result;
            END;
            """);
        assertEquals(42L, asLong(scalar("CALL doubler(21)")));
    }

    @Test
    public void dollarQuotedProcedureBody() {
        logger.info("Doc example: CREATE PROCEDURE ... AS $$ BEGIN ... END $$");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE add_one(x INTEGER)
            RETURNS INTEGER
            LANGUAGE SQL
            AS $$
            BEGIN
              RETURN x + 1;
            END;
            $$
            """);
        assertEquals(42L, asLong(scalar("CALL add_one(41)")));
    }

    // ─────────────────────────── Branching ───────────────────────────

    @Test
    public void ifElseifElse() {
        logger.info("Doc example: IF / ELSEIF / ELSE");
        assertEquals("positive", scalar("""
            DECLARE
              n INTEGER DEFAULT 5;
            BEGIN
              IF (n < 0) THEN
                RETURN 'negative';
              ELSEIF (n = 0) THEN
                RETURN 'zero';
              ELSE
                RETURN 'positive';
              END IF;
            END;
            """));
    }

    @Test
    public void caseSearched() {
        logger.info("Doc example: searched CASE");
        assertEquals("medium", scalar("""
            DECLARE
              score INTEGER DEFAULT 75;
            BEGIN
              CASE
                WHEN score >= 90 THEN RETURN 'high';
                WHEN score >= 50 THEN RETURN 'medium';
                ELSE RETURN 'low';
              END;
            END;
            """));
    }

    // ─────────────────────────── Loops ───────────────────────────

    @Test
    public void forRangeLoop() {
        logger.info("Doc example: FOR i IN 1 TO n counter loop");
        assertEquals(15L, asLong(scalar("""
            DECLARE
              total INTEGER DEFAULT 0;
            BEGIN
              FOR i IN 1 TO 5 DO
                total := total + i;
              END FOR;
              RETURN total;
            END;
            """)));
    }

    @Test
    public void whileLoop() {
        logger.info("Doc example: WHILE loop");
        assertEquals(3L, asLong(scalar("""
            DECLARE
              counter INTEGER DEFAULT 0;
            BEGIN
              WHILE (counter < 3) DO
                counter := counter + 1;
              END WHILE;
              RETURN counter;
            END;
            """)));
    }

    @Test
    public void repeatUntilLoop() {
        logger.info("Doc example: REPEAT ... UNTIL loop");
        assertEquals(3L, asLong(scalar("""
            DECLARE
              counter INTEGER DEFAULT 0;
            BEGIN
              REPEAT
                counter := counter + 1;
              UNTIL (counter >= 3)
              END REPEAT;
              RETURN counter;
            END;
            """)));
    }

    @Test
    public void loopWithBreak() {
        logger.info("Doc example: LOOP with BREAK");
        assertEquals(4L, asLong(scalar("""
            DECLARE
              counter INTEGER DEFAULT 0;
            BEGIN
              LOOP
                counter := counter + 1;
                IF (counter >= 4) THEN
                  BREAK;
                END IF;
              END LOOP;
              RETURN counter;
            END;
            """)));
    }

    // ─────────────────────────── Cursors ───────────────────────────

    @Test
    public void cursorOpenFetchClose() {
        logger.info("Doc example: OPEN / FETCH / CLOSE a cursor");
        engine.execute("CREATE TABLE nums (n INTEGER)");
        engine.execute("INSERT INTO nums VALUES (1), (2), (3)");
        assertEquals(3L, asLong(scalar("""
            DECLARE
              c CURSOR FOR SELECT n FROM nums ORDER BY n;
              s INTEGER DEFAULT 0;
              v INTEGER;
            BEGIN
              OPEN c;
              FETCH c INTO v;
              s := s + v;
              FETCH c INTO v;
              s := s + v;
              CLOSE c;
              RETURN s;
            END;
            """)));
    }

    @Test
    public void cursorForLoop() {
        logger.info("Doc example: FOR row IN cursor loop");
        engine.execute("CREATE TABLE nums (n INTEGER)");
        engine.execute("INSERT INTO nums VALUES (1), (2), (3)");
        assertEquals(6L, asLong(scalar("""
            DECLARE
              total INTEGER DEFAULT 0;
              c CURSOR FOR SELECT n FROM nums;
            BEGIN
              FOR rec IN c DO
                total := total + rec.n;
              END FOR;
              RETURN total;
            END;
            """)));
    }

    // ─────────────────────────── SELECT … INTO ───────────────────────────

    @Test
    public void selectInto() {
        logger.info("Doc example: SELECT expr INTO variable");
        engine.execute("CREATE TABLE nums (n INTEGER)");
        engine.execute("INSERT INTO nums VALUES (1), (2), (3)");
        assertEquals(3L, asLong(scalar("""
            DECLARE
              mx INTEGER;
            BEGIN
              SELECT MAX(n) INTO mx FROM nums;
              RETURN mx;
            END;
            """)));
    }

    // ─────────────────────────── Exceptions ───────────────────────────

    @Test
    public void raiseAndCatchNamedException() {
        logger.info("Doc example: DECLARE EXCEPTION / RAISE / EXCEPTION WHEN <name>");
        assertEquals("caught", scalar("""
            DECLARE
              my_exception EXCEPTION (-20001, 'Raised on purpose');
            BEGIN
              RAISE my_exception;
              RETURN 'not reached';
            EXCEPTION
              WHEN my_exception THEN
                RETURN 'caught';
            END;
            """));
    }

    @Test
    public void whenOtherHandler() {
        logger.info("Doc example: EXCEPTION WHEN OTHER catch-all (EXIT — the default — ends the block)");
        assertEquals("handled", scalar("""
            BEGIN
              LET x INTEGER := 1 / 0;
              RETURN 'not reached';
            EXCEPTION
              WHEN OTHER THEN
                RETURN 'handled';
            END;
            """));
    }

    // ─────────────────────── Cursor over a RESULTSET ───────────────────────

    @Test
    public void cursorOverResultsetVariable() {
        logger.info("Doc example: DECLARE c CURSOR FOR <resultset_variable>");
        engine.execute("CREATE TABLE nums (n INTEGER)");
        engine.execute("INSERT INTO nums VALUES (1), (2), (3)");
        assertEquals(6L, asLong(scalar("""
            DECLARE
              res RESULTSET DEFAULT (SELECT n FROM nums ORDER BY n);
              c CURSOR FOR res;
              total INTEGER DEFAULT 0;
              v INTEGER;
            BEGIN
              OPEN c;
              FETCH c INTO v;
              total := total + v;
              FETCH c INTO v;
              total := total + v;
              FETCH c INTO v;
              total := total + v;
              CLOSE c;
              RETURN total;
            END;
            """)));
    }

    // ─────────────────────────── Loop labels ───────────────────────────

    @Test
    public void labeledLoopBreakAndTrailingLabel() {
        logger.info("Doc example: <label>: LOOP … BREAK <label>; END LOOP <label>;");
        assertEquals(5L, asLong(scalar("""
            DECLARE
              counter INTEGER DEFAULT 0;
            BEGIN
              my_loop: LOOP
                counter := counter + 1;
                IF (counter >= 5) THEN
                  BREAK my_loop;
                END IF;
              END LOOP my_loop;
              RETURN counter;
            END;
            """)));
    }

    // ─────────────────── CONTINUE exception handlers ───────────────────

    @Test
    public void continueHandlerResumesNextStatement() {
        logger.info("Doc example: WHEN OTHER CONTINUE THEN resumes at the statement after the failure");
        engine.execute("CREATE TABLE log (seq INTEGER, v VARCHAR)");
        assertEquals("done", scalar("""
            BEGIN
              INSERT INTO log VALUES (1, 'a');
              INSERT INTO no_such_table VALUES (99);
              INSERT INTO log VALUES (3, 'b');
              RETURN 'done';
            EXCEPTION
              WHEN OTHER CONTINUE THEN
                INSERT INTO log VALUES (2, 'caught');
            END;
            """));
        final ResultSet rs = engine.executeQuery("SELECT v FROM log ORDER BY seq");
        final List<String> vals = new ArrayList<String>();
        for (int i = 0; i < rs.getRowCount(); i++) {
            vals.add(rs.getRows().get(i).getValue(0).toString());
        }
        assertEquals(List.of("a", "caught", "b"), vals);
    }

    @Test
    public void continueHandlerInLoopResumesNextIteration() {
        logger.info("Doc example: a CONTINUE handler catches a loop-body error and resumes next iteration");
        // i=2 raises (10/0) before its increment, so that iteration's += is skipped: 1 + (skip) + 3 + 4 = 8.
        assertEquals(8L, asLong(scalar("""
            DECLARE
              total INTEGER DEFAULT 0;
            BEGIN
              FOR i IN 1 TO 4 DO
                IF (i = 2) THEN
                  LET bad INTEGER := 10 / 0;
                END IF;
                total := total + i;
              END FOR;
              RETURN total;
            EXCEPTION
              WHEN OTHER CONTINUE THEN
                total := total;
            END;
            """)));
    }
}
