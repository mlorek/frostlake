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

import dev.frostlake.types.DataType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalField;
import dev.frostlake.types.IntervalQualifier;
import dev.frostlake.types.IntervalYearMonthType;

/**
 * A unit-suffixed interval literal as written — {@code INTERVAL '1 02' DAY TO HOUR}, {@code INTERVAL '1.5'
 * SECOND}, {@code INTERVAL '1' DAY(2)} — its text and the type its qualifier names. The qualifier is judged
 * when the literal is read, while the statement compiles: a leading precision outside 1 to 9, a fractional one
 * outside 0 to 9, two precisions on a field other than SECOND, a precision on a trailing field other than
 * SECOND and a pair of fields that spans no type are each "Invalid specification for type INTERVAL: …",
 * naming the leading field alone when its own precisions are at fault and both fields otherwise
 * (live-verified). The TEXT is read only when a row reaches the literal, as it is live — see
 * {@link IntervalLiteralText}.
 */
public final class IntervalLiteralSpec {

    private static final int MAX_PRECISION = 9;

    private final String text;
    private final IntervalQualifier qualifier;
    private final int leadingPrecision;
    private final int fractionalPrecision;
    private final boolean standardPrecision;
    /** The value the text reads as, once a row has read it; an immutable value any evaluation may share. */
    private volatile Object value;

    private IntervalLiteralSpec(final String text, final IntervalQualifier qualifier, final int leadingPrecision,
                                final int fractionalPrecision, final boolean standardPrecision) {
        this.text = text;
        this.qualifier = qualifier;
        this.leadingPrecision = leadingPrecision;
        this.fractionalPrecision = fractionalPrecision;
        this.standardPrecision = standardPrecision;
    }

    /**
     * A literal's specification from its written qualifier, refused when the qualifier names no type.
     *
     * @param text            the literal's text, unquoted
     * @param lead            the leading field
     * @param leadPrecisions  the precisions written after the leading field: none, one or two
     * @param trail           the trailing field after TO, or null for a one-field qualifier
     * @param trailPrecision  the precision written after the trailing field, or null
     * @return the specification
     */
    public static IntervalLiteralSpec of(final String text, final IntervalField lead, final int[] leadPrecisions,
                                         final IntervalField trail, final Integer trailPrecision) {
        final boolean leadSecond = lead == IntervalField.SECOND;
        if (leadPrecisions.length > 0 && !withinPrecision(leadPrecisions[0], 1)
                || leadPrecisions.length > 1 && (!leadSecond || !withinPrecision(leadPrecisions[1], 0))) {
            throw invalid(lead.name());
        }
        final IntervalField end = trail == null ? lead : trail;
        final IntervalQualifier qualifier = IntervalField.qualifier(lead, end);
        final String spelled = trail == null ? lead.name() : lead.name() + " TO " + trail.name();
        if (qualifier == null || trail == lead
                || trailPrecision != null && (trail != IntervalField.SECOND || !withinPrecision(trailPrecision, 0))) {
            throw invalid(spelled);
        }
        final int leading = leadPrecisions.length > 0 ? leadPrecisions[0] : MAX_PRECISION;
        final int fraction;
        if (end != IntervalField.SECOND) {
            fraction = 0;
        } else if (trail == null) {
            fraction = leadPrecisions.length > 1 ? leadPrecisions[1] : MAX_PRECISION;
        } else {
            fraction = trailPrecision != null ? trailPrecision.intValue() : MAX_PRECISION;
        }
        final boolean standard = leading == MAX_PRECISION && (end != IntervalField.SECOND || fraction == MAX_PRECISION);
        return new IntervalLiteralSpec(text, qualifier, leading, fraction, standard);
    }

    private static boolean withinPrecision(final int precision, final int lowest) {
        return precision >= lowest && precision <= MAX_PRECISION;
    }

    private static RuntimeException invalid(final String spelled) {
        return new RuntimeException("Invalid specification for type INTERVAL: INTERVAL " + spelled);
    }

    /** @return the value a row read the text as, or null before one has */
    Object readValue() {
        return value;
    }

    /** @param read the value the text reads as, kept for the rows after */
    void keepValue(final Object read) {
        value = read;
    }

    /** @return the literal's text, unquoted */
    public String getText() {
        return text;
    }

    /** @return the fields the literal spans */
    public IntervalQualifier getQualifier() {
        return qualifier;
    }

    /** @return the digits its leading field holds */
    public int getLeadingPrecision() {
        return leadingPrecision;
    }

    /** @return the fractional digits a trailing SECOND keeps; 0 when the literal does not end in SECOND */
    public int getFractionalPrecision() {
        return fractionalPrecision;
    }

    /** @return whether this is a day-time literal rather than a year-month one */
    public boolean isDayTime() {
        return qualifier.isDayTime();
    }

    /** @return the literal's type: {@code INTERVAL DAY(9) TO HOUR}, {@code INTERVAL SECOND(2,3)} and the like */
    public DataType getType() {
        return qualifier.isDayTime() ? IntervalDayTimeType.of(qualifier, leadingPrecision, fractionalPrecision)
            : IntervalYearMonthType.of(qualifier, leadingPrecision);
    }

    /**
     * The qualifier as an expression print spells it: the fields alone at the standard precisions
     * ({@code DAY}, {@code DAY TO HOUR}), and the type's own spelling otherwise ({@code DAY(2)},
     * {@code DAY(9) TO SECOND(3)}), so two literals of one type print alike and two types never do.
     *
     * @return the spelling
     */
    public String qualifierText() {
        if (standardPrecision) {
            final IntervalField lead = IntervalField.leadingOf(qualifier);
            final IntervalField trail = IntervalField.trailingOf(qualifier);
            return lead == trail ? lead.name() : lead.name() + " TO " + trail.name();
        }
        return getType().getName().substring("INTERVAL ".length());
    }
}
