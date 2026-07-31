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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The VARIANT JSON null ({@code NULL_VALUE}) versus SQL NULL boundary.
 *
 * <p>A JSON null is a real VARIANT value, not SQL NULL. Live-verified on a real account
 *, with {@code jn} standing for {@code PARSE_JSON('{"b":null}'):b}:
 *
 * <pre>
 *   TYPEOF(jn)          -&gt; 'NULL_VALUE'      jn IS NULL      -&gt; FALSE
 *   IS_NULL_VALUE(jn)   -&gt; TRUE              jn IS NOT NULL  -&gt; TRUE
 *   TO_JSON(jn)         -&gt; 'null'            EQUAL_NULL(jn, NULL) -&gt; FALSE
 * </pre>
 *
 * <p>A MISSING key stays SQL NULL and is a different thing entirely: live,
 * {@code PARSE_JSON('{"a":1}'):zz IS NULL} is TRUE and {@code TYPEOF(...)} of it is SQL NULL.
 *
 * <p>The JSON null only becomes SQL NULL where it has to leave the semi-structured world and be
 * read as an ordinary scalar. Live-verified in the same run:
 *
 * <pre>
 *   jn + 1  jn * 2  jn - 1     -&gt; SQL NULL      jn || 'x'   CONCAT(jn,'x')  -&gt; SQL NULL
 *   jn::INT  jn::VARCHAR  jn::BOOLEAN -&gt; SQL NULL
 *   UPPER(jn)  LENGTH(jn)  ABS(jn)  TO_VARCHAR(jn) -&gt; SQL NULL
 *   DATEADD(day, jn, d) -&gt; SQL NULL
 *   COUNT(jn) -&gt; 0    SUM/AVG/LISTAGG over {jn, 3} -&gt; 3 / 3 / '3'   (the JSON null is skipped)
 * </pre>
 *
 * <p>while the semi-structured and null-handling families keep seeing the value itself — live,
 * {@code COALESCE(jn, 9)} returns the JSON null (so {@code COALESCE(jn,9)::VARCHAR} is SQL NULL,
 * not 9), {@code ARRAY_CONSTRUCT(jn)} is {@code [null]}, {@code OBJECT_CONSTRUCT('k',jn)} is
 * {@code {"k":null}} and MIN/MAX order it as a value ({@code MAX} over {@code {jn,5}} is the JSON
 * null, {@code MIN} is 5).
 */
public final class VariantJsonNulls {

    /**
     * Aggregates that treat a JSON null as a VALUE rather than as missing input: the ordering-based
     * ones and the collection builders. Live: MAX over {JSON null, 5} is the JSON null while
     * SUM/COUNT/AVG/LISTAGG over the same input ignore it. Every other aggregate sees SQL NULL.
     */
    private static final Set<String> VALUE_AGGREGATES = new HashSet<>();

    static {
        VALUE_AGGREGATES.add("MIN");
        VALUE_AGGREGATES.add("MAX");
        VALUE_AGGREGATES.add("MIN_BY");
        VALUE_AGGREGATES.add("MAX_BY");
        VALUE_AGGREGATES.add("ANY_VALUE");
        VALUE_AGGREGATES.add("MODE");
        VALUE_AGGREGATES.add("ARRAY_AGG");
        VALUE_AGGREGATES.add("ARRAY_UNION_AGG");
        VALUE_AGGREGATES.add("ARRAY_UNIQUE_AGG");
        VALUE_AGGREGATES.add("OBJECT_AGG");
    }

    private VariantJsonNulls() {
    }

    /** Whether {@code value} is the VARIANT JSON null — a typed semi-structured {@code null}. */
    public static boolean isJsonNull(final Object value) {
        return value instanceof VariantValue && ((VariantValue) value).isJsonNull();
    }

    /**
     * The value as an ordinary scalar operand: a JSON null reads as SQL NULL, everything else is
     * itself. Apply this wherever a VARIANT leaves the semi-structured world — arithmetic, string
     * concatenation, a cast to a non-semi-structured type, a scalar function argument.
     */
    public static Object asScalar(final Object value) {
        return isJsonNull(value) ? null : value;
    }

    /** Whether an aggregate of this name reads a JSON null as a value rather than as missing input. */
    public static boolean isValueAggregate(final String aggregateName) {
        return aggregateName != null && VALUE_AGGREGATES.contains(aggregateName.toUpperCase());
    }

    /**
     * The aggregate's input value: SQL NULL for a JSON null unless the aggregate orders or collects
     * values rather than reading them as scalars.
     */
    public static Object asAggregateInput(final String aggregateName, final Object value) {
        return isValueAggregate(aggregateName) ? value : asScalar(value);
    }

    /**
     * A copy of {@code args} with every JSON null read as SQL NULL, or {@code args} itself when
     * nothing changes. Callers must skip this for variant-aware functions.
     */
    public static List<Object> asScalarArgs(final List<Object> args) {
        if (args == null) {
            return null;
        }
        int firstJsonNull = -1;
        for (int i = 0; i < args.size(); i++) {
            if (isJsonNull(args.get(i))) {
                firstJsonNull = i;
                break;
            }
        }
        if (firstJsonNull < 0) {
            return args;
        }
        final List<Object> coerced = new ArrayList<>(args);
        for (int i = firstJsonNull; i < coerced.size(); i++) {
            if (isJsonNull(coerced.get(i))) {
                coerced.set(i, null);
            }
        }
        return coerced;
    }
}
