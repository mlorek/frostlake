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

package dev.frostlake.functions.scalar.crypto;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.BinaryType;
import java.util.List;

/** TRY_DECRYPT_RAW — DECRYPT_RAW that returns NULL instead of erroring. */
public class TryDecryptRaw extends BuiltInFunction {

    private static final DecryptRaw BASE = new DecryptRaw();

    public TryDecryptRaw() { super("TRY_DECRYPT_RAW", BinaryType.VARBINARY); }

    @Override
    public Object evaluate(final List<Object> args) {
        // Argument-type errors surface as a compile error on live even for TRY_ — only a genuine
        // decryption failure (wrong key/tag) yields NULL. Validate the BINARY-typed arguments up
        // front so a VARCHAR raises "Invalid argument types" instead of being swallowed.
        for (int i = 0; i < args.size() && i < 3; i++) {
            if (args.get(i) != null) {
                RawCipherSupport.binaryBytes("TRY_DECRYPT_RAW", args.get(i));
            }
        }
        if (args.size() >= 4 && args.get(3) != null) {
            RawCipherSupport.binaryBytes("TRY_DECRYPT_RAW", args.get(3));
        }
        if (args.size() >= 6 && args.get(5) != null) {
            RawCipherSupport.binaryBytes("TRY_DECRYPT_RAW", args.get(5));
        }
        try {
            return BASE.evaluate(args);
        } catch (final RuntimeException failed) {
            return null;
        }
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 6; }
}
