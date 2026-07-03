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

/**
 * REPEAT … UNTIL &lt;condition&gt; END REPEAT — a post-test loop: the body runs at least once, then the
 * condition is checked; iteration continues while the condition is false and stops once it is true.
 */
public class RepeatStatement extends Statement {

    private final BaseExpression condition;
    private final ProceduralBlock block;

    public RepeatStatement(final BaseExpression condition, final ProceduralBlock block) {
        super(StatementType.REPEAT);
        this.condition = condition;
        this.block = block;
    }

    public BaseExpression getCondition() {
        return condition;
    }

    public ProceduralBlock getBlock() {
        return block;
    }
}
