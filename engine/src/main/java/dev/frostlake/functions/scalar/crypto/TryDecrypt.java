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

/** TRY_DECRYPT — DECRYPT that returns NULL instead of erroring on a bad key or payload. */
public class TryDecrypt extends BuiltInFunction {

    private static final Decrypt BASE = new Decrypt();

    public TryDecrypt() { super("TRY_DECRYPT", BinaryType.UNSIZED); }

    @Override
    public Object evaluate(final List<Object> args) {
        try {
            return BASE.evaluate(args);
        } catch (final RuntimeException failed) {
            return null;
        }
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 4; }
}
