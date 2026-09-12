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

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.BooleanType;

import java.util.List;

/**
 * REGEXP_LIKE(subject, pattern [, parameters]) — TRUE when {@code pattern} matches the ENTIRE subject
 * (implicitly anchored, as in Snowflake). The optional {@code parameters} string supplies i/m/s flags.
 * NULL subject or pattern yields NULL; an invalid pattern is an error.
 */
public class RegexpLike extends TextArgumentFunction {
    public RegexpLike() { super("REGEXP_LIKE", new BooleanType()); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) {
            return null;
        }
        final String subject = args.get(0).toString();
        final String pattern = args.get(1).toString();
        final String parameters = args.size() > 2 && args.get(2) != null ? args.get(2).toString() : null;
        return RegexpHelper.compile(pattern, parameters).matcher(subject).matches();
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }

    /** A BINARY is no text here: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
