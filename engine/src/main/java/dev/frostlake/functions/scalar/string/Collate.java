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
 * {@code COLLATE(expr, 'spec')}, also written {@code expr COLLATE 'spec'}: the string itself, carrying
 * the named collation into whatever compares it. The value never changes — what the collation changes
 * is judged where the value is COMPARED, from the call's specification, so the evaluator reads the
 * call rather than this answer (see CollationSpec).
 */
public class Collate extends BuiltInFunction {
    public Collate() {
        super("COLLATE", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return args.get(0);
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return 2;
    }
}
