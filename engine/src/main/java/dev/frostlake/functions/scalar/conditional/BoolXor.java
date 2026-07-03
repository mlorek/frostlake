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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.BooleanType;

import java.util.List;

/**
 * BOOLXOR(expr1, expr2) — three-valued logical exclusive-OR over numeric/boolean
 * inputs (non-zero is TRUE, zero is FALSE). Returns TRUE when exactly one input is
 * true, FALSE when both are true or both are false, and NULL when either input is NULL.
 */
public class BoolXor extends BuiltInFunction {
    public BoolXor() { super("BOOLXOR", BooleanType.BOOLEAN); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Boolean a = BoolFunctionHelper.truthiness(args.get(0));
        final Boolean b = BoolFunctionHelper.truthiness(args.get(1));
        if (a == null || b == null) {
            return null;
        }
        return a.booleanValue() != b.booleanValue();
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
