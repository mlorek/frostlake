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

package dev.frostlake.rt.py;

import dev.frostlake.executor.NumericRangeRefusal;
import dev.frostlake.executor.udf.HandlerJson;
import dev.frostlake.executor.udf.HandlerResults;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * What a Python procedure's handler hands back, as the value of the declared RETURNS type (live-verified). The
 * Python side ({@link #CONVERT}) applies the language's own conversions — {@code int(value)} for a whole NUMBER,
 * which truncates, {@code float(value)}, {@code str(value)}, {@code bool(value)} — and judges the classes a DATE,
 * a TIMESTAMP, an ARRAY, an OBJECT and a BINARY take; this side builds the SQL value from the text it reports,
 * rounds a NUMBER with a scale half to even, and words the refusals as the account does, each naming the
 * procedure and its handler.
 *
 * <p>A semi-structured answer is the value's JSON with every float marked as a DOUBLE (see {@link HandlerJson}),
 * a tuple as a list, and a date, a time, a datetime, a {@code Decimal} and {@code bytes} as {@code str(value)};
 * any other class is {@code TypeError: Object of type set is not serializable}. {@code None} is a JSON null for
 * a VARIANT and a SQL NULL for an ARRAY and an OBJECT.
 */
final class PythonReturn {

    /**
     * Defines {@code __fl_convert(value, kind, scale)}, answering a pair: what became of the value ({@code null},
     * {@code value}, {@code int}, {@code decimal}, {@code python}, {@code refuse}, {@code pass}) and its text.
     */
    static final String CONVERT = """
        import datetime as __fl_datetime
        import decimal as __fl_decimal
        import json as __fl_json
        def __fl_spell(v):
            if v is None or isinstance(v, (bool, int, str)):
                return v
            if isinstance(v, float):
                return '@DOUBLE@' + repr(v)
            if isinstance(v, (list, tuple)):
                return [__fl_spell(e) for e in v]
            if isinstance(v, dict):
                return dict((k, __fl_spell(e)) for k, e in v.items())
            if isinstance(v, (__fl_datetime.date, __fl_datetime.time, __fl_decimal.Decimal, bytes)):
                return str(v)
            raise TypeError('Object of type ' + type(v).__name__ + ' is not serializable')
        def __fl_convert(v, kind, scale):
            if v is None:
                return ('value', 'null') if kind == 'VARIANT' else ('null', '')
            if kind == 'NUMBER':
                if scale == 0:
                    return ('int', str(int(v)))
                if isinstance(v, __fl_decimal.Decimal):
                    return ('decimal', str(v))
                return ('python', str(int(v) if isinstance(v, bool) else __fl_decimal.Decimal(str(v))))
            if kind == 'FLOAT':
                return ('value', repr(float(v)))
            if kind == 'TEXT':
                return ('value', str(v))
            if kind == 'BOOLEAN':
                return ('value', 'true' if bool(v) else 'false')
            if kind == 'DATE':
                if isinstance(v, __fl_datetime.datetime):
                    return ('value', v.date().isoformat())
                if isinstance(v, __fl_datetime.date):
                    return ('value', v.isoformat())
                return ('refuse', 'Return value should be a date object')
            if kind == 'TIME':
                if isinstance(v, __fl_datetime.time):
                    return ('value', v.isoformat())
                return ('pass', '')
            if kind == 'TIMESTAMP':
                if isinstance(v, __fl_datetime.datetime):
                    return ('value', v.isoformat())
                return ('refuse', 'Return value should be a datetime object')
            if kind == 'ARRAY':
                if isinstance(v, list):
                    return ('value', __fl_json.dumps(__fl_spell(v)))
                return ('refuse', 'python value needs to be a list to convert to Snowflake ARRAY')
            if kind == 'OBJECT':
                if isinstance(v, dict):
                    return ('value', __fl_json.dumps(__fl_spell(v)))
                return ('refuse', 'python value needs to be a dict to convert to Snowflake OBJECT')
            if kind == 'VARIANT':
                return ('value', __fl_json.dumps(__fl_spell(v)))
            if kind == 'BINARY':
                if not isinstance(v, (bytes, bytearray)):
                    raise TypeError("cannot convert '" + type(v).__name__ + "' object to bytes")
                return ('value', bytes(v).hex())
            return ('pass', '')
        """.replace("@DOUBLE@", HandlerJson.DOUBLE_IN_SOURCE);

    private PythonReturn() {
    }

    /**
     * The kind {@link #CONVERT} is told for a declared type, or null for one the answer is handed back under as
     * the runtime has it.
     */
    static String kindOf(final DataType declared) {
        if (declared instanceof NumericType) {
            return NumericType.isApproximate((NumericType) declared) ? "FLOAT" : "NUMBER";
        }
        if (declared instanceof StringType) {
            return "TEXT";
        }
        if (declared instanceof BooleanType) {
            return "BOOLEAN";
        }
        if (declared instanceof BinaryType) {
            return "BINARY";
        }
        if (declared instanceof ObjectType) {
            return "OBJECT";
        }
        if (declared instanceof VariantType) {
            return "VARIANT";
        }
        final String name = declared == null || declared.getName() == null ? ""
            : declared.getName().toUpperCase(Locale.ROOT);
        if ("ARRAY".equals(name) || "DATE".equals(name) || "TIME".equals(name)) {
            return name;
        }
        return name.startsWith("TIMESTAMP") || "DATETIME".equals(name) ? "TIMESTAMP" : null;
    }

    /** The scale {@link #CONVERT} is told: a NUMBER's declared scale, zero for any other type. */
    static int scaleOf(final DataType declared) {
        return declared instanceof NumericType ? ((NumericType) declared).getScale() : 0;
    }

    /**
     * Builds the SQL value from what the Python side reported.
     *
     * @param outcome   what became of the value, as {@link #CONVERT} names it; never {@code pass}
     * @param text      the value's text, or the refusal's sentence
     * @param procedure the procedure, whose RETURNS type and name the answer and the refusals carry
     * @return the value the CALL answers
     */
    static Object toSql(final String outcome, final String text, final Procedure procedure) {
        final DataType declared = procedure.getReturnType();
        final String where = " in function " + procedure.getName() + " with handler " + procedure.getHandler();
        if ("null".equals(outcome)) {
            return null;
        }
        if ("refuse".equals(outcome)) {
            throw new RuntimeException(text + where);
        }
        if (declared instanceof NumericType) {
            final NumericType numeric = (NumericType) declared;
            return NumericType.isApproximate(numeric) ? (Object) floating(text) : exact(outcome, text, numeric, where);
        }
        if (declared instanceof BooleanType) {
            return Boolean.valueOf("true".equals(text));
        }
        if (declared instanceof BinaryType) {
            return HandlerResults.binary(BinaryValue.fromHex(text.toUpperCase(Locale.ROOT)), (BinaryType) declared,
                where);
        }
        final String kind = kindOf(declared);
        if ("DATE".equals(kind)) {
            return LocalDate.parse(text);
        }
        if ("TIME".equals(kind)) {
            return LocalTime.parse(text);
        }
        if ("TIMESTAMP".equals(kind)) {
            return zoned(text) ? (Object) OffsetDateTime.parse(text) : LocalDateTime.parse(text);
        }
        if ("ARRAY".equals(kind) || "OBJECT".equals(kind) || "VARIANT".equals(kind)) {
            return HandlerJson.toVariant(text);
        }
        return text;
    }

    /** Whether an ISO timestamp text ends in an offset. */
    private static boolean zoned(final String text) {
        final int time = text.indexOf('T');
        return time >= 0 && (text.indexOf('+', time) >= 0 || text.indexOf('-', time) >= 0);
    }

    private static Double floating(final String text) {
        if ("nan".equals(text)) {
            return Double.valueOf(Double.NaN);
        }
        if ("inf".equals(text) || "-inf".equals(text)) {
            return Double.valueOf(text.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
        }
        return Double.valueOf(text);
    }

    private static Object exact(final String outcome, final String text, final NumericType type, final String where) {
        final BigDecimal held = new BigDecimal(text).setScale(type.getScale(), RoundingMode.HALF_EVEN);
        if (type.getPrecision() > 0 && NumericRangeRefusal.exceeds(held, type.getPrecision(), type.getScale())) {
            throw new RuntimeException(("decimal".equals(outcome)
                ? "Decimal value cannot be losslessly represented as a Snowflake NUMBER with a precision of "
                    + type.getPrecision() + " and a scale of " + type.getScale()
                : "Python value cannot be losslessly represented as a Snowflake NUMBER with a precision of "
                    + type.getPrecision() + ", value: " + text) + where);
        }
        if (type.getScale() == 0) {
            return held.unscaledValue().bitLength() < Long.SIZE ? (Object) Long.valueOf(held.longValue()) : held;
        }
        return held;
    }
}
