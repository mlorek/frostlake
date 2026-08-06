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

package dev.frostlake.executor.expressions;

/**
 * Where a node sits inside the expression TEXT it was parsed from — a line (1-based) and a character
 * offset within that line (0-based), the two numbers Snowflake reports as
 * {@code error line 1 at position 7}.
 *
 * <p><b>Fragment-relative, never statement-relative.</b> This is the invariant that keeps the
 * expression AST cache correct: {@code ExpressionEvaluator} caches a parsed AST BY ITS TEXT, so two
 * occurrences of the same expression anywhere in any statement share one AST. A position stored here
 * is therefore a pure function of that text and stays true for every occurrence. Storing an absolute
 * statement offset instead would let the first occurrence's offset be reported for all the others —
 * a wrong answer rather than a missing one.
 *
 * <p>The statement-relative position a message quotes is produced late, by adding the ORIGIN of the
 * fragment being evaluated. See {@link ExpressionSource}.
 */
public final class SourcePosition {

    private final int line;
    private final int charPositionInLine;

    public SourcePosition(final int line, final int charPositionInLine) {
        this.line = line;
        this.charPositionInLine = charPositionInLine;
    }

    /** 1-based, as Snowflake counts lines. */
    public int getLine() {
        return line;
    }

    /** 0-based within the line, as Snowflake counts positions. */
    public int getCharPositionInLine() {
        return charPositionInLine;
    }

    @Override
    public String toString() {
        return "line " + line + " at position " + charPositionInLine;
    }
}
