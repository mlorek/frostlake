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

public abstract class Statement {

    private final StatementType type;

    // A Snowflake Scripting loop label. On a loop statement it is the loop's OWN label, taken from the
    // TRAILING `END LOOP <label>` / `END FOR <label>` / `END WHILE <label>`; on a BREAK / CONTINUE it is
    // the TARGET label. Null when unlabeled. (Snowflake has no LEADING `<label>:` declaration.)
    private String label;

    // Where the statement stands in the script, for the uncaught-exception message
    // ("… on line L at position P"); -1 when unknown.
    private int sourceLine = -1;
    private int sourcePosition = -1;

    protected Statement(final StatementType type) {
        this.type = type;
    }

    /** 1-based line of the statement's first token, or -1 when unknown. */
    public int getSourceLine() {
        return sourceLine;
    }

    /** 0-based column of the statement's first token, or -1 when unknown. */
    public int getSourcePosition() {
        return sourcePosition;
    }

    public void setSourcePosition(final int line, final int position) {
        this.sourceLine = line;
        this.sourcePosition = position;
    }

    public StatementType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(final String label) {
        this.label = label;
    }
}
