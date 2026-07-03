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
 * BOOLAND(expr1, expr2) — three-valued logical AND over numeric/boolean inputs
 * (non-zero is TRUE, zero is FALSE). Returns TRUE when both are true, FALSE when
 * either is false (even if the other is NULL), and NULL (UNKNOWN) otherwise.
 */
public class BoolAnd extends BuiltInFunction {
    public BoolAnd() { super("BOOLAND", BooleanType.BOOLEAN); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Boolean a = BoolFunctionHelper.truthiness(args.get(0));
        final Boolean b = BoolFunctionHelper.truthiness(args.get(1));
        if (Boolean.FALSE.equals(a) || Boolean.FALSE.equals(b)) {
            return Boolean.FALSE;
        }
        if (a == null || b == null) {
            return null;
        }
        return Boolean.TRUE;
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
