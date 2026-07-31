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
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.List;

/**
 * Checks a SQL UDF body that is a Snowflake-Scripting block against the restricted language Snowflake
 * calls a <em>Snowscript UDF</em>. Such a body is NOT the full scripting language a stored procedure
 * body gets: it is control flow plus scalar expressions and nothing else, so a function can never have a
 * side effect and can never reach the tables.
 *
 * <p>Live-verified on a real account, each rejection reproducing Snowflake's own message:
 * <ul>
 *   <li>{@code Unsupported statement type for Snowscript UDF: query statement} — any plain SQL statement
 *       in the body: {@code SELECT 1}, {@code INSERT INTO t VALUES (99)}, {@code UPDATE t SET v = 0},
 *       {@code CREATE TABLE zz (a INT)}, {@code EXECUTE IMMEDIATE '…'} and {@code CALL someproc()};</li>
 *   <li>{@code … : select into statement} — {@code SELECT COUNT(*) INTO x FROM t};</li>
 *   <li>{@code … : cursor declaration} — {@code DECLARE c CURSOR FOR SELECT …};</li>
 *   <li>{@code Unsupported expression for Snowscript UDF: <expr>} — any subquery
 *       ({@code RETURN (SELECT COUNT(*) FROM t)}, {@code EXISTS (SELECT …)}, {@code FOR r IN (SELECT …)},
 *       with a {@code :name} bind or a bare one alike), and any call of a user-defined function —
 *       including the routine calling ITSELF, so a Snowscript UDF cannot recurse.</li>
 * </ul>
 *
 * <p>Control flow is fully available and was live-verified to work: {@code DECLARE}/{@code LET}
 * variables, assignment, {@code IF}, {@code CASE}, {@code WHILE}, {@code REPEAT}, {@code FOR i IN a TO b},
 * {@code LOOP} with {@code BREAK}, nested {@code BEGIN … END}, {@code EXCEPTION} handlers and
 * {@code RAISE}, plus built-in scalar functions ({@code ABS}, {@code IFF}, {@code DATEADD},
 * {@code PARSE_JSON}, {@code CURRENT_DATABASE}, …).
 *
 * <p>Like the rest of {@link RoutineBodyCompiler}, this FAILS OPEN: only a construct the engine can
 * positively read and knows Snowflake refuses is rejected. A call of a name the catalog does not hold is
 * left alone (Snowflake rejects it too, but as a Frostlake grammar subset an unknown name is more likely
 * a built-in this engine has not modelled than a genuine error).
 */
final class ScriptingUdfBodyValidator {

    private final QueryExecutor queryExecutor;
    private final Catalog catalog;
    private final String routineName;

    /**
     * @param queryExecutor decides, with the engine's own parser, whether a stored body is a block.
     * @param catalog       resolves whether a called name is a user-defined function; may be null.
     * @param routineName   the unqualified name of the routine being created, so a self-call is caught even
     *                      on a first {@code CREATE} where the catalog does not hold it yet.
     */
    ScriptingUdfBodyValidator(final QueryExecutor queryExecutor, final Catalog catalog,
                              final String routineName) {
        this.queryExecutor = queryExecutor;
        this.catalog = catalog;
        this.routineName = routineName;
    }

    /** Walk the whole block, throwing on the first construct Snowflake refuses in a Snowscript UDF. */
    void validate(final FrostlakeParser.BeginEndBlockContext block) {
        walk(block);
    }

    /**
     * The rule from the other side: a NON-block UDF body — an expression or a query — may not call a
     * block-bodied one either. Live-verified: with {@code blk} a Snowscript UDF, both
     * {@code CREATE FUNCTION e(n INT) RETURNS INT AS 'blk(n) + 1'} and the query-body form
     * {@code AS 'SELECT blk(n) + 1'} fail at CREATE, reporting the INNER block's text as a syntax error
     * ({@code Compilation of SQL UDF failed: … syntax error line 1 at position 8 unexpected 'RETURN'}).
     * A view, a stored procedure, a WHERE clause and {@code INSERT … SELECT} may all call one freely — it
     * is composition with another UDF, in either direction, that Snowflake refuses.
     */
    void rejectScriptingUdfCalls(final ParseTree body) {
        if (body == null) {
            return;
        }
        if (body instanceof FrostlakeParser.FunctionNameContext) {
            final FrostlakeParser.FunctionNameContext name = (FrostlakeParser.FunctionNameContext) body;
            if (name.identifier() != null && !name.identifier().isEmpty()) {
                final String called = text(name.identifier().get(name.identifier().size() - 1));
                if (hasScriptingBody(name, called)) {
                    throw new RuntimeException("Compilation of SQL UDF failed: SQL compilation error: "
                        + "a SQL UDF body cannot call " + called.toUpperCase()
                        + ", whose own body is a Snowflake Scripting block.");
                }
            }
        }
        for (int i = 0; i < body.getChildCount(); i++) {
            rejectScriptingUdfCalls(body.getChild(i));
        }
    }

    /** Whether every overload the catalog holds for {@code called} has a scripting-block body. */
    private boolean hasScriptingBody(final FrostlakeParser.FunctionNameContext name, final String called) {
        final Schema schema = resolveSchema(name);
        if (schema == null) {
            return false;
        }
        final List<Function> overloads = schema.getFunctionOverloads(called);
        if (overloads.isEmpty()) {
            return false;
        }
        for (final Function overload : overloads) {
            if (overload.getUdfLanguage() != UdfLanguage.SQL || !isBlock(overload.getBody())) {
                return false;
            }
        }
        return true;
    }

    /** Whether a stored body text is a {@code BEGIN … END} block, decided by the engine's own parser. */
    private boolean isBlock(final String body) {
        return body != null && queryExecutor != null && queryExecutor.isProceduralBlock(body.trim());
    }

    private void walk(final ParseTree node) {
        if (node instanceof FrostlakeParser.StatementContext) {
            final FrostlakeParser.StatementContext stmt = (FrostlakeParser.StatementContext) node;
            if (stmt.proceduralStatement() == null) {
                // Any non-procedural statement — DML, DDL, a bare query, SHOW … — is where a side effect
                // would come from, and Snowflake reports every one of them as "query statement".
                throw unsupportedStatement("query statement");
            }
        }
        if (node instanceof FrostlakeParser.ProceduralStatementContext) {
            checkProceduralStatement((FrostlakeParser.ProceduralStatementContext) node);
        }
        if (node instanceof FrostlakeParser.DeclarationItemContext) {
            if (((FrostlakeParser.DeclarationItemContext) node).CURSOR() != null) {
                throw unsupportedStatement("cursor declaration");
            }
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            // Reached only from inside an expression or a declaration — a query STATEMENT was already
            // rejected above — so this is a subquery, which a Snowscript UDF may not contain.
            throw unsupportedExpression((ParserRuleContext) node);
        }
        if (node instanceof FrostlakeParser.FunctionNameContext) {
            checkFunctionName((FrostlakeParser.FunctionNameContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i));
        }
    }

    private void checkProceduralStatement(final FrostlakeParser.ProceduralStatementContext stmt) {
        if (stmt.selectIntoStatement() != null) {
            throw unsupportedStatement("select into statement");
        }
        if (stmt.executeImmediateStatement() != null || stmt.callStatement() != null) {
            // Both run arbitrary SQL, so Snowflake groups them with the plain statements it refuses.
            throw unsupportedStatement("query statement");
        }
        if (stmt.openStatement() != null || stmt.fetchStatement() != null || stmt.closeStatement() != null) {
            throw unsupportedStatement("cursor declaration");
        }
    }

    /** Reject a call of a user-defined function — Snowscript UDFs may call built-ins only, never a UDF. */
    private void checkFunctionName(final FrostlakeParser.FunctionNameContext name) {
        if (name.identifier() == null || name.identifier().isEmpty()) {
            return;   // IDENTIFIER(expr) — the name is only known at run time
        }
        final String called = text(name.identifier().get(name.identifier().size() - 1));
        if (routineName != null && name.identifier().size() == 1 && called.equalsIgnoreCase(routineName)) {
            throw unsupportedExpression(callSite(name));
        }
        if (isUserDefinedFunction(name, called)) {
            throw unsupportedExpression(callSite(name));
        }
    }

    /** Whether {@code called} names a UDF held by the schema the call resolves against. */
    private boolean isUserDefinedFunction(final FrostlakeParser.FunctionNameContext name, final String called) {
        final Schema schema = resolveSchema(name);
        return schema != null && !schema.getFunctionOverloads(called).isEmpty();
    }

    /**
     * The schema a call resolves against, exactly as the evaluator resolves one: a qualified name against
     * the schema it names, a bare one against the current schema. Null when nothing resolves — an
     * unresolvable database/schema is "unknown", not "invalid", so the checks above fail open.
     */
    private Schema resolveSchema(final FrostlakeParser.FunctionNameContext name) {
        if (catalog == null || catalog.getCurrentDatabase() == null) {
            return null;
        }
        final int parts = name.identifier().size();
        final String databaseName = parts == 3 ? text(name.identifier().get(0)) : catalog.getCurrentDatabase();
        final String schemaName = parts >= 2 ? text(name.identifier().get(parts - 2)) : catalog.getCurrentSchema();
        if (schemaName == null) {
            return null;
        }
        try {
            final Database database = catalog.getDatabase(databaseName);
            return database.getSchema(schemaName);
        } catch (final RuntimeException noSuchContainer) {
            return null;
        }
    }

    /** The enclosing expression of a function name, so the message quotes the call and not just the name. */
    private ParserRuleContext callSite(final FrostlakeParser.FunctionNameContext name) {
        return name.getParent() instanceof ParserRuleContext ? (ParserRuleContext) name.getParent() : name;
    }

    private RuntimeException unsupportedStatement(final String kind) {
        return new RuntimeException("Unsupported statement type for Snowscript UDF: " + kind);
    }

    private RuntimeException unsupportedExpression(final ParserRuleContext ctx) {
        return new RuntimeException("Unsupported expression for Snowscript UDF: " + text(ctx));
    }

    /** The context's ORIGINAL source text, so the message reads like the body the user wrote. */
    private String text(final ParserRuleContext ctx) {
        if (ctx.start == null || ctx.stop == null || ctx.start.getInputStream() == null) {
            return ctx.getText();
        }
        return ctx.start.getInputStream()
            .getText(new Interval(ctx.start.getStartIndex(), ctx.stop.getStopIndex()));
    }
}
