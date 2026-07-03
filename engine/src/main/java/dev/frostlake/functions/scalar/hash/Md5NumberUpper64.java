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
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

public class Md5NumberUpper64 extends BuiltInFunction {
    public Md5NumberUpper64() { super("MD5_NUMBER_UPPER64", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final byte[] d = SharedFunctionHelpers.digest("MD5", SharedFunctionHelpers.toUtf8(args.get(0)));
        // Upper 64 bits of the MD5 digest as an UNSIGNED integer (Snowflake NUMBER(38,0)). Reading the
        // 8 bytes as a signed long would return a negative value whenever the high bit is set.
        return new BigDecimal(new BigInteger(1, Arrays.copyOfRange(d, 0, 8)));
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
