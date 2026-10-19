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
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;

import java.util.Locale;

/**
 * The call a PIVOT is rewritten into, as the account prints it when it refuses the aggregate there
 * (live-verified): the aggregate over the column the first pivot value selects,
 *
 * <pre>
 *   CORR(IFF('x' = FL2.S, FL2.N, SYSTEM$NULL_TO_FIXED(null)))
 * </pre>
 *
 * The relation is named as the FROM item names it — its alias, a table's or a CTE's own name, {@code "values"}
 * for a subquery — and the null is typed as the aggregated column's family. The first value meets the FOR
 * column in the column's type: a number against a text is {@code (CAST(1 AS NUMBER(18,5))) = (TO_NUMBER(FL2.S,
 * 18, 5))}, a text against a number {@code (TO_NUMBER('1')) = FL2.N}, a number of another scale casts the side
 * of the smaller one, a text against a DATE {@code (CAST('2024-01-01' AS DATE)) = FL2.D}, a number against a
 * FLOAT {@code (CAST(1 AS FLOAT)) = FL2.F}, and NULL is {@code EQUAL_NULL(SYSTEM$NULL_TO_TEXT(null), FL2.S)}. A
 * list of ANY or a subquery leaves the aggregate over {@code null}.
 */
final class PivotRewrite {

    private PivotRewrite() {
    }

    /**
     * The aggregate's argument as the rewrite prints it.
     *
     * @param pivot     the clause
     * @param relation  the relation's name as the rewrite spells it
     * @param forColumn the FOR column's canonical name
     * @param forType   the FOR column's type
     * @param column    the aggregated column's canonical name
     * @param type      the aggregated column's type
     * @return the argument
     */
    static String argument(final FrostlakeParser.PivotClauseContext pivot, final String relation,
                           final String forColumn, final DataType forType, final String column,
                           final DataType type) {
        final FrostlakeParser.PivotValueListContext values = pivot.pivotInList().pivotValueList();
        if (values == null) {
            return "null";
        }
        final String read = relation + "." + SqlIdentifiers.spellCanonical(forColumn);
        return "IFF(" + comparison(values.pivotValue(0), read, forType) + ", " + relation + "."
            + SqlIdentifiers.spellCanonical(column) + ", " + typedNull(type) + ")";
    }

    /**
     * The name a FROM item gives the relation in the rewrite.
     *
     * @param reference the FROM item holding the pivot
     * @return its alias, its table's last name, or {@code "values"} for any other source
     */
    static String relation(final FrostlakeParser.TableReferenceContext reference) {
        if (reference.aliasName() != null) {
            return SqlIdentifiers.spellCanonical(ParseTreeText.getIdentifier(reference.aliasName()));
        }
        if (reference.nonJoinKeywordIdentifier() != null) {
            final FrostlakeParser.NonJoinKeywordIdentifierContext alias = reference.nonJoinKeywordIdentifier();
            return SqlIdentifiers.spellCanonical(alias.identifier() != null
                ? ParseTreeText.getIdentifier(alias.identifier()) : alias.getText().toUpperCase(Locale.ROOT));
        }
        final FrostlakeParser.TableQualifiedNameContext name = reference.tableSource().tableQualifiedName();
        if (name != null) {
            final String[] parts = ParseTreeText.qualifiedNameParts(name);
            return SqlIdentifiers.spellCanonical(parts[parts.length - 1]);
        }
        return "\"values\"";
    }

    /** The first value against the FOR column, in the column's type. */
    private static String comparison(final FrostlakeParser.PivotValueContext value, final String read,
                                     final DataType forType) {
        final FrostlakeParser.LiteralContext literal = value.literal();
        if (literal != null && literal.NULL() != null) {
            return "EQUAL_NULL(" + typedNull(forType) + ", " + read + ")";
        }
        final String written = literal == null ? ParseTreeText.getOriginalText(value.booleanExpr())
            : literal.TRUE() != null || literal.FALSE() != null ? literal.getText().toUpperCase(Locale.ROOT)
            : literal.getText();
        final boolean number = literal != null && (literal.INTEGER_LITERAL() != null || isDecimal(literal));
        final boolean text = literal != null && literal.STRING_LITERAL() != null;
        if (forType instanceof StringType && number) {
            return "(CAST(" + written + " AS NUMBER(18,5))) = (TO_NUMBER(" + read + ", 18, 5))";
        }
        if (forType instanceof NumericType && NumericType.isApproximate(forType) && number) {
            return "(CAST(" + written + " AS FLOAT)) = " + read;
        }
        if (forType instanceof NumericType && !NumericType.isApproximate(forType)) {
            final NumericType fixed = (NumericType) forType;
            if (text) {
                return "(TO_NUMBER(" + written + ")) = " + read;
            }
            if (number) {
                final int scale = scaleOf(literal);
                if (scale > fixed.getScale()) {
                    return written + " = (CAST(" + read + " AS NUMBER(38," + scale + ")))";
                }
                if (scale < fixed.getScale()) {
                    return "(CAST(" + written + " AS NUMBER(" + fixed.getPrecision() + "," + fixed.getScale()
                        + "))) = " + read;
                }
            }
        }
        if (forType instanceof DateTimeType && text) {
            return "(CAST(" + written + " AS " + SqlTypeNames.internalName(forType) + ")) = " + read;
        }
        return written + " = " + read;
    }

    /** Whether a literal is a number with a fraction and no exponent. */
    private static boolean isDecimal(final FrostlakeParser.LiteralContext literal) {
        final String text = literal.FLOAT_LITERAL() == null ? null : literal.FLOAT_LITERAL().getText();
        return text != null && text.indexOf('e') < 0 && text.indexOf('E') < 0;
    }

    /** The digits a number literal writes after its point. */
    private static int scaleOf(final FrostlakeParser.LiteralContext literal) {
        if (literal.INTEGER_LITERAL() != null) {
            return 0;
        }
        final String text = literal.FLOAT_LITERAL().getText();
        final int point = text.indexOf('.');
        return point < 0 ? 0 : text.length() - point - 1;
    }

    /** The null typed as a column's family: {@code SYSTEM$NULL_TO_TEXT(null)}, FIXED, REAL, DATE, … */
    static String typedNull(final DataType type) {
        final String family = type == null ? null : SqlTypeNames.internalName(type);
        return "SYSTEM$NULL_TO_" + (family == null ? "VARIANT" : family) + "(null)";
    }
}
