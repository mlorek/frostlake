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

package dev.frostlake.functions.scalar.hash;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.IntegerResultWidths;

import java.util.List;

/**
 * HASH(expr[, …]) — a deterministic signed 64-bit hash over the UTF-8 encodings of its arguments
 * (FNV-1a). Stable within Frostlake for the same run and across runs, so it is fine for bucketing,
 * sampling, GROUP BY on a hash, or change detection.
 *
 * <p><strong>Does NOT reproduce Snowflake's HASH() values.</strong> Snowflake's HASH is a proprietary,
 * undocumented, type-aware 64-bit algorithm (e.g. it hashes the number 1 and the string '1' differently
 * and normalizes numeric scale), which cannot be matched bit-for-bit from the outside. Do not compare a
 * Frostlake HASH result against a value computed by Snowflake — only rely on its stability within this
 * engine. HASH_AGG has the same limitation.
 */
public class HashFn extends BuiltInFunction {
    public HashFn() { super("HASH", IntegerResultWidths.WIDE_COUNTER); }

    @Override
    public Object evaluate(final List<Object> args) {
        long h = 0xcbf29ce484222325L;
        for (final Object arg : args) {
            if (arg == null) {
                h ^= 0L;
                h *= 0x100000001b3L;
                continue;
            }
            final byte[] b = SharedFunctionHelpers.toUtf8(arg);
            for (final byte v : b) {
                h ^= v;
                h *= 0x100000001b3L;
            }
        }
        return h;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
