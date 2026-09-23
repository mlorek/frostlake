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
 * A call's quantifier and named arguments: a scalar takes neither DISTINCT nor ALL, an aggregate takes ALL as a
 * no-op and refuses DISTINCT where the account does, and a built-in without a named signature refuses named
 * arguments once its count is judged — each at the place the account judges it.
 */
public class CallQuantifierRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5))");
        engine.execute("INSERT INTO rt VALUES (1, 1.0, 'a'), (2, 4.0, 'a'), (3, 9.0, 'b')");
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

    private static String at(final int position, final String detail) {
        return "SQL compilation error: error line 1 at position " + position + "|" + detail;
    }

    private static String noNames(final int position, final String name) {
        return at(position, "function " + name + " does not support named arguments");
    }

    @Test
    public void aScalarTakesNoQuantifier() {
        check(new String[][] {
            {"SELECT UPPER(DISTINCT 'a')", invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"SELECT UPPER(DISTINCT g) FROM rt", invalidUse("distinct", "UPPER(DISTINCT RT.G)")},
            {"SELECT ABS(DISTINCT -1)", invalidUse("distinct", "ABS(DISTINCT -1)")},
            {"SELECT CONCAT(DISTINCT 'a', 'b')", invalidUse("distinct", "CONCAT(DISTINCT 'a', 'b')")},
            {"SELECT TO_VARCHAR(DISTINCT 1)", invalidUse("distinct", "TO_VARCHAR(DISTINCT 1)")},
            {"SELECT COALESCE(DISTINCT 1, 2)", invalidUse("distinct", "COALESCE(DISTINCT 1, 2)")},
            {"SELECT COALESCE(DISTINCT n, 2) FROM rt", invalidUse("distinct", "COALESCE(DISTINCT RT.N, 2)")},
            {"SELECT IFF(DISTINCT TRUE, 1, 2)", invalidUse("distinct", "IFF(DISTINCT TRUE, 1, 2)")},
            {"SELECT LENGTH(DISTINCT 'ab')", invalidUse("distinct", "LENGTH(DISTINCT 'ab')")},
            {"SELECT HASH(DISTINCT 1)", invalidUse("distinct", "HASH(DISTINCT 1)")},
            {"SELECT ARRAY_CONSTRUCT(DISTINCT 1, 2)", invalidUse("distinct", "ARRAY_CONSTRUCT(DISTINCT 1, 2)")},
            {"SELECT DATEADD(DISTINCT day, 1, '2020-01-01'::DATE)",
                invalidUse("distinct", "DATEADD(DISTINCT 'DAY', 1, CAST('2020-01-01' AS DATE))")},
            {"SELECT DAY(DISTINCT '2020-01-01'::DATE)", invalidUse("distinct", "DAY(DISTINCT CAST('2020-01-01' AS DATE))")},
            {"SELECT UPPER(ALL 'a')", invalidUse("all", "UPPER(ALL 'a')")},
            {"SELECT UPPER(ALL g) FROM rt", invalidUse("all", "UPPER(ALL RT.G)")},
            {"SELECT SOUNDEX_P123(ALL 'ab')", invalidUse("all", "SOUNDEX_P123(ALL 'ab')")},
            {"SELECT UPPER(DISTINCT x => 'a')", invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"SELECT UPPER(ALL x => 'a')", invalidUse("all", "UPPER(ALL 'a')")},
            {"SELECT UPPER(DISTINCT 1) OVER () FROM rt", invalidUse("distinct", "UPPER(DISTINCT 1)")},
            {"SELECT UPPER(ALL g) OVER () FROM rt", invalidUse("all", "UPPER(ALL RT.G)")},
            // A quantifier with no argument after it names the function alone.
            {"SELECT UPPER(DISTINCT)", "SQL compilation error:|invalid function 'UPPER'"},
            {"SELECT UPPER(ALL)", "SQL compilation error:|invalid function 'UPPER'"},
            {"SELECT CURRENT_DATE(DISTINCT)", "SQL compilation error:|invalid function 'CURRENT_DATE'"},
            {"SELECT SUM(DISTINCT) FROM rt", "SQL compilation error:|invalid function 'SUM'"},
            {"SELECT ROW_NUMBER(ALL) OVER (ORDER BY n) FROM rt", "SQL compilation error:|invalid function 'ROW_NUMBER'"},
            // An unknown name is refused for its name.
            {"SELECT NOSUCHFN(DISTINCT 1)", "SQL compilation error:|Unknown function NOSUCHFN."},
            {"SELECT NOSUCHFN(ALL 1)", "SQL compilation error:|Unknown function NOSUCHFN."},
            // A bare star takes no quantifier at all.
            {"SELECT COUNT(DISTINCT *) FROM rt", "Unsupported feature 'TOK_STAR'."},
            {"SELECT COUNT(ALL *) FROM rt", "Unsupported feature 'TOK_STAR'."},
            {"SELECT HASH(DISTINCT *) FROM rt", "Unsupported feature 'TOK_STAR'."},
        });
    }

    @Test
    public void anAggregateTakesAllAndRefusesDistinctWhereTheAccountDoes() {
        check(new String[][] {
            {"SELECT TO_VARCHAR(SUM(ALL n)), TO_VARCHAR(COUNT(ALL n)), TO_VARCHAR(MEDIAN(ALL n)) FROM rt", "6 3 2.000"},
            {"SELECT LISTAGG(ALL g, ',') WITHIN GROUP (ORDER BY g) FROM rt", "a,a,b"},
            {"SELECT TO_VARCHAR(COUNT_IF(ALL n > 1)) FROM rt", "2"},
            {"SELECT TO_VARCHAR(SUM(ALL n) OVER ()) FROM rt", "6 / 6 / 6"},
            {"SELECT TO_VARCHAR(FIRST_VALUE(ALL n) OVER (ORDER BY n)) FROM rt", "1 / 1 / 1"},
            {"SELECT TO_VARCHAR(SUM(DISTINCT f)), TO_VARCHAR(COUNT(DISTINCT g)), TO_VARCHAR(MAX(DISTINCT n)) FROM rt",
                "14 2 3"},
            {"SELECT MEDIAN(DISTINCT n) FROM rt", invalidUse("distinct", "MEDIAN(DISTINCT RT.N)")},
            {"SELECT CORR(DISTINCT n, f) FROM rt", invalidUse("distinct", "CORR(DISTINCT RT.N, RT.F)")},
            {"SELECT COVAR_SAMP(DISTINCT n, f) FROM rt", invalidUse("distinct", "COVAR_SAMP(DISTINCT RT.N, RT.F)")},
            {"SELECT REGR_SLOPE(DISTINCT n, f) FROM rt", invalidUse("distinct", "REGR_SLOPE(DISTINCT RT.N, RT.F)")},
            {"SELECT REGR_COUNT(DISTINCT n, f) FROM rt", invalidUse("distinct", "REGR_COUNT(DISTINCT RT.N, RT.F)")},
            {"SELECT BOOLAND_AGG(DISTINCT n > 0) FROM rt", invalidUse("distinct", "BOOLAND_AGG(DISTINCT RT.N > 0)")},
            {"SELECT BOOLXOR_AGG(DISTINCT n > 0) FROM rt", invalidUse("distinct", "BOOLXOR_AGG(DISTINCT RT.N > 0)")},
            {"SELECT COUNT_IF(DISTINCT n > 1) FROM rt", invalidUse("distinct", "COUNT_IF(DISTINCT RT.N > 1)")},
            {"SELECT MEDIAN(DISTINCT n) OVER () FROM rt", invalidUse("distinct", "MEDIAN(DISTINCT RT.N)")},
            {"SELECT CORR(DISTINCT n, f) OVER () FROM rt", invalidUse("distinct", "CORR(DISTINCT RT.N, RT.F)")},
            {"SELECT COUNT_IF(DISTINCT n > 1) OVER () FROM rt", invalidUse("distinct", "COUNT_IF(DISTINCT RT.N > 1)")},
        });
    }

    @Test
    public void aBuiltInWithoutNamedParametersRefusesNamesAfterItsCount() {
        check(new String[][] {
            {"SELECT UPPER(x => 'a')", noNames(7, "UPPER")},
            {"SELECT ABS(x => -1)", noNames(7, "ABS")},
            {"SELECT SOUNDEX(x => 'ab')", noNames(7, "SOUNDEX")},
            {"SELECT CONCAT(a => 'x', b => 'y')", noNames(7, "CONCAT")},
            {"SELECT SPLIT_PART(string => 'a,b', delimiter => ',', partNumber => 2)", noNames(7, "SPLIT_PART")},
            {"SELECT ABS(x => n) FROM rt", noNames(7, "ABS")},
            {"SELECT n FROM rt WHERE UPPER(x => g) = 'A'", noNames(23, "UPPER")},
            {"SELECT UPPER(g, x => 2) FROM rt",
                at(7, "too many arguments for function [UPPER(RT.G, 2)] expected 1, got 2")},
            {"SELECT UPPER(x => 1, y => 2)", at(7, "too many arguments for function [UPPER(1, 2)] expected 1, got 2")},
            // The built-ins the account answers with names take them as positional values.
            {"SELECT TO_VARCHAR(TO_DATE(x => '2020-01-01')), LEFT(a => 'abc', b => 2)", "2020-01-01 ab"},
        });
    }

    @Test
    public void aScalarQuantifierIsRefusedAtItsPlaceInTheNameWalk() {
        check(new String[][] {
            {"SELECT UPPER(DISTINCT 'a'), UPPER(1, 2) FROM rt", invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"SELECT UPPER(1, 2), UPPER(DISTINCT 'a') FROM rt", invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"SELECT UPPER(DISTINCT 'a'), nosuch FROM rt", invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"SELECT n, UPPER(DISTINCT 'a') FROM rt WHERE nosuch = 1", invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"SELECT UPPER(DISTINCT 'a') FROM rt WHERE n + TRUE = 1", invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"SELECT UPPER(DISTINCT g), NOSUCHFN(1) FROM rt", invalidUse("distinct", "UPPER(DISTINCT RT.G)")},
            {"SELECT NOSUCHFN(1), UPPER(ALL g) FROM rt", invalidUse("all", "UPPER(ALL RT.G)")},
            {"SELECT UPPER(DISTINCT 1, 2)", invalidUse("distinct", "UPPER(DISTINCT 1, 2)")},
            {"SELECT UPPER(DISTINCT nosuch) FROM rt", at(22, "invalid identifier 'NOSUCH'")},
            {"SELECT n FROM rt WHERE UPPER(DISTINCT g) = 'A'", invalidUse("distinct", "UPPER(DISTINCT RT.G)")},
            {"SELECT n FROM rt WHERE nosuch = 1 AND UPPER(DISTINCT g) = 'A'", at(23, "invalid identifier 'NOSUCH'")},
            {"SELECT n FROM rt ORDER BY UPPER(DISTINCT g)", invalidUse("distinct", "UPPER(DISTINCT RT.G)")},
            {"SELECT n FROM rt GROUP BY n HAVING UPPER(DISTINCT 'a') = 'A'", invalidUse("distinct", "UPPER(DISTINCT 'a')")},
            {"SELECT n FROM rt QUALIFY UPPER(DISTINCT g) = 'A'", invalidUse("distinct", "UPPER(DISTINCT RT.G)")},
            {"SELECT n FROM rt GROUP BY UPPER(DISTINCT g)", invalidUse("distinct", "UPPER(DISTINCT RT.G)")},
            // Named arguments wait for the names of every clause and the unknown functions.
            {"SELECT UPPER(x => 'a'), nosuch FROM rt", at(24, "invalid identifier 'NOSUCH'")},
            {"SELECT UPPER(x => 'a'), NOSUCHFN(1) FROM rt", "SQL compilation error:|Unknown function NOSUCHFN."},
            {"SELECT UPPER(1, 2), UPPER(x => 'a') FROM rt",
                at(7, "too many arguments for function [UPPER(1, 2)] expected 1, got 2")},
            {"SELECT UPPER(x => 'a'), UPPER(1, 2) FROM rt", noNames(7, "UPPER")},
        });
    }
}
