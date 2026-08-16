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
import dev.frostlake.types.IntegerResultWidths;

import java.util.List;

/**
 * HASH(expr[, …]) — a deterministic signed 64-bit hash (FNV-1a) over the canonical, type-tagged encoding
 * of its arguments. Stable within Frostlake for the same run and across runs, so it is fine for bucketing,
 * sampling, GROUP BY on a hash, or change detection.
 *
 * <p><strong>The VALUES are not Snowflake's, but the EQUALITY RELATION is.</strong> Snowflake's HASH is a
 * proprietary 64-bit algorithm that cannot be matched bit-for-bit from outside, so never compare a
 * Frostlake HASH result against one computed by Snowflake. Which arguments hash ALIKE is reproducible,
 * however, and it is: numbers hash by value with scale and declared type ignored, so 1 and 1.00 collide
 * while the number 1 and the string '1' do not. {@link HashCanonicalValue} holds the full set of classes —
 * several of them counter-intuitive, including booleans and temporals landing in the number class.
 *
 * <p>Arguments are mixed in order and each contributes its tag even when NULL, so arity and order both
 * matter: HASH(1) , HASH(1, NULL) and HASH(NULL) are three different hashes. HASH_AGG shares this encoding.
 */
public class HashFn extends BuiltInFunction {
    public HashFn() { super("HASH", IntegerResultWidths.WIDE_COUNTER); }

    @Override
    public Object evaluate(final List<Object> args) {
        long h = 0xcbf29ce484222325L;
        for (final Object arg : args) {
            final byte[] b = HashCanonicalValue.encode(arg);
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
