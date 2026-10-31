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

import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.transaction.SetAsideTransaction;
import dev.frostlake.transaction.Transaction;
import dev.frostlake.transaction.TransactionManager;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DecimalText;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * A query calling a procedure by its SYSTEM$REFERENCE token, {@code SELECT IDENTIFIER($tok)(4)}, which runs the
 * procedure (all live-verified). A query whose one item is the call, written with no FROM, answers as the CALL of it
 * answers: the procedure's value under the procedure's name - the item's alias is not read - a table procedure's rows,
 * the procedure's own failure, and a refusal of the arguments placed at the call ({@code error line 1 at position 7
 * Invalid argument types for function 'PR_ARG': ()}). Anywhere else the call is the procedure's value, the procedure
 * run each time it is read; a table procedure's call there is no value.
 *
 * <p>The account's answers beyond the one-call query are defects of its own and are not mirrored: there the call's
 * value read by an operator or a function is its internal form ({@code Numeric value '{"type":"SCALAR",…}' is not
 * recognized}), a query of more items, of a column argument or of a filter is an internal error, and a call over the
 * rows of a FROM writes for each row but answers one.
 *
 * <p>An EXPLAIN plans the call as the procedure over its parameters' names, {@code PR_ARG(X)}, a table procedure's
 * too, and a query of no FROM that holds one over an empty ValuesClause; nothing runs while it compiles
 * (live-verified).
 */
public final class ProcedureTokenCalls {

    private static final String PROCEDURE = "PROCEDURE";

    private ProcedureTokenCalls() {
    }

    /**
     * The procedure's token a call's IDENTIFIER names.
     *
     * @param executor the executor that resolves the IDENTIFIER's argument
     * @param name     the call's name
     * @return the token, or null where the name is no IDENTIFIER of a procedure's token
     */
    static ObjectReference token(final QueryExecutor executor, final FrostlakeParser.FunctionNameContext name) {
        if (name == null || name.KW_IDENTIFIER_REF() == null || name.identifierArgument() == null) {
            return null;
        }
        try {
            return procedureToken(executor.identifierArgumentText(name.identifierArgument()));
        } catch (final RuntimeException unresolved) {
            return null;
        }
    }

    /**
     * The procedure's token a name's text is.
     *
     * @param text the text an IDENTIFIER resolved to
     * @return the token, or null where it is no procedure's token
     */
    static ObjectReference procedureToken(final String text) {
        final ObjectReference token = text == null ? null : ObjectReferenceTokens.issued(text);
        return token != null && PROCEDURE.equals(token.getDomain()) ? token : null;
    }

    /**
     * Whether a name's text is a procedure's token, a table procedure's among them.
     *
     * @param text the text an IDENTIFIER resolved to
     * @return true where it is one
     */
    public static boolean namesProcedure(final String text) {
        return procedureToken(text) != null;
    }

    /**
     * Whether a select list holds a call of a procedure's token, the queries nested in it left out.
     *
     * @param executor the executor that resolves the calls' IDENTIFIERs
     * @param node     the select list
     * @return true where it holds one
     */
    static boolean holdsCall(final QueryExecutor executor, final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionNameContext
                && token(executor, (FrostlakeParser.FunctionNameContext) node) != null) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsCall(executor, node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The answer of a query whose one item is a call of a procedure's token and that has no FROM: the CALL's.
     *
     * @param executor the executor that runs the call
     * @param query    the query
     * @return the answer, or null for any other query
     */
    public static ResultSet asCall(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext query) {
        // A read of the query's shape alone, as CREATE VIEW reads its body, runs nothing.
        if (RelationShapeOnly.isActive() || query.withClause() != null || query.selectOperand().size() != 1
                || query.orderByClause() != null || query.limitClause() != null || query.fetchClause() != null) {
            return null;
        }
        final FrostlakeParser.SelectClauseContext select = query.selectOperand(0).selectClause();
        if (select == null || select.tableExpression() != null || ParseTreeText.getWhereClause(select) != null
                || select.groupByClause() != null || select.havingClause() != null || select.qualifyClause() != null
                || select.DISTINCT() != null || select.topClause() != null || select.intoClause() != null
                || select.selectList().selectItem().size() != 1
                || !(select.selectList().selectItem(0) instanceof FrostlakeParser.ExprItemContext)) {
            return null;
        }
        final ParseTree call = valueOf(((FrostlakeParser.ExprItemContext) select.selectList().selectItem(0))
            .booleanExpr());
        final FrostlakeParser.FunctionNameContext name = nameOf(call);
        if (name == null || token(executor, name) == null) {
            return null;
        }
        final ParserRuleContext called = (ParserRuleContext) call;
        final Token opening = name.getStop();
        final String arguments = opening.getInputStream().getText(Interval.of(opening.getStopIndex() + 1,
            called.getStop().getStopIndex()));
        final SourcePosition at = new SourcePosition(called.getStart().getLine(),
            called.getStart().getCharPositionInLine());
        final String sql = "CALL IDENTIFIER(" + ParseTreeText.getOriginalText(name.identifierArgument()) + ")"
            + arguments.trim();
        try {
            final List<ResultSet> answered = executor.executeFromUnrecorded(sql, at);
            return answered.isEmpty() ? null : answered.get(0);
        } catch (final RuntimeException refused) {
            throw placed(refused, at);
        }
    }

    /**
     * The value of a call of a procedure's token read anywhere but as a one-call query: the procedure run with the
     * arguments given.
     *
     * @param executor  the executor that runs the procedure
     * @param token     the token's text
     * @param arguments the arguments' values
     * @param names     the parameter each argument is named for, or null where they are written in order
     * @param at        where the call is written, or null
     * @return the procedure's value
     */
    public static Object value(final QueryExecutor executor, final String token, final List<Object> arguments,
                               final List<String> names, final SourcePosition at) {
        final StringBuilder sql = new StringBuilder("CALL IDENTIFIER('").append(token).append("')(");
        for (int i = 0; i < arguments.size(); i++) {
            sql.append(i > 0 ? ", " : "");
            if (names != null && i < names.size() && names.get(i) != null) {
                sql.append(names.get(i)).append(" => ");
            }
            sql.append(literalOf(arguments.get(i)));
        }
        sql.append(')');
        // Run inside a statement's own transaction, the body would end it under the statement: the procedure's work
        // is its own there, as a task's is, and the statement's transaction is the thread's again after it.
        final TransactionManager transactions = executor.getTransactionManager();
        final Transaction current = transactions == null ? null : transactions.getCurrentTransaction();
        final SetAsideTransaction aside = current != null && !current.isExplicit()
            ? transactions.setAsideTransaction() : null;
        final List<ResultSet> answered;
        try {
            answered = executor.executeFromUnrecorded(sql.toString(), at);
        } catch (final RuntimeException refused) {
            throw placed(refused, at);
        } finally {
            if (aside != null) {
                transactions.resumeTransaction(aside);
            }
        }
        final ResultSet answer = answered.isEmpty() ? null : answered.get(0);
        return answer == null || answer.getRowCount() == 0 ? null : answer.getRows().get(0).getValue(0);
    }

    /**
     * Whether a name's text is a procedure's token whose call is a value: one of a procedure answering no table.
     *
     * @param executor the executor whose catalog holds the procedure
     * @param text     the text an IDENTIFIER resolved to
     * @return true where the call is the procedure's value
     */
    public static boolean callsForValue(final QueryExecutor executor, final String text) {
        final ObjectReference token = procedureToken(text);
        return token != null && !returnsTable(executor, token);
    }

    /** Whether the procedure a token stands for answers a table, which no value read of the call can be. */
    private static boolean returnsTable(final QueryExecutor executor, final ObjectReference token) {
        final List<Procedure> overloads = overloads(executor, token);
        for (final Procedure procedure : overloads) {
            if (!procedure.returnsTable()) {
                return false;
            }
        }
        return !overloads.isEmpty();
    }

    /**
     * The type a call of a procedure's token answers: the procedure's RETURNS type, where its name holds one.
     *
     * @param executor the executor whose catalog holds the procedure
     * @param text     the text the call's IDENTIFIER resolved to
     * @return the type, or null where the text is no such token or the overloads answer types of their own
     */
    public static DataType returnType(final QueryExecutor executor, final String text) {
        final ObjectReference token = procedureToken(text);
        DataType type = null;
        final List<Procedure> overloads = token == null ? Collections.<Procedure>emptyList()
            : overloads(executor, token);
        for (final Procedure procedure : overloads) {
            if (procedure.returnsTable()
                    || type != null && !type.toString().equals(procedure.getReturnType().toString())) {
                return null;
            }
            type = procedure.getReturnType();
        }
        return type;
    }

    /**
     * The name a call of a procedure's token answers under: the procedure's.
     *
     * @param executor the executor that resolves the call's IDENTIFIER
     * @param item     a select item's expression
     * @return the name, or null where the item is no such call
     */
    static String nameOfCall(final QueryExecutor executor, final ParserRuleContext item) {
        final ObjectReference token = token(executor, nameOf(valueOf(item)));
        return token == null ? null : token.getParts()[token.getParts().length - 1];
    }

    private static List<Procedure> overloads(final QueryExecutor executor, final ObjectReference token) {
        final String[] parts = token.getParts();
        final Database database = parts.length == 3 ? executor.getCatalog().databaseExact(parts[0]) : null;
        final Schema schema = database == null ? null : database.schemaExact(parts[1]);
        return schema == null ? Collections.<Procedure>emptyList() : schema.getProcedureOverloads(parts[2]);
    }

    /**
     * Refuses an INSERT the account does not run over a call of a procedure's token, before anything runs: an INSERT
     * of a query is {@code Unsupported statement type 'LogicalInsert'.} and a VALUES row holding the call
     * {@code Invalid expression [...] in VALUES clause} (live-verified; the account names a wrapper of its own where
     * this names the call as the plan writes it, {@code PR_ARG(X)}).
     *
     * @param executor the executor that resolves the calls' IDENTIFIERs
     * @param insert   the INSERT
     */
    public static void refuseInInsert(final QueryExecutor executor,
                                      final FrostlakeParser.InsertStatementContext insert) {
        final FrostlakeParser.FunctionNameContext call = firstCall(executor, insert);
        if (call == null) {
            return;
        }
        if (insert.VALUES() != null) {
            throw new RuntimeException(SqlCompilationError.of("Invalid expression [" + planText(executor, call)
                + "] in VALUES clause"));
        }
        throw new RuntimeException(SqlCompilationError.of("Unsupported statement type 'LogicalInsert'."));
    }

    /**
     * Refuses a MERGE over a call of a procedure's token, before anything runs: {@code Unsupported statement type
     * 'Merge'.} (live-verified).
     *
     * @param executor the executor that resolves the calls' IDENTIFIERs
     * @param merge    the MERGE
     */
    public static void refuseInMerge(final QueryExecutor executor, final FrostlakeParser.MergeStatementContext merge) {
        if (firstCall(executor, merge) != null) {
            throw new RuntimeException(SqlCompilationError.of("Unsupported statement type 'Merge'."));
        }
    }

    /** The first call of a procedure's token beneath a node, or null. */
    private static FrostlakeParser.FunctionNameContext firstCall(final QueryExecutor executor, final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionNameContext
                && token(executor, (FrostlakeParser.FunctionNameContext) node) != null) {
            return (FrostlakeParser.FunctionNameContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.FunctionNameContext found = firstCall(executor, node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * A call as the plan writes it: the procedure's name over its parameters' names, {@code PR_TWO(X,Y)}, the
     * overload of as many parameters as the call has arguments.
     */
    private static String planText(final QueryExecutor executor, final FrostlakeParser.FunctionNameContext call) {
        return planText(executor, token(executor, call), argumentCount(call.getParent()));
    }

    /**
     * A call of a procedure's token as the plan writes it, {@code PR_TWO(X,Y)}.
     *
     * @param executor  the executor whose catalog holds the procedure
     * @param text      the text the call's IDENTIFIER resolved to
     * @param arguments how many arguments the call is written with
     * @return the text, or null where the text is no procedure's token
     */
    static String planText(final QueryExecutor executor, final String text, final int arguments) {
        final ObjectReference token = procedureToken(text);
        return token == null ? null : planText(executor, token, arguments);
    }

    private static String planText(final QueryExecutor executor, final ObjectReference token, final int written) {
        final String[] parts = token.getParts();
        Procedure chosen = null;
        for (final Procedure procedure : overloads(executor, token)) {
            if (chosen == null || procedure.getParameters().size() == written) {
                chosen = procedure;
            }
        }
        final List<String> names = new ArrayList<>();
        if (chosen != null) {
            for (final Parameter parameter : chosen.getParameters()) {
                names.add(SqlIdentifiers.spellCanonical(parameter.getName().toUpperCase(Locale.ROOT)));
            }
        }
        return SqlIdentifiers.spellCanonical(parts[parts.length - 1]) + "(" + String.join(",", names) + ")";
    }

    private static int argumentCount(final ParseTree call) {
        if (call instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionArgListContext list = ((FrostlakeParser.FunctionCallExprContext) call)
                .functionArgList();
            return list == null ? 0 : list.functionArg().size();
        }
        if (call instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallNamedArgsExprContext) call).namedArgumentList().namedArgument()
                .size();
        }
        return -1;
    }

    /**
     * The refusal of a TABLE( … ) over a call of a procedure's token: no table function, named by the token
     * ({@code Unknown table function ENT_REF_PROCEDURE_…}, live-verified).
     *
     * @param executor the executor that resolves the call's IDENTIFIER
     * @param name     the call's name
     * @return the refusal, or null where the name is no procedure's token
     */
    public static RuntimeException unknownTableFunction(final QueryExecutor executor,
                                                        final FrostlakeParser.FunctionNameContext name) {
        if (token(executor, name) == null) {
            return null;
        }
        return new RuntimeException(SqlCompilationError.of("Unknown table function "
            + executor.identifierArgumentText(name.identifierArgument())));
    }

    /** A refusal the CALL places nowhere, line 0 at position -1, placed at the call written in the query. */
    private static RuntimeException placed(final RuntimeException refused, final SourcePosition at) {
        final String unplaced = SqlCompilationError.at(0, -1, "");
        final String message = refused.getMessage();
        if (at == null || message == null || !message.startsWith(unplaced)) {
            return refused;
        }
        return new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            message.substring(unplaced.length())), refused.getCause());
    }

    /** A value written as the CALL takes it: a number as written, a text quoted, NULL. */
    private static String literalOf(final Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? "TRUE" : "FALSE";
        }
        if (value instanceof BigDecimal || value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte) {
            return DecimalText.parse(value.toString()).toPlainString();
        }
        final String text = value.toString().replace("\\", "\\\\").replace("'", "''");
        return value instanceof Double || value instanceof Float ? "CAST('" + text + "' AS FLOAT)" : "'" + text + "'";
    }

    /** The name of a call, or null where the node is no call by a name. */
    private static FrostlakeParser.FunctionNameContext nameOf(final ParseTree call) {
        if (call instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext plain = (FrostlakeParser.FunctionCallExprContext) call;
            return plain.overClause() == null && plain.withinGroupClause() == null && plain.DISTINCT() == null
                ? plain.functionName() : null;
        }
        if (call instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            final FrostlakeParser.FunctionCallNamedArgsExprContext named =
                (FrostlakeParser.FunctionCallNamedArgsExprContext) call;
            return named.overClause() == null ? named.functionName() : null;
        }
        return null;
    }

    private static ParseTree valueOf(final ParseTree written) {
        ParseTree node = written;
        while (node instanceof FrostlakeParser.ValueExprContext || node instanceof FrostlakeParser.ParenExprContext) {
            node = node instanceof FrostlakeParser.ValueExprContext
                ? ((FrostlakeParser.ValueExprContext) node).expression()
                : ((FrostlakeParser.ParenExprContext) node).booleanExpr();
        }
        return node;
    }
}
