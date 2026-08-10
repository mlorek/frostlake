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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A complex (non-aggregate) expression projected alongside an aggregate in a GROUP BY query must
 * be evaluated, not column-looked-up. The grouped-evaluation path previously resolved a select
 * item by column name and returned null for anything that was not a bare column (arithmetic,
 * boolean, function of the group keys); these now evaluate on a representative group row via the
 * AST evaluator (group keys are constant within a group).
 */
public class GroupByComplexProjectionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE DATABASE IF NOT EXISTS test_db");
        engine.execute("CREATE TABLE gp (a INT, b INT, name VARCHAR)");
        engine.execute("INSERT INTO gp VALUES (1, 1, 'x'), (1, 0, 'x'), (0, 0, 'y')");
    }

    private Set<String> projectedColumn0(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final Set<String> vals = new HashSet<>();
        for (final Row r : rs.getRows()) {
            vals.add(String.valueOf(r.getValue(0)));
        }
        return vals;
    }

    @Test
    public void testFunctionProjection() {
        // groups name='x','y' -> UPPER -> 'X','Y'
        assertEquals(new HashSet<>(Arrays.asList("X", "Y")),
            projectedColumn0("SELECT UPPER(name), COUNT(*) FROM gp GROUP BY name"));
    }

    @Test
    public void testArithmeticProjection() {
        // groups (1,1)->2, (1,0)->1, (0,0)->0
        assertEquals(new HashSet<>(Arrays.asList("0", "1", "2")),
            projectedColumn0("SELECT a + b, COUNT(*) FROM gp GROUP BY a, b"));
    }

    @Test
    public void testBooleanProjection() {
        // groups (1,1)->true, (1,0)->false, (0,0)->false
        assertEquals(new HashSet<>(Arrays.asList("true", "false")),
            projectedColumn0("SELECT a > 0 AND b > 0, COUNT(*) FROM gp GROUP BY a, b"));
    }
}
