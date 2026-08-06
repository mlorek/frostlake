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

package dev.frostlake.scripting;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

public class PythonTableFunctionTest {

    private static final Logger logger = LoggerFactory.getLogger(PythonTableFunctionTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) engine.shutdown();
    }

    @Test
    public void testStockSaleAveragePythonUdtf() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION stock_sale_average(symbol VARCHAR, quantity NUMBER, price NUMBER)
              RETURNS TABLE (symbol VARCHAR, total NUMBER)
              LANGUAGE PYTHON
              RUNTIME_VERSION = '3.9'
              PACKAGES = ('snowflake-snowpark-python')
              HANDLER = 'StockSaleAverage'
            AS $$
            class StockSaleAverage:
                def __init__(self):
                    self._price_array = []
                    self._quantity_total = 0
                    self._symbol = ""

                def process(self, symbol, quantity, price):
                    self._symbol = symbol
                    self._price_array.append(float(price))
                    cost = quantity * price
                    yield (symbol, cost)

                def end_partition(self):
                    total = sum(self._price_array) / len(self._price_array) if self._price_array else 0.0
                    yield (self._symbol, total)
            $$
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(stock_sale_average('AAPL', 10, 150.0))");
        assertNotNull(rs);
        assertTrue(rs.getRowCount() >= 1, "Expected at least one row from process()");
        logger.info("stock_sale_average rows: {}", rs.getRowCount());
        // process() yields (symbol, cost) → ('AAPL', 1500.0)
        assertEquals("AAPL", rs.getRows().get(0).getValue(0).toString());
        logger.info("First row: {}", rs.getRows().get(0).getValues());
    }

    @Test
    public void testSimplePythonTableFunctionYield() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION expand(n NUMBER)
              RETURNS TABLE (val NUMBER)
              LANGUAGE PYTHON
              RUNTIME_VERSION = '3.11'
              HANDLER = 'Expander'
            AS $$
            class Expander:
                def process(self, n):
                    for i in range(int(n)):
                        yield (i,)
            $$
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(expand(3))");
        assertNotNull(rs);
        assertEquals(3, rs.getRowCount(), "Expected 3 rows from expand(3)");
        assertEquals(0L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals(1L, ((Number) rs.getRows().get(1).getValue(0)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(2).getValue(0)).longValue());
        logger.info("expand(3) returned {} rows", rs.getRowCount());
    }

    @Test
    public void testPythonTableFunctionWithEndPartition() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION sum_and_avg(v NUMBER)
              RETURNS TABLE (label VARCHAR, result NUMBER)
              LANGUAGE PYTHON
              RUNTIME_VERSION = '3.11'
              HANDLER = 'Aggregator'
            AS $$
            class Aggregator:
                def __init__(self):
                    self._values = []

                def process(self, v):
                    self._values.append(float(v))
                    yield ('item', float(v))

                def end_partition(self):
                    if self._values:
                        avg = sum(self._values) / len(self._values)
                        yield ('avg', avg)
            $$
            """);

        ResultSet rs = engine.executeQuery("SELECT * FROM TABLE(sum_and_avg(10))");
        assertNotNull(rs);
        // process() yields ('item', 10.0) + end_partition() yields ('avg', 10.0) = 2 rows
        assertEquals(2, rs.getRowCount(), "Expected process row + end_partition row");
        assertEquals("item", rs.getRows().get(0).getValue(0).toString());
        assertEquals("avg",  rs.getRows().get(1).getValue(0).toString());
        logger.info("sum_and_avg result: {}", rs.getRows());
    }

    @Test
    public void testPythonTableFunctionCreationMetadata() {
        engine.execute("""
            CREATE FUNCTION meta_fn(x NUMBER)
              RETURNS TABLE (out_val NUMBER)
              LANGUAGE PYTHON
              RUNTIME_VERSION = '3.9'
              PACKAGES = ('numpy')
              HANDLER = 'MyHandler'
            AS $$
            class MyHandler:
                def process(self, x):
                    yield (x * 2,)
            $$
            """);

        Function f = engine.getCatalog()
            .getDatabase("TEST_DB").getSchema("PUBLIC").getFunction("META_FN");
        assertNotNull(f);
        assertTrue(f.isTableFunction());
        assertEquals("PYTHON", f.getLanguage());
        assertEquals("3.9", f.getRuntimeVersion());
        assertEquals("MyHandler", f.getHandler());
        assertEquals(1, f.getReturnColumns().size());
        assertEquals("OUT_VAL", f.getReturnColumns().get(0).getName());
        logger.info("Metadata verified for meta_fn");
    }
}
