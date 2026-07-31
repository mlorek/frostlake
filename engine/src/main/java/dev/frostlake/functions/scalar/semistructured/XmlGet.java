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
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.ObjectType;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.XmlVariants;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * XMLGET(xml, tag_name [, instance]) — the {@code instance}-th (0-based, default 0) direct child
 * element with the given tag name, taken from the XML element's {@code "$"} content; SQL NULL when
 * the value is not an XML element, the tag does not occur (matching is case-sensitive), or the
 * instance is out of range (a negative instance is NULL too, live-verified). A VARCHAR first
 * argument is rejected at compile time, as in Snowflake.
 */
public class XmlGet extends BuiltInFunction {

    public XmlGet() { super("XMLGET", ObjectType.OBJECT); }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object value = args.get(0);
        final Object tag = args.get(1);
        if (value == null || tag == null) return null;
        if (args.size() > 2 && args.get(2) == null) return null;
        final JsonNode node = ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value);
        if (!XmlVariants.isXmlElement(node)) {
            return null;
        }
        final int instance;
        if (args.size() > 2) {
            if (!(args.get(2) instanceof Number)) return null;
            instance = ((Number) args.get(2)).intValue();
        } else {
            instance = 0;
        }
        if (instance < 0) {
            return null;
        }
        final List<JsonNode> matches = new ArrayList<JsonNode>();
        final JsonNode content = node.get("$");
        if (content.isArray()) {
            for (final JsonNode piece : content) {
                collectMatch(piece, tag.toString(), matches);
            }
        } else {
            collectMatch(content, tag.toString(), matches);
        }
        if (instance >= matches.size()) {
            return null;
        }
        return VariantValue.ofNode(matches.get(instance));
    }

    private static void collectMatch(final JsonNode piece, final String tag, final List<JsonNode> matches) {
        if (XmlVariants.isXmlElement(piece) && piece.get("@").asText().equals(tag)) {
            matches.add(piece);
        }
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 3; }
}
