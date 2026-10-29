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

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Locale;
import java.util.Set;

/**
 * What a stored procedure called in a FROM clause may run. Its statements are child jobs of the query
 * that calls it, and a query changes nothing: a child job that reads — a SELECT, a SHOW, a CALL of a
 * procedure that only reads, a sequence's NEXTVAL — runs, and one that writes is refused where it
 * stands in the body (all live-verified):
 *
 * <pre>
 *   INSERT / UPDATE / DELETE / MERGE   Invalid statement type 'DELETE' for child job from stored procedure
 *                                      in FROM clause on TABLE 'T'.
 *   CREATE … / DROP …                  … type 'CREATE' … in FROM clause on VIEW 'V'.
 *   TRUNCATE                           … type 'TRUNCATE_TABLE' … in FROM clause.
 *   UNDROP                             … type 'RESTORE' …
 *   ALTER … RENAME TO                  … type 'RENAME_TABLE', 'RENAME_VIEW' …
 *   ALTER TABLE … ADD / DROP COLUMN    … type 'ALTER_TABLE_ADD_COLUMN', 'ALTER_TABLE_DROP_COLUMN' …
 *   ALTER TABLE … CLUSTER BY           … type 'ALTER_TABLE' …
 *   COMMENT ON COLUMN                  … type 'ALTER_TABLE_MODIFY_COLUMN' …
 *   any other ALTER, COMMENT ON        … type 'ALTER' …
 *   SET / UNSET of a session variable  … type 'SET' … — in a caller's-rights procedure; an owner's-rights one
 *                                      is refused the statement in its own words wherever it is called from
 *   ALTER SESSION                      … type 'ALTER_SESSION' …, likewise in a caller's-rights procedure only
 *   LIST, REMOVE                       … type 'LIST_FILES', 'REMOVE_FILES' …, likewise
 *   BEGIN, START TRANSACTION           … type 'BEGIN_TRANSACTION' … in FROM clause.
 *   COMMIT, ROLLBACK                   … type 'COMMIT', 'ROLLBACK' …
 * </pre>
 *
 * <p>A PUT and a GET are no child jobs at all: a procedure's body may run neither, however it is called.
 *
 * <p>The rule reaches as deep as the call does: the text an EXECUTE IMMEDIATE runs and the body of a
 * procedure the body calls are child jobs too. The same procedure called by CALL — also the CALL that
 * TO_QUERY compiles — is no child job of a query and writes as it is written.
 */
final class FromClauseProcedureJobs {

    private static final ThreadLocal<int[]> DEPTH = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[1];
        }
    };

    /** The words of a CREATE that are no part of the object's kind. */
    private static final Set<Integer> MODIFIERS = Set.of(FrostlakeParser.TEMPORARY, FrostlakeParser.TEMP,
        FrostlakeParser.VOLATILE, FrostlakeParser.TRANSIENT, FrostlakeParser.LOCAL, FrostlakeParser.GLOBAL,
        FrostlakeParser.SECURE);

    private static final String INVALID = "SQL compilation error: Invalid statement type '";

    private static final String CHILD_JOB = "' for child job from stored procedure in FROM clause";

    private FromClauseProcedureJobs() {
    }

    /** A procedure begins to run for a FROM clause. */
    static void enter() {
        DEPTH.get()[0]++;
    }

    /** The procedure a FROM clause called has returned. */
    static void exit() {
        DEPTH.get()[0]--;
    }

    /**
     * Refuses a statement that may not run as a child job of a query; returns quietly outside one.
     *
     * @param statement the statement about to run
     * @param ownersRights whether an owner's-rights procedure is running, which refuses a statement that
     *     changes the session in words of its own
     */
    static void require(final FrostlakeParser.StatementContext statement, final boolean ownersRights) {
        if (DEPTH.get()[0] == 0) {
            return;
        }
        final String type = typeOf(statement, ownersRights);
        if (type != null) {
            throw new RuntimeException(INVALID + type + CHILD_JOB + targetOf(statement) + ".");
        }
    }

    /** The type the account names a writing statement by, or null for a statement a child job may run. */
    private static String typeOf(final FrostlakeParser.StatementContext statement, final boolean ownersRights) {
        final FrostlakeParser.DmlStatementContext dml = statement.dmlStatement();
        if (dml != null) {
            return dml.getStart().getText().toUpperCase(Locale.ROOT);
        }
        if (statement.sessionSetStatement() != null || statement.sessionUnsetStatement() != null) {
            return ownersRights ? null : statement.getStart().getText().toUpperCase(Locale.ROOT);
        }
        if (statement.listStatement() != null || statement.removeStatement() != null) {
            return ownersRights ? null : statement.listStatement() != null ? "LIST_FILES" : "REMOVE_FILES";
        }
        final FrostlakeParser.TransactionStatementContext transaction = statement.transactionStatement();
        if (transaction != null) {
            final int word = transaction.getStart().getType();
            return word == FrostlakeParser.BEGIN || word == FrostlakeParser.START ? "BEGIN_TRANSACTION"
                : transaction.getStart().getText().toUpperCase(Locale.ROOT);
        }
        final FrostlakeParser.DdlStatementContext ddl = statement.ddlStatement();
        if (ddl != null) {
            if (ddl.useStatement() != null) {
                return ownersRights ? null : "USE";
            }
            if (ddl.truncateStatement() != null) {
                return "TRUNCATE_TABLE";
            }
            if (ddl.undropStatement() != null) {
                return "RESTORE";
            }
            if (ddl.commentStatement() != null) {
                return ddl.commentStatement().COLUMN() != null ? "ALTER_TABLE_MODIFY_COLUMN" : "ALTER";
            }
            final FrostlakeParser.AlterStatementContext alter = ddl.alterStatement();
            if (alter != null && alter.SESSION() != null) {
                return ownersRights ? null : "ALTER_SESSION";
            }
            if (alter != null) {
                return alterType(statement, alter);
            }
            return ddl.getStart().getText().toUpperCase(Locale.ROOT);
        }
        if (statement.securityStatement() != null || statement.accessControlStatement() != null
                || statement.taskStatement() != null || statement.containerServicesStatement() != null) {
            return statement.getStart().getText().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    /** An ALTER's type: a rename is named with its object's kind, a table's column and clustering actions by name. */
    private static String alterType(final FrostlakeParser.StatementContext statement,
                                    final FrostlakeParser.AlterStatementContext alter) {
        if ("RENAME".equals(StatementKindText.of(statement)) && alter.getChildCount() > 1) {
            return "RENAME_" + alter.getChild(1).getText().toUpperCase(Locale.ROOT);
        }
        final FrostlakeParser.TableActionContext action = alter.TABLE() != null ? alter.tableAction() : null;
        if (action == null) {
            return "ALTER";
        }
        if (action.ADD() != null && action.columnDef() != null) {
            return "ALTER_TABLE_ADD_COLUMN";
        }
        if (action.DROP() != null && !action.identifier().isEmpty() && action.CONSTRAINT() == null) {
            return "ALTER_TABLE_DROP_COLUMN";
        }
        return action.CLUSTER() != null ? "ALTER_TABLE" : "ALTER";
    }

    /** " on KIND 'NAME'" for the statements the account names their object in, else nothing. */
    private static String targetOf(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.DmlStatementContext dml = statement.dmlStatement();
        if (dml != null) {
            final String table = dmlTable(dml);
            return table == null ? "" : " on TABLE '" + table + "'";
        }
        final FrostlakeParser.DdlStatementContext ddl = statement.ddlStatement();
        if (ddl == null || ddl.createStatement() == null && ddl.dropStatement() == null) {
            return "";
        }
        final ParseTree rule = ddl.createStatement() != null ? ddl.createStatement() : ddl.dropStatement();
        final StringBuilder kind = new StringBuilder();
        for (int i = 1; i < rule.getChildCount(); i++) {
            final ParseTree child = rule.getChild(i);
            if (child instanceof TerminalNode) {
                final int word = ((TerminalNode) child).getSymbol().getType();
                if (!MODIFIERS.contains(word)) {
                    kind.append(kind.length() > 0 ? " " : "").append(child.getText().toUpperCase(Locale.ROOT));
                }
                continue;
            }
            final String name = lastPart(child);
            if (name != null) {
                return kind.length() == 0 ? "" : " on " + kind + " '" + name + "'";
            }
        }
        return "";
    }

    /** The table a DML statement writes, by the last part of its name. */
    private static String dmlTable(final FrostlakeParser.DmlStatementContext dml) {
        if (dml.insertStatement() != null) {
            return lastPart(dml.insertStatement().objectName());
        }
        if (dml.updateStatement() != null) {
            return lastPart(dml.updateStatement().objectName());
        }
        if (dml.deleteStatement() != null) {
            return lastPart(dml.deleteStatement().objectName());
        }
        if (dml.mergeStatement() != null) {
            return lastPart(dml.mergeStatement().qualifiedName());
        }
        return null;
    }

    /** The last part of the name a node writes, or null when the node is no name. */
    private static String lastPart(final ParseTree node) {
        final FrostlakeParser.QualifiedNameContext name;
        if (node instanceof FrostlakeParser.ObjectNameContext) {
            name = ((FrostlakeParser.ObjectNameContext) node).qualifiedName();
        } else if (node instanceof FrostlakeParser.QualifiedNameContext) {
            name = (FrostlakeParser.QualifiedNameContext) node;
        } else {
            return null;
        }
        if (name == null) {
            return null;
        }
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        return parts.length == 0 ? null : parts[parts.length - 1];
    }
}
