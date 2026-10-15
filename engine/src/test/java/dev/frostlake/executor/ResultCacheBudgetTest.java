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
package dev.frostlake.executor;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The results kept for RESULT_SCAN are bounded by the cells they hold as well as by their number: past the budget
 * the oldest results are let go, and each session's latest result stays however large it is.
 */
public class ResultCacheBudgetTest {

    /** A one-column result of {@code rows} rows. */
    private static ResultSet rows(final int rows) {
        final List<Row> data = new ArrayList<>();
        for (int i = 0; i < rows; i++) {
            data.add(new Row(Integer.valueOf(i)));
        }
        return new ResultSet(Collections.singletonList(new ResultSetColumn("X", new NumericType("NUMBER", 38, 0))),
            data);
    }

    @Test
    public void theOldestResultsGoPastTheBudget() {
        final QueryResultCache cache = new QueryResultCache(500, 10);
        final String first = cache.cacheResult("SELECT 1", rows(6));
        final String second = cache.cacheResult("SELECT 2", rows(3));
        assertTrue(cache.hasResult(first));
        final String third = cache.cacheResult("SELECT 3", rows(4));
        assertFalse(cache.hasResult(first), "the oldest result goes once the cells pass the budget");
        assertTrue(cache.hasResult(second));
        assertTrue(cache.hasResult(third));
    }

    @Test
    public void aSessionsLatestResultStaysHoweverLarge() {
        final QueryResultCache cache = new QueryResultCache(500, 10);
        final String small = cache.cacheResult("SELECT 1", rows(2));
        final String large = cache.cacheResult("SELECT 2", rows(50));
        assertFalse(cache.hasResult(small));
        assertTrue(cache.hasResult(large), "the latest result is what RESULT_SCAN(LAST_QUERY_ID()) reads");
    }

    @Test
    public void eachSessionKeepsItsLatest() {
        final QueryResultCache cache = new QueryResultCache(500, 10);
        cache.beginSessionScope("a");
        final String fromA = cache.cacheResult("SELECT 1", rows(8));
        cache.beginSessionScope("b");
        final String fromB = cache.cacheResult("SELECT 2", rows(8));
        cache.clearSessionScope();
        assertTrue(cache.hasResult(fromA));
        assertTrue(cache.hasResult(fromB));
    }
}
