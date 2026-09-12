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

/**
 * Whether an expression reads anything from the ROW — the question behind Snowflake's
 * "argument N to function F needs to be constant" refusals.
 *
 * <p>Constant there means row-INDEPENDENT rather than literal: {@code BASE64_ENCODE(g, 0 - 1)} is
 * accepted live and {@code BASE64_ENCODE(g, n)} is refused, so an arithmetic expression over literals
 * counts as constant while a column reference does not. Only the column reference is looked for,
 * because that is the only shape measured — inventing a refusal for a subquery or a session variable
 * would refuse SQL a real account may well run.
 */
final class RelationReferenceWalk extends AstPrinterVisitor {

    private boolean found;

    @Override
    public String visitColumnReference(final ColumnReferenceExpression expr) {
        found = true;
        return super.visitColumnReference(expr);
    }

    boolean found() {
        return found;
    }
}
