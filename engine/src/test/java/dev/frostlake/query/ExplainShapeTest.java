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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * EXPLAIN in the account's shapes: ten columns with a GlobalStats row first by default, one {@code content} column
 * holding the plan as text or JSON for USING TEXT and USING JSON, any other format word refused, and the statement
 * compiled first. A query with no FROM reads a Generator.
 */
public class ExplainShapeTest extends BaseDatabaseTest {

    private String spell(final DataType type) {
        return type instanceof NumericType ? "NUMBER(" + ((NumericType) type).getPrecision() + ","
            + ((NumericType) type).getScale() + ")" : type.getName();
    }

    private String columns(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn column : rs.getColumns()) {
            out.append(out.length() > 0 ? ", " : "").append(column.getName()).append(':')
                .append(spell(column.getDataType()));
        }
        return out.toString();
    }

    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            out.append(out.length() > 0 ? ";" : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void theTabularPlanHasTenColumnsAndAGlobalStatsRow() {
        assertEquals("step:NUMBER(9,0), id:NUMBER(9,0), parentOperators:VARCHAR, operation:VARCHAR, objects:VARCHAR, "
            + "alias:VARCHAR, expressions:VARCHAR, partitionsTotal:NUMBER(38,0), partitionsAssigned:NUMBER(38,0), "
            + "bytesAssigned:NUMBER(38,0)", columns("EXPLAIN SELECT 1"));
        final String plan = "null|null|null|GlobalStats|null|null|null|0|0|0;1|0|null|Result|null|null|1|null|null|null;"
            + "1|1|[0]|Generator|null|null|1|null|null|null";
        assertEquals(plan, rows("EXPLAIN SELECT 1"));
        assertEquals(plan, rows("EXPLAIN USING TABULAR SELECT 1"));
    }

    @Test
    public void textAndJsonAreOneContentColumn() {
        final String text = "GlobalStats:\n    partitionsTotal=0\n    partitionsAssigned=0\n    bytesAssigned=0\n"
            + "Operations:\n1:0     ->Result  1  \n1:1          ->Generator  1  \n";
        assertEquals("content:VARCHAR", columns("EXPLAIN USING TEXT SELECT 1"));
        assertEquals(text, rows("EXPLAIN USING TEXT SELECT 1"));
        assertEquals(text, rows("EXPLAIN USING text SELECT 1"));
        assertEquals("content:VARCHAR", columns("EXPLAIN USING JSON SELECT 1"));
        assertEquals("{\"GlobalStats\":{\"partitionsTotal\":0,\"partitionsAssigned\":0,\"bytesAssigned\":0},"
            + "\"Operations\":[[{\"id\":0,\"operation\":\"Result\",\"expressions\":[\"1\"]},{\"id\":1,\"operation\":"
            + "\"Generator\",\"expressions\":[\"1\"],\"parentOperators\":[0]}]]}", rows("EXPLAIN USING JSON SELECT 1"));
    }

    @Test
    public void anUnknownFormatAndAMissingObjectAreRefused() {
        assertEquals("SQL compilation error: error line 1 at position 4\n Invalid explain plan format 'NOSUCH'",
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("EXPLAIN USING NOSUCH SELECT 1");
                }
            }).getMessage());
        assertTrue(assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("EXPLAIN SELECT a FROM nosuch_rt");
            }
        }).getMessage().startsWith("SQL compilation error:\nObject 'NOSUCH_RT' does not exist or not authorized."));
    }
}
