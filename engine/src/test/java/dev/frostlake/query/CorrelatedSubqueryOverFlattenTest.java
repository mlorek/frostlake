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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A correlated subquery may reference the outer row's FLATTEN outputs UNQUALIFIED (VALUE, KEY, INDEX
 * have no natural alias): {@code WHERE EXISTS (SELECT 1 FROM t WHERE k = UPPER(VALUE:field))}. The
 * lateral context passed into the subquery only carried table-/alias-qualified names, so such
 * correlations silently resolved to NULL and an INNER-JOIN-plus-EXISTS pipeline produced zero rows.
 *
 * <p>That is EXISTS. A SCALAR subquery correlating the same way is refused outright unless it
 * aggregates — see {@code TableFunctionCorrelationRule} for the measured boundary.
 */
public class CorrelatedSubqueryOverFlattenTest extends BaseDatabaseTest {

    @BeforeEach
    public void setUpData() {
        engine.execute("CREATE TABLE raw_events (id INT, src VARIANT)");
        engine.execute("""
            INSERT INTO raw_events SELECT 1, PARSE_JSON(
              '[{"grp":{"guid":"g1"},"inst":{"iid":"i1"}},{"grp":{"guid":"g2"},"inst":{"iid":"i2"}}]')""");
        engine.execute("CREATE TABLE parents (pid VARCHAR, pkey VARCHAR)");
        engine.execute("INSERT INTO parents VALUES ('PFX:G1', 'K1')");
    }

    @Test
    public void correlatedExistsOnAFlattenValuePath() {
        final ResultSet rs = engine.executeQuery("""
            SELECT VALUE:inst:iid::VARCHAR iid
            FROM raw_events s, TABLE(FLATTEN(src, OUTER => true))
            WHERE EXISTS (SELECT 1 FROM parents WHERE pid = UPPER('PFX:' || VALUE:grp:guid::VARCHAR))""");
        assertEquals(1, rs.getRowCount());
        assertEquals("i1", rs.getRows().get(0).getValue(0));
    }

    @Test
    public void joinPlusCorrelatedExistsBothOverTheFlattenValue() {
        // The loader shape: source, FLATTEN, an INNER JOIN whose ON reads VALUE, and a correlated
        // EXISTS over the same VALUE path.
        final ResultSet rs = engine.executeQuery("""
            SELECT VALUE:inst:iid::VARCHAR iid, p.pkey
            FROM raw_events s, TABLE(FLATTEN(src, OUTER => true))
            INNER JOIN parents p ON p.pid = UPPER('PFX:' || VALUE:grp:guid::VARCHAR)
            WHERE EXISTS (SELECT 1 FROM parents WHERE pid = UPPER('PFX:' || VALUE:grp:guid::VARCHAR))""");
        assertEquals(1, rs.getRowCount());
        assertEquals("i1", rs.getRows().get(0).getValue(0));
        assertEquals("K1", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void innerColumnsStillWinOverOuterBareNames() {
        // The subquery's own column named like an outer one must keep resolving to the INNER table.
        engine.execute("CREATE TABLE inner_t (pid VARCHAR)");
        engine.execute("INSERT INTO inner_t VALUES ('PFX:G1')");
        final ResultSet rs = engine.executeQuery("""
            SELECT p.pkey
            FROM parents p
            WHERE EXISTS (SELECT 1 FROM inner_t WHERE pid = p.pid)""");
        assertEquals(1, rs.getRowCount());
    }

    /**
     * A SCALAR subquery correlating on a FLATTEN output is REFUSED, though the EXISTS above is not.
     * The refusal names the subquery's own opening parenthesis, counting BOTH line and position from 1.
     */
    @Test
    public void aCorrelatedScalarSubqueryOnAFlattenValueIsRefused() {
        assertEquals("SQL compilation error:\n"
            + "Unsupported subquery type cannot be evaluated at line 1, position 8",
            refusalOf("SELECT (SELECT pkey FROM parents"
                + " WHERE pid = UPPER('PFX:' || VALUE:grp:guid::VARCHAR)) k"
                + " FROM raw_events s, TABLE(FLATTEN(src, OUTER => true))"
                + " ORDER BY VALUE:inst:iid::VARCHAR"));
    }

    /** An aggregate in the subquery's select list makes exactly that query legal. */
    @Test
    public void anAggregateInTheSubqueryMakesItLegal() {
        final ResultSet rs = engine.executeQuery("""
            SELECT (SELECT MAX(pkey) FROM parents WHERE pid = UPPER('PFX:' || VALUE:grp:guid::VARCHAR)) k
            FROM raw_events s, TABLE(FLATTEN(src, OUTER => true))
            ORDER BY VALUE:inst:iid::VARCHAR""");
        assertEquals(2, rs.getRowCount());
        assertEquals("K1", rs.getRows().get(0).getValue(0));
        assertEquals(null, rs.getRows().get(1).getValue(0));
    }

    /** The WHERE clause is refused the same way, at the subquery's place in it. */
    @Test
    public void theSameSubqueryInWhereIsRefusedAtItsOwnPosition() {
        assertEquals("SQL compilation error:\n"
            + "Unsupported subquery type cannot be evaluated at line 1, position 96",
            refusalOf("SELECT VALUE:inst:iid::VARCHAR iid"
                + " FROM raw_events s, TABLE(FLATTEN(src, OUTER => true))"
                + " WHERE (SELECT pkey FROM parents"
                + " WHERE pid = UPPER('PFX:' || VALUE:grp:guid::VARCHAR)) = 'K1'"));
    }

    /** It is the table FUNCTION that matters, not FLATTEN: SPLIT_TO_TABLE is refused alike. */
    @Test
    public void anotherTableFunctionsOutputIsRefusedTheSameWay() {
        assertEquals("SQL compilation error:\n"
            + "Unsupported subquery type cannot be evaluated at line 1, position 8",
            refusalOf("SELECT (SELECT pkey FROM parents WHERE pid = t.value) k"
                + " FROM parents p, TABLE(SPLIT_TO_TABLE(p.pid, ',')) t"));
    }

    /**
     * What the rule does NOT touch, so it stays as narrow as the account's: a subquery that correlates
     * to the BASE table while a table function sits in the same FROM, an uncorrelated one beside a
     * table function, and an ordinary correlated scalar subquery with no table function in sight.
     */
    @Test
    public void theNeighbouringShapesAreStillAccepted() {
        assertEquals(2, engine.executeQuery(
            "SELECT (SELECT pkey FROM parents WHERE pkey <> s.id::VARCHAR) k"
            + " FROM raw_events s, TABLE(FLATTEN(src, OUTER => true))").getRowCount());
        assertEquals(2, engine.executeQuery(
            "SELECT (SELECT pkey FROM parents WHERE pid = 'PFX:G1') k"
            + " FROM raw_events s, TABLE(FLATTEN(src, OUTER => true))").getRowCount());
        assertEquals(1, engine.executeQuery(
            "SELECT p.pid, (SELECT pkey FROM parents q WHERE q.pid = p.pid) k"
            + " FROM parents p").getRowCount());
    }

    private String refusalOf(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        return error.getMessage();
    }
}
