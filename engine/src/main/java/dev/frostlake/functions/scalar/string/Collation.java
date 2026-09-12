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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * {@code COLLATION(expr)}: the collation specification {@code expr} carries, lower-cased, or NULL when
 * it carries none. It is a property of the EXPRESSION rather than of any value — a column's declared
 * collation, an explicit COLLATE, or what a concatenation or a conditional inherits from them — so the
 * evaluator answers it before an argument is evaluated; this entry holds the name and the arity.
 */
public class Collation extends BuiltInFunction {
    public Collation() {
        super("COLLATION", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return null;
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
