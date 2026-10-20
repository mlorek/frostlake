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
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import org.graalvm.polyglot.Value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * What a Python handler hands back, as the value of its declared type (live-verified): a procedure's or a function's
 * answer as its RETURNS type, an aggregate's as well, and each value a table function writes as its column's type.
 * The Python side ({@link #CONVERT}) applies the language's own conversions — {@code int(value)} for a whole NUMBER,
 * which truncates, {@code float(value)}, {@code str(value)}, {@code bool(value)} — and judges the classes a DATE,
 * a TIMESTAMP, an ARRAY, an OBJECT and a BINARY take, a TIMESTAMP_NTZ and a TIMESTAMP_LTZ a {@code datetime}
 * without an offset; this side builds the SQL value from the text it reports, rounds a NUMBER with a scale half to
 * even, and words the refusals as the account does, each naming the routine and its handler.
 *
 * <p>A semi-structured answer is the value's JSON with every float marked as a DOUBLE (see {@link HandlerJson}),
 * a tuple as a list, and a date, a time, a datetime, a {@code Decimal} and {@code bytes} as {@code str(value)};
 * any other class is {@code TypeError: Object of type set is not serializable}. {@code None} is a JSON null for
 * a VARIANT and a SQL NULL for an ARRAY and an OBJECT.
 */
final class PythonReturn {

    /** What {@link #declared} answers for a type that takes the answer as the runtime holds it. */
    static final Object AS_IS = new Object();

    /** The name of the module {@link #CONVERT} is made into on each interpreter. */
    static final String MODULE = "__frostlake_returns";

    /**
     * Defines {@code __fl_converted(value, kind, scale)}, answering a pair: what became of the value ({@code null},
     * {@code value}, {@code int}, {@code decimal}, {@code python}, {@code refuse}, {@code raise}, {@code pass}) and its
     * text.
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
            if kind.startswith('TIMESTAMP'):
                if not isinstance(v, __fl_datetime.datetime):
                    return ('refuse', 'Return value should be a datetime object')
                if kind != 'TIMESTAMP_TZ' and v.utcoffset() is not None:
                    return ('refuse', 'Datetime object must be naive to be convertible to ' + kind)
                return ('value', v.isoformat())
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
        def __fl_converted(v, kind, scale):
            try:
                return __fl_convert(v, kind, scale)
            except Exception as __fl_error:
                return ('raise', type(__fl_error).__name__ + ': ' + str(__fl_error))
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
        final String written = declared == null || declared.getName() == null ? ""
            : declared.getName().toUpperCase(Locale.ROOT);
        final int precision = written.indexOf('(');
        final String name = precision < 0 ? written : written.substring(0, precision).trim();
        if ("ARRAY".equals(name) || "DATE".equals(name) || "TIME".equals(name)) {
            return name;
        }
        if (!name.startsWith("TIMESTAMP") && !"DATETIME".equals(name)) {
            return null;
        }
        // A TIMESTAMP and a DATETIME are the NTZ one.
        return name.endsWith("_TZ") ? "TIMESTAMP_TZ" : name.endsWith("_LTZ") ? "TIMESTAMP_LTZ" : "TIMESTAMP_NTZ";
    }

    /** The scale {@link #CONVERT} is told: a NUMBER's declared scale, zero for any other type. */
    static int scaleOf(final DataType declared) {
        return declared instanceof NumericType ? ((NumericType) declared).getScale() : 0;
    }

    /**
     * The handler's answer as the value of a declared type, converted as Python converts and judged as the account
     * judges it (see {@link #CONVERT}), or {@link #AS_IS} when the type takes the answer as the runtime holds it.
     *
     * @param answer   what the handler answered, or null when it left nothing
     * @param declared the declared type, or null for none
     * @param name     the routine's name, which a refusal carries
     * @param handler  the routine's handler, which a refusal carries
     * @return the value, or {@link #AS_IS}
     * @throws PythonReturnRefusal when the type does not take the answer
     */
    static Object declared(final Value answer, final DataType declared, final String name, final String handler) {
        final String kind = kindOf(declared);
        if (kind == null) {
            return AS_IS;
        }
        if (answer == null) {
            return "VARIANT".equals(kind) ? HandlerJson.toVariant("null") : null;
        }
        final Value pair = PythonRuntime.module(MODULE, CONVERT)
            .invokeMember("__fl_converted", answer, kind, Integer.valueOf(scaleOf(declared)));
        return fromPair(pair.getArrayElement(0).asString(), pair.getArrayElement(1).asString(), declared, name,
            handler);
    }

    /**
     * {@link #declared(Value, DataType, String, String)} for the global a handler left, on whichever backend runs: the
     * CPython worker runs {@link #CONVERT} as a module of its own and hands back the pair.
     *
     * @param global   the global holding the answer
     * @param declared the declared type
     * @param name     the routine, for a refusal
     * @param handler  the handler, for a refusal
     * @return the value, or {@link #AS_IS}
     */
    static Object declaredGlobal(final String global, final DataType declared, final String name,
                                 final String handler) {
        if (PythonInterpreter.holdsPolyglotValues()) {
            return declared(PythonRuntime.global(global), declared, name, handler);
        }
        final String kind = kindOf(declared);
        if (kind == null) {
            return AS_IS;
        }
        installConvert();
        PythonInterpreter.eval("import " + MODULE + " as __fl_returns\n__fl_pair = __fl_returns.__fl_converted("
            + global + ", '" + kind + "', " + scaleOf(declared) + ")\n");
        final List<?> pair = (List<?>) PythonInterpreter.global("__fl_pair");
        return fromPair(String.valueOf(pair.get(0)), String.valueOf(pair.get(1)), declared, name, handler);
    }

    /** Make {@link #CONVERT} a module of the runtime's on this thread's interpreter, importable as {@link #MODULE}. */
    static void installConvert() {
        PythonInterpreter.installModule(MODULE, CONVERT);
    }

    /**
     * The SQL value for the pair {@link #CONVERT} reported, or {@link #AS_IS} for {@code pass}.
     *
     * @param outcome  what became of the value
     * @param text     the value's text, or the refusal's sentence
     * @param declared the declared type
     * @param name     the routine, for a refusal
     * @param handler  the handler, for a refusal
     * @return the value, or {@link #AS_IS}
     */
    static Object fromPair(final String outcome, final String text, final DataType declared, final String name,
                           final String handler) {
        if ("pass".equals(outcome)) {
            return AS_IS;
        }
        final String where = " in function " + name + " with handler " + handler;
        if ("raise".equals(outcome)) {
            throw new PythonReturnRefusal("Python Interpreter Error:\n" + text + where);
        }
        try {
            return toSql(outcome, text, declared, where);
        } catch (final PythonReturnRefusal refused) {
            throw refused;
        } catch (final RuntimeException refused) {
            throw new PythonReturnRefusal(refused.getMessage());
        }
    }

    /**
     * Builds the SQL value from what the Python side reported.
     *
     * @param outcome  what became of the value, as {@link #CONVERT} names it; never {@code pass} or {@code raise}
     * @param text     the value's text, or the refusal's sentence
     * @param declared the declared type
     * @param where    where a refusal says it happened: the routine and its handler
     * @return the value
     */
    private static Object toSql(final String outcome, final String text, final DataType declared,
                                final String where) {
        if ("null".equals(outcome)) {
            return null;
        }
        if ("refuse".equals(outcome)) {
            throw new PythonReturnRefusal(text + where);
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
        if (kind != null && kind.startsWith("TIMESTAMP")) {
            // Only a TIMESTAMP_TZ takes a datetime with an offset, and it keeps that offset as its own.
            return zoned(text) ? (Object) OffsetDateTime.parse(text).toZonedDateTime() : LocalDateTime.parse(text);
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
            throw new PythonReturnRefusal(("decimal".equals(outcome)
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
