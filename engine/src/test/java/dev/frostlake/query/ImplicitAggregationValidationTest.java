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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Implicit aggregation (an aggregate or HAVING with no GROUP BY) holds the select list to live's
 * rule: a bare column — reached directly, through an expression, an alias, or star expansion — is
 * refused with the bracketed family {@code [<OWNER>.<COLUMN>] is not a valid group by expression}:
 * positionless, first offender in select-list order, qualifier as written (folded), quoted
 * identifiers kept verbatim, and the {@code "values"} moniker for an unaliased derived table.
 * Constants and aggregate-derived expressions stay legal, as does a window function with no plain
 * aggregate beside it.
 */
public class ImplicitAggregationValidationTest extends BaseDatabaseTest {

    @BeforeEach
    public void createFixture() {
        engine.execute("CREATE TABLE ia_orders (city VARCHAR, qty INTEGER)");
        engine.execute("INSERT INTO ia_orders VALUES ('Berlin', 1), ('Oslo', 5)");
        engine.execute("CREATE TABLE ia_qt (\"mIxed\" INTEGER, n INTEGER)");
        engine.execute("CREATE TABLE ia_empty (a INTEGER, b INTEGER)");
    }

    private void assertRefused(final String sql, final String reference) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertEquals("SQL compilation error:\n[" + reference + "] is not a valid group by expression",
            e.getMessage());
    }

    @Test
    public void bareColumnBesideAggregateIsRefused() {
        assertRefused("SELECT city, COUNT(1) AS n FROM ia_orders", "IA_ORDERS.CITY");
    }

    @Test
    public void firstOffenderInSelectListOrderIsNamed() {
        assertRefused("SELECT qty, city, COUNT(1) FROM ia_orders", "IA_ORDERS.QTY");
    }

    @Test
    public void qualifiedReferenceNamesTheWrittenAlias() {
        assertRefused("SELECT t.city, COUNT(1) FROM ia_orders t", "T.CITY");
    }

    @Test
    public void anItemAliasDoesNotShieldTheColumn() {
        assertRefused("SELECT city AS c, COUNT(1) FROM ia_orders", "IA_ORDERS.CITY");
    }

    @Test
    public void aColumnInsideAnExpressionIsReached() {
        assertRefused("SELECT UPPER(city), COUNT(1) FROM ia_orders", "IA_ORDERS.CITY");
    }

    @Test
    public void havingAloneImpliesTheAggregation() {
        assertRefused("SELECT city FROM ia_orders HAVING COUNT(*) > 0", "IA_ORDERS.CITY");
    }

    @Test
    public void refusalFiresAtCompileTimeOverAnEmptyTable() {
        assertRefused("SELECT a, COUNT(1) FROM ia_empty", "IA_EMPTY.A");
    }

    @Test
    public void aWindowBesideThePlainAggregateDoesNotShield() {
        assertRefused("SELECT city, COUNT(1) OVER (), COUNT(1) FROM ia_orders", "IA_ORDERS.CITY");
    }

    @Test
    public void aStarNamesItsFirstColumn() {
        assertRefused("SELECT * FROM ia_orders HAVING COUNT(*) > 1", "IA_ORDERS.CITY");
    }

    @Test
    public void aQuotedColumnKeepsItsQuotes() {
        assertRefused("SELECT \"mIxed\", COUNT(1) FROM ia_qt", "IA_QT.\"mIxed\"");
    }

    @Test
    public void anUnaliasedDerivedTableSpellsTheValuesMoniker() {
        assertRefused("SELECT c2, COUNT(1) FROM (SELECT city AS c2 FROM ia_orders)", "\"values\".C2");
    }

    @Test
    public void constantsAndAggregateExpressionsStayLegal() {
        assertEquals(1, engine.executeQuery("SELECT 'x', COUNT(1) FROM ia_orders").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT COUNT(1)+1 FROM ia_orders").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT 1, COUNT(1) FROM ia_orders").getRowCount());
    }

    @Test
    public void aWindowWithNoPlainAggregateStaysLegal() {
        assertEquals(2, engine.executeQuery(
            "SELECT city, COUNT(1) OVER () FROM ia_orders").getRowCount());
    }
}
