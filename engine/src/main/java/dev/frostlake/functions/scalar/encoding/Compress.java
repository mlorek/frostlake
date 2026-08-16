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

package dev.frostlake.functions.scalar.encoding;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.BinaryType;
import dev.frostlake.values.BinaryValue;

import java.util.List;

/**
 * COMPRESS(input, method) — compresses the input (a string's UTF-8 bytes, or a BINARY value's
 * bytes) with {@code method} and returns the compressed bytes as BINARY, matching Snowflake's
 * return type.
 *
 * <p>The method is mandatory: Snowflake has no default and rejects {@code COMPRESS('hello')} with
 * "not enough arguments for function [COMPRESS('hello')], expected 2, got 1" (SQLSTATE 22023, error
 * 938). Either argument being NULL yields NULL, without validating the other.
 */
public class Compress extends BuiltInFunction {
    public Compress() { super("COMPRESS", BinaryType.UNSIZED); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final CompressionMethod method = CompressionMethod.parse(args.get(1).toString());
        return BinaryValue.of(
            CompressionCodec.compress(SharedFunctionHelpers.toUtf8(args.get(0)), method));
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
