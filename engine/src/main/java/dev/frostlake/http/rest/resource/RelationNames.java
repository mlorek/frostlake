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

package dev.frostlake.http.rest.resource;

import dev.frostlake.http.rest.RestCall;
import dev.frostlake.http.rest.RestException;
import dev.frostlake.http.rest.RestIdentifier;
import dev.frostlake.parser.FrostlakeLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * Names and definitions of the schema-level relations the table, view, dynamic table and sequence endpoints
 * serve: a target placed by the {@code targetDatabase}/{@code targetSchema} query parameters, a possibly
 * qualified name the API passes as text, the qualified name {@code TAG_REFERENCES} reads, and the defining
 * query a SHOW listing's {@code text} carries.
 */
final class RelationNames {

    /** Static helpers only. */
    private RelationNames() {
    }

    /**
     * The object's qualified name — database, schema and name, each as the API spells it — as {@code TAG_REFERENCES}
     * reads it and as a does-not-exist sentence names a schema object.
     */
    static String qualifiedDisplay(final RestCall call, final RestIdentifier name) {
        return RestIdentifier.display(call.identifier("database").name()) + "."
            + RestIdentifier.display(call.identifier("schema").name()) + "." + RestIdentifier.display(name.name());
    }

    /**
     * A name placed by the {@code targetDatabase} and {@code targetSchema} query parameters, each defaulting to
     * the path's database and schema, as a qualified SQL name.
     */
    static String placed(final RestCall call, final RestIdentifier name) {
        final String database = call.query("targetDatabase");
        final String schema = call.query("targetSchema");
        final String databaseSql = database == null || database.isEmpty() ? call.databaseSql()
            : RestIdentifier.parse(database, "targetDatabase").sql();
        final String schemaSql = schema == null || schema.isEmpty() ? call.identifier("schema").sql()
            : RestIdentifier.parse(schema, "targetSchema").sql();
        return databaseSql + "." + schemaSql + "." + name.sql();
    }

    /**
     * A name the API passes as text that may be qualified — {@code name}, {@code schema.name} or
     * {@code database.schema.name}, each part an identifier, a double-quoted part keeping its dots — as a
     * qualified SQL name, the path's database and schema supplying the parts it leaves out.
     *
     * @throws RestException {@code 400} when the text is missing or is not such a name
     */
    static String qualified(final RestCall call, final String text, final String what) {
        if (text == null || text.isEmpty()) {
            throw RestException.badRequest("Missing required parameter '" + what + "'.");
        }
        final List<RestIdentifier> parts = new ArrayList<>();
        final StringBuilder part = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            }
            if (c == '.' && !quoted) {
                parts.add(RestIdentifier.parse(part.toString(), what));
                part.setLength(0);
            } else {
                part.append(c);
            }
        }
        parts.add(RestIdentifier.parse(part.toString(), what));
        if (parts.size() > 3) {
            throw RestException.badRequest("Invalid identifier for " + what + ": '" + text + "'.");
        }
        final String database = parts.size() == 3 ? parts.get(0).sql() : call.databaseSql();
        final String schema = parts.size() >= 2 ? parts.get(parts.size() - 2).sql() : call.identifier("schema").sql();
        return database + "." + schema + "." + parts.get(parts.size() - 1).sql();
    }

    /**
     * The defining query of a relation, read from the CREATE text a SHOW listing reports: what follows the
     * first {@code AS} outside parentheses. Null when the text has none.
     */
    static String definingQuery(final String createText) {
        if (createText == null || createText.isEmpty()) {
            return null;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(createText));
        lexer.removeErrorListeners();
        int depth = 0;
        for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
            final int type = token.getType();
            if (type == FrostlakeLexer.LPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.RPAREN) {
                depth--;
            } else if (type == FrostlakeLexer.AS && depth == 0) {
                final String query = createText.substring(token.getStopIndex() + 1).trim();
                return query.isEmpty() ? null : query;
            }
        }
        return null;
    }

    /** Whether the CREATE text carries the given keyword before the object kind's keyword, e.g. TRANSIENT before DYNAMIC. */
    static boolean declares(final String createText, final int keyword, final int kindKeyword) {
        if (createText == null || createText.isEmpty()) {
            return false;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(createText));
        lexer.removeErrorListeners();
        for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
            if (token.getType() == keyword) {
                return true;
            }
            if (token.getType() == kindKeyword) {
                return false;
            }
        }
        return false;
    }
}
