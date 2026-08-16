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
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * MD5(msg) — the lowercase hex MD5 digest of the message bytes.
 *
 * <p>A BINARY message is digested as its OWN bytes, so {@code MD5(TO_BINARY('61','HEX'))} equals
 * {@code MD5('a')}; digesting {@code toString()} would hash the ASCII hex text instead.
 */
public class Md5 extends BuiltInFunction {
    public Md5() { super("MD5", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        return SharedFunctionHelpers.toHex(
            SharedFunctionHelpers.digest("MD5", SharedFunctionHelpers.toUtf8(args.get(0))));
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
