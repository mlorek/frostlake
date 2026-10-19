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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This engine's own words for a handler that reads a name its block's BEGIN section declared with LET. The account
 * reports the read as an internal failure of its own, which is not imitated: here the name resolves to nothing,
 * {@code invalid identifier 'Y'} at the place it is read. What the two sides share - that the read is refused,
 * ends the body, and is caught by the statement that called the body alone - is asserted on both in
 * {@link HandlerLetScopeTest}; the sentence is this engine's, so the class runs on the embedded engine alone.
 */
public class HandlerLetRefusalTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private String answer(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return String.valueOf(refused.getMessage());
    }

    @Test
    public void theReadIsANameThatResolvesToNothingWhereItIsWritten() {
        assertEquals("SQL compilation error: error line 1 at position 64\ninvalid identifier 'Y'", refusal(
            "EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN y; END; $$"));
        // A bind is placed at its colon, as any bind the block does not know is.
        assertEquals("SQL compilation error: error line 1 at position 71\ninvalid identifier 'Y'", refusal(
            "EXECUTE IMMEDIATE $$ BEGIN LET y NUMBER := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN :y; END; $$"));
        assertEquals("SQL compilation error: error line 1 at position 77\ninvalid identifier 'C'", refusal(
            "EXECUTE IMMEDIATE $$ BEGIN LET c CURSOR FOR SELECT 1; SELECT 1/0; EXCEPTION WHEN OTHER THEN OPEN c; "
                + "RETURN 'opened'; END; $$"));
    }

    @Test
    public void noHandlerOfTheBodyCatchesItAndTheCallerDoes() {
        assertEquals("SQL compilation error: error line 1 at position 70\ninvalid identifier 'Y'", refusal("""
            EXECUTE IMMEDIATE $$ BEGIN BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN y; END; \
            RETURN 'after'; EXCEPTION WHEN OTHER THEN RETURN 'outer caught it'; END; $$"""));
        engine.execute("""
            CREATE OR REPLACE PROCEDURE reads_let(p NUMBER) RETURNS NUMBER LANGUAGE SQL AS $$
            BEGIN LET y := p + 1; SELECT 1/0; EXCEPTION WHEN OTHER THEN RETURN y; END; $$""");
        assertEquals("caller: 904 42000", answer("""
            EXECUTE IMMEDIATE $$ BEGIN CALL reads_let(4); RETURN 'no'; EXCEPTION WHEN OTHER THEN \
            RETURN 'caller: ' || SQLCODE || ' ' || SQLSTATE; END; $$"""));
    }

    @Test
    public void aBodyTheHandlerRunsReadsItsOwnNames() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE own_y() RETURNS NUMBER LANGUAGE SQL AS $$
            DECLARE y NUMBER DEFAULT 3; BEGIN RETURN y; END; $$""");
        assertEquals("3", answer("""
            EXECUTE IMMEDIATE $$ DECLARE r NUMBER; BEGIN LET y := 5; SELECT 1/0; EXCEPTION WHEN OTHER THEN \
            CALL own_y() INTO :r; RETURN r; END; $$"""));
        // And once the handler has ended, the name is the BEGIN section's to read again.
        assertEquals("continued 5", answer("""
            EXECUTE IMMEDIATE $$ BEGIN LET y := 5; SELECT 1/0; RETURN 'continued ' || y; EXCEPTION \
            WHEN OTHER CONTINUE THEN LET z := 1; END; $$"""));
    }
}
