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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.values.VariantValue;
import java.math.BigDecimal;
import java.util.List;

/**
 * Stateless value/ordering comparison helpers extracted from {@link QueryExecutor}. These are pure
 * functions used by sort, DISTINCT, and column-resolution logic across query stages; none of them
 * read engine instance state.
 */
public final class ValueComparisons {

    private ValueComparisons() {
    }

    /** Like {@link #getColumnIndex} but answers -1 instead of throwing — for callers probing
     *  whether a text is a bare column at all (the throw built a stack trace per probe). */
    public static int findColumnIndex(final Table table, final String columnName) {
        final List<TableColumn> cols = table.getColumns();
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i).getName().equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1;
    }

    public static int getColumnIndex(final Table table, final String columnName) {
        // One snapshot for the whole scan — Table.getColumns() copies its list per call, so the
        // former per-iteration double call cost 2i copies to resolve column i.
        final List<TableColumn> cols = table.getColumns();
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i).getName().equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        throw new RuntimeException(SqlCompilationError.invalidIdentifier(columnName));
    }

    /**
     * Normalize a value for DISTINCT comparison.
     * Converts numbers to Double for consistent comparison (e.g., 1 == 1.0).
     */
    public static Object normalizeValueForDistinct(final Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return value;
    }

    /**
     * Canonicalize one grouping-key value so equal numbers with different runtime types land in the
     * same bucket: Integer 1, Long 1, Double 1.0 and BigDecimal 1.00 must form one group (mixed
     * representations arise from VARIANT casts, set-operation branches and literal folding). Unlike
     * the join-key normalizer this must not over-group — the bucket IS the grouping decision, with no
     * downstream re-confirmation — so only exact-value numeric canonicalization is applied; strings,
     * temporals and booleans pass through unchanged. Integral values become {@code Long}, everything
     * else a trailing-zero-stripped {@code BigDecimal}, both of which honor equals/hashCode.
     */
    public static Object canonicalGroupKeyValue(final Object value) {
        if (value instanceof VariantValue) {
            // Group semi-structured values by their JSON text so typed and text-carried equal values
            // fall into one group.
            return ((VariantValue) value).text();
        }
        if (!(value instanceof Number)) {
            return value;
        }
        if (value instanceof Double && (((Double) value).isNaN() || ((Double) value).isInfinite())) {
            return value;
        }
        if (value instanceof Float && (((Float) value).isNaN() || ((Float) value).isInfinite())) {
            return value;
        }
        final BigDecimal exact = new BigDecimal(value.toString()).stripTrailingZeros();
        if (exact.scale() <= 0) {
            try {
                return Long.valueOf(exact.longValueExact());
            } catch (final ArithmeticException beyondLongRange) {
                return exact;
            }
        }
        return exact;
    }

    @SuppressWarnings("unchecked")
    public static int compareValues(final Object v1, final Object v2) {
        // NULLs sort as greater than any non-NULL value (Snowflake default). With the ASC/DESC sign
        // applied by the caller this yields NULLS LAST for ASC and NULLS FIRST for DESC.
        if (v1 == null && v2 == null) return 0;
        if (v1 == null) return 1;
        if (v2 == null) return -1;

        // Two numbers may have different runtime types across set-operation branches (e.g. a column that is
        // Long in one SELECT and BigDecimal in another) — Long.compareTo(BigDecimal) would ClassCastException,
        // so compare by value. Integral and same-type BigDecimal pairs compare directly; the
        // toString/BigDecimal bridge stays for Double/Float and mixed pairs.
        if (v1 instanceof Number && v2 instanceof Number) {
            if (isIntegral(v1) && isIntegral(v2)) {
                return Long.compare(((Number) v1).longValue(), ((Number) v2).longValue());
            }
            if (v1 instanceof BigDecimal && v2 instanceof BigDecimal) {
                return ((BigDecimal) v1).compareTo((BigDecimal) v2);
            }
            return new BigDecimal(v1.toString()).compareTo(new BigDecimal(v2.toString()));
        }
        if (v1 instanceof Comparable && v2 instanceof Comparable && v1.getClass() == v2.getClass()) {
            return ((Comparable) v1).compareTo(v2);
        }

        return v1.toString().compareTo(v2.toString());
    }

    private static boolean isIntegral(final Object value) {
        return value instanceof Long || value instanceof Integer
            || value instanceof Short || value instanceof Byte;
    }

    /** NULLS FIRST → TRUE, NULLS LAST → FALSE, unspecified → null, for an ORDER BY item. */
    public static Boolean nullsFirstFlag(final FrostlakeParser.OrderItemContext item) {
        if (item.NULLS() == null) {
            return null;
        }
        return item.FIRST() != null ? Boolean.TRUE : Boolean.FALSE;
    }

    /**
     * Compare two ORDER BY key values, returning the final (already ASC/DESC-adjusted) ordering. NULL
     * placement follows an explicit NULLS FIRST/LAST when {@code nullsFirst} is non-null, otherwise the
     * Snowflake default: NULLS LAST for ASC, NULLS FIRST for DESC.
     */
    public static int compareOrderKey(final Object v1, final Object v2, final boolean ascending, final Boolean nullsFirst) {
        if (v1 == null && v2 == null) {
            return 0;
        }
        if (v1 == null || v2 == null) {
            final boolean nf = nullsFirst != null ? nullsFirst.booleanValue() : !ascending;
            if (v1 == null) {
                return nf ? -1 : 1;
            }
            return nf ? 1 : -1;
        }
        final int cmp = compareValues(v1, v2);
        return ascending ? cmp : -cmp;
    }

    /** True when s matches \d+\.\d+ (a numeric literal like 1.5), to tell it from a qualified name. */
    public static boolean isNumericDotted(final String s) {
        final int dot = s.indexOf('.');
        if (dot <= 0 || dot == s.length() - 1 || s.indexOf('.', dot + 1) >= 0) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (i != dot && !Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
