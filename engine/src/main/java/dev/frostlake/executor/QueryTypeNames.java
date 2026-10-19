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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * The QUERY_TYPE the query history lists a statement under, in the account's own words (live-verified). Most
 * kinds name the statement's family — SELECT, INSERT, SHOW, DESCRIBE, USE, CALL, DROP, ALTER, CREATE — and a few
 * name what it does in full:
 *
 * <ul>
 *   <li>CREATE_TABLE for a table with columns, a LIKE or a CLONE, and CREATE_TABLE_AS_SELECT for one created from a
 *       query and for a materialized view; CREATE_VIEW, CREATE_SEQUENCE and CREATE_STREAM;</li>
 *   <li>RENAME_TABLE, RENAME_COLUMN, RENAME_VIEW and RENAME_SCHEMA; ALTER_TABLE_ADD_COLUMN,
 *       ALTER_TABLE_DROP_COLUMN and ALTER_TABLE_MODIFY_COLUMN, which a COMMENT ON COLUMN is too;
 *       ALTER_TABLE_DROP_CLUSTERING_KEY, {@code CREATE CONSTRAINT} (with a space) for an added constraint,
 *       ALTER_TABLE for a clustering key, ALTER_VIEW_MODIFY_SECURITY and ALTER_SESSION, while a property set or
 *       unset, a comment and a swap are ALTER;</li>
 *   <li>SELECT_FOR_UPDATE, TRUNCATE_TABLE, RESTORE for an UNDROP, DROP_STREAM, LIST_FILES and REMOVE_FILES, and
 *       BEGIN for every way of opening a transaction;</li>
 *   <li>a block, whether sent as it is or through EXECUTE IMMEDIATE, is a CALL, and an EXECUTE IMMEDIATE of a
 *       statement's text is that statement's kind.</li>
 * </ul>
 *
 * A statement the parser cannot read is UNKNOWN.
 */
final class QueryTypeNames {

    /** The kind of a statement that could not be read. */
    static final String UNKNOWN = "UNKNOWN";

    private QueryTypeNames() {
    }

    /**
     * The kind a statement is listed under.
     *
     * @param statement the statement
     * @param executor  the executor, which parses the text an EXECUTE IMMEDIATE runs
     * @return the kind
     */
    static String of(final FrostlakeParser.StatementContext statement, final QueryExecutor executor) {
        if (statement == null) {
            return UNKNOWN;
        }
        if (statement.queryStatement() != null) {
            return statement.queryStatement().forUpdateClause() != null ? "SELECT_FOR_UPDATE" : "SELECT";
        }
        if (statement.dmlStatement() != null) {
            return dml(statement.dmlStatement());
        }
        if (statement.ddlStatement() != null) {
            return ddl(statement.ddlStatement());
        }
        if (statement.explainStatement() != null) {
            return "EXPLAIN";
        }
        if (statement.transactionStatement() != null) {
            final FrostlakeParser.TransactionStatementContext transaction = statement.transactionStatement();
            return transaction.COMMIT() != null ? "COMMIT" : transaction.ROLLBACK() != null ? "ROLLBACK" : "BEGIN";
        }
        if (statement.listStatement() != null) {
            return "LIST_FILES";
        }
        if (statement.removeStatement() != null) {
            return "REMOVE_FILES";
        }
        if (statement.getStatement() != null) {
            return "GET_FILES";
        }
        if (statement.putStatement() != null) {
            return "PUT_FILES";
        }
        if (statement.sessionSetStatement() != null) {
            return "SET";
        }
        if (statement.sessionUnsetStatement() != null) {
            return "UNSET";
        }
        if (statement.proceduralStatement() != null) {
            return procedural(statement.proceduralStatement(), executor);
        }
        if (statement.showStatement() != null || statement.showClassStatement() != null
                || statement.securityObjectListing() != null) {
            return "SHOW";
        }
        if (statement.describeStatement() != null) {
            return "DESCRIBE";
        }
        return byFirstWord(statement);
    }

    private static String dml(final FrostlakeParser.DmlStatementContext dml) {
        if (dml.updateStatement() != null) {
            return "UPDATE";
        }
        if (dml.deleteStatement() != null) {
            return "DELETE";
        }
        if (dml.mergeStatement() != null) {
            return "MERGE";
        }
        if (dml.copyIntoStatement() != null) {
            return dml.copyIntoStatement().qualifiedName() != null ? "COPY" : "UNLOAD";
        }
        return "INSERT";
    }

    private static String ddl(final FrostlakeParser.DdlStatementContext ddl) {
        if (ddl.createStatement() != null) {
            return create(ddl.createStatement());
        }
        if (ddl.alterStatement() != null) {
            return alter(ddl.alterStatement());
        }
        if (ddl.dropStatement() != null) {
            return ddl.dropStatement().STREAM() != null ? "DROP_STREAM" : "DROP";
        }
        if (ddl.undropStatement() != null) {
            return "RESTORE";
        }
        if (ddl.useStatement() != null) {
            return "USE";
        }
        if (ddl.commentStatement() != null) {
            return ddl.commentStatement().COLUMN() != null ? "ALTER_TABLE_MODIFY_COLUMN" : "ALTER";
        }
        if (ddl.truncateStatement() != null) {
            return "TRUNCATE_TABLE";
        }
        return byFirstWord(ddl);
    }

    private static String create(final FrostlakeParser.CreateStatementContext create) {
        if (create.FUNCTION() != null || create.PROCEDURE() != null || create.DYNAMIC() != null) {
            return "CREATE";
        }
        if (create.VIEW() != null) {
            return create.MATERIALIZED() != null ? "CREATE_TABLE_AS_SELECT" : "CREATE_VIEW";
        }
        if (create.TABLE() != null) {
            return create.selectStatement() != null && create.TEMPLATE() == null ? "CREATE_TABLE_AS_SELECT"
                : "CREATE_TABLE";
        }
        if (create.STREAM() != null) {
            return "CREATE_STREAM";
        }
        if (create.SEQUENCE() != null) {
            return "CREATE_SEQUENCE";
        }
        return "CREATE";
    }

    private static String alter(final FrostlakeParser.AlterStatementContext alter) {
        if (alter.SESSION() != null) {
            return "ALTER_SESSION";
        }
        if (alter.tableAction() != null) {
            return tableAction(alter.tableAction());
        }
        if (alter.schemaAction() != null && alter.schemaAction().RENAME() != null) {
            return "RENAME_SCHEMA";
        }
        if (alter.viewAction() != null) {
            if (alter.viewAction().RENAME() != null) {
                return "RENAME_VIEW";
            }
            if (alter.viewAction().SECURE() != null) {
                return "ALTER_VIEW_MODIFY_SECURITY";
            }
        }
        return "ALTER";
    }

    private static String tableAction(final FrostlakeParser.TableActionContext action) {
        if (action.wholeObjectName() != null) {
            return "RENAME_TABLE";
        }
        if (action.RENAME() != null) {
            return action.COLUMN() != null ? "RENAME_COLUMN" : "ALTER_TABLE";
        }
        if (action.SWAP() != null || action.COMMENT() != null && action.SET() != null
                || !action.tableSetProperty().isEmpty() || action.tableUnsetProperties() != null) {
            return "ALTER";
        }
        if (action.columnDef() != null) {
            return "ALTER_TABLE_ADD_COLUMN";
        }
        if (!action.alterColumnItem().isEmpty()) {
            return "ALTER_TABLE_MODIFY_COLUMN";
        }
        if (action.tableConstraint() != null) {
            return "CREATE CONSTRAINT";
        }
        if (action.CLUSTERING() != null && action.DROP() != null) {
            return "ALTER_TABLE_DROP_CLUSTERING_KEY";
        }
        if (action.SEARCH() != null && action.ADD() != null) {
            return "CREATE_TABLE_AS_SELECT";
        }
        if (action.DROP() != null && action.CONSTRAINT() == null && action.PRIMARY() == null
                && action.UNIQUE() == null && action.FOREIGN() == null && action.SEARCH() == null
                && action.ROW() == null && action.DATA() == null && !action.identifier().isEmpty()) {
            return "ALTER_TABLE_DROP_COLUMN";
        }
        return "ALTER_TABLE";
    }

    private static String procedural(final FrostlakeParser.ProceduralStatementContext procedural,
                                     final QueryExecutor executor) {
        if (procedural.executeImmediateStatement() != null) {
            return immediate(procedural.executeImmediateStatement(), executor);
        }
        if (procedural.selectIntoStatement() != null) {
            return "SELECT";
        }
        if (procedural.setStatement() != null) {
            return "SET";
        }
        // A block, and a call, runs as a procedure does.
        return "CALL";
    }

    /**
     * The statement a statement runs: the one an EXECUTE IMMEDIATE's written text holds, else the statement itself.
     *
     * @param statement the statement, or null
     * @param executor  the executor, which parses the text
     * @return the statement it runs, or null when none was read
     */
    static FrostlakeParser.StatementContext effective(final FrostlakeParser.StatementContext statement,
                                                      final QueryExecutor executor) {
        if (statement == null || statement.proceduralStatement() == null
                || statement.proceduralStatement().executeImmediateStatement() == null) {
            return statement;
        }
        final String text = writtenText(statement.proceduralStatement().executeImmediateStatement());
        final FrostlakeParser.StatementContext inner = text == null ? null : executor.firstStatementOf(text);
        return inner == null ? statement : effective(inner, executor);
    }

    /** The text an EXECUTE IMMEDIATE runs when it is written out, or null. */
    private static String writtenText(final FrostlakeParser.ExecuteImmediateStatementContext immediate) {
        if (immediate.expression() == null) {
            return null;
        }
        final Expression text;
        try {
            text = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(immediate.expression()));
        } catch (final RuntimeException unreadable) {
            return null;
        }
        return text instanceof LiteralExpression && ((LiteralExpression) text).getValue() instanceof String
            ? (String) ((LiteralExpression) text).getValue() : null;
    }

    /** An EXECUTE IMMEDIATE of a written text is the kind of the statement the text holds. */
    private static String immediate(final FrostlakeParser.ExecuteImmediateStatementContext immediate,
                                    final QueryExecutor executor) {
        final String text = writtenText(immediate);
        if (text == null) {
            return "CALL";
        }
        final FrostlakeParser.StatementContext inner = executor.firstStatementOf(text);
        return inner == null ? UNKNOWN : of(inner, executor);
    }

    /** The kind of a statement of a family this list does not name in full: its leading word's family. */
    private static String byFirstWord(final ParserRuleContext statement) {
        switch (statement.getStart().getType()) {
            case FrostlakeParser.CREATE:
                return "CREATE";
            case FrostlakeParser.ALTER:
                return "ALTER";
            case FrostlakeParser.DROP:
                return "DROP";
            case FrostlakeParser.GRANT:
                return "GRANT";
            case FrostlakeParser.REVOKE:
                return "REVOKE";
            case FrostlakeParser.SHOW:
                return "SHOW";
            case FrostlakeParser.DESCRIBE:
            case FrostlakeParser.DESC:
                return "DESCRIBE";
            case FrostlakeParser.USE:
                return "USE";
            default:
                return UNKNOWN;
        }
    }
}
