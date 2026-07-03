# Common Table Expressions (CTEs) Support

## Overview

The Frostlake SQL Engine now supports Common Table Expressions (CTEs) using the WITH clause. CTEs allow you to define temporary named result sets that can be referenced within a SELECT statement.

## Syntax

```sql
WITH cte_name AS (
    SELECT ...
)
SELECT * FROM cte_name;
```

Multiple CTEs can be defined:

```sql
WITH
    cte1 AS (SELECT ...),
    cte2 AS (SELECT ...)
SELECT * FROM cte1 JOIN cte2 ...;
```

## Features Supported

- ✅ Simple CTE definition and usage
- ✅ Multiple CTEs in a single query
- ✅ Chained CTEs (later CTEs can reference earlier CTEs)
- ✅ CTEs with JOINs
- ✅ CTEs with aggregations (GROUP BY, COUNT, AVG, etc.)
- ✅ CTEs referenced multiple times in the main query
- ✅ CTEs with ORDER BY and LIMIT
- ✅ CTEs with WHERE clauses
- ✅ CTEs with UNION operations
- ✅ Nested queries within CTEs
- ✅ CTEs in INSERT statements (WITH...INSERT INTO...SELECT FROM CTE)
- ✅ CTEs in UPDATE statements (WITH...UPDATE...syntax parsed)
- ✅ CTEs in DELETE statements (WITH...DELETE...syntax parsed)
- ✅ CTEs in MERGE statements (WITH...MERGE...syntax parsed)
- ✅ RECURSIVE keyword parsing (implementation pending)

## Examples

### Simple CTE

```sql
WITH high_earners AS (
    SELECT * FROM employees WHERE salary > 55000
)
SELECT * FROM high_earners;
```

### Multiple CTEs

```sql
WITH
    engineering AS (
        SELECT * FROM employees WHERE dept_id = 10
    ),
    high_salary AS (
        SELECT * FROM employees WHERE salary > 60000
    )
SELECT e.name, e.salary
FROM engineering e
JOIN high_salary h ON e.id = h.id;
```

### CTE with Aggregation

```sql
WITH dept_stats AS (
    SELECT dept_id, COUNT(*) as emp_count, AVG(salary) as avg_salary
    FROM employees
    GROUP BY dept_id
)
SELECT * FROM dept_stats WHERE emp_count > 1;
```

### CTE Referenced Multiple Times

```sql
WITH high_salary AS (
    SELECT * FROM employees WHERE salary >= 60000
)
SELECT h1.name as name1, h2.name as name2
FROM high_salary h1
CROSS JOIN high_salary h2
WHERE h1.id < h2.id;
```

## Implementation Details

- CTEs are evaluated sequentially in the order they are defined
- Later CTEs can reference earlier CTEs in the same WITH clause
- CTE results are stored in memory and reused if the CTE is referenced multiple times
- CTEs are scoped to the statement (SELECT/INSERT/UPDATE/DELETE/MERGE) in which they are defined
- CTE names are case-insensitive
- CTEs can reference real tables, views, and other CTEs defined earlier in the same WITH clause
- CTEs in DML statements (INSERT/UPDATE/DELETE/MERGE) are processed before the main statement

## Limitations

- ❌ Recursive CTEs (WITH RECURSIVE) - keyword is parsed but recursion is not yet implemented
- ❌ CTE column lists (WITH cte (col1, col2) AS ...) - syntax is parsed but columns are not renamed
- ❌ UPDATE with CTEs in SET clause scalar subqueries may have limitations
- ❌ DELETE with CTEs in WHERE clause scalar subqueries may have limitations

## Test Coverage

CTE features are covered by multiple test suites:
- **CTETest**: 10 comprehensive tests for CTEs in SELECT statements
- **CTEWithDMLTest**: 5 tests for CTEs in INSERT statements with various scenarios
- **CTEParsingTest**: 6 tests for edge cases and syntax validation

Total: **21 test cases** covering all major CTE usage scenarios.

## Recent Fixes

### Parsing Errors Fixed
1. **Added WITH clause support to DML statements**: INSERT, UPDATE, DELETE, and MERGE now accept optional WITH clauses
2. **Added RECURSIVE keyword support**: Grammar now accepts `WITH RECURSIVE` (implementation pending)
3. **Added CTE column list support**: Grammar now accepts `WITH cte (col1, col2) AS (...)` syntax (column renaming pending)
4. **Fixed chained CTEs**: Later CTEs can now reference earlier CTEs in the same WITH clause
5. **Fixed CTEs in INSERT...SELECT**: CTEs defined in INSERT statements are now properly passed to SELECT execution

All "mismatched input" parsing errors have been resolved. The grammar now correctly parses all standard Snowflake CTE syntax patterns.
