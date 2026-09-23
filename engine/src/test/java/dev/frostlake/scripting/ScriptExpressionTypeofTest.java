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
 * SYSTEM$TYPEOF in a block's own expression — a RETURN, a LET, DECLARE or assignment value, a condition — types
 * every name the block declares as a parameter of its declared type: a number is tagged by its declared
 * width, a text has no width of its own, a bare name reads the variable as its bound form does, and a FOR
 * counter, SQLROWCOUNT and a cursor record's field read the same way. A statement inside the block binds the
 * value instead. Every cell is live-verified.
 */
public class ScriptExpressionTypeofTest extends BaseDatabaseTest {

    private String block(final String body) {
        final Object value = engine.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$").getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    @Test
    public void aBoundNameIsTaggedByItsDeclaredWidth() {
        assertEquals("NUMBER(38,0)[SB16]", block("BEGIN LET x := 1; RETURN SYSTEM$TYPEOF(:x); END;"));
        assertEquals("NUMBER(38,0)[SB16]",
            block("BEGIN FOR i IN 1 TO 2 DO LET y := i; RETURN SYSTEM$TYPEOF(:y); END FOR; END;"));
        assertEquals("NUMBER(9,0)[SB4]", block("BEGIN FOR i IN 1 TO 2 DO RETURN SYSTEM$TYPEOF(:i); END FOR; END;"));
        assertEquals("NUMBER(38,0)[SB16]",
            block("DECLARE x NUMBER(10,0) DEFAULT 5; BEGIN LET y := x; RETURN SYSTEM$TYPEOF(:y); END;"));
        assertEquals("NUMBER(10,4)[SB8]",
            block("DECLARE x NUMBER(10,4) DEFAULT 1.7777; BEGIN RETURN SYSTEM$TYPEOF(:x); END;"));
        assertEquals("NUMBER(3,0)[SB2]", block("DECLARE x NUMBER(3,0) DEFAULT 5; BEGIN RETURN SYSTEM$TYPEOF(:x); END;"));
        assertEquals("FLOAT[DOUBLE]", block("BEGIN LET x := 1.5; RETURN SYSTEM$TYPEOF(:x); END;"));
        assertEquals("DATE[SB4]", block("BEGIN LET d := CURRENT_DATE(); RETURN SYSTEM$TYPEOF(:d); END;"));
        assertEquals("BOOLEAN[SB1]", block("BEGIN LET b := TRUE; RETURN SYSTEM$TYPEOF(:b); END;"));
        assertEquals("NUMBER(1,0)[SB1]", block("BEGIN LET x := 1; RETURN SYSTEM$TYPEOF(1); END;"));
    }

    @Test
    public void aBareNameReadsTheVariable() {
        assertEquals("NUMBER(38,0)[SB16]", block("BEGIN LET x := 1; RETURN SYSTEM$TYPEOF(x); END;"));
        assertEquals("NUMBER(38,0)[SB16]",
            block("BEGIN FOR i IN 1 TO 2 DO LET y := i; RETURN SYSTEM$TYPEOF(y); END FOR; END;"));
        assertEquals("NUMBER(9,0)[SB4]", block("BEGIN FOR i IN 1 TO 2 DO RETURN SYSTEM$TYPEOF(i); END FOR; END;"));
        assertEquals("NUMBER(10,4)[SB8]",
            block("DECLARE x NUMBER(10,4) DEFAULT 1.7777; BEGIN RETURN SYSTEM$TYPEOF(x); END;"));
        assertEquals("NUMBER(3,0)[SB2]", block("DECLARE x NUMBER(3,0) DEFAULT 5; BEGIN RETURN SYSTEM$TYPEOF(x); END;"));
    }

    @Test
    public void aTextNameHasNoWidthOfItsOwn() {
        assertEquals("VARCHAR[LOB]", block("BEGIN LET s := 'abc'; RETURN SYSTEM$TYPEOF(:s); END;"));
        assertEquals("VARCHAR[LOB]", block("DECLARE s VARCHAR(10) DEFAULT 'abc'; BEGIN RETURN SYSTEM$TYPEOF(:s); END;"));
        assertEquals("VARCHAR[LOB]", block("DECLARE s VARCHAR DEFAULT 'abc'; BEGIN RETURN SYSTEM$TYPEOF(s); END;"));
        assertEquals("VARCHAR(134217728)[LOB]", block("BEGIN LET s := 'abc'; RETURN SYSTEM$TYPEOF(s || 'x'); END;"));
    }

    @Test
    public void expressionsOverNamesKeepNoInterval() {
        assertEquals("NUMBER(38,0)[SB16]", block("BEGIN LET x := 1; RETURN SYSTEM$TYPEOF(:x + 1); END;"));
        assertEquals("NUMBER(38,0)[SB16]", block("BEGIN LET x := 1; RETURN SYSTEM$TYPEOF(x + 1); END;"));
        assertEquals("NUMBER(38,0)[SB16]", block("BEGIN LET x := 100000; RETURN SYSTEM$TYPEOF(-x); END;"));
        assertEquals("NUMBER(38,0)[SB16]",
            block("DECLARE x NUMBER(38,0) DEFAULT 1; BEGIN RETURN SYSTEM$TYPEOF(COALESCE(:x, 0)); END;"));
        assertEquals("NUMBER(38,0)[SB16]",
            block("BEGIN LET x := 1; LET y := 2; RETURN SYSTEM$TYPEOF(IFF(TRUE, :x, :y)); END;"));
        assertEquals("NUMBER(10,0)[SB8]", block("BEGIN LET x := 1; RETURN SYSTEM$TYPEOF(:x::NUMBER(10,0)); END;"));
    }

    @Test
    public void specialNamesAndRecordsReadTheSameWay() {
        engine.execute("CREATE OR REPLACE TABLE setf_t (a NUMBER(38,0))");
        engine.execute("INSERT INTO setf_t VALUES (1), (2)");
        assertEquals("VARCHAR[LOB]", block("BEGIN RETURN SYSTEM$TYPEOF(SQLROWCOUNT); END;"));
        assertEquals("VARCHAR[LOB]",
            block("BEGIN INSERT INTO setf_t VALUES (3); RETURN SYSTEM$TYPEOF(SQLROWCOUNT); END;"));
        assertEquals("NUMBER(19,5)[SB16]",
            block("BEGIN INSERT INTO setf_t VALUES (3); RETURN SYSTEM$TYPEOF(SQLROWCOUNT + 1); END;"));
        assertEquals("VARCHAR[LOB]",
            block("BEGIN INSERT INTO setf_t VALUES (3); LET n := SQLROWCOUNT; RETURN SYSTEM$TYPEOF(:n); END;"));
        assertEquals("5|VARCHAR[LOB]", block("BEGIN INSERT INTO setf_t VALUES (3); LET n := SQLROWCOUNT; n := 5;"
            + " RETURN n || '|' || SYSTEM$TYPEOF(:n); END;"));
        assertEquals("NUMBER(38,0)[SB16]", block("DECLARE c1 CURSOR FOR SELECT a FROM setf_t;"
            + " BEGIN FOR rec IN c1 DO RETURN SYSTEM$TYPEOF(rec.a); END FOR; END;"));
        assertEquals("NUMBER(38,0)[SB16]", block("DECLARE c1 CURSOR FOR SELECT a FROM setf_t;"
            + " BEGIN FOR rec IN c1 DO RETURN SYSTEM$TYPEOF(rec.a + 1); END FOR; END;"));
    }

    @Test
    public void everyScriptingPositionTypesTheSameWay() {
        assertEquals("yes", block("BEGIN LET x := 1; IF (SYSTEM$TYPEOF(:x) = 'NUMBER(38,0)[SB16]') THEN RETURN 'yes';"
            + " END IF; RETURN 'no'; END;"));
        assertEquals("is NUMBER(38,0)[SB16]", block("BEGIN LET x := 1; RETURN 'is ' || SYSTEM$TYPEOF(:x); END;"));
        assertEquals("NUMBER(38,0)[SB16]",
            block("BEGIN LET x := 1; LET t VARCHAR := ''; t := SYSTEM$TYPEOF(:x); RETURN t; END;"));
        assertEquals("NUMBER(38,0)[SB16]", block("BEGIN LET x := 1; LET t VARCHAR := SYSTEM$TYPEOF(:x); RETURN t; END;"));
        assertEquals("NUMBER(38,0)[SB16]", block("BEGIN LET x := 1; LET t VARCHAR := SYSTEM$TYPEOF(x); RETURN t; END;"));
        assertEquals("NUMBER(1,0)[SB1]", block("BEGIN LET t := SYSTEM$TYPEOF(1); RETURN t; END;"));
        assertEquals("NUMBER(1,0)[SB1]", block("DECLARE t DEFAULT SYSTEM$TYPEOF(1); BEGIN RETURN t; END;"));
        assertEquals("NUMBER(1,0)[SB1]", block("DECLARE t VARCHAR DEFAULT SYSTEM$TYPEOF(1); BEGIN RETURN t; END;"));
        assertEquals("NUMBER(1,0)[SB1]x", block("BEGIN LET t := SYSTEM$TYPEOF(1) || 'x'; RETURN t; END;"));
        assertEquals("NUMBER(1,0)[SB1]", block("BEGIN LET t := UPPER(SYSTEM$TYPEOF(1)); RETURN t; END;"));
    }

    @Test
    public void aProcedureParameterIsOneOfTheNames() {
        engine.execute("CREATE OR REPLACE PROCEDURE setf_p(a NUMBER(10,2)) RETURNS VARCHAR LANGUAGE SQL AS"
            + " $$ BEGIN RETURN SYSTEM$TYPEOF(:a); END; $$");
        assertEquals("NUMBER(10,2)[SB8]", String.valueOf(engine.executeQuery("CALL setf_p(1.5)").getRows().get(0).getValue(0)));
    }

    /** A statement inside the block binds the value, and a scalar subquery in the expression is one. */
    @Test
    public void aStatementBindsTheValue() {
        assertEquals("NUMBER(10,4)[SB2]", block("DECLARE x NUMBER(10,4) DEFAULT 1.7777; t VARCHAR;"
            + " BEGIN t := (SELECT SYSTEM$TYPEOF(:x)); RETURN t; END;"));
        assertEquals("NUMBER(38,0)[SB1]", block("BEGIN LET x := 1; RETURN SYSTEM$TYPEOF((SELECT :x)); END;"));
    }
}
