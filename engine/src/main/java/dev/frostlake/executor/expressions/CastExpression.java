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

import dev.frostlake.types.DataType;

/**
 * Represents a type cast (e.g., CAST(x AS INTEGER), x::VARCHAR). When {@code tryMode} is set (TRY_CAST),
 * a failed conversion yields NULL instead of raising an error.
 *
 * <p>A PARAMETERIZED target whose parameters decide the cast's legality and result — a STRUCTURED type
 * ({@code OBJECT(x VARCHAR)}, {@code ARRAY(INT)}, {@code MAP(k,v)}) or a {@code VECTOR(FLOAT|INT, n)} —
 * is carried as a parsed {@link DataType} alongside the target-type text, together with the optional
 * {@code RENAME FIELDS} / {@code ADD FIELDS} modifier. Those parameters must survive from the parse
 * tree rather than being re-derived from the (whitespace-stripped) target text.
 */
public class CastExpression implements Expression {
    private final Expression expression;
    private final String targetType;
    private final boolean tryMode;
    private final DataType declaredTarget;
    private final CastFieldsModifier fieldsModifier;
    /** Where the cast was written — its {@code ::} or its CAST keyword — relative to its fragment. */
    private SourcePosition position;

    public CastExpression(final Expression expression, final String targetType) {
        this(expression, targetType, false);
    }

    public CastExpression(final Expression expression, final String targetType, final boolean tryMode) {
        this(expression, targetType, tryMode, null, CastFieldsModifier.NONE);
    }

    public CastExpression(final Expression expression, final String targetType, final boolean tryMode,
                          final DataType declaredTarget, final CastFieldsModifier fieldsModifier) {
        this.expression = expression;
        this.targetType = targetType;
        this.tryMode = tryMode;
        this.declaredTarget = declaredTarget;
        this.fieldsModifier = fieldsModifier;
    }

    public Expression getExpression() {
        return expression;
    }

    public String getTargetType() {
        return targetType;
    }

    public boolean isTryMode() {
        return tryMode;
    }

    /**
     * The parsed target type when its PARAMETERS matter (a structured type, or a
     * {@code VECTOR(t, n)}), or null when the plain target-type text says everything.
     */
    public DataType getDeclaredTarget() {
        return declaredTarget;
    }

    /** The {@code RENAME FIELDS} / {@code ADD FIELDS} modifier, never null. */
    public CastFieldsModifier getFieldsModifier() {
        return fieldsModifier;
    }

    /**
     * Where the cast was written, relative to its fragment — the {@code ::} of the shorthand, the CAST
     * or TRY_CAST keyword otherwise — or null for a cast the engine built itself. A refusal of the cast
     * as a whole is positioned there: live points {@code v::VECTOR(INT,3)} at the {@code ::}.
     */
    public SourcePosition getPosition() {
        return position;
    }

    public void setPosition(final SourcePosition position) {
        this.position = position;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitCast(this);
    }

    @Override
    public String toString() {
        return "CAST(" + expression + " AS " + targetType + ")";
    }
}
