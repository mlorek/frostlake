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

package dev.frostlake.metastore.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The value of one property of an object whose properties are kept as written rather than as fields: an
 * integration's or an external volume's. A scalar keeps its text; a list its items; a nested property list or a
 * string-pair map its entries in the order they were written.
 */
public final class PropertyValue {

    private final PropertyValueKind kind;
    private final String text;
    private final List<PropertyValue> items;
    private final Map<String, PropertyValue> entries;
    private String written;

    private PropertyValue(final PropertyValueKind kind, final String text, final List<PropertyValue> items,
                          final Map<String, PropertyValue> entries) {
        this.kind = kind;
        this.text = text;
        this.items = items;
        this.entries = entries;
    }

    /** A string value. */
    public static PropertyValue text(final String value) {
        return new PropertyValue(PropertyValueKind.TEXT, value, null, null);
    }

    /** An integer value, as written. */
    public static PropertyValue number(final String value) {
        return new PropertyValue(PropertyValueKind.NUMBER, value, null, null);
    }

    /** TRUE or FALSE. */
    public static PropertyValue bool(final boolean value) {
        return new PropertyValue(PropertyValueKind.BOOLEAN, value ? "TRUE" : "FALSE", null, null);
    }

    /** A bare word or a name. */
    public static PropertyValue word(final String value) {
        return new PropertyValue(PropertyValueKind.WORD, value, null, null);
    }

    /** A list of values. */
    public static PropertyValue list(final List<PropertyValue> values) {
        return new PropertyValue(PropertyValueKind.LIST, null, new ArrayList<>(values), null);
    }

    /** Nested properties, keyed by their upper-case names. */
    public static PropertyValue properties(final Map<String, PropertyValue> values) {
        return new PropertyValue(PropertyValueKind.PROPERTIES, null, null, new LinkedHashMap<>(values));
    }

    /** String pairs, keyed as written. */
    public static PropertyValue pairs(final Map<String, PropertyValue> values) {
        return new PropertyValue(PropertyValueKind.PAIRS, null, null, new LinkedHashMap<>(values));
    }

    /** The value as the statement wrote it, or its DESCRIBE text when it was not written by a statement. */
    public String getWritten() {
        return written != null ? written : describe();
    }

    /** Records how the statement wrote the value; answers the value itself. */
    public PropertyValue writtenAs(final String source) {
        this.written = source;
        return this;
    }

    /** What the value holds. */
    public PropertyValueKind getKind() {
        return kind;
    }

    /** Whether the value is a single literal or word. */
    public boolean isScalar() {
        return text != null;
    }

    /** A scalar's text, or null for a list or a property list. */
    public String getText() {
        return text;
    }

    /** A list's items; a scalar reads as a list of itself, anything else as an empty list. */
    public List<PropertyValue> getItems() {
        if (items != null) {
            return Collections.unmodifiableList(items);
        }
        if (text != null) {
            return Collections.singletonList(this);
        }
        return Collections.emptyList();
    }

    /** The nested entries of a property list or a pair map; empty for anything else. */
    public Map<String, PropertyValue> getEntries() {
        return entries == null ? Collections.<String, PropertyValue>emptyMap() : Collections.unmodifiableMap(entries);
    }

    /** One nested entry, or null. */
    public PropertyValue entry(final String key) {
        return entries == null ? null : entries.get(key);
    }

    /** The texts of a list's scalar items (a scalar reads as a list of one). */
    public List<String> strings() {
        final List<String> out = new ArrayList<>();
        for (final PropertyValue item : getItems()) {
            if (item.text != null) {
                out.add(item.text);
            }
        }
        return out;
    }

    /** The value read as a flag: TRUE or FALSE in any case, or null. */
    public Boolean flag() {
        if (text == null) {
            return null;
        }
        final String upper = text.toUpperCase(Locale.ROOT);
        if ("TRUE".equals(upper)) {
            return Boolean.TRUE;
        }
        if ("FALSE".equals(upper)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * The value as DESCRIBE shows it: a flag in lower case, a list comma-joined, a property list or a pair map as
     * a JSON object, and every other scalar as written.
     */
    public String describe() {
        if (kind == PropertyValueKind.BOOLEAN) {
            return text.toLowerCase(Locale.ROOT);
        }
        if (text != null) {
            return text;
        }
        if (items != null) {
            return String.join(",", strings());
        }
        return json();
    }

    /** The value as JSON: scalars as strings (flags and numbers bare), lists as arrays, entries as objects. */
    public String json() {
        final StringBuilder out = new StringBuilder();
        appendJson(out);
        return out.toString();
    }

    private void appendJson(final StringBuilder out) {
        if (kind == PropertyValueKind.BOOLEAN) {
            out.append(text.toLowerCase(Locale.ROOT));
        } else if (kind == PropertyValueKind.NUMBER) {
            out.append(text);
        } else if (text != null) {
            appendJsonString(out, text);
        } else if (items != null) {
            out.append('[');
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                items.get(i).appendJson(out);
            }
            out.append(']');
        } else {
            out.append('{');
            int i = 0;
            for (final Map.Entry<String, PropertyValue> entry : entries.entrySet()) {
                if (i++ > 0) {
                    out.append(',');
                }
                appendJsonString(out, entry.getKey());
                out.append(':');
                entry.getValue().appendJson(out);
            }
            out.append('}');
        }
    }

    /** Appends a JSON string literal holding the text. */
    public static void appendJsonString(final StringBuilder out, final String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c < 0x20) {
                out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }
}
