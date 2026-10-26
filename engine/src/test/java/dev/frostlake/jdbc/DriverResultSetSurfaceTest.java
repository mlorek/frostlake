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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

/**
 * The driver surface both transports share (see {@link DriverSurfaceChecks}), over the in-process driver — and, with
 * SF_LIVE=1, over the account's own driver, which every check describes. The HTTP driver runs the same checks in
 * {@link DriverResultSetSurfaceHttpTest}.
 */
public class DriverResultSetSurfaceTest extends BaseJdbcTest {

    private static final String OUT_PARAMETERS = "reads a procedure's value through a callable statement's OUT"
        + " parameters, a surface of the Frostlake driver's own: the account's driver models the value only as a"
        + " result set and refuses every OUT getter";

    @Test
    public void anIntegerGetterRefusesAFixedPointNumberWithAFractionByPositionAndByLabel() throws SQLException {
        DriverSurfaceChecks.scaledNumberRefusals(connection, isLiveSnowflake());
    }

    @Test
    public void theIntegerGettersParseTheCellsTextAndRefuseWhatTheirTypeCannotHold() throws SQLException {
        DriverSurfaceChecks.integerReads(connection);
    }

    @Test
    public void theNumericGettersOverATextOrAVariantRefuseWhatSpellsNoNumberOfTheirKind() throws SQLException {
        DriverSurfaceChecks.textAndVariantReads(connection);
    }

    @Test
    public void getBooleanReadsOneZeroTrueAndFalseAndRefusesAnythingElse() throws SQLException {
        DriverSurfaceChecks.booleanReads(connection);
    }

    @Test
    public void aLabelNamesTheFirstColumnMatchingItIgnoringCaseAndAMissingOneIsRefused() throws SQLException {
        DriverSurfaceChecks.labelsAndPositions(connection);
    }

    @Test
    public void theCursorMovesForwardOnlyAndReportsItsPositionAsTheAccountsDriverDoes() throws SQLException {
        DriverSurfaceChecks.cursor(connection);
    }

    @Test
    public void aClosedResultSetRefusesReadsAndANullReadSaysSo() throws SQLException {
        DriverSurfaceChecks.closedAndNull(connection);
    }

    @Test
    public void getCatalogAndGetSchemaFollowWhereTheSessionStands() throws SQLException {
        DriverSurfaceChecks.namespace(connection);
    }

    @Test
    public void aCallInTheEscapeSyntaxRunsAndItsResultReadsThroughTheGetters() throws SQLException {
        DriverSurfaceChecks.escapedCall(connection);
    }

    @Test
    public void theMetadataDescribesTheSqlAsTheAccountsDriverDoes() throws SQLException {
        DriverSurfaceChecks.metadataAnswers(connection);
    }

    @Test
    public void anOutParameterReadsAsItsColumnOfTheCallsResult() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), OUT_PARAMETERS);
        DriverSurfaceChecks.outParameters(connection);
    }
}
