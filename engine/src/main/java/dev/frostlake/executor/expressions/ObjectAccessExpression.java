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

import java.util.List;

/**
 * Represents object property access (e.g., data:name, json:address:city). The property path is kept as
 * its ordered segments straight from the parse tree — {@code [address, city]} — so navigation never
 * re-splits a flattened string.
 */
public class ObjectAccessExpression implements Expression {
    private final Expression base;
    private final List<String> pathParts;

    public ObjectAccessExpression(final Expression base, final List<String> pathParts) {
        this.base = base;
        this.pathParts = pathParts;
    }

    public Expression getBase() {
        return base;
    }

    /** The property-path segments in order, e.g. {@code [address, city]} for {@code data:address.city}. */
    public List<String> getPathParts() {
        return pathParts;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitObjectAccess(this);
    }

    @Override
    public String toString() {
        return base + ":" + String.join(".", pathParts);
    }
}
