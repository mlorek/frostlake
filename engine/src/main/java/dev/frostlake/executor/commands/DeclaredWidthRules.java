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

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigInteger;
import java.util.Locale;

/**
 * The RANGE a declared type parameter has to fall in, and the sentence live gives when it does not.
 *
 * <p>Every family states its own bounds in its own words, and the ranges do not agree with one
 * another — a zero is refused for a string or a binary and accepted for a NUMBER, which is why this
 * is a check per family rather than one shared "widths must be positive" rule:
 *
 * <pre>
 * VARCHAR / CHAR / STRING / TEXT   Invalid character length: N. Must be between 1 and 134,217,728.
 * BINARY / VARBINARY               Invalid binary length: N. Must be between 1 and 67,108,864.
 * NUMBER precision                 Invalid number precision: N. Must be between 0 and 38.
 * NUMBER scale                     Invalid number scale: N. Must be between 0 and 37.
 * TIME                             Invalid time scale: N. Must be between 0 and 9.
 * the TIMESTAMP family             Invalid timestamp scale: N. Must be between 0 and 9.
 * VECTOR                           Invalid vector dimension 'N'.
 * </pre>
 *
 * <p>The value is spelled with THOUSANDS SEPARATORS on both sides of the sentence, so a width of
 * 134217729 reads back as {@code 134,217,729} — and the vector sentence, alone in the family, quotes
 * it and carries no source position.
 *
 * <p>The POSITION is the offending literal's own: line 1-based, character offset 0-based, pointing at
 * the number itself rather than at the parenthesis before it (live puts it on the digit even when
 * whitespace separates the two). When more than one parameter is wrong the checks run precision,
 * then scale, then the scale-exceeds-precision rule, and the FIRST offending column of a column list
 * wins — including over a duplicate column name, which live reports only once the widths are legal.
 */
final class DeclaredWidthRules {

    /** Characters, 128MB — the same constant a bare cast target declares. */
    private static final BigInteger CHARACTER_MIN = BigInteger.ONE;
    private static final BigInteger CHARACTER_MAX = BigInteger.valueOf(134217728L);
    /** Bytes, 64MB — half the character ceiling, and its own sentence. */
    private static final BigInteger BINARY_MIN = BigInteger.ONE;
    private static final BigInteger BINARY_MAX = BigInteger.valueOf(67108864L);
    /** A NUMBER's precision starts at ZERO, which is what makes {@code NUMBER(0)} legal. */
    private static final BigInteger NUMBER_PRECISION_MIN = BigInteger.ZERO;
    private static final BigInteger NUMBER_PRECISION_MAX = BigInteger.valueOf(38L);
    /** One less than the precision ceiling: {@code NUMBER(38,38)} is refused, {@code (38,37)} is not. */
    private static final BigInteger NUMBER_SCALE_MIN = BigInteger.ZERO;
    private static final BigInteger NUMBER_SCALE_MAX = BigInteger.valueOf(37L);
    private static final BigInteger TEMPORAL_SCALE_MIN = BigInteger.ZERO;
    private static final BigInteger TEMPORAL_SCALE_MAX = BigInteger.valueOf(9L);
    private static final BigInteger VECTOR_DIMENSION_MIN = BigInteger.ONE;
    private static final BigInteger VECTOR_DIMENSION_MAX = BigInteger.valueOf(4096L);

    private DeclaredWidthRules() {
    }

    /**
     * The declared length of a VARCHAR / CHAR / STRING / TEXT and every alias that collapses onto them.
     *
     * @param typeParams the type's parameter list, null when the type was written bare
     */
    static void checkCharacterLength(final FrostlakeParser.TypeParametersContext typeParams) {
        checkParameter(typeParams, 0, "character length", CHARACTER_MIN, CHARACTER_MAX);
    }

    /**
     * The declared length of a BINARY or VARBINARY.
     *
     * @param typeParams the type's parameter list, null when the type was written bare
     */
    static void checkBinaryLength(final FrostlakeParser.TypeParametersContext typeParams) {
        checkParameter(typeParams, 0, "binary length", BINARY_MIN, BINARY_MAX);
    }

    /**
     * A NUMBER's precision and scale, in the order live checks them: each against its own range
     * first, and only then against each other. A scale larger than the precision is a THIRD sentence,
     * lower-cased and carrying no position, which reads the two back in the order it compares them —
     * {@code NUMBER(2,5)} is {@code invalid data type specification (5>2)}.
     *
     * @param typeParams the type's parameter list, null when the type was written bare
     */
    static void checkNumber(final FrostlakeParser.TypeParametersContext typeParams) {
        checkParameter(typeParams, 0, "number precision", NUMBER_PRECISION_MIN, NUMBER_PRECISION_MAX);
        checkParameter(typeParams, 1, "number scale", NUMBER_SCALE_MIN, NUMBER_SCALE_MAX);
        final TerminalNode precision = parameter(typeParams, 0);
        final TerminalNode scale = parameter(typeParams, 1);
        if (precision == null || scale == null) {
            return;
        }
        final BigInteger declaredPrecision = new BigInteger(precision.getText());
        final BigInteger declaredScale = new BigInteger(scale.getText());
        if (declaredScale.compareTo(declaredPrecision) > 0) {
            throw new RuntimeException(SqlCompilationError.of("invalid data type specification ("
                + declaredScale + ">" + declaredPrecision + ")"));
        }
    }

    /**
     * The fractional-seconds scale of a TIME.
     *
     * @param typeParams the type's parameter list, null when the type was written bare
     */
    static void checkTimeScale(final FrostlakeParser.TypeParametersContext typeParams) {
        checkParameter(typeParams, 0, "time scale", TEMPORAL_SCALE_MIN, TEMPORAL_SCALE_MAX);
    }

    /**
     * The fractional-seconds scale of any TIMESTAMP spelling, DATETIME included — one range and one
     * sentence for the whole family, which is why the flavour it resolves to does not enter into it.
     *
     * @param typeParams the type's parameter list, null when the type was written bare
     */
    static void checkTimestampScale(final FrostlakeParser.TypeParametersContext typeParams) {
        checkParameter(typeParams, 0, "timestamp scale", TEMPORAL_SCALE_MIN, TEMPORAL_SCALE_MAX);
    }

    /**
     * A VECTOR's dimension, which lives in the type name's own context rather than in a parameter
     * list. Its refusal is shaped unlike the rest: the value is quoted, and there is no position.
     *
     * @param ctx the type name's parse tree, carrying the dimension when one was written
     */
    static void checkVectorDimension(final FrostlakeParser.DataTypeNameContext ctx) {
        if (ctx.INTEGER_LITERAL() == null) {
            return;
        }
        final BigInteger declared = new BigInteger(ctx.INTEGER_LITERAL().getText());
        if (declared.compareTo(VECTOR_DIMENSION_MIN) >= 0
                && declared.compareTo(VECTOR_DIMENSION_MAX) <= 0) {
            return;
        }
        throw new RuntimeException(SqlCompilationError.of("Invalid vector dimension '"
            + grouped(declared) + "'."));
    }

    /**
     * A declared parameter narrowed to an int, saturating rather than failing. A width past an int's
     * range is a perfectly legal TOKEN — live reads 1,000,000,000,000 and refuses it by value, naming
     * the number it read — so narrowing may not throw here. Every family's own range check runs before
     * the narrowed value is used and every ceiling is far below this one, so a saturated value can
     * never reach a type that was accepted.
     *
     * @param literal the literal's text, which the lexer guarantees is unsigned digits
     * @return the value, or {@link Integer#MAX_VALUE} when it does not fit
     */
    static int saturatingInt(final String literal) {
        final BigInteger declared = new BigInteger(literal);
        return declared.bitLength() < Integer.SIZE ? declared.intValue() : Integer.MAX_VALUE;
    }

    /** One parameter against its family's range, anchored on the literal's own line and offset. */
    private static void checkParameter(final FrostlakeParser.TypeParametersContext typeParams,
                                       final int index, final String what,
                                       final BigInteger min, final BigInteger max) {
        final TerminalNode literal = parameter(typeParams, index);
        if (literal == null) {
            return;
        }
        final boolean negative = isNegated(typeParams, index);
        final BigInteger declared = negative
            ? new BigInteger(literal.getText()).negate()
            : new BigInteger(literal.getText());
        if (declared.compareTo(min) >= 0 && declared.compareTo(max) <= 0) {
            return;
        }
        if (negative) {
            // ★ A NEGATIVE PARAMETER IS REPORTED AT AN IMPOSSIBLE PLACE: live answers line 0 position 0,
            // where every other width in this family points at the literal's own offset. The zero
            // survives a leading comment and a second line, so it is a constant rather than a shifted
            // position — live reads the minus in a phase that has no token left to point at. A negative
            // in another slot still carries a real position (LIMIT -1 reports one), so this belongs to
            // the type-parameter reader alone.
            throw new RuntimeException(SqlCompilationError.at(0, 0,
                "Invalid " + what + ": " + grouped(declared) + ". Must be between "
                    + grouped(min) + " and " + grouped(max) + "."));
        }
        // A CAST target is parsed from the EXPRESSION's own text, so the literal's offset is an offset
        // into that fragment; a column definition is parsed with the statement, so it already carries
        // the statement's. Resolving covers both — there is no origin in force on the DDL path.
        final SourcePosition within = new SourcePosition(literal.getSymbol().getLine(),
            literal.getSymbol().getCharPositionInLine());
        final SourcePosition resolved = ExpressionSource.resolve(within);
        final SourcePosition where = resolved == null ? within : resolved;
        throw new RuntimeException(SqlCompilationError.at(where.getLine(),
            where.getCharPositionInLine(),
            "Invalid " + what + ": " + grouped(declared) + ". Must be between "
                + grouped(min) + " and " + grouped(max) + "."));
    }

    /**
     * Whether the index-th parameter was written with a leading minus, read off the parse tree in
     * order — the tokens are siblings, so the sign is found by walking the children rather than by
     * re-reading the text. A comma opens the next parameter, which is what keeps NUMBER(5,-1)'s sign
     * attached to the scale and not to the precision.
     */
    private static boolean isNegated(final FrostlakeParser.TypeParametersContext typeParams,
                                     final int index) {
        if (typeParams == null) {
            return false;
        }
        int seen = 0;
        boolean minusPending = false;
        for (int i = 0; i < typeParams.getChildCount(); i++) {
            final ParseTree child = typeParams.getChild(i);
            if (!(child instanceof TerminalNode)) {
                continue;
            }
            final int token = ((TerminalNode) child).getSymbol().getType();
            if (token == FrostlakeParser.MINUS) {
                minusPending = true;
            } else if (token == FrostlakeParser.INTEGER_LITERAL) {
                if (seen == index) {
                    return minusPending;
                }
                seen++;
                minusPending = false;
            } else if (token == FrostlakeParser.COMMA) {
                minusPending = false;
            }
        }
        return false;
    }

    /** The index-th declared parameter, or null when the type carries fewer than that many. */
    private static TerminalNode parameter(final FrostlakeParser.TypeParametersContext typeParams,
                                          final int index) {
        if (typeParams == null || typeParams.INTEGER_LITERAL() == null
                || typeParams.INTEGER_LITERAL().size() <= index) {
            return null;
        }
        return typeParams.INTEGER_LITERAL(index);
    }

    /** A number as the sentence spells it — thousands-separated, so 134217729 reads 134,217,729. */
    private static String grouped(final BigInteger value) {
        return String.format(Locale.ROOT, "%,d", value);
    }
}
