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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ALTER TABLE ADD COLUMN judges a DEFAULT far more strictly than CREATE TABLE does, and speaks the
 * INSERT surface's vocabulary while doing it. Two rules, both live-measured:
 *
 * <ul>
 *   <li>The default must be a BARE LITERAL. A cast, arithmetic, a function call and even
 *       {@code CURRENT_TIMESTAMP()} — all of which CREATE TABLE accepts — are refused outright, the
 *       offending expression echoed in brackets and a {@code ::} cast echoed in its CAST spelling.</li>
 *   <li>The literal's own type must be in the column's FAMILY, with no coercion whatsoever. So
 *       {@code BOOLEAN DEFAULT 'true'} and {@code DATE DEFAULT 7} are refused here though CREATE TABLE
 *       takes both, and a DATE or TIMESTAMP column accepts NO literal at all except NULL.</li>
 * </ul>
 *
 * <p>Only the family is judged — neither a string's length nor a number's precision is. VARIANT takes
 * no part in the family rule and falls through to CREATE TABLE's coercion sentence instead.
 */
public class AddColumnDefaultTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ac_t (id NUMBER)");
    }

    private void assertRefused(final String columnDef, final String expected) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE ac_t ADD COLUMN " + columnDef);
            }
        }, columnDef);
        assertEquals(expected, ex.getMessage(), columnDef);
    }

    private void assertAccepted(final String columnDef) {
        engine.execute("ALTER TABLE ac_t ADD COLUMN " + columnDef);
    }

    private String mistyped(final String expecting, final String got, final String column) {
        return "SQL compilation error:\nExpression type does not match column data type, expecting "
            + expecting + " but got " + got + " for column " + column;
    }

    private String invalidExpression(final String expression) {
        return "SQL compilation error:\nInvalid column default expression [" + expression + "]";
    }

    /** No computed default of any shape reaches an added column. */
    @Test
    public void aComputedDefaultIsRefusedOutright() {
        assertRefused("c1 NUMBER DEFAULT 1 + 1", invalidExpression("1 + 1"));
        assertRefused("c2 NUMBER DEFAULT LENGTH('abc')", invalidExpression("LENGTH('abc')"));
        assertRefused("c3 VARCHAR(9) DEFAULT UPPER('a')", invalidExpression("UPPER('a')"));
    }

    /** Including the one CREATE TABLE is happiest with. */
    @Test
    public void evenCurrentTimestampIsRefused() {
        assertRefused("c4 TIMESTAMP_NTZ DEFAULT CURRENT_TIMESTAMP()",
            invalidExpression("CURRENT_TIMESTAMP()"));
    }

    /** A cast is echoed in its CAST spelling, not as it was written. */
    @Test
    public void aCastIsEchoedInItsCastSpelling() {
        assertRefused("c5 DATE DEFAULT '2026-01-01'::DATE",
            invalidExpression("CAST('2026-01-01' AS DATE)"));
    }

    /** A literal of the wrong family is refused, both types spelled with their parameters. */
    @Test
    public void aLiteralOfTheWrongFamilyIsRefused() {
        assertRefused("c6 DATE DEFAULT 7", mistyped("DATE", "NUMBER(1,0)", "C6"));
        assertRefused("c7 BOOLEAN DEFAULT 1", mistyped("BOOLEAN", "NUMBER(1,0)", "C7"));
        assertRefused("c8 VARCHAR(9) DEFAULT 7", mistyped("VARCHAR(9)", "NUMBER(1,0)", "C8"));
        assertRefused("c9 VARCHAR(9) DEFAULT 7.5", mistyped("VARCHAR(9)", "NUMBER(2,1)", "C9"));
    }

    /** A string literal is measured by its own length, a boolean and a binary by themselves. */
    @Test
    public void theLiteralIsSpelledWithItsOwnMeasurements() {
        assertRefused("d1 NUMBER(5,1) DEFAULT 'abc'", mistyped("NUMBER(5,1)", "VARCHAR(3)", "D1"));
        assertRefused("d2 BOOLEAN DEFAULT 'true'", mistyped("BOOLEAN", "VARCHAR(4)", "D2"));
        assertRefused("d3 NUMBER(5,1) DEFAULT TRUE", mistyped("NUMBER(5,1)", "BOOLEAN", "D3"));
        assertRefused("d4 NUMBER(5,1) DEFAULT X'AB'", mistyped("NUMBER(5,1)", "BINARY(1)", "D4"));
    }

    /** A datetime column accepts no literal at all — only NULL. */
    @Test
    public void aDatetimeColumnAcceptsOnlyNull() {
        assertRefused("d5 DATE DEFAULT '2026-01-01'", mistyped("DATE", "VARCHAR(10)", "D5"));
        assertAccepted("d6 DATE DEFAULT NULL");
    }

    /** Only the FAMILY is judged: a length or precision that overflows is not this rule's concern. */
    @Test
    public void neitherLengthNorPrecisionIsJudged() {
        assertAccepted("e1 VARCHAR(2) DEFAULT 'toolong'");
        assertAccepted("e2 NUMBER(5,1) DEFAULT 123456");
        assertAccepted("e3 NUMBER(5,1) DEFAULT 7.55");
        assertAccepted("e4 FLOAT DEFAULT 7");
        assertAccepted("e5 BOOLEAN DEFAULT TRUE");
        assertAccepted("e6 VARCHAR(9) DEFAULT 'ok'");
    }

    /** VARIANT sits out the family rule and falls through to CREATE TABLE's coercion sentence. */
    @Test
    public void variantFallsThroughToTheCoercionRule() {
        assertAccepted("f1 VARIANT DEFAULT 'z'");
        assertRefused("f2 VARIANT DEFAULT 7", "SQL compilation error:\nDefault value data type"
            + " does not match data type for column F2");
    }

    /** OBJECT, ARRAY and the geospatial and FILE families judge nothing at all here. */
    @Test
    public void theSemiStructuredFamiliesTakeAnyLiteral() {
        assertAccepted("g1 OBJECT DEFAULT 7");
        assertAccepted("g2 ARRAY DEFAULT 'abc'");
        assertAccepted("g3 GEOGRAPHY DEFAULT TRUE");
        assertAccepted("g4 GEOMETRY DEFAULT 7");
        assertAccepted("g5 FILE DEFAULT 'abc'");
    }

    /** MAP and VECTOR are the opposite — they accept NO literal, and spell themselves in full. */
    @Test
    public void mapAndVectorAcceptNoLiteralAtAll() {
        assertRefused("h1 MAP(VARCHAR,NUMBER) DEFAULT 'abc'",
            mistyped("MAP(VARCHAR(16777216), NUMBER(38,0))", "VARCHAR(3)", "H1"));
        assertRefused("h2 MAP(VARCHAR,NUMBER) DEFAULT 7",
            mistyped("MAP(VARCHAR(16777216), NUMBER(38,0))", "NUMBER(1,0)", "H2"));
        assertRefused("h3 VECTOR(FLOAT,3) DEFAULT 'abc'",
            mistyped("VECTOR(FLOAT, 3)", "VARCHAR(3)", "H3"));
        assertRefused("h4 VECTOR(FLOAT,3) DEFAULT TRUE",
            mistyped("VECTOR(FLOAT, 3)", "BOOLEAN", "H4"));
        assertAccepted("h5 VECTOR(FLOAT,3) DEFAULT NULL");
    }

    /** A NEGATIVE numeric literal is a literal, not a computed expression. */
    @Test
    public void aNegativeLiteralIsAccepted() {
        assertAccepted("n1 NUMBER DEFAULT -1");
        assertAccepted("n2 NUMBER(5,1) DEFAULT -7.5");
    }

    /** And a BINARY column still takes no default at all, in its own sentence. */
    @Test
    public void aBinaryColumnStillTakesNoDefault() {
        assertRefused("f3 BINARY(4) DEFAULT X'AB'",
            "SQL compilation error: Default values are not allowed on 'BINARY' columns.");
    }
}
