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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The walk {@link VirtualColumnUsage#columnsRead} takes: the printer's traversal, noting the name of each column a
 * reference reads.
 */
final class ColumnsReadWalk extends AstPrinterVisitor {

    private final Set<String> read = new LinkedHashSet<>();

    Set<String> read() {
        return read;
    }

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        read.add(expr.getColumnName());
        return super.visitColumnReference(expr);
    }
}
