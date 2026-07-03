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

import java.util.List;

public class OpenStatement extends Statement {
    private final String cursorName;
    // OPEN c USING (a, b, …) — values bound positionally to the cursor query's `?` placeholders. Empty
    // when the cursor has no placeholders / no USING clause.
    private final List<BaseExpression> usingBindings;

    public OpenStatement(final String cursorName) {
        this(cursorName, List.of());
    }

    public OpenStatement(final String cursorName, final List<BaseExpression> usingBindings) {
        super(StatementType.OPEN);
        this.cursorName = cursorName;
        this.usingBindings = usingBindings;
    }

    public String getCursorName() {
        return cursorName;
    }

    public List<BaseExpression> getUsingBindings() {
        return usingBindings;
    }
}
