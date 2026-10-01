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

package dev.frostlake.values;

import tools.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Map;

/**
 * Renders a semi-structured value the way Snowflake displays one: indented by JSON_INDENT spaces,
 * compact at zero. An empty array or object stays on one line either way. The indented form is
 * byte-identical to a real account's at the same width.
 *
 * <p>The width is per session, so it is bound to the calling thread for the duration of a statement
 * — the same shape the catalog and transaction manager use for their session scopes. Nothing else
 * about the value changes: equality, ordering and hashing all stay on the canonical compact text.
 */
public final class VariantJsonFormat {

    /**
     * The width this engine uses when a session has not set one. Snowflake's own JSON_INDENT
     * default is 2; this engine's canonical rendering is COMPACT, and the live-comparison harness
     * pins the server to JSON_INDENT = 0 so both sides emit the same text. Setting the parameter
     * gives live's rendering at any width — only the unset default differs.
     */
    public static final int DEFAULT_INDENT = 0;

    /** JSON_INDENT for the calling thread, when a session has bound one. */
    private static final ThreadLocal<Integer> BOUND_INDENT = new ThreadLocal<>();

    private VariantJsonFormat() {
    }

    /** Bind this session's JSON_INDENT to the calling thread for one statement. */
    public static void beginSessionScope(final int indent) {
        BOUND_INDENT.set(indent);
    }

    /** Drop this thread's JSON_INDENT binding. Idempotent. */
    public static void clearSessionScope() {
        BOUND_INDENT.remove();
    }

    /** The width in force: the session's when one is bound, else Snowflake's default. */
    public static int indent() {
        final Integer bound = BOUND_INDENT.get();
        return bound != null ? bound : DEFAULT_INDENT;
    }

    /**
     * {@code node} rendered at the width in force. {@code compactText}, the value's compact display
     * text, is returned unchanged at width zero, so the common case costs nothing.
     */
    public static String render(final JsonNode node, final String compactText) {
        final int indent = indent();
        if (indent <= 0 || node == null || !(node.isArray() || node.isObject())) {
            return compactText;
        }
        final StringBuilder out = new StringBuilder();
        write(node, indent, 0, out);
        return out.toString();
    }

    /**
     * {@code node} rendered at the given width, whatever width the session is at: a scalar as its JSON text, an
     * array or object across lines.
     *
     * @param node   the value
     * @param indent the spaces per level
     * @return the rendered text
     */
    public static String indented(final JsonNode node, final int indent) {
        final StringBuilder out = new StringBuilder();
        write(node, indent, 0, out);
        return out.toString();
    }

    private static void write(final JsonNode node, final int indent, final int depth,
                              final StringBuilder out) {
        if (node.isArray()) {
            writeArray(node, indent, depth, out);
        } else if (node.isObject()) {
            writeObject(node, indent, depth, out);
        } else {
            // A DOUBLE takes the account's fifteen-decimal form at every width, as it does compact.
            out.append(VariantJsonText.isRewritableDouble(node)
                ? VariantJsonText.doubleText(node.doubleValue()) : node.toString());
        }
    }

    private static void writeArray(final JsonNode node, final int indent, final int depth,
                                   final StringBuilder out) {
        if (node.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append("[\n");
        for (int i = 0; i < node.size(); i++) {
            pad(out, indent, depth + 1);
            write(node.get(i), indent, depth + 1, out);
            out.append(i < node.size() - 1 ? ",\n" : "\n");
        }
        pad(out, indent, depth);
        out.append(']');
    }

    private static void writeObject(final JsonNode node, final int indent, final int depth,
                                    final StringBuilder out) {
        if (node.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        final Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            pad(out, indent, depth + 1);
            out.append('"').append(field.getKey()).append("\": ");
            write(field.getValue(), indent, depth + 1, out);
            out.append(fields.hasNext() ? ",\n" : "\n");
        }
        pad(out, indent, depth);
        out.append('}');
    }

    private static void pad(final StringBuilder out, final int indent, final int depth) {
        for (int i = 0; i < indent * depth; i++) {
            out.append(' ');
        }
    }
}
