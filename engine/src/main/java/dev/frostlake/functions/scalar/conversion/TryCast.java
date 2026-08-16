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

package dev.frostlake.functions.scalar.conversion;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.VariantType;

import java.util.List;

/**
 * Catalog entry for TRY_CAST. The construct is pure grammar — {@code TRY_CAST(expr AS type)} parses
 * as a cast expression and evaluates there; a function-call shape ({@code TRY_CAST(x, 'type')} or
 * {@code TRY_CAST(x)}) is a syntax error at the comma/paren, live-verified, and the grammar refuses
 * it before name resolution. This class exists only so SHOW FUNCTIONS lists TRY_CAST the way a real
 * account does.
 */
public class TryCast extends BuiltInFunction {

    public TryCast() {
        super("TRY_CAST", VariantType.VARIANT);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        throw new RuntimeException(
            "TRY_CAST is not callable as a function; use TRY_CAST(<expr> AS <type>)");
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 2;
    }
}
