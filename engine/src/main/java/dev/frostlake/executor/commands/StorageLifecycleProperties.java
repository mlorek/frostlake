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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.model.StorageLifecyclePolicy;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A storage lifecycle policy's properties: its COMMENT, ARCHIVE_TIER, COOL or COLD, and ARCHIVE_FOR_DAYS, the days
 * an archived row is kept, judged as the account judges them (live-verified):
 *
 * <pre>
 *   ARCHIVE_TIER = HOT                         invalid value 'HOT' for property 'ARCHIVE_TIER'
 *   ARCHIVE_FOR_DAYS = 'x'                     invalid value ['x'] for parameter 'ARCHIVE_FOR_DAYS'
 *   ARCHIVE_FOR_DAYS = 90 alone                invalid property combination 'ARCHIVE_FOR_DAYS'='90' and
 *                                              'ARCHIVE_TIER'='NULL'
 *   ARCHIVE_TIER = COOL alone                  invalid property combination 'ARCHIVE_FOR_DAYS'='NULL' and
 *                                              'ARCHIVE_TIER'='COOL'
 *   ARCHIVE_TIER = COOL ARCHIVE_FOR_DAYS = 89  Invalid value '89' for property 'ARCHIVE_FOR_DAYS' when using 'COOL'
 *                                              archive tier . It must be at least '90' days.
 * </pre>
 *
 * <p>A tier is a name or a text in any case. The days are a whole number written as one: a text, a fraction and a
 * number past 2147483647 are each an invalid value, and a COOL tier keeps a row 90 days at least, a COLD one 180. A
 * name the kind does not know is refused as {@code invalid property 'x' for 'STORAGE_LIFECYCLE_POLICY'} and a name
 * written twice as {@code duplicate property 'COMMENT';}. A CREATE judges its properties once its signature, its
 * body and its return type have been judged.
 *
 * <p>An ALTER that sets a tier sets the days too, or it is refused as the tier alone; one that sets the days alone
 * judges them against the policy's tier. UNSET ARCHIVE_FOR_DAYS leaves the tier as it is, and the tier is never
 * unset: {@code invalid value 'NULL' for property 'ARCHIVE_TIER'}.
 */
final class StorageLifecycleProperties {

    /** The kind as a refusal names it. */
    static final String KIND = "STORAGE_LIFECYCLE_POLICY";

    /** The properties beside COMMENT, as an ALTER's property list names them. */
    static final List<String> ARCHIVE_PROPERTIES = Arrays.asList("ARCHIVE_TIER", "ARCHIVE_FOR_DAYS");

    private static final String TIER = "ARCHIVE_TIER";
    private static final String DAYS = "ARCHIVE_FOR_DAYS";
    private static final String COMMENT = "COMMENT";
    private static final String NONE = "NULL";
    private static final String COOL = "COOL";
    private static final String COLD = "COLD";
    private static final int COOL_DAYS = 90;
    private static final int COLD_DAYS = 180;

    private final String comment;
    private final String tier;
    private final Integer days;
    private final boolean tierWritten;
    private final boolean daysWritten;

    private StorageLifecycleProperties(final String comment, final String tier, final Integer days,
                                       final boolean tierWritten, final boolean daysWritten) {
        this.comment = comment;
        this.tier = tier;
        this.days = days;
        this.tierWritten = tierWritten;
        this.daysWritten = daysWritten;
    }

    /**
     * Reads the properties a CREATE writes after the policy's body, refusing what the account refuses.
     *
     * @param ddl        the handler that reads a COMMENT's text
     * @param properties the properties as written
     * @return the properties
     */
    static StorageLifecycleProperties read(final DDLCommandHandler ddl,
                                           final List<FrostlakeParser.StorageLifecyclePropertyContext> properties) {
        final List<String> seen = new ArrayList<>();
        String comment = null;
        String tier = null;
        Integer days = null;
        for (final FrostlakeParser.StorageLifecyclePropertyContext property : properties) {
            final String name = property.commentClause() != null ? COMMENT : property.propertyWord.getText();
            final String upper = name.toUpperCase(Locale.ROOT);
            if (seen.contains(upper)) {
                throw new RuntimeException(SqlCompilationError.duplicateProperty(upper));
            }
            seen.add(upper);
            if (property.commentClause() != null) {
                comment = ddl.extractComment(property.commentClause());
            } else if (TIER.equals(upper)) {
                tier = tierOf(property.policyScalarValue());
            } else if (DAYS.equals(upper)) {
                days = daysOf(property.policyScalarValue());
            } else {
                throw new RuntimeException(SqlCompilationError.invalidPropertyFor(name, KIND));
            }
        }
        requireCombination(tier, days);
        return new StorageLifecycleProperties(comment, tier, days, tier != null, days != null);
    }

    /**
     * Judges the archive properties an ALTER's SET list writes against the policy it alters; the list's names have
     * been judged (see PolicyPropertyList).
     *
     * @param action the ALTER's policy action
     * @param policy the policy altered
     * @return the properties the list sets
     */
    static StorageLifecycleProperties readSet(final FrostlakeParser.PolicyActionContext action,
                                              final StorageLifecyclePolicy policy) {
        String tier = null;
        Integer days = null;
        boolean tierWritten = false;
        boolean daysWritten = false;
        for (final FrostlakeParser.PolicyPropertyContext property : action.policyProperty()) {
            final String upper = property.identifier().getText().toUpperCase(Locale.ROOT);
            if (TIER.equals(upper)) {
                tier = tierOf(scalarOf(property.policyPropertyValue(), TIER));
                tierWritten = true;
            } else if (DAYS.equals(upper)) {
                days = daysOf(scalarOf(property.policyPropertyValue(), DAYS));
                daysWritten = true;
            }
        }
        if (tierWritten) {
            requireCombination(tier, days);
        } else if (daysWritten) {
            requireCombination(policy.getArchiveTier(), days);
        }
        return new StorageLifecycleProperties(null, tier, days, tierWritten, daysWritten);
    }

    /**
     * Judges the properties an ALTER's UNSET list names; the names have been judged (see PolicyPropertyList).
     *
     * @param action the ALTER's policy action
     */
    static void requireUnsettable(final FrostlakeParser.PolicyActionContext action) {
        for (final FrostlakeParser.IdentifierContext name : action.identifier()) {
            if (TIER.equals(name.getText().toUpperCase(Locale.ROOT))) {
                throw new RuntimeException(invalidTier(NONE));
            }
        }
    }

    /**
     * Whether an ALTER's UNSET list names ARCHIVE_FOR_DAYS.
     *
     * @param action the ALTER's policy action
     * @return true when it unsets the days
     */
    static boolean unsetsDays(final FrostlakeParser.PolicyActionContext action) {
        if (action.UNSET() == null) {
            return false;
        }
        for (final FrostlakeParser.IdentifierContext name : action.identifier()) {
            if (DAYS.equals(name.getText().toUpperCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** A tier and the days written beside it, or the lack of either, as the account takes them together. */
    private static void requireCombination(final String tier, final Integer days) {
        if (tier == null && days == null) {
            return;
        }
        if (tier == null || days == null) {
            throw new RuntimeException(SqlCompilationError.of("invalid property combination '" + DAYS + "'='"
                + (days == null ? NONE : days.toString()) + "' and '" + TIER + "'='" + (tier == null ? NONE : tier)
                + "'"));
        }
        final int least = COLD.equals(tier) ? COLD_DAYS : COOL_DAYS;
        if (days.intValue() < least) {
            throw new RuntimeException("Invalid value '" + days + "' for property '" + DAYS + "' when using '" + tier
                + "' archive tier . It must be at least '" + least + "' days.");
        }
    }

    /** The one value an ALTER's property takes; a parenthesised list is an invalid value. */
    private static FrostlakeParser.PolicyScalarValueContext scalarOf(
            final FrostlakeParser.PolicyPropertyValueContext value, final String property) {
        if (value.LPAREN() == null) {
            return value.policyScalarValue(0);
        }
        throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.getText(), property));
    }

    /** The tier a value names, upper-cased: COOL or COLD, written as a name or a text. */
    private static String tierOf(final FrostlakeParser.PolicyScalarValueContext value) {
        final String named;
        if (value.literal() != null && value.literal().STRING_LITERAL() != null) {
            named = SqlStringLiterals.decode(value.literal().STRING_LITERAL().getText());
        } else if (value.literal() == null && value.MINUS() == null && value.SESSION_VAR_REF() == null
                && value.identifier().size() == 1) {
            named = SqlIdentifiers.canonical(value.identifier(0));
        } else {
            named = value.getText();
        }
        final String upper = named.toUpperCase(Locale.ROOT);
        if (!COOL.equals(upper) && !COLD.equals(upper)) {
            throw new RuntimeException(invalidTier(upper));
        }
        return upper;
    }

    private static String invalidTier(final String value) {
        return SqlCompilationError.of("invalid value '" + value + "' for property '" + TIER + "'");
    }

    /** The days a value writes: a whole number, signed or not, that fits an INT. */
    private static Integer daysOf(final FrostlakeParser.PolicyScalarValueContext value) {
        final boolean whole = value.MINUS() != null ? value.INTEGER_LITERAL() != null
            : value.literal() != null && value.literal().INTEGER_LITERAL() != null;
        if (whole) {
            try {
                return Integer.valueOf(Integer.parseInt(value.getText()));
            } catch (final NumberFormatException tooLarge) {
                // refused below, as written
            }
        }
        throw new RuntimeException(SqlCompilationError.invalidValueForParameter(value.getText(), DAYS));
    }

    /** The COMMENT a CREATE writes, or null. */
    String comment() {
        return comment;
    }

    /** The tier, or null when none is written. */
    String tier() {
        return tier;
    }

    /** The days, or null when none are written. */
    Integer days() {
        return days;
    }

    /** Whether an ALTER's SET list writes the tier. */
    boolean tierWritten() {
        return tierWritten;
    }

    /** Whether an ALTER's SET list writes the days. */
    boolean daysWritten() {
        return daysWritten;
    }
}
