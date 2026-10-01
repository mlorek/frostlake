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
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The comparison-level operators — the comparisons, [NOT] LIKE and ILIKE, RLIKE, [NOT] IN, [NOT] BETWEEN, LIKE
 * ANY and the quantified comparisons — are one precedence, read from the left, while IS NULL and IS DISTINCT
 * FROM bind tighter. So a LIKE's pattern stops before a comparison written after it, and a comparison before an
 * IN, a LIKE or a BETWEEN is that operator's left operand. Live-verified.
 */
public class ComparisonLevelChainTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        return rs.getRows().get(0).getValue(0);
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private static String at(final int line, final int position, final String function, final String types) {
        return "SQL compilation error: error line " + line + " at position " + position
            + "\nInvalid argument types for function '" + function + "': (" + types + ")";
    }

    /** The first column of every row, in the order returned. */
    private List<Object> column(final String sql) {
        final List<Object> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.add(row.getValue(0));
        }
        return out;
    }

    @Test
    public void aLikeIsComparedRatherThanTakingTheComparisonAsItsPattern() {
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE 'a' = TRUE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE 'a' IN (TRUE) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE 'b' = FALSE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' NOT LIKE 'b' = TRUE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' ILIKE 'A' = TRUE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE 'a' <> FALSE AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT 'a' LIKE 'a' < TRUE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE 'a' = 1 AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE 'a' NOT IN (FALSE) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE 'a' IN (SELECT TRUE) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE 'a' BETWEEN FALSE AND TRUE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE 'a' = ANY (SELECT TRUE) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE 'b' || 'c' = FALSE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT NOT 'a' LIKE 'b' = TRUE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE 'a' AND 'b' LIKE 'b' = TRUE AS r"));
        assertEquals("Boolean value 'a' is not recognized", refusal("SELECT 'a' LIKE 'a' = 'a' AS r"));
    }

    @Test
    public void anOperatorBeforeALikeIsItsSubject() {
        assertEquals(at(1, 18, "LIKE", "BOOLEAN, VARCHAR(1)"), refusal("SELECT TRUE = 'a' LIKE 'a' AS r"));
        assertEquals(at(1, 18, "RLIKE", "BOOLEAN, VARCHAR(1)"), refusal("SELECT TRUE = 'a' RLIKE 'a' AS r"));
        assertEquals(at(1, 20, "LIKE", "BOOLEAN, VARCHAR(1)"), refusal("SELECT 'a' LIKE 'a' LIKE 'a' AS r"));
        assertEquals(at(1, 20, "RLIKE", "BOOLEAN, VARCHAR(4)"), refusal("SELECT 'a' LIKE 'a' RLIKE 'true' AS r"));
        assertEquals(at(1, 21, "LIKE", "BOOLEAN, VARCHAR(1)"), refusal("SELECT 'a' RLIKE 'a' LIKE 'a' AS r"));
        assertEquals(at(1, 17, "LIKE", "BOOLEAN, VARCHAR(5)"), refusal("SELECT 'x' = 'y' LIKE 'false' AS r"));
        assertEquals(at(1, 17, "ILIKE", "BOOLEAN, VARCHAR(5)"), refusal("SELECT 'x' = 'y' ILIKE 'FALSE' AS r"));
        assertEquals(at(0, -1, "LIKE", "BOOLEAN, VARCHAR(5)"), refusal("SELECT 'x' = 'y' NOT LIKE 'false' AS r"));
        assertEquals(at(1, 26, "LIKE", "BOOLEAN, VARCHAR(1)"), refusal("SELECT 'a' LIKE 'a' = 'b' LIKE 'b' AS r"));
        assertEquals(at(1, 25, "LIKE", "BOOLEAN, VARCHAR(1)"), refusal("SELECT 1 BETWEEN 0 AND 2 LIKE 'a' AS r"));
        assertEquals(at(1, 25, "RLIKE", "BOOLEAN, VARCHAR(1)"), refusal("SELECT 1 BETWEEN 0 AND 2 RLIKE 'a' AS r"));
    }

    @Test
    public void aComparisonBeforeAnInOrABetweenIsItsValue() {
        assertEquals(Boolean.TRUE, scalar("SELECT 'x' = 'y' IN (FALSE) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT 'x' = 'y' NOT IN (FALSE) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'x' = 'y' IN (SELECT FALSE) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT FALSE = 1 BETWEEN 0 AND 2 AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT FALSE = 1 NOT BETWEEN 0 AND 2 AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 1 = 1 = 1 IN (TRUE) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 1 IN (1) = 1 IN (1) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 1 BETWEEN 0 AND 2 BETWEEN FALSE AND TRUE AS r"));
        assertEquals(at(1, 17, "LIKE_ANY", "BOOLEAN, NULL, VARCHAR(4)"), refusal("SELECT 'a' = 'a' LIKE ANY ('true') AS r"));
    }

    @Test
    public void isNullAndIsDistinctFromBindTighter() {
        assertEquals(at(1, 11, "LIKE", "VARCHAR(1), BOOLEAN"), refusal("SELECT 'a' LIKE 'a' IS NULL AS r"));
        assertEquals(at(1, 11, "LIKE", "VARCHAR(1), BOOLEAN"), refusal("SELECT 'a' LIKE 'a' IS DISTINCT FROM TRUE AS r"));
        assertEquals(at(1, 11, "RLIKE", "VARCHAR(1), BOOLEAN"), refusal("SELECT 'a' RLIKE 'a' IS NULL AS r"));
        assertEquals("SQL compilation error:\nCan not convert parameter ''y' IS NULL' of type [BOOLEAN] into expected "
            + "type [VARCHAR(1)]", refusal("SELECT 'x' = 'y' IS NULL AS r"));
    }

    @Test
    public void theChainReadsTheSameInEveryClause() {
        engine.execute("CREATE OR REPLACE TABLE clc (s VARCHAR, n INT)");
        engine.execute("INSERT INTO clc VALUES ('a', 1), ('b', 2)");
        assertEquals("a", scalar("SELECT s FROM clc WHERE s LIKE 'a' = TRUE"));
        assertEquals("b", scalar("SELECT s FROM clc WHERE s LIKE 'a' = FALSE"));
        assertEquals("a", scalar("SELECT s FROM clc WHERE n = 1 IN (TRUE)"));
        final List<Object> flags = column("SELECT s LIKE 'a' = TRUE AS r FROM clc ORDER BY s");
        assertEquals(Boolean.TRUE, flags.get(0));
        assertEquals(Boolean.FALSE, flags.get(1));
        final List<Object> cases = column("SELECT CASE WHEN s LIKE 'a' = TRUE THEN 1 ELSE 0 END AS r FROM clc ORDER BY s");
        assertEquals("1", String.valueOf(cases.get(0)));
        assertEquals("0", String.valueOf(cases.get(1)));
        assertEquals("1", String.valueOf(scalar("SELECT COUNT_IF(s LIKE 'a' = TRUE) AS r FROM clc")));
        final List<Object> ordered = column("SELECT s FROM clc ORDER BY s LIKE 'a' = FALSE, s");
        assertEquals("a", ordered.get(0));
        assertEquals(at(1, 32, "LIKE", "BOOLEAN, VARCHAR(4)"), refusal("SELECT s FROM clc WHERE s = 'b' LIKE 'true'"));
    }

    /** What an anonymous block returns, as text. */
    private String blockAnswer(final String block) {
        return String.valueOf(scalar("EXECUTE IMMEDIATE $$" + block + "$$")).toLowerCase();
    }

    @Test
    public void aBlockExpressionFoldsItsComparisonsFromTheLeft() {
        assertEquals("true", blockAnswer("BEGIN LET r BOOLEAN := 'x' = 'y' IN (FALSE); RETURN r; END;"));
        assertEquals("true", blockAnswer("BEGIN LET r BOOLEAN := FALSE = 1 BETWEEN 0 AND 2; RETURN r; END;"));
        assertEquals("false", blockAnswer("BEGIN LET x INT := 2; RETURN x = 1 IN (TRUE); END;"));
        assertEquals("true", blockAnswer("BEGIN LET x INT := 1; RETURN x = 1 IN (TRUE); END;"));
        assertEquals("yes", blockAnswer("BEGIN IF ('x' = 'y' IN (FALSE)) THEN RETURN 'yes'; END IF; RETURN 'no'; END;"));
        assertEquals("true", blockAnswer("DECLARE r BOOLEAN DEFAULT 'x' = 'y' IN (FALSE); BEGIN RETURN r; END;"));
        assertEquals("false", blockAnswer("BEGIN RETURN NOT 'x' = 'y' IN (FALSE); END;"));
        assertEquals("true", blockAnswer("BEGIN RETURN 'x' = 'y' IN (FALSE) AND TRUE; END;"));
        assertEquals("true", blockAnswer("BEGIN LET x INT := 1; RETURN x = 1 = TRUE; END;"));
        assertEquals("looped", blockAnswer(
            "BEGIN LET x INT := 5; WHILE (x = 1 BETWEEN 0 AND 2) DO RETURN 'looped'; END WHILE; RETURN 'no'; END;"));
        final String refused = refusal("EXECUTE IMMEDIATE $$BEGIN LET r BOOLEAN := TRUE = 'a' LIKE 'a'; RETURN r; END;$$");
        assertTrue(refused.startsWith("Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 23 : "),
            refused);
    }

    @Test
    public void aBlockComparisonOverAnAggregateAggregates() {
        assertEquals("true", blockAnswer("BEGIN RETURN COUNT(1) = 1; END;"));
        assertEquals("true", blockAnswer("BEGIN RETURN 1 = COUNT(1); END;"));
        assertEquals("true", blockAnswer("BEGIN RETURN COUNT(*) = 1; END;"));
        assertEquals("true", blockAnswer("BEGIN RETURN COUNT(DISTINCT 1) = 1; END;"));
        assertEquals("true", blockAnswer("BEGIN RETURN COUNT(ALL 1) = 1; END;"));
        assertEquals("true", blockAnswer("BEGIN RETURN MEDIAN(x => 1) = 1; END;"));
        assertEquals("true", blockAnswer("BEGIN RETURN SUM(2) > 1; END;"));
        assertEquals("true", blockAnswer("BEGIN RETURN MAX(2) > MIN(1); END;"));
        assertEquals("true", blockAnswer("BEGIN RETURN COUNT(1) + 1 = 2; END;"));
        assertEquals("true", blockAnswer("BEGIN LET x NUMBER := 5; RETURN COUNT(x) = 1; END;"));
        assertEquals("true", blockAnswer("BEGIN LET b BOOLEAN := COUNT(1) = 1; RETURN b; END;"));
        assertEquals("y", blockAnswer("BEGIN IF (COUNT(1) = 1) THEN RETURN 'y'; END IF; RETURN 'n'; END;"));
    }
}
