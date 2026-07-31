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
import dev.frostlake.types.BooleanType;
import java.time.LocalDateTime;
import java.util.List;

/** IS_TIMESTAMP_NTZ(v) — TRUE when the value is a TIMESTAMP (LTZ/TZ alias here; the engine models all as NTZ). */
public class IsTimestampNtz extends StructuredArgumentFunction {
    public IsTimestampNtz() { super("IS_TIMESTAMP_NTZ", BooleanType.BOOLEAN); }

    @Override
    public Object evaluate(final List<Object> args) {
        return args.get(0) == null ? null : args.get(0) instanceof LocalDateTime;
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
