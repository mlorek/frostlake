# Performance Tests

## ThreeTableJoinPerformanceTest

A comprehensive performance test for 3-table joins with realistic data volumes.

### Test Configuration

- **Rows per table:** 10,000
- **Batch size:** 1,000 rows per INSERT
- **Total rows:** 30,000 (across 3 tables)
- **Columns per table:** 10
- **Relationship:** 1-to-1-to-1 (matching IDs)

### Tables Schema

**customers** (10 columns, 10k rows)
- customer_id, name, email, phone, address, city, state, zipcode, country, registration_date

**orders** (10 columns, 10k rows)
- order_id, customer_id, order_date, total_amount, status, payment_method, shipping_address, tracking_number, notes, processed_by

**order_details** (10 columns, 10k rows)
- detail_id, order_id, product_name, quantity, unit_price, discount, tax_amount, subtotal, category, supplier

### Test Scenarios

1. **testThreeTableInnerJoinPerformance**
   - Basic 3-way INNER JOIN
   - Returns all 10,000 matched rows
   - Selects 12 columns from all 3 tables
   - Validates row count and query completion time

2. **testThreeTableJoinWithFilter**
   - 3-way JOIN with WHERE clause
   - Filters: status = 'COMPLETED' AND quantity > 5
   - Tests selective query performance
   - Returns subset of data

3. **testThreeTableJoinWithAggregation**
   - 3-way JOIN with GROUP BY
   - Aggregations: COUNT, SUM, AVG
   - Groups by city (approximately 100 groups)
   - Tests aggregation performance on joined data

4. **testThreeTableLeftJoinPerformance**
   - 3-way LEFT JOIN
   - Returns all customers with their orders
   - Tests outer join performance

5. **testThreeTableJoinSelectivity**
   - 3-way JOIN with high selectivity
   - Filter: customer_id < 100
   - Returns only 99 rows
   - Tests early filtering optimization

### Performance Metrics

Each test reports:
- Setup time (table creation + data insertion)
- Query execution time (milliseconds)
- Rows returned
- Throughput (rows/second)
- Columns in result set

### Benchmark Results (10,000 rows per table)

- 3-table INNER JOIN (10k rows out): ~25 s, ~405 rows/sec
- 3-table LEFT JOIN (10k rows out): ~27 s
- JOIN + filter (~2k rows out): ~25 s
- JOIN + GROUP BY city: ~27 s
- JOIN + `customer_id < 100` (99 rows out): ~26 s

NOTE: these are slow because multi-way (3+ table) joins currently run as **nested loops**
(O(n²) per join pair) — the hash-join path that makes 2-table joins fast (tens of ms at far
larger row counts, see `EmbeddedDbComparisonTest`/`PerfLoadTest`) does not yet extend to 3+
tables, and predicates are not pushed below the join (the `customer_id < 100` query still joins
all 30k rows before filtering to 99). The 30 s assertions are a loose "not catastrophically
worse" regression guard, not a performance target.

### Scalability

Each join pair is O(n²) (nested loop), so wall time grows quadratically with ROWS_PER_TABLE:
- 1,000 rows: quick validation
- 10,000 rows: ~25 s per query (the default)
- 100,000 rows: impractical until multi-table joins are hash-joined

### Running the Tests

```bash
# This test is @Tag("perf") — excluded from a plain `mvn test`; run it via the perf profile:
mvn test -Pperf -Dtest=ThreeTableJoinPerformanceTest

# Run one scenario
mvn test -Pperf -Dtest=ThreeTableJoinPerformanceTest#testThreeTableInnerJoinPerformance

# Run with a fixed 12 GB heap (the perf-mem profile)
mvn test -Pperf-mem -Dtest=ThreeTableJoinPerformanceTest

# Run every perf harness in dev.frostlake.perf
mvn test -Pperf
```

### Performance Thresholds

All tests include a 30-second timeout assertion to catch performance regressions:
```java
assertTrue(duration < 30000, "Query should complete in under 30 seconds");
```

### Notes

- Data is generated programmatically with realistic patterns
- Batch inserts optimize setup time
- Progress messages display during data insertion
- All assertions verify both correctness and performance
