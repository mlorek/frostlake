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

package dev.frostlake.task;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A task graph's configuration: the JSON object a root task's {@code CONFIG} holds, merged with the one an
 * {@code EXECUTE TASK … USING CONFIG} gives for a single run, and read inside the graph's tasks through
 * {@code SYSTEM$GET_TASK_GRAPH_CONFIG}.
 *
 * <p>The function answers text: with no path the whole configuration as compact JSON, keys in the order they
 * were written; with a path the value it names — a string as its content, a number or a boolean as written, an
 * object or an array as compact JSON — and NULL when the path names nothing.
 */
public final class TaskGraphConfig {

    /** The refusal for a configuration that is not a JSON object, the same for CREATE, ALTER and EXECUTE TASK. */
    public static final String INVALID_CONFIG = "Invalid config. Must be a string representation of a valid JSON Object.";

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private TaskGraphConfig() {
    }

    /**
     * Refuse a configuration that is not a JSON object.
     *
     * @param text the configuration as written
     * @throws RuntimeException with the account's sentence when it is not one
     */
    public static void requireObject(final String text) {
        if (!isObject(text)) {
            throw new RuntimeException(INVALID_CONFIG);
        }
    }

    /**
     * Whether the text is a JSON object.
     *
     * @param text the text
     * @return whether it parses as one
     */
    public static boolean isObject(final String text) {
        return parseObject(text) != null;
    }

    /**
     * The configuration a run reads: the task's own with the run's merged over it, field by field — a field
     * both hold takes the run's value, an object both hold is merged the same way, and a field only one holds
     * is kept.
     *
     * @param configured the task's CONFIG, or null
     * @param override   the run's USING CONFIG, or null
     * @return the merged configuration as compact JSON, or null when neither is set
     */
    public static String merge(final String configured, final String override) {
        final ObjectNode base = parseObject(configured);
        final ObjectNode extra = parseObject(override);
        if (base == null) {
            return extra == null ? null : MAPPER.writeValueAsString(extra);
        }
        if (extra != null) {
            mergeInto(base, extra);
        }
        return MAPPER.writeValueAsString(base);
    }

    private static void mergeInto(final ObjectNode target, final ObjectNode source) {
        for (final Map.Entry<String, JsonNode> field : source.properties()) {
            final JsonNode existing = target.get(field.getKey());
            if (existing != null && existing.isObject() && field.getValue().isObject()) {
                mergeInto((ObjectNode) existing, (ObjectNode) field.getValue());
            } else {
                target.set(field.getKey(), field.getValue());
            }
        }
    }

    /**
     * {@code SYSTEM$GET_TASK_GRAPH_CONFIG} as evaluated: the configuration of the graph run executing on this
     * thread, refused outside a task in the account's words. A string argument may arrive still quoted.
     *
     * @param args the function's arguments
     * @return the configuration or the value it names, as text, or null
     */
    public static String readInTask(final List<Object> args) {
        if (TaskScheduler.currentTask() == null) {
            throw new RuntimeException("Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task.");
        }
        final List<Object> paths = new ArrayList<>();
        for (final Object arg : args) {
            final String text = arg == null ? null : String.valueOf(arg);
            paths.add(text != null && text.length() >= 2 && text.startsWith("'") && text.endsWith("'")
                ? text.substring(1, text.length() - 1) : text);
        }
        return read(TaskScheduler.currentGraphConfig(), paths);
    }

    /**
     * What {@code SYSTEM$GET_TASK_GRAPH_CONFIG} answers inside a task.
     *
     * @param config the run's configuration, or null when the graph has none
     * @param args   the function's arguments: none, or the path of the value to read
     * @return the configuration or the value, as text, or null
     */
    public static String read(final String config, final List<Object> args) {
        final ObjectNode root = parseObject(config);
        if (root == null) {
            return null;
        }
        if (args.isEmpty() || args.get(0) == null) {
            return args.isEmpty() ? MAPPER.writeValueAsString(root) : null;
        }
        final JsonNode found = find(root, String.valueOf(args.get(0)));
        if (found == null || found.isNull() || found.isMissingNode()) {
            return null;
        }
        if (found.isString()) {
            return found.asString();
        }
        if (found.isContainer()) {
            return MAPPER.writeValueAsString(found);
        }
        return found.toString();
    }

    /** The node a path names: dot-separated keys, each optionally followed by {@code [n]} indexes. */
    private static JsonNode find(final JsonNode root, final String path) {
        JsonNode node = root;
        final StringBuilder key = new StringBuilder();
        int i = 0;
        while (i <= path.length()) {
            final char c = i < path.length() ? path.charAt(i) : '.';
            if (c == '.' || c == '[') {
                if (key.length() > 0) {
                    node = node == null ? null : node.get(key.toString());
                    key.setLength(0);
                }
                if (c == '[') {
                    final int close = path.indexOf(']', i);
                    if (close < 0) {
                        return null;
                    }
                    final String index = path.substring(i + 1, close).trim();
                    node = step(node, index);
                    i = close + 1;
                    continue;
                }
            } else {
                key.append(c);
            }
            i++;
        }
        return node;
    }

    private static JsonNode step(final JsonNode node, final String index) {
        if (node == null) {
            return null;
        }
        if (index.length() >= 2 && (index.charAt(0) == '\'' || index.charAt(0) == '"')) {
            return node.get(index.substring(1, index.length() - 1));
        }
        try {
            return node.get(Integer.parseInt(index));
        } catch (final NumberFormatException notIndex) {
            return null;
        }
    }

    private static ObjectNode parseObject(final String text) {
        if (text == null) {
            return null;
        }
        try {
            final JsonNode node = MAPPER.readTree(text);
            return node != null && node.isObject() ? (ObjectNode) node : null;
        } catch (final JacksonException notJson) {
            return null;
        }
    }
}
