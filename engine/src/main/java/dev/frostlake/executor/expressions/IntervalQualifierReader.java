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

import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.IntervalField;

import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigInteger;
import java.util.List;

/**
 * Reads a unit-suffixed interval literal's qualifier off its parse tree into an {@link IntervalLiteralSpec}:
 * the fields by their tokens (a plural field is its singular), the precisions as written. The spec judges
 * whether they name a type.
 */
final class IntervalQualifierReader {

    private IntervalQualifierReader() {
    }

    /**
     * The literal's specification.
     *
     * @param text      the literal's text, unquoted
     * @param qualifier the qualifier as parsed
     * @return the specification, refused when the qualifier names no type
     */
    static IntervalLiteralSpec read(final String text, final FrostlakeParser.IntervalLiteralQualifierContext qualifier) {
        final IntervalField lead;
        IntervalField trail = null;
        if (qualifier.intervalPluralField() != null) {
            lead = fieldOf(qualifier.intervalPluralField().getStart().getType());
        } else {
            lead = fieldOf(qualifier.intervalField(0).getStart().getType());
            if (qualifier.TO() != null) {
                trail = fieldOf(qualifier.intervalField(1).getStart().getType());
            }
        }
        final FrostlakeParser.IntervalLeadingPrecisionContext written = qualifier.intervalLeadingPrecision();
        final List<TerminalNode> digits = written == null ? null : written.INTEGER_LITERAL();
        final int[] leadPrecisions = new int[digits == null ? 0 : digits.size()];
        for (int i = 0; i < leadPrecisions.length; i++) {
            leadPrecisions[i] = precision(digits.get(i));
        }
        final FrostlakeParser.IntervalTrailingPrecisionContext trailing = qualifier.intervalTrailingPrecision();
        final Integer trailPrecision = trailing == null ? null : Integer.valueOf(precision(trailing.INTEGER_LITERAL()));
        return IntervalLiteralSpec.of(text, lead, leadPrecisions, trail, trailPrecision);
    }

    /** A written precision, any value past an int's range read as the largest int, which no field takes. */
    private static int precision(final TerminalNode digits) {
        final BigInteger value = new BigInteger(digits.getText());
        return value.bitLength() < Integer.SIZE ? value.intValue() : Integer.MAX_VALUE;
    }

    /** The field a qualifier token names, its plural spelling included. */
    private static IntervalField fieldOf(final int tokenType) {
        switch (tokenType) {
            case FrostlakeParser.YEAR:
            case FrostlakeParser.YEARS:
                return IntervalField.YEAR;
            case FrostlakeParser.MONTH:
            case FrostlakeParser.MONTHS:
                return IntervalField.MONTH;
            case FrostlakeParser.DAY:
            case FrostlakeParser.DAYS:
                return IntervalField.DAY;
            case FrostlakeParser.HOUR:
            case FrostlakeParser.HOURS:
                return IntervalField.HOUR;
            case FrostlakeParser.MINUTE:
            case FrostlakeParser.MINUTES:
                return IntervalField.MINUTE;
            default:
                return IntervalField.SECOND;
        }
    }
}
