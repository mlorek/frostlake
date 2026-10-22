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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.metastore.model.WarehouseSize;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.transaction.Transaction;
import dev.frostlake.transaction.TransactionManager;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;
import java.util.Locale;

/**
 * What a statement's QUERY_HISTORY row records beyond its text and times (live-verified):
 *
 * <ul>
 *   <li>its kind, in the account's words (see {@link QueryTypeNames}), and for a failure its error's code;</li>
 *   <li>the transaction it ran in: the explicit one, or the one a write under autocommit ran as — a DML statement,
 *       a CREATE TABLE … AS SELECT, a clone, a TRUNCATE, a DML statement that failed as it ran — else 0, a
 *       statement refused while it compiled included, and a CALL or a block whose statements ran transactions of
 *       their own;</li>
 *   <li>whether a warehouse ran it: a DML statement, a CREATE TABLE … AS SELECT, a CALL or a block, a query that
 *       reads a relation or a sequence, and anything that failed as it ran; a DDL statement, a SHOW, a constant
 *       query and anything refused while it compiled ran without one. CLUSTER_NUMBER is 1 and WAREHOUSE_SIZE the
 *       warehouse's size where one ran it, both NULL where none did;</li>
 *   <li>the rows it produced: none where no warehouse ran it, for a write the rows of the table it wrote as they
 *       stand once it is done — a table is rewritten whole — and none where it changed no row, else the rows it
 *       answered; the rows it inserted, for an INSERT, a MERGE and a CREATE TABLE … AS SELECT.</li>
 * </ul>
 *
 * <p>The account answers some queries over a table from its statistics alone, without a warehouse: a COUNT, a MIN or
 * a MAX, and a filter that rules out every row (see CountStatisticsBound#answersFromStatistics). A statement of a
 * block, of a procedure's body or of an EXECUTE IMMEDIATE's text has its cells as one sent on its own.
 */
final class StatementHistoryCells {

    private static final String COMPILATION_ERROR = "SQL compilation error";

    private StatementHistoryCells() {
    }

    /**
     * Fill the cells of a statement that completed.
     *
     * @param row          its row
     * @param statement    the statement whose kind it is listed under, or null when none was read
     * @param executor     the executor, which reads the text an EXECUTE IMMEDIATE runs
     * @param results      what it answered
     * @param writtenRows  the rows the tables it wrote hold now
     * @param lastBefore   the session's last transaction as the statement began, or null
     * @param lastAfter    the session's last transaction now, or null
     * @param transactions the transaction manager
     * @param catalog      the catalog, which knows the session's warehouse
     */
    static void completed(final QueryHistory row, final FrostlakeParser.StatementContext statement,
                          final QueryExecutor executor, final List<ResultSet> results, final long writtenRows,
                          final String lastBefore, final String lastAfter, final TransactionManager transactions,
                          final Catalog catalog) {
        final FrostlakeParser.StatementContext effective = QueryTypeNames.effective(statement, executor);
        final String kind = QueryTypeNames.of(statement, executor);
        row.setQueryTypeName(kind);
        row.setTransactionId(transactionOf(transactions, lastBefore, lastAfter, kind));
        final boolean warehouse = usesWarehouse(kind, effective, executor);
        row.setWarehouseSize(warehouse ? warehouseSize(catalog) : null);
        if (!warehouse) {
            row.setRowsProduced(0L);
        } else if (writes(kind)) {
            // A write that changed no row rewrote nothing.
            row.setRowsProduced("CREATE_TABLE_AS_SELECT".equals(kind) || affected(results) > 0 ? writtenRows : 0L);
        }
        if ("CREATE_TABLE_AS_SELECT".equals(kind) && warehouse) {
            row.setRowsInserted(writtenRows);
        } else if ("INSERT".equals(kind) || "MERGE".equals(kind)) {
            row.setRowsInserted(inserted(results));
        }
    }

    /**
     * Fill the cells of a statement that failed, beyond the error {@link QueryHistory#markFailed} records.
     *
     * @param row          its row
     * @param statement    the statement whose kind it is listed under, or null when none was read
     * @param executor     the executor, which reads the text an EXECUTE IMMEDIATE runs
     * @param failure      what it failed with
     * @param lastBefore   the session's last transaction as the statement began, or null
     * @param lastAfter    the session's last transaction now, or null
     * @param transactions the transaction manager
     * @param catalog      the catalog, which knows the session's warehouse
     */
    static void failed(final QueryHistory row, final FrostlakeParser.StatementContext statement,
                       final QueryExecutor executor, final Throwable failure, final String lastBefore,
                       final String lastAfter, final TransactionManager transactions, final Catalog catalog) {
        final String kind = statement != null ? QueryTypeNames.of(statement, executor) : null;
        if (kind != null) {
            row.setQueryTypeName(kind);
        }
        // A statement refused while it compiled ran in no transaction and on no warehouse; one that failed as it ran
        // did both.
        final boolean ran = failure.getMessage() == null || !failure.getMessage().startsWith(COMPILATION_ERROR);
        row.setTransactionId(ran ? transactionOf(transactions, lastBefore, lastAfter, kind) : 0L);
        row.setWarehouseSize(ran ? warehouseSize(catalog) : null);
    }

    /**
     * The transaction a statement ran in: the one open as it ends — the explicit one, or the one a write under
     * autocommit opened — else the one it ended: a COMMIT's, a ROLLBACK's, or that of a statement that ran as a
     * transaction of its own; 0 for none. A CALL or a block runs in none of its own: its statements ran theirs.
     */
    private static long transactionOf(final TransactionManager transactions, final String lastBefore,
                                      final String lastAfter, final String kind) {
        final Transaction open = transactions.getCurrentTransaction();
        if ("CALL".equals(kind)) {
            return open != null && transactions.isLastingTransaction() ? open.getPublicId() : 0L;
        }
        if (open != null) {
            return open.getPublicId();
        }
        if (lastAfter != null && !lastAfter.equals(lastBefore)) {
            try {
                return Long.parseLong(lastAfter);
            } catch (final NumberFormatException notANumber) {
                return 0L;
            }
        }
        return 0L;
    }

    /** Whether a warehouse runs a statement of this kind. */
    private static boolean usesWarehouse(final String kind, final FrostlakeParser.StatementContext statement,
                                         final QueryExecutor executor) {
        switch (kind) {
            case "INSERT":
            case "UPDATE":
            case "DELETE":
            case "MERGE":
            case "COPY":
            case "UNLOAD":
            case "CALL":
                return true;
            case "CREATE_TABLE_AS_SELECT":
                // A table created from a query; a materialized view and search optimization are listed so too,
                // and run on none.
                return statement != null && statement.ddlStatement() != null
                    && statement.ddlStatement().createStatement() != null
                    && statement.ddlStatement().createStatement().TABLE() != null;
            case "SELECT":
            case "SELECT_FOR_UPDATE":
                return statement != null && readsData(statement) && !executor.answeredFromStatistics(statement);
            default:
                return false;
        }
    }

    /** Whether a statement of this kind writes a table, whose rows it produces. */
    private static boolean writes(final String kind) {
        switch (kind) {
            case "INSERT":
            case "UPDATE":
            case "DELETE":
            case "MERGE":
            case "COPY":
            case "CREATE_TABLE_AS_SELECT":
                return true;
            default:
                return false;
        }
    }

    /**
     * The outermost query a statement holds, or null for none.
     *
     * @param node the statement, or any part of a parse tree
     * @return the query
     */
    static FrostlakeParser.SelectStatementContext outermostQuery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return (FrostlakeParser.SelectStatementContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.SelectStatementContext query = outermostQuery(node.getChild(i));
            if (query != null) {
                return query;
            }
        }
        return null;
    }

    /** Whether a query reads a relation — it has a FROM clause — or a sequence. */
    private static boolean readsData(final ParseTree node) {
        if (node instanceof FrostlakeParser.TableExpressionContext) {
            return true;
        }
        if (node instanceof TerminalNode) {
            return ((TerminalNode) node).getSymbol().getType() == FrostlakeParser.NEXTVAL;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsData(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** The rows a DML statement's own counts say it changed. */
    private static long affected(final List<ResultSet> results) {
        long total = 0;
        for (final ResultSet result : results) {
            if (result.getUpdateCount() != null) {
                total += result.getUpdateCount().longValue();
            }
        }
        return total;
    }

    /** The rows a DML statement's own count columns say it inserted. */
    private static long inserted(final List<ResultSet> results) {
        long total = 0;
        for (final ResultSet result : results) {
            if (result.getUpdateCount() == null) {
                continue;
            }
            final List<ResultSetColumn> columns = result.getColumns();
            for (final Row row : result.getRows()) {
                for (int i = 0; i < columns.size() && i < row.getValues().size(); i++) {
                    final Object cell = row.getValue(i);
                    if (columns.get(i).getName().toLowerCase(Locale.ROOT).startsWith("number of rows inserted")
                            && cell instanceof Number) {
                        total += ((Number) cell).longValue();
                    }
                }
            }
        }
        return total;
    }

    /**
     * The size of the session's warehouse, as SHOW WAREHOUSES spells it: a session the engine lets run with none
     * is listed as running on the size a warehouse is created with.
     */
    private static String warehouseSize(final Catalog catalog) {
        final String name = catalog.getCurrentWarehouse();
        Warehouse warehouse = null;
        if (name != null) {
            try {
                warehouse = catalog.getWarehouse(name);
            } catch (final RuntimeException missing) {
                warehouse = null;
            }
        }
        return warehouse != null && warehouse.getSize() != null ? warehouse.getSize().getDisplayName()
            : WarehouseSize.X_SMALL.getDisplayName();
    }
}
