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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.scalar.context.PolicyBodyScope;
import dev.frostlake.functions.table.TagReferencesFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Locale;

/**
 * The two functions that read a tag of the object a policy protects, SYSTEM$GET_TAG_ON_CURRENT_TABLE and
 * SYSTEM$GET_TAG_ON_CURRENT_COLUMN. Outside a policy's body there is no such object and the call is refused while
 * its statement compiles — over a table with no row, in a WHERE clause, in a block and in a statement that only
 * stores the call alike — each in its own sentence, the table's with no blank after its first period, as the
 * account spells it. The tag's name is judged first: a column is no constant, nor is a NULL, and a number is no
 * string. The table's function is a masking policy's and a row access policy's to call, the column's a masking
 * policy's and a projection policy's: the other kind of policy is refused as it is created (all live-verified).
 */
public final class PolicyObjectTagCalls {

    /** The function that reads a tag of the table a policy protects. */
    public static final String ON_CURRENT_TABLE = "SYSTEM$GET_TAG_ON_CURRENT_TABLE";

    /** The function that reads a tag of the column a policy protects. */
    public static final String ON_CURRENT_COLUMN = "SYSTEM$GET_TAG_ON_CURRENT_COLUMN";

    private PolicyObjectTagCalls() {
    }

    /**
     * @param functionName a call's name, in any case
     * @return whether it names one of the two functions
     */
    public static boolean names(final String functionName) {
        return ON_CURRENT_TABLE.equalsIgnoreCase(functionName) || ON_CURRENT_COLUMN.equalsIgnoreCase(functionName);
    }

    /**
     * Refuses a call of one of the two functions that no policy's body makes.
     *
     * @param functionName the call's name, in any case
     * @param tag          the tag's name as written
     */
    public static void judge(final String functionName, final Expression tag) {
        if (PolicyBodyScope.isInside()) {
            return;
        }
        final String name = functionName.toUpperCase(Locale.ROOT);
        if (tag instanceof ColumnReferenceExpression) {
            throw new RuntimeException(SqlCompilationError.of("Invalid value ["
                + SystemConstantArguments.writtenName((ColumnReferenceExpression) tag) + "] for function '" + name
                + "', parameter 0: constant arguments expected"));
        }
        if (isNull(tag)) {
            throw notConstant(name);
        }
        if (tag instanceof LiteralExpression && (((LiteralExpression) tag).getType() == LiteralType.INTEGER
                || ((LiteralExpression) tag).getType() == LiteralType.DECIMAL)) {
            throw new RuntimeException(SqlCompilationError.of("argument needs to be a string: '" + name + "'"));
        }
        throw refusal(name);
    }

    /**
     * Refuses a policy's body that calls the one of the two functions its kind of policy may not.
     *
     * @param body         the body as parsed, or null
     * @param functionName the function the policy's kind may not call
     */
    public static void refuseIn(final ParseTree body, final String functionName) {
        if (body == null) {
            return;
        }
        if (body instanceof FrostlakeParser.SystemFuncExprContext && functionName.equalsIgnoreCase(
                ((FrostlakeParser.SystemFuncExprContext) body).SYSTEM_FUNC().getText())) {
            throw refusal(functionName);
        }
        for (int i = 0; i < body.getChildCount(); i++) {
            refuseIn(body.getChild(i), functionName);
        }
    }

    /**
     * Judges the tags a policy's body reads off what it protects, as the policy is created: a NULL for a tag's name
     * is no constant, and a tag that is not there is refused as any statement naming it is (live-verified).
     *
     * @param body    the body as parsed, or null
     * @param catalog the catalog the tags are looked up in
     */
    public static void requireTagsIn(final ParseTree body, final Catalog catalog) {
        if (body == null) {
            return;
        }
        if (body instanceof FrostlakeParser.SystemFuncExprContext
                && names(((FrostlakeParser.SystemFuncExprContext) body).SYSTEM_FUNC().getText())) {
            final FrostlakeParser.SystemFuncExprContext call = (FrostlakeParser.SystemFuncExprContext) body;
            final Expression parsed = new ExpressionAstBuilder().visit(call);
            if (parsed instanceof FunctionCallExpression
                    && ((FunctionCallExpression) parsed).getArguments().size() == 1) {
                final Expression tag = ((FunctionCallExpression) parsed).getArguments().get(0);
                if (isNull(tag)) {
                    throw notConstant(call.SYSTEM_FUNC().getText());
                }
                if (tag instanceof LiteralExpression && ((LiteralExpression) tag).getType() == LiteralType.STRING) {
                    TagReferencesFunction.requireTag(catalog, String.valueOf(((LiteralExpression) tag).getValue()));
                }
            }
            return;
        }
        for (int i = 0; i < body.getChildCount(); i++) {
            requireTagsIn(body.getChild(i), catalog);
        }
    }

    private static boolean isNull(final Expression tag) {
        final Expression value = tag instanceof CastExpression ? ((CastExpression) tag).getExpression() : tag;
        return value instanceof LiteralExpression && ((LiteralExpression) value).getType() == LiteralType.NULL;
    }

    private static RuntimeException notConstant(final String functionName) {
        return new RuntimeException(SqlCompilationError.of("argument 0 to function "
            + functionName.toUpperCase(Locale.ROOT) + " needs to be constant, found 'null'"));
    }

    /**
     * @param functionName the call's name, in any case
     * @return the refusal of the call where its kind of policy is not being evaluated
     */
    public static PolicyOnlyCallRefusal refusal(final String functionName) {
        return new PolicyOnlyCallRefusal(ON_CURRENT_TABLE.equalsIgnoreCase(functionName)
            ? "Invalid system$get_tag_on_current_table function invocation.It can only be invoked from a masking"
                + " policy/row access policy."
            : "Invalid system$get_tag_on_current_column function invocation. It can only be invoked from a masking"
                + " or projection policy.");
    }
}
