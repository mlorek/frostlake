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

package dev.frostlake.executor.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the lists a service specification declares — its {@code endpoints} and its {@code serviceRoles} — out of
 * the specification's YAML text, in the block style the specification reference writes. Each list item becomes the
 * map of its scalar properties; nested blocks and flow-style items are passed over.
 */
public final class ServiceSpecReader {

    /** Static helpers only. */
    private ServiceSpecReader() {
    }

    /**
     * The items of the block list under a key.
     *
     * @param yaml the specification text, or null
     * @param key the list's key, such as {@code endpoints}
     * @return each item's scalar properties, in order; empty when the text declares no such list
     */
    public static List<Map<String, String>> entries(final String yaml, final String key) {
        final List<Map<String, String>> items = new ArrayList<>();
        if (yaml == null) {
            return items;
        }
        final String[] lines = yaml.replace("\r", "").split("\n");
        int i = 0;
        int keyIndent = -1;
        while (i < lines.length && keyIndent < 0) {
            final String line = stripComment(lines[i]);
            if (line.trim().equals(key + ":")) {
                keyIndent = indent(line);
            }
            i++;
        }
        if (keyIndent < 0) {
            return items;
        }
        Map<String, String> current = null;
        int fieldIndent = -1;
        for (; i < lines.length; i++) {
            final String line = stripComment(lines[i]);
            final String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            final int indent = indent(line);
            final boolean item = trimmed.startsWith("- ") || "-".equals(trimmed);
            if (indent < keyIndent || indent == keyIndent && !item) {
                break;
            }
            if (item) {
                current = new LinkedHashMap<>();
                items.add(current);
                fieldIndent = indent + 2;
                field(current, trimmed.substring(1).trim());
            } else if (current != null && indent == fieldIndent) {
                field(current, trimmed);
            }
        }
        return items;
    }

    private static void field(final Map<String, String> item, final String text) {
        final int colon = text.indexOf(':');
        if (colon <= 0) {
            return;
        }
        final String value = unquote(text.substring(colon + 1).trim());
        if (!value.isEmpty()) {
            item.put(text.substring(0, colon).trim(), value);
        }
    }

    private static String unquote(final String value) {
        if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'')
                && value.charAt(value.length() - 1) == value.charAt(0)) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String stripComment(final String line) {
        final int hash = line.indexOf(" #");
        return hash >= 0 ? line.substring(0, hash) : line.startsWith("#") ? "" : line;
    }

    private static int indent(final String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }
}
