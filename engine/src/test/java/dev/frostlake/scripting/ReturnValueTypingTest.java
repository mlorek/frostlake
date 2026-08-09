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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A routine's result takes the static type of the expression the EXECUTED RETURN names,
 * live-verified. A literal, an arithmetic expression and a variable DECLAREd with a type all keep
 * their number; a name carrying no declared type — a FOR-loop counter — makes the result text, as
 * do the text-typed SQLCODE, SQLSTATE and SQLERRM.
 *
 * <p>Two parts catch people out. A declared RETURNS does NOT override this: a procedure declaring
 * {@code RETURNS INTEGER} still answers text when its RETURN names an untyped variable, so the rule
 * is about the expression rather than the signature. And only the RETURN that RUNS counts — there
 * is no whole-block fold, so a sibling RETURN in another branch, a nested block or an unfired
 * handler never changes the type of the one that executes.
 */
public class ReturnValueTypingTest extends BaseDatabaseTest {

    private Object returned(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        assertEquals(1, rs.getRowCount());
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void aLiteralKeepsItsNumber() {
        assertEquals(4L, ((Number) returned("BEGIN RETURN 4; END")).longValue());
    }

    @Test
    public void arithmeticKeepsItsNumber() {
        assertEquals(4L, ((Number) returned("BEGIN RETURN 2 + 2; END")).longValue());
    }

    @Test
    public void aDeclaredVariableKeepsItsNumber() {
        assertEquals(4L, ((Number) returned(
            "DECLARE x INTEGER; BEGIN x := 4; RETURN x; END")).longValue());
    }

    /** The loop counter is bound without a type, so the result degrades to text. */
    @Test
    public void aForLoopCounterReturnsText() {
        assertEquals("1", returned("BEGIN FOR i IN 1 TO 10 DO RETURN i; END FOR; RETURN -1; END"));
    }

    /** Routing the same counter through a typed variable first keeps the number. */
    @Test
    public void aCounterThroughATypedVariableKeepsItsNumber() {
        assertEquals(4L, ((Number) returned(
            "DECLARE x INTEGER; BEGIN FOR i IN 1 TO 10 DO IF (i = 4) THEN x := i; RETURN x;"
            + " END IF; END FOR; RETURN -1; END")).longValue());
    }

    @Test
    public void sqlcodeAndSqlstateReturnText() {
        assertEquals("100051",
            returned("BEGIN RETURN 1 / 0; EXCEPTION WHEN OTHER THEN RETURN SQLCODE; END"));
        assertEquals("22012",
            returned("BEGIN RETURN 1 / 0; EXCEPTION WHEN OTHER THEN RETURN SQLSTATE; END"));
    }

    /**
     * SQLROWCOUNT is the one script-supplied name that is NUMERIC — its siblings SQLCODE, SQLSTATE
     * and SQLERRM are text, so "a script variable" is not by itself a reason to expect either.
     */
    @Test
    public void sqlRowCountKeepsItsNumber() {
        engine.execute("CREATE TABLE rc (n INTEGER)");
        assertEquals(3L, ((Number) returned(
            "BEGIN INSERT INTO rc VALUES (1),(2),(3); RETURN SQLROWCOUNT; END")).longValue());
    }

    /** SQLERRM is text, and its value already is. */
    @Test
    public void sqlerrmReturnsText() {
        assertEquals("Division by zero",
            returned("BEGIN RETURN 1 / 0; EXCEPTION WHEN OTHER THEN RETURN SQLERRM; END"));
    }

    /** A routine PARAMETER is a declared name, so it keeps its signature type. */
    @Test
    public void aParameterKeepsItsDeclaredType() {
        engine.execute("CREATE PROCEDURE ret_param(v INTEGER) RETURNS INTEGER LANGUAGE SQL AS $$"
            + " BEGIN RETURN v; END $$");
        assertEquals(7L, ((Number) returned("CALL ret_param(7)")).longValue());

        engine.execute("CREATE PROCEDURE ret_param_text(v VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$"
            + " BEGIN RETURN v; END $$");
        assertEquals("x", returned("CALL ret_param_text('x')"));
    }

    @Test
    public void sqlcodeThroughATypedVariableKeepsItsNumber() {
        assertEquals(100051L, ((Number) returned(
            "DECLARE x INTEGER; BEGIN RETURN 1 / 0;"
            + " EXCEPTION WHEN OTHER THEN x := SQLCODE; RETURN x; END")).longValue());
    }

    /** A declared RETURNS does not rescue an untyped expression — the procedure still answers text. */
    @Test
    public void aDeclaredReturnsDoesNotOverrideTheExpression() {
        engine.execute("CREATE PROCEDURE ret_counter() RETURNS INTEGER LANGUAGE SQL AS $$"
            + " BEGIN FOR i IN 1 TO 10 DO IF (i = 4) THEN RETURN i; END IF; END FOR; RETURN -1; END $$");
        assertEquals("4", returned("CALL ret_counter()"));

        engine.execute("CREATE PROCEDURE ret_literal() RETURNS INTEGER LANGUAGE SQL AS $$"
            + " BEGIN RETURN 4; END $$");
        assertEquals(4L, ((Number) returned("CALL ret_literal()")).longValue());
    }

    /** RETURN of a name that holds nothing stays NULL rather than becoming the four letters. */
    @Test
    public void aNullValuedNameStaysNull() {
        assertEquals(null, returned("BEGIN FOR i IN 1 TO 1 DO NULL; END FOR; RETURN missing_name; END"));
    }

    // ── Mixed blocks: the type follows the RETURN that EXECUTES, live-verified. A sibling RETURN
    // elsewhere in the block — another branch, a nested block, a handler that never fires — does not
    // participate. There is no whole-block fold. ──────────────────────────────────────────────────

    @Test
    public void theTypeFollowsTheExecutedReturnNotItsSiblings() {
        assertEquals(4L, ((Number) returned(
            "BEGIN IF (1 = 1) THEN RETURN 4; END IF;"
            + " FOR i IN 1 TO 3 DO RETURN i; END FOR; RETURN -1; END")).longValue());
    }

    @Test
    public void anUnfiredHandlerReturnDoesNotChangeTheType() {
        assertEquals(1L, ((Number) returned(
            "BEGIN RETURN 1; EXCEPTION WHEN OTHER THEN RETURN SQLCODE; END")).longValue());
        assertEquals(4L, ((Number) returned(
            "BEGIN RETURN 2 + 2; EXCEPTION WHEN OTHER THEN RETURN 'boom'; END")).longValue());
    }

    @Test
    public void eachBranchKeepsItsOwnTypeWhereverBothAppear() {
        assertEquals(4L, ((Number) returned(
            "BEGIN IF (1 = 1) THEN RETURN 4; ELSE RETURN 'x'; END IF; END")).longValue());
        assertEquals("x", returned(
            "BEGIN IF (1 = 1) THEN RETURN 'x'; ELSE RETURN 4; END IF; END"));
        assertEquals(4L, ((Number) returned(
            "DECLARE v VARCHAR DEFAULT 'x';"
            + " BEGIN IF (1 = 1) THEN RETURN 4; ELSE RETURN v; END IF; END")).longValue());
        assertEquals(4L, ((Number) returned(
            "BEGIN IF (1 = 1) THEN RETURN 4; ELSE RETURN NULL; END IF; END")).longValue());
    }

    @Test
    public void aNestedBlocksCounterDoesNotAffectAnOuterReturn() {
        assertEquals(4L, ((Number) returned(
            "BEGIN IF (1 = 1) THEN RETURN 4; END IF;"
            + " BEGIN FOR i IN 1 TO 2 DO RETURN i; END FOR; END; RETURN -1; END")).longValue());
    }

    /** The same selection applies in a procedure: the counter branch text, the literal branch number. */
    @Test
    public void aProcedureMixedBlockFollowsTheExecutedReturnToo() {
        engine.execute("CREATE PROCEDURE ret_mixed() RETURNS INTEGER LANGUAGE SQL AS $$"
            + " BEGIN IF (1 = 1) THEN RETURN 4; END IF;"
            + " FOR i IN 1 TO 3 DO RETURN i; END FOR; RETURN -1; END $$");
        assertEquals(4L, ((Number) returned("CALL ret_mixed()")).longValue());
    }
}
