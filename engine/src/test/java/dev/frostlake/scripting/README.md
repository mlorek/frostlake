# Scripting Package Tests

This package contains tests for PL/SQL and procedural scripting features in the Frostlake SQL Engine.

## Test Files

### Stored Procedures

**CreateProcedureTest.java** (8 tests)
- Tests for creating stored procedures
- Procedure metadata validation
- Procedure invocation (basic support)
- Features: parameter handling, return types, qualified names

**CreateProcedureJdbcTest.java** (8 tests)
- JDBC-based stored procedure tests
- Tests IF statements in procedures
- Tests LOOP statements in procedures
- Tests OBJECT return types
- Dollar-quoted string support

### User-Defined Functions (UDFs)

**CreateFunctionTest.java** (7 tests)
- Tests for creating scalar functions
- Tests for creating table-valued functions
- Function metadata validation
- Features: parameter handling, return types, qualified names

**CreateFunctionJdbcTest.java** (4 tests)
- JDBC-based function tests
- Scalar function creation
- Table function creation
- Function listing via SHOW FUNCTIONS

### Dynamic SQL

**ExecuteImmediateDollarQuotedTest.java** (5 tests)
- Tests EXECUTE IMMEDIATE with dollar-quoted strings
- Dynamic DDL execution
- Dynamic DML execution
- Dynamic SELECT execution

**DollarQuotedTest.java** (8 tests)
- Dollar-quoted string literals ($$...$$)
- Used in procedure and function definitions
- Tests parsing of complex procedural code
- Handles special characters in procedure bodies

## Coverage

### Supported Features

✅ **CREATE PROCEDURE**
- With parameters
- With return types
- With qualified names (schema.procedure)
- With dollar-quoted bodies
- With single-quoted bodies

✅ **CREATE FUNCTION**
- Scalar functions (RETURNS type)
- Table functions (RETURNS TABLE(...))
- With parameters
- With qualified names

✅ **CALL Statement**
- Basic procedure invocation
- Parameter passing

✅ **Dollar-Quoted Strings**
- Single dollar signs: $$...$$
- Tagged dollar quotes: $tag$...$tag$
- Nested dollar quotes

✅ **EXECUTE IMMEDIATE**
- Dynamic SQL execution
- With dollar-quoted strings

### Procedural Constructs (Parsed but Limited Execution)

⚠️ **Partially Supported:**
- IF/THEN/ELSE statements
- LOOP statements
- WHILE statements
- DECLARE statements
- SET statements
- RETURN statements

**Note:** Procedural constructs are parsed and stored in procedure/function bodies, but full procedural execution is not yet implemented. Procedures and functions are stored in the catalog and can be listed via SHOW commands.

## Test Statistics

```
Total Tests: 40
- CreateProcedureTest: 8 tests
- CreateProcedureJdbcTest: 8 tests
- CreateFunctionTest: 7 tests
- CreateFunctionJdbcTest: 4 tests
- ExecuteImmediateDollarQuotedTest: 5 tests
- DollarQuotedTest: 8 tests

All tests passing ✓
```

## Related Code

### Main Implementation

```
src/main/java/dev/frostlake/
├── metastore/
│   ├── Procedure.java          # Procedure metadata
│   └── Function.java           # Function metadata
├── executor/
│   └── commands/
│       └── DDLCommandHandler.java  # CREATE PROCEDURE/FUNCTION handling
└── parser/
    └── SQL.g4         # Grammar for procedures/functions
```

### Other Related Tests

- `jdbc/CallableStatementTest.java` - JDBC CallableStatement API (stays in jdbc package)
- `TaskDollarQuotedTest.java` - Tasks with dollar-quoted definitions (in main test package)

## Test Patterns

### Creating a Procedure

```java
@Test
public void testCreateProcedure() {
    engine.execute("""
        CREATE PROCEDURE my_proc(x INTEGER, y INTEGER) 
        RETURNS INTEGER 
        AS 'RETURN x + y'
        """);
    
    // Verify in catalog
    Procedure proc = schema.getProcedure("MY_PROC");
    assertNotNull(proc);
    assertEquals(2, proc.getParameters().size());
}
```

### Using Dollar-Quoted Strings

```java
@Test
public void testDollarQuotes() {
    statement.execute("""
        CREATE PROCEDURE test_proc(x INTEGER) 
        RETURNS INTEGER 
        AS $$
            DECLARE result INTEGER;
            BEGIN
                SET result = x * 2;
                RETURN result;
            END;
        $$
        """);
}
```

### Dynamic SQL Execution

```java
@Test
public void testExecuteImmediate() {
    statement.execute("""
        EXECUTE IMMEDIATE $$
            CREATE TABLE products (id INTEGER, name VARCHAR)
        $$
        """);
}
```

## Known Limitations

1. **Procedural Execution**: IF/LOOP/WHILE statements are parsed but not executed
2. **CALL Statement**: Limited support - executes procedure body as SQL, not as procedural code
3. **Variable Scoping**: DECLARE statements are parsed but variables are not tracked
4. **Control Flow**: No branching or looping execution
5. **Exception Handling**: No TRY/CATCH support

## Future Enhancements

Potential areas for expansion:
- Full procedural language interpreter
- Variable scoping and tracking
- Control flow execution (IF, LOOP, WHILE, FOR)
- Exception handling (TRY/CATCH)
- Cursor support (OPEN, FETCH, CLOSE)
- Advanced parameter modes (IN, OUT, INOUT)
- Procedure overloading

## Running Tests

```bash
# Run all scripting tests
mvn test -Dtest="CreateProcedureTest,CreateFunctionTest,DollarQuotedTest,ExecuteImmediateDollarQuotedTest"

# Run specific test
mvn test -Dtest="CreateProcedureTest#testCreateSimpleProcedure"

# Run all tests including JDBC variants
mvn test -Dtest="*Procedure*Test,*Function*Test,DollarQuotedTest,ExecuteImmediate*Test"
```

## Documentation

For more information about procedural SQL features:
- See `docs/` for engine documentation
- See grammar file: `src/main/antlr4/dev/frostlake/parser/SQL.g4`
- Search for `proceduralStatement` in grammar for procedural constructs

---

Last Updated: April 13, 2026
