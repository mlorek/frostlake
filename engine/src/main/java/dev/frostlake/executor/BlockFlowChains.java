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

import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/**
 * A block's statements chained with the flow arrow (all live-verified): {@code BEGIN SELECT 1 AS a ->> SELECT a + 1 AS
 * b FROM $1; RETURN 'ok'; END} runs its stages in turn, each reading a prior stage's result as {@code $n}, n stages
 * back, as a request's own chain does, in a loop's body too, and the last stage's query is the one LAST_QUERY_ID()
 * names after it. The lexer reads the arrow as the statement's semicolon (see Frostlake.g4), and a statement keeps
 * its terminator, so a stage is told by its last token's text.
 *
 * <p>A {@code $n} no stage of its chain can answer - in a statement no arrow joins, or further back than one stage
 * before the chain's first - is refused while the block compiles, {@code error line 0 at position -1}, {@code invalid
 * identifier 'SQL_PIPE_n'}; one stage before the chain's first is refused as the stage runs, {@code Statement null
 * not found}.
 */
public final class BlockFlowChains {

    private static final String ARROW = "->>";

    private BlockFlowChains() {
    }

    /**
     * Whether a block's statement is a stage the flow arrow joins to the statement after it.
     *
     * @param statement the statement
     * @return whether its result feeds the next statement
     */
    public static boolean feedsTheNext(final FrostlakeParser.StatementContext statement) {
        final Token stop = statement.getStop();
        return stop != null && stop.getType() == FrostlakeParser.SEMI && ARROW.equals(stop.getText());
    }

    /**
     * Whether a block's statement runs after a stage the flow arrow joins to it.
     *
     * @param statement the statement
     * @return whether a prior stage's result is in its reach
     */
    public static boolean followsAStage(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.StatementContext before = previous(statement);
        return before != null && feedsTheNext(before);
    }

    /**
     * A stage's text as it runs on its own: its arrow is its end.
     *
     * @param statement the statement
     * @param written   its text as written, the arrow included
     * @return the text without the arrow
     */
    public static String ownText(final FrostlakeParser.StatementContext statement, final String written) {
        if (!feedsTheNext(statement) || written == null || !written.endsWith(ARROW)) {
            return written;
        }
        return written.substring(0, written.length() - ARROW.length()).stripTrailing();
    }

    /**
     * Refuse every {@code $n} of a block's statements that no stage of its chain can answer.
     *
     * @param block the block, its nested statement lists included
     */
    public static void requirePipeReferences(final ParseTree block) {
        for (int i = 0; i < block.getChildCount(); i++) {
            final ParseTree child = block.getChild(i);
            if (child instanceof FrostlakeParser.StatementContext
                    && block instanceof FrostlakeParser.StatementListContext) {
                requireIn((FrostlakeParser.StatementContext) child);
            }
            requirePipeReferences(child);
        }
    }

    private static void requireIn(final FrostlakeParser.StatementContext statement) {
        final boolean chained = feedsTheNext(statement) || followsAStage(statement);
        int before = 0;
        for (FrostlakeParser.StatementContext at = previous(statement); at != null && feedsTheNext(at);
                at = previous(at)) {
            before++;
        }
        for (final FrostlakeParser.TableSourceContext source : sourcesOf(statement)) {
            final int back = Integer.parseInt(source.POSITIONAL_PARAMETER().getText().substring(1));
            if (back >= 1 && (!chained || back > before + 1)) {
                throw new RuntimeException(SqlCompilationError.at(0, -1,
                    "invalid identifier 'SQL_PIPE_" + back + "'"));
            }
        }
    }

    /** The {@code $n} sources a statement reads in its own text, outside the statements nested in it. */
    private static List<FrostlakeParser.TableSourceContext> sourcesOf(final ParserRuleContext node) {
        final List<FrostlakeParser.TableSourceContext> found = new ArrayList<>();
        collect(node, found);
        return found;
    }

    private static void collect(final ParseTree node, final List<FrostlakeParser.TableSourceContext> found) {
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParseTree child = node.getChild(i);
            if (child instanceof FrostlakeParser.StatementContext) {
                continue;
            }
            if (child instanceof FrostlakeParser.TableSourceContext
                    && ((FrostlakeParser.TableSourceContext) child).POSITIONAL_PARAMETER() != null) {
                found.add((FrostlakeParser.TableSourceContext) child);
            }
            collect(child, found);
        }
    }

    /** The statement before {@code statement} in its list, or null. */
    private static FrostlakeParser.StatementContext previous(final FrostlakeParser.StatementContext statement) {
        final ParserRuleContext list = statement.getParent();
        if (!(list instanceof FrostlakeParser.StatementListContext)) {
            return null;
        }
        FrostlakeParser.StatementContext before = null;
        for (int i = 0; i < list.getChildCount(); i++) {
            final ParseTree child = list.getChild(i);
            if (child == statement) {
                return before;
            }
            if (child instanceof FrostlakeParser.StatementContext) {
                before = (FrostlakeParser.StatementContext) child;
            }
        }
        return null;
    }
}
