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

import dev.frostlake.functions.scalar.SharedFunctionHelpers;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

        // The variables named in a SELECT … INTO target list are ASSIGNMENT TARGETS, not values.
        final Set<Integer> intoTargets = intoTargetColonIndices(toks);

        final StringBuilder out = new StringBuilder(sql.length());
        int cursor = 0;
        for (int i = 0; i < toks.size(); i++) {
            final Token t = toks.get(i);
            if (t.getType() == Token.EOF) {
                break;
            }

            // :name — a COLON immediately followed by an identifier is a bind variable. An unknown name
            // resolves to NULL, matching the historical behavior of the character-scanning version.
            if (t.getType() == FrostlakeLexer.COLON && i + 1 < toks.size()
                    && !isVariantPathColon(toks, i) && !intoTargets.contains(i)) {
                final Token nameTok = toks.get(i + 1);
                if (SqlTokens.isWord(nameTok)
                        && nameTok.getStartIndex() == t.getStopIndex() + 1) {
                    rejectDottedBindVariable(toks, i + 1, nameTok);
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

    /**
     * A bind variable cannot name a field: {@code :rec.col} is rejected outright, as Snowflake does.
     *
     * <p>Live-verified on a real account inside a cursor FOR loop. NONE of the record-field
     * spellings work in embedded SQL there:
     * <pre>
     *   INSERT INTO t VALUES (:r.price)  -&gt; "syntax error line 4 at position 31 unexpected '.'"
     *   INSERT INTO t VALUES (r.price)   -&gt; "invalid identifier 'R.PRICE'"
     *   INSERT INTO t VALUES (r)         -&gt; "invalid identifier 'R'"
     *   INSERT INTO t VALUES (:r)        -&gt; "Bind variable :r not set."
     * </pre>
     * The supported idiom is to copy the field into a scalar variable and bind THAT — live, {@code pv :=
     * r.price;} followed by {@code INSERT INTO t VALUES (:pv)} works and sums to 30. Frostlake used to
     * substitute only the {@code :r} part, leaving {@code NULL.price} behind, so the documented-looking
     * form silently inserted NULL — the reason this is an error rather than a quiet fallback.
     */
    private void rejectDottedBindVariable(final List<Token> toks, final int nameIndex, final Token nameTok) {
        if (nameIndex + 1 >= toks.size()) {
            return;
        }
        final Token next = toks.get(nameIndex + 1);
        if (next.getType() != FrostlakeLexer.DOT || next.getStartIndex() != nameTok.getStopIndex() + 1) {
            return;
        }
        throw new RuntimeException("SQL compilation error:\nsyntax error unexpected '.'. "
            + "A bind variable cannot name a field (:" + nameTok.getText() + ".…); assign the field to a "
            + "variable first and bind that variable instead.");
    }

    /**
     * The token indices of the COLONs that introduce a {@code SELECT … INTO :v1, :v2} TARGET. Those names are
     * assignment targets, not values: substituting them produced {@code INTO '1', '2'} — a syntax error at the
     * literal ("mismatched input ''1''"). Only a {@code :name} run that directly follows the INTO keyword,
     * separated by commas, is a target list; {@code INSERT INTO t} is unaffected because the token after INTO
     * is an identifier rather than a colon.
     */
    private static Set<Integer> intoTargetColonIndices(final List<Token> toks) {
        final Set<Integer> targets = new HashSet<>();
        for (int i = 0; i < toks.size(); i++) {
            if (toks.get(i).getType() != FrostlakeLexer.INTO) {
                continue;
            }
            int j = i + 1;
            while (j + 1 < toks.size()
                    && toks.get(j).getType() == FrostlakeLexer.COLON
                    && SqlTokens.isWord(toks.get(j + 1))) {
                targets.add(j);
                j += 2;
                if (j < toks.size() && toks.get(j).getType() == FrostlakeLexer.COMMA) {
                    j++;
                } else {
                    break;
                }
            }
        }
        return targets;
    }

    /**
     * Whether the COLON at {@code colonIndex} is the semi-structured PATH operator ({@code src:key},
     * {@code src:a:b}) rather than a Scripting bind reference ({@code :var}). It is a path when it directly
     * follows something that can END a value expression — an identifier, a quoted identifier, {@code )},
     * {@code ]} or a string literal — with no space between. A bind reference never appears in that position
     * (it follows an operator, a comma, an opening bracket or a keyword). Without this test every path key was
     * substituted as an unknown bind variable, so {@code s.src:a:b} became the single identifier
     * {@code s.srcNULLNULL} — reported later as "Column not found: S.SRCNULLNULL".
     */
    private static boolean isVariantPathColon(final List<Token> toks, final int colonIndex) {
        if (colonIndex == 0) {
            return false;
        }
        final Token prev = toks.get(colonIndex - 1);
        final int type = prev.getType();
        // Any bare word ends a value — a path step may be a keyword token that the grammar
        // admits as an identifier (src:value:attributes:core), so the check must not be
        // limited to the IDENTIFIER token type.
        final boolean endsAValue = SqlTokens.isWord(prev)
            || type == FrostlakeLexer.QUOTED_IDENTIFIER
            || type == FrostlakeLexer.RPAREN
            || type == FrostlakeLexer.RBRACKET
            || type == FrostlakeLexer.STRING_LITERAL;
        return endsAValue && prev.getStopIndex() + 1 == toks.get(colonIndex).getStartIndex();
    }

    private static String toLiteral(final Object value) {
        if (value == null) {
            return "NULL";
        }
        // A BOOLEAN variable binds as a BOOLEAN literal — quoting it handed boolean positions a
        // non-empty STRING ('false'), so a FALSE flag behind IFF(:flag, …) took the TRUE branch.
        if (value instanceof Boolean) {
            return ((Boolean) value) ? "TRUE" : "FALSE";
        }
        // A semi-structured variable re-enters SQL as PARSE_JSON of its canonical text — inlining a
        // bare string literal would lose its variant-ness, and the strict functions (TYPEOF / GET /
        // TO_JSON) would rightly reject the literal that substitution created.
        if (value instanceof VariantValue) {
            return "PARSE_JSON(" + SqlStringLiterals.encode(((VariantValue) value).text()) + ")";
        }
        // A BINARY variable binds as a hex literal, which the parser reads back as BINARY.
        if (value instanceof BinaryValue) {
            return "X'" + ((BinaryValue) value).toHex() + "'";
        }
        // A NUMERIC variable binds as a bare number. Quoting it put a STRING where the grammar and the
        // column both want a number: `LIMIT :batch_size` became LIMIT '2', which is a syntax error,
        // and `SELECT …, :batch_id` inserted the text '1' into an INTEGER column.
        if (value instanceof Number) {
            return value.toString();
        }
        // A temporal variable binds as a cast literal so it re-enters SQL as a temporal.
        final String temporal = SharedFunctionHelpers.temporalSqlLiteral(value);
        if (temporal != null) {
            return temporal;
        }

        return SqlStringLiterals.encode(value.toString());
    }

    /** Strip the surrounding double quotes from a QUOTED_IDENTIFIER token, unescaping "" to ". */
    private static String unquoteIdentifier(final String quoted) {
        if (quoted.length() >= 2 && quoted.startsWith("\"") && quoted.endsWith("\"")) {
            return quoted.substring(1, quoted.length() - 1).replace("\"\"", "\"");
        }
        return quoted;
    }
}
