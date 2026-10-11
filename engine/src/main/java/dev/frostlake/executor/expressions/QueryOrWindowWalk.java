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
 * Notes whether an expression holds a query or a window call anywhere, a membership in a query and an EXISTS
 * included: with one in it the tree as parsed prints every operator as a call (see {@link ParsedEchoPrinter}).
 */
final class QueryOrWindowWalk extends AstPrinterVisitor {

    private boolean found;

    @Override
    public String visitSubquery(final SubqueryExpression expr) {
        found = true;
        return "";
    }

    @Override
    public String visitWindowFunction(final WindowFunctionExpression expr) {
        found = true;
        return "";
    }

    /** Whether a query or a window call was met. */
    boolean found() {
        return found;
    }
}
