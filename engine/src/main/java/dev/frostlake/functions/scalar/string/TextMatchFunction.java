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

import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.BooleanType;

import java.util.List;

/**
 * CONTAINS, STARTSWITH and ENDSWITH: whether a text holds another where each looks, character for character, or as
 * a collation matches it under one. A NULL in either argument answers NULL, never FALSE.
 */
public abstract class TextMatchFunction extends TextArgumentFunction {

    protected TextMatchFunction(final String name) {
        super(name, new BooleanType());
    }

    /**
     * Whether a text holds the needle where this function looks, character for character.
     *
     * @param text   the text searched
     * @param needle the text looked for
     * @return whether it is there
     */
    protected abstract boolean holds(final String text, final String needle);

    /**
     * Whether a text holds the needle where this function looks, as a collation matches it.
     *
     * @param collation the collation in force
     * @param text      the text searched
     * @param needle    the text looked for
     * @return whether it is there
     */
    protected abstract boolean holdsUnder(final CollationSpec collation, final String text, final String needle);

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) {
            return null;
        }
        return holds(SharedFunctionHelpers.textOf(args.get(0)), SharedFunctionHelpers.textOf(args.get(1)));
    }

    @Override
    public Object evaluate(final List<Object> args, final CollationSpec collation) {
        if (collation == null || args.get(0) == null || args.get(1) == null) {
            return evaluate(args);
        }
        return holdsUnder(collation, SharedFunctionHelpers.textOf(args.get(0)),
            SharedFunctionHelpers.textOf(args.get(1)));
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
