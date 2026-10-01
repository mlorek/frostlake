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
    private int initializerLine = -1;
    private int initializerPosition = -1;
    /** The own static type of an untyped declaration's initialiser, or null. */
    private DataType initialiserType;

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

    /** Where the initialiser expression stood, so a coercion fault can anchor on it. */
    public void setInitializerAt(final int line, final int position) {
        this.initializerLine = line;
        this.initializerPosition = position;
    }

    public int getInitializerLine() {
        return initializerLine;
    }

    public int getInitializerPosition() {
        return initializerPosition;
    }

    /** The own static type of an untyped declaration's initialiser, which a bare RETURN of the name reports. */
    public DataType getInitialiserType() {
        return initialiserType;
    }

    public void setInitialiserType(final DataType initialiserType) {
        this.initialiserType = initialiserType;
    }
}
