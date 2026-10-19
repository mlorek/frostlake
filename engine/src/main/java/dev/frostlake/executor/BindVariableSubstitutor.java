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
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.NonFiniteDoubles;
import dev.frostlake.values.VariantJsonText;
import dev.frostlake.values.VariantValue;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Substitutes procedural scripting BIND variables ({@code :var}) into raw SQL text before it is
 * parsed/executed. Holds a live reference to the procedural variable map.
 *
 * <p>A record field is not one of them. Inside a SQL statement {@code rec.col} is an ordinary column
 * reference on the account: {@code INSERT INTO t VALUES (r.a)} is "invalid identifier 'R.A'", and over
 * a table aliased {@code r} it reads that table's own column. Only a scripting EXPRESSION reads the
 * field ({@code x := r.a}, {@code RETURN r.a}, an IF condition), and none of those comes through here.
 *
 * <p>The substitution is driven by the SQL <em>lexer</em>, not hand-rolled character scanning: the SQL
 * is tokenized, and a bind variable is recognized as the token sequence {@code ':' IDENTIFIER}. Because
 * the lexer classifies the text,
 * a name that appears inside a {@link FrostlakeLexer#STRING_LITERAL} or a {@link FrostlakeLexer#QUOTED_IDENTIFIER}
 * is never mistaken for a reference, and {@code ::} (cast) / {@code :=} (assignment) — their own tokens
 * — are never confused with a {@code :var}. The grammar's tokenizer stays the single source of truth
 * about the SQL's lexical structure.
 */
public class BindVariableSubstitutor {

    private final Map<String, Object> variables;
    private final ProceduralExecutor declarations;

    public BindVariableSubstitutor(final Map<String, Object> variables) {
        this(variables, null);
    }

    /**
     * A substitutor that also knows the names' DECLARED types, so a numeric variable is spelled with
     * its type — {@code (1)::NUMBER(9,0)} for a FOR counter, {@code (1.78)::NUMBER(5,2)} for a declared
     * NUMBER — and the statement it lands in types it as live does (a column derived from {@code :i}
     * declares NUMBER(9,0), not the digits' own width).
     */
    public BindVariableSubstitutor(final Map<String, Object> variables, final ProceduralExecutor declarations) {
        this.variables = variables;
        this.declarations = declarations;
    }

    /** Substitute colon bind variables into the SQL. */
    public String substitute(final String sql) {
        return substitute(sql, false);
    }

    /**
     * Substitute colon bind variables into the SQL.
     *
     * @param sql       the text
     * @param statement whether the text is a statement a block runs, which cannot bind an infinity (see
     *                  {@link InfiniteBindRefusal})
     * @return the text with each bind written out
     */
    public String substitute(final String sql, final boolean statement) {
        return substitute(sql, statement, null);
    }

    /**
     * Substitute colon bind variables into the SQL, noting each one written out.
     *
     * @param sql       the text
     * @param statement whether the text is a statement a block runs
     * @param binds     where each bind written out is noted — its line, its column, its written length and the
     *                  length of what stands in its place — or null when nobody asks
     * @return the text with each bind written out
     */
    public String substitute(final String sql, final boolean statement, final List<int[]> binds) {
        // Nothing to do unless the text can contain a bind variable.
        if (sql == null || sql.indexOf(':') < 0) {
            return sql;
        }

        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
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
                    if (isNeverBoundSlot(toks, i)) {
                        // A SAMPLE's size, its seed and a PIVOT's values bind nothing: the name stays as written
                        // and the statement refuses it there.
                        i++;
                        continue;
                    }
                    rejectDottedBindVariable(toks, i + 1, nameTok);
                    if (declarations != null) {
                        // A handler may not read what its block's BEGIN section declared (see HandlerLets).
                        declarations.requireReadableInHandler(nameTok.getText());
                    }
                    final Object held = variables.get(nameTok.getText().toUpperCase(Locale.ROOT));
                    final Object value = declarations != null
                        ? declarations.boundValue(nameTok.getText(), held, statement) : held;
                    final RuntimeException infinite = statement ? InfiniteBindRefusal.of(value) : null;
                    if (infinite != null) {
                        throw infinite;
                    }
                    out.append(sql, cursor, t.getStartIndex());
                    final String written = typedLiteral(nameTok.getText(), value, toks, i);
                    out.append(written);
                    if (binds != null) {
                        binds.add(new int[] {t.getLine(), t.getCharPositionInLine(),
                            nameTok.getStopIndex() + 1 - t.getStartIndex(), written.length()});
                    }
                    cursor = nameTok.getStopIndex() + 1;
                    i++;   // consumed the identifier too
                    continue;
                }
            }

        }
        out.append(sql, cursor, sql.length());
        return out.toString();
    }

    /**
     * The first {@code :name} a text binds, recognised as {@link #substitute} recognises one, or null when it binds
     * none.
     *
     * @param sql the text
     * @return the name's token, which the colon stands right before, or null
     */
    public Token firstBind(final String sql) {
        return firstBind(sql, false);
    }

    /**
     * The first bind a text holds: a {@code :name}, and a {@code :n} too when {@code numbered} says so.
     *
     * @param sql      the text
     * @param numbered whether a colon before digits is a bind as well
     * @return the token after the colon, or null when the text binds nothing
     */
    public static Token firstBind(final String sql, final boolean numbered) {
        if (sql == null || sql.indexOf(':') < 0) {
            return null;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> toks = stream.getTokens();
        // A name in an INTO list is a target, judged as the clause is, and no bind.
        final Set<Integer> intoTargets = intoTargetColonIndices(toks);
        for (int i = 0; i + 1 < toks.size(); i++) {
            final Token colon = toks.get(i);
            final Token name = toks.get(i + 1);
            if (colon.getType() != FrostlakeLexer.COLON || isVariantPathColon(toks, i) || intoTargets.contains(i)) {
                continue;
            }
            if (SqlTokens.isWord(name) && name.getStartIndex() == colon.getStopIndex() + 1
                    || numbered && name.getType() == FrostlakeLexer.INTEGER_LITERAL) {
                return name;
            }
        }
        return null;
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

    /**
     * Whether the bind at {@code colonIndex} stands where the grammar takes a bare literal and no
     * expression — after LIMIT / OFFSET / TOP / FETCH FIRST / NEXT, a {@code =>} argument, or inside
     * SAMPLE ( … ) — so a typed cast would not parse there.
     */
    private static boolean isLiteralOnlySlot(final List<Token> toks, final int colonIndex) {
        if (colonIndex == 0) {
            return false;
        }
        final int previous = toks.get(colonIndex - 1).getType();
        if (previous == FrostlakeLexer.LIMIT || previous == FrostlakeLexer.OFFSET || previous == FrostlakeLexer.TOP
                || previous == FrostlakeLexer.FIRST || previous == FrostlakeLexer.NEXT
                || previous == FrostlakeLexer.ARROW) {
            return true;
        }
        if (previous == FrostlakeLexer.LPAREN && colonIndex >= 2) {
            final int before = toks.get(colonIndex - 2).getType();
            return before == FrostlakeLexer.SAMPLE || before == FrostlakeLexer.TABLESAMPLE;
        }
        return false;
    }

    /**
     * Whether the bind at {@code colonIndex} stands where nothing is ever bound: the size of a SAMPLE or a
     * TABLESAMPLE, with a method word before its parenthesis or without, its SEED or REPEATABLE, the value list
     * of a PIVOT ({@code FOR k IN (:s, 1)}), and the word TOP, which no bind counts for ({@code SELECT TOP :s v}
     * reads a column TOP).
     */
    private static boolean isNeverBoundSlot(final List<Token> toks, final int colonIndex) {
        if (colonIndex > 0 && toks.get(colonIndex - 1).getType() == FrostlakeLexer.TOP) {
            // TOP takes a number written out: before a colon it is a name, and the colon a path's.
            return true;
        }
        int open = colonIndex - 1;
        int depth = 0;
        while (open >= 0) {
            final int type = toks.get(open).getType();
            if (type == FrostlakeLexer.RPAREN) {
                depth++;
            } else if (type == FrostlakeLexer.LPAREN) {
                if (depth == 0) {
                    break;
                }
                depth--;
            }
            open--;
        }
        if (open < 1) {
            return false;
        }
        final int before = toks.get(open - 1).getType();
        if (before == FrostlakeLexer.IN) {
            return open >= 3 && toks.get(open - 3).getType() == FrostlakeLexer.FOR;
        }
        if (open != colonIndex - 1) {
            return false;
        }
        if (before == FrostlakeLexer.SAMPLE || before == FrostlakeLexer.TABLESAMPLE
                || before == FrostlakeLexer.SEED || before == FrostlakeLexer.REPEATABLE) {
            return true;
        }
        final int method = open >= 2 ? toks.get(open - 2).getType() : Token.EOF;
        return method == FrostlakeLexer.SAMPLE || method == FrostlakeLexer.TABLESAMPLE;
    }

    /** Whether the bind at {@code colonIndex} is the count of a LIMIT, an OFFSET or a FETCH. */
    private static boolean isRowCountSlot(final List<Token> toks, final int colonIndex) {
        if (colonIndex == 0) {
            return false;
        }
        final int previous = toks.get(colonIndex - 1).getType();
        return previous == FrostlakeLexer.LIMIT || previous == FrostlakeLexer.OFFSET
            || previous == FrostlakeLexer.FIRST || previous == FrostlakeLexer.NEXT;
    }

    /** Whether the bind at {@code colonIndex} is the whole argument of an IDENTIFIER() reference, which takes no cast. */
    private static boolean isIdentifierArgument(final List<Token> toks, final int colonIndex) {
        if (colonIndex < 2 || toks.get(colonIndex - 1).getType() != FrostlakeLexer.LPAREN) {
            return false;
        }
        final int before = toks.get(colonIndex - 2).getType();
        return before == FrostlakeParser.KW_IDENTIFIER_REF || before == FrostlakeParser.KW_IDENTIFIER_OPEN;
    }

    /**
     * The value as a literal, cast to its declared NUMBER type when the name carries one and a cast may stand there;
     * a NULL cast to whatever type the name is declared with.
     */
    private String typedLiteral(final String name, final Object value, final List<Token> toks, final int colonIndex) {
        if (isRowCountSlot(toks, colonIndex)) {
            // A row count is the whole number its variable holds, or refused as the bind it is (RowCountSlot).
            final Integer rows = RowCountSlot.ofBind(name, value,
                toks.get(colonIndex - 1).getType() == FrostlakeLexer.OFFSET);
            return rows == null ? "NULL" : rows.toString();
        }
        final boolean literalOnlySlot = isLiteralOnlySlot(toks, colonIndex);
        final String literal = toLiteral(value);
        final DataType declared = declarations == null ? null : declarations.getDeclaredVariableType(name);
        if (isIdentifierArgument(toks, colonIndex)) {
            if (value instanceof Number) {
                throw new RuntimeException("Unsupported feature 'Bind by Object ID'.");
            }
            if (!(value instanceof CharSequence)) {
                // IDENTIFIER() over a variable that holds NULL, or a value that is no text, names the bind
                // itself, as written: "Object '"s"' does not exist or not authorized." (live-verified).
                return SqlStringLiterals.encode('"' + name.replace("\"", "\"\"") + '"');
            }
        }
        if (value == null) {
            // A NULL keeps its declared type where live binds it so (TypedNullBind) and a cast may stand.
            if (literalOnlySlot) {
                return literal;
            }
            final NullBindSlot slot = NullBindSlot.at(toks, colonIndex);
            final String call = slot.wholeArgumentOf();
            return TypedNullBind.spell(declared, slot, call != null && declarations != null
                && declarations.isUserFunctionName(call));
        }
        if (!literalOnlySlot && value instanceof VariantValue
                && (declared instanceof ArrayType || declared instanceof ObjectType)
                && !(declared instanceof StructuredArrayType) && !(declared instanceof StructuredObjectType)) {
            // An ARRAY or an OBJECT variable is one where it is bound, not the VARIANT its text parses to.
            return literal + "::" + (declared instanceof ArrayType ? "ARRAY" : "OBJECT");
        }
        if (!literalOnlySlot && declared instanceof VariantType && !(value instanceof VariantValue)) {
            // A VARIANT variable is a VARIANT where it is bound, whatever it holds: SELECT :v over one holding 1 is a
            // VARIANT column, which a RETURNS TABLE (a NUMBER) refuses (live-verified).
            return "TO_VARIANT(" + (value instanceof Double ? "(" + literal + ")::FLOAT" : literal) + ")";
        }
        if (literalOnlySlot || !(declared instanceof NumericType)
                || (value instanceof Double && !Double.isFinite((Double) value))) {
            return literal;
        }
        final NumericType numeric = (NumericType) declared;
        final String family = numeric.getName() == null ? "" : numeric.getName().toUpperCase(Locale.ROOT);
        final String typeText = family.equals("FLOAT") || family.equals("DOUBLE") || family.equals("REAL")
            ? "FLOAT" : "NUMBER(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        return "(" + literal + ")::" + typeText;
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
            return "PARSE_JSON(" + SqlStringLiterals.encode(VariantJsonText.rereadableTextOf((VariantValue) value))
                + ")";
        }
        // A BINARY variable binds as a hex literal, which the parser reads back as BINARY.
        if (value instanceof BinaryValue) {
            return "X'" + ((BinaryValue) value).toHex() + "'";
        }
        // A NUMERIC variable binds as a bare number. Quoting it put a STRING where the grammar and the
        // column both want a number: `LIMIT :batch_size` became LIMIT '2', which is a syntax error,
        // and `SELECT …, :batch_id` inserted the text '1' into an INTEGER column.
        if (value instanceof Number) {
            // NaN and the infinities have no literal: bound bare, the word would be read as a name.
            final String nonFinite = NonFiniteDoubles.castOfWord((Number) value);
            return nonFinite != null ? nonFinite : value.toString();
        }
        // A temporal variable binds as a cast literal so it re-enters SQL as a temporal.
        final String temporal = SharedFunctionHelpers.temporalSqlLiteral(value);
        if (temporal != null) {
            return temporal;
        }

        return SqlStringLiterals.encode(value.toString());
    }

}
