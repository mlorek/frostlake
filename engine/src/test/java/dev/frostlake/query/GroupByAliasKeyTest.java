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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code GROUP BY <alias>} — Snowflake groups by the aliased SELECT expression when the name is not a
 * real column (a real column always wins). The bare alias name used to fail evaluation on every row,
 * and the error-sentinel group key silently collapsed ALL rows into a single group — including the
 * scoring idiom {@code SELECT …, value::string AS target_id … , TABLE(FLATTEN(ids)) GROUP BY
 * container, target_id} where the per-target dimension vanished entirely.
 */
public class GroupByAliasKeyTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE t (c VARCHAR, aid VARCHAR, v NUMBER(10,2), ids ARRAY)");
        engine.execute("""
            INSERT INTO t SELECT 'x', 'a1', 1, ['t1','t2'] UNION ALL
                          SELECT 'X', 'a2', 2, ['t1'] UNION ALL
                          SELECT 'y', 'a3', 3, ['t2']""");
    }

    @Test
    public void groupByAnExpressionAlias() {
        final ResultSet rs = engine.executeQuery(
            "SELECT UPPER(c) AS uc, SUM(v) FROM t GROUP BY uc ORDER BY uc");
        assertEquals(2, rs.getRowCount());
        assertEquals(3.0, ((Number) rs.getRows().get(0).getValue(1)).doubleValue());   // X: 1 + 2
        assertEquals(3.0, ((Number) rs.getRows().get(1).getValue(1)).doubleValue());   // Y: 3
    }

    @Test
    public void aRealColumnWinsOverASameNamedAlias() {
        // GROUP BY c must group by the COLUMN c ('x','X','y' — case-sensitive), not by UPPER(c).
        assertEquals(3, engine.executeQuery(
            "SELECT UPPER(c) AS c, SUM(v) FROM t GROUP BY c ORDER BY 1, 2").getRowCount());
    }

    @Test
    public void groupByAnAliasOfALateralFlattenValue() {
        // The scoring-loader idiom: per-target regrouping of flattened array membership.
        final ResultSet rs = engine.executeQuery("""
            SELECT c, value::string AS tid, AVG(v), COUNT(DISTINCT aid)
            FROM t, TABLE(FLATTEN(ids)) GROUP BY c, tid ORDER BY c, tid""");
        assertEquals(4, rs.getRowCount());                                             // (X,t1),(x,t1),(x,t2),(y,t2)
        final ResultSet upper = engine.executeQuery("""
            SELECT UPPER(c) AS uc, value::string AS tid, AVG(v)
            FROM t, TABLE(FLATTEN(ids)) GROUP BY uc, tid ORDER BY uc, tid""");
        assertEquals(3, upper.getRowCount());                                          // (X,t1),(X,t2),(Y,t2)
        assertEquals(1.5, ((Number) upper.getRows().get(0).getValue(2)).doubleValue()); // X/t1: avg(1,2)
    }

    @Test
    public void ordinalAndExpressionFormsStillWork() {
        assertEquals(2, engine.executeQuery(
            "SELECT UPPER(c) AS uc, SUM(v) FROM t GROUP BY 1 ORDER BY 1").getRowCount());
        assertEquals(2, engine.executeQuery(
            "SELECT UPPER(c) AS uc, SUM(v) FROM t GROUP BY UPPER(c) ORDER BY 1").getRowCount());
    }
}
