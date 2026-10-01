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

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.misc.Interval;

/**
 * The query a block's {@code SELECT … INTO} runs: its own text as written, less the INTO clause. Live runs
 * exactly that text, so every place a refusal reports inside it counts from it (live-verified): the INTO
 * keyword goes with the one whitespace character before it and everything up to its last target, and
 * everything else — the WITH clause, the line breaks and the runs of spaces — stays where it was written.
 *
 * <pre>
 *   SELECT COUNT(*) INTO :c FROM IDENTIFIER(:x)        runs  SELECT COUNT(*) FROM IDENTIFIER(:x)
 *   SELECT COUNT(*)   INTO :c   FROM IDENTIFIER(:x)    runs  SELECT COUNT(*)     FROM IDENTIFIER(:x)
 * </pre>
 *
 * <p>A line break after the targets stays too, so a FROM written on the next line is still on the query's
 * second line.
 */
final class SelectIntoText {

    private SelectIntoText() {
    }

    /**
     * @param ctx the statement
     * @return its query, without the INTO clause and without a closing semicolon
     */
    static String withoutInto(final FrostlakeParser.SelectIntoStatementContext ctx) {
        final CharStream input = ctx.getStart().getInputStream();
        final int start = ctx.getStart().getStartIndex();
        int intoStart = ctx.INTO().getSymbol().getStartIndex();
        if (intoStart > start && Character.isWhitespace(input.getText(Interval.of(intoStart - 1, intoStart - 1)).charAt(0))) {
            intoStart--;
        }
        final int targetsEnd = ctx.intoTargetList().getStop().getStopIndex();
        final int end = ctx.SEMI() != null ? ctx.SEMI().getSymbol().getStartIndex() - 1 : ctx.getStop().getStopIndex();
        final String head = input.getText(Interval.of(start, intoStart - 1));
        return end > targetsEnd ? head + input.getText(Interval.of(targetsEnd + 1, end)) : head;
    }
}
