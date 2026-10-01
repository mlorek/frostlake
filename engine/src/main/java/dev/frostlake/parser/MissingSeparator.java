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

import dev.frostlake.executor.SqlCompilationError;
import java.util.ArrayList;
import java.util.List;

/**
 * The first place a script is refused for two statements that no semicolon separates: where its first
 * syntax-error line points, and the lines live reports there.
 */
final class MissingSeparator {

    private final int line;
    private final int position;
    private final List<String> lines;
    /** The index of the statement chain the second statement begins, or -1 when it is not known. */
    private int secondChain = -1;
    private boolean swallowed;

    MissingSeparator(final int line, final int position, final List<String> lines) {
        this.line = line;
        this.position = position;
        this.lines = new ArrayList<>(lines);
    }

    /** Record the chain the second statement begins. */
    MissingSeparator between(final int chainIndex) {
        this.secondChain = chainIndex;
        return this;
    }

    /** The index of the statement chain the second statement begins, or -1. */
    int secondChain() {
        return secondChain;
    }

    /** Record that the second statement's first word was swallowed as the first one's alias. */
    MissingSeparator swallowing() {
        this.swallowed = true;
        return this;
    }

    /** Whether the second statement's first word was swallowed as the first one's alias. */
    boolean swallows() {
        return swallowed;
    }

    /**
     * Whether this fault stands before the given place in the text, or there is no other place to compare.
     *
     * @param coordinates another fault's line and position, or null
     * @return true when this fault is the earlier one
     */
    boolean precedes(final int[] coordinates) {
        return coordinates == null || line < coordinates[0]
            || line == coordinates[0] && position < coordinates[1];
    }

    /** The first line the refusal reports. */
    String firstLine() {
        return lines.get(0);
    }

    /** The line and position the first line points at. */
    int[] coordinates() {
        return new int[] {line, position};
    }

    /**
     * The refusal to throw.
     *
     * @param sql the script's text
     * @return the syntax exception carrying every reported line
     */
    SqlSyntaxException refusal(final String sql) {
        return new SqlSyntaxException(SqlCompilationError.of(String.join("\n", lines)), new ArrayList<>(lines), sql);
    }
}
