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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code ARRAY_AGG(DISTINCT expr)} — repeated values are dropped. The DISTINCT flag was ignored on the
 * generic-accumulator path, so duplicates survived into the array (with or without WITHIN GROUP).
 */
public class ArrayAggDistinctTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE labels (grp INTEGER, tag VARCHAR)");
        engine.execute("INSERT INTO labels VALUES (1, 'T2'), (1, 'T1'), (1, 'T2'), (1, 'T1'), (2, 'T9')");
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void distinctWithinGroupDropsDuplicatesAndSorts() {
        assertEquals("[\"T1\",\"T2\"]", String.valueOf(scalar(
            "SELECT ARRAY_AGG(DISTINCT tag) WITHIN GROUP (ORDER BY tag) FROM labels WHERE grp = 1 GROUP BY grp")));
    }

    @Test
    public void distinctWithoutWithinGroupAlsoDropsDuplicates() {
        assertEquals(2L, ((Number) scalar(
            "SELECT ARRAY_SIZE(ARRAY_AGG(DISTINCT tag)) FROM labels WHERE grp = 1 GROUP BY grp")).longValue());
    }

    @Test
    public void plainArrayAggStillKeepsDuplicates() {
        assertEquals(4L, ((Number) scalar(
            "SELECT ARRAY_SIZE(ARRAY_AGG(tag)) FROM labels WHERE grp = 1 GROUP BY grp")).longValue());
    }

    @Test
    public void numericDuplicatesCompareByValue() {
        // 1 and 1.0 are the same value for DISTINCT purposes.
        engine.execute("CREATE TABLE nums (v NUMBER(10,1))");
        engine.execute("INSERT INTO nums VALUES (1), (1.0), (2)");
        assertEquals(2L, ((Number) scalar(
            "SELECT ARRAY_SIZE(ARRAY_AGG(DISTINCT v)) FROM nums")).longValue());
    }

    @Test
    public void nullInputsAreSkipped() {
        // Snowflake: ARRAY_AGG ignores SQL NULLs — the loader idiom ARRAY_AGG(IFF(cond, obj, NULL))
        // must yield [] when no row qualifies, not [null].
        engine.execute("CREATE TABLE nulled (grp INTEGER, v VARCHAR)");
        engine.execute("INSERT INTO nulled VALUES (1, NULL), (1, NULL), (2, 'a'), (2, NULL)");
        assertEquals("[]", String.valueOf(scalar(
            "SELECT ARRAY_AGG(v) FROM nulled WHERE grp = 1 GROUP BY grp")));
        assertEquals("[\"a\"]", String.valueOf(scalar(
            "SELECT ARRAY_AGG(v) FROM nulled WHERE grp = 2 GROUP BY grp")));
    }

    @Test
    public void nullsSkippedWithWithinGroupOrdering() {
        engine.execute("CREATE TABLE nulled2 (v VARCHAR, k INTEGER)");
        engine.execute("INSERT INTO nulled2 VALUES ('b', 2), (NULL, 3), ('a', 1)");
        assertEquals("[\"a\",\"b\"]", String.valueOf(scalar(
            "SELECT ARRAY_AGG(v) WITHIN GROUP (ORDER BY k) FROM nulled2")));
    }
}
