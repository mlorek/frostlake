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

package dev.frostlake.values;

import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorElementType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

/**
 * The JSON TEXT of a semi-structured value in the account's own spelling, which differs from the
 * engine's canonical text in exactly one place — the DOUBLE family. The account displays a DOUBLE
 * exactly as it converts one:
 *
 * <pre>
 *   SELECT ARRAY_CONSTRUCT(1.0::FLOAT)             [1.000000000000000e+00]     the display
 *   SELECT TO_JSON(ARRAY_CONSTRUCT(1.0::FLOAT))    [1.000000000000000e+00]     the conversion
 * </pre>
 *
 * <p>★ THE FAMILY DECIDES, NOT THE VALUE. A DECIMAL and an INTEGER convert exactly as they display —
 * {@code [1.5]} stays {@code [1.5]} and {@code [1]} stays {@code [1]} — so this is one family's own
 * rule rather than a number format. The family is read off the parsed node ({@code isFloatingPointNumber}),
 * because the canonical TEXT has already been normalised and no longer says which notation it was
 * written in.
 *
 * <p>★ THE FORM IS {@code %.15e}, measured part by part: one digit, a point, FIFTEEN decimals, a
 * lower-case {@code e}, an explicit sign, and an exponent of at least two digits that widens rather
 * than truncating — {@code 1e100} is {@code 1.000000000000000e+100} and {@code 1e308} keeps all three.
 * A negative zero keeps its sign ({@code -0.000000000000000e+00}).
 *
 * <p>★ THE SURFACES THAT USE IT are the conversions — TO_JSON, {@code ::VARCHAR} and TO_VARCHAR — and
 * the display: the cell a client reads through the driver or the HTTP wire, a whole value or a member
 * alike, and the engine's own display of a container. The one exception is a bare DOUBLE under the
 * STRING conversions, which takes a FLOAT's text instead (see {@link #stringConversionTextOf}). HASH's
 * canonical value keeps the canonical text, and CONCAT and LISTAGG refuse a container outright.
 */
public final class VariantJsonText {

    private VariantJsonText() {
    }

    /**
     * The conversion text of a semi-structured value, or null when the value is not one.
     *
     * @param value any runtime value
     * @return the JSON text live converts it to, or null when this rule does not apply
     */
    public static String convertedTextOf(final Object value) {
        if (!(value instanceof VariantValue)) {
            return null;
        }
        final JsonNode node = ((VariantValue) value).node();
        // Null unless there is actually a DOUBLE in there to rewrite, so every caller keeps its own
        // rendering for everything else. That is not an optimisation: an XML-shaped value stringifies
        // as XML rather than as its JSON model, and the display path honours JSON_INDENT — rebuilding
        // either from the tree here would quietly turn it into something else.
        if (node == null || XmlVariants.isXmlElement(node) || !holdsFiniteDouble(node, true)) {
            return null;
        }
        return render(node);
    }

    /**
     * The JSON text a client reads for a semi-structured value, or null when it holds no DOUBLE and
     * the canonical text already is that text. Every DOUBLE takes the conversion form, a whole value
     * as much as a member: live displays {@code TO_VARIANT(1.5::FLOAT)} as {@code 1.500000000000000e+00}
     * and {@code ARRAY_CONSTRUCT(1.0::FLOAT)} as {@code [1.000000000000000e+00]}. A VECTOR keeps its own
     * text, since no display of one inside a VARIANT has been measured.
     *
     * @param value the value
     * @return its display JSON text, or null when that is the canonical text
     */
    public static String displayTextOf(final VariantValue value) {
        final JsonNode node = value.node();
        if (node == null || XmlVariants.isXmlElement(node) || !holdsFiniteDouble(node, false)) {
            return null;
        }
        final StringBuilder out = new StringBuilder();
        append(node, out, false);
        return out.toString();
    }

    /**
     * The JSON text a client reads for a semi-structured value: {@link #displayTextOf}, or the canonical
     * text when there is no DOUBLE in it.
     *
     * @param value the value
     * @return its client JSON text
     */
    public static String clientTextOf(final VariantValue value) {
        final String displayed = displayTextOf(value);
        return displayed != null ? displayed : value.text();
    }

    /**
     * The client text of a DOUBLE read out of a semi-structured value, or null when {@code value} is not
     * one. An element or a path result arrives unwrapped, a Double under its VARIANT column, and the
     * account still spells it as the VARIANT it is: the fifteen-decimal form, a non-finite value as its
     * bare word.
     *
     * @param value any runtime value
     * @param declared the column's declared type
     * @return its client text, or null when this rule does not apply
     */
    public static String unwrappedDoubleText(final Object value, final DataType declared) {
        if (!(value instanceof Double || value instanceof Float) || !(declared instanceof VariantType
                || declared instanceof ObjectType || declared instanceof ArrayType)) {
            return null;
        }
        final double number = ((Number) value).doubleValue();
        return Double.isNaN(number) || Double.isInfinite(number) ? Double.toString(number) : doubleText(number);
    }

    /** Whether a column's declared type is one whose cells a client reads as JSON text. */
    public static boolean isSemiStructured(final DataType declared) {
        return declared instanceof VariantType || declared instanceof ObjectType || declared instanceof ArrayType;
    }

    /**
     * The client text of a STRING read out of a semi-structured value: its JSON text, quotes included, as
     * the account's driver hands every VARIANT cell back ({@code "v"} for
     * {@code OBJECT_CONSTRUCT('k', 'v'):k}). The engine carries a JSON null unwrapped as the bare text
     * {@code null}, and a string whose content could pass for structure or for that null already in its
     * quotes; both are their own JSON text.
     *
     * @param value an unwrapped string under a semi-structured column
     * @return its JSON text
     */
    public static String unwrappedStringText(final String value) {
        if ("null".equals(value) || isQuotedCarrier(value)) {
            return value;
        }
        return new StringNode(value).toString();
    }

    /** Whether {@code text} is the engine's quoted carrier: a JSON string whose content opens a structure or reads null. */
    private static boolean isQuotedCarrier(final String text) {
        final String trimmed = text.trim();
        return trimmed.length() >= 2 && trimmed.endsWith("\"")
            && (trimmed.startsWith("\"{") || trimmed.startsWith("\"[") || "\"null\"".equals(trimmed));
    }

    /**
     * The text of a semi-structured value under the STRING conversions — {@code ::VARCHAR},
     * TO_VARCHAR, TO_CHAR — which differs from TO_JSON's in one place: a value that IS a double and
     * nothing more converts to a FLOAT's text rather than the fifteen-decimal one
     * (live-verified cell by cell). A double that came from a FLOAT through TO_VARIANT takes the
     * shortest round-trip form held to ten significant digits and one more per decade ({@code 1.5},
     * {@code 3.0}, {@code 1.0E18}, {@code -0.0}, {@code 1.414213562} for SQRT(2)); a double read from
     * JSON text takes the FLOAT text ({@code 100} for {@code 1e2}, {@code 1e+18}, {@code 1e-07},
     * {@code -0}). A double INSIDE a container stays in the conversion form
     * ({@code [1.500000000000000e+00]}), as does TO_JSON of the bare double.
     *
     * @param value any runtime value
     * @return the text, or null when the value is not semi-structured or needs no rewriting
     */
    public static String stringConversionTextOf(final Object value) {
        if (!(value instanceof VariantValue)) {
            return null;
        }
        final JsonNode node = ((VariantValue) value).node();
        if (node != null && (node.isDouble() || node.isFloat())) {
            return node instanceof FloatOriginNode
                ? floatOriginText(node.doubleValue()) : SharedFunctionHelpers.floatText(node.doubleValue());
        }
        return convertedTextOf(value);
    }

    /**
     * A FLOAT's spelling through TO_VARIANT and back to text: the shortest round-trip form, held to ten
     * significant digits plus one per decade — so SQRT(2) is {@code 1.414213562} while a seventeen-digit
     * 1.2345678901234568E16 keeps every digit — with the non-finite values and the signed zero spelled
     * as the round-trip form spells them.
     */
    static String floatOriginText(final double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value == 0.0) {
            return Double.toString(value);
        }
        final BigDecimal exact = new BigDecimal(Math.abs(value));
        final int decade = exact.precision() - exact.scale() - 1;
        final int digits = Math.max(10, 10 + decade);
        return Double.toString(new BigDecimal(value).round(new MathContext(digits, RoundingMode.HALF_UP)).doubleValue());
    }

    /**
     * Whether anywhere in the document there is a value this rule would rewrite; a VECTOR counts only
     * when {@code vectorsToo}, which the conversions pass and the display does not.
     */
    private static boolean holdsFiniteDouble(final JsonNode node, final boolean vectorsToo) {
        // A VECTOR's elements are doubles wearing a fixed-point node, so the plain test cannot see
        // them — but TO_JSON spells them the same fifteen-decimal way it spells any other double.
        if (TypedVectorNode.vectorValueOf(node) != null) {
            return vectorsToo && TypedVectorNode.vectorValueOf(node).getElementType() == VectorElementType.FLOAT;
        }
        if (isRewritableDouble(node)) {
            return true;
        }
        for (int i = 0; i < node.size(); i++) {
            if (node.isArray() && holdsFiniteDouble(node.get(i), vectorsToo)) {
                return true;
            }
        }
        if (node.isObject()) {
            for (final Map.Entry<String, JsonNode> member : node.properties()) {
                if (holdsFiniteDouble(member.getValue(), vectorsToo)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether one node is a DOUBLE this rule rewrites. A BigDecimal node is the DECIMAL family and
     * converts as it displays; a non-finite one is written BARE by the canonical writer and must keep
     * that spelling, so neither is rewritten — the same reading TYPEOF makes.
     *
     * @param node the node
     * @return whether it takes the fifteen-decimal form
     */
    static boolean isRewritableDouble(final JsonNode node) {
        return node.isFloatingPointNumber() && !node.isBigDecimal()
            && !Double.isNaN(node.doubleValue()) && !Double.isInfinite(node.doubleValue());
    }

    /**
     * One node's conversion text.
     *
     * @param node the parsed document
     * @return its JSON text with every DOUBLE rewritten
     */
    public static String render(final JsonNode node) {
        final StringBuilder out = new StringBuilder();
        append(node, out, true);
        return out.toString();
    }

    private static void append(final JsonNode node, final StringBuilder out, final boolean vectorsToo) {
        final VectorValue vector = TypedVectorNode.vectorValueOf(node);
        if (vector != null && !vectorsToo) {
            out.append(VariantText.canonical(node));
            return;
        }
        if (vector != null) {
            // The vector's own elements, each in the fifteen-decimal form — a THIRD spelling of the
            // same number, after the six-decimal display and the sixteen-digit variant text.
            out.append('[');
            for (int i = 0; i < vector.dimension(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                if (vector.getElementType() == VectorElementType.INT) {
                    out.append((long) vector.element(i));
                } else {
                    out.append(doubleText(vector.element(i)));
                }
            }
            out.append(']');
            return;
        }
        if (node.isObject()) {
            out.append('{');
            boolean first = true;
            for (final Map.Entry<String, JsonNode> member : node.properties()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                out.append(StringNode.valueOf(member.getKey()).toString()).append(':');
                append(member.getValue(), out, vectorsToo);
            }
            out.append('}');
            return;
        }
        if (node.isArray()) {
            out.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                append(node.get(i), out, vectorsToo);
            }
            out.append(']');
            return;
        }
        if (isRewritableDouble(node)) {
            out.append(doubleText(node.doubleValue()));
            return;
        }
        // Every other leaf converts as it displays — including the typed scalars, whose text is their
        // own and must stay byte-identical, and the non-finite numbers, which the canonical writer
        // spells bare where a standard one would quote them into strings.
        out.append(VariantText.canonical(node));
    }

    /**
     * One DOUBLE in the form the conversion uses: {@code %.15e} as C prints it, rounded from the double's
     * exact binary value. Java's formatter pads the shortest decimal representation instead, which
     * differs wherever that representation is shorter than the value's sixteen digits: the smallest
     * subnormal, 4.9E-324, is {@code 4.940656458412465e-324} on the account.
     *
     * @param value the double
     * @return its fifteen-decimal scientific text
     */
    public static String doubleText(final double value) {
        if (value == 0.0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return String.format(Locale.ROOT, "%.15e", Double.valueOf(value));
        }
        final BigDecimal rounded = new BigDecimal(value).round(new MathContext(16, RoundingMode.HALF_EVEN));
        final String digits = rounded.unscaledValue().abs().toString();
        final int exponent = rounded.precision() - rounded.scale() - 1;
        final StringBuilder out = new StringBuilder(24);
        if (rounded.signum() < 0) {
            out.append('-');
        }
        out.append(digits.charAt(0)).append('.');
        for (int i = 1; i < 16; i++) {
            out.append(i < digits.length() ? digits.charAt(i) : '0');
        }
        out.append('e').append(exponent < 0 ? '-' : '+');
        final int magnitude = Math.abs(exponent);
        if (magnitude < 10) {
            out.append('0');
        }
        return out.append(magnitude).toString();
    }
}
