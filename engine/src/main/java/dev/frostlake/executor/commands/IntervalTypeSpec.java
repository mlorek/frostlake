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
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalQualifier;
import dev.frostlake.types.IntervalYearMonthType;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Locale;

/**
 * An INTERVAL type as written in a column definition or a cast target, read off its parse tree. Live takes
 * every pair of fields from a leading one to a later one of the same family — {@code YEAR TO MONTH},
 * {@code DAY TO HOUR} … {@code MINUTE TO SECOND} — and every single field, each leading field with one to
 * nine digits ({@code DAY(3)}), a SECOND with fractional digits too ({@code SECOND(3,3)}, zero to nine) and a
 * trailing SECOND with its fractional digits alone ({@code DAY TO SECOND(3)}); nine and nine by default. Any
 * other pair or digit count is refused in its own sentence, without the compilation prefix and naming the
 * fields as written: "Invalid specification for type INTERVAL: INTERVAL MONTH TO DAY" (live-verified).
 */
public final class IntervalTypeSpec {

    private static final int MAX_PRECISION = 9;

    private IntervalTypeSpec() {
    }

    /**
     * The interval type a written INTERVAL names.
     *
     * @param fields     the fields after INTERVAL
     * @param typeParams any parentheses the grammar read after the fields, which no interval type takes
     * @return the day-time or year-month interval type
     */
    static DataType parse(final FrostlakeParser.IntervalTypeFieldsContext fields,
                          final FrostlakeParser.TypeParametersContext typeParams) {
        refuseTrailingParameters(typeParams);
        final String leading = fields.intervalTypeUnit(0).getText().toUpperCase(Locale.ROOT);
        final String trailing = fields.TO() == null ? null
            : fields.intervalTypeUnit(1).getText().toUpperCase(Locale.ROOT);
        final String written = "INTERVAL " + leading + (trailing == null ? "" : " TO " + trailing);
        final IntervalQualifier qualifier = IntervalQualifier.ofFields(leading, trailing);
        if (qualifier == null) {
            throw invalid(written);
        }
        int leadingDigits = MAX_PRECISION;
        int fractionDigits = qualifier.endsInSecond() ? MAX_PRECISION : 0;
        final FrostlakeParser.IntervalTypeLeadingPrecisionContext lead = fields.intervalTypeLeadingPrecision();
        if (lead != null) {
            leadingDigits = digits(lead.INTEGER_LITERAL(0));
            if (lead.INTEGER_LITERAL().size() > 1) {
                if (qualifier != IntervalQualifier.SECOND) {
                    throw invalid(written);
                }
                fractionDigits = digits(lead.INTEGER_LITERAL(1));
            }
        }
        final FrostlakeParser.IntervalTypeTrailingPrecisionContext trail = fields.intervalTypeTrailingPrecision();
        if (trail != null) {
            if (!qualifier.endsInSecond()) {
                throw invalid(written);
            }
            fractionDigits = digits(trail.INTEGER_LITERAL());
        }
        if (leadingDigits < 1 || leadingDigits > MAX_PRECISION || fractionDigits > MAX_PRECISION) {
            throw invalid(written);
        }
        return qualifier.isDayTime() ? IntervalDayTimeType.of(qualifier, leadingDigits, fractionDigits)
            : IntervalYearMonthType.of(qualifier, leadingDigits);
    }

    /**
     * No container holds an interval: {@code ARRAY(INTERVAL DAY)} is "Unsupported data type 'Array with element
     * type INTERVAL DAY(9)'.", and alike for an OBJECT field and a MAP value (live-verified).
     *
     * @param type      the nested type
     * @param container how live names the position: {@code Array with element type} and the like
     */
    static void refuseNested(final DataType type, final String container) {
        if (type instanceof IntervalDayTimeType || type instanceof IntervalYearMonthType) {
            throw new RuntimeException(SqlCompilationError.of("Unsupported data type '" + container + " "
                + type.getName() + "'."));
        }
    }

    /**
     * Parentheses after the whole type are a syntax error live, at the token its grammar stops on: the comma of
     * {@code INTERVAL DAY TO SECOND(3,3)} and then its closing parenthesis, the closing parenthesis of empty
     * ones, the opening one of a second precision.
     */
    private static void refuseTrailingParameters(final FrostlakeParser.TypeParametersContext typeParams) {
        if (typeParams == null) {
            return;
        }
        final int integers = typeParams.INTEGER_LITERAL() == null ? 0 : typeParams.INTEGER_LITERAL().size();
        if (integers == 0) {
            throw new RuntimeException(SqlCompilationError.of(unexpected(typeParams.RPAREN().getSymbol())));
        }
        if (typeParams.COMMA() == null) {
            throw new RuntimeException(SqlCompilationError.of(unexpected(typeParams.LPAREN().getSymbol())));
        }
        throw new RuntimeException(SqlCompilationError.of(unexpected(typeParams.COMMA().getSymbol()) + "\n"
            + unexpected(typeParams.RPAREN().getSymbol())));
    }

    /** The syntax-error line for a token, placed in the statement when the type sits in an expression fragment. */
    private static String unexpected(final Token token) {
        final SourcePosition within = new SourcePosition(token.getLine(), token.getCharPositionInLine());
        final SourcePosition resolved = ExpressionSource.resolve(within);
        final SourcePosition at = resolved == null ? within : resolved;
        return "syntax error line " + at.getLine() + " at position " + at.getCharPositionInLine()
            + " unexpected '" + token.getText() + "'.";
    }

    private static int digits(final TerminalNode literal) {
        final String text = literal.getText();
        return text.length() > 2 ? Integer.MAX_VALUE : Integer.parseInt(text);
    }

    private static RuntimeException invalid(final String written) {
        return new RuntimeException("Invalid specification for type INTERVAL: " + written);
    }
}
