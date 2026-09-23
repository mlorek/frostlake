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
package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

/**
 * Frostlake's in-process JDBC transport holds to Snowflake's execute / update-count contract: DML answers its
 * affected rows, every other statement that answers no rows answers 0, a query answers rows, and a request of
 * several statements walks as snowflake-jdbc walks it. Two-sided: under SF_LIVE=1 the same assertions run on a
 * live account's own driver. The HTTP transport runs them in {@link HttpUpdateCountContractTest}.
 */
public class JdbcUpdateCountContractTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        UpdateCountContract.setUp(statement);
    }

    @Test
    public void everyStatementWithoutRowsCountsZero() throws SQLException {
        UpdateCountContract.everyStatementWithoutRowsCountsZero(statement);
    }

    @Test
    public void dmlCountsItsRows() throws SQLException {
        UpdateCountContract.dmlCountsItsRows(statement);
    }

    @Test
    public void copyCountsTheRowsItLoaded() throws SQLException {
        UpdateCountContract.copyCountsTheRowsItLoaded(statement);
    }

    @Test
    public void rowAnsweringStatementsAnswerRows() throws SQLException {
        UpdateCountContract.rowAnsweringStatementsAnswerRows(statement);
    }

    @Test
    public void executeUpdateRefusesRows() throws SQLException {
        UpdateCountContract.executeUpdateRefusesRows(statement);
    }

    @Test
    public void executeQueryReadsOneStatementsGrid() throws SQLException {
        UpdateCountContract.executeQueryReadsOneStatementsGrid(connection);
    }

    @Test
    public void severalStatementsWalkInOrder() throws SQLException {
        UpdateCountContract.severalStatementsWalkInOrder(statement);
    }

    @Test
    public void batchesCountEachStatement() throws SQLException {
        UpdateCountContract.batchesCountEachStatement(connection);
    }

    @Test
    public void preparedStatementsFollowTheRule() throws SQLException {
        UpdateCountContract.preparedStatementsFollowTheRule(connection);
    }
}
