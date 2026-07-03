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

import dev.frostlake.types.DataType;

public class DeclareStatement extends Statement {
    private final String variableName;
    private final Object defaultValue;
    private final DataType declaredType;

    public DeclareStatement(final String variableName, final Object defaultValue) {
        this(variableName, defaultValue, null);
    }

    public DeclareStatement(final String variableName, final Object defaultValue, final DataType declaredType) {
        super(StatementType.DECLARE);
        this.variableName = variableName;
        this.defaultValue = defaultValue;
        this.declaredType = declaredType;
    }

    public String getVariableName() {
        return variableName;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    /** The variable's declared type, or null when the declaration didn't carry one. */
    public DataType getDeclaredType() {
        return declaredType;
    }
}
