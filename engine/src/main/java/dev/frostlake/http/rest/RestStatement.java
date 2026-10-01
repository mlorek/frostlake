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

package dev.frostlake.http.rest;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * The text of one statement a REST request translates into, assembled from a fixed head and the properties a
 * request body sets: {@code KEYWORD = value} for each property present, each value written as the SQL literal
 * its type calls for. Values are never spliced in as written — text becomes a string literal, a name a quoted
 * identifier, a number or a flag is checked before it is written.
 */
public final class RestStatement {

    private final StringBuilder text;
    private int properties;

    /** A statement starting with the given text. */
    public RestStatement(final String head) {
        this.text = new StringBuilder(head);
    }

    /** Appends text as written; the caller vouches for it. */
    public RestStatement append(final String sql) {
        text.append(sql);
        return this;
    }

    /** {@code KEYWORD = 'text'} when the body sets the property. */
    public RestStatement string(final JsonNode body, final String property, final String keyword) {
        final String value = RestJson.text(body, property);
        if (value != null) {
            property(keyword, RestSql.literal(value));
        }
        return this;
    }

    /** {@code KEYWORD = n} when the body sets the property. */
    public RestStatement integer(final JsonNode body, final String property, final String keyword) {
        final Long value = RestJson.integer(body, property);
        if (value != null) {
            property(keyword, value.toString());
        }
        return this;
    }

    /** {@code KEYWORD = TRUE|FALSE} when the body sets the property. */
    public RestStatement bool(final JsonNode body, final String property, final String keyword) {
        final Boolean value = RestJson.bool(body, property);
        if (value != null) {
            property(keyword, value.booleanValue() ? "TRUE" : "FALSE");
        }
        return this;
    }

    /** {@code KEYWORD = "NAME"} when the body sets the property to an object name. */
    public RestStatement identifier(final JsonNode body, final String property, final String keyword) {
        final RestIdentifier value = RestJson.identifier(body, property, false);
        if (value != null) {
            property(keyword, value.sql());
        }
        return this;
    }

    /** {@code KEYWORD = ('a', 'b')} when the body sets the property to an array of strings. */
    public RestStatement stringList(final JsonNode body, final String property, final String keyword) {
        final List<String> values = RestJson.strings(body, property);
        if (values != null) {
            final StringBuilder list = new StringBuilder("(");
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) {
                    list.append(", ");
                }
                list.append(RestSql.literal(values.get(i)));
            }
            property(keyword, list.append(')').toString());
        }
        return this;
    }

    /** {@code KEYWORD = value}, the value already rendered as SQL. */
    public RestStatement property(final String keyword, final String sqlValue) {
        text.append(' ').append(keyword).append(" = ").append(sqlValue);
        properties++;
        return this;
    }

    /** How many properties have been appended. */
    public int propertyCount() {
        return properties;
    }

    /** The statement text. */
    @Override
    public String toString() {
        return text.toString();
    }
}
