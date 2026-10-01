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
 * Where a call written with OVER is judged among the query's other refusals: its KIND while its clause's names are
 * walked, at its written place after the names inside it; its named arguments with the select list's own walk, after
 * the items written before it and ahead of the other clauses' types; a window function that needs an ORDER BY in
 * every clause it may stand in.
 */
public class CallShapeRankingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5))");
        engine.execute("INSERT INTO rt VALUES (1, 1.5, 'a'), (2, 2.5, 'b')");
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

    private static String invalidType(final String name) {
        return "SQL compilation error:|Invalid function type [" + name + "] for window function.";
    }

    private static String noNames(final int position, final String name) {
        return at(position, "function " + name + " does not support named arguments");
    }

    @Test
    public void theKindIsJudgedAtItsPlaceInTheNameWalk() {
        check(new String[][] {
            {"SELECT ABS(nosuch) OVER () FROM rt", at(11, "invalid identifier 'NOSUCH'")},
            {"SELECT ABS(1) OVER (ORDER BY nosuch) FROM rt", at(29, "invalid identifier 'NOSUCH'")},
            {"SELECT GENERATOR(ROWCOUNT => nosuch) OVER () FROM rt", at(29, "invalid identifier 'NOSUCH'")},
            {"SELECT ABS(1) OVER (), nosuch FROM rt", invalidType("ABS")},
            {"SELECT n, ABS(1) OVER () FROM rt WHERE nosuch = 1", invalidType("ABS")},
            {"SELECT n FROM rt WHERE ABS(1) OVER () = 1 AND nosuch = 1", invalidType("ABS")},
            {"SELECT n FROM rt WHERE nosuch = 1 AND ABS(1) OVER () = 1", at(23, "invalid identifier 'NOSUCH'")},
            {"SELECT ABS(1) OVER () FROM rt GROUP BY nosuch", invalidType("ABS")},
            {"SELECT ABS(1) OVER (), NOSUCHFN(1) FROM rt", invalidType("ABS")},
            {"SELECT NOSUCHFN(1), ABS(1) OVER () FROM rt", invalidType("ABS")},
            {"SELECT ABS(1) OVER () FROM rt WHERE n + TRUE = 1", invalidType("ABS")},
            {"SELECT UPPER(1, 2), ABS(1) OVER () FROM rt", invalidType("ABS")},
            {"SELECT n + TRUE, ABS(1) OVER () FROM rt", invalidType("ABS")},
            {"SELECT n FROM rt QUALIFY ABS(1) OVER () = 1 AND nosuch = 1", invalidType("ABS")},
            {"SELECT n FROM rt WHERE n + TRUE = 1 QUALIFY ABS(1) OVER () = 1", invalidType("ABS")},
            {"SELECT nosuch FROM rt QUALIFY ABS(1) OVER () = 1", at(7, "invalid identifier 'NOSUCH'")},
            {"SELECT MEDIAN(n) WITHIN GROUP (ORDER BY n), ABS(1) OVER () FROM rt",
                "SQL compilation error:|Function MEDIAN does not support WITHIN GROUP clause."},
            {"SELECT ABS(1) OVER (), MEDIAN(n) WITHIN GROUP (ORDER BY n) FROM rt", invalidType("ABS")},
        });
    }

    @Test
    public void theNamedArgumentsAreJudgedWithTheSelectListsOwnWalk() {
        check(new String[][] {
            {"SELECT SUM(x => 1) OVER () FROM rt WHERE n + TRUE = 1", noNames(7, "SUM")},
            {"SELECT SUM(x => nosuch) OVER () FROM rt", at(16, "invalid identifier 'NOSUCH'")},
            {"SELECT SUM(x => 1) OVER (ORDER BY nosuch) FROM rt", at(34, "invalid identifier 'NOSUCH'")},
            {"SELECT UPPER(1, 2), SUM(x => 1) OVER () FROM rt WHERE n + TRUE = 1",
                at(7, "too many arguments for function [UPPER(1, 2)] expected 1, got 2")},
            {"SELECT UPPER(1, 2), RANK(1) OVER (ORDER BY n) FROM rt",
                at(7, "too many arguments for function [UPPER(1, 2)] expected 1, got 2")},
            {"SELECT SUM(x => 1) OVER (), nosuch FROM rt", at(28, "invalid identifier 'NOSUCH'")},
            {"SELECT SUM(x => 1) OVER (), NOSUCHFN(1) FROM rt", "SQL compilation error:|Unknown function NOSUCHFN."},
            {"SELECT n + TRUE, SUM(x => 1) OVER () FROM rt",
                at(9, "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)")},
            {"SELECT SUM(x => 1) OVER (), ABS(1) OVER () FROM rt", invalidType("ABS")},
        });
    }

    @Test
    public void aNamedCallOutsideTheSelectListWaitsForTheWhereTypes() {
        final String plusTypes = "Invalid argument types for function '+': (NUMBER(38,0), BOOLEAN)";
        check(new String[][] {
            {"SELECT n FROM rt WHERE n + TRUE = 1 QUALIFY SUM(x => 1) OVER () = 1", at(25, plusTypes)},
            {"SELECT n FROM rt WHERE n + TRUE = 1 ORDER BY SUM(x => 1) OVER ()", at(25, plusTypes)},
            {"SELECT n FROM rt WHERE n + TRUE = 1 QUALIFY RANK(x => 1) OVER (ORDER BY n) = 1", at(25, plusTypes)},
            {"SELECT n FROM rt WHERE n + TRUE = 1 QUALIFY MEDIAN(x => n) OVER () = 1", at(25, plusTypes)},
            {"SELECT n + TRUE FROM rt QUALIFY SUM(x => 1) OVER () = 1", at(9, plusTypes)},
            {"SELECT n FROM rt QUALIFY SUM(x => 1) OVER () = 1 AND nosuch = 1", at(53, "invalid identifier 'NOSUCH'")},
            {"SELECT SUM(x => 1) OVER () FROM rt QUALIFY nosuch = 1", at(43, "invalid identifier 'NOSUCH'")},
            {"SELECT n FROM rt GROUP BY n HAVING n + TRUE = 1 ORDER BY SUM(x => 1) OVER ()", noNames(57, "SUM")},
            {"SELECT n FROM rt QUALIFY SUM(x => 1) OVER () = 1 ORDER BY SUM(y => 1) OVER ()", noNames(58, "SUM")},
            {"SELECT n FROM rt ORDER BY SUM(x => 1) OVER (), n + TRUE", noNames(26, "SUM")},
        });
    }

    @Test
    public void theMissingOrderByIsJudgedAtItsPlaceInTheNameWalk() {
        final String rowNumber = "SQL compilation error:|Window function type [ROW_NUMBER] requires ORDER BY in window "
            + "specification.";
        check(new String[][] {
            {"SELECT ROW_NUMBER() OVER (), nosuch FROM rt", rowNumber},
            {"SELECT ROW_NUMBER() OVER (), ABS(1) OVER () FROM rt", rowNumber},
            {"SELECT ABS(1) OVER (), ROW_NUMBER() OVER () FROM rt", invalidType("ABS")},
            {"SELECT ROW_NUMBER() OVER (), UPPER(DISTINCT g) FROM rt", rowNumber},
            {"SELECT UPPER(DISTINCT g), ROW_NUMBER() OVER () FROM rt",
                "SQL compilation error:|invalid use of 'distinct' for function 'UPPER(DISTINCT RT.G)'"},
            {"SELECT ROW_NUMBER() OVER (), MEDIAN(n) WITHIN GROUP (ORDER BY n) FROM rt", rowNumber},
            {"SELECT MEDIAN(DISTINCT n), ROW_NUMBER() OVER () FROM rt", rowNumber},
            {"SELECT ROW_NUMBER() OVER (), SUM(x => 1) OVER () FROM rt", rowNumber},
            {"SELECT ROW_NUMBER() OVER (), UPPER(x => 'a') FROM rt", rowNumber},
            {"SELECT ROW_NUMBER() OVER (), NOSUCHFN(1) FROM rt", rowNumber},
            {"SELECT NOSUCHFN(1), ROW_NUMBER() OVER () FROM rt", rowNumber},
            {"SELECT UPPER(1, 2), ROW_NUMBER() OVER () FROM rt", rowNumber},
            {"SELECT ROW_NUMBER() OVER (), n + TRUE FROM rt", rowNumber},
            {"SELECT RANK() OVER (PARTITION BY g), UPPER(ALL g) FROM rt",
                "SQL compilation error:|Window function type [RANK] requires ORDER BY in window specification."},
            {"SELECT FIRST_VALUE(n) OVER (), ROW_NUMBER() OVER () FROM rt",
                "SQL compilation error:|Window function type [FIRST_VALUE] requires ORDER BY in window specification."},
            {"SELECT \"ROW_NUMBER\"() OVER () FROM rt", rowNumber},
            // The names inside the call and its window come first.
            {"SELECT ROW_NUMBER() OVER (PARTITION BY nosuch) FROM rt", at(39, "invalid identifier 'NOSUCH'")},
            {"SELECT LAG(nosuch) OVER () FROM rt", at(11, "invalid identifier 'NOSUCH'")},
            {"SELECT NTILE(nosuch) OVER (PARTITION BY g) FROM rt", at(13, "invalid identifier 'NOSUCH'")},
            {"SELECT RANK() WITHIN GROUP (ORDER BY n) OVER () FROM rt",
                "SQL compilation error:|Function RANK does not support WITHIN GROUP clause."},
            // Every clause's names are walked in turn, the select list's first.
            {"SELECT LAG(n) OVER () FROM rt WHERE nosuch = 1",
                "SQL compilation error:|Window function type [LAG] requires ORDER BY in window specification."},
            {"SELECT RANK() OVER () FROM rt ORDER BY nosuch",
                "SQL compilation error:|Window function type [RANK] requires ORDER BY in window specification."},
            {"SELECT ROW_NUMBER() OVER () FROM rt GROUP BY nosuch", rowNumber},
            {"SELECT n, nosuch FROM rt QUALIFY ROW_NUMBER() OVER () = 1", at(10, "invalid identifier 'NOSUCH'")},
            {"SELECT n FROM rt WHERE nosuch = 1 QUALIFY ROW_NUMBER() OVER () = 1", at(23, "invalid identifier 'NOSUCH'")},
            {"SELECT n FROM rt WHERE ROW_NUMBER() OVER () = 1", rowNumber},
            {"SELECT n FROM rt WHERE ROW_NUMBER() OVER () = 1 AND nosuch = 1", rowNumber},
            {"SELECT n FROM rt WHERE nosuch = 1 AND ROW_NUMBER() OVER () = 1", at(23, "invalid identifier 'NOSUCH'")},
            {"SELECT n FROM rt GROUP BY n HAVING RANK() OVER () = 1",
                "SQL compilation error:|Window function type [RANK] requires ORDER BY in window specification."},
            {"SELECT COUNT(*) FROM rt GROUP BY ROW_NUMBER() OVER ()", rowNumber},
            {"SELECT n FROM rt QUALIFY ROW_NUMBER() OVER () = 1 AND nosuch = 1", rowNumber},
            {"SELECT n FROM rt QUALIFY ROW_NUMBER() OVER () = 1 AND UPPER(DISTINCT g) = 'A'", rowNumber},
            {"SELECT n FROM rt WHERE n + TRUE = 1 QUALIFY ROW_NUMBER() OVER () = 1", rowNumber},
            {"SELECT n FROM rt ORDER BY ROW_NUMBER() OVER (), UPPER(DISTINCT g)", rowNumber},
            {"SELECT n FROM rt ORDER BY ROW_NUMBER() OVER (), MEDIAN(n) WITHIN GROUP (ORDER BY n)", rowNumber},
            // A subquery's own names wait for the query around it.
            {"SELECT (SELECT ROW_NUMBER() OVER () FROM rt LIMIT 1), nosuch FROM rt",
                at(54, "invalid identifier 'NOSUCH'")},
        });
    }

    @Test
    public void aWindowFunctionNeedsItsOrderByInEveryClause() {
        check(new String[][] {
            {"SELECT n FROM rt ORDER BY ROW_NUMBER() OVER ()",
                "SQL compilation error:|Window function type [ROW_NUMBER] requires ORDER BY in window specification."},
            {"SELECT n FROM rt ORDER BY RANK() OVER (PARTITION BY g)",
                "SQL compilation error:|Window function type [RANK] requires ORDER BY in window specification."},
            {"SELECT n FROM rt QUALIFY ROW_NUMBER() OVER () = 1",
                "SQL compilation error:|Window function type [ROW_NUMBER] requires ORDER BY in window specification."},
            {"SELECT 1 ORDER BY ROW_NUMBER() OVER ()",
                "SQL compilation error:|Window function type [ROW_NUMBER] requires ORDER BY in window specification."},
            {"SELECT 1 QUALIFY ROW_NUMBER() OVER () = 1",
                "SQL compilation error:|Window function type [ROW_NUMBER] requires ORDER BY in window specification."},
            {"SELECT 1 ORDER BY FIRST_VALUE(1) OVER ()",
                "SQL compilation error:|Window function type [FIRST_VALUE] requires ORDER BY in window specification."},
        });
    }
}
