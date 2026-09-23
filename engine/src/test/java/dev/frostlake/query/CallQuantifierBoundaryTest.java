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
package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a call's quantifier stops: LAG, LEAD and NTH_VALUE take none in their own syntax, a table function's call
 * in FROM takes none, a window-only function other than FIRST_VALUE and LAST_VALUE refuses one as a scalar does, a
 * user's function refuses one when it is named with its schema and is no function at all when it is not, and a
 * Snowflake Scripting expression keeps the quantifier it was written with.
 */
public class CallQuantifierBoundaryTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5))");
        engine.execute("INSERT INTO rt VALUES (1, 1.0, 'a'), (2, 4.0, 'a'), (3, 9.0, 'b'), (NULL, 16.0, 'b')");
        engine.execute("CREATE OR REPLACE FUNCTION fl_quant_add(x NUMBER) RETURNS NUMBER AS 'x + 1'");
    }

    /** Every row's cells joined, or the refusal with its lines joined by '|'. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder();
            while (rs.next()) {
                if (all.length() > 0) {
                    all.append(" / ");
                }
                for (int i = 0; i < rs.getColumns().size(); i++) {
                    all.append(i > 0 ? " " : "").append(String.valueOf(rs.getValue(i)));
                }
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private void check(final String[][] cases) {
        for (final String[] c : cases) {
            assertEquals(c[1], answer(c[0]), c[0]);
        }
    }

    private static String invalidUse(final String quantifier, final String echo) {
        return "SQL compilation error:|invalid use of '" + quantifier + "' for function '" + echo + "'";
    }

    private static String compilation(final String detail) {
        return "SQL compilation error:|" + detail;
    }

    /** One syntax line at the position, naming the token. */
    private static String line(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void theNavigationFunctionsTakeNoQuantifierInTheirOwnSyntax() {
        check(new String[][] {
            {"SELECT LAG(ALL n) OVER (ORDER BY n) FROM rt",
                compilation(line(11, "ALL") + "|" + line(10, "("))},
            {"SELECT LAG(DISTINCT n) OVER (ORDER BY n) FROM rt",
                compilation(line(11, "DISTINCT") + "|" + line(10, "("))},
            {"SELECT lag(ALL n) OVER (ORDER BY n) FROM rt", compilation(line(11, "ALL") + "|" + line(10, "("))},
            {"SELECT LEAD(ALL n, 1, 0) OVER (ORDER BY n) FROM rt",
                compilation(line(12, "ALL") + "|" + line(11, "("))},
            {"SELECT LEAD(DISTINCT n) OVER (ORDER BY n) FROM rt",
                compilation(line(12, "DISTINCT") + "|" + line(11, "("))},
            {"SELECT NTH_VALUE(ALL n, 1) OVER (ORDER BY n) FROM rt",
                compilation(line(17, "ALL") + "|" + line(16, "("))},
            {"SELECT NTH_VALUE(DISTINCT n, 1) FROM FIRST OVER (ORDER BY n) FROM rt",
                compilation(line(17, "DISTINCT") + "|" + line(16, "("))},
            {"SELECT LAG(ALL n) FROM rt", compilation(line(11, "ALL") + "|" + line(10, "("))},
            {"SELECT LAG(ALL x => n) OVER (ORDER BY n) FROM rt", compilation(line(11, "ALL") + "|" + line(10, "("))},
            {"SELECT n, LAG(ALL n) OVER (ORDER BY n) FROM rt WHERE nosuch = 1",
                compilation(line(14, "ALL") + "|" + line(13, "("))},
            // A quoted name is an ordinary call, judged for its quantifier as any window function is.
            {"SELECT \"LAG\"(ALL n) OVER (ORDER BY n) FROM rt", invalidUse("all", "LAG(ALL RT.N)")},
            {"SELECT \"LAG\"(n) IGNORE NULLS OVER (ORDER BY n) FROM rt", compilation(line(16, "IGNORE"))},
            {"SELECT \"FIRST_VALUE\"(n) IGNORE NULLS OVER (ORDER BY n) FROM rt", compilation(line(24, "IGNORE"))},
        });
    }

    @Test
    public void aTableFunctionInFromTakesNoQuantifier() {
        check(new String[][] {
            {"SELECT * FROM TABLE(GENERATOR(DISTINCT ROWCOUNT => 2))", compilation(line(30, "DISTINCT"))},
            {"SELECT COUNT(*) FROM TABLE(GENERATOR(ALL ROWCOUNT => 2))", compilation(line(37, "ALL"))},
            {"SELECT * FROM TABLE(FLATTEN(ALL input => PARSE_JSON('[1]')))", compilation(line(28, "ALL"))},
            {"SELECT * FROM TABLE(FLATTEN(DISTINCT PARSE_JSON('[1]')))", compilation(line(28, "DISTINCT"))},
        });
        // Where a comma or a string follows, the account's recovery names one more token; the refusal starts alike.
        final String[][] firstLines = {
            {"SELECT * FROM TABLE(SPLIT_TO_TABLE(ALL 'a,b', ',')) ORDER BY 1", line(35, "ALL")},
            {"SELECT * FROM TABLE(SPLIT_TO_TABLE(DISTINCT 'a,b', ',')) ORDER BY 1", line(35, "DISTINCT")},
            {"SELECT value FROM TABLE(FLATTEN(ALL [1,2])) ORDER BY 1", line(32, "ALL")},
            {"SELECT * FROM rt, TABLE(SPLIT_TO_TABLE(ALL rt.g, ','))", line(39, "ALL")},
            {"SELECT * FROM rt JOIN TABLE(SPLIT_TO_TABLE(ALL rt.g, ',')) s ON TRUE", line(43, "ALL")},
        };
        for (final String[] c : firstLines) {
            final String refused = answer(c[0]);
            assertEquals(compilation(c[1]), refused.substring(0, Math.min(refused.length(),
                compilation(c[1]).length())), c[0]);
        }
    }

    @Test
    public void aWindowOnlyFunctionTakesNoQuantifierButTheValueFunctions() {
        check(new String[][] {
            {"SELECT NTILE(ALL n) OVER (ORDER BY n) FROM rt", invalidUse("all", "NTILE(ALL RT.N)")},
            {"SELECT NTILE(ALL 2) OVER () FROM rt", invalidUse("all", "NTILE(ALL 2)")},
            {"SELECT NTILE(ALL 2) FROM rt", invalidUse("all", "NTILE(ALL 2)")},
            {"SELECT NTILE(DISTINCT 2) FROM rt", invalidUse("distinct", "NTILE(DISTINCT 2)")},
            {"SELECT NTILE(ALL x => 2) OVER (ORDER BY n) FROM rt", invalidUse("all", "NTILE(ALL 2)")},
            {"SELECT \"ntile\"(ALL 2) OVER (ORDER BY n) FROM rt", invalidUse("all", "NTILE(ALL 2)")},
            {"SELECT RANK(ALL 1) OVER (ORDER BY n) FROM rt", invalidUse("all", "RANK(ALL 1)")},
            {"SELECT ROW_NUMBER(ALL n) OVER (ORDER BY n) FROM rt", invalidUse("all", "ROW_NUMBER(ALL RT.N)")},
            {"SELECT CUME_DIST(ALL n) OVER (ORDER BY n) FROM rt", invalidUse("all", "CUME_DIST(ALL RT.N)")},
            {"SELECT CONDITIONAL_TRUE_EVENT(ALL n > 1) OVER (ORDER BY n) FROM rt",
                invalidUse("all", "CONDITIONAL_TRUE_EVENT(ALL RT.N > 1)")},
            {"SELECT CONDITIONAL_TRUE_EVENT(ALL n > 1) FROM rt", invalidUse("all", "CONDITIONAL_TRUE_EVENT(ALL RT.N > 1)")},
            {"SELECT CONDITIONAL_TRUE_EVENT(DISTINCT n > 1) OVER (PARTITION BY g) FROM rt",
                invalidUse("distinct", "CONDITIONAL_TRUE_EVENT(DISTINCT RT.N > 1)")},
            // Under OVER the account plans CONDITIONAL_CHANGE_EVENT as LAG, and RATIO_TO_REPORT as SUM.
            {"SELECT CONDITIONAL_CHANGE_EVENT(ALL n) OVER (ORDER BY n) FROM rt", invalidUse("all", "LAG(ALL RT.N)")},
            {"SELECT CONDITIONAL_CHANGE_EVENT(ALL n) FROM rt", invalidUse("all", "CONDITIONAL_CHANGE_EVENT(ALL RT.N)")},
            {"SELECT RATIO_TO_REPORT(ALL n) FROM rt", invalidUse("all", "RATIO_TO_REPORT(ALL RT.N)")},
            {"SELECT TO_VARCHAR(RATIO_TO_REPORT(ALL n) OVER ()) FROM rt ORDER BY 1",
                "0.166667 / 0.333333 / 0.500000 / null"},
            {"SELECT TO_VARCHAR(RATIO_TO_REPORT(DISTINCT n) OVER ()) FROM rt ORDER BY 1",
                "0.166667 / 0.333333 / 0.500000 / null"},
            {"SELECT TO_VARCHAR(FIRST_VALUE(ALL n) OVER (ORDER BY n)) FROM rt", "1 / 1 / 1 / 1"},
            {"SELECT FIRST_VALUE(DISTINCT n) OVER (ORDER BY n) FROM rt",
                "SQL compilation error: error line 1 at position 31|distinct cannot be used with a window frame or an order."},
            // A quantifier with no argument after it names the function alone, whatever the function.
            {"SELECT DENSE_RANK(DISTINCT) OVER (ORDER BY n) FROM rt", compilation("invalid function 'DENSE_RANK'")},
            {"SELECT COUNT(DISTINCT) OVER (ORDER BY n) FROM rt", compilation("invalid function 'COUNT'")},
            {"SELECT NTILE(DISTINCT) OVER (PARTITION BY g) FROM rt", compilation("invalid function 'NTILE'")},
            {"SELECT CUME_DIST(ALL) OVER (ORDER BY n) FROM rt", compilation("invalid function 'CUME_DIST'")},
            {"SELECT NOSUCHFN(ALL)", compilation("invalid function 'NOSUCHFN'")},
            // A built-in answers to its name in any case, quoted or not.
            {"SELECT \"upper\"(DISTINCT 'a')", invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"SELECT \"Upper\"(ALL 'a')", invalidUse("all", "UPPER(ALL 'a')")},
            // The window's missing ORDER BY waits for the quantifier.
            {"SELECT NTILE(ALL 2) OVER (ORDER BY n), ROW_NUMBER() OVER () FROM rt", invalidUse("all", "NTILE(ALL 2)")},
            {"SELECT ROW_NUMBER() OVER (), NTILE(ALL 2) OVER (ORDER BY n) FROM rt",
                compilation("Window function type [ROW_NUMBER] requires ORDER BY in window specification.")},
            {"SELECT NTILE(ALL nosuch) OVER (ORDER BY n) FROM rt",
                "SQL compilation error: error line 1 at position 17|invalid identifier 'NOSUCH'"},
            {"SELECT n FROM rt WHERE n + TRUE = 1 QUALIFY NTILE(ALL 2) OVER (ORDER BY n) = 1",
                invalidUse("all", "NTILE(ALL 2)")},
        });
    }

    @Test
    public void aUserFunctionIsRefusedItsQuantifierByItsSchemaName() {
        check(new String[][] {
            {"SELECT test_schema.fl_quant_add(ALL n) FROM rt", invalidUse("all", "FL_QUANT_ADD(ALL RT.N)")},
            {"SELECT test_schema.fl_quant_add(DISTINCT n) FROM rt", invalidUse("distinct", "FL_QUANT_ADD(DISTINCT RT.N)")},
            {"SELECT test_schema.fl_quant_add(ALL 1, 2)", invalidUse("all", "FL_QUANT_ADD(ALL 1, 2)")},
            {"SELECT test_schema.fl_quant_add(ALL x => 1)", invalidUse("all", "FL_QUANT_ADD(ALL 1)")},
            {"SELECT test_schema.fl_quant_add(ALL 1) OVER ()", invalidUse("all", "FL_QUANT_ADD(ALL 1)")},
            {"SELECT test_schema.fl_quant_add(n) WITHIN GROUP (ORDER BY n) FROM rt",
                compilation("Function FL_QUANT_ADD does not support WITHIN GROUP clause.")},
            {"SELECT test_schema.fl_quant_add(ALL 1), nosuch FROM rt", invalidUse("all", "FL_QUANT_ADD(ALL 1)")},
            {"SELECT test_schema.fl_quant_add(ALL 1) FROM rt WHERE n + TRUE = 1", invalidUse("all", "FL_QUANT_ADD(ALL 1)")},
            {"SELECT test_schema.fl_quant_add(ALL)", compilation("invalid function 'FL_QUANT_ADD'")},
            {"SELECT test_schema.nosuchfn(ALL 1)", compilation("Unknown user-defined function TEST_SCHEMA.NOSUCHFN.")},
            // Unqualified, a name written with a quantifier or a WITHIN GROUP calls no user's function.
            {"SELECT fl_quant_add(DISTINCT 1)", compilation("Unknown function FL_QUANT_ADD.")},
            {"SELECT fl_quant_add(ALL n) FROM rt", compilation("Unknown function FL_QUANT_ADD.")},
            {"SELECT \"FL_QUANT_ADD\"(ALL 1)", compilation("Unknown function FL_QUANT_ADD.")},
            {"SELECT fl_quant_add(1) WITHIN GROUP (ORDER BY 1)", compilation("Unknown function FL_QUANT_ADD.")},
            {"SELECT fl_quant_add(DISTINCT 1), NOSUCHFN(1)", compilation("Unknown functions FL_QUANT_ADD, NOSUCHFN.")},
            {"SELECT fl_quant_add(ALL 1) FROM rt WHERE n + TRUE = 1", compilation("Unknown function FL_QUANT_ADD.")},
            {"SELECT fl_quant_add(ALL 1), nosuch FROM rt",
                "SQL compilation error: error line 1 at position 28|invalid identifier 'NOSUCH'"},
            {"SELECT fl_quant_add(ALL)", compilation("invalid function 'FL_QUANT_ADD'")},
            {"SELECT fl_quant_add(ALL 1) OVER ()", compilation("Invalid function type [FL_QUANT_ADD] for window function.")},
            {"SELECT TO_VARCHAR(fl_quant_add(x => 1)), TO_VARCHAR(test_schema.fl_quant_add(1))", "2 2"},
        });
    }

    @Test
    public void aScriptingCallKeepsItsQuantifier() {
        final String uncaught = "Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 9 : ";
        check(new String[][] {
            {"BEGIN\n  RETURN UPPER(ALL 'a') || 'b';\nEND;", uncaught + invalidUse("all", "UPPER(ALL 'a')")},
            {"BEGIN\n  RETURN IFF(TRUE, UPPER(DISTINCT 'a'), 'b');\nEND;",
                uncaught + invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"BEGIN\n  LET v VARCHAR := 'a';\n  RETURN UPPER(DISTINCT v);\nEND;",
                "Uncaught exception of type 'EXPRESSION_ERROR' on line 3 at position 9 : "
                    + invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"BEGIN\n  LET v VARCHAR := UPPER(DISTINCT 'a');\n  RETURN v;\nEND;",
                "Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 19 : "
                    + invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"BEGIN\n  RETURN fl_quant_add(ALL 1);\nEND;", uncaught + compilation("Unknown function FL_QUANT_ADD.")},
            {"BEGIN\n  RETURN COUNT(DISTINCT 1);\nEND;", "1"},
            {"BEGIN\n  RETURN SUM(ALL 1);\nEND;", "1"},
            // A named call's refusal is placed in the expression, which the account compiles on its own.
            {"BEGIN\n  RETURN UPPER(x => 'a');\nEND;",
                uncaught + "SQL compilation error: error line 1 at position 0|function UPPER does not support named arguments"},
            {"BEGIN\n  RETURN ABS(x => -1);\nEND;",
                uncaught + "SQL compilation error: error line 1 at position 0|function ABS does not support named arguments"},
            {"BEGIN\n  RETURN CASE WHEN TRUE THEN UPPER(x => 'a') END;\nEND;",
                uncaught + "SQL compilation error: error line 1 at position 20|function UPPER does not support named arguments"},
            {"BEGIN\n  IF (UPPER(x => 'a') = 'A') THEN RETURN 1; END IF;\n  RETURN 0;\nEND;",
                "Uncaught exception of type 'EXPRESSION_ERROR' on line 2 at position 6 : "
                    + "SQL compilation error: error line 1 at position 0|function UPPER does not support named arguments"},
        });
    }
}
