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

package dev.frostlake.testkit;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Runs statements over one JDBC connection: Frostlake's own driver in-process
 * ({@code jdbc:frostlake:direct:}, the path {@code BaseJdbcTest} takes), or a live account's
 * connection when the suite runs with {@code SF_LIVE=1}. The DML count is the statement's update
 * count, or the first cell of a count grid.
 */
public final class JdbcBackend implements Backend {

    private final String name;
    private final Set<Capability> capabilities;
    private final Connection connection;
    private final Statement statement;
    /** The role the session opened with, and the warehouse, so a case cannot take either away. */
    private final String openingRole;
    private final String openingWarehouse;

    /**
     * @param name the backend's name
     * @param connection the connection every statement runs on, closed with the backend
     * @param capabilities what this connection's transport reports
     * @throws SQLException when no statement can be created
     */
    public JdbcBackend(final String name, final Connection connection, final Set<Capability> capabilities)
            throws SQLException {
        this.name = name;
        this.connection = connection;
        this.capabilities = capabilities;
        this.statement = connection.createStatement();
        this.openingRole = sessionValue("CURRENT_ROLE()");
        this.openingWarehouse = sessionValue("CURRENT_WAREHOUSE()");
    }

    /**
     * One session value, or null when the backend has none. Asked once, at construction, so the reset
     * that follows every case restores what the SESSION opened with rather than what the last case left.
     *
     * @param function the context function to read
     * @return its value, or null
     */
    private String sessionValue(final String function) {
        try (ResultSet rs = statement.executeQuery("SELECT " + function)) {
            final String value = rs.next() ? rs.getString(1) : null;
            return value == null || value.isEmpty() ? null : value;
        } catch (final SQLException noSuchValue) {
            return null;
        }
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Set<Capability> capabilities() {
        return capabilities;
    }

    @Override
    public ExecResult execute(final String sql) {
        final ExecResult out = new ExecResult();
        try {
            if (statement.execute(sql)) {
                try (final ResultSet rs = statement.getResultSet()) {
                    readGrid(rs, out);
                }
            } else {
                out.setUpdateCount(statement.getUpdateCount());
            }
            out.deriveUpdateCountFromGrid();
        } catch (final SQLException refused) {
            out.setErrorMessage(refused.getMessage() == null ? refused.getClass().getSimpleName()
                : refused.getMessage());
            if (refused.getErrorCode() != 0) {
                out.setErrorCode(String.valueOf(refused.getErrorCode()));
            }
            out.setSqlState(refused.getSQLState());
        }
        return out;
    }

    private static void readGrid(final ResultSet rs, final ExecResult out) throws SQLException {
        final ResultSetMetaData meta = rs.getMetaData();
        final int width = meta.getColumnCount();
        final List<String> names = new ArrayList<>();
        final boolean[] semiStructured = new boolean[width];
        for (int i = 1; i <= width; i++) {
            names.add(meta.getColumnLabel(i));
            semiStructured[i - 1] = SemiStructuredCells.isSemiStructured(meta.getColumnTypeName(i));
        }
        final List<List<String>> grid = new ArrayList<>();
        while (rs.next()) {
            final List<String> cells = new ArrayList<>();
            for (int i = 1; i <= width; i++) {
                final String cell = rs.getString(i);
                cells.add(semiStructured[i - 1] ? SemiStructuredCells.value(cell) : cell);
            }
            grid.add(cells);
        }
        out.setColumns(names);
        out.setRows(grid);
    }

    @Override
    public void resetContext() throws SQLException {
        // The session is ONE session for the whole corpus, so what a case leaves behind is still in
        // force for the next one. Two things a case takes away stop every later case dead: a USE ROLE
        // onto a role that may not CREATE DATABASE, and a warehouse — CREATE WAREHOUSE makes the new
        // warehouse current, so a case that creates one and drops it again leaves the session with
        // none at all. Both are restored to what the session opened with, BEFORE the reset statements
        // that need them.
        if (openingRole != null) {
            statement.execute("USE ROLE " + openingRole);
        }
        if (openingWarehouse != null) {
            statement.execute("USE WAREHOUSE " + openingWarehouse);
        }
        for (final String sql : RESET_CONTEXT) {
            statement.execute(sql);
        }
    }

    @Override
    public void close() throws SQLException {
        try {
            statement.close();
        } finally {
            connection.close();
        }
    }
}
