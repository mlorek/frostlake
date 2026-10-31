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

package dev.frostlake.jdbc;

import dev.frostlake.executor.BoundedParseCache;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The placeholders of a prepared statement that stand alone as a sort or grouping key — {@code ORDER BY ?},
 * {@code ORDER BY -(?)}, {@code GROUP BY (?)}, {@code ROLLUP (?, a)}, {@code GROUPING SETS ((?), ())} — where a number
 * written in the value's place would name a select-list position. The account binds on the server, so a value bound
 * there is a constant: {@code ORDER BY ?, a} bound to 2 orders by {@code a}, {@code GROUP BY ?} bound to 1 makes one
 * group, and a bound 0 or -1 is no position out of range (live-verified). A placeholder inside a larger key,
 * {@code GROUP BY a + ?}, is a value where it stands already.
 *
 * <p>Read from the statement's parse tree, where a placeholder is an expression of its own; a statement that does not
 * parse has none, and is refused where it runs.
 */
final class BoundKeyPlaces {

    /** The statements whose places are kept: a prepared statement runs its one text again and again. */
    private static final BoundedParseCache<Set<Integer>> KEPT = new BoundedParseCache<Set<Integer>>(256, 64 * 1024);

    private BoundKeyPlaces() {
    }

    /**
     * Where the statement's lone-key placeholders stand.
     *
     * @param sql the statement
     * @return the offset in {@code sql} of each such {@code ?}
     */
    static Set<Integer> of(final String sql) {
        Set<Integer> places = KEPT.get(sql);
        if (places == null) {
            places = read(sql);
            KEPT.put(sql, places);
        }
        return places;
    }

    private static Set<Integer> read(final String sql) {
        FrostlakeParser.SqlScriptContext tree = parse(sql, PredictionMode.SLL);
        if (tree == null) {
            tree = parse(sql, PredictionMode.LL);
        }
        if (tree == null) {
            return Collections.<Integer>emptySet();
        }
        final List<ParserRuleContext> keys = new ArrayList<ParserRuleContext>();
        keysOf(tree, keys);
        final Set<Integer> places = new HashSet<Integer>();
        for (final ParserRuleContext key : keys) {
            final Token alone = lonePlaceholder(key);
            if (alone != null) {
                places.add(Integer.valueOf(alone.getStartIndex()));
            }
        }
        return Collections.unmodifiableSet(places);
    }

    /** The statement read with one prediction mode, or null where that reading meets a fault. */
    private static FrostlakeParser.SqlScriptContext parse(final String sql, final PredictionMode mode) {
        final boolean[] faulted = {false};
        final BaseErrorListener anyFault = new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                faulted[0] = true;
            }
        };
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(anyFault);
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(anyFault);
        parser.setErrorHandler(new BailErrorStrategy());
        parser.getInterpreter().setPredictionMode(mode);
        try {
            final FrostlakeParser.SqlScriptContext tree = parser.sqlScript();
            return faulted[0] ? null : tree;
        } catch (final RuntimeException unread) {
            return null;
        }
    }

    /** Every sort key and grouping key below {@code node}: ORDER BY items, GROUP BY elements and their members. */
    private static void keysOf(final ParseTree node, final List<ParserRuleContext> keys) {
        if (node instanceof FrostlakeParser.OrderItemContext) {
            keys.add(((FrostlakeParser.OrderItemContext) node).booleanExpr());
        } else if (node instanceof FrostlakeParser.GroupByElementContext) {
            keys.add(((FrostlakeParser.GroupByElementContext) node).booleanExpr());
        } else if (node instanceof FrostlakeParser.GroupByColumnListContext) {
            keys.addAll(((FrostlakeParser.GroupByColumnListContext) node).booleanExpr());
        } else if (node instanceof FrostlakeParser.GroupingSetContext) {
            keys.addAll(((FrostlakeParser.GroupingSetContext) node).booleanExpr());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            keysOf(node.getChild(i), keys);
        }
    }

    /**
     * The placeholder a key is, under any parentheses and signs, or null when the key is anything else.
     *
     * @param key the key, or null where its element holds none of its own
     * @return the placeholder's token, or null
     */
    private static Token lonePlaceholder(final ParserRuleContext key) {
        if (key == null) {
            return null;
        }
        final List<Token> spoken = new ArrayList<Token>();
        terminals(key, spoken);
        Token alone = null;
        for (final Token token : spoken) {
            final int type = token.getType();
            if (type == FrostlakeLexer.LPAREN || type == FrostlakeLexer.RPAREN || type == FrostlakeLexer.MINUS
                    || type == FrostlakeLexer.PLUS) {
                continue;
            }
            if (alone != null || type != FrostlakeLexer.QUESTION) {
                return null;
            }
            alone = token;
        }
        return alone;
    }

    private static void terminals(final ParseTree node, final List<Token> spoken) {
        if (node instanceof TerminalNode) {
            spoken.add(((TerminalNode) node).getSymbol());
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            terminals(node.getChild(i), spoken);
        }
    }
}
