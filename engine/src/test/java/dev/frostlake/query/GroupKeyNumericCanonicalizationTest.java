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
 * Equal numbers with different runtime types (a VARIANT-cast NUMBER vs an integer literal, 1 vs 1.0)
 * must land in the SAME group / window partition / pivot bucket. Mixed representations arise from
 * set-operation branches and {@code value:field::NUMBER} casts; before canonicalization they split
 * into duplicate groups and QUALIFY dedups silently kept both rows.
 */
public class GroupKeyNumericCanonicalizationTest extends BaseDatabaseTest {

    @Test
    public void testGroupByMergesVariantCastAndLiteral() {
        final ResultSet rs = engine.executeQuery("""
            SELECT k, COUNT(*) AS c
            FROM (SELECT PARSE_JSON('{"n":1}'):n::NUMBER AS k UNION ALL SELECT 1)
            GROUP BY k
            """);
        assertEquals(1, rs.getRows().size());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void testGroupByMergesIntegerAndDecimalScales() {
        final ResultSet rs = engine.executeQuery("""
            SELECT k, COUNT(*) AS c
            FROM (SELECT 1 AS k UNION ALL SELECT 1.0 UNION ALL SELECT 1.00 UNION ALL SELECT 2)
            GROUP BY k ORDER BY k
            """);
        assertEquals(2, rs.getRows().size());
        assertEquals(3L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(1L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void testGroupByKeepsDistinctFractions() {
        final ResultSet rs = engine.executeQuery("""
            SELECT k, COUNT(*) AS c
            FROM (SELECT 1.5 AS k UNION ALL SELECT 1.50 UNION ALL SELECT 1.25)
            GROUP BY k ORDER BY k
            """);
        assertEquals(2, rs.getRows().size());
    }

    @Test
    public void testQualifyRowNumberPartitionMergesMixedTypes() {
        // Before canonicalization the two rows fell into two partitions and BOTH survived the QUALIFY.
        final ResultSet rs = engine.executeQuery("""
            SELECT k, src
            FROM (SELECT PARSE_JSON('{"n":1}'):n::NUMBER AS k, 'a' AS src UNION ALL SELECT 1, 'b')
            QUALIFY ROW_NUMBER() OVER (PARTITION BY k ORDER BY src) = 1
            """);
        assertEquals(1, rs.getRows().size());
        assertEquals("a", rs.getRows().get(0).getValue(1));
    }

    @Test
    public void testWindowCountOverMixedTypePartition() {
        final ResultSet rs = engine.executeQuery("""
            SELECT COUNT(1) OVER (PARTITION BY k) AS c
            FROM (SELECT PARSE_JSON('{"n":7}'):n::NUMBER AS k UNION ALL SELECT 7)
            """);
        assertEquals(2, rs.getRows().size());
        assertEquals(2L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
    }
}
