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

package dev.frostlake.executor;

import java.util.ArrayList;
import java.util.List;

/**
 * One operator of an EXPLAIN plan: its id, the operators it feeds, what it does, the object it reads or writes, the
 * alias it reads it under, and the expressions it computes.
 */
final class PlanOperator {

    private final int id;
    private final Integer parent;
    private final int depth;
    private final String operation;
    private final String objects;
    private final String alias;
    private final List<String> expressions;

    /**
     * @param id          the operator's id, 0 for the root
     * @param parent      the id of the operator it feeds, or null for the root
     * @param depth       how many operators stand above it
     * @param operation   what it does
     * @param objects     the object it reads or writes, or null
     * @param alias       the alias it reads the object under, or null
     * @param expressions the expressions it computes, empty for none
     */
    PlanOperator(final int id, final Integer parent, final int depth, final String operation, final String objects,
                 final String alias, final List<String> expressions) {
        this.id = id;
        this.parent = parent;
        this.depth = depth;
        this.operation = operation;
        this.objects = objects;
        this.alias = alias;
        this.expressions = new ArrayList<>(expressions);
    }

    int getId() {
        return id;
    }

    Integer getParent() {
        return parent;
    }

    int getDepth() {
        return depth;
    }

    String getOperation() {
        return operation;
    }

    String getObjects() {
        return objects;
    }

    String getAlias() {
        return alias;
    }

    List<String> getExpressions() {
        return new ArrayList<>(expressions);
    }
}
