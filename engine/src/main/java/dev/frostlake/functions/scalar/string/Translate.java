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
import dev.frostlake.types.StringType;
import dev.frostlake.values.CodePointText;

import java.util.List;

public class Translate extends TextArgumentFunction {
    public Translate() { super("TRANSLATE", StringType.VARCHAR); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String str = args.get(0).toString();
        final String from = args.get(1) == null ? "" : args.get(1).toString();
        final String to   = args.get(2) == null ? "" : args.get(2).toString();
        // Character by character, a supplementary one included: TRANSLATE('a😀b', 'ab', '😀') is 😀😀.
        final int[] fromPoints = CodePointText.codePoints(from);
        final int[] toPoints = CodePointText.codePoints(to);
        final StringBuilder result = new StringBuilder();
        for (final int point : CodePointText.codePoints(str)) {
            int idx = -1;
            for (int i = 0; i < fromPoints.length && idx < 0; i++) {
                if (fromPoints[i] == point) {
                    idx = i;
                }
            }
            if (idx < 0) {
                result.appendCodePoint(point);
            } else if (idx < toPoints.length) {
                result.appendCodePoint(toPoints[idx]);
            }
        }
        return result.toString();
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }

    /** A BINARY is no text here: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
