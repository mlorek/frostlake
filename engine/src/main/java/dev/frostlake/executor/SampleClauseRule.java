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

import dev.frostlake.parser.FrostlakeParser;

import java.math.BigDecimal;

/**
 * What a SAMPLE / TABLESAMPLE clause is held to while the statement compiles, before a row is read. Its percentage
 * lies between 0 and 100, a fixed row count between 0 and 1,000,000 and a seed between 0 and 2,147,483,647; a value
 * outside is refused with the range, placed nowhere:
 *
 * <pre>
 *   SELECT COUNT(*) FROM t SAMPLE (101)
 *       SQL compilation error:
 *       parameter value out of range: sample percentage. Must be between 0 and 100.
 * </pre>
 *
 * A fixed row count past its range is "size of fixed sample. Must be between 0 and 1,000,000.", and a seed is
 * judged before the size. A bind standing where the size or the seed is written is never bound there — a {@code ?}
 * and a block's {@code :name} alike, whatever the variable holds — and is refused with the range's sentence, a
 * row count's as "fixed row size. Must be between 0 and 1,000,000." (live-verified).
 */
final class SampleClauseRule {

    private static final BigDecimal MOST_PERCENT = BigDecimal.valueOf(100);
    private static final BigDecimal MOST_ROWS = BigDecimal.valueOf(1_000_000);
    private static final BigDecimal MOST_SEED = BigDecimal.valueOf(Integer.MAX_VALUE);

    private SampleClauseRule() {
    }

    /**
     * Refuse a clause whose size or seed is a bind or lies outside its range.
     *
     * @param sample the clause
     */
    static void require(final FrostlakeParser.SampleClauseContext sample) {
        // The seed is judged before the size.
        final FrostlakeParser.SampleSeedContext seed = sample.sampleSeed();
        if (seed != null && (seed.INTEGER_LITERAL() == null
                || new BigDecimal(seed.INTEGER_LITERAL().getText()).compareTo(MOST_SEED) > 0)) {
            throw outOfRange("seed. Must be between 0 and 2,147,483,647.");
        }
        final boolean rows = sample.ROWS() != null;
        if (sample.COLON() != null || sample.QUESTION() != null) {
            throw outOfRange(rows ? "fixed row size. Must be between 0 and 1,000,000."
                : "sample percentage. Must be between 0 and 100.");
        }
        final String size = sample.INTEGER_LITERAL() != null ? sample.INTEGER_LITERAL().getText()
            : sample.FLOAT_LITERAL() != null ? sample.FLOAT_LITERAL().getText() : null;
        if (size != null && new BigDecimal(size).compareTo(rows ? MOST_ROWS : MOST_PERCENT) > 0) {
            throw outOfRange(rows ? "size of fixed sample. Must be between 0 and 1,000,000."
                : "sample percentage. Must be between 0 and 100.");
        }
    }

    private static RuntimeException outOfRange(final String parameter) {
        return new RuntimeException("SQL compilation error: \nparameter value out of range: " + parameter);
    }
}
