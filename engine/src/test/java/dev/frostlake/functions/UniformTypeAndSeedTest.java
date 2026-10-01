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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UNIFORM's type follows its bounds — a NUMBER as wide as the wider bound's integer digits, two at the
 * least, beside the larger scale, or a FLOAT for a FLOAT or text bound — and it draws at that scale,
 * inclusive of both bounds. RANDOM's seed and UNIFORM's bounds must be constants. Live-verified.
 */
public class UniformTypeAndSeedTest extends BaseDatabaseTest {

    private void createTable() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), d DATE)");
        engine.execute("CREATE OR REPLACE TABLE rt0 (n INT, f FLOAT, g VARCHAR(5), d DATE)");
        engine.execute("INSERT INTO rt VALUES (5, 1.5, 'x', '2024-01-01')");
    }

    /** The one cell of a single-row query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? "NULL" : value.toString();
    }

    /** The message a refused statement carries. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private String typeOf(final String uniform) {
        return scalar("SELECT SYSTEM$TYPEOF(" + uniform + ")");
    }

    private static String notConstant(final int argument, final String function, final String echo) {
        return "SQL compilation error:\nargument " + argument + " to function " + function
            + " needs to be constant, found '" + echo + "'";
    }

    /** Integer bounds: the wider bound's digits, two at the least, tagged by that precision. */
    @Test
    public void integerBoundsTypeByTheirDigits() {
        assertEquals("NUMBER(2,0)[SB1]", typeOf("UNIFORM(1, 10, RANDOM())"));
        assertEquals("NUMBER(2,0)[SB1]", typeOf("UNIFORM(0, 9, RANDOM())"));
        assertEquals("NUMBER(2,0)[SB1]", typeOf("UNIFORM(5, 5, RANDOM())"));
        assertEquals("NUMBER(2,0)[SB1]", typeOf("UNIFORM(-5, 5, RANDOM())"));
        assertEquals("NUMBER(3,0)[SB2]", typeOf("UNIFORM(-100, 10, RANDOM())"));
        assertEquals("NUMBER(3,0)[SB2]", typeOf("UNIFORM(0, 127, RANDOM())"));
        assertEquals("NUMBER(4,0)[SB2]", typeOf("UNIFORM(1, 1000, RANDOM())"));
        assertEquals("NUMBER(5,0)[SB4]", typeOf("UNIFORM(0, 32768, RANDOM())"));
        assertEquals("NUMBER(10,0)[SB8]", typeOf("UNIFORM(1, 1234567890, RANDOM())"));
        assertEquals("NUMBER(2,0)[SB1]", typeOf("UNIFORM(NULL, 10, RANDOM())"));
        assertEquals("NUMBER(2,0)[SB1]", typeOf("UNIFORM(1, 10, 42)"));
    }

    /** Decimal bounds add their scale beside at least two integer digits; FLOAT and text draw a FLOAT. */
    @Test
    public void decimalBoundsAddTheirScale() {
        assertEquals("NUMBER(3,1)[SB2]", typeOf("UNIFORM(1.5, 10, RANDOM())"));
        assertEquals("NUMBER(3,1)[SB2]", typeOf("UNIFORM(0.5, 0.7, RANDOM())"));
        assertEquals("NUMBER(4,2)[SB2]", typeOf("UNIFORM(0.25, 0.5, RANDOM())"));
        assertEquals("NUMBER(5,3)[SB4]", typeOf("UNIFORM(0.123, 99, RANDOM())"));
        assertEquals("NUMBER(5,2)[SB4]", typeOf("UNIFORM(1, CAST(10 AS NUMBER(5,2)), RANDOM())"));
        assertEquals("NUMBER(2,0)[SB1]", typeOf("UNIFORM(0.0, 1.0, RANDOM())"));
        assertEquals("FLOAT[DOUBLE]", typeOf("UNIFORM(1::FLOAT, 10, RANDOM())"));
        assertEquals("FLOAT[DOUBLE]", typeOf("UNIFORM('1', '10', RANDOM())"));
    }

    /** The draw keeps to the type's scale and to both bounds. */
    @Test
    public void theDrawKeepsToTheTypesScale() {
        assertEquals("0", scalar("SELECT COUNT(*) FROM (SELECT UNIFORM(0.0, 1.0, RANDOM()) AS x"
            + " FROM TABLE(GENERATOR(ROWCOUNT => 200))) WHERE x NOT IN (0, 1)"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM (SELECT UNIFORM(1.5, 10, RANDOM()) AS x"
            + " FROM TABLE(GENERATOR(ROWCOUNT => 200))) WHERE x * 10 <> ROUND(x * 10) OR x < 1.5 OR x > 10"));
        assertEquals("0", scalar("SELECT COUNT(*) FROM (SELECT UNIFORM(0.25, 0.5, RANDOM()) AS x"
            + " FROM TABLE(GENERATOR(ROWCOUNT => 200))) WHERE x * 100 <> ROUND(x * 100) OR x < 0.25 OR x > 0.5"));
        assertEquals("2", scalar("SELECT COUNT(DISTINCT x) FROM (SELECT UNIFORM(0.0, 1.0, RANDOM()) AS x"
            + " FROM TABLE(GENERATOR(ROWCOUNT => 200)))"));
    }

    /** A seed or a bound that reads a row or draws a value is refused as the statement compiles. */
    @Test
    public void aSeedOrABoundMustBeConstant() {
        createTable();
        assertEquals(notConstant(1, "RANDOM", "RT.N"), refusal("SELECT RANDOM(n) FROM rt"));
        assertEquals(notConstant(1, "RANDOM", "RT0.N"), refusal("SELECT RANDOM(n) FROM rt0"));
        assertEquals(notConstant(1, "RANDOM", "RT.N + 1"), refusal("SELECT RANDOM(n + 1) FROM rt"));
        assertEquals(notConstant(1, "RANDOM", "NEGATE(RT.N)"), refusal("SELECT RANDOM(-n) FROM rt"));
        assertEquals(notConstant(1, "RANDOM", "RT.F"), refusal("SELECT RANDOM(f) FROM rt"));
        assertEquals(notConstant(1, "RANDOM", "RT.G"), refusal("SELECT RANDOM(g) FROM rt"));
        assertEquals(notConstant(1, "RANDOM", "RT.N"), refusal("SELECT 1 FROM rt WHERE RANDOM(n) > 0"));
        assertEquals(notConstant(1, "RANDOM", "RT.N"), refusal("SELECT UNIFORM(1, 10, RANDOM(n)) FROM rt"));
        assertEquals(notConstant(1, "RANDOM", "RANDOM()"), refusal("SELECT RANDOM(RANDOM()) IS NOT NULL"));
        assertEquals(notConstant(1, "UNIFORM", "RT.N"), refusal("SELECT UNIFORM(n, 10, RANDOM()) FROM rt"));
        assertEquals(notConstant(2, "UNIFORM", "RT.N"), refusal("SELECT UNIFORM(1, n, RANDOM()) FROM rt"));
    }

    /** An aggregate, an argument-less call, a draw and a NULL the plan converts are no constants either. */
    @Test
    public void whatThePlanCannotFoldIsNoConstant() {
        createTable();
        assertEquals(notConstant(1, "RANDOM", "COUNT(*)"), refusal("SELECT RANDOM(COUNT(*)) FROM rt"));
        assertEquals(notConstant(1, "RANDOM", "SCALED_ROUND_INT_DIVIDE(SUM(RT.N), COUNT(RT.N))"),
            refusal("SELECT RANDOM(AVG(n)) FROM rt"));
        assertEquals(notConstant(2, "UNIFORM", "COUNT(*)"), refusal("SELECT UNIFORM(1, COUNT(*), RANDOM()) FROM rt"));
        assertEquals(notConstant(1, "RANDOM", "ROW_NUMBER() OVER (ORDER BY 1 ASC NULLS LAST)"),
            refusal("SELECT RANDOM(ROW_NUMBER() OVER (ORDER BY 1)) FROM rt"));
        assertEquals(notConstant(1, "RANDOM", "PI()"), refusal("SELECT RANDOM(PI())"));
        assertEquals(notConstant(1, "RANDOM", "ABS(PI())"), refusal("SELECT RANDOM(ABS(PI()))"));
        assertEquals(notConstant(1, "UNIFORM", "PI()"), refusal("SELECT UNIFORM(PI(), 10, RANDOM())"));
        assertEquals(notConstant(1, "RANDOM", "CAST(CURRENT_DATE() AS VARCHAR(134217728))"),
            refusal("SELECT RANDOM(CURRENT_DATE()::VARCHAR)"));
        assertEquals(notConstant(1, "RANDOM", "DATE_DIFFDATEINDAYS(CAST('2020-01-01' AS DATE), CURRENT_DATE())"),
            refusal("SELECT RANDOM(CURRENT_DATE() - '2020-01-01'::DATE)"));
        assertEquals(notConstant(1, "RANDOM", "RANDOM(5)"), refusal("SELECT RANDOM(RANDOM(5))"));
        assertEquals(notConstant(1, "RANDOM", "SEQ8(0)"), refusal("SELECT RANDOM(SEQ8(0))"));
        assertEquals(notConstant(1, "RANDOM", "SYSTEM$NULL_TO_FIXED(null)"), refusal("SELECT RANDOM(CAST(NULL AS INT))"));
        assertEquals(notConstant(1, "RANDOM", "IFF(CAST(TRUE AS BOOLEAN), SYSTEM$NULL_TO_FIXED(null), 5)"),
            refusal("SELECT RANDOM(IFF(TRUE, NULL, 5))"));
    }

    /** A column of a derived table, a CTE or a cast is re-printed from the plan. */
    @Test
    public void theEchoIsThePlan() {
        createTable();
        assertEquals(notConstant(1, "RANDOM", "RT.N"), refusal("SELECT RANDOM(CAST(n AS INT)) FROM rt"));
        assertEquals(notConstant(1, "RANDOM", "CAST(RT.N AS VARCHAR(134217728))"), refusal("SELECT RANDOM(n::VARCHAR) FROM rt"));
        assertEquals(notConstant(1, "RANDOM", "\"values\".K"), refusal("SELECT RANDOM(k) FROM (SELECT 5 AS k)"));
        assertEquals(notConstant(1, "RANDOM", "C.K"), refusal("WITH c AS (SELECT 5 AS k) SELECT RANDOM(k) FROM c"));
        assertEquals(notConstant(1, "RANDOM", "R.N"), refusal("SELECT RANDOM(r.n) FROM rt r"));
        assertEquals(notConstant(1, "RANDOM", "DATE_DIFFDATEINDAYS(RT.D, CAST('2020-01-05' AS DATE))"),
            refusal("SELECT RANDOM(DATEDIFF(day, d, '2020-01-05')) FROM rt"));
    }

    /** What the plan folds is a constant: deterministic calls, a unit word, constructors, unconverted NULLs. */
    @Test
    public void whatThePlanFoldsIsAConstant() {
        engine.execute("SET seed_base = 5");
        final String[] constants = {
            "UNIFORM(1, 10, 5)", "DATEDIFF(day, '2020-01-01', '2020-01-05')", "HASH(1)", "PARSE_JSON('5')",
            "NULLIF(1, 1)", "TRY_TO_NUMBER('x')", "IFF(NULL, 1, 2)", "IFF(NULL OR NULL, 1, 2)", "ARRAY_SIZE([NULL])",
            "ARRAY_SIZE(ARRAY_CONSTRUCT())", "CASE WHEN TRUE THEN 1 ELSE 2 END", "5 + $seed_base", "NULL",
        };
        for (final String constant : constants) {
            assertTrue(Boolean.parseBoolean(scalar("SELECT RANDOM(" + constant + ") IS NOT NULL")), constant);
        }
    }

    /** A constant seed of any readable family is taken; BOOLEAN and temporal seeds are refused by type. */
    @Test
    public void aConstantSeedIsTaken() {
        createTable();
        assertEquals("NUMBER(19,0)[SB8]", scalar("SELECT SYSTEM$TYPEOF(RANDOM(1 + 1))"));
        assertEquals("NUMBER(19,0)[SB8]", scalar("SELECT SYSTEM$TYPEOF(RANDOM('5'))"));
        assertEquals("NUMBER(19,0)[SB8]", scalar("SELECT SYSTEM$TYPEOF(RANDOM(NULL))"));
        assertEquals("NUMBER(19,0)[SB8]", scalar("SELECT SYSTEM$TYPEOF(RANDOM(1.5))"));
        assertTrue(Boolean.parseBoolean(scalar("SELECT RANDOM(ABS(-5)) IS NOT NULL")));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'RANDOM': (BOOLEAN)", refusal("SELECT RANDOM(TRUE)"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'RANDOM': (DATE)", refusal("SELECT RANDOM(d) FROM rt"));
        final String unreadable = refusal("SELECT RANDOM(UPPER('a'))");
        assertTrue(unreadable.contains("Numeric value 'A' is not recognized"), unreadable);
        assertEquals("SQL compilation error: error line 1 at position 18\ninvalid identifier 'MISSING'",
            refusal("SELECT RANDOM(n), missing FROM rt"));
    }
}
