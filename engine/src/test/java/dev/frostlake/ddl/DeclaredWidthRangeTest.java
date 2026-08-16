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
 * The RANGE a declared type parameter has to fall in.
 *
 * <p>★ A ZERO WIDTH WAS ACCEPTED, and the consequence was worse than the acceptance: Frostlake then
 * INSERTED values into a column declared to hold no characters, so an {@code 'x'} sat in a
 * {@code VARCHAR(0)}. Live has no such column because it never creates one.
 *
 * <p>★ ZERO IS REFUSED PER FAMILY, NOT GENERALLY. A string and a binary start at 1; a NUMBER's
 * precision starts at 0, so {@code NUMBER(0)} and {@code NUMBER(0,0)} are perfectly legal and a
 * time scale of 0 is legal too. There is no "widths must be positive" rule to write — each family
 * states its own bounds, in its own sentence, and the sentences disagree about the lower one.
 *
 * <p>★ THE CEILING WAS MISSING TOO, and it had to be, because the sentence names the whole range:
 * writing "Must be between 1 and 134,217,728" while accepting 134,217,729 would refuse a value on
 * one side of a bound and accept it on the other. The character ceiling is the 128MB unknown length,
 * the binary ceiling is half that, and a NUMBER's scale stops ONE SHORT of its precision's — 38 is a
 * legal precision and an illegal scale.
 *
 * <p>★ THE POSITION IS THE LITERAL'S OWN, not the parenthesis before it: live points at the digit
 * even when whitespace separates the two. A CAST reaches the same check through the expression AST,
 * where the offset is an offset into the FRAGMENT, so it is the resolved statement position that has
 * to be reported — which is what makes the CTAS and view cells below the ones worth having.
 *
 * <p>★ THE ORDER IS FIXED where more than one thing is wrong: precision, then scale, then
 * scale-exceeds-precision, and the FIRST offending column of a list wins — over a later bad width and
 * over a duplicate column name alike.
 *
 * <p>NOT FIXED HERE, and tracked separately: a NEGATIVE width, which live refuses with these same
 * sentences at an impossible line 0 position 0 while Frostlake's grammar refuses the minus sign as a
 * syntax error; a scripting DECLARE, where Frostlake wraps the refusal as an uncaught statement
 * exception because the block is not compiled before it runs; and a duplicate column name or an
 * unknown type NAME, neither of which Frostlake refuses the way live does.
 */
public class DeclaredWidthRangeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE dw (a INT, s VARCHAR(5))");
        engine.execute("CREATE OR REPLACE TABLE dw_empty (s VARCHAR(5))");
    }

    /** What a statement answers: ACCEPTED, or its refusal with the newlines shown. */
    private String answer(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The same for a query, DRAINED — so a refusal that only fires per row would show as ACCEPTED. */
    private String queried(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            while (rs.next()) {
                rs.getValue(0);
            }
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String positioned(final int line, final int position, final String detail) {
        return "SQL compilation error: error line " + line + " at position " + position + "|" + detail;
    }

    private String characterLength(final int position, final String declared) {
        return positioned(1, position, "Invalid character length: " + declared
            + ". Must be between 1 and 134,217,728.");
    }

    private String binaryLength(final int position, final String declared) {
        return positioned(1, position, "Invalid binary length: " + declared
            + ". Must be between 1 and 67,108,864.");
    }

    /** ★ Every character spelling refuses a zero, at its own literal. */
    @Test
    public void azeroCharacterLengthIsRefused() {
        assertEquals(characterLength(38, "0"),
            answer("CREATE OR REPLACE TABLE zw (c VARCHAR(0))"));
        assertEquals(characterLength(35, "0"),
            answer("CREATE OR REPLACE TABLE zw (c CHAR(0))"));
        assertEquals(characterLength(37, "0"),
            answer("CREATE OR REPLACE TABLE zw (c STRING(0))"));
        assertEquals(characterLength(35, "0"),
            answer("CREATE OR REPLACE TABLE zw (c TEXT(0))"));
        assertEquals(characterLength(39, "0"),
            answer("CREATE OR REPLACE TABLE zw (c NVARCHAR(0))"));
        assertEquals(characterLength(36, "0"),
            answer("CREATE OR REPLACE TABLE zw (c NCHAR(0))"));
    }

    /** ★ And the binary family, with a sentence of its own naming a different ceiling. */
    @Test
    public void azeroBinaryLengthIsRefused() {
        assertEquals(binaryLength(37, "0"),
            answer("CREATE OR REPLACE TABLE zw (c BINARY(0))"));
        assertEquals(binaryLength(40, "0"),
            answer("CREATE OR REPLACE TABLE zw (c VARBINARY(0))"));
    }

    /** ★ A NUMBER's precision starts at ZERO, so the same digit is legal here. */
    @Test
    public void azeroNumberPrecisionIsAccepted() {
        assertEquals("ACCEPTED", answer("CREATE OR REPLACE TABLE zw (c NUMBER(0))"));
        assertEquals("ACCEPTED", answer("CREATE OR REPLACE TABLE zw (c NUMBER(0,0))"));
        assertEquals("ACCEPTED", answer("CREATE OR REPLACE TABLE zw (c DECIMAL(0))"));
        assertEquals("ACCEPTED", answer("CREATE OR REPLACE TABLE zw (c NUMERIC(0))"));
        assertEquals("ACCEPTED", answer("CREATE OR REPLACE TABLE zw (c TIME(0))"),
            "and a temporal scale of zero, which means no fractional seconds at all");
        assertEquals("ACCEPTED", answer("CREATE OR REPLACE TABLE zw (c TIMESTAMP_NTZ(0))"));
    }

    /** ★ The CEILING of each family: the last legal width, then the first illegal one. */
    @Test
    public void aceilingIsEnforcedTheSameWay() {
        assertEquals("ACCEPTED", answer("CREATE OR REPLACE TABLE zw (c VARCHAR(134217728))"));
        assertEquals(characterLength(38, "134,217,729"),
            answer("CREATE OR REPLACE TABLE zw (c VARCHAR(134217729))"),
            "the value is read back with thousands separators");
        assertEquals("ACCEPTED", answer("CREATE OR REPLACE TABLE zw (c BINARY(67108864))"));
        assertEquals(binaryLength(37, "67,108,865"),
            answer("CREATE OR REPLACE TABLE zw (c BINARY(67108865))"));
    }

    /** A width past an INT's range is still just a value, named in full rather than crashing. */
    @Test
    public void awidthPastAnIntIsAValueLikeAnyOther() {
        assertEquals(characterLength(38, "1,000,000,000,000"),
            answer("CREATE OR REPLACE TABLE zw (c VARCHAR(1000000000000))"));
    }

    /** ★ A NUMBER's two parameters have two ranges and two sentences, checked in that order. */
    @Test
    public void anumbersPrecisionAndScaleHaveTheirOwnRanges() {
        assertEquals("ACCEPTED", answer("CREATE OR REPLACE TABLE zw (c NUMBER(38,0))"));
        assertEquals(positioned(1, 37, "Invalid number precision: 39. Must be between 0 and 38."),
            answer("CREATE OR REPLACE TABLE zw (c NUMBER(39))"));
        assertEquals("ACCEPTED", answer("CREATE OR REPLACE TABLE zw (c NUMBER(38,37))"),
            "the scale stops one short of the precision's own ceiling");
        assertEquals(positioned(1, 40, "Invalid number scale: 38. Must be between 0 and 37."),
            answer("CREATE OR REPLACE TABLE zw (c NUMBER(38,38))"));
        assertEquals(positioned(1, 37, "Invalid number precision: 39. Must be between 0 and 38."),
            answer("CREATE OR REPLACE TABLE zw (c NUMBER(39,40))"),
            "with both wrong the precision is reported, being checked first");
        assertEquals(positioned(1, 39, "Invalid number scale: 38. Must be between 0 and 37."),
            answer("CREATE OR REPLACE TABLE zw (c NUMBER(5,38))"));
    }

    /** ★ A scale larger than its precision is a THIRD sentence — lower-cased, and carrying no position. */
    @Test
    public void ascaleLargerThanItsPrecisionIsItsOwnSentence() {
        assertEquals("SQL compilation error:|invalid data type specification (6>5)",
            answer("CREATE OR REPLACE TABLE zw (c NUMBER(5,6))"));
        assertEquals("SQL compilation error:|invalid data type specification (5>0)",
            answer("CREATE OR REPLACE TABLE zw (c NUMBER(0,5))"));
        assertEquals("SQL compilation error:|invalid data type specification (5>2)",
            queried("SELECT CAST(1 AS NUMBER(2,5))"),
            "and a cast target is held to it too");
    }

    /** The temporal scales, which name the family rather than the spelling. */
    @Test
    public void atemporalScaleStopsAtNine() {
        assertEquals(positioned(1, 35, "Invalid time scale: 10. Must be between 0 and 9."),
            answer("CREATE OR REPLACE TABLE zw (c TIME(10))"));
        assertEquals(positioned(1, 44, "Invalid timestamp scale: 10. Must be between 0 and 9."),
            answer("CREATE OR REPLACE TABLE zw (c TIMESTAMP_NTZ(10))"));
        assertEquals(positioned(1, 44, "Invalid timestamp scale: 10. Must be between 0 and 9."),
            answer("CREATE OR REPLACE TABLE zw (c TIMESTAMP_LTZ(10))"));
        assertEquals(positioned(1, 40, "Invalid timestamp scale: 10. Must be between 0 and 9."),
            answer("CREATE OR REPLACE TABLE zw (c TIMESTAMP(10))"));
        assertEquals(positioned(1, 39, "Invalid timestamp scale: 10. Must be between 0 and 9."),
            answer("CREATE OR REPLACE TABLE zw (c DATETIME(10))"),
            "DATETIME resolves to a TIMESTAMP and is held to the TIMESTAMP sentence");
    }

    /** ★ A VECTOR's dimension is shaped unlike the rest — the value is quoted, and unpositioned. */
    @Test
    public void avectorDimensionIsQuotedAndUnpositioned() {
        assertEquals("SQL compilation error:|Invalid vector dimension '0'.",
            answer("CREATE OR REPLACE TABLE zw (c VECTOR(FLOAT, 0))"));
        assertEquals("SQL compilation error:|Invalid vector dimension '4,097'.",
            answer("CREATE OR REPLACE TABLE zw (c VECTOR(FLOAT, 4097))"));
        assertEquals("ACCEPTED", answer("CREATE OR REPLACE TABLE zw (c VECTOR(FLOAT, 4096))"));
    }

    /** ★ The position is the LITERAL's, wherever the column sits and however it is spaced. */
    @Test
    public void thepositionIsTheLiteralsOwn() {
        assertEquals(characterLength(47, "0"),
            answer("CREATE OR REPLACE TABLE nl_p (a INT, c VARCHAR(0))"),
            "a later column carries its own offset");
        assertEquals(characterLength(41, "0"),
            answer("CREATE OR REPLACE TABLE nl_p2 (a VARCHAR(0), c VARCHAR(0))"),
            "and with two of them the FIRST wins");
        assertEquals(characterLength(28, "0"),
            queried("SELECT CAST('x' AS VARCHAR( 0 ))"),
            "the digit, not the parenthesis — whitespace between them moves it");
    }

    /** A multi-line statement reports the literal's own LINE, counted from one. */
    @Test
    public void amultiLineStatementReportsItsOwnLine() {
        assertEquals(positioned(3, 12,
                "Invalid character length: 0. Must be between 1 and 134,217,728."),
            answer("CREATE OR REPLACE TABLE nl_ml (\n  a INT,\n  c VARCHAR(0)\n)"));
    }

    /** ★ The width outranks the other things a column list can be wrong about. */
    @Test
    public void thewidthIsCheckedBeforeTheRestOfTheColumnList() {
        assertEquals(characterLength(38, "0"),
            answer("CREATE OR REPLACE TABLE zc (c VARCHAR(0), c INT)"),
            "over a duplicate column name");
        assertEquals(characterLength(38, "0"),
            answer("CREATE OR REPLACE TABLE za (a VARCHAR(0), b NUMBER(39))"));
        assertEquals(positioned(1, 37, "Invalid number precision: 39. Must be between 0 and 38."),
            answer("CREATE OR REPLACE TABLE zb (a NUMBER(39), b VARCHAR(0))"),
            "whichever family it is, the leftmost one is reported");
    }

    /** Every ALTER form that names a type is held to the same rule. */
    @Test
    public void alterIsHeldToItToo() {
        assertEquals(characterLength(36, "0"),
            answer("ALTER TABLE dw ADD COLUMN c VARCHAR(0)"));
        assertEquals(characterLength(52, "0"),
            answer("ALTER TABLE dw ALTER COLUMN s SET DATA TYPE VARCHAR(0)"));
        assertEquals(characterLength(43, "0"),
            answer("ALTER TABLE dw ALTER COLUMN s TYPE VARCHAR(0)"));
    }

    /** ★ A CAST target, whose offset is an offset into the EXPRESSION and has to be resolved back. */
    @Test
    public void acastTargetReportsTheStatementsPosition() {
        assertEquals(characterLength(20, "0"), queried("SELECT 'x'::VARCHAR(0)"));
        assertEquals(characterLength(27, "0"), queried("SELECT CAST('x' AS VARCHAR(0))"));
        assertEquals(characterLength(24, "0"), queried("SELECT CAST('x' AS CHAR(0))"));
        assertEquals(binaryLength(44, "0"),
            queried("SELECT CAST(TO_BINARY('61','HEX') AS BINARY(0))"));
        assertEquals(characterLength(28, "0"), queried("SELECT CAST(NULL AS VARCHAR(0))"));
        assertEquals(characterLength(31, "0"), queried("SELECT TRY_CAST('x' AS VARCHAR(0))"),
            "TRY_CAST tries the VALUE, never the target");
    }

    /** ★ And it is a COMPILE-time refusal: an empty table still refuses, with no row to fail on. */
    @Test
    public void anemptyTableIsStillRefused() {
        assertEquals(characterLength(25, "0"),
            queried("SELECT CAST(s AS VARCHAR(0)) FROM dw_empty"));
    }

    /** A routine's parameter and its return type are both declared types. */
    @Test
    public void aroutineSignatureIsHeldToIt() {
        assertEquals(characterLength(42, "0"),
            answer("CREATE OR REPLACE FUNCTION nl_f(x VARCHAR(0)) RETURNS INT AS 'SELECT 1'"));
        assertEquals(characterLength(55, "0"),
            answer("CREATE OR REPLACE FUNCTION nl_g(x INT) RETURNS VARCHAR(0) AS 'SELECT ''a'''"));
    }

    /** ★ A CTAS and a VIEW re-parse their body, so both origins have to compose. */
    @Test
    public void abodyReParsedFromItsTextStillReportsTheStatementsPosition() {
        assertEquals(characterLength(52, "0"),
            answer("CREATE OR REPLACE TABLE nl_c AS SELECT 'x'::VARCHAR(0) AS c"));
        assertEquals(characterLength(51, "0"),
            answer("CREATE OR REPLACE VIEW nl_v AS SELECT 'x'::VARCHAR(0) AS c"));
    }

    /** A width nested inside a structured type is the same width. */
    @Test
    public void anestedFieldIsHeldToIt() {
        assertEquals(characterLength(50, "0"),
            answer("CREATE OR REPLACE TABLE nl_s1 (c OBJECT(x VARCHAR(0)))"));
        assertEquals(characterLength(47, "0"),
            answer("CREATE OR REPLACE TABLE nl_s2 (c ARRAY(VARCHAR(0)))"));
        assertEquals(characterLength(37, "0"),
            queried("SELECT CAST(NULL AS OBJECT(x VARCHAR(0)))"));
    }

    /**
     * And the BOUNDARY widths that are legal still declare, store and read back — a one-character
     * string and a whole-second time, the narrowest each family admits.
     */
    @Test
    public void alegalWidthIsUntouched() {
        engine.execute("CREATE OR REPLACE TABLE dw_ok (c VARCHAR(1), t TIME(0))");
        engine.execute("INSERT INTO dw_ok VALUES ('x', '10:11:12')");
        final ResultSet rs = engine.executeQuery("SELECT c, t FROM dw_ok");
        rs.next();
        assertEquals("x", String.valueOf(rs.getValue(0)));
        assertEquals("10:11:12", String.valueOf(rs.getValue(1)));
    }
}
