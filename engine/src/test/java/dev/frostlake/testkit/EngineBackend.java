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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Runs statements on an in-process {@link DatabaseEngine} — the path {@code BaseDatabaseTest} takes.
 * The engine reports a refusal either as an exception or as a failed result; both come back as the
 * result's message.
 */
public final class EngineBackend implements Backend {

    private final DatabaseEngine engine = new DatabaseEngine();

    @Override
    public String name() {
        return "engine";
    }

    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.UPDATE_COUNT, Capability.COLUMN_NAMES, Capability.SESSION);
    }

    @Override
    public ExecResult execute(final String sql) {
        final ExecResult out = new ExecResult();
        final ExecutionResult result;
        try {
            result = engine.execute(sql);
        } catch (final RuntimeException refused) {
            out.setErrorMessage(refused.getMessage() == null ? refused.getClass().getSimpleName()
                : refused.getMessage());
            return out;
        }
        if (!result.isSuccess()) {
            out.setErrorMessage(result.getErrorMessage() == null ? "(no message)" : result.getErrorMessage());
            return out;
        }
        out.setUpdateCount(result.getRowsAffected());
        final List<ResultSet> sets = result.getResultSets();
        if (sets != null && !sets.isEmpty()) {
            final ResultSet first = sets.get(0);
            final List<String> names = new ArrayList<>();
            for (final ResultSetColumn column : first.getColumns()) {
                names.add(column.getName());
            }
            final List<List<String>> grid = new ArrayList<>();
            for (final Row row : first.getRows()) {
                final List<String> cells = new ArrayList<>();
                for (final Object value : row.getValues()) {
                    cells.add(value == null ? null : String.valueOf(value));
                }
                grid.add(cells);
            }
            out.setColumns(names);
            out.setRows(grid);
        }
        return out;
    }

    @Override
    public void resetContext() {
        for (final String statement : RESET_CONTEXT) {
            engine.execute(statement);
        }
    }

    @Override
    public void close() {
        engine.shutdown();
    }
}
