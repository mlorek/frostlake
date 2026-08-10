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
 * SENTIMENT(text) — a score from -1 (negative) to 1 (positive). The model is asked for the number
 * alone and the reply is read for its first, so a chattier model still yields a score; anything
 * unreadable settles at neutral rather than failing the row.
 */
public class CortexSentiment extends BuiltInFunction {

    public CortexSentiment(final String name) {
        super(name, NumericType.DOUBLE);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String subject = CortexText.text(args.get(0));
        if (subject == null) {
            return null;
        }
        final String reply = OllamaClient.generate(OllamaConfig.model(),
            "Rate the sentiment of the text below as a single number between -1.0 (very negative)"
            + " and 1.0 (very positive). Reply with the number only.\n\n" + subject,
            Double.valueOf(0.0), null);
        final double score = CortexText.firstNumber(reply, 0.0);
        return Double.valueOf(Math.max(-1.0, Math.min(1.0, score)));
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
