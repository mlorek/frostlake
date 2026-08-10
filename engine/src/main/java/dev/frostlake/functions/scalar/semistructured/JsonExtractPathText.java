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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.scalar.JsonTypeHelper;
import dev.frostlake.types.StringType;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * JSON_EXTRACT_PATH_TEXT(json, path) — parses the JSON text and returns the path's value as unquoted
 * text.
 *
 * <p>The first argument is JSON TEXT or a VARIANT; a statically OBJECT- or ARRAY-typed value is an
 * argument-type error, not a document — live refuses
 * {@code JSON_EXTRACT_PATH_TEXT(OBJECT_CONSTRUCT('a', 1), 'a')} at compile time while the same call
 * over a VARIANT, over a string, and even over a number returns a value.
 */
public class JsonExtractPathText extends BuiltInFunction {
    public JsonExtractPathText() { super("JSON_EXTRACT_PATH_TEXT", StringType.VARCHAR); }

    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return position == 0 ? SemiStructuredRejection.ARGUMENT_TYPES : SemiStructuredRejection.NONE;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        JsonNode node = JsonTypeHelper.parse(args.get(0));
        if (node == null) return null;
        for (final String rawStep : args.get(1).toString().split("\\.")) {
            if (node == null) return null;
            String step = rawStep;
            while (step.contains("[")) {
                final int open = step.indexOf('[');
                final String head = step.substring(0, open);
                if (!head.isEmpty()) {
                    node = node.get(head);
                    if (node == null) return null;
                }
                final int close = step.indexOf(']', open);
                node = node.get(Integer.parseInt(step.substring(open + 1, close)));
                step = step.substring(close + 1);
            }
            if (!step.isEmpty()) {
                node = node == null ? null : node.get(step);
            }
        }
        if (node == null || node.isNull()) return null;
        return node.isTextual() ? node.asText() : node.toString();
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
