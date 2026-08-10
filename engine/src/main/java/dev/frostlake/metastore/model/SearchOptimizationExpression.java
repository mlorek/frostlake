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

package dev.frostlake.metastore.model;

/**
 * One configured SEARCH OPTIMIZATION expression — a METHOD over a column, as
 * {@code DESCRIBE SEARCH OPTIMIZATION ON <table>} reports it.
 *
 * <p>Frostlake records these and reports them; nothing in the executor consults them, so no lookup
 * structure exists and no query plan changes (see docs/scope.md). The numbers are handed out once
 * and never reused: dropping expression 2 leaves 1, 3, 4 numbered as they were (live-verified).
 */
public class SearchOptimizationExpression {

    private final int expressionId;
    private final String method;
    private final String target;
    private final String targetDataType;

    public SearchOptimizationExpression(final int expressionId, final String method,
                                        final String target, final String targetDataType) {
        this.expressionId = expressionId;
        this.method = method;
        this.target = target;
        this.targetDataType = targetDataType;
    }

    public int getExpressionId() { return expressionId; }
    public String getMethod() { return method; }
    public String getTarget() { return target; }
    public String getTargetDataType() { return targetDataType; }

    /** How the ALTER form spells this expression back when it refuses to drop a missing one. */
    public String render(final String tableName) {
        return method + "(" + tableName + "." + target + ")";
    }
}
