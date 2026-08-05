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

package dev.frostlake.functions.scalar.conversion;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.functions.scalar.SnowflakeDateFormat;
import dev.frostlake.functions.scalar.SnowflakeNumberFormat;
import dev.frostlake.types.StringType;
import dev.frostlake.values.BinaryValue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Locale;

public class ToChar extends BuiltInFunction {
    public ToChar() { super("TO_CHAR", StringType.VARCHAR); }

    /**
     * A plain OBJECT or ARRAY renders to its JSON text; a STRUCTURED value has no text conversion at
     * all. Live, {@code TO_VARCHAR(o)} is {@code {"k":"v1"}} and {@code TO_CHAR(a)} is
     * {@code [1,2]}, while {@code TO_VARCHAR(so)} is "invalid type [TO_VARCHAR(ST.SO)] for parameter
     * 'TO_VARCHAR'" and {@code TO_CHAR(so)} the same sentence for 'TO_CHAR' — the parameter named is
     * the name as WRITTEN, which is why one registered object serves both spellings. All three
     * structured kinds refuse alike, and the {@code CAST} spellings of the same conversion are
     * rejected by their own rule since they are not function calls.
     *
     * <p>Position 0 only: {@code TO_VARCHAR(so, 'x')} does fail live, but it fails for the ARGUMENT,
     * naming the whole call — nothing has been measured about a structured value in the FORMAT
     * position.
     */
    @Override
    public SemiStructuredRejection structuredRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.INVALID_TYPE_PARAMETER
            : SemiStructuredRejection.NONE;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final Object value = args.get(0);
        if (value instanceof BinaryValue && args.size() >= 2 && args.get(1) != null) {
            // BINARY has its own format model — the target ENCODING, not a picture string.
            return renderBinary((BinaryValue) value, args.get(1).toString());
        }
        if (args.size() >= 2 && args.get(1) != null) {
            final String format = args.get(1).toString();
            if (!format.isEmpty()) {
                // The format model is chosen by the input type: numeric vs date/time.
                if (value instanceof Number) {
                    return SnowflakeNumberFormat.format(new BigDecimal(value.toString()), format);
                }
                if (value instanceof Temporal) {
                    return SnowflakeDateFormat.format(value, format);
                }
                // A DATE/TIMESTAMP value can reach a function as its ISO string; apply the date model
                // when the format uses date/time elements and the string parses as a temporal. A plain
                // (non-temporal) string falls through to the verbatim value below.
                if (value instanceof String && SnowflakeDateFormat.isDateFormat(format)) {
                    try {
                        return SnowflakeDateFormat.format(value, format);
                    } catch (final RuntimeException ignored) {
                        // not a temporal value
                    }
                }
            }
        }
        // No format: temporals render in Snowflake's default output forms (space + FF3), not java.time's.
        return SharedFunctionHelpers.textOf(value);
    }

    /**
     * TO_VARCHAR(&lt;binary&gt;, &lt;format&gt;) — the format names the ENCODING to render the bytes in
     * (live: {@code HEX} → {@code 48454C4C4F}, {@code BASE64} → {@code SEVMTE8=},
     * {@code UTF-8} → {@code HELLO}; case-insensitive, and any other value — including the empty
     * string — is rejected). Without a format the value renders as hex, handled by the caller.
     */
    private String renderBinary(final BinaryValue value, final String format) {
        switch (format.toUpperCase(Locale.ROOT)) {
            case "HEX":
                return value.toHex();
            case "BASE64":
                return value.toBase64();
            case "UTF-8":
            case "UTF8":
                return new String(value.bytes(), StandardCharsets.UTF_8);
            default:
                throw new RuntimeException("Invalid binary format string '" + format + "'");
        }
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 2; }
}
