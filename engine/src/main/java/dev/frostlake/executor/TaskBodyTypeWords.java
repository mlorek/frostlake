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
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.parser.SyntaxErrorListener;
import dev.frostlake.parser.UnknownTypeReading;
import dev.frostlake.parser.UnknownTypeWord;
import dev.frostlake.parser.UnknownTypeWords;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A task's body is stored as written and judged when the task runs, so a word in it that names no type refuses nothing
 * at CREATE TASK (live-verified: {@code CREATE TASK tk … AS CREATE TABLE t9 (x NOSUCHTYPE)} is created, and GET_DDL
 * tells the body as written; so is a block, {@code AS BEGIN LET v NOSUCHTYPE := 1; END}). Such a statement is read
 * with each word replaced by a known type of the word's own length, and the body is then cut from the statement as
 * written.
 */
public final class TaskBodyTypeWords {

    /** The statement as written behind each script read with its words replaced. */
    private static final Map<ParseTree, String> WRITTEN =
        Collections.synchronizedMap(new WeakHashMap<ParseTree, String>());

    private TaskBodyTypeWords() {
    }

    /**
     * The script a CREATE TASK reads as when every word of it that names no type stands in the task's body.
     *
     * @param refused the refusal the statement's parse raised
     * @param sql     the statement as written
     * @return the script, or null when the refusal is no such statement's
     */
    public static FrostlakeParser.SqlScriptContext parsed(final RuntimeException refused, final String sql) {
        if (!(refused instanceof SqlSyntaxException)) {
            return null;
        }
        UnknownTypeReading reading = ((SqlSyntaxException) refused).getUnknownTypes();
        if ((reading == null || reading.getWords().isEmpty()) && sql != null) {
            // A body that is a block faults at its END, past the word: the words are found by their places.
            reading = UnknownTypeWords.readTaskBlock(sql);
        }
        if (reading == null || reading.getWords().isEmpty() || sql == null
                || reading.getSubstituted().length() != sql.length()) {
            return null;
        }
        final FrostlakeParser.SqlScriptContext tree = SyntaxErrorListener.cleanParse(reading.getSubstituted());
        final FrostlakeParser.TaskBodyContext body = tree == null ? null : taskBody(tree);
        if (body == null || body.start == null || body.stop == null) {
            return null;
        }
        for (final UnknownTypeWord word : reading.getWords()) {
            if (word.getStart() < body.start.getStartIndex() || word.getStart() > body.stop.getStopIndex()) {
                return null;
            }
        }
        WRITTEN.put(tree, sql);
        return tree;
    }

    /**
     * The text between two characters of a script read with its words replaced, as the statement wrote it.
     *
     * @param node  a node of the script
     * @param start the first character
     * @param stop  the last character
     * @return the text, or null for a script read as written
     */
    static String written(final ParserRuleContext node, final int start, final int stop) {
        ParseTree root = node;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        final String sql = WRITTEN.get(root);
        return sql == null || start < 0 || stop >= sql.length() ? null : sql.substring(start, stop + 1);
    }

    /** The body of the script's one statement when it is a CREATE TASK, or null. */
    private static FrostlakeParser.TaskBodyContext taskBody(final FrostlakeParser.SqlScriptContext tree) {
        if (tree.flowChain().size() != 1 || tree.flowChain(0).statement().size() != 1) {
            return null;
        }
        final FrostlakeParser.StatementContext statement = tree.flowChain(0).statement(0);
        final FrostlakeParser.CreateStatementContext create = statement.ddlStatement() == null ? null
            : statement.ddlStatement().createStatement();
        return create == null || create.TASK() == null ? null : create.taskBody();
    }
}
