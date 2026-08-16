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

package dev.frostlake.demo;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.List;

/**
 * Demonstration of WHERE, GROUP BY, and ORDER BY operators
 */
public class EngineDemo extends AbstractDemo {

    public static void main(final String[] args) {
        new EngineDemo().execute();
    }

    @Override
    protected String getDemoTitle() {
        return "Engine Demo";
    }

    @Override
    protected void runDemo() throws Exception {
        // Setup
        setupDatabase("sales_db");

        // Create tables
        engine.execute("create table t1(i int);");
        engine.execute("create table t2(i int);");
        engine.execute("create table t3(i int);");
        engine.execute("insert into t1 values (1),(2),(3),(4);");
        engine.execute("insert into t2 values (1),(2),(3);");
        engine.execute("insert into t3 values (1),(2);");


        // Insert sample data
        final ResultSet resultSet = engine.executeQuery("""
                SELECT *
                FROM t1
                WHERE EXISTS (
                    SELECT *
                    FROM t2
                    WHERE t1.i = t2.i
                        AND EXISTS (
                            SELECT *
                            FROM t3
                            WHERE t2.i = t3.i
                        )
                )
                """);

        System.out.println(resultSet.getRowCount());
        final List<Row> rows = resultSet.getRows();
        System.out.println(rows);
    }
}
