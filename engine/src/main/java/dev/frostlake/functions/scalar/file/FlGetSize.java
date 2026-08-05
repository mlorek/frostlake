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

package dev.frostlake.functions.scalar.file;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * {@code FL_GET_SIZE(file)} — the file's size in bytes.
 *
 * <p>Live-verified: the byte length of the file, and on a server-side-encrypted stage it is
 * the PLAINTEXT file size rather than the stored ciphertext length. NULL in, NULL out.
 */
public class FlGetSize extends BuiltInFunction {

    public FlGetSize() {
        super("FL_GET_SIZE", NumericType.INTEGER);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return FileFunctionHelper.numberField(args.get(0), FileDescriptor.SIZE);
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }
}
