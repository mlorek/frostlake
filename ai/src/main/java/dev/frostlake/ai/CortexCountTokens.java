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

package dev.frostlake.ai;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * COUNT_TOKENS(model, text) — how many tokens the text costs. Counted here rather than asked of the
 * model: a whitespace-and-punctuation split is deterministic and free, and callers use this to size
 * a prompt, not to bill for one.
 */
public class CortexCountTokens extends BuiltInFunction {

    public CortexCountTokens(final String name) {
        super(name, NumericType.NUMBER);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String subject = args.size() > 1 ? CortexText.text(args.get(1)) : null;
        if (subject == null) {
            return null;
        }
        int tokens = 0;
        boolean inWord = false;
        for (int i = 0; i < subject.length(); i++) {
            final char ch = subject.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                if (!inWord) {
                    tokens++;
                    inWord = true;
                }
            } else {
                inWord = false;
                if (!Character.isWhitespace(ch)) {
                    tokens++;
                }
            }
        }
        return Long.valueOf(tokens);
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 2; }
}
