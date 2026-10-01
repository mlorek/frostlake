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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.executor.QueryResultCache;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.StringType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * LAST_QUERY_ID([index]) — the ID of a statement the session ran (live-verified): -1, the default, is the
 * most recent, -2 the one before it, and a positive index counts from the session's first statement. 0,
 * NULL, and an index past either end of the session's history answer NULL.
 *
 * <p>The index is a NUMBER(18,0): a numeric text is read as one, rounded, and a BOOLEAN, a temporal or a
 * semi-structured value is refused as an argument type. It must be constant — which statement it names
 * is settled while the statement compiles — and no further than 10,000 statements either way; both rules
 * are the evaluator's, since they judge the argument as written.
 */
public class LastQueryId extends NumericArgumentFunction {

    /** How far either way an index may reach. */
    public static final int INDEX_LIMIT = 10000;

    /** The refusal for an index past {@link #INDEX_LIMIT}. */
    public static final String EXCEEDS_LIMIT = "Value for parameter 1 exceeds maximum allowable value (10,000).";

    private final QueryResultCache resultCache;

    public LastQueryId(final QueryResultCache resultCache) {
        super("LAST_QUERY_ID", StringType.VARCHAR);
        this.resultCache = resultCache;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.isEmpty()) {
            return resultCache.getQueryId(-1);
        }
        final BigDecimal index = wholeIndex(args.get(0));
        if (index == null) {
            return null;
        }
        requireWithinLimit(index);
        return resultCache.getQueryId(index.intValue());
    }

    /**
     * An index as the NUMBER(18,0) it is converted to: a number or a numeric text rounded half away from
     * zero, or null for NULL.
     *
     * @param value the argument's value
     * @return the whole number, or null
     */
    public static BigDecimal wholeIndex(final Object value) {
        if (value == null) {
            return null;
        }
        final BigDecimal number = value instanceof BigDecimal ? (BigDecimal) value
            : value instanceof Number ? new BigDecimal(value.toString())
            : new BigDecimal(value.toString().trim());
        return number.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * Refuses an index further than {@link #INDEX_LIMIT} either way, as the account refuses it while the
     * statement compiles.
     *
     * @param index a whole index
     */
    public static void requireWithinLimit(final BigDecimal index) {
        if (index.abs().compareTo(BigDecimal.valueOf(INDEX_LIMIT)) > 0) {
            throw new RuntimeException(SqlCompilationError.of(EXCEEDS_LIMIT));
        }
    }

    @Override
    public int getMinArgCount() {
        return 0;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }
}
