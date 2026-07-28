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

package dev.frostlake.functions.scalar.conditional;

import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.VariantType;

import java.util.List;

public class Iff extends BuiltInFunction {
    public Iff() { super("IFF", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        Object condition = args.get(0);
        Object trueValue = args.get(1);
        Object falseValue = args.get(2);

        // Snowflake coerces the condition like any boolean position ('false' text is FALSE, numbers
        // by zero/non-zero) — treating every non-null value as TRUE sent IFF('false', a, b) to a.
        return SqlTruth.isTrue(condition) ? trueValue : falseValue;
    }

    @Override public int getMinArgCount() { return 3; }
    @Override public int getMaxArgCount() { return 3; }
}
