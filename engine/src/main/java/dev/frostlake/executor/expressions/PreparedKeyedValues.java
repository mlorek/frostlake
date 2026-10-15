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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.DeferredFault;
import dev.frostlake.storage.Row;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A correlated aggregate subquery's value for each key of its correlating equality, computed once over the inner
 * relation grouped by its key (see {@link dev.frostlake.executor.EqualityCorrelation}), so each outer row looks its
 * value up instead of running the subquery again. Like {@link PreparedInSet}, it answers only a probe of the kind
 * every key is, where hashing is the comparison's own: numbers numerically and scale-insensitively, texts by their
 * characters. A probe no key equals reads the aggregate over no rows, kept once the first such probe computed it.
 */
public final class PreparedKeyedValues {

    private final Map<String, Object> numericValues = new HashMap<>();
    private final Map<String, Object> textValues = new HashMap<>();
    private boolean onlyNumbers = true;
    private boolean onlyTexts = true;
    private boolean emptyKnown;
    private Object emptyValue;

    private PreparedKeyedValues() {
    }

    /**
     * @param rows one row for each key, the key first and the value second; a NULL key, which no probe equals,
     *             is left out
     * @return the values by key
     */
    public static PreparedKeyedValues build(final List<Row> rows) {
        final PreparedKeyedValues values = new PreparedKeyedValues();
        for (final Row row : rows) {
            final Object key = DeferredFault.read(row.getValue(0));
            if (key == null) {
                continue;
            }
            if (key instanceof Number) {
                final String numeric = numericKey(key);
                values.onlyTexts = false;
                if (numeric == null) {
                    values.onlyNumbers = false;
                } else {
                    values.numericValues.put(numeric, row.getValue(1));
                }
            } else if (key instanceof String) {
                values.onlyNumbers = false;
                values.textValues.put((String) key, row.getValue(1));
            } else {
                values.onlyNumbers = false;
                values.onlyTexts = false;
            }
        }
        return values;
    }

    /**
     * Whether the values settle a probe: a number against keys that are all numbers, a text against keys that are
     * all texts.
     *
     * @param probe the outer row's key, not NULL
     * @return whether {@link #holds} and {@link #valueOf} answer it
     */
    public boolean answers(final Object probe) {
        return probe instanceof Number ? onlyNumbers && numericKey(probe) != null
            : probe instanceof String && onlyTexts;
    }

    /**
     * @param probe an answered probe
     * @return whether some inner key equals it
     */
    public boolean holds(final Object probe) {
        return probe instanceof Number ? numericValues.containsKey(numericKey(probe)) : textValues.containsKey(probe);
    }

    /**
     * @param probe an answered probe some inner key equals
     * @return the aggregate's value over the rows of that key, as its cell holds it
     */
    public Object valueOf(final Object probe) {
        return probe instanceof Number ? numericValues.get(numericKey(probe)) : textValues.get(probe);
    }

    /** @return whether the aggregate's value over no rows is known yet */
    public boolean knowsEmptyValue() {
        return emptyKnown;
    }

    /** @return the aggregate's value over no rows, once known */
    public Object emptyValue() {
        return emptyValue;
    }

    /**
     * Keeps the aggregate's value over no rows, as the subquery computed it for an outer row no inner key equals.
     *
     * @param value the value
     */
    public void recordEmptyValue(final Object value) {
        emptyValue = value;
        emptyKnown = true;
    }

    /** Canonical key for numeric equality, or null for values BigDecimal can't parse (NaN/Infinity). */
    private static String numericKey(final Object number) {
        try {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
