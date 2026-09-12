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

import dev.frostlake.executor.SqlCompilationError;

import java.util.Locale;

/**
 * The collation an expression carries into a comparison, with the level it holds it at — see
 * {@link CollationLevel}. The specification is kept lower-cased, as live reports and compares it:
 * {@code 'EN-CI'} and {@code 'en-ci'} are one collation.
 */
public final class ExpressionCollation {

    /** The collation of an expression that carries none. */
    public static final ExpressionCollation NONE = new ExpressionCollation(null, CollationLevel.NONE);

    private final String spec;
    private final CollationLevel level;

    private ExpressionCollation(final String spec, final CollationLevel level) {
        this.spec = spec;
        this.level = level;
    }

    /**
     * A collation named in the statement. The empty specification is no collation at all: COLLATE ''
     * takes a column's collation AWAY, so the other operand's decides (live-verified).
     *
     * @param spec the specification as written
     * @return the collation it names
     */
    public static ExpressionCollation explicit(final String spec) {
        return spec.isEmpty() ? NONE
            : new ExpressionCollation(spec.toLowerCase(Locale.ROOT), CollationLevel.EXPLICIT);
    }

    /**
     * A column's declared collation, or none for a column declared without one.
     *
     * @param spec the column's specification, or null
     * @return the collation it carries
     */
    public static ExpressionCollation column(final String spec) {
        return spec == null || spec.isEmpty() ? NONE
            : new ExpressionCollation(spec.toLowerCase(Locale.ROOT), CollationLevel.COLUMN);
    }

    /** The lower-cased specification, or null when there is none. */
    public String getSpec() {
        return spec;
    }

    /** The level the collation is held at. */
    public CollationLevel getLevel() {
        return level;
    }

    /** The rules a comparison under this collation runs by, or null for a binary comparison. */
    public CollationSpec toRules() {
        return spec == null ? null : CollationSpec.parse(spec);
    }

    /**
     * The collation two operands settle on: the higher level's, or the shared one when both stand at
     * the same level. Two DIFFERENT specifications at one level are a compilation error that names
     * {@code first} and then {@code second} — which operand counts as first is the caller's to know,
     * because live's order depends on the construct: a comparison names its right side first, a
     * concatenation and LIKE their left.
     *
     * @param first  the collation named first in a refusal
     * @param second the collation named second
     * @return the collation both compare under
     */
    public static ExpressionCollation combine(final ExpressionCollation first,
                                              final ExpressionCollation second) {
        if (first.level.compareTo(second.level) > 0) {
            return first;
        }
        if (second.level.compareTo(first.level) > 0) {
            return second;
        }
        if (first.spec == null || first.spec.equals(second.spec)) {
            return first;
        }
        throw new RuntimeException(SqlCompilationError.inline("Incompatible collations: '"
            + first.spec + "' and '" + second.spec + "'"));
    }
}
