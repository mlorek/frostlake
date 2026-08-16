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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.StructuredArgumentFunction;
import dev.frostlake.types.BinaryType;
import dev.frostlake.values.BinaryValue;
import java.util.List;

/** AS_BINARY(v) — the value when it is BINARY, else NULL (the engine's variant model carries no binary members). */
public class AsBinary extends StructuredArgumentFunction {
    public AsBinary() { super("AS_BINARY", BinaryType.VARBINARY); }

    @Override
    public Object evaluate(final List<Object> args) {
        return args.get(0) instanceof BinaryValue ? args.get(0) : null;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
