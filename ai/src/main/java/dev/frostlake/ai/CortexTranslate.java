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
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * TRANSLATE(text, from, to) — the text in the target language. An empty source language means "work
 * it out", which is how Snowflake reads the empty string there.
 */
public class CortexTranslate extends BuiltInFunction {

    public CortexTranslate(final String name) {
        super(name, StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String subject = CortexText.text(args.get(0));
        final String from = args.size() > 1 ? CortexText.text(args.get(1)) : null;
        final String to = args.size() > 2 ? CortexText.text(args.get(2)) : null;
        if (subject == null || to == null) {
            return null;
        }
        final String source = from == null || from.isEmpty() ? "the source language" : from;
        return OllamaClient.generate(OllamaConfig.model(),
            "Translate the text below from " + source + " to " + to
            + ". Reply with the translation only.\n\n" + subject,
            Double.valueOf(0.0), null).trim();
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }
}
