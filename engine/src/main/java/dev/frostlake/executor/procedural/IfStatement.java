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

public class IfStatement extends Statement {
    private final List<IfCondition> conditions;
    private final ProceduralBlock elseBlock;

    public IfStatement(final List<IfCondition> conditions, final ProceduralBlock elseBlock) {
        super(StatementType.IF);
        this.conditions = conditions;
        this.elseBlock = elseBlock;
    }

    public List<IfCondition> getConditions() {
        return conditions;
    }

    public ProceduralBlock getElseBlock() {
        return elseBlock;
    }
}
