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

import dev.frostlake.executor.SessionZone;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.DataType;
import dev.frostlake.values.TypedScalarNode;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

/**
 * {@code TIME(<expr>)} — the TIME synonym, which is not TO_TIME. The account resolves it among
 * one-argument overloads — a text, a VARIANT, a timestamp — so it reads a text exactly as TO_TIME does
 * and everything else its own way (all live-verified):
 *
 * <pre>
 *   TIME('10:00:00')              10:00:00
 *   TIME(TRUE)                    Time 'true' is not recognized            a BOOLEAN reads as its text
 *   TIME(123), TIME(n)            Failed to cast variant value 123 to TIME   a NUMBER goes by a VARIANT
 *   TIME(d)                       00:00:00                                 a DATE is its midnight
 *   TIME(ts), TIME(ltz), TIME(tz) the wall clock
 *   TIME(o), TIME(a)              Failed to cast variant value {"a":1} to TIME
 *   TIME(PARSE_JSON('"10:00"'))   10:00:00                                 a VARIANT's text, or its TIME
 *   TIME(PARSE_JSON('"abc"'))     Failed to cast variant value "abc" to TIME
 *   TIME(tm)                      incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]
 *   TIME(bn)                      Invalid argument types for function 'TIME': (BINARY(8388608))
 *   TIME('10:00', 'HH24:MI')      too many arguments for function [...] expected 1, got 2
 *   TIME()                        Invalid argument types for function 'TIME': ()
 * </pre>
 */
public class TimeFunction extends ToTime {

    public TimeFunction() { super("TIME"); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object value = args.get(0);
        if (value instanceof LocalDate) {
            return LocalTime.MIDNIGHT;
        }
        if (value instanceof OffsetDateTime) {
            // A TIMESTAMP_LTZ is an instant, read on the session's clock.
            return ((OffsetDateTime) value).atZoneSameInstant(SessionZone.current()).toLocalTime();
        }
        if (value instanceof ZonedDateTime) {
            // A TIMESTAMP_TZ keeps the clock it was written with.
            return ((ZonedDateTime) value).toLocalTime();
        }
        if (value instanceof VariantValue) {
            return fromVariant((VariantValue) value);
        }
        return super.evaluate(args);
    }

    /** A VARIANT's TIME, or its text read as TO_TIME reads one; anything else fails the cast. */
    private Object fromVariant(final VariantValue value) {
        final JsonNode node = value.node();
        final Object typed = TypedScalarNode.typedValueOf(node);
        if (typed instanceof LocalTime) {
            return typed;
        }
        if (typed == null && node.isTextual()) {
            try {
                return super.evaluate(Collections.<Object>singletonList(node.asText()));
            } catch (final RuntimeException unreadable) {
                throw castFailure(value);
            }
        }
        throw castFailure(value);
    }

    private static RuntimeException castFailure(final VariantValue value) {
        return new RuntimeException("Failed to cast variant value " + VariantJsonText.clientTextOf(value)
            + " to TIME");
    }

    @Override
    public int getMaxArgCount() { return 1; }

    @Override
    public boolean refusesMissingArgumentsByType() {
        return true;
    }

    /** A BINARY matches no overload: "Invalid argument types for function 'TIME': (BINARY(1))". */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /** A TIME is refused on its way to the TIMESTAMP_LTZ overload; a DATE and a timestamp are read. */
    @Override
    public SemiStructuredRejection temporalRejection(final int position, final int argumentCount,
                                                     final DataType temporal) {
        return "TIME".equalsIgnoreCase(temporal.getName())
            ? SemiStructuredRejection.TIME_TO_TIMESTAMP_LTZ : SemiStructuredRejection.NONE;
    }
}
