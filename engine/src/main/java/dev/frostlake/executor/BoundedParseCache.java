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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thread-safe, bounded LRU cache for parse trees keyed by their source text. Parsing is expensive and
 * many statements/expressions repeat — a correlated subquery re-evaluated per outer row, or an operator
 * re-parsing the same predicate across rows — so memoizing by text is a large win. Caching <em>every</em>
 * distinct text, however, is an unbounded leak: a bulk load issuing thousands of distinct multi-row
 * INSERTs, or a sweep of distinct-literal predicates (the engine has no prepared statements), would
 * otherwise retain every parse tree forever — and ANTLR trees run on the order of 100x their source text.
 *
 * <p>This caps the entry count, evicting the least-recently-used entry, so a genuinely hot entry accessed
 * every row survives the loop while one-off statements age out. It also refuses to cache oversized text:
 * a giant one-off statement (e.g. a multi-thousand-row INSERT) whose tree dwarfs any normal query is never
 * worth a slot. Access is synchronized, but a cache lookup is cheap relative to the parse it avoids, so
 * the shared monitor is not a meaningful contention point.
 */
final class BoundedParseCache<V> {

    private final int maxKeyLength;
    private final Map<String, V> entries;

    BoundedParseCache(final int capacity, final int maxKeyLength) {
        this.maxKeyLength = maxKeyLength;
        this.entries = new LinkedHashMap<String, V>(capacity * 2, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, V> eldest) {
                return size() > capacity;
            }
        };
    }

    synchronized V get(final String key) {
        return entries.get(key);
    }

    synchronized void put(final String key, final V value) {
        if (key.length() <= maxKeyLength) {
            entries.put(key, value);
        }
    }
}
