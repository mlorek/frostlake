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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a Snowflake Scripting CONDITION takes as true — IF and ELSEIF, a searched CASE's WHEN, WHILE,
 * REPEAT's UNTIL. It is a TEXT rule, not TO_BOOLEAN's and not the SQL predicate's (live-verified):
 * the value is true when it is the BOOLEAN true or when its text is exactly {@code 1} or {@code true}
 * in any letter case. Everything else is false, without an error:
 *
 * <pre>
 *   'true'  'TRUE'  'True'  '1'      taken            'yes'  't'  'Y'  'on'  'x'  ''      not taken
 *   1  1.0  1e0  1::FLOAT            taken            0  2  -1  0.5                      not taken
 *   ' true'  'TRUE '  '01'  '1.0'    not taken        NULL                               not taken
 *   PARSE_JSON('true')  PARSE_JSON('1')   taken       PARSE_JSON('"true"')               not taken
 * </pre>
 *
 * <p>★ A NUMBER IS JUDGED BY ITS OWN SPELLING: a NUMBER 1, a FLOAT 1 and an integer-declared variable
 * holding 1.0 are '1' and taken, while a NUMBER(3,1) variable holding 1.0 is '1.0' and is not.
 *
 * <p>★ AN OPERATOR INSIDE THE CONDITION KEEPS ITS OWN STRICTNESS: {@code IF ('x' AND TRUE)} refuses
 * the way AND refuses everywhere, as an uncaught EXPRESSION_ERROR; and a SELECT inside the block is
 * held to SQL's compile-time rules, so {@code SELECT IFF('x', 1, 2) INTO :r} is a STATEMENT_ERROR.
 *
 * <p>★ A LITERAL IS TYPED AS SQL TYPES IT, so {@code 1.0} is the NUMBER 1 and taken, while an arithmetic
 * result keeps SQL's scale: {@code 1.5 - 0.5} is a NUMBER(2,1) spelled '1.0', and is not.
 */
public class ScriptingConditionTruthTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE rt (g VARCHAR(10))");
        engine.execute("INSERT INTO rt VALUES ('x')");
    }

    /** The block's returned value, or its refusal. */
    private String block(final String body) {
        try {
            return String.valueOf(engine.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$")
                .getRows().get(0).getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** "1" when the IF takes {@code condition}, "2" when it falls to the ELSE. */
    private String ifTaken(final String condition) {
        return block("BEGIN IF (" + condition + ") THEN RETURN 1; ELSE RETURN 2; END IF; END");
    }

    /** {@link #ifTaken} with a declared variable in scope. */
    private String ifTakenWith(final String declaration, final String condition) {
        return block("DECLARE " + declaration + "; BEGIN IF (" + condition
            + ") THEN RETURN 1; ELSE RETURN 2; END IF; END");
    }

    @Test
    void onlyTheWordTrueAndTheDigitOneAreTaken() {
        assertEquals("1", ifTaken("'true'"));
        assertEquals("1", ifTaken("'TRUE'"));
        assertEquals("1", ifTaken("'True'"));
        assertEquals("1", ifTaken("'1'"));
        assertEquals("1", ifTaken("'tr' || 'ue'"));
        assertEquals("2", ifTaken("'yes'"));
        assertEquals("2", ifTaken("'t'"));
        assertEquals("2", ifTaken("'Y'"));
        assertEquals("2", ifTaken("'y'"));
        assertEquals("2", ifTaken("'on'"));
        assertEquals("2", ifTaken("'x'"));
        assertEquals("2", ifTaken("'abc'"));
        assertEquals("2", ifTaken("'false'"));
        assertEquals("2", ifTaken("''"));
        // Exactly the text — no trimming, no numeric reading.
        assertEquals("2", ifTaken("' true'"));
        assertEquals("2", ifTaken("'TRUE '"));
        assertEquals("2", ifTaken("'01'"));
        assertEquals("2", ifTaken("'1.0'"));
        assertEquals("2", ifTaken("NULL"));
    }

    @Test
    void aNumberIsTakenOnlyWhenItSpellsOne() {
        assertEquals("1", ifTaken("1"));
        assertEquals("1", ifTaken("1.0"));
        assertEquals("1", ifTaken("1.00"));
        assertEquals("1", ifTaken("1e0"));
        assertEquals("1", ifTaken("1::FLOAT"));
        assertEquals("2", ifTaken("0"));
        assertEquals("2", ifTaken("2"));
        assertEquals("2", ifTaken("-1"));
        assertEquals("2", ifTaken("0.5"));
        assertEquals("2", ifTaken("10/10"));
        assertEquals("2", ifTaken("CAST(1 AS NUMBER(3,1))"));
    }

    @Test
    void aVariantIsTakenByItsJsonText() {
        assertEquals("1", ifTaken("PARSE_JSON('true')"));
        assertEquals("1", ifTaken("PARSE_JSON('1')"));
        assertEquals("2", ifTaken("PARSE_JSON('\"true\"')"));
        assertEquals("1", ifTakenWith("x VARIANT DEFAULT PARSE_JSON('true')", "x"));
        assertEquals("2", ifTakenWith("x VARIANT DEFAULT PARSE_JSON('\"true\"')", "x"));
    }

    @Test
    void aVariableIsJudgedByWhatItHolds() {
        assertEquals("1", ifTakenWith("x VARCHAR DEFAULT 'true'", "x"));
        assertEquals("1", ifTakenWith("x VARCHAR DEFAULT 'True'", "x"));
        assertEquals("1", ifTakenWith("x VARCHAR DEFAULT '1'", "x"));
        assertEquals("2", ifTakenWith("x VARCHAR DEFAULT 'x'", "x"));
        assertEquals("2", ifTakenWith("x VARCHAR DEFAULT 'yes'", "x"));
        assertEquals("2", ifTakenWith("x VARCHAR DEFAULT '1.0'", "x"));
        assertEquals("1", ifTakenWith("x BOOLEAN DEFAULT TRUE", "x"));
        assertEquals("1", ifTakenWith("x NUMBER DEFAULT 1", "x"));
        assertEquals("1", ifTakenWith("x NUMBER DEFAULT 1.0", "x"));
        assertEquals("2", ifTakenWith("x NUMBER DEFAULT 2", "x"));
        assertEquals("1", ifTakenWith("x FLOAT DEFAULT 1", "x"));
        assertEquals("1", ifTakenWith("x FLOAT DEFAULT 1e0", "x"));
        assertEquals("1", ifTakenWith("x FLOAT DEFAULT 1.5", "x - 0.5"));
        // The declared scale is part of the spelling: 1.0 in a NUMBER(3,1) is '1.0'.
        assertEquals("2", ifTakenWith("x NUMBER(3,1) DEFAULT 1.0", "x"));
        assertEquals("2", ifTakenWith("x NUMBER(3,1) DEFAULT 1", "x"));
        assertEquals("2", ifTakenWith("x NUMBER(3,1) DEFAULT 1.0", "x + 0"));
        // Values read from a query follow the same rule.
        assertEquals("1", block("DECLARE x VARCHAR; BEGIN SELECT 'TRUE' INTO :x;"
            + " IF (x) THEN RETURN 1; ELSE RETURN 2; END IF; END"));
        assertEquals("2", block("DECLARE x VARCHAR; BEGIN SELECT g INTO :x FROM rt;"
            + " IF (x) THEN RETURN 1; ELSE RETURN 2; END IF; END"));
        assertEquals("1", block("DECLARE x NUMBER; BEGIN SELECT 1.0 INTO :x;"
            + " IF (x) THEN RETURN 1; ELSE RETURN 2; END IF; END"));
        assertEquals("2", block("DECLARE x NUMBER(3,1); BEGIN SELECT 1.0 INTO :x;"
            + " IF (x) THEN RETURN 1; ELSE RETURN 2; END IF; END"));
        // A real predicate is a BOOLEAN and needs no spelling.
        assertEquals("1", ifTakenWith("x NUMBER DEFAULT 2", "x = 2"));
        assertEquals("1", ifTaken("TO_BOOLEAN('yes')"));
    }

    @Test
    void everyConditionalStatementSharesTheRule() {
        assertEquals("0", block("DECLARE i INT DEFAULT 0; BEGIN WHILE ('x') DO i := i + 1;"
            + " IF (i > 3) THEN BREAK; END IF; END WHILE; RETURN i; END"));
        assertEquals("0", block("DECLARE i INT DEFAULT 0; BEGIN WHILE (2) DO i := i + 1;"
            + " IF (i > 3) THEN BREAK; END IF; END WHILE; RETURN i; END"));
        assertEquals("2", block("BEGIN CASE WHEN 'x' THEN RETURN 1; ELSE RETURN 2; END CASE; END"));
        assertEquals("1", block("BEGIN CASE WHEN 1 THEN RETURN 1; ELSE RETURN 2; END CASE; END"));
        assertEquals("2", block("BEGIN IF (FALSE) THEN RETURN 0; ELSEIF ('yes') THEN RETURN 1;"
            + " ELSE RETURN 2; END IF; END"));
        assertEquals("1", block("DECLARE i INT DEFAULT 0; BEGIN REPEAT i := i + 1;"
            + " UNTIL (i = 5 OR 'true') END REPEAT; RETURN i; END"));
    }

    @Test
    void anOperatorInsideTheConditionKeepsItsOwnStrictness() {
        assertEquals("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 11 :"
            + " Boolean value 'x' is not recognized", ifTaken("'x' AND TRUE"));
        assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 22 :"
            + " SQL compilation error: error line 1 at position 7|Invalid argument types for function 'IFF':"
            + " (VARCHAR(1), NUMBER(1,0), NUMBER(1,0))",
            block("DECLARE r INT; BEGIN SELECT IFF('x', 1, 2) INTO :r; RETURN r; END"));
    }

    @Test
    public void aScaledArithmeticResultIsJudgedByItsOwnSpelling() {
        assertEquals("1", ifTaken("1.0"));
        assertEquals("1", ifTaken("1e0"));
        assertEquals("2", ifTaken("1.5 - 0.5"));
    }
}
