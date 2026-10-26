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

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The kind the account names a SQL statement by when it refuses it inside a Snowscript UDF's body:
 * {@code Unsupported statement type for Snowscript UDF: CREATE_TABLE}. The names are the account's own, one for
 * each kind of statement it tells apart (all live-verified):
 * <ul>
 *   <li>a query in any form, an EXPLAIN and a CALL are {@code SELECT}, a query that locks its rows
 *       {@code SELECT_FOR_UPDATE};</li>
 *   <li>{@code INSERT} (a multi-table one and a COPY INTO a table too), {@code UPDATE}, {@code DELETE},
 *       {@code MERGE}, {@code UNLOAD} for a COPY INTO a location, {@code TRUNCATE_TABLE};</li>
 *   <li>a CREATE by what it creates — {@code CREATE_TABLE}, {@code CREATE_TABLE_AS_SELECT} (a table created from
 *       a query, a materialized view and a dynamic table), {@code CREATE_EVENT_TABLE},
 *       {@code CREATE_ICEBERG_TABLE}, {@code CREATE_VIEW}, {@code CREATE_SEQUENCE}, {@code CREATE_STREAM},
 *       {@code CREATE_TAG}, {@code CREATE_TASK}, {@code CREATE_MASKING_POLICY},
 *       {@code CREATE_ROW_ACCESS_POLICY} — and plain {@code CREATE} for the rest: a schema, a function, a
 *       procedure, a stage, a file format;</li>
 *   <li>{@code DROP} for every kind but a tag's {@code DROP_TAG}, {@code RESTORE} for an UNDROP;</li>
 *   <li>an ALTER TABLE by its action — {@code ALTER_TABLE_ADD_COLUMN}, {@code ALTER_TABLE_DROP_COLUMN},
 *       {@code ALTER_TABLE_MODIFY_COLUMN}, {@code RENAME_TABLE}, {@code RENAME_COLUMN},
 *       {@code CREATE_CONSTRAINT}, {@code DROP_CONSTRAINT}, {@code ALTER_TABLE} for a new clustering key,
 *       {@code ALTER_TABLE_DROP_CLUSTERING_KEY}, {@code ALTER_AUTO_RECLUSTER}, {@code ALTER_SET_TAG},
 *       {@code ALTER_TABLE_MANAGE_ROW_ACCESS_POLICY}, and {@code CREATE_TABLE_AS_SELECT} for a search
 *       optimization added;</li>
 *   <li>a rename by what is renamed — {@code RENAME_VIEW}, {@code RENAME_FUNCTION}, {@code RENAME_SEQUENCE},
 *       {@code RENAME_STAGE}, {@code RENAME_FILE_FORMAT}, {@code RENAME_SCHEMA} — and plain {@code RENAME} for a
 *       procedure, a tag and a constraint;</li>
 *   <li>{@code ALTER_VIEW_MODIFY_SECURITY}, {@code ALTER_MATERIALIZED_VIEW_LIFECYCLE} and
 *       {@code ALTER_DYNAMIC_TABLE_LIFECYCLE} for a view made secure and a suspended or resumed materialized view
 *       or dynamic table, and plain {@code ALTER} for every other ALTER and for a COMMENT ON — but a column's
 *       comment is {@code ALTER_TABLE_MODIFY_COLUMN};</li>
 *   <li>{@code SHOW}, {@code DESCRIBE}, {@code USE}, {@code SET}, {@code UNSET}, {@code LIST_FILES}.</li>
 * </ul>
 * A statement of a kind that was not measured keeps the older sentence, {@code query statement}.
 */
final class SnowscriptStatementKind {

    /** What a statement of no measured kind is named. */
    static final String UNMEASURED = "query statement";

    private SnowscriptStatementKind() {
    }

    /**
     * @param statement a statement of a function's body that is no scripting statement
     * @return the account's name for its kind
     */
    static String of(final FrostlakeParser.StatementContext statement) {
        if (statement.queryStatement() != null) {
            return statement.queryStatement().forUpdateClause() != null ? "SELECT_FOR_UPDATE" : "SELECT";
        }
        if (statement.explainStatement() != null) {
            return "SELECT";
        }
        if (statement.showStatement() != null || statement.showClassStatement() != null
                || statement.securityObjectListing() != null) {
            return "SHOW";
        }
        if (statement.describeStatement() != null) {
            return "DESCRIBE";
        }
        if (statement.listStatement() != null) {
            return "LIST_FILES";
        }
        if (statement.sessionSetStatement() != null) {
            return "SET";
        }
        if (statement.sessionUnsetStatement() != null) {
            return "UNSET";
        }
        if (statement.dmlStatement() != null) {
            return ofDml(statement.dmlStatement());
        }
        if (statement.ddlStatement() != null) {
            return ofDdl(statement.ddlStatement());
        }
        return ofOpeningWord(statement);
    }

    private static String ofDml(final FrostlakeParser.DmlStatementContext dml) {
        if (dml.insertStatement() != null || dml.multiTableInsertStatement() != null) {
            return "INSERT";
        }
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
            // A COPY INTO a table loads it as an INSERT does; one into a location unloads.
            return dml.copyIntoStatement().qualifiedName() != null ? "INSERT" : "UNLOAD";
        }
        return UNMEASURED;
    }

    private static String ofDdl(final FrostlakeParser.DdlStatementContext ddl) {
        if (ddl.createStatement() != null) {
            return ofCreate(ddl.createStatement());
        }
        if (ddl.alterStatement() != null) {
            return ofAlter(ddl.alterStatement());
        }
        if (ddl.dropStatement() != null) {
            return ddl.dropStatement().TAG() != null ? "DROP_TAG" : "DROP";
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
        return ofOpeningWord(ddl);
    }

    private static String ofCreate(final FrostlakeParser.CreateStatementContext create) {
        if (create.TABLE() != null) {
            if (create.EVENT() != null) {
                return "CREATE_EVENT_TABLE";
            }
            if (create.DYNAMIC() != null) {
                return "CREATE_TABLE_AS_SELECT";
            }
            if (create.ICEBERG() != null) {
                return "CREATE_ICEBERG_TABLE";
            }
            return create.selectStatement() == null || create.TEMPLATE() != null
                ? "CREATE_TABLE" : "CREATE_TABLE_AS_SELECT";
        }
        if (create.VIEW() != null) {
            return create.MATERIALIZED() != null ? "CREATE_TABLE_AS_SELECT" : "CREATE_VIEW";
        }
        if (create.SEQUENCE() != null) {
            return "CREATE_SEQUENCE";
        }
        if (create.STREAM() != null) {
            return "CREATE_STREAM";
        }
        if (create.TASK() != null) {
            return "CREATE_TASK";
        }
        if (create.TAG() != null) {
            return "CREATE_TAG";
        }
        if (create.MASKING() != null) {
            return "CREATE_MASKING_POLICY";
        }
        if (create.ROW() != null && create.ACCESS() != null) {
            return "CREATE_ROW_ACCESS_POLICY";
        }
        return "CREATE";
    }

    private static String ofAlter(final FrostlakeParser.AlterStatementContext alter) {
        if (alter.tableAction() != null) {
            return ofTableAction(alter.tableAction());
        }
        if (alter.viewAction() != null) {
            final FrostlakeParser.ViewActionContext action = alter.viewAction();
            if (action.RENAME() != null) {
                return "RENAME_VIEW";
            }
            return action.SECURE() != null ? "ALTER_VIEW_MODIFY_SECURITY" : "ALTER";
        }
        final ParserRuleContext action = actionOf(alter);
        final int opening = action == null || action.getStart() == null ? -1 : action.getStart().getType();
        if (opening == FrostlakeParser.RENAME) {
            if (alter.FUNCTION() != null) {
                return "RENAME_FUNCTION";
            }
            if (alter.SEQUENCE() != null) {
                return "RENAME_SEQUENCE";
            }
            if (alter.STAGE() != null) {
                return "RENAME_STAGE";
            }
            if (alter.FILE() != null) {
                return "RENAME_FILE_FORMAT";
            }
            if (alter.SCHEMA() != null) {
                return "RENAME_SCHEMA";
            }
            return "RENAME";
        }
        if ((opening == FrostlakeParser.SUSPEND || opening == FrostlakeParser.RESUME)
                && action.getStart() == action.getStop()) {
            if (alter.MATERIALIZED() != null) {
                return "ALTER_MATERIALIZED_VIEW_LIFECYCLE";
            }
            if (alter.DYNAMIC() != null) {
                return "ALTER_DYNAMIC_TABLE_LIFECYCLE";
            }
        }
        return "ALTER";
    }

    private static String ofTableAction(final FrostlakeParser.TableActionContext action) {
        if (action.RENAME() != null) {
            if (action.CONSTRAINT() != null) {
                return "RENAME";
            }
            return action.COLUMN() != null ? "RENAME_COLUMN" : "RENAME_TABLE";
        }
        if (action.ROW() != null && action.ACCESS() != null) {
            return "ALTER_TABLE_MANAGE_ROW_ACCESS_POLICY";
        }
        if (action.RECLUSTER() != null) {
            return action.SUSPEND() != null || action.RESUME() != null ? "ALTER_AUTO_RECLUSTER" : "RECLUSTER";
        }
        if (action.CLUSTERING() != null) {
            return "ALTER_TABLE_DROP_CLUSTERING_KEY";
        }
        if (action.CLUSTER() != null) {
            return "ALTER_TABLE";
        }
        if (action.tagSet() != null) {
            return "ALTER_SET_TAG";
        }
        if (action.ADD() != null) {
            if (action.tableConstraint() != null) {
                return "CREATE_CONSTRAINT";
            }
            if (action.SEARCH() != null) {
                return "CREATE_TABLE_AS_SELECT";
            }
            return action.columnDef() != null ? "ALTER_TABLE_ADD_COLUMN" : "ALTER";
        }
        if (action.DROP() != null) {
            if (action.CONSTRAINT() != null || action.PRIMARY() != null || action.UNIQUE() != null
                    || action.FOREIGN() != null) {
                return "DROP_CONSTRAINT";
            }
            return action.SEARCH() != null || action.DATA() != null ? "ALTER" : "ALTER_TABLE_DROP_COLUMN";
        }
        if ((action.ALTER() != null || action.MODIFY() != null) && action.CONSTRAINT() == null
                && action.DATA() == null) {
            return "ALTER_TABLE_MODIFY_COLUMN";
        }
        return "ALTER";
    }

    /** The action an ALTER ends with: the last rule its statement holds. */
    private static ParserRuleContext actionOf(final FrostlakeParser.AlterStatementContext alter) {
        for (int i = alter.getChildCount() - 1; i >= 0; i--) {
            if (alter.getChild(i) instanceof ParserRuleContext) {
                return (ParserRuleContext) alter.getChild(i);
            }
        }
        return null;
    }

    /** A statement of a family with no rule of its own here, named by the word it opens with. */
    private static String ofOpeningWord(final ParserRuleContext statement) {
        final TerminalNode opening = firstWord(statement);
        if (opening == null) {
            return UNMEASURED;
        }
        switch (opening.getSymbol().getType()) {
            case FrostlakeParser.CREATE:
                return "CREATE";
            case FrostlakeParser.ALTER:
                return "ALTER";
            case FrostlakeParser.DROP:
                return "DROP";
            case FrostlakeParser.UNDROP:
                return "RESTORE";
            case FrostlakeParser.SHOW:
                return "SHOW";
            case FrostlakeParser.DESCRIBE:
            case FrostlakeParser.DESC:
                return "DESCRIBE";
            default:
                return UNMEASURED;
        }
    }

    private static TerminalNode firstWord(final ParseTree node) {
        if (node instanceof TerminalNode) {
            return (TerminalNode) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final TerminalNode found = firstWord(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
