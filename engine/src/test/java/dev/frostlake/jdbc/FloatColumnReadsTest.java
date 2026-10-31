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
 * A FLOAT column through each typed getter, as the account's driver reads one on its default results (see
 * {@link FloatColumnChecks}): over the in-process driver, and with SF_LIVE=1 over the account's own, on a session put
 * on those results. The HTTP driver runs the same checks in {@link FloatHttpTransportTest}.
 */
public class FloatColumnReadsTest extends BaseJdbcTest {

    @Override
    protected void setupTest() throws SQLException {
        DefaultResultFormat.use(statement);
    }

    @Test
    public void theIntegerGettersNarrowTheDoubleAsJavaDoes() throws SQLException {
        FloatColumnChecks.integerReads(connection);
    }

    @Test
    public void getBigDecimalIsTheDecimalOfTheDoublesText() throws SQLException {
        FloatColumnChecks.decimalReads(connection, isLiveSnowflake());
    }

    @Test
    public void getBooleanRefusesEveryValueQuotingItAsANumber() throws SQLException {
        FloatColumnChecks.booleanReads(connection);
    }

    @Test
    public void getByteAndTheTemporalGettersRefuseEveryCellNullIncluded() throws SQLException {
        FloatColumnChecks.refusedReads(connection);
    }

    @Test
    public void theOtherGettersReadTheDoubleItself() throws SQLException {
        FloatColumnChecks.doubleReads(connection);
    }
}
