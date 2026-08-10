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
 * COMPLETE(model, prompt) — the model's reply as plain text. The model argument names an Ollama model
 * rather than a Snowflake-hosted one, so a prompt written for Snowflake runs unchanged once the local
 * name matches; an unknown name is the model server's error, not a silent fallback.
 */
public class CortexComplete extends BuiltInFunction {

    public CortexComplete(final String name) {
        super(name, StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String model = CortexText.text(args.get(0));
        final String prompt = args.size() > 1 ? CortexText.text(args.get(1)) : null;
        if (model == null || prompt == null) {
            return null;
        }
        return OllamaClient.generate(model, prompt, null, null);
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 3; }
}
