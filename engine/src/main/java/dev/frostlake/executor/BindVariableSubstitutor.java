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

package dev.frostlake.executor;

import dev.frostlake.parser.FrostlakeLexer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.List;
import java.util.Map;

/**
 * Substitutes procedural scripting variables into raw SQL text before it is parsed/executed:
 * record-field references ({@code rec.col} / {@code rec."col"}) and colon-prefixed bind variables
 * ({@code :var}). Holds a live reference to the procedural variable map.
 *
 * <p>The substitution is driven by the SQL <em>lexer</em>, not hand-rolled character scanning: the SQL
 * is tokenized, and matches are recognized as token sequences ({@code IDENTIFIER '.' IDENTIFIER} for a
 * record field, {@code ':' IDENTIFIER} for a bind variable). Because the lexer classifies the text,
 * a name that appears inside a {@link FrostlakeLexer#STRING_LITERAL} or a {@link FrostlakeLexer#QUOTED_IDENTIFIER}
 * is never mistaken for a reference, and {@code ::} (cast) / {@code :=} (assignment) — their own tokens
 * — are never confused with a {@code :var}. The grammar's tokenizer stays the single source of truth
 * about the SQL's lexical structure.
 */
public class BindVariableSubstitutor {

    private final Map<String, Object> variables;

    public BindVariableSubstitutor(final Map<String, Object> variables) {
        this.variables = variables;
    }

    /** Substitute record-field refs and colon bind variables into the SQL. */
    public String substitute(final String sql) {
        // Nothing to do unless the text can contain a bind variable (':') or a record field ('.').
        if (sql == null || (sql.indexOf(':') < 0 && sql.indexOf('.') < 0)) {
            return sql;
        }

        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> toks = stream.getTokens();   // structural tokens only (WS / comments are skipped)

        final StringBuilder out = new StringBuilder(sql.length());
        int cursor = 0;
        for (int i = 0; i < toks.size(); i++) {
            final Token t = toks.get(i);
            if (t.getType() == Token.EOF) {
                break;
            }

            // :name — a COLON immediately followed by an identifier is a bind variable. An unknown name
            // resolves to NULL, matching the historical behavior of the character-scanning version.
            if (t.getType() == FrostlakeLexer.COLON && i + 1 < toks.size()) {
                final Token nameTok = toks.get(i + 1);
                if (SqlTokens.isWord(nameTok)
                        && nameTok.getStartIndex() == t.getStopIndex() + 1) {
                    out.append(sql, cursor, t.getStartIndex());
                    out.append(toLiteral(variables.get(nameTok.getText().toUpperCase())));
                    cursor = nameTok.getStopIndex() + 1;
                    i++;   // consumed the identifier too
                    continue;
                }
            }

            // rec.col / rec."col" — a qualified reference that resolves to a record-field variable.
            if (SqlTokens.isWord(t) && i + 2 < toks.size()
                    && toks.get(i + 1).getType() == FrostlakeLexer.DOT) {
                final Token dot = toks.get(i + 1);
                final Token colTok = toks.get(i + 2);
                final int colType = colTok.getType();
                if ((SqlTokens.isWord(colTok) || colType == FrostlakeLexer.QUOTED_IDENTIFIER)
                        && dot.getStartIndex() == t.getStopIndex() + 1
                        && colTok.getStartIndex() == dot.getStopIndex() + 1) {
                    final String colName = colType == FrostlakeLexer.QUOTED_IDENTIFIER
                        ? unquoteIdentifier(colTok.getText()) : colTok.getText();
                    final String key = (t.getText() + "." + colName).toUpperCase();
                    if (variables.containsKey(key)) {
                        out.append(sql, cursor, t.getStartIndex());
                        out.append(toLiteral(variables.get(key)));
                        cursor = colTok.getStopIndex() + 1;
                        i += 2;   // consumed the dot and the column token
                        continue;
                    }
                }
            }
        }
        out.append(sql, cursor, sql.length());
        return out.toString();
    }

    /** Render a variable value as a SQL literal: NULL, or a single-quoted string with quotes escaped. */
    private static String toLiteral(final Object value) {
        if (value == null) {
            return "NULL";
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }

    /** Strip the surrounding double quotes from a QUOTED_IDENTIFIER token, unescaping "" to ". */
    private static String unquoteIdentifier(final String quoted) {
        if (quoted.length() >= 2 && quoted.startsWith("\"") && quoted.endsWith("\"")) {
            return quoted.substring(1, quoted.length() - 1).replace("\"\"", "\"");
        }
        return quoted;
    }
}
