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

package dev.frostlake.executor.operators;

import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for operator pipeline architecture.
 */
public class OperatorPipelineTest {

    private Table testTable;
    private FunctionRegistry functionRegistry;
    private List<Row> testData;

    @BeforeEach
    public void setup() {
        // Create test table
        final List<TableColumn> columns = Arrays.asList(
            new TableColumn("id", NumericType.INTEGER, true, null, false, false, false),
            new TableColumn("name", StringType.VARCHAR, true, null, false, false, false),
            new TableColumn("age", NumericType.INTEGER, true, null, false, false, false)
        );
        testTable = new Table("users", columns, false);

        // Create test data
        testData = new ArrayList<>();
        testData.add(new Row(Arrays.asList(1L, "Alice", 30L)));
        testData.add(new Row(Arrays.asList(2L, "Bob", 25L)));
        testData.add(new Row(Arrays.asList(3L, "Charlie", 35L)));
        testData.add(new Row(Arrays.asList(4L, "David", 28L)));
        testData.add(new Row(Arrays.asList(5L, "Eve", 32L)));

        final Catalog catalog = new Catalog();
        functionRegistry = new FunctionRegistry(catalog);
    }

    @Test
    public void testWhereOperatorSimple() {
        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        final WhereOperator whereOp = new WhereOperator("age > 30");

        final List<Row> result = whereOp.execute(testData, context);

        assertEquals(2, result.size());
        assertEquals("Charlie", result.get(0).getValue(1));
        assertEquals("Eve", result.get(1).getValue(1));
    }

    @Test
    public void testLimitOperator() {
        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        final LimitOperator limitOp = new LimitOperator(3);

        final List<Row> result = limitOp.execute(testData, context);

        assertEquals(3, result.size());
        assertEquals("Alice", result.get(0).getValue(1));
        assertEquals("Bob", result.get(1).getValue(1));
        assertEquals("Charlie", result.get(2).getValue(1));
    }

    @Test
    public void testLimitOperatorWithOffset() {
        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        final LimitOperator limitOp = new LimitOperator(2, 2);

        final List<Row> result = limitOp.execute(testData, context);

        assertEquals(2, result.size());
        assertEquals("Charlie", result.get(0).getValue(1));
        assertEquals("David", result.get(1).getValue(1));
    }

    @Test
    public void testPipeline() {
        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        // Build pipeline: WHERE age > 25 -> LIMIT 2
        final OperatorPipeline pipeline = OperatorPipeline.builder()
            .context(context)
            .addOperator(new WhereOperator("age > 25"))
            .addOperator(new LimitOperator(2))
            .build();

        final List<Row> result = pipeline.execute(testData);

        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).getValue(1));
        assertEquals("Charlie", result.get(1).getValue(1));
    }

    @Test
    public void testPipelineDescription() {
        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        final OperatorPipeline pipeline = OperatorPipeline.builder()
            .context(context)
            .addOperator(new WhereOperator("age > 25"))
            .addOperator(new LimitOperator(2))
            .build();

        final String description = pipeline.getDescription();

        assertTrue(description.contains("WHERE"));
        assertTrue(description.contains("LIMIT"));
        assertTrue(description.contains("age > 25"));
    }

    @Test
    public void testWhereOperatorAutoMode() {
        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        final WhereOperator whereOp = WhereOperator.create("age > 25", context);

        final List<Row> result = whereOp.execute(testData, context);

        assertEquals(4, result.size());
    }

    @Test
    public void testEmptyPipeline() {
        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        // Pipeline with no operators
        final OperatorPipeline pipeline = OperatorPipeline.builder()
            .context(context)
            .build();

        final List<Row> result = pipeline.execute(testData);

        assertEquals(testData.size(), result.size());
    }

    @Test
    public void testCrossJoinOperator() {
        // Create second table
        final List<TableColumn> rightColumns = Arrays.asList(
            new TableColumn("dept_id", NumericType.INTEGER, true, null, false, false, false),
            new TableColumn("dept_name", StringType.VARCHAR, true, null, false, false, false)
        );
        final Table deptTable = new Table("departments", rightColumns, false);

        // Create department data
        final List<Row> deptData = new ArrayList<>();
        deptData.add(new Row(Arrays.asList(1L, "Engineering")));
        deptData.add(new Row(Arrays.asList(2L, "Sales")));

        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        // Take first 2 users
        final List<Row> leftRows = testData.subList(0, 2);

        final JoinOperator joinOp = JoinOperator.cross(testTable, deptTable, deptData);
        final List<Row> result = joinOp.execute(leftRows, context);

        // 2 users x 2 departments = 4 rows
        assertEquals(4, result.size());
        // Each result row should have 5 values (3 from users + 2 from departments)
        assertEquals(5, result.get(0).getValues().size());
    }

    @Test
    public void testInnerJoinOperator() {
        // Create second table
        final List<TableColumn> rightColumns = Arrays.asList(
            new TableColumn("user_id", NumericType.INTEGER, true, null, false, false, false),
            new TableColumn("city", StringType.VARCHAR, true, null, false, false, false)
        );
        final Table cityTable = new Table("cities", rightColumns, false);

        // Create city data
        final List<Row> cityData = new ArrayList<>();
        cityData.add(new Row(Arrays.asList(1L, "New York")));
        cityData.add(new Row(Arrays.asList(2L, "Boston")));
        cityData.add(new Row(Arrays.asList(3L, "Chicago")));

        // Create condition evaluator: left.id = right.user_id
        // Left row has id at index 0, right row has user_id at index 0
        final JoinConditionEvaluator conditionEvaluator = new JoinConditionEvaluator() {
            @Override
            public boolean matches(final Row leftRow, final Row rightRow) {
                final Object leftId = leftRow.getValue(0);
                final Object rightUserId = rightRow.getValue(0);
                return leftId != null && leftId.equals(rightUserId);
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        final JoinOperator joinOp = new JoinOperator(testTable, cityTable, cityData,
            JoinType.INNER, "id = user_id", conditionEvaluator);

        final List<Row> result = joinOp.execute(testData, context);

        // Only first 3 users match
        assertEquals(3, result.size());
        assertEquals("Alice", result.get(0).getValue(1));
        assertEquals("New York", result.get(0).getValue(4)); // city is at index 4
        assertEquals("Bob", result.get(1).getValue(1));
        assertEquals("Boston", result.get(1).getValue(4)); // city is at index 4
    }

    @Test
    public void testLeftJoinOperator() {
        // Create second table
        final List<TableColumn> rightColumns = Arrays.asList(
            new TableColumn("user_id", NumericType.INTEGER, true, null, false, false, false),
            new TableColumn("city", StringType.VARCHAR, true, null, false, false, false)
        );
        final Table cityTable = new Table("cities", rightColumns, false);

        // Create city data - only 2 matches
        final List<Row> cityData = new ArrayList<>();
        cityData.add(new Row(Arrays.asList(1L, "New York")));
        cityData.add(new Row(Arrays.asList(2L, "Boston")));

        // Create condition evaluator
        final JoinConditionEvaluator conditionEvaluator = new JoinConditionEvaluator() {
            @Override
            public boolean matches(final Row leftRow, final Row rightRow) {
                final Object leftId = leftRow.getValue(0);
                final Object rightUserId = rightRow.getValue(0);
                return leftId != null && leftId.equals(rightUserId);
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        final JoinOperator joinOp = new JoinOperator(testTable, cityTable, cityData,
            JoinType.LEFT, "id = user_id", conditionEvaluator);

        final List<Row> result = joinOp.execute(testData, context);

        // All 5 users should be present (LEFT JOIN)
        assertEquals(5, result.size());
        assertEquals("Alice", result.get(0).getValue(1));
        assertEquals("New York", result.get(0).getValue(4)); // city is at index 4
        assertEquals("Charlie", result.get(2).getValue(1));
        assertNull(result.get(2).getValue(4)); // No city for Charlie
    }

    @Test
    public void testJoinWithWherePipeline() {
        // Create second table
        final List<TableColumn> rightColumns = Arrays.asList(
            new TableColumn("user_id", NumericType.INTEGER, true, null, false, false, false),
            new TableColumn("city", StringType.VARCHAR, true, null, false, false, false)
        );
        final Table cityTable = new Table("cities", rightColumns, false);

        // Create city data
        final List<Row> cityData = new ArrayList<>();
        cityData.add(new Row(Arrays.asList(1L, "New York")));
        cityData.add(new Row(Arrays.asList(2L, "Boston")));
        cityData.add(new Row(Arrays.asList(3L, "Chicago")));

        // Create condition evaluator
        final JoinConditionEvaluator conditionEvaluator = new JoinConditionEvaluator() {
            @Override
            public boolean matches(final Row leftRow, final Row rightRow) {
                final Object leftId = leftRow.getValue(0);
                final Object rightUserId = rightRow.getValue(0);
                return leftId != null && leftId.equals(rightUserId);
            }
        };

        // Create merged table for WHERE clause
        final List<TableColumn> mergedColumns = new ArrayList<>();
        mergedColumns.addAll(testTable.getColumns());
        mergedColumns.addAll(cityTable.getColumns());
        final Table mergedTable = new Table("users_cities", mergedColumns, false);

        final OperatorContext context = OperatorContext.builder()
            .table(mergedTable)
            .functionRegistry(functionRegistry)
            .build();

        // Build pipeline: JOIN -> WHERE age > 25
        final OperatorPipeline pipeline = OperatorPipeline.builder()
            .context(context)
            .addOperator(new JoinOperator(testTable, cityTable, cityData,
                JoinType.INNER, "id = user_id", conditionEvaluator))
            .addOperator(new WhereOperator("age > 25"))
            .build();

        final List<Row> result = pipeline.execute(testData);

        // Alice (30) and Charlie (35) both > 25, but only first 3 users have cities
        // So only Alice matches
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).getValue(1));
    }

    @Test
    public void testProjectOperator() {
        // Create expression evaluator that extracts columns by index
        final RowExpressionEvaluator expressionEvaluator = new RowExpressionEvaluator() {
            @Override
            public Object evaluate(final Expression expr, final Row row) {
                final String col = expr instanceof ColumnReferenceExpression
                    ? ((ColumnReferenceExpression) expr).getColumnName() : String.valueOf(expr);
                // Simple column name extraction
                if ("name".equalsIgnoreCase(col)) {
                    return row.getValue(1);
                } else if ("age".equalsIgnoreCase(col)) {
                    return row.getValue(2);
                }
                return null;
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        // Project only name and age columns
        final ProjectOperator projectOp = new ProjectOperator(
            Arrays.asList("name", "age"),
            Arrays.asList("name", "age"),
            expressionEvaluator
        );

        final List<Row> result = projectOp.execute(testData, context);

        assertEquals(5, result.size());
        // Each row should have 2 values instead of 3
        assertEquals(2, result.get(0).getValues().size());
        assertEquals("Alice", result.get(0).getValue(0));
        assertEquals(30L, result.get(0).getValue(1));
    }

    @Test
    public void testGroupByOperator() {
        // Add more data with duplicate ages
        final List<Row> dataWithDuplicates = new ArrayList<>(testData);
        dataWithDuplicates.add(new Row(Arrays.asList(6L, "Frank", 30L))); // Same age as Alice

        // Column evaluator for GROUP BY
        final RowExpressionEvaluator columnEvaluator = new RowExpressionEvaluator() {
            @Override
            public Object evaluate(final Expression expr, final Row row) {
                final String col = expr instanceof ColumnReferenceExpression
                    ? ((ColumnReferenceExpression) expr).getColumnName() : String.valueOf(expr);
                if ("age".equalsIgnoreCase(col)) {
                    return row.getValue(2);
                }
                return null;
            }
        };

        // Aggregate evaluator for SELECT
        final AggregateEvaluator aggregateEvaluator = new AggregateEvaluator() {
            @Override
            public Object evaluate(final int index, final List<Row> rows) {
                // select list is ["age", "COUNT(*)"]
                if (index == 1) {
                    return (long) rows.size();
                } else if (index == 0) {
                    // Return the age value (same for all rows in group)
                    return rows.get(0).getValue(2);
                }
                return null;
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        // GROUP BY age, SELECT age, COUNT(*)
        final GroupByOperator groupByOp = new GroupByOperator(
            Arrays.asList("age"),
            Arrays.asList("age", "COUNT(*)"),
            columnEvaluator,
            aggregateEvaluator
        );

        final List<Row> result = groupByOp.execute(dataWithDuplicates, context);

        // Should have 5 groups (25, 28, 30, 32, 35)
        assertEquals(5, result.size());

        // Find the group with age 30 (should have count 2)
        Row age30Group = null;
        for (final Row row : result) {
            if (Long.valueOf(30L).equals(row.getValue(0))) {
                age30Group = row;
                break;
            }
        }
        assertNotNull(age30Group, "no group with age 30");
        assertEquals(2L, age30Group.getValue(1)); // COUNT(*) = 2
    }

    @Test
    public void testImplicitGroupByOperator() {
        // Aggregate evaluator for implicit grouping
        final AggregateEvaluator aggregateEvaluator = new AggregateEvaluator() {
            @Override
            public Object evaluate(final int index, final List<Row> rows) {
                // select list is ["COUNT(*)", "AVG(age)"]
                if (index == 0) {
                    return (long) rows.size();
                } else if (index == 1) {
                    // Calculate average age
                    long sum = 0;
                    for (final Row row : rows) {
                        sum += (Long) row.getValue(2);
                    }
                    return sum / (double) rows.size();
                }
                return null;
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        // SELECT COUNT(*), AVG(age) (no GROUP BY - implicit grouping)
        final GroupByOperator groupByOp = GroupByOperator.createImplicit(
            Arrays.asList("COUNT(*)", "AVG(age)"),
            aggregateEvaluator
        );

        final List<Row> result = groupByOp.execute(testData, context);

        // Should return single row with aggregates
        assertEquals(1, result.size());
        assertEquals(5L, result.get(0).getValue(0)); // COUNT(*)
        assertEquals(30.0, result.get(0).getValue(1)); // AVG(age) = (30+25+35+28+32)/5 = 30
    }

    @Test
    public void testProjectAndWheresPipeline() {
        // Expression evaluator that extracts columns
        final RowExpressionEvaluator projectEvaluator = new RowExpressionEvaluator() {
            @Override
            public Object evaluate(final Expression expr, final Row row) {
                final String col = expr instanceof ColumnReferenceExpression
                    ? ((ColumnReferenceExpression) expr).getColumnName() : String.valueOf(expr);
                if ("name".equalsIgnoreCase(col)) {
                    return row.getValue(1);
                } else if ("age".equalsIgnoreCase(col)) {
                    return row.getValue(2);
                }
                return null;
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        // Build pipeline: WHERE age > 25 -> PROJECT name, age
        final OperatorPipeline pipeline = OperatorPipeline.builder()
            .context(context)
            .addOperator(new WhereOperator("age > 25"))
            .addOperator(new ProjectOperator(
                Arrays.asList("name", "age"),
                Arrays.asList("name", "age"),
                projectEvaluator))
            .build();

        final List<Row> result = pipeline.execute(testData);

        // Should have 4 rows (age > 25), each with 2 columns
        assertEquals(4, result.size());
        assertEquals(2, result.get(0).getValues().size());
        assertEquals("Alice", result.get(0).getValue(0));
        assertEquals(30L, result.get(0).getValue(1));
    }

    @Test
    public void testTableFunctionOperator() {
        // Create a mock table function that generates rows
        final TableFunction mockTableFunc =
            new TableFunction("MOCK_GENERATOR") {
                @Override
                public ResultSet execute(final Map<String, Object> namedArgs) {
                    final int rowCount = (Integer) namedArgs.getOrDefault("ROWCOUNT", 5);

                    // Create result columns
                    final List<ResultSetColumn> columns = Arrays.asList(
                        new ResultSetColumn("ID", NumericType.INTEGER, null),
                        new ResultSetColumn("VALUE", StringType.VARCHAR, null)
                    );

                    // Generate rows
                    final List<Row> rows = new ArrayList<>();
                    for (int i = 0; i < rowCount; i++) {
                        rows.add(new Row(Arrays.asList((long) i, "value_" + i)));
                    }

                    return new ResultSet(columns, rows);
                }

                @Override
                public void validateArgs(final Map<String, Object> namedArgs) {
                    // No validation needed for test
                }
            };

        // Create named arguments
        final Map<String, Object> namedArgs = new HashMap<>();
        namedArgs.put("ROWCOUNT", 3);

        final OperatorContext context = OperatorContext.builder()
            .functionRegistry(functionRegistry)
            .build();

        // Create and execute table function operator
        final TableFunctionOperator tableFuncOp = new TableFunctionOperator(
            "MOCK_GENERATOR",
            mockTableFunc,
            namedArgs
        );

        final List<Row> result = tableFuncOp.execute(new ArrayList<>(), context);

        // Should generate 3 rows
        assertEquals(3, result.size());
        assertEquals(0L, result.get(0).getValue(0));
        assertEquals("value_0", result.get(0).getValue(1));
        assertEquals(2L, result.get(2).getValue(0));
        assertEquals("value_2", result.get(2).getValue(1));
    }

    @Test
    public void testQualifyOperator() {
        // Simulate window function results: row_number for each row
        // Rows: Alice(1), Bob(2), Charlie(3), Diana(4), Eve(5)
        final Map<Integer, Map<Integer, Object>> windowResults = new HashMap<>();
        for (int i = 0; i < testData.size(); i++) {
            final Map<Integer, Object> rowResults = new HashMap<>();
            rowResults.put(0, (long) (i + 1)); // row_number starts at 1
            windowResults.put(i, rowResults);
        }

        // Create QUALIFY evaluator that filters rows where row_number <= 3
        final QualifyEvaluator qualifyEvaluator = new QualifyEvaluator() {
            @Override
            public boolean evaluate(final Expression condition, final Row row, final int rowIndex) {
                // Simulate evaluation of "row_number <= 3"
                final Map<Integer, Object> rowWindowResults = windowResults.get(rowIndex);
                if (rowWindowResults != null && rowWindowResults.containsKey(0)) {
                    final Long rowNumber = (Long) rowWindowResults.get(0);
                    return rowNumber <= 3;
                }
                return false;
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        // Create and execute QUALIFY operator
        final QualifyOperator qualifyOp = new QualifyOperator("row_number <= 3", qualifyEvaluator);
        final List<Row> result = qualifyOp.execute(testData, context);

        // Should return first 3 rows
        assertEquals(3, result.size());
        assertEquals("Alice", result.get(0).getValue(1));
        assertEquals("Bob", result.get(1).getValue(1));
        assertEquals("Charlie", result.get(2).getValue(1));
    }

    @Test
    public void testQualifyInPipeline() {
        // Simulate window function results
        final Map<Integer, Map<Integer, Object>> windowResults = new HashMap<>();
        for (int i = 0; i < testData.size(); i++) {
            final Map<Integer, Object> rowResults = new HashMap<>();
            rowResults.put(0, (long) (i + 1));
            windowResults.put(i, rowResults);
        }

        // Build pipeline: WHERE age > 25 -> QUALIFY row_number <= 2
        final QualifyEvaluator qualifyEvaluator = new QualifyEvaluator() {
            @Override
            public boolean evaluate(final Expression condition, final Row row, final int rowIndex) {
                // Note: rowIndex is relative to the filtered input, not original
                // For this test, we'll just take the first 2 rows after WHERE
                return rowIndex < 2;
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        final OperatorPipeline pipeline = OperatorPipeline.builder()
            .context(context)
            .addOperator(new WhereOperator("age > 25"))
            .addOperator(new QualifyOperator("row_number <= 2", qualifyEvaluator))
            .build();

        final List<Row> result = pipeline.execute(testData);

        // First filter: age > 25 gives 4 rows (Alice, Charlie, Eve, Diana)
        // Then QUALIFY takes first 2 rows
        assertEquals(2, result.size());
    }

    @Test
    public void testHavingOperator() {
        // Simulate aggregated rows after GROUP BY
        // Rows format: [dept, COUNT(*), AVG(age)]
        final List<Row> aggregatedRows = new ArrayList<>();
        aggregatedRows.add(new Row(Arrays.asList("Engineering", 3L, 75.0))); // 3 employees, avg age 75
        aggregatedRows.add(new Row(Arrays.asList("Sales", 2L, 57.5)));         // 2 employees, avg age 57.5

        // Create HAVING evaluator that filters groups with COUNT(*) > 2
        final HavingEvaluator havingEvaluator = new HavingEvaluator() {
            @Override
            public boolean evaluate(final Expression condition, final Row aggregatedRow) {
                // Simulate evaluation of "COUNT(*) > 2"
                // COUNT(*) is at index 1
                final Long count = (Long) aggregatedRow.getValue(1);
                return count > 2;
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .functionRegistry(functionRegistry)
            .build();

        // Create and execute HAVING operator
        final HavingOperator havingOp = new HavingOperator("COUNT(*) > 2", havingEvaluator);
        final List<Row> result = havingOp.execute(aggregatedRows, context);

        // Should return only Engineering group (count = 3)
        assertEquals(1, result.size());
        assertEquals("Engineering", result.get(0).getValue(0));
        assertEquals(3L, result.get(0).getValue(1));
    }

    @Test
    public void testHavingWithMultipleConditions() {
        // Simulate aggregated rows after GROUP BY
        final List<Row> aggregatedRows = new ArrayList<>();
        aggregatedRows.add(new Row(Arrays.asList("Engineering", 3L, 75.0)));
        aggregatedRows.add(new Row(Arrays.asList("Sales", 2L, 57.5)));
        aggregatedRows.add(new Row(Arrays.asList("Marketing", 4L, 65.0)));

        // Create HAVING evaluator: COUNT(*) >= 3 AND AVG(age) > 60
        final HavingEvaluator havingEvaluator = new HavingEvaluator() {
            @Override
            public boolean evaluate(final Expression condition, final Row aggregatedRow) {
                final Long count = (Long) aggregatedRow.getValue(1);
                final Double avgAge = (Double) aggregatedRow.getValue(2);
                return count >= 3 && avgAge > 60;
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .functionRegistry(functionRegistry)
            .build();

        final HavingOperator havingOp = new HavingOperator("COUNT(*) >= 3 AND AVG(age) > 60", havingEvaluator);
        final List<Row> result = havingOp.execute(aggregatedRows, context);

        // Should return Engineering (3, 75.0) and Marketing (4, 65.0)
        assertEquals(2, result.size());
        assertEquals("Engineering", result.get(0).getValue(0));
        assertEquals("Marketing", result.get(1).getValue(0));
    }

    @Test
    public void testGroupByWithHavingPipeline() {
        // This test simulates a GROUP BY + HAVING pipeline
        // Start with original data, simulate GROUP BY, then apply HAVING

        // For this test, we'll manually create aggregated rows as if GROUP BY was done
        final List<Row> aggregatedRows = new ArrayList<>();
        // Simulate: SELECT dept, COUNT(*) FROM employees GROUP BY dept
        aggregatedRows.add(new Row(Arrays.asList("Engineering", 3L)));
        aggregatedRows.add(new Row(Arrays.asList("Sales", 2L)));

        // HAVING COUNT(*) > 2
        final HavingEvaluator havingEvaluator = new HavingEvaluator() {
            @Override
            public boolean evaluate(final Expression condition, final Row aggregatedRow) {
                final Long count = (Long) aggregatedRow.getValue(1);
                return count > 2;
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .functionRegistry(functionRegistry)
            .build();

        // Build pipeline with just HAVING (GROUP BY would be before this in real scenario)
        final OperatorPipeline pipeline = OperatorPipeline.builder()
            .context(context)
            .addOperator(new HavingOperator("COUNT(*) > 2", havingEvaluator))
            .build();

        final List<Row> result = pipeline.execute(aggregatedRows);

        // Should filter to only Engineering
        assertEquals(1, result.size());
        assertEquals("Engineering", result.get(0).getValue(0));
    }

    @Test
    public void testAsofJoinOperator() {
        // ASOF JOIN: for each left row, the CLOSEST right row satisfying the MATCH_CONDITION, left-outer.
        // Live-verified against Snowflake: `>=` keeps the GREATEST qualifying right value, and a left row
        // with no qualifying right row survives null-extended.
        final List<TableColumn> rightColumns = Arrays.asList(
            new TableColumn("user_id", NumericType.INTEGER, true, null, false, false, false),
            new TableColumn("as_of", NumericType.INTEGER, true, null, false, false, false)
        );
        final Table quoteTable = new Table("quotes", rightColumns, false);

        final List<Row> quotes = new ArrayList<>();
        quotes.add(new Row(Arrays.asList(1L, 20L)));
        quotes.add(new Row(Arrays.asList(1L, 26L)));
        quotes.add(new Row(Arrays.asList(1L, 40L)));

        // MATCH_CONDITION (users.age >= quotes.as_of): age is index 2 on the left, as_of index 1 right.
        final Expression leftKey = new ColumnReferenceExpression("age");
        final Expression rightKey = new ColumnReferenceExpression("as_of");
        final RowExpressionEvaluator leftEval = new RowExpressionEvaluator() {
            @Override
            public Object evaluate(final Expression expr, final Row row) {
                return row.getValue(2);
            }
        };
        final RowExpressionEvaluator rightEval = new RowExpressionEvaluator() {
            @Override
            public Object evaluate(final Expression expr, final Row row) {
                return row.getValue(1);
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        final AsofJoinOperator asofOp = new AsofJoinOperator(testTable, quoteTable, quotes,
            BinaryOperator.GREATER_THAN_OR_EQUAL, leftKey, leftEval, rightKey, rightEval, null);

        final List<Row> result = asofOp.execute(testData, context);

        // Every left row survives, widened by the right table's two columns.
        assertEquals(5, result.size());
        assertEquals(5, result.get(0).getValues().size());
        // Alice (30) -> 26, Bob (25) -> 20, Charlie (35) -> 26, David (28) -> 26, Eve (32) -> 26.
        assertEquals(26L, result.get(0).getValue(4));
        assertEquals(20L, result.get(1).getValue(4));
        assertEquals(26L, result.get(2).getValue(4));
        assertEquals(26L, result.get(3).getValue(4));
        assertEquals(26L, result.get(4).getValue(4));
    }

    @Test
    public void testAsofJoinOperatorKeepsUnmatchedLeftRows() {
        final List<TableColumn> rightColumns = Arrays.asList(
            new TableColumn("as_of", NumericType.INTEGER, true, null, false, false, false)
        );
        final Table quoteTable = new Table("quotes", rightColumns, false);

        final List<Row> quotes = new ArrayList<>();
        quotes.add(new Row(Arrays.asList(31L)));

        final Expression leftKey = new ColumnReferenceExpression("age");
        final Expression rightKey = new ColumnReferenceExpression("as_of");
        final RowExpressionEvaluator leftEval = new RowExpressionEvaluator() {
            @Override
            public Object evaluate(final Expression expr, final Row row) {
                return row.getValue(2);
            }
        };
        final RowExpressionEvaluator rightEval = new RowExpressionEvaluator() {
            @Override
            public Object evaluate(final Expression expr, final Row row) {
                return row.getValue(0);
            }
        };

        final OperatorContext context = OperatorContext.builder()
            .table(testTable)
            .functionRegistry(functionRegistry)
            .build();

        final AsofJoinOperator asofOp = new AsofJoinOperator(testTable, quoteTable, quotes,
            BinaryOperator.GREATER_THAN_OR_EQUAL, leftKey, leftEval, rightKey, rightEval, null);

        final List<Row> result = asofOp.execute(testData, context);

        assertEquals(5, result.size());
        // Only Charlie (35) and Eve (32) reach 31; the rest are null-extended.
        assertNull(result.get(0).getValue(3));
        assertNull(result.get(1).getValue(3));
        assertEquals(31L, result.get(2).getValue(3));
        assertNull(result.get(3).getValue(3));
        assertEquals(31L, result.get(4).getValue(3));
    }

    /** A stage planned with its own context runs under it; one planned without runs under the pipeline's. */
    @Test
    public void aStageRunsUnderItsOwnContextWhereItHasOne() {
        final OperatorContext pipelineContext = OperatorContext.builder()
            .table(testTable).functionRegistry(functionRegistry).build();
        final OperatorContext ownContext = OperatorContext.builder()
            .table(testTable).functionRegistry(functionRegistry).build();
        final List<OperatorContext> seen = new ArrayList<>();
        final Operator spy = new Operator() {
            @Override
            public List<Row> execute(final List<Row> input, final OperatorContext context) {
                seen.add(context);
                return input;
            }

            @Override
            public String getDescription() {
                return "SPY";
            }
        };
        final OperatorPipeline pipeline = OperatorPipeline.builder()
            .context(pipelineContext)
            .addOperator(spy)
            .addStage(spy, ownContext)
            .build();
        assertEquals(5, pipeline.execute(testData).size());
        assertEquals(2, pipeline.stageCount());
        assertTrue(seen.get(0) == pipelineContext, "a stage without a context takes the pipeline's");
        assertTrue(seen.get(1) == ownContext, "a stage with a context keeps it");
    }

    /** A source stage answers its relation whatever reaches it, so a pipeline can start from nothing. */
    @Test
    public void aSourceStageStartsThePipeline() {
        final OperatorContext context = OperatorContext.builder()
            .table(testTable).functionRegistry(functionRegistry).build();
        final OperatorPipeline pipeline = OperatorPipeline.builder()
            .context(context)
            .addOperator(new SourceOperator(testData, "SOURCE[users]"))
            .addOperator(new LimitOperator(2))
            .build();
        final List<Row> result = pipeline.execute(new ArrayList<Row>());
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).getValue(1));
        assertEquals("Pipeline[SOURCE[users] -> LIMIT[2]]", pipeline.getDescription());
    }

    /** A stage operator is a plain rows-in, rows-out function, named in the pipeline's description. */
    @Test
    public void aStageOperatorIsARowsFunction() {
        final OperatorContext context = OperatorContext.builder()
            .table(testTable).functionRegistry(functionRegistry).build();
        final Operator evenIds = new StageOperator("EVEN[id]") {
            @Override
            protected List<Row> apply(final List<Row> input) {
                final List<Row> kept = new ArrayList<>();
                for (final Row row : input) {
                    if (((Long) row.getValue(0)) % 2 == 0) {
                        kept.add(row);
                    }
                }
                return kept;
            }
        };
        final OperatorPipeline pipeline = OperatorPipeline.builder()
            .context(context)
            .addOperator(evenIds)
            .build();
        final List<Row> result = pipeline.execute(testData);
        assertEquals(2, result.size());
        assertEquals("Bob", result.get(0).getValue(1));
        assertEquals("David", result.get(1).getValue(1));
        assertEquals("Pipeline[EVEN[id]]", pipeline.getDescription());
    }

    /** An offset beside the widest limit stays within the input: the window is computed without overflowing. */
    @Test
    public void theWidestLimitWithAnOffsetAnswersTheRest() {
        final OperatorContext context = OperatorContext.builder()
            .table(testTable).functionRegistry(functionRegistry).build();
        final List<Row> result = new LimitOperator(Integer.MAX_VALUE, 1).execute(testData, context);
        assertEquals(4, result.size());
        assertEquals("Bob", result.get(0).getValue(1));
    }
}
