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

public class FunctionCallExpression extends BaseExpression {
    private final String functionName;
    private final List<BaseExpression> arguments;
    /** The quantifier written before the arguments, {@code DISTINCT} or {@code ALL}, or null. */
    private String quantifier;
    /** The text of each argument that reads no name, null where one does; null when none was recorded. */
    private List<String> namelessArguments;

    public FunctionCallExpression(final String functionName, final List<BaseExpression> arguments) {
        this.functionName = functionName;
        this.arguments = arguments;
    }

    public String getFunctionName() {
        return functionName;
    }

    public List<BaseExpression> getArguments() {
        return arguments;
    }

    /**
     * Record the quantifier the call was written with, which the call keeps when it is compiled as SQL: a scalar
     * refuses it there as it refuses it in a query.
     *
     * @param written {@code DISTINCT}, {@code ALL}, or null
     */
    public void describeQuantifier(final String written) {
        this.quantifier = written;
    }

    /** The quantifier written before the arguments, {@code DISTINCT} or {@code ALL}, or null. */
    public String getQuantifier() {
        return quantifier;
    }

    /**
     * Record the text of each argument that reads no name — literals, and the operators, conversions and calls
     * over them — which is how SQL judges the argument when its value alone does not say what was written.
     *
     * @param written the arguments' texts in order, null where an argument reads a name
     */
    public void describeNamelessArguments(final List<String> written) {
        this.namelessArguments = written;
    }

    /**
     * The text of an argument that reads no name.
     *
     * @param index the argument's place, from zero
     * @return the text as written, or null where the argument reads a name or its text was not recorded
     */
    public String getNamelessArgument(final int index) {
        return namelessArguments != null && index < namelessArguments.size() ? namelessArguments.get(index) : null;
    }
}
