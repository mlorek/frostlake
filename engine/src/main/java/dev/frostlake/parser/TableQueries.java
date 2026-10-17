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

package dev.frostlake.parser;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.misc.Pair;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code TABLE <name>} read as the query it is: {@code SELECT * FROM <name>}, the name's time travel included
 * (live-verified — the account echoes {@code TABLE fz} in a refusal as {@code SELECT FZ.ID AS "ID", … FROM FZ AS
 * FZ}). The query operand is rebuilt as that select clause, so every reader of a query reads it as one: the
 * clause stands where the TABLE keyword stands, and its text is the text written.
 */
final class TableQueries {

    /** Static helpers only — never instantiated. */
    private TableQueries() {
    }

    /**
     * Replaces a {@code TABLE <name>} query operand's children by the select clause it is.
     *
     * @param operand the operand, as the grammar read it
     */
    static void expand(final FrostlakeParser.SelectOperandContext operand) {
        final TerminalNode table = operand.TABLE_QUERY();
        final FrostlakeParser.TableQueryNameContext name = operand.tableQueryName();
        if (table == null || name == null || name.children == null || name.getStop() == null) {
            return;
        }
        final Token at = table.getSymbol();
        final FrostlakeParser.SelectClauseContext clause =
            new FrostlakeParser.SelectClauseContext(operand, operand.invokingState);
        clause.addAnyChild(word(clause, at, FrostlakeParser.SELECT, "SELECT"));

        final FrostlakeParser.SelectListContext list = new FrostlakeParser.SelectListContext(clause, -1);
        final FrostlakeParser.StarItemContext star =
            new FrostlakeParser.StarItemContext(new FrostlakeParser.SelectItemContext(list, -1));
        final TerminalNode starWord = word(star, at, FrostlakeParser.STAR, "*");
        star.addAnyChild(starWord);
        star.start = starWord.getSymbol();
        star.stop = starWord.getSymbol();
        list.addAnyChild(star);
        list.start = star.start;
        list.stop = star.stop;
        clause.addAnyChild(list);

        clause.addAnyChild(word(clause, at, FrostlakeParser.FROM, "FROM"));
        final FrostlakeParser.TableExpressionContext from = new FrostlakeParser.TableExpressionContext(clause, -1);
        final FrostlakeParser.TableReferenceContext reference = new FrostlakeParser.TableReferenceContext(from, -1);
        final FrostlakeParser.TableSourceContext source = new FrostlakeParser.TableSourceContext(reference, -1);
        final List<ParseTree> parts = new ArrayList<>(name.children);
        for (final ParseTree part : parts) {
            if (part instanceof ParserRuleContext) {
                ((ParserRuleContext) part).setParent(source);
            } else if (part instanceof TerminalNodeImpl) {
                ((TerminalNodeImpl) part).setParent(source);
            }
            source.addAnyChild(part);
        }
        placed(source, name);
        reference.addAnyChild(source);
        placed(reference, name);
        from.addAnyChild(reference);
        placed(from, name);
        clause.addAnyChild(from);
        clause.start = at;
        clause.stop = name.getStop();

        operand.children = new ArrayList<>();
        operand.addAnyChild(clause);
    }

    /**
     * Whether a select clause was written {@code TABLE <name>}: it stands where that keyword stands.
     *
     * @param clause the clause, or any other context
     * @return whether it is a select clause read from TABLE
     */
    static boolean writtenAsTable(final ParserRuleContext clause) {
        return clause instanceof FrostlakeParser.SelectClauseContext && clause.getStart() != null
            && clause.getStart().getType() == FrostlakeParser.TABLE_QUERY;
    }

    /** Gives a rebuilt context the place of the name it holds. */
    private static void placed(final ParserRuleContext context, final ParserRuleContext name) {
        context.start = name.getStart();
        context.stop = name.getStop();
    }

    /**
     * A word the query is read with but was never written, placed where the TABLE keyword is: its own text is its
     * whole input, so a context made of it alone reads back as that word.
     */
    private static TerminalNode word(final ParserRuleContext parent, final Token at, final int type,
                                     final String text) {
        final CommonToken token = new CommonToken(
            new Pair<TokenSource, CharStream>(null, CharStreams.fromString(text)), type,
            Token.DEFAULT_CHANNEL, 0, text.length() - 1);
        token.setText(text);
        token.setLine(at.getLine());
        token.setCharPositionInLine(at.getCharPositionInLine());
        token.setTokenIndex(at.getTokenIndex());
        final TerminalNodeImpl node = new TerminalNodeImpl(token);
        node.setParent(parent);
        return node;
    }
}
