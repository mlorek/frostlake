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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.LimitValueSyntax;
import dev.frostlake.types.DataType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A masking or row access policy's body, compiled when the policy is given one.
 *
 * <p>A policy body IS a scalar SQL UDF body — the account compiles it at CREATE and at
 * {@code ALTER … SET BODY}, with the policy's own parameters in scope and nothing else, and refuses
 * it in the UDF's own words: an unknown name is the positioned invalid-identifier sentence counted
 * in the BODY's frame, and a body whose static type is of another family than the declared RETURNS
 * is {@code Declared return type '…' is incompatible with actual return type '…'}. Storing the text
 * unchecked let a policy be created that could only fail later, when a query touched the column it
 * masked.
 *
 * <p>An explicit cast in the body changes the verdict, because the check reads the body's STATIC
 * type: {@code RETURNS STRING -> 1} is refused where {@code RETURNS STRING -> 1::STRING} is created.
 */
final class PolicyBodyCheck {

    private PolicyBodyCheck() {
    }

    /**
     * Compiles a policy body, refusing it exactly as a SQL UDF body of the same shape is refused.
     *
     * @param queryExecutor the executor whose parser and catalog read the body, or null to skip
     * @param catalog       the catalog the body's names resolve against
     * @param policyName    the policy's own name, for the sentences that carry it
     * @param declared      the declared RETURNS type, or null when the statement did not spell one
     * @param parameters    the policy's parameters, the only names the body may use
     * @param body          the body as expression text
     * @param home          the schema the policy lives in
     */
    static void compile(final QueryExecutor queryExecutor, final Catalog catalog,
                        final String policyName, final DataType declared,
                        final List<Parameter> parameters, final String body, final Schema home) {
        requireNumericLimits(body);
        if (queryExecutor == null || body == null || body.trim().isEmpty()) {
            return;
        }
        try {
            RoutineBodyCompiler.compileFunctionBody(queryExecutor, UdfLanguage.SQL, body, false,
                policyName, parameterNames(parameters),
                home == null ? catalog.getCurrentDatabase() : home.getDatabaseName(),
                home == null ? catalog.getCurrentSchema() : home.getName());
            RoutineReturnTypeChecker.checkScalarSqlUdf(queryExecutor, catalog, "SQL", false,
                declared, parameters, body, home);
        } catch (final RuntimeException refused) {
            if (namesAFunctionThisEngineLacks(refused)) {
                return;
            }
            throw refused;
        }
    }

    /**
     * Refuses a LIMIT or OFFSET value written as a string in a policy's body, of any policy kind: the account
     * compiles the body as a SQL UDF's, so the refusal is the UDF's, in the UDF's frame, and comes before anything
     * else the body is compiled for — a masking policy's argument and return types included (live-verified).
     *
     * @param body the body as expression text, or null
     */
    static void requireNumericLimits(final String body) {
        if (body == null || body.trim().isEmpty()) {
            return;
        }
        final FrostlakeParser.BooleanExprContext parsed;
        try {
            parsed = AntlrExpressionParser.parseTree(body);
        } catch (final RuntimeException notAnExpression) {
            // A string body, or one this grammar cannot read whole, is left to the compile that says why.
            return;
        }
        LimitValueSyntax.requireNumericValuesInUdfBody(parsed);
    }

    /**
     * Whether a refusal is only this engine's function set being a SUBSET of the account's.
     *
     * <p>A body calling a function the account has and this engine does not compiles there and cannot
     * compile here, so refusing it would stop a schema from migrating over a function that is not the
     * point of the policy — a real row access policy reads
     * {@code SYS_CONTEXT('SNOWFLAKE$SESSION_ATTRIBUTES', 'TENANT')}. The body compiler already fails
     * open for the bodies it cannot read, for the same reason, and this keeps the two consistent. The
     * measured refusals — a wrong return family, a name the signature does not declare — are unaffected,
     * because neither is spelled this way.
     *
     * @param refused the refusal the body compile raised
     * @return true when it names a function rather than a fault in the body
     */
    private static boolean namesAFunctionThisEngineLacks(final RuntimeException refused) {
        final String message = refused.getMessage();
        return message != null
            && (message.contains("Unknown function ") || message.contains("Unknown user-defined function "));
    }

    /** The parameter names a body may use, upper-cased as the UDF body check expects them. */
    private static Set<String> parameterNames(final List<Parameter> parameters) {
        final Set<String> names = new LinkedHashSet<>();
        if (parameters != null) {
            for (final Parameter parameter : parameters) {
                if (parameter.getName() != null) {
                    names.add(parameter.getName().toUpperCase());
                }
            }
        }
        return names;
    }
}
