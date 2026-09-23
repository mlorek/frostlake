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
 * A call written with named arguments that the account answers as the positional call its values spell — under
 * OVER for the measured window-capable set, without OVER for the measured aggregates — with the null treatments,
 * quantifiers and frames the positional call takes, and the refusals of the calls it does not answer.
 */
public class NamedWindowCallAnswerTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5))");
        engine.execute("INSERT INTO rt VALUES (1, 1.5, 'a'), (2, 2.5, 'b')");
        engine.execute("CREATE OR REPLACE TABLE rt2 (n INT, f FLOAT, g VARCHAR(5))");
        engine.execute("INSERT INTO rt2 VALUES (1, 1.0, 'a'), (2, 4.0, 'a'), (3, 9.0, 'b'), (NULL, 16.0, 'b')");
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

    private static String at(final int position, final String detail) {
        return "SQL compilation error: error line 1 at position " + position + "|" + detail;
    }

    private static String noNames(final int position, final String name) {
        return at(position, "function " + name + " does not support named arguments");
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void theMeasuredWindowFunctionsAnswerNamedArgumentsUnderOver() {
        check(new String[][] {
            {"SELECT TO_VARCHAR(FIRST_VALUE(x => n) OVER (ORDER BY n)) FROM rt ORDER BY 1", "1 / 1"},
            {"SELECT FIRST_VALUE(abc => g) OVER (ORDER BY n) FROM rt ORDER BY 1", "a / a"},
            {"SELECT TO_VARCHAR(LAST_VALUE(x => n) OVER (ORDER BY n)) FROM rt ORDER BY 1", "2 / 2"},
            {"SELECT TO_VARCHAR(MEDIAN(x => n) OVER ()) FROM rt", "1.500 / 1.500"},
            {"SELECT TO_VARCHAR(MEDIAN(x => 1) OVER ())", "1.000"},
            {"SELECT TO_VARCHAR(COUNT_IF(x => n > 1) OVER ()) FROM rt", "1 / 1"},
            {"SELECT TO_VARCHAR(BOOLAND_AGG(x => n > 0) OVER ()) FROM rt", "true / true"},
            {"SELECT TO_VARCHAR(ANY_VALUE(x => g) OVER (PARTITION BY g)) FROM rt2 ORDER BY 1", "a / a / b / b"},
            {"SELECT TO_VARCHAR(COUNT_IF(x => n > 1) OVER (ORDER BY n)) FROM rt2 ORDER BY 1", "0 / 1 / 2 / 2"},
            // The names are ignored: the values are the positional arguments in the order written.
            {"SELECT TO_VARCHAR(ROUND(REGR_AVGX(y => n, x => f) OVER (), 3)) FROM rt", "2 / 2"},
            {"SELECT TO_VARCHAR(ROUND(REGR_AVGX(x => f, y => n) OVER (), 3)) FROM rt", "1.5 / 1.5"},
            {"SELECT TO_VARCHAR(REGR_COUNT(n, y => f) OVER ()) FROM rt2", "3 / 3 / 3 / 3"},
            {"SELECT TO_VARCHAR(ROUND(STDDEV(DISTINCT x => n) OVER (), 3)) FROM rt", "0.707 / 0.707"},
            {"SELECT n FROM rt QUALIFY FIRST_VALUE(x => n) OVER (ORDER BY n) = n", "1"},
            {"SELECT n FROM rt2 ORDER BY MEDIAN(x => n) OVER (PARTITION BY g), n", "1 / 2 / 3 / null"},
            {"SELECT TO_VARCHAR(n + MEDIAN(x => n) OVER ()) FROM rt ORDER BY 1", "2.500 / 3.500"},
            {"SELECT g, TO_VARCHAR(MEDIAN(x => SUM(n)) OVER ()) FROM rt2 GROUP BY g ORDER BY 1", "a 3.000 / b 3.000"},
            {"SELECT TO_VARCHAR(FIRST_VALUE(x => n) OVER (ORDER BY n ROWS BETWEEN 1 PRECEDING AND 1 FOLLOWING))"
                + " FROM rt2 ORDER BY 1", "1 / 1 / 2 / 3"},
            {"SELECT TO_VARCHAR(LAST_VALUE(x => n) OVER (ORDER BY n ROWS BETWEEN CURRENT ROW AND CURRENT ROW))"
                + " FROM rt2 ORDER BY 1", "1 / 2 / 3 / null"},
        });
        // The column is named by the call as written.
        assertEquals("FIRST_VALUE(X => N) OVER (ORDER BY N)",
            engine.executeQuery("SELECT FIRST_VALUE(x => n) OVER (ORDER BY n) FROM rt").getColumns().get(0).getName());
        assertEquals("MEDIAN(X => N) OVER ()",
            engine.executeQuery("SELECT MEDIAN(x => n) OVER () FROM rt").getColumns().get(0).getName());
    }

    @Test
    public void theMeasuredAggregatesAnswerNamedArgumentsWithoutOver() {
        check(new String[][] {
            {"SELECT TO_VARCHAR(MEDIAN(x => n)) FROM rt", "1.500"},
            {"SELECT TO_VARCHAR(COUNT_IF(x => n > 1)) FROM rt2", "2"},
            {"SELECT TO_VARCHAR(AVG(x => n)) FROM rt2", "2.000000"},
            {"SELECT TO_VARCHAR(APPROX_COUNT_DISTINCT(x => n)) FROM rt2", "3"},
            {"SELECT TO_VARCHAR(HLL(x => n)) FROM rt2", "3"},
            {"SELECT TO_VARCHAR(ROUND(CORR(x => n, y => f), 3)) FROM rt", "1"},
            {"SELECT TO_VARCHAR(MEDIAN(ALL x => n)) FROM rt2", "2.000"},
            {"SELECT g, TO_VARCHAR(MEDIAN(x => n)) FROM rt2 GROUP BY g ORDER BY 1", "a 1.500 / b 3.000"},
        });
        assertEquals("MEDIAN(X => N)",
            engine.executeQuery("SELECT MEDIAN(x => n) FROM rt").getColumns().get(0).getName());
    }

    @Test
    public void theNullTreatmentBelongsToTheValueFunctionsNamedOrNot() {
        check(new String[][] {
            {"SELECT TO_VARCHAR(FIRST_VALUE(x => n) IGNORE NULLS OVER (ORDER BY n DESC)) FROM rt2", "3 / 3 / 3 / 3"},
            {"SELECT TO_VARCHAR(FIRST_VALUE(x => n) RESPECT NULLS OVER (ORDER BY n DESC)) FROM rt2",
                "null / null / null / null"},
            {"SELECT TO_VARCHAR(FIRST_VALUE(x => n IGNORE NULLS) OVER (ORDER BY n DESC)) FROM rt2", "3 / 3 / 3 / 3"},
            {"SELECT TO_VARCHAR(LAST_VALUE(x => n IGNORE NULLS) OVER (ORDER BY n)) FROM rt2", "3 / 3 / 3 / 3"},
            {"SELECT TO_VARCHAR(LAG(n) IGNORE NULLS OVER (ORDER BY n)) FROM rt2 ORDER BY 1", "1 / 2 / 3 / null"},
            {"SELECT MEDIAN(x => n) IGNORE NULLS OVER () FROM rt2", syntax(22, "IGNORE")},
            {"SELECT SUM(x => n) IGNORE NULLS OVER () FROM rt2", syntax(19, "IGNORE")},
            {"SELECT MEDIAN(n) IGNORE NULLS OVER () FROM rt2", syntax(17, "IGNORE")},
            {"SELECT SUM(n) RESPECT NULLS OVER () FROM rt2", syntax(14, "RESPECT")},
            {"SELECT RANK() IGNORE NULLS OVER (ORDER BY n) FROM rt2", syntax(14, "IGNORE")},
            {"SELECT UPPER(g) IGNORE NULLS FROM rt2", syntax(16, "IGNORE")},
            {"SELECT MEDIAN(n) IGNORE NULLS FROM rt2", syntax(17, "IGNORE")},
            {"SELECT FIRST_VALUE(n IGNORE NULLS) IGNORE NULLS OVER (ORDER BY n) FROM rt2",
                "SQL compilation error: duplicated use of null handling option"},
            {"SELECT FIRST_VALUE(x => n IGNORE NULLS) RESPECT NULLS OVER (ORDER BY n) FROM rt2",
                "SQL compilation error: duplicated use of null handling option"},
        });
    }

    @Test
    public void theCallsTheAccountDoesNotAnswerAreRefusedAtTheCall() {
        check(new String[][] {
            {"SELECT APPROX_PERCENTILE(x => n, y => 0.5) OVER () FROM rt2", noNames(7, "APPROX_PERCENTILE")},
            {"SELECT AVG(x => n) OVER () FROM rt2", noNames(7, "AVG")},
            {"SELECT HLL(x => n) OVER () FROM rt2", noNames(7, "HLL")},
            {"SELECT FIRST_VALUE(n, x => 1) OVER (ORDER BY n) FROM rt2",
                at(7, "too many arguments for function [FIRST_VALUE(RT2.N, 1)] expected 1, got 2")},
            // A frame that makes FIRST_VALUE or LAST_VALUE the NTH_VALUE the account plans it as.
            {"SELECT FIRST_VALUE(x => n) OVER (ORDER BY n ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) FROM rt2",
                noNames(7, "NTH_VALUE")},
            {"SELECT FIRST_VALUE(x => n) OVER (ORDER BY n ROWS CURRENT ROW) FROM rt2", noNames(7, "NTH_VALUE")},
            {"SELECT FIRST_VALUE(x => n) OVER (ORDER BY n ROWS BETWEEN 2 PRECEDING AND 1 PRECEDING) FROM rt2",
                noNames(7, "NTH_VALUE")},
            {"SELECT LAST_VALUE(x => n) OVER (ORDER BY n ROWS BETWEEN CURRENT ROW AND 1 FOLLOWING) FROM rt2",
                noNames(7, "NTH_VALUE")},
            // CONDITIONAL_CHANGE_EVENT is planned over LAG before its count, RATIO_TO_REPORT over SUM.
            {"SELECT CONDITIONAL_CHANGE_EVENT(x => n) OVER (PARTITION BY g ORDER BY n) FROM rt2", noNames(7, "LAG")},
            {"SELECT CONDITIONAL_CHANGE_EVENT(x => n, y => 1, z => 2) OVER (ORDER BY n) FROM rt2",
                noNames(7, "LAG")},
            {"SELECT RATIO_TO_REPORT(x => n) OVER () FROM rt2", noNames(7, "SUM")},
            {"SELECT RATIO_TO_REPORT(x => n, y => 1) OVER () FROM rt2",
                at(7, "too many arguments for function [SUM(RT2.N, 1)] expected 1, got 2")},
            {"SELECT SUM(DISTINCT x => n) OVER () FROM rt2", noNames(7, "SUM")},
            {"SELECT MEDIAN(DISTINCT x => n) OVER () FROM rt2",
                "SQL compilation error:|invalid use of 'distinct' for function 'MEDIAN(DISTINCT RT2.N)'"},
        });
    }

    @Test
    public void aNamedCallWithoutOverIsJudgedAsThePlainCall() {
        check(new String[][] {
            {"SELECT SUM(x => n) FROM rt2", noNames(7, "SUM")},
            {"SELECT COUNT(x => n) FROM rt2", noNames(7, "COUNT")},
            {"SELECT APPROXIMATE_COUNT_DISTINCT(x => n) FROM rt2", noNames(7, "APPROXIMATE_COUNT_DISTINCT")},
            {"SELECT SUM(DISTINCT x => n) FROM rt2", noNames(7, "SUM")},
            {"SELECT LAG(x => n) FROM rt2", noNames(7, "LAG")},
            {"SELECT RATIO_TO_REPORT(x => n) FROM rt2", noNames(7, "RATIO_TO_REPORT")},
            {"SELECT RANK(x => n) FROM rt2",
                at(7, "too many arguments for function [RANK(RT2.N)] expected 0, got 1")},
            {"SELECT FIRST_VALUE(x => n) FROM rt",
                "SQL compilation error:|Missing window specification for function [FIRST_VALUE(RT.N)]."},
            {"SELECT LAST_VALUE(x => n) FROM rt2",
                "SQL compilation error:|Missing window specification for function [LAST_VALUE(RT2.N)]."},
            {"SELECT MEDIAN(DISTINCT x => n) FROM rt2",
                "SQL compilation error:|invalid use of 'distinct' for function 'MEDIAN(DISTINCT RT2.N)'"},
            {"SELECT CORR(DISTINCT x => n, y => f) FROM rt2",
                "SQL compilation error:|invalid use of 'distinct' for function 'CORR(DISTINCT RT2.N, RT2.F)'"},
        });
    }
}
