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

/**
 * One parenthesized value before IN is that value, not a one-column row: {@code (1 = 1) IN (TRUE)} is an
 * ordinary IN, three-valued like one, over a list, a subquery or a list of one-value rows. A wider row in the
 * list is refused with the value typed as the scalar it is. Live-verified.
 */
public class ParenthesizedInOperandTest extends BaseDatabaseTest {

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

    /** The second column of every row, in the order returned. */
    private List<Object> answers(final String sql) {
        final List<Object> out = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.add(row.getValue(1));
        }
        return out;
    }

    private void createTable() {
        engine.execute("CREATE OR REPLACE TABLE tpi (n INT, s VARCHAR, b BOOLEAN)");
        engine.execute("INSERT INTO tpi VALUES (1, 'a', TRUE), (2, 'b', FALSE), (NULL, NULL, NULL)");
    }

    @Test
    public void aParenthesizedPredicateIsTheValueItHolds() {
        assertEquals(Boolean.TRUE, scalar("SELECT (1 = 1) IN (TRUE) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT (1 = 1) NOT IN (TRUE) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT (1 = 1) IN (FALSE, TRUE) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT (1 = 1) IN (1) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT (1 = 1) IN ('true') AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT ('a' LIKE 'a') IN (TRUE) AS r"));
        assertEquals("Boolean value 'abc' is not recognized", refusal("SELECT (1 = 1) IN ('abc') AS r"));
    }

    @Test
    public void aParenthesizedValueIsAnOrdinaryInOverAList() {
        assertEquals(Boolean.TRUE, scalar("SELECT (1) IN (1, 2) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT (1) NOT IN (1, 2) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT (3) IN (1, 2) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT ((1)) IN (1, 2) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT (1 + 1) IN (2) AS r"));
        assertNull(scalar("SELECT (NULL) IN (1, 2) AS r"));
        assertNull(scalar("SELECT (1) IN (NULL, 2) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT (1) IN ('1') AS r"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT (1) IN ('x') AS r"));
    }

    @Test
    public void aParenthesizedValueIsAScalarInOverASubquery() {
        assertEquals(Boolean.TRUE, scalar("SELECT (1) IN (SELECT 1) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT (2) IN (SELECT 1) AS r"));
        assertNull(scalar("SELECT (NULL) IN (SELECT 1) AS r"));
        assertNull(scalar("SELECT (NULL) IN (SELECT 1 WHERE FALSE) AS r"));
        assertNull(scalar("SELECT (2) IN (SELECT NULL) AS r"));
        assertNull(scalar("SELECT (2) NOT IN (SELECT NULL) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT (1 = 1) IN (SELECT TRUE) AS r"));
    }

    @Test
    public void rowsOfOneValueAreThatManyValues() {
        assertEquals(Boolean.TRUE, scalar("SELECT (1) IN ((1), (2)) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT (3) IN ((1), (2)) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT (1) NOT IN ((1), (2)) AS r"));
    }

    @Test
    public void aWiderRowBesideOneValueIsRefused() {
        assertEquals("SQL compilation error: error line 1 at position 11\nInvalid argument types for function 'IN': "
            + "(NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))", refusal("SELECT (1) IN ((1, 2)) AS r"));
        assertEquals("SQL compilation error: error line 1 at position 11\nInvalid argument types for function 'IN': "
            + "(NUMBER(1,0), NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))", refusal("SELECT (1) IN ((1), (2, 3)) AS r"));
        assertEquals("SQL compilation error: error line 0 at position -1\nInvalid argument types for function 'IN': "
            + "(NUMBER(1,0), ROW(NUMBER(1,0), NUMBER(1,0)))", refusal("SELECT (1) NOT IN ((1, 2)) AS r"));
    }

    @Test
    public void overColumnsTheInIsThreeValued() {
        createTable();
        final List<Object> in = answers("SELECT n, (n) IN (1, 3) AS r FROM tpi ORDER BY n");
        assertEquals(Boolean.TRUE, in.get(0));
        assertEquals(Boolean.FALSE, in.get(1));
        assertNull(in.get(2));
        final List<Object> notIn = answers("SELECT n, (n) NOT IN (1, 3) AS r FROM tpi ORDER BY n");
        assertEquals(Boolean.FALSE, notIn.get(0));
        assertEquals(Boolean.TRUE, notIn.get(1));
        assertNull(notIn.get(2));
        final List<Object> subquery = answers("SELECT n, (n) IN (SELECT n FROM tpi) AS r FROM tpi ORDER BY n");
        assertEquals(Boolean.TRUE, subquery.get(0));
        assertEquals(Boolean.TRUE, subquery.get(1));
        assertNull(subquery.get(2));
        final List<Object> predicate = answers("SELECT n, (n = 1) IN (b) AS r FROM tpi ORDER BY n");
        assertEquals(Boolean.TRUE, predicate.get(0));
        assertEquals(Boolean.TRUE, predicate.get(1));
        assertNull(predicate.get(2));
        assertEquals(2, engine.executeQuery("SELECT n FROM tpi WHERE (n) IN (1, 2)").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT n FROM tpi WHERE (n = 1) IN (TRUE)").getRowCount());
    }

    @Test
    public void theInIsOneOperatorOfAComparisonChain() {
        assertEquals(Boolean.TRUE, scalar("SELECT (1 = 1) IN (TRUE) = TRUE AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT TRUE = (1) IN (TRUE) AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 'x' = ('y') IN (FALSE) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT 2 = (2) IN (SELECT FALSE) AS r"));
    }
}
