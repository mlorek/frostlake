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
import dev.frostlake.functions.CollationMatching;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.StringType;

import java.util.List;

public class Replace extends TextArgumentFunction {
    public Replace() {
        super("REPLACE", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String str = args.get(0).toString();
        final String search = args.get(1) != null ? args.get(1).toString() : "";
        final String replacement = args.size() > 2 && args.get(2) != null ? args.get(2).toString() : "";
        return str.replace(search, replacement);
    }

    /** Under a collation every substring the collation calls equal to the search text is replaced. */
    @Override
    public Object evaluate(final List<Object> args, final CollationSpec collation) {
        if (collation == null || args.get(0) == null) {
            return evaluate(args);
        }
        return CollationMatching.replaceUnder(collation, args.get(0).toString(),
            args.get(1) != null ? args.get(1).toString() : "",
            args.size() > 2 && args.get(2) != null ? args.get(2).toString() : "");
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
