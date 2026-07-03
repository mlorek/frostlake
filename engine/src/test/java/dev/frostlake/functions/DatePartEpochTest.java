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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DATE_PART with the epoch family, both as a bare keyword (Snowflake's usual spelling) and quoted. The
 * bare form (e.g. {@code DATE_PART(epoch_second, CURRENT_TIMESTAMP())}) previously failed with
 * "Column not found: epoch_second" because the epoch units were absent from the bareword-keyword whitelist.
 */
public class DatePartEpochTest extends BaseDatabaseTest {

    private long scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    private long epochOf(final String part) {
        // 2024-01-01 00:00:00 UTC == 1704067200 s (datePart treats the value as UTC).
        return scalar("SELECT DATE_PART(" + part + ", TO_TIMESTAMP('2024-01-01 00:00:00'))");
    }

    @Test
    public void bareEpochSecondOnCurrentTimestampWorks() {
        // The exact reported query — must not throw, and returns a recent Unix timestamp.
        final long epoch = scalar("SELECT DATE_PART(epoch_second, CURRENT_TIMESTAMP())");
        assertTrue(epoch > 1_700_000_000L, "expected a recent epoch second, got " + epoch);
    }

    @Test
    public void epochFamilyDeterministic() {
        assertEquals(1704067200L, epochOf("epoch_second"));
        assertEquals(1704067200L, epochOf("epoch"));               // EPOCH is a synonym of EPOCH_SECOND
        assertEquals(1704067200000L, epochOf("epoch_millisecond"));
        assertEquals(1704067200000000L, epochOf("epoch_microsecond"));
        assertEquals(1704067200000000000L, epochOf("epoch_nanosecond"));
    }

    @Test
    public void quotedFormStillWorksAndOtherBarewordsResolve() {
        assertEquals(1704067200L, scalar("SELECT DATE_PART('EPOCH_SECOND', TO_TIMESTAMP('2024-01-01 00:00:00'))"));
        // A full-word bareword that was also missing from the whitelist now resolves.
        assertEquals(15L, scalar("SELECT DATE_PART(dayofmonth, TO_TIMESTAMP('2024-03-15 12:00:00'))"));
    }
}
