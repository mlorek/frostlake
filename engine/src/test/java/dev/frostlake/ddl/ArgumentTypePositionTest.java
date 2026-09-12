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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The compilation prefix and its POSITION, for the families a sweep of the engine's bare throws
 * measured against live.
 *
 * <p>★ THE RULE: the prefix marks a refusal live raises while COMPILING. A ROW-time refusal stays bare
 * on both engines — "Numeric value 'abc' is not recognized" and "Division by zero" are measured
 * prefix-less — so a bare throw is not itself a bug, and each family has to be measured rather than
 * given a prefix on principle.
 *
 * <p>★ THE ARGUMENT-TYPE FAMILY IS POSITIONED, not merely prefixed, and the position is the CALL's own
 * offset rather than the statement's start — it moves when the call does:
 *
 * <pre>
 *   SELECT IFF(g, 1, 2) FROM rt      error line 1 at position 7
 *   SELECT 1, IFF(g, 1, 2) FROM rt   error line 1 at position 10
 * </pre>
 *
 * <p>★ A ROLE THAT DOES NOT EXIST NAMES NOTHING. Live answers "Object does not exist, or operation
 * cannot be performed." for USE ROLE, where its other object-not-found sentences quote the name and its
 * kind — a role a session cannot use is indistinguishable from one that is absent, so the message
 * cannot confirm either.
 *
 * <p>★ THE OBJECT-NOT-FOUND FAMILY ALREADY AGREED and is pinned here so it stays that way: the name is
 * FULLY QUALIFIED, upper-cased, and the sentence ends with a full stop.
 */
public class ArgumentTypePositionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (n NUMBER(10,2), g VARCHAR(10), i INT)");
        engine.execute("INSERT INTO rt VALUES (1.00, 'x', 1)");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ").append(String.valueOf(rs.getValue(0)));
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String statement(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** ★ The argument-type refusal carries the prefix AND the call's own position. */
    @Test
    public void theargumentTypeRefusalIsPositionedAtTheCall() {
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for"
            + " function 'IFF': (VARCHAR(10), NUMBER(1,0), NUMBER(1,0))",
            answer("SELECT IFF(g, 1, 2) FROM rt"));
    }

    /** ★ THE POSITION MOVES WITH THE CALL — this is the cell a fixed prefix would fail. */
    @Test
    public void thepositionFollowsTheCallRatherThanTheStatement() {
        assertEquals("SQL compilation error: error line 1 at position 10|Invalid argument types for"
            + " function 'IFF': (VARCHAR(10), NUMBER(1,0), NUMBER(1,0))",
            answer("SELECT 1, IFF(g, 1, 2) FROM rt"));
    }

    /** An aggregate over a semi-structured argument is positioned the same way. */
    @Test
    public void theaggregateArgumentTypeRefusalAgrees() {
        assertEquals("SQL compilation error: error line 1 at position 7|Invalid argument types for"
            + " function 'SUM': (ARRAY)",
            answer("SELECT SUM(ARRAY_CONSTRUCT(1, 2)) FROM rt"));
    }

    /** ★ USE ROLE over a missing role names NOTHING. */
    @Test
    public void ausemissingRoleNamesNothing() {
        assertEquals("SQL compilation error:|Object does not exist, or operation cannot be performed.",
            statement("USE ROLE nosuchrole"));
    }

    /** The object-not-found family: fully qualified, upper-cased, full stop. */
    @Test
    public void theobjectNotFoundFamilyKeepsItsShape() {
        assertEquals("SQL compilation error:|Stage 'TEST_DB.TEST_SCHEMA.NOSUCHSTAGE' does not exist"
            + " or not authorized.", statement("DROP STAGE nosuchstage"));
        assertEquals("SQL compilation error:|Sequence 'TEST_DB.TEST_SCHEMA.NOSUCHSEQUENCE' does not"
            + " exist or not authorized.", statement("DROP SEQUENCE nosuchsequence"));
        assertEquals("SQL compilation error:|Task 'TEST_DB.TEST_SCHEMA.NOSUCHTASK' does not exist"
            + " or not authorized.", statement("DROP TASK nosuchtask"));
        assertEquals("SQL compilation error:|Warehouse 'NOSUCHWH' does not exist or not authorized.",
            statement("ALTER WAREHOUSE nosuchwh RESUME"),
            "a warehouse is account-level, so its name carries no schema");
    }

    /** An unknown function, with its trailing full stop. */
    @Test
    public void anunknownFunctionKeepsItsSentence() {
        assertEquals("SQL compilation error:|Unknown function NOSUCHFUNCTION.",
            answer("SELECT NOSUCHFUNCTION(1) FROM rt"));
        assertEquals("SQL compilation error:|Unknown function SYSTEM$NOSUCHFUNC.",
            answer("SELECT SYSTEM$NOSUCHFUNC()"));
    }

    /** ★ THE ROW-TIME FAMILY STAYS BARE on both engines — the control the rule rests on. */
    @Test
    public void therowTimeFamilyCarriesNoPrefix() {
        assertEquals("Numeric value 'abc' is not recognized", answer("SELECT 'abc'::NUMBER FROM rt"));
        assertEquals("Division by zero", answer("SELECT 1 / 0 FROM rt"));
    }
}
