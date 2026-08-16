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
 * DECOMPRESS_BINARY(input, method) — decompresses a BINARY input and returns the decompressed
 * bytes as BINARY, matching Snowflake's signature. The method is mandatory, as in Snowflake ("not
 * enough arguments … expected 2, got 1").
 */
public class DecompressBinary extends BuiltInFunction {
    public DecompressBinary() { super("DECOMPRESS_BINARY", BinaryType.VARBINARY); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final byte[] data = SharedFunctionHelpers.binaryArgBytes(args.get(0), "DECOMPRESS_BINARY");
        final CompressionMethod method = CompressionMethod.parse(args.get(1).toString());
        return BinaryValue.of(CompressionCodec.decompress(data, method));
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
