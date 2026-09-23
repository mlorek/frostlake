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

package dev.frostlake.executor.procedural;

import org.antlr.v4.runtime.ParserRuleContext;

public abstract class BaseExpression {

    /** 1-based line the expression starts on in the script's source, or -1 when unknown. */
    private int sourceLine = -1;

    /** 0-based column the expression starts at, or -1 when unknown. */
    private int sourcePosition = -1;

    /**
     * The parse tree of a block's own expression — a RETURN's value, a condition, a value given to a variable —
     * whose argument types are judged when it is reached, before it is evaluated; null for any other expression.
     */
    private ParserRuleContext judgedFrom;

    public ParserRuleContext getJudgedFrom() {
        return judgedFrom;
    }

    public void setJudgedFrom(final ParserRuleContext judgedFrom) {
        this.judgedFrom = judgedFrom;
    }

    public int getSourceLine() {
        return sourceLine;
    }

    public int getSourcePosition() {
        return sourcePosition;
    }

    public void setSourcePosition(final int line, final int position) {
        this.sourceLine = line;
        this.sourcePosition = position;
    }
}
