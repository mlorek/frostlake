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

package dev.frostlake.functions.table;

import dev.frostlake.executor.BindVariableSubstitutor;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.storage.ResultSet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TO_QUERY table function — compiles a SQL text into a query and returns its result set:
 * {@code SELECT * FROM TABLE(TO_QUERY('SELECT ...'))}. Optional named arguments are bound into
 * {@code :name} placeholders inside the text ({@code TO_QUERY('... WHERE c = :v', v => 5)});
 * an unsupplied placeholder binds as NULL.
 */
public class ToQuery extends TableFunction {

    /** The query text's parameter name — SQL, which is also what a leading positional argument fills. */
    private static final String SQL_PARAMETER = "SQL";

    private final QueryRunner queryRunner;

    public ToQuery(final QueryRunner queryRunner) {
        super("TO_QUERY");
        this.queryRunner = queryRunner;
    }

    /**
     * The query text's parameter is named <b>SQL</b>, not INPUT — {@code TO_QUERY(SQL => '…')} runs
     * and {@code TO_QUERY(INPUT => '…')} is refused for naming a parameter that does not exist. Every
     * OTHER named argument is a bind for a {@code :name} placeholder inside the text.
     */
    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        validateArgs(namedArgs);
        final Object sqlText = namedArgs.get(SQL_PARAMETER);
        final Map<String, Object> binds = new HashMap<>();
        for (final Map.Entry<String, Object> arg : namedArgs.entrySet()) {
            if (!SQL_PARAMETER.equals(arg.getKey())) {
                binds.put(arg.getKey(), arg.getValue());
            }
        }
        return run(sqlText, binds);
    }

    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        if (positionalArgs.size() != 1) {
            throw new RuntimeException(
                "TO_QUERY requires a SQL text argument (binds are passed as named arguments)");
        }
        return run(positionalArgs.get(0), new HashMap<>());
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        if (!(namedArgs.get(SQL_PARAMETER) instanceof String)) {
            throw new RuntimeException("TO_QUERY requires a SQL text argument");
        }
    }

    private ResultSet run(final Object sqlText, final Map<String, Object> binds) {
        if (!(sqlText instanceof String)) {
            throw new RuntimeException("TO_QUERY requires a SQL text argument");
        }
        String sql = (String) sqlText;
        if (!binds.isEmpty()) {
            sql = new BindVariableSubstitutor(binds).substitute(sql);
        }
        final ResultSet result = queryRunner.runQuery(sql);
        if (result == null) {
            throw new RuntimeException("TO_QUERY input did not produce a result set: " + sql);
        }
        return result;
    }
}
