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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A LIKE ANY, LIKE ALL or ILIKE ANY without ESCAPE takes its pattern list as a VALUE: the value operators written
 * after the list apply to it. A list of one is that value — {@code 'a' LIKE ANY ('a') || ''} matches 'a' || '' —
 * and a list of several is a ROW the first such operator refuses. Live-verified.
 */
public class LikeAnyValueOperandTest extends BaseDatabaseTest {

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

    /** The second column of every row, in the order returned. */
    private List<Object> answers(final String sql) {
        final List<Object> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.add(row.getValue(1));
        }
        return out;
    }

    @Test
    public void aListOfOneIsTheValueTheOperatorsApplyTo() {
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE ANY ('a') || '' AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'ab' LIKE ANY ('a') || 'b' AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT 'a' LIKE ANY ('a') || '' || 'x' AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE ALL ('a') || '' AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' ILIKE ANY ('A') || '' AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'ab' LIKE ALL ('a%') || '' AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT 'a' LIKE ANY ('1') + 1 AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT '2' LIKE ANY ('1') + 1 AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE ANY ('a') ::VARCHAR AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'ax' LIKE ANY ('a')::VARCHAR || 'x' AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE ANY (ARRAY_CONSTRUCT('a'))[0] AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE ANY (PARSE_JSON('{\"x\":\"a\"}')):x AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE ANY ('a') || '' = TRUE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'a' LIKE ANY ('a') || '' IN (TRUE) AS r"));
        assertNull(scalar("SELECT 'a' LIKE ANY (NULL) || 'a' AS r"));
        assertNull(scalar("SELECT NULL LIKE ANY ('a') || 'a' AS r"));
        assertEquals("Numeric value 'a' is not recognized", refusal("SELECT 'a' LIKE ANY ('a') + 1 AS r"));
        assertEquals("Numeric value 'A' is not recognized", refusal("SELECT 'a' ILIKE ANY ('A') + 1 AS r"));
    }

    @Test
    public void theOperatorsOverTheListAreJudgedAsItsOwn() {
        assertEquals(at(1, 11, "LIKE_ANY", "VARCHAR(1), NULL, BOOLEAN"), refusal("SELECT 'a' LIKE ANY ('a') IS NULL AS r"));
        assertEquals(at(1, 11, "LIKE_ALL", "VARCHAR(1), NULL, BOOLEAN"), refusal("SELECT 'a' LIKE ALL ('a') IS NULL AS r"));
        assertEquals(at(1, 11, "ILIKE_ANY", "VARCHAR(1), NULL, BOOLEAN"), refusal("SELECT 'a' ILIKE ANY ('A') IS NULL AS r"));
        assertEquals(at(1, 11, "LIKE_ANY", "VARCHAR(1), NULL, BOOLEAN"),
            refusal("SELECT 'a' LIKE ANY ('a') IS DISTINCT FROM TRUE AS r"));
        assertEquals(at(1, 26, "GET", "VARCHAR(1), VARCHAR(1)"), refusal("SELECT 'a' LIKE ANY ('a') :x AS r"));
        assertEquals(at(1, 26, "GET", "VARCHAR(1), NUMBER(1,0)"), refusal("SELECT 'a' LIKE ANY ('a') [0] AS r"));
        assertEquals("SQL compilation error: error line 1 at position 11\nFunction LIKE_ANY does not support collation.",
            refusal("SELECT 'a' LIKE ANY ('a') COLLATE 'de' AS r"));
        assertEquals("SQL compilation error:\nInvalid argument for (+): 'a'.", refusal("SELECT 'a' LIKE ANY ('a') (+) AS r"));
    }

    @Test
    public void aListOfSeveralIsARowTheFirstOperatorRefuses() {
        final String row = "ROW(VARCHAR(1), VARCHAR(1))";
        assertEquals(at(1, 31, "||", row + ", VARCHAR(1)"), refusal("SELECT 'a' LIKE ANY ('a', 'b') || '' AS r"));
        assertEquals(at(1, 31, "||", row + ", VARCHAR(1)"), refusal("SELECT 'a' LIKE ALL ('a', 'b') || '' AS r"));
        assertEquals(at(1, 32, "||", row + ", VARCHAR(1)"), refusal("SELECT 'a' ILIKE ANY ('A', 'b') || '' AS r"));
        assertEquals(at(1, 31, "+", row + ", NUMBER(1,0)"), refusal("SELECT 'a' LIKE ANY ('a', 'b') + 1 AS r"));
        assertEquals(at(1, 31, "-", row + ", NUMBER(1,0)"), refusal("SELECT 'a' LIKE ANY ('a', 'b') - 1 AS r"));
        assertEquals(at(1, 31, "IS NULL", row), refusal("SELECT 'a' LIKE ANY ('a', 'b') IS NULL AS r"));
        assertEquals(at(1, 31, "IS NOT NULL", row), refusal("SELECT 'a' LIKE ANY ('a', 'b') IS NOT NULL AS r"));
        assertEquals(at(1, 31, "GET", row + ", VARCHAR(1)"), refusal("SELECT 'a' LIKE ANY ('a', 'b') :x AS r"));
        assertEquals(at(1, 30, "GET", row + ", NUMBER(1,0)"), refusal("SELECT 'a' LIKE ANY ('a', 'b')[0] AS r"));
        assertEquals(at(0, -1, "EQUAL_NULL", row + ", VARCHAR(1)"),
            refusal("SELECT 'a' LIKE ANY ('a', 'b') IS DISTINCT FROM 'a' AS r"));
        assertEquals("SQL compilation error:\nInvalid argument for (+): (.", refusal("SELECT 'a' LIKE ANY ('a', 'b') (+) AS r"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(ROW('a', 'b') AS VARCHAR(134217728))] for parameter "
            + "'TO_VARCHAR'", refusal("SELECT 'a' LIKE ANY ('a', 'b')::VARCHAR AS r"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(ROW('a', 'b') AS NUMBER(38,0))] for parameter "
            + "'TO_NUMBER'", refusal("SELECT 'a' LIKE ANY ('a', 'b')::NUMBER AS r"));
        assertEquals("SQL compilation error:\nargument needs to be a string: 'ROW('a', 'b')'",
            refusal("SELECT 'a' LIKE ANY ('a', 'b') COLLATE 'de' AS r"));
    }

    @Test
    public void overColumnsTheListValueIsReadPerRow() {
        engine.execute("CREATE OR REPLACE TABLE lav (s VARCHAR, p VARCHAR)");
        engine.execute("INSERT INTO lav VALUES ('ab', 'a'), ('cd', 'x')");
        final List<Object> any = answers("SELECT s, s LIKE ANY (p) || '%' AS r FROM lav ORDER BY s");
        assertEquals(Boolean.TRUE, any.get(0));
        assertEquals(Boolean.FALSE, any.get(1));
        assertEquals("ab", scalar("SELECT s FROM lav WHERE s LIKE ANY (p) || '%'"));
        assertEquals("ab", scalar("SELECT s FROM lav WHERE s ILIKE ANY (UPPER(p)) || '%'"));
        assertEquals(at(1, 30, "||", "ROW(VARCHAR(16777216), VARCHAR(1)), VARCHAR(1)"),
            refusal("SELECT s, s LIKE ANY (p, 'c') || '%' AS r FROM lav ORDER BY s"));
    }

    /** What an anonymous block returns, as text. */
    private String blockAnswer(final String block) {
        return String.valueOf(scalar("EXECUTE IMMEDIATE $$" + block + "$$")).toLowerCase();
    }

    @Test
    public void aBlockExpressionAppliesTheOperatorsToTheList() {
        assertEquals("true", blockAnswer("BEGIN LET r BOOLEAN := 'ab' LIKE ANY ('a') || 'b'; RETURN r; END;"));
        assertEquals("true", blockAnswer("BEGIN LET s VARCHAR := 'ab'; RETURN s LIKE ANY ('a') || 'b'; END;"));
        assertEquals("true", blockAnswer("BEGIN RETURN 'a' LIKE ANY ('a') || ''; END;"));
        assertEquals("true", blockAnswer("DECLARE r BOOLEAN DEFAULT 'ab' LIKE ANY ('a') || 'b'; BEGIN RETURN r; END;"));
        final String numeric = refusal("EXECUTE IMMEDIATE $$BEGIN LET s VARCHAR := 'a'; RETURN s LIKE ANY ('a') + 1; END;$$");
        assertTrue(numeric.endsWith("Numeric value 'a' is not recognized"), numeric);
        final String row = refusal("EXECUTE IMMEDIATE $$BEGIN RETURN 'a' LIKE ANY ('a', 'b') || ''; END;$$");
        assertTrue(row.endsWith("Invalid argument types for function '||': (ROW(VARCHAR(1), VARCHAR(1)), VARCHAR(1))"),
            row);
    }
}
