# Changelog

Notable changes to Frostlake, newest first. Versions follow the published Maven artifacts
(`dev.frostlake:frostlake-db` and the six optional packs, which release together).

Frostlake's goal is to answer SQL the way a real Snowflake account does. That makes **a new refusal a
fix, not a regression**: SQL that Snowflake rejects and Frostlake accepted was a fidelity bug, and
closing it can break code that relied on the leniency. Those changes are listed first in every release
for exactly that reason.

## 0.3.1 — unreleased

Changes since 0.3.0 accumulate here as they land.

### Now refused (SQL that earlier versions accepted)

- **SET refuses more sources the account's folder does not take**: one calling a SQL function whose body calls such a
  function (`SET x = f(65)` over `f AS 'CHR(x)'`), a conversion to VARIANT of anything but a literal
  (`NULL::VARIANT::VARIANT`, `TO_VARIANT(NULL::VARCHAR)`, `TO_VARIANT(TO_VARIANT(NULL))`) and an IS NULL over a
  VARIANT (`NULL::VARIANT IS NULL`), each `Unsupported feature 'assignment from non-constant source expression'.`,
  where they were assigned.
- **A user's function the role may not use is refused as a MERGE or a subquery compiles**, in a MERGE's ON, SET and
  VALUES and in a subquery no row reaches, whether or not a row reaches the call, where it was refused only once a row
  did.
- **A set operation's number that the unified type cannot hold is refused at its row**, as a cast to that type is:
  `SELECT 0.3333333333333333333333333333333333333 UNION ALL SELECT 11` is `Number out of representable range: type
  FIXED[SB16](38,37){not null}, value 11`, where 11 was answered past the type's digits.
- **A managed access schema's ownership does not pass to a different role while a future OWNERSHIP grant in it is
  open** (documented), for GRANT OWNERSHIP ON SCHEMA and ON ALL SCHEMAS IN DATABASE, every schema asked before any
  moves, where the schema moved with the grant open.
- **A masking or projection policy a materialized view's column carries is not dropped or replaced while it does**, as
  for a table's or a view's column, where DROP and CREATE OR REPLACE went through.
- **An untyped LET or DECLARE value that gives a date/time function an unknown unit as text is refused as the block
  compiles**, on a branch that never runs too: `IF (FALSE) THEN LET x := DATEADD('nosuchpart', 1, CURRENT_DATE()); END
  IF;` is `['nosuchpart'] is not a valid date/time component for function DATEADD.`, where the block ran.
- **SET refuses a source the account's folder cannot fold**: a source calling DATE_FROM_PARTS, TIME_FROM_PARTS,
  DATEDIFF, MONTHNAME, ROUND, LENGTH, REPLACE, HASH, RANDOM or another function the folder does not take, outside a
  scalar subquery, is refused with `Unsupported feature 'assignment from non-constant source expression'.` Before, the
  variable was set.
- **A virtual column named as a target**: `INSERT INTO t (a, b) …`, `UPDATE t SET b = …` and a MERGE's INSERT list or
  SET are "Virtual column 'B' is invalid target.".
- **A virtual column's definition the account refuses**: a NOT NULL or a CHECK on the column; an expression whose type
  the column does not take ("Data type of virtual column does not match the data type of its expression for column
  'B'. …"); an aggregate, a window function, a user's function, a function whose answer moves (RANDOM,
  CURRENT_TIMESTAMP, …), a subquery, a bind variable, a session variable or `$1` in the expression; and a name it does
  not find, a virtual column defined after it among them. Dropping a column a virtual column reads is "Cannot drop
  column 'I' because virtual column J depends on it.".
- **An array or an object constructor as an item of a VALUES list in FROM**: `SELECT * FROM VALUES ([1])` is "Invalid
  expression [ARRAY_CONSTRUCT(1)] in VALUES clause", as it is in an INSERT's VALUES list.
- **A structured value written to a structured column must coerce to the column's type**: an `ARRAY(VARCHAR)` into an
  `ARRAY(INT)` or `ARRAY(DECFLOAT)` column is refused while the write compiles, `Expression type does not match column
  data type, expecting ARRAY(NUMBER(38,0)) but got ARRAY(VARCHAR(134217728)) for column AR`. So are a MAP into an
  OBJECT column, an OBJECT into a MAP column, an OBJECT with other keys, and a timestamp element of another flavour.
  The text element failed per row; the MAP and DECFLOAT writes were accepted.
- **A table's COMMENT given twice, once as a text and once as a word or NULL, is refused** as `duplicate property
  'COMMENT';`, as two texts are.
- **An aggregate in a recursive term's HAVING is refused**: `Aggregate functions are not allowed in a CTEs recursive
  term.`, as one in its select list is, for a CTE and a RECURSIVE view, once a bare column beside it has been refused
  as `[R.N] is not a valid group by expression`. A term with no column beside the aggregate ran as an aggregating step
  and recursed without end; a HAVING with no aggregate still filters the step.
- **A text in non-ASCII digits no longer converts to a number**: `'٤٨'::NUMBER`, `n = '４８'`, `n + '٤٨'`, `ABS('٢')`,
  an INSERT of `'٤٨'` into a NUMBER column and every other conversion of such a text are refused with `Numeric value
  '٤٨' is not recognized`, as TO_NUMBER refused it already; `TO_BINARY('٤٨')`, `'４８'::BINARY` and `X'٤٨'` are refused
  as no hexadecimal digits. Arabic-Indic, fullwidth and other Unicode decimal digits converted as the ASCII digits
  they stand for.
- **A projection policy on a column a row access policy or a conditional masking policy reads as an argument**
  (`Cannot apply projection policy on 'R' because it is used as an argument by one or more policies.`, SQLCODE
  506002), and a conditional masking policy reading a column a projection policy guards (`Column 'C' cannot be used as
  policy argument because it has a projection policy attached.`); the column a masking policy masks still takes a
  projection policy.
- **ALTER SCHEMA … ENABLE MANAGED ACCESS by a schema owner without MANAGE GRANTS, or while a future grant in the
  schema is open**, and DISABLE MANAGED ACCESS without MANAGE GRANTS while one is open.
- **GRANT OWNERSHIP in a schema WITH MANAGED ACCESS to a role that is neither the schema's owner nor granted to it**,
  naming the object, ON ALL objects in the schema or its database, or ON FUTURE objects in the schema, whichever role
  grants it: ownership there passes only to a subordinate role of the schema owner.
- SKEW and KURTOSIS over a series that does not vary, of any type, in an aggregate or a window: `Division by zero`, as
  Snowflake refuses them; they answered 0 and NULL.
- **CREATE ROLE, DROP ROLE, DROP USER and ALTER ACCOUNT SET or UNSET TAG ask their privileges** (documented): CREATE
  ROLE asks CREATE ROLE on the account, DROP ROLE, DROP USER and CREATE OR REPLACE ROLE OWNERSHIP of what they remove,
  and the account's tags MODIFY on the account, in the account's privilege sentence, where any role ran them. The
  engine's own SYSTEM session is asked nothing, as for the account's other privileges.
- **DROP WAREHOUSE, DROP ALERT and ALTER ALERT of an object another role owns are refused** (documented): DROP asks
  OWNERSHIP, ALTER ALERT … RESUME or SUSPEND OPERATE and any other ALTER ALERT MODIFY, where any role dropped or
  altered them. A rename of a dynamic table or a materialized view asks OWNERSHIP too, as a table's does.
- **A SQL function whose body writes a comma inside a CAST's parentheses is refused as it is created**
  (live-verified): the body is read in its frame between parentheses, so `CREATE FUNCTION f() RETURNS INT AS 'SELECT
  UPPER(CAST(1, 2))'` is `Compilation of SQL UDF failed: SQL compilation error:` with `syntax error line 1 at position
  1 unexpected 'SELECT'.` and `syntax error line 1 at position 14 unexpected 'CAST'.`, the second line naming where
  the reading resumes, the token after the first `(` or the cast's comma; an expression body names the comma and then
  the frame's closing `)`, a TRY_CAST's too, where the function was created.
- **DROP MASKING POLICY of a policy a view's own column carries is refused** (live-verified): `SQL compilation error:
  Policy MP751 cannot be dropped/replaced as it is associated with one or more entities.`, whether the view's column
  list or an ALTER VIEW attached it, where the policy was dropped.
- **CREATE OR REPLACE of an attached row access policy, and DROP of a join policy a view carries, are refused**
  (live-verified): `SQL compilation error: Policy RAP7 cannot be dropped/replaced as it is associated with one or more
  entities.`, as the policy's DROP and a masking policy's replacement are, where the row access policy was replaced
  under its table and the join policy dropped from under the view.
- **A column DEFAULT reads only the columns defined before it** (live-verified): `CREATE TABLE t (c INT DEFAULT c2 +
  1, c2 INT)` is `invalid identifier 'C2'` where the c2 stands, as is a name no column carries or a qualified one, and
  `c INT DEFAULT c + 1` is `Column 'C' cannot reference itself in its default value expression.`, where each table was
  created; `(c2 INT, c INT DEFAULT c2 + 1)` is created. An added column's DEFAULT reading a name the table lacks is
  `invalid identifier 'NOSUCH'`, and one reading its columns echoes them named by the table, `Invalid column default
  expression [T.C2 + 1]`.
- **VALUES refuses a whole item that is a query the plan does not fold** (live-verified): `INSERT INTO t VALUES
  ((SELECT MAX(12345)))` is `Invalid expression [(SELECT MAX(12345) AS "MAX(12345)" FROM (VALUES (null)) DUAL)] in
  VALUES clause`, a query of a table of one row echoes the row folded into it, `[(SELECT 12345 AS "V" FROM TABLE
  (GENERATOR(ROWCOUNT => 1, rowCount => 1)))]`, a LIMIT, an OFFSET, a DISTINCT, a GROUP BY, a false filter and a SUM
  over that row included, and a query of a table of other sizes as it reads it, where the query's value was written; a
  query of no table that does not aggregate, an aggregate the statistics answer and a MIN or a MAX over a table of one
  row are still taken.
- **MAP_PICK and MAP_DELETE refuse an ARRAY among several keys** (live-verified): `MAP_PICK(m, 'a', ['b'])` is `Can
  not convert parameter 'ARRAY_CONSTRUCT('b')' of type [ARRAY] into expected type [VARCHAR(1)]`, `MAP_PICK(m, ['a'],
  'b')` the same of `''b''` into `[ARRAY]`, and two ARRAYs, or an ARRAY and a NULL, `Function MAP_PICK cannot be used
  with arguments of types MAP(VARCHAR(134217728), NUMBER(38,0)) and ARRAY`, where the keys were read.
- **SYSTEM$AUTO_REFRESH_STATUS judges its argument** (live-verified): it takes one argument, a NULL is `inputs may not
  be null`, a number or a column `argument 0 to function … needs to be constant, found 'CAST(5 AS
  VARCHAR(134217728))'`, a missing object is named as written, and a view, a table or a temporary table is `invalid
  parameter 'Auto Refresh is not supported for table AR681'`, also in a query that answers no row, where every call
  answered `{"state": "Active"}`.
- **A table function's getOutputClass must be static and its endPartition an instance's** (live-verified): `The
  getOutputClass() method must be static and return a java.lang.Class`, and `Optional endPartition() method must be
  public, non-static, and return a java.util.stream.Stream` for a static one or one that answers no Stream, each when
  the function is created.
- **A Scala table function is refused** (live-verified): one whose handler keeps the Java rules is `Table functions
  not supported for language 'SCALA'.`, where it was created and ran.
- **A SQL procedure whose body declares a variable of an unknown type** (live-verified): `CREATE PROCEDURE p() RETURNS
  INT LANGUAGE SQL AS $$ DECLARE x nosuch; BEGIN RETURN 1; END $$` is refused at CREATE with `Unsupported data type
  'NOSUCH'.`, where it was created.
- **SYSTEM$REFERENCE of a routine that is not there, and privileges no reference takes** (live-verified):
  `SYSTEM$REFERENCE('PROCEDURE', 'p(NUMBER)')` and `('FUNCTION', 'f(VARCHAR)')` naming no overload are `Procedure
  'p(NUMBER)' does not exist or not authorized.`; a function or a procedure takes USAGE alone, a warehouse USAGE,
  OPERATE, MONITOR, MODIFY and APPLYBUDGET, and a warehouse reference that is not PERSISTENT USAGE alone.
- **A WINDOW clause in a SQL function body** (live-verified): `CREATE FUNCTION f() RETURNS NUMBER AS 'SELECT a FROM t
  WHERE a > 0 WINDOW w AS (ORDER BY a)'` is refused at the body's first word and the WINDOW, where it was created.
- **A name in a column DEFAULT's condition** (live-verified): `DEFAULT NOT c2` is refused where the name stands,
  `invalid identifier 'C2'`, and ALTER TABLE ADD COLUMN refuses a condition as a computed default, `Invalid column
  default expression [NOT(FALSE)]`.
- **An untyped LET of a query of more than one item** (live-verified): `LET q := (SELECT 1, 2 x)` is refused while the
  block compiles, ` variable 'Q' cannot have its type inferred from initializer`, where it failed as it ran.
- **SYSTEM$ names a SQL function's body calls** (live-verified): a name the account does not have is refused at
  CREATE, `Unknown function SYSTEM$NOSUCH_FN.`, as is a table function written where a value is read, `Unknown
  function SYSTEM$STREAM_BACKLOG.`; a view, a CTAS or a body calling SYSTEM$AUTO_REFRESH_STATUS is refused, `View or
  UDF contains a function with side effects [SYSTEM$AUTO_REFRESH_STATUS]`.
- **AUTOCOMMIT_API_SUPPORTED at a session, a task or an alert** (live-verified): every SET is refused, `invalid value
  [true] for parameter 'AUTOCOMMIT_API_SUPPORTED'`, where the session and a task took a boolean and an alert refused
  one for access; an UNSET is taken.
- **A file format given a COMMENT alone** (live-verified): `CREATE FILE FORMAT f COMMENT = 'x'` is refused, `Missing
  option(s): type`, where a CSV format was created; one given no option, or any option beside its COMMENT, is still
  created.
- **Ambiguous Java table function handlers** (live-verified): a handler class with two `process` methods of the
  function's argument count is refused as the function is created, `Cannot determine which implementation of handler
  "process" to invoke since there are multiple definitions with 1 arguments …`, where one of them was called.
- **A quoted value for a table's switch in ALTER TABLE** (live-verified): `ALTER TABLE t SET CHANGE_TRACKING = 'TRUE'`
  is refused, `invalid value ['TRUE'] for parameter 'CHANGE_TRACKING'`, as CREATE TABLE refuses it, where it turned
  change tracking off.
- **CREATE OR ALTER TABLE and VIEW with a policy or a column tag** (live-verified): a table's row access, aggregation
  or join policy, a column's masking or projection policy or tag, and a view's row access policy refuse the statement
  with `CREATE OR ALTER does not support setting policies or tags.` once the policy is found, where the object was
  created or altered.
- **GENERATOR subquery bounds the account does not fold** (live-verified): `ROWCOUNT => (SELECT SUM(n) FROM gn)`,
  `(SELECT n FROM gn ORDER BY n LIMIT 1)`, a correlated `(SELECT MAX(x.n) FROM gn x WHERE x.n <= gn.n)` and a set
  operation's count are refused as no constant, a subquery over one table echoed as the plan holds it — `(SELECT
  SUM(CAST(GN.N AS NUMBER(38,0))) AS "SUM(N)" FROM GN AS GN)` — where they were answered; a MIN, a MAX or a COUNT of a
  table and a constant subquery are still answered.
- **SYSTEM$REFERENCE scopes and default privileges** (live-verified): a CALL or PERSISTENT token a query makes names
  no object once its statement has ended, where it read its table on; a table or view reference made with no privilege
  takes SELECT alone, and an UPDATE or a DELETE that reads the rows it changes takes SELECT beside its own privilege,
  where both wrote.
- **A read-only request that draws a sequence through a function's body**: `SELECT f()` whose SQL function draws
  `sq.NEXTVAL`, sent read-only, is refused as the request's own NEXTVAL is, where the sequence moved.
- **A grouped select-list aggregate over a subquery with an aggregate of its own, under a test** (live-verified):
  `MAX((SELECT MAX(g.v) FROM g WHERE g.id = t.id)) > 0` is refused as nested aggregates, `[MAX(G.V)] nested in
  [MAX(MAX(G.V))]`, where it was answered.
- **SQL function bodies with a nested query's parameter row count or a named window** (live-verified): a body such as
  `SELECT COUNT(*) FROM (SELECT a FROM t LIMIT n)`, `n` a parameter, alike after OFFSET and FETCH FIRST, or `SELECT
  SUM(a) OVER w FROM t WINDOW w AS (ORDER BY a)` is refused at CREATE with the syntax error at the body's first word,
  where the function was created.
- **EXPLAIN DROP of a missing pipe, stream, task, procedure, masking policy or tag, and EXPLAIN ALTER TABLE over a
  view** (live-verified): each is refused as the plain statement is, `Stream '…' does not exist or not authorized.`
  and `Object found is of type 'VIEW', not specified type 'TABLE'.`, where a plan was returned.
- **ROUND with a mode beside a FLOAT scale** (live-verified): `ROUND(1.2345, 2::FLOAT, 'HALF_TO_EVEN')` is `too many
  arguments for function [ROUND] expected 2, got 3`, as beside a FLOAT input, where the mode was read.
- **A cast into GEOGRAPHY or GEOMETRY of any typed value** (live-verified): `'POINT(1 2)'::GEOMETRY` is `invalid type
  [CAST('POINT(1 2)' AS GEOMETRY)] for parameter 'TO_GEOMETRY'`, and alike a number, a VARIANT, an OBJECT, a BINARY,
  a text column, a typed NULL text and TRY_CAST, where the value passed through; TO_GEOGRAPHY and TO_GEOMETRY are the
  conversions. A NULL cast to either is a geospatial value, refused as a GROUP BY or ORDER BY key (a FROM-less ORDER
  BY's included), as a function's or an operator's argument and as a cast source, `[CAST(UNDERLYING_TO_UDT(NULL) AS
  VARCHAR(134217728))]`.
- **A time, a timestamp, a text or a VARIANT other than an object given to a block's OBJECT variable**
  (live-verified): `invalid type [CAST(TO_TIME('10:00:00') AS OBJECT)] for parameter 'TO_OBJECT'`, as a DATE was
  refused; a text is `[CAST('x' AS OBJECT)]` whether assigned, a DEFAULT, a typed LET or an expression, and
  `[CAST(identity('x') AS OBJECT)]` selected INTO; a VARIANT is `Failed to cast variant value "y" to OBJECT`. The
  value used to be held.
- **An ARRAY, a VARIANT, a text or a VECTOR of another type written to a VECTOR column** (live-verified): refused
  while the INSERT, the UPDATE or the MERGE compiles, `Expression type does not match column data type, expecting
  VECTOR(FLOAT, 3) but got ARRAY for column V`, where the value was converted.
- **A SQL function body that calls SYSTEM$LOG, a SYSTEM$LOG_<level>, SYSTEM$ADD_EVENT or SYSTEM$SET_SPAN_ATTRIBUTES**
  (live-verified): refused at CREATE with `Unknown function SYSTEM$LOG`, where the function was created.
- **SYSTEM$SET_RETURN_VALUE outside a task** (live-verified): `Function SYSTEM$SET_RETURN_VALUE must be called from
  within a task.`, and in a procedure that runs with its owner's rights `Query called from a stored procedure contains
  a function with side effects [SYSTEM$SET_RETURN_VALUE].`, where it answered its argument.
- **SYSTEM$TASK_DEPENDENTS_ENABLE of a task that is not there** (live-verified): `invalid value [nosuch_task] for
  parameter 'RECURSIVE_TASK_ENABLE'`, the name as written, and a task named by a number as no constant, where it
  answered `Statement executed successfully.`; a NULL answers NULL.
- **GET of a VARIANT at a FLOAT, DECFLOAT, date or time, or binary index** (live-verified): `Invalid argument types
  for function 'GET': (FILE, FLOAT)` as the call compiles, at the call, where the call answered: NULL at a FLOAT or a
  date index, the element at a DECFLOAT one (a binary index was refused naming the VARIANT). An ARRAY or an OBJECT
  read at a FLOAT index still answers NULL.
- **A DECFLOAT column in a hybrid table, and search optimization ON EQUALITY or SUBSTRING of a DECFLOAT column**
  (live-verified): `This feature is not supported for hybrid tables: 'DECFLOAT(38)'.`, ahead of the table's keys, and
  `Expression EQUALITY(IDX_SRC_TABLE.D) cannot be used in search optimization.` at the column. A clustering key on a
  DECFLOAT or an expression of one, and search optimization of the whole table, still take it, as the account does.
- **A MAP keyed by anything but a text or a NUMBER of no scale** (live-verified), wherever the type is written — a
  cast, a TRY_CAST, a column, ALTER TABLE … ADD COLUMN, a routine's parameter or result, a MAP or an ARRAY nesting
  one, EXPLAIN: `Unsupported data type 'MAP with key type FLOAT'.`, the key named canonically (`TIMESTAMP_NTZ(9)`,
  `BINARY(67108864)`, `ARRAY(NUMBER(38,0))`) and a GEOGRAPHY as `UNKNOWN`. A FLOAT, BOOLEAN, DATE, TIME, timestamp,
  BINARY, VARIANT, VECTOR, UUID or structured key was taken.
- **A GENERATOR bound calling TIMEDIFF, TIMESTAMPDIFF or POSITION** (live-verified), as one calling DATEDIFF or
  CHARINDEX already was: `argument 1 to function GENERATOR needs to be constant, found
  'DATE_DIFFDATEINDAYS('2024-01-01', '2024-01-04')'`, POSITION echoed as `POSITION('c', 'abc')` in both its spellings,
  a TIMELIMIT as argument 2 and an EXPLAIN alike. The bound was folded and the generator ran.
- **BITAND_AGG, BITOR_AGG and BITXOR_AGG of a VECTOR, as aggregates and as window functions, and
  CONDITIONAL_TRUE_EVENT of one** (live-verified), as the statement compiles and whatever rows it reads: `Invalid
  argument types for function 'BITAND_AGG': (VECTOR(FLOAT, 2))`. Over rows they failed reading the vector as a number
  or a boolean, and over none they answered.
- **The JDBC drivers refuse getBytes over a NUMBER with digits after its point, a DECFLOAT, a DATE, a TIME and every
  timestamp** (live-verified), as the account's driver does, quoting the cell's wire form: `Cannot convert value in
  the driver from type:93 to type:byte array, value=1704164645.123456789.` They read such a cell's text, and a
  timestamp's text differed between the HTTP and the in-process driver.
- **An ALTER TABLE or ALERT … SET value of another kind than its parameter's type** (live-verified; an ALTER DATABASE
  or SCHEMA … SET follows the same rule), refused before the object's level or the role's access is judged: a NUMBER
  takes a whole number, a FLOAT a number written with a fraction (`10.0`, not `7`), a BOOLEAN TRUE or FALSE and a
  STRING a text — `invalid value ['x'] for parameter 'DATA_RETENTION_TIME_IN_DAYS'`, `invalid value [5] for parameter
  'LOG_LEVEL'`. A parameter the object takes was set to it, and one it does not take was refused for want of access.
  `ALTER TABLE … SET INITIAL_REPLICATION_SIZE_LIMIT_IN_TB = 10.0` parses, where the number was a syntax error. An
  unknown STAGE property is named as written (`invalid property 'foo' for 'STAGE'`), and an unknown FILE FORMAT option
  is `invalid property 'foo' for 'FILE_FORMAT'` when a CREATE or ALTER FILE FORMAT writes it alone and `invalid
  parameter 'foo'`, as written, beside another option or in a stage's format list.
- **A MAP or ARRAY function's argument a structured value's types do not take** (live-verified), whatever rows the
  statement reads: a key of the other family in MAP_INSERT, MAP_PICK or MAP_DELETE (`Function MAP_PICK cannot be used
  with arguments of types MAP(VARCHAR(134217728), NUMBER(38,0)) and NUMBER(1,0)`); a plain ARRAY, OBJECT or VARIANT
  value for a structured value type, or a BOOLEAN for a number, in MAP_INSERT; maps whose keys or values are of two
  families in MAP_CAT; a structured ARRAY in ARRAY_FLATTEN, and one beside a plain ARRAY in ARRAYS_ZIP (`Invalid
  argument types for function '…': (…)`); and a value of another family than a structured ARRAY's elements in
  ARRAY_REMOVE (`Invalid type [VARIANT] for function 'ARRAY_REMOVE' at position 2`; a text was refused there in other
  words). Each was answered.
- **A correlated subquery over a derived relation of a shape of its own is refused in more places** (live-verified):
  in the HAVING and the ORDER BY, as in the select list (a WHERE's is still answered); over a relation on either side
  of an outer join, as over an inner one; correlated by two conditions, a non-equality or an OR, unless every outer
  column it reads is a constant of its relation or its relation is pruned; and over a relation that reads a table
  function, even under the statement's constant `WHERE 1 = 0`. Each answered no rows.
- **A name a table function's arguments read that nothing answers to is refused whatever rows reach the call**
  (live-verified), `invalid identifier` as over rows: written inside a subquery there (`TABLE(FLATTEN(INPUT => (SELECT
  nosuch FROM t)))`, correlated or not) over an empty relation to the call's left or over an empty table the subquery
  reads, and written anywhere in the arguments under a query that reads no row of its FROM (`WHERE 1 = 0`, `LIMIT 0`).
  Each answered no rows.
- **A JavaScript table function with a TIME column is refused at the call** (live-verified), as one with a NUMBER
  column already was: `Snowflake type TIME[SB8](0,9){nullable} is not supported in function F`. A scale up to 4 is
  stored as SB4, and scale 0 omits the parenthesised scale.
- **A running task graph refuses changes to its tasks** (live-verified). While the graph's root runs and has a child
  or a finalizer, each of the following is refused with `Unable to update graph with root task … since that root task
  is not suspended.`: ALTER TASK … SET, UNSET, MODIFY, REMOVE WHEN and SET or UNSET EXECUTE AS on any task of the
  graph; RESUME or SUSPEND of a child or finalizer; and DROP TASK of any task of the graph. Tags and contacts can
  still be changed, and the root can still be resumed and suspended.
- **ALTER TASK IF EXISTS and DROP TASK IF EXISTS forgive only a missing task**: when the task exists, its refusal
  stands. Before, IF EXISTS answered success.
- **A child task refuses SUSPEND_TASK_AFTER_NUM_FAILURES** (live-verified) with `Cannot set parameter
  SUSPEND_TASK_AFTER_NUM_FAILURES on non-root task …`, as it already refused TASK_AUTO_RETRY_ATTEMPTS. ALTER TASK …
  SET refuses both before it checks whether the graph is running.
- **CREATE OR ALTER refuses a tag clause** (live-verified): on a TASK, a TABLE or a VIEW, `WITH TAG (…)` is refused
  with `CREATE OR ALTER does not support setting policies or tags.` once every tag it names has been found. CREATE OR
  ALTER TASK no longer takes `WITH CONTACT (…)`.
- **The JDBC drivers refuse getArray and getObject with a type map** (live-verified), as the account's driver does
  over JSON and Arrow results alike: a semi-structured or structured ARRAY reads as its JSON text through getString
  and getObject.
- **RPAD refuses a BOOLEAN length, REGEXP_SUBSTR_ALL and REGEXP_EXTRACT_ALL a BINARY, and GREATEST_IGNORE_NULLS and
  LEAST_IGNORE_NULLS a GEOGRAPHY** (live-verified), as LPAD, REGEXP_SUBSTR, GREATEST and LEAST already did: `Invalid
  argument types for function 'RPAD': (VARCHAR(1), BOOLEAN)`, where they answered.
- **GET_PATH refuses a path it cannot read over a NULL source too** (live-verified): a path read with the row —
  `'a.'`, `'a['`, `'a..b'`, the empty path — is `Bad compound object's field path name '<path>' in GET_PATH` whatever
  the source holds, where a NULL source answered NULL before the path was read.
- **A path written as a literal refuses U+00C0, U+00C1 and U+00F5 to U+00FF in a bare name** (live-verified): every
  other character of Latin-1 past ASCII is taken, first in the name and later, and these are `Invalid extraction path
  'À': invalid token at position 0` (or `invalid field`); the same path read with the row takes them.
- **EXPLAIN of an INSERT, an UPDATE, a DELETE or a MERGE is refused as the statement is** (live-verified): the
  statement compiles whole before its plan is answered, so an unreachable merge case, a column or a source relation
  that is not there, a value list or a source query of another width than its columns and a column set or listed
  twice are refused at the statement's own place; a missing UPDATE or DELETE target is a missing `Object`. Nothing is
  read or written, and no transaction begins.
- **An INSERT … SELECT whose query answers another number of columns than the table has is refused before a row is
  read** (live-verified): `Insert value list does not match column list expecting 2 but got 1`, from an empty source
  too.
- **A view, a table's query or a SQL function body that calls SYSTEM$CANCEL_QUERY, SYSTEM$CANCEL_ALL_QUERIES,
  SYSTEM$SET_RETURN_VALUE, SYSTEM$TASK_DEPENDENTS_ENABLE or SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS is refused**,
  as one calling SYSTEM$ABORT_TRANSACTION was (live-verified): `View or UDF contains a function with side effects
  [<name>]`, the name quoted too.
- **DESCRIBE DATABASE SNOWFLAKE is refused** (live-verified): `Insufficient privileges to operate on database
  'SNOWFLAKE'. Your primary role R must have MONITOR granted on DATABASE SNOWFLAKE.`
- **CREATE NOTEBOOK … FROM a location is refused as CREATE STREAMLIT is** (live-verified): the notebook copies the
  location's files, so a user or a table stage is `Copy files not support stage kind: 'USER' for stage '~'.`, a stage
  that is not there and a location that is no stage URL are refused in Streamlit's words, and a stage that does not
  hold the MAIN_FILE — `notebook_app.ipynb` when none is named — is `Notebook MAIN_FILE cannot be found.`
- **A Python stored procedure without the Snowpark package is refused** (live-verified): its handler is handed a
  Snowpark session, which needs `snowflake-snowpark-python`. A procedure whose PACKAGES names nothing, an empty list
  included, is refused as it is created, a table procedure too, and one naming only other packages when it is called:
  `Python Interpreter Error:` and `ModuleNotFoundError: No module named 'snowflake.snowpark' in function P1 with
  handler main`. The name is read in any case and with a version; a function needs no Snowpark.
- **A FLOAT whole-number argument the function's fixed-point slot cannot hold is refused in that slot's words**
  (live-verified): NaN, an infinity or a value rounding past the slot's digits is `Number out of representable range:
  type FIXED[SB8](18,0){not null}, value 1e+30` for a code point, a SUBSTR, LEFT, RIGHT or INSERT position or length,
  a REGEXP position, occurrence, option or group, a GETBIT place and a RANDSTR length, and `FIXED[SB4](9,0)` for a
  CHARINDEX or POSITION start, a STRTOK or SPLIT_PART part (SPLIT_PART's always `{nullable}`), an ARRAY_SLICE bound,
  an ARRAY_INSERT or ARRAY_REMOVE_AT position, an XMLGET instance and a padded length; a nullable column is spelled
  `{nullable}`. NaN and the infinities were refused as `FIXED[SB16](38,0)` and a large FLOAT was held to 32 bits.
- **An exact whole-number argument a function reads in 32 bits is out of range past them, and any past 64 bits is not
  recognized** (live-verified): `CHARINDEX('b', 'abc', 3000000000)` is `Numeric value '3000000000' is out of range`,
  as are a STRTOK part, an ARRAY_SLICE bound, an ARRAY_INSERT or ARRAY_REMOVE_AT position and an XMLGET instance, and
  10^25 there, as a REGEXP position or a padded length, is `Numeric value '10000000000000000000000000' is not
  recognized`. Both were held to the nearest 32-bit number.
- **CHAR refuses a code no character has, as CHR does** (live-verified): CHAR is CHR's other name, so
  `CHAR(3000000000)` is `Invalid character code 3000000000 in the CHR input` and `CHAR('inf'::FLOAT)` the slot's
  refusal, where both answered NULL.
- **GETBIT refuses a place past 127, and RANDSTR a length past 134217728** (live-verified): `Bit index 3000000000 out
  of range; Should be between 0 and 127` and `Invalid parameter value: 3000000000. Reason: length must be at most
  134217728`. GETBIT answered 0, and RANDSTR tried to build the text.
- **GET refuses an array index written out below 0 or past 2,147,483,647 while the statement compiles**
  (live-verified): `GET([1,2,3], -1)` and `[1,2,3][3000000000]` are `Invalid extraction path '-1': array index -1 is
  off limits; must be between 0 and 2,147,483,647.`, over a table with no row too. A computed index naming no element
  still answers NULL.
- **ARRAY_INSERT refuses an array past 2,147,483,647 elements** (live-verified): `Result array of (ARRAY_INSERT) is
  too large`, where a position near the limit exhausted the heap.
- **BOOLAND, BOOLOR, BOOLXOR and BOOLNOT read a FLOAT operand through a FIXED[SB8](18,0) slot** (live-verified): NaN,
  an infinity or a value from 10^18 on is `Number out of representable range: type FIXED[SB8](18,0){not null},
  value nan`, a nullable column `{nullable}`; each was TRUE.
- **The scale ROUND, TRUNC, CEIL and FLOOR round an exact number to is read through a FIXED[SB2](4,0) slot**
  (live-verified): a whole number outside -32768..32767 is `Numeric value '32768' is out of range`, and a FLOAT
  scale that is NaN, infinite or rounds to 10000 or more is refused in the slot's words. Such a scale was cut to
  its low 32 bits.
- **A Python handler's VECTOR answer that is a tuple or a text is refused** (live-verified): `Vectors returned from
  Python must be lists or array-like in function F with handler h`, only a list or what answers `tolist()` (a
  memoryview, an `array.array`) being a vector. A tuple was taken.
- **DROP COLUMN of a column a conditional masking policy reads is refused beside the column it masks too**
  (live-verified): `ALTER TABLE m DROP COLUMN ssn, region` keeps `region` while the policy on `ssn` reads it through
  USING, as dropping `region` alone does. The masked column alone may still go.
- **A MAP keyed by a NUMBER with a scale is refused** (live-verified): `MAP(NUMBER(5,2), VARCHAR)` is `Unsupported data
  type 'MAP with key type NUMBER(5,2)'.`, a MAP keying by text or by a whole NUMBER only. It was taken, its keys
  rounded to the scale. A DECFLOAT key is refused the same way, `MAP with key type DECFLOAT(38)`.
- **A structured ARRAY, OBJECT or MAP of DECFLOAT takes only text and DECFLOAT members** (live-verified):
  `[1.5]::ARRAY(DECFLOAT)`, a FLOAT or JSON number member and `TRUE` are `Typed object schema mismatch in conversion`,
  and so is an ARRAY(NUMBER) written to an ARRAY(DECFLOAT) column, while `['1.5']` and `[DECFLOAT '1.5']` hold the
  DECFLOAT 1.5. A number member was converted.
- **A grouped or partitioned MEDIAN or PERCENTILE_CONT of a DECFLOAT that has to interpolate is refused** (live-verified):
  a group whose place falls between two values - MEDIAN of two values, even two equal ones - is `DecFloat not
  supported`, where a group of one value, MEDIAN of three, PERCENTILE_CONT(0) and (1), a group of NULLs and every call
  over the whole input or a window over () are answered.
- **A grouped query's subquery that aggregates an outer column over a VALUES list or FLATTEN is refused as an
  unsupported subquery** (live-verified): a VALUES list carries its alias's columns or COLUMN1 onwards and FLATTEN
  its SEQ … THIS, so `(SELECT MAX(v) FROM (VALUES (5), (7)) x)` reads the outer `v`. Both were read as carrying every
  name, and the subquery answered with the grouped query's own aggregate.
- **A correlated scalar subquery over a FROM that calls the scalar overload of a name with an aggregate one beside it is
  refused as an unsupported subquery** (live-verified): over `mix(x INT)` and an aggregate `mix(x VARCHAR)`,
  `(SELECT mix(t2.v) FROM t t2 WHERE t2.v = t.v)` computes a scalar for each row and promises no one row. It was
  judged as the aggregate and answered.
- **A routine's EXTERNAL_ACCESS_INTEGRATIONS names external access integrations only**: an API, catalog,
  notification or security integration there is `Integration 'X' is not an external access integration.`, where any
  integration was taken (documented).
- **A SQL procedure whose block value is a query in parentheses that does not parse is refused as it is created**
  (live-verified): `LET c := (SELECT 1 FROM x y z)`, `c := 1 + (SELECT …)` and `RETURN (SELECT …)` are refused at
  the query's SELECT and then at the query's own first fault, as the same block is refused when it runs; the
  procedure was created and failed at each CALL.
- **A correlated subquery over a derived relation of a shape of its own is refused before any row** (live-verified):
  one that computes over (`id + 0`, a cast, a call), groups, unites or joins its table, or that stands beside another
  relation in the FROM, as a pass-through relation already was - unless the column it reads is a constant, the
  statistics of what the column computes settle the correlation (`-id` never meets the inner ids), or the relation is
  pruned (its own `FALSE`, a condition its statistics rule out, its own constant `WHERE 1 = 0` over a derived relation
  of its own, or the statement's constant `WHERE 1 = 0` over one that does not group). It answered no rows.
- **`LATERAL TABLE( … )` over a user-defined table function** is a syntax error at `TABLE`, as it is over a built-in
  one (live-verified); a handler function's call was taken.
- **A JavaScript table function called with an OVER clause and joined with an ON** is refused as any lateral table
  function with a join predicate is (`Unsupported feature 'lateral table function called with OUTER JOIN syntax or a
  join predicate (ON clause)'.`), where it was refused as a window function.
- **A Java procedure whose handler is no static method is made an instance of as it is created**, as a Java
  function's is (live-verified): a class whose constructor raises is refused with the handler's report
  (`java.lang.ArithmeticException: / by zero` at `function_handler_0//H.<init>(InlineCode.java:1)`), and one with no
  public zero-argument constructor as a function's is; the procedure was created and failed at each CALL.
- **A dotted name over a block's variable is refused** (live-verified): `RETURN x.a` over a variable a LET gives a
  value, or a DECLARE gives a VARIANT or an OBJECT, is `invalid identifier 'X'` at the variable, as over a DECLAREd
  number already; it answered NULL.
- **SHOW, DESCRIBE and GET_DDL answer a schema's object only to a role that uses its database and schema**
  (documented: a role needs USAGE on the database and the schema to reach an object in it): a role holding SELECT on
  a table but no USAGE on its containers no longer finds it listed by SHOW … IN ACCOUNT or answered by DESCRIBE and
  GET_DDL, as SELECT and INFORMATION_SCHEMA already refused it.
- **DESCRIBE SEQUENCE, DESCRIBE TASK and DESCRIBE STREAM refuse an object the role does not see** as a missing one,
  as GET_DDL does: they answered the sequence and the task in full, and the stream with no row.
- **OBJECT_INSERT over a structured OBJECT keeps to its declared fields** (live-verified): with the update flag, a
  field the type does not declare is `Function OBJECT_INSERT: expected structured object to contain field c but it
  did not.`, a NULL value is `Cannot set NULL value for structured object field` and a value the field's type does
  not read is that cast's refusal (`Numeric value 'zz' is not recognized`); the field was added, removed and stored
  as text.
- **A path, a subscript or a GET to a field a structured OBJECT does not declare is refused as the statement
  compiles** (live-verified): `Function GET: expected structured object to contain field nosuch but it did not.` over
  an empty table, a NULL object, a filter that keeps no row and in a CREATE VIEW alike, where only a non-NULL row
  reaching it was refused and the view was created. GET over a structured OBJECT takes its key as a string literal
  alone (`Argument number 1 for function 'GET' needs to be a string literal.`); a computed key read the field.
- **A VECTOR is refused by the statistics, the boolean aggregates and the percentiles' window forms**
  (live-verified): CORR, COVAR_POP, COVAR_SAMP and the REGR_ family in the DOUBLE conversion's words (`invalid type
  [TO_DOUBLE(…)] for parameter 'TO_DOUBLE'`), BOOLAND_AGG, BOOLOR_AGG and BOOLXOR_AGG in TO_BOOLEAN's, and
  PERCENTILE_CONT and PERCENTILE_DISC with OVER as without it; they answered NULL, 0.0, FALSE or a vector, or failed
  with a number reader's message. REGR_COUNT still counts the rows.
- **A MAP is read by a key of its own key type alone, judged as the statement compiles** (live-verified): over a
  NUMBER-keyed map a text, a FLOAT, a VARIANT, a BOOLEAN, NULL and a number with a fraction are `Function GET cannot be
  used with arguments of types MAP(NUMBER(38,0), VARCHAR(16777216)) and VARCHAR(1)`, by a subscript, GET, a path and
  MAP_CONTAINS_KEY (which names the key first) alike, and a text-keyed map refuses a number the same way; they
  answered NULL or read the member the text named.
- **A FLOAT amount of DATEADD, TIMEADD, TIMESTAMPADD and ADD_MONTHS that rounds to a billion, or to 10^18 for a
  minute or finer, is refused in the fixed-point cast's words** (live-verified): `Number out of representable range:
  type FIXED[SB4](9,0){not null}, value 1.5e+09`, the amount as C's `%g` prints it and the argument's nullability
  spelled in; it was shifted.
- **TIME_SLICE refuses a slice length that is no whole number of one at least** (live-verified): a FLOAT, a VARIANT
  and a fixed-point number with a scale by their type as the statement compiles (`invalid type [FLOAT] for parameter
  'slice-length'`), a length written out that is not positive by its value, read as a 32-bit integer (`invalid value
  [-1,294,967,296] …` for 3000000000), and a row's as the row is read (`Invalid parameter value: 0. Reason:
  non-positive slice length`); they sliced or divided by zero.
- **REGR_SXY, REGR_R2, REGR_VALX and REGR_VALY refuse a VECTOR** (live-verified): REGR_SXY and REGR_R2 in the
  product's words, `Invalid argument types for function '*': (VECTOR(FLOAT, 2), NUMBER(38,0))`, REGR_VALX and
  REGR_VALY in the DOUBLE conversion's where they return it; they answered, or failed with a number reader's message.
- **SYSTEM$ABORT_TRANSACTION of an autocommit statement's own transaction answers `Could not abort txn: <id>`**
  (live-verified), and the statement runs on; it was aborted, and the statement failed with an internal message.
- **ALTER TABLE … SET and UNSET refuse each parameter in the account's class for it** (live-verified, every parameter
  the account lists): LOG_LEVEL, TRACE_LEVEL, ROW_TIMESTAMP_DEFAULT and the other task, alert, logging and pipe
  parameters are `cannot set parameter 'LOG_LEVEL' at 'TABLE'`, where they were refused for want of access or set;
  BASE_LOCATION is `Unsupported feature 'BASE_LOCATION'.`, and a name no parameter has is an invalid property before
  the table is looked for; an Iceberg table's parameters over a plain table — CATALOG, EXTERNAL_VOLUME,
  TARGET_FILE_SIZE, STORAGE_SERIALIZATION_POLICY, PURGE_ON_DROP_TABLE, REPLACE_INVALID_CHARACTERS,
  VENDED_CREDENTIAL_STORAGE_REGION and a named CATALOG_SYNC — are refused in CREATE's words, where they were set;
  CREATE TABLE refuses VENDED_CREDENTIAL_STORAGE_REGION too.
- **ALTER ALERT … SET refuses a parameter an alert takes no value of** (live-verified): a few at the alert's level
  (`cannot set parameter 'NETWORK_POLICY' at 'ALERT'`), any other for want of access (`Insufficient privileges to
  operate on alert 'A'.`), and a name no parameter has as an invalid property.
- **A table function called by a name of more than three parts is a syntax error** (live-verified):
  `TABLE(a.b.c.flatten(…))` and `LATERAL a.b.c.flatten(…)` are refused at the dot that opens the fourth part, then at
  the first token after the call that closes no bracket (after a join's alias, its ON as well); the built-in ran.
- **COUNT(DISTINCT a, b) over a window that orders its rows is refused at its OVER** (live-verified):
  `too many arguments for function [Window function COUNT(DISTINCT ...)] expected 1, got 2`; it counted.

### Added

- **DYNAMIC_TABLE_REFRESH_HISTORY's `INPUTS_WITH_CHANGED_DATA` lists the inputs whose data changed since the refresh
  before, each with its `statistics`**: the micro-partitions added and removed and the rows they hold, a table being
  one partition while it holds rows.
- **The dynamic table refresh history survives a restart of a persistent engine**, with where each dynamic table's
  rows lie; the function reads the past day when no `DATA_TIMESTAMP` bound is given and lists no refresh older than
  seven days, as documented.
- **`r := (SELECT … ->> …)` assigns a flow chain's last answer to a RESULTSET**, as LET and DECLARE … DEFAULT do.
  Before, the assignment was a syntax error at the second SELECT.
- **Streams on views that join NATURAL or join a FROM-clause subquery**: CREATE STREAM on such a view is taken and
  turns the view's change tracking on, and the stream reads the view's changes as a join view's stream does. Before,
  both were refused with the engine's own sentence about the shapes change tracking supports.
- **Virtual columns**: `CREATE TABLE t (a INT, b INT AS (a + 1))`, also written `b INT GENERATED ALWAYS AS (a + 1)
  VIRTUAL`, and `ALTER TABLE t ADD COLUMN c INT AS (a * 10)`. The value is computed from the row's other columns
  whenever the row is written (INSERT, UPDATE, MERGE, a CTAS's typed column list, COPY, and the rows already there
  when the column is added), so a query reads what the account computes. A positional INSERT and a CTAS's query fill
  the other columns in order. DESCRIBE TABLE reports the column as VIRTUAL with its expression, SHOW COLUMNS as
  VIRTUAL_COLUMN, and GET_DDL writes `B NUMBER(38,0) AS (A + 1)`. A virtual column reads the virtual columns defined
  before it.
- **CALL with an anonymous procedure**: `WITH name AS PROCEDURE (…) RETURNS … LANGUAGE … AS '…' CALL name(…)` defines
  a procedure for its CALL alone, in any language a procedure takes, at the top level or inside a block (`CALL … INTO
  :v`). It takes no CREATE PROCEDURE privilege, runs with the caller's rights, and no schema keeps it. A Python one
  without the Snowpark package is refused at the CALL as a named one is.
- **AUTO reads the Internet (RFC) timestamp and time forms**: `Thu, 21 Dec 2000 16:01:07 +0200` with a fraction, an
  `HH12 ... AM`/`PM` clock or no zone, `Mon Jul 08 18:09:51 +0000 2013`, and the times `04:01 AM`, `07:57:01.123456789
  PM` and `20:57:01.123456789+07:00` (Snowflake's AUTO detection table). Each was refused, and its TRY_ form was NULL.
- **A view's and a materialized view's column list takes masking and projection policies, tags and comments**
  (live-verified): `CREATE VIEW v (id, secret WITH MASKING POLICY mp USING (secret, region), region) AS …`, MASKING
  POLICY without its WITH, `WITH PROJECTION POLICY pp`, `WITH TAG (t = 'x')` and a COMMENT after them. The view's
  readers meet the policies, and DESCRIBE VIEW, GET_DDL, in the WITH forms, and POLICY_REFERENCES report them. A
  column holding both policies, or a clause twice or out of that order, is refused at the list's first column, `syntax
  error line 1 at position 29 unexpected 'id'.`, and CREATE OR ALTER VIEW refuses them as `CREATE OR ALTER does not
  support setting policies or tags.`
- **GRANT OWNERSHIP ON ROLE, USER and DATABASE ROLE** (documented): the principal passes to another role, which then
  decides its grants. Ownership never passes to a user, REVOKE OWNERSHIP of a principal is refused as `SQL execution
  error: OWNERSHIP can only be transferred.`, and REVOKE CURRENT GRANTS takes back the roles granted to a role that
  moves, where these were syntax errors.
- **MFA_POLICY, PAT_POLICY, WORKLOAD_IDENTITY_POLICY and CLIENT_POLICY of an authentication policy** (live-verified):
  each takes its list of properties, `MFA_POLICY = (ALLOWED_METHODS = ('PASSKEY', 'TOTP'),
  ENFORCE_MFA_ON_EXTERNAL_AUTHENTICATION = 'ALL')`, at CREATE, CREATE OR ALTER and ALTER … SET, and DESCRIBE and
  GET_DDL answer it as live does. An ALTER … SET of MFA_POLICY or CLIENT_POLICY replaces the setting, one of
  PAT_POLICY or WORKLOAD_IDENTITY_POLICY merges into it, and CREATE OR ALTER resets what it leaves out. Each property
  is judged with live's refusals: ALLOWED_METHODS is upper-cased, deduplicated, sorted and never empty, the PAT expiry
  days keep their bounds, blocked roles must exist, and the workload providers, AWS accounts and partitions, Azure and
  OIDC issuers and a driver's three-part MINIMUM_VERSION are checked; these settings were refused as `invalid property
  'MFA_POLICY' for 'AUTHENTICATION_POLICY'`.
- **INFORMATION_SCHEMA.DYNAMIC_TABLE_REFRESH_HISTORY** (live-verified): one row per refresh of a dynamic table, by
  table, the last name first, and each table's latest refresh first, the order a RESULT_LIMIT keeps the first rows of,
  in live's 23 columns: the refresh's trigger (CREATION, MANUAL or SCHEDULED), its action (INCREMENTAL, FULL or
  NO_DATA), state, statistics, the inputs with changed data, the last completed dependency and the target lag, read by
  NAME, NAME_PREFIX, RESULT_LIMIT, ERROR_ONLY and the data timestamp range. A refresh stays listed under the name its
  table held when it ran, past the table's replacement, DROP, UNDROP and RENAME, and a table made again by that name
  reads it too. Creating a dynamic table refreshes the dynamic tables it reads first, as live does.
- **SHOW REGIONS IN DATA EXCHANGE and SHOW ORGANIZATION ACCOUNTS LIKE** (live-verified): no data exchange is
  modelled, so `SHOW REGIONS IN DATA EXCHANGE x` is refused as `Data exchange 'X' does not exist or not authorized.`
  with the privilege the role would need, a quoted name keeping its quotes, and the form without a name as `Unsupported
  feature 'SHOW REGIONS IN DATA EXCHANGE'; use 'SHOW REGIONS IN DATA EXCHANGE <exchange_name>' instead.`; both were a
  syntax error at EXCHANGE. SHOW ORGANIZATION ACCOUNTS reads a LIKE, and a STARTS WITH after it is a syntax error at
  STARTS.
- **Scoring profiles of a Cortex Search Service** (documented): `ALTER CORTEX SEARCH SERVICE s ADD SCORING PROFILE
  [IF NOT EXISTS] p '<json>'` (or a `$$…$$` body) and `DROP SCORING PROFILE [IF EXISTS] p`, kept with the service and
  its snapshot. Over a service that is not there both are refused as any other action is, and the DROP with IF
  EXISTS, as an ALTER … IF EXISTS, answers the skipped-statement status (live-verified).
- **Caller grants** (documented): `GRANT CALLER <privileges> ON <kind> <name> TO ROLE | DATABASE ROLE <owner>`,
  `GRANT INHERITED CALLER <privileges> ON ALL <kinds> IN ACCOUNT | DATABASE d | SCHEMA s`, `GRANT ALL [INHERITED]
  CALLER PRIVILEGES`, the matching `REVOKE CALLER` forms, `SHOW CALLER GRANTS ON <kind> <name> | ON ACCOUNT | TO
  ROLE | TO DATABASE ROLE` (its ten columns live-verified) and the account privilege `MANAGE CALLER GRANTS` that
  granting one takes. A procedure that runs with restricted caller's rights uses a privilege of its caller's on an
  object, USAGE on its database and schema included, where a caller grant its owner holds names it and the caller
  holds it; it used none. Caller grants are kept in snapshots. A Snowflake Native App names nothing.
- **External access integrations** (documented): `CREATE [OR REPLACE] EXTERNAL ACCESS INTEGRATION` with
  `ALLOWED_NETWORK_RULES` (egress network rules that exist; required), `ALLOWED_API_AUTHENTICATION_INTEGRATIONS`
  (API_AUTHENTICATION security integrations, or NONE), `ALLOWED_AUTHENTICATION_SECRETS` (secrets that exist, ALL or
  NONE), `ENABLED` (required) and `COMMENT`; ALTER sets each of them and unsets all but ENABLED; DESCRIBE lists them
  by full name; SHOW INTEGRATIONS calls the kind `EXTERNAL_ACCESS` in category `SECURITY`. Frostlake never calls out.
- **CREATE OR ALTER TASK** (live-verified): a task that is not there is created; one that is becomes what the
  statement writes — a schedule, comment, predecessor, condition or parameter left out is unset — and keeps its
  state, its runs and its tags (`Statement executed successfully.`). A warehouse that does not exist is refused and
  the task left as it was; a running root task is refused (`Unable to update graph with root task … since that root
  task is not suspended.`); IF NOT EXISTS and tags are no part of the statement.
- **TASK_HISTORY, COMPLETE_TASK_GRAPHS and CURRENT_TASK_GRAPHS tell a run's query and scheduler** (live-verified): a
  run that ran tells `QUERY_ID` (the query QUERY_HISTORY lists for the task's statement), `ERROR_CODE` /
  `FIRST_ERROR_CODE` (`100051` for a division by zero), `QUERY_HASH` and `QUERY_PARAMETERIZED_HASH` (QUERY_HISTORY's,
  versions 2 and 1) and `SCHEDULED_BY_USER` (who ran EXECUTE TASK); a scheduled run tells who resumed the task and
  `NEXT_SCHEDULED_TIME`, the fire after its own. They were NULL.
- **INFORMATION_SCHEMA.SERVERLESS_TASK_HISTORY**: the account's four columns, and no row, since the engine bills no
  credits.
- **A bare table function call after LATERAL takes an OVER clause**, as one inside `TABLE( … )` does (live-verified):
  `FROM ev, LATERAL f(ev.v) OVER (PARTITION BY ev.g) f`; FLATTEN takes the clause and reads no partitions.
- **A session of a user starts from the user's DEFAULT_SECONDARY_ROLES** when it sets them: `('ALL')` puts every role
  granted to the user in use, `()` none (documented).

- **Storage integrations** (live-verified for S3, documented otherwise): `CREATE [OR REPLACE] STORAGE INTEGRATION …
  TYPE = EXTERNAL_STAGE` (or `POSTGRES_EXTERNAL_STORAGE`) with `STORAGE_PROVIDER` `'S3'`, `'S3CHINA'` or `'S3GOV'`
  and `STORAGE_AWS_ROLE_ARN`, `'GCS'`, or `'AZURE'` and `AZURE_TENANT_ID`, `STORAGE_ALLOWED_LOCATIONS` and `ENABLED`
  (required), `STORAGE_BLOCKED_LOCATIONS` and `COMMENT`; SHOW INTEGRATIONS tells `EXTERNAL_STAGE` in category
  `STORAGE`; DESCRIBE lists the provider, the locations and the identity the account reaches the cloud as; ALTER sets
  all but the type and the provider and unsets ENABLED, STORAGE_BLOCKED_LOCATIONS and COMMENT. A stage that names one
  is refused for a location outside its allowed ones or inside a blocked one (`Location 's3://…' is not allowed by
  integration SI. Please use DESC INTEGRATION to check out allowed and blocked locations.`). It was a syntax error.
- **Snowflake OAuth, External OAuth, SAML2 and SCIM security integrations** (documented; the listing of a custom OAuth
  client live-verified): each takes the properties its page documents and requires its own, its variant-choosing
  values judged; SHOW tells `OAUTH - CUSTOM`, `EXTERNAL_OAUTH - OKTA`, `SAML2` and `SCIM - OKTA`, and DESCRIBE lists
  ENABLED and what the integration was written with. They were `Unsupported feature 'SECURITY INTEGRATION of type
  OAUTH'.`.
- **GRANT and REVOKE ON DYNAMIC TABLE and ON EVENT TABLE** (documented), recorded where SHOW GRANTS ON reads them;
  GRANT ALL on a dynamic table is MONITOR, OPERATE and SELECT. They were syntax errors.
- **A COPY's transformation query takes a staged-file query's arguments** (live-verified): `COPY INTO t FROM (SELECT
  $1 FROM @st (FILE_FORMAT => ff))`, judged as the query's are (a format written as a list is no constant); the
  format and the pattern they give read the files where the COPY writes no FILE_FORMAT or PATTERN of its own. It was
  a syntax error.
- **A block's variable named by DEFAULT, CONSTRAINT, JOIN, INNER, CROSS, CAST, CASE or WHEN in a DECLARE section, and
  an assignment to one** (live-verified): `DECLARE join INT DEFAULT 4`, `DECLARE join CURSOR FOR …`, `DECLARE join
  EXCEPTION (…)` and `inner := inner + 1` are taken, where they were syntax errors; a statement that begins with one
  of the words elsewhere is refused where it was.
- **FETCH … INTO a variable named by CASE, WHEN, CAST, DEFAULT, CONSTRAINT, JOIN, INNER or CROSS** (live-verified):
  `FETCH c INTO join` fills the variable, as `SELECT … INTO :join` does. It was a syntax error.
- **ALTER DYNAMIC TABLE names its table by IDENTIFIER()** (live-verified), as ALTER TABLE does, for every action:
  `ALTER DYNAMIC TABLE IDENTIFIER('dt') RENAME TO dt7`, a SUSPEND, a RESUME, a SET, a qualified name in the string and
  IF EXISTS alike. It was a syntax error.

### Fixed

- **DROP ROLE takes back what the role granted**: the privileges it granted, those on the objects it owned included,
  and the database roles it granted are revoked with it, and a database role it owned passes to the dropping role with
  its other objects, where they stayed granted by, and owned by, a role that no longer existed.
- **A view stream read beside the session's change-tracked temporary table** that hides one of the view's tables lists
  the temporary table's changes since the stream's offset, as the view reads that table in the session, where it
  listed the hidden table's.
- **Over the HTTP driver, `Connection.close()` cancels a statement another thread runs on the connection**: the
  statement ends with `SQL execution canceled` and keeps none of what it had yet to do, and the close returns at once,
  where it waited for the statement to end and kept what it did.
- **SHOW GRANTS names a database role that owns an object as its other grants name it**: SHOW GRANTS ON the object
  lists the owner as `DATABASE_ROLE` and its bare name, where it read `ROLE` and the qualified name, and SHOW GRANTS
  TO DATABASE ROLE lists the OWNERSHIP of what the role owns, which it left out.
- **COPY INTO <table> with RETURN_FAILED_ONLY = TRUE answers only the files that failed to load**, the statement's
  option over the one the stage keeps, where it listed every file; a load in which no file failed answers no row.
- **A Python table function's or aggregate's module serves every partition and every group its call reaches**, as a
  scalar function's call serves every row, where its body ran again for each; each call in a statement keeps a module
  of its own.
- **A VALUES row whose number the list's type cannot hold is refused in the conversion's words**: `VALUES
  (0.3333333333333333333333333333333333333), (11)` is refused as `Numeric value '11' is out of range`, in a query and
  in an INSERT, where the range sentence named the list's type.
- **A VALUES item that converts to VARIANT is echoed as the cast it is**: `INSERT INTO t VALUES
  (TO_VARIANT(0.1::FLOAT))` is refused as `Invalid expression [CAST(0.1 AS VARIANT)] in VALUES clause`, the number
  converted to FLOAT folded, where the call was echoed as written.
- **GRANT and REVOKE take a join, aggregation, projection, authentication or packages policy and a contact**, by the
  exact name written: APPLY on a policy, APPLY and MODIFY on a contact, with SHOW GRANTS ON and GRANT OWNERSHIP, where
  each kind was a syntax error. Another privilege is refused for the kind, as `Invalid object type 'POLICY' for
  privilege 'SELECT'.`
- **SHOW GRANTS TO no longer lists the privileges of a dropped policy or contact**, and a policy or contact created
  under a dropped one's name does not take them, where they stayed listed and passed to the new object; one that
  replaces another keeps them.
- **A view read `AT(STREAM => '<name>')` answers the view as the stream's offset leaves it**, where it was refused as
  an unsupported feature; a table read so is still refused.
- **A materialized view's column policies and tags are reported**: GET_DDL prints them, and TAG_REFERENCES_ALL_COLUMNS
  lists its columns' tags.
- **An OVERLAY in the PLACING form inside a block's statement names the lines a top-level statement names, then the
  block's END, or the cast's AS and its bracket in a RESULTSET's query**, as the account words it, where the PLACING
  alone was named.
- **A result column's nullability in its metadata follows its rows and its item**: a VALUES column is nullable only
  where one of its rows holds NULL, a scalar subquery item by what it selects (`(SELECT 1)` is not), and JDBC's
  `isNullable` of a join's qualified column reads that relation's own column, where every VALUES column and every
  scalar subquery were nullable and a qualified column could read the other relation's.
- **`ALLOWLIST = (1)` in a packages policy is refused as `invalid value [1] for parameter 'ALLOWLIST'`**, as the
  account words it, where it was a syntax error at the number.
- **A block expression's refusal is placed and echoed as the account does it**: a variable ahead of a subquery is
  written out as a bind (`RETURN x + (SELECT ABS(1, 2))` is refused at position 36), a miscounted call in an IF
  condition counts the variables so too (`IF (x + ABS(1, 2) > 0)` at 21), and a NUMBER variable holding NULL is echoed
  as `TO_NUMBER(null)` rather than as a statement's own `SYSTEM$NULL_TO_FIXED(null)`.
- **A dynamic table's refresh copies only the rows left in the micro-partitions it deletes from**: a refresh that
  deletes every row an earlier refresh wrote copies none and adds no partition, where it copied every row the table
  kept; DYNAMIC_TABLE_REFRESH_HISTORY's `STATISTICS` counts the partitions added and removed so.
- **`DROP AGGREGATE FUNCTION f(NUMBER)` is refused at `AGGREGATE` and then at `FUNCTION`**, as the account words it,
  where it was refused at `FUNCTION` and at the name.
- **A Python procedure whose handler takes fewer arguments than the call passes is handed the first ones**: `def
  run(s)` answers for a procedure of one argument, where the call raised a TypeError.
- **Two signs in a row are refused before any name is looked up**: a sort or grouping key such as `ORDER BY - -1` or
  `GROUP BY ++1` is refused as `invalid function '-'` (the outer sign), over a missing table too, where it was refused
  as an out-of-range position.
- **A set operation's VARIANT arm that wraps a FROM-less derived select's literal is folded**: `SELECT 'x' v UNION ALL
  SELECT TO_VARIANT(z) FROM (SELECT 12 z)` answers `12 ;; x`, as the wrap written in place does, where it was held to
  the text column's length.
- **An engine whose python.venv names another venv than the one the JVM's GraalPy interpreters already import from is
  refused at its first Python call**, in words that say why, instead of importing the other venv's packages.
- **SYSTEM$STREAM_HAS_DATA answers from the versions the stream's tables took since its offset**, not from the rows a
  read would list: an APPEND_ONLY stream answers TRUE after a DELETE or an UPDATE it does not list, as the function's
  documented version diff does.
- **A join-view stream read inside a transaction lists the changes committed by the transaction's start**: the
  transaction's own writes and later commits wait for a read after COMMIT, where the read listed the transaction's own
  UPDATE as a lone DELETE.
- **A view stream re-bound while it kept its changes is listed on its new tables after a restore or a clone**, where
  it was listed stale over inaccessible tables until it was read again.
- **A stored element converts inside a grouping key, a folded IFF and a correlated subquery**: `TO_VARCHAR(a[0]::DATE,
  'YYYYMMDD') ... GROUP BY a[0]::DATE`, a derived `IFF(TRUE, a, NULL) AS a` and `(SELECT TO_VARCHAR(va.a[0]::DATE,
  'YYYYMMDD')) FROM va` read the stored element as a plain select does, where each was refused with `Failed to cast
  variant value 1 to DATE`.
- **DROP ROLE revokes the role from every role and user it was granted to**, and the roles it granted as their
  grantor: SHOW GRANTS and CURRENT_AVAILABLE_ROLES no longer name it. **Dropping the session's own primary role is
  refused** (`Cannot drop role R as it is the current primary role.`), IF EXISTS or not.
- **The variance family over exact NUMBERs is exact**: VARIANCE, VAR_POP and the STDDEVs over NUMBER arguments are
  computed from exact sums and presented at their declared scale, over a group and on every window frame. Over
  NUMBER(12,2) values near 10^8, a moving frame's VARIANCE reads 0.0000500000 where it read 0.0000499999.
- **SHOW TABLES HISTORY lists a table replaced or dropped with a retention of 0 days**, as dropped and with its row
  count, while UNDROP refuses it as purged. Before, the version left the listing at once.
- **A renamed internal stage keeps its files**, a temporary stage's included, and **a session's temporary table keeps
  its table stage to itself**: another session's temporary table of the same name lists none of its files.
- **A task's interval schedule counts elapsed time**: on a night the host's clock falls back, a 60-minute task's
  NEXT_SCHEDULED_TIME reads 3600 seconds after its SCHEDULED_TIME, as on the account. Before, it read 7200 seconds in
  the repeated hour.
- **TASK_HISTORY lists a run EXECUTING until it ends**, a run waiting for another session's held table among them.
  Before, a run was listed only once it had ended.
- **A table function's bracketed-query argument inside a query written in brackets is refused at that query's
  SELECT**, as the account refuses it: `SELECT (SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => (SELECT …) + 1)))`
  names the inner SELECT and then the `(` after COUNT, and under an IN the FROM after the first `)`. Before, the lines
  named the `+`, as for a call in the statement's own FROM.
- **GENERATOR rows past 2147483647 answer more queries**: constant aggregates over the rows alone count them (`SELECT
  COUNT(*), MAX(1) FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))` is 3000000000 and 1), a WHERE reading each row is
  tested until the select's own LIMIT is met, and a WHERE over a derived table's literal columns is decided before any
  row is read. Before, each was refused as more rows than the engine holds.
- **EXPLAIN plans a procedure called by its SYSTEM$REFERENCE token as the account does**: `EXPLAIN SELECT
  IDENTIFIER($tok)(4)` prints the procedure over its parameters' names, `PR_ARG(X)`, over an empty ValuesClause, and a
  table procedure's call is planned too. Before, the call was printed as written over a Generator, and a table
  procedure's call was refused as an unknown user-defined function.
- **A flow chain filling a RESULTSET fails as a request's chain does**: `LET r RESULTSET := (SELECT 1 AS a ->> SELECT
  b FROM $1)` is refused at the chain's arrow, wrapping the chain's own STATEMENT_ERROR at the failing stage, whose
  places count from its first token. Before, it was refused at the chain's first word, the stage's places counted from
  the chain.
- **A request of several statements runs up to a statement the account reads whole**: `SELECT 1; DESCRIBE TABLE t1 (a)
  x = @s!=x` runs the SELECT and is then refused by the multi-statement sentence naming the DESCRIBE, `invalid
  parameter 'x'` inside. Before, the whole request was refused with a syntax error at the `!=`.
- **Merged-view sums print as the account plans them**: an AVG's count casts the column beside a constant of eighteen
  digits, a SUM(DISTINCT) of a column `x + c` reads as the column's distinct sum moved by the constant times its
  distinct count, and EXPLAIN prints a number beside a FLOAT column as a FLOAT literal (`FZF.F + 1.0`).
- **SHOW FUNCTIONS leaves out the SYSTEM$ functions the account does not list**: SYSTEM$TYPEOF,
  SYSTEM$STREAM_HAS_DATA, SYSTEM$STREAM_BACKLOG, SYSTEM$STREAM_GET_TABLE_TIMESTAMP, SYSTEM$AUTO_REFRESH_STATUS,
  SYSTEM$ALLOWLIST, SYSTEM$WHITELIST, SYSTEM$GET_SNOWFLAKE_PLATFORM_INFO, SYSTEM$GENERATE_SCIM_ACCESS_TOKEN and
  SYSTEM$VERIFY_CATALOG_INTEGRATION still run but no longer appear in SHOW FUNCTIONS or SHOW BUILTIN FUNCTIONS, as on
  the account, whose SYSTEM$ listing names 172 others.
- **A LET's value run into the next statement in a WHILE's or a LOOP's body, a FOR's body or a nested block is refused
  with the account's lines**: up to four, e.g. 'CREATE', 'TABLE', '(' and 'INT' for `LET a := 1 CREATE OR REPLACE
  TABLE u (a INT)` in a loop. Before, a loop's report held the refused word and the loop's END, and a nested block's
  one line fewer.
- **A stream read inside an explicit transaction holds the stream**: another session's consuming DML, CTAS or query
  inside its own explicit transaction waits for it and, once LOCK_TIMEOUT passes, is refused "Statement '…' has locked
  stream 'S4' in transaction … and this lock has not yet been released.". An autocommit query, SYSTEM$STREAM_HAS_DATA,
  a write of the stream's table and CREATE OR REPLACE STREAM go ahead, and SHOW LOCKS lists the held stream with the
  type STREAM. Before, no stream lock existed, so two sessions could consume the same records.
- **A user's function the role may not use is refused as the statement compiles**: over no rows, in a CASE or IFF
  branch no row takes, as an unread derived-table or CTE item, in a WHERE no row passes and in an UPDATE's SET over no
  rows, each with the refusal the call itself gives. Before, only a row that reached the call was refused.
- **A correlated IN raises a member's fault from an inner row of any key**: `ka.id IN (SELECT 10 / kb.z FROM kb WHERE
  kb.k = ka.v)` is "Division by zero" when an inner row of another key holds the zero, as the account computes the
  members over every inner row the other conditions keep. Before, the members query failing made the subquery run per
  outer row, which never read that row.
- **ARRAY_INSERT at a position far past either end builds the array without a node per hole**:
  `ARRAY_SIZE(ARRAY_INSERT([1,2], 1000000000, 9))` answers 1000000001, as the account does, where the padding filled
  the heap. A run of more than a million holes is held as a run, and its elements read where they stand.
- **A user whose DEFAULT_SECONDARY_ROLES was never written starts with the documented default, ALL**: a session
  switched to the user puts every role granted to it in use. Another granted role's privilege then answers its query,
  and CURRENT_SECONDARY_ROLES() reads `{"roles":"…","value":"ALL"}`. Before, the session's secondary roles were left
  as they were.
- **The embedder's role switch drops the former primary role as USE ROLE does**: `DatabaseEngine.setCurrentRole` no
  longer leaves the role it replaces in use beside the new one, unless the secondary roles name it.
- **A BOOLEAN converted to an exact number takes the number the conversion declares**: `TRUE::NUMBER(5,1)` is `1.0`
  typed `NUMBER(5,1)[SB4]`, `TRUE::NUMBER` and `TO_NUMBER(TRUE)` are `NUMBER(38,0)[SB16]`, and `TO_DECIMAL(TRUE, 5,
  1)` is `1.0`. A procedure that `RETURNS NUMBER(5,2)` and returns `TRUE` answers `1.00`, as a scripting declaration
  does, and the TRY_CAST and TRY_TO_* refusals name the declared type (`BOOLEAN and NUMBER(38,0)`). Every such
  conversion was the unscaled 1 or 0 typed `NUMBER(2,0)`.
- **A prepared or callable statement binds a numbered `:n` as its n-th parameter**: `CALL p(:1)` over `setInt(1, 7)`
  passes 7 and `SELECT :1 + 1` answers 8, as the n-th `?` would. The numbered bind was left unbound, and the statement
  failed with an internal error.
- **A numbered bind with no value set is refused at its colon**: `Bind variable :2 not set.`, placed at the bind,
  where the statement failed with an internal error.
- **A table property given NULL, a decimal or a parenthesized list of constants is judged by its name**, where each
  was a syntax error at the value. A name that is no property is refused as one (`invalid property 'x' for 'TABLE'`),
  and a decimal is refused as written (`invalid value [1.5] for parameter …`). NULL is refused a switch or a count
  (`invalid value 'null' for property 'CHANGE_TRACKING'`) and leaves COMMENT unset. A list is taken by a text
  parameter (DEFAULT_DDL_COLLATION) and refused a count parameter (`invalid value [TOK_CONSTANT_LIST] for parameter
  …`), a switch property (`Invalid value specified for property …`) and COMMENT.
- **A table comment written as a word is the word as a name reads it**: `COMMENT = abc` comments the table `ABC`,
  where the comment was left unset.
- **A TRUNCATE in a transaction forgets COPY's load history only as it empties the rows**: at once for its own
  transaction, so a COPY after it there loads the files again, for everyone at the commit, and not at all after a
  ROLLBACK, which keeps the rows and leaves the files skipped. The history was forgotten at once, so a rolled-back
  TRUNCATE let the next COPY load the same files a second time.
- **A commit whose writes cannot apply ends its COPY loads as a rollback does**: the files are not left marked as
  loaded, for no session to load again.
- **A ROLLUP, CUBE or GROUPING SETS key is reported nullable**: a NOT NULL column that some grouping set leaves out
  holds NULL on the super-group rows, and it is now nullable to a JavaScript procedure's `isColumnNullable`, to JDBC's
  `isNullable` and in a view's DESCRIBE and INFORMATION_SCHEMA, as is an item that reads it; a key every grouping set
  keeps stays NOT NULL. It was reported NOT NULL beside the NULL it held.
- **A Python table function's bytes row or cell is one BINARY value on GraalPy too**: `return b'ab'` and `yield b'ab'`
  write `6162`, as a `bytearray` does, where GraalPy split the bytes into integers that the BINARY column refused.
  Only a tuple or a list is a row's cells, so `yield range(3)` is one cell its INT column refuses, as on CPython,
  where GraalPy wrote 0.
- **A Python table function skips a row for `None`**: `return None`, `return [None]` and `return []` write no row for
  their input row, and the rows yielded or listed after a None item are ignored, in `end_partition` too (Snowflake's
  UDTF documentation). Each None item was a row of NULLs, and the rows after it were kept.
- **A statement's timeout, a driver's query timeout and cancel() now end a Python or JavaScript handler**: a Python
  function that sleeps, a JavaScript function or procedure that spins, and a Python procedure that catches what its
  queries raise all fail with the statement's refusal (`Statement reached its statement or warehouse timeout of N
  second(s) and was canceled.`, or `SQL execution canceled`, SQLSTATE 57014), on GraalPy and on CPython. They ran to
  completion, and the Python procedure's CALL succeeded.
- **A Python routine is no longer killed after a minute**: a procedure runs as long as its statement may. GraalPy
  cancelled its context (`Context execution was cancelled.`) and CPython killed its worker (`Python execution timed
  out after 60 seconds`) at a fixed 60 seconds, whatever STATEMENT_TIMEOUT_IN_SECONDS said.
- **`sys.exit()` in a Python function fails only that function**: a procedure that calls the function through
  `session.sql` catches the failure and goes on, where on GraalPy the exit ended the calling procedure too (`Context
  execution was cancelled.`). An exit with no status is worded `SystemExit`, not `SystemExit: None`.
- **A stream on a view over a view, or over a FROM-clause subquery,** and `CHANGES` over such a view now follow the
  fully expanded query: filters and projections on every level, a UNION ALL arm through a view, a view over a UNION
  ALL view, inner column lists, aliases and alias column lists, an inner view in another schema, and the row access
  and masking policies of every view on the way. Creating the stream turns change tracking on for the views below it,
  and SHOW STREAMS lists the tables at the bottom. Such a stream was refused `Table '<inner view>' does not exist`,
  and a subquery source was refused outright.
- **A stream reads an inner view as it now stands**: a replaced inner view's new query applies, and a dropped one
  fails the stream's read, its CREATE and CHANGES with `Failure during expansion of view '<view>': ... Object '<inner
  view>' does not exist or not authorized`. CREATE STREAM on a view whose table is gone is refused in the same words,
  not `Table '<table>' does not exist`.
- **A view stream's column read bare through a FROM alias** (`SELECT x.id FROM t x`) is named `ID`, as in the view;
  the stream named it `X.ID`, so selecting `id` from it failed `invalid identifier 'ID'`.
- **A view that carries both a row access policy and a masking policy masks its columns** when read; the masking
  policy was skipped whenever the view had a row access policy.
- **SHOW STREAMS lists a view stream's tables with a comma and a space between them**, as the account does; they were
  joined by a comma alone.
- **A Python function's module is imported once for each call of the function in a statement** and serves every row
  that call reaches: module-level initialisation runs once, and module counters, `functools.lru_cache` caches and
  class attributes keep their state from row to row; each call keeps its own module, and none outlives its statement.
  The module body ran again for every row.
- **Over the HTTP JDBC driver `Statement.cancel()` cancels only that statement object's own statement**: a statement
  waiting behind another statement of the same connection no longer cancels the one that is running.
- **CREATE SCHEMA with an unqualified name clones a source named in full**, from the current database or another, into
  the current database; it was refused `Source schema must be in current database when target is unqualified`.
- **A stream on a view follows its view**: renamed or moved to another schema, the view is still the stream's source,
  read and listed under its new name, and a new view under the old name is not; the stream had failed `View '…VW' does
  not exist`.
- **A COPY INTO a table takes the copy options of the stage it reads**: a named stage's COPY_OPTIONS and a table
  stage's STAGE_COPY_OPTIONS (ON_ERROR, PURGE, FORCE, SIZE_LIMIT, MATCH_BY_COLUMN_NAME, ENFORCE_LENGTH,
  TRUNCATECOLUMNS) are the defaults of the options the statement does not write; they were kept and listed but never
  applied.
- **A policy is refused DROP and CREATE OR REPLACE only while it is itself attached**: a masking, row access,
  projection, aggregation or join policy beside an attached namesake in another schema is dropped and replaced freely,
  an attachment holding the policy its full name names.
- **A database's PUBLIC schema is owned by the role that created the database**, so a role with CREATE DATABASE alone
  works in its new database's default schema; it was owned by SYSADMIN.
- **A HAVING aggregate over a subquery whose own aggregate reads only the outer group's column is refused in
  Snowflake's words** under a COUNT or a SUM as well as a MAX or a MIN, with the subquery's aggregate alone or beside
  a literal, filtered by no WHERE or by one that reads nothing of the outer row, and an AVG of whole numbers spelled
  as its SUM over its COUNT: `Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in
  [COUNT(CORRELATION(MAX(FZ.ID)))]`. Snowflake answers some of these over a table from the table's statistics,
  ignoring the query's own WHERE; Frostlake refuses them as Snowflake does where no statistics apply.
- **A grant ON ALL objects of a kind is recorded in each object's owner's name**, as a grant naming the object is, or
  in the name of the role whose grant option passes it on; it was recorded under the role that ran it, so the owner's
  REVOKE took nothing back.
- **A database's future OWNERSHIP grant no longer reaches a new object in a schema WITH MANAGED ACCESS**; the
  database's other future grants still do.
- **A subquery whose one select item is a ROW is judged by what reads it**: an EXISTS over a body that reads no
  relation and filters nothing answers TRUE; a comparison, IN, ANY and an arithmetic operator refuse it in their own
  words (`Invalid argument types for function '=': (NUMBER(38,0), ROW(NUMBER(1,0), NUMBER(1,0)))`); a scalar subquery
  is refused at its own SELECT; INSERT … SELECT judges the source's width and the ROW's type first; and a view's
  unnamed ROW is a missing column specification.
- **A session with no current schema refuses LIST and REMOVE as STAGE LS and STAGE RM**, as Snowflake names them, and
  a handler reads code 90106 and SQLSTATE 22000 for every refusal for want of a current schema.
- **DECFLOAT statistics are DECFLOATs**: CORR, COVAR_POP, COVAR_SAMP, the REGR family but REGR_COUNT (when either
  argument is a DECFLOAT), SKEW and KURTOSIS over a DECFLOAT answer DECFLOATs of 38 digits computed as Snowflake
  computes them - the sums in DECFLOAT arithmetic held to 38 digits at every step, the slope and CORR over the
  correctly rounded VAR_POP and STDDEV_POPs, SKEW and KURTOSIS with their small-sample coefficients rounded to twelve
  fractional digits - where they were FLOATs and KURTOSIS a NUMBER(38,12).
- **KURTOSIS of a FLOAT or a text is a FLOAT**, as Snowflake types it, where it was a NUMBER(38,12).
- **A VARIANT holding a double is converted to an exact number by its binary value**: TO_VARIANT(0.1::FLOAT) or
  PARSE_JSON('1e-1') written into a NUMBER(38,20) column, or read by TO_NUMBER and TO_DECIMAL, is
  0.10000000000000000555, as the FLOAT itself is, where its text was read.
- **SHOW DATABASES, SHOW SCHEMAS, SHOW WAREHOUSES, SHOW COMPUTE POOLS, SHOW ALERTS and SHOW CORTEX SEARCH SERVICES
  list only what the role sees** (it owns them or holds a privilege on them, or holds MANAGE GRANTS), and DESCRIBE
  DATABASE, WAREHOUSE, ALERT and CORTEX SEARCH SERVICE and GET_DDL of a database, a schema, a warehouse or a Streamlit
  answer one it does not see as missing.
- **A SQL function whose body is a query reads a parameter over a same-named column** (live-verified): a call over a
  column argument is refused as `Unsupported subquery type cannot be evaluated inside Function object: C_LAT2` where
  the body reads that parameter inside an aggregate, `SUM(value)` over a FLATTEN or `MAX(id)` over a table with a
  column ID, as it already was where no column shared the name; a parameter read only in a WHERE, or not at all, still
  answers. A constant argument types the call's interval: `c_max(12345)` is `NUMBER(5,0)[SB2]` and
  `c_lat2(1.25::FLOAT)` `NUMBER(38,0)[SB8]`, where the body's own column statistics were read.
- **A grouped query's select alias reads as the type of the item it names in HAVING and ORDER BY** (live-verified):
  `SELECT k, ARRAY_AGG(x) a … GROUP BY k HAVING ARRAY_SIZE(OBJECT_KEYS(OBJECT_PICK(ANY_VALUE(o), a))) = 2` picks the
  array's keys, as a later select item already did, where `a` read as one text key and every group was dropped; ORDER
  BY over the same key sorts by it, and SYSTEM$TYPEOF names the ARRAY.
- **A DROP or an ALTER of an object another role owns is refused in the account's sentence for every kind**
  (documented, in the sentence measured for the other kinds): `SQL access control error: Insufficient privileges to
  operate on <kind> '<name>'. Your primary role R must have <privilege> granted on <KIND> <name>.`, where database,
  warehouse, task, pipe, tag, stream, dynamic table, masking and row access policy changes and every DROP answered in
  this engine's own `Permission denied: User 'U' with roles […] does not have …` words. A DROP and a rename ask
  OWNERSHIP, every other ALTER MODIFY, and resuming, suspending, refreshing or pausing a task, an alert, a pipe, a
  dynamic table or a warehouse OPERATE.
- **GRANT OWNERSHIP ON DYNAMIC TABLE and ON ALERT moves the owner** (documented): SHOW DYNAMIC TABLES and SHOW ALERTS
  name the new owner, and the old one no longer drops or alters the object, where the statement succeeded and the
  owner stayed.
- **An external stage is used with USAGE** (documented): COPY INTO a table from an external stage, COPY INTO it and a
  query of its files ask USAGE on the stage, as CREATE PIPE already did, where READ or WRITE was asked; an internal
  stage keeps READ and WRITE.
- **A stream reports no update that leaves a row as its reader sees it** (live-verified): a table stream drops an
  UPDATE that sets a column to its own value, and a view stream one that changes only a masked value or a column the
  view does not project, SYSTEM$STREAM_HAS_DATA and CHANGES agreeing, where the update was reported as a DELETE and an
  INSERT.
- **A view's own column masking applies to a stream on the view and to CHANGES over it** (live-verified), set by the
  view's column list or by ALTER VIEW, as it applies where the view is read.
- **An OVERLAY in the PLACING form names live's further lines under more bracket stacks** (live-verified): under a
  CAST or a TRY_CAST, nested calls, plain parentheses and their mixes, with a FOR tail, in a second select item,
  before an alias or a `+ 1` and under a CASE's THEN, the lines after the PLACING name what live names, the PLACING's
  value, the FROM's value or a bracket's `)`, where only the PLACING was named.
- **A literal after a TIMESTAMP nested in a DESCRIBE's signature** (live-verified): `DESCRIBE TABLE t (ARRAY(TIMESTAMP
  1))` names the 1 and then the token that ends the signature's item, the `)` or the `,`, inside OBJECT, MAP, TABLE
  and nested ARRAY types too, where the 1 alone was named.
- **Quoted policy and contact names keep their case** (live-verified): a contact is created, found, altered, attached
  and dropped by its exact name, so `"cct4"` stands beside `CCT4` and `DROP CONTACT cct4` leaves `"cct4"` alone, where
  the names were folded to upper case. The in-use refusals of a masking, row access, join, aggregation or projection
  policy name the policy as written, `Policy qmp8 cannot be dropped/replaced as it is associated with one or more
  entities.`, an in-use check no longer counts a namesake of another case, a table's or a view's join policy and a
  task's contact are recorded by their exact names, and GET_DDL of a task spells its contact
  `TEST_DB.TEST_SCHEMA."cct9"`. DESCRIBE CONTACT is `Unsupported feature 'CONTACT'.`, where it was a syntax error.
- **UNDROP DYNAMIC TABLE answers `Dynamic_table D successfully restored.`** (live-verified), as its refusal names the
  kind, where it answered `Dynamic D successfully restored.`
- **SYSTEM$TYPEOF types a correlated scalar subquery that is no aggregate** (live-verified): the item's declared type
  and storage, under arithmetic, in a WHERE and over a UNION ALL, and a subquery two or three levels down whose
  aggregate select list reads the outermost query, where Frostlake refused the subquery as unsupported or typed it
  NULL. Running such a subquery as a value is still refused, as live refuses it.
- **Each stage of a `->>` chain keeps its work when a later stage fails** (live-verified): an INSERT, an UPDATE, a
  DELETE, a DDL statement and a stream a stage consumed stand, under autocommit, inside an explicit transaction and
  with AUTOCOMMIT off, where Frostlake undid the whole chain. The write-ahead log records a completed chain once,
  whole, and keeps a failed chain's earlier stages by a checkpoint.
- **TO_VARCHAR and TO_CHAR of an XML value answer its XML text** (live-verified): `<r><a>1</a></r>`, as `$1` and a
  cast print it, where the JSON form was answered. A format over an OBJECT, an ARRAY or an XML value, a NULL format
  included, is refused for its count, as over a VARIANT.
- **A RECURSIVE view's distinct UNION recurses as UNION ALL does** (live-verified): no row is dropped as a duplicate,
  so an anchor row met twice recurses twice, a join that reaches a row twice keeps both, and a step that meets its
  rows again runs until the statement times out, where Frostlake deduplicated every step.
- **RATIO_TO_REPORT is typed from the column it reads in each statement** (live-verified): after a table is replaced
  with another column type, or through a view over it, the ratio is rounded to the new declared scale and a DECFLOAT
  ratio keeps its exact digits, where the first statement's scale was kept for the engine's lifetime.
- **ARRAY_AGG, ARRAY_UNIQUE_AGG, ARRAY_UNION_AGG and OBJECT_AGG are ARRAY and OBJECT as window functions**
  (live-verified): the result's columns, SYSTEM$TYPEOF and a table created from them carry the type, where they were
  declared VARCHAR, and a windowed ARRAY_AGG or LISTAGG collects in its WITHIN GROUP order.
- **DEFAULT_NULL_ORDERING places the NULLs of every sort** (live-verified): with FIRST, a key that names no NULLS
  clause puts NULLs first ascending and last descending in ORDER BY, LIMIT and FETCH, set operations, window ORDER BY
  with its ranks and frames, WITHIN GROUP, MATCH_RECOGNIZE and a dynamic PIVOT's columns, and EXPLAIN and refusals
  print the placement it gives, where the parameter was accepted and ignored.
- **A number bound as a whole ORDER BY or GROUP BY key is a constant** (live-verified): over JDBC, ORDER BY ? bound to
  2 no longer sorts by the second column, and GROUP BY ?, ROLLUP (?) and GROUPING SETS ((?)) bound to 1 make one
  group, as the account's server-side binding does, also inside parentheses or under a sign.
- **A recursive CTE's parenthesized body recurses, and its term's clauses are refused in the account's words**
  (live-verified): ((anchor UNION ALL term)) recurses for a CTE and a RECURSIVE view alike, an ORDER BY, LIMIT or
  FETCH inside a parenthesized term is refused with live's sentences, and a window call in the term's QUALIFY is
  refused as one in its select list is.
- **GROUP BY ALL beside a window call refuses the ungrouped column the window reads** (live-verified): SELECT r,
  SUM(amt) OVER () ... GROUP BY ALL is [S.AMT] is not a valid group by expression, as with the keys written out, where
  the refusal named the window item; the item itself is named only where its window reads grouped columns alone.
- **An ORDER BY key that names a select item by its alias or position reads the value shown** (live-verified): beside
  a key that is no output, a sequence's NEXTVAL is drawn once a row and the rows come out in the order of the values
  shown, where the item was computed again for the sort.
- **A window call that reads a select alias reads the value shown** (live-verified): RANK() OVER (ORDER BY n), SUM(n)
  OVER () and MAX(n) OVER (PARTITION BY ...) beside a sequence's NEXTVAL n read the one value drawn for the row, in
  row order, where each read drew again.
- **A correlated subquery reads a bare USING or NATURAL key as the merged key** (live-verified): over p FULL JOIN q
  USING (kk), a row only q holds reads its own kk in a subquery's WHERE, IN or select list, under GROUP BY and past a
  later join, where it read the left copy, NULL; a LATERAL derived table refuses the bare key as ambiguous, and a
  LATERAL table function's argument reads the merged key and refuses a bare name an ON join shares.
- **A VARIANT arm after a text arm converts to the text column's type** (live-verified): 'a' UNION SELECT 'a'::VARIANT
  is one row, EXCEPT removes the row and INTERSECT keeps it, a JSON null is NULL, a temporal takes its output form,
  and a value longer than the column is refused, unless it is a number or boolean constant wrapped into the VARIANT,
  where the VARIANT values were kept as they were.
- **An INTERSECT or EXCEPT over a leading BOOLEAN column whose first row is NULL converts a number arm**
  (live-verified): VALUES (NULL), (TRUE) INTERSECT SELECT 5 keeps TRUE as the same rows in the other order do, where
  it answered no row.
- **A derived table or a CTE run again with the same text compiles its unread items again** (live-verified): after a
  column, a function or a table an unread item names is dropped, or once the role may no longer read a table it reads,
  the statement is refused where the item stands, where a text run before kept answering from the first run's decision
  until its spacing changed.
- **Each engine runs its Python on the interpreter its own configuration chose**: building another engine with a
  different python.runtime no longer switches the engines built before it to that runtime, and engines configured
  alike share one interpreter.
- **A CPython worker no longer outlives the thread it served**: the worker of a thread that has ended is stopped when
  the next worker starts and by a sweep every ten seconds, where every thread that ever ran Python kept a python3
  process for the life of the JVM.
- **An ALTER SESSION run inside a block or a caller's-rights procedure reaches the statements after it**
  (live-verified): WEEK_START, WEEK_OF_YEAR_POLICY, TIMEZONE, TIMESTAMP_TYPE_MAPPING and the input and output formats
  are pinned again, where every later statement of the block kept the values the outermost statement started with.
- **A staged CSV file's byte order mark is passed over unless SKIP_BYTE_ORDER_MARK = FALSE, and a JSON file's always**
  (live-verified): the mark stayed in the first field of a CSV file, failing a number in it, and made a JSON file
  invalid, in loads, staged-file queries and INFER_SCHEMA alike.
- **MAX_BY, MIN_BY, WIDTH_BUCKET and ZIPF take a NaN or infinite FLOAT** (live-verified): MAX_BY and MIN_BY order NaN
  above every number, WIDTH_BUCKET buckets in FLOAT arithmetic, and ZIPF draws rank 1 for an infinite exponent and
  refuses an infinite or NaN count in the account's words, where each failed with a Java number format error.
- **A stream keeps its table while a session's own temporary table takes its name** (live-verified): that session
  reads, consumes and asks SYSTEM$STREAM_HAS_DATA of the stream as any other does, where every read was refused as
  `Base table '...' dropped`; a view stream read there is refused with the account's change-tracking sentence while
  the temporary table, which the view reads in that session, tracks no changes.
- **Every read of a stream inside a transaction lists the changes committed before the transaction began, and none of
  its own** (live-verified): each statement of the transaction sees the same changes, another session's commits after
  the BEGIN wait for the next read, and a COMMIT leaves the stream at the transaction's start, so the transaction's
  own changes stay in it. Reads folded the transaction's own uncommitted DML in and consumed it at the COMMIT.
- **A view whose query change tracking cannot follow is refused in the account's words and order** (live-verified):
  CREATE STREAM ... ON VIEW, CREATE VIEW ... CHANGE_TRACKING = TRUE and ALTER VIEW ... SET CHANGE_TRACKING = TRUE
  refuse window functions, QUALIFY, DISTINCT, GROUP BY and its super-groups, LIMIT, TOP and FETCH, scalar aggregates
  (named as the account computes them), set operations other than UNION ALL, outer joins and CONNECT BY, each at its
  place, and a stream also any table function first; a stream over a window function or a scalar aggregate was created
  and failed when read.
- **A stream on a view whose table was dropped and created again re-binds the first time it is read or
  SYSTEM$STREAM_HAS_DATA asks of it** (live-verified): SHOW STREAMS then lists it healthy over the new table, a stream
  holding no change it has not read reads that table from then on - refused until the table next changes where its
  change tracking began only with the re-bind - and one holding changes keeps reading its own table, so UNDROP brings
  those changes back; a view stream whose table is gone with no table under its name fails as its view's expansion
  does, `Failure during expansion of view 'DB.S.V': ... Object 'DB.S.T' does not exist or not authorized.`, where
  every read was refused and the stream never recovered.
- **A copy of the engine - `cloneInstance()`, or a `checkpointStateTo` snapshot rebuilt by `restoreStateFrom` -
  carries nothing a session created TEMPORARY**: the copy has none of the source's sessions, so their tables, views,
  functions, procedures, stages and stage files stay behind, as after a restart, while the restoring engine's own
  sessions keep theirs. A snapshot no longer saves temporary views, functions and procedures, which came back
  permanent after a restart.
- **INFORMATION_SCHEMA.ENABLED_ROLES and APPLICABLE_ROLES answer for the session** (documented): ENABLED_ROLES lists
  the roles in use and every role granted to one of them, with PUBLIC, and APPLICABLE_ROLES the role grants applied to
  the session's user, where both listed every role and every user's grants.
- **A VARIANT number an element or a path reads off a stored table's column converts to a DATE, a TIME or a
  TIMESTAMP_NTZ** (live-verified): `a[0]::DATE`, `o:n::DATE`, `GET(a, 0)::DATE` and `TO_DATE(a[0])` read the number as
  days from the epoch, half away from zero (1.5 is 1970-01-03), `::TIME` and `TO_TIME` as seconds into a UTC day, and
  `::TIMESTAMP_NTZ` as seconds from the epoch in UTC, through derived relations, CTEs and views that pass the column
  or the element on as it is, and through a grouping key the item itself is (`GROUP BY 1`, `GROUP BY ALL`). The number
  a constant holds, a VARIANT column read whole, and an element of a value a grouping, a DISTINCT, a sort, a row
  limit, a set operation or an expression computed stay refused with `Failed to cast variant value 1 to DATE`, where
  every element was refused before.
- **A TRY_CAST into GEOGRAPHY or GEOMETRY is refused with the geospatial parameter's sentence** — `invalid type
  [TRY_CAST(1)] for parameter 'TO_GEOGRAPHY'` — ahead of TRY_CAST's own argument-type one, and a constant geospatial
  value casts to its own type, `TO_GEOGRAPHY('POINT(1 2)')::GEOGRAPHY`, where it was refused (live-verified).
- **A correlated IN, and a correlated EXISTS or scalar aggregate over TIMESTAMP_LTZ, TIMESTAMP_TZ or BINARY keys, is
  computed once for the statement** rather than once per outer row: the IN compares the value with the members of the
  outer row's keys under its own NULL rules — a positive membership two-valued, a NOT IN three-valued — and the keys
  equal by instant and by bytes. At 2,500 rows a side a correlated IN went from about 750 ms to 27 ms.
- **A FLOAT window frame that moves along its partition is computed in one walk, with live's digits** (live-verified):
  SUM and AVG over a ROWS frame read the partition's running sum before any frame is added up on its own; over the
  RANGE frame from the current row to the partition's end they add the peer groups from the end, each group's sum
  corrected — over 1e16, 1, 2.5, -1e16, 3.25, 0.001 and 7 SUM answers 14 on the first row where the frame added from
  its start gave 14.251; and VARIANCE, VAR_POP, STDDEV and STDDEV_POP slide one running sum and sum of squares that
  rows enter and leave, compensated for a ROWS frame and for RANGE from the partition's start, plain for a RANGE frame
  with an offset, where each frame was added up afresh. Other aggregates over a frame answer each distinct frame once,
  so the rows of a peer group share one answer.
- **A session's temporary stage is its own**: two sessions may each hold a temporary stage of one name, each listing,
  reading and dropping its own files, and a temporary stage above a permanent one hides it from its own session alone
  — `CREATE [OR REPLACE] TEMPORARY STAGE` over a permanent stage creates the session's own instead of being refused or
  replacing the permanent one, and a dropped one uncovers it (live-verified). Its files live under the session and go
  when the session ends.
- **A temporary routine and a permanent one of the same signature cannot meet in the session that creates the second**
  (live-verified): a temporary function or procedure over a permanent one, and a permanent one over the session's own
  temporary one, are refused with the existing routine spelled — `Object 'QF(X NUMBER):NUMBER(38,0)' already exists.`
  — whatever OR REPLACE or IF NOT EXISTS says, where `CREATE OR REPLACE TEMPORARY` replaced the permanent routine for
  every session.
- **A transaction that writes a session's own temporary table holds nothing of the permanent table its name hides**:
  another session's UPDATE of the permanent table no longer waits for it (live-verified); and `ALTER TABLE … SWAP
  WITH` swaps two of a session's own temporary tables, where it failed with `Cannot SWAP: table storage missing` after
  trading their names.
- **A hybrid table's foreign keys hold across a transaction's own changes and a table's reference to itself**
  (documented): a parent row the open transaction deleted no longer holds a child's key and one it updated holds its
  new key, where only its inserts were counted; a self-referencing table takes a row that names itself; and TRUNCATE
  of a hybrid table another one's foreign key references is refused while the reference stands.
- **A Java function's or procedure's handler in an IMPORTS JAR is judged as the routine is created** (live-verified):
  an overloaded handler is refused as `Cannot determine which implementation of handler "f" to invoke since there are
  multiple definitions with 1 arguments in function F with handler H.f`, a missing, wrong-arity or non-public one as
  `Failed to find a public method named "f" with 1 arguments …` — a procedure's Session counted, `with 2 arguments` —
  and an instance handler whose class has no public zero-argument constructor as `The provided method is non-static,
  but the enclosing class does not have a public zero-argument constructor …`, where each was created and resolved by
  its arguments' classes when called; once the JAR is replaced, a call refuses in those words, and a class it no
  longer holds as the account's class loader refuses one rather than naming the engine's stage directory.
- **An engine that cannot open its write-ahead log refuses to start**: `Cannot open the write-ahead log at <path>:
  <cause>`, where it started without one and silently kept nothing a restart could recover; a `jdbc:frostlake:file:`
  connection to such a directory fails to connect and leaves the directory free.
- **The JDBC driver reads DATE, TIME and timestamp cells as the account's driver does** (live-verified):
  `getTimestamp`, `getDate`, `getTime` and `getObject`, with and without a `Calendar`, read a cell that names no zone
  in the zone the session's TIMEZONE names — a TIMESTAMP_LTZ as its instant shown at the session's wall clock and a
  TIMESTAMP_TZ at its own offset, a TIME through `getTimestamp` as 1970-01-01 in UTC, a DATE through `getObject` as
  the session's midnight, a TIMESTAMP_NTZ through `getTime` as its wall clock in the session's zone — where each read
  the JVM's zone; the HTTP API reports the session's time zone (`timezone`) beside each answer.
- **A table CREATE OR REPLACE replaces is kept as a dropped version** (live-verified): SHOW TABLES HISTORY lists it
  with its rows, and once its name is free UNDROP brings back the most recent version — of a permanent, a transient or
  a temporary table, and of one a CTAS or a CLONE replaced — with what a transaction open in another session committed
  into it after the replace, after a clone or a restart too, where UNDROP was refused; CREATE OR ALTER keeps none, and
  the storage set aside for such a transaction is released with its version.
- **A connection of the in-process JDBC driver waits for a table another connection's transaction holds**
  (live-verified): its UPDATE, DELETE, MERGE or TRUNCATE waits for the holder to end, runs over what it committed, and
  is refused once the session's LOCK_TIMEOUT passes, where it wrote through the held table.
- **A lock refusal and SHOW LOCKS name the holder's latest statement that changed a row of the table**
  (live-verified): an INSERT among them, a statement that changed no row not, and a procedure's statement rather than
  its CALL, which a child statement's query ID now names from its start; SHOW LOCKS lists the lock an INSERT into a
  table with rows takes, where one into an empty table takes none, each lock acquired at its first statement. The
  refusal carries code 627 — 626 where the session may not wait — and SQLSTATE 57014 to a handler and to the driver,
  and a task's run it ends reads ERROR_CODE `000627`, where the refusal named the first statement or a fresh ID and
  read 100351.
- **A task's run that waits for a held table no longer stalls the engine**: a run inside EXECUTE TASK waits for the
  holder as on an account, while another run and every other request go on, where a second run waited with the
  engine's lock held and the holder could not commit.
- **A time or a timestamp session variable keeps the precision of the type it was set from** (live-verified): after
  `SET t = '10:00:00'::TIME(3)`, `SYSTEM$TYPEOF($t)` is `TIME(3)[SB4]`, a column `CREATE TABLE … AS SELECT $t` builds
  is TIME(3), a variable set from it or from `(SELECT $t)` keeps TIME(3), and a parameter refuses it as `Unsupported
  type TIME(3) for parameter …`, where each read TIME(9); the write-ahead log keeps the precision beside the value.
- **SHOW VARIABLES lists a variable's family and value as the account does** (live-verified): `date`, `time`,
  `timestamp_ntz`, `timestamp_ltz`, `timestamp_tz`, `binary` and `real` beside `fixed`, `decfloat`, `boolean` and
  `text`, a Boolean as `TRUE`, a time to the nanosecond, a timestamp as its instant in UTC, `2024-01-02
  03:04:05.000000000Z`, a TIMESTAMP_TZ with its offset, `…Ztz=1500`, where each was `text` and the value's own
  spelling.
- **Every property of a network rule, a secret, a packages and an authentication policy takes a session variable, and
  so does a list's item** (live-verified): `VALUE_LIST = ($hp)`, `TYPE = $t`, `SECRET_STRING = $s`, `ALLOWLIST = ($p)`
  and a session policy's `ALLOWED_SECONDARY_ROLES = ($r)` read the variable's text; a number is refused by the
  variable's name, `invalid type of property '$n' for 'SECRET_STRING'` and `invalid value '$n' for property
  'VALUE_LIST'`, and a NULL or a Boolean as the property judges it, where each was a syntax error at the variable.
- **A network rule MODE no type takes stands for INGRESS while the rule's TYPE judges it** (live-verified): `TYPE =
  HOST_PORT MODE = nope` is refused as `The network rule mode INGRESS is not supported by the network rule type
  HOST_PORT. …` and an IPV4 rule's as no mode at all, where both were refused as no mode; an ALTER refuses a TYPE or a
  MODE before judging its value.
- **A GENERATOR's rows past 2147483647 are read through a derived table or a CTE that only projects them**
  (live-verified): `SELECT x FROM (SELECT 1 AS x FROM TABLE(GENERATOR(ROWCOUNT => 3000000000))) LIMIT 2`, the same
  through two derived tables or a CTE named once, and a select under a WHERE of literals that is TRUE generate no more
  rows than the LIMIT reads, where each was refused `GENERATOR cannot produce 3000000000 rows here`.
- **A Snowpark Variant over a Java value that holds a Variant is refused with the serializer's reference chain**
  (live-verified): `new Variant((Object) Collections.singletonMap("k", Arrays.asList(new Variant(1))))` ends its
  message `(through reference chain: java.util.Arrays$ArrayList[0])`, each list's or map's class with the index or the
  key, an array's element by its own class; a top-level array of objects reads its members, `new Variant((Object) new
  Object[] {new Variant(1)})` being `[1]`, where it was refused.
- **An inline Java table function's class lasts its call** (live-verified): what its static fields hold carries over
  the rows a LATERAL call is made for, a counter reading 1, 2 and 3 over three rows, as a jar's does, where the class
  was loaded anew for each row.
- **A view's aggregation and join policies are listed and cloned as a table's** (live-verified):
  INFORMATION_SCHEMA.POLICY_REFERENCES lists them, the entity key as the arguments; a schema's clone names its own
  copies of a view's policies; an ENTITY KEY naming no column of the view is refused at CREATE, `column 'NOSUCH' does
  not exist`; the key's column is read for a group by another column, which counted no entity; and an idle UNSET names
  the view a TABLE, as the account does.
- **A table function's first argument that opens with a query in brackets is that query alone** (live-verified):
  `TABLE(GENERATOR(ROWCOUNT => (SELECT MAX(n) FROM g2) + 1))` is `syntax error … unexpected '+'.` then the second `)`
  after it, a cast, a path, a bracket, IS, IN and BETWEEN alike, after LATERAL too and with the text past a following
  comma read as the FROM list's next tables, where the whole expression was read and answered; `((SELECT …) + 1)`, a
  later argument and a scalar function's argument still take any expression.
- **A block with faults of its own besides a word that names no type is refused for its own faults** (live-verified):
  `BEGIN CREATE TABLE t (a nosuch); SELECT FROM; END` is the FROM and its semicolon, run on its own and through
  EXECUTE IMMEDIATE, where the type word's syntax lines or an extra `Unsupported data type` line were reported; a
  block statement missing its select list or its table reads on through EXECUTE IMMEDIATE as on its own, and `EXECUTE
  IMMEDIATE $$ BEGIN RETURN 1; END SELECT 2 $$` is refused at the SELECT rather than counted.
- **A SQL function's body with a word that names no type is refused at CREATE** (live-verified): `AS $$ SELECT
  1::nosuch $$`, `CAST(1 AS nosuch)` and `a::nosuch` are `Unsupported data type 'NOSUCH'.`, a word with parameters
  under `Compilation of SQL UDF failed: `, once the body's syntax and its relations are read and ahead of its columns
  and calls, where most were created and the rest worded as syntax errors.
- **A SQL procedure's block body with a fault of its own, or more than one statement, is refused at CREATE**
  (live-verified): an operand, a table or a select list missing, a bracket left open, an END where END IF, END LOOP,
  END WHILE, END FOR or UNTIL … END REPEAT is due, and a word after the block's END are refused in the lines the body
  run on its own earns, and `BEGIN RETURN 1; END; SELECT 2` or `SELECT 1; SELECT 2;` with `Actual statement count 2
  did not match the desired statement count 1.`, where each was created and failed only at CALL.
- **A procedure called in a query by its SYSTEM$REFERENCE token runs** (live-verified): `SELECT IDENTIFIER($tok)(4)`
  answers as `CALL IDENTIFIER($tok)(4)` does - the procedure's value under its name, a table procedure's rows, a
  refusal of the arguments at the call (`error line 1 at position 7 Invalid argument types for function 'PR_ARG': ()`)
  - and the call is the procedure's value inside a larger query, where it was `Unknown user-defined function`; CREATE
  VIEW over such a call runs nothing, a TABLE( … ) of one is `Unknown table function ENT_REF_PROCEDURE_…`, and an
  INSERT or a MERGE over one is refused before anything runs, `Unsupported statement type 'LogicalInsert'.` and
  `'Merge'.`
- **A GENERATOR bound over an INTERSECT, an EXCEPT or a UNION of tables' columns is echoed as the plan holds it**
  (live-verified): an INTERSECT as its left side SEMI JOINed to its right on each column's equality and grouped by its
  columns, a side of another type cast to the type both take, an EXCEPT as its left side grouped, a UNION's column as
  SET_COMBINE of the branches' values, one value they all hold standing for it, and nested operations under OB$ names,
  where the bound was echoed as written.
- **A GENERATOR bound over a derived column the statistics pin to one value folds** (live-verified): `FROM (SELECT
  MAX(n) AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))` runs three rows a row, as an expression over pinned
  columns, MIN and COUNT over a whole table, DISTINCT and GROUP BY of a pinned column, a UNION of one value and a CTE
  over any of these do, where each was refused `found 'D.N'`; a refused bound over an outer join's column or a
  set-operation CTE's names it `SYS_VW.N_0`, and EXPLAIN counts the rows of a Generator over a pinned column, the
  table scanned first.
- **A merged view's FLOAT sum prints its constant as a FLOAT literal** (live-verified): the nesting refusal of
  `SUM(v.id)` over a view of `f + 1` is `[SUM(FZF.F + 1.0)]`, where it printed `(CAST(1 AS FLOAT))`; a filter the
  statistics prove true of every row keeps the derived count, `WHERE g3.v > 0` over 10 and 20 as no WHERE at all.
- **A VALUES item's subquery is echoed without the conjuncts the statistics prove** (live-verified): `(SELECT MIN(id)
  FROM dz WHERE id > 0 AND id < 5)` over 0, 1 and 2 echoes `WHERE DZ.ID > 0`, where both conjuncts were printed.
- **A HAVING subquery's IFF or CASE is printed in the nesting refusal** (live-verified): `MAX(IFF(fz.id > 5, g.v, 0))`
  is `[MAX(IFF(CORRELATION(FZ.ID) > 5, G.V, 0))] nested in [MAX(MAX(…))]`, a CASE as `CASE_FLATTENED(…)`, a number
  converted to BOOLEAN as its comparison with zero, a NULL branch as `null`, where the refusal was `Unsupported
  subquery type cannot be evaluated`.
- **A block's flow chain reads its stages as `$n`** (live-verified): `BEGIN SELECT 1 AS a ->> SELECT a + 1 AS b FROM
  $1; RETURN 'ok'; END` runs each stage reading the stages before it, in a loop's body too, and a DDL stage hands on
  its status row, at the request's own level as well; a `$n` no stage of its chain answers is refused while the block
  compiles, `error line 0 at position -1` and `invalid identifier 'SQL_PIPE_1'`, where every `$n` in a block was
  `invalid pipe reference '$1'`.
- **A RESULTSET is filled from a flow chain** (live-verified): `LET r RESULTSET := (SELECT 1 AS a ->> SELECT a + 1 AS
  b FROM $1)` and a DECLARE's `DEFAULT (…)` hold the last stage's answer, where the arrow was a syntax error.
- **A view read that keeps no row types its columns as its body plans them** (live-verified): `SELECT * FROM v WHERE 1
  = 0` over a view of `CAST(NULL AS VARCHAR)` is VARCHAR(134217728), as a read of its rows is, where it took the
  view's stored VARCHAR(16777216).
- **A LET run into the next statement in an IF's, a CASE's or a REPEAT's body reports live's lines** (live-verified):
  the block's own lines, the last moved on past the next name - `'CREATE'`, `'TABLE'` and `'INT'` for `CREATE OR
  REPLACE TABLE u (a INT)` in an IF's branch - where the construct's END was named.
- **A statement word after a FROM list's comma reports live's lines** (live-verified): `SELECT ABS((SELECT 1 FROM t,
  (DESCRIBE TABLE t)))` names the TABLE after the word twice, an UPDATE its SET, a comparison's query the TABLE once,
  and a JOIN's bracket in a call's argument is refused at the JOIN.
- **A write folds an aggregate over no table with its conversion** (live-verified): `UPDATE t SET n = (SELECT
  MAX(12345))`, a MERGE's UPDATE and `INSERT INTO t SELECT MAX(12345)` refuse a value out of the column's range bare,
  `Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345`, where the refusal named the DML
  operation and the column; a query of constants alone, or one reading a table, keeps that envelope.
- **MATCH_RECOGNIZE reports the lines after more of a pattern's commas** (live-verified): a second comma in the
  pattern's own bracket, a bracket after a comma holding one, a reserved word alone before the bracket's close, and a
  comma opening the pattern's own bracket or one inside it report the lines that follow, where the comma was reported
  alone.
- **A grouped select's SEQ item numbers the rows it answers** (live-verified): in `SELECT a, SEQ4() FROM t GROUP BY a
  ORDER BY a` the SEQ numbers the rows in the order they are answered, after the ORDER BY, a DISTINCT and a LIMIT with
  its OFFSET, a window beside it or not, where it read the scan place of each group's first row; an item the ORDER BY
  reads numbers the groups the HAVING kept, in the order they were formed. A SEQ inside an aggregate still numbers the
  scan.
- **Long messages are cut as the account cuts them** (live-verified): a Java or Scala handler's report cut at 4093
  bytes keeps a character the cut splits as one U+FFFD; a failed statement's 16384 characters count a character of two
  UTF-16 units once; and SQLERRM in a block's handler reads the message cut, the failure of a statement the block ran
  or of a procedure it called, where it read the whole message.
- **A session variable is the value of more DDL properties** (live-verified): a table's STAGE_FILE_FORMAT and
  STAGE_COPY_OPTIONS in CREATE and ALTER TABLE, a stage's encryption TYPE, a password or a session policy's COMMENT,
  and a task's SCHEDULE holding a NULL or a Boolean, which leaves no schedule. A parameter refuses a date, a time, a
  timestamp or a binary variable with `Unsupported type DATE for parameter … specified with session variable` and a
  DECFLOAT with `invalid value [5] …`; a missing variable of a parameter is placed at line -1, position -1; a stage's
  FILE_FORMAT reads a Boolean or a date as a format's name and refuses a NULL with `invalid parameter '$v'`; a stage's
  DIRECTORY refuses a variable for ENABLE with `invalid value [$v] for parameter 'ENABLE'`; a switch refuses a date
  with `invalid value [$d] for parameter …`.
- **A column's COMMENT that no string follows is refused at what follows it** (live-verified): `CREATE TABLE t (a INT
  COMMENT = 'c')` names the `=`, where it named the COMMENT.
- **A stage's encryption type no stage knows is refused by its value** (live-verified): `ENCRYPTION = (TYPE =
  'hello')` is `invalid value [hello] for parameter 'TYPE'`, where an internal stage refused it as a key it cannot
  set.
- **CREATE FUNCTION judges a jar table function's handler** (live-verified): two `process` methods of the function's
  argument count are refused when the function is created, as an inline handler's are, where the first call refused
  them.
- **A GENERATOR read under a LIMIT generates no more rows than it reads** (live-verified): `SELECT SEQ4() FROM
  TABLE(GENERATOR(ROWCOUNT => 3000000000)) LIMIT 3` answers 0, 1 and 2, under FETCH and with an OFFSET as well, where
  a ROWCOUNT past 2147483647 was refused; a TIMELIMIT under such a LIMIT ends with the rows it is read for.
- **A jar table function's handler class lasts the statement's call** (live-verified): what its static fields hold
  carries from one row of a LATERAL call to the next, where the class was loaded anew for each row.
- **CREATE VIEW takes an aggregation and a join policy** (live-verified): `CREATE VIEW v [WITH] AGGREGATION POLICY p
  [ENTITY KEY (…)]` and `[WITH] JOIN POLICY p`, before or after the COMMENT, and ALTER VIEW's SET and UNSET of either;
  the view is read under them as a table is, GET_DDL shows them, a missing policy is refused and CREATE OR ALTER VIEW
  refuses them, where each was a syntax error.
- **A GENERATOR bound reading a column the statistics pin to one value folds** (live-verified): `FROM t,
  TABLE(GENERATOR(ROWCOUNT => t.n + 1))` over a table whose rows hold one value, or NULL alone, runs with that value,
  where it was refused as no constant; so does a bound reading such a column through a derived table, a CTE or a view
  that passes it on or projects a literal, from a scalar, EXISTS or LATERAL subquery, or on the kept side of an outer
  join.
- **GET_DDL's refusal of a missing object carries SQLSTATE 02000** (live-verified): a missing table or view, to a
  client's driver and to a handler alike, where it carried the 42S02 the same sentence carries elsewhere.
- **A block's unknown type word is refused as an unsupported data type** (live-verified): `EXECUTE IMMEDIATE $$ BEGIN
  CREATE TABLE t (a nosuch); RETURN 1; END $$`, a block run on its own and a procedure's body are refused with
  `Unsupported data type 'NOSUCH'.` for the first such word, under any scripting construct and in CAST, `::`, DECLARE
  and LET, where they were refused with syntax lines.
- **A GENERATOR bound over a table passed on whole folds** (live-verified): `GENERATOR(ROWCOUNT => (SELECT COUNT(*)
  FROM (SELECT * FROM t)))` runs as the table's count, where it was refused as no constant. A GENERATOR bound over a
  set operation, a FROM-less select or a VALUES list is refused with the plan the account holds for it — a branch's
  rows a GENERATOR of their count, its constants combined by SET_COMBINE, a UNION without ALL grouped — where it was
  echoed as written.
- **The server's lock judgement follows a session variable's name**: a read-only request that reaches a writing body
  through `IDENTIFIER($v)` runs under the engine's exclusive lock, as one naming the body does.
- **SYSTEM$STREAM_BACKLOG survives a restart**: a stream's snapshot keeps the versions past its offset, so a
  checkpoint's restore and a restart from the write-ahead log answer the backlog as before them, where a stream on a
  changed table answered none.
- **A number converted to BOOLEAN settles by its table's statistics** (live-verified): over a column holding no zero
  and no NULL `x::BOOLEAN` is TRUE in the plan, over one of zeros alone FALSE, so `(SELECT MAX(fz.id::BOOLEAN::INT +
  g.v) FROM g)` over such an outer column reads nothing of the outer row and is answered in a HAVING, a select list
  and a WHERE, where it was refused as an unsupported subquery. A HAVING subquery's own aggregate that converts a
  number or a comparison to BOOLEAN and still reads the outer row is refused with the nesting the plan prints —
  `CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN)`, a comparison's number through NUMBER(2,0), a comparison the statistics
  decide as TRUE or FALSE — where it was refused as an unsupported subquery.
- **A SYSTEM$REFERENCE token is its session's** (live-verified): a SESSION reference reads its object in the session
  that made it alone, and one a CALL's arguments make with no scope written ends with the call; another session read
  every token.
- **CALL IDENTIFIER(...)(...)** (live-verified): `CALL IDENTIFIER('p')(4)`, `CALL IDENTIFIER($var)()` and `CALL
  IDENTIFIER(:r)()` in a body call the procedure the name or the SYSTEM$REFERENCE token names, a function's token the
  function, where the second parenthesis was a syntax error.
- **A staged record's keys are sorted** (live-verified): `SELECT $1` over a staged JSON, Parquet, ORC or Avro file
  answers the OBJECT the account holds, its keys sorted at every level — `{"a":"x","m":{"b":3,"y":2},"z":1}` for a
  record written `z, a, m(y, b)` — and OBJECT_KEYS, TO_JSON and the record's text alike, where the file's own order
  was kept.
- **Dynamic tables and join-view streams over tables named in full** (live-verified): a dynamic table whose query
  reads `db.s.t` and a stream on a join view whose FROM names its tables in full know a row by its identity, so an
  UPDATE reads as one row's DELETE and INSERT with METADATA$ISUPDATE TRUE; the join view's stream was refused with
  `invalid identifier` and the dynamic table's update read as an unpaired DELETE and INSERT.
- **A VALUES item's subquery that does not fold is echoed as the plan holds it** (live-verified): `INSERT INTO t
  VALUES (1/(SELECT MIN(id) FROM dz WHERE id > 0))` is refused as `Invalid expression [1 / (CAST((SELECT MIN(DZ.ID) AS
  "MIN(ID)" FROM DZ AS DZ WHERE DZ.ID > 0) AS NUMBER(38,0)))] in VALUES clause` — the item cast to its type where it
  carries a column's or a literal's, a SUM adding its column as its own NUMBER, an AVG as that sum over its count, a
  select of constants over a table as a GENERATOR of its rows — where the subquery was echoed as written. Through a
  LIMIT that keeps a row and through a view passing one table's columns on, the statistics fold the subquery (`[1 /
  0]`), and a quotient of a literal divided again is cast first, `(CAST(1 / 0 AS NUMBER(13,12))) / 2`.
- **A value query whose FROM reads a statement word, as a call's argument** (live-verified): `SELECT UPPER((SELECT 1
  FROM (DESCRIBE TABLE t)))` is the SELECT, the `DESCRIBE` and the `TABLE` after it — the SET after an UPDATE's name,
  the token after INSERT INTO and its name, the `@st` after LIST — and the inner `(` of doubled brackets; two calls
  deep, or with a name first in the query's select list, the SELECT and the word; in a WHERE, a HAVING, an ORDER BY or
  a GROUP BY the SELECT, the word and the `)` before the outermost call's own. It was the SELECT alone.
- **Readers blind to duplicates over grouped and passing bodies** (live-verified): `SELECT DISTINCT y FROM (SELECT x,
  y FROM (… UNION …) GROUP BY x, y)` answers the values of y, x never converted — a block grouping by columns,
  positions, ALL or one grouping set, computing nothing over the groups, loses its grouping under such a reader, in a
  derived table, a CTE or a view; a block passing its rows through, `SELECT * FROM (… UNION …)`, reads the union as
  such a reader; and a DISTINCT body that sorts compares its rows in their own order, naming the first value that
  fails to convert as written.
- **A column DEFAULT written as a condition** (live-verified): `DEFAULT NOT FALSE`, `DEFAULT TRUE AND FALSE`, `DEFAULT
  NOT 1 = 2` and the like are created and told back as the plan prints them, `NOT(FALSE)`, `(NOT(FALSE)) AND TRUE`,
  `NOT(NOT(TRUE))`, `NOT('a' LIKE 'b')`; `DEFAULT NOT NULL` is the NOT of a NULL, `NOT(CAST(null AS BOOLEAN))`, which
  leaves the column nullable; `DEFAULT (NOT FALSE)` was told as NULL.
- **The SUM and the AVG of a merged view's column** (live-verified): over `vz AS SELECT id + 1 AS id FROM t`, an outer
  aggregate refused as nested reads the column's sum and the constant times the derived count, `CAST((SUM(CAST(T.ID AS
  NUMBER(38,0)))) + (CAST(1 * 2 AS NUMBER(19,0))) AS NUMBER(38,0))`, its COUNT form over a join, and any other
  expression as its own sum, `SUM(CAST(T.ID * 2 AS NUMBER(38,0)))`; the AVG divides that sum by the expression's
  COUNT. The view's column was named as written.
- **A block value query's lines in the places next to the measured** (live-verified): a declared scalar's `x :=
  (SELECT 1 x y)` reads its query alone, `'y'`, where the next statement was reported too; a call's later argument is
  refused at its SELECT alone; a bracket before the first word, `(SELECT (1) x y)`, names the `'1'` and then the
  query's own fault; a list item followed by a name names it and the query's `)`; and a TABLE query with words after
  its name in a routine's unquoted block body reads as the block alone, `'x'`, `'y'`.
- **SHOW GRANTS OF DATABASE ROLE over the SNOWFLAKE database** (live-verified): each of its roles lists the SNOWFLAKE
  application as a grantee, `APPLICATION | SNOWFLAKE`, granted by no role.
- **TAG_REFERENCES' ACCOUNT domain**: the account named by its locator, as CURRENT_ACCOUNT() answers it, lists the
  account's own tags, where every name was refused as an account that does not exist.
- **Syntax-error lines of a MATCH_RECOGNIZE pattern's comma** (live-verified): a comma in a pattern's bracket that is
  no PERMUTE list, or a reserved word opening one, is the first line, where the bracket's first symbol or the DEFINE
  was named, and inside a nested bracket live's reading on — the rest read as the clause's definitions, then the
  relation's alias — is reported in full: `PATTERN ((lo, hi))` is `','`, `')'`, `')'`.
- **Syntax-error lines of a CAST's bracketed operand and of a cast's comma in a longer IF or WHILE condition**
  (live-verified): `SELECT CAST((1 x y) AS INT)` is `'x'`, `'y'`, then the cast's `')'`, and `BEGIN IF (CAST(1, 2) = 1
  AND TRUE) THEN RETURN 1; …` reports the body's `'1'` after `','` and `'='`.
- **A session variable read with a dotted key** (live-verified): `$x.y` is one variable's name, `X.Y`, whether or not
  `X` is set — `Session variable '$X.Y' does not exist` — where a set `X` gave a syntax error at the dot.
- **A CALL answer for an OBJECT, an ARRAY or a VARIANT variable** (live-verified): `CALL … INTO :x` judges the answer
  by its static type as a SELECT … INTO judges a selected value, so an ARRAY makes a text its one element,
  `["[1,2]"]`, where it held the text parsed, and an OBJECT refuses a text, NULL among them, and a whole number,
  `invalid type [CAST(identity('x') AS OBJECT)] for parameter 'TO_OBJECT'` and `CAST(TO_NUMBER('5') AS OBJECT)`, where
  it held the value; a JSON null read from a held answer is SQL NULL for an OBJECT or an ARRAY.
- **Syntax-error lines around value queries and method calls** (live-verified): an alias in a select-list value query,
  an IN list's query faulting in a later call, a first-item call's word after its first argument, a value after a
  method's argument, a FROM after a method call inside a call's brackets, and an external table's virtual columns
  before a faulty type report the lines the account reports.
- **MAP_PICK and MAP_DELETE over several keys** (live-verified): the keys are judged by the one type their values fold
  to, as a conditional's branches fold, and a type that is no key of the map is refused with GET's sentence:
  `MAP_PICK(m, 'a', 2)` over a text map is `Function MAP_PICK cannot be used with arguments of types
  MAP(VARCHAR(134217728), NUMBER(38,0)) and NUMBER(18,5)`, where only a single key was judged.
- **The nullability a DML range refusal prints** (live-verified): a value an UPDATE … FROM reads from a FROM relation,
  and a scalar subquery an UPDATE or a MERGE writes that reads a table, a name of the statement around it, or an item
  that can be NULL, print `{nullable}`, as does any scalar subquery an INSERT … VALUES writes, where they printed
  `{not null}`.
- **A session variable as a DDL property's value** (live-verified): `COMMENT = $x` is taken by every kind's CREATE and
  ALTER, and a table's, a schema's and a database's parameters, a task's options and SCHEDULE, a file format's options
  and a stage's FILE_FORMAT and COPY_OPTIONS take a variable too, each judging what it holds as the account does: a
  COMMENT takes a text and leaves no comment for a NULL or a Boolean, a switch such as CHANGE_TRACKING takes no
  variable, a parameter a value of the type it is listed with (`invalid value [zz] for parameter
  'DATA_RETENTION_TIME_IN_DAYS'`), and a file format's or a stage's option a text alone (`invalid type [$n] for
  parameter 'SKIP_HEADER'`), where every such place was a syntax error at the variable.
- **Long failure messages** (live-verified): a Java or Scala handler's report of more than 4096 bytes of UTF-8 is cut
  to its first 4093 and ended by `...` before ` in function F with handler H`, and a failed statement's message of
  more than 16384 bytes is cut to its first 16384 characters and ended by `... (Original error message truncated since
  it exceeded ERROR_MSG_MAX_SIZE).`, as the account cuts it, twice over.
- **CREATE OR ALTER SCHEMA** (live-verified): creates a schema that is not there, as CREATE does, and makes one that
  is there the schema it writes, unsetting a COMMENT, a retention, a parameter or MANAGED ACCESS it leaves out, the
  session staying in its own schema (`Statement executed successfully.`), where the statement was a syntax error; IF
  NOT EXISTS, a CLONE, a tag and a change of transience are refused with the account's sentences.
- **A schema clone's cut-short options and early points** (live-verified): `CREATE SCHEMA s CLONE t INCLUDE INTERNAL
  STAGE`, `… IGNORE TABLES WITH …` and `… INCLUDE` report the account's further syntax lines, and a clone `AT` or
  `BEFORE` a point before its source schema was made is `Time travel data is not available for schema T. …`, where it
  was a missing object.
- **A jar's handler class per statement** (live-verified): a Java function whose IMPORTS jar holds its handler runs in
  a class loaded for the statement and the call that names it, so a static field starts over in the next statement and
  at a second call, as an inline body's does, where the jar's class was kept for the life of the engine; a Java
  procedure's and table function's jar class is loaded anew per call likewise.
- **Snowpark Variant time texts** (live-verified): `asTimestamp()`, `asDate()` and `asTime()` read a text on the
  java.sql calendar of the session's zone, `0001-01-01 00:00:00` in America/Los_Angeles reading -62135740800000, and a
  `java.sql.Time` inside a `Variant(Object)` is written as its own text.
- **CREATE OR ALTER TASK with a WITH CONTACT clause** (live-verified): the syntax lines are the account's, one line at
  the second WITH after `WITH TAG (…)`, where a further line followed.
- **A failed statement carries its code and SQLSTATE** (live-verified): both drivers, in-process and HTTP, raise a
  failed statement's `SQLException` with the account's vendor code and SQLSTATE of its family — a missing table 2003 /
  42S02, a missing schema, stage or routine 2003 / 02000, a syntax error 1003 / 42000, a division by zero 100051 /
  22012, a NULL in a NOT NULL column 100072 / 22000, an exception a script raised its declared code with P0001, and
  some sixty families more — where they carried code 0 and no SQLSTATE; the HTTP answer's `errorCode` and `sqlState`
  carry the pair, and a block's handler reads the same table, a missing schema, sequence or stage now 2003 / 02000.
- **A plain star over the scan of a scripting answer** (live-verified): `SELECT * FROM
  TABLE(RESULT_SCAN(LAST_QUERY_ID()))` after a SQL procedure's CALL or an anonymous block hands a VARIANT out laid out
  across lines as the CALL did, and so does a plain star over the scan of that star, where it read compact; every
  other statement over the scan still reads it compact.
- **GENERATOR bounds that read the row around their query** (live-verified): inside a value subquery or an EXISTS,
  `ROWCOUNT => gn.n` is refused as `found 'CORRELATION(GN.N)'`, where it was an unsupported subquery, a LATERAL
  subquery's correlated TIMELIMIT alone is argument 1, where it was argument 2, and an outer column inside a subquery
  bound is echoed as a correlation.
- **SYSTEM$REFERENCE function tokens** (live-verified): a FUNCTION token calls its function in `IDENTIFIER(:r)(…)`,
  where it was an unknown function.
- **A SQL function's block body binds its parameters at their declared types** (live-verified): 1.239 into a
  NUMBER(10,2) parameter reads 1.24 in a `BEGIN … END` body, where it kept the caller's digits; an expression body
  keeps an argument's own NUMBER(38,37), where it read NUMBER(2,1).
- **CONVERT_TIMEZONE's three-argument form over a TIME** (live-verified): `CONVERT_TIMEZONE('UTC', 'America/New_York',
  '12:00:00'::TIME)` is `incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]`, as the two-argument form refuses one,
  where the time's text was refused as no timestamp.
- **SYSTEM$STREAM_BACKLOG lists a version per statement** (live-verified): every statement that writes a table lists a
  version of its own, inside an explicit transaction too, where a transaction's statements were listed as one; a
  statement that changes no row lists a version of nothing where it commits alone, a TRUNCATE lists one even of an
  empty table, a MERGE is an UPDATE whatever its branches did, and the estimate rounds three in ten of the rows added
  and removed half up, an append-only stream reading a MERGE's rows where the table grew.
- **Grouped select-list aggregates over a per-group subquery, under a test** (live-verified): `NOT (MAX((SELECT g.v
  FROM g WHERE g.id = t.id)) > 0)` in a grouped select list, and an IS NULL, a CASE, an IFF or a COALESCE over such a
  comparison, are computed once for each group over its keys, as in HAVING, where they were refused as an unsupported
  subquery; one reading a column the select does not group by is refused at the column, neither an aggregate nor in
  the group by clause.
- **The nested-aggregate refusal of a HAVING subquery prints further shapes** (live-verified): a text or a FLOAT cast
  to a number (TO_NUMBER, a cast of its own) with the other side cast to its own type, an outer number cast to a
  BOOLEAN, remainders and MOD, decimal and text literals, chained concatenations and a SUM over arithmetic, where an
  unsupported subquery was reported.
- **A table function's row naming a column the call cannot see** (live-verified): a parenthesized row among a table
  function's arguments has its names resolved before it is typed, so `SELECT * FROM TABLE(FLATTEN((a, b))), t2` is
  `invalid identifier 'A'` at the `a`, where it was refused for its type, and a row after the relation holding its
  names, `SELECT * FROM t2, TABLE(FLATTEN((a, b)))`, is `invalid type [ROW(NUMBER(38,0), VARCHAR(16777216))] for
  parameter '1'`, where it was "not enough arguments".
- **Rounding to a FLOAT scale** (live-verified): CEIL, FLOOR, ROUND and TRUNC over a FLOAT scale - a cast, a FLOAT
  column, TO_DOUBLE, a double bound through JDBC - round in FLOAT arithmetic to the scale's own value and answer a
  FLOAT: ROUND(1.2345, 2::FLOAT) is the FLOAT 1.23 and ROUND(1.2345, 2.5::FLOAT) 1.233288287, where a NUMBER 1.2300
  came back; ROUND takes no mode beside a FLOAT scale.
- **LAST_QUERY_ID's bind for DESCRIBE RESULT and RESULT_SCAN** (live-verified): an unset :x, :1 or ? there is refused
  as no constant, `found 'TO_NUMBER(:x, 18, 0)'`, before the bind is read.
- **DECFLOAT session variables across a restart**: the write-ahead log names the variables a SET gave a DECFLOAT, so a
  replayed statement reads them as DECFLOATs again; a log of the earlier version still reads, and keeps its first
  start.
- **Parameter values judged by type first** (live-verified): ALTER SCHEMA and ALTER DATABASE refuse a value of the
  wrong type for a session's or a warehouse's own parameter as an invalid value, before the parameter's level:
  TELEMETRY_ENABLE_INTERACTIVE_MODE = 'x' and FALLBACK_WAREHOUSE = 1.
- **Geospatial casts typed** (live-verified): a cast to GEOGRAPHY or GEOMETRY has that type, so a CTAS over
  NULL::GEOGRAPHY declares a GEOGRAPHY column and Snowpark's schema() names it GeographyType, where both read a text.
- **VALUES folds before it refuses** (live-verified): a NULL operand folds a division or a refused function to NULL
  and the item is taken, a division to a whole quotient other than zero folds, and a constant or statistics-answered
  subquery is echoed as its value, a text operand as the number it spells.
- **Staged refusals name the column read** (live-verified): a staged JSON, XML or CSV file's refusal names the
  positional column being read only when the query reads one; under COUNT(*), a constant or METADATA$ columns alone it
  names none.
- **Dynamic table changes by identity** (live-verified): CHANGES over a dynamic table and a stream on one pair an
  updated row as a DELETE and an INSERT with METADATA$ISUPDATE TRUE, and read a row deleted and inserted again as
  both, where the table's query reads stored tables in one block.
- **Parquet and ORC strings that are no UTF-8** (live-verified): a staged Parquet or ORC string that is no UTF-8 is
  refused in the account's words where a query reads a value, read as U+FFFD under REPLACE_INVALID_CHARACTERS, and
  COUNT(*) reads the rows without decoding them.
- **A refused LONG reads on** (live-verified): after a `::` cast to LONG the token after the word is told too, unless
  a whole select item may end there.
- **FLOAT cells on the wire** (live-verified): the HTTP result set carries a FLOAT cell with the digits the account's
  JSON results carry, at most nine decimals and fifteen significant digits from 1 up and ten significant digits below
  1; the engine's values are unchanged.
- **Named logical arguments** (live-verified): a NOT, AND or OR over values written for a name in a function's or
  FLATTEN's argument list binds its BOOLEAN, and an incomplete one is refused at the call's ')'.
- **Column defaults told back** (live-verified): an expression DEFAULT is printed by DESCRIBE, SHOW COLUMNS, GET_DDL
  and INFORMATION_SCHEMA.COLUMNS from the plan it compiled to, as a routine parameter's default is: a `::` cast as
  `CAST(… AS …)` with the type's default precision, a CASE as `CASE_FLATTENED(…)`, a division with its scaled cast,
  DATEADD as `DATE_ADDDAYSTODATE`, a parenthesized operation uncast; a boolean default reads `TRUE`, and DESCRIBE
  shows no default for `DEFAULT NULL`; the text survives a snapshot and a clone.
- **DESCRIBE's worded types** (live-verified): a TIMESTAMP in a DESCRIBE's argument list whose time-zone words break
  off is told as a cast's is, and on once more.
- **Outer aggregates over merged views** (live-verified): a derived table's MIN, MAX or COUNT of a view column
  standing for an expression prints that expression over the stored table; a COUNT under a filter or over a join is
  refused as nested; a view or derived table passing a stored table through counts that table's rows; an AVG under a
  filter or over a join divides by the column's COUNT, a fixed-point one too.
- **Statement words where a query is read** (live-verified): in a FROM clause's brackets INSERT, UPDATE, MERGE and
  TRUNCATE are refused by their first word, EXPLAIN USING and UPDATE's SET are told next, and INSERT's report reads on
  past its first comma; an INSERT or an UPDATE as a select item's bracketed value is refused at the name's next token;
  a query used as a value is refused at its SELECT before the statement word in its FROM, and an EXISTS's query also
  at the EXISTS's ')'; a word after a name in an INSERT's column list ends the report, except TABLE, whose query is
  followed by the list's ')'; a VALUES list with an empty row or a trailing comma is refused at VALUES; a bracketed
  source holding an aliased relation and given a second alias is `duplicate alias`, before its relations are looked
  for.
- **DISTINCT under a reader blind to duplicates** (live-verified): a derived table's, a CTE's or a view's body that is
  one SELECT DISTINCT block loses its DISTINCT under a block that cannot tell duplicate rows apart, so a column or an
  item that block never reads is never computed; a star that excludes columns passes the relation's own values on.
- **Grouped alias types** (live-verified): in a grouped or aggregating select list, a later item that reads an earlier
  item's alias is typed from the expression the alias names, so SYSTEM$TYPEOF and arithmetic over the alias agree with
  an ungrouped list.
- **Data metric references** (live-verified): MODIFY DATA METRIC FUNCTION refuses a metric the table or view does not
  carry, as DROP does; DATA_METRIC_FUNCTION_REFERENCES answers its texts at the full text width and PROPERTIES as a
  VARIANT object, shown as the session's JSON_INDENT shows one.
- **Lexer and operator leftovers** (live-verified): a dollar sign after a select item's value is read past to the next
  name or AS; each ampersand after an ampersand is refused, and a FROM after one reads the statement on; a dollar
  quote left open reads on from its second dollar sign; a dot straight after a number is refused itself; a sign or a
  tilde straight after a tilde is an invalid function named by the outer one; an unknown function is named before a
  known one's argument count; a name ending in a dollar sign and a backticked qualifier are echoed in quotes; a word
  after a string inside brackets ends the report; a statement word after a LIMIT that may be an alias is told for both
  readings; a second `!` after a value's method marker ends the report.
- **Account parameters** (live-verified): SHOW PARAMETERS IN ACCOUNT reads JSON_INDENT at the account's own value,
  lists ENABLE_CORTEX_EMAIL_ATTACHMENTS, ENABLE_CORTEX_TOOLS_ADVANCED_GOVERNANCE and
  ENABLE_DEFAULT_EMAIL_NOTIFICATION, and gives ENABLE_SNOWFLAKE_INTELLIGENCE_ARTIFACT_SHARING and
  FEATURE_CORTEX_CODE_SANDBOX their new defaults; a tag set on the account survives a snapshot.
- **SQL function block bodies** (live-verified): a body is compiled whole with the statement that calls the function,
  so a RETURN no call reaches — in an exception handler, a branch never taken, after another RETURN — is refused at
  the call; a refusal's place counts each name as a bind; TABLE and a pipe of statements are a block's statements,
  refused in a function's body by their kind; a manual ALTER TABLE … RECLUSTER is an unsupported feature, and
  RECLUSTER in a body; and a quoted body that reads as no block names what its frame reads on to past a bracket of its
  own.
- **A DECLARE section in a block's statements** (live-verified): its items take any word as their type, so the END its
  BEGIN is due at, or a second word after the type, is refused, and an unknown type is refused when the section's
  block follows.
- **The SNOWFLAKE database's ACCOUNT_USAGE.QUERY_HISTORY view** (live-verified): a secure view no role owns, with the
  account's columns and no rows, listed by SHOW VIEWS and described by DESCRIBE VIEW; and PUBLIC holds the thirteen
  database roles the account grants it.
- **Faulty queries in a block's values** (live-verified): a query in parentheses as a RETURN's or a LET's value
  reports its SELECT and then the token after the first name the block reads past the query's first word, one word
  further for each bracket around it; a ',' first reads the value as a list, where an AS is refused; a RESULTSET's
  query reads as a statement of its own; a cast's comma inside a call in a value adds the token after the cast; and
  JOIN INNER, CROSS or ASOF before a name in a block reports the name twice, as at the top level.
- **Backticked names, a DESCRIBE's stage value, correlated depth and EXISTS operands** (live-verified): a DESCRIBE
  whose property value is a stage reference the account reads whole leaves the faulty statements after it to report
  their own lines; a correlated query two levels deep is placed at the ORDER BY's beside a select list's; an EXISTS or
  a NOT EXISTS written as the right operand of an arithmetic or concatenation operator reports its first word, its
  query's SELECT and the query's `)`; and a bracketed EXISTS cast with `::` reports the `::` and then the bracket's
  `)`.
- **Faults inside a query used as a value** (live-verified): a literal written as a select item's alias reports the
  query's `)` after it and the AS before it after an unaliased item, and a call in the ANSI FROM form in the query's
  first item reports the query's SELECT and then the item's second token, as a first-item call does.
- **Script recovery past a first fault** (live-verified): a later SHOW whose scope is a keyword word refuses the name
  after it, a statement keyword run into a later statement is refused, a run-in DROP's words after its name are read
  past while a run-in MERGE ends the report, the WHILE of END WHILE no longer adds a line, and a semicolon where IF
  NOT wants EXISTS adds the end of the input after the next statement's line.
- **Cast commas in block conditions and stray brackets in FROM lists** (live-verified): a cast's comma opening an IF's
  or a WHILE's condition reports the token after the cast and the construct's next statement, and a second FROM after
  a bracketed FROM item reads on at the next clause.
- **MATCH_RECOGNIZE lines past the first fault** (live-verified): a word where a sub-clause is due is read as a
  definition's symbol and value, a reserved word is read past to the next word, an AS, a bracket or an ORDER before
  the PATTERN lets the query read on, a measure without its alias before a comma names the words after it, and a
  PATTERN without its bracket reads DEFINE as a symbol, each reporting the further lines the account reports.
- **Unfinished statements and IN after an alias** (live-verified): a name with an empty middle part in a statement
  that breaks off at its end is reported as in a finished one, and `SELECT SHOW TABLES IN a` refuses the IN after the
  item's alias.
- **Syntax-line readers in more places** (live-verified): a bracketed literal argument is read through in a CTE's and
  a derived table's query and before a call's last argument, a join's USING without its list is refused at the USING
  in any query, a derived table's adding its ')', and an unset `$x.y` is named whole without a position.
- **Argument types refused while the statement compiles, at the call** (live-verified): BOOLAND and BOOLOR over
  BOOLEANs, VECTOR_AVG and VECTOR_SUM over a VARIANT, and a bit aggregate over a BOOLEAN or a date or time are refused
  with the call's place, ahead of a correlated aggregate's place refusal, where they were refused unplaced while rows
  were read or as a number that was not recognized.
- **The echo of an outer aggregate refused in a subquery** (live-verified): the regressions print their planned
  formulas over exact values, COUNT_IF keeps a doubled NOT, and a grouped ORDER BY key comparing a ROW with a query
  names the ROW.
- **Column list and star modifier lines** (live-verified): a typeless first column before a typed one is refused at
  its comma, ADD COLUMN followed by a parenthesis names the word after the first one inside it, and a star's misplaced
  REPLACE under a CAST reads on as live does.
- **FIRST_VALUE and LAST_VALUE whose argument is a call followed by a null treatment** (live-verified):
  `FIRST_VALUE(ABS(n) IGNORE NULLS) OVER (ORDER BY n)` runs, ignoring the NULLs, where it was refused as a syntax
  error at the IGNORE.
- **JSON.stringify of a value holding a circle, in a JavaScript function or procedure** (live-verified): the refusal
  describes the circle as the account's engine does - where it starts, its first links and its last, and the key that
  closes it - where only its first line was given.
- **Further syntax-error lines** (live-verified): a fault inside a value query's call, a policy's RETURNS type list
  that cannot begin, a type list in a dynamic or external table's column list, an AS inside a quantified or IN list,
  an EXISTS after a quantifier, a null treatment inside a call nested in FIRST_VALUE, and a method's name written
  without its parenthesis report the lines the account reports.
- **SYSTEM$TYPEOF of a difference of two dates** (live-verified): `NUMBER(9,0)[SB4]` beside a NULL date too, where it
  was `[SB1]`; a date difference is tagged by its declared width whatever the dates hold.
- **SHOW GRANTS OF ROLE over the built-in roles** (live-verified): PUBLIC lists no one, and the grants of the
  account's own role hierarchy name no grantor, where the user's implicit PUBLIC was listed and SYSADMIN named.
- **Which SQL function overload a text or a NULL runs among TIME and the timestamps** (live-verified): a text runs a
  TIMESTAMP_NTZ, a TIMESTAMP_LTZ or a NUMBER overload before a TIME one, and the timestamp kinds sort by their names,
  NTZ before TZ, where the TIME and the TZ overloads ran.
- **A text or a VARIANT given to a block's ARRAY variable** (live-verified): a text — assigned, as a DEFAULT, by a
  typed LET, as an expression such as `'x' || 'y'`, or selected INTO from a text column — is the array's one element,
  `["x"]`, and so is a VARIANT holding anything but an array, `[5]`, where both stayed as they were. A CALL's answer
  is still held as the CALL handed it back.
- **A scalar subquery whose derived column name holds a double quote, echoed in a refusal** (live-verified): `(SELECT
  '{"a": 1}')::OBJECT` echoes `(SELECT '{"a": 1}' AS "'{"A": 1}'" FROM (VALUES (NULL)) DUAL)`, where the echo printed
  an internal `(subquery …)` form.
- **A cast of an array with a missing element to a VECTOR** (live-verified): `Cannot convert an array with missing
  elements to a vector. Compact the array to a dense representation first using ARRAY_COMPACT.`, after a wrong length
  and before any element is judged, where the elements were reported as not real numbers; a JSON null is an element
  and is judged as one.
- **EXPLAIN of a window straight over a table scan** (live-verified): when the query's first window is a ROW_NUMBER, a
  RANK or a DENSE_RANK, or a LAG or a LEAD with no PARTITION BY, the TableScan lists its table twice, the columns that
  window partitions and orders by first, and GlobalStats counts its partitions twice; any other first window reads the
  scan once.
- **Arithmetic over a block's NULL DATE variable in SELECT … INTO, INSERT and UPDATE** (live-verified): the folded
  operation reads as the bare word NULL to what surrounds it, so `:d + 1 + 1` is a NUMBER(19,0) and `COALESCE(:d + 1,
  CURRENT_DATE())` a DATE, where both answered NULL[LOB].
- **SYSTEM$PIPE_FORCE_RESUME** (live-verified): a view over it is created, a missing pipe is refused as
  SYSTEM$PIPE_STATUS refuses it, an argument that is no text is refused as `argument needs to be a string: '1'` and a
  column as no constant, and a pipe that is there is resumed; it was an unknown function. `SYSTEM$ADD_EVENT` and
  `SYSTEM$SET_SPAN_ATTRIBUTES` written as calls are refused as `Unknown function SYSTEM$ADD_EVENT`, with no full stop,
  as the logging statements' names are.
- **A DECFLOAT literal and a PERCENTILE fraction in refusal texts** (live-verified): an expression printed in a
  refusal spells a DECFLOAT literal `decfloat '1'`, as in `Invalid expression [ARRAY_CONSTRUCT(decfloat '1')] in
  VALUES clause`, and a PERCENTILE_CONT or PERCENTILE_DISC fraction that is no constant is quoted through the cast the
  plan gives it: `CAST(CAST(0.1 AS FLOAT) AS NUMBER(18,0))` for a FLOAT, `CAST(decfloat '0.1' AS NUMBER(18,0))` for a
  DECFLOAT, `TO_NUMBER('0.1', 18, 1)` for a numeric text, while an exact NUMBER cast is quoted as written.
- **Nullability through derived relations and set operations** (live-verified): a column of a derived table, a CTE or
  a view whose item cannot be NULL is `{not null}` in a range refusal — `SUBSTR('abcdef', n)` over `(SELECT
  'inf'::FLOAT n)` — with a FROM or without; a set operation's column can be NULL where any arm's can, whatever the
  operator, and a BY NAME column always can. An INSERT … SELECT or a MERGE that writes such a value into a NOT NULL
  column prints the same nullability, and a JavaScript statement's `isColumnNullable` gives the same answer for a
  select list without a FROM, a set operation and a derived relation's column.
- **The snowpark shim's classes carry Snowpark's names and modules** (live-verified):
  `snowflake.snowpark.dataframe.DataFrame`,
  `snowflake.snowpark.relational_grouped_dataframe.RelationalGroupedDataFrame` (it was `GroupedFrame`), and the
  Session, Row, Column, DataFrameWriter and DataFrameReader (it was `_SessionReader`) classes each in its own module,
  importable by that name, so a DataFrame prints as `<snowflake.snowpark.dataframe.DataFrame object at 0x…>`;
  `snowflake.snowpark.__version__` is the account's default package version, `1.55.3`, where it was `1.30.0`.
- **RATIO_TO_REPORT over DECFLOAT values is a DECFLOAT** (live-verified): the value divided by the partition's sum as
  DECFLOAT divides, the sum added up to thirty-eight digits — `3.3333333333333333333333333333333333333e-1`, and a
  share of `1e-50` beside 1 is `1e-50` — and NULL for a NULL value or a sum of zero. It was typed NUMBER(38,6) and
  worked out in double arithmetic, so 1e-50 read as 0.000000.
- **A DECFLOAT is its own text wherever it becomes one**: bound through `OPEN … USING` or `EXECUTE IMMEDIATE … USING`
  (live-verified) it is `1.5e300` and `1e16384`, so a numeric context refuses `Numeric value '1.5e300' is not
  recognized`; a JavaScript procedure's ResultSet reads it by `getColumnValue` and `getColumnValueAsString` alike as
  that text, a string (live-verified), where getColumnValue gave a JavaScript number that lost its digits; and `COPY
  INTO @stage` unloads it as that text, as every number unloads as its cast to VARCHAR. Each wrote the plain digits,
  16385 of them for 1e16384.
- **GET_DDL and TAG_REFERENCES read a bare TIMESTAMP in a routine reference by the session's TIMESTAMP_TYPE_MAPPING**
  (live-verified), as DESCRIBE and DROP read it: under `TIMESTAMP_LTZ`, `GET_DDL('function', 'f(TIMESTAMP)')` returns
  the DDL of the function declared `f(x TIMESTAMP)` there, its parameter `"X" TIMESTAMP_LTZ(9)`, and
  `TAG_REFERENCES('f(TIMESTAMP)', 'function')` reads that function's tags, where both refused the reference as an
  object that does not exist.
- **getBytes reads a cell by its column's type in both JDBC drivers** (live-verified): a whole number as its
  two's-complement bytes (`[1, 44]` for 300), a FLOAT as its eight IEEE 754 bytes, a BOOLEAN as the one byte 1 or 0,
  and a text, a VARIANT, an OBJECT, an ARRAY or a VECTOR as the UTF-8 bytes of what getString reads, where a cell's
  text was decoded as hex or Base64 whenever it spelled either (`'abc'` read as two bytes, `TRUE` as three). A
  conversion refusal over a timestamp quotes the account's wire form — its seconds past the epoch to the column's
  scale, a TIMESTAMP_TZ's followed by its offset in minutes plus 1440 — and writes a TIMESTAMP_TZ's type `type:2,014`;
  a TIMESTAMP_NTZ(0) reads by getInt and getLong as its seconds.
- **A RECURSIVE view named by a quoted name recurses as the same name written plain does** (live-verified): `CREATE
  RECURSIVE VIEW "RVX"(n) AS … FROM "RVX" … UNION ALL SELECT 100` reads 1, 2 and 3 as `RVX` does, where it was refused
  `'RVX' is an invalid Recursive CTE`, and a name in lower or mixed case (`"rvq"`, `"Rv Q"`) recurses too, which a
  WITH RECURSIVE still refuses.
- **A Python routine's source runs as a module of its own** (live-verified), on GraalPy and on the CPython backend
  alike: named after the handler (`h_module`, `Gen_module`, `run_module`), its `__file__` `_udf_code.py` and
  `__cached__` None, registered on `sys.modules`, and its globals holding the module's own names (`__builtins__`,
  `__cached__`, `__doc__`, `__file__`, `__loader__`, `__name__`, `__package__`, `__spec__`) and none of the runtime's;
  an exception class it defines is reported qualified by it, `h_module.MyErr: boom`. It ran as `__main__`, beside the
  runtime's own names.
- **Creating a view or a SQL function over a sequence's NEXTVAL draws no value** (live-verified), nor does a query
  that reads no row of its FROM through such a view, a CTAS over one included: the first draw after them is the
  sequence's first, where each drew one. A view's column that is the bare NEXTVAL is described NOT NULL; `NEXTVAL + 0`
  stays nullable.
- **SYSTEM$TYPEOF of a number read back through RESULT_SCAN is tagged by its declared width** (live-verified):
  NUMBER(38,0)[SB16] for a column, an expression over one or a scalar subquery, correlated or not, where the scan was
  tagged by the values the query held (SB1 for small numbers); a literal's NUMBER(1,0) stays SB1. A correlated
  subquery over a common table expression of one row (`WITH t AS (SELECT 1 AS c) SELECT k, (SELECT c + o.k FROM t)
  FROM o`) is answered, as over a derived table, where it was refused `Unsupported subquery type cannot be evaluated`.
- **The array functions keep a structured ARRAY's type, and MAP_INSERT and MAP_CAT cast into a structured map's**
  (live-verified): ARRAY_SORT, ARRAY_DISTINCT, ARRAY_REMOVE, ARRAY_REMOVE_AT and ARRAY_REVERSE answer the array's own
  type, so `ARRAY_SORT([3,1,2]::ARRAY(INT))[0] + 1` is 2 where it was the FLOAT 2.0, and ARRAYS_ZIP over structured
  arrays answers `ARRAY(OBJECT($1 NUMBER(38,0), $2 VARCHAR))`. ARRAY_REMOVE takes a text out of an ARRAY(VARCHAR),
  where it was refused. MAP_INSERT casts its value to the map's value type: '5' into a NUMBER map is 5, 2.25 into
  NUMBER(3,1) is 2.3, 5 into a VARCHAR map is '5', and 'x' into a NUMBER map is refused `Numeric value 'x' is not
  recognized`. MAP_CAT casts its second map to the first's type, a FLOAT map's 1.5 joining a NUMBER map as 2.
- **A lateral table function of a query that reads no row of its FROM is called for none of its rows**
  (live-verified), as a table function that reads no column to its left already was not. Under a WHERE whose constant
  conjunct is FALSE or NULL (`WHERE 1 = 0`, `WHERE FALSE`, `WHERE NULL`, `WHERE fz.id = 5 AND 1 = 0`), a LIMIT 0 or an
  inner join on such a constant, `FROM fz, TABLE(FLATTEN(INPUT => PARSE_JSON('[' || fz.id))) f` answers no row where
  it failed `Error parsing JSON`, as does an argument that divides by zero or holds a correlated subquery; a name the
  arguments read that nothing answers to is still refused. `LATERAL FLATTEN(…)` written without TABLE reads no
  argument there either. A lateral FLATTEN whose arguments cannot be read with NULL inputs keeps its six columns, so a
  correlated subquery in its argument is refused `Unsupported subquery type cannot be evaluated` at the first row,
  where `f.value` was an invalid identifier.
- **A Python handler's failure is reported as the account reports it** (live-verified). The report is `Python
  Interpreter Error:`, then the traceback of the handler's own source as the file `_udf_code.py` (Python's caret lines
  included), then the exception and ` in function <NAME> with handler <h>`. This holds for functions, table functions,
  aggregates and procedures, at CREATE and at the call. A source that does not compile is reported by its line, text
  and caret, with no traceback. Before, the report read `Error executing Python function …: ValueError: …`.
- **A JavaScript table function's argumentInfo reports every argument's type and every constant's value**
  (live-verified). The types are named `text`, `real`, `boolean`, `binary`, `date`, `timestamp_ntz`, `timestamp_ltz`,
  `variant`, `array` and `object`. A constant's value is given as the handler reads it: a boolean as 1 or 0, a date or
  timestamp as its instant, a VARIANT as its JSON, a BINARY as its bytes. Before, only a FLOAT constant was reported,
  and only FLOAT arguments named a type.
- **SYSTEM$TYPEOF of a block variable holding NULL matches the account** (live-verified). A text declared as
  `VARCHAR(134217728)` in so many words reads `VARCHAR`, and a GEOGRAPHY or GEOMETRY variable reads `OBJECT`.
  `NULL::GEOGRAPHY` reads `VARIANT`. A date or time function, or date arithmetic, over the variable folds to an
  untyped NULL in a `SELECT … INTO` too, and in `DATE - :d`.
- **A Java handler's `Variant` made from a `BigDecimal` answers by the decimal's scale** (live-verified): at scale 0
  it is an INTEGER and at any other scale a DECIMAL, whole values included (`1E+3`, `1000.0`, `BigDecimal.valueOf(25,
  -1)`). Before, every whole value answered as an INTEGER.
- **CREATE OR REPLACE OR ALTER is refused with one syntax line** (live-verified), the line naming the second OR,
  whatever kind follows. Before, a second line named the token after the object's name.
- **CREATE OR ALTER of a running task with no children or finalizer redefines it in place** (live-verified). Before,
  it was refused as if the task were the root of a running graph.
- **The policy listings answer their rows by name** (live-verified): SHOW JOIN, MASKING, ROW ACCESS, AGGREGATION and
  PROJECTION POLICIES list a schema's policies character for character — a quoted lower-case name after every
  upper-case one, a renamed policy at its new name's place — where join, aggregation and projection policies came out
  in hash order.
- **A database or schema clone copies the stages the account copies** (live-verified): a named external stage always,
  a named internal one only under the new `INCLUDE INTERNAL STAGES`, a temporary stage never, where every stage was
  copied; `IGNORE HYBRID TABLES` (which now leaves the hybrid tables out) is taken in either order beside it, and
  `IGNORE TABLES WITH INSUFFICIENT DATA RETENTION` after a point of Time Travel, as the account takes them.
- **The JDBC drivers bind setObject as its target type and apply setMaxRows** (live-verified): a text type binds the
  value's text and a whole-number or DECIMAL type its number, from a String too, at the scale a DECIMAL names (a scale
  that would round is refused, SQLState 22003), and a result hands out no more rows than setMaxRows allows, where both
  were ignored.
- **A GENERATOR bound is refused with the account's folded plan** (live-verified): a number literal a FLOAT function
  converts echoes as the FLOAT it folds to — `SQRT(9.0)`, `(LN(1.0)) + 2.0`, `NORMAL(2.0, 0.0, RANDOM())` — a text
  cast to DATE as its date, LEN as LENGTH, and SIGN, LENGTH, HASH or a day difference cast to its NUMBER where
  arithmetic or ABS reads it, `(CAST(SIGN(-3) AS NUMBER(2,0))) + 3`, where the conversions printed as written.
- **A SQL procedure's or an anonymous block's semi-structured answer reads laid out through the drivers**
  (live-verified): at JSON_INDENT 0 a CALL or block returning an array or object reads `{\n"a": 1\n}` through the
  in-process driver and the HTTP wire, where a query's value stays compact; JavaScript and Python procedures stay
  compact. The in-process driver's getObject reads a VARIANT as getString does at any width, where it handed back the
  compact text.
- **CHARINDEX answers NULL for a NULL start under a collation, and GREATEST_IGNORE_NULLS and LEAST_IGNORE_NULLS keep
  the first of a signed-zero tie** (live-verified), as POSITION, GREATEST and LEAST do; the HTTP driver's prepared
  statement binds a NULL URL as NULL, where it threw a NullPointerException.
- **HEX_ENCODE holds its input to the width its type declares** (live-verified): a table function's BINARY(1) column
  holding two bytes is refused as `Binary value 'ABCD' is too long and would be truncated in 'HEX_ENCODE'`, through a
  derived table too, where it was encoded whole; the other functions read the value whole, as the account does.
- **A routine parameter's default that reads a query is echoed resolved** (live-verified): `invalid default argument
  expression (SELECT COUNT(*) AS "COUNT(*)" FROM TC AS TC WHERE TC.I > 0)`, the operators as written and a bare name
  its relation's, and a table it names that is not there is refused first as an object that does not exist.
- **A Python function's NaN or infinity given an exact NUMBER is refused as the account refuses it** (live-verified):
  `ValueError: cannot convert NaN to integer` and `OverflowError: cannot convert Infinity to integer`, whatever the
  scale, where a Java parser's sentence came through; a time with an offset is a TIME of its wall clock.
- **A JavaScript statement's isColumnNullable is true for the lookups that answer NULL** (live-verified):
  ARRAY_POSITION, ARRAY_MIN, ARRAY_MAX, ARRAY_SIZE, JSON_EXTRACT_PATH_TEXT, CHECK_XML and GETVARIABLE over columns
  that are not; a database-role grant that would close a loop is refused.
- **A HAVING aggregate over an expression around a per-group subquery is answered** (live-verified): `MAX((SELECT …) +
  1)`, `MAX(COALESCE((SELECT …), 0))`, a cast, ABS, NVL and IFF around it are computed over the rows the subquery
  answers for each group, so a group it answers none for reads as an empty aggregate, where each was refused as an
  unsupported subquery.
- **The nested-aggregate refusal of a HAVING subquery prints the casts the plan holds** (live-verified): a BOOLEAN
  cast to a number through NUMBER(2,0), the conversions arithmetic makes of its operands (FLOAT, aligned scales, a
  product's operands cast to their own types, SCALED_ROUND_INT_DIVIDE), and an aggregate of the outer row alone as
  `MAX(CORRELATION(MAX(…)))`, where such shapes were refused as unsupported subqueries.
- **SYSTEM$STREAM_BACKLOG is a table function** (live-verified): it lists the versions commits wrote of a stream's
  table past its offset, with the DML mode, the rows added and removed and the estimated change records, and refuses a
  name that is no stream; `AS_NUMBER(v, 0)` is typed NUMBER(0,0); a SYSTEM$REFERENCE token reads as its table or view
  in IDENTIFIER() and TABLE(), and a row change through a reference made without its privilege is refused.
- **EXPLAIN plans a sequence read as a Getnextval** (live-verified): `"getNextVal_SQ".NEXTVAL` in the Result over a
  Getnextval for each read and an empty ValuesClause where there is no FROM, and a TO_QUERY of a CALL reads an empty
  ValuesClause answering `P(<parameters>)`, where it failed with an internal error; a request whose SQL function,
  view, procedure or policy body reaches a write takes the server's exclusive lock.
- **A SQL function's query body whose row count is no count is refused at CREATE** (live-verified): `LIMIT -1` names
  `SELECT` and the token after the number, `FETCH FIRST 'x'` names the text alone, and a parameter where LIMIT, OFFSET
  or FETCH FIRST wants a count names `SELECT`, where each function was created.
- **A row written where a value goes is refused as the account refuses it** (live-verified): `LET x INT := (1, 2)` is
  `invalid type [CAST(ROW(1, 2) AS NUMBER(38,0))] for parameter 'TO_NUMBER'` (into a text, a DATE and a VARIANT
  alike), `FLATTEN((1, 2))` and `SPLIT_TO_TABLE('a,b', (1, 2))` name the row's type and its place, a bare star over
  GENERATOR is `SELECT with no columns` before the call's constant arguments are judged, a row among PIVOT's values is
  `Invalid argument types for function '='`, and `ORDER BY (b DESC, a)` and `SAMPLE ((1, 2))` report the account's
  second syntax line.
- **EXPLAIN of a query sorted with no row limit lists its scan as the account does** (live-verified): the table twice,
  the sort keys' columns before the columns read, and the partitions counted twice in GlobalStats; EXPLAIN DROP of a
  missing stage or file format is refused, and `CREATE TABLE … AS VALUES` names the VALUES alone.
- **A NOT over a HAVING aggregate of a per-group subquery is answered as the anti join it decorrelates into**
  (live-verified): `HAVING NOT (MAX((SELECT g.v FROM g WHERE g.id = fz.id)) > 0)` keeps the group the subquery finds
  nothing for, where it was refused as an unsupported subquery.
- **A query written in parentheses is read through them** (live-verified): `(SELECT a FROM tt) ORDER BY b` sorts by
  `b`, which the select reads but does not answer, where it was `invalid identifier 'B'`; a row limit written at
  another level applies to the sorted rows (`(SELECT a FROM tt LIMIT 2) ORDER BY b DESC` keeps the two greatest), and
  a clause two levels write is refused, `Duplicate ORDER BY: ORDER.`, `Duplicate LIMIT: LIMIT.` or `Duplicate LIMIT:
  FETCH.`, where the outer clause applied to the inner query's rows.
- **DESCRIBE RESULT reads an expression** (live-verified): `LAST_QUERY_ID(-2)`, a negated number, a session variable
  and parentheses around them name a statement, where any argument of LAST_QUERY_ID was a syntax error; a bare name is
  `Invalid result query ID, found 'x'`, NULL `argument needs to be a string: '1'` and any other call or operator
  `argument 1 to function <position> needs to be constant, found '<head>'`; a statement not found is `Statement <id>
  not found` and one that failed `Query <id> has no result because it failed`, where both were `No cached result for
  query id`.
- **QUERY_HISTORY lists a block's and a procedure's statements with their own cells** (live-verified): an INSERT a
  block runs carries the transaction it ran in, CLUSTER_NUMBER 1, the warehouse's size, the rows of its table and the
  rows it inserted, where those cells were left at their defaults; a CALL or a block itself runs in no transaction of
  its own outside an explicit one (TRANSACTION_ID 0).
- **QUERY_HISTORY lists the queries the statistics answer without a warehouse** (live-verified): `SELECT COUNT(*) FROM
  t`, MIN and MAX, `COUNT(*) + 1`, a COUNT under a WHERE the statistics prove true, over a view as over a table, and a
  scan whose WHERE they rule out (`SELECT a FROM t WHERE a > 100`) list CLUSTER_NUMBER and WAREHOUSE_SIZE NULL and
  ROWS_PRODUCED 0; a write inside an explicit transaction produces the rows of its own table, where every table the
  transaction had written was counted; the two QUERY_ACCELERATION cells that read NULL read 0.
- **A prepared statement binds a double or a float as a FLOAT** (live-verified): `SYSTEM$TYPEOF(?)` over
  `setDouble(0.1)` is `FLOAT[DOUBLE]`, `setDouble(1.255)` written into a `NUMBER(10,2)` column is 1.25 and
  `setDouble(0.1)` into a DECFLOAT column 0.100000000000000006, where the value was written as a NUMBER; a FLOAT bound
  as a LIMIT or an OFFSET is `Invalid row count '?' in limit clause`, and a `setBigDecimal` whose fraction takes it
  past 38 digits is refused, `Invalid intermediate datatype: NUMBER(51,50).`, where it was read as a FLOAT. Both
  drivers report a refused bind as an SQLException.
- **A CALL written in parentheses as a declared variable's value is refused with the account's lines**
  (live-verified): `LET x VARCHAR := (CALL pn(:d))` names `pn` and then the `)` after `:d`, `(CALL pn(:d, 1))` the `,`
  and `(CALL pn(TO_DATE(:d)))` the `(` after `TO_DATE`; arguments holding no name, as in `(CALL pn(1))`, name `pn`
  alone, and a DEFAULT reads the same.
- **A session variable set to a DECFLOAT keeps its type** (live-verified): after `SET v = 1.5::DECFLOAT`,
  `SYSTEM$TYPEOF($v)` is `DECFLOAT(38)[DECFLOAT18]` and `$v + 1` stays a DECFLOAT, `SET w = (SELECT 1::DECFLOAT / 3)`
  reads back as `3.3333333333333333333333333333333333333e-1`, and SHOW VARIABLES lists the type as `decfloat`, where
  the variable was typed by its value as a NUMBER.
- **A DECFLOAT argument to a SQL function's NUMBER parameter converts to the parameter's type** (live-verified): over
  `f(x NUMBER)` whose body is `x + 1`, `f(1.5::DECFLOAT)` is `3`, `f(2.5::DECFLOAT)` `4` and `f(-1.5::DECFLOAT)` `-1`,
  and `SYSTEM$TYPEOF` reads `NUMBER(38,0)[SB1]`, over `TO_DECFLOAT('2.5')` too; over `NUMBER(10,2)`, `1.257::DECFLOAT`
  is `1.26`. The DECFLOAT used to reach the body unconverted.
- **A SQL table function refuses a DECFLOAT argument for a NUMBER parameter** (live-verified):
  `TABLE(t(1.5::DECFLOAT))` over `t(x NUMBER)` is `Invalid argument types for function 'T': (DECFLOAT(38))`, where it
  was accepted.
- **A DESCRIBE's list of property lists whose last property follows a comma is refused with the account's lines**
  (live-verified): `x = ((a = 1,))` names the `)` where a name is due and then the end of the text, and `x = ((a =
  1,), (b = 2))` the `)` and the `(` after the comma.
- **A task whose query ends with an aliased FROM item and a COMMENT refuses the COMMENT** (live-verified): `… AS
  SELECT 1 FROM t x COMMENT = 'c'` names the COMMENT, where the `=` was named.
- **An alert's query condition with a word after its parenthesis refuses the word alone** (live-verified): `IF (EXISTS
  (SELECT 1) x)) THEN …` names the `x`, where a `)` was named too.
- **A text literal of more than 38 decimals beside a NUMBER is refused while the statement compiles** (live-verified):
  compared, added or listed beside a NUMBER, `'1e-39'` is `Invalid intermediate datatype: NUMBER(40,39).` and
  `'1e-308'` `NUMBER(309,308)`, where the comparison answered rows; `'1e-38'` is read as before.
- **A SYSTEM$ call inside TABLE( … ) is an unknown table function** (live-verified): `TABLE(SYSTEM$TYPEOF(1))` is
  `Unknown table function SYSTEM$TYPEOF`, after the arguments' names are judged, beside a relation, under a join or
  with an alias, where it was a syntax error.
- **A block's call refused for its quantifier echoes its arithmetic** (live-verified): `ABS(ALL n + 1)` over an INT
  holding 5 echoes `ABS(ALL (CAST('5' AS NUMBER(38,0))) + 1)`, `-n` echoes `NEGATE(CAST('5' AS NUMBER(38,0)))` and
  `UPPER(DISTINCT s || 'b')` echoes `UPPER(DISTINCT 'a' || 'b')`, where the value was echoed folded.
- **An UPDATE whose value's query reads a stream fails without the column envelope** (live-verified): `UPDATE sink SET
  id = 1/(SELECT MIN(id) FROM sts)` over a stream holding a 0 is `Division by zero`, in a transaction or not, where
  the same value over a table keeps `DML operation to table SINK failed on column ID with error: …`.
- **DESCRIBE NETWORK RULE of a missing rule names the one privilege the account names** (live-verified): the hint asks
  for `APPLY MASKING POLICY` on the account, where it listed thirteen privileges.
- **Arithmetic over MAX, MIN or ANY_VALUE of NULL is typed as over the word NULL** (live-verified): `MAX(NULL) + 1` is
  `NUMBER(19,0)`, `ANY_VALUE(NULL) * 1.25` `NUMBER(21,2)`, `-MIN(NULL)` `NUMBER(18,0)` and `COALESCE(MAX(NULL), 1)`
  `NUMBER(1,0)`, where the result was untyped and described as `VARCHAR`.
- **SYSTEM$GET_TAG is judged ahead of a query's names and of the kind of any windowed call** (live-verified): its
  count, its constant and text arguments, its domain and its tag are refused before an invalid identifier written
  ahead of it and before `Invalid function type [ABS] for window function.`, its own OVER included, and a fourth
  argument that is no text is `argument needs to be a string: 'SYSTEM$GET_TAG'`.
- **A table procedure called after LATERAL or joined with ON judges its arguments first** (live-verified): `t1,
  LATERAL p(t1.c)` and `t1 JOIN TABLE(p(t1.c)) ON TRUE` are `Invalid argument T1.C for procedure in FROM clause` where
  the column stands, after the argument types, where the first was an unknown table function and the second the
  lateral restriction of a join predicate; `LATERAL p('x')` runs the procedure as `TABLE(p('x'))` does.
- **A star over a chain of USING joins echoes one SYS_VW view, and a USING column two relations to its left both show
  is ambiguous** (live-verified): `fa JOIN fb USING (id) JOIN fc USING (id)` echoes `SYS_VW.ID, SYS_VW.A, SYS_VW.B,
  SYS_VW.C`, and `f JOIN g USING (id) JOIN h USING (b)` is `ambiguous column name 'B'`; where the account leaves out
  of a chain's star the columns an earlier USING join renamed, every column is kept.
- **A procedure's quoted parameter keeps its case** (live-verified): `"low"` reads it, `low` and `"LOW"` are invalid
  identifiers, and `"low" => 1` names it.
- **More syntax-error lines read as the account's** (live-verified): a TABLE query with an alias inside an IN, ANY,
  ALL or scalar bracket refuses the TABLE and then the token after the first part of its name; a model's method named
  inside `TABLE( … )` without its arguments refuses the token after the parenthesis closing the TABLE too; and an
  ILIKE ALL bracket that opens with a bracket refuses the parenthesis closing the inner one.
- **EXPLAIN of a recursive CTE plans it as the account does** (live-verified): a `RecursiveWithClause` named by the
  CTE over its anchor — a `ValuesClause ()` where the anchor has no FROM — and its recursive term, whose operator
  feeds the clause back as one of its parents (`[0, 3]`), the CTE's columns read as `R.N`; no `TableScan` of a table
  of the CTE's name is listed any more, nor counted in GlobalStats, whether such a table exists or not.
- **A stage's COPY_OPTIONS list is refused with the account's lines** (live-verified): a list of bare keys refuses its
  `=` and its closing parenthesis, `(PURGE NOPE)` names `'NOPE'` twice and the parenthesis, and a `%` in a value is
  named twice.
- **CREATE OR ALTER MASKING POLICY with SECURE or IF NOT EXISTS is refused with the account's lines** (live-verified):
  `CREATE OR ALTER SECURE MASKING POLICY …` names the MASKING alone, and `… IF NOT EXISTS m AS (v VARCHAR) …` names
  the NOT, the AS, the VARCHAR and the signature's parenthesis.
- **TO_BINARY and TRY_TO_BINARY given a NULL format answer NULL** (live-verified): `TO_BINARY('4869', NULL)` is NULL,
  where the text was read as hex; `TO_BINARY(NULL, NULL)` is refused as the account refuses it, `too many arguments
  for function [TO_BINARY(NULL, NULL)] expected 1, got 2`.
- **A recursive term that reads its CTE on the NULL-supplying side of an outer join is refused** (live-verified): `…
  FROM e LEFT JOIN r ON …` in a recursive CTE `r`, `r RIGHT JOIN e` and a FULL JOIN are refused with `OUTER JOINs with
  a self reference are not allowed in a recursive CTE.`, where they answered; the preserved side may still read the
  CTE.
- **A CHANGES clause on a CTE's name is refused** (live-verified): `WITH t AS (…) SELECT * FROM t CHANGES(INFORMATION
  => DEFAULT) AT(…)` is refused for change tracking on an internal view, `Change tracking is not enabled or has been
  missing for the time range requested on table 'SYS$_VIEW_…'`, where the CTE was read; an AT or BEFORE clause on a
  CTE's name is passed over, as on the account.
- **A CONNECT BY list of several conditions is refused at its first comma only** (live-verified): the account takes
  one condition, and `CONNECT BY a = PRIOR b, c = PRIOR d, PRIOR e < e` names the first comma and nothing after it,
  where the second comma was named too.
- **A statement run on a warehouse is timed by the lowest non-zero of the session's and the warehouse's
  STATEMENT_TIMEOUT_IN_SECONDS** (documented): `ALTER WAREHOUSE … SET STATEMENT_TIMEOUT_IN_SECONDS = 1` ends a
  statement after a second whatever the session allows, refused with `Statement reached its statement or warehouse
  timeout of 1 second(s) and was canceled.`, where the warehouse's value was stored and not enforced; a warehouse's 0
  leaves the session's.
- **A Scala routine's PACKAGES are judged against the account's Scala package list as it is created** (live-verified):
  an unknown package, an unknown version (`com.snowflake:snowpark_2.12:1.14.0`) and a name without a version are
  refused with `Package '…' is not supported.`, as a Java routine's are, where the routine was created; snowpark,
  snowpark_2.12 and snowpark_2.13 at `latest` or a listed version are taken.
- **A named window is refused with the account's lines** (live-verified): `SELECT SUM(v) OVER w FROM ev WINDOW w AS
  (ORDER BY v)` names every `w` an OVER reads, `OVER (w)` included, then the WINDOW clause's `w` and the token after
  its ORDER BY's first item, or the WINDOW itself after a WHERE, where only the first `w` was named.
- **A window frame's offset names more of the account's lines** (live-verified): a bind as the offset, `ROWS BETWEEN
  :k PRECEDING …`, names the colon and the PRECEDING, and in a block one token more — the AND in a statement of the
  block, the token after the FROM's name in a query `LET … := (…)` assigns; an offset in a QUALIFY's window names
  nothing after itself; `ROWS BETWEEN v v PRECEDING` names the PRECEDING.
- **A JavaScript procedure's `snowflake.execute` binds its `binds`**: `snowflake.execute({sqlText: 'SELECT ? + ?',
  binds: [40, 2]})` answers 42 (documented), where the binds were ignored and the statement failed with `Bind variable
  ? not set`; a `binds` that is no array, a number or an object, is refused with `Invalid binds argument` (100183 /
  P0000), as createStatement refuses it (live-verified).
- **A JavaScript procedure's `getColumnValue` over a VECTOR column answers the vector's text** (live-verified):
  `[1,2]`, and `[1.500000,2.000000]` for a FLOAT vector, as `getColumnValueAsString` does, where an array of numbers
  was answered.
- **A Python handler's semi-structured argument holding a DATE, a TIME, a timestamp or a BINARY arrives as the text
  the VARIANT shows** (live-verified): `TO_VARIANT('2024-01-02'::DATE)` is the str `'2024-01-02'`, a TIMESTAMP_NTZ
  `'2024-01-02 10:11:12.000'`, a TIMESTAMP_TZ `'2024-01-02 10:11:12.000 +0100'`, a TIME `'10:11:12'` and a BINARY its
  upper-case hex, as the members of an ARRAY or an OBJECT already arrived, where a `datetime.date`,
  `datetime.datetime`, `datetime.time` or `bytes` was handed over; on both Python backends.
- **A subquery that calls a JavaScript function not declared IMMUTABLE is evaluated again for each outer row, the
  function's state kept** (live-verified): over a counter, `SELECT v FROM ct WHERE v IN (SELECT cnt(1))` answers every
  row of three and `SELECT v, (SELECT cnt(1)) FROM ct` counts 1, 2, 3, where the subquery was evaluated once; an
  IMMUTABLE function's and a Python function's call are still evaluated once.
- **A handler function's call refused in a VALUES clause prints as the plan types it** (live-verified):
  `EXTENSION_FUNC_P` for a function declared IMMUTABLE; an argument of constants folded, `cnt(1 + 1)` as
  `EXTENSION_FUNC_V(2.0)`; in FLOAT arithmetic every constant the FLOAT it becomes, folded — `cnt(1) + 1` as
  `(EXTENSION_FUNC_V(1.0)) + 1.0`, `cnt(1) - (1 + 1)` as `… - 2.0` — and an exact call cast,
  `(CAST(EXTENSION_FUNC_V(1) AS FLOAT))`; and a whole call added to or taken from a decimal cast to its scale,
  `(CAST(EXTENSION_FUNC_V(1) AS NUMBER(38,2))) + 1.25`.
- **A JavaScript handler's answer past a declared BINARY(n) is refused in lower-case hex** (live-verified): `Binary
  value '80ff' is too long and would be truncated`, where upper case was written; a column's bytes keep upper case.
- **A Python handler's dict answer keyed by a tuple or another object is keyed by the key's text** (live-verified):
  `{(1, 2): 'x'}` answers `{"(1, 2)":"x"}` on GraalPy as on CPython, where the call was refused with json's `keys must
  be str, int, float, bool or None`.
- **A plain star over a CALL's scan with an alias hands out the answer as without one** (live-verified): `SELECT *
  FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) r`, and `AS r`, over a Java procedure's answer wider than its RETURNS
  VARCHAR(2) answer `abcdef`, where the read was refused; an alias's star `r.*` is still refused.
- **A Java, Python or Scala procedure at the seventeenth level is ended before its code runs** (live-verified): the
  session such a handler is handed issues a statement as it opens, so sixteen procedures calling one more that issues
  nothing are refused with `Stored procedure exceeds the maximum allowed number of recursive calls.`, worded once more
  by every body the refusal leaves, where the seventeenth body answered; a SQL or JavaScript body at that depth still
  runs until it issues a statement.
- **Snowpark's `DataFrame.schema()` names a GEOMETRY column `GeometryType` and a GEOGRAPHY one `GeographyType`**
  (live-verified): both columns were named `Variant`, because the geospatial types carry the semi-structured category;
  and a struct field prints either by its whole class name, `StructField(G, GeometryType, Nullable = true)`.
- **A SQL function's answer over an untyped NULL argument keeps the parameter's declared shape** (live-verified): over
  a parameter declared without a length, `SYSTEM$TYPEOF(wb(NULL))` is `BINARY[LOB]` and `wv(NULL)` `VARCHAR[LOB]`,
  where the catalog's default widths were named; a sized VARCHAR's NULL is `VARCHAR(134217728)`, and a sized BINARY, a
  number and a typed NULL keep theirs.
- **A Snowscript UDF's body read as an expression is refused at the call's parenthesis after an IN list, and at the
  second IS after an IS DISTINCT FROM test whose operand is more than one token** (live-verified): `RETURN
  nosuch(a.b.c IN (1, 2));` adds the call's `)` after the RETURN and the `a`, and `RETURN nosuch(a IS DISTINCT FROM 1
  + 1 IS NULL);` is refused at the second IS and nothing after it.
- **The `Unsupported expression for Snowscript UDF` refusal prints an aggregate over a frame of the whole partition as
  `OVER ()`** (live-verified): `SUM(a)` and `MAX(a) OVER (ORDER BY a ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED
  FOLLOWING)` echo as `SUM(A) OVER ()` and `MAX(A) OVER ()`, as AVG's did; LAST_VALUE keeps its window.
- **A Python handler's failure carries the code 100357 / P0000** (live-verified): a CALL that fails with a Python
  procedure's uncaught exception, a depth refusal under Python bodies included, records ERROR_CODE 100357 in its
  history row and reads it as SQLCODE in a block's handler, where 100351, or the depth refusal's 90235, was read.
- **LPAD's and RPAD's argument-type refusal names an unsized BINARY at the 64MB maximum** (live-verified):
  `LPAD(TO_BINARY('AB', 'HEX'), 3)` is `Invalid argument types for function 'LPAD': (BINARY(67108864), NUMBER(1,0))`,
  with two arguments or three and with the BINARY as the pad, as the other argument-type sentences name it, where the
  engine's held width `BINARY(8388608)` was named; a BINARY(4) keeps its width.
- **A block's statement that opens with a call and is no whole call statement is refused as the account reads it**
  (live-verified): `BEGIN ABS(1) + 1; …` is refused at the `+`, `BEGIN SYSTEM$WAIT(0) RETURN 1; …` at the RETURN and
  the token after it, and the statements after a whole call statement are read on, `BEGIN ABS(1); RETURN 1 END;` at
  the END; a quoted name, LEFT, RIGHT, IDENTIFIER, CAST, TRY_CAST and a path opening a statement are refused at the
  word, where the call's name or parenthesis was named.
- **A session SYSTEM$ABORT_SESSION ended leaves no transaction behind**: the aborted session's request is answered
  once the session has ended, its transaction rolled back and its locks released, and ending a session rolls back
  every transaction still open under its number, so a table its aborted transaction wrote is free for the statements
  after the answer.
- **A parameter of any level set on a schema or a database where it does not belong is refused for want of access**
  (live-verified): ALTER SCHEMA … SET or UNSET of a database's, a warehouse's, a user's or a session's parameter,
  `ENABLE_HORIZON_IRC_UNMANAGED_CREDENTIAL_VENDING`, `FALLBACK_WAREHOUSE` or `TELEMETRY_ENABLE_INTERACTIVE_MODE`, is
  `Insufficient privileges to operate on schema 'TEST_SCHEMA'.` as an account parameter is, where it was an invalid
  property; `ENABLE_ICEBERG_MANAGED_TABLE_REPLICATION` is no parameter at all, so ALTER DATABASE refuses it as an
  invalid property where it was taken.
- **A block's LET with an unknown word for its type is refused for the type** (live-verified): `BEGIN LET q r; RETURN
  q; END` is `Unsupported data type 'R'.`, in a block run on its own and for a word of one or two letters too, where a
  syntax error at the next statement was reported; a LET that declares a type and no value run into a RETURN or a LET
  is refused at that word and at the token after it.
- **A pipe's COPY is read as a COPY statement when the pipe is created**: a fault in it refuses the pipe at its place
  in the statement, `CREATE PIPE pp AS COPY INTO ct FROM @cs FILE_FORMAT = (, TYPE = CSV)` at the comma and the list's
  closing parenthesis as live refuses it, where the pipe was created.
- **The syntax-error lines of a comma at either end of a parenthesized list follow the account's** (live-verified): an
  external table's `FILE_FORMAT = (, TYPE = CSV)` names the comma and the closing parenthesis, where the statement was
  refused at its kind word, a trailing comma in a list of options or of values names the closing parenthesis alone,
  and a view's column list followed by a comma is refused at the list's first name.
- **Privileges on routine overloads that differ by a structured parameter's shape are kept apart**: `f1(x OBJECT(a
  INT))` and `f1(x OBJECT(b INT))` each record their privileges under their own shape, named so in SHOW GRANTS, so a
  DROP or a REVOKE of one leaves the other's, where both shared one record.
- **ALTER TABLE ADD COLUMN echoes a DEFAULT that moves a date or a timestamp by a quoted interval as the plan holds
  it** (live-verified): `CURRENT_DATE + INTERVAL '1 DAY'` is `Invalid column default expression
  [DATE_ADDDAYSTODATE(CAST(1.0 AS NUMBER(9,0)), CURRENT_DATE())]`, one shift per part of the interval in the order
  written, an hour first converting the date to `TIMESTAMP_NTZ(9)` and a subtraction negating each amount, where the
  interval call was printed; an interval written first, `INTERVAL '1 DAY' + CURRENT_DATE`, is refused by its argument
  types at the operator, `(INTERVAL, DATE)`, while the statement compiles.
- **A called SQL function's GET_DDL over a call argument that reads a column is refused while the statement compiles**
  (live-verified): with `gdp(t) AS 'GET_DDL(''table'', t)'`, `SELECT SYSTEM$TYPEOF(gdp(x::VARCHAR)) FROM gtt` is
  `Invalid value [CAST(GTT.X AS VARCHAR(134217728))] for function '2', parameter EXPORT_DDL: constant arguments
  expected`, numbered by the place GET_DDL reads the argument in as for a direct call, where the call was typed, or
  run with each row's value.
- **The syntax-error lines of a CLUSTER BY on a view follow the account's** (live-verified): `ALTER VIEW v CLUSTER BY
  (x)` is refused at the CLUSTER alone, `CREATE VIEW v CLUSTER BY (x) AS SELECT …` at the BY alone and, with a key
  written bare, at the AS as well, and `CREATE MATERIALIZED VIEW mv CLUSTER BY (x) COPY GRANTS` at the GRANTS and at
  the end of the input.
- **The syntax-error lines of a PIVOT that lists more than one aggregate follow the account's** (live-verified): after
  the comma that ends the first aggregate, the bracket of a call that follows, a token after a comma that is no word
  and the FOR column's first token are each refused, so `PIVOT (SUM(amt), COUNT(amt) FOR mon IN ('JAN'))` is refused
  at the comma, at COUNT's bracket and at `mon`, where the recovery read on to other tokens or stopped early.
- **A QUALIFY window partitioned by one subquery that reads the outer row is refused as the plan refuses it**
  (live-verified): an aggregate window with no ORDER BY whose one PARTITION BY key is such a subquery is `[CAST(R1.V
  AS NUMBER(38,0))] is not a valid group by expression` for `SUM(v)`, the argument bare for COUNT, MAX and MIN and
  converted twice for AVG, and a `COUNT(*)` is an unsupported subquery at the subquery, where the window was answered;
  a second key, an ORDER BY or a key that reads nothing of the outer row is answered as before.
- **Over no rows, a select list's subquery in a CASE branch or a choosing call raises its fault** (live-verified):
  `SELECT i, CASE WHEN i > 0 THEN (SELECT i FROM g) END FROM empty` and the same subquery in COALESCE, IFF or NVL are
  `Single-row subquery returns more than one row.`, over a table with no rows and under a WHERE that keeps none, where
  they answered no row; where rows reach the projection and none takes the branch, and in a grouped query a HAVING
  leaves no group of, the subquery is still left uncomputed.
- **A staged CSV file's refusal names the record's own line under a FIELD_DELIMITER of several characters**
  (live-verified): a closing enclosure followed by text in the third record of a file whose records end at line breaks
  is refused at `line 1, character 4`, as the account counts there, where the file's line 3 was named; a delimiter of
  one character still names the file's line, and a COPY load reports the same place.
- **A staged XML file reads in Snowflake's XML model** (live-verified): a query or a COPY reads every top-level
  element as a record in the model PARSE_XML builds, printing as XML (`<root><a>café</a></root>`), TYPEOF XML and read
  by XMLGET, where it built an object keyed by the root element's name; several top-level elements are several
  records, STRIP_OUTER_ELEMENT reads the elements those hold, DISABLE_AUTO_CONVERT keeps text as text, PRESERVE_SPACE
  keeps a text's spaces and marks its element `xml:space="preserve"`, and the XML declaration and comments are no
  records.
- **A staged XML or Avro file holding a byte that is no UTF-8 is refused in the account's words, or read with U+FFFD**
  (live-verified): `Error parsing XML: invalid UTF-8 sequence: 0xE9` with the file, the line and the character, and
  for Avro `Error parsing AVRO: error parsing AVRO data: Invalid UTF8 detected while decoding 'caf0xE9'`, where both
  read U+FFFD silently; REPLACE_INVALID_CHARACTERS = TRUE, and IGNORE_UTF8_ERRORS = TRUE, its other spelling, read
  each such byte as U+FFFD.
- **A COPY of a record file that cannot be read at all is one error of the file**: under ON_ERROR = CONTINUE or
  SKIP_FILE such a file is reported LOAD_FAILED, the reader's refusal its first error, and the next file loads, where
  the statement failed whatever ON_ERROR said; the default still aborts the statement.
- **A CHANGES clause reads a dynamic table's changes** (live-verified): the rows its refreshes added and removed
  between two points are INSERTs and DELETEs, where the clause was refused as unsupported; APPEND_ONLY is refused
  (`Change tracking of type APPEND_ONLY is not supported on queries with 'DYNAMIC_TABLE' inside views, …`), and so is
  a point CURRENT_TIMESTAMP names; creating a dynamic table turns change tracking on for the tables its query reads,
  so a CHANGES clause over its base table answers.
- **LOAD_HISTORY, COPY_HISTORY and the query IDs a time-travel point names outlive a restart**: a snapshot (a
  checkpoint, a saved state, an engine clone) keeps the files each COPY loaded and the statements a STATEMENT point
  may name, and with the write-ahead log on a file beside the log keeps the points of the statements the log holds no
  record of, a query or a SHOW, so `AT(STATEMENT => '<a SELECT's id>')` reads where it did after a restart.
- **A structured column refuses a semi-structured value before a row is read, and a VALUES item is echoed as
  compiled** (live-verified): `INSERT INTO t SELECT ARRAY_CONSTRUCT('zz'::NUMBER) FROM t` into an ARRAY(NUMBER(10,2))
  column is `incompatible types: [ARRAY] and [ARRAY(NUMBER(10,2))]` with rows in t, where the text's own refusal came
  first; `Invalid expression [...] in VALUES clause` prints the expression the statement compiled: `[1, 2]` as
  `ARRAY_CONSTRUCT(1, 2)`, `{'a': 1}` as `OBJECT_CONSTRUCT('a', 1)`, a `::` as `CAST(x AS <type in full>)`, and a text
  appended to a structured NUMBER array as `TO_NUMBER('zz', 10, 2)`.
- **A cast out of range over a recursive CTE's own column inside its term names the widest exact number**
  (live-verified): in `WITH RECURSIVE r (n) AS (SELECT 5 UNION ALL SELECT (n * 3)::NUMBER(1,0) FROM r WHERE n < 9) …`
  the refusal reads `type FIXED[SB16](1,0){not null}, value 15`, the value printed at the target's scale (`value 15.0`
  for NUMBER(2,1)), where it named the width of the term's own arithmetic (`FIXED[SB1]`); a cast in the anchor or over
  a joined table's column keeps its source's tag.
- **A CALL's named argument takes NOT, AND and OR** (live-verified): `CALL p(x => NOT TRUE)`, `x => TRUE AND FALSE`,
  `x => FALSE OR TRUE` and the parenthesized `x => (NOT TRUE)` bind the value they compute to the parameter they name,
  where the words were a syntax error; a comparison, BETWEEN and IN bind as before.
- **A WAL checkpoint waits for every session's open transaction**: a checkpoint, explicit or automatic, taken while
  another session's transaction holds logged statements is put off until no transaction holds any, so recovery no
  longer replays such a statement at the checkpoint, where an `INSERT … SELECT` inside the transaction read what
  another session committed after it and recovered the newer value.
- **A materialized view's and a dynamic table's clustering key is kept as written, and ALTER DYNAMIC TABLE sets it**
  (live-verified): SHOW MATERIALIZED VIEWS, SHOW DYNAMIC TABLES, SHOW TABLES and GET_DDL print the text written after
  CLUSTER BY, its spacing and casing kept (`LINEAR(LOWER( b ),  a)`), where they printed the keys rejoined; `ALTER
  DYNAMIC TABLE … CLUSTER BY (…)` and `… DROP CLUSTERING KEY` run, and a column action the statement does not take is
  refused at its word.
- **SYSTEM$TYPEOF tags a FROM-less SUM and AVG by their accumulators, and LAST_QUERY_ID echoes DUAL's column as the
  plan does** (live-verified): `SUM(NULL)`, `SUM(NULL::INT)` and `SUM(1)` with no FROM are SB8 and `AVG(NULL)`,
  `AVG(1)` SB16, where over a table the values still decide; a column written `$1` is echoed as written (`DUAL.$1`,
  `VALUES.$1` under FROM DUAL), and a conversion of DUAL's untyped NULL column to a number as
  `SYSTEM$NULL_TO_FIXED(DUAL.$1)` or, for TO_DOUBLE, `SYSTEM$NULL_TO_REAL(DUAL.$1)`.
- **A PIVOT value naming the relation's columns pivots each row on its own value** (live-verified): `FOR id IN (id)`
  gives every row its id in a column named `ID`, `IN (id + 1)` none, and `IN (id, 2)` puts the row whose id is 2 in
  both columns, where such a value was refused as an invalid identifier.
- **INFORMATION_SCHEMA lists its own views' columns** (live-verified): `SHOW COLUMNS IN VIEW
  <db>.INFORMATION_SCHEMA.<view>` and `INFORMATION_SCHEMA.COLUMNS` list every column of the schema's own views with
  its type, nullability and description, of kind `VIEW_COLUMN` in SHOW COLUMNS, where they listed none.
- **A dynamic table whose REFRESH_MODE AUTO settles on FULL says why** (live-verified): the CREATE's status ends `FULL
  refresh mode was selected because: <reason>` and SHOW DYNAMIC TABLES answers `refresh_mode` FULL with the reason in
  `refresh_mode_reason` — for a subquery, a view's FROM-less row generator, a window without PARTITION BY, a
  non-deterministic function such as CURRENT_TIMESTAMP or RANDOM, and a LIMIT; every other query stays INCREMENTAL.
- **An Iceberg table holds FLOAT members and column-list CTAS values to the format, and takes LONG as a column type**
  (live-verified): a FLOAT member of a structured OBJECT, ARRAY or MAP is held to 32 bits, `CREATE ICEBERG TABLE …
  (column list) AS SELECT` refuses a value past the format's int or long and rounds a FLOAT, and `LONG` — the word
  GET_DDL prints — is the format's long, NUMBER(19,0), in an Iceberg table's column definitions, structured members,
  ADD COLUMN and SET DATA TYPE; another table's column refuses it as `Unsupported data type 'long'` as written, and
  any other type position, a cast or a routine's argument, as a syntax error at the word.
- **An aggregate over the outer row in a derived table or a scalar subquery answers as the account does**
  (live-verified): a derived table's MIN or MAX of a view's column — renamed, filtered or nested — reads its table's
  whole extreme as a stored table's does; a derived table's COUNT of the outer column is its own rows less the NULLs
  the column holds; the nested-aggregate refusal prints a view's column as its table's, a COUNT cast to NUMBER(18,0)
  and an AVG as the sum it divides — by that count, or by the column's COUNT under a filter — for whole-number,
  fixed-point and FLOAT columns; and an outer aggregate beside the scalar subquery's own column is refused as
  `Unsupported subquery type cannot be evaluated`, not as that column's grouping.
- **A column DEFAULT that reads the clock is printed as the account prints it** (live-verified): DESCRIBE, SHOW COLUMNS,
  GET_DDL and INFORMATION_SCHEMA.COLUMNS print `CURRENT_TIMESTAMP()` for CURRENT_TIMESTAMP however written — and so
  CURRENT_DATE(), CURRENT_TIME(), LOCALTIMESTAMP(), LOCALTIME() — keep a precision (`CURRENT_TIMESTAMP(3)`), print
  GETDATE() and SYSTIMESTAMP() as `CURRENT_TIMESTAMP()` and SYSDATE() as its expansion.
- **A time-zone phrase a data type cannot take reports the account's lines** (live-verified): a cast's complete type
  followed by WITH or WITHOUT tells the word after the phrase's next word too, a TIMESTAMP whose words break off tells
  the word that opened them, a column's type followed by WITH tells the word after it alone, and a COMMENT target of two
  words (`COMMENT ON ICEBERG TABLES …`) tells its second word alone.
- **A statement refused for another session's table lock names its own query ID, and an interrupted wait ends as a
  cancel** (live-verified for the ID): the refusal's "Your statement '<id>'" is the ID LAST_QUERY_ID answers once it is
  refused, where it was a random one, and a waiting statement whose thread is interrupted ends with `SQL execution
  canceled` rather than as its LOCK_TIMEOUT running out.
- **The JDBC Calendar getters and setDate count a day before 1582 as java.sql and the account's driver do**
  (live-verified): with a Calendar of the JVM's zone, `getDate`/`getTimestamp` read DATE 1500-01-01 and TIMESTAMP_NTZ
  1500-01-01 10:00:00 as without one, and `setDate` binds 1500-01-01 unshifted, where they moved the day by the
  Julian/Gregorian gap; `setTimestamp` with a Calendar keeps the account's driver's java.time count.
- **SYSTEM$TYPEOF of an earlier select item's alias in a query over a table names the aliased expression's type and
  storage** (live-verified): `SELECT UPPER(s) AS x, SYSTEM$TYPEOF(x) FROM t` answers `VARCHAR(50331648)[LOB]` and
  `SELECT n AS z, SYSTEM$TYPEOF(z + 1) FROM t` `NUMBER(38,0)[SB1]`, where both were `NULL[LOB]`; a column of the name
  still wins over the alias.
- **DATE_FROM_PARTS and TIMESTAMP_FROM_PARTS count their parts exactly and refuse a result past the range a date or
  timestamp holds**: `DATE_FROM_PARTS(2147483647, 1, 1)` is `Date 784351576412 is out of range` and
  `TIMESTAMP_FROM_PARTS(2147483647, 1, 1, 0, 0, 0)` `Timestamp 67767976201996800 is out of range`, where java.time's own
  text escaped; the account answers such parts with what an integer overflow leaves, which is not imitated.
- **A quoted role name is that name alone, a system role's included**: `CREATE ROLE "sysadmin"` creates a role beside
  SYSADMIN, and a user's `DEFAULT_ROLE = "low"` keeps `low`, as every quoted identifier names exactly what it spells.
- **A derived UNION keeps its rows under a reader that cannot tell duplicates apart, as the account's does**
  (live-verified): a derived table's, a common table expression's or a view's top UNION read by a block that is DISTINCT
  over the relation's columns, constants, or a column's COLLATION or SYSTEM$TYPEOF, or that groups over such keys or
  aggregates with a column's MAX, MIN or ANY_VALUE, keeps every row, so a text arm that would fail to convert toward a
  number arm is converted only where the block reads it — `SELECT DISTINCT COLLATION(x) FROM (SELECT c AS x FROM t UNION
  SELECT 1 FROM t)` answers NULL where it was `Numeric value 'a' is not recognized`. A computed item, COUNT, a window, a
  join, IN, EXISTS, a plain projection and a body that sorts or cuts its rows keep the check. The column of a collated
  text beside a number holds no collation, so COLLATION answers NULL.
- **A fault in an ALTER SCHEMA's, ALTER DATABASE's or ALTER VIEW's property list reports the account's lines**
  (live-verified): the list's first name is told first; ALTER DATABASE and ALTER VIEW tell nothing more, ALTER SCHEMA's
  UNSET the parse's own lines, and ALTER SCHEMA's SET each value of the properties a comma joins, then what follows a
  comma no name follows — `ALTER SCHEMA s SET DATA_RETENTION_TIME_IN_DAYS = 1,` tells the name, `'1'` and the end of
  input.
- **SPLIT_PART's whole part past 32 bits is refused as the account refuses it** (live-verified): while the statement
  compiles where the plan knows its value — `SPLIT_PART('a,b', ',', 3000000000)` is `argument 3 to function
  SPLIT_PART needs to be an integer, found: '3000000000'`, an expression of literals echoed as written
  (`'2147483647 + 1'`), a column its statistics leave one value echoed by its name — and as the row is read where the
  plan cannot know it (`Number out of representable range: type FIXED[SB4](38,0){nullable}, value 3000000000`); a
  text spelling such a number is `Numeric value '3000000000' is out of range`. A NULL text or separator answers NULL
  first, and a WHERE the statistics cannot prove keeps the plan from knowing the value.
- **A DESCRIBE or a SHOW where a query stands reports the account's lines** (live-verified): in a predicate's or a
  CTAS's brackets the one line, in a common table expression the word after it, and in a FROM after a JOIN or in a
  doubled bracket the word itself.
- **ALTER ACCOUNT IF EXISTS of another account's tags is refused before the account is looked for** (as measured):
  `Insufficient privileges to operate on account 'X'. Your primary role R must have MANAGE ORGANIZATION ACCOUNTS
  granted on ACCOUNT <this account>.`; and of two unknown names in one list the first written is refused.
- **A view carries data metric functions** (live-verified): `ALTER VIEW v SET | UNSET DATA_METRIC_SCHEDULE` and
  `ADD | DROP | MODIFY DATA METRIC FUNCTION` work on a view as on a table, the attachments survive a snapshot, and
  DATA_METRIC_FUNCTION_REFERENCES lists a view's rows in the View domain whichever table-like domain is asked; a
  schedule given as `USING CRON 0 0 1 1 * UTC` is listed as `0 0 1 1 * UTC`, for a table too. Thirty more kinds
  of object (`APPLICATION ROLE`, `GIT BRANCH`, `TRANSACTION` …) are refused as the function's domain by name.
- **The SNOWFLAKE database answers as the share it is** (live-verified): it holds the account's 43 system database
  roles (`CORTEX_USER`, `OBJECT_VIEWER` …), listed whole and inherited, a `STARTS WITH` or `LIMIT` on the listing
  refused as an unsupported feature; neither it nor a schema of its own lists a parameter; three of its schemas
  are described as missing; none of its roles is created, altered or dropped. Through the REST API a fetch of one
  of its schemas is the access refusal with status 401, and its roles' listing passes a `fromName` over.
- **Characters the account reads as operators** (live-verified): `a | b` is refused as `Unsupported feature '|'.`
  before any caret, `a & b` and `~a` are calls of the functions `"&"` and `"~"`, which no account has, and a `!`
  after a value names the token after it, the select list read on; a `!` after a name adds its own line in a select
  item only, or the dot of a name written with a path.
- **A number the lexer gives up on is reported as the account's lexer reports it** (live-verified): `1e`, `1e+`,
  `0x` and a second point no digit follows (`1.5.`) tell the character after them by its code, and the text is read
  on without the number and that character — `SELECT 1e FROM t` names the FROM, `LIMIT 1e` nothing more.
- **A name between backticks is a name** (live-verified): `` `a` `` keeps its backticks and folds as an unquoted
  name does (`invalid identifier '"`A`"'`); a backtick name the text never closes is the lexer's line at the
  character it stops at, and a backtick no name character follows is a character no token opens with.
- **A string left open straight after the token a statement is refused at is told first** (live-verified): the
  account's parser reads into it before it names the token.
- **A SHOW's LIMIT or STARTS WITH written again before a later fault is the one refused** (live-verified): alone at
  the top level, and with the token after it in a block.
- **A string the text never closes reports its escapes as the account's lexer does** (live-verified): after the
  lexer's line at the end of the input, each backslash is told at the character after it by that character's code
  (`near '98'` for `\b`), or at the end when nothing follows, and a text that ends in such an escape refuses the
  end of the input too, unless the string follows a comma; a backslash before a blank is told not at all.
- **A column type word that is a keyword here is an unknown type only when the account reads it as a name**
  (live-verified): OTHER is, LANGUAGE and SESSION are refused where they stand, as before the earlier change that
  took every keyword.
- **A WHERE's subquery whose own subquery reads the outer row is placed beside any other subquery of the
  statement** (live-verified), as it is not when it stands alone.
- **A SQL function's block body judges a template query before the statement's kind, and refuses a VALUES alone**
  (live-verified): `CREATE TABLE z USING TEMPLATE (SELECT 1)` is refused for the template, and `VALUES (1), (2)` is
  the VALUES' line alone.
- **Block recovery reads more like the account's** (live-verified): an exception handler without its THEN refuses
  the token in its place twice; every argument list of a statement that ends in a comma is refused in turn and the
  statement read on (`SELECT ABS(1,), ABS(2,) FROM t`); an operator's operand missing at a plain bracket's `)` is
  refused alone and the statement read on (`SELECT ABS((1 *)) FROM t WHERE a b` names the `)` and the `b`).
- **A statement's first fault before a query used as a value keeps its line, and the query is refused at its
  SELECT** (live-verified): `SELECT 1 +, (SELECT 1 x y)` names the `,`, the SELECT and then the `y`; an EXISTS's
  fault before a compared query names that query's SELECT; a word run in before the query still ends the report.
- **Script recovery reads more of what follows a fault** (live-verified): a block or a DECLARE after a statement
  refused at its semicolon reports its own lines (`SELECT 1 +; BEGIN SELECT 1 x y; END` adds the `y` and the
  END); `SELECT 'foo', INSERT INTO t VALUES (1)` refuses the VALUES after the INTO; a quantified LIKE's list
  followed by another bracket names that bracket's first token alone; `a IS TRUE ('x')` in a select list names
  the `(` after the IS.
- **A faulty CAST right after a WHERE reads the condition on** (live-verified): a later faulty cast's fault is told
  before the token that ends the clause, `… WHERE CAST(1 2) = 1 AND CAST(3 4) = 2` naming `2`, `4` and the end.
- **A select list read on past a literal alias reads its bracket rules too** (live-verified): `SELECT 1 AS 1, (2 x
  y)` names the `1`, then the bracket's `x` and `y`, and in a block the `)` after them.
- **A star call's misplaced RENAME or REPLACE in a derived table's or a CTE's item is reported on as the account
  reads it** (live-verified): after a derived table's bracket a lone name or an aliased literal stacks the
  bracket's `)`, a list the token after its first comma, and a lone literal or a text nothing of its own, the
  statement around the derived table read on; `AS k` stacks the call's `)`, a braced name its `}`; a CTE's
  bracketed query stacks the token after it, and a braced REPLACE in a CTE's item its first token and the `}`.
- **A malformed MATCH_RECOGNIZE clause is reported as the account reports it** (live-verified): with every
  sub-clause in its place the first fault alone, the clause read on to its end; a sub-clause written out of its
  place — after a later one, or a DEFINE before any PATTERN — refused at its keyword, each later one out of place
  again once, and a PATTERN left last followed by the clause's `)`.
- **A name with an empty middle part deeper than a select item is reported at the account's places**
  (live-verified): in a bracket that is a call's argument the bracket's `)`, then the name's first part and its
  dot; in a bracket under a CAST the CAST's `)`; in a call inside another call the outer call's `)`; in a CASE's
  condition the call's `)` twice and the THEN; in a query used as a value the query's SELECT and then the call's
  `(` (a select item) or the token past the call (an IN), and so a LET's bracketed query in a block.
- **SHOW's scope name with a run of three dots or more refuses the third dot**, the first part and two dots read
  as a database with an empty schema, and then a word written after the name (live-verified).
- **A word run into a name or a literal in a bracket inside calls is reported as the account does**
  (live-verified): `ABS((x y))` names the word, then the bracket's name and the word again; `ABS(ABS((1 x y)))`
  names the word and the outermost call's `)`, as without the bracket; `ABS(ABS(T.x.))` names the `)` after the
  trailing dot and then the dot.
- **A misplaced correlated aggregate is echoed and refused as the account does** (live-verified): REGR_COUNT and
  REGR_AVGX over a FLOAT are named by the formulas the plan computes them with,
  `COUNT(IFF(FZ.ID IS NULL, SYSTEM$NULL_TO_REAL(null), FZ.F))`; a predicate compared as a value is cast,
  `(CAST(FZ.ID > 5 AS BOOLEAN)) = TRUE`; and a percentile over a DATE or a TIMESTAMP, one that is no constant, and
  an average or a median left without three decimals of scale are refused for the type before the place is judged.
- **A grouped or DISTINCT ORDER BY key holding a query is named as its plan prints it** (live-verified): a ROW tested
  by IN is refused as `[ROW(ID, ID)]`, a key naming an output column by that name; a NATURAL or USING join whose
  sides share more than one column is `SYS_VW` over an AND of the keys' equalities, a right column whose name the
  left took renamed `<NAME>_0`; GROUP BY ALL over a constant is `GROUP BY 1`; a ROLLUP is the grouping sets it
  stands for, `GROUPING SETS ((), (G.V))`.
- **`HASH(*) OVER ()` is refused for its kind** (live-verified), `Invalid function type [HASH] for window function.`,
  before the star is read; a SQL function body that is a list of values and a value more is the ROW it is,
  `ROW(ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0))`, refused against the declared return type.
- **A column type word that is a keyword here and a plain name on the account (`other`) is an unknown type**
  (live-verified): an earlier column's width fault is reported first, and the first unknown type is the one refused,
  where the word was a syntax error.
- **An Azure storage queue integration and an Azure storage location show the account's rows** (live-verified):
  DESCRIBE lists `USE_PRIVATELINK_ENDPOINT`, the queue's URI, `AZURE_CONSENT_URL` and `AZURE_MULTI_TENANT_APP_NAME`
  and no provider or tenant row; a location's JSON adds the application, the consent URL and the encryption after
  the tenant. The application's name has the account's shape, 21 letters or digits, an underscore and 13 digits.
- **Three JavaScript errors are worded as the account's engine words them** (live-verified): a pattern RegExp refuses
  is quoted, `Invalid regular expression: /(/: Unterminated group`; `then` called on another receiver is
  `Method Promise.prototype.then called on incompatible receiver 1`; a thrown global object is `[object Object]`.
- **A fault inside a structured type's parentheses is reported as the account reports it** (live-verified): an
  ARRAY's or an OBJECT's first token that begins no type (`ARRAY(5 NOT NULL)`, `ARRAY('x')`) is named twice in a
  cast, then the `)` closing a parenthesis around the cast, or a later faulty cast's lines; a field without a type,
  a second element, a third MAP type, a missing MAP value or VECTOR dimension are named once, with the structured
  type's `)` or the CAST's and the column list's after it; a list nested in a structured type is judged as its
  cast's; and a column whose list is faulty reads what follows as a property's name and value
  (`VARCHAR(5,2) COMMENT 'c'` names `'c'` and the `)`), a name after the list's comma as a column without a type.
- **FLATTEN's PATH writes a key's control character as three octal digits** (live-verified): `['a\001b']`, DEL as
  `\177`, where the character was written raw; a newline, a tab, a carriage return, a backspace and a form feed keep
  their letters.
- **Dates, times and numbers are written in ASCII digits under any default locale** (an engine started under, say,
  `ar-SA-u-nu-arab` wrote a date's and a time's digits, a timestamp offset's and a stack trace's line numbers in
  Arabic-Indic digits): every `String.format` of the main sources names `Locale.ROOT`, and checkstyle's
  `LocaleFreeFormat` / `LocaleFreeFormatted` rules keep it so.
- **A call given up in a query that is a value is reported as the account reports it** (live-verified): in a select
  list the query's SELECT and then the parenthesis of the call its first item opens with, wherever the fault stands;
  compared in a WHERE or a HAVING the first token in that call; as an IN's list the token past the call.
- **LAG, LEAD and NTH_VALUE refuse a quantifier before anything written after it** (live-verified): `LAG(DISTINCT n
  IGNORE NULLS)` is `'DISTINCT'` and then the call's `(`; a null treatment written twice after a call is refused at
  the second's word, and a relation's star given a name, `COUNT(x => t.*)`, at the star and then its dot.
- **APPROX_PERCENTILE echoes a subquery percentile as the query its plan runs** (live-verified): `Invalid value
  [(SELECT 0.5 AS "0.5" FROM (VALUES (null)) DUAL)]`, `(SELECT MAX(RT.N) AS "MAX(N)" FROM RT AS RT)`, where it
  printed `(subquery SELECT 0.5)`.
- **SHOW GRANTS TO ROLE and TO USER name a grant on the account by the account's locator** (live-verified), as SHOW
  GRANTS ON ACCOUNT does; the name was the word `ACCOUNT`.
- **A refusal in a block's expression is placed past a VARIANT, OBJECT or ARRAY variable as the account places it**
  (live-verified): the variable is read back from its JSON text there, so `RETURN IS_DOUBLE(v) || ' ' || TRUE` over
  a VARIANT is refused twelve columns further on than the variable's cast alone placed it.
- **An ASYNC job written without its brackets, or with a semicolon inside them, is refused at two places**
  (live-verified): `ASYNC SELECT 1;` at the SELECT and then at the semicolon where the closing bracket is wanted, and
  `ASYNC (SELECT 1; SELECT 2)` at the first semicolon and then at the bracket after the next statement.
- **The too-many-arguments sentence of a SQL function echoes a cast as the plan writes it** (live-verified):
  `pick('2024-01-01'::DATE, 1)` is `too many arguments for function [PICK(CAST('2024-01-01' AS DATE), 1)] expected 1,
  got 2`, where the cast was echoed as written.
- **Date arithmetic over a block's variable holding NULL is the bare word NULL in a query the block runs**
  (live-verified): a NULL DATE plus or minus a number, a date or an INTERVAL, a number plus it, and a DATE beside a
  NULL number — `SYSTEM$TYPEOF(:d + 1)` is `NULL[LOB]` where it was `NUMBER(19,0)`, `:d + 1 + 1` a NUMBER(19,0) and
  `COALESCE(:d + 1, CURRENT_DATE())` a DATE, no longer refused.
- **The SNOWFLAKE database's rows are the account's** (live-verified): SHOW DATABASES lists it as the APPLICATION of
  origin `SNOWFLAKE.ACCOUNT_USAGE`, owned by no role and retained for no day; INFORMATION_SCHEMA.DATABASES leaves it
  out; and every database's INFORMATION_SCHEMA has NULL `created` and `last_altered` in SCHEMATA.
- **BOOLAND, BOOLOR, BOOLXOR and BOOLNOT round each operand to a whole number first** (live-verified):
  `BOOLAND(0.4, 1)`, `BOOLAND(0.4::FLOAT, 1)` and `BOOLAND('0.4', 1)` are FALSE and `BOOLNOT(0.4)` TRUE, as 0.4
  rounds to 0; 0.5 rounds to 1.
- **A DECFLOAT rounds to any scale its slot holds** (live-verified): `ROUND(DECFLOAT '1e-100', 50)` is 0 and
  `ROUND(DECFLOAT '1e6000', -5999)` is 1e6000, TRUNC, CEIL and FLOOR alike, where a scale span past 38 digits was
  refused as `Scale too large`; NUMBER keeps that rule. `ROUND(1.25::FLOAT, 32768)` is NaN, as the account answers,
  not 0.
- **A user switch through the embedded API keeps no role the new user may not use**: `DatabaseEngine.setCurrentUser`
  takes every such role out of use, the former primary role giving way to PUBLIC until `setCurrentRole` names the
  user's, where a user switched to from a SYSADMIN session kept SYSADMIN in use and read what only SYSADMIN may.
  CURRENT_USER() names the user switched to, and the configured user again after a switch back to SYSTEM.
- **GET's FLOAT index names no element** (live-verified): `GET([1,2,3], 1::FLOAT)` and `[1,2,3][1.5::FLOAT]` are NULL,
  a NaN or an infinity included, where they read the nearest element or were refused; an index written out, as in
  `GET(a, 1e0)`, still reads its element.
- **ARRAY_INSERT at a position before the first element pads the gap** (live-verified): `ARRAY_INSERT([1,2], -3, 9)` is
  `[9,undefined,1,2]`, not `[9,1,2]`.
- **CHR's refusal names the code's low 64 bits** (live-verified): `CHR(1e25)` is `Invalid character code
  1590897978359414784 in the CHR input`, as the account reads the code, not the 26 digits written.
- **DROP COLUMN refuses a column a policy reads in the account's words** (live-verified): `SQL compilation error: Cannot
  drop column 'REG' which is associated with a Row access policy.` for a column of a row access policy's ON list and
  `… with a Masking policy.` for one a conditional masking policy reads through USING, on one line each.
- **A Python handler's VECTOR answer is converted element by element as the account converts it** (live-verified):
  `int(element)`, which truncates (`[1.5, 2]` into a VECTOR(INT, 2) is `[1,2]`, `'1'` and `True` are 1), or
  `float(element)`, held to 32 bits (`1e300` is infinite); a length other than the declared one is `Returned list
  dimension, 3, does not match the expected vector dimension, 2`, a None element `Vector elements cannot be 'None'`,
  and a conversion's own failure `Invalid element in returned vector: …`. The answer went through the cast to VECTOR,
  which refused a float element of an INT vector and worded every refusal as the cast's.
- **PERCENTILE_CONT and MEDIAN over a DECFLOAT interpolate as the account does** (live-verified): the place and both
  weights are doubles, each weight taken to 18 significant digits, and the interpolation is DECFLOAT arithmetic, so
  PERCENTILE_CONT(0.1) over 0 and 1 is 0.0999999999999999778 and over 1e100 and 3e100 is 1.1999999999999999554e100. A
  descending WITHIN GROUP order runs the same steps over the values reversed, and a window honours it: a windowed
  descending percentile of any type answered the ascending one. Valid inputs were refused as out of NUMBER range and
  interpolations cut to the input scale plus three.
- **A NaN or an infinity cast to a NUMBER names the target's width and the source's nullability** (live-verified):
  `f::NUMBER(10,2)` over a nullable FLOAT column holding NaN is `Number out of representable range: type
  FIXED[SB8](10,2){nullable}, value nan` and `TO_DECIMAL('NaN'::FLOAT, 10, 2)` names `FIXED[SB8](10,2){not null}`;
  TO_NUMBER over a nullable column prints `{nullable}` for any value past its declared width. Each printed
  `FIXED[SB16](38,0){not null}`.
- **A number too wide for the column it is written to prints `{nullable}` when the column or the value's source can be
  NULL** (live-verified, for INSERT … SELECT, UPDATE, MERGE and a CTAS column list): a 12345 from a nullable column
  into a NOT NULL NUMBER(3,0) is `FIXED[SB2](3,0){nullable}`, the literal 12345 `{not null}`. Only the column's own
  nullability was printed.
- **TYPEOF, TO_JSON and the IS_ functions read a DECFLOAT as its VARIANT** (live-verified): TYPEOF of a DECFLOAT, a
  bare value or a structured member, is `DECFLOAT`, `TO_JSON(DECFLOAT '1.5')` is `"1.5"`, and IS_DECIMAL, IS_INTEGER,
  IS_DOUBLE and IS_REAL of a DECFLOAT are FALSE. They answered DECIMAL or INTEGER, `1.5` and TRUE.
- **A semi-structured value or a VARIANT written to a structured column is refused in the account's words**
  (live-verified): `incompatible types: [ARRAY] and [ARRAY(NUMBER(38,0))]`, for INSERT, UPDATE and MERGE alike, where
  the column-type sentence was printed.
- **Dropping a task takes it out of the predecessor lists it was in, and TASK_DEPENDENTS lists predecessors by name**
  (live-verified): a child of the dropped task follows its other predecessors or stands alone, where it went on naming
  the dropped one, and `CREATE TASK z AFTER r, o` is listed `["…O","…R"]` as SHOW TASKS lists it.
- **REPEAT, LPAD, RPAD and SPACE read their length as the account does** (live-verified): REPEAT's count times its
  text's length goes into a 32-bit length typed as that product (`Number out of representable range: type
  FIXED[SB4](29,0){not null}, value 10000000000`), a padded length past 32 bits is `Numeric value '…' is out of
  range`, a FLOAT one goes through `FIXED[SB4](9,0)`, and a length that fits but builds past 128 MB of text or 64 MB
  of binary is `String '(padded string)' is too long and would be truncated`. A negative length past 32 bits is
  refused too; it answered an empty string.
- **ARRAY_APPEND, ARRAY_PREPEND and ARRAY_INSERT hold an added element to its scale, not to its declared length**
  (live-verified): `ARRAY_APPEND(['a']::ARRAY(VARCHAR(2)), 'toolong')` is `["a","toolong"]`, still an
  ARRAY(VARCHAR(2)) that a CTAS stores, and reading the over-long element back is refused as too long; a double past
  thirty-eight digits is refused naming the element's declared width. OBJECT_INSERT's field refusal carries the
  field's nullability (`{nullable}`) and no DML sentence in an INSERT or a CTAS.
- **A cast to a NUMBER-keyed MAP collapses keys that convert to one number** (live-verified): the value of the source
  key sorting last as text is kept, `{'01':'x', '1':'y', '1.0':'z'}::MAP(INT, VARCHAR)` being `{1: 'z'}`, and a key
  that converts to no number is refused as its text's cast is (`Numeric value 'abc' is not recognized`). The first
  was refused as a duplicate key, the second as a schema mismatch.
- **A DECFLOAT converted to an exact NUMBER that cannot hold it is refused naming the conversion** (live-verified):
  a cast to NUMBER(p,s), INT or a bare NUMBER, TO_NUMBER and TO_DECIMAL, a cast of a column and a write into a NUMBER
  column are each `Decfloat out of representable range, operation: TO_NUMBER(99.95, 3, 1)`, the write's inside its
  DML sentence. They were refused as a NUMBER's overflow, with the value's digits.
- **An out-of-range FLOAT is refused with the double as the account prints it** (live-verified): C's `%g`, six
  significant digits (`value 1e+39`, `1.23457e+39`, `123457`, `1000`), classed by the target's width in TO_DECIMAL as
  in a cast, and a VARIANT's double as the VARIANT prints it (`1.2345678e+39`). The value's exact binary expansion was
  printed.
- **A UNION ALL arm's value that does not fit the unified type is refused where the row is read** (live-verified): a
  plain SELECT refuses it bare, a CTAS or an INSERT … SELECT inside its DML sentence naming the column, and a UNION's
  duplicate check bare again. GREATEST and LEAST, which convert while they compare, are refused bare by every
  statement. Both were the other way round.
- **A double becomes an exact NUMBER at its exact binary value** (live-verified): a VARIANT's double cast to NUMBER
  and a FLOAT written into a NUMBER column round as a FLOAT's cast already did - 2.675 is 2.67 at scale 2, and
  `PARSE_JSON('1e38')::INT` is 99999999999999997748809823456034029568 - where they rounded the shortest decimal
  spelling (2.68) and refused 1e38 as past the range.
- **A FROM-less correlated subquery that calls the scalar overload of a name with an aggregate one beside it is
  answered** (live-verified): `(SELECT mix(t.v))` is the outer row's own scalar. It was refused as an unsupported
  subquery.
- **A Java or Scala procedure's handler that raises reads as its report** (live-verified), as a function's
  handler's does: `User Error Report:`, `Java Stack Trace:`, the exception, its frames in `InlineCode.java` or
  `ScalaUDF` (an object's method under its forwarder) and its cause, then ` in function P with handler H.run`, which
  a block's handler reads as SQLCODE `100315` and SQLSTATE `P0000`. The procedure failed with the exception's message
  alone, and SQLCODE `100351`.
- **A statement a Java or Scala handler runs through its Snowpark session that fails is the driver's exception**
  (live-verified): a checked `java.sql.SQLException` printed `net.snowflake.client.jdbc.SnowflakeSQLException: ` and
  the statement's refusal, with the code and state a block reads for it (`2003` / `42S02` for a missing table) and
  frames from the session's `collect` down to the handler. So is the driver's refusal of a whole number no Long holds.
  It was an unchecked exception of the engine's own.
- **A NullPointerException a Java handler of RUNTIME_VERSION 17 or later raises keeps its sentence** in the report
  (`Cannot invoke "String.length()" because "<local3>" is null`), as the account's Java 17 composes it; a handler of
  Java 11 is named without it, as before (live-verified).
- **A Scala handler class the body does not hold is refused at CREATE as the class loader reports it**
  (live-verified): `java.lang.ClassNotFoundException: G` and the Java 11 class loader's three frames, for a function,
  a procedure and a table function alike; it was `Failed to compile Scala routine F: G`, or the exception alone.
- **What a Scala handler's object holds lasts as a Java handler's static fields do** (live-verified): a function's for
  the statement and the place in it that calls the function, so `SELECT f(1)` twice answers 1 and 1 and
  `SELECT f(1), f(1)` 1|1, and a procedure's for its CALL. It lasted for as long as the engine ran.
- **A JavaScript handler that makes an `Error` answers under a Python procedure as it does on its own**: a procedure or
  a function the Python procedure's statement calls failed with a NullPointerException as the script engine walked
  the Python handler's frames for the error's stack.
- **A JavaScript function whose body does not parse is refused in the account's words** (live-verified):
  `JavaScript compilation error: Uncaught SyntaxError: Unexpected token ';' in F at 'return (;' position 8`, with no
  stack lines - a token the parser did not expect named by its kind (`Unexpected number`, `Unexpected string`,
  `Unexpected identifier 'B'`, `Unexpected end of input`), a text, a number or a character no token starts as
  `Invalid or unexpected token` where it starts, and a fault past the body's end quoted on the closing brace's line;
  a table function's body is quoted inside its parentheses. It was `JavaScript execution error:` in the script
  engine's own words.
- **A JavaScript table function's handler object reads as the account's** (live-verified): an error a member raises
  lists the object's own functions after `stackstrace: `, each at its line counted from 0 (`processRow line: 0`),
  with no frame for the routine; a member that is no function is `Uncaught TypeError: udtfObject.initialize is not a
  function`, quoting the account's own line calling it; a body whose value is no object is `User Defined Table
  Function not an object`; `argumentInfo` tells a constant number argument by `isConst: true` and its `constValue`;
  and the object is made once for the statement's call, so what it keeps carries from one partition to the next. A
  name no function carries in a lateral `TABLE( … )` is an unknown table function before the select list's names.
- **Inside an aggregate's argument, an IN list's element the subject never equals and a COALESCE's or an NVL's
  argument after one that holds no NULL correlate nothing**, in a value's subquery and an IN's alike, and a settled
  condition folds in an IN's subquery as in a value's (live-verified): `(SELECT MAX(IFF(tt.id IN (fz.id, 1), 'x',
  value)) FROM tt)` and `id IN (SELECT MAX(IFF(tt.id = fz.id, 5, 7)) FROM tt)` are answered where they were refused.
  A text column none of whose rows is NULL holds no NULL for the rule.
- **A block's binds read as the account reads them** (live-verified): a RESULTSET's unaliased column is named by the
  query as the block wrote it (`:S`, `UPPER(:S)`), an argument-count refusal echoes a variable holding NULL as
  written (`[FO(:s, 1)]`, where it was `[FO((null :: VARCHAR(10)), 1)]`), and `LIMIT ?` bound to NULL through
  EXECUTE IMMEDIATE's USING limits nothing, where it was refused.
- **A bare table function in a common table expression's query is refused with the account's lines**
  (live-verified): its parenthesis, its first argument's name and the token after that argument's value.
- **GRANT OWNERSHIP ON MATERIALIZED VIEW moves the view to the role**, and **a secure materialized view's text is its
  owner's** (documented): SHOW MATERIALIZED VIEWS and SHOW VIEWS show a role that does not own it an empty `text`,
  as for a secure view. The grant recorded an OWNERSHIP privilege and left the owner as it was.
- **A grant ON TABLE or ON VIEW over a materialized view or a dynamic table is that relation's**: it is recorded under
  the relation's own kind, where SHOW GRANTS ON and the privilege views read it. It was recorded under TABLE, and a
  dynamic table was not found.
- **INFORMATION_SCHEMA's privilege views list only the objects the current role reaches** (documented): those it owns
  or holds a privilege on. They listed every grant in the database.
- **ALTER SECRET … SET OAUTH_SCOPES is judged against the secret's integration**, as CREATE SECRET is: a scope the
  integration does not allow is `invalid value '[c]' for property 'OAUTH_SCOPES'`.
- **A staged-file query's argument list that does not parse is refused with the account's lines** (live-verified):
  a token after a whole argument is refused alone, `(FILE_FORMAT => ())` at the inner `)` alone, a `=>` inside a value
  in parentheses and then the list's own `)`, `@st (,)` at the comma and `@st (=> ff)` at the `=>` and then where the
  name's own `=>` was due.
- **A sequence never hands a value out twice across a restart** (documented): a value drawn through a view or a SQL
  function, one drawn inside a transaction that rolled back and one drawn by a statement that failed are kept by a
  checkpoint, since no record of the write-ahead log carries them; a restart handed them out again.
- **A correlated scalar subquery whose item reads the outer row through a derived table or a common table expression
  of its own is typed from the item** (live-verified): `(SELECT c + o.k FROM (SELECT 1 AS c))` declares NUMBER(38,0),
  where it declared VARCHAR(16777216) and RESULT_SCAN read its values as text.
- **GET_DDL of a hybrid table writes `create or replace HYBRID TABLE`** (documented), so the DDL run back makes a
  hybrid table that holds its keys again; it made a standard one.
- **ARRAY_INTERSECTION, ARRAY_UNION_AGG and MAP_INSERT, MAP_DELETE, MAP_CAT and MAP_PICK keep a structured type**
  (live-verified): ARRAY_INTERSECTION answers the elements' common type (`ARRAY(NUMBER(38,2))` over an ARRAY(INT) and
  an ARRAY(NUMBER(10,2))), ARRAY_UNION_AGG its argument's with the element NOT NULL, and the MAP functions the map's,
  so an element read from them keeps its exact digits; they answered ARRAY or MAP(VARCHAR, VARIANT), and arithmetic
  on an element fell back to FLOAT.
- **OBJECT_INSERT with the update flag casts the value to the structured field's declared type and keeps the type**
  (live-verified): `'5'` into a NUMBER(38,0) field is 5, 2.7 is 3 and 5 into a VARCHAR field is `"5"`, and a table
  built over the call stores the declared type; the field took the value's type. Without the flag a field the type
  declares is refused in the account's words.
- **A table built over a structured OBJECT stores an unsized VARCHAR field at VARCHAR(16777216)** (live-verified),
  where it stored VARCHAR(134217728).
- **A NUMBER-keyed MAP is read by the number a key names** (live-verified): `m[1]`, `m[2.0]`, `m[i]` and
  `GET(m, 1.0)` read the member, where a subscript answered NULL and a join or a filter on it found nothing; MAP_KEYS
  answers the keys as the map declares them, `[1,2]` typed `ARRAY(NUMBER(38,0) NOT NULL)`, where it answered
  `["1","2"]` typed `ARRAY(VARIANT)`.
- **A NaN or infinite FLOAT amount of DATEADD, TIMEADD, TIMESTAMPADD and ADD_MONTHS is refused in the account's
  words** (live-verified), `Number out of representable range: type FIXED[SB4](9,0){not null}, value nan`, and a
  VARIANT holding NaN as the VARIANT's cast, `… type FIXED, value NaN`, where Java's number parser spoke.
- **REGR_VALX and REGR_VALY answer a DOUBLE** (live-verified): `REGR_VALX(g, g)` over an INT is `1.0`, typed
  `FLOAT`, where it answered the INT.
- **A statement waiting for another session's table lock ends at once when its own transaction is aborted**
  (live-verified): `Transaction '<name>', id '<id>', was aborted by user '<user>'. SQL   execution canceled.`, the
  session then holding the transaction as aborted; it waited for the holder, then reported a row written into the
  aborted transaction.
- **SYSTEM$ABORT_SESSION ends a request the session is running** at its statement's next checkpoint, its open
  transaction rolled back, when the abort names the session by a number written out: the abort is carried out as
  its request is read, where it waited for the other request, which ran on and committed.
- **ALTER ALERT … SET and UNSET take a session parameter, LOG_LEVEL and their kin** (live-verified), several in one
  SET, and SHOW PARAMETERS IN ALERT lists what an alert sets at level ALERT; it was a syntax error at the name.
  SHOW GRANTS ON ALERT lists an alert's grants, and the future grants on a schema's alerts are a new alert's.
- **A refusal names an unknown property as written** (live-verified): `invalid property 'foo' for 'TABLE'`, `'Foo'`,
  and a quoted name with its quotes, for tables, schemas, views and sequences, where an unquoted name was upper-folded.
- **SHOW PARAMETERS lists the parameters the system sets at level SYSTEM** (live-verified): ENABLE_USE_STABLE_PATH
  reads `true` there, its default `false`, where it read its default with no level.
- **A bound java.sql.Time keeps its milliseconds** (live-verified), at the clock its moment shows in UTC, through
  setTime, setTime with a Calendar, setObject and setObject as Types.TIME alike; setObject as Types.TIME bound the
  local clock, and every setter dropped the fraction.
- **A policy's body is judged against its RETURNS first** (live-verified): a masking policy whose body is of another
  type than it declares is `Declared return type 'NUMBER(38,0)' is incompatible with actual return type
  'VARCHAR(134217728)'`, before its argument and return types are compared, where the generic mismatch was answered;
  a row access policy may declare any type after RETURNS and is refused for it by name, `Row access policy return
  type 'VARCHAR(134217728)' is not BOOLEAN.`, where any type but BOOLEAN was a syntax error.
- **CREATE MASKING POLICY and CREATE ROW ACCESS POLICY compile before IF NOT EXISTS or OR REPLACE look at the name**
  (live-verified): IF NOT EXISTS over a policy of the name refuses a body or a RETURNS the policy could not have, and
  OR REPLACE whose body is refused leaves the policy it would replace as it was, as it does a policy of another kind;
  the first answered that the policy exists, and the second dropped it.
- **A manual refresh of a dynamic table whose query reads a relation that is gone is a failed refresh**
  (live-verified): `Failed to refresh dynamic table with refresh_trigger MANUAL at data_timestamp … because of the
  error: SQL compilation error: Target table failed to refresh: …`, the query expanded as a view's body under the
  table's name, the missing object named in full and the role the table runs as noted; the missing object's own
  sentence was answered.
- **An arity sentence echoes a shift's unit and a DISTINCT as written** (live-verified): `DATEADD(day, 0.0000001)`
  is `not enough arguments for function [DATEADD('DAY', 0.0000001)], expected 3, got 2`, and `SUM(DISTINCT 1, 2)
  OVER ()` keeps its DISTINCT in the echo.
- **A whole number of a scaled NUMBER cast to VARIANT keeps no trailing zeros** (live-verified):
  `TO_JSON(100::NUMBER(10,2)::VARIANT)` and its VARCHAR are `100`, as TO_VARIANT gives them; they read `100.00`.

### Changed

- **A SQL function's call costs about a quarter less**: the cast every bound argument is written through no longer
  has its type parsed and checked again on every call (`SUM(addone(i))` over 20,000 rows from 196 to 148 ms).
- **EXPLAIN lists the expressions each operator computes as Snowflake resolves them** (live-verified): every column
  named by its relation (`ET.A`, `E.B` under an alias), a star as its columns, a scan the columns the statement reads
  of its table (and no partition when it reads none), a Filter its conditions (`(ET.A > 1) AND (ET.B = 'x')`), a join
  `joinKey: (EU.A = ET.A)` and `joinFilter: …`, an outer join's Filter `EU.A IS NOT NULL`, an Aggregate `aggExprs:
  [SUM(ET.A)], groupKeys: [ET.B]`, a Sort `ET.A DESC NULLS FIRST`, a SortWithLimit `sortKey: […], rowCount: 2, offset:
  1`, a union's Result `UNION_ALL(ET.A, EU.A)`, and the forms the plan rewrites to — `CASE_FLATTENED(…)`,
  `TO_CHAR(ET.A)`, `ET.A IN 1 IN 2`, `STARTSWITH(ET.B, 'x')`, `SCALED_ROUND_INT_DIVIDE(SUM(ET.A), COUNT(ET.A))`,
  `DATE_ADDDAYSTODATE(1, ET.D)`. A LIMIT 0 reads a Generator of no rows. Expressions were the statement's own text
  before; what Snowflake's optimizer derives from table statistics and metadata is still not reproduced.
- **The JDBC driver reads a column label by its exact spelling, as Snowflake's driver does by default**
  (live-verified): over a column named `X`, `getInt("x")` is now `Column not found: x`, and over columns `"a"` and
  `"A"`, `getInt("A")` reads the second. `ALTER SESSION SET CLIENT_RESULT_COLUMN_CASE_INSENSITIVE = TRUE` switches the
  connection to the first match ignoring case, and it stays so for the connection: a later FALSE or UNSET leaves it
  on, as it leaves Snowflake's driver. Both transports read labels so, and an HTTP answer reports the setting as
  `clientResultColumnCaseInsensitive`. Code that relied on labels in any case needs the ALTER SESSION, as it does
  against Snowflake.

## 0.3.0 — unreleased

Changes since 0.2.0 accumulate here as they land.

### Now refused (SQL that earlier versions accepted)

- **A COPY load rejects a CSV record a staged-file query refuses, with the query's own sentence**: a character after
  a closing enclosure (`"a"x,b`) is `Found character 'x' instead of field delimiter ','` at line 1, character 4,
  against the table's first column, and an enclosure still open at the end of the file is `matching enclosing
  character '"' not found before end of file` at its opening, where the load took `ax` and read on; the aborting
  default fails the statement, and ON_ERROR = CONTINUE reports it as the file's first error and loads the next record
  (the query's rule, live-verified for the query).
- **A warehouse, a user, a compute pool and a role are named by their exact spelling, as every schema object
  already was**: with only a warehouse `"wh"`, `DROP WAREHOUSE WH`, `USE WAREHOUSE WH`, `ALTER WAREHOUSE WH`,
  `DESCRIBE WAREHOUSE WH` and a task's `WAREHOUSE = WH` are refused as missing; so are `DESCRIBE USER "Us"` beside a
  user US, `ALTER ROLE RL` and `DROP ROLE RL` beside a role `"rl"`, and `DESCRIBE COMPUTE POOL "cp1"` beside CP1,
  where each reached the object of the other case (documented).
- **BOOLAND, BOOLOR, BOOLXOR and BOOLNOT read a text or a VARIANT as a NUMBER**: `BOOLAND('yes', 1)` and
  `BOOLNOT('t')` are `Numeric value 'yes' is not recognized`, a VARIANT holding "yes" `Failed to cast variant value
  "yes" to FIXED`, where any text that was no number was TRUE (live-verified).
- **A text, a VARIANT or a bare NULL beside an interval is refused at the operator**: `'2024-01-02' + INTERVAL
  '1 day'` is `Invalid argument types for function '+': (VARCHAR(10), INTERVAL)` while the statement compiles, and
  `NULL + INTERVAL '1 day'` `(NULL, INTERVAL)`, where the text was read as a date (live-verified).
- **AS_DECIMAL's precision and scale and TIME_SLICE's length refuse a fraction written as one**: `AS_DECIMAL(v, 10,
  1.6)` is `argument 3 to function AS_DECIMAL needs to be an integer, found: '1.6'` and `TIME_SLICE(ts, 5.6,
  'MINUTE')` `invalid type [NUMBER(2,1)] for parameter 'slice-length'`, where 2.0 and 5.0 are taken (live-verified).
- **A RECURSIVE view whose body does not recurse on it as the account reads a recursive body is refused at
  CREATE as the missing object the view still is**: an EXCEPT or a MINUS on top of the body (`SELECT 1 UNION ALL
  SELECT n + 1 FROM v WHERE n < 3 EXCEPT SELECT 2`), a distinct UNION on top whose second operand reads nothing
  (`… UNION SELECT 100`), a second anchor before the recursive term (`SELECT 100 UNION ALL SELECT 1 UNION ALL …`) and
  an anchor that reads the view are each `Object 'D.S.V' does not exist or not authorized.`, where the view was
  created and answered from an operand chosen otherwise (live-verified).
- **An unquoted name reaches no quoted lower-case object, whatever the statement**: `EXECUTE TASK TK` no longer runs
  a task `"tk"`, `DESCRIBE`, `ALTER` and `DROP PIPE PP` no longer reach a pipe `"pp"`, `DESCRIBE VIEW VW` no longer
  describes a view `"vw"`, and `SYSTEM$PIPE_STATUS('LOWDB.SCH.PP')` no longer finds a pipe of `"lowdb"."sch"` - each
  is refused as missing, as every other statement already refused them (documented).
- **A COMMENT, an ALTER or a DESCRIBE names a schema or a database exactly, as CREATE and DROP do**: `COMMENT ON TAG
  D.MIXEDSCH.t` and `ALTER SEQUENCE D.MIXEDSCH.s SET COMMENT = ...` over a schema `"mixedSch"` are refused
  `Schema 'D.MIXEDSCH' does not exist or not authorized`, and `DESCRIBE STAGE LOWDB.SCH.STG` over a database `"lowdb"`
  is refused `Database 'LOWDB' does not exist or not authorized`, where each reached the object (documented).
- **A star argument's qualifier names an alias or a relation exactly**: `OBJECT_CONSTRUCT("LC".*) FROM "lc"`,
  `ARRAY_CONSTRUCT("LC".*)`, `OBJECT_CONSTRUCT(lc.*) FROM "lc"` and `OBJECT_CONSTRUCT("x".*) FROM "lc" x` are refused
  `Object 'LC' does not exist or not authorized.` (`'"x"'` for the quoted alias), as the star item `"LC".*` already
  was, where each answered the row (live-verified).
- **A stage's `FILE_FORMAT = (…)`, a table's `STAGE_FILE_FORMAT` and COPY's inline FILE_FORMAT list are judged as
  CREATE FILE FORMAT judges its options**: an unknown parameter (`invalid parameter 'BOGUS_OPT'`), an option outside
  the type's tree (`Option FIELD_DELIMITER is not valid for file format type JSON.`), a value outside its domain
  (`invalid value [-1] for parameter 'SKIP_HEADER'`, `invalid value ['yes'] for parameter 'TRIM_SPACE'`) and a
  parameter given twice (`conflicting values file format parameter 'FIELD_DELIMITER'`) are refused in the words
  CREATE FILE FORMAT already used, where a stage and a COPY accepted each (live-verified for stages; documented for
  COPY, whose options are the same set).
- **ON_ERROR takes one domain in COPY and in a stage's COPY_OPTIONS**: `ABORT_STATEMENT`, `CONTINUE`, `SKIP_FILE`,
  `SKIP_FILE_<n>` and `'SKIP_FILE_<p>%'` with p from 0 to 100, a fraction allowed, so COPY now refuses
  `'SKIP_FILE_150%'` as a stage did and a stage now takes `'SKIP_FILE_12.5%'` as COPY must (live-verified for stages).

- **An administrative role drops, reads and writes only what it owns or was granted**: ACCOUNTADMIN and SYSADMIN no
  longer stand in for a privilege on an object another role owns - a DROP of such an object by either is refused as
  a custom role's is, as every statement already asked (live-verified for reads and writes; documented for DROP).

- **A text past a NUMBER's range beside a NUMBER in UNION, INTERSECT, EXCEPT, GREATEST or LEAST** - `SELECT id FROM t
  UNION ALL SELECT '1e39'`, `GREATEST(id, '1e38')` - is refused as a comparison refuses it, `Numeric value '1e39' is not
  recognized` past the 128-bit carrier and `... is out of range` from 10^38, where a NUMBER(38,0) column held a
  39-digit value (live-verified).
- **TO_NUMBER, TO_DECIMAL and TO_NUMERIC of a text past 38 digits with no precision** - `TO_NUMBER('1e38')`,
  `TO_NUMBER('99999999999999999999999999999999999999.5')` - are refused as `Numeric value '1e38' is out of range`, the
  NUMBER(38,0) default holding no more, and the TRY_ forms answer NULL, where a 39-digit value was answered; a VARIANT
  past the width fails its cast to FIXED, and a text whose digits at the target's scale pass the 128-bit carrier -
  `TO_NUMBER('1e37', 10, 2)` - is `not recognized` there and in a cast, where it was out of range (live-verified).
- **ARRAY_CAT over two structured arrays that do not unify** - a text beside a number, a nesting of another depth, an
  OBJECT of other fields - is refused as `Can not convert parameter 'CAST(ARRAY_CONSTRUCT(ARRAY_CONSTRUCT('zz')) AS
  ARRAY(ARRAY(VARCHAR(134217728))))' of type [ARRAY(ARRAY(VARCHAR(134217728)))] into expected type
  [ARRAY(ARRAY(NUMBER(38,0)))]`, and a semi-structured second array beside a structured first one as an invalid
  argument type, where the second array's elements were kept under the first's type (live-verified).
- **A REVOKE under RESTRICT, the default, of a privilege its grantee passed on WITH GRANT OPTION** is refused while
  the grant made through the option stands, `Dependent grant of privilege 'SELECT' on securable 'P.PUBLIC.T' to role
  'C' exists. It must be revoked first. …`, where the grantee's grant was taken back and the one made through it kept;
  REVOKE GRANT OPTION FOR is judged the same way (documented).
- **A GENERATOR bound the account does not fold**: a ROWCOUNT or TIMELIMIT calling LENGTH, DATEDIFF, SQRT, TRUNC,
  CEIL, ROUND, POWER, BITAND, ARRAY_SIZE, REGEXP_COUNT, HASH, SIGN, LN, EXP, LOG or CHARINDEX, subtracting a date from
  a date, or adding a value folded late (`ABS(-2) + ZEROIFNULL(NULL)`) is `argument 1 to function GENERATOR needs to
  be constant, found 'LENGTH('abc')'`, where its value was taken (live-verified).
- **LAST_DAY with a part it does not read in a SQL function's body** - `LAST_DAY(d, hour)`, `LAST_DAY(d, day)` - is
  refused at CREATE as `['HOUR'] is not a valid date/time component for function LAST_DAY.`, where the function was
  created and every call refused (live-verified).
- **SYSTEM$STREAM_BACKLOG as a scalar function** is `Unknown function SYSTEM$STREAM_BACKLOG.`, where it answered
  a table's row count; the account's is a table function (live-verified).
- **A block's LET of a ROW, and its RETURN of one**: `LET x := (1, 2)` is refused while the block compiles, ` variable
  'X' cannot have its type inferred from initializer` at the LET, and `RETURN (1, 2)` as ` Unsupported: return
  multiple columns row. Consider returning a table (from a RESULTSET) or an ARRAY.` at the row, where the variable held
  an array and the block returned one (live-verified).
- **A SQL function whose query body has a fault every grammar has** - a word after an alias or a relation's name,
  `SELECT a a a FROM t`, `SELECT a FROM t x y`, `SELECT a b c`, or a text after TOP - is refused at CREATE as `Compilation
  of SQL UDF failed: SQL compilation error: syntax error line 1 at position 1 unexpected 'SELECT'.`, where it was
  created and every call failed (live-verified).
- **A number with two points**, `SELECT 1.5.6`, is refused at the whole of it, `unexpected '1.5.6'`, where the second
  number was named (live-verified).
- **A key written twice in an object constant**, `{'a': 1, 'a': 2}`, is refused as `Duplicate field key 'a'`, as
  OBJECT_CONSTRUCT already refused it, where the later value was kept; a pair whose value is NULL is dropped first
  (live-verified).
- **An EXCLUDE of an unqualified star argument naming a column two joined relations hold** - `{* EXCLUDE id}`,
  `OBJECT_CONSTRUCT(* EXCLUDE id)`, `HASH(* EXCLUDE id)`, `COUNT(* EXCLUDE id)` over `fz JOIN fy` - is refused as
  `ambiguous column name 'ID'` while the statement compiles, where both columns were dropped; a qualified star and a
  USING or NATURAL key are no ambiguity (live-verified).
- **A read or a RESUME of a materialized view whose base table's name now finds another table** (live-verified): a
  table swapped in under the name, or created anew there, is "not accessible" - `Failure during expansion of view
  'MV': SQL compilation error: error line 1 at position 14` / `Invalid materialized view definition. Source table used
  in the definition is not accessible or been deleted.`, at position 24 for a RESUME - and the view stays invalid for
  that reason, where the newcomer was read. A swap undone before anything read the view leaves it as it was, and SHOW
  MATERIALIZED VIEWS names the source table where it stands now.
- **SHOW, DESCRIBE and GET_DDL of a stage, file format, function, procedure, tag or policy the role does not see**:
  a listing leaves the object out and DESCRIBE and GET_DDL answer it as a missing one, as they already did for
  relations, sequences, streams, tasks and pipes - the role sees an object it owns or holds a privilege on, or every
  object with MANAGE GRANTS (documented). A function or a procedure is seen through its own signature's privileges.
- **A statement only a role the session never activated may run**: every role granted to the session's user
  authorized every statement, whatever USE ROLE and USE SECONDARY ROLES had said, so a user holding R1 and R2 read,
  as R1 with secondary roles NONE, a table granted to R2 alone. Now the primary role and the roles it inherits decide
  alone while no secondary role is in use, `Insufficient privileges to operate on table 'T2'. Your primary role R1
  must have SELECT granted on TABLE Q.PUBLIC.T2.`; secondary roles ALL stand for the user's roles as each statement
  runs, so a role granted later is in use and one revoked is not; and the primary role a USE ROLE leaves stops
  acting, unless USE SECONDARY ROLES named it (documented).
- **Inside a stored procedure that runs with owner's rights, what only its caller may do**: the body runs with its
  owner's privileges and none of the caller's (documented), where the caller's roles were asked beside the owner's.
  IS_ROLE_IN_SESSION() inside such a body looks at the owner's roles, the ones acting there.
- **ALTER ACCOUNT SET and UNSET, ALTER USER and CREATE USER by a role without the privilege** (live-verified): MODIFY
  on the account, `Insufficient privileges to operate on account '<ACCOUNT>'. Your primary role PUBLIC must have
  MODIFY granted on ACCOUNT <ACCOUNT>.`, which ACCOUNTADMIN holds; CREATE USER on the account, which USERADMIN and the
  roles above it hold or a grant gives; and MODIFY on the user, `Insufficient privileges to operate on user 'U'. Your
  primary role PUBLIC must have MODIFY granted on USER U.`, which the role owning the user holds. Each is asked after
  the statement's names and values are judged, and users still change their own DEFAULT_WAREHOUSE, DEFAULT_NAMESPACE,
  DEFAULT_ROLE and session parameters (documented). The engine's own session, the SYSTEM user, is not held to them.
- **A recursive term the account refuses** (live-verified): one of another width than its anchor, `Anchor and
  recursive term in Recursive CTE 'R' must select the same number of columns.`, where ragged rows followed; and one
  holding a window function, an aggregate, DISTINCT, GROUP BY or TOP, `Aggregate functions are not allowed in a CTEs
  recursive term.` and alike, where an aggregate made the recursion endless.
- **A recursive CTE of a shape the account refuses** (live-verified): a CTE read by an operand other than the last,
  or not by the last, `'R' is an invalid Recursive CTE`; a distinct UNION before the recursive term, `UNION not yet
  supported within a Recursive CTE`; and a UNION ALL inside the anchor, `Multiple set operators are not yet supported
  within a Recursive CTE`. Before, an operand that read nothing of the CTE was run again at every step, and the
  statement never ended.
- **A positional UNION, EXCEPT or MINUS arm narrower than the UNION BY NAME layout before it**: `SELECT 1 a UNION ALL
  BY NAME SELECT 2 b UNION ALL SELECT 3` is refused `invalid number of result columns for set operator input branches,
  expected 2, got 1 in branch 2`, counting the arms up to the last BY NAME as one branch (live-verified). Before, its
  rows were appended narrower than the result, and reading them failed with an IndexOutOfBoundsException.
- **A Python function's, aggregate's or table function's answer that its declared type does not take** is refused
  naming the function and its handler, as a procedure's is (live-verified): a text for a DATE (`Return value should be
  a date object in function D_TEXT with handler h`) or a TIMESTAMP, a tuple for an ARRAY, a list for an OBJECT, a
  `Decimal` past a NUMBER's precision, and a text for a BINARY (`Python Interpreter Error: TypeError: cannot convert
  'str' object to bytes`). Before, the answer was handed back as it was. A `datetime` with an offset for a
  TIMESTAMP_NTZ or a TIMESTAMP_LTZ is refused too, `Datetime object must be naive to be convertible to
  TIMESTAMP_NTZ`, by procedures as well.
- **ARRAY_APPEND, ARRAY_PREPEND and ARRAY_INSERT of a semi-structured value into an ARRAY of a structured element
  type** (`ARRAY(ARRAY(INT))`, `ARRAY(OBJECT(a INT))`), or of a scalar, are refused while the statement compiles:
  `Invalid argument types for function 'ARRAY_APPEND': (ARRAY(ARRAY(NUMBER(38,0))), ARRAY)` (live-verified). Before,
  `['zz']` was appended as given under the typed result.
- **A COPY whose stage names a file format that is not there** is refused, `File format 'PF' does not exist or not
  authorized.`, as a query of the stage is. Before, it loaded with the default CSV options.
- **CREATE PIPE without its COPY's privileges.** The role creating a pipe needs READ on the internal stage the COPY
  reads (USAGE on an external one) and SELECT and INSERT on the table it loads, as Snowflake's CREATE PIPE page lists
  them. Before, the pipe was created and only its load was refused later.
- **A grant or a revoke the role may not decide, on every form of GRANT and REVOKE.** A privilege on an object is
  granted by the object's owner, by a role holding that privilege WITH GRANT OPTION, or by MANAGE GRANTS. `GRANT … ON
  ALL …` asks that for every object it reaches, refusing before anything is granted, and `GRANT OWNERSHIP ON ALL …`
  likewise. A future grant and a privilege on the account ask MANAGE GRANTS. `GRANT ROLE` / `REVOKE ROLE` and `GRANT
  DATABASE ROLE` / `REVOKE DATABASE ROLE` are decided by the role's owner or by MANAGE GRANTS, and a system role by
  MANAGE GRANTS alone. These were all accepted from a role holding nothing: such a role could grant itself
  ACCOUNTADMIN, MANAGE GRANTS or OWNERSHIP of a schema's tables. The administrative roles decide every grant, as
  before.
- **A grant by an object's owner in a schema WITH MANAGED ACCESS.** Only the schema's owner, or MANAGE GRANTS, decides
  the grants on the objects in it, future grants included; the object's owner granted on it.
- **A projection policy through UNPIVOT, PIVOT and MATCH_RECOGNIZE.** Over a column whose policy denies projection,
  UNPIVOT's value column, PIVOT's pivoted columns over an aggregate that hands out values (MAX, not COUNT) and a
  MATCH_RECOGNIZE measure over the column (`FIRST(ssn) AS s`) are refused by the policy when projected, and so is the
  column itself where the rewrite passes it through, where they handed the values out.
- **CURRENT_DATE() in SYSTEM$ABORT_TRANSACTION's argument** is no constant: `DATE_PART(year, CURRENT_DATE())` is
  `argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be constant, found 'EXTRACT(year from CURRENT_DATE())'`,
  where the call answered `Bad transaction id`.
- **A text past every NUMBER beside one.** `id < '1e+308'`, `id + '1e+308'` and a text of thirty-nine nines are
  `Numeric value '…' is not recognized`, and `id < '1.7e38'`, `id + '1e38'` and `CAST('1e38' AS NUMBER)` are
  `Numeric value '…' is out of range`, where they compared or answered: a text is read into the signed 128-bit
  carrier, out of range from 10^38 on and unreadable past 2^127 - 1, with an exponent or without.
- **A comma inside a cast's parentheses in a SQL procedure's body, at CREATE.** `BEGIN SELECT UPPER(CAST(1, 2));
  RETURN 1; END` is refused as the procedure is created, with the lines the statement reports alone placed where it
  stands in the body — the `,`, then the `)` after the cast's — and a RETURN's, a LET's or an assignment's value names
  the comma alone, on a branch that never runs too, where the procedure was created and its CALL refused it.
- **A window item under GROUP BY ALL with no aggregate inside.** `SELECT COUNT(*), ROW_NUMBER() OVER (ORDER BY 1) FROM
  fz GROUP BY ALL` is `[ROW_NUMBER() OVER (ORDER BY 1)] is not a valid group by expression` — named by its alias
  where it has one, `[R]` — where it answered.
- **An unknown function in a block's inferred declaration, wherever it stands.** `LET x := nosuch_f(1)` — and a
  DECLARE item with a value and no type — is refused `Unknown function NOSUCH_F.` as the block compiles, ahead of
  every name the block reads, on a branch that never runs and past every handler, where the block ran or the LET was
  refused only when it ran; the value's unknown calls are named in one sentence, and a function the block itself
  creates is no function yet.
- **NULL as a session policy's number.** `SET SESSION_IDLE_TIMEOUT_MINS = NULL` is `invalid value 'null' for property
  …`, where it was a syntax error; `SET COMMENT = NULL` now takes the comment away.
- **An ALTER | MODIFY COLUMN of a column its table lacks inside an open transaction** is refused with the transaction
  still open — a ROLLBACK after it undoes the transaction's rows — where it committed the transaction first.
- **A PIVOT over an aggregate of two arguments.** `PIVOT (OBJECT_AGG(student) FOR subject IN ('Math', 'Art'))` — and
  CORR, the covariances, the regressions, APPROX_PERCENTILE — is `not enough arguments for function
  [OBJECT_AGG(IFF('Math' = FLAGS.SUBJECT, FLAGS.STUDENT, SYSTEM$NULL_TO_TEXT(null)))], expected 2, got 1`, where it
  answered `{}` or NULL in every cell; PERCENTILE_CONT and PERCENTILE_DISC are `argument 1 to function PERCENTILE_CONT
  needs to be constant, found '…'`.
- **A query that reads a dynamic table that never refreshed, wherever the read stands.** A subquery no row reaches
  (`WHERE FALSE AND EXISTS (SELECT 1 FROM d)`), a CASE branch never taken and a view over the table are refused
  `Dynamic Table '…' is not initialized. …` as the query compiles, where they answered.
- **A quoted name in another case for a stream, a task, a policy, a file format or a stage.** DESCRIBE, ALTER, COMMENT
  ON and DROP of `"cstr"` where the stream is CSTR — and so of a task, a masking, row access, projection or
  aggregation policy, a file format or a stage, COMMENT ON a tag or a sequence, and a SET … POLICY attaching a policy
  — refuse it as missing by its full path, `Stream 'TEST_DB.TEST_SCHEMA."cstr"' does not exist or not authorized.`,
  and `SELECT … FROM "cstr"` is `Object '"cstr"' does not exist or not authorized.`, where each reached the object.
- **POLICY_REFERENCES over an EXTERNAL TABLE that is not there.** `REF_ENTITY_DOMAIN => 'EXTERNAL TABLE'` naming no
  table is `External table 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.`, where it answered no rows.
- **ALTER TABLE over a view.** ADD or DROP COLUMN, SET or UNSET COMMENT or CHANGE_TRACKING and SWAP WITH are refused
  `Object found is of type 'VIEW', not specified type 'TABLE'.`, IF EXISTS or not, where IF EXISTS ran them as a
  missing table's; a parameter is refused as the view's to operate on, a column's ALTER and an added constraint
  `cannot use ALTER TABLE on a view; use ALTER VIEW instead`.
- **ALTER VIEW … SET CHANGE_TRACKING = TRUE over a query change tracking cannot follow** is refused as CREATE VIEW …
  CHANGE_TRACKING = TRUE refuses it (`Change tracking is not supported on queries with 'VALUES'. (inside view …)`),
  where it ran; once a view's change tracking is turned off, a stream on it is refused.
- **A correlated subquery reading a set operation's column carried from the outer row.** `(SELECT MIN(v) FROM
  (SELECT t.id AS v UNION ALL SELECT 'z'))` — and the same through a CTE, a column list, or a SQL function called over
  a column — is refused `Unsupported subquery type cannot be evaluated`, where it was answered; COUNT(*), which reads
  no column, is still answered.
- **A bare name in a session whose current schema was renamed away.** A read misses it as an object not there
  (`Table 'MC' does not exist or not authorized.`), and a CREATE, DROP or TRUNCATE is `Cannot perform CREATE
  TABLE. This session does not have a current schema. Call 'USE SCHEMA', or use a qualified name.`, where both were
  refused for the vanished schema; CURRENT_SCHEMA() answers NULL.
- **A RETURNS TABLE column written as a table's column.** `RETURNS TABLE (a INT NOT NULL)` — and NULL, COMMENT,
  COLLATE, a key or a constraint, AUTOINCREMENT, UNIQUE, a tag, or a `:=` default — is refused where the option stands,
  where the function or procedure was created; a column without a type is `Return signature missing data type for
  column 'B'.`, and one without a DEFAULT after one with it `required argument B cannot follow optional argument A`.
- **A query over a table's stage past its table's width.** `SELECT $2 FROM @%t` over a one-column table is an
  invalid identifier, and a record of another width than the table's is refused as a load refuses it, `Number of
  columns in file (1) does not match that of the corresponding table (2), …`, where the file's fields were read.
- **A star over a named stage's CSV files.** `SELECT * FROM @s` is `SELECT with no columns`, and ORDER BY 1 over it
  `[1] is not a valid order by expression`, where the fields were answered.
- **A date with a time no AUTO form reads.** An MM/DD/YYYY date takes only an HH24:MI:SS time after it, and a
  DD-Mon-YYYY date none: `'05/03/2024 10:00'::DATE`, TO_DATE and TO_TIMESTAMP of a time without seconds, with a
  fraction, a zone or a T are refused, `Date '05/03/2024 10:00' is not recognized`, and `TO_DATE(x, 'AUTO')` `Can't
  parse '05/03/2024 10:00' as date with format 'AUTO'`, where they read the date; the TRY_ forms answer NULL.
- **BASE64 text that is not whole padded groups.** `TO_BINARY(x, 'BASE64')`, and a cast or a column write under
  `BINARY_INPUT_FORMAT = BASE64`, refuse unpadded text (`'SGk'`), padding inside the text, the URL-safe digits, a
  tab, a line break and a NUL, `The following string is not a legal base64-encoded value: 'SGk'`, where unpadded
  text decoded and the refusals were worded by the JDK.
- **A file format's unknown BINARY_FORMAT.** `CREATE FILE FORMAT f TYPE = CSV BINARY_FORMAT = FOO`, and ALTER FILE
  FORMAT alike, is refused `Invalid binary format string 'FOO': Must be 'HEX', 'BASE64', or 'UTF-8'`, where it was
  recorded.
- **CREATE STREAM … CLONE of a table stream whose table is gone** is refused, `Table must be present to create/clone
  stream`, where it made a stream reading whatever table now held the name.
- **A Java routine's PACKAGES the account does not offer.** `PACKAGES = ('com.snowflake:snowpark')` — a package without
  a version, an unknown version or package, a name of more or fewer parts — is refused as the routine is created,
  `Package 'com.snowflake:snowpark' is not supported.`, ahead of its handler; a listed version or `latest` is taken.
- **A handler class an inline Java body declares in a package.** `package p; class H` with `HANDLER = 'p.H.f'` is
  refused `java.lang.ClassNotFoundException: p.H` with the class loader's frames, where the routine was created.
- **A Java routine whose IMPORTS do not hold its handler class.** A stage directory that holds nothing is refused as
  the routine is created, `java.lang.ClassNotFoundException: JS`, and a handler that names no method `Invalid
  function name JS …`, where the routine was created and failed at its first call.
- **A ROWS window frame of more than a thousand rows.** `ROWS BETWEEN 1000 PRECEDING AND CURRENT ROW` is refused while
  the statement compiles, `Unsupported window frame: window frame size of 1001 rows exceeds limit (1000 rows)`, where
  it ran; ARRAY_AGG takes any frame, and a RANGE frame has no limit.
- **A handler function's call in a VALUES row.** `INSERT INTO t VALUES (jcnt(1))` over a Java, JavaScript, Python or
  Scala function is refused `Invalid expression [EXTENSION_FUNC_V(1)] in VALUES clause`, the item echoed as the
  plan prints it, where the row was written. A SQL function's call is still taken.
- **A JavaScript statement's `sqlText` that is no text.** `snowflake.createStatement` and `snowflake.execute` refuse
  a number, a boolean, an object or an array, `Argument with invalid type number passed`, and createStatement a
  `binds` that is no array, `Invalid binds argument`, where they ran the value's text.
- **A TIME given where a timestamp overload stands beside a text one.** A TIME argument reaches a timestamp
  parameter of any kind before a VARCHAR one — by position and by name, of a procedure and of a function — and is
  refused there, `incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]`, as the account refuses it, where the
  VARCHAR overload ran with the time's text.
- **A Snowpark Java handler collecting a whole number no Long holds.** `s.sql("SELECT 12345678901234567890")
  .collect()` fails with the refusal of the driver under Snowpark, a `java.sql.SQLException` of SQLSTATE 0A000 and
  code 200038, `Cannot convert value in the driver from type:FIXED(null,null) to type:Long,
  value=12345678901234567890.`, for any NUMBER column of no scale, where the row held a BigDecimal. A 19-digit
  whole number within Long's bounds, `-9223372036854775808` among them, is now handed over as a Long.
- **AT or BEFORE on a materialized view.** `SELECT … FROM mv AT (OFFSET => -1)` and every other point is refused
  `Failure during expansion of view 'MV': SQL compilation error: Time travel is not supported for materialized
  views.`, as the account refuses it, where the clause was ignored and the current rows answered.
- **OBJECT_PICK and OBJECT_DELETE keys over a structured OBJECT; an ARRAY of keys beside others.** Over a
  structured OBJECT every key must be a string literal naming one of its fields: an ARRAY, a VARIANT, an
  expression or a NULL there is refused `Argument number 1 for function 'OBJECT_PICK' needs to be a string
  literal.`, and a literal naming no field `Function OBJECT_PICK: expected structured object to contain field zz
  but it did not.` whatever rows the statement reads. `OBJECT_PICK(o, ARRAY_CONSTRUCT('a'), 'b')` and a
  structured ARRAY of keys are refused by their argument types.
- **A (+) marker beside an explicit outer join.** `FROM d LEFT JOIN p ON …, e WHERE e.k(+) = p.k` is refused
  `(+)-style outer join not allowed with from-clause outer join: 'E.K(+)'`, where the marker was ignored.

- **A date-looking text beside a number; a loaded number too wide for its column.** `'2024-01-15' + 1`, `- 1` and
  `1 + '2024-01-15'` are refused `Numeric value '2024-01-15' is not recognized`, where the text was read as a DATE.
  A COPY load refuses a field too wide for its NUMBER column as an INSERT of it is refused, `Numeric value '12345'
  is out of range`, so the record is skipped or the load aborted under its ON_ERROR, where the number was written.

- **CALL arguments no conversion carries; integer getters over scaled numbers.** A CALL refuses, while it
  compiles, an ARRAY, an OBJECT or a BINARY for a DATE, BOOLEAN, FLOAT, timestamp or TIME parameter, and a BOOLEAN,
  a FLOAT, a DATE, a timestamp or a TIME for an OBJECT, ARRAY or BINARY one — `Invalid argument types for function
  'P': (ARRAY)` — where the value failed later or was taken; a VARIANT parameter refuses a TIME, a timestamp and a
  BINARY, and a predicate (`1 = 1`, `x IS NULL`) is refused by every parameter but a BOOLEAN one, while a BOOLEAN
  value binds. A TIME for a timestamp parameter is `incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]`. The JDBC
  driver refuses `getLong` and `getInt` over a NUMBER of a scale above 0, `Cannot convert value in the driver from
  type:3 to type:long, value=3.00.`, as the account's driver does.

- **ATANH and ACOSH outside their domain.** ATANH of a value outside [-1, 1] and ACOSH of one below 1 are refused
  `Invalid floating point operation: atanh(2)` / `acosh(0)`, in a query and in a block, where they answered NaN.

- **TRUNC and TIME_SLICE operands the account refuses.** TRUNC with a date part over a BOOLEAN, a text, an OBJECT,
  an ARRAY, a BINARY or a VARIANT is refused `Function TRUNC does not support BOOLEAN argument type`, where a number
  was expected or a conversion failed; TIME_SLICE of a DATE by a clock unit is refused `['HOUR'] is not a valid
  date/time component for function TIME_SLICE and type DATE.`, where the date was answered, and a slice length that
  is a text `invalid type [VARCHAR(1)] for parameter 'slice-length'`.

- **A malformed enclosure in a staged-file query.** A CSV field whose closing enclosure is followed by anything but
  a delimiter is refused `Found character 'b' instead of field delimiter '|'`, naming the file, line, character,
  row and column (`"TRANSIENT_STAGE_TABLE"["$2":2]`), where the field was read leniently.

- **A bare dual beside a CTE of that name; moving a stream or a paused pipe without its table's privilege.** In
  `WITH dual AS (…) SELECT … FROM dual` the bare word is the pseudo-table — its one column holds NULL and no
  qualifier reaches it — so `dual.column1` and the CTE's own columns are invalid identifiers, where the CTE answered;
  `"DUAL"` still reads the CTE. GRANT OWNERSHIP ON STREAM by rights without SELECT on the stream's table is refused
  `To grant use role with SELECT privilege on the target table TEST_DB.TEST_SCHEMA.T1`, and of a paused pipe without
  INSERT on the table it loads `Pipe may have pending files that could be loaded into table when resumed. To do
  grant, use role with INSERT privilege on the target table T2`; each moved the object.

- **Another kind's parameters on a plain table, an untrackable query under CHANGE_TRACKING, a dynamic table without
  a base table.** CREATE TABLE refuses the parameters of other kinds of table as the account words each — CATALOG
  (`Catalog integration cannot be set for legacy iceberg tables`), EXTERNAL_VOLUME (`EXTERNAL_VOLUME is not
  supported on table TQ2.`), CATALOG_SYNC, STORAGE_SERIALIZATION_POLICY, TARGET_FILE_SIZE, PURGE_ON_DROP_TABLE,
  REPLACE_INVALID_CHARACTERS and a STAGE_FILE_FORMAT naming no file format — and a CLONE refuses a CLUSTER BY
  (`Invalid clustering key A`); each created the table. A view created with CHANGE_TRACKING = TRUE is refused the
  first construct of its query that change tracking cannot follow, at the place the account names: a query block
  without FROM or a VALUES list (`Change tracking is not supported on queries with 'VALUES'.`), a table function, a
  scalar, EXISTS or IN subquery (`… with subquery expressions.`), or one of these in a view it reads (`(inside view
  'DB.SCHEMA.V')`); it was created. A dynamic table whose query reads no base table — no relation at all, or only
  common table expressions — is refused `Dynamic Tables must have at least one base table.` once its name, its
  warehouse and its body pass; it was created.

- **A derived table's outer aggregate read where it is no statistic.** Where the select over a derived table reads an
  aggregate over the outer row alone outside its own aggregates — `(SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x
  FROM g) WHERE x = 7)` — the subquery is refused as unsupported, and where it reads it inside them beside a filter, a
  grouping, another relation or a derived WHERE with an equality to the outer row, or reads a SUM, an AVG or a MIN
  of a text, it is "Aggregate functions cannot be nested: [MAX(FZ.ID)] nested in [SUM(CORRELATION(CAST(MAX(FZ.ID) AS
  NUMBER(38,0))))]"; each failed as an invalid identifier. A GRANT of a privilege an external volume does not take
  is refused: `Invalid object type 'EXTERNAL_VOLUME' for privilege 'SELECT'.`

- **A value an Iceberg table's int or long cannot hold, and a type change the format does not promote.** An INSERT, an
  UPDATE or a MERGE writing a number past 32 bits to an INT or INTEGER column of an Iceberg table, or past 64 bits to a
  BIGINT one — a member of a structured column's value too — is refused "Number out of representable range: type int,
  value 2147483648", outside the DML envelope; it was stored. ALTER ICEBERG TABLE … ALTER COLUMN … SET DATA TYPE
  takes an int to a long, a float to a double and a decimal to a wider one of its scale, and refuses every other change
  in the format's names — `cannot change column A from type long to decimal(19, 0)` — where a NUMBER(19,0) or a
  NUMBER(5,0) target was taken; a column a query or LIKE gave the table changes to its own type alone.

- **A membership the account cannot plan.** A NOT IN over a query that reads nothing of the outer row is refused
  outside the WHERE — in the select list, a CASE there, the HAVING and the ORDER BY — where the query's one column may
  hold NULL ("Unsupported subquery type cannot be evaluated", at the query's SELECT): `SELECT id NOT IN (SELECT gn.id
  FROM gn) FROM fo`; one over a column declared NOT NULL, over a literal or over a query the statistics prove empty
  stays answered. A correlated IN over an aggregate in a select list is refused for an OR, an inequality or an outer
  name alone in its filter, and for an item such as `COALESCE(SUM(g.v), 0)`. Each was answered.

- **LIKE over a value that is no text.** LIKE, ILIKE, LIKE ANY, LIKE ALL and ILIKE ANY refuse a subject or a pattern
  that is an ARRAY, an OBJECT, a MAP, a BINARY or a VECTOR, and LIKE ANY a ROW beside another pattern, as the account
  does — `'a' LIKE ANY ['a']` is "Invalid argument types for function 'LIKE_ANY': (VARCHAR(1), NULL, ARRAY)", `'a'
  LIKE x'61'` names 'LIKE' for (VARCHAR(1), BINARY(1)) — where they answered FALSE or TRUE. LIKE ANY's query is
  matched as LIKE matches its rows: one of several columns, or of an ARRAY, is refused naming 'LIKE'.
- **A quantified comparison's query the account cannot run.** `id = ANY (SELECT g.id FROM g WHERE g.id = fz.id LIMIT
  1)`, `id <> ALL (…)`, `id > ANY (…)` and the other operators are judged as the IN is, and refused where it is
  ("Unsupported subquery type cannot be evaluated", placed at the quantifier), where they were answered, or failed as
  an invalid identifier over an aggregate of the outer row.

- **A pivot over an aggregate its column's type does not take, and a private host-and-port network rule's values.**
  `PIVOT (SUM(b) …)` or `AVG(b)` over a BOOLEAN is refused with `SQL compilation error: error line 0 at position -1` /
  `Invalid argument types for function 'SUM': (BOOLEAN)`, and `MIN(a)` or `MAX(a)` over an ARRAY with `Function MIN
  does not support ARRAY argument type`, as a grouped query refuses them; they used to compute a value per cell.
  A `TYPE = PRIVATE_HOST_PORT` network rule refuses every value it is given with `invalid value '[<value>]' for
  property 'VALUE_LIST'` — no form the account takes was found — and keeps taking an empty list.
- **A read of a dynamic table that never refreshed.** A table created with `INITIALIZE = ON_SCHEDULE` holds nothing
  until its first refresh, and every query that reads it — a SELECT however filtered or limited, a join, a subquery, a
  view, a stream, a CTAS or an INSERT … SELECT — is refused with `Dynamic Table '<DB>.<SCHEMA>.<NAME>' is not
  initialized. Please run a manual refresh or wait for a scheduled refresh before querying.`, as the account refuses
  it. CREATE VIEW, CREATE STREAM and DESCRIBE over it are taken, and a clone or a rename of it is as uninitialized.
- **A Pub/Sub notification integration the account would not take.** `GCP_PUBSUB_SUBSCRIPTION_NAME` has to read
  `projects/<project>/subscriptions/<subscription>` with a project id of six characters or more (`invalid value
  ['projects/p/subscriptions/s'] for parameter 'GCP_PUBSUB_SUBSCRIPTION_NAME'`), and `DIRECTION = OUTBOUND` with
  `NOTIFICATION_PROVIDER = GCP_PUBSUB` is refused with `invalid value 'OUTBOUND' for property 'Direction'`, as the
  AWS account the engine emulates refuses it.
- **A column a table that holds rows cannot fill, and a hybrid table without a primary key.** ALTER TABLE ... ADD
  COLUMN on a table with rows refuses a NOT NULL column without a non-null default (`Non-nullable column 'D' cannot
  be added to non-empty table 'T' unless it has a non-null default value.`) and an AUTOINCREMENT, IDENTITY or sequence
  default (`Cannot add column 'K' with non-constant default to non-empty table 'T'.`), judging every column of the
  statement before adding any; it used to leave NULLs in a NOT NULL column. An empty table takes all of them, a
  sequence default included, which used to be refused. `CREATE HYBRID TABLE` without a PRIMARY KEY is refused with
  `A hybrid table must contain primary key.`
- **A file format's ENCODING outside the account's character sets, and a Boolean option that is no bare TRUE or
  FALSE.** `ENCODING = 'latin1'`, `= 'CP1252'` or `= ''`, and `TRIM_SPACE = 'yes'`, `EMPTY_FIELD_AS_NULL = 1` or
  `REPLACE_INVALID_CHARACTERS = 'TRUE'`, are refused at CREATE and ALTER FILE FORMAT with `invalid value [<value as
  written>] for parameter '<NAME>'`, as the account refuses them. An ENCODING may name a set by the account's name
  (`ISO88591`) or the set's own (`ISO-8859-1`), in any case. A COPY's inline ENCODING is judged alike.
- **A window frame no rows can make, and an offset that is no literal.** A start past its end (`ROWS BETWEEN 1
  FOLLOWING AND CURRENT ROW`, `RANGE BETWEEN 1 PRECEDING AND 2 PRECEDING`, a lone `ROWS 1 FOLLOWING`), an UNBOUNDED
  FOLLOWING start, an UNBOUNDED PRECEDING end and a ROWS offset of `1.5` are now `Invalid window frame` at that
  bound when the statement compiles, ahead of every other rule of the window; they used to answer empty frames.
  A frame offset is an unsigned number or an `INTERVAL '…'` constant: a column, a sign, a parenthesis, a bind, a
  string or `NULL` is a syntax error at the offset.
- **A distinct UNION in a recursive CTE.** `WITH RECURSIVE r AS (SELECT 1 UNION SELECT … FROM r)` is `SQL
  compilation error: UNION not yet supported within a Recursive CTE`, in a view's body too; a RECURSIVE view's
  body still takes one, and its recursion ends when a step meets no row it has not met.
- **An IMPORTS entry the stage does not hold, judged at CREATE.** An import that names a stage and no file below it
  (`'@stg'`, `'@stg/'`, `'@~'`) is refused first — `IMPORT must specify the path of a file on a stage, but only the
  stage name 'stg' was provided.` — then a stage that does not exist, a table's stage among them, which is there
  while its table is, and then a file a user's, a table's or an internal stage does not hold, named by its path
  below the stage as written: `Remote file 'dir/a.jar' was not found. …`. A function and a procedure of any
  language are refused so, ahead of their runtime version and packages.
- **Snowpark in a Java routine's inline body without `PACKAGES = ('com.snowflake:snowpark:…')`.** The body compiles
  against Snowpark only when the routine names it, at any version and in any case; without it a Snowpark type is
  the compiler's `cannot find symbol` and `com.snowflake.snowpark_java` and `com.snowflake.snowpark` are packages
  that do not exist, in the compiler's own report.
- **A Java handler that names no class, and an instance handler's protected constructor.** `HANDLER = 'f'` is
  `Invalid function name f in function F with handler f`, for a function and a procedure; a class whose
  zero-argument constructor is protected is refused for an instance handler as a private one is.
- **A Java handler's `java.util.List` parameter for anything but an OBJECT.** A list was taken for any type and the
  CALL failed with `argument type mismatch`; it is paired as a map is, and a VARIANT, a VARCHAR or an ARRAY for it
  is refused as the procedure is created — `Snowflake type ARRAY[LOB](38,0){nullable} is not supported as input
  to argument at index 0 with Java type java.util.Map<String, String> in function F with handler H.run`, the
  account's own words for the map.
- **A name nothing declares, inside a call's argument in a block.** `BEGIN IF (FALSE) THEN RETURN NVL(nosuch, 'x');
  END IF; RETURN 1; END;` answered 1, and the name was found only when its statement ran, as an
  EXPRESSION_ERROR. The block is refused while it compiles, `invalid identifier 'NOSUCH'` at the name's own place
  and bare — in a nested call, a condition, a loop's bound, a handler and a DECLARE's default alike, and ahead of a
  later name outside the call. A lambda's parameter is no name of the block's, so `TRANSFORM([1], x -> x + 1)` in a
  block's own expression is refused for `X`. The word standing where DATEADD, DATEDIFF, DATE_TRUNC, DATE_PART and
  their aliases read a unit is left to the call.
- **A Snowpark `Row`'s accessors cast, they do not convert.** A Java handler's `row.getString(0)` over a number
  answered its digits, `getInt` read a text, `getBoolean` a number: each now fails the handler as on the account —
  a `ClassCastException` for `getString`, `getBoolean`, `getDecimal`, `getDate`, `getTime`, `getTimestamp`,
  `getBinary`, `getList` and `getMap`, and `Error Code: 0400, Error message: Cannot cast java.lang.String(a) to
  Int.` for the number accessors, which read a whole number their type can hold (`getByte` over 300 is refused)
  and, for `getDouble` and `getFloat`, a FLOAT. `Row.toArray()` and `Row.getStruct()`, which Snowpark does not
  have, are gone: a handler that calls one no longer compiles.
- **A sign after a sign in a Snowscript function's block.** `… AS $$ BEGIN RETURN - -a; END $$` created the
  function; it is `invalid function '-'` as the function is created, named by the outer sign, as a query's is.
- **A procedure's body that opens with a name, or reads a `$name` nothing set.** `… LANGUAGE SQL AS $$x := 1;$$`
  created the procedure: a body written without a block that opens with a name opens with no statement, and is
  refused at that name (`syntax error line 1 at position 0 unexpected 'x'.`). A body of one statement that names
  a session variable which does not exist as the procedure is created is refused for it, as a block's is, and a
  `CALL` written without a block and without its semicolon at the `END` of the frame the account compiles it in.
- **`DESCRIBE TAG`.** It is `Unsupported feature 'TAG'.` whether or not the tag exists, by any name, with any
  property, inside a block and through EXECUTE IMMEDIATE, as on the account; `SHOW TAGS` reads a tag back.
- **An infinity bound into a block's statement.** A FLOAT variable may hold NaN and the infinities, but a
  statement of the block cannot bind an infinity: `Invalid bind value (inf) for type (REAL).` while it compiles.
  A NaN binds.
- **A bare name as a VALUES row's item, and a date part's word outside a unit's place.** `INSERT … VALUES (x)`
  with a name that is no value is an invalid identifier at its place; `UPPER(ms)`, `COUNT(day)` and
  `TRUNC(d, month)` read the word as a name like any other, refused when no column has it. A date part's word is
  a word only where a date function's unit stands.
- **A SELECT … INTO whose select list writes another number of values than it has targets** is refused while
  the block compiles; more than one row is `A SELECT INTO statement expects exactly 1 returned row, but got <n>.`
- **A context word written without parentheses in a block's own expression.** `CURRENT_DATE`, `CURRENT_TIME`,
  `CURRENT_TIMESTAMP`, `CURRENT_USER`, `LOCALTIME` and `LOCALTIMESTAMP` are names there, refused as invalid
  identifiers while the block compiles.
- **A `:name` in a cursor's query.** A cursor's query binds none of the block's variables: the name is refused
  when the cursor opens, as a bind nothing sets. Only a question mark is bound, by `OPEN … USING`.
- **A row count that is no whole number of rows.** A LIMIT, an OFFSET or a FETCH count with more digits than 64
  bits hold, and a block's variable bound there that is negative, a text, a FLOAT or a BOOLEAN, is an invalid row
  count; a SAMPLE's percentage, size and seed are held to their ranges while the statement compiles.
- **A SHOW's LIMIT of 0, or past 10,000.** A page size runs from 1 to 10,000: zero is refused while the statement
  compiles, before its scope is looked up, and a size past 10,000 once the scope is found. What a SHOW's shape
  alone rules out is refused while a block compiles, before any statement of it runs.
- **`||` over numbers or a NULL as a predicate.** It is VARCHAR(134217728), so a WHERE or a HAVING of one is
  refused as a predicate that is no BOOLEAN.
- **A GENERATOR's ROWCOUNT that is a text or a BOOLEAN** is refused by its type while the statement compiles,
  and a negative one in the account's sentence; a NULL bound is a bound not given.
- **The IS_ and AS_ functions over what is no VARIANT.** The type predicates of a VARIANT, its casts that answer
  NULL for another kind and STRIP_NULL_VALUE refuse a text, a date, a time, a timestamp and a BINARY by the
  argument types; AS_INTEGER, AS_DECIMAL, AS_NUMBER and the AS_TIMESTAMP casts take a VARIANT and nothing else.
- **A frame the ranking and navigation functions do not take.** ROW_NUMBER, NTILE, CUME_DIST, PERCENT_RANK, LAG,
  LEAD and the event counters refuse a cumulative or a sliding ROWS frame and a RANGE frame; RANK and DENSE_RANK
  the same RANGE frames. NTILE's count is at least 1.
- **`TO_QUERY` of anything but one query or one CALL**, a write in a procedure called in a FROM clause, and a
  SET or an UNSET of a session variable in an owner's-rights procedure. An EXPLAIN carries nothing out: a
  procedure in its statement's FROM clause does not run and a sequence it reads does not move.
- **A text of two statements that counted as one.** The one-statement gate counts the statements a text runs
  as, off the parse the run reuses: `SELECT 1 AS a$$b; DELETE …` is two, as are a statement after a line comment
  that a carriage return ends and one behind a block's word used as a name.
- **A NULL typed from what stands beside it as `SYSTEM$ABORT_TRANSACTION`'s argument, and an ARRAY as
  `SYSTEM$WAIT`'s.** `SYSTEM$ABORT_TRANSACTION(NVL(NULL, 12))` and `(NULL + 1)` answered `Bad transaction id`
  or NULL; the plan types such a NULL with a conversion it cannot fold, so the argument is no constant:
  `argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be constant, found 'NVL(SYSTEM$NULL_TO_FIXED(null),
  12)'`. `SYSTEM$WAIT(ARRAY_CONSTRUCT(1))` waited; it is `Invalid argument types for function 'SYSTEM$WAIT':
  (ARRAY)`.
- **`SYSTEM$LOG` and `SYSTEM$LOG_<level>` called as functions.** `SELECT SYSTEM$LOG_INFO('m')` and
  `CALL SYSTEM$LOG('info', 'm')` answered NULL. They are statements of a Snowflake Scripting block and no
  functions: a query or a CALL that names one is refused, `Unknown function SYSTEM$LOG_INFO`, over no rows and
  inside `SYSTEM$TYPEOF` too. A statement of that spelling under a name that names none
  (`SYSTEM$LOG_NOSUCH('m');`) no longer compiles.
- **A number written `0x…`.** `SELECT 0x3` was read as `0 AS x3` and `SELECT 0x` as `0 AS x`. A hexadecimal
  number is one token no statement reads, refused where it stands (`unexpected '0x3'`), and the prefix alone is
  a number that stops: `parse error line 1 at position 9 near '<EOF>'.`
- **A value outside a mathematical function's domain.** `ACOS(2)`, `ASIN(-1.5)`, `LN(0)`, `LOG(1, 5)`,
  `LOG(10, 0)`, `SQRT(-1)` and `POWER(-8, 1/3)` answered NaN, an infinity or NULL. Each is refused in the
  account's words, the operation named: `Invalid floating point operation: acos(2)`, `log(e,0)`, `log(1,5)`,
  `sqrt(-1)`, `pow(-8, 0.333333)`. `COT(0)` is `Infinity`, as on the account.
- **`FACTORIAL` outside 0 … 33, a negative shift count, a character code that names no character.**
  `FACTORIAL(34)` is `Numeric value is out of range, error: 34 is too large for factorial function.`,
  `FACTORIAL(-1)` is `… Input of factorial function can't be negative. -1 is invalid.`,
  `BITSHIFTLEFT(1, -1)` is `Invalid parameter value: -1. Reason: shift parameter must be greater or equal to
  zero`, and `CHR(-1)`, `CHR(1114112)` and a surrogate's code are `Invalid character code -1 in the CHR input`.
- **A key written twice in `OBJECT_CONSTRUCT`, and `OBJECT_KEYS` over what is no object.**
  `OBJECT_CONSTRUCT('a', 1, 'a', 2)` kept the last value; it is `Duplicate field key 'a'`.
  `OBJECT_KEYS(PARSE_JSON('[1]'))` answered NULL; it is `Failed to cast variant value [1] to OBJECT`.
- **A task named by a number.** `SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS(1)` looked for a task named 1; the
  name is a constant text: `argument 1 to function SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS needs to be
  constant, found 'CAST(1 AS VARCHAR(134217728))'`.
- **A field read from a FOR variable that is no record.** `FOR r IN (SELECT v FROM t) DO x := r.v; END FOR;` ran
  no iteration, and `FOR r IN 1 TO 2 DO x := r.v; END FOR;` answered NULL. The variable of a FOR over a cursor or
  a RESULTSET is a record; over anything else it has no fields, and a field read from it is refused while the
  block compiles, on a branch that never runs too: `invalid identifier 'R.V'` for a loop over a value in
  parentheses, and the counter alone, `invalid identifier 'R'`, for a loop over a range.
- **`TOP` with a bind in a RESULTSET's query.** `res := (SELECT TOP :s v FROM t)` took the variable's value as
  the count. TOP takes a number written out: before a colon it is a name and the colon a path's, so the query
  is refused as an account refuses it, `invalid identifier 'TOP'`.
- **A fraction as `SPLIT_PART`'s part.** `SELECT SPLIT_PART('a,b,c', ',', 1.6)` answered `b`, the part rounded.
  An exact NUMBER with a fraction there — a literal, a NUMBER(4,2) column, a cast to one, `1 + 0.6` — is now
  refused while the statement compiles, over a table with no row too: `argument 3 to function SPLIT_PART needs to
  be an integer, found: '1.6'`, the argument echoed as the plan prints it (`'T.N'`, `'CAST(1.6 AS NUMBER(4,2))'`).
  What is computed from literals is computed first, so a whole number written with a fraction is still read
  (`2.0`, `1.5 + 0.5`, `2::NUMBER(4,1)`) and a NULL answers NULL (`NULL::NUMBER(4,2)`); so is the one whole value
  a column's statistics leave an expression. A FLOAT, a text and a VARIANT are read and rounded as before.
- **A BINARY given to an ARRAY parameter.** `DECLARE b BINARY; BEGIN CALL pa(:b); … END;` ran where `pa` takes an
  ARRAY. It is refused as the other families an ARRAY parameter does not take are: `Invalid argument types for
  function 'PA': (BINARY(67108864))`.
- **A cursor declared over a name that is no RESULTSET.** `DECLARE c CURSOR FOR nosuch; BEGIN … END;` ran, and
  `OPEN c` over the missing name did nothing. The name a cursor is declared over must be a RESULTSET declared
  before the cursor, in its block or in one around it; any other name is refused while the body compiles, at the
  name, whether the cursor is opened or not — `invalid identifier 'NOSUCH'`: a name nothing declares, a variable
  that is no RESULTSET, the cursor's own name, a RESULTSET declared after the cursor and a table's name alike,
  in the DECLARE section and in `LET c CURSOR FOR …`. A procedure with such a cursor is created and refused when
  it is called.

- **A NULL, or a value that is no constant, in seven argument places.** Each answered NULL or a default where an
  account refuses the call:
  - `GET(arr, NULL)` and `arr[NULL]` over an ARRAY, for an index the plan folds to NULL — the bare word,
    `NULL::INT`, `NULL + 1`, `TO_NUMBER(NULL)` — are `Invalid extraction path 'Extract path index cannot be null':
    invalid number at position {1}.` (the account's own sentence, placeholder included). A NULL read from a
    column or answered by a condition, a NULL text and a VARIANT or OBJECT read with a NULL still answer NULL.
  - `TO_CHAR(x, NULL)` and a format of any type but text (`TO_CHAR(1, 5)`, a BOOLEAN, a date, a VARIANT) are
    `Format argument for function 'TO_CHAR' needs to be a string`, named as the call is written; `TO_CHAR(TRUE,
    'x')` and `TO_CHAR(NULL, NULL)` are refused for their count of arguments.
  - `SHA2('a', n)`, with a size that is a column, a typed NULL or an expression over an untyped NULL, is
    `argument 2 to function SHA2 needs to be constant, found 'T.N'`, over a table with no row too.
  - `RANDSTR(NULL, g)` is `Invalid parameter value: NULL. Reason: length must not be NULL` and a negative length
    `… length must not be negative`, where it was a NULL and an internal error.
  - `UNIFORM(NULL, b, g)` / `UNIFORM(a, NULL, g)` are `… a must not be NULL` / `… b must not be NULL`, and a first
    bound above the second `Invalid parameter value: 2, 1. Reason: a must be smaller than or equal to b`, where
    it answered the first bound.
  - `NTH_VALUE(x, NULL)` is `Invalid value [null] for function 'NTH_VALUE', parameter 2: n in nth_value cannot be
    null`, `NTH_VALUE(x, 0)` `… N (i.e., second argument) cannot be zero.`, a negative n `Invalid parameter value:
    -1. Reason: Second argument of the function must be a positive integer.`, and an n that is no whole number
    written out — a column, `1 + 1`, `1.5` — `argument 2 to function NTH_VALUE needs to be constant, found '…'`.
  - `LAG` and `LEAD` take a whole number written out as their offset: a column, a typed NULL or `1 + NULL` is
    `argument 2 to function LAG needs to be constant, found 'T.N'`, and the bare word NULL, a fraction, a text,
    `1 + 1` or `ABS(1)` `argument 1 to function LAG needs to be constant, found 'SYSTEM$NULL_TO_FIXED(null)'`,
    each echoed as the plan prints it. `LAG(a, n)` over a column was `invalid identifier 'N'`.

- **A SQL function whose table is gone, where no row calls it.** A SQL function's body ran only when a row
  called it, so `SELECT f() FROM t` over an empty `t`, `SELECT * FROM t WHERE a = f()`, `IFF(FALSE, f(), 1)`,
  `… LIMIT 0` and a table function joined to an empty table all ran although the table `f`'s body reads had been
  dropped. An account expands the body into the statement while it compiles and refuses it — `Object
  'DB.S.UE' does not exist or not authorized.` — in a query, an INSERT … SELECT, an UPDATE, a DELETE and a MERGE,
  and a view, a table (`CREATE TABLE … AS`) and a function created over such a call are no longer created. A
  function reached through another is refused alike, and a view the body reads is expanded under its own name.
  The statement's own refusals — a column it does not have, a call's argument count — still speak first.

- **A bare name as the scope of a security listing is a schema.** `SHOW SECRETS IN <name>`, `SHOW PASSWORD
  POLICIES IN <name>` and `SHOW NETWORK RULES IN <name>` listed a DATABASE of that name when the current database
  had no such schema. The name is a schema of the current database and nothing else: `Schema 'DB.NAME' does not
  exist or not authorized.` (a network rule listing: `Object does not exist, or operation cannot be performed.`).

- **The tag functions of what a policy protects, outside a policy.** `SYSTEM$GET_TAG_ON_CURRENT_TABLE('t')` and
  `SYSTEM$GET_TAG_ON_CURRENT_COLUMN('t')` answered NULL wherever they were written. Outside a policy's body they
  are refused while the statement compiles, as on an account — `Invalid system$get_tag_on_current_table function
  invocation.It can only be invoked from a masking policy/row access policy.` and `Invalid
  system$get_tag_on_current_column function invocation. It can only be invoked from a masking or projection
  policy.` — over a table, in a WHERE clause, in a block (bare, past every handler) and in a view, a function or
  a procedure that only stores the call, which is no longer created. The tag's name is judged first: no argument or
  two is the count's sentence, a column `Invalid value [A] for function …, parameter 0: constant arguments
  expected`, a NULL `argument 0 to function … needs to be constant, found 'null'`, a number `argument needs to be
  a string: …`. A row access policy that reads the column's tag, a projection policy that reads the table's and
  an aggregation policy that reads either are refused as they are created, and so is a policy naming a tag that
  is not there.

- **SYSTEM$GET_TAG compiled with what holds it.** A procedure or a function whose body calls
  `SYSTEM$GET_TAG` over a tag, an object or a domain that is missing was created and failed when called; it is
  refused at CREATE. In a block's own expression a `:name` among the arguments is refused where it stands
  (`Bind variable null not set.`; in a procedure's body `Bind variables not allowed in view and UDF
  definitions.`), an argument that reads a variable inside an expression is `argument 1 to function
  SYSTEM$GET_TAG needs to be constant, found 'null'`, and a function's parameter for an argument `Invalid value
  [CORRELATION(null)] for function 'SYSTEM$GET_TAG', parameter 1: constant arguments expected`.

- **A USING value bound where the statement takes no text.** A USING list — a cursor's `OPEN c USING (x)` and
  `EXECUTE IMMEDIATE … USING (x)` — hands each value over as its text, as an account does, so what ran with the
  value's own type is refused in the account's words: a placeholder as a row count (`LIMIT ?`, `OFFSET ?`) is
  `Invalid row count '?' in limit clause`, a BOOLEAN bound as a predicate or into IFF `Invalid data type
  [VARCHAR(4)] for predicate` / `Invalid argument types for function 'IFF'`, a DATE bound into `? + 1` `Numeric
  value '2024-01-15' is not recognized`, and an infinity bound into a comparison with a NUMBER `Numeric value
  'inf' is not recognized`. A cast in the text (`?::INT`, `?::DATE`) reads the value as before.
- **A SQL function's block body whose expression does not compile, behind a handler.** `CREATE FUNCTION f() RETURNS
  INT AS BEGIN RETURN 'a' + TRUE; EXCEPTION WHEN OTHER THEN RETURN 5; END` answered 5 for `SELECT f()`. On an
  account the body compiles with the statement that calls the function, so the call is refused with the
  expression's own compilation error and no handler of the body catches it. An expression that fails as it is
  evaluated (`RETURN 1 / 0`) is still the body's exception, which its handlers catch.
- **A COMMENT written after a task's body.** `CREATE TASK … AS SELECT 1 COMMENT = 'c'` set the task's comment. On an
  account the body runs to the end of the statement, so the word belongs to the body: a syntax error after a query,
  a CALL, DML or a block, and the body's own clause after a CREATE (`AS CREATE TABLE t (x INT) COMMENT = 'c'`).
  The task's own comment is written before AS, as before.
- **A column a window call reads in the ORDER BY of an aggregate query with no GROUP BY.** `SELECT MAX(id) FROM t
  ORDER BY MAX(id) OVER ()` answered the row. The clause is dropped, as before, but a window call among its keys is
  held to the grouping first, as the select list's are: `[T.ID] is not a valid group by expression`. A window over
  aggregates and constants alone (`SUM(MAX(id)) OVER ()`, `COUNT(*) OVER ()`) still answers the row.
- **A column a window call reads beside a GROUP BY ALL that finds no key.** `SELECT MAX(id), MAX(id) OVER () FROM t
  GROUP BY ALL` answered a row; the query is the one group an aggregate query is, and the column is refused the
  same way.
- **A SQL procedure whose body the account refuses to read.** `CREATE PROCEDURE … AS $$ BEGIN DROP TABLE t (VIEW);
  … END $$` created the procedure and failed at its CALL. An account reads the body when the procedure is created:
  a faulty signature or property of a DROP or a DESCRIBE in it, a word run in after a statement and a call in a
  FROM form no name accepts refuse the CREATE with the body's own lines, and no procedure is left to call.
- **A table named after LATERAL.** `FROM t, LATERAL x` and `JOIN LATERAL x y` read `x` as a table; LATERAL takes
  a query in parentheses or a function's call, and an account refuses the token after the name (twice, as it
  reports it).
- **A star's RENAME without AS.** `SELECT * RENAME (a x) FROM t` renamed the column; the AS is required, and the
  name after the column is a syntax error.
- **A ':=' SET of a name no block declares.** `BEGIN SET x := 1; … END` set a session variable; `SET x := …`
  assigns the block's variable as `x := …` does, and an undeclared one is `invalid identifier 'X'` while the block
  compiles. Written with '=' it sets the session's variable, as before.
- **A list SET whose values do not match its names.** `SET (x, y) = (1)` set `x` and left `y` NULL; it is `Number
  of variables on left-hand side of assignment (2) is not equal to the number of values on right-hand side (1).`
- **A bind in a session SET outside a block.** `SET x = :nosuch` set NULL (and `SET x = :1` failed inside the
  engine); both are `Bind variable :nosuch not set.` at the bind.

- **A type's name as a staged-file query's format.** `SELECT $1 FROM @st (FILE_FORMAT => 'CSV')` and `=> json`
  read the file as that type; an account knows no file format of the name: `File format 'CSV' does not exist or
  not authorized.` Name a `CREATE FILE FORMAT` object, or leave the argument out for CSV. An argument other than
  FILE_FORMAT and PATTERN (`NOSUCH => 'x'`), which was ignored, is `Unsupported feature 'TABLE'.`
- **TRUNCATE TABLE IF EXISTS of a stream or a dynamic table** answered success. IF EXISTS forgives a missing
  table alone: a stream is `Object found is of type 'STREAM', not specified type 'TABLE'.`, a dynamic table
  `Invalid Operation TRUNCATE on Dynamic Table`.

- **A window of a measure's own in MATCH_RECOGNIZE.** `MEASURES ROW_NUMBER() OVER (ORDER BY id) AS rn` and `SUM(v)
  OVER () AS sv` were computed; a measure is read over its match, and the clause refuses the OVER where it stands:
  `OVER clause not allowed in MATCH_RECOGNIZE MEASURES clause.`

- **SYSTEM$WAIT over an amount that is no whole constant.** `SYSTEM$WAIT(1.5)` waited two seconds. An account takes
  a whole number it need not compute: a number written with a fraction, and any number that is computed or cast
  (`1 + 0`, `2 / 2`, `0::NUMBER(10,2)`), is `argument 0 to function SqlIdentifier{qualifierNames=[],
  identifierName=SYSTEM$WAIT} needs to be constant, found 'CAST(1.5 AS NUMBER(18,0))'` - the amount as the plan
  prints it - and a BOOLEAN `Invalid argument types for function 'SYSTEM$WAIT': (BOOLEAN)`. `1.0`, a text and a
  FLOAT are still taken.
- **A bare star under ALL, and under a name that is no function's.** `COUNT(DISTINCT *)` was refused over the
  statement's text; `COUNT(ALL *)` was counted, and `NOSUCHFN(DISTINCT *)` was an unknown function. Each is
  `Unsupported feature 'TOK_STAR'.`, ahead of a table or a column that is not there, windowed too.
- **A CALL of a function that answers only over a window**: `CALL ROW_NUMBER()` is `Missing window specification
  for function [ROW_NUMBER()].`, where it was an unknown function.

- **A SYSTEM$ function's argument that reads a row.** The SYSTEM$ functions that answer while a statement
  compiles took a column and answered row by row. An account refuses it, over no row too, each family in its own
  sentence: `SELECT SYSTEM$GET_TAG_ALLOWED_VALUES(n) FROM t` is `Invalid value [T.N] for function
  'SYSTEM$GET_TAG_ALLOWED_VALUES', parameter 0: constant arguments expected`, the argument as the plan prints it
  (`[UPPER(T.N)]`, `[(SELECT T.N AS "N" FROM T AS T LIMIT 1 OFFSET 0)]`), and so is anything the plan has not folded
  by then - a cast, written or implied, and every call but `||`, CONCAT, UPPER, LOWER and GETVARIABLE over
  constants; `SYSTEM$CLUSTERING_DEPTH(n)`, `SYSTEM$CLUSTERING_INFORMATION(n)` and `SYSTEM$LAST_CHANGE_COMMIT_TIME(n)`
  are `argument 0 to function … needs to be constant, found 'T.N'`, `SYSTEM$STREAM_HAS_DATA(n)` and
  `SYSTEM$STREAM_GET_TABLE_TIMESTAMP(n)` the same counted from one, and `SYSTEM$WAIT(n)` in the account's own
  naming of that function. SYSTEM$GET_TAG reads its arguments before a name resolves: a qualified column is echoed
  as written (`[T.N]`), a column inside an expression is `invalid identifier 'N'` at the name, and any query is
  refused, printed as parsed. A view over SYSTEM$GET_TAG_ALLOWED_VALUES is created, and its reader refused.
- **The clustering functions over what they cannot read.** `SYSTEM$CLUSTERING_DEPTH` and
  `SYSTEM$CLUSTERING_INFORMATION` answered a fixed text for any name. A table that does not exist is now `Table 'T'
  does not exist or not authorized.`, a table with no clustering key and no key list `Invalid clustering keys or
  table T is not clustered`, a key naming no column an `invalid identifier`, a view `Cannot specify clustering keys
  on views`, a NULL `null argument for table name`; `SYSTEM$CLUSTERING_RATIO` is `Unsupported feature 'Clustering
  Ratio'.`
- **A user-defined function's arguments inside a block.** A call whose arguments no overload takes was refused in a
  statement run on its own and answered in a block wherever the argument was not a NULL or a number bind. It is
  refused there as well, by the declared types: a BOOLEAN or an ARRAY bind, an expression over a bind (`fd(:s + 1)`
  is `Invalid argument types for function 'FD': (NUMBER(11,2))`), a literal the block's query writes, and a variable
  of the block's own expression - `t := fd(s)` over a NUMBER variable is that expression's EXPRESSION_ERROR.

- **A grouped or DISTINCT query's ORDER BY key that tests a subquery.** A key reading a subquery as a value was
  refused there; one testing it - `ORDER BY EXISTS (SELECT 1 FROM g)`, `id IN (SELECT id FROM g)`, `id > ANY
  (SELECT …)` - was answered. An account refuses every subquery in such a key, re-printed from its plan:
  `[EXISTS(SELECT 1 AS "1" FROM G AS G)] is not a valid order by expression`, an IN and a quantified comparison
  by the quantifier alone, `[ANY(SELECT G.ID AS "ID" FROM G AS G)]` (`ALL` for NOT IN). The same subquery in the
  select list does not make the key legal; naming it by the list's alias does, and so does an ungrouped query.
- **A policy created over the name of another kind of policy.** A schema's policies share one namespace on an
  account, and each kind kept its own here: `CREATE ROW ACCESS POLICY p` beside a masking policy `p` was created.
  It is now `Object 'DB.S.P' already exists.`, with OR REPLACE `Object found is of type 'MASKING_POLICY', not
  specified type 'ROW_ACCESS_POLICY'.`, and IF NOT EXISTS finds the name taken. A policy renamed onto a name
  another policy holds is refused the same way - a join, an aggregation or a projection policy renamed onto a
  name of its own kind used to replace the holder silently.
- **A rename into a schema the role may not create in.** Renaming a stage, a file format, a function, a
  procedure, a tag, a masking, row access, join, aggregation, projection or password policy asked for nothing
  but the object. An account creates the object under its new name: the schema the name lands in - its own
  included - is asked for the privilege to create the kind, `Insufficient privileges to operate on schema 'S'.
  Your primary role R must have CREATE STAGE granted on SCHEMA DB.S.`
- **A session's parameter written as a property of an Iceberg table.** `CREATE ICEBERG TABLE t (a INT) TIMEZONE =
  'UTC'` was created. As for any table the parameter is none of its properties: `cannot set parameter 'TIMEZONE'
  at 'ICEBERG_TABLE'`.
- **A call of a function that cannot use its secrets, wherever it is compiled.** A function that names
  SECRETS and no external access integration was refused only when a row called it: `SELECT f(a) FROM t WHERE
  1 = 0` answered no rows, and a view, a function, a procedure or a column default calling it was created. As
  on an account the call is refused while the statement compiles - `invalid property 'SECRETS' for 'FUNCTION'`,
  or `Object 'S' does not exist or not authorized.` for a secret that is gone - in a query over no rows, an
  EXPLAIN, a DELETE or an UPDATE, a block, and the definition that calls it is not created. The refusal comes
  ahead of an invalid identifier and of the call's argument count.
- **A dotted name in a SQL function's body.** `CREATE FUNCTION f(x NUMBER) RETURNS NUMBER AS 't.x'` was created
  and failed when called. Outside a subquery a dotted name is no parameter's, and an account refuses the
  CREATE: `invalid identifier 'T.X'`. `s.nextval`, which reaches a sequence, is still created.
- **A parameter DEFAULT that reads a query.** `DEFAULT (SELECT 1)` was created. An account refuses it where the
  query starts: `invalid default argument expression (SELECT 1 AS "1" FROM (VALUES (null)) DUAL)`.
- **A Java handler compiled for a newer Java than the runtime it declares.** `CREATE FUNCTION ... LANGUAGE JAVA
  RUNTIME_VERSION = '11' HANDLER = 'H.h' IMPORTS = ('@st/h.jar')` was created whatever the jar held. As on an
  account a handler class the imports hold compiled for a newer Java than the declared runtime reads - 17 for
  runtime 11, 21 for runtime 17 - is refused at CREATE: `Class files were compiled with an unsupported version
  of the JDK. Use the '-target' or '-release' option when compiling your class files to target Java 11 in
  function F with handler H.h`. A function that declares no RUNTIME_VERSION runs on the account's default
  runtime, which differs from account to account, and is held to no level; a class newer than the engine's own
  Java is refused when it is called, where it failed inside the JVM.
- **A repeated secret name under IF EXISTS.** `ALTER FUNCTION IF EXISTS nosuch(NUMBER) SET SECRETS = ('a' = s,
  'a' = s)` answered success. The list is judged first: `Duplicate secret name 'a' passed in.`
- **An APPROX_PERCENTILE percentile above 1.** `APPROX_PERCENTILE(n, 1.01)` answered the largest value. As on
  an account the percentile lies between 0 and 1 inclusive: `Invalid value [1.01] for function
  'APPROX_PERCENTILE', parameter 2: Percentile must be between 0 and 1 inclusive.`
- **A USING TEMPLATE query of more than one column.** `CREATE TABLE t USING TEMPLATE (SELECT
  ARRAY_AGG(OBJECT_CONSTRUCT(*)), 1 FROM TABLE(INFER_SCHEMA(...)))` read its first column and ignored the rest.
  An account refuses the query: `Invalid template: The sub-query must SELECT a single scalar value`.
- **A foreign key given an ON DELETE or an ON UPDATE rule twice.** `FOREIGN KEY (y) REFERENCES p (id) ON DELETE
  CASCADE ON DELETE SET NULL` created the table. As on an account a key takes one rule of each kind: a second
  rule of a kind is a syntax error at its `DELETE` or `UPDATE`, a third rule at its `ON`.
- **A cast out of a vector or a MAP that has no conversion.** `[1,2,3]::VECTOR(INT,3)::DATE` answered the
  vector's text, `::NUMBER` failed only when the row was read, and a MAP cast to a DATE, a BOOLEAN, a FLOAT or a
  BINARY likewise; `TRY_CAST` and `TRY_TO_NUMBER` over either answered NULL. As on an account neither converts
  to a scalar, and the statement is refused while it compiles: `invalid type [CAST(TO_VECTOR(ARRAY_CONSTRUCT(1,
  2, 3)) AS DATE)] for parameter 'TO_DATE'`. A vector is read into an ARRAY of its own elements' kind alone
  (`::ARRAY(FLOAT)` over an INT vector is `Invalid argument types for function 'CAST': (VECTOR(INT, 3))`), is no
  OBJECT (`… for parameter 'TO_OBJECT'`, by a cast and by `TO_OBJECT`) and no shaped OBJECT or MAP
  (`incompatible types: [VECTOR] and [MAP]`).
- **A MAP or a shaped OBJECT wrapped into an ARRAY.** `{'a': 1}::MAP(VARCHAR, INT)::ARRAY` and `TO_ARRAY` over a
  MAP or an `OBJECT(a INT)` answered `[{"a":1}]`, as a plain OBJECT does. An account refuses both:
  `Unsupported data type 'MAP'.`, `Unsupported data type 'STRUCTURED_OBJECT'.`
- **A DECFLOAT with a precision other than 38.** `NULL::DECFLOAT(5)`, a `DECFLOAT(16)` column, parameter or
  declared variable were taken, the number ignored. As on an account the type takes the one precision:
  `Unsupported DECFLOAT precision: 5.`, while the statement compiles, for any other number - a negative one
  included. `DECFLOAT` and `DECFLOAT(38)` are unchanged.
- **A written signature that names a structured parameter by another shape.** `DESCRIBE FUNCTION f(OBJECT(b
  INT))`, `DROP FUNCTION f(OBJECT(a VARCHAR))` and `f(ARRAY)` all reached `f(x OBJECT(a INT))` - only the outer
  family was compared. As on an account a structured parameter is named by its shape: the field names, their
  order, each type's family and a field's `NOT NULL` must be the declared ones, whatever widths are written
  inside (`OBJECT(a NUMBER(10,2))` still names `OBJECT(a INT)`). Another shape is `Function '…' does not exist
  or not authorized`.
- **ALTER MATERIALIZED VIEW IF EXISTS no longer forgives a missing view for every action.** `ALTER MATERIALIZED
  VIEW IF EXISTS nosuch SUSPEND` answered `Statement executed successfully.` As on an account IF EXISTS forgives
  the view's absence for the clustering actions, RENAME and SET / UNSET COMMENT; SUSPEND, RESUME and SET / UNSET
  SECURE are refused with `Materialized view '…' does not exist or not authorized`. A `CLUSTER BY` key is
  compiled even then, so a column that is nowhere is `invalid identifier 'X'`.
- **A metric schedule that is no schedule.** `CREATE TABLE t (a INT) DATA_METRIC_SCHEDULE = 'x'` was created, a
  view likewise. As on an account the text is `'<n> MINUTE'` with 5, 15, 30, 60, 720 or 1440 minutes, a `USING
  CRON` expression with its time zone or - for a table alone - `'TRIGGER_ON_CHANGES'`; anything else is
  `Invalid schedule was specified. Please refer to the docs on what constitutes a valid schedule.`
- **A user's table function, or a table procedure, called with named and positional arguments mixed.**
  `TABLE(f(1, b => 'x'))` ran, the positional arguments filling the leading parameters. As on an account a call
  in a FROM clause writes its arguments one way or the other, in whichever order they come: `illegal mixing of
  named and positional arguments for function F`, at the function's name and before the names and the types are
  judged - the rule a CALL already had. Name every argument, or none.
- **A table procedure's arguments in a FROM clause are no longer converted.** `SELECT * FROM TABLE(p('1'))` over
  `p(x NUMBER)` ran, the text converted as a CALL converts it, and `TABLE(p('x'))` failed only when the procedure
  ran. As on an account the FROM form matches its arguments to the parameters' declared types exactly, while the
  statement compiles, as a table function's are matched: a text, a FLOAT, a NUMBER of another scale and an
  untyped NULL are no arguments of a NUMBER parameter, nor a `TIMESTAMP_NTZ(3)` of a `TIMESTAMP_NTZ` one. Every
  misfit, too few and too many arguments among them, is `Invalid argument types for function 'P': (VARCHAR(1))`
  at the procedure's name, the name as the call writes it (`'TEST_SCHEMA.P'`); written with names it is `named
  arguments [X] do not match any signature for function P`. A number written as a literal is matched at the scale
  its value needs, so `p(1.0)` still names a NUMBER(38,0) parameter. Cast the argument (`p('1'::NUMBER)`), or CALL
  the procedure.
- **A routine's signature written with its family's default width.** `DROP FUNCTION f(VARCHAR(16777216))` dropped
  a function declared `f(p VARCHAR)`, and `f(TIMESTAMP_NTZ(9))` named a parameter declared `TIMESTAMP_NTZ(3)`:
  a width equal to the default read as no width at all. As on an account a type written with a length or digits
  names only a parameter declared with the same - `Function 'DB.SCHEMA.F' does not exist or not authorized.`
  otherwise - and a parameter declared without a length is as wide as a text or a binary gets, named by
  `VARCHAR(134217728)` and `BINARY(67108864)` and by no other length. DESCRIBE, DROP, ALTER, COMMENT ON and
  GET_DDL read a signature alike, a function's and a procedure's. A type written bare names a parameter of any
  width, as before, and so does `NUMBER(38,0)`: write the bare type.
- **USE ROLE of a role the user may not activate.** `USE ROLE r` activated any role that existed, so a script
  that created a role and switched to it without granting it ran here and failed on an account. As there, a role
  is activated only by a user it reaches: one granted to the user, or to a role the user holds, however far
  down - the system roles under ACCOUNTADMIN and PUBLIC included. Any other role is refused, `SQL access control
  error: Requested role 'R' is not assigned to the executing user.  Specify another role to activate.`, and the
  session keeps the role it has. `USE SECONDARY ROLES` holds each role it names to the same rule, after it has
  found every one of them. Add `GRANT ROLE r TO ROLE <a role the user holds>` (or `TO USER`) before the switch.
- **FLATTEN with no argument, or with more than five by position.** `TABLE(FLATTEN())` answered no rows and a
  sixth positional argument was ignored. As on an account: `not enough arguments for function [FLATTEN], expected
  1, got 0` and `too many arguments for function [FLATTEN] expected 5, got 6`.
- **A GENERATOR bound that is no constant.** `ROWCOUNT` and `TIMELIMIT` were read like any other argument, so a
  column of a relation to the left gave every row its own count and `ROWCOUNT => RANDOM()` could ask for more
  rows than the heap holds. As on an account both must be constants: a column, `RANDOM()` and `SEQ4()`
  are refused while the statement compiles, `argument 1 to function GENERATOR needs to be constant, found
  'GN.N'` (`argument 2` for the time limit). An expression of constants, a scalar subquery and a
  session variable are still taken, and so is a bound no row can change - `NULL + t.n`, `t.n * 0 + 2`,
  `IFF(FALSE, t.n, 2)`, `NVL(2, t.n)` - where `NVL(NULL, t.n)` is refused for `'T.N'`. The LATERAL spellings of
  the call (`CROSS JOIN LATERAL GENERATOR(ROWCOUNT => t.n)`) are refused alike. A negative `TIMELIMIT` is `Invalid value [-1] for function 'GENERATOR',
  parameter TIMELIMIT:  must be non-negative.` and a text one `invalid type [VARCHAR(1)] for parameter
  'TIMELIMIT'`.
- **A malformed path in GET_PATH and in FLATTEN's PATH.** `GET_PATH(v, 'a.')`, `'a['`, `'a[x]'`, `'a..b'`, `'.a'`
  and `' a'` answered a value or NULL, and FLATTEN took `PATH => 'a.'` for `a`. A path written as a text literal
  is now judged while the statement compiles, as an account judges it, and the refusal says what is wrong and
  where: `Invalid extraction path 'a.': dangling dot at position 2.` - likewise `dangling square bracket`,
  `dangling quote`, `empty square brackets`, `empty (unquoted) field name`, `invalid number`, `invalid field` and
  `invalid token`, and `array index 2,147,483,648 is off limits; must be between 0 and 2,147,483,647.` Any other
  path is judged when the row is read: `Bad compound object's field path name 'a.' in GET_PATH` (the empty path
  too) and `… in FLATTEN`.
- **A Java or Scala table function whose handler class cannot be one.** CREATE FUNCTION … RETURNS TABLE took any
  class - or none - for a Java or a Scala function, and every call failed. As on an account, the class is now
  judged when the function is created: `Failed to find a public method named "getOutputClass" with 0 arguments` /
  `The getOutputClass() method is required for Java UDTFs`, `Failed to find a public method named "process" with 1
  arguments`, `The return type of the "process" method is "java.util.List" but must be java.util.stream.Stream`,
  and `The output class "Out" is missing fields with the following names: MISSING. Make sure the member fields
  are public`, each ending ` in function F with handler Gen`. A Scala table function is held to the same rules.
- **A JavaScript table function whose body ran as SQL.** A JavaScript RETURNS TABLE function whose body happened
  to be a query - `AS $$SELECT 42$$` - answered the query's rows. Its body is JavaScript now: that one is refused
  when it is called, as a script that is no handler object.
- **An inline Java body written for a later Java than its routine's RUNTIME_VERSION.** A body was compiled at the
  level of the JDK the engine runs on, whatever the routine declared, so a function of `RUNTIME_VERSION = '11'`
  was created and ran with a switch expression, a pattern in `instanceof`, a text block, a record or
  `"…".formatted(…)` in it, and failed only when it was deployed. The body is now compiled at its routine's
  level - 11 when the routine names no version, as on an account - for functions, procedures and the classes a
  TARGET_PATH keeps: such a body is refused when the routine is created, `Error while compiling source: …`,
  and the same body under `RUNTIME_VERSION = '17'` is created. A version above the engine's own JDK cannot be
  held to and is compiled at the JDK's level. The syntax refusals are worded by the engine's compiler
  (`switch expressions are not supported in -source 11`), where an account's Java 11 compiler words them its own
  way (`illegal start of expression`); a method the version's library lacks reads the same on both
  (`cannot find symbol … method formatted(int)`).
- **A Scala function or procedure whose handler method the body lacks.** It was created, and the first call
  failed with `Method g with 1 args not found in H$`. CREATE now refuses it as an account does: `Failed to find a
  public method named "g" with 1 arguments in function F with handler H.g`, a procedure's count including its
  Session.
- **A value a Java or Scala function returns that its RETURNS type cannot hold.** A Java function's value came
  back as it was, or as its Java text. As on an account — and as a procedure's CALL already did — a value the
  declared type does not take is refused: a text that is no JSON, or JSON of another kind, for an ARRAY, an
  OBJECT or a VARIANT (`Error parsing JSON: output JSON is not an array type: {"a":1} in function JAS with
  handler H.f`); a text that is no number for a NUMBER or a FLOAT; a `BigDecimal` or a whole number the declared
  width has no room for (`BigDecimal value cannot be losslessly represented as a Snowflake NUMBER with a
  precision of 5 and a scale of 1`); bytes past a declared BINARY(n); and a `java.sql.Time` that counts no time
  of day (`Time value out of range: 86401000 ms`).
- **A Java table procedure whose handler hands back no DataFrame.** `RETURNS TABLE (…)` over a handler method
  that returns anything but a DataFrame — and a scalar RETURNS over one that returns a DataFrame — is refused
  when the procedure is created: `Expected stored procedure to return TABLE if and only if handler method returns
  DATAFRAME in function P with handler H.run`. A handler that returns `null` for a table is refused at the CALL.
- **A number a Java or Scala handler's integer parameter cannot hold, and a NULL for a primitive one.** A NUMBER
  handed to an `int`, a `long` or a `short` parameter was narrowed as Java narrows: `fi(3000000000)` answered
  -1294967296 and `fsh(40000)` -25536. An account refuses it, and so does the engine: `Number out of representable
  range: type int32, value 3000000000 in function FI with handler H.f` (`int64`, `int16`). A SQL NULL for a
  primitive parameter is `Cannot pass a NULL value as input to a Java type of int in function FI with handler
  H.f`; a boxed `Integer` takes it as `null`.
- **A Java function whose instance handler's class cannot be made.** A handler method that is not static is
  called on an instance, which an account makes when the function is created. A class without a zero-argument
  constructor, or with a private one, is refused there — `The provided method is non-static, but the enclosing
  class does not have a public zero-argument constructor in function F with handler H.f` — as are an abstract
  class and a constructor that throws, which read as a handler's own exception does (`User Error Report:`).
- **A JavaScript procedure's result set read by a name it does not have, or before its first row.**
  `rs.getColumnValue('name')` read the column `NAME`: names were matched whatever their case. An account matches a
  name exactly as the result spells it, and so does the engine now — `Given column name/index does not exist:
  name`, also for a number past the columns. A column read before `rs.next()` is `ResultSet is empty or not
  prepared (call next() first).`, a statement asked for its columns before it ran `Statement is not executed
  yet.`, and `snowflake.execute({})` `empty argument passed`; each is thrown to the handler with the code 100183
  and the state P0000.
- **A value a JavaScript function returns that its RETURNS type cannot hold.** A JavaScript function handed back
  whatever its handler returned, whatever it declared. As on an account — and as a procedure's CALL already did —
  a value the declared type does not take is refused: `JavaScript execution error: Return value is not an ARRAY`
  for anything but an array (`null` and a JSON text included), `… is not an OBJECT` for an array, a text, a number
  or `null`, `Late exception in return value conversion` for a symbol returned as a VARCHAR, a BigInt returned as
  a FLOAT or inside a VARIANT and a conversion that throws, and `String 'abcdef' is too long and would be
  truncated` for a text wider than a declared VARCHAR(n). A procedure's symbol, BigInt and throwing conversion
  are refused the same way, where a symbol was its description and a BigInt a number.
- **A TIME inside a JavaScript handler's VARIANT, ARRAY or OBJECT argument.** JavaScript has no counterpart for a
  TIME, so an account refuses the call — `JavaScript execution error: TIME inside VARIANT value - not supported
  in function F with handler F`, for a procedure as for a function — where the handler was handed the text. A
  variant that is no object, handed to a JavaScript function's OBJECT parameter, is refused as a cast refuses it:
  `Failed to cast variant value 1 to OBJECT`.
- **A column DEFAULT that calls a function it cannot expand.** A column's DEFAULT may call a SQL function whose
  body is an expression — an account expands the body into the default, and so does the engine. A JavaScript, a
  Python, a Java or a Scala function, a Snowflake Scripting one, and a SQL function whose body reaches one, were
  taken too; they are refused as on an account, the default echoed as the plan prints it: `Invalid column default
  expression [JSF()]`. A SQL function whose body aggregates is refused while it is expanded (`While expanding UDF
  in default expression: … aggregate functions are not allowed as part of the specification of a default value
  clause.`), a call with too many arguments where it stands, and such a function's FLOAT in an exact NUMBER
  column for its type.
- **A routine parameter's DEFAULT that calls a user-defined function or reads a name.** `CREATE FUNCTION g(x
  NUMBER DEFAULT f1())` and its procedure twin were created; a parameter's default calls no user-defined
  function, a SQL one included — `invalid default argument expression F1()`, placed where the expression stands
  — and `DEFAULT a` over another parameter is `invalid identifier 'A'`. A built-in call and arithmetic are taken
  as before, and a name that is no function is an unknown function when the routine is created.
- **A number a table function's declared column has no room for.** `RETURNS TABLE (n NUMBER(3,1)) AS 'SELECT
  1234.5'` answered 1234.5; reading the column is now `Number out of representable range: type
  FIXED[SB2](3,1){nullable}, value 1234.5`, as on an account. A statement that reads the function's other
  columns, or counts its rows, is not refused.
- **Named arguments that several overloads take equally well.** `ov(a => 1, b => 2)` between `ov(a NUMBER, b
  VARCHAR)` and `ov(a VARCHAR, b NUMBER)` ran the first overload; it is refused as on an account — `More than one
  function of OV are resolved in prepare state.` — while the statement compiles, over a table with no rows and
  in the body of a function being created too, and so is the same `CALL` of a procedure. Written by position the
  arguments still run an overload: the first in the order of the signatures.
- **A routine named with a quoted schema or database of another case.** `"test_db".s.f(1)`,
  `TABLE(db."s".tf())` and `CALL "test_db".s.p()` reached the routine of the database TEST_DB; a part the name
  writes is now matched exactly, as a table's is, and the call is an unknown user-defined function. A table
  function missed under a qualified name is `Unknown user-defined table function S.TF`.
- **A SECURE function's, procedure's or view's definition, to a role that does not own it.** Only the owner — a
  role the session holds or inherits — is shown how a secure object is defined. To any other role, whatever it was
  granted on the object, `GET_DDL` of it is `Object does not exist, or operation cannot be performed.`, a schema's
  or a database's DDL leaves it out, `DESCRIBE FUNCTION` and `DESCRIBE PROCEDURE` answer a NULL body and `SHOW
  VIEWS` an empty text, where each showed the whole definition. A procedure's body is its owner's whether it is
  secure or not: another role's `GET_DDL` carries `AS ''` and its DESCRIBE an empty body. And
  `INFORMATION_SCHEMA`'s `FUNCTION_DEFINITION`, `PROCEDURE_DEFINITION` and `VIEW_DEFINITION` are NULL for every
  object another role owns.
- **A unit a SQL function's body cannot read.** `CREATE FUNCTION … AS 'DATEADD(u, 1, d)'` over a parameter `u` is
  refused when the function is created — `Date/time component [CORRELATION(NULL) ]for function DATEADD needs to
  be an identifier or a string literal.` — and a word that is no unit in the function's own sentence (`['NOSUCH']
  is not a valid date/time component for function DATEADD.`), where both were created and failed when called.
- **A RESULTSET read before anything was given to it.** A cursor declared over a RESULTSET that was declared and
  never assigned a statement, and a FOR over one, ran over no rows; they are refused as the account refuses them —
  `Stored procedure execution error: uninitialized resultSet 'RS'` (92212 to a handler), placed where the cursor's
  declaration names the RESULTSET, so its OPEN is never reached. `RETURN TABLE(rs)` of one answers that sentence
  too, where it named a Java class (`RETURN TABLE expects a RESULTSET, got: ResultSetVariable`).
- **A table function's TIME or timestamp argument of other digits than its parameter's.**
  `TABLE(f('10:30:00.5'::TIME(3)))` for a parameter declared `TIME` is `Invalid argument types for function 'F':
  (TIME(3))`, as a NUMBER of another scale already was: a table function takes only arguments of its parameters'
  own types.
- **A handler that reads a LET of its block's BEGIN section.** A variable, a cursor or a RESULTSET that a block
  declares with LET among its statements is not passed to the block's exception handlers — one a handler uses
  belongs in the DECLARE section — and Frostlake let the handler read it. The block still compiles and a handler
  that never touches the name runs; the read is now refused when it is reached, as `invalid identifier 'Y'` at
  the place it is written: in an expression, a bind of a statement, an OPEN or a FOR over a LET cursor, a RETURN
  TABLE of a LET RESULTSET, a block nested in the handler. A handler that assigns the name first, or declares it
  again, reads its own value. The refusal ends the body — no handler of the body catches it — and the statement
  that called the body may catch it. (An account aborts such a read with an internal error of its own; the
  sentence is Frostlake's.)
- **A RESULTSET read before its asynchronous job was awaited, and an AWAIT of anything else.** `RETURN TABLE(r)`
  and a cursor declared over `r` are `Stored procedure execution error: uninitialized resultSet 'R'` until
  `AWAIT r` or `AWAIT ALL` has run the job that fills it, where the job used to run at its ASYNC. `AWAIT x` of a
  name that is no RESULTSET, or of no name at all, is refused while the block compiles (` Invalid target for
  AWAIT statement. Expected a resultset.`), `AWAIT r` of a RESULTSET never given anything is `Can not wait on an
  empty resultset`, and of one whose job failed at an earlier AWAIT `Statement is terminated.`
- **A date assigned to an OBJECT variable.** `o := d` for a DATE `d` is `invalid type [CAST(TO_DATE('2024-01-02')
  AS OBJECT)] for parameter 'TO_OBJECT'`, as a number assigned to one already was.
- **A BREAK, a CONTINUE or an EXIT that stands in no loop.** A body that held one was accepted, and running it set
  a flag that ended, or skipped an iteration of, a loop of the body that had called it — or of the next statement
  the session sent. Such a body is now refused when it starts to run, at the word, on a branch never taken too:
  ` Break used outside of loop` (the one sentence for the three words), and ` Label 'NOSUCH' not found` for a
  label no loop around it carries. A procedure or a function that holds one is still created and is refused when
  it is called, so a body can no longer end a loop that is not its own.
- **A cursor used out of turn.** An OPEN of a cursor that is open is `Stored procedure execution error: CURSOR
  'C' is already open.` (92215 to a handler) and a CLOSE of one already closed `CURSOR C is not open` (92201),
  where the second CLOSE was accepted; a FETCH from a cursor that is not open fails with `CURSOR C is not open`
  alone, and no handler of the block catches it. A procedure that opens a cursor its caller declared is refused
  as `invalid identifier 'C'`: a body sees none of its caller's cursors.
- **Procedure calls nested more than sixteen deep.** A procedure that called itself without end ran until the
  Java stack gave out and ended in a `StackOverflowError` that no handler caught and no history recorded. Bodies
  now nest as on an account — a procedure's call in any language, a block the session sends and a dynamic text
  that is a block each count as one. Sixteen run inside one another; a seventeenth still runs, but the first
  statement it issues ends it, and the statement that started it fails with `Stored procedure execution error:
  Stored procedure exceeds the maximum allowed number of recursive calls.` (90235 / 42601 to a handler), worded
  once more by every body it leaves. The sixteenth body may handle it; the seventeenth's own handler never sees
  it. As for any failed CALL, nothing the bodies wrote before is undone.
- **A handler that names no exception in sight.** `WHEN RuntimeException THEN` caught any failure by the name of
  its Java class, `WHEN no_such THEN` was accepted and never ran, and `SQL_STATEMENT_EXCEPTION`, `SYNTAX_ERROR`
  and `NUMERIC_VALUE_OUT_OF_RANGE` were names of the engine's own. A handler names STATEMENT_ERROR,
  EXPRESSION_ERROR, OTHER, or an exception its block or a block around it declares; any other name — a
  variable's, an exception's that only a nested block declares — is `invalid identifier 'NO_SUCH'` at the name
  while the block compiles, whether or not anything fails when it runs.
- **OTHER in a list of names, or before another handler.** `WHEN e1 OR OTHER THEN` is `The OTHER handler cannot
  be used in a list`, and an OTHER handler followed by another is `The OTHER handler must be the last handler`,
  each at the handler's WHEN.
- **An exception named STATEMENT_ERROR or EXPRESSION_ERROR declared anew.** A body's top block that declares one
  is refused as ` Variable with name 'STATEMENT_ERROR' declared twice.`, where the declaration replaced the
  built-in exception for its handlers.
- **An untyped LET over an expression that holds a query with a FROM.** `LET z := 10 / (SELECT COUNT(*) FROM t)`
  is ` variable 'Z' cannot have its type inferred from initializer`, as `LET z := (SELECT COUNT(*) FROM t)`
  was; `1 + (SELECT 1)` and a declared type are fine.
- **A SQL function whose body reaches itself through a view.** `CREATE OR REPLACE FUNCTION f() … AS 'SELECT MAX(c)
  FROM v'` over a view `v` that calls `f()` — or reads a view that does — is `Detected a cycle in SQL UDF: F`,
  as a cycle through another function was; the earlier definition stays.
- **A value a handler returns that its procedure's RETURNS type cannot hold.** A CALL of a JavaScript, Python,
  Java or Scala procedure answered the text of whatever the handler returned, whatever RETURNS declared. The value
  is now converted to the declared type, and one that does not convert is refused in the sentence an account
  uses: `JavaScript execution error: Return value is not an ARRAY` (`an OBJECT`, `a BINARY`, `Incorrect Timestamp
  returned`), `String 'abc' is too long and would be truncated` and `Binary value '010203' is too long and would
  be truncated` for a JavaScript body; `Python value cannot be losslessly represented as a Snowflake NUMBER with a
  precision of 3, value: 1000 in function P with handler run`, `Return value should be a date object …`, `python
  value needs to be a list to convert to Snowflake ARRAY …` and `TypeError: Object of type set is not
  serializable` for a Python one; `BigDecimal value cannot be losslessly represented as a Snowflake NUMBER with a
  precision of 10 and a scale of 2 in function P with handler H.run`, `Numeric value 'x' is not recognized …` and
  `Error parsing JSON: Document is not a valid JSON object: abc …` for a Java or a Scala one; and for bytes past
  a declared BINARY(n) from a Python, a Java or a Scala handler `System byte length limit (2) exceeded by string
  …`.
- **An overload that differs from an existing one by optional arguments alone.** `CREATE FUNCTION f(a NUMBER, b
  NUMBER DEFAULT 0)` beside `f(a NUMBER)` was accepted, although `f(1)` could mean either. A signature is now
  refused when a call of some number of arguments could mean it or an existing overload — `SQL compilation error:
  Cannot overload FUNCTION 'F' as it would cause ambiguous FUNCTION overloading.` (`PROCEDURE` for a procedure;
  949 / 42723 to a handler) — also under OR REPLACE and IF NOT EXISTS, for a table function and a JavaScript
  function, and for an `ALTER FUNCTION … RENAME TO` a name that holds such an overload. Overloads that differ in
  the type of a parameter a call must supply stay legal.
- **A function created over a procedure of its name and signature, or a procedure over a function.** `CREATE
  PROCEDURE f(a NUMBER)` beside a function `f(a NUMBER)` was accepted, and so was the other order. Each is now
  refused — `Object 'F' already exists as FUNCTION`, `… as PROCEDURE` — with OR REPLACE and with IF NOT EXISTS
  too, and so is an `ALTER FUNCTION … RENAME TO` or an `ALTER PROCEDURE … RENAME TO` onto such a name. A function
  and a procedure of one name whose signatures differ stand side by side as before.
- **An argument that does not convert to its parameter's type.** A CALL handed a JavaScript, Python, Java or
  Scala procedure, and a SQL procedure's BOOLEAN parameter, the argument as written, so `CALL p('maybe')` into a
  BOOLEAN parameter or `CALL p('x')` into a NUMBER one ran the body with the text. Each is now refused at the
  CALL, before the body runs, as on an account: `Boolean value 'maybe' is not recognized`, `Numeric value 'x' is
  not recognized`, `Date 'x' is not recognized`, and for a variant `Failed to cast variant value [1] to OBJECT`.
- **A NULL or a number a Java handler's parameter cannot hold.** `Cannot pass a NULL value as input to a Java type
  of long in function F with handler H.run` and `Number out of representable range: type int32, value 3000000000
  in function F with handler H.run`, where the first reached the handler's reflection and the second wrapped.
- **A COMMIT, a ROLLBACK or a statement that commits, in a procedure or a block run inside a transaction.** With
  a transaction open, a COMMIT or a ROLLBACK written in a procedure the session calls, in a block it sends or in a
  text that is a block ended the caller's transaction, and a `CREATE TABLE` there committed it. As on an account
  each is now refused — `Modifying a transaction that has started at a different scope is not allowed.`, 90239 /
  25000 to a handler — and the transaction stays open with everything written so far. A transaction is ended
  where it was begun: move the COMMIT beside the BEGIN, or begin the transaction in the procedure.
- **A block that leaves a transaction of its own open.** `BEGIN BEGIN TRANSACTION; INSERT …; END;` left its
  transaction open in the session. It is rolled back and the block refused — `Stored procedure execution error:
  Scoped transaction started in stored procedure is incomplete and it was rolled back.` — as a procedure's was,
  and that is what a block or a procedure reports when it lets another failure out with its transaction open.
- **A called procedure reading or writing its caller's variables.** A procedure called from a block, or from another
  procedure, saw that caller's variables and parameters: it could read them, bind them as `:name` and assign them,
  and an assignment was still in force after the CALL. A body now sees its own parameters and declarations alone,
  as on an account: a caller's name in it is `invalid identifier 'X'` while the body compiles, whatever rights the
  procedure runs with, and the calling block reports that as its own statement's failure (`Uncaught exception of
  type 'STATEMENT_ERROR' on line 1 at position 60 : SQL compilation error: …`). Pass the value as an argument
  instead. The statements a JavaScript, Python, Java or Scala handler issues bind none of the caller's variables
  either: a `:name` in one is a bind nothing sets.
- **A block run by EXECUTE IMMEDIATE reading the block that runs it.** Inside a block, `EXECUTE IMMEDIATE 'BEGIN x
  := 1; END;'` assigned that block's `x`. The text is a body of its own now: it reads, assigns and binds none of
  the enclosing block's variables and has none of the names of a handler that runs it. A text that is itself a
  block binds nothing of a USING list either — `?` in it is ` Unexpected unnamed bind in SQL stored procedure.` and
  `:1` is `invalid identifier '1'`; a statement text still takes the list.
- **SQLERRM and SQLSTATE outside an exception handler, and a numbered bind in a block.** Each is `invalid
  identifier` while the block compiles — in an expression, a call's argument or a bind, on a branch that never runs
  too — where they read NULL or, in a procedure called from a handler, that handler's values. `BEGIN RETURN :1;
  END` is `invalid identifier '1'`. SQLCODE still reads NULL where no handler runs.
- **A RAISE that names nothing, outside a handler.** It raised `Error raised`; it is now refused while the block
  compiles: `RAISE without specifying exception only allowed inside a handler.`
- **Declaring or assigning the variables a script is given.** In a body's top block a DECLARE or a LET of SQLCODE,
  SQLROWCOUNT, SQLFOUND, SQLNOTFOUND or SQLID is ` Variable with name 'SQLCODE' declared twice.` (a nested block may
  declare one), and an assignment to one of them or to ACTIVITY_COUNT is ` Assignment to variable 'SQLCODE' is not
  permitted.` A procedure that declares one is still created, and refused when it is called.
- **An owner's-rights procedure reading the session's variables and listings.** A stored procedure that runs with
  its owner's rights read the caller's `$name` and ran the session's listings. As on an account it is now refused
  `Use of session variable '$V' is not allowed in owners rights stored procedure` where it reads one — in an
  expression of its block, in a statement, in a statement its JavaScript, Python, Java or Scala handler issues, and
  in a caller's-rights procedure it calls — and `SHOW VARIABLES`, `SHOW LOCKS`, `SHOW TRANSACTIONS` and a `SHOW
  PARAMETERS` of the session are `Stored procedure execution error: Unsupported statement type 'SHOW VARIABLE'.`
  (`SHOW LOCK`, `SHOW TRANSACTION`, `SHOW PARAMETER`), a `SHOW GRANTS` that names nothing `SHOW GRANTS to the
  current user is not supported in owner's rights stored procedures.` GETVARIABLE still answers, and `EXECUTE AS
  CALLER` keeps all of it. Pass the value as an argument instead.
- **SET, UNSET, ALTER SESSION and USE issued by a handler with its owner's rights.** A JavaScript, Python, Java or
  Scala procedure that runs with its owner's rights changed the caller's session through the statements it issued.
  Each is now `Stored procedure execution error: Unsupported statement type 'SET'.` (`UNSET`, `ALTER_SESSION`,
  `USE`), as it already was in a SQL body.
- **CREATE PROCEDURE over a session variable that does not exist.** A SQL procedure whose block names a `$name`
  compiles against the session's variables when it is created: without the variable it is `Session variable '$V'
  does not exist`, at the name's place in the body, whatever rights it would run with.
- **A RESULTSET declared in a Snowscript UDF.** A SQL function whose body is a block was created with `LET rs
  RESULTSET;`, `DECLARE rs RESULTSET;` or a RESULTSET over a dynamic statement in it, and one declared over a query
  was refused for the query. Each is now `Unsupported statement type for Snowscript UDF: resultset declaration`, as
  on an account, before anything its query holds.
- **Statements of a session whose transaction was aborted.** After `SYSTEM$ABORT_TRANSACTION` ended a session's
  transaction, that session ran on as if it had rolled back. It now holds the aborted transaction, as on an account:
  every statement is refused with `Your transaction was aborted. All statements will be ignored until you issue a
  COMMIT or a ROLLBACK.` until it issues a ROLLBACK, which succeeds, or a COMMIT, which fails as `COMMIT failed. Your
  transaction '<name>', id '<id>', was already aborted.` A block that aborts the transaction it runs in goes on, and
  each statement it then runs is refused (SQLCODE 624, SQLSTATE 57014).
- **Arguments SYSTEM$ABORT_TRANSACTION does not take.** The function answered `Could not abort txn: <text>` for
  whatever it was handed. No argument or two are an arity refusal; a BOOLEAN, a date, a time, a timestamp, a BINARY,
  an ARRAY and an OBJECT are refused for their type; a column, a subquery, `CURRENT_TRANSACTION()` and a NULL cast to
  a type are `argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be constant, found '…'`. Hand the id over in a
  session variable or a block's variable.
- **SYSTEM$ABORT_TRANSACTION inside a stored query or an owner's-rights procedure.** `CREATE VIEW v AS SELECT
  SYSTEM$ABORT_TRANSACTION(…)`, a `CREATE TABLE … AS SELECT` over the call and a SQL function whose body calls it
  were created; they are refused as `View or UDF contains a function with side effects [SYSTEM$ABORT_TRANSACTION]`.
  A procedure that runs with its owner's rights is refused the call when it runs.

- **A failure a block's STATEMENT_ERROR handler no longer catches.** `WHEN STATEMENT_ERROR` caught everything but a
  syntax error: a value a `LET` could not compute (`LET x INT := 1/0`) and an exception the script raised itself
  (`RAISE my_exception`) ran its handler. The first is an EXPRESSION_ERROR and the second is caught by its own name
  or by `WHEN OTHER` alone, so a block with only a STATEMENT_ERROR handler now fails with `Uncaught exception of
  type 'EXPRESSION_ERROR' …` or the exception's own sentence, as it does on an account.
- **A flow chain as a task's body.** `CREATE TASK t AS SELECT 1 ->> SELECT * FROM $1` created the task from the first
  statement and then failed on the second, and `ALTER TASK t MODIFY AS …` did the same. A body is one statement, so
  both are now `Invalid statement for task definition. Expecting one of the following: STATEMENT` and nothing is
  created or changed. A COMMENT written after a body that is no query, DML, call or block (`CREATE TASK t AS DROP
  TABLE x COMMENT = 'c'`) is a syntax error at the COMMENT.
- **An ungrouped column read by a subquery inside a HAVING aggregate.** `SELECT b FROM fz GROUP BY b HAVING
  MAX((SELECT MAX(v) FROM g WHERE g.id < fz.id)) > 0` answered rows, and its neighbours were refused as an
  unsupported correlation. A query written as a value inside a HAVING aggregate's argument reads the enclosing
  query's columns against the groups, as one written in HAVING directly does: `[FZ.ID] is not a valid group by
  expression`. A query with no FROM, one under EXISTS and a condition that an `OR TRUE` settles are not judged.
- **A keyword as a signature item, wherever the statement stands.** `DROP TABLE t (VIEW)` and `DESCRIBE TABLE t
  (TYPE)` were refused only as a request's one statement. Beside another statement (`DROP TABLE t (VIEW); SELECT
  1`), in a `BEGIN … END` block, in the text of EXECUTE IMMEDIATE and in a SQL procedure's body they ran, and the DROP
  dropped the table. The word is now refused by the parse itself, so nothing runs anywhere, and each statement
  reports the lines it reports standing alone; a statement after it keeps its own.
- **DESCRIBE ICEBERG TABLE over a table that is no Iceberg table.** It described the table. It is now `SQL
  Compilation error: The object T1 is not an Iceberg table. Only Iceberg tables can use DESCRIBE ICEBERG TABLE
  command.`
- **A signature item that names no type.** After a table's or a view's name a DROP and a DESCRIBE take a signature,
  and `DROP TABLE t (VECTOR)`, `(MAP)`, `(INTERVAL)`, `(OBJECT(a))`, `(ARRAY.a)`, `(MAP(INT))` or `(VECTOR(VARCHAR,
  3))` dropped the table: a structured type's word was read as a plain one with any parameters. Each is now a syntax
  error where the account stops reading — the `)` after `VECTOR`, the `.` after `ARRAY`, the `VARCHAR` — and the
  table stays. ARRAY takes one type, OBJECT fields, MAP a key and a value, VECTOR an integer or floating-point type's
  word and a signed dimension, INTERVAL its fields; an element, a field and a MAP's value may end in NOT NULL.
- **Two columns of one name under `OBJECT_CONSTRUCT(*)`.** Over a join whose sides share a column name the call kept
  one of the two values. It is now refused per row as `Duplicate field key 'ID'`, and `OBJECT_CONSTRUCT(* ILIKE
  'i%')` keeps the matching columns only, where the pattern was ignored.
- **CAST and TRY_CAST qualify no column.** `SELECT cast.a FROM t cast` ran, reading `cast` as the table's alias. The
  two words read as nothing but a cast where a value is read, so the statement is now a syntax error — at the
  token after `cast.a` in a select item, at the dot anywhere else — as is `try_cast.a`. The words stay aliases
  (`FROM t cast`, `SELECT a AS cast`), a star's qualifier (`SELECT cast.* FROM t cast`) and names when quoted.
- **A parenthesized list is a ROW, not an array, and no function takes one.** `ARRAY_CONSTRUCT((1, 2))` answered
  `[[1,2]]`, `COALESCE((1, 2), (3, 4))` `[1,2]`, and `HASH`, `TO_JSON`, `COUNT`, `ARRAY_AGG`, `IFF`, `NVL`, `LEAST`
  and the rest read the list as an array too. Each is now refused while the statement compiles, the list typed as
  the account types it: `Invalid argument types for function 'HASH': (ROW(NUMBER(1,0), NUMBER(1,0)))` at the call
  (COALESCE named as the `IFNULL` of its first two arguments), `Function ARRAY_CONSTRUCT does not support
  ROW(NUMBER(1,0), NUMBER(1,0)) argument type` for the constructors, `MIN`, `MAX` and `MODE`, and `invalid type
  [TO_VARCHAR(ROW(1, 2))] for parameter 'TO_VARCHAR'` for a conversion. A window function and a user-defined
  function refuse it the same way. `SEARCH((c1, c2), …)` still reads its columns, and `EQUAL_NULL` and `DECODE`
  compare a row with rows of as many values.
- **A date function refuses a value that is no date, time or timestamp.** `EXTRACT`, `DATE_PART` and the functions
  named after one part (`YEAR`, `MONTH`, `DAY`, `HOUR`, `DAYOFWEEK`, …) read a NUMBER as epoch seconds and failed
  per row over a FLOAT, a BOOLEAN, an ARRAY, an OBJECT or a BINARY. Each is now refused while the statement
  compiles — over no rows, in a branch never reached and in a view's body too — as `Function EXTRACT does not
  support NUMBER(1,0) argument type`, the value's type named and judged before the part's own name. `DATE_TRUNC`,
  `LAST_DAY` and `TIME_SLICE` say the same in their own names (`TIME_SLICE` also for a text, a VARIANT, a TIME and a
  zoned timestamp); `DAYNAME`, `MONTHNAME`, `NEXT_DAY` and `PREVIOUS_DAY` are `Invalid argument types for function
  'DAYNAME': (NUMBER(1,0))`, also for a TIME; `MONTHS_BETWEEN` and `ADD_MONTHS` name the difference or the shift
  they are planned as (`'DATE_DIFFDATEINMONTHS': (DATE, NUMBER(38,0))`, `'DATE_ADDMONTHSEOMTOTIMESTAMP':
  (NUMBER(1,0), NUMBER(38,0))`).
- **A part the value's kind lacks is refused.** `EXTRACT(hour FROM <DATE>)` answered 0 and `EXTRACT(year FROM
  <TIME>)` 1970; they are now `invalid value [hour] for parameter 'EXTRACT date/time part'`, the function naming
  itself (`invalid value [YEAR] for parameter 'YEAR date/time part'`). A TIME has an hour, a minute, a second and
  a nanosecond; a DATE every part but those and the zone components.
- **SET x := 1 is a block's statement.** Written as a statement of a script it ran as a session SET; it is now a
  syntax error at the name, as on an account.
- **A CSV unload refuses an empty string it could not tell from a NULL.** `COPY INTO @st/f FROM (SELECT '' …)`
  wrote an empty field; without FIELD_OPTIONALLY_ENCLOSED_BY, and unless EMPTY_FIELD_AS_NULL is FALSE, it is now
  `Cannot unload empty string without file format option field_optionally_enclosed_by being specified.` — also for a
  NULL when `NULL_IF = ('')`.
- **A routine needs a body.** `CREATE PROCEDURE p() RETURNS VARCHAR` — with or without LANGUAGE SQL or JAVASCRIPT,
  EXECUTE AS, a comment, SECURE or IF NOT EXISTS — and a JavaScript function without AS were created; each is now
  `Property 'as' must be specified`. A Python, Java or Scala routine with neither a body nor IMPORTS is `Either a
  function BODY or a ZIP or PY file in the IMPORTS clause is required.` (`a JAR file` for Java), a SQL table
  function without one is `Table function definition expected a subquery, found 'null'.`, and a SQL procedure
  whose body is empty or blank is `Empty SQL statement.`. A SQL scalar function may still be written without one:
  it answers NULL.
- **CREATE STREAMLIT … FROM judges its location.** Any location was taken. A user or table stage is now `Copy
  files not support stage kind: 'USER' for stage '~'.`, a stage that does not exist is `The specified stage S does
  not exist or the current role does not have access. …`, a location that is no stage reference is `invalid URL
  prefix found in: '…'`, and a location without the app's main file — `streamlit_app.py` when the statement names
  none — is `Missing MAIN_FILE.`. A ROOT_LOCATION is still taken as written.
- **A recursive CTE's branches have to be of families that meet.** A number computed as a FLOAT or as a text, a
  text computed as a number, and a boolean against a number were read with whatever each step produced; reading
  one is now `Type mismatch between anchor and recursive term of column 'N' in Recursive CTE 'R'`. A recursive
  view is still created and described — the refusal is the reader's — and names its CTE `V_RECURSIVE_CTE`.
- **ERROR_ON_NONDETERMINISTIC_UPDATE is honoured.** With the parameter set, an UPDATE … FROM whose target row
  several source rows join ran and took one of them; it is now refused before anything is written: `Duplicate row
  detected during DML action` / `Row Values: [1, "x"]`.
- **No role is exempt from privileges.** ACCOUNTADMIN, SYSADMIN and the built-in SYSTEM user read, wrote and
  altered objects another role owns. A statement now needs what the account asks of it, held by one of the
  session's roles or by the object's owner being among them: SELECT to read a table, a view, a materialized view or
  a stream — also as the source of an INSERT or a MERGE, as the target of an UPDATE, a DELETE or a MERGE, for a
  clone, a CREATE STREAM, a CTAS and a view over it; INSERT, UPDATE, DELETE and TRUNCATE to write; MODIFY for an
  ALTER or a COMMENT on a table, view, sequence, routine, stage, file format or schema; USAGE to call a function
  or a procedure and to USE a schema; MONITOR to DESCRIBE a schema; READ and WRITE on a stage, and any privilege
  at all to LIST it or to CREATE TABLE … LIKE. The refusal is the account's: `SQL access control error:` /
  `Insufficient privileges to operate on table 'T'. Your primary role R must have SELECT granted on TABLE D.S.T.`,
  naming `or one of your secondary roles` after USE SECONDARY ROLES. A view whose query reads such a table is
  refused under the view's name. Objects the session's own role created are unaffected.
- **USE SECONDARY ROLES ALL activates the user's roles, not the account's.** It made every role of the account
  active, so a role nobody was granted lent its privileges to the session.
- **An event table takes no rows from a statement.** `INSERT INTO et …`, an INSERT OVERWRITE, an INSERT ALL, an
  UPDATE, a `COPY INTO et` and a MERGE that inserts or updates were run; each is now `INSERT statement's target
  must be a table` with the statement's own word in front (`INSERT (MULTI-TABLE)`, `UPDATE`, `MERGE`). A DELETE,
  a TRUNCATE and a MERGE that only deletes still run. Its columns are the account's own: `ALTER TABLE et ADD /
  DROP / RENAME COLUMN`, `ALTER COLUMN` and `SWAP WITH` are `Unsupported feature 'INVALID OPERATION FOR EVENT
  TABLES'.`, `CREATE TABLE … CLONE et` is `Invalid operation Cannot clone from an event table.`, and `CREATE OR
  REPLACE TABLE et …` is `Object 'ET' already exists as EVENT_TABLE`.
- **A dropped role's objects have an owner again.** An object whose owner role no longer existed could be replaced
  or dropped by any role; it now belongs to the role that dropped its owner, and only that role acts as its owner.
- **A name a relation carries twice is ambiguous.** `SELECT a FROM (SELECT 1 a, 2 a) t`, `t.a`, `MAX(a)` and the
  same name in a WHERE, a GROUP BY, an ORDER BY, a join's ON or over a common table expression answered the first
  column; each is now `ambiguous column name 'A'`. A star, a position and the relation's other columns still read,
  and a name an alias list gives twice (`FROM t AS q (x, x)`) still reads its first column.
- **A stored relation needs a name of its own for every column.** `CREATE VIEW v AS SELECT 1 a, 2 a`, the same
  query behind a materialized view or a `CREATE TABLE … AS SELECT`, and a star that expands to two columns of one
  name are `duplicate column name 'A'`; a column list that names the columns apart stores the query, and one that
  repeats a name is refused itself.
- **ORDER BY refuses a name two output columns carry, the columns of a star counted.** `SELECT *, id * -1 AS id
  … ORDER BY id`, `SELECT *, * … ORDER BY id`, `SELECT * FROM a JOIN b USING (k) ORDER BY v` over a `v` both sides
  hold, and `SELECT 1 a, 2 a UNION ALL … ORDER BY a` are `ambiguous column name`; a position still sorts.
- **A view whose body no longer compiles refuses every read.** A view over a table that lost a column the body
  names answered whatever the reading statement did not need of it — a count, a constant, another column, a read
  through a view over it. Each is now the body's own refusal, at its place in the view's CREATE statement.
- **A materialized view whose base table was altered is invalid.** After `ALTER TABLE … RENAME COLUMN`, `DROP
  COLUMN`, `ALTER COLUMN … SET DATA TYPE` or `SET / DROP NOT NULL` on the base table — whether the view reads the
  column or not — and after the table is dropped or replaced, a read is `Failure during expansion of view 'MV':
  SQL compilation error: Materialized View MV is invalid. Invalidation reason: …`; a suspended view is refused
  the same way, as marked invalid manually. UNDROP of the table and RESUME of a suspended view bring it back.
- **SYSTEM$GET_TAG matches a quoted name exactly.** `SYSTEM$GET_TAG('"tg"', 't1', 'TABLE')` read the tag TG; a
  quoted lower-case tag, table or column that was created unquoted is `does not exist or not authorized`, in
  `SYSTEM$GET_TAG_ALLOWED_VALUES` too.
- **A subquery two levels in may not read the outer row.** `SELECT id, (SELECT COUNT(*) FROM g WHERE g.v >
  (SELECT MIN(v) FROM g g2 WHERE g2.id <= fz.id)) FROM fz` was answered; a query nested in a subquery reading the
  row of the query around both is now `Unsupported subquery type cannot be evaluated` — a plain query's row, a
  grouped query's key and its aggregate alike, under a scalar subquery, EXISTS and IN. The refusal is placed at the
  first-level subquery in a select list or a HAVING and carries no position in a WHERE.
- **A correlated subquery over an empty or pruned relation is judged before any row.** A subquery reading the outer
  row through a table function is refused over an empty table and under `WHERE FALSE`, `WHERE NULL` or a filter
  the statistics rule out — only a relation of exactly one row is read row by row. A derived table, a common table
  expression or a view that passes one table's columns through is judged as the query over that table, so
  `… FROM (SELECT * FROM fz WHERE 1 = 0) fz` and `… FROM (SELECT * FROM fz LIMIT 0) fz` are refused where they
  answered no rows.
- **A pruned scan no longer hides an aggregate beside the outer row.** `id IN (SELECT MAX(v) + fz.id FROM g WHERE
  FALSE)` answered no rows and `EXISTS (SELECT MAX(v) + fz.id FROM g WHERE FALSE)` every row; both are refused, as
  is the scalar `(SELECT v + fz.id FROM g WHERE FALSE)`, which read NULL.
- **A stage's STORAGE_INTEGRATION has to exist.** `CREATE STAGE s URL = '…' STORAGE_INTEGRATION = nosuch` created
  the stage; it is now `Integration 'NOSUCH' does not exist or not authorized.`, an integration of another kind
  `Integration 'X' is not a storage integration of type EXTERNAL_STAGE.`, and a name written as a text is refused
  where it stands. `ALTER STAGE … SET STORAGE_INTEGRATION` judges the same way.
- **ORC takes no COMPRESSION.** `CREATE FILE FORMAT f TYPE = ORC COMPRESSION = AUTO` is `Option COMPRESSION is not
  valid for file format type ORC.`, and the option no longer appears in an ORC format's description.
- **`IDENTIFIER(:name)` over an undeclared name is the block's compilation error** inside an IF or a loop body too:
  `invalid identifier 'NOSUCH'` at the colon, before any statement runs. It was a syntax error at `NULL`, raised
  when the body ran.
- **No name reaches DUAL written in FROM.** `SELECT dual.column1 FROM DUAL` and `SELECT dual.* FROM DUAL` answered
  NULL. Only an alias reaches the pseudo-table there: the first is now `invalid identifier 'DUAL.COLUMN1'` at the
  name — in the select list, WHERE, GROUP BY, ORDER BY and a join's ON alike, `DUAL.$1` too — and the second `Object
  'DUAL' does not exist or not authorized.` `COLUMN1`, `$1`, a plain star and `FROM DUAL d` read it as before, and a
  SELECT with no FROM still answers to `DUAL.COLUMN1`, from a query nested inside it too.
- **A set operation arm of several columns is refused for its type.** `SELECT (SELECT 1, 2) UNION ALL SELECT 1`
  was `Unsupported: Scalar subquery with multi-column SELECT clause.`; the arms' types are compared first, so it is
  `inconsistent data type for result columns for set operator input branches, expected NUMBER(1,0), got
  ROW(NUMBER(1,0), NUMBER(1,0))` — "expected" the later arm — under UNION, INTERSECT and EXCEPT, and in the INSERT,
  the CREATE TABLE … AS, the derived table and the IN list around it. Two ROWs of one width keep the old refusal.
- **A column a clustering key reads keeps its name and its place.** `ALTER TABLE t RENAME COLUMN a TO z` and
  `ALTER TABLE t DROP COLUMN a` ran over a table clustered by `a`, by `a + 1`, by `UPPER(a)` or by a path into it.
  They are now `Cannot rename column 'A' which belongs to a clustering key` and `Cannot drop column 'A' which
  belongs to a clustering key`, named before a taken new name, before a column the list misses and whatever IF
  EXISTS says. Once the key is dropped both run, and the column's type, comment and nullability change as before.
- **A policy is not created over another.** A second `CREATE MASKING POLICY p` or `CREATE ROW ACCESS POLICY p`
  silently replaced the first. It is now `Object 'P' already exists.`, judged before the signature and the body
  are, `IF NOT EXISTS` answers `P already exists, statement succeeded.`, and a policy renamed onto a taken name,
  its own included, is refused the same way.
- **A schema renamed to its own name finds it taken.** `ALTER SCHEMA s RENAME TO s` ran and changed nothing; it is
  now `Object 'S' already exists.`, the name spelled as the statement wrote it.
- **INFORMATION_SCHEMA's views take no statement.** `COMMENT ON TABLE` and `COMMENT ON COLUMN` over one, an
  INSERT, UPDATE, DELETE or TRUNCATE of one, `ALTER TABLE` and `ALTER VIEW … SET SECURE`, a `CREATE OR REPLACE VIEW`
  over one and a GRANT or REVOKE on one are `Insufficient privileges to operate on view 'TABLES'.`, the view found
  before the statement's own kind is judged. A CREATE in the schema, of any kind, and a grant on the schema or on
  all its views are `Insufficient privileges to operate on schema 'INFORMATION_SCHEMA'.`, where a CREATE answered
  `Cannot create views in INFORMATION_SCHEMA` and a GRANT ran. `GET_DDL('VIEW', 'db.INFORMATION_SCHEMA.TABLES')`
  is `Object does not exist, or operation cannot be performed.` where it printed a placeholder.
- **GROUPING needs a GROUP BY.** `SELECT GROUPING(n) FROM t` answered 0 for every row. A GROUPING or GROUPING_ID
  call in a query that groups by nothing is now `GROUPING function without a GROUP BY.`, as on the account — in the
  select list, WHERE, ORDER BY, QUALIFY, a HAVING that only filters, a FROM-less select, and in the INSERT, the
  CREATE TABLE … AS and the CREATE VIEW built over such a query — once the query's names have resolved. Under
  `GROUP BY ALL` the call is one of the keys, and refused as `GROUPING function cannot appear outside of SELECT,
  HAVING, and ORDER BY clauses.`
- **LISTAGG's delimiter is a constant.** `LISTAGG(n, g)` over a column joined the values with the column's *name*.
  A delimiter a row, the session or a random draw decides is now `argument 2 to function LISTAGG needs to be
  constant, found 'RT.G'`, the argument echoed from the plan (`CAST(T.B AS VARCHAR(134217728))`,
  `CURRENT_DATABASE()`), over an empty table and under OVER or WITHIN GROUP too.
- **A lateral table function's arguments are compiled before a row is read.** Over a relation holding no row,
  `FROM t, LATERAL FLATTEN(input => t.nosuch)` answered no rows; it is now `invalid identifier 'T.NOSUCH'` at the
  name, an unknown function is `Unknown function NOSUCHFN.`, and an argument of a type the parameter does not take
  (`LATERAL FLATTEN(input => t.n)` over a number) is `invalid type [NUMBER(38,0)] for parameter 'input'` — the
  refusals a relation holding rows already gave.
- **A star under DISTINCT is refused for the statement's text.** `COUNT(DISTINCT *) OVER ()` was counted; it is
  `Unsupported feature 'TOK_STAR'.` like the plain call, and that refusal now comes ahead of every name in the
  statement — a table that is not there included — in whichever clause the call stands.

- **A table function is called in FROM inside `TABLE( … )` or after LATERAL, and nowhere else.** `SELECT * FROM
  FLATTEN(…)`, `FROM t, FLATTEN(…) f`, `FROM t LEFT JOIN FLATTEN(…)` and `FROM t CROSS JOIN FLATTEN(…)` ran; they
  are now syntax errors at the parenthesis after the name, as on the account, in a view's query and a routine's
  body too. Any other name written that way — `FROM t1('x')`, `FROM GENERATOR(…)`, `FROM UPPER('a') z`, `DELETE …
  USING t2 ('x')` — was refused in other words and is refused at the same parenthesis. Write `LATERAL FLATTEN(…)`
  or `TABLE(FLATTEN(…))`.

- **A CREATE TABLE's property is one the account knows.** `CREATE TABLE t (a INT) x = 1` created the table. The
  properties are now judged before anything else the statement names - a taken name, a missing schema, the table a
  LIKE or a CLONE copies: a name that is no property is `invalid property 'x' for 'TABLE'`, spelled as written (a
  quoted one with its quotes); a value of another type is refused as written, `invalid value [5] for parameter
  'CHANGE_TRACKING'` (a switch takes TRUE and FALSE alone, a count an unsigned number, a text no number); a
  parameter of another level is `cannot set parameter 'TIMEZONE' at 'TABLE'`; a property of an external or an
  Iceberg table is `Unsupported feature 'AUTO_REFRESH'.` on a plain one, and `BASE_LOCATION` asks for an Iceberg
  table. A name written twice is `duplicate property 'x';` ahead of all of them.
- **A CREATE VIEW's and a CREATE SEQUENCE's properties are judged the same way.** A view names an unknown property
  a table's (`invalid property 'x' for 'TABLE'`, as the account does), is refused a parameter of another level
  (`cannot set parameter 'TIMEZONE' at 'VIEW'`), a table's parameters (`Insufficient privileges to operate on
  schema 'S'.`) and a table's own properties. A sequence takes its comment, START, INCREMENT and its ordering and
  nothing else: `invalid property 'foo' for 'SEQUENCE'`, `cannot set parameter 'TIMEZONE' at 'SEQUENCE'`. Both
  took any name.
- **No table option follows a CREATE TABLE's query.** `CREATE TABLE t AS SELECT 1 a COMMENT = 'c'`, and CLUSTER BY
  or COPY GRANTS there, are syntax errors at the option, as on the account; the options stand before AS. They were
  taken after the query too.
- **`ALTER MATERIALIZED VIEW … REFRESH` is a syntax error** at REFRESH, the view there or not: a materialized view
  has no such action. It was accepted and did nothing.

- **A user-defined function called in a block is handed its arguments' declared types.** Inside a Snowflake
  Scripting block the argument check of a user-defined function stood aside altogether. It now judges an argument
  that still carries its type — a variable bound as one, a NULL or a number substituted with its declared type, a
  cast the statement writes — as the account does, by the declaration and whatever the variable holds: `DECLARE s
  NUMBER(10,2) DEFAULT 1.5; … SELECT fd(:s)` with `fd(d DATE)` is `Invalid argument types for function 'FD':
  (NUMBER(10,2))`, holding NULL as well, in a RESULTSET's query, a SELECT … INTO, a CTAS the block runs and a
  procedure's argument. It answered `date`.
- **A BINARY beside a NULL is refused in arithmetic.** `NULL::BINARY(10) + NULL`, `TO_BINARY('00') - NULL` and the
  like are `Invalid argument types for function '+': (BINARY(10), NULL)` at the operator, as a DATE's, a TIME's and
  a BOOLEAN's were; they answered NULL.
- **A tag whose allowed values were all dropped takes no value.** `ALTER TAG t DROP ALLOWED_VALUES …` that removes
  the last value leaves the list in place, empty: SHOW TAGS lists `[]`, GET_DDL writes ` allowed_values `, a clone
  keeps it, and every `SET TAG t = '…'` — on a table, a column, a view, a schema, in a `WITH TAG` — is `Value 'a' is
  not allowed by the specified allowed_values for tag 'T'.`, as on the account. Such a tag read as one without a
  list and took any value. `UNSET ALLOWED_VALUES` still removes the list.
- **A quoted database role name keeps its case.** `CREATE DATABASE ROLE "dr_low"` made, and every later statement
  reached, `DR_LOW`. It is now a role of its own beside `DR_LOW`, as on the account: `DROP DATABASE ROLE "dr_up"` no
  longer drops `DR_UP`.
- **An Iceberg table's columns take the Iceberg format's types.** `CREATE ICEBERG TABLE`, `ALTER ICEBERG TABLE … ADD
  COLUMN` and `ALTER COLUMN … SET DATA TYPE` held any type a table takes. Now a string is unsized or
  `VARCHAR(134217728)` (`For Iceberg tables, only max length (134,217,728) is supported for 'VARCHAR(L)/STRING(L)' or
  use STRING directly.`), a binary unsized or `BINARY(67108864)`, a time or a timestamp unsized or at scale 6
  (`Invalid time type scale specified for column 'C' with data type 'TIME(3)'. …`), and a number is INT, BIGINT or a
  NUMBER written with both its precision and its scale (`data type 'NUMBER/DECIMAL' without precision and scale is
  not supported for Iceberg tables.`). CHAR, NCHAR, SMALLINT, TINYINT, VARBINARY, VECTOR, VARIANT, a plain OBJECT or
  ARRAY, GEOGRAPHY, GEOMETRY and TIMESTAMP_TZ are `Unsupported data type '…' for iceberg tables.`, in the members
  of a structured OBJECT, ARRAY and MAP as well. The types are judged column by column before anything else the
  statement names — the table, its schema, its external volume. A table created from a query is held to the same:
  a `TIMESTAMP_NTZ(9)` or a VARIANT result column is refused.
- **An Iceberg table and a table are altered each by its own statement.** `ALTER TABLE` over an Iceberg table, in
  every action, and `COMMENT ON TABLE` or `COLUMN` over one, answer `The table T is an Iceberg table. Iceberg tables
  should use ALTER ICEBERG TABLE commands. Please ensure the appropriate command format is used.`; `ALTER ICEBERG
  TABLE` over an ordinary table answers `The table T is a legacy Iceberg table. Legacy Iceberg tables should use the
  ALTER TABLE commands. …`. A swap with an Iceberg table on either side is `SWAP WITH is not supported for ICEBERG
  TABLE.`, and `ALTER ICEBERG TABLE … SWAP WITH` a syntax error at SWAP.
- **An Iceberg table cannot stop tracking its changes.** `ALTER ICEBERG TABLE … SET CHANGE_TRACKING = FALSE`, alone
  or among other properties, and `UNSET CHANGE_TRACKING` are refused as the CREATE was: `invalid value 'false' for
  property 'CHANGE_TRACKING', Reason: Change Tracking cannot be turned off for Iceberg tables`.
- **A clone is of its source's kind.** `CREATE TABLE … CLONE` of an Iceberg table and `CREATE ICEBERG TABLE … CLONE`
  of an ordinary table are refused (`Invalid operation The table T is an Iceberg table. Clone Iceberg table should
  use CREATE ICEBERG TABLE CLONE command. …`); `LIKE` copies the columns either way.
- **`ALTER CORTEX SEARCH SERVICE … UNSET COMMENT` is a syntax error.** UNSET takes PRIMARY KEY and ATTRIBUTES and
  nothing else, as on the account; a comment is set again, never unset. A quoted service name is no longer folded
  to upper case, so `"svc"` no longer reaches `SVC`.
- **A CREATE needs its privilege where the object lands, held by the primary role.** A role creates a table,
  a view, a sequence, a stage, a routine, a policy or any other object of a schema only where it, a role it
  inherits, PUBLIC or a database role granted to one of them owns the schema or holds the CREATE privilege for that
  kind on it, and a schema only where the same holds of CREATE SCHEMA on the database: `Insufficient privileges to
  operate on schema 'S'. Your primary role R must have CREATE TABLE granted on SCHEMA D.S.` A secondary role
  authorizes no CREATE, ACCOUNTADMIN and SYSADMIN are no exception, and neither is the engine's own default
  session: `USE ROLE PUBLIC; CREATE TABLE t (a INT)` in a schema SYSADMIN owns was taken, and so was every CREATE
  after a `USE ROLE` to a role holding nothing. A temporary object asks for nothing. The refusal is still a
  `SecurityException`, now in the account's words. A materialized view, a dynamic table and a hybrid table each ask
  for their own privilege, where CREATE VIEW or CREATE TABLE stood in for them.
- **A rename creates the new name.** `ALTER TABLE … RENAME TO`, and a view's and a sequence's, ask for the CREATE
  privilege on the schema the new name lands in, in place or in another schema, as the account does.
- **A subquery holding the outer query's aggregate stands in its select list or HAVING only.** An aggregate
  written inside a subquery over the columns of a query around it alone is that query's aggregate, and the subquery
  holding it — however deep — may not stand in that query's WHERE, a join's ON, its GROUP BY or its ORDER BY, nor
  be named there by a select item's position or alias: `SELECT id FROM fz WHERE EXISTS (SELECT MAX(fz.id) FROM g)`
  is `Subquery containing correlated aggregate function [MAX(FZ.ID)] can only appear in having or select clause`,
  raised while the statement compiles and whatever rows it would read. The WHERE of an UPDATE and of a DELETE is held
  to the same. Some of these ran — an EXISTS over no matching row answered TRUE — and the rest were refused as
  `invalid identifier 'FZ.ID'` or as an unsupported subquery.
- **An aggregate of an EXISTS's or IN's own may not read the outer row inside its argument.** `EXISTS (SELECT
  MAX(fz.id + g.v) FROM g)` and `id IN (SELECT MAX(fz.id + g.v) FROM g)` are `Unsupported subquery type cannot be
  evaluated`, as the scalar subquery so written already was; they failed on the first row as `invalid identifier
  'FZ.ID'`, and answered where no row reached the aggregate.
- **A named argument is matched by its exact name.** `TABLE(f("n" => 1))` over a parameter `N` is `named
  arguments [n] do not match any signature for function F`, as a scalar call and a CALL already were; a name is
  folded as an identifier is, so `"N" => 1` and `n => 1` reach it. A CALL naming an argument twice is `duplicate
  named argument A for function P`, placed at the second name, and one with more arguments than the procedure has
  parameters is `too many arguments for function [P(1)] expected 0, got 1`.
- **A routine that names secrets and no external access integration cannot be called.** Its call is `invalid
  property 'SECRETS' for 'FUNCTION'`, for a procedure too, and a secret dropped since is `Object 'S' does not exist
  or not authorized.` The routine is still created, listed and described.
- **A PIVOT's aggregate is one aggregate over one column.** `PIVOT (COUNT(DISTINCT c) …)`, `SUM(c + 1)` and
  `MAX(UPPER(c))` are syntax errors where they are written; `ANY_VALUE(c)` and a function that aggregates nothing
  are `'ANY_VALUE' is not a recognized aggregate function`; a name in quotes or one nothing answers to is `invalid
  function '"SUM"'`; and the column is named as an identifier is, so `SUM(low)` no longer reaches a column `"low"`
  (`invalid identifier 'LOW'`). Each of these ran, most answering NULL.
- **A template query reads INFER_SCHEMA.** `CREATE TABLE t USING TEMPLATE (…)` takes its query in parentheses
  (`USING TEMPLATE SELECT …` is a syntax error) and the query reads INFER_SCHEMA as the first relation of its FROM
  clause: any other table function there is `Unsupported feature 'INFER_SCHEMA function must be used in the
  TEMPLATE sub-query'.`, and a table, a subquery, a common table expression or a set operation is `… 'Table
  function must be used in the TEMPLATE sub-query'.` A query answering several rows is `Invalid template: Exactly
  1 template is expected. Found: 3`. FLATTEN, GENERATOR and RESULT_SCAN templates were taken.
- **A network rule's TYPE decides its modes and reads its values.** A HOST_PORT rule needs `MODE = EGRESS` (the
  default INGRESS is `The network rule mode INGRESS is not supported by the network rule type HOST_PORT. The
  network rule mode must be one of [EGRESS, DATA_CONNECTIVITY_PROXY_EGRESS].`), and every type refuses the modes
  it does not take; an unknown MODE is `The network rule mode must be one of the snowflake supported network rule
  mode.` An IPV4 value is `a.b.c.d[/n]`, an IPV6 one an address, an AWSVPCEID `vpce-` and 8 or 17 hex digits, an
  AZURELINKID or GCPPSCID a whole number: anything else is `invalid value '[bad1, bad2]' for property
  'VALUE_LIST'`, naming the values that fail, at CREATE, CREATE OR ALTER and ALTER … SET alike. Any text was kept.
- **A change-tracking point reads no function whose value moves.** A stream's AT | BEFORE and a CHANGES clause's
  AT | BEFORE | END refuse RANDOM, UUID_STRING, LAST_QUERY_ID, CURRENT_DATE, CURRENT_TIME, LOCALTIMESTAMP and
  SYSDATE as they refused CURRENT_TIMESTAMP under a TIMESTAMP alone: `Query contains time travel to a version
  containing function 'RANDOM', but change tracking is not supported on queries with non-deterministic functions
  in versions.`, placed at the call. A CHANGES clause's END point was not judged at all.
- **RESULT_LIMIT is a number of 0 to 10000** for TASK_HISTORY, CURRENT_TASK_GRAPHS and COMPLETE_TASK_GRAPHS: a
  negative one is `invalid value [-1] for parameter 'RESULT_LIMIT'` and a text `invalid type [VARCHAR(1)] for
  parameter 'RESULT_LIMIT'`, where both were read as no limit; TASK_HISTORY also refuses one above 10000.
- **IS_ROLE_IN_SESSION(NULL) and IS_ROLE_IN_SESSION(1)** are refused while the statement compiles (`invalid
  argument for function [IS_ROLE_IN_SESSION] unexpected argument [NULL] at position 0,`), and
  IS_DATABASE_ROLE_IN_SESSION over an expression of a column is `invalid identifier`.
- **A dynamic table's column list has to fit its query.** `CREATE DYNAMIC TABLE dt (x) … AS SELECT i, n …` and a
  list that is too long are `Invalid column definition list`, a name given twice is `duplicate column name 'X'`,
  and a type the query's column cannot be written to is `incompatible types: [NUMBER(38,0)] and [DATE]`; a value
  the declared type cannot hold fails the first refresh (`Failed to refresh dynamic table with refresh_trigger
  INITIAL …`). The list was read and ignored.
- **An integration a statement names has to exist and to be of the kind the clause takes.** A pipe's
  `ERROR_INTEGRATION` and `INTEGRATION`, a secret's `API_AUTHENTICATION` and an artifact repository's
  `API_INTEGRATION` took any name. A miss is `Integration 'X' does not exist or not authorized.` (an artifact
  repository's `Object 'X' …`); a pipe's error integration that is no queue is `Integration 'X' is not a
  notification integration.`, one the account reads from `Invalid direction type of error_integration X, expected
  OUTBOUND, got INBOUND.`; a pipe's `INTEGRATION` needs `AUTO_INGEST = TRUE` and a queue of the stage's cloud; a
  task's integration that is no queue is `Integration 'X' is not a valid notification integration for UserTasks.`;
  a secret's integration that is no security integration is `Integration 'X' is not of type API Authentication.`,
  one of the other authentication type `invalid value [X] for parameter 'API_AUTHENTICATION'`, a disabled one
  `Integration 'X' is not enabled.`, and scopes it does not allow `invalid value '[c]' for property
  'OAUTH_SCOPES'`; an artifact repository's integration of another provider is `Invalid API Provider for 'Artifact
  Repository' object.` A name written as a string is the integration's exact name.
- **A secret takes no ENABLED**: `CREATE SECRET … TYPE = CLOUD_PROVIDER_TOKEN … ENABLED = TRUE` is `invalid
  property 'ENABLED' for 'SECRET'`.
- **SYSTEM$GET_TAG needs its tag and its object to exist.** A missing tag is `Tag 'X' does not exist or not
  authorized.`, a missing table, schema, column or warehouse is refused in the words of its kind, an unknown domain
  is `Unknown domain: X.`, and a NULL argument is `argument 0 to function SYSTEM$GET_TAG needs to be constant,
  found 'null'`; each answered NULL.
- **A pipe that ingests automatically from an Azure stage needs an integration**: `Pipe Notifications bind failure
  "Integration cannot be null for an auto-ingest pipe that uses a stage in AZURE."`
- **SHOW PARAMETERS IN ROLE is no scope.** `SHOW PARAMETERS IN ROLE r` is `Unsupported statement type 'Cannot show
  parameters of SqlIdentifier{qualifierNames=[], identifierName=ROLE}'.`, as on the account; it listed the session's
  parameters. A name that is no container is refused alike: `IN ACCOUNT abc`, `IN DATABASE a.b` and
  `IN SCHEMA a.b.c` are `Object does not exist, or operation cannot be performed.`
- **A foreign key has to point at a key of a table that exists.** `REFERENCES nosuch (id)` is `Table
  'DB.S.NOSUCH' does not exist or not authorized.`; columns that are no primary or unique key of the parent, in the
  key's own order, are `primary or unique key does not exist for the columns referenced by the foreign key` (a view
  has none); another number of columns is `Number of columns in foreign key specification does not match number of
  columns in foreign key reference`; and a key that names no columns of a parent with no key that fits is `could
  not find matching constraints for the foreign key`. Each was taken, at CREATE TABLE and at ALTER TABLE … ADD.
- **A table-level key names columns of its table.** `PRIMARY KEY (nosuch)` and `FOREIGN KEY (nosuch) REFERENCES …`
  are `invalid identifier 'NOSUCH'` at the name's own place, as a UNIQUE's already was (now with its place).
- **No two constraints of a table share a name.** A second `CONSTRAINT c` is `constraint 'C' already exists for
  table 'T'`, and a foreign key of that name `Object 'C' already exists.`, at CREATE TABLE and at ALTER TABLE … ADD.
- **Contradicting constraint properties are refused**: `RELY NORELY`, `ENABLE DISABLE`, `DEFERRABLE NOT DEFERRABLE`
  and `INITIALLY DEFERRED INITIALLY IMMEDIATE` are `Conflicting constraint properties`, and `NOT DEFERRABLE
  INITIALLY DEFERRED` is `Constraint is not deferrable` (the first was taken, the others were syntax errors).
- **An identity's ORDER is written once**: `AUTOINCREMENT ORDER NOORDER` is `duplicate property 'SEQUENCE_ORDER';`.
- **A stage's DIRECTORY property list is judged option by option.** `DIRECTORY = (ENABLE = MAYBE)` is `invalid
  value [MAYBE] for parameter 'ENABLE'` (a quoted `'TRUE'` and a `1` likewise, and AUTO_REFRESH and
  REFRESH_ON_CREATE in the same words), `DIRECTORY = (ENABLE = TRUE FOO = 1)` is `invalid parameter 'FOO'`, and an
  empty list, a string or a bare TRUE is `Parameter for directory requires property list. Example:
  DIRECTORY=(ENABLE=TRUE AUTO_REFRESH=FALSE).`, at CREATE STAGE and at ALTER STAGE … SET alike; an ALTER refuses
  REFRESH_ON_CREATE (`invalid property 'true' for 'REFRESH_ON_CREATE'`). Each ran, and only ENABLE was read.
- **ALTER STAGE … SET knows the stage's properties.** `SET FOO = 'x'` is `invalid property 'FOO' for 'STAGE'`; a
  URL, CREDENTIALS or ENCRYPTION on an internal stage is `Cannot set URL, credentials, or encryption key of an
  internal or temporary stage.`; `SET COMMENT = 5` is `invalid value [5] for parameter 'COMMENT'`; `SET FILE_FORMAT =
  (TYPE = NOPE)` is `invalid value [NOPE] for parameter 'TYPE'`; a STORAGE_INTEGRATION that does not exist is
  refused by name. With IF EXISTS only the stage's absence is forgiven: a refused property was silently taken.
- **ALTER STAGE … REFRESH judges its option and its stage.** `REFRESH FOO = 'x'` is `Invalid Alter Stage Refresh
  Option FOO`, `SUBPATH = 5` is `invalid value [5] for parameter 'SUBPATH'` (a NULL likewise), and `ALTER STAGE IF
  EXISTS nosuch REFRESH` refuses the missing stage, as on the account; each answered success.
- **RANDOM's seed has to fit its family's range.** A NUMBER takes the signed 64-bit range (`RANDOM(1e19)` is
  `Numeric value '10000000000000000000' is not recognized`); text eighteen digits (`RANDOM('9223372036854775807')`
  is `Numeric value '9223372036854775807' is out of range`); a FLOAT and a VARIANT number eighteen digits too
  (`Number out of representable range: type FIXED[SB8](18,0){not null}, value 1e+18`, and `type FIXED` for a
  VARIANT); a VARIANT text that is no seed is `Failed to cast variant value "…" to FIXED`. Each was wrapped into a
  64-bit seed.
- **A predicate is no separator for CONCAT_WS once there are two values**: `CONCAT_WS(1 = 1, 'a', 'b')` is
  `Invalid argument types for function 'CONCAT': (VARCHAR(1), BOOLEAN, VARCHAR(1))`; it answered `atrueb`.
- **A privilege on a routine names its argument types.** `GRANT USAGE ON FUNCTION f TO ROLE r`, the REVOKE and
  `SHOW GRANTS ON FUNCTION f` are `Argument types of function 'F' must be specified.`, a procedure in the same
  words, as on the account; each ran against every overload.
- **A grant names its object in the object's own case.** `GRANT USAGE ON SEQUENCE "seq" …` where only `SEQ` exists
  is `Sequence 'DB.S."seq"' does not exist or not authorized.` (a stage likewise); it reached `SEQ`.
- **A stream and a relation are told apart by kind.** `DESCRIBE STREAM t` over a table or a view is
  `Invalid object type: 'STREAM'`, and `DESCRIBE TABLE s` over a stream `Invalid object type: 'TABLE'` (`'VIEW'`,
  `'MATERIALIZED_VIEW'`, `'DYNAMIC_TABLE'`), as on the account, where one namespace holds both. The object was
  reported missing.
- **A SQL function whose query body answers more than one row is refused**: `Single-row subquery returns more than
  one row.`, as the scalar subquery the call is inlined as. The first row's value was answered.
- **A constant is no clustering key for a materialized view** (`Invalid clustering key 1`), and reclustering cannot
  be paused on one that has no key (`Table 'MV' is not clustered`).
- **A SQL table function's arguments are matched to its parameters by type.** `TABLE(f('1'))` over `f(p NUMBER)`
  is `Invalid argument types for function 'F': (VARCHAR(1))`, positioned where the function is named, as on the
  account; it ran. A NUMBER parameter takes a NUMBER of its own scale (`NUMBER` never `1.5`, `NUMBER(10,2)` never
  `1`), a FLOAT no NUMBER, a VARCHAR text of any width, every other family itself alone, and an untyped NULL
  matches nothing. A wrong number of arguments is the same sentence. A scalar SQL function still converts.
- **Two collations that disagree at one level are refused in a set operation.** Over `c` declared `en-ci` and `d`
  declared `en-cs`, `SELECT c FROM t UNION SELECT d FROM t` is `Incompatible collations: 'en-cs' and 'en-ci'`
  (UNION ALL, INTERSECT and EXCEPT alike, and two COLLATEs that disagree); the column was compared in binary.
- **The array functions refuse an argument family their position does not take**, while the statement compiles:
  `ARRAY_SIZE('abc')` is `Invalid argument types for function 'ARRAY_SIZE': (VARCHAR(3))`, `ARRAY_CAT(CURRENT_DATE(),
  [1])` `… (DATE, ARRAY)`, `ARRAY_SLICE(1, 0, 1)` `… (NUMBER(1,0), NUMBER(1,0), NUMBER(1,0))`. An array position
  refuses text, numbers, booleans, dates and times, binaries and objects; a value position text, dates and times and
  binaries; a whole-number position booleans, dates and times, binaries, objects and arrays (the table is in
  `docs/functions.md`). They answered NULL, or failed on the row in other words.
- **A UUID beside a number, a date or a boolean in a function that folds its branches is refused.** Over a UUID
  column `u`, `IFF(TRUE, 1, u)`, `COALESCE(u, 1)`, `NVL`, `NVL2`, `IFNULL`, `NULLIF`, `DECODE`, `CASE`, `GREATEST`,
  `LEAST`, `u = 1` and `u IN (1)` are `Can not convert parameter '1' of type [NUMBER(1,0)] into expected type
  [UUID]` — the later of the two operands, and the type the first one set — where they answered a NUMBER or a
  VARCHAR. `TO_ARRAY(u)` and `TO_OBJECT(u)` are `invalid type [TO_ARRAY(UT.U)] for parameter 'TO_ARRAY'`. A UUID
  beside text or NULL stays a UUID, and `TO_VARIANT`, `ARRAY_CONSTRUCT` and `OBJECT_CONSTRUCT` take one.
- **GET_DDL finds the object it names while the statement compiles.** A missing object, or an object type it does
  not know, is refused wherever the call stands — under `SYSTEM$TYPEOF`, behind `WHERE FALSE`, in the branch
  `IFF(FALSE, …)` never takes, over an empty table, in a CTE nothing reads, in an UPDATE, DELETE or MERGE that
  touches no row — where it was refused only once a row computed the call. A name written as a whole number
  names no object: `GET_DDL('table', 1)` is `Object 'table' does not exist or not authorized.`, and a number
  with a fraction is `Invalid value [CAST(1.5 AS NUMBER(18,0))] … constant arguments expected`. A missing
  warehouse is refused as one (`Warehouse 'W' does not exist or not authorized.`).
- **ALTER SESSION SET takes a value of the parameter's own kind.** A STRING parameter refuses a number or a
  boolean (`ALTER SESSION SET QUERY_TAG = 5`), a NUMBER parameter anything but a whole number of 32 bits
  (`LOCK_TIMEOUT = '5'`, `= 1.5`), a BOOLEAN parameter anything but TRUE or FALSE (`AUTOCOMMIT = 1`,
  `= 'true'`) — each `invalid value [<as written>] for parameter '<NAME>'`. A value of the right kind is then
  held to the parameter's range or vocabulary, in the account's sentence: `WEEK_START = 8`,
  `STATEMENT_TIMEOUT_IN_SECONDS = -1` (`parameter value out of range: -1. Must be between 0 and 604,800.`),
  `BINARY_OUTPUT_FORMAT = 'nope'`, `LOG_LEVEL = 'nope'`, a QUERY_TAG past 2000 characters.
- **A session TIMEZONE is a zone database name spelled exactly.** An offset (`'+02:00'`, `'GMT+2'`, `'Z'`) and an
  abbreviation outside EST, MST, HST and ROC (`'PST'`) are refused, as on the account; `'EST'`, `'ROC'`,
  `'Etc/GMT+2'` and `'US/Pacific'` are taken, and a session in `'EST'` or `'ROC'` now runs in that zone rather than
  in UTC.
- **A quoted session parameter name is no parameter**: `ALTER SESSION SET "LOCK_TIMEOUT" = 5` is
  `invalid parameter '"LOCK_TIMEOUT"'`, and a name written twice in one statement is `duplicate property '<name>';`.
- **SYSTEM$ABORT_SESSION takes one argument that is a session's number**: NULL, text that is no number, a number
  with a fraction, a boolean and a second argument are refused.
- **A routine's required argument may not follow an optional one.** `CREATE FUNCTION f(x INT DEFAULT 1, y INT)`
  and the same PROCEDURE are `required argument Y cannot follow optional argument X`, at the argument's name;
  they were created.
- **RETURN TABLE takes a RESULTSET by its name**, or a call over one name (`RESULTSET_FROM_CURSOR(c)`). A query
  (`RETURN TABLE(SELECT …)`), a parenthesis, a literal, `res + 1` and `x.y` are syntax errors, in the account's
  lines, and a procedure whose body writes one is refused at CREATE instead of being created and failing when
  called.
- **A Snowscript UDF — a SQL function whose body is a block — may call built-ins only.** A call of a name nothing
  declares and a call written with OVER are `Unsupported expression for Snowscript UDF: <expression>` at CREATE,
  as a call of a user-defined function and a subquery already were; such a function used to be created.
- **A SQL UDF's expression body is held to the calls it makes at CREATE.** `AS 'ABS(1, 2)'`, a count or argument
  types no overload of a called function takes, and named arguments that fit no signature are refused where the
  call stands in the body; the function used to be created and to fail at every call.
- **A signature written with a length names only a parameter declared with it.** A bare `CHAR` is a text of
  length 1, so `DROP FUNCTION f(CHAR)` no longer finds `f(code CHAR(2))`, nor does `f(VARCHAR(3))`;
  `f(NUMBER(10,0))` does not find a `NUMBER(5,2)` parameter, `BINARY(5)` a `BINARY(4)` one, `TIMESTAMP_NTZ(6)` a
  `TIMESTAMP_NTZ(3)` one. A type written bare — `VARCHAR`, `NUMBER`, `INT` — still names any of its family. DROP,
  DESCRIBE, COMMENT ON, ALTER and GET_DDL read a signature the same way, for functions and procedures.
- **A table procedure read in FROM is held to its declared columns.** `TABLE(p())` refuses a returned text longer
  than its column (`String 'abcdef' is too long and would be truncated`) and a number it cannot hold, a procedure
  declared `RETURNS TABLE ()` (`Invalid stored procedure 'P' in FROM clause: Return table must declare a nonzero
  number of columns.`), and a procedure that returns no table (`Unknown table function P`).
- **A column DEFAULT over a sequence must name a sequence.** `CREATE TABLE t (a INT DEFAULT nosuchseq.NEXTVAL)`
  is `invalid identifier 'NOSUCHSEQ.NEXTVAL'` at CREATE, where the table used to be created and every INSERT
  into it failed. And a sequence dropped and created again under the same name is another sequence: a column
  that drew from the first is refused (`Sequence used as a default value in table 'DB.S.T' column 'A' was not
  found or could not be accessed.`), where the new one used to be picked up by its name.
- **A SQL function's body resolves its unqualified names where the function lives when it is called**, not where
  the caller is. A function in schema A whose body calls `f()` or reads `t` finds A's `f` and A's `t` — and a
  function PUBLIC holds — whatever schema the session is in; one that only worked because the caller's schema
  held them is now `Unknown function F.` or a missing object. At CREATE the body compiles where the session
  is, so `CREATE FUNCTION a.v() … AS 'f()'` run from a schema that does not reach `f` is refused, as is a body
  calling `IDENTIFIER('nosuchfunc')(…)`.
- **A procedure that runs with its CALLER's rights looks for unqualified names where the caller is.** It used to
  look where the procedure lives, as an owner's-rights procedure does.
- **`ALTER SEQUENCE … SET INCREMENT = 0`** is `invalid value '0' for property 'SEQUENCE_INCREMENT'`.
- **A type takes the parameter list its family takes, and no other.** The character types and FLOAT, FLOAT4,
  FLOAT8 and REAL take empty parentheses or one parameter; NUMBER, DECIMAL, NUMERIC and DEC one or two; BINARY,
  VARBINARY, TIME and the TIMESTAMP types exactly one; and INT and its spellings, DOUBLE, BOOLEAN, DATE, VARIANT,
  GEOGRAPHY, ARRAY and OBJECT none at all. `NUMBER()`, `TIMESTAMP_NTZ()`, `INT(5)`, `DATE(5)`, `BOOLEAN(1)`,
  `VARCHAR(5,2)`, `FLOAT(5,2)` and `TIME(3,1)` were all accepted, in a cast, a column, an argument and a variable
  alike; each is now a syntax error at the token the account names — after `::` the token that cannot continue the
  list, in a CAST or a column the opening parenthesis when the list cannot begin. `GEOMETRY(n)` is
  `Unsupported data type 'GEOMETRY with arguments'.`
- **TRIGGER and WHENEVER are words the account keeps for itself** wherever a name may stand — a column, a table,
  an alias with or without AS, a variable, an argument. Each was an ordinary name; quoted, it still is.
- **OVER after a select item's expression is no alias.** `SELECT 1 over` and `SELECT a over FROM t` are syntax
  errors; `SELECT 1 AS over` and `FROM t over` still name an alias.
- **A CREATE names its schema and its database exactly.** With a schema `SS` and no `"ss"`, `CREATE TABLE "ss".t`
  was created in `SS`; it is now `Schema 'DB."ss"' does not exist or not authorized.`, as it is for a view, a
  sequence, a function, a procedure, a stage, a file format, a stream, a task, a tag, a pipe and a policy, under
  OR REPLACE and IF NOT EXISTS too — and `CREATE TABLE "db".public.t` for a database `DB` is refused the same way.
- **`DROP TABLE … RESTRICT` keeps a table another table's foreign key references**: `Cannot drop the table
  because of dependencies`, with or without IF EXISTS. `DROP SCHEMA … RESTRICT` and `DROP DATABASE … RESTRICT`
  keep a container that holds such a table (`Cannot drop the schema …`, `… the database …`). Each used to drop.
- **A table's stage loads into that table alone.** `COPY INTO t2 FROM @%t1` — with a path, a pattern, a file list
  or through `(SELECT $1 FROM @%t1)` — is `Access to the stage area of a table (T1) with the schema of another
  table (T2) is not allowed.`; it used to load t1's files into t2. The stage of a table that does not exist is a
  stage that does not exist. Unloading into another table's stage and querying one are unchanged.
- **A temporary table is not cloned into a permanent one**: `CREATE TABLE c CLONE temp` is `Temp table cannot be
  cloned to a permanent table; clone to a transient table instead.`, where it made a second temporary table.
- **A regular expression is read as the account reads one**: POSIX extended syntax with `\d \s \w`, word
  boundaries and lazy repetition, and nothing else of Perl. A `(?…)` group of any kind — named, non-capturing,
  a lookaround, an inline flag — a backreference (`\1`), `\p{…}`, `\A`, `\z`, `\Z`, `\G`, `\h`, `\R` and
  `\Q…\E` were all taken, because `java.util.regex` takes them; RLIKE and every REGEXP function now refuse
  them as the account does, `Invalid regular expression: '<pattern>', <reason>: <fragment>`. A count past 1000
  (`a{1001}`), a count with nothing to repeat (`{2}`) and an unknown POSIX class (`[[:foo:]]`) are refused too.
- **The REGEXP functions' other arguments have ranges**: a position or an occurrence below 1 (`Invalid parameter
  value: 0. Reason: Position must be positive`), a negative capture group, a REGEXP_INSTR return option other
  than 0 or 1, a negative REGEXP_REPLACE occurrence, and a parameter letter outside `c i m e s` (`Unknown
  parameter: 'z'`) each answered something; a replacement that refers to a group its pattern does not have is
  refused when a match is rewritten with it.

### Fixed

- **A schema's clone keeps a quoted policy name in its case**: a masking, row access, projection or aggregation
  policy created as `"mp"` was held in a cloned schema or database under `MP`, so DESCRIBE, SET MASKING POLICY and
  DROP by `"mp"` refused it as missing there (a column unset from it could not be masked again) while `"MP"` reached
  it; the clone now holds each policy under its own name, as the source does.
- **A COPY load's column-count refusal quotes a delimiter of several characters as written**: `Field delimiter '||'
  found while expecting record delimiter '\n'`, where it quoted a stand-in character, and the record delimiter the
  format names; a load and a staged-file query now split a CSV file with one splitter.
- **A quoted lower-case warehouse, user or compute pool and its upper-cased twin are two objects**: `CREATE WAREHOUSE
  "wh"` then `CREATE WAREHOUSE WH` creates a second warehouse, where it was `Object 'WH' already exists.`, and
  `CREATE USER "us"` keeps the name `us` rather than upper-casing it, so `SHOW USERS` lists both `us` and `US`; a
  role, a warehouse or a user renamed to a quoted name keeps that name's case, and a snapshot restores both twins
  (documented).
- **A callable statement's OUT parameter reads as its column of the CALL's result**, through the same getters: a
  TIMESTAMP_NTZ return is a `Timestamp` whose `getString` is `2024-01-02 03:04:00.000` on both drivers, where the
  in-process driver handed out a `LocalDateTime` and the engine's own VARIANT class; a `:name` parameter binds over
  HTTP as it did in process, a named `setObject` keeps its type and scale, and the JDBC escape syntax `{call p()}`
  runs, as the account's driver runs it.
- **The HTTP driver's `getCatalog` and `getSchema` follow where the session stands**: a CREATE SCHEMA moves them, as
  a USE does, and a fresh connection reports where its session starts. The server reports the session's database,
  schema and user with every answer (`namespace` on the wire, see the HTTP API).
- **A position, an occurrence, an index, a count or a code point is the nearest whole number to what it gives**, a
  text's included, as SUBSTR's already was: REGEXP_INSTR, REGEXP_COUNT, REGEXP_REPLACE and REGEXP_SUBSTR_ALL,
  ARRAY_INSERT, ARRAY_REMOVE_AT and ARRAY_GENERATE_RANGE (its step too), XMLGET, CHAR and GETBIT round 1.6 to 2 and
  read '2', where they cut the fraction off and refused a text - `CHAR('66.6')` is `C` (live-verified).
- **CONDITIONAL_TRUE_EVENT and the boolean aggregates read their argument as TO_BOOLEAN does**: ' on ', 'yes' and a
  VARIANT holding "true" are TRUE and 'abc' is `Boolean value 'abc' is not recognized`, where the window counted only
  'true' and '1' and BOOLOR_AGG refused a VARIANT's text; `NOT 'nan'::FLOAT` is FALSE, NaN being true, where it failed
  with a Java message (live-verified).
- **SUM, AVG, SKEW, RATIO_TO_REPORT, DIV0 over two texts and a comparison with a FLOAT read a text as a cast to FLOAT
  reads it**: `SUM('1d')` and `1.5::FLOAT = '1.5d'` answer 1.0 and TRUE, `AVG('0x10')` 16.0, where each was refused;
  KURTOSIS reads a text as a decimal number, as STDDEV and VARIANCE do, refusing `'1d'` in the account's words where
  it failed with a Java message (live-verified).
- **A RECURSIVE view recurses through one recursive term, as the account does**: an operand after the recursive
  term takes no part, a second recursive term included, so `SELECT 1 UNION ALL SELECT n + 1 FROM v WHERE n < 3 UNION
  ALL SELECT n + 10 FROM v WHERE n < 3` reads 1, 2 and 3, where 11 and 12 were added too (live-verified); a recursive
  term that holds an INTERSECT, its own operands or a parenthesized one, recurses through the intersection, where
  the INTERSECT's other operand was dropped or the view was refused as an invalid Recursive CTE (the account creates
  such a view and fails reading it with an internal error of its own, which is not mirrored).
- **A grouped query's subquery aggregates its own derived table, common table expression or view**: in
  `SELECT g, (SELECT MAX(v) FROM (SELECT v FROM u)) FROM t GROUP BY g` the subquery's FROM carries v, so MAX(v) is
  the subquery's own aggregate over u, as it already was over a table, where it was computed over each group's rows
  of t (live-verified).
- **A correlated subquery compares an outer column under the column's collation**: `EXISTS (SELECT 1 FROM i WHERE
  i.s = o.s)` over an 'en-ci' o.s matches 'abc' to 'ABC', as the comparison does beside the subquery, in EXISTS, NOT
  EXISTS and scalar subqueries alike (live-verified).
- **A row-value IN, NOT IN and `= ANY` against a subquery compare each column under its collation**, the subject's
  or the query's column's, as `IN` of one value already did: `(id, s) IN (SELECT id, s FROM t)` over t's 'en-ci' s
  matches 'abc' to 'ABC' (live-verified).
- **A stage's format options are decoded as a named format's are**: `CREATE STAGE st FILE_FORMAT = (TYPE = CSV
  FIELD_DELIMITER = '\t')` keeps a tab, so `SELECT $1, $2 FROM @st/f.tsv` and `COPY INTO t FROM @st` split the fields
  on it, where the stage kept the two characters backslash and t and read a tab-separated file as one field
  (live-verified); a stage's NULL_IF is kept as a named format keeps it, a value holding a comma whole.
- **DESCRIBE FILE FORMAT, DESCRIBE STAGE and DESCRIBE TABLE … TYPE = STAGE spell a control character behind a
  backslash** - a tab delimiter lists as `\t`, a line feed as `\n` - and **SHOW FILE FORMATS' format_options blob
  spells every value as JSON does**: `"RECORD_DELIMITER":"\n"`, `"FIELD_DELIMITER":"\t"`,
  `"ESCAPE_UNENCLOSED_FIELD":"\\"`, where DESCRIBE printed the raw tab and the blob doubled the default line feed's
  backslash (live-verified).
- **A staged-file query reads TRIM_SPACE and SKIP_BLANK_LINES as a load reads them**: `SELECT $1, $2 FROM @st/f.csv
  (FILE_FORMAT => 'trimming')` answers `a|b` for ` a , b `, where it kept the spaces, and a blank line is a record of
  NULLs - `SELECT COUNT(*) FROM @st/blank.csv` counts it - unless the format's SKIP_BLANK_LINES passes it over, where
  every blank line was passed over (live-verified).
- **A statement that ran no Python routine no longer pays for putting the interpreter's shared state back**: once a
  routine had run on a thread, every later statement on it ran the restore (about 6 ms each in a harness that imports
  a venv), which the local CI's vendor gate paid some 50 seconds for; the restore now runs only after a statement
  that ran a routine.
- **`SYSTEM$PIPE_STATUS` reads its argument as an identifier**: `SYSTEM$PIPE_STATUS('"lowdb"."sch".PP')` answers the
  pipe's state, where the quoted parts were not honoured and the pipe was reported missing (documented).
- **A DESCRIBE or a SHOW in a session with no current database or schema is refused as a statement with none is**:
  `Cannot perform DESCRIBE. This session does not have a current database. Call 'USE DATABASE', or use a qualified
  name.`, where `DESCRIBE FUNCTION f(int)` answered `Database 'null' does not exist` and the pipe, stream, sequence
  and task listings `No database or schema selected`.
- **A star argument's refusal echoes the qualifier as live echoes it**: `OBJECT_CONSTRUCT("q".*)` is
  `Object '"q"' does not exist or not authorized.`, where it echoed `'Q'` (live-verified).
- **A MERGE changes only the target rows its row access policy lets the session see**, as an UPDATE and a DELETE
  already did: a hidden row is neither updated nor deleted and counts for nothing, and a source row matching a hidden
  row alone is NOT MATCHED, so its INSERT lands beside the hidden row, where the MERGE updated and deleted the hidden
  rows (live-verified).
- **A MERGE holds a hybrid table's keys as an UPDATE, a DELETE and an INSERT do**: a matched row's new image may
  not repeat a primary or UNIQUE key nor point a foreign key at no parent, a deleted or changed parent row a child
  still names is refused, and an inserted row names a parent with each foreign key - each before any row is
  written, and a duplicate key inserted inside a transaction is refused at the MERGE rather than at COMMIT
  (documented).
- **A user's grants are kept as a role's are**: a privilege granted to a user WITH GRANT OPTION keeps the option
  when its table is renamed, a table created under a dropped one's name starts with no grant to the user, a role
  revoked from a user is gone from SHOW GRANTS TO USER, and a user's privilege row dates from its grant, where the
  option was lost on a rename, outlived the drop, the revoked role stayed listed and the row dated from the user's
  creation.
- **A privilege is decided once**: the DML paths no longer ask twice with two different answers, a TRUNCATE asks
  TRUNCATE alone where it also asked DELETE (live-verified), a multi-table INSERT and a stream's source table ask in
  the account's own sentence (`Your primary role R must have INSERT granted on TABLE ...`), and the session's roles
  are gathered by one hierarchy walk.
- **A ROLLUP, CUBE or GROUPING SETS super-group row reads its rolled-up dimension as NULL in an item that also
  computes an aggregate**: `SUM(v) + LENGTH(r)` and `MAX(r) || r` are NULL there and `IFF(r IS NULL, SUM(v), 0)` is the
  total, the aggregates still reading every row, where the dimension was read from the group's first row
  (live-verified).
- **A subquery in a recursive CTE's anchor or step sees the WITH's other CTEs** - a scalar, IN or EXISTS subquery, a
  nested WITH, a derived table - where it was refused as an unknown object, or read a table of the CTE's name
  (live-verified).
- **A Python function's arguments are its handler's parameters alone**: a parameter named like a built-in - `type`,
  `len`, `str`, `id`, `max`, `list` - no longer replaces the built-in for the whole body, and a helper that reads a
  parameter's name finds no such global, where the call failed `'str' object is not callable` (live-verified).
- **A user aggregate's overload is chosen once for the call, by its arguments' static types**, so every group - an
  all-NULL one too - and a typed NULL run the same overload, where each group chose by its first non-NULL value
  (live-verified).
- **TO_NUMBER of a FLOAT converts its binary value**: `TO_NUMBER(1e38::FLOAT)` is
  `99999999999999997748809823456034029568` (live-verified).
- **A view read AT or BEFORE a point reads its subqueries' tables at the point too** - an IN, a NOT IN, an EXISTS, a
  scalar or correlated subquery, a LATERAL argument - as it reads its FROM, where they read the tables now and one row
  mixed two points in time (live-verified).
- **ARRAY_CAT over two structured arrays answers the type that holds both**, numbers to FLOAT beside an approximate one
  and otherwise to NUMBER(38, s) of the larger scale, both arrays' elements converted to it, where the first array's
  type was kept over the second's unconverted elements (live-verified).
- **What Python routines share on a thread is put back when each outermost statement ends**: a built-in name a routine
  added, an environment variable it set and a snowpark emulation method it replaced reach neither the next statement's
  routines nor another engine's, where a routine could make every later `session.sql` answer what it chose.
- **A grant passed on through a grant option is recorded in its grantor's name**, GRANTED_BY the role that holds the
  option, where every grant read as made by the object's owner; a REVOKE … CASCADE takes back the grants made through
  the option with the grant that gave it, however deep, and the grantor takes back the grants it made. A role that
  holds the privilege from another grantor keeps the grant it held (documented).
- **A stream on a join view keeps its pending changes across a clone, a checkpoint's restore and a restart**: the
  offset's place among the tables' snapshots is saved with the stream, where the copy read no change at all; and the
  view's own query is read where the view lives, so a session with no current schema reads the stream and a CHANGES
  clause over the view, where it was refused as `Object 'A' does not exist` (documented).
- **COPY INTO a table takes part in the session's transaction**: other sessions see the rows it loads once the
  transaction commits and a ROLLBACK takes them back, the load history and PURGE wait for the commit, a rolled-back
  load is loaded again by the next COPY, and a stream on the table reports the rows, where they were written straight
  to the table, kept by a ROLLBACK, marked as loaded and purged at once, and missed by its streams (documented).
- **A SQL function's exact parameter is typed by its argument's declared type**: `7::NUMBER(10,0)` into a NUMBER is a
  NUMBER(10,0) and a NUMBER(10,0) column into a NUMBER(10,2) a NUMBER(12,2), where the digits the value held typed
  it (NUMBER(1,0), NUMBER(10,2)); a binary literal argument keeps its width, `X'AB'` a BINARY(1), in a scalar and a
  table function alike (live-verified).
- **A SQL function's DATE, TIME or timestamp parameter keeps its value whatever the session's input formats say**,
  where `DATE_INPUT_FORMAT = 'DD/MM/YYYY'` refused it as `Date '2024-02-01' is not recognized`; and a block's
  SELECT … INTO hands a DATE, TIME or TIMESTAMP_NTZ variable its value as text read back under that format, refused
  at the SELECT, where the refusal came later from the first expression reading the variable (live-verified).
- **An INSERT … SELECT writes a table function's text by its declared width**: six characters a function declares
  VARCHAR(3) go into a VARCHAR(3) column, where the write was refused as too long (live-verified).
- **The refusal of a column default calling a Snowflake Scripting function echoes the plan's spelling**:
  `[TO_NUMBER(FBLK())]`, `[(TO_NUMBER(FBLK())) + 1]`, a conversion to FLOAT as `TO_DOUBLE(…)`, where it echoed
  `[FBLK()]` and `CAST(… AS FLOAT)` (live-verified).
- **RANK and DENSE_RANK beside a ROWS frame rank the row within the frame**: one plus the frame's rows that sort
  before it, DENSE_RANK counting their distinct keys, and the partition's rank where the frame does not hold the row -
  `RANK() OVER (ORDER BY v ROWS 1 PRECEDING)` is 1 or 2 on every row - where the frame was ignored (live-verified).
- **The casts of a VARIANT read their further arguments**: `AS_TIMESTAMP_NTZ(v, 0)`, `_LTZ` and `_TZ` take a scale
  that cuts the fraction and types the result, where a second argument was refused as too many; `AS_NUMBER(v, 3, 1)`
  and `AS_DECIMAL` are typed NUMBER(3,1), a precision of 0 holds any value, and a precision or a scale out of range
  is refused in the words of the call's own signature (live-verified).
- **COSH computes as the account does** - half the exponential plus half its reciprocal, expm1 near zero - so
  `COSH(1)` is 1.5430806348152437 where it answered the JDK's 1.543080634815244; and NORMAL, UNIFORM and ZIPF read
  their generator as RANDOM reads its seed, a whole number past 64 bits being `Numeric value '…' is not recognized`
  where it was drawn from (live-verified).
- **TO_QUERY leaves a further positional string argument unused**, `TO_QUERY('SELECT 1 AS x', 'extra')` answering 1,
  and names `SQL => NULL` a missing argument; EXPLAIN of a query over TO_QUERY of a literal text plans that text's
  query in its place (live-verified).
- **An UPDATE's SET and a MERGE's branches write a constant text to a VARIANT, ARRAY or OBJECT column by reading it as
  JSON** when the row is written: `UPDATE tv SET v = '1'` stores the number 1 (`INTEGER`), `'{"k":1}'` an OBJECT, `''`
  SQL NULL and `'null'` the JSON null, an ARRAY column holding what the document is; a text that is no JSON fails the
  write, `DML operation to table TV failed on column V with error: Error parsing JSON: unknown keyword "abc", pos 4`.
  A literal, a call over literals, a cast, a block's bind and a session variable are constant; a text that reads a
  column stays refused. Each was refused while compiling, `Expression type does not match column data type, expecting
  VARIANT but got VARCHAR(1) for column V` (live-verified).
- **A derived table, a CTE or a view whose QUALIFY reads none of its window columns** answers whatever the query
  around it reads, where `SELECT COUNT(*) FROM (SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS rn FROM people QUALIFY
  1 = 1)` was refused as `found QUALIFY clause but no window function.` (live-verified).
- **A quoted name a block does not declare is echoed with its quotes only where it needs them**: `BEGIN RETURN "ABC"
  IS NOT NULL; END;` is refused as `invalid identifier 'ABC'`, where it read `'"ABC"'`; `"abc"` keeps them
  (live-verified).
- **A HAVING aggregate over a scalar subquery that reads the grouped relation** - `HAVING MAX((SELECT g.v FROM g WHERE
  g.id = fz.id)) > 0`, and MIN, SUM, COUNT and ANY_VALUE, DISTINCT or not, under comparisons, arithmetic, AND, OR or a
  COALESCE - is answered group by group, where it was refused as `Unsupported subquery type cannot be evaluated`; a
  subquery with an aggregate of its own is refused as the account plans it, `Aggregate functions cannot be nested:
  [MAX(G.V)] nested in [MAX(MAX(G.V))]`, where it was refused as unsupported or answered, unless an OR the aggregate
  stands under is settled by a constant TRUE, `… OR TRUE`, which leaves the subquery unplanned (live-verified).
- **SHOW GRANTS and SHOW FUTURE GRANTS name what they list by an IDENTIFIER() reference** as by a name - `SHOW
  GRANTS ON TABLE IDENTIFIER($tn)`, `ON IDENTIFIER($tn)`, `ON SCHEMA IDENTIFIER('s')`, `TO ROLE IDENTIFIER($r)`, `OF
  ROLE …`, `ON ROLE …`, `TO USER …`, `SHOW FUTURE GRANTS IN SCHEMA | DATABASE IDENTIFIER(…)`, the database-role forms -
  where each was a syntax error at the parenthesis; a reference to nothing is refused as the name is, and
  `IDENTIFIER(NULL)` at the NULL (live-verified).
- **SYSTEM$TYPEOF over an INFORMATION_SCHEMA listing's columns** - QUERY_HISTORY and its variants, TASK_HISTORY,
  COMPLETE_TASK_GRAPHS, CURRENT_TASK_GRAPHS and TASK_DEPENDENTS - names the type each column declares,
  `VARCHAR[LOB]`, `NUMBER(38,0)[SB16]`, `OBJECT[LOB]`, where it answered `NULL[LOB]` (live-verified).
- **A query that reads no row of its FROM never calls its table functions**: a constant WHERE conjunct that is FALSE
  or NULL, a LIMIT 0 or an inner join on such a constant - `SELECT * FROM TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(
  TASK_NAME => 'nosuch')) WHERE 1 = 0`, `… FLATTEN(INPUT => PARSE_JSON('[')) WHERE 1 = 0` - answers no row, where the
  call ran and its refusal was raised (live-verified).
- **ROLLUP and CUBE over a parenthesized list**, `GROUP BY ROLLUP ((a, b))`, take the list as one composite element -
  the groups of `(a, b)` and the grand total - where the query was refused as not grouping by its columns
  (live-verified).
- **A ROW given to a function is refused in the words of the function the call is planned as**: STDDEV, VARIANCE and
  the other deviations, variances and moments as `COUNT`, COUNT_IF as `IFF` over 1 and 0, APPROX_COUNT_DISTINCT as
  `HLL_ACCUMULATE`, BOOLAND_AGG, BOOLOR_AGG and BOOLXOR_AGG as `invalid type [TO_BOOLEAN(ROW(T.A, T.B))] for
  parameter 'TO_BOOLEAN'`, AS_INTEGER, AS_NUMBER and AS_DECIMAL as `invalid type [ROW(…)] for parameter
  'AS_INTEGER(variantValue...)'`, and FIRST_VALUE or LAST_VALUE as a select item of several columns at its alias or
  its OVER. SYSTEM$TYPEOF of a ROW names its values' types, `ROW(NUMBER(1,0), NUMBER(1,0))[UNKNOWN]`, where it
  answered `NULL[LOB]`, and GROUPING reads a ROW as no key, 1, where it was refused (live-verified).
- **SEARCH over a tuple of a star** - `SEARCH((*), 'q')`, `SEARCH((t.*), 'q')`, with EXCLUDE or ILIKE inside - searches
  the columns the star stands for, in a select list and in a WHERE alike, where it was a syntax error (live-verified).
- **A SQL function whose query body holds a QUALIFY with no window function** is created, and each call is refused in
  the body's own positions as the account frames it, where the CREATE was refused; and `SELECT 1e` is told as a number
  that stops and as an end nothing may stand at, as `SELECT 0x` was (live-verified).
- **An ORDER BY, LIMIT or FETCH after a parenthesized query** - `(SELECT * FROM t) ORDER BY 1`, `(TABLE t) ORDER BY b
  DESC FETCH FIRST 1 ROW ONLY` - applies to the query's rows, by its output columns' names, positions and expressions
  over them, where the clauses were dropped (live-verified).
- **A correlated subquery whose filter compares two rows** (live-verified): `EXISTS (SELECT 1 FROM u WHERE (u.a, u.b)
  = (t.a, t.b))` - under NOT EXISTS, a scalar COUNT or an IN too, written either way round, mixing the two sides pair
  by pair, beside a constant, for every comparison operator - is answered as the comparisons of its pairs would be,
  where it was refused as `Unsupported subquery type cannot be evaluated`; a pair whose operand mixes the inner and
  the outer row stays refused.
- **EXPLAIN of a PIVOT whose values are read as the query runs** - IN (ANY), IN (a subquery) - answers a plan, where it
  was refused as `SELECT with no columns`: the values are read while the statement compiles, as the account's planner
  reads them. A pivot is planned as a Pivot over the scan where its source keeps a column beside the aggregated and the
  FOR one, otherwise as an Aggregate over the scan for written values and over a Pivot for read ones (live-verified).
  A refusal of an EXPLAIN's query is placed in the EXPLAIN statement, after its first word, where it was placed in the
  query alone; and a query refused for its FOR UPDATE is compiled without a hidden `EXPLAIN …` statement of its own,
  which QUERY_HISTORY listed beside it.
- **GRANT and REVOKE ON MATERIALIZED VIEW** are statements, where they were syntax errors: ALL stands for the eleven
  privileges a table takes, SHOW GRANTS ON MATERIALIZED VIEW lists them, and REVOKE ALL counts them, `11 objects
  affected.` (live-verified).
- **A name a column of the relation and a select alias both carry reads the column in GROUP BY, HAVING and QUALIFY**
  (live-verified), as WHERE always did: `SELECT id, COUNT(*) AS id … GROUP BY id HAVING id > 5` groups and filters by
  the column where it was refused as `ambiguous column name 'ID'`, an ordinal GROUP BY key naming the column reads it
  in HAVING, and a select alias - a window's included - no longer hides a column of its name in QUALIFY. `ambiguous
  column name` stays for two aliases of a name no relation carries, and for ORDER BY. A select alias written over a
  qualified column, `UPPER(q.c) AS u`, beside a lateral table function no longer reads as `invalid identifier
  'UPPERQ.C'` where ORDER BY 1 names it.
- **A RESUME makes a materialized view valid again when its base table only changed a column's type or nullability**
  (live-verified), as it does after a SUSPEND; one whose table lost or renamed a column stays invalid, refused by the
  reason alone. A SET NOT NULL a column already has, or a DROP NOT NULL it does not, answers `Not null constraint
  already exists for column 'C'` / `Not null constraint does not exist for column 'B'` (the statement's last such
  item), where it answered `Statement executed successfully.`.
- **QUERY_HISTORY's cells are the account's** (live-verified): QUERY_TYPE names a statement as the account does -
  CREATE_TABLE, CREATE_TABLE_AS_SELECT, ALTER_TABLE_ADD_COLUMN, RENAME_COLUMN, ALTER_SESSION, SET, TRUNCATE_TABLE,
  LIST_FILES, CALL for a block, the kind of the statement an EXECUTE IMMEDIATE's text holds, UNKNOWN for one that does
  not parse - where the engine's own coarse kinds (OTHER, CREATE, ALTER) showed; ERROR_CODE carries a failure's code;
  TRANSACTION_ID the explicit transaction, or the one a write ran as, else 0; CLUSTER_NUMBER and WAREHOUSE_SIZE are
  set where a warehouse ran the statement (a write, a CALL, a query reading a relation or a sequence, a failure at run
  time) and NULL elsewhere; ROWS_PRODUCED is 0 where none ran it and, for a write, the rows of the table it wrote;
  ROWS_WRITTEN_TO_RESULT and ROWS_INSERTED are filled; and the cells the account answers alike for every statement -
  IS_CLIENT_GENERATED_STATEMENT, the two hash versions, the transfer, external-function, acceleration and retry
  counts, RELEASE_VERSION, QUERY_HASH, AUTHN_EVENT_ID - are no longer NULL. A handler reads SQLCODE / SQLSTATE 2020 /
  21S01 for an INSERT whose values do not match its columns, and 711 / 02000 for a result asked of a failed statement.
- **A stage path's segment may be a reserved word** (live-verified): `LIST @st/select/from/where`, `SELECT $1 FROM
  @st/dir/inner` and `REMOVE @st/inner/join` read those paths, where the keyword was a syntax error; the same word
  after a space is still the keyword.
- **DECFLOAT is a decimal of 38 significant digits whose point floats**, where it was carried as a DOUBLE and typed
  FLOAT (live-verified): `SYSTEM$TYPEOF` answers `DECFLOAT(38)[DECFLOAT18]` for a cast, `TO_DECFLOAT`, a column,
  arithmetic beside a NUMBER, a FLOAT or a numeric text, IFF, COALESCE, CASE, a UNION arm, ABS, ROUND, FLOOR, CEIL,
  TRUNC, MOD, SIGN, GREATEST and LEAST, and SUM, AVG, MIN and MAX; DESCRIBE spells it `DECFLOAT(38)`, SHOW COLUMNS
  `{"type":"DECFLOAT","precision":38,…}` and INFORMATION_SCHEMA `DECFLOAT` with no precision or scale. A FLOAT converts
  at its exact binary value, so `1e38::DECFLOAT` is 99999999999999997748809823456034029568; `1::DECFLOAT / 3` keeps 38
  digits and `0.1::DECFLOAT + 0.2::DECFLOAT = 0.3` holds; a column keeps every digit of a 38-digit integer; and a cast
  to VARCHAR is plain up to 38 digits (`0.0000000001`) and scientific past them
  (`3.3333333333333333333333333333333333333e-1`). The value stays exact wherever it is held: a UNION, INTERSECT or
  EXCEPT branch beside a NUMBER, a VALUES row (the column folds to DECFLOAT), a recursive CTE's DECFLOAT column, a
  scripting variable, LET or DEFAULT declared DECFLOAT (a decimal literal by its digits), a procedure's DECFLOAT
  argument, SELECT … INTO, FETCH and a USING bind; arithmetic in a block's own expression keeps 38 digits, and a
  concatenation reads the DECFLOAT's text. A recursive CTE whose NUMBER anchor meets a DECFLOAT term is refused as a
  type mismatch.
- **DECFLOAT's own functions, literal, range, VARIANT members and driver reads** (live-verified):
  SQRT, CBRT, LN, LOG, EXP, POWER and SQUARE over a DECFLOAT are DECFLOATs of 38 correctly rounded digits — LOG
  divides two logarithms each held to 38 digits, so `LOG(2::DECFLOAT, 8)` is 2.999…9 — refusing an argument with no
  value as `Decfloat invalid operation: SQRT(-1)` (`LOG(e, 0)` for LN, `POW(0, -1)`), and so are the variance and
  deviation family, PERCENTILE_CONT, the windowed SUM and AVG, and SUM and AVG over a table column of any magnitude.
  The typed literal `DECFLOAT '123e7000'` reads its text, a text that spells no number refused while the statement
  compiles (`Can not convert parameter ''abc'' of type [VARCHAR] into expected type [DECFLOAT]`). A DECFLOAT's last
  digit lies between exponents -16383 and 16384: finer values round to 0, `DECFLOAT '1e16385'` is 10e16384, and past
  38 digits there the value is refused. A DECFLOAT read as text — TO_VARCHAR, TO_CHAR, `||`, CONCAT, LIKE, LISTAGG,
  a text function's subject, a hash — is its own text. Inside a VARIANT, an ARRAY or an OBJECT a DECFLOAT is the JSON
  string of its text whose TYPEOF is DECFLOAT; it casts back to a DECFLOAT and to text, a NUMBER, a FLOAT, a BOOLEAN
  and arithmetic refuse it (`Failed to cast variant value "1.5" to FIXED`), IS_ answers FALSE and AS_ NULL, it never
  equals a NUMBER or a text of the same digits, and a VARIANT column written from a DECFLOAT keeps it. A FLOAT written
  into a DECFLOAT column keeps 18 significant digits (`0.100000000000000006`), where a cast keeps the binary value.
  A DECFLOAT parameter takes a DECFLOAT argument only, DECFLOAT is a return-type family of its own spelled
  `DECFLOAT(38)`, and a Java, Python or Scala routine refuses it in its signature. Over JDBC a DECFLOAT column is
  type code DECIMAL read as the BigDecimal of its text, `getString` its text and `getLong` refused for a fraction,
  and the HTTP wire carries it as a JSON string of its text.
- **A call of a name that holds a scalar overload beside an aggregate one** aggregates only where its arguments'
  types choose the aggregate (live-verified): over `mix(x INT)` and an aggregate `mix(x VARCHAR)`, `mix(1)` and
  `mix(v)` over an INT column run the scalar one for each row, grouped or not, and `mix(MAX(v))` is the scalar one
  over an aggregate, where every call of the name was taken for the aggregate and the query collapsed to one row.
- **A recursive CTE holds every value, the anchor's included, to its column's type** (live-verified): over a
  NUMBER(38,2) column `n * 1.5` from 1.5 is 1.50, 2.25, 3.38, 5.07, each step reading the value so held, an anchor's
  1 in a NUMBER(38,1) column is 1.0, and a CTAS of the query stores the rows a SELECT of it answers. Values kept the
  scale of the term's own arithmetic (3.375, 5.063), so the SELECT and the CTAS disagreed.
- **`x := (CALL p())` holds the procedure's answer unconverted** (live-verified): a bare `RETURN x` answers it as
  the CALL did and `SYSTEM$TYPEOF(x)` the declared type, an expression that reads `x` converts it there and fails as
  an EXPRESSION_ERROR at that expression, a statement's bind refuses it `Invalid bind value (abc) for type (DATE).`,
  and a cursor's or an EXECUTE IMMEDIATE's USING binds it as it is. The assignment converted at once and failed there.
- **A SQL function's query body is typed with its parameters bound as a call binds them** (live-verified): a parameter
  wins over a same-named column of the body's FROM, so `SUM(value)` over a FLATTEN reads the parameter and a call with
  the argument 10 is NUMBER(14,0), where it was typed FLOAT from FLATTEN's VALUE column.
- **CURRENT_AVAILABLE_ROLES() in the engine's own session** lists the roles of the user CURRENT_USER() names, the
  roles USE ROLE may activate there, where it listed the session's active roles alone; inside a stored procedure that
  runs with owner's rights it answers as its caller does (live-verified).
- **MODE, MEDIAN, LISTAGG and the other generic aggregates read a column where it stands after ALTER TABLE**
  (live-verified): after a DROP COLUMN they read the dropped column's old position, another column's values or past
  the row's end (an IndexOutOfBoundsException), when the same thread had aggregated the table before.
- **FLOAT SUM and AVG keep an infinity** (live-verified): two 1e308 values, or `'inf'` beside 1, sum to Infinity,
  and `'-inf'` beside 1 to -Infinity, where the compensated sum answered NaN.
- **A bare name inside a grouped query's scalar subquery binds in the subquery's own FROM first** (live-verified):
  `(SELECT MAX(v) FROM u)` beside `GROUP BY g` over a table that also has a `v` is u's maximum on every group, where it
  was each group's own, in the select list and in HAVING alike.
- **Sessions running window queries at once each get their own answer.** The window computation's state was shared
  by every session of an engine, so a concurrent RANK or LAG could be computed from another session's state.
- **QUALIFY reads a select alias of a volatile item as the row shows it** (live-verified): a sequence's NEXTVAL,
  `UNIFORM(…, RANDOM())` or `UUID_STRING()` is computed once, so every returned row satisfies the predicate on its own
  values, where the projection computed the item again.
- **A recursive CTE's anchor of several operands** is their set operation (live-verified): `SELECT 5 EXCEPT SELECT 6
  UNION ALL …` starts from 5, where it started from 6, and a parenthesized anchor or recursive term is the query it
  holds, where the term was dropped or the anchor run as the term.
- **A ROLLUP, CUBE or GROUPING SETS super-group row reads a dimension it aggregates over as NULL inside any
  expression** (live-verified): `COALESCE(r, 'ALL')` labels the grand total ALL, `r IS NULL` is TRUE there, and
  `CONCAT(r, '-', p)`, `UPPER(r)`, `s.r` and `R` read NULL, where they read the group's first row. An item that also
  computes an aggregate still reads the rows as they are.
- **GROUPING and GROUPING_ID answer a super-group row's grouping set wherever they stand** (live-verified): inside
  arithmetic, IFF and CASE, where they answered 0, and with several arguments as a bitmask. An argument spelled
  otherwise than its key matches it: `GROUPING(s.r)` under `ROLLUP (r)` and `GROUPING(r)` under `ROLLUP (s.r)`.
- **TOP n limits a select without a FROM** (live-verified): `SELECT TOP 0 1` answers no row.
- **An ORDER BY ordinal counts the columns of a star with EXCLUDE, REPLACE, RENAME or ILIKE** (live-verified), and
  an ordinal past them is refused `[3] is not a valid order by expression`. Before, the ordinal sorted by the
  constant, or counted the star as one item.
- **A positional arm after UNION BY NAME is held to the merged layout** (live-verified): `SELECT 1 a UNION ALL BY NAME
  SELECT 2 b UNION ALL SELECT 3, 4` answers `3 | 4` as its third row, where it was refused for not matching the first
  arm's width, and an INTERSECT arm is held to the width of its own run's first arm.
- **UNION BY NAME matches a quoted column name case-sensitively** (live-verified): `"a"` and `A` are two columns, where
  one took the other's values.
- **UNION BY NAME sorts and limits in the folded column types** (live-verified): a text arm beside a NUMBER arm was
  converted only after ORDER BY and LIMIT ran, so the NUMBER column sorted as text and LIMIT kept the wrong rows.
- **SELECT DISTINCT with TOP, LIMIT or FETCH limits the distinct rows** (live-verified): the limit cut the rows
  before duplicates were removed, so `SELECT DISTINCT a FROM t ORDER BY a LIMIT 2` could answer one row.
- **An aggregate over a bare USING or NATURAL join key reads the merged key** (live-verified). Over a FULL or a RIGHT
  join, `COUNT(k)`, `SUM(k)`, `MIN(k)`, `MAX(k)`, `AVG(k)`, `COUNT(DISTINCT k)`, `LISTAGG(k)` and `ARRAY_AGG(k)` left
  out the rows only the right side holds.
- **INTERSECT, EXCEPT and MINUS answer in the set operation's folded column types** (live-verified). A text arm beside
  a NUMBER arm is converted (`'01' EXCEPT 99` answers 1.00000, sorted as a number), a TIMESTAMP_NTZ meets an equal
  TIMESTAMP_LTZ or TIMESTAMP_TZ, and an exact NUMBER beside a FLOAT compares as a FLOAT, where a NaN or an infinity
  was refused `Numeric value 'NaN' is not recognized`. An empty left side still answers no rows without converting
  the right.
- **A WITH nested in a subquery, a derived table or a parenthesized set-operation arm sees the enclosing statement's
  CTEs** (live-verified), and an enclosing CTE wins over a nested one of its name, as it already did for a WITH
  nested in another CTE's body. Before, the nested WITH read a table of the same name or was refused `Object 'X' does
  not exist or not authorized`.
- **SELECT * over a USING or NATURAL join of a table with itself** prints each alias's own columns (live-verified):
  `SELECT * FROM p y JOIN p x USING (k)` printed y's V twice, and an `INSERT … SELECT *` stored it so.
- **A correlated EXISTS or scalar aggregate over several equalities compares a collated key under its collation**,
  wherever that equality stands (live-verified). A case-insensitive second key matched nothing, so EXISTS answered
  FALSE, and a scalar aggregate read another row's value.
- **A USING or NATURAL join after an earlier USING join of the same key** keys on the merged column the earlier join
  prints (live-verified): `x1 LEFT JOIN x2 USING (a) JOIN x3 USING (a)` lost every row whose x2 side was
  null-extended, and a FULL chain split one key over two rows.
- **A view's, a materialized view's, a policy's and a SQL function's body read the objects they name**, never a CTE
  of the statement that reads them (live-verified). Before, `WITH acl AS (…) SELECT * FROM sv` handed a secure view's
  filter the query's own rows, a CTE named like a row access or masking policy's mapping table lifted the policy, a
  CTE named like a nested view replaced it, and a SQL function or table function read the caller's CTE in place of
  its table (`WITH t AS (SELECT 42 a) SELECT f3()` answered NULL). A dynamic table's query and a join, aggregation or
  projection policy's body read their own objects likewise.
- **A CTE is in scope for its own statement alone.** Two sessions of one engine running statements at once no longer
  read each other's CTEs: the scope was one field every session shared.
- **Python function, aggregate and table-function arguments take their parameters' types**, as a procedure's do
  (live-verified): an INT parameter rounds 1.5 to the `int` 2, a BOOLEAN one reads `'off'` as `False`, a VARCHAR one
  reads 12345 as `'12345'` and a NUMBER(10,2) one holds 1.255 as `Decimal('1.26')`. Before, a handler got each
  argument as evaluated: a `str` for a BOOLEAN, an opaque host decimal for a NUMBER, an `int` for a VARCHAR.
- **A Python function's, aggregate's or table function's answer takes its declared type**, converted as Python
  converts (live-verified): a `Decimal` is the NUMBER it spells, rounded half to even, where `Decimal('2.50')` was
  stored as that text; `bytes` are a BINARY where they became the array `[255,0]`; a TIMESTAMP_TZ keeps a
  `datetime`'s offset; a VARCHAR is `str()` of the answer, an INT `int()` of it and a FLOAT `float()` of it; and
  inside a VARIANT a `Decimal`, `bytes` and a `datetime` are their `str()`. A procedure's TIMESTAMP_TZ answer keeps
  its offset too, where it was read as an instant.
- **A Python table function given a TIMESTAMP_TZ or a VARIANT holding null** runs: each argument reaches `process`
  as the Python value its type maps to, an aware `datetime` or a dict holding `None`, where the call failed with a
  `SyntaxError` or a `NameError`. A NUMBER(10,2) argument is a `Decimal`, where it was a `float`.
- **Each Python routine runs in a module namespace of its own.** A function a procedure's query calls no longer
  replaces the procedure's own module-level functions, a name one routine's body defines is not one another routine
  sees, and nothing a call defines outlives it, so a routine run by another engine on the same thread does not read
  it either.
- **A structured element added to an ARRAY of a structured element type** is converted as a cast converts it:
  `[2.6]::ARRAY(DOUBLE)` into an `ARRAY(ARRAY(INT))` is `[3]`, where the double was kept under the integer type.
- **OBJECT_PICK with an aggregated key array** (`ARRAY_AGG`, `ARRAY_AGG … WITHIN GROUP`, `ARRAY_UNIQUE_AGG`) picks
  the keys, `{"b":2,"c":3}`, where it answered `{}`.
- **A user aggregate function's overloads** are chosen by the arguments' types, as a scalar function's are:
  `pa(1)` runs `pa(INT)` and `pa('a')` runs `pa(VARCHAR)` (live-verified), where the first overload taking as many
  arguments ran.
- **A COPY reads its stage's named file format where the stage lives.** An unqualified name resolves in the stage's
  schema, so a session in another schema loads with it too.
- **A failed statement with AUTOCOMMIT off** undoes only itself and leaves the implicit transaction open, as an
  explicit transaction's statement does (live-verified). This covers a query of a missing table, an INSERT its column
  refuses, and a DDL refused as it compiles (CREATE VIEW over a bad query, DROP or ALTER of a missing table). The rows
  written before it commit, or roll back, with the transaction. Before, the whole implicit transaction was rolled back
  and the session went on with none. The same holds for a JDBC connection with `setAutoCommit(false)`.
- **A COPY load of an enclosed CSV field that holds a line break** loads it whole (`two\nlines`) where it cut the
  field at the break and failed the next line. A record that spans lines counts them toward the line a later error
  names.
- **A COPY load with a RECORD_DELIMITER other than a line break** reads records as a staged-file query does: an
  escaped quote inside an enclosure (`"a\";b"`) and a quote inside an unenclosed field (`x"y`) end nothing. A field
  is enclosed only when it starts with the enclosure, at any record delimiter, so `x"y` keeps its quote.
- **MATCH_BY_COLUMN_NAME over JSON** matches as its mode says. CASE_INSENSITIVE takes a key in any case, the exact
  spelling first, and CASE_SENSITIVE takes only the key spelled as the column is. Both used to try the column's own,
  lower-case and upper-case spellings.
- **A DML that reads a stream and fails inside an explicit transaction** leaves the stream as it was. Its read of
  the stream is rolled back with its changes, so the COMMIT of the transaction's other work no longer advances the
  stream past changes no statement consumed (live-verified: the stream still holds its 3 rows).
- **A database or schema clone made in a session that holds a temporary table** copies the permanent table the
  temporary one hides. Before, the temporary table's rows reached the clone, which every session reads, and a
  temporary table of another shape refused the clone with `Row column count mismatch`.
- **A logged write that read a stage's files, or a handler function's answer**, survives a restart as it was
  written. This covers a COPY into a table, a query of a stage, an EXECUTE IMMEDIATE FROM, and a JavaScript, Python,
  Java or Scala function, table function or procedure not declared IMMUTABLE. Such a write now owes a checkpoint,
  so the restart no longer reads files a purge or an overwrite changed since, or calls the handler again for another
  answer.
- **GRANT OWNERSHIP over an object a database role or a user holds a grant on.** Those grants are outbound grants
  like an account role's. With no tail they block the move (`… to database role 'P.DR' exists.`, `… to user 'U2'
  exists.`), REVOKE CURRENT GRANTS drops them, and COPY CURRENT GRANTS keeps them as granted by the new owner. Before,
  the move went through and left them standing under the old grantor.
- **A write reading a table AT or BEFORE a STATEMENT the restarted engine does not know** survives a restart. A
  logged CTAS, INSERT … SELECT or CLONE keeps where the point fell in its log record: its instant, its place among the
  snapshots, and the last logged statement visible by then, which the replay maps the place through. Its replay
  therefore needs no history of that statement, whether it is a SELECT's ID or the ID of a statement from before a
  checkpoint (an EXECUTE TASK takes one), and it tells apart writes inside one millisecond. Before, the replay failed
  with `Statement … not found` and the table or rows were lost.
- **A CALL, block or EXECUTE IMMEDIATE that fails after writing** keeps that work across a restart: the rows its
  statements committed, the tables they created, and the sequence values they drew. The failed statement is never
  logged, so it now owes a checkpoint, as a task run does. Before, the work was committed but lost on restart, and a
  sequence could hand out a value twice.
- **A stream on a view, and a CHANGES clause over one, under policies.** They hand out what the view hands out:
  the rows the row access policy of each table under the view keeps, each masked column masked before the view's
  filter and projection read it, then the rows the view's own row access policy keeps. Before, every change came
  through unfiltered and unmasked, and a CTAS from the stream copied the withheld rows. This covers a join view, an
  append-only stream and a stream showing its initial rows.
- **INVOKER_ROLE()** names the role that owns the executing object: a view's owner inside the view, a function's
  owner inside the function, an owner's-rights procedure's owner inside the procedure, and the current role outside
  them. A policy on a table read through a view therefore sees the view's owner. It answered the current role
  everywhere.
- **A REVOKE takes back only the grants its role made**, unless the role holds MANAGE GRANTS; in a managed access
  schema the schema's owner takes back any. Any role took back any grant. A REVOKE that takes back nothing still
  succeeds (`0 objects affected`), as Snowflake's REVOKE page describes.
- **CALL SYSTEM$SEND_EMAIL** is the account's procedure: a missing integration is refused in its body's frame,
  `Execution error in store procedure SYSTEM$SEND_EMAIL: … Integration 'NOSUCH_INT' does not exist or not
  authorized.`, a NULL argument as an unsupported binding, and another count of arguments for the signature, where every
  CALL was `Unknown function SYSTEM$SEND_EMAIL.`
- **A common table expression, or a stream, as a query's first relation for a role that is no administrator.** A CTE
  asks for no privilege, a recursive one included, and a stream is read with SELECT on it and SELECT on its table,
  where both were refused with a raw `SecurityException`.
- **A block's call over its variables.** A refusal names a bound NUMBER variable by the CAST the block wrote,
  `ABS(ALL CAST('5' AS NUMBER(38,0)))`, where it printed `TO_NUMBER('5')`; and a variable read by its bare name, or an
  argument that reads no name, is the constant LAST_QUERY_ID, UNIFORM and RANDOM ask for, where both were refused as
  no constant.
- **A query in a session whose transaction was aborted** is refused for what it fails to compile for — an object or
  a column that is not there, an unknown function, a miscounted call, a duplicate alias — before the aborted
  transaction is named, where every query met `Your transaction was aborted. …`.
- **SYSTEM$ABORT_TRANSACTION's refusal of an argument that is no constant** prints a query FROM DUAL over
  `(VALUES (null)) "VALUES"` and DATE_PART and EXTRACT as `EXTRACT(year from …)`, as the plan holds them.
- **A block's variable bound into the query of an assignment, a LET or a RETURN** is echoed in an argument-count
  refusal as the plan converts it — `[ABS(TO_NUMBER(:s), 1)]`, `TO_NUMBER(:s, 10, 2)`, `CAST(:s AS FLOAT)`,
  `CAST(:s AS DATE)` — and a variable named like a unit in a unit's place as `TO_NUMBER('5')`, `TO_DOUBLE('5.5')` or
  `TO_BOOLEAN('true')`, where the echo showed the value written in.
- **A bare word in LAST_DAY's second place that names no part**, in a block's own expression, is `invalid identifier`,
  as a unit's word in the first place of DATEADD is.
- **The remote JDBC driver counts a block's word used as a name as a name**: `SELECT 1 AS begin, 2 AS b; DELETE FROM
  t` is refused before it is sent, `Actual statement count 2 …` with state 0A000, where the server refused it in its
  own state.
- **A task whose body is a block holding a word that names no type** — `AS BEGIN CREATE TABLE t9 (x NOSUCHTYPE);
  END`, a LET's type, a CAST's — is created and GET_DDL tells the body as written, where it was refused at the END.
- **A DESCRIBE property's list of property lists.** `x = ((a = 1 b = 2), (c = (1, 2)))` is read and refused as
  `invalid parameter 'x'`, and `TYPE = ((a = 1))` as `invalid value [TOK_LIST] for parameter 'TYPE'`, where both were
  syntax errors; a faulty one names what the account names (`x = ((1))` the 1, then the second ')'), and a property's
  name due at a block statement's ';' reads the next statement's first word as that name.
- **A NULL in a USING list** is bound as a text with no value — `SYSTEM$TYPEOF(?)` is `VARCHAR[LOB]` — and a bound
  value standing as a whole predicate is named by its placeholder, `Invalid data type [VARCHAR(4)] for predicate [?]`
  (or `[:1]`), where the refusal echoed the value.
- **The token after a LIMIT read both ways.** `LIMIT (SELECT 1)` names the SELECT and then the '(', and after the two
  readings the clause reads on: `LIMIT -1 OFFSET -1` names the second '-' and `LIMIT -1; SELECT 1 x y` the y.
- **A window key of an aggregate query's dropped ORDER BY** naming a column that does not exist is `invalid
  identifier 'NOSUCH'` where the name stands — `ORDER BY SUM(id) OVER (PARTITION BY nosuch)` at the NOSUCH — where it
  was `[FZ.ID] is not a valid group by expression`.
- **A literal after a DESCRIBE signature's own TIMESTAMP.** `DESCRIBE TABLE t1 (TIMESTAMP 1)` names the 1 twice and
  then the `)`, and `(TIMESTAMP 1, INT)` the 1 twice and the `)` after INT; a DROP's TABLE item of five parts,
  `(TABLE(a.b.c.d.e))`, is refused at its fourth part alone.
- **OVERLAY's PLACING form under brackets.** `SELECT CAST(OVERLAY(b PLACING 'x' FROM 2) AS INT) FROM t` names the
  PLACING, the `'x'` and the 2; under a call the 2 alone follows the PLACING, under two calls the inner call's `)`,
  under a call in a cast the cast's `)`, and in parentheses the `'x'`.
- **A table procedure's arguments in a FROM clause compile with the statement.** `TABLE(qa(nosuchcol))` is `invalid
  identifier 'NOSUCHCOL'` where the name stands and `TABLE(qa(ABS(1, 2)))` the call's own count, where the procedure
  ran and failed inside or the types were refused; a NOT, an AND or an OR is an argument as a value is, where it was
  dropped; too many arguments beside a parameter with a default are the CALL's refusal; and a column of a relation
  written before the call is `Invalid argument T1.C for procedure in FROM clause` once the types fit.
- **A routine parameter's default told back by GET_DDL** prints a parenthesized arithmetic operand as the cast to its
  type, `1 * (CAST(2 + 3 AS NUMBER(2,0)))`, a cast or a one-argument TO_VARCHAR to a text without a width as
  `CAST(1 AS VARCHAR)`, and TO_VARCHAR with a format as TO_CHAR.
- **The tag functions' echoes.** A wrong count of SYSTEM$GET_TAG's arguments echoes a column as written
  (`[SYSTEM$GET_TAG(N, 'tt')]`), and SYSTEM$GET_TAG_ALLOWED_VALUES's refusal prints what the plan folds early as its
  value (`[('E1') || 'x']`, `['TEST_SCHEMA']`) and drops a join to the empty string.
- **A star over two relations joined with USING or NATURAL** is echoed as the plan's one view, `SYS_VW.ID,
  SYS_VW.B, SYS_VW.B_0`, and an EXCLUDE of a name it holds twice is `ambiguous column name 'B'`.
- **A positional argument after a named one, placed at the argument's own token.** A typed literal stands at its
  string, a COLLATE at the word, a NOT IN list at the NOT and an IS DISTINCT FROM at the IS; a star among such
  arguments is read — `n1(a => 1, t1.*)` placed at the T1, a bare star at line 0, position 0 — where it was a syntax
  error.
- **A range FOR's bounds** are judged as the block reaches them: a miscounted call in the bound the counter starts
  from is placed eight columns into the bound (fifteen when the bound calls a user's function), one in the other bound
  at its own column, the upper bound being the start of a REVERSE loop, and the uncaught exception stands at the
  bound's first token, a parenthesis too. A subquery's unresolved name in a block's expression is refused ahead of the
  expression's types.
- **The words after a DESCRIBE's object are read in pairs to the end of the statement**, the first of a pair taken as
  a property's name and the token after it refused: `DESCRIBE TABLE t x y z w v;` is the y, the w and the ';', and a
  dataset's, a model's, a stage's, a function's and a policy's DESCRIBE read so too.
- **EXPLAIN of a star over one table** names the columns the star stands for — `PQ1.N, PQ1.M`, `T.N, T.M` under an
  alias, less what an EXCLUDE names — and its TableScan row lists the columns it reads, where it printed `*` and no
  columns.
- **A set operation an IN reads that holds an outer aggregate** is `Unsupported subquery type cannot be evaluated` at
  the subquery, where the place rule refused it first.
- **`x IN (SELECT 1 WHERE FALSE)` over an aggregate of a type answering NULL** — SUM(NULL), AVG(NULL), MAX(NULL::INT)
  — is FALSE, where it was NULL; MAX(NULL) and a NULL constant still fold to NULL.
- **A request of several statements that declares its count holds an EXECUTE IMMEDIATE among them to one statement**,
  as the account's multi-statement runner does, where the request's count applied to the text.
- **PIVOT's window words.** FIRST_VALUE, LAST_VALUE, NTH_VALUE, LAG and LEAD are syntax errors at the call's
  parenthesis and at the FOR column, and the other window functions `'RANK' is not a recognized aggregate function`,
  where each was an invalid function; a FOR column that is not there is refused where it is written.
- **A TEMPORARY Python or Java function is created without its handler judged**: `Temporary function TE successfully
  created, but not validated.`, and the first row that reaches a call is refused in the sentence CREATE would have
  given, where CREATE judged and refused it.
- **A Python procedure whose body defines no handler** is refused `Could not find handler in function P with handler
  nosuch` at the call, where a NameError was reported.
- **A JavaScript procedure's isColumnNullable** works out an expression's nullability as the account does — NULL, TRUE
  and FALSE, an operator over a nullable operand, a comparison, a CASE without ELSE, the aggregates, PARSE_JSON —
  where every expression was told not nullable.
- **ALTER DYNAMIC TABLE … REFRESH refreshes the dynamic tables it reads first**, suspended or not, and
  refreshed_dt_count counts them with the table, where only the table refreshed and the count was 1.
- **A stream on a dynamic table lists METADATA$ROW_ID first**, as a stream on a view does.
- **ALTER TABLE … MODIFY | ALTER UNIQUE (columns) and MODIFY | ALTER PRIMARY KEY** set the RELY of the key over those
  columns, where both were syntax errors; a list no UNIQUE covers is `Object 'UNIQUE KEYon' does not exist or not
  authorized.`, a FOREIGN KEY list `Object 'FOREIGN KEYon' …` and a missing primary key `Object 'primary key' …`, as
  the account words them.
- **GET_DDL lists the keys ALTER TABLE adds in the order they were made**, an added column's own key among them, where
  it listed every UNIQUE, then the primary key, then the foreign keys.
- **A dynamic table's parameters.** SHOW PARAMETERS IN TABLE lists its nineteen, its own values at TABLE level, where
  it listed none, and ALTER DYNAMIC TABLE … SET takes LOG_LEVEL, LOG_EVENT_LEVEL, TRACE_LEVEL, DATA_METRIC_SCHEDULE,
  DEFAULT_DDL_COLLATION, ENABLE_DATA_COMPACTION and QUOTED_IDENTIFIERS_IGNORE_CASE, each judged as the account judges
  it (`Invalid value for log level. Allowed values are TRACE,DEBUG,INFO,WARN,ERROR,FATAL,OFF.`), where each was an
  invalid property; MAX_DATA_EXTENSION_TIME_IN_DAYS takes a whole number alone, and NULL or UNSET takes a value away.
- **POLICY_REFERENCES over more domains.** SEQUENCE, NETWORK RULE, SECRET, NOTEBOOK, MODEL, DATASET, STREAMLIT, FILE
  FORMAT, AUTHENTICATION POLICY, PACKAGES POLICY and ORGANIZATION are `Invalid value 'NETWORK_RULE' for argument
  'ref_entity_domain' …`, where they were an unknown domain; an INTEGRATION names one that must exist, an APPLICATION
  is never found, and the ACCOUNT, by its locator in any case, has no rows, where it was refused as missing.
- **COMMENT ON SECRET** is `Unsupported feature 'SECRET'.`, where it was a syntax error.
- **A quoted policy name keeps its case.** CREATE [OR REPLACE | OR ALTER] MASKING, ROW ACCESS, PROJECTION or
  AGGREGATION POLICY `"qmp"` makes a policy of that name beside its upper-case namesake QMP, as RENAME TO `"qmp"`
  names it, and DESCRIBE, SHOW and DROP read it so, where the name was upper-cased and the statement was refused as
  taken or replaced QMP; an attachment names exactly that policy.
- **ALTER VIEW … SET / UNSET CHANGE_TRACKING** turns the view's change tracking on and off, where it changed nothing;
  turning it on, through ALTER or CREATE VIEW … CHANGE_TRACKING = TRUE, turns it on for every table the view reads.
  `COMMENT = NULL` takes the comment away, `CHANGE_TRACKING = NULL` is `invalid value 'null' for property
  'CHANGE_TRACKING'`, and a reserved word for a value is a syntax error at the list's first name.
- **ALTER TABLE … RENAME TO renames a view**, IF EXISTS or not, where it was refused as a missing table.
- **A view read AT or BEFORE a point reads its tables as of the point**, through the views it reads too, where the
  point was ignored and the view read now.
- **CHANGES over a view that joins its tables** reads every point a table's does — a time, a statement, another
  stream's offset, END, APPEND_ONLY — where only a stream on the view itself was read; a joined row is known by the
  table rows it joins, so an UPDATE reads as one row's DELETE and INSERT under one METADATA$ROW_ID, in a stream on
  the view too, window after window.
- **A stage's COPY_OPTIONS list judges NULL, a number with a fraction or an exponent, and a parenthesized value**
  (`invalid value [1.5] for parameter 'SIZE_LIMIT'`, `invalid value [TOK_PROPERTY_LIST] …`, `Invalid value specified
  for property 'ON_ERROR'`), where each was a syntax error; `SIZE_LIMIT = NULL` and a list of names alone are taken.
- **UNDROP brings a table back with its time-travel history.** A point before the drop, or while the table was
  dropped, reads the rows it held then — through AT, BEFORE, CHANGES and CLONE — where it was refused as before the
  table's creation; so do the tables of an undropped schema or database. An engine clone, a checkpoint's restore and a
  write-ahead log's recovery keep a dropped table's history for its UNDROP too.
- **A CLONE ... AT a point before a column change made before an engine copy** takes the shape of the point on the
  copy too — a clone, a checkpoint's restore, a recovery from a checkpoint — where it took the table's current columns.
- **A write-ahead-log restart without a checkpoint keeps the built-in objects' created_on**: the system roles, the
  default user, COMPUTE_WH, the SNOWFLAKE database and its schemas, and a configured default database report the
  engine's first start, which the log now keeps in its header (v7; an older log takes its first record's instant),
  where they moved to the restart.
- **SHOW EXTERNAL VOLUMES reads a scope.** IN ACCOUNT lists; IN SCHEMA, DATABASE, TABLE or APPLICATION is `Unsupported
  statement type 'Cannot show objects of type EXTERNAL VOLUME in SCHEMA'.`; and TERSE, STARTS WITH, LIMIT and WITH
  PRIVILEGES are taken, where each of them was a syntax error.
- **A RETURNS TABLE column named CONSTRAINT**, or by any word an argument's name takes, is created, for a function
  and a procedure alike, where it was a syntax error; a reserved word there names the list's `)` after it.
- **A `->>` chain's failing stage is refused as a block's statement is**: `Uncaught exception of type
  'STATEMENT_ERROR' on line 1 at position 13 : …`, placed at the stage, with a compile refusal's places counted from
  the stage; a reference to the stage before the chain's first is `Statement null not found`.
- **A SQL function's query body and a correlated subquery are tagged by the intervals they reach**, the argument's or
  the enclosing query's column statistics: `SELECT MAX(x) + a FROM other` called with 1 is `NUMBER(11,2)[SB2]` where
  it was `[SB8]`.
- **The session's BINARY_INPUT_FORMAT reads text as BINARY.** Under BASE64, UTF8 or UTF-8 a cast, CAST and
  TRY_CAST, a one-argument TO_BINARY and TRY_TO_BINARY, and a text written into a BINARY column decode as the
  format says, where they always read hex. HEX and BASE64 pass over spaces (`'48 69'`, `' SGk= '`), and an unknown
  TO_BINARY format is refused `Invalid binary format string 'FOO'`.
- **An ISO date with an hour alone** (`'2024-03-05 10'`) reads as a date and a timestamp, where it was not
  recognized.
- **A table stream cloned with its schema or database after its table went is lost in the clone.** SHOW STREAMS and
  DESCRIBE STREAM list it stale with `1 inaccessible table(s)`, a read is refused `Table for stream 'ST' invalid due
  to loss of table before or during cloning of parent entity`, and GET_DDL names its table
  `CLONED_FROM_STREAM_WITH_DROPPED_SOURCE`, where the clone read whatever table now held the name. A view stream
  any of whose view's tables went is as stale in the clone as in place.
- **A window frame offset that is no literal names one more token of the frame.** `ROWS BETWEEN v PRECEDING AND
  CURRENT ROW` names the `v` and then the AND, a second bound's offset its FOLLOWING, as the account reads on.
- **A failure in a block written inside a loop's body is placed where the block stands.** A cursor's query refused
  at the FOR or the OPEN of a nested block, and a statement of a nested `BEGIN … END`, are placed in the text that
  wrote the block (`on line 1 at position 105`), where they counted from the nested block's own first token.
- **A FOR over a cursor an earlier FOR ran to its end runs no iteration**, as on the account, where it opened the
  cursor afresh.
- **A view read twice in one statement keeps its handler calls apart.** `SELECT a.x, b.x FROM v a, v b` over a view
  that counts its calls answers 1 and 1, each reference of the view its own calls.
- **A plain star over a CALL's scan hands the answer out.** `SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))` after
  a CALL whose answer its RETURNS type cannot hold — a text wider than VARCHAR(2), what is no object for OBJECT —
  answers as the CALL did; every other statement over the scan is still refused.
- **A JavaScript result set's members.** Past the last row a column's member keeps the last row's value, and a
  column named like a method replaces the method once a row is read.
- **A Snowscript body a SQL function cannot read: the lines inside its first bracket.** The account reads on
  from the token refused in the bracket, and so does the engine: a second test after a finished IS test
  (`nosuch(a IS NULL IS NULL)` names the second IS, at the body's own level too), a quantifier no predicate takes
  before the token that opens its list (`a NOT LIKE ANY ('x', 'y')` names the ANY and the `'x'`, an RLIKE ANY too),
  and after an operator, a sign, a finished IS NULL, a LIKE with its pattern or a query in parentheses the next
  comma or the bracket's closing parenthesis (`nosuch(a.b.c + 1)` names the `)`).
- **A negated LIKE's quantifier, wherever it stands.** After the quantifier and the token that opens its list the
  lines follow the account's recovery: a call's own closing parenthesis when the predicate is its argument, a FROM
  subquery's own, the list's after a comma in an ORDER BY or a GROUP BY, nothing more in a WHERE for a name written
  first; a query in parentheses in a select list has its SELECT refused first, a join's ON condition is refused at
  the ON alone, and NOT REGEXP ANY reads as NOT RLIKE ANY.
- **Dynamic texts nested past a hundred.** A text EXECUTE IMMEDIATE runs from inside another nests 300 deep before
  the engine refuses it, where it stopped at 100; the account ran 1001 without a bound found.
- **A write over a CALL's scan of an answer wider than its RETURNS column is refused with the scan's own sentence.**
  `INSERT INTO t SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))` and CREATE TABLE ... AS SELECT are `String 'abc'
  is too long and would be truncated`, where the DML sentence was put around it. A view's too-long value keeps the
  DML sentence, as the account words it.

- **A CREATE VIEW whose query does not compile is refused before it ends the open transaction.** A ROLLBACK after it
  undoes the transaction's rows, where they had been committed first. In a block the view's own fault is named.
- **A comment close no comment opened, and an x''' binary literal, are read as the account reads them.** `SELECT 1
  */` names the slash and then the end of the text. `SELECT x'''` names the end after the lexer's line.

- **A FOR over an OBJECT loops once per key.** The variable holds NULL each time, where no iteration ran. The
  variable of a loop over a value is a VARIANT, so `SYSTEM$TYPEOF(r)` answers VARIANT[LOB].
- **A routine's RETURNS type written NOT NULL is told by DESCRIBE FUNCTION and DESCRIBE PROCEDURE.** The returns
  row reads `VARCHAR NOT NULL`; it is kept across a restart and a clone.
- **SYSTEM$ call shapes read as the account reads them.** A comma or an arrow where a call's first argument should
  stand names the call's parenthesis after its own line (`SELECT ABS(, 1)`). SYSTEM$PIPE_STATUS names a missing pipe
  as its argument writes it. A CALL of a SYSTEM$ function refuses a name written twice at its second writing, and
  echoes a named argument's value alone in an argument-count refusal.

- **A built-in function named under INFORMATION_SCHEMA is the built-in itself.** `INFORMATION_SCHEMA.UPPER('a')`,
  `SNOWFLAKE.INFORMATION_SCHEMA.SYSTEM$TYPEOF(1)` and `CALL INFORMATION_SCHEMA.SYSTEM$WAIT(0)` run the built-in;
  each was an unknown user-defined function. Its refusals name it bare, and a database written before the schema is
  looked up. An unknown name there is `Unknown function INFORMATION_SCHEMA.X.`.
- **A SHOW listing's word after a keyword scope, and a DESCRIBE's WITH, are named as the account names them.**
  `SHOW TABLES IN USER x (y)` names the `x`, where a token inside the parenthesis was named. `DESCRIBE TABLE t WITH x
  AS (SELECT 1) SELECT 1` names the `AS`, where the WITH was named.
- **A RESULTSET query's argument-count refusal echoes its binds as written.** `res := (SELECT ABS(:s, 1) FROM t)` is
  `too many arguments for function [ABS(:s, 1)]`, where the value written in the bind's place was echoed.

- **A call written as a statement of a block is refused as the account refuses it.** `BEGIN SYSTEM$WAIT(0); RETURN
  1; END;` is `Unsupported feature 'Function SYSTEM$WAIT cannot be executed as a statement in Snowscript'.`, the
  function named as written, where the name or its parenthesis was a syntax error. This holds in an EXECUTE
  IMMEDIATE's text and in a procedure's body too. A name no built-in function has is `Unknown function <name>`,
  checked first. A star, DISTINCT or an arrow among the call's arguments is refused where it stands.
- **A SQL table function called once per row of another relation is tagged by all of its calls.**
  `SYSTEM$TYPEOF(a)` over `ti, TABLE(t_div(ti.i))`, with `ti` holding 3 and 300, is `NUMBER(38,0)[SB2]`, where the
  declared width was kept.
- **The invalid-type sentence spells NVL2, CASE, COALESCE and DECODE as the plan holds them.** NVL2 is its IFF, a
  searched CASE is CASE_FLATTENED, and a COALESCE over NULLs alone is its IFNULL chain in the NULL type. A DECODE
  converts its search values with `COERCE(1 AS NUMBER(38,0))` and types a NULL result by the others.
- **A fraction given to a password policy's whole-number property is refused by its value.** `PASSWORD_MIN_LENGTH =
  1.5` is `invalid value [1.5] for parameter 'PASSWORD_MIN_LENGTH'`, where it was a syntax error. A missing tag
  set on a password or network policy is named as written.

- **A SQL function's overload is settled by its arguments' types while the statement compiles.** Where several
  overloads take as many arguments, a call judged none of their bodies before it ran; an exact number and the bare
  NULL now reach a number parameter and a text a text one, so `f_o(1)` over an empty table is refused when the
  overload it reaches reads a table that is gone.
- **A hexadecimal number opening a call's arguments names the call's parenthesis too.** `SELECT ABS(0x3)` reports
  the `'('` after the `'0x3'`, as the account does.
- **The security listings read ON and IN ACCOUNT as the account does.** `SHOW NETWORK RULES ON ACCOUNT` and `SHOW
  SECRETS ON ACCOUNT` refused the ON; the token after ACCOUNT is refused now, the statement's end too. A rule is
  shown on no name, `Object does not exist, or operation cannot be performed.`, and a secret listing looks its name
  up as a table. `SHOW NETWORK POLICIES IN ACCOUNT x` is `Account 'X' does not exist or not authorized.`, and the
  other listings read the name as a path that does not start from a database.
- **A timestamp moved by an interval keeps its days.** `YEAR(t + INTERVAL '7 years')`, `t - INTERVAL '1 day'` and
  DATEADD over a timestamp were tagged by the declared width; they are tagged by the column's days moved as the
  value is, and an INTERVAL written before a date or a time is refused while the statement compiles.
- **An empty IDENTIFIER() text is refused while a block compiles.** `IDENTIFIER('')` in a block's statement or in a
  RESULTSET's query was refused when the statement ran, under an uncaught-exception wrapper; it is refused before
  any statement runs, placed in the block's text.
- **SPLIT_PART reads a VARIANT BOOLEAN part.** `SPLIT_PART('a,b,c', ',', TRUE::VARIANT)` was refused by its argument
  types and `PARSE_JSON('true')` failed per row; a VARIANT BOOLEAN reads as 1 or 0, an ARRAY is refused as the
  VARIANT's cast, and a fraction a conversion of a text or a FLOAT constant made is echoed by its value.
- **Base64 decodes only whole groups.** `BASE64_DECODE_STRING('QQ')` and `BASE64_DECODE_BINARY('QUJDRA')` decoded the
  unpadded text; they are now refused as not legal base64 (the TRY forms answer NULL), and a line break inside the
  text is dropped as the account drops it, where a blank still refuses it.
- **A DATE has no epoch finer than a second.** `DATE_PART(epoch_millisecond, d)` and the microsecond and nanosecond
  epochs over a DATE answered a number; they are refused as an invalid date/time part while the statement compiles.
- **OBJECT_KEYS refuses a value that holds no object by its type.** A number, a text, an ARRAY, a BOOLEAN, a date or
  a binary was converted and answered NULL or failed per row; it is now `Invalid argument types for function
  'OBJECT_KEYS'` at the call as the statement compiles.
- **Declared types and tags of the function catalog.** `NULLIFZERO(NULL)` is the untyped NULL, LPAD and RPAD over a
  BINARY are a BINARY, the decodings are sized by their text's whole groups (a VARCHAR(0) spelled as such, a NULL's
  at the unknown length), CHECK_JSON and CHECK_XML are a bare VARCHAR, a fraction counts two whole digits in a bitwise
  width and BITSHIFTRIGHT counts its shift, `CURRENT_TIME(3.0)` is TIME(3), the task-cancel call is a VARCHAR(1); a
  conversion read by a format or one that does not fit keeps its declared tag, and an epoch part of a constant
  timestamp and a conversion of PI() are tagged by their value.
- **A conditional over texts and a NULL reads at its texts' scale.** `IFF(TRUE, '1.50', NULL) * 1` was a
  NUMBER(19,5) holding 1.50000; it is a NUMBER(19,1) holding 1.5, and LAST_QUERY_ID's refusal echoes the product
  unfolded and uncast.
- **GETVARIABLE's name is a constant only where the plan folds it.** An IFF or a searched CASE over a TRUE or FALSE
  condition was taken as a constant name, and a simple CASE or a DECODE settled by a later match or with no default
  too; they are refused, and the refusal echoes the name as the plan folds it — a NULL head dropped and the value
  cast to the conditional's type, `ENSURE_NULLABLE` before a last NULL, ZEROIFNULL and UPPER(NULL) folded.
- **The invalid-type sentence spells NULLs and conversions as the plan holds them.** A conversion of the bare word
  NULL is its target's typed NULL, a NULL tested by IS NULL is `COERCE(NULL AS NULL)`, NOT converts what is no
  condition, a number beside `||` is `TO_CHAR`, a division over a NULL rescales its dividend and NULLIFZERO is the
  NULLIF it stands for.
- **A derived relation inside a scalar subquery is merged.** `SUM(x)` over `(SELECT n + 1 x FROM t)` inside a scalar
  subquery was typed by the unmerged column, NUMBER(18,2)[SB8]; it is typed as `SUM(n + 1)` is, NUMBER(24,2)[SB16],
  wherever the relation reads nothing of the row around it.
- **A projection policy reads the tags of the column it judges.** Its body was evaluated with no column, so
  `SYSTEM$GET_TAG_ON_CURRENT_COLUMN` answered NULL and a policy keyed on the tag never allowed the projection; it now
  answers the column's value of the tag, else its table's.
- **SEARCH takes a star beside named options and refuses data with no text.** `SEARCH(*, 'a b', SEARCH_MODE => 'EXACT')`
  was a syntax error; it now searches the row. A number, a BOOLEAN or a date given as the data — a value or a column,
  alone or as every member of the row — is refused as `Expected non-empty set of columns supporting full-text search.`
  at the call, where its text was searched.
- **A FOR over a value that is no ARRAY is refused.** `FOR r IN (5)`, `FOR r IN x` over an INT or a VARCHAR, and a
  query's value ran no iteration; they are refused as `Expression in variant for loop does not evaluate to an array or
  an object` — while the block compiles for a literal or a plain variable, at the FOR for a query's value. A field read
  from a plain variable (`y.v` over an INT `y`) is an invalid identifier while the block compiles, where it read NULL.
- **A USING list binds an ARRAY or an OBJECT as text.** The value was bound as itself; it is now its JSON with each
  member on a line of its own and nothing indented (`[\n1,\n2\n]`, a VARCHAR(8)), as every other value is bound as its
  text.
- **`LET q INT;` declares the variable NULL.** A LET with a type and no value was a syntax error; it now declares the
  variable NULL of its type, as a DECLARE section's item does, so it reads NULL and takes an assignment, a `SELECT …
  INTO` and a `CALL … INTO`. Its semicolon is required.
- **A positional bind is a LIMIT's, an OFFSET's and a FETCH's count.** `LIMIT ?`, `OFFSET ?` and `FETCH FIRST ? ROWS
  ONLY` were syntax errors; a bind nothing sets is now refused while the statement compiles, `Bind variable ? not set.`
  at the mark.
- **A task's body is stored as written.** A word in it that names no type — `CREATE TASK tk … AS CREATE TABLE t9 (x
  NOSUCHTYPE)`, a cast's or an added column's — refused the task (`Unsupported data type 'NOSUCHTYPE'.`); the task is
  now created, its body judged when it runs, and GET_DDL tells the body as written.
- **The dropped ORDER BY of an aggregate query is read first.** An aggregate in it over a select alias that is an
  aggregate (`SELECT MAX(id) x FROM fz ORDER BY MAX(x)`) answered the row; it is refused as `Aggregate functions cannot
  be nested: [MAX(FZ.ID)] nested in [MAX(X)]`, the key printed as the plan holds it (`MAX(SCALED_ROUND_INT_DIVIDE(X,
  0))` for `MAX(x / 0)`), with no GROUP BY or under a GROUP BY ALL. A name nothing resolves inside a window call of the
  clause is an invalid identifier at the name.
- **LIKE and ILIKE called as functions refuse an OBJECT or an ARRAY.** `LIKE(OBJECT_CONSTRUCT('a', 1), 'i%')` and
  `ILIKE(ARRAY_CONSTRUCT(1), 'x')` answered FALSE; they are now refused at the call (`Invalid argument types for
  function 'LIKE': (OBJECT, VARCHAR(2))`), as the operators are. A NULL pattern folds the match to NULL before any type
  is asked: `OBJECT_CONSTRUCT('a', 1) ILIKE NULL` is NULL, where it was refused.
- **A call's star argument takes one modifier.** `HASH(* EXCLUDE (id) EXCLUDE (b))` was read with both, and ran over a
  table with a column left; a second EXCLUDE or ILIKE is now a syntax error, with live's lines: the keyword, then the
  token after it (`'('`), or after an ILIKE's pattern (`')'`).
- **GET_DDL('ICEBERG_TABLE', …) looks the table up.** It was refused as an unsupported object type; it now reads the
  name as a table — a plain table answers its DDL — and a missing one is `Iceberg table 'TEST_DB.TEST_SCHEMA.NOSUCH_ICE'
  does not exist or not authorized.`, the type spelled in any case or with a space.
- **A VALUES clause given an alias list keeps its own column names in the plan.** A star argument's echo over one
  printed the aliases (`W.M, W.N`); it now prints `W.COLUMN1, W.COLUMN2`. A USING join matches the clause's columns by
  those names, so `(VALUES (1)) AS w (id) JOIN fz USING (id)` is `Invalid identifier ID`, where it joined.
- **A NULL beside a correlated subquery folds GREATEST, LEAST and BETWEEN.** `GREATEST(NULL, (SELECT …))`, `LEAST(CASE
  WHEN FALSE THEN 1 END, (SELECT …))` and `NULL [NOT] BETWEEN 1 AND (SELECT …)` were refused (`Unsupported subquery type
  cannot be evaluated`); they now answer NULL without running the subquery, as an operator beside a NULL does. A NULL
  bound, GREATEST_IGNORE_NULLS and a GREATEST over values still leave the subquery to be judged.
- **A bare name in a subquery over a view or a CTE is the view's or the CTE's column only when it has one.** Their
  columns were unknown to the correlation judgement, which took every bare name in such a subquery for theirs and so
  could not tell a subquery of its own from one that reads the outer row; they are now read from the view's column list
  or query and from the CTE's column list or select list. A WHERE subquery over one beside a lateral table function is
  applied before the function, which numbers only the rows the condition keeps.
- **CREATE EVENT TABLE … CLONE takes a point of time and nothing else.** `CLONE src AT (…)` and `BEFORE (…)` were syntax
  errors; they are now taken, the point judged as a table's clone judges it (`Future data is not yet available for table
  BASE_ET.`). Any token after the source or the point is refused where it stands (`'COMMENT'` at its position, where the
  refusal named a later token), a CLONE with no source at the end of the statement, and a materialized view as the
  source is `Cannot clone an Event table from a materialized view.`
- **A staged file query's missing schema-qualified format is named with its database.** `FILE_FORMAT =>
  test_schema."ff"` is refused as `File format 'TEST_DB.TEST_SCHEMA."ff"' does not exist or not authorized.`, where the
  database was left out.
- **SYSTEM$CLUSTERING_DEPTH reads its predicate.** The third argument was ignored. It now filters the rows, compiled
  after the table's name and the key list: a name the table has not is an invalid identifier placed 56 characters in,
  after both as written (`error line 1 at position 62` for `('t', '(i)', 'nosuch = 1')`), a syntax fault is placed the
  same way, an aggregate and a value that is no condition are refused as a WHERE refuses them, and a predicate no row
  meets gives the depth 0.
- **The task-only functions are refused outside a task while the statement compiles.**
  SYSTEM$GET_PREDECESSOR_RETURN_VALUE answered NULL and SYSTEM$TASK_RUNTIME_INFO `{}` outside a task, and inside
  SYSTEM$TYPEOF all three task-only functions were typed `NULL[LOB]`. Each is now `Function SYSTEM$… must be called from
  within a task.`, under a WHERE, over a table with no row and inside SYSTEM$TYPEOF alike; SYSTEM$CLUSTERING_RATIO
  inside SYSTEM$TYPEOF is `Unsupported feature 'Clustering Ratio'.` too.
- **SYSTEM$LAST_CHANGE_COMMIT_TIME is 0 for a table never written.** The token of a table no statement had written was
  its creation stamp; it is now 0, for a view over such tables too, until an INSERT or a CREATE TABLE AS writes the
  table, with a row or without. SYSTEM$TYPEOF tags the token by its value: `NUMBER(38,0)[SB8]` for a written table's,
  `[SB1]` for the 0, where it read `[SB16]`.
- **SYSTEM$STREAM_GET_TABLE_TIMESTAMP and SYSTEM$QUERY_REFERENCE are a VARCHAR(1).** They were typed `NULL[LOB]` to
  SYSTEM$TYPEOF and VARCHAR(16777216) as a column, and the stamp was a number, so a table created over it took
  NUMBER(38,0). Both are now a VARCHAR(1) whatever text they hold, `|| 'x'` is a VARCHAR(2), a UNION ALL with `'abcd'` a
  VARCHAR(4), and a table created over the stamp keeps the width and the whole text.
- **A Pub/Sub name is judged as the account judges it.** `GCP_PUBSUB_TOPIC_NAME` took any text, and a subscription's
  name only had to have a project of six characters. Both are now `projects/<project>/topics|subscriptions/<name>`, the
  two words in any case and the word matching the parameter: a project of 6 to 30 lower-case letters, digits and
  hyphens, a letter first and no hyphen last, and a name of three characters or more, a letter first and not `goog`.
  Anything else is `invalid value ['<text>'] for parameter 'GCP_PUBSUB_TOPIC_NAME'`, and a topic's name is judged before
  its direction. DESCRIBE of an inbound Pub/Sub integration lists `ENABLED, DIRECTION, GCP_PUBSUB_SUBSCRIPTION_NAME,
  GCP_PUBSUB_SERVICE_ACCOUNT, COMMENT`, without the provider.
- **ALTER EVENT TABLE's syntax error names the TABLE, and a materialized view's bare new name is in the session's
  schema.** `ALTER EVENT TABLE … RENAME` was refused at the EVENT; it is now refused at the TABLE (`'TABLE'` at position
  12), as an event table is renamed by ALTER TABLE. `ALTER MATERIALIZED VIEW s.mv RENAME TO mv_r` kept the view in `s`;
  a bare new name now places it in the session's schema, as a created name is placed.
- **ALTER SCHEMA, ALTER DATABASE and ALTER TABLE take their own level's parameters alone.** SET and UNSET took any name.
  An unknown name is now an invalid property of the kind (`invalid property 'NOSUCH_PARAMETER' for 'SCHEMA'`), judged
  first and in the account's order. A parameter of another level, such as TIMEZONE or AUTOCOMMIT, is refused for want of
  access (`Insufficient privileges to operate on schema 'TEST_SCHEMA'.`, and `… on database …`, `… on table …` and, for
  an Iceberg table, `… on iceberg_table …`). CREATE SCHEMA and CREATE TABLE refuse one as `cannot set parameter
  'TIMEZONE' at 'SCHEMA'`. A database's or a schema's CLASSIFICATION_PROFILE may be unset, and a profile it is given
  must exist (`invalid value 'p' for property 'bundle instance name'`, at CREATE SCHEMA too); CREATE SCHEMA refuses a
  catalog's namespace properties, and a REST `PUT` of a database no longer unsets them.
- **A routine parameter's default may be a condition.** `p BOOLEAN DEFAULT NOT TRUE`, `TRUE AND FALSE` and `TRUE OR
  FALSE` were syntax errors. They are now taken and told back as the plan prints them (`NOT(TRUE)`). A default of type
  BOOLEAN that the parameter's type does not take is refused at the predicate's operator: a comparison, LIKE, IN,
  BETWEEN or IS given to a text or a number, or TRUE given to a number or a DATE (`default argument data type BOOLEAN
  does not match expected data type NUMBER(38,0)`).
- **A key dropped by its columns must be there.** DROP FOREIGN KEY, DROP UNIQUE and DROP PRIMARY KEY over a table that
  holds no such key used to answer success. They are now refused in the account's own words (`constraint 'FOREIGN
  KEYon' does not exist`, `constraint 'UNIQUE KEYon' does not exist`, `constraint 'primary key' does not exist`). A
  foreign key written with an ON DELETE or ON UPDATE action is not found by its columns, and a column the table lacks
  is an invalid identifier at the column.
- **A VARCHAR written without a width inside a routine's or a policy's shape stays unsized.** DESCRIBE's `returns` and
  GET_DDL now spell it bare (`OBJECT(a NUMBER(38,0), b VARCHAR)`), where it read `VARCHAR(16777216)`, and
  INFORMATION_SCHEMA spells it at 134217728, for functions, procedures and masking policies alike.
- **JavaScript reads the old rule zones as live does.** Under CET, MET, EET and WET, a date's zone is named by its
  offset (`GMT+0100 (GMT+01:00)`, and `(GMT)` at none). A formatter given an unknown zone is refused as `Invalid time
  zone specified: X`.
- **An ARRAY's element and a MAP's value may be declared NOT NULL.** `ARRAY(INT NOT NULL)` and
  `MAP(VARCHAR, NUMBER NOT NULL)` were syntax errors; they are now types of their own. SYSTEM$TYPEOF spells the mark
  (`ARRAY(NUMBER(38,0) NOT NULL)[LOB]`), and so do DESCRIBE, SHOW and GET_DDL. A routine's written signature must
  repeat it, while a call binds whatever the mark. A cast refuses a NULL element (`NULL result in a non-nullable
  column`). A table refuses the type (`DDL operation failed because it would result in a non-nullable structured type
  field 'A.element' contained within an array or map`). ARRAY_COMPACT and OBJECT_KEYS answer an ARRAY whose element is
  never NULL.
- **A comma may stand between a statement's own properties.** CREATE STAGE, CREATE TABLE, CREATE VIEW, CREATE SCHEMA
  and COPY INTO take `a = 1, b = 2`, where the comma was a syntax error. A comma before the first property, or a
  doubled one, is now a syntax error at the comma alone. A table takes one after its last property; a stage and a
  schema refuse it at the end of input. One after a CLUSTER BY or after WITH MANAGED ACCESS is refused at the comma.
- **A select list's calls are judged before the other clauses'.** Of two calls with too many arguments a clause
  apart, the select list's is named before the WHERE's, HAVING's, QUALIFY's or ORDER BY's, and so is a cast its operand
  refuses. A miscounted call of the list also comes before a clause whose predicate is of no BOOLEAN type. A stage
  query's FILE_FORMAT matches a quoted name in exactly its case. A RETURN value's fault is placed at its first
  parenthesis.
- **An element or a path read out of a semi-structured value is cast as the VARIANT it is.** `[1,2][0]::DATE`,
  `GET([1,2], 0)::DATE`, `{'n': 1}:n::DATE` and `GET({'n': 1}, 'n')::DATE` answered the number. Each is now refused
  `Failed to cast variant value 1 to DATE`, as `PARSE_JSON('1')::DATE` is, and a number is no TIME, BOOLEAN or BINARY
  either. A text element is read as a VARIANT text, so `['x'][0]::BOOLEAN` is the variant's refusal too.
- **ALTER VIEW and ALTER MATERIALIZED VIEW read SET and UNSET as property lists.** Every name is now read and judged
  before the view is looked for, IF EXISTS or not, where anything but COMMENT and SECURE was a syntax error. An item
  without its value is a syntax error where the list begins. A name the view does not take is `invalid property 'FOO'
  for 'VIEW'` (`for 'MATERIALIZED_VIEW'`); of several, the one named is the first of a table hashed by name. A value of
  another type is `invalid value [5] for parameter 'COMMENT'`. A parameter is `Insufficient privileges to operate on
  view 'V0'.` A view refuses a repeated name, while a materialized view takes the last one written. A view takes
  CHANGE_TRACKING, and a materialized view does not. ALTER TABLE … SET now judges its names the same way before it
  applies any property, so `SET COMMENT = 'c' FOO = 1` leaves the comment as it was.
- **Clustering actions cross between tables and materialized views.** ALTER TABLE clusters, reclusters, unclusters and
  renames a materialized view. ALTER MATERIALIZED VIEW clusters, reclusters and unclusters a table; its SUSPEND, RESUME
  and SECURE leave the table as it is. A plain view is refused a clustering key under either statement (`cannot use
  ALTER TABLE on a view; use ALTER VIEW instead`), SUSPEND and RESUME leave it be, and RENAME TO renames it. Any other
  action is refused by the kind the name finds (`Object found is of type 'MATERIALIZED_VIEW', not specified type
  'TABLE'.`), ALTER VIEW included. A search optimization on a materialized view is `Invalid object type: 'MV0'`, and
  one added to a missing table names an object, IF EXISTS or not. A materialized view's column list may follow its
  options (`CLUSTER BY (a) (a, b) AS …`), and a second COPY GRANTS with no column list before it is a syntax error.
- **A structured parameter's shape is an overload of its own.** Beside `f(x OBJECT(a INT))`, another field name,
  another member family, another field order, an `ARRAY(VARCHAR)` beside an `ARRAY(INT)` and a MAP of other members
  are each created as overloads, for functions and procedures alike, where they were refused as already existing or as
  ambiguous. The widths inside a shape stay the same signature. A field's NOT NULL makes no overload either: a plain
  CREATE of such a twin is refused naming the new signature (`Object 'F1(X OBJECT(a NUMBER NOT NULL)):VARCHAR' already
  exists.`, and `…:TABLE: (C NUMBER)` for a table function); OR REPLACE replaces it; IF NOT EXISTS leaves it; and a
  temporary one stands beside a permanent one. Each shape is dropped and described by its own written signature. A
  call reaches the overload its argument's shape binds: fields by name and order, a number's leaf binding any number's
  (`OBJECT(a FLOAT)` reaches `OBJECT(a INT)`), and every other leaf only its own family. A call no overload binds is
  refused at the call (`error line 1 at position 7` for a function, `line 0 at position -1` for a CALL). An untyped
  NULL runs the plain OBJECT overload first.
- **A built-in table function reads a positional argument written after a named one; a SQL procedure's body is judged
  for one at CREATE.** FLATTEN binds such an argument to the parameter of its place among every argument written:
  INPUT, PATH, OUTER, RECURSIVE, MODE. So `FLATTEN(INPUT => x, 'a')` reads `'a'` as PATH, and `LATERAL FLATTEN` reads
  the same. A positional argument of the wrong type is refused by its place's number (`invalid type [VARCHAR(1)] for
  parameter '3'`), and a missing INPUT and more than five arguments are refused at the function's name. GENERATOR
  leaves such an argument unread. SPLIT_TO_TABLE and STRTOK_SPLIT_TO_TABLE refuse the argument name, which they take
  none of. Before, every such call was a syntax error at the argument. `CREATE PROCEDURE … LANGUAGE SQL` refuses a
  positional argument written after a named one in its body, at the argument in the body's text. An unknown table
  function called with named arguments is named in upper case. A table procedure in a FROM clause refuses an
  argument that calls an unknown function as the statement's own refusal, not as the procedure's execution error.
- **Correlated subqueries over several equalities or DATE and BOOLEAN keys are computed once; a called SQL function's
  GET_DDL reads its arguments; a SQL table function joined with ON.** A correlated EXISTS, NOT EXISTS or aggregate that
  reads the outer row through several `inner = outer` equalities, or through DATE, TIME, TIMESTAMP_NTZ or BOOLEAN keys,
  is now computed once for the statement and probed per outer row, as the single-key form already was. It answers as
  before, in time that grows with the two relations rather than with their product: about 18 ms where it took 676 ms,
  and 26 ms where it took 6.2 s at 2,500 rows a side. A statement that calls a SQL function now looks up the object
  that a GET_DDL in the body names through a parameter, with the call's constant argument in place. That argument may
  be a literal, a constant expression, a named argument, a default or the calling function's own parameter. A missing
  object refuses the statement wherever the call stands, as the account refuses it. A SQL table function joined with
  ON reads the left side as a correlated subquery. An inner join keeps the pairs its condition holds, and an outer join
  is refused as `Unsupported subquery type cannot be evaluated`. Before, every such join was refused as a lateral
  table function with a join predicate.
- **Two sessions each hold a temporary table or view of one name; a temporary table hides a permanent one from its
  own session alone.** A session's temporary tables and views are its own. Another session may create one of the
  same name, and `CREATE OR REPLACE TEMPORARY TABLE` in one session leaves the other's untouched, each session reading
  its own rows. Before, the second session was refused as `Object '…' already exists.`, which parallel sessions that
  run the same procedure met. A temporary table above a permanent table of its name now hides it from its own session
  only, and every other session keeps reading the permanent rows. A dropped schema takes every session's temporary
  tables with it.
- **FROM items join in written order; Azure stage locations are judged; ADD COLUMN echoes the planned default; hybrid
  tables hold their keys on UPDATE and DELETE.** A FROM clause's comma items and explicit joins are joined in the
  order they are written. So `SELECT *` over `FROM d JOIN p ON …, e` lists d, p, e, a NATURAL or USING join merges
  only the relations written before it, and a join's ON can't read a later comma item (`invalid identifier`).
  Before, every comma item was joined first. `CREATE STAGE` and `ALTER STAGE … SET URL` refuse an Azure location
  without a storage account, a `.blob.core.windows.net` host or a container, with the account's sentences for the
  Data Lake and other endpoints and for the Government and China clouds. A stage written `AZURE://` is listed as an
  external Azure stage. `ALTER TABLE … ADD COLUMN` echoes a refused default as the plan prints it (`CURRENT_DATE()`,
  `DATE_ADDDAYSTODATE(NEGATE(1), CURRENT_DATE())`), and a timestamp plus a number is refused at the operator first.
  A hybrid table's UPDATE refuses a repeated primary or UNIQUE key and a foreign key that names no parent row. A
  DELETE or UPDATE refuses to take away a key value a child row names, and a UNIQUE key over several columns is held
  whole. A block's handler reads SQLCODE 200001 and 200009 for the primary and foreign key refusals.
- **A boolean of aggregates is an aggregate item; a select list's subquery raises ahead of no rows; a grouping reads
  the WHERE's alias values; set-operation arms convert before they are compared.** `SELECT MIN(r) >= 1 AND MAX(r) <=
  4 FROM t` answers one row, the connective over the aggregates. Before, AND, OR and NOT hid the aggregates, and the
  query was refused as `Unknown function MIN.`. An uncorrelated scalar subquery of the select list that reads a
  relation raises its own fault (`Single-row subquery returns more than one row.`) even when no row reaches the
  projection, the grouping or the window stage, because the account computes it ahead of the rows. Before, only an
  aliased item did, and only when the WHERE received rows. A GROUP BY key that names an alias the WHERE read groups
  by the value the WHERE tested, so `UNIFORM(1, 4, RANDOM()) AS r … WHERE r > 1 GROUP BY r` makes no group the WHERE
  dropped. A DATE arm of an INTERSECT or EXCEPT beside a TIMESTAMP becomes its midnight before the rows are compared.
  A VARIANT arm under an ARRAY column becomes an array, as TO_ARRAY makes one. Under an OBJECT column, a value that
  holds no object is refused.
- **CONNECT BY refuses its pseudo-columns out of place; a QUALIFY window's correlated key is refused; ZIPF;
  cumulative FLOAT frames in one pass.** A CONNECT BY query refuses each of these, as the account does: LEVEL
  inside an aggregate, in the WHERE or in the ORDER BY (an invalid identifier at it), any window function,
  CONNECT_BY_ROOT inside a call (a syntax error), and SYS_CONNECT_BY_PATH inside a call (an unknown function).
  Before, it answered them. A subquery reading the outer row as the ordering key of a window the QUALIFY reads is
  refused as `Unsupported subquery type cannot be evaluated` at its SELECT. `ZIPF(s, N, gen)` is implemented
  (see `docs/functions.md`). A SUM or AVG over a FLOAT with a cumulative RANGE frame (`OVER (ORDER BY i)`) is read
  from one pass over its partition, with the same digits, where each row added up its whole frame again.
- **LAST_TRANSACTION replays as stored; a temporary file format is its session's; block arithmetic is typed as
  SQL.** A logged statement keeps what `LAST_TRANSACTION()` answered, as it keeps `CURRENT_TRANSACTION()`, so a
  restart stores the same id. `CREATE TEMPORARY FILE FORMAT` makes a format that only its session reaches, lists
  and drops, and that ends with the session. Before, it was permanent and every session's. Live lists every
  session's temporary file formats in `INFORMATION_SCHEMA.FILE_FORMATS`, and so does this. A call in a block hands
  SQL arithmetic over its NUMBER variables with each variable written as its value's conversion, so
  `TO_VARCHAR(x / 4)` over an INT holding 10 is `2.500000`.
- **A handler's statements replay once; another session's temporary objects are out of reach; a block's
  expression argument is typed as SQL types it.** A statement that a Java, JavaScript or Python procedure issues
  runs inside the CALL, whose replay issues it again. The write-ahead log now keeps it with the CALL alone, where it
  was also logged on its own, so a restart applied it twice. A task the handler runs is logged apart, as a SQL
  procedure's is. A session no longer reads, lists or drops another session's temporary table, view or stage. The
  refusal is the account's (`Object 'TMPT' does not exist or not authorized. …`), and only
  `INFORMATION_SCHEMA.VIEWS` lists every session's temporary views. Before, they were there for every session. A
  call in a block hands SQL an argument that reads no name as written, so `TO_VARCHAR(10 / 4)` is `2.500000` and
  `TO_VARCHAR(1.50::NUMBER(10,2))` is `1.50`, as in a query.
- **A structured ARRAY's added element takes its type; an aborted session's transaction ends with it; COPY splits
  on any delimiter.** `ARRAY_APPEND`, `ARRAY_PREPEND` and `ARRAY_INSERT` over a structured ARRAY cast the element
  to the array's element type. `2.7` into `ARRAY(INT)` is 3, `'2.5'` into `ARRAY(DOUBLE)` is 2.5, and `5` into
  `ARRAY(VARCHAR)` is `'5'`. All three answer the array's structured type. A text that reads as no number is refused
  in the cast's own words, even in a CTAS or an `INSERT`. A DATE, a BOOLEAN or an ARRAY into an `ARRAY(NUMBER)` is
  refused while the statement compiles. Before, the value was stored uncoerced, so an `ARRAY(NUMBER)` held strings
  and dates. A session aborted while its request waited for a table lock now has the transaction that request opened
  rolled back and its locks released. Before, the transaction stayed open under no session. A COPY load now splits
  at a `FIELD_DELIMITER` of several characters and at a `RECORD_DELIMITER` other than a line break (an enclosed
  field may hold one), and at neither under `NONE`. `SKIP_HEADER` still skips lines. Before, the load split at the
  delimiter's first character and ignored the record delimiter.
- **Recovery stores what identities answered, and a procedure's task run stays out of its transaction; COPY reads
  by its stage's file format.** A logged statement keeps what `CURRENT_SESSION()`, `CURRENT_TRANSACTION()` and
  `LAST_QUERY_ID(n)` answered and the rows it read from the query-history listings. A restart stores them again,
  where the replay used to store its own session, transaction and query IDs. A procedure or a block that runs
  `EXECUTE TASK`, `EXECUTE ALERT` or `ALTER PIPE … REFRESH` logs that statement in a record of its own, since its
  run commits by itself. The rest of the procedure stays in the caller's transaction, and its replay does not run
  the task again. Before, the whole `CALL` was logged as committed, so writes the caller's `ROLLBACK` had undone
  came back after a restart. A `COPY INTO <table>` without a `FILE_FORMAT` now reads its files by the format its
  stage keeps. That is a named stage's `FILE_FORMAT`, written out or naming a file format, or a table stage's
  `STAGE_FILE_FORMAT`. Its `SKIP_HEADER` skips the header row and its `FIELD_DELIMITER` splits the fields, where
  the load used CSV defaults. A `FILE_FORMAT` written on the COPY replaces the stage's as a whole.
- **Recovery keeps time-travel statement points and COPY load history, and drops temporary tables.** A logged
  statement keeps the query ID it first answered with. A CTAS, an `INSERT … SELECT` or a CLONE that reads a table
  `AT (STATEMENT => '<id>')` replays as it first ran, and a read at that point after the restart still finds it.
  Before, all three failed replay and were rolled back. A temporary table no longer outlives the session that
  created it across a restart, whether the log replayed it or a checkpoint held it. The files a table has loaded
  are kept with a checkpoint, a clone and a restored state, so a later COPY skips them rather than loading them
  again.
- **MERGE sources and unloads read through their policies; random values are no longer derived from the clock.**
  `MERGE … USING t` reads `t` as a query does: its row access policy keeps the rows the session may see and its
  masking policies mask them, where the MERGE copied every stored row unmasked. A stream source keeps the rows its
  table's policy allows. `COPY INTO @stage FROM t` of a table with only a row access policy unloads only the rows
  the policy allows, where it unloaded every row. `UUID_STRING()`, `RANDOM()`, `UNIFORM` and a seedless `SAMPLE`
  draw from a stream started by a random seed. That seed is recorded with the statement for the write-ahead log's
  replay. Before, they were derived from the statement's start alone, so two engines repeated each other's values
  and anyone knowing the time could predict them.
- **Row access policies through joins and DML; writes to a table dropped or replaced meanwhile; dynamic-table
  history over a restart.** A row access policy filters its table wherever the table is read: each relation of a
  join (both sides of a self-join, the null-extended side of an outer join), a view's own policy where the view is
  joined, and the sources of `UPDATE … FROM` and `DELETE … USING`. Before, a join read every row. An UPDATE or a
  DELETE of a protected table changes and counts only the rows the policy lets the session see. A transaction that
  wrote a table another session then drops commits into the dropped version, which `UNDROP` brings back with the
  change; and a table that replaced it by CREATE OR REPLACE stays empty. Before, the change was lost on UNDROP or
  landed in the new table. A dynamic table's refresh history is kept with a checkpoint, so a read AT a point
  before a restart still reaches it.
- **Staged-file encodings; INSERT OVERWRITE beside a held table; SHOW LOCKS while a statement waits.** A
  staged-file query reads a CSV file in its format's ENCODING, a stage's own format included, where it read every
  file as UTF-8. A byte the set cannot read refuses the field holding it when the query reads that field: `Invalid
  UTF8 detected in string 'caf0xE9'`, then the file, line and character, and the row. A count of the file's rows
  still answers. `REPLACE_INVALID_CHARACTERS = TRUE` reads such a sequence as U+FFFD. A JSON file's byte that is no
  UTF-8 refuses the staged query and the load (`Error parsing JSON: invalid UTF-8 sequence: 0xE9`) unless that
  option is set, where both read U+FFFD silently. A COPY load's refusal spells the byte as `0xE9`, where it showed
  U+FFFD. An `INSERT OVERWRITE` into a table another session's transaction holds waits for it and is refused at
  LOCK_TIMEOUT, as an UPDATE is, where it rewrote the table at once. SHOW LOCKS lists a waiting statement's lock as
  `WAITING` with no `acquired_on`, where it listed it as `HOLDING`.
- **Write-ahead log replay: statements in the place they ran, RESULT_SCAN rows, session state.** A transaction
  that committed after other sessions' work replays each statement where it ran, so an `INSERT … SELECT`,
  `UPDATE … FROM`, `MERGE` or `DELETE … USING` reads after a restart what it first read, where it read what the
  others committed meanwhile. A logged statement reading a result by `RESULT_SCAN` keeps the rows it read, so
  `RESULT_SCAN(LAST_QUERY_ID(-2))` or a query ID held in a variable reads them again, where the replay read another
  result or none. The variables and parameters a session set before a checkpoint are in force again, and a replayed
  `ALTER SESSION SET AUTOCOMMIT = FALSE` no longer leaves the replayed statements after it uncommitted, which lost
  them. The log is written in format v5; an earlier log is read and rewritten in it.
- **OBJECT_PICK with an ARRAY of keys; a VECTOR cast to ARRAY; a window AVG over a VARIANT or a text.**
  `OBJECT_PICK(o, ARRAY_CONSTRUCT('a', 'b'))` and `OBJECT_PICK(o, ['a', 'b'])` pick the keys the array holds,
  where they picked none (a VARIANT holding an array is still one key). `[1.0, 0.1234567]::VECTOR(FLOAT,
  2)::ARRAY` holds the DOUBLEs its 32-bit elements widen to, 0.1234567016, as `TO_ARRAY` does, where it held the
  six-decimal display text. `AVG(v) OVER ()` over a VARIANT or a text is declared a FLOAT, as the grouped AVG is,
  where it was declared a whole number, so a CTAS stored 1.75 as 2.
- **(+) beside explicit joins; JSON nulls in the ordering aggregates; STRIP_NULL_VALUES in staged queries.** A
  query block whose FROM holds inner joins and comma items joins a (+)-marked relation after the inner joins its
  marked conjuncts read, so `FROM d JOIN p ON …, e WHERE e.k(+) = p.k` keeps the rows of p that no e matches, with
  SELECT * in the written order, where the marker was ignored. MEDIAN, PERCENTILE_CONT and PERCENTILE_DISC over a
  VARIANT count a JSON null as a value sorting after every number — MEDIAN over {JSON null, 3} is NULL and over
  {JSON null, 3, 5} 5 — and APPROX_COUNT_DISTINCT counts it, where it was skipped. A staged-file query reads a JSON
  file by its format's STRIP_NULL_VALUES too, and a null array element is left as a hole, `undefined`, in a query
  and a COPY load alike.

- **Each data directory keeps its own stages.** An engine with a write-ahead log or snapshot persistence and no
  `stage.internal.localRoot` configured keeps its internal, table and user stages in `stages` inside its data
  directory, so two data directories whose stages share a name no longer see or erase each other's files; a data
  directory an earlier version wrote takes a copy of the shared root's stages once, on its first start.

- **Text functions read a number by its SQL text; CALL arguments; the driver's exact numbers.** UPPER, LENGTH,
  SUBSTR, LPAD, REPLACE, POSITION and the other text functions, and LIKE, read a number as it converts to text —
  `UPPER(0.0000001)` is `0.0000001` and `LENGTH(1e20::FLOAT)` 5 (`1e+20`), where they read `1E-7` and `1.0E20`. A
  CALL takes `NOT TRUE` and `TRUE AND FALSE` as arguments, binds `$1` alone as NULL, and words a number too wide
  for its NUMBER parameter at the parameter's scale, `type FIXED[SB8](5,2){nullable}, value 123456789.00`. A
  VARIANT variable bound in a query is a VARIANT column, so a RETURNS TABLE (a VARIANT) procedure answers the number
  its VARIANT parameter was given. The in-process JDBC transport reads a scale-0 NUMBER that fits a long as a
  `Long`, and the columns of a LIST are reported nullable.

- **Path steps match keys exactly; a failure under another handler language; temporary routines per session.** A
  colon step, a quoted step, a bracket and a chained step find a member only when its key is written in the same
  case — `PARSE_JSON('{"id":1}'):ID` is NULL and `WHERE v:ID = 1` matches no row, where a key of any case matched —
  over a VARIANT, an OBJECT, an object constant, an ARRAY element and a FLATTEN value alike. A Python procedure that
  fails under a JavaScript procedure's `snowflake.execute` reports its refusal to the caller every time, where the
  second such call could fail on an internal check of the polyglot runtime; a handler's own exception keeps the
  thread's Python interpreter, where every failure replaced it, and a Python function whose body lacks its handler
  is refused even when an earlier body defined one of that name. A temporary function or procedure is its session's
  alone: another session neither calls nor lists it (INFORMATION_SCHEMA.FUNCTIONS lists it, as the account does),
  creates its own of the same name and signature, and drops only its own; a temporary routine over a permanent
  one's signature is refused `Object 'PERMF():NUMBER(38,0)' already exists.`, and a permanent function or procedure
  created after a temporary one of its signature is the one every session calls.

- **NaN and the infinities in ROUND and SIGN; the domain-fault print; the precedence of `||`.** ROUND, at any
  scale, hands NaN and the infinities through — `ROUND('inf'::FLOAT)` is Infinity, where it was
  9.223372036854776E18 — and SIGN of a FLOAT is a FLOAT, 0.0 for NaN and ±1.0 for an infinity, where SIGN of a
  non-finite value failed. A refused floating-point operation prints its arguments as C's %g does,
  `sqrt(-1e+10)`. The concatenation operator shares the level of `+` and `-`, left-associative with them and below
  `*`, `/` and `%`: `1 + 2 || 'a'` is `3a` and `'x' || 2 * 3` is `x6`, where `||` bound tighter than both and the
  statements failed or answered another value.

- **SEARCH over a row with a NULL; date conversions; a block's declared variables in a call.** SEARCH over a row of
  columns leaves its numbers out and answers NULL where no searched column matches and one of them is NULL, where it
  answered FALSE; `SEARCH(t.*, …)` searches the row of t's columns, where they were read as further arguments.
  DATEDIFF of a TIME beside a DATE is refused by its argument types before its unit; a VARIANT holding an OBJECT
  converted to a date or a timestamp is refused `Failed to cast variant value {…} to DATE`; TO_TIME of a
  TIMESTAMP_TZ or a TIMESTAMP_LTZ answers its wall clock's time, where it failed. A block binds a NUMBER, FLOAT or
  BOOLEAN variable into a call as the conversion of its text to its declared type: TO_VARCHAR of a NUMBER(10,2)
  holding 1.50 is 1.50, where it was 1.5, and a refused call echoes `CAST('1.5' AS FLOAT)`.

- **Recursive CTE steps; CSV delimiters of several characters; escapes in a COPY load; two restart cases.** A
  recursive CTE's step values take the column's settled type — a DATE column holds the date of a timestamp a term
  computes, a timestamp column the timestamp of a date, a FLOAT column a double — where each kept the term's own; a
  number anchor beside a BOOLEAN term describes BOOLEAN; a term that reads its CTE inside a derived table or a
  subquery is refused `'R' is an invalid Recursive CTE`, where it failed as a missing object. A staged-file query
  splits on a FIELD_DELIMITER of several characters and a RECORD_DELIMITER other than a line break, where it split
  on the delimiter's first character and ignored the record delimiter. A COPY load reads an escaped line break as
  part of its record and ESCAPE inside an enclosure, as the staged-file query does, where it split the record and
  kept the escape. A warehouse keeps the resumed_on and updated_on SHOW WAREHOUSES lists across a snapshot restart,
  and an EXECUTE TASK inside an explicit transaction is no longer run again by a WAL restart.

- **Aggregates over an untyped NULL; PIVOT values written as expressions; COPY's ENCRYPTION group.** SUM(NULL)
  declares NUMBER(30,0), AVG(NULL) NUMBER(36,6) (NUMBER(33,3) over a window), MEDIAN(NULL) NUMBER(12,3) and the
  variances NUMBER(38,6), over a column holding nothing but NULL too, where SUM and AVG reported BIGINT and stored
  NUMBER(38,0) and MEDIAN and the variances a text; MAX(NULL) and MIN(NULL) project a VARCHAR(0) column. A PIVOT
  value may be any constant expression — `TRUE::BOOLEAN`, `TO_DATE('2024-01-01')`, `1 + 1`, `NOT FALSE`, `(SELECT
  1)` — and names its column as the account prints it (`CAST(TRUE AS BOOLEAN)`, `NOT(FALSE)`, `UNARY PLUS(2)`, `NULL`
  for `NULL::INT`), where each was a syntax error; a text value meets a TIMESTAMP_NTZ column as the timestamp it
  converts to, where it matched no row. A COPY naming a STORAGE_INTEGRATION judges an ENCRYPTION group whose TYPE
  is NONE beside another parameter before the integration: `Incompatible number of parameters for value [TYPE]`.

- **Dynamic tables under another kind's ALTER; quoted names in creation sentences.** ALTER TABLE sets and unsets a
  dynamic table's comment, gives and drops its clustering key and suspends and resumes its reclustering, and refuses
  a rename of a column the key reads (`Cannot rename column 'A' which belongs to a clustering key`) and every other
  action by kind (`Object found is of type 'DYNAMIC_TABLE', not specified type 'TABLE'.`), where each failed as a
  missing table; ALTER VIEW and ALTER MATERIALIZED VIEW refuse a dynamic table by its kind, and ALTER DYNAMIC TABLE
  renames a view and refuses it every other action. A dynamic table whose source is renamed or dropped answers the
  rows of its last refresh, where reading it failed. The sentence a CREATE answers spells a materialized view's and a
  dynamic table's quoted name as a view's — `Dynamic table "ldt" successfully created.` — where it printed it bare.

- **An outer aggregate in a derived table; future grants on more kinds; a user's grant option.** An aggregate over the
  outer row alone in a derived table's select list is the outer query's value: where nothing reads it the derived
  table's rows are its FROM's (`(SELECT COUNT(*) FROM (SELECT MAX(fz.id) AS x FROM g))` counts g's rows), and a MIN
  or a MAX of a number or a date column read inside the select's own aggregates is the outer table's extreme over the
  whole table — the outer query's WHERE, grouping and joins ignored — where each failed as an invalid identifier or
  a grouping error. A future grant on materialized views, dynamic tables, event tables or file formats is applied
  to each one created afterwards, and a privilege granted to a user WITH GRANT OPTION keeps the option in SHOW
  GRANTS TO USER, REVOKE GRANT OPTION FOR taking it back.

- **Iceberg tables in GET_DDL, and their FLOAT columns.** GET_DDL spells an Iceberg table `create or replace ICEBERG
  TABLE`, its columns in the format's names — INT, LONG, DECIMAL(10, 2), FLOAT, DOUBLE, STRING, BINARY, MAP(STRING,
  INT) — and its EXTERNAL_VOLUME, ICEBERG_VERSION and CATALOG after the column list, a clone's BASE_LOCATION too, where
  it printed an ordinary table's DDL; a column a query or LIKE gave the table keeps a table's names. A FLOAT, FLOAT4 or
  FLOAT8 column of an Iceberg table holds a 32-bit value — 0.1 reads back as 0.1000000015, 1e300 as an infinity — and
  DOUBLE, DOUBLE PRECISION and REAL a 64-bit one. A refused type change names an Iceberg column's types as the format
  does (`from type int to string`). `'…'::TIMESTAMP WITH LOCAL TIME ZONE` is a TIMESTAMP_LTZ, where it was a NULL of
  no type.

- **A membership over a NULL the row never changes; ownership granted to a database role.** `x IN (SELECT …)` over
  a derived relation's NULL constant — `(SELECT NULL AS x)`, `CAST(NULL AS INT)`, through a LIMIT or a join — is
  UNKNOWN whatever the query answers, where it answered FALSE, and a NOT IN over an aggregate filtered by an inequality
  is answered where it was refused. GRANT OWNERSHIP … TO DATABASE ROLE makes the database role the owner: the role
  that owned the object is refused its DROP and RENAME, and a session acts as the owner while it holds the database
  role, where the grant was recorded as a privilege and the owner stayed.

- **LIKE ANY over one ROW, and TABLE(x!y()).** `'a' LIKE ANY (('a', 'b'))` matches 'a' and 'b' as `'a' LIKE ANY
  ('a', 'b')` does, in as many parentheses as it is written, where it read the ROW as one pattern and answered FALSE;
  `SELECT * FROM TABLE(x!y())` is "Unknown table function X!Y" (an instance written with its schema a user-defined
  one) instead of a syntax error at the `!`.

- **A key that is a condition.** An ORDER BY, a GROUP BY, a window's PARTITION BY and ORDER BY, a WITHIN GROUP's
  ORDER BY, a ROLLUP's, a CUBE's and a grouping set's member and a MATCH_RECOGNIZE's partition take NOT, AND and OR as
  a select item does — `ORDER BY NOT b, id`, `GROUP BY b AND b`, `OVER (PARTITION BY NOT b ORDER BY id)` — and `!` as
  the account reads it: before EXISTS a negation, before anything else `Unknown function "!".`. Each used to be a
  syntax error. A grouped query's ORDER BY key that negates an EXISTS is refused naming `NOT EXISTS(…)` whole, and a
  select item that is a condition beside a window call (`SELECT NOT b, ROW_NUMBER() OVER (…)`) is answered instead of
  failing on an internal null.
- **A dynamic table read or streamed AT or BEFORE a point after its first refresh.** `SELECT … FROM dt AT | BEFORE
  (…)` reads the rows of the last refresh before the point, where it used to ignore the clause and read the current
  rows — and took even the statement that created the table, which is now refused with `Statement <id> cannot be
  used to specify time for time travel query.` A stream `ON DYNAMIC TABLE dt AT | BEFORE (…)` starts from that
  refresh with every later refresh's changes pending, where a point after the first refresh used to be refused as
  unsupported. An `ALTER DYNAMIC TABLE … REFRESH` statement can be named as the point and stands for the moment it
  began, for AT as for BEFORE. A CHANGES clause on a dynamic table is refused as unsupported instead of ignored.
- **The session's WEEK_START and WEEK_OF_YEAR_POLICY make the week.** DAYOFWEEK (and DATE_PART / EXTRACT's
  dayofweek, dow and weekday), WEEK and WEEKOFYEAR, YEAROFWEEK, DATE_TRUNC('WEEK'), LAST_DAY(…, 'WEEK') and
  DATEDIFF(WEEK, …) follow them: `WEEK_START = 5` makes Friday the first day, so DAYOFWEEK of a Wednesday is 6, and
  `WEEK_OF_YEAR_POLICY = 1` makes the week holding January 1st week 1. The ISO variants, NEXT_DAY and PREVIOUS_DAY
  do not. They used to read the default week whatever the session set.
- **A quoted role name is that name alone.** `CREATE ROLE "low"` keeps the name as written — SHOW ROLES, CURRENT_ROLE()
  and the grant listings spell it `low`, and `"LOW"` or `LOW` is another role — and a quoted name reaches only the role
  of exactly that name in USE ROLE, USE SECONDARY ROLES, GRANT, REVOKE, SHOW GRANTS, GRANT OWNERSHIP and DROP:
  `USE ROLE "r"` over a role `R` is `Object does not exist, or operation cannot be performed.` The engine's Java
  callers still name a role in any case.
- **Policy listings page as the account pages them.** `SHOW PASSWORD POLICIES … LIMIT n FROM 'name'` is read; on
  the masking, row access and password policy listings the FROM name is read and starts nothing, so every row up to
  the limit is listed, in a schema or across a database. A network rule value that holds commas counts as that many
  entries in SHOW NETWORK RULES.
- **A dynamic table refreshes on schedule, and a refresh initializes what it reads.** A running task scheduler
  refreshes a dynamic table once its target lag has passed since its last refresh, or since it was created for one that
  never refreshed; a suspended table and a DOWNSTREAM one wait. A refresh, the first one at CREATE included, refreshes
  a dynamic table it reads that never refreshed first, so a DOWNSTREAM table initializes with the table that reads it.
- **EXECUTE TASK … RETRY LAST is one more attempt of its graph run.** COMPLETE_TASK_GRAPHS answers a row per attempt,
  each retry scheduled from MANUAL RETRY under the graph run's RUN_ID and GRAPH_RUN_GROUP_ID; TASK_HISTORY numbers
  every run of a retry with its attempt, a dependent the retry reaches for the first time too; and a retry of a graph
  run whose last attempt failed nowhere names that attempt. Attempts are kept across a restart.
- **Listings and REST bodies of tables and dynamic tables.** SHOW OBJECTS counts the rows of a materialized view;
  SHOW PARAMETERS IN TABLE tells a DATA_METRIC_SCHEDULE set on the table; SHOW TABLES tells the retention a dynamic
  table set itself. A REST table body carries `max_data_extension_time_in_days`, `default_ddl_collation` and
  `data_metric_schedule`, a dynamic table's its retention, `initialize`, `log_level` and `row_timestamp`;
  `cluster_by_raw` is the key list as written and `cluster_by` names an identifier key in upper case; a
  suspend-recluster of a table with no clustering key answers 401 with code 003205.
- **A quoted tag name is that name alone.** `DROP TAG "cc"`, `CREATE OR REPLACE TAG "cc"`, `ALTER TAG "cc"`, a
  tag set on or unset from an object, and a tag a grant or an ownership transfer names no longer fall back to the one
  tag `CC` that matches ignoring case: a quoted name that names no tag is refused as missing, and CREATE OR REPLACE
  creates the tag beside its upper-case namesake.
- **A secret's API_AUTHENTICATION is looked up first, and ALTER SECRET takes none.** CREATE SECRET of a type that
  takes no integration names the integration's miss (`Integration 'X' does not exist or not authorized.`) or its kind
  (`Integration 'X' is not of type API Authentication.`) before the type refuses the property, and `ALTER SECRET ...
  SET API_AUTHENTICATION` is refused with `invalid property 'API_AUTHENTICATION' for 'SECRET'`.
- **A new session starts in its user's DEFAULT_WAREHOUSE.** An HTTP session or a direct JDBC connection whose user's
  DEFAULT_WAREHOUSE names a warehouse starts with that warehouse current, otherwise in the engine's current warehouse
  as before. An ALTER USER reaches the sessions that start after it.
- **SHOW PARAMETERS lists the account's current names and defaults.** IN ACCOUNT lists three parameters the account
  added and no longer lists the session's retired TELEMETRY_ENABLE_INTERACTIVE_MODE. The session, user and task
  lists gain JS_QUERY_RESULT_FORMAT. AI_SQL_EMBED_SIMILARITY_ERROR_HANDLING_USE_FAIL_ON_ERROR now defaults to false
  and DYNAMIC_TABLES_VIEW_VERSION to 13. A warehouse lists ENABLE_USE_STABLE_PATH and FALLBACK_WAREHOUSE, and a
  database no longer lists ENABLE_ICEBERG_MANAGED_TABLE_REPLICATION.
- **POLICY_REFERENCES and TAG_REFERENCES refuse a missing object as any statement does.**
  - POLICY_REFERENCES ends a missing policy, table or view with the privilege sentence, and names a missing database
    or schema first.
  - Its REF_ENTITY_DOMAIN reads as the account reads it: `Unknown domain: X.` for a domain no reference function
    knows, the invalid-value sentence on one line for one it does not take, and TAG, USER and ACCOUNT objects
    looked up.
  - TAG_REFERENCES and SYSTEM$GET_TAG refuse an object of a kind this engine models none of by that kind's noun:
    a share, an account, a replication or failover group, an instance, a backup policy or set, an agent, a Cortex
    Search Service, Snowflake Intelligence. They used to answer no rows.
- **CREATE EXTERNAL FUNCTION and CREATE GIT REPOSITORY are read.** Both were syntax errors.
  - An external function's schema, API_INTEGRATION and integration are judged as the account judges them. One that
    passes is kept, and SHOW FUNCTIONS lists it with `is_external_function` Y and language EXTERNAL. A call of it is
    refused, as no remote service is reached.
  - A Git repository's ORIGIN and API_INTEGRATION are required and its integration judged. One that passes is
    refused, as its ORIGIN is not cloned.
- **A stage's copy options are judged.** `COPY_OPTIONS = (…)` of CREATE and ALTER STAGE and `STAGE_COPY_OPTIONS =
  (…)` of CREATE and ALTER TABLE took any name and any value. They are now judged in the order written: a name a stage
  does not take is `invalid copy option 'NOPE'`, a value its option does not take is `invalid value ['x'] for
  parameter 'PURGE'`, and an option given again with another value is `conflicting values for copy option 'PURGE'`.
  ALTER STAGE … SET COPY_OPTIONS now replaces the stage's whole set, and ALTER TABLE … SET STAGE_COPY_OPTIONS merges
  into the table's, which it used to ignore. A refused CREATE OR REPLACE STAGE leaves the stage it would replace. DESC
  lists each value as the account spells it, MATCH_BY_COLUMN_NAME and LOAD_MODE once set.
- **RELY belongs to each key.** It was kept on a key's columns, so two keys over one column shared it. Each key now
  keeps its own in GET_DDL, SHOW PRIMARY | UNIQUE KEYS and INFORMATION_SCHEMA.TABLE_CONSTRAINTS, and ALTER … CONSTRAINT
  … RELY sets the key named alone. A column's own UNIQUE is now a key beside a table-level UNIQUE over that column. A
  primary key takes the place of a UNIQUE over its columns in its order, and ALTER TABLE … ADD UNIQUE over a key the
  table has is refused. A key ADD COLUMN declares keeps its CONSTRAINT name. CREATE TABLE … LIKE, CLONE, an engine copy
  and a restart keep the primary key's order.
- **SHOW GRANTS ON finds a materialized view, a dynamic table and an event table.** Under TABLE, VIEW or a bare name it
  listed nothing for a materialized view and refused a dynamic table. It now lists each under its own kind. MATERIALIZED
  VIEW, DYNAMIC TABLE, EVENT TABLE, EXTERNAL TABLE and ICEBERG TABLE are read as relation kinds too, and a missing one
  is refused in that kind's words.
- **Names that differ only in case are two objects for views, sequences, file formats and stages.** CREATE OR
  REPLACE replaced an object whose name differed only in case, CREATE FILE FORMAT refused one as already existing,
  and a quoted name in another case reached the object through FROM, NEXTVAL, DROP, DESCRIBE SEQUENCE and ALTER. Each
  now matches the name exactly: an unquoted name folds to upper case, and a quoted one is exact. A view's creation
  message quotes a name that must be quoted (`View "v775" successfully created.`).
- **GRANT reads a share, an application role and a bare ROLE as grantees.** `GRANT … TO SHARE s` and `TO APPLICATION
  ROLE r` were syntax errors, and so was `TO ROLE` with no name. They are now refused for the grantee, as the account
  refuses one that is not there (this engine holds no share or application role), once the object is found, as for
  any grantee. ROLE alone names a role called ROLE.
- **A table's COPY load history follows ALTER TABLE … RENAME and a move to another schema.** The renamed table loaded
  the files of its own stage a second time. It now skips them, as the files moved with it.
- **ALTER SCHEMA and ALTER DATABASE take a list of properties.** One SET may now name several properties,
  separated by spaces or commas and with COMMENT among them, and one UNSET may name a comma list. Before, a second
  property was a syntax error. A property given twice is refused (`duplicate property '…';`). ALTER SCHEMA now
  refuses a name a schema does not take (`invalid property 'FOO' for 'SCHEMA'`), as ALTER DATABASE already did.
- **SHOW GRANTS TO ROLE lists what the role owns.** Every object whose owner is the role now gets an OWNERSHIP row,
  with the grant option, granted by the role itself. This covers databases, schemas, relations, sequences, stages,
  file formats, pipes, streams, tasks, and functions and procedures named with their argument types.
- **A row keeps its METADATA$ROW_ID across an engine copy and a restart.** Snapshots and checkpoints keep each row's
  identity and the engine's next one. After a restore, a stream folds a later update of a row it already holds into
  the change it holds, and a new row takes an id no row had before.
- **A CHANGES clause over a view reads the view's changes.** It used to be ignored: the view was read as it is
  now, and the METADATA$ columns were invalid identifiers. The clause now reads the changes of the tables the view
  reads, replayed through the view's filter, projection and UNION ALL branches, with the view-stream metadata
  columns. It starts from any stream's offset (a stream on the view or on a table it reads) or from a time point.
  A view whose change tracking is off is refused as such a table is, and a point that reads a moving function is
  refused naming the view. Views now track changes: a stream created on the view or CHANGE_TRACKING = TRUE turns
  tracking on, it stays on after the stream is dropped, and SHOW VIEWS reports it.
- **SHOW COLUMNS lists a stream's columns.** `SHOW COLUMNS IN TABLE <stream>` reported the table missing. It now
  lists the stream's source columns under the source's name, then METADATA$ISUPDATE, METADATA$ACTION and
  METADATA$ROW_ID with their comments. This works for streams on a table and on a view, and for append-only streams.
- **A UUID is typed as the account types it beside numbers and text.** The numeric functions and the arithmetic
  operators refuse a UUID at compile time: `ABS(u)` is `Invalid argument types for function 'ABS': (UUID)`. Before,
  each row failed with a numeric conversion error, or ZEROIFNULL typed it FLOAT. A string function reads a UUID as a
  text nothing bounds: `u || 'x'` and `UPPER(u)` are `VARCHAR(134217728)`. GREATEST and LEAST refuse a number beside
  a UUID wherever the call stands, not only when they are the selected value.
- **The JDBC temporal getters read what the account's driver reads and refuse the rest with an SQLException.**
  getTime on a TIMESTAMP_LTZ, getTimestamp or getDate on a TIME, and getTime on a DATE threw an unchecked
  `DateTimeParseException`, in process and over HTTP. getTime now reads every timestamp's wall clock and keeps the
  milliseconds, and getTimestamp reads a TIME on 1970-01-01. A getter the column's type does not convert to is
  refused as the account's driver refuses it: SQLSTATE `0A000`, code 200038, `Cannot convert value in the driver
  from type:91 to type:time, value=19724.` That covers getDate on a TIME, getTime on a DATE, and every temporal getter
  on a text, number, BOOLEAN or VARIANT column, including text that spells a date, which was parsed before. In
  process, the three getters now set wasNull on a NULL.
- **Creating a SQL procedure no longer parses its body twice with full prediction.** The check for what the account
  refuses in a body (a DROP's or a DESCRIBE's signature, a call in a FROM form) re-read the whole body with LL
  prediction, which tripled the cost of CREATE PROCEDURE on a large body. It now uses the quick SLL stage first, as
  every other parse does, and falls back to LL only for a body that stage gives up on. The answers are unchanged.
- **The newer date and time format elements are read and written.** H24, H12, MO, ME, S, P, UUUU and FF0 were
  printed as literal text or split wrongly by TO_CHAR (17:08 as `1724`), and failed to parse in TO_DATE,
  TO_TIMESTAMP, TO_TIME and their TRY_ forms. They now render the unpadded hour, month, minute and second, the
  meridiem's letter, the year and no fraction digits, and parse the same way. `Y` prints the year's last two digits
  unpadded, so `YYY` is `YY` then `Y`.
- **HASH_AGG has a static type.** Its column was declared VARCHAR and SYSTEM$TYPEOF said `NULL[LOB]`. It is now
  `NUMBER(19,0)`, as HASH is, and a CTAS over it stores that type.
- **The array and object functions keep a structured type.** ARRAY_CAT, ARRAY_APPEND, ARRAY_SLICE, ARRAY_COMPACT,
  ARRAY_EXCEPT, OBJECT_DELETE, OBJECT_INSERT, OBJECT_PICK and OBJECT_KEYS over a structured value answered a plain
  ARRAY or OBJECT. They now answer the structured type the account declares, and OBJECT_PICK answers the picked
  fields in the order picked.
- **A star-argument aggregate reads an unaliased VALUES list or derived table.** `HASH_AGG(*)`, `ARRAY_AGG(*)`,
  `OBJECT_AGG(*)` and `COUNT(* EXCLUDE …)` over such a source failed with a ParseCancellationException carrying no
  message, grouped or not. They now read its columns as the written-out call does.
- **The numeric aggregates refuse a VECTOR.** SUM, AVG, MEDIAN, the percentiles, the deviations and variances,
  KURTOSIS, SKEW, MIN and MAX accepted a VECTOR and answered 0.0 (MIN and MAX answered one of the vectors). They now
  refuse it as the statement compiles, each in the words the account uses. An AVG over an ordered window is now
  refused in SUM's name, as an ungrouped AVG is.
- **A cast to a VECTOR refuses an element too wide for its type.** An element past float32's greatest value was stored
  as an infinity, and one past 32 bits in a `VECTOR(INT, n)` was wrapped. Both are now refused as elements that
  "overflow". A NaN or an infinite FLOAT element is kept and reads as `"NaN"` or `"Infinity"` instead of failing
  every read of its row. A refused vector cast keeps its own sentence when it stops an INSERT.
- **A grouped COUNT, SUM or AVG skips a VARIANT JSON null.** Under GROUP BY over a plain column, COUNT and
  COUNT(DISTINCT) counted a JSON null, and SUM and AVG failed with "Failed to cast variant value null to REAL". They
  now read it as missing input, as the ungrouped and windowed aggregates do.
- **BITSHIFTLEFT and BITSHIFTRIGHT shift the whole 128-bit operand.** A first operand of 2^63 or more was cut to its
  low 64 bits before the shift, so a left shift undone by a right one gave 0, and shifting 2^63 right flipped its sign.
- **FACTORIAL, CHR and the FROM_PARTS functions read NaN, infinite and wide arguments as the account does.** A FLOAT
  NaN or infinity leaked Java's NumberFormatException text; it is now refused as "Number out of representable range".
  A date or time part past 32 bits, and FACTORIAL's argument past 64 bits, were cut to their low bits; they are now
  refused. A nanosecond part is read in 64 bits, and CHR reads the low 32 bits of its code.
- **A read of a structured value has the type the value declares.** An element of a structured ARRAY, a field of a
  structured OBJECT and a value of a MAP - read by subscript, by path, by GET, GET_IGNORE_CASE or GET_PATH, or as the
  VALUE of a FLATTEN that is not RECURSIVE - was a VARIANT, so arithmetic over it ran in floating point and lost
  digits, a CTAS declared VARIANT columns, and a DATE element could not be shifted. It now has the declared type, its
  arithmetic is exact, and SYSTEM$TYPEOF tags it as the account does; a field the OBJECT does not declare is refused.
  GET_IGNORE_CASE keeps every digit of a whole number past 64 bits.
- **A copy of an engine keeps the files of its internal stages.** An engine clone and a checkpoint's restore had every
  named, table and user stage empty, so a COPY from one loaded nothing. The copy now starts with copies of the files,
  and an UNDROP of a dropped schema or database brings its stages' files back.
- **A copy of an engine keeps more of its objects' metadata.** A view's, a materialized view's and a dynamic table's
  column collations, a literal DEFAULT as the value it is (a number no longer comes back as text), a dynamic table's
  last refresh - its data_timestamp, and what its next refresh inserts - and the creation times of the system roles,
  INFORMATION_SCHEMA and the SNOWFLAKE database. A write-ahead log's replay of a CREATE STAGE with a directory table
  no longer registers files that the statements after it staged.
- **A copy of an engine keeps each table's time-travel history.** An engine clone, a checkpoint's restore and the
  checkpoint a write-ahead log recovers from kept only each table's current rows, so the copy refused AT and BEFORE
  points from before it was made, and a logged write that read such a point - after an EXECUTE TASK, which takes a
  checkpoint, say - failed on replay and was lost. A snapshot now keeps each table's history with its rows, row
  identities included, so an AT or BEFORE point and a CHANGES clause read the copy as they read the engine it copies.
- **A transaction's writes follow a table another session renames or swaps.** Another session's ALTER TABLE ...
  RENAME TO or SWAP WITH, and a rename of the table's schema or database, run at once beside a transaction that has
  written the table, as they do on a real account; but the transaction's buffered changes kept the old name, so its
  COMMIT reported success and dropped an UPDATE, or put an INSERT into the table swapped in. The changes now follow
  the table to its new name.
- **A statement waiting for another session's table lock can be stopped.** Its session's
  STATEMENT_TIMEOUT_IN_SECONDS, SYSTEM$CANCEL_QUERY, SYSTEM$ABORT_SESSION and an engine shutdown left it waiting
  until the holder's transaction ended, and the statement of an aborted session then committed its write. The wait
  now ends as a running statement does, and an aborted statement writes nothing.
- **Write-ahead log recovery replays each session in a session of its own.** The log kept statement texts and
  instants only, and recovery ran them all as one session, so after a restart one session's USE, SET, ALTER SESSION
  or USE ROLE applied to another session's statements: a row went into another schema's table or was lost, values
  came from another session's variables and time zone, and new tables were owned by another session's role. Each
  logged statement now carries its session, user, roles, database and schema (log format v4), and recovery puts them
  back statement by statement; a log of an earlier version is upgraded when it is opened.
- **A replayed statement draws what it first drew.** RANDOM(), UNIFORM over it, UUID_STRING() - also as a column
  DEFAULT - and the rows SAMPLE picks came out different on every restart; they now come from a stream the
  statement's recorded instant seeds. IDENTITY and sequence values a statement drew are logged with it and handed
  out again on replay, so a transaction that committed after another session's insert keeps its ids and the counter
  goes on past both.
- **Queries that change durable state are logged.** A SELECT of a sequence's NEXTVAL, a TO_QUERY that may run a CALL
  and SYSTEM$TASK_DEPENDENTS_ENABLE were left out of the write-ahead log, so a restart reissued sequence values and
  suspended the task graph again; they are logged. A statement reading the query its session ran just before by
  RESULT_SCAN has that query logged ahead of it, so the replay reads the same rows instead of failing.
- **A write-ahead log compacted at a checkpoint keeps its records readable.** The compaction wrote the records it
  kept without their instants under a header that promised them.
- **CREATE MASKING POLICY takes EXEMPT_OTHER_POLICIES.** `EXEMPT_OTHER_POLICIES = TRUE | FALSE` was a syntax error;
  a masking or a row access policy takes it beside its COMMENT, in either order, and GET_DDL and SHOW ... POLICIES
  report it. A row access policy or a conditional masking policy may read a column masked by an exempt policy, in
  whichever order the two are attached; masking a column another policy already reads is refused as the account
  refuses it (`Column 'S' cannot be masked because it is used as argument by one or more masking/row-access
  policies.`), so the rule no longer depends on the order of the statements. A repeated property, a value other
  than TRUE or FALSE, and the property on a projection or an aggregation policy are refused with the account's
  sentences.
- **GET_DDL of a task writes its session parameters.** TIMEZONE, QUERY_TAG and every other session parameter set on
  a task were left out, so replaying the text ran the task under the account's settings; each is written on its own
  line.
- **A table's DEFAULT_DDL_COLLATION reaches its columns.** A string column created by CREATE TABLE or ADD COLUMN
  without a collation of its own now takes the DEFAULT_DDL_COLLATION of its table, schema or database, as the
  account gives it - in its comparisons, DESCRIBE and GET_DDL alike.
- **GET_DDL of a materialized view keeps its column comments,** as a view's DDL does.
- **A foreign key stays on the table it was declared against.** Renaming, moving or swapping the parent table, or
  renaming or swapping its schema or database, left the key naming the old name: SHOW IMPORTED KEYS,
  REFERENTIAL_CONSTRAINTS and GET_DDL named a missing or wrong table, `DROP ... RESTRICT` dropped the real parent
  and refused one nothing referenced, a new table taking the old name became the parent, and a hybrid child checked
  its rows against the wrong table. The key now follows its parent under the new name, as streams and grants do.
- **A hybrid table's column-level FOREIGN KEY is enforced.** `col INT REFERENCES p (id)`, with or without the
  FOREIGN KEY words or a constraint name, let an orphan row in where the same key declared on the table refused it;
  both are now checked on INSERT, the referenced column defaulting to the parent's primary key.
- **The correlated-body refusal judges every call of a SQL function.** A call passing a row's value by name -
  `nw(b => 1, a => o.k)` - or mixing positional and named arguments, and a call of a function whose body is an
  expression holding a correlated subquery - `(SELECT a + 1 WHERE a > 0) * 10` - answered where the positional
  call of a query body is refused; each is refused the same way, the expression body naming its own function. A
  body's other parameters, given constants, no longer leave it unjudged: `nd(1, o.k)` over `SELECT a + b WHERE
  a > 0` is refused as `nd(o.k, 1)` is.
- **SHOW lists only the objects the role sees.** SHOW TABLES, VIEWS, MATERIALIZED VIEWS, OBJECTS, COLUMNS,
  SEQUENCES, STREAMS, TASKS, PIPES and DYNAMIC TABLES listed every object of their scope to any role - row counts
  and a view's whole `text` among them; each lists only what the role owns or holds a privilege on, as
  INFORMATION_SCHEMA does, while a role holding MANAGE GRANTS - ACCOUNTADMIN and SECURITYADMIN from the start - still
  sees every object.
- **DESCRIBE and GET_DDL answer an object the role does not see as one that does not exist.** DESCRIBE TABLE and
  DESCRIBE VIEW, SHOW COLUMNS IN a named relation, and GET_DDL of a table, a view, a materialized view, a dynamic
  table, a sequence, a stream, a task or a pipe gave the columns or the DDL to a role holding no privilege on the
  object; each is refused as a missing name is, `Table 'SECRET' does not exist or not authorized.`
- **A SQL function that hands its own parameter to a query-bodied SQL function is judged by what its call passes.**
  A wrapper such as `SELECT sq_where(a) * 10` was refused on every call, constants included, and one whose body is
  an expression could not be created; over a constant each answers again, while a call that passes a row's value is
  refused naming the wrapper where its body is a query, and the function it calls where its body is an expression.
- **An unload of a table needs SELECT on it.** `COPY INTO @stage FROM <table>` read and wrote a table the role holds
  no SELECT on - the user's stage always takes the files - where its query form `FROM (SELECT * FROM <table>)` was
  refused; it is refused the same way.
- **A privilege on an object reaches it only through USAGE on its database and schema.** A role granted SELECT, or
  any other privilege, on an object of a schema it holds no USAGE on - or whose database it holds no USAGE on - is
  refused as for a container that is not there, `Database 'P' does not exist or not authorized.` first, then the
  schema's; owning the container or the object reaches it as before.
- **A query that reads the outer row through a common table expression is judged as the query it passes on.** A SQL
  function whose body reads its parameter in a WITH clause - `WITH d AS (SELECT p AS v) SELECT v FROM d`, or a
  definition that counts or aggregates the rows matching the parameter - called over a column is answered again, as
  is a correlated scalar subquery of that shape, where both were refused as unsupported subqueries; a filter or a
  grouping of the outer value and two such definitions read together stay refused.
- **CASE, WHEN, CAST, DEFAULT, CONSTRAINT, JOIN, INNER and CROSS name arguments and script variables.** Each names a
  function's, a procedure's and a masking or row access policy's argument, a call's named argument
  (`f(when => 'a', case => 2)`), and a script's variable, cursor or RESULTSET as LET, FOR (`FOR inner IN 1 TO 3`),
  OPEN, INTO and a bind (`:join`) name it, where each was a syntax error at the word; DEFAULT and CONSTRAINT are
  assigned too. Where a value is read CASE, CAST and WHEN still lead their own constructs, and an unquoted DEFAULT
  names nothing.
- **An ORDER BY or GROUP BY key written DEFAULT reads nothing.** `SELECT 1 AS default ORDER BY default` and
  `SELECT 1 AS d GROUP BY default` are refused `invalid identifier 'DEFAULT'` at the key, where the first sorted by
  the item aliased DEFAULT and the second answered.
- **FOR UPDATE is judged by what the query reads.** A query that reads a table only through a derived table, a common
  table expression or a set operation's arm is refused `Unsupported feature 'FOR UPDATE clause'.`, and one whose FROM
  reads VALUES or a derived table of no table `A 'FOR UPDATE' clause is not compatible with 'not selecting from a
  table'.`, where each got the other sentence. A RESULTSET's and a cursor's query take the clause, refused when the
  RESULTSET is filled or the cursor opens, where both were syntax errors.
- **A SHOW listing's scope names its schema and its database exactly.** `SHOW TABLES IN SCHEMA "ss"` and
  `SHOW SEQUENCES IN DATABASE "test_db"` no longer list the schema SS or the database TEST_DB: they are refused as
  for a scope that names nothing, in the generic sentence or naming the schema or the database, kind by kind.
  `SHOW COLUMNS IN SCHEMA "ss"` names `ss` in its full-path sentence, and `SHOW COLUMNS IN DATABASE "test_db"` quotes
  the database it misses.
- **A staged-file query reads its files by the format their stage keeps.** A query that writes no FILE_FORMAT reads
  a named stage's files by its FILE_FORMAT, inline or naming a format, SKIP_HEADER and NULL_IF included, and a table
  stage's by its STAGE_FILE_FORMAT, where every stage was read as default CSV; `FILE_FORMAT => NULL` reads them by
  the default.
- **The array functions read their arguments on the row as the account does.** A VARIANT that holds no array is
  read as a one-element array by ARRAY_SLICE, ARRAY_CONTAINS, ARRAY_POSITION, ARRAY_APPEND, ARRAY_PREPEND,
  ARRAY_DISTINCT, ARRAY_INSERT, ARRAY_REMOVE, ARRAY_REVERSE, ARRAY_SORT, ARRAYS_OVERLAP, ARRAY_INTERSECTION,
  ARRAY_EXCEPT, ARRAY_MAX and ARRAY_MIN, where they answered NULL or refused. A text or VARIANT position of
  ARRAY_INSERT, ARRAY_REMOVE_AT and ARRAY_GENERATE_RANGE is read as the number it holds, where it was refused as an
  invalid argument type. A VARIANT text that spells no number is `Failed to cast variant value "abc" to FIXED`, and
  ARRAY_SORT's flags convert as TO_BOOLEAN does. GET over an array answers NULL for a text or VARIANT index, and
  refuses a BINARY, OBJECT or ARRAY index while the statement compiles.
- **A SQL function whose query body reads its parameter in a WHERE is judged where the call reads the row.**
  `SELECT a + 1 WHERE a > 0` called over a column is refused with `Unsupported subquery type cannot be evaluated
  inside Function object: SQ_WHERE`, as the correlated subquery it expands into; over a constant it answers as
  before.
- **A stream whose table was replaced shows it as the account does.** SHOW STREAMS lists `No privilege or table
  dropped` for a table stream's table and base tables, and `1 inaccessible table(s)` for a view stream's. It names a
  healthy view stream's base tables rather than its view, and gives a stale stream no `stale_after`. A read of the
  table stream is `Base table '…' dropped, cannot read from stream 'ST' in line 1 position 14.` (no compilation
  error), and of the view stream `Change tracking is not enabled or has been missing for the time range requested on
  table '…'.` SYSTEM$STREAM_HAS_DATA refuses such a stream, and GET_DDL quotes its table's full name.
- **A collation is settled over every relation that feeds a column.** A UNION BY NAME gave its columns no collation
  at all: each column's collation now folds over the arms' columns of its name, so `UNION BY NAME` deduplicates
  under it and two that differ are refused with `Incompatible collations: 'en-cs' and 'en-ci'`. A recursive CTE kept
  its anchor's collation; it now settles the anchor with its recursive terms, a COLLATE in a term outranking the
  anchor's column. `c = ANY (SELECT d …)` over two columns' collations is refused as `IN` is, where it used to
  compare under the left side's alone.
- **A user-defined table function after LATERAL without TABLE( … ) is resolved and judged.** A call whose arguments
  did not match its parameters, or that named them, was not found and the query failed with `invalid identifier`
  on its select list; it is now matched as inside TABLE( … ), refused with `Invalid argument types for function
  'PTF': (NUMBER(10,2))` at the call, and a name no table function carries is `Unknown table function NOSUCH_TF`
  (a quoted name keeps its quotes). A JavaScript, Python, Java or Scala table function's arguments are judged by
  type as a SQL one's are, where any arguments used to reach the handler.
- **A called SQL function's GET_DDL is judged while the statement compiles.** A function whose body names a missing
  object in GET_DDL was refused only when a row ran it; the object is now looked up wherever the call stands, under
  SYSTEM$TYPEOF, in a branch never taken and over no row. GET_DDL's constant-arguments refusal prints CURRENT_DATE
  as the day it answers, `[CAST('2026-10-14' AS VARCHAR(134217728))]`, as the account's plan does.
- **A correlated EXISTS, NOT EXISTS or scalar aggregate over one equality is computed once for every key.** Such a
  subquery ran in full for each outer row, so its cost grew with the product of its relations: over 10,000 rows on
  each side EXISTS took 9.3 s, NOT EXISTS 9.3 s and `(SELECT MAX(k) FROM b WHERE b.id = a.id)` 10.8 s, where they
  now take 18, 22 and 39 ms. A subquery that reads the outer row only through one `inner = outer` conjunct of its
  WHERE has its inner keys, or its aggregate by key, computed once per statement and probed per row; any other
  shape, a key of a kind hashing does not compare, and a collated key still run the subquery for each row. The
  answers are unchanged.
- **SEQ1, SEQ2, SEQ4 and SEQ8 number the rows a GROUP BY reads.** A key built from them read 0 for every row, so
  `SELECT SEQ4() % 3 AS b, COUNT(*) … GROUP BY b` put every row in one group; the keys now number the rows the
  grouping reads, an item shows the key its group was formed by (or, written out again, reads its group's first
  row), and an aggregated SEQ, in the select list or a HAVING, numbers each row by its place in that scan rather
  than in its group: `SUM(SEQ4())` over the groups of `i % 3` in 0..8 is 9, 12 and 15.
- **An ORDER BY position over a star sorts by the column at that position.** Over relations sharing a column name,
  `SELECT * FROM a JOIN b ON … ORDER BY 3` sorted by the first column of that name, A.X, instead of the third
  output column, B.X; a position counting to a star's column now reads that column's own slot, through USING, a
  comma join, DISTINCT, a derived table and qualified stars alike.
- **A WHERE computes only the select aliases it reads, and shows the value it tested.** Every aliased item was
  computed for every input row before the predicate ran, read or not, and computed again for the rows that passed:
  an aliased SQL UDF or correlated subquery cost 20 to 50 times the unaliased form, a sequence's NEXTVAL was drawn
  for every row the WHERE removed, and `WHERE r = 1` over `UNIFORM(1, 2, RANDOM()) AS r` showed rows with r = 2.
  Over a single relation the value the predicate read is now the value shown, through ORDER BY, LIMIT, DISTINCT,
  a window item and an ungrouped HAVING; over a join, or under a QUALIFY, the item is computed again for the
  projection, as the account does. A WHERE that reads an alias beside a LIMIT is no longer refused with `invalid
  identifier`, and an aliased item's scalar subquery raises what it raises ahead of the rows even when the WHERE
  keeps none.
- **A volatile GROUP BY key shows the value its group was formed by.** An item the key names by its alias or
  position, or under GROUP BY ALL, ROLLUP, CUBE and GROUPING SETS, was computed again for each group, so
  `SELECT UNIFORM(1, 50, RANDOM()) AS r, COUNT(*) … GROUP BY r` formed 50 groups showing 29 distinct keys. A key
  that writes the expression out again stays a call of its own, as on the account.
- **CONNECT BY walks a hierarchy as deep as its data goes.** A chain deeper than 1000 levels was refused as most
  likely cyclic; it is now walked to its end, and only a real cycle, a row reached again below itself on its own
  path from the root, is refused with `CONNECT BY reached a row again below itself: the hierarchy contains a
  cycle.`
- **PIVOT and UNPIVOT take their documented aliases.** `PIVOT (SUM(amt) [AS] total FOR …)` names each pivoted column
  after its value and the alias, `'JAN_TOTAL'`, and `UNPIVOT (… IN (jan [AS] january, …))` carries the alias in the
  name column; both were syntax errors. A pivot value written twice is refused with `The column ''JAN'' was specified
  multiple times for 'FROM'` and two equal value aliases with `Duplicate column alias 'X'.`, where the repeat's column
  used to stay NULL.
- **A set operation's arms meet as the account folds them.** A DATE arm beside a TIMESTAMP_LTZ one becomes its
  midnight; a number or a BOOLEAN before a VARIANT makes the column a VARIANT; a text before a VARIANT keeps its own
  type; `TRUE UNION ALL 'false'` is a BOOLEAN and `'false' UNION ALL TRUE` a VARCHAR(5); and a text or a date after a
  VARIANT, or a text after an ARRAY, is refused with `inconsistent data type for result columns for set operator
  input branches, expected VARCHAR(7), got VARIANT`.
- **TOP takes the counts LIMIT takes.** `TOP 2147483648` failed with a raw `NumberFormatException`; a count is now
  taken up to 2^62 - 1 in TOP, LIMIT, FETCH and OFFSET alike and refused past it with `Invalid row count '…' in limit
  clause` (`result offset` for an OFFSET), where LIMIT took counts up to 2^63 - 1.
- **UNPIVOT holds its columns to one type.** The value column took the type of the first listed column whatever
  the others were, so a CTAS stored 2.125 as 2.1 under NUMBER(5,1) and a 10-character text under VARCHAR(3). The
  listed columns are now read in the relation's column order and the first of another type is refused with `The
  type of column 'B' conflicts with the type of other columns in the UNPIVOT list.` (a NUMBER of another precision
  or scale included); texts of different widths join in the widest, and the name column is a VARCHAR of no width.
- **A CONNECT BY query keeps its columns' types.** SYSTEM$TYPEOF answered `NULL[LOB]` for every column and LEVEL,
  arithmetic over them was declared VARCHAR, and a CTAS rounded an AVG to an integer. The hierarchy now reads its
  table's declared types and tags, and LEVEL is a NUMBER(38,0).
- **A time-travel read is tagged by the version it reads.** Over a table that has shrunk since, a COUNT, MIN or
  MAX at an AT or BEFORE point was tagged by the table's current rows (`[SB1]` for 300 earlier rows); it is now
  tagged by the rows of that version, as a copy of them would be.
- **A seeded RANDOM answers a value per row.** `RANDOM(seed)` answered one value for every row, so a seeded
  `UNIFORM`, `NORMAL` or `RANDSTR` wrote one value into every generated row and `WHERE UNIFORM(0, 9, RANDOM(42)) < 5`
  kept no row or all of them. Each row now draws its own value from the seed, every call with that seed in one row
  agrees, and a statement run again draws the same values.
- **The rows the embedding API answers are the caller's own.** A SELECT that read a table's columns as they are
  handed out the stored rows themselves, so changing one through `Row.setValue` or `getValues()` rewrote the table,
  its time-travel versions and the cached result RESULT_SCAN reads. `DatabaseEngine.execute` and `executeQuery` now
  answer copies.
- **A quoted CTE name keeps its case.** A CTE named with a quoted identifier, `WITH "c" AS (…)`, could not be read by
  any spelling and failed as a missing table, while a quoted reference such as `"c"` read an unquoted CTE `c`. CTE
  names now match as identifiers do: `"c"` and `c` are two CTEs, `"C"` and `c` one. A CTE shadows a table of its
  name, the first of two CTEs of one name wins, and a CTE that reads itself under a name that is not all upper case
  is refused with `'r' is an invalid Recursive CTE`, as live refuses it.
- **A column list of the wrong length is refused.** A CTE's column list must name exactly the columns its query
  produces, even when the statement never reads the CTE, and a FROM item's alias list may rename fewer columns than
  the item has but not more: `Mismatch between number of columns produced by 'C' and the number of aliases
  specified.` Both used to be accepted with their names half applied.
- **A column named like a date part is held to the grouping.** A column called YEAR, MONTH, DAY or another
  date-part word escaped the grouped-query check inside any function call, so `ABS(year)`, `COALESCE(day, 0)` or
  `MAX(year) OVER ()` over an ungrouped column answered one row's value per group. The word is read as a unit only
  in a date function's unit slot (`DATEADD(day, …)`, `LAST_DAY(d, month)`); anywhere else it is the column, refused
  as live refuses it.
- **An aggregate and a window over aggregates combine in one expression.** Over grouped rows,
  `SUM(b) / SUM(SUM(b)) OVER ()`, `COUNT(*) * 100 / SUM(COUNT(*)) OVER ()` and `MAX(b) - LAG(MAX(b)) OVER (…)`
  were refused with `invalid identifier 'B'` or `Unknown function COUNT.`; they now answer, in the select list
  and in QUALIFY.
- **A correlated subquery beside a window reads its relation's alias.** Over one relation with an alias, a
  scalar, EXISTS or IN subquery correlated through the alias failed with `invalid identifier 'R1.O'` when the
  select list also held a window function.
- **The `(+)` outer-join marker works over any number of comma-separated relations.** It used to be honored only
  with exactly two: with a third relation every marked conjunct filtered like an inner join, so the documentation's
  departments, projects and employees example answered 2 rows instead of 4. Each marked relation now joins with a
  LEFT JOIN on its marked conjuncts once the relations they read have joined, a chain of markers being a chain of
  left joins, and a LATERAL item beside them reads the null-supplied rows. `SELECT *` and positional references
  read the relations in the order the FROM clause wrote them; a marked relation written first used to come last.
  A marker on a self-join's alias null-supplies that alias's own rows.
- **A join pairs every row its equality accepts.** An equi-join could miss rows a filter over the same equality
  kept: a text column beside a BOOLEAN, a DATE, a TIME or a timestamp (`b = 'true'`, `d = '2024-01-01'`), a number
  beside a BOOLEAN (`TRUE = 2` holds), -0.0 beside 0.0, and text under an accent- or space-insensitive collation;
  and a text that does not read as the number beside it (`'1a'`) was passed over where it is refused with
  `Numeric value '1a' is not recognized`. A bare USING
  key read by a later join (`a FULL JOIN b USING (k) JOIN c ON k = c.ck`) lost the rows whose left copy is NULL.
- **Each session keeps its own role.** A USE ROLE or USE SECONDARY ROLES in one session of an engine shared by
  several — the HTTP server's sessions, or direct JDBC connections to one engine — used to become every other
  session's role: their CURRENT_ROLE() changed under them, what they created was owned by the other session's role
  and their privileges were checked against it. A session now takes the engine's role until it sets its own, and keeps
  that to itself.
- **EQUAL_NULL and IS [NOT] DISTINCT FROM answer what `=` answers for two values.** They used to compare two non-NULL
  values by their printed text: a VARIANT number and a VARIANT string with the same digits were "not distinct", two
  TIMESTAMP_TZ values at one instant with different offsets were "distinct", and a NULL-safe join paired rows `=` keeps
  apart. Only NULLs are treated differently now.
- **A dropped key takes only its own foreign keys, a sequence stays in range, and SET INCREMENT serves the computed
  value first.** DROP PRIMARY KEY, DROP UNIQUE, DROP CONSTRAINT and DROP COLUMN take the foreign keys other tables hold
  on the key that goes, and only those: a foreign key on a UNIQUE key used to go with the primary key, and one on a key
  dropped by name or with its column used to stay. They take CASCADE and RESTRICT, which refuses a referenced key with
  `cannot drop constraint because of dependencies`, and SHOW IMPORTED KEYS names a UNIQUE key as its pk_name. A
  sequence draw whose next step would leave the 64-bit range fails with `Sequence value has exceeded valid range in
  sequence object S.` where it used to wrap around. After ALTER SEQUENCE ... SET INCREMENT the value already computed
  comes next and the new step applies from there, as documented; it used to step from the last value at once.
- **A table swapped with itself survives, and a hybrid table holds its keys.** `ALTER TABLE t SWAP WITH t`, however
  either name is spelled, leaves the table as it was; it used to drop the table and its rows for good. A view named as
  the table to swap with is refused by its kind, and a missing one is refused even under IF EXISTS. A hybrid table
  refuses a repeated PRIMARY KEY (`Primary key already exists`), a repeated UNIQUE value (`Duplicate key value violates
  unique constraint "SYS_INDEX_<table>_UNIQUE_<column>"`) and a row whose FOREIGN KEY no parent row holds, a NULL one
  included (`Foreign key constraint "SYS_INDEX_…" was violated.`), on INSERT; it used to take them all.
- **A JSON load strips null values on request, a stage named with a dot is one stage, and the load history is
  reported.** STRIP_NULL_VALUES = TRUE loads a JSON record without its null-valued fields and null array elements, at
  every depth, where they used to stay. A quoted stage name or schema name that holds a dot (`@"s.dotted"`,
  `@"S.A".c`) reaches its stage in PUT, a staged-file query and a COPY, which used to read it as a schema and a
  stage; INFORMATION_SCHEMA.STAGES lists a quoted name as written. INFORMATION_SCHEMA.LOAD_HISTORY reports each file a
  completed COPY loaded, which it used to leave empty, and a table's records go when it is truncated, replaced or
  dropped.
- **COPY reads a file in its ENCODING, fills the columns a column list leaves out, truncates on request, and a long
  AND chain compiles at once.** A delimited file loads in the character set its format's ENCODING names, and a byte
  sequence that set cannot read refuses its record (`Invalid UTF8 detected in string '...'`) unless
  REPLACE_INVALID_CHARACTERS = TRUE turns it into U+FFFD; the load used to read every file as UTF-8 and replace bad
  bytes silently. A COPY with a column list gives each column it leaves out its DEFAULT or next IDENTITY value, where
  it used to leave them NULL, and refuses to leave out a column whose DEFAULT is a sequence. TRUNCATECOLUMNS = TRUE
  and ENFORCE_LENGTH = FALSE cut a text longer than its column to the column's length instead of refusing it. A
  CREATE OR REPLACE FILE FORMAT that is refused leaves the old format as it was. A chain of ANDs, ORs or comparisons
  is typed in time that grows with its length: each operand's value range is worked out once, where every operator
  used to work it out again, so a chain of a few dozen terms took minutes.
- **COPY reads by its file format's date, time, timestamp and binary formats, an S3 stage stays inside its local
  root, and an engine keeps its stages to itself.** A COPY load reads its fields by the file format's DATE_FORMAT,
  TIME_FORMAT and TIMESTAMP_FORMAT, written inline or in a named file format, and by the session's input formats
  where a format is AUTO or not written; a BINARY column's text reads by BINARY_FORMAT, HEX, BASE64 or UTF8. A
  day-first file used to load `05/01/2024` as 1 May and refuse `13/01/2024`. An `s3://` stage's `..` segments and a
  URL with no bucket name files inside `stage.s3.localRoot` or the mapped directory, as S3 keys are flat names;
  they used to read and write any file the engine could reach. An engine that keeps nothing across a restart keeps
  its internal, table and user stages in a directory of its own below `stage.internal.localRoot`, deleted when it
  shuts down or the JVM exits; two engines whose stages shared a name used to see and erase each other's files.
- **A running statement can be stopped, a session keeps its own results, and the HTTP driver reads a FLOAT's NaN.**
  STATEMENT_TIMEOUT_IN_SECONDS ends a statement that outlives it — `Statement reached its statement or warehouse
  timeout of N second(s) and was canceled.`, SQLSTATE 57014, a whole block with it — and the in-process JDBC
  driver's `setQueryTimeout` and `cancel()` end one with `SQL execution canceled`; both used to run to the end.
  SYSTEM$CANCEL_QUERY cancels the running statement its ID names, and answers `Identified SQL statement is not
  currently executing.` or `invalid UUID` where it claimed a cancellation it did not make. LAST_QUERY_ID and
  RESULT_SCAN no longer lose a session's last query when other sessions run statements: each session keeps its own
  results and history. Over the HTTP driver a FLOAT column's NaN and infinities come back from `getObject` as
  Doubles and read `NaN`, `inf` and `-inf`.
- **An ORDER BY key's row fault, a text where a number belongs, and the JDBC Calendar overloads.** An ORDER BY key
  that cannot compute its value for a row (`ORDER BY v::NUMBER`, `ORDER BY 1 / n`) raises the expression's own
  error, `Numeric value 'abc' is not recognized` or `Division by zero`, where it was reported as `invalid
  identifier`, and a window's ORDER BY no longer turns such a key into NULL. ADD_MONTHS, HEX_ENCODE, BASE64_ENCODE,
  BITCOUNT, NEGATE and ARRAY_REPEAT answer `Numeric value 'b' is not recognized` instead of Java's own text, and a
  FLOAT's NaN or infinity given to a bitwise function is `Number out of representable range`. Both JDBC drivers
  honour the Calendar of `setTimestamp`, `setDate`, `getTimestamp` and `getDate` as the account's driver does, and
  `setTime` with a Calendar binds the time its moment shows in UTC. A bound date, time or timestamp is no longer
  read through the session's input formats.
- **TIME plus or minus an interval, the BIT functions in 128 bits, FROM_PARTS parts read as numbers.** `t + INTERVAL
  '3 hours, 18 minutes'` and `t - INTERVAL '1 hour'` move a TIME around the clock (23:30 plus an hour is 00:30)
  where both failed with `Cannot add`; a day or longer is refused while the statement compiles (`['DAY'] is not a
  valid date/time component for function + and type TIME(9).`). BITAND, BITOR, BITXOR, BITNOT, GETBIT and the
  BITAND_AGG family keep every bit of a value past 2^63. DATE_FROM_PARTS, TIME_FROM_PARTS and TIMESTAMP_FROM_PARTS
  round a fractional part half away from zero instead of truncating it and read a text part as its number;
  `DATE_FROM_PARTS(TRUE, 1, 1)` is refused at the call.
- **Equal moments meet, and DISTINCT aggregates count exact values.** Two TIMESTAMP_TZ or TIMESTAMP_LTZ values
  for one moment, written at different offsets, are now one value in a join, an IN subquery, UNION, INTERSECT,
  EXCEPT, DISTINCT, COUNT(DISTINCT), GROUP BY, PARTITION BY, RANK and an ORDER BY tie, as `=` already found them.
  A DATE meets its midnight TIMESTAMP_NTZ in a join, and a TIMESTAMP_NTZ meets a TIMESTAMP_TZ in the session's
  zone. COUNT, SUM, AVG and ARRAY_AGG with DISTINCT keyed numbers through a double, so integers past 2^53 and
  long decimals merged; they now key the exact value. AVG(DISTINCT x) declares and truncates to three more
  decimals than its argument, and its window form reads the distinct values. A RANGE frame's peers are found by
  exact keys too.
- **DATE_INPUT_FORMAT, TIMESTAMP_INPUT_FORMAT and TIME_INPUT_FORMAT are applied.** Once set to a format, it is the
  only reading of text as a date, a timestamp or a time: a cast, a typed literal, TO_DATE / TO_TIMESTAMP_* /
  TO_TIME and their TRY_ forms, a comparison, and a value written into a column. Text the format does not fit is
  `Date '…' is not recognized` (the TRY_ forms answer NULL), an ISO text and a string of digits included; a
  written `'AUTO'` reads by the format first and by AUTO after it. They were stored and never read.
- **RANGE frames with an offset over DATE and TIMESTAMP keys.** `RANGE BETWEEN INTERVAL '1 day' PRECEDING AND
  CURRENT ROW` over a DATE, a plain number of days over a DATE and an interval over any TIMESTAMP now hold the rows
  within that distance, ascending or descending, where all of them were refused. The refusals a RANGE frame earns
  are the account's own and come when the statement compiles: a second ORDER BY key, a text key, an interval beside
  a number's key, a number beside a timestamp's, and a negative interval. `AVG` over any frame but the running one
  declares three more decimals than its argument, as it already did over a ROWS frame.
- **A recursive CTE runs until a step adds no row.** It stopped silently after 1000 steps, so a three-year date
  series answered 1001 rows; the account caps the recursion nowhere, and neither does the engine now.
- **LISTAGG(DISTINCT x, delimiter) drops repeated values without GROUP BY.** `LISTAGG(DISTINCT g, ',')` listed
  every value (`a,a,b,b`) in a query that does not group; it answers `a,b`, as the grouped call does.
- **SELECT * over a join keeps the type of each column of a shared name.** A second column named like an earlier
  one (`t1.k` NUMBER, `t2.k` VARCHAR) was reported with the first one's type, in the result's metadata and in
  DESCRIBE RESULT; each now keeps its own, over a derived relation too.
- **A USING or NATURAL join compares its keys as the ON form's `=` does.** Keys were compared by their text, so two
  texts equal under the columns' collation (`'Abc'` and `'abc'` under `'en-ci'`) did not join; they do now, and a
  text key against a number is read as a number, a text that reads as none refusing the statement (`Numeric value
  'a' is not recognized`).
- **A value IN a query compares with each member as `value = member` does.** An uncorrelated subquery's members
  were matched by their text, so `2 IN (SELECT '02')` was FALSE and NOT IN returned the rows the account excludes.
  A number against text members now reads every member as a number — one that reads as none refuses the statement
  wherever the match stands — a date reads a text as a date, and the query column's COLLATE counts.
- **UNION [ALL] BY NAME types each column by the arms' columns of its name.** The types were folded by position,
  so a VARCHAR column became NUMBER(38,5) when the arms listed their columns in another order, and a DATE beside a
  NUMBER was refused as inconsistent; the arms' values now take the folded types too (`1` beside `2.5` is `1.0`).
- **An UPDATE's and a MERGE's SET values read the row as it was.** `SET a = b, b = a` wrote one column into both,
  and a value read what an item applied before it had set — in the hash order of the column names, not the order
  written. Every value now reads the old row, so two columns swap and `SET y = x + 1, x = 0` reads the old `x`. A
  plain UPDATE computes all its rows before it writes any, so a SET subquery over the table sees it as the
  statement began — `SET v = (SELECT SUM(v) FROM t)` gives every row one sum — inside a transaction too. Of two
  values that fail, a plain UPDATE reports the one of the column the table holds first, UPDATE … FROM and MERGE
  the one written first.
- **A writer waits for another session's transaction that holds its table.** An UPDATE, DELETE, MERGE or TRUNCATE
  that would change a row of a table another session's open transaction had written ran at once, and whichever
  transaction committed last silently undid the other's committed write. It now waits until the holder commits or
  rolls back and runs over what it left, or is refused once the session's LOCK_TIMEOUT passes — `Statement '…' has
  locked table 'T' in transaction … and this lock has not yet been released.` / `Your statement '…' was aborted
  because waiting for this lock is limited to 2 seconds.`, `… is currently not allowed.` under 0 — and the
  transaction it runs in stays open. Reads, INSERTs and statements that change no row neither wait nor hold a
  table. Sessions of the HTTP server and of its JDBC clients wait; an engine one thread drives has no other session
  to wait for.
- **A handler's state is kept for each place a statement calls it from.** A JavaScript function's globals and a
  Java function's class state were shared by calls written alike: two select items, two arms of a UNION, two
  common table expressions, two scalar subqueries, two aggregates and an INSERT's or an UPDATE's items counted on
  from one another (`SELECT cnt(1), cnt(1)` answered `1 | 2`). Each place counts from 1 now, and the rows it
  reads share its state; a call written once in a SQL function's body is one place for each call of the
  function, its rows sharing it; a block's own expressions — an assignment, a condition, a RETURN — start afresh
  on each evaluation, a loop's turns included; and reading a query's shape before it runs keeps nothing for its
  rows.
- **A dollar sign that opens nothing is refused where it stands.** `SELECT $"PV"`, `SELECT $ 1` and `SELECT 1 + $`
  were `parse error … near '<EOF>'` at the end of the text: they are `syntax error line 1 at position 7
  unexpected '$'.` at the dollar sign, and in a block the END after the statement is named too.
- **`EXIT WHEN …` and `BREAK WHEN …` report the account's lines.** The account reads WHEN as the loop's label and
  refuses the token after it and the statement's semicolon — `unexpected 'i'.`, `unexpected ';'.` — where the
  engine named a later semicolon.
- **A stray BREAK in a scripting function a block's expression calls keeps the function body's place.**
  `x := (SELECT fbreak())` reported the BREAK at the place the subquery's own refusals take in the expression;
  inside the block's `EXPRESSION_ERROR` it is `error line 1 at position 7`, the body's own, as from the session.
- **A FOR over a cursor leaves it open past its last row.** A FETCH after the loop finds no row and leaves its
  variables as they were, a CLOSE closes the cursor and a second CLOSE is refused, `CURSOR C is not open`; the
  engine closed the cursor at the loop's end, refused the FETCH and took any number of CLOSEs.
- **A FETCH from a cursor that is not open, inside a procedure a block calls,** is that block's statement's failure:
  its OTHER handler catches it, `SQLCODE` 92201, where it went past every handler.
- **The scan of a JavaScript procedure's OBJECT answer that is no object.** A `Uint8Array`, a `String` object or a
  `Date` answered for `RETURNS OBJECT` is handed out by the CALL; a statement that reads it from the CALL's scan
  is refused, `Failed to cast variant value "01" to OBJECT`.
- **A JavaScript answer that holds itself** — `var a = {}; a.self = a; return a;` for a VARIANT, an ARRAY or an
  OBJECT — is refused `Object contains cyclic references`, where it read as a late conversion failure.
- **A statement refused for the names it writes leaves an open transaction alone.** `BEGIN TRANSACTION; INSERT
  …; DROP TABLE nosuch;` committed the INSERT before the DROP was refused. A DROP, an ALTER or a COMMENT of a table
  or a view that is not there — or is there as the other kind — an UNDROP of a table never dropped or of a name a
  table holds, a CREATE TABLE or a CREATE VIEW under a taken name and a table created LIKE or as the CLONE of a
  source that is not there are refused before the transaction is touched, so a ROLLBACK undoes its rows; in a
  block or a procedure called under its caller's transaction each is refused for itself rather than as
  `Modifying a transaction that has started at a different scope is not allowed.` A column to add that is there
  and a column to drop that is not are refused after the commit, as on the account, and a rename — of a table, a
  column or a view — ends no transaction at all.
- **`UNDROP TABLE` of a name a table holds** is `Object 'T' already exists.`, before the dropped versions are
  looked at.
- **The width of a block's and a procedure's binary.** A procedure's parameter written `BINARY` reads
  BINARY(67108864), and a procedure's or a block's binary answer — whatever width RETURNS wrote — reads
  BINARY(67108864) to SYSTEM$TYPEOF over its scan and to DESCRIBE RESULT, which spells any binary result column
  without a written width, TO_BINARY's included, at that maximum.
- **The scan of a handler's answer wider than its column.** A Java handler answering `'abc'` for `RETURNS
  VARCHAR(2)`: the CALL hands it out, and a statement that reads the value from the CALL's RESULT_SCAN is refused
  `String 'abc' is too long and would be truncated`, while one that counts its rows answers.
- **Which of two type refusals an expression earns.** `(TRUE || 'a') || IS_NULL_VALUE('x')` was refused for the
  concatenation: what an operator's operands are refused for comes first, in the order written — a call's
  argument types, an arithmetic operator's, a sign's — and a concatenation's chain is judged last.
- **A Java handler's `String[]` over an ARRAY of containers** reads a NULL inside a nested array or object as
  `null`, where it read `undefined`.
- **A procedure's body written without a block runs as a block's statement.** `… AS $$SELECT x;$$` ran its
  statement bare. The account runs such a body as the one statement of a block of its own, `BEGIN` on a line
  above it: a failure is `Uncaught exception of type 'STATEMENT_ERROR' on line 2 at position 0 : …` (line 3 when
  the body opens with a line break, the position the statement's own), with the statement's refusal placed in the
  statement behind it, and a `:name` the block cannot bind is `invalid identifier 'X'` at line 2 as the body
  compiles. Called from a block, the calling statement's sentence stands in front of both.
- **The caret is an operator, and an unsupported one.** `SELECT 2 ^ 2` was a syntax error at a character no token
  opens with. The account reads the caret between two values and refuses the statement while it compiles,
  `Unsupported feature '^'.`, before any name in it is looked up — in a select list, a WHERE, HAVING or ORDER BY
  clause, an INSERT's values, a view's query, under EXPLAIN, on a block's branch that never runs and in a
  routine's body as it is created (`Compilation of SQL UDF failed: Unsupported feature '^'.` for a function's
  expression). `SELECT 2 ^` and `SELECT ^ 2` stay syntax errors, at the end of the text and at the caret.
- **A negated LIKE with a quantifier reports the account's lines.** `'a' NOT LIKE ANY ('x', 'y')` — and NOT
  ILIKE, NOT RLIKE, ALL — named the quantifier alone: the token that opens the parentheses is named too, and
  then the parenthesis behind a name written first, or, in a select list, the closing parenthesis of a list of
  several patterns.
- **A Snowscript function's block the account does not read.** A word before a string inside a call's
  parentheses (`nosuch(TIMESTAMP_LTZ '…')`, where DATE, TIME and TIMESTAMP alone open a literal) and a name of
  three parts were answered `Unsupported expression for Snowscript UDF: …`; the body is read as an expression
  instead and refused in that frame, at the RETURN and at the token after the first parenthesis. A membership
  list's plain member after a computed one is printed in the list the plain ones share:
  `NOSUCH(A IN (1 IN (1 + 1), 3))`.
- **A word that names no unit, in a block's own expression.** `BEGIN RETURN DATEADD(nosuch, 1, d); END;` was
  refused by the function's sentence; a block is refused for the name nothing declares, `invalid identifier
  'NOSUCH'` at line 1, position 0, when the statement runs.
- **What a Snowpark `Row` hands a Java handler.** `DataFrame.collect()` hands each cell in the class Snowpark
  does: a NUMBER of no scale a `Long`, one with a scale a `BigDecimal`, a FLOAT a `Double`, a DATE, a TIME and a
  TIMESTAMP the `java.sql` classes, a BINARY its bytes, a VARIANT, an ARRAY and an OBJECT their JSON text. `get`
  answers that object, `getVariant` reads the text, `getString` over bytes is the list of their numbers, and
  `toString` writes a text between double quotes (`Row[1,"a"]`).
- **`ALTER ACCOUNT` reads a name and the account's tags.** `ALTER ACCOUNT <name> SET | UNSET …` was a syntax
  error at the name: the statement is read, its list judged first (`invalid value [5] for parameter
  'QUERY_TAG'`, `invalid property 'X' for 'ACCOUNT'`), and an account that is not this one is then refused as
  one that does not exist; a fault in a named account's list is told at the SET or the UNSET after the name.
  `ALTER ACCOUNT SET TAG t = 'v'` and `UNSET TAG t` were syntax errors at TAG: they look the tag up and keep its
  value on the account.
- **A routine's types are spelled as they were declared.** A RETURNS type or an argument written
  `VARCHAR(16777216)` or `BINARY(8388608)` was spelled bare, like one written with no width: GET_DDL and DESCRIBE
  keep the width that was written, and INFORMATION_SCHEMA's `data_type` and `character_maximum_length` tell
  it, where a type written bare still reads `VARCHAR(134217728)` / `BINARY(67108864)` there. A `RETURNS BINARY`
  written bare reads `BINARY` in GET_DDL and DESCRIBE.
- **A statement that is no query in a FROM clause's parentheses** is refused at its first word, as on the
  account: `SELECT * FROM (DESCRIBE TABLE t)` names `DESCRIBE`, not the `TABLE` after it, and SHOW, EXPLAIN,
  CALL, USE and a bare VALUES there likewise.
- **Non-finite numbers in a block.** A FLOAT, DOUBLE or REAL variable holds NaN and the infinities, from a LET, a
  DECLARE default, an assignment, a procedure's argument and its RETURN; a call rebuilt from its arguments'
  values keeps a FLOAT a FLOAT and writes them as the casts of their words; a BINARY literal in a block is the
  BINARY it spells, where it was a NULL.
- **Constant arguments that fold to NULL.** GETVARIABLE takes a name the plan folds to NULL; RANDOM and UNIFORM
  refuse a call folding to NULL that a condition tests; BASE64_ENCODE's line length and alphabet follow the
  same rule and echo the refused argument from the plan.
- **A text literal in arithmetic** reads as the plan reads it: one that spells a number with nothing about it at
  eighteen digits and its value's scale (`'1.50' * 1` is NUMBER(19,1)), any other text as NUMBER(18,5).
  SYSTEM$TYPEOF names a small literal by its digits written out (`1e-7` is NUMBER(8,7)).
- **SYSTEM$GET_TAG and TAG_REFERENCES** judge their arguments while the statement compiles — the domain first
  and as written, a missing tag refused over no rows — match a domain whatever its case, name a missing object
  as a statement over its kind names it, and name a function or a procedure by its signature.
  `ALTER FUNCTION | PROCEDURE | SERVICE | IMAGE REPOSITORY … SET TAG` keeps the tag; it was accepted and kept
  nothing.
- **A missing object is named as the account names it**: a stream's source table, a dynamic table's CLONE source
  and a RENAME COLUMN's table as written when written bare; DESCRIBE FILE FORMAT, MASKING POLICY and ROW ACCESS
  POLICY by the full path; a SHOW over a schema or a database that does not exist by its listing's kind.
- **Scripting.** An error that leaves several bodies carries one uncaught-exception sentence for each; a CALL a
  block runs is the statement it is written as, each `:name` judged by the type its variable is declared with;
  `CALL … INTO` takes one variable; a variable that holds NULL, written alone as a VALUES row's item, carries no
  type and fits any column; a block's variable bound as a row count gives the whole part of an exact number.
- **Syntax-error lines in a block.** A statement refused at a word is given up with the construct whose
  statement it is, and the rest is read from the construct around it; a stage reference is one token in a
  refusal; FLOAT4 and FLOAT8 are names.
- **NULL arguments and whole-number counts.** A NULL in any argument of a text function answers NULL, whatever
  place it stands in; a position, a length or a count is a whole number whatever is written there, a fraction
  going to the nearest; a date call with the bare word NULL among its arguments folds to an untyped NULL before
  its unit is judged. SYSTEM$TYPEOF tags a call over an argument that is always NULL as the empty interval.
- **Set operations and VALUES fold their columns' types**: a later branch gives its value in the column's type
  (a number under a BOOLEAN column is FALSE for zero), a FROM-clause VALUES holds every row at the folded type,
  and the word NULL cast to a text type is the bare word where it meets an exact number.
- **`EXECUTE IMMEDIATE … USING`** binds each `:n` by its number beside the question marks, and the text is a
  statement of its own that reads none of the block's variables. Each statement of a text of several commits at
  its own end; the places inside the text are counted as the text is written.
- **A call of a SQL function with several signatures of one argument count** is typed by the body of the
  signature the call runs.
- **The clock functions' precision.** CURRENT_TIMESTAMP, LOCALTIMESTAMP, CURRENT_TIME and LOCALTIME take a
  whole-number constant between 0 and 9, judged while the statement compiles.
- **Functions.** ARRAY_FLATTEN takes an array of arrays; the empty pattern matches nothing in the REGEXP family;
  TRY_TO_BOOLEAN reads the words TO_BOOLEAN reads; a SHA2 digest size is 224, 256, 384 or 512; CONVERT_TIMEZONE
  knows a zone by its own name; NORMAL takes two numbers and a generator; a VARIANT read inside a block stays one
  for the call around it; a RANGE frame with an offset gives a row whose key is NULL the rows that share it.
- **Policies.** `ALTER TABLE … ALTER | MODIFY [COLUMN] c SET / UNSET PROJECTION POLICY` takes both verbs and
  resolves the policy before the column; DATA_METRIC_FUNCTION_REFERENCES reads its metric and its entity as
  identifier paths under a domain judged first.
- **Task functions.** TASK_HISTORY declares the account's 28 columns and the four task functions its types; a
  run's ROOT_TASK_ID, RUN_ID and GRAPH_RUN_GROUP_ID are its graph run's.
- **Sessions running side by side.** The statement pins — clock, zone, output formats, the refusal's role —
  follow each thread's own outermost statement, so a statement that starts while another session's is running
  keeps its session's zone and its privilege sentence. CURRENT_ROLE() answers the owner inside a body running
  with owner's rights.
- **Warehouses, Iceberg tables and users.** A warehouse altered to another type keeps the acceleration setting
  it has; CREATE ICEBERG TABLE without an external volume takes the EXTERNAL_VOLUME parameter of its schema,
  else its database's, else the account's; NETWORK_POLICY is one of a user's properties, read by CREATE USER
  and by an ALTER USER … SET list, which takes commas.
- **REST API.** The 404 a resource answers for an object that is not there is the sentence a statement over it
  refuses with; getTags answers on functions, procedures, service functions, image repositories and services; a
  user's network_policy is applied and read back.
- **A SYSTEM$ function is called as any other function is.** `SELECT SYSTEM$TYPEOF(a => 1)` was a syntax
  error and answers its type; a function that takes no names says so (`function SYSTEM$WAIT does not support
  named arguments`), a name written twice is refused at its second value, a quantifier and a WITHIN GROUP by
  the call's echo, and a wrong count echoes the values alone: `too many arguments for function
  [SYSTEM$TYPEOF(1, 2)] expected 1, got 2`. Under a qualifier the name is a routine nobody declared —
  `Unknown user-defined function SNOWFLAKE.SYSTEM$TYPEOF.` — in a query and in a CALL.
- **A query carries each `SYSTEM$ABORT_TRANSACTION` it writes out once, while it compiles.** The call ran when
  a row evaluated it, so behind a false `IFF`, over an empty table, in a `WHERE` no row passes and under
  `EXPLAIN` it ended nothing, and two rows called it twice. The transaction is now over whether or not a row
  reaches the call, and every row reads the one answer.
- **`SYSTEM$ABORT_TRANSACTION`'s argument.** An argument that fails as it is read — `TO_NUMBER('abc')`, `1/0`,
  `SQRT(-1)` — and a conversion by a format name no transaction, `Bad transaction id`, where the failure was
  raised; the argument's type is judged before its value (`'x'::DATE` is refused as a DATE); a NULL the plan
  types from what stands beside it is no constant (`NVL(NULL, 12)` is `… needs to be constant, found
  'NVL(SYSTEM$NULL_TO_FIXED(null), 12)'`); `IFF(NULL, 12, 13)`, `CASE WHEN NULL …` and `DECODE(NULL, NULL, …)`
  name the branch the NULL leaves. A CALL's refusal of an argument's type names no place, and
  `SYSTEM$WAIT` refuses an ARRAY.
- **A binary literal is `X` and a string literal, and a quoted name may cross a line end.** A backslash
  escapes and a doubled quote stays inside a binary literal, so `SELECT x'41\'; DELETE FROM t --'` is one
  statement, its literal running to the last quote, where it was read as two; line feeds and carriage returns
  in the literal's text, written out or escaped, are no part of its digits, as blanks are not (`x'4\n1'` is
  one byte). `SELECT 1 AS "a<LF>b"` was a parse error and names its column. A text that is nothing but a
  comment that never closes reports the lexer's line alone.
- **Text functions at their edges.** `LTRIM`, `RTRIM` and `TRIM` with no list of characters trim the blank
  alone — a tab and a line feed stay; `REPLACE` with an empty pattern replaces nothing; part 0 of `SPLIT_PART`
  is part 1; `STRTOK` with no delimiter characters answers the whole text; `CHR` rounds a fraction to the
  nearest code.
- **Dates built from parts roll over in both directions.** `DATE_FROM_PARTS(2024, 0, 1)` is 2023-12-01,
  `DATE_FROM_PARTS(2024, 3, 0)` is 2024-02-29 and `TIMESTAMP_FROM_PARTS(2024, 3, 15, 25, 61, 61)` carries its
  hour, minute and second on; an hour, a minute, a second or a nanosecond below its range borrows. A
  `DATEADD` over a text answers a timestamp, as it is typed, and a DATE truncated to an hour or a shorter part
  stays the DATE it is.
- **Bits.** `BITSHIFTLEFT` and `BITSHIFTRIGHT` shift within 128 bits, a left shift dropping what leaves the
  top and a right shift keeping the sign. The bitwise aggregates round a fraction half away from zero before
  they read its bits (`BITXOR_AGG` over 4.5 reads 5), declare the whole digits of their argument — NUMBER(2,0)
  over a FLOAT — and refuse a text that is no number as a failed conversion. `BITOR` over literals, a bitwise
  aggregate over values of no sign and `FACTORIAL` of a whole literal are tagged by the value they hold.
- **Aggregates.** `PERCENTILE_CONT` and `PERCENTILE_DISC` over a descending order read the group from its
  top. `KURTOSIS` answers a NUMBER(38,12) and `SKEW` a FLOAT; `REGR_COUNT` is a NUMBER(18,0); `BIT_AND`,
  `BIT_OR`, `BIT_XOR` and `BIT_NOT` are typed as the names they stand for.
- **`CBRT` as the account computes it.** The root is within a unit in the last place and not always the
  nearest double: `CBRT(27)` is 3.0000000000000004 and `CBRT(216)` is 6.000000000000001, where `CBRT(64)` and
  `CBRT(125)` are exact.
- **`GET_IGNORE_CASE` reads the key spelled exactly so first**, and only without one a key of another case;
  a whole number is told as one (`2.0` is 2).
- **A block's logging statement evaluates its arguments.** `SYSTEM$LOG_INFO(1 / 0);` passed over the
  division; it fails the block where the argument stands, `Uncaught exception of type 'EXPRESSION_ERROR' on
  line 1 at position 22 : Division by zero`.
- **`SHOW NETWORK POLICIES` and `SHOW IMAGE REPOSITORIES` read `STARTS WITH` and `LIMIT … FROM`** as the
  other listings do, a `LIMIT 0` refused as theirs is. A fault after a user's `NETWORK_POLICY` —
  `ALTER USER u SET NETWORK_POLICY =`, `ALTER USER u UNSET NETWORK_POLICY COMMENT` — is refused at that word.
- **`SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS` with no argument or a NULL one.** Written with no argument
  it is `not enough arguments for function [SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS()], expected 1, got 0`
  and not a syntax error; a NULL name answers NULL.
- **The clock functions' precision argument** echoes a FLOAT as the plan converts it:
  `CURRENT_TIMESTAMP(PI())` is `Invalid value [CAST(PI() AS NUMBER(18,0))] for function 'CURRENT_TIMESTAMP',
  parameter 1: must be an integer constant`.
- **A USING or NATURAL join matched NULL keys, and missed numbers of another scale.** `a JOIN b USING (k)`
  joined a row whose key is NULL to the other side's NULL-keyed rows, and did not join an INT 1 to a NUMBER(5,2)
  1.00. A key is matched by equality: a NULL joins nothing, so an inner join drops such rows and an outer join
  keeps each side's unmatched, and two numbers are equal by value whatever scale each is held at.
- **A NULL of a text type through a derived relation, and a text column of NULLs.** `SELECT a + 1 FROM (SELECT
  NULL::VARCHAR(10) AS a)` was typed NUMBER(19,5) as a text read as a number is. Such a NULL —
  `NULL::VARCHAR(10)`, `CAST(NULL AS STRING)`, `TO_VARCHAR(NULL)` — is the bare word to an exact number beside
  it, and a derived table, a common table expression, a view and a renamed column carry it to the query above:
  NUMBER(19,0), as `NULL + 1` is. What is computed from a text that is always NULL — a stored column of NULLs,
  a conversion of one, `COALESCE` and `NVL2` over one — is tagged by the empty interval, `[SB1]`.
- **A SQL function's number is held at the scale its call is typed with.** `fx(n)` over a NUMBER(10,2) column
  holding 1.5, the body `n + 0.5`, answered `2.0` in a column typed NUMBER(11,2). It answers `2.00`, with one
  signature or several, and `fx(n) + 1` is `3.00`.
- **A dynamic text's refusal is placed in the text as written.** A fault after a bound placeholder was placed
  in the text as it stood once the value was written in: `EXECUTE IMMEDIATE 'SELECT :1a AS a' USING (s, t)`
  named the AS at position 14 for a value of five characters. A syntax fault and a name that does not resolve
  are placed where the text has them — position 11 — on its later lines too, and a comment that opens the text
  is not counted.
- **A query assigned to a RESULTSET reports its own faults, from its parenthesis.** `res := (SELECT … OFFSET
  :s)` and `res := (SELECT * FROM t UNPIVOT (x FOR y IN (:s)))` were refused at the query's SELECT. The query's
  own faults are reported where they stand — the parenthesis that closes it where a clause still waits, the
  colon in an UNPIVOT's list of names — and a refusal the query earns when it runs is placed from the opening
  parenthesis (`res := (SELECT missing FROM t)` names the identifier at position 8, and at line 2 when the
  query stands on the next line). A query a RESULTSET is declared with keeps its own places, a refusal of a
  call's arguments included.
- **`IDENTIFIER()` over a block's variable echoes what the variable holds.** An empty text was echoed as four
  quotes, and in a SELECT … INTO as `':s'`; the refusal names the text between quotes, `invalid identifier ''`.
- **A CALL of a name a built-in function holds.** With a procedure `ABS(n)` in the schema, `CALL abs(-3)` ran the
  procedure. A name with no schema before it that a built-in function holds — a scalar's or an aggregate's,
  written bare or between quotes in any case (`CALL "abs"(-3)`) — now calls the function, as an account does:
  `CALL abs(-3)` answers 3, `CALL pi(1)` is the built-in's `too many arguments for function [PI(1)] expected 0,
  got 1`, and `CALL test_schema.abs(-3)` still runs the procedure. A block's CALL, `x := (CALL abs(-3))` and
  `CALL abs(-3) INTO :x` read the name alike. A built-in's refusal of named arguments is placed nowhere in a
  CALL (`error line 0 at position -1`), as its count refusals are.
- **The shape of a CALL's named arguments is judged first.** A name written twice was refused when the CALL ran
  (in a block as an uncaught STATEMENT_ERROR placed in the CALL's own text), an unknown procedure's CALL was
  `Unknown function` whatever its arguments, and a mix of named and positional arguments was told before a name
  written twice. The shape is now judged before the procedure is looked for, and for a block's CALL while the
  block compiles, on a branch that never runs too: `duplicate named argument N for function PN` at the second
  writing, then `illegal mixing of named and positional arguments for function PN` at the procedure's name,
  the procedure named as the CALL writes it (`TEST_SCHEMA.P`, `"p"`).
- **Where an assigned CALL stands.** A failing `x := (CALL p(:d))` was placed at the assignment, and for a
  RESULTSET assigned inside a construct at an offset in the CALL's own text; a bare name among its arguments
  was placed from the assignment's start. The CALL inside the parentheses is the statement that runs: its
  failure is placed at the CALL, at the block's top level and inside IF, FOR, CASE and a nested block, and its own
  places count from the CALL (`invalid identifier 'STATS'` at position 13 of `CALL add_dml(stats, 'z')`).
- **A VARIANT variable bound to a CALL.** A variable declared VARIANT that holds a text was converted as the
  text: `Date 'x' is not recognized`. It is a VARIANT to the CALL whatever it holds — `Failed to cast variant
  value "x" to DATE`, and `to FIXED`, `to REAL`, `to BOOLEAN`, `to TIME`, `to TIMESTAMP_NTZ` by the parameter.
  The too-many-arguments sentence echoes a semi-structured variable that holds a value as the JSON the statement
  binds: `[PN(PARSE_JSON(:v), 1)]`, `[ADD_DML(CAST(PARSE_JSON(:stats) AS OBJECT), 'z', 1)]`.
- **The width a body holds a text parameter at.** A parameter declared `VARCHAR`, `STRING` or `TEXT` with no
  length was held at the column width, so a NULL bound on from it was `VARCHAR(16777216)`. A body holds it as
  wide as a block's own `DECLARE s VARCHAR` — `Invalid argument types for function 'PA': (VARCHAR(134217728))` —
  and a parameter written with a length at that length.
- **Storage tags follow a column of NULLs and a date column's days further.** `SYSTEM$TYPEOF` tagged a date part
  or a character code over a column that is always NULL by its declared width once the column came through a
  derived table, a common table expression, a view, a set operation or a join, and a year or a day of the year
  over a DATE column by the part's whole range. The empty interval now follows such a column of any type through
  a derived relation and through what is computed from it; `YEAR`, the week's year and `DAYOFYEAR` over a DATE,
  TIMESTAMP_NTZ or TIMESTAMP_LTZ column are tagged by the least and the greatest day the table holds (`YEAR(d)`
  over years 100 to 120 is `[SB1]`), read through MIN / MAX / ANY_VALUE, a derived relation, a conversion that
  keeps no offset of its own, a DATE moved by a whole number of days, `DATEADD`, `DATE_TRUNC`, `LAST_DAY` and
  what `COALESCE`, `IFF`, `GREATEST` and `LEAST` choose. A SQL table function's column is tagged by the values
  its body computes, at the scale the column declares, instead of by the declared width.
- **`SPLIT_PART`'s part of a type that is no number.** A BOOLEAN there was refused with no place, a date as
  `Numeric value '2026-…' is not recognized` when a row was read, and a BINARY with no place. Each — and a time
  and a timestamp — is refused while the statement compiles, at the call: `Invalid argument types for function
  'SPLIT_PART': (VARCHAR(5), VARCHAR(1), DATE)`.
- **The lines of a text the lexer gives up on.** A text with an unclosed string, quoted name, comment or binary
  literal reported the lexer's line alone, and one with a character no token opens with that character alone,
  whatever else the text held. An unclosed string now loses its quote and its words are read on (`SHOW TABLES
  LIKE 'abc` is the lexer's line, then `'abc'`), a quoted name, a comment and a binary literal take everything to
  the end (`SELECT "abc` adds `'<EOF>'`), and the lexer's line stands among the parser's by the place of the
  fault (`SELECT 1 x y; SELECT 'abc` is `'y'`, then the lexer's line). After a character no token opens with the
  reading goes on where an account's does — at the next alias, comma or clause after a select list's value
  (`SELECT 1 # 2 x y` is `'# '`, then `'y'`), at the next comma in a list of keys, at the next statement
  otherwise — and a fault before the character ends a script's report (`SELECT 1 x y # 2` is `'y'` alone). An
  unclosed comment was read as a division and a star.

- **A NULL generator, a NULL format, and NTH_VALUE's n.** `UNIFORM(1, 2, NULL)` drew a number and
  `TO_CHAR(1, NULL::VARCHAR)` printed the number unformatted; both answer NULL. `NTH_VALUE(x, 2.0)` and
  `NTH_VALUE(x, '2')` read the first value: the n was taken from the argument's text and fell back to 1.

- **A listing's modifier written three times.** `SHOW TABLES LIMIT 1 LIMIT 2 LIMIT 3` was refused at the third
  LIMIT; it is refused at the second, as two are, and in a block at that LIMIT and its count.

- **A SHOW in a function's block body.** `CREATE FUNCTION f() … AS 'BEGIN SHOW TABLES LIMIT 0; RETURN 1; END;'`
  answered `Unsupported statement type for Snowscript UDF: SHOW` where an account judges the SHOW's own shape
  first: the page-size sentence, and `Unsupported feature 'SHOW TABLES ... WITH PRIVILEGES <>'.`

- **A parenthesized list after a SHOW scope's name.** An account reads a signature — a list of types or names,
  empty too — after the name a SHOW's `IN` names, and then looks the scope up as if it were not written.
  `SHOW TABLES IN test_schema (x)`, `SHOW TABLES IN DATABASE db (x)`, `SHOW COLUMNS IN TABLE t1 (x)`,
  `SHOW FUNCTIONS IN CLASS c (x)` and `SHOW TABLES IN ACCOUNT a (x)` were syntax errors at the parenthesis; each
  now answers what the statement answers without the list, for every listing that takes a scope, `SHOW DATASETS`,
  `SHOW CLASSES` and `SHOW <class>` included. A faulty list is refused where an account refuses it — `(x y)` at
  the `y`, `(1)` at the `1`, `((x))` at the inner parenthesis, `(a.b.c.d)` at the `d`, `(ARRAY(1))` at the `1` and
  then at the list's own `)` — nothing but the listing's modifiers follows the list, and neither an instance's
  name (`IN c i (x)`) nor the lone word `ACCOUNT` takes one. The word `IDENTIFIER` before a list that opens no
  reference is the scope's own name: `SHOW TABLES IN SCHEMA IDENTIFIER(x)` misses the schema `IDENTIFIER`.

- **A SYSTEM$ word as a name.** A word that begins `SYSTEM$` was a system function's token wherever it stood, so
  `SELECT 1 AS system$foo`, `CREATE TABLE system$t (a INT)`, `DROP TABLE system$foo`, `USE SCHEMA system$foo`,
  `LET system$x := 1` and `SHOW SYSTEM$FOO` were all syntax errors at the word. It is a plain name wherever it
  calls nothing: an alias, a column, a table, a view, a schema, a common table expression, a variable, a class and
  the function a `CREATE FUNCTION` names — a function of the schema named `SYSTEM$F` is created, called by its
  name and dropped — and `SELECT SYSTEM$FOO` is `invalid identifier 'SYSTEM$FOO'`. A call that names no function
  stays `Unknown function SYSTEM$FOO.`

- **A word after a block's END.** An account reads one word after a block's `END`, as it reads a loop's label
  after `END LOOP`, and Frostlake refused the word. `BEGIN RETURN 1; END IF;` now answers `1`, as a statement, as
  the text `EXECUTE IMMEDIATE` runs and as a procedure's body, and so it does ended by `LOOP`, `CASE`, `DO`,
  `UNTIL`, a quoted name, `COMMENT` or `IDENTIFIER`; a second word is refused at itself (`END a b` at the `b`),
  and `FOR`, `WHILE`, `REPEAT`, `BEGIN`, `END` and a plain word after a top block's `END` stay refused where they
  stand. A block nested in another that ends in a word and declares nothing is read and then neither compiled nor
  run, as on an account: `BEGIN LET r := 0; BEGIN r := 1; END IF; RETURN r; END;` answers `0`, and a name the
  nested block's statements do not declare is no fault. A nested block with a `DECLARE` section runs.

- **SHOW DATASETS, SHOW CLASSES and SHOW of a class in a table, a view, a class or an application.** Each of
  these scopes was a syntax error. A table, a view and a class hold nothing the three statements list —
  `Unsupported statement type 'Cannot show objects of type DATASET in TABLE'.`, the noun `DATASET`, `CLASS` or
  `INSTANCE`, with the scope's name or without it, told before a `LIMIT 0` — an instance listing looks a class
  scope up (`Object type or Class 'X' does not exist or not authorized.`), and an application is looked up and
  missed (`Application 'X' does not exist or not authorized.`, `Application package 'X' …`), the datasets' listing
  naming nothing it misses. `SHOW CLASS` is refused at the `CLASS`, where it was refused at the end of the text.

- **DROP CLASS.** `DROP CLASS x` was a syntax error at the name. It reads a name, a signature and `CASCADE` or
  `RESTRICT`, and misses the class by its full path — `Object type or Class 'DB.S.X' does not exist or not
  authorized.` — which `IF EXISTS` forgives; a schema or a database that does not exist is named first, and a
  name of four parts names nothing.

- **A database named by a path in the key listings and SHOW DATABASE ROLES.** `SHOW PRIMARY KEYS IN DATABASE
  a.b` (and `UNIQUE`, `IMPORTED`, `EXPORTED`) was a syntax error at the name and is `Must specify the full search
  path starting from database for B`, the path's last part; `SHOW DATABASE ROLES IN DATABASE a.b` and `… IN a.b`
  were syntax errors at the dot and are `Object does not exist, or operation cannot be performed.`, told after a
  `LIMIT 0` and before a `TERSE` listing's refusal.

- **A view as a MERGE's source.** `MERGE INTO t USING v ON …` was refused with `Object 'V' does not exist or not
  authorized.` whenever `v` was a view. A view is read there as a FROM clause reads it — named bare, by a path or
  under an alias — and one whose table is gone answers `Failure during expansion of view 'V': …`.

- **DML over a view whose table is gone.** `DELETE FROM v`, `UPDATE v …`, `INSERT INTO v …`, `MERGE INTO v …`
  and `TRUNCATE TABLE v` told the statement's own rule for a view (`DELETE statement's target must be a table`).
  The view is expanded first, as an account does it: `Failure during expansion of view 'V': SQL compilation
  error: Object 'DB.S.VB' does not exist or not authorized.`; a whole view keeps the target sentence.

- **The scopes of SHOW NETWORK RULES, SECRETS, PASSWORD POLICIES and NETWORK POLICIES.** `IN TABLE [t]`, `IN VIEW
  [v]` and `IN CLASS c` were syntax errors; they are `Unsupported statement type 'Cannot show objects of type
  SECRET in TABLE'.` in the listing's own noun. `IN APPLICATION a` and `IN APPLICATION PACKAGE p` look the
  application up (`Application 'A' does not exist or not authorized.`; a network rule listing `Object does not
  exist, or operation cannot be performed.`), and a package without its name is the syntax error at the
  statement's end. `SHOW NETWORK POLICIES` reads a scope: any kind of object holds none (`… of type NETWORK
  POLICY in DATABASE`), and a bare name is another account's, `Account 'X' does not exist or not authorized.`
  `SHOW PASSWORD POLICIES ON DATABASE d` is `Unsupported feature 'SHOW PASSWORD POLICIES ON DATABASE'.`, and an
  ON before anything but ACCOUNT, USER or DATABASE is refused at the ON.

- **DATA_METRIC_FUNCTION_REFERENCES' domain words and argument types.** 58 more kinds of object are `Invalid
  value '<KIND>' for argument 'ref_entity_domain' …` where they were an unknown domain (ALERT, MASKING POLICY,
  SERVICE, SECRET, NOTEBOOK, MODEL …), a kind of several words echoed with underscores (`'ROW_ACCESS_POLICY'`);
  a table kind may be written with an underscore (`ICEBERG_TABLE`, `MATERIALIZED_VIEW`); `REF_ENTITY_NAME` and
  `REF_ENTITY_DOMAIN` given a number, a boolean, a date or a VARIANT are `invalid type [NUMBER(1,0)] for
  parameter 'REF_ENTITY_NAME'`, the entity judged before the domain wherever each is written, and one argument
  written without a name is judged as the metric's.

- **SYSTEM$GET_TAG in a block is answered while the block compiles.** `BEGIN RETURN SYSTEM$GET_TAG('nosuch',
  't', 'table'); END;` reported the missing tag as the RETURN's `EXPRESSION_ERROR`, a handler caught it, and a
  branch that never ran was not judged. The lookup happens before any statement runs, as on an account: the
  refusal is bare, past every handler, for a RETURN, a LET, a condition, a DECLARE default, a nested block and a
  query such an expression reads alike. A variable written by its bare name as an argument — `SYSTEM$GET_TAG('tg',
  n, 'table')` — is read as its value, where it was refused as no constant.

- **A row access policy reads the tag of its table.** `SYSTEM$GET_TAG_ON_CURRENT_TABLE('tg')` in a row access
  policy's body answered NULL; it reads the tag of the table the policy filters, as in a masking policy.

- **The echo of a text conditional read as a number.** `LAST_QUERY_ID(IFF(TRUE, '1.50', '2') * 1)` echoed
  `CAST((TO_NUMBER(IFF(…), 18, 5)) * 1 AS NUMBER(18,0))`. An IFF or a CASE over text literals reads at the widest
  scale its results spell and a product by one folds into the cast to its type, `CAST(CAST(TO_NUMBER(IFF(…), 18,
  1) AS NUMBER(19,1)) AS NUMBER(18,0))`; NVL and any other text value read at NUMBER(18,5) and fold alike; a term
  of a smaller scale is raised as beside a number of the width the results spell, `… + (CAST(0 AS NUMBER(3,1)))`,
  and beside a term of a larger scale the conditional reads at the term's; a CASE matching one constant against
  others folds to the result it picks, and a text a call made (`UPPER('1.50') * 1`) reads as a text value does.

- **A NULL alphabet for the BASE64 decoders.** `BASE64_DECODE_BINARY(x, NULL)`, `TRY_BASE64_DECODE_BINARY(x,
  NULL)` and `TRY_BASE64_DECODE_STRING(x, NULL)` answer NULL, as `BASE64_DECODE_STRING` did.

- **What the UNICODE_ANALYZER joins.** A colon or a middle dot between two letters and a comma between two digits
  join them into one word (`SEARCH('a:b', 'a', ANALYZER => 'UNICODE_ANALYZER')` is FALSE, `'1,2'` is not found by
  `'1'`); a colon between digits and a comma between letters still cut.

- **What follows the name of a kind without objects.** `DESCRIBE SNAPSHOT | DATASET | MODEL | CONNECTION |
  SERVICE | IMAGE REPOSITORY | EXTERNAL VOLUME <name> x` named the surplus word; the account reads it as a
  property waiting for its value and refuses the statement's end, and reads a signature after the name too.
  `DESCRIBE DATASET` and `DESCRIBE MODEL` miss their object (`Dataset 'DB.S.X' does not exist or not
  authorized.`) where they were an unsupported feature. `DROP IMAGE REPOSITORY` and `DROP SERVICE` take a
  signature and a behaviour word — PURGE being refused in its one sentence, and a word after FORCE where it
  stands — and `DROP SNAPSHOT | CONNECTION | DATASET | MODEL x (a)` reads the signature.

- **Syntax-error lines over the kinds' words.** `DESCRIBE RESOURCE x` reports the word and then the statement's
  end, `DESCRIBE STORAGE x` the STORAGE and then the end — the words after such a keyword are read in pairs, the
  second of each refused — and `DESCRIBE DBT | GIT | SEMANTIC | AUTHENTICATION | SESSION | PACKAGES | PRIVACY x
  y` the wrong second word and then the end; `DROP DATA METRIC FUNCTION f(TABLE(INT))` stops at the METRIC.
  `ALTER EXTERNAL TABLE t SET TAG a = 'x'` is refused at the SET and `UNSET TAG a` at the tag's name; `UNSET x`
  is `invalid property 'x' for 'EXTERNAL_TABLE'`, `UNSET AUTO_REFRESH` `invalid value 'null' for property
  'AUTO_REFRESH'`, and a refused SET property is named as written.

- **REST: missing objects of the system database's schemas, and a role's grants without a current database.** A
  request under `/api/v2/databases/SNOWFLAKE/schemas/ACCOUNT_USAGE/…` answered that the schema does not exist; it
  names the object that is missing, as under any schema (`\ntable 'snowflake.account_usage.nosuch_t' does not
  exist or not authorized. …`). `GET /api/v2/databases/{database}/database-roles/{name}/grants` in a session
  that has no current database answers `400` with code `090105` — `cannot perform show grants. this session does
  not have a current database. call 'use database', or use a qualified name.` — before the database or the role
  is looked up.

- **A USING list binds the text of each value.** `SYSTEM$TYPEOF(?)` over a FLOAT variable holding 6.5 is
  `VARCHAR(3)`, as on an account, where the value kept its own type: a NUMBER is bound as its digits at the declared
  scale (`1.50`), a FLOAT as fifteen significant digits with an exponent where its magnitude calls for one
  (`1e+20`, `1.5e-07`, `inf`, `NaN`), a TIME and a TIMESTAMP with the fractional digits their type keeps, a
  BOOLEAN as `true` / `false`, a BINARY as its hexadecimal digits. A placeholder the list leaves unset is refused
  at its place in the text as written. A text compared with a FLOAT reads `inf`, `-inf` and `NaN`.
- **A VALUES clause carries no statistics.** An exact number read from one is tagged by its declared precision
  alone — `SYSTEM$TYPEOF(column1)` over `VALUES (150), (25000)` is `NUMBER(5,0)[SB4]`, where the rows' own interval
  gave `[SB2]` — and a SUM over a shifted column of it is rewritten whatever its rows hold (`SUM(column1 + 1)`
  over one row is `NUMBER(22,1)[SB16]`); a subquery or a WITH clause over the clause is merged into the query over
  it, so `SUM(x)` over `(SELECT column1 + 1 x FROM VALUES …)` is rewritten too, and an alias list renames the
  clause's columns and nothing else.
- **Refusal echoes spell a NULL as an account does.** The invalid-type sentence prints an untyped NULL as the plan
  types it, in its own spellings: `TO_OBJECT(NULL + 1)` is refused for `(SYSTEM$NULL_TO_FIXED(NULL)) + 1` and
  `TO_OBJECT(NVL(NULL, NULL) + 1)` for `(SYSTEM$NULL_TO_FIXED(NVL(COERCE(NULL AS NULL), NULL))) + 1`.
  GETVARIABLE's constant-name sentence leaves every NULL bare (`IFF(1 = 1, null, 'x')`, `HASH(null)`), and more
  names fold to the NULL or the text they are and are no refusal: `NULLIF(NULL, 'x')`, `IFF(1 = 1, NULL, NULL)`,
  `NVL2(NULL, NULL, NULL)`, `TO_CHAR(NULL)`, `NULLIFZERO(NULL)`, a simple CASE over a NULL operand.
- **The statement a SQL function's block body may not hold is named by its kind**, as an account names it:
  `Unsupported statement type for Snowscript UDF: INSERT`, where every statement was `query statement`. A query, an
  EXPLAIN and a CALL are `SELECT`; a COPY INTO a table is `INSERT` and one into a location `UNLOAD`; a CREATE, an
  ALTER and a rename are named by what they make or change where an account tells that apart (`CREATE_TABLE`,
  `CREATE_TABLE_AS_SELECT`, `CREATE_VIEW`, `ALTER_TABLE_ADD_COLUMN`, `RENAME_TABLE`, `CREATE_CONSTRAINT`, …) and
  `CREATE`, `ALTER`, `RENAME` or `DROP` otherwise; an UNDROP is `RESTORE`, an EXECUTE IMMEDIATE `UNKNOWN`; a
  cursor's OPEN, FETCH and CLOSE and an AWAIT have sentences of their own. A body that is one such statement ending
  in its semicolon (`AS $$ DELETE FROM t; $$`) is named the same way.
- **A SQL function's block body is compiled with the statement that calls it.** A RETURN, a condition or an
  assignment of the body that does not compile (`RETURN 'a' + TRUE`) refuses the calling statement with its own
  compilation error, where it read as the body's uncaught `EXPRESSION_ERROR`; a block's LET or assignment whose
  value calls the function is refused the same way, and a block's RETURN that calls it words the refusal as its own
  statement's.
- **`RAISE STATEMENT_ERROR` and `RAISE EXPRESSION_ERROR`** raise the two exceptions a script has without declaring
  them, with the code -20000, the state P0001 and no message; `WHEN STATEMENT_ERROR` catches the one and `WHEN
  EXPRESSION_ERROR` the other, a procedure's caller catches either as the CALL's `STATEMENT_ERROR`, and nothing
  catching it reads `Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6`. Both were refused as
  invalid identifiers. An undeclared exception written in quotes is named without them.
- **A variable declared by its name alone** — `DECLARE q;`, `LET q;` — is refused in an account's words, `variable
  'Q' must have either a type or an initializer to infer a type from`, at the name of a DECLARE section's item and
  at the LET, after the block's syntax and before its names; a procedure's and a function's body are refused as they
  are created. It was a syntax error at the semicolon.
- **A refusal inside a SQL function's expression body is placed in the body as written.** The lines and the blanks a
  body opens with count (`AS '\n nosuch + 1'` is line 2, position 1), and the place no longer moves with the
  statement that creates the function: a CREATE FUNCTION in a block, or after a comment, reported position 0.
- **A block's query that recovers from its own fault lets the later statements be read.** A closing parenthesis
  refused where its bracket wants an operand (`OVER (ORDER BY)`, `ABS(1,)`, `IN ()`) and the statement's semicolon
  refused where a clause wants one (`ORDER BY;`, `WHERE;`) end the statement all the same, so a fault in a later
  statement or in a handler is reported after it — where the report named the next statement's first word, or
  stopped.
- **A quoted function body that opens a block and reads as none** reports the lines an account reads on to: the
  token after the first opening parenthesis, the semicolon or a closing parenthesis where an operator wants an
  operand, a later bracket's own fault, and a word after a finished operand — `$$BEGIN RETURN 1 +; END$$` is the
  RETURN and the `;`.
- **A view's NUMBER column is told within the capacity it is declared with.** `SYSTEM$TYPEOF` of a view's `SUM(n +
  1)` column declared NUMBER(18,2) reads `[SB8]` through the view, and expressions over it follow, where the body's
  wider interval showed; an aggregate over the column, a derived table and a WITH clause keep the body's interval.
  `BASE64_ENCODE` and `HEX_ENCODE` over a binary an expression makes with no size (`TO_BINARY(…)`, a cast to
  BINARY) are VARCHAR(89478488) and VARCHAR(134217728), and a line length adds nothing over a binary.
- **Which of several correlated subqueries a statement is refused for.** One refused for its own aggregate over the
  outer row and its columns (`(SELECT MAX(t.id + g.v) FROM g)`) outranks every other shape wherever it stands, and
  of several such the last written is reported — the ORDER BY's over the WHERE's. Of the other shapes (a LIMIT, a
  query inside reading the outer row, an aggregate of the outer row alone) the first is reported, the WHERE's
  before the select list's. The last written was reported whatever the shape.
- **An operator after EXISTS inside a call's arguments** stacks the call's closing bracket, as an account does:
  `SELECT f(EXISTS (SELECT 1 FROM g)::INT)` is the `::` and the `)`, the outermost call's when calls are nested.
- **A SELECT with no select list is read on.** `SELECT FROM` is the FROM and the end of the input, `SELECT FROM
  WHERE` the FROM, the WHERE and the end, `SELECT FROM t x y` the FROM and the `y`; a query written as a value with
  no list is its SELECT alone (and its `)` after a table). Only the first token was reported.
- **A DROP's and a DESCRIBE's signature, the readings that were left.** The statement ends at its first `;`, one
  written inside brackets too (`DESCRIBE TABLE t (a;x)` is the `;` alone); a `:` after a word value is named twice;
  and a value the account reads where this grammar cannot — `x = @s!=x`, a backticked name — leaves the property
  to be judged: `invalid parameter 'x'`.
- **A task's body and an alert's condition.** A word that opens no body is refused alone (`AS VALUES (1)` is the
  VALUES); a DESCRIBE as a body is read as the DESCRIBE it is (`AS DESCRIBE TABLE t (a b) x` is the `b` and the end
  of the input); an alert's `IF (EXISTS (CREATE TABLE zz (x INT)))` is the CREATE and the last `)`, the statement's
  own brackets unread.
- **A word after a CREATE in a block** is a property's name there too: `BEGIN CREATE TABLE t (x INT) y z; END` is
  the `z` alone, and `… y; END` the `;`. The `y` was refused as a statement run in.
- **GROUP BY ALL over a select list of nothing but aggregates** is the one group an aggregate query with no GROUP
  BY is: it answers one row over an empty input too (`SELECT COUNT(*) FROM t WHERE FALSE GROUP BY ALL` is 0, where
  it answered no row), its ORDER BY is dropped (`ORDER BY MAX(id / 0)` answers the row, it divided), and a column
  its HAVING reads is `[T.ID] is not a valid group by expression`. A constant item is a key, as before.
- **What a window call names when it reads a column that is not grouped** is the argument or the key as a whole,
  as the plan holds it, once it is more than the column: `MAX(id + 1) OVER ()` beside an aggregate is `[T.ID + 1] is
  not a valid group by expression`, `MAX(ABS(id)) OVER ()` is `[ABS(T.ID)] …`. It named the column.
- **A subquery inside an aggregate or a call, as a refusal echoes it**, is re-printed from its plan: `[MAX((SELECT
  MAX(G.V) AS "MAX(G.V)" FROM G AS G))] is not a valid order by expression`, a name of the query around it as
  `CORRELATION(FZ.ID)` — in a window key and in the argument list an arity refusal tells as well. It printed
  `(subquery SELECT …)`.
- **A string LIMIT and a view's body in a statement that is not the request's first** are placed from that
  statement's own first token and line, as its other refusals are: `SELECT 1; SELECT a FROM t LIMIT 'x'` names
  position 22, `SELECT 1; CREATE VIEW v AS SELECT nosuch FROM t` position 24. A comment that opens the statement
  no longer moves a view body's place either.
- **The token after a LIMIT, OFFSET or FETCH that may be a table's alias** is reported once for each reading, as an
  account reports it: `SELECT a FROM t LIMIT -1` is the `-` twice, `LIMIT (1)` the `1` and then the `(`. After a
  table that has its alias, an ORDER BY or any other clause it is reported once, and `SELECT a FROM t ORDER BY a
  LIMIT` names the end of the input.
- **EXECUTE IMMEDIATE of a text that holds no statement** — blank, comments or semicolons alone — is `Empty SQL
  statement.`, and in a block `Executing NULL or empty statement is not permitted.` It was a syntax error at the
  end of the text.
- **A signature after every kind a DROP or a DESCRIBE names.** An account reads one after its own kinds too:
  `DROP WAREHOUSE | COMPUTE POOL | USER | ROLE | CONTACT | DATABASE ROLE | NETWORK POLICY IF EXISTS x (a)` and the
  projection, aggregation and join policies' run where they were syntax errors, and so does a DESCRIBE of a
  compute pool, a network policy and those policies. A faulty signature is reported alike after every kind —
  `DROP SECRET s ('x')` names the `'x'` and no longer the `(`, `(ARRAY(1))` is the `1` and the `)` after a
  database, a schema, a notebook and a session policy as after a table.
- **A DESCRIBE's properties after every kind.** `DESCRIBE SECRET | NETWORK RULE | NETWORK POLICY | PASSWORD POLICY
  | NOTEBOOK | STREAMLIT | ALERT x TYPE = COLUMNS` was a syntax error: a TYPE is read and changes nothing, and any
  other name is `invalid parameter 'x'` before the object is looked up — for a session policy and the other kinds
  no object is modelled of as well, where the missing object was reported first.
- **`DESCRIBE ICEBERG TABLE` over a name that reaches nothing** names the kind and the full path: `Iceberg table
  'DB.SCHEMA.T' does not exist or not authorized.` It read `Table 'T' …`.
- **A DESCRIBE's properties with no signature before them** are read as after one. `DESCRIBE TABLE t x = (a b)` is
  the `b` twice and the `)`, a CASE or a CAST where a value stands names the token after it twice, and the same
  lines are reported for a statement of a script and of a block; a block refuses a name the account does not know
  (`invalid parameter 'x'`) while it compiles, so the refusal carries no wrapper.
- **A DROP's signature in a block.** A faulty one in the body of an IF, a CASE or a loop gives the construct up, and
  what follows is read from the block around it (`BEGIN IF (TRUE) THEN DROP TABLE t (VIEW); END IF; RETURN 1; END;`
  is the VIEW and the RETURN). A word run in after a signature is refused and the block given up when the
  statement ends there: `BEGIN DROP TABLE t (a) x; RETURN 1; END;` is the `x` and the RETURN.
- **The place an uncaught failure's wrapper names** is counted past the comments a text the session sends opens
  with, as every other place of the text is: `/* c */ BEGIN EXECUTE IMMEDIATE 'SELECT 1 FROM'; END` fails "on line
  1 at position 6", and a raised exception is placed the same way. A procedure's body keeps its comments.
- **A DROP's and a DESCRIBE's signature: DEFAULT, TIMESTAMP, SELECT and BOOLEAN.** DEFAULT heading an item reads as
  a field's name does and wants a type after it: `DESCRIBE TABLE t1 (DEFAULT x)` and `(DEFAULT INT, INT)` describe
  the table where they were syntax errors at the word, `(DEFAULT)` names the `)` and `(DEFAULT 1)` the `1`. A
  `.` after the word TIMESTAMP is named twice in a DESCRIBE, `(TIMESTAMP.a)` being the dot, the dot and the `)`;
  `OBJECT(DEFAULT INT)` and `VECTOR(BOOLEAN, 3)` are read; an ARRAY's and an OBJECT's parentheses are not theirs
  before a SELECT, so `OBJECT(SELECT INT)` names the `(`; and `ARRAY(DEFAULT)` is the DEFAULT alone.
- **A fault straight after a select item's alias in a query that is not the statement's own**, the shapes that
  were left: a list of values in parentheses is read from its first comma as the FROM clause's next table
  (`(SELECT 1 x (1, 2)) WHERE a b` is the `(`, the `2` and the `b`), an empty pair before the query's end is the
  one line, an `=` or an IN after the alias is the fault and not the alias (`SELECT 1 x = 1` names the `=`), an
  EXISTS's query in a WHERE is read on from, and a joined query names the ON after a word too.
- **The lines after a call in an ANSI FROM form is refused**, the places that were left: a call that is a WHERE's,
  a HAVING's or an ORDER BY's own operand is passed over and the statement read on — a later fault and a second
  such call are reported — a cast around the call under two brackets or a CASE adds its AS before its `)`, a name
  with no FROM form of its own (TRIM) reads one bracket further out, a FOR tail under a cast names the token after
  the FOR, and a block's query reports these lines and then the block's END.
- **`DROP … PURGE` for the kinds of the account.** After a warehouse, a compute pool, a user, a role and a database
  role the word was a syntax error; an account reads it there as after any other kind and answers `DROP TABLE ...
  PURGE is only supported for unmanaged Iceberg tables in Catalog-Linked Databases. Table: '<name>'.` once the
  name is there or IF EXISTS forgives its absence, and refuses a missing one as missing. A network policy takes
  the word and drops as if it were not written, and `DROP DATABASE ROLE` reads CASCADE and RESTRICT too.
- **Another kind under the name `DROP ICEBERG TABLE` gives** is refused before anything else, with or without
  PURGE or IF EXISTS: `Object found is of type 'VIEW', not specified type 'ICEBERG_TABLE'.` — and so for a table
  that is no Iceberg table, an event table (`'EVENT_TABLE'`) and a stream. The PURGE sentence was answered there,
  and a view was refused against `'TABLE'`.
- **`DROP HYBRID …` is refused at HYBRID**, which names no kind after DROP, and a name straight after it is refused
  too: `DROP HYBRID t` is `'HYBRID'` then `'t'`. The word after HYBRID was the one named.
- **An aggregation policy and a join policy are created in the sentence a projection policy is:** `Aggregation
  policy 'AP' is successfully created.`
- **A star argument in a call the plan echoes names its columns.** `TO_JSON(DISTINCT {*})` over `fz (id, b)` is
  `invalid use of 'distinct' for function 'TO_JSON(DISTINCT OBJECT_CONSTRUCT(*)(FZ.ID, FZ.B))'`, the columns
  those an EXCLUDE or an ILIKE leaves, each under its relation's name; any other call reads them as its
  arguments, `HASH(FZ.ID, FZ.B, 1)` for `HASH(*, 1)`. The star was echoed as written.
- **The lines after a fault inside the braces of a star read as a value**, `TO_JSON({*, 1})`: after a comma the
  braces are closed where they stand and the closing brace is the token left over, twice when the last argument
  is a name; a word after the star takes the name after it and refuses the token behind; DISTINCT, a literal and
  a second EXCLUDE are followed by the brace or the parenthesis the account names; a RENAME or a REPLACE in the
  braces of a call's argument is read as under a call. The query's FROM was named in most of them.
- **A faulty query that is a statement of a BEGIN … END block** reports the lines it reports as a statement of
  its own — a select list read on after `1 AS 1`, a CAST's comma and its parenthesis, a word run into a bracket
  — and then the block's reading of the place it was given up at: the first name from there is taken and the
  token after it refused, a semicolon there gives the block up (`BEGIN SELECT UPPER(CAST(1, 2)) FROM t; END;` is
  `','`, `')'`, then the END), an END on the way closes the block, and a star's misplaced REPLACE is followed by
  the token after the first name behind it. Only the first of those lines was reported.
- **A character no token opens with is refused where it stands.** `SELECT 1 § 2` and `SELECT 😀` were `parse
  error … near '<EOF>'` at the end of the input; an account refuses the character as a token of two characters -
  the character and the one after it, so both halves of one outside the Basic Multilingual Plane - and reports
  nothing else: `unexpected '§ '` at 9. The recovery readings also run now on a text that holds such a character
  inside a string.
- **The lines after a fault inside a CAST** follow the account's in the places that were left: after LIMIT (the
  CAST twice), in a WHERE (the condition is read on), in a later item of an ORDER BY or GROUP BY, as a CASE's
  operand or inside a call in its first condition, in a scalar subquery and a VALUES row, as an operand of
  something more, left unclosed before the FROM, and after TOP n.
- **Script recovery** in more shapes: a word after the name of a DESCRIBE of any kind, a dot a DROP's name ends
  with, and `RLIKE ON t x` / `INSERT ON (t)` after a select list's comma. After a SHOW's scope and after a bare
  `DESC` the word is refused only behind a name the account keeps as a keyword (`t`, `d`); behind any other name
  it is judged when the statement runs.

- **A literal or a word run in after a script's first statement** is reported with the account's lines in the
  shapes that were left: `BEGIN 1; …` refuses the literal and reads the statements after it (it was read as a
  block to the end of the input), `COMMIT NULL` and `ROLLBACK NULL` refuse the NULL, a word after a DESCRIBE's
  name, a COPY, a LIST or a REMOVE is read and the `;` after it refused, and the statements after a word that
  follows a FETCH, a USING list or a SAMPLE are no longer reported.
- **A fault inside a query used as a value** names what an account names after the query's SELECT: the word after
  a name (`(SELECT a x y FROM t)`), nothing for a literal after a literal, `FROM` and the fault after a star, the
  cast's or the query's closing parenthesis around it, a WITH and its AS, and only the SELECT of each faulty query
  of a WHERE's IN list.
- **A refusal does not depend on what was parsed before it.** The readings a refusal makes of a rebuilt text keep
  a prediction cache of their own, as the refused statement's own second reading already did.

- **A doubled '=' is read.** `SET x == 1` and `SET (x, y) == (1, 2)` set their variables, where they were syntax
  errors; in an expression `a == b` is a call of a function named "==", refused as an account refuses it:
  `Unknown function "==".`
- **A schema's variable is refused by name.** `SET s.v = 1` and `UNSET db.s.v` were syntax errors at the dot; they
  are read, and refused as `Schema Variable 'S.V' does not exist or not authorized.{2}` - the account's sentence,
  its `{2}` included.
- **FOR UPDATE after a statement's query is read.** `SELECT … FROM t FOR UPDATE [NOWAIT | WAIT n]` was a syntax
  error at the FOR; it is `Unsupported feature 'FOR UPDATE clause'.` once the query's names have resolved, and `A
  'FOR UPDATE' clause is not compatible with 'not selecting from a table'.` for a query that reads none.
- **The lines after a refused SET name.** `SET x (1)`, `SET x, y = 1`, `SET x (1) y z` and their like reported
  the name alone; an account reads on from the first comma or closing parenthesis as its list form would stand
  there, and reports what it meets: the end of the input, the '=', the word where the '=' is missing.
- **The lines for a word where a join's table belongs.** `JOIN USING (a)`, `JOIN OFFSET 1`, `JOIN WINDOW w AS
  (…)`, `JOIN INNER n`, `JOIN ASOF n` and a dot are reported with the account's lines (a second parenthesis, the
  token twice, the end of the input).
- **The lines for a word run into a bracket's value** in a CASE, an IN list, a star's EXCLUDE / RENAME / REPLACE,
  an aggregate's DISTINCT, a scalar subquery's first item, before an '=' or an IN, before a surplus parenthesis
  and before a second bracket follow the account's; a script's last SELECT is reported as it is alone.

- **A staged-file query's arguments are read as a table function's.** `FROM @st (FILE_FORMAT => (ff))`,
  `=> IDENTIFIER('ff')`, `=> NULL`, `PATTERN => ('.*')` and an empty `()` were syntax errors; each value is now any
  expression, and one that is no constant is refused as an account refuses it, told as the tree holds it:
  `Invalid argument [FILE_FORMAT=>[unresolved function: "="](TYPE, CSV)] for table function. Table function
  argument is required to be a constant.` An option list written as the format, `(TYPE = CSV FIELD_DELIMITER =
  '|')`, is refused at the second option word. A format that does not exist, a schema that does not, and a
  pattern that is no regular expression are refused in the account's sentences, in its order; of an argument
  written twice the last counts.
- **The duplicate-row refusal prints every kind of cell as it is stored.** A TIME and a timestamp were printed
  as a query shows them, or in nanoseconds whatever the column's scale: `Row Values` now holds a TIME and a
  TIMESTAMP_NTZ / _LTZ in the units of the column's scale, a TIMESTAMP_TZ with its offset folded in, a BINARY as
  `0x414243` (`NO_VALUE` for one of no bytes) and a FLOAT in six significant digits (`1e+06`, `0.333333`, `nan`).
- **What is no table as a DML target.** A MERGE and a multi-table INSERT into a materialized view were `Object
  'MV' does not exist`; they are `MERGE operations are not allowed on materialized views` (`INSERT (MULTI-TABLE)
  operations …`). Every statement that writes rows to a dynamic table - INSERT, UPDATE, DELETE, MERGE, COPY,
  TRUNCATE - was a missing table and is `Invalid Operation INSERT on Dynamic Table` (its own word). TRUNCATE of
  a stream is `Object found is of type 'STREAM', not specified type 'TABLE'.`
- **INFORMATION_SCHEMA.TABLES tells an event table by its kind:** `TABLE_TYPE` is `EVENT TABLE`, where it was
  `BASE TABLE`.

- **A window function written with no OVER in MATCH_RECOGNIZE's MEASURES** was `Missing window specification for
  function [ROW_NUMBER()].` It is read over the rows of its match: ROW_NUMBER numbers them, RANK and DENSE_RANK
  rank them, NTILE, PERCENT_RANK and CUME_DIST divide them, NTH_VALUE and CONDITIONAL_TRUE_EVENT read the rows so
  far; under ONE ROW PER MATCH a ranking function is 1. An operator refused for its operands in a measure or a
  definition is placed where the clause writes it.
- **A table function's rows are numbered after every condition on its input.** A WHERE condition on the relations
  to the function's left was applied before the function only when it held no subquery: `FROM t, TABLE(SPLIT_TO_TABLE
  (t.c, '.')) s WHERE t.k > (SELECT MIN(k) FROM t)` numbered its rows 2 and 3, where an account numbers them 1
  and 2. A condition holding a subquery that reads no row of those relations, and an EXISTS that reads the row,
  filter the input as well.
- **The lines after a stray word in a call inside another call.** `SELECT ABS(ABS(x y)) FROM t` reported the word
  alone; an account adds the outermost call's closing parenthesis (a CAST's and an IFF's too), repeats the word
  after a name written with its relation, and reports the word alone after a literal or a call - where a second
  line was stacked.
- **A refusal named an internal stand-in.** `SELECT SHOW TABLES IN a...c` was `unexpected 'xxxxxx'`: the word a
  re-read spells a name with is never reported.

- **A string between dollar signs is a string literal wherever one may stand.** `ALTER TABLE t SET COMMENT =
  $$z$$`, a column's `COMMENT $$z$$` in an ALTER, a view's, a schema's and a stage's comment, a tag's
  `ALLOWED_VALUES $$a$$, $$b$$`, a file format's `FIELD_DELIMITER = $$|$$`, `DATE $$2020-01-01$$`, `TIMESTAMP
  $$…$$` and `INTERVAL $$1 day$$` were syntax errors at the string, and `ALTER FUNCTION f() SET COMMENT = $$c$$` an
  invalid property. The text is read again with the string spelled as the quoted string it is; where a quoted
  string is refused too - `SELECT 1 $$x$$` - the refusal still names the dollar string.
- **A correlated subquery beside a constant NULL is not judged.** `SELECT id, CASE WHEN FALSE THEN 1 END + (SELECT
  v FROM g WHERE g.id = t.id) FROM t` was refused as an unsupported subquery; the plan folds the sum to NULL -
  under `+`, `-`, `*`, `/`, `||` and a comparison, for NULL, a cast of it, and a CASE, IFF, NULLIF, COALESCE, NVL,
  ABS or TO_NUMBER over constants - so the statement answers NULL for every row.
- **A settled condition inside an aggregate's argument of a subquery read as a value.** `(SELECT MAX(IFF(tt.id =
  t.id, 'x', value)) FROM tt)` over key ranges that never meet was refused; the condition is folded there as it
  is anywhere else, and the subquery answered.
- **GROUPING over what is no key of a plain GROUP BY** - `GROUPING(n + 1)`, `GROUPING(1)`, `GROUPING(SUM(n))`,
  an argument given a name - answers 1, where it answered 0; a refusal names the call GROUPING_ID, as an account
  does (`not enough arguments for function [GROUPING_ID()]`, `Invalid function type [GROUPING_ID] for window
  function.`).
- **A CALL of a built-in function** types its column as the SELECT of the call does (`CALL UPPER('a')` is a
  VARCHAR(3), where every text was VARCHAR(134217728)) and places a refused count of arguments nowhere (`error
  line 0 at position -1`).
- **A table function written bare in a FROM clause** reports the account's second line: with two arguments, the
  second a text, the first closing parenthesis left with nothing to close.

- **The lines after a star call's misplaced RENAME or REPLACE, one bracket deeper.** A star call under ROLLUP, CUBE
  or GROUPING SETS, in a window's keys inside a QUALIFY or an ORDER BY, in the WHERE of an EXISTS, in a CTE's
  clause and in a CASE of a WHERE reported the keyword and then lines of its own. It is read as a statement's
  clause is: `SELECT 1 FROM t GROUP BY ROLLUP (HASH(* REPLACE (1 AS id)))` reports `'REPLACE'`, the `'1'` and the
  call's `')'`. A window's keys in a select item report the keyword and the token after it, a derived table's
  clause the keyword alone - but for a lone name in parentheses, after which the call's `')'` is refused - and a
  comma the text ends at adds `'<EOF>'`.
- **A block statement's refusal is placed in the statement as the block wrote it.** A place after a bind was
  counted in the text with the bind's value written out: `res := (SELECT :s + 1)` over a BINARY variable pointed
  at position 24 where an account says 11. A RESULTSET's query is counted from its opening parenthesis, so a
  refused call in `res := (SELECT ABS(:s))` is at position 8.
- **A variable holding NULL picks a user-defined function's overload by its declaration** in a block's own
  expression: `t := fo(s)` over a BOOLEAN or a VARCHAR variable holding NULL takes the VARCHAR overload beside a
  NUMBER one.
- **The types of the SYSTEM$ functions.** `SYSTEM$TYPEOF` read every SYSTEM$ call but four as `NULL[LOB]`.
  SYSTEM$GET_TAG_ALLOWED_VALUES, SYSTEM$CLUSTERING_INFORMATION, SYSTEM$ALLOWLIST, SYSTEM$GET_SNOWFLAKE_PLATFORM_INFO
  and SYSTEM$CURRENT_USER_TASK_NAME are a text of no width (`VARCHAR[LOB]`, a VARCHAR(134217728) column),
  SYSTEM$CLUSTERING_DEPTH a FLOAT and SYSTEM$LAST_CHANGE_COMMIT_TIME a NUMBER(38,0).
  SYSTEM$CURRENT_USER_TASK_NAME outside a task is the empty text, not NULL, and `SELECT NULL::ARRAY + NULL` is
  refused at its operator, position included.

- **The aggregate a misplaced subquery is refused for, as the plan computes it.** `Subquery containing
  correlated aggregate function […] can only appear in having or select clause` echoed the call as written. An
  account names what its plan holds by then: an AVG over an INTEGER is `SCALED_ROUND_INT_DIVIDE(SUM(FZ.ID),
  COUNT(FZ.ID))`, over a NUMBER(10,2) `(CAST(SUM(FZ.D) AS NUMBER(28,8))) / (COUNT(FZ.D))`, a MEDIAN is
  `PERCENTILE_CONT(CAST(FZ.ID AS NUMBER(38,3)), 0.5)`, a COUNT_IF `SUM(IFF(CAST(FZ.B AS BOOLEAN), 1, 0))` - a
  predicate kept as it is, `SUM(IFF(FZ.ID > 5, 1, 0))`, and only the values beside one cast - an
  `AVG(DISTINCT n)` casts its argument, a distinct count is `HLL_ESTIMATE(HLL_ACCUMULATE(FZ.ID))`, a
  BOOLXOR_AGG its null-guarded sum. An argument of a type the call does not take is refused for the type first,
  at the call: `Invalid argument types for function 'SUM': (BOOLEAN)` for an AVG over a BOOLEAN.
- **A name that is taken, told before the privilege to create.** Only a table, a view and a sequence refused a
  taken name ahead of `Insufficient privileges`. Every kind does on an account - a stage, a file format, a
  function, a procedure, a stream, a task, a pipe, a tag, the policies, a materialized view, an event table, a
  secret, a network rule - and names the object as the statement wrote it, `Object 'DB.S.F' already exists.` for
  a function too. A name another kind of relation holds is refused before anything is asked, under IF NOT
  EXISTS and OR REPLACE alike: `Object 'T' already exists as TABLE`, with a table and an event table told
  apart (`as EVENT_TABLE`). A rename onto a taken name is refused for the name before the privilege as well.
- **A word that names no data type, in its turn.** `Unsupported data type 'X'.` was given as soon as the word
  was met. An account reads any name where a type belongs and judges the statement's syntax first, so `CREATE
  TABLE t (n nosuch,)` is a syntax error at the parenthesis and nothing else; then the widths of the types it
  knows, so `(n nosuch, v VARCHAR(0))` is refused for the length; then the word. Several such words no longer
  make a syntax error: the first is refused, one written with parameters before one written bare. A word with
  parameters is echoed as written (`'NoSuch(5, 2)'`), a quoted one keeps its case (`'nosuch'`), and a dotted or
  quoted name with parameters is the syntax error at its parenthesis that it is on an account. A member of an
  ARRAY, an OBJECT or a MAP is read the same.
- **The order an Iceberg table's column list is refused in.** The list was judged column by column, each whole.
  An account judges it in steps, each over every column: what a type is written as - an unknown word, a length,
  a NUMBER without digits - then OBJECT, ARRAY and TIMESTAMP_TZ, then the scale of a time, then VARIANT and the
  rest. `(v VARIANT, c CHAR)` is refused for the CHAR and `(v VARIANT, t TIME(3))` for the scale; columns added
  by ALTER ICEBERG TABLE are judged for their scales first. An unknown word no longer comes before an earlier
  column's refusal.
- **The external ID of a cloud-facing object is its creation's.** An API, a Glue catalog or an SNS integration
  and an external volume showed an external ID derived from the name alone, so one created again showed the ID
  it had. As on an account a CREATE OR REPLACE and a DROP followed by a CREATE give another, an ALTER and an
  UNDROP keep it; the IAM user stays the account's one.
- **`DESCRIBE TABLE` of an Iceberg table** shows the `name mapping` column an account shows ahead of `write
  default`.
- **How a routine parameter's default is told back.** GET_DDL, and the REST body's `default_value` read from
  it, repeated the default as written. An account prints it from the plan it compiled to: `'2020-01-01'::DATE`,
  `TO_DATE('2020-01-01')` and `DATE '2020-01-01'` are all `CAST('2020-01-01' AS DATE)`, `NULL` is `null`, a bare
  `CURRENT_DATE` is `CURRENT_DATE()`, `[1, 2]` is `ARRAY_CONSTRUCT(1, 2)`, `NULL::INT` is
  `SYSTEM$NULL_TO_FIXED(null)`. A parameter declared `BINARY` reads `BINARY` there, not its width. A default
  written with a date function's unit word - `DATEADD(day, 1, '2020-01-01'::DATE)` - is no longer refused as
  reading a name.
- **A secrets list that names a variable twice.** `ALTER FUNCTION` and `ALTER PROCEDURE ... SET SECRETS = ('a' =
  s, 'a' = s)` over a routine that is not there said the routine does not exist. The list is judged before the
  routine is looked up, as on an account, and two names that differ in case alone are one: `Duplicate secret
  name 'A' passed in.`
- **A word out of place between a routine's parameters and its body.** `CREATE FUNCTION f(x NUMBER) COPY GRANTS
  COPY GRANTS RETURNS NUMBER AS 'x'` was refused at the second `COPY`. An account reads what follows the last
  clause it takes as properties, a name, an `=` and a value: the report is `'GRANTS'` and then `'NUMBER'` - the
  RETURNS being a property's name by then - and `RETURNS NUMBER LANGUAGE SQL LANGUAGE SQL` is the second `'SQL'`.
- **A quoted part of a refused dotted name.** `invalid identifier 'T."LOW"'` for `t."low"`; the part is now
  told as written, `'T."low"'`.
- **A Java body written beside IMPORTS.** A function with both an inline body and imports looked for its
  handler in the jars alone and failed with a class not found. The inline handler runs, as on an account.
- **The zone a JavaScript handler is told.** A formatter given a zone told it back as written. It now tells the
  name the account's engine keeps - `Asia/Kolkata` is `Asia/Calcutta`, `Japan` is `Asia/Tokyo`, `EST` is
  `Etc/GMT+5`, every name of UTC is `UTC` - and a session whose TIMEZONE is `Etc/GMT`, `Greenwich`, `Zulu` or
  `Universal` is told `UTC`, one set to `GMT` its offset, `+00:00`. `ALTER SESSION SET TIMEZONE = 'GMT+0'` and
  `'GMT-0'` are taken.
- **OVER after what is no call.** `invalid function` now tells a windowed value as its tree prints it -
  `(SELECT 1 AS "1" FROM (VALUES (null)) DUAL)`, `SUM(X) OVER (ORDER BY X ASC NULLS LAST)`, `"COUNT(*)"() OVER
  ()`, `ROW(1, 2)` - and of two kinds of function refused the one written first is named: `SELECT ABS(x) OVER
  (), x::INT OVER ()` is `[ABS]`. An OVER with no window names the token after it (`SELECT x OVER w` is `'w'`),
  a `!` followed by no method's name names that token and then the `!`, and `b IS UNKNOWN` is refused at its
  `IS`.
- **A star, a DISTINCT or an ALL given a name.** `COUNT(x => *) OVER ()` reported the star and then a
  parenthesis of the window; an account reports the star alone, the parenthesis after it closing the call.
  What follows a star otherwise is read on as an account reads it: `COUNT(x => *, 1)` is `'*'`, `','` and the
  call's `')'`, `COUNT(x => *, y => 1, 2)` is `'*'`, `','`, `'=>'` and `')'`, inside another call that call's own
  parenthesis is the one named, and `COUNT(x => DISTINCT n, y => 1)` is `'DISTINCT'` and the `'=>'`.
- **A null treatment inside a call's parentheses.** `SUM(n IGNORE NULLS) OVER ()` was refused at the closing
  parenthesis of the window, `LAG(n IGNORE NULLS) OVER (ORDER BY n)` at the `ORDER`, and a call outside a window
  named the word twice. Only FIRST_VALUE and LAST_VALUE take the treatment there; for every other function the
  word is where the report starts and, but for LAG, LEAD and NTH_VALUE, where it ends. Those three are read as
  closed at the word, so the parenthesis after it is named too - their own, or the one of a call around them:
  `ABS(LAG(n IGNORE NULLS) OVER (ORDER BY n))` is `'IGNORE'` and the last `')'`.
- **APPROX_PERCENTILE's percentile.** A percentile that was no plain number failed inside the engine -
  `Character n is neither a decimal digit number...` for a column, `Index 2 out of bounds for length 2` for
  `2` - and a call with named arguments was refused for its names first. The percentile is now judged as an
  account judges it, after the argument count and before the names: a number outside 0 to 1 by its value, a
  text, a NULL or a BOOLEAN as `Percentile must be a numeric value.`, anything computed as `Percentile must be a
  constant numberic value between 0 and 1 inclusive.` with the expression echoed from the plan - `RT2.N`,
  `0.5 + 0.1`, `CAST(0.5 AS FLOAT)`.
- **A time-travel or change-tracking point that reads a column.** In a query the refusal carried no place; it
  is now `error line 1 at position 73` and `invalid identifier 'ID'`, at the name. A CREATE STREAM's point and a
  clone's said invalid identifier too, where an account resolves the source table's column and refuses it as
  no constant: `argument TIMESTAMP to function AT needs to be constant, found 'CAST(CT_CONST.ID AS
  TIMESTAMP_LTZ(0))'`, `argument OFFSET to function AT needs to be constant, found 'NEGATE(CT_CONST.ID)'`,
  `argument JOBID to function AT ...` for a STATEMENT.
- **How DESCRIBE spells a policy's signature.** A masking or a row access policy over a vector was listed as
  `(VAL VECTOR)` and one over a structured type with full types inside - `(VAL ARRAY(NUMBER(38,0)))`. The
  signature now reads as a routine's does: `(VAL VECTOR(FLOAT, 3))`, `(VAL ARRAY(NUMBER))`, `(VAL OBJECT(a NUMBER
  NOT NULL))`.
- **What USING TEMPLATE makes of a text answer.** A text was refused as `template must be a non-null JSON
  array` whatever it held. An account reads it as JSON: the text of an array of column definitions creates the
  table, and a text that is no JSON is refused with the reader's sentence and place - `Invalid template:
  Unrecognized token 'x': was expecting (JSON String, Number, Array, Object or token 'null', 'true' or
  'false')`, then ` at [Source: (String)"x"; line: 1, column: 2]`. A fault of the template's query is placed
  where it stands in the whole statement, not in the query alone.
- **IS_ROLE_IN_SESSION and secondary roles.** A role held only through `USE SECONDARY ROLES` was not in the
  session. It is now, with every role it holds, as on an account.
- **A clause written out of place after a key constraint.** A MATCH or an ON UPDATE / ON DELETE rule after a
  property, a second MATCH, a VALIDATE with no ENABLE before it, and any of them after a PRIMARY KEY or a UNIQUE
  were refused at the right word in some orders and at a later one in others - `RELY ON DELETE NO ACTION` at
  the `NO`, `ON UPDATE CASCADE MATCH PARTIAL ON DELETE SET NULL` at the `SET` - and one line was reported. The
  report now starts at the first word that breaks the order, and in a CREATE TABLE's column list reads on as an
  account does: `NOT ENFORCED MATCH SIMPLE)` is `'MATCH'`, `'SIMPLE'` and `')'`, `NOT ENFORCED ON DELETE NO
  ACTION)` is `'ON'`, `'DELETE'`, `'NO'`, `'ACTION'` and `')'`. An ALTER TABLE names the one word.
- **A scope after SHOW LOCKS and SHOW TRANSACTIONS.** `SHOW LOCKS IN DATABASE abc` was refused at `DATABASE`
  alone. An account names the token after it too when the two could be a kind and its name, or two names:
  `'DATABASE'` then `'abc'`, `'abc'` then `'def'` - not a number or a `$variable`, and nothing after `TABLE`.
- **The type of a text DECODE without a default.** `DECODE(n, 1, 'x')` was `VARCHAR(1)`. No default is a default
  of NULL, read as a CASE's missing ELSE is, so the type is `VARCHAR(134217728)` as on an account; with a
  default it stays the widest result.
- **How a refusal echoes a cast into or out of a shape.** An invalid-type sentence spelled the cast as written -
  `CAST(CAST(ARRAY_CONSTRUCT(1, 2) AS ARRAY(NUMBER(38,0))) AS DATE)`. An account names a cast whose source or
  target is a structured ARRAY, OBJECT or MAP `STRUCTURED_CAST(x)`, the target unspelled, and a vector read into
  an array `FROM_VECTOR(x)`, by a cast and by `TO_ARRAY` alike: `CAST(STRUCTURED_CAST(ARRAY_CONSTRUCT(1, 2)) AS
  DATE)`, `CAST(FROM_VECTOR(VT.V) AS NUMBER(38,0))`. `TO_ARRAY`, `TO_OBJECT` and `TO_VARIANT` over a value that is
  one already are `identity(x)`; in an arity or a predicate sentence they are dropped, and `TO_ARRAY` over a
  vector is `CAST(x AS ARRAY)`.
- **A comma before the first option of a parenthesized option list.** `CREATE STAGE s DIRECTORY = (, ENABLE =
  TRUE)` was refused at the comma alone. An account names each leading comma and then the parenthesis closing
  the list, and reports nothing after it - in a stage's DIRECTORY, FILE_FORMAT, ENCRYPTION, COPY_OPTIONS and
  CREDENTIALS, a table's staging properties, ALTER STAGE and COPY INTO. A list left open has no line for a
  parenthesis: `(,` names the comma and the end of input, `(, ENABLE = TRUE` the comma alone. In a list of
  values the value after the comma is named too: `NULL_IF = (, 'a')` is `','` and `''a''`.
- **REST: a listed stage is the whole stage.** `GET /api/v2/…/stages` answered each stage without its file
  format and copy options and with one directory table property. An account answers the body a fetch of the
  stage does, and so does the list now. Measured with it: an external stage's `cloud` is `AWS` / `AZURE` as SHOW
  STAGES spells it, an internal one's null, and the directory table carries no time of its last refresh.
- **The lines a faulty type parameter list is refused with.** Only the first unexpected token was reported. As
  on an account a cast names the first token inside an ARRAY's or an OBJECT's parentheses twice (`NULL::ARRAY(5)`
  is `'5'` and `'5'` again), and a list of definitions - a table's columns, a routine's or a policy's
  parameters, an OBJECT's fields - reads on after a fault one token would not mend: `CREATE TABLE t (a
  VARCHAR(5,2))` is refused at the `,`, at the `2` and at the list's own `)`. An ALTER TABLE's column reports
  the comma and the number, a scalar RETURNS and a declaration the comma alone.
- **The words around a materialized view's clustering key.** `CREATE MATERIALIZED VIEW mv CLUSTER BY (x) COPY
  GRANTS AS …` was refused at `COPY`; the word is read as a property's name, so the fault is the `GRANTS` after
  it, as on an account. Under ALTER the clustering actions - CLUSTER BY, DROP CLUSTERING KEY, SUSPEND / RESUME
  RECLUSTER - name a view that is not there a `Table`, the other actions a `Materialized view`.
- **The properties a materialized view is created with were a syntax error.** `CREATE MATERIALIZED VIEW mv
  CHANGE_TRACKING = FALSE AS …` did not parse. The `name = value` properties are now read and judged as an
  account judges them: an unknown name is `invalid property 'FOO' for 'TABLE'`, a value of another type `invalid
  value [5] for parameter 'CHANGE_TRACKING'`, another kind of table's property, schema evolution and a file
  location `Unsupported feature 'AUTO_REFRESH'.`, a parameter of another level `cannot set parameter 'TIMEZONE'
  at 'MATERIALIZED_VIEW'`, a table's parameter a schema the statement may not operate on, and `ERROR_LOGGING`,
  change tracking switched on and a row timestamp switched on `invalid property … for 'MATERIALIZED_VIEW'`. A
  table's staging properties, `CHANGE_TRACKING = FALSE`, `ROW_TIMESTAMP = FALSE` and a metric schedule are taken.
- **Which of several refused property names is named.** A CREATE TABLE, VIEW or MATERIALIZED VIEW with two
  unknown properties named the first written. An account names neither the first nor the last but the first of
  a table hashed by name - `A = 1 P = 2` and `P = 1 A = 2` both answer `invalid property 'P' for 'TABLE'` - and
  the same holds for two values of another type and two parameters of another level. A view judges its names
  class by class in that order too, so `ERROR_LOGGING = TRUE TIMEZONE = 'UTC'` is refused for the TIMEZONE.
- **`COPY = …` among a table's or a view's properties.** It was `invalid property 'COPY' for 'TABLE'`. COPY is
  a word of the statement that takes no value: a number or TRUE / FALSE is `invalid value [1] for parameter
  'COPY'`, a text, a word or a list `invalid type of property ''x'' for 'COPY'`.
- **A view's `ROW_TIMESTAMP = FALSE`, and another kind of table's property on a view.** The first was refused
  as an invalid property and is taken, as on an account, which refuses the row timestamp only switched on.
  `AUTO_REFRESH`, `LOCATION` and the like were `invalid property … for 'TABLE'` and are `options AUTO_REFRESH
  and {1} are incompatible.`, as a table's staging properties already were; `ENABLE_SCHEMA_EVOLUTION = 5` is
  that too, its value unread.
- **How a structured parameter is spelled where a signature is shown.** DESCRIBE FUNCTION / PROCEDURE's
  `signature`, SHOW's `arguments` and INFORMATION_SCHEMA's `ARGUMENT_SIGNATURE` printed the full types inside a
  shape - `(X OBJECT(a NUMBER(38,0) NOT NULL, b VARCHAR(16777216)))`. An account spells them bare and keeps a
  field's NOT NULL: `(X OBJECT(a NUMBER NOT NULL, b VARCHAR))`, `ARRAY(NUMBER)`, `MAP(VARCHAR, ARRAY(FLOAT))`. A
  VECTOR keeps its element type and size in DESCRIBE and SHOW - `VECTOR(INT, 3)`, where it was a bare `VECTOR` -
  and SHOW's `RETURN` part of a structured result is its shape, `RETURN OBJECT(a NUMBER)`.
- **A positional argument after a named one is refused by the call's name.** `SELECT f(a => 1, 2)` was a syntax
  error at the `2`. The call is read whole now and refused as an account refuses it, while the statement
  compiles: `positional argument cannot follow named argument in function call 'F'`, at the argument - a
  built-in, an aggregate, a window function, a user's function and a name nobody defined alike, before any table
  or column of the statement is looked up and after every syntax error. The place is the argument's own: the `+`
  of `f(a => 1, 1 + 1)`, the `::` of a cast, the first token of anything else. A SQL function's body is refused
  for it at CREATE.
- **Of two miscounted calls in one FROM-less item, the first written is the one refused.** `SELECT ABS(1, 2) +
  f(1)` over a function `f` that takes no argument named `F(1)`, and `f(ABS(1, 2))` the outer call: a user's
  function was judged before any built-in. The calls are judged in the order written, each after the calls its
  arguments hold, as in a query over a table. `CREATE FUNCTION … AS 'ABS(1, 2) + f(1)'` names the ABS too.
- **A miscounted call inside a block's expression is placed where the account places it.** `BEGIN RETURN 1 +
  ABS(1, 2); END;` reported `error line 1 at position 7` whatever the expression, and a condition's or a CASE's
  miscounted call no compilation error at all. The place counts from the expression's own start: position 4 for
  that RETURN, 11 for `RETURN 1 + f(1)` - an expression holding a user's function is compiled as a query, seven
  columns on - eight more for a value given to a variable, and each variable ahead of the call written out as its
  type. The echo writes a variable as its bound value's conversion, `[ABS(TO_NUMBER('1'), 2)]`. An untyped LET's
  value and a CASE statement's operand are refused while the block compiles, bare and placed in the block, on a
  branch that never runs too. `Invalid argument types for function 'F'` of a user's function takes the same
  seven columns.
- **INFORMATION_SCHEMA views for a role that is no administrator.** Reading `INFORMATION_SCHEMA.TABLES`,
  `COLUMNS` or `FUNCTIONS` as a user without an administrative role failed with a raw
  `java.lang.SecurityException` - each view was held to a SELECT grant no role can be given. The views need no
  grant now, as on an account, and a grant on one is still refused.
- **INFORMATION_SCHEMA lists what the role reaches.** `TABLES`, `COLUMNS`, `VIEWS`, `TABLE_CONSTRAINTS`,
  `REFERENTIAL_CONSTRAINTS`, `FUNCTIONS`, `PROCEDURES`, `SEQUENCES`, `STAGES`, `FILE_FORMATS` and `SCHEMATA` listed
  every object of the database to every role. As on an account they list only what the statement's rights own
  or hold a privilege on, in the schemas and the database those rights were granted into - ACCOUNTADMIN is not
  exempt - and inside a stored procedure that runs with owner's rights, what the owner reaches. A session that
  reads these views as a role other than the objects' owner sees fewer rows than before; the SHOW commands
  still list every object, as the account's do.
- **A refusal of FLATTEN or SPLIT_TO_TABLE names the call as it is written.** The echo was the function's name
  in capitals. It is the first part of the call's name as written - `[flatten]`, `[my_schema]`, `["My"]` - which
  is what an account echoes, since a qualifier before a built-in table function's name is not looked up: the
  built-in runs whatever schema the name is written under.
- **A decimal below a millionth in JSON text and in a refusal's echo.** `ARRAY_CONSTRUCT(0.0000001)` read
  `[1E-7]`, `TO_JSON` and `OBJECT_CONSTRUCT` likewise, and a refusal echoed the literal as `ABS(1E-7, 1)`. The
  digits stay in place, `[0.0000001]` and `ABS(0.0000001, 1)`, as an account writes them.
- **Paths with two positions in a row, quoted names and bracketed names.** FLATTEN's `PATH` applied only the
  first position of a step, so `PATH => 'a[0][1]'` flattened `a[0]`, and read no quoted name at all; GET_PATH split
  its path on every dot. Both now read one path grammar, the account's: a bare name, a quoted one (`"a b"`), a
  bracketed one (`['a b']`, `["a b"]`) and a position (`[0]`), joined by `.` or `:`, with a backslash escape and a
  doubled quote inside the quotes. `PATH => '"a b".c'`, `'k[''m''].n'` and `'a[0][1]'` select what the same steps
  select after a colon, a name reaches into an object only and a position into an array only, and a NULL `PATH` is
  no path instead of an internal error.
- **FLATTEN's PATH column for a member that is no plain name.** It joined every name with a dot, `a b.c[0]`. A
  name that is empty, starts with a digit or holds anything but letters, digits and underscores now goes between
  brackets and quotes, `['a b'].c[0]`, `['a.b'][0]`, `['0']`, with a quote, a backslash and a line break escaped -
  for the members FLATTEN walks and for the path a `PATH` argument selected, which is spelled this way whatever
  spelling it was written in (`PATH => 'k[''m'']'` reports `k.m.n`).
- **GENERATOR counts past a 32-bit number.** A count was narrowed to 32 bits, so `ROWCOUNT => 4294967297`
  generated one row and `2147483648` was refused as negative. A count is read whole: with a `TIMELIMIT` the rows
  stop at whichever bound comes first, a `TIMELIMIT` of 0 is no limit, a fraction is cut to its whole part, and
  a count above 2147483647 with no time limit - more rows than the engine can hold - is refused by name instead
  of wrapped.
- **Names and keywords under a JVM whose default locale folds case its own way.** Function names, keywords,
  identifiers, type names and format elements were folded with `toUpperCase()` / `toLowerCase()` in the JVM's
  default locale. Under a Turkish default locale `insert` folds to `İNSERT` and `TITLE` to `tıtle`, so statements
  failed with unknown functions, columns and types, and texts read differently. Every fold in the engine and the
  optional packs is now in the root locale - 1,302 calls - and the build refuses a new one.
- **A SQL function's call costs a fraction of what it did.** Every call of a SQL function parsed the function's
  body twice as a script, to learn that it was no scripting block, before it did anything else: over 20,000 rows
  `SUM(addone(i))` took 4 seconds where `SUM(i + 1)` took 5 milliseconds, and a function that calls another took
  25. That answer is now kept per body, and expressions and statements are parsed in two stages — a quick
  prediction first, the full one only for the rare text the quick one gives up on, with the same tree either
  way — which every statement gains from, not only a function's body. The same calls take 76 ms (an expression
  body), 290 ms (a query body, 1.1 s before) and 220 ms (the nested one); a call with a constant argument 33 ms
  where it took 3.6 s. What a call answers is unchanged.
- **Table functions written in Java and JavaScript run.** A RETURNS TABLE function in Java, JavaScript or Scala
  was created, and every call ran its body as SQL: `SELECT * FROM TABLE(jt(5))` was `syntax error line 2 at
  position 0 unexpected 'import'`. A Java table function now runs its handler class - `process` for each input
  row, `endPartition` once a partition is through, the output class's public fields the declared columns by
  name, whatever their case and order - and a JavaScript one its handler object - `initialize`, `processRow(row,
  rowWriter, context)` and `finalize`, the rows those written with `rowWriter.writeRow`, a row's columns the
  object's members of the declared columns' upper-case names. An argument and a written value are converted as a
  scalar function's of the language are; what a handler raises is the call's refusal in the language's own
  report. `SHOW USER FUNCTIONS` and the catalog are as they were.
- **`TABLE(f(…) OVER (PARTITION BY … ORDER BY …))` is the partitioning of a table function's input.** Every such
  call was refused as a window call, `Invalid function type [F] for window function`. A handler table function -
  Java, JavaScript or Python - now reads its input by the partitions the clause names, in the order it asks for:
  one handler per partition, each row it writes joined to the row it was reading, and the rows it writes at the
  partition's end joined to the partition's own columns with NULL in every other. `OVER ()` and an ORDER BY alone
  make one partition; a key that is no column is `Invalid partitioning expression 'UPPER(EV.G)'; must be a
  column.` and a frame a syntax error. So `TABLE(py_sum(ev.v) OVER (PARTITION BY ev.g))` answers one total a
  group, where each row got a handler and an `end_partition` of its own. Without OVER a JavaScript function now
  reads the whole input as one partition, as an account did; a Java or a Python function still reads each row
  as its own. A SQL or a built-in table function takes the clause to no effect.
- **The SEQ functions count beside a window function.** In a query whose select list holds a window function
  SEQ1, SEQ2, SEQ4 and SEQ8 answered 0 on every row - in the select list, and in the window's own arguments and
  keys, so `ROW_NUMBER() OVER (ORDER BY SEQ4() DESC)` did not reverse and `SELECT SEQ4(), SUM(SEQ4()) OVER ()
  FROM TABLE(GENERATOR(ROWCOUNT => 3))` answered `0 | 0` three times. They number the rows the projection is
  handed there as anywhere else: `0 | 3`, `1 | 3`, `2 | 3`.
- **A lateral item keeps its columns when its argument would fail for a NULL.** To learn a lateral item's columns
  the engine read it once with every column to its left bound to NULL, and when that reading failed it planned
  the item with no columns at all. So over rows that hold no NULL, `SELECT w.id, f.value FROM w, LATERAL
  FLATTEN(INPUT => jsplit(w.s)) f` was `invalid identifier 'F.VALUE'` whenever `jsplit` - a JavaScript, Python,
  Java or Scala function - was not null-safe, and so was a FLATTEN over `PARSE_JSON(COALESCE(j.s, '{'))`, a
  SPLIT_TO_TABLE over `SUBSTR(w.s, 1 / COALESCE(w.id, 0))` or any argument that divides by a column; `f.*` was
  `Object 'F' does not exist` and `SELECT *` answered rows wider than its own column list. A table function's
  arguments are now read for the shape without a row behind them: the names they use are judged, a fault a NULL
  raises is not the statement's, and the item has the columns its function declares. A handler in the argument
  is no longer called an extra time with NULLs for the shape - a counter it keeps saw three calls for two rows -
  and a Python table function called laterally has the columns its RETURNS TABLE declares without its handler
  being run for them, so one that raises on `None` no longer loses its columns.
  What a row's own value raises now reads in its own words, without `Unable to evaluate expression '…' in
  LATERAL context:` before it.
- **A Scala function's exception, and a Scala body that does not compile, say what went wrong.** A handler's
  exception reached SQL as `Failed to execute Scala function F: null`, and a body that does not compile as `Scala
  compilation failed for H` while the compiler's complaint went to the engine's console. An exception now reads
  as an account reports it - `User Error Report:`, `Java Stack Trace:`, the exception, its frames
  `function_handler_0//H$.f(ScalaUDF:1)` and, for an object, the forwarder `function_handler_0//H.f(ScalaUDF)`,
  its cause, and ` in function F with handler H.f` - and a compile error is the compiler's own: `ScalaUDF:1:
  error: type mismatch;`, ` found   : String`, ` required: Int`, the line and a mark under the place, for a
  function and a procedure.
- **An inline Java body's compile error is the compiler's whole report.** The refusal quoted the compiler's
  messages alone. It is now what an account answers: `Error while compiling source: /InlineCode.java:4: error:
  incompatible types: String cannot be converted to int`, the body's line and a mark under the place, each
  further fault, the compiler's notes, `2 errors`, and ` in function F with handler H.f` - with the line counted
  from the body's first line.
- **A Java procedure is compiled once.** Every CALL of an inline Java procedure wrote its source to a temporary
  directory and ran the compiler over the whole class path, about 33 ms a CALL on a warm engine. The body is now
  compiled when the procedure is created - or at its first CALL - and the classes are kept, as a Java
  function's are: twenty CALLs took 640 ms and take 3. A body that does not compile is refused with `Error while
  compiling source: …` at a CALL too, the sentence CREATE gives.
- **A Java handler's class is the statement's or the CALL's own.** A Java function's class was loaded once and
  kept, so what a handler left in a static field was there for every later statement of the JVM, another
  session's included; a procedure's instance was new for every CALL and its class too, at the cost above. On an
  account a procedure's static and instance fields both start as the source writes them on every CALL, and a
  function's static fields are kept over the rows one call of the function reads and by nothing else: `jfc(1)`
  over a counter answers 1 in every statement, `SELECT jfc(1), jfc(2)` answers `1 | 1`, and `MAX(jfc(v))` over
  three rows 3. The engine now loads the kept classes anew for each CALL and for each statement and calling
  place, about 0.07 ms a statement; a query's rows cost what they did.
- **Two inline sources that share a hash run their own code.** The compiled classes of Java and Scala handlers
  were kept under the handler's class name and the source's 32-bit hash, so of two bodies that differ only in
  texts sharing a hash - `"Aa"` and `"BB"` - the second ran the first one's code: `SELECT hc1(1), hc2(1)`
  answered `Aa | Aa`. The whole source names a compilation now, and the answer is `Aa | BB`.
- **The Snowpark `Variant` a Java handler compiles against is the real class.** The engine's
  `com.snowflake.snowpark_java.types.Variant` had four constructors and two methods, so a handler that read a
  VARIANT with `asMap()`, `asString()` or `asInt()`, or built one from a list, a date or bytes, did not compile
  and CREATE FUNCTION refused it. It now has the real class's sixteen constructors - `float`, `double`, `long`,
  `int`, `short`, `boolean`, `BigDecimal`, `BigInteger`, `String`, `byte[]`, `java.sql.Time`, `Date` and
  `Timestamp`, `List<Object>`, `Object[]` and `Object` - and its readers: `asString`, `asJsonString`,
  `asJsonNode`, `asBinary`, `asTime`, `asDate`, `asTimestamp`, `asBoolean`, `asFloat`, `asDouble`, `asShort`,
  `asInt`, `asLong`, `asBigDecimal`, `asBigInteger`, `asArray`, `asList`, `asMap`, `equals`, `hashCode` and
  `toString`, each answering as the account's does. A value answers by what it was made as: one made from a text
  answers every reader, every value answers as a text, a number answers as a whole or a real number and as a
  timestamp, a boolean as a number, and a date, a time, a timestamp, bytes, a list and a map as their own kind
  alone - any other reading raises `Conversion from Variant of Date to FixedNumber is not supported`, and
  `asMap` or `asList` over anything else `Failed to convert s to Map`. The earlier `Variant(Map)`,
  `Variant(Number)` and `Variant(Boolean)` overloads, which the real class lacks, are gone: `Variant(Object)`
  takes those values. What changes for a handler that already compiled: `new Variant(text)` no longer throws
  for a text that is no JSON - the value is the text - and reads only the text's first value, and `toString()`
  is `asString()`, a text without its quotes.
- **A VARIANT argument arrives in a Java handler as its kind, and a `Variant` handed back answers as its
  kind.** Every argument arrived as its JSON text read back. An exact number now arrives as a decimal, a FLOAT
  as a double, a BOOLEAN as a boolean, a BINARY as bytes and a DATE, a TIME and a timestamp as their `java.sql`
  values; a text, an OBJECT, an ARRAY and a JSON null arrive as their text, which is read as JSON - so
  `TO_VARIANT('12')` is the number 12 in the handler, `TO_VARIANT('{"a":1}')` an object, and an array holding a
  SQL NULL, whose text `[1,undefined,"x"]` is no JSON, the text. Handed back, a number is an INTEGER, a DECIMAL
  or a DOUBLE, a boolean a BOOLEAN, bytes a BINARY and a `java.sql` value a DATE, a TIME or a TIMESTAMP_NTZ
  where each was the VARCHAR of its JSON; a value made from a text is the OBJECT, the ARRAY or the JSON null it
  holds and a VARCHAR otherwise, so `new Variant("7")` answers the text `7`. A handler runs in the session's
  time zone as far as a `Variant` goes: a date, a time and a timestamp are held as their instant's wall clock
  there, so under `America/Los_Angeles` a DATE argument reads `2024-03-04` for 2024-03-05 and comes back as
  that day, as on an account. A procedure's `Variant` parameter and result follow the same rules.
- **A Java or Scala function answers its RETURNS type.** Numbers, texts and booleans passed as they were and
  anything else became its Java text: a `byte[]` under RETURNS BINARY was `[B@1cc8416a`, a `String[]` under
  RETURNS ARRAY `[Ljava.lang.String;@…`, a `Map` under RETURNS OBJECT `{k=v}`, and a text returned for an ARRAY
  stayed a text, so a container nested it quoted. Each is now the declared type's value: bytes a BINARY, a
  `String[]` an ARRAY of texts (a null element one of its SQL NULLs), a `Map` an OBJECT, a text read as JSON for
  an ARRAY, an OBJECT or a VARIANT and as a number for a NUMBER or a FLOAT, a `BigDecimal` rounded half to even
  to the declared scale (`fdec(1.25)` under NUMBER(10,2) is 1.39), a `short` a whole number, and a
  `java.sql.Date`, `Time` or `Timestamp` the DATE, the TIME or the timestamp it counts at UTC, nanoseconds kept.
  A Scala `Array[String]` and `Map[String, String]` follow.
- **A Java function's DATE, TIME and timestamp arguments hold the account's instants.** A `java.sql.Date` was the
  machine's local midnight and a `java.sql.Timestamp` of a TIMESTAMP_NTZ the machine's local reading, so
  `getTime()` depended on where the engine ran. As on an account, and as a procedure's arguments already were, a
  DATE is its midnight at UTC, a TIME the milliseconds of its day and a TIMESTAMP_NTZ its wall clock read at UTC,
  whatever the session's zone — and handed back, each is the value it was.
- **A Java or Scala procedure declared RETURNS TABLE answers its DataFrame's rows.** The CALL answered one row
  holding the DataFrame object's text (`com.snowflake.snowpark_java.DataFrame@…`) and `SELECT * FROM TABLE(p())`
  failed on it. The rows are now the DataFrame's, under the declared columns' names, for a CALL and in a FROM
  clause; a DataFrame of another shape than the declaration is `Stored procedure execution error: data type of
  returned table does not match expected returned table type`.
- **A Scala function or procedure finds a handler JAR an internal named stage keeps.** `IMPORTS =
  ('@st/handlers.jar')` worked for a Java routine and failed for a Scala one with `Handler class … not found …
  [MISSING FILE]`: the stage's file was looked for as a relative path. A handler loaded from IMPORTS is now found
  where the engine keeps the stage's files, whatever the routine's language.
- **A Java function's handler may be an instance method.** Every handler was called without an instance, so a
  method that is not static failed with a `NullPointerException` on every call. The class is now made with its
  zero-argument constructor and the method called on that instance, one for a statement that calls the function:
  what the instance holds is there for the statement's later rows and for nothing else. A method the class
  inherits is the handler too.
- **A Java function's arguments become the handler's parameter types.** The handler was looked for by the classes
  of the values the engine held, so a pairing the CREATE had accepted failed on every call with `Method not
  found: f in class H`: a number for a VARCHAR parameter, a text for a NUMBER one, a BOOLEAN, a NUMBER or a FLOAT
  for a `String`, a BINARY for a `byte[]`, a DATE for a `java.sql.Date`, a timestamp for a `java.sql.Timestamp`,
  an OBJECT for a `Map<String, String>` and a VARIANT for a `Variant`. As on an account, each argument now
  becomes its parameter's SQL type first — `fs(42)` is `s:42`, `fi('42')` 42, and a NUMBER parameter rounds a
  fraction away where Java truncated it: `fi(1.99)` is 2 and `fi(-1.5)` -2, for a Scala `Int` too — and then the
  Java type the handler declares. A `String` holds the value's text for any type (a NUMBER(10,2) as `7.00`, a
  TIME as `10:11:12.500000000Z`, a timestamp to the millisecond with its offset, a BINARY as hex, a VARIANT as its
  JSON); a `BigDecimal` holds the parameter's declared scale; a `Map` and a `String[]` hold a text as it is,
  anything else as its JSON, and a JSON null or a SQL NULL element as `null`.
- **An exception a Java handler raises reads as an account reports it.** It read `Java function handler f threw:
  java.lang.IllegalArgumentException: bad 1`. It is now `User Error Report:`, a line `Java Stack Trace:`, the
  exception, its frames down to the handler's own — `at function_handler_0//H.f(InlineCode.java:1)`, the line the
  source's — a cause as Java prints one, and ` in function F with handler H.f`.
- **A JavaScript function's globals are its own call's, for one statement.** Every JavaScript function called on a
  thread ran in one shared world, which only the handler's own name was taken out of: a global one function set
  was there for any other function, for later statements and — since a server's sessions share its threads — for
  another session's functions; a member added to `Array.prototype`, or a replaced `Math.max`, stayed for all of
  them. As on an account, each call of a function written in a statement now runs in a world made for that
  statement: its globals are there for the statement's later rows and gone with the statement, and neither
  another function, another call of the same function in the statement, a later statement, a procedure nor
  another session sees them. A statement that calls a JavaScript function costs about a millisecond more for the
  world it makes; a call's cost per row is unchanged.
- **A JavaScript handler reaches no Java object.** The JavaScript context allowed every public member of any Java
  object that reached a handler, and several did: a procedure's result set and statement were the engine's own
  objects (`rs.getClass()` answered), a column of a NUMBER with a fraction, a DATE, a TIMESTAMP, a BINARY or a
  VARIANT was a Java value with Java's methods and none of the language's, and a failed statement was caught as
  a Java exception. Host access is now limited to the members the engine exports for its own wrapper script, and
  nothing of the host is handed to a handler: a BINARY argument crosses as text, and the procedure API's objects
  are made in the handler's world, as an account's are — a `ResultSet` and a `Statement` that print as `[object
  Object]`. A column's value is a number, a string, a boolean, `null`, a `Date`, a `Uint8Array`, an array or an
  object, and a member of the result set under the column's name once a row is read. A statement that fails is
  thrown as an object that is no `Error`, with the statement's `code`, `state` and `message` and the place of the
  call in `stackTraceTxt` (`Snowflake.execute, line 1 position 11`); uncaught, it fails the CALL as an account
  words it: `Execution error in store procedure P:`, the statement's sentence, `At Snowflake.execute, line 1
  position 11`.
- **An inline Java function whose source declares more than one class.** Only the handler's class was loaded, so
  a function that used a nested class, an enum, an anonymous class or another top-level class of its own source
  was created and then failed on every call with `NoClassDefFoundError`. Every class the source declares is
  loaded with the handler's.
- **A JavaScript handler's VARIANT, ARRAY and OBJECT arguments arrive as native values.** An array that held a
  SQL NULL element — `ARRAY_CONSTRUCT(1, NULL, 'x')`, at any depth of the argument — reached a function's handler
  as its text (an ARRAY parameter wrapped that string in a one-element array), because the text spelled the
  element `undefined`, which is not JSON, and the failed parse was swallowed. The handler now gets the array, the
  element a hole of it as on an account: `A.length` is 3, `A[1]` is `undefined` and `1 in A` is false, while a
  JSON null stays `null`. A procedure's handler found a `null` there and finds the hole too. What the value
  holds arrives as its own kind, in a function and a procedure alike: a DATE and a TIMESTAMP as a `Date` (a DATE
  the midnight that begins it in the session's time zone), a BINARY as a `Uint8Array`, and a FLOAT NaN or
  infinity as that number, where each was a text. A variant that is no array is a one-element array for a
  function's ARRAY parameter, and a JSON null is a SQL NULL for an ARRAY or an OBJECT one.
- **A JavaScript function answers its RETURNS type.** What a handler returned came back nearly as it was, so
  `RETURNS VARCHAR` over `return 5` was a number and over `return {a: 1}` the object's JSON, and a FLOAT could
  come back a text. Each declared type now converts the value as an account does: a VARCHAR is its string (`1,2`
  for an array, `[object Object]` for an object without a `toString`), a FLOAT its number (`NaN` for a text that
  is none), a BOOLEAN its truthiness, a DATE and a TIMESTAMP the `Date` or the milliseconds it converts to. A
  VARIANT, an ARRAY and an OBJECT are a copy of the value, member by member: a `Date` is a TIMESTAMP_LTZ and a
  `Uint8Array` a BINARY, a number with a fraction a DOUBLE, a function `{}`, an `undefined` element one of the
  array's SQL NULLs (dropped where it trails), an `undefined` member left out, `null` a JSON null, and `toJSON`
  is not called. A value handed in therefore comes back as it arrived — `rv(ARRAY_CONSTRUCT(1, NULL, 2))` is
  `[1,undefined,2]`, and a DATE inside it a timestamp. A procedure's answer follows the same copy (it never
  calls `toJSON` either and unwraps a `Number` or a `String` object), keeping a `Date` and a `Uint8Array` as
  their texts, and its OBJECT takes any object that is no array.
- **A JavaScript function's arguments arrive as their parameters' types.** A JavaScript function's handler was
  handed each argument as the engine held it: a decimal for a FLOAT parameter was a host object, so `X + 1`
  concatenated (`0.1 + 1` was `0.11`) and handing it back failed; a DATE or a timestamp had no `getFullYear()`; a
  number or a date for a VARCHAR parameter was no string; `'no'` for a BOOLEAN parameter was truthy; a BINARY was
  a signed byte array. As on an account — and as a procedure's CALL already did — each argument now becomes its
  parameter's SQL type first (a text that does not convert is the call's refusal, `Numeric value 'abc' is not
  recognized`) and then the handler's own: a FLOAT is a number, a VARCHAR a string, a DATE the `Date` of its
  midnight in the session's time zone, a TIMESTAMP_NTZ the `Date` of its wall clock there, a TIMESTAMP_LTZ and a
  TIMESTAMP_TZ their instants, a BINARY a `Uint8Array`, a BOOLEAN the number 1 or 0 (`X === true` is false, as on
  an account) and a SQL NULL `undefined`. A `Date` handed back for a DATE or a timestamp, a `Uint8Array` for a
  BINARY and a truthy value for a BOOLEAN are those values.
- **A SQL table function's number columns hold what they declare.** The result columns took the declared RETURNS
  TABLE types while the rows stayed as the body produced them, so an `INT` column held `3.333333` — and `WHERE a =
  3` found nothing — and a `NUMBER(5,2)` column held `1.257`. Each value of a column declared an exact NUMBER is
  now held at the declared scale, rounded half away from zero as a write rounds it: `3`, `1.26`, and `1.50` for
  `1.5` — in a direct and a lateral call, an aggregate, a UNION, a view, a CTAS and an INSERT. A text keeps its
  characters whatever width the column declares, as on an account.
- **A session's temporary objects end with it.** A temporary table, view, function, procedure or stage was
  dropped only when the whole engine shut down, so on the HTTP server and over JDBC it stayed for every later
  session, kept its name taken, and a long-running server collected every client's temporary tables. Each is now
  dropped when the session that created it ends: a released, an expired or an aborted session of the server, a
  closed connection (`jdbc:frostlake:direct:` and `file:` alike), and a task's run, which is a session of its
  own. A permanent table a temporary one hid is there again, and the session's open transaction is rolled back
  first. The embedded engine's own session still ends at shutdown. While its session lives a temporary object is
  still visible to the other sessions of the engine, where an account shows it to its own session alone.
- **ALTER TABLE ADD COLUMN echoes a default's function as the plan names it.** `ADD COLUMN c NUMBER DEFAULT
  test_schema.f1() + 1` was refused as `[test_schema.f1() + 1]`; it is `Invalid column default expression [F1() +
  1]`, and a default that calls a name no function has is an unknown function.
- **A SQL function's parameter is read only where a value stands.** A parameter's name was replaced wherever the
  body spelled it, so a function, a table, a common table expression, an alias, a named argument's name or a type
  of that name broke the body: `f(upper VARCHAR) AS 'upper(upper)'`, `f(people NUMBER) AS 'SELECT COUNT(*) FROM
  people'`, `f(v NUMBER) AS 'SELECT 5 AS v'`, `f(input VARCHAR) AS 'SELECT … FLATTEN(input => PARSE_JSON(input))'`
  and `f(date VARCHAR) AS 'date::DATE'` all failed when called. The body is now bound by its parse tree: a
  parameter stands where a column could be read by its bare name, and there it comes before a column of that name
  (`MAX(id)` over a parameter `id` is the argument, `people.id` the column). A parameter in a GROUP BY or an
  ORDER BY is its value, which a number names a position with. A parameter is named in upper case however it was
  declared, so `"X"` reads the parameter `x`. A query body whose `ON a.id = id` meets a parameter `id` is no
  longer refused as an ambiguous column when the function is created.
- **A parameter named like a word of another parameter's value.** The parameters were bound one after another, so
  one named `number`, `date`, `varchar` or `timestamp_ntz` rewrote the type the previous parameter's value was
  written with: `f(a NUMBER(10,2), number NUMBER)` failed for every call. All parameters are bound in one pass.
- **Named arguments choose the overload.** An overload was chosen by the values in the order written, against each
  overload's parameters in the order declared, and the names were read afterwards: `pick(y => 5)` beside
  `pick(x NUMBER)` failed on an unknown argument name, and `ov(b => 1, a => 'x')` ran the overload whose `a` is a
  NUMBER. Only an overload that declares every name written is now a candidate, and each value is judged against
  the parameter its name fills — for a function, and for a procedure's `CALL`, which read a named argument as an
  untyped NULL. Names whose only overloads refuse a value's type are `named arguments [X] do not match any
  signature for function PICK`, and that sentence names the routine as the call writes it (`TEST_SCHEMA.PICK`).
  A name written twice is refused before the number of arguments is.
- **Overloads are chosen by all the arguments together.** Between overloads that take the argument count, the
  first argument decided and the others only broke its ties, so `f('x', 'y')` over `f(a NUMBER, b VARCHAR)` and
  `f(a VARCHAR, b NUMBER)` ran the second. A call now runs the overload whose conversions weigh least in total,
  as an account does — a NUMBER read as a FLOAT weighs less than one read as a text, a text read as a BOOLEAN
  less than one read as a FLOAT or a NUMBER, a FLOAT read as a NUMBER most of all — and of overloads that weigh
  alike the first in the order of their signatures, whatever order they were created in. Procedures choose the
  same way.
- **A function called by its full name with no current database.** `SELECT db.schema.f(1)` was an unknown
  user-defined function in a session without a current database — one opened without one, or whose database was
  dropped — while the same schema's tables and table functions answered. A name written with its database needs
  no session context and is found there.
- **A SQL function's expression body may name a date part and bind a lambda.** `CREATE FUNCTION … AS 'DATEADD(day,
  1, t)'` was refused with `invalid identifier 'DAY'`, and so was every other unit word of DATEADD, DATEDIFF,
  DATE_TRUNC, DATE_PART, TIMEADD, TIMEDIFF, TIMESTAMPADD, TIMESTAMPDIFF and LAST_DAY, and the parameter of a
  lambda (`FILTER(a, x -> x > 1)`, `TRANSFORM`, `REDUCE`): the check of a body's names read each as a name the
  signature had to declare. A unit is the function's word and a lambda's parameters are names of its body, so
  these functions are created and answer as their query-bodied twins did. Where a lambda's parameter and the
  function's share a name, the function's is the one the body reads, as on an account.
- **A SQL function's body reads with its owner's privileges.** A role granted USAGE on a function was refused the
  table the body reads — by a scalar function with a query body or a subquery, by a table function, by a function
  called from another's body — where a view over the same table answered. The body now reads what its owner may
  read, beside what the caller may; what neither may read is refused as the statement's own, in the caller's
  name. `CURRENT_ROLE()` inside the body is still the caller's.
- **A SQL function's parameter holds its argument's value.** A SQL function's body was run with each parameter
  written out as its argument's display text, so the value was only right when that text read back as the same
  value of the same type. In a scalar function a FLOAT parameter computed as a NUMBER (`x / 3` over `1` answered
  `0.333333`), a TIME lost its fraction and a timestamp everything past the millisecond, a date followed
  `DATE_OUTPUT_FORMAT` (`MONTH(d)` of 1 February answered `1` under `DD/MM/YYYY`, and the call failed under
  `MON DD, YYYY`), a BINARY arrived as text, and a VARIANT holding a DATE or a BINARY was refused. In a table
  function a DATE became the arithmetic `2024 - 01 - 15`, a TIME, a timestamp, a VARIANT and an OBJECT were
  syntax errors, an ARRAY of texts, a BINARY and a NaN unknown names, and a backslash in a text was read again as
  an escape. Every argument is now written as SQL that answers the value itself: a FLOAT as a FLOAT, temporals
  with their nine digits and offset whatever the output formats, a BINARY as one, a semi-structured value with
  what it holds. In a scalar function a NULL argument is the NULL of the parameter's type, a number takes the
  parameter's scale when its own is smaller (`1` into a NUMBER(10,2) is `1.00` of that type), and a parameter
  declared `TIMESTAMP_NTZ(3)` reads three digits. A negative argument is one value beside an operator: `2-x` over
  `-5` is `7`, where the text read `2--5`, a comment.
- **An exception declared with the empty string as its message** keeps the ` : ` tail of its uncaught sentence
  (`Uncaught exception of type 'E1' on line 1 at position 42 : `); only one declared with no message at all ends
  at the place.
- **A semi-structured variable holds the JSON of its value.** A VARIANT, ARRAY or OBJECT variable of a block —
  and a SQL procedure's parameter, a FETCH or SELECT INTO target, a field of a loop's record, a value returned
  under a semi-structured RETURNS — kept whatever it was given, so `TYPEOF(v)` of a variable holding
  `TO_VARIANT('2024-01-02'::DATE)` answered `DATE`. It holds the JSON of the value read back, as on an account:
  a DATE, a TIME, a timestamp, a BINARY and a UUID in it are text (`VARCHAR`), whole or inside an array or an
  object, and a DOUBLE is the number its sixteen digits of JSON say. A date assigned to an ARRAY variable is
  its one element.
- **A DOUBLE stays a DOUBLE in a block's function call.** A call in a block expression is evaluated from its
  arguments' values, and a VARIANT holding `3.5::FLOAT` was written back out as `3.5`, a DECIMAL to the function:
  `TYPEOF(v)` answered `DECIMAL` and `TO_JSON(v)` lost the fifteen-decimal form, for a FLOAT variable or
  parameter handed to `TO_VARIANT` too. The value now reads back with its family, and what a call written as
  another call's argument answers reaches the outer call with its members' types:
  `TYPEOF(GET(ARRAY_CONSTRUCT('2024-01-02'::DATE), 0))` is `DATE` in a block as in a query.
- **ASYNC takes any statement, and its job runs when it is awaited.** `ASYNC (…)` took DML and a CALL only and ran
  them where it stood. It takes any one statement — a query, DDL, a SHOW, an EXECUTE IMMEDIATE, a block — and a
  RESULTSET is filled by one: `r RESULTSET DEFAULT ASYNC (…)`, `LET r RESULTSET := ASYNC (…)` and
  `r := ASYNC (…)`. The job binds the block's variables as they stand at the ASYNC and runs at `AWAIT r` or
  `AWAIT ALL`, in the
  order the jobs were started; `AWAIT ALL` stops at the first job that fails, reports it where that job's
  statement stands and leaves the later jobs to be awaited again, and a job the body never waits for never
  runs. After `AWAIT r` the counts are the job's; after `AWAIT ALL` they are NULL.
- **`$name` in a block is the session's variable.** In a block's own expressions — a RETURN, an assignment, a
  CASE operand, a FOR bound, a function's argument — `$x` read a block variable, a parameter or a loop counter
  named `x` when there was one, while the same `$x` in a statement of the block read the session's. It reads
  the session variable everywhere; the block's `x` is `x` or `:x`.
- **A cursor's name belongs to its block.** An inner block, a called procedure and a dynamic block could not
  declare a cursor named like one of the calling block's (`Cursor already declared: C`). Each declares the name
  for itself, the outer cursor keeps its position, and it is the name's again when the inner block ends. A
  RESULTSET declared in an inner block no longer replaces the outer one of its name, and a block nested in a FOR
  loop over a cursor reads the loop's record, where it was `invalid identifier 'R'`.
- **EXIT is BREAK's other spelling.** `EXIT;` and `EXIT label;` were syntax errors.
- **An exception declaration belongs to its block.** Declarations were kept in one map for the session: an inner
  block, a called procedure, a dynamic block or a scripting function that declared an exception of a name its
  caller used replaced the caller's, and a later `RAISE e1` there raised the other one's code and message. A
  declaration now hides an outer one only while its own block runs, and a body run from a block declares for
  itself alone. A handler catches the declaration its block sees: an `e1` that a nested block or a called
  procedure declared for itself and let out is caught by OTHER (and by STATEMENT_ERROR when a CALL let it out),
  not by the caller's `WHEN e1`.
- **A handler clause takes EXIT, and several names joined by OR.** `WHEN STATEMENT_ERROR OR e1 EXIT THEN …` was
  a syntax error at `OR` and at `EXIT`. EXIT says what a handler does by default; CONTINUE goes on with the
  statement after the one that failed.
- **Runaway nesting fails as a statement does.** A text that ran itself (`SET q = 'EXECUTE IMMEDIATE $q';
  EXECUTE IMMEDIATE $q`) and a cycle of SQL functions made by `ALTER FUNCTION … RENAME TO`, or written with
  qualified names, ended in a `StackOverflowError`: no handler caught it, the history held no row and the
  statement's writes were not treated as a failed statement's. Dynamic texts now nest a hundred deep and the
  statement past that fails with `SQL execution internal error: Processing aborted: the statement nests deeper
  than the engine can follow.`; a call that meets a SQL function again inside that function's own body is
  refused as `Detected a cycle in SQL UDF: F` (an account abandons such a call as an internal failure of its
  own). Both are ordinary failures: a handler catches them and the session goes on.
- **SQLCODE and SQLSTATE of two more refusals.** `Invalid expression [1 / 0] in VALUES clause` reads 2014 / 22000
  and `Error parsing JSON: …` 100069 / 22P02 in a handler, where both read 100351 / P0000.
- **A CALL of a JavaScript, Python, Java or Scala procedure answers its RETURNS type.** The result was a VARCHAR
  column holding the handler's value as text. It is one row in a column named after the procedure and typed as
  declared — `NUMBER(10,2)`, `FLOAT`, `BOOLEAN`, `DATE`, `TIMESTAMP_NTZ`, `VARIANT`, `ARRAY`, `OBJECT`, `BINARY` —
  with the value converted the way each runtime does on an account: a JavaScript body's by `String(…)`,
  `Number(…)` and truthiness, a `Date` or a number of milliseconds for a date or a timestamp, a `Uint8Array` for
  BINARY; a Python body's by `int(…)` (7.5 is 7), `float(…)`, `str(…)` and `bool(…)`, a `Decimal` rounded half to
  even to the declared scale; a Java or a Scala handler's `BigDecimal` rounded half to even, its `String` read as
  a SQL cast reads it, its `java.sql.Date`, `Time` and `Timestamp`, `String[]`, `Map` and `byte[]` as a date, a
  time, a timestamp, an ARRAY, an OBJECT and a BINARY. The column keeps a declared width — `VARCHAR(2)`,
  `BINARY(2)` — and a BINARY declared without one reads `BINARY(67108864)`.
- **What a handler's semi-structured answer holds.** Inside a VARIANT, an ARRAY or an OBJECT a JavaScript number
  with a fraction (or from 1e21 on) and every Python float is a DOUBLE — `[1.500000000000000e+00,2]` —, a NaN and
  an infinity stay themselves, a JavaScript `Date` is the text of its instant in the session's zone (`2024-01-01
  19:04:05.678 -0800`), a `Uint8Array` its hex text, a Python date, time, datetime, `Decimal` and `bytes` the text
  `str(…)` gives, and an object's members come in key order. A JavaScript `null` and a Python `None` returned for
  a VARIANT are a JSON null (`IS NULL` is false and TYPEOF is `NULL_VALUE`), where `undefined` and a Java `null`
  are a SQL NULL; a text a Java or a Scala handler returns for a VARIANT is read as PARSE_JSON reads one, so
  `1.5e0` is a DOUBLE and `[undefined]` keeps its element.
- **A handler that returns nothing answers a NULL.** A JavaScript, Python, Java or Scala procedure whose handler
  returned `null`, `None`, `undefined` or nothing at all answered a `status` column reading `Statement executed
  successfully.`; its CALL answers one row holding NULL in the procedure's column, of the declared type, as a SQL
  body's does.
- **Overloads of which one has optional arguments resolve.** With `f(a NUMBER)` and `f(a VARCHAR, b NUMBER DEFAULT
  0)` — a legal pair — both `f(1)` and `f('x')` were `Unknown function F.`: only overloads declaring exactly the
  call's number of parameters were considered.
- **An owner's-rights CALL from a session with no current database leaves it with none.** After the CALL the
  session sat in the procedure's database and schema, so a bare `CREATE TABLE t (…)` created its table there; the
  session is where it was — nowhere — and the statement is refused again as `Cannot perform CREATE TABLE. This
  session does not have a current database. Call 'USE DATABASE', or use a qualified name.`
- **A USE in a procedure that runs with its caller's rights stays in force.** A SQL body's `USE SCHEMA` was undone
  when the CALL returned, while its SET and its ALTER SESSION stayed; the caller now finds the session where the
  body left it, as it does after a handler's USE.
- **A RETURN, a BREAK or a CONTINUE inside a branch or a loop of an exception handler ends the handler.** `…
  EXCEPTION WHEN OTHER THEN IF (c) THEN RETURN 'a'; END IF; RETURN 'b'; END` answered `b` with `c` true: the
  handler ran on to its last statement.
- **A RAISE that names nothing raises the handled exception again from a loop or a branch of the handler too.**
  Written inside an IF or a FOR of the handler it raised `Error raised`.
- **Where a failure inside a handler, a called procedure or a dynamic text is placed.** A statement of a handler
  that fails is named at its own place, where the block named the statement that failed first. A procedure called
  from a block reports the places of its own body (they read `position 0`). A block run by EXECUTE IMMEDIATE words
  what it lets out as a script does, and the statement that ran it adds its own sentence: `Uncaught exception of
  type 'STATEMENT_ERROR' on line 1 at position 53 : Uncaught exception of type 'STATEMENT_ERROR' on line 1 at
  position 6 : Division by zero`.
- **An unnamed bind in a query that is a scripting expression's value** — `LET y := (SELECT ?)` — is refused as `
  Unexpected unnamed bind in SQL stored procedure.`, where it read `in {2}.` as a statement's query does.
- **A Snowscript UDF's refusal prints its expression from the tree as parsed.** `Unsupported expression for Snowscript
  UDF: …` named the expression as the plan holds it, with the type check's rewrites in it, where an account names it
  as parsed. `CASE WHEN nosuch(1) = 1 THEN 2 ELSE 3 END` now reads `CASE((NOSUCH(1)) = 1, 2, 3)` (was
  `CASE_FLATTENED(…)`), `nosuch(1)::INT` reads `CAST(NOSUCH(1) AS NUMBER(38,0))` (was `… AS INT`),
  `nosuch(a) BETWEEN 1 AND 2` reads `BETWEEN(NOSUCH(A), 1, 2)` (was the two comparisons), `COALESCE`, `NVL2`,
  `DATEADD`, `MEDIAN` and `COUNT_IF` keep their names, `IFF(TRUE, …)` takes no cast, `nosuch(a => 1)` reads
  `NOSUCH(1)`, `"noSuch"(1)` keeps its quotes, `CURRENT_DATE` stays the keyword it was written as and `a != 1`
  keeps its spelling. With a query or a window call in it the expression prints every operator as a call —
  `"+"((SELECT 1 AS "1" FROM (VALUES (null)) DUAL), NOSUCH(1))`, `"="(1, ANY(SELECT …))`, `"COUNT(*)"() OVER ()` —
  and a query prints with no name resolved, `(SELECT MAX(A) AS "MAX(A)" FROM RC_T AS RC_T)`, its clauses in the
  order the account's tree keeps them, a set operation over operands named `OB$n`.
- **The width of a routine's RETURNS TABLE columns.** A column declared as a text or a binary with no width —
  `RETURNS TABLE (a VARCHAR, b BINARY)` — was read at a table column's default width, 16777216 and 8388608. It
  is of no width of its own, as on an account: a driver reads `VARCHAR(134217728)` and `BINARY(67108864)` for it
  in a `CALL`, in `TABLE(p())`, over the CALL's `RESULT_SCAN` and from a SQL table function, a view over it and
  what is computed from it follow, and a table created over it still declares `VARCHAR(16777216)` and
  `BINARY(8388608)`. A width that is written is kept, the default one included. `SYSTEM$TYPEOF` over a table
  procedure's column in FROM names its type, where it answered `NULL`, and `GET_DDL` writes a routine's return
  columns back as they were declared — `VARCHAR` beside `VARCHAR(16777216)`, `BINARY` beside `BINARY(8388608)`.
- **Where a SHOW statement's syntax error is reported.** A listing that reads no scope — `SHOW VARIABLES IN
  db.s`, `SHOW ORGANIZATION ACCOUNTS IN DATABASE d` — reported its `IN` and then the name after it; an account
  reports the `IN` alone. `SHOW REPLICATION` and `SHOW FAILOVER` written alone are refused at the end of the
  statement, where the listing's second word is missing, and `SHOW GROUPS`, `SHOW MONITORS`, `SHOW AUTHORIZATIONS`,
  `SHOW REPOSITORIES` and `SHOW POLICIES` at the word itself, which was reported at the end. A `STARTS WITH` that a
  listing does not read reports a second line at its prefix only straight after the listing's own words:
  `SHOW VARIABLES LIKE 'a' STARTS WITH 'b'` is the one line at `STARTS`.
- **UPDATE and DELETE over rows of the same values.** A statement that matched several rows whose values were all
  the same wrote only the first of them and still reported every one: `INSERT INTO t VALUES (1), (1), (1); DELETE
  FROM t` answered 3 and left two rows, and `UPDATE t SET c = c + 1` left 1, 1, 2. Each row is now written at its
  own position, outside a transaction and inside one. Rows that differ in any column were never affected.
- **SYSTEM$ABORT_TRANSACTION ends any open transaction.** It ended only the caller's own and answered `Transaction
  aborted.`; for another session's transaction it answered `Could not abort txn: <id>` and left it open. It now
  ends the transaction the id names, whichever session holds it — over HTTP and over in-process JDBC connections
  alike — rolls its writes back, releases its locks and answers `Aborted transaction id: <id>`. A text argument is
  read as the number it spells, a number with a fraction and a value in a VARIANT answer `Bad transaction id`, NULL
  answers NULL, and the answer is typed VARCHAR.
- **A failed COMMIT or ROLLBACK through `Connection.commit()` on an in-process connection** surfaced as an unchecked
  exception; it is an `SQLException` now, as a failed statement's is.

- **BASE64_ENCODE's width with a line length.** `BASE64_ENCODE('abcdef', 2)` declared VARCHAR(32), the width without
  the line breaks the call inserts, so a table created from it held values longer than its column declared. The
  width counts the breaks: W + ceil(W / L) - 1 for an integer literal L below the unbroken width W (VARCHAR(47)
  here), W for a 0 or a longer line, and 2W - 1 when the line length is anything but an integer literal.
- **A grouped MIN or MAX's storage tag.** `SELECT SYSTEM$TYPEOF(MAX(v)) FROM g GROUP BY k` was tagged by one end of
  the column's interval (`NUMBER(6,0)[SB1]`), as an ungrouped MAX rightly is. Under a grouping key, a HAVING or a
  WHERE each answer may be any value of the interval, so the whole interval tags it (`[SB2]`), and a SUM over such a
  column of a derived table is rewritten as one over a column that varies.
- **The width a rewritten SUM reports.** `SELECT SUM(n + 1) FROM t` over a NUMBER(5,2) reported NUMBER(18,2) in its
  result's columns, where only SYSTEM$TYPEOF knew the NUMBER(24,2) the plan computes it at. The result's own column,
  DESCRIBE RESULT and a RESULT_SCAN of it now report the rewritten width; a table created from the query, a view, a
  derived table and a set operation's operand keep the declared one.
- **What a block's handler reads for a refusal.** A syntax error in the text EXECUTE IMMEDIATE runs went to `WHEN
  OTHER`; it is the statement's STATEMENT_ERROR. SQLCODE and SQLSTATE read 100351 / P0000 for nearly every refusal:
  they now read the refusal's family — 1003 / 42000 for a syntax error (a string LIMIT and a routine body's
  included), 90203 / 42601 for a SQL function's or a policy's body that does not compile, 904 / 42000 for an invalid
  identifier, 2002 / 42710 for a name already taken and 1430 / 42601 for a column's, 2139 / 02000 for an unknown
  function or procedure, 2043 / 02000 for a schema USE cannot enter, 938 and 939 / 22023 for a wrong argument count,
  100038 / 22018 for a text that is no number, 100040 / 22007 for one that is no date, 90150 / 22000 for a scalar
  subquery of several rows and 1104 / 42601 for a column outside the grouping. SQLERRM is NULL for an exception
  declared without a message, where it read the exception's class name.
- **A subquery of several columns as a function's argument.** `SELECT COUNT((SELECT 1, 2)) FROM g`, `SELECT MAX((SELECT
  1, 2))` and the same under a WHERE, a HAVING or a QUALIFY were refused as `Unsupported: Scalar subquery with
  multi-column SELECT clause`. The subquery is the ROW of its items and the function refuses it in its family's
  words: `Invalid argument types for function 'COUNT': (ROW(NUMBER(1,0), NUMBER(1,0)))` at the call — AVG as SUM,
  STDDEV and VARIANCE as COUNT, APPROX_COUNT_DISTINCT as HLL_ACCUMULATE, COUNT_IF as IFF —, `Function MAX does not
  support ROW(…) argument type` for MIN, MAX, MODE and the constructors, `incompatible types` for MEDIAN, and for a
  conversion `invalid type [TO_VARCHAR((SELECT 1 AS "1", 2 AS "2" FROM (VALUES (NULL)) DUAL))] for parameter
  'TO_VARCHAR'` with the subquery printed from its plan. A sign over one is `NEGATE`, a CASE result converts to
  nothing, and SYSTEM$TYPEOF places the subquery's own sentence at its argument.
- **A DECLARE section after a block's statements.** `BEGIN RETURN 1; DECLARE q INT; END` was refused at the end of the
  input. It is refused at the END written where the section's BEGIN belongs, and nothing after it is read — in an
  anonymous block, in a procedure's, a function's and a task's body, quoted or not.
- **A function's block that does not compile.** `CREATE FUNCTION f() RETURNS INT AS BEGIN RETURN (SELECT a FROM t LIMIT
  'x'); END` was refused as an expression a Snowscript function may not hold. The block does not compile, so the
  body is read as the function's expression and refused where that frame stops: `Compilation of SQL UDF failed:` and
  the RETURN and the SELECT, in the frame's positions. A cursor a LET declares in a function's block is refused as a
  cursor declaration.
- **Any statement as a task's body or an alert's action.** `CREATE TASK t AS CREATE OR REPLACE TABLE x (a INT)` was a
  syntax error at the CREATE: a body had to be a query, DML, a CALL, an EXECUTE IMMEDIATE or a block. A CREATE, an
  ALTER or a DROP, a TRUNCATE, a SHOW or a DESCRIBE, a USE, a SET or an UNSET, a GRANT, a COMMIT, an EXPLAIN, a LIST
  and a COMMENT ON are bodies too, after `ALTER TASK … MODIFY AS`, an alert's THEN and `ALTER ALERT … MODIFY ACTION` as
  well. The body is stored as written, without its `;` and unjudged, runs when the task or the alert does, and a
  fault in it is reported where that statement reports it: `… AS DROP TABLE t7 x` names the x, as `… AS INSERT INTO t
  VALUES (1, 2) x` now does too, and `… AS DESCRIBE` the DESCRIBE.
- **A subquery refused inside a SQL function's arguments.** `SELECT f1((SELECT MAX(fz.id + g.v) FROM g)) FROM fz` was
  refused with the place a built-in call's argument gets. Among a SQL function's arguments — under a built-in call,
  a CASE, a cast or an operator too, in any clause, in DML and in a SQL table function's arguments — it is
  `Unsupported subquery type cannot be evaluated inside Function object: F1`, the innermost such function as it is
  stored and no place. A function in another language keeps the placed sentence. Of several subqueries the account
  cannot evaluate the one written last in the select list is now reported, and with none there the last of the
  first other clause, where the first one written was: `SELECT ABS((SELECT …)), ABS((SELECT …))` is placed at the
  second.
- **A DROP's or DESCRIBE's signature read by the account's tokens.** Where this lexer reads a text otherwise than the
  account's, the lines of a faulty signature were the parse's own. A stage reference is one token up to its
  delimiter — `DESCRIBE TABLE t (@"s")` names `@"s"`, `(@s!=x)` names `@s!=x`, `(@s<>x)` the `@s` and then the `)` —
  `|`, `^`, `&` and a lone `$` are tokens of one character, a character with no token is named with the one after
  it (`(é b)` is `'é '` and then the `)`), a backticked name is a name, and `1.5.6` and `0x1F` are one token each. A
  text the account's lexer refuses — an unclosed string, quoted name or comment, a number that stops after its
  exponent's letter or its second point — adds that lexer's line among the others, where the reading first looks
  behind it: `DESCRIBE TABLE t ((a), b) /* x` is the `(`, the first `)`, `parse error … near '<EOF>'` and the second
  `)`. After a property list's comma a token that names no property is named and passed over (`x = 1, 2, y = 3` is
  the 2 alone), a word value followed by a token no rule reads is refused twice (`x = y +`), and a binary string is
  a property's value (`x = X'AB'` is refused for its name, not as a syntax error).
- **A SQL function's body refused at CREATE.** A string LIMIT in the body (`… AS 'SELECT a FROM t LIMIT ''x'''`) was
  refused without the account's `Compilation of SQL UDF failed: ` before it, and every refusal of a body was
  placed as if the blanks that open the body were not there: `$$⏎SELECT nosuch FROM t$$` reported line 1 at
  position 8. The syntax refusal carries the prefix, and a refusal is placed where the body writes it — line 2 at
  position 7, and position 10 for `'  SELECT nosuch FROM t'` — for a table function too.
- **The place of a later statement's refusal in a request of several statements.** `SELECT 1; SELECT nosuch FROM
  t` reported the invalid identifier at position 17, counted from the request's start, and on line 2 when the
  statement stood on the request's second line. A statement's refusal is placed from the statement's own first
  token — position 7, line 1 — for a name, a type, an arity and a QUALIFY without a window alike; the sentence
  naming the failing statement still places it in the request, a comment that opens the request not counted.
- **The place of a refusal raised inside an aggregate's argument.** `SELECT b, MAX(IFF(b, (SELECT MAX(t.id + g.v)
  FROM g), 0)) FROM t GROUP BY b` was `Unsupported subquery type cannot be evaluated` with no place. A refusal raised
  while a grouped query computes an aggregate's argument is placed in the statement — `at line 1, position 22`, the
  subquery's SELECT — in the select list and in ORDER BY, on the line the argument is written on.
- **The aggregate echoed in a DISTINCT query's ORDER BY refusal.** `SELECT DISTINCT b FROM t GROUP BY b ORDER BY
  MAX(id + 1)` was `[MAX(id + 1)] is not a valid order by expression`, the key as written. It is echoed as the
  plan holds it: `[MAX(T.ID + 1)]`, the column qualified by its relation or its alias, a cast spelled out
  (`[MAX(UPPER(CAST(T.ID AS VARCHAR(134217728))))]`) and a column the select list publishes by its bare name.
- **The order a select with no FROM is judged in.** `SELECT (SELECT 'x' + TRUE), nosuch` reported the subquery's
  argument types, `SELECT 'o' + TRUE, nosuch` and `SELECT UPPER(1, 2), nosuch` the first item's own fault, and
  `SELECT (SELECT 'x' + TRUE), (SELECT nosuch)` the second subquery's name. Such a select is now judged as one over
  a relation is: the names of every item, of the WHERE and of the ORDER BY first (`invalid identifier 'NOSUCH'`),
  then each subquery whole in the order written, the ORDER BY's included, and only then the select's own types.
- **The ORDER BY of an aggregate query with no GROUP BY.** Such a query answers one row, and the account drops its
  ORDER BY once the keys' names, argument types and ordinals have been judged. Frostlake computed the keys: `SELECT
  MAX(id) FROM t ORDER BY MAX(id / 0)` was `Division by zero`, a key holding a subquery of several rows `Single-row
  subquery returns more than one row`, a correlated one an unsupported subquery, and under DISTINCT a key outside
  the select list `not a valid order by expression`. Each now answers the one row — with a HAVING, a QUALIFY or a
  LIMIT beside it and inside a derived table too. A GROUP BY query keeps its ORDER BY, one group or not, and a
  name that does not resolve is still refused.
- **A DESCRIBE's properties.** A comma between two properties (`DESCRIBE TABLE t TYPE = STAGE, TYPE = COLUMNS`,
  the last TYPE winning) and a `$$` string as a value (`TYPE = $$COLUMNS$$`) were syntax errors and are read. So
  are a list in parentheses — an empty one too — a bind (`?`, `:b`) and a stage reference as a value, which the
  account reads before refusing the property by its name (`invalid parameter 'x'`); under TYPE a bind is `Bind
  variable ? not set.` and a NULL `invalid value [NULL] for parameter 'TYPE'`. The statement is read whole before
  any name is judged: `DESCRIBE TABLE t x = TRUE y` is the syntax error at the end of the text, not the invalid
  parameter.
- **A signature after the name of every DROP and DESCRIBE.** `DROP SEQUENCE IF EXISTS s (a)`, `DROP SECRET IF EXISTS
  s ()` and the same after a database, a stream, a task, a pipe, a tag, a file format, a masking or row access
  policy, an alert, a network rule, a password policy, a notebook, a streamlit, a search service and a dynamic or
  Iceberg table were syntax errors, and so were their DESCRIBE counterparts and `DESCRIBE EVENT TABLE t (a)`. The
  account reads a signature after every kind's name but a stage's, where the parenthesis is the statement's one
  line. `DESCRIBE MATERIALIZED VIEW mv ((a), b)` and `DESCRIBE DYNAMIC TABLE dt ((a), b)` report their third line.
- **Places in the text of EXECUTE IMMEDIATE after a comment that opens the request.** `/* c */ EXECUTE IMMEDIATE
  'SELECT 1 FROM'` reported its fault at position 5, the comment's width taken off; the text's places are its own
  (13), for a syntax error and for a refusal placed while the text compiles (`error line 1 at position 7`).
- **The braced star where a value stands.** `{*}` was read as a whole select item only, so `TO_JSON({*})`,
  `ARRAY_CONSTRUCT({*})`, `{*}:ID`, `{*}::VARCHAR`, `CASE WHEN … THEN {*} END` and `{t.* EXCLUDE (a)}` inside a call
  were syntax errors. It is now a value wherever one stands — the row as an OBJECT, `OBJECT_CONSTRUCT` over the
  star's columns — named in the column as written (`TO_JSON({* EXCLUDE B})`), with one EXCLUDE or one ILIKE at most.
  Like a star argument it stands in the select list and in GROUP BY: in a WHERE or an ORDER BY it is `Use of * as a
  function argument is only allowed in the SELECT clause.`
- **DROP … PURGE.** PURGE after a DROP was a syntax error at the word. It is read where CASCADE or RESTRICT stand —
  one word of the three at most — and refused as the account refuses it, once the name has resolved: `SQL
  Compilation Error: DROP TABLE ... PURGE is only supported for unmanaged Iceberg tables in Catalog-Linked Databases.
  Table: 'TP'.`, the sentence speaking of a table whatever is dropped and showing the name as written. A missing
  database or schema, a missing object that no IF EXISTS forgives and a relation of another kind are refused first;
  with IF EXISTS on a missing object the sentence stands in place of the skipped drop. A file format, a contact, a
  secret and a network rule are dropped as if the word were not there. Nothing is dropped by a refused PURGE.
- **TABLE(…) items and structured types in a DROP's or a DESCRIBE's signature.** `DESCRIBE TABLE t (TABLE(a))`,
  `(TABLE(a), TABLE(b))`, `(ARRAY(INT NOT NULL))`, `(OBJECT(a a))`, `(MAP(INT, INT NOT NULL))`, `(VECTOR(FLOAT,
  -3))` and `(VECTOR(BIGINT, 3))` were refused and are read; a fault inside a structured type or a TABLE item
  reports the lines the account stacks after it (`(ARRAY(1))` is the `1` and the signature's `)`, `(MAP(INT, 1))`
  the `1` alone).
- **Lines after a fault that follows an alias inside a derived table.** `SELECT a FROM (SELECT 1 x 'y') WHERE a b`
  reported the `'y'` and stopped. The statement is read on as the account reads it: the `b` after a query that ends
  at the token, the query's own `)` when more of it follows a value (`(SELECT 1 x NULL FROM t)`), the `b` again
  after an AS, a path, a cast or a list in parentheses before the FROM of a one-table query, the token after a
  comma read as the FROM clause's next table, and a WITH clause's query as if the token were not there.
- **A cast around a FROM-form call inside another call.** `SELECT UPPER(CAST(EXTRACT('wks' FROM d) AS INT)) FROM t`
  reported the FROM alone; the cast's closing parenthesis is refused after it, as for a SUBSTRING and a TRY_CAST.
- **A syntax error names the token where the reading stopped.** Where no reading of the text held, the parser
  fell back on one that a rule of the grammar forbids and named the token that reading starts with: a fault inside
  `CASE WHEN 1 = 1 THEN x y END` was reported at the `CASE` and then at the `y`, and `SELECT a, UPPER(b FROM t` at
  the select list's comma. A refused statement is now read without that fallback, so the first line is the `y`
  and the `FROM`, as the account's is — after `TOP n` and in the text of EXECUTE IMMEDIATE too, which is read for
  its faults as a statement of its own is.
- **The lines after a fault inside a cast, a CASE and a FROM list.** A comma inside a `CAST` or `TRY_CAST` nested
  in a call, a group, an IN list or a window's `OVER` is followed by the parenthesis left over once the cast's own
  is given up (`SELECT UPPER(CAST(1, 2)) FROM t`: `','` at 19, `')'` at 23); any other fault inside a cast's
  parentheses is followed by what the statement holds wrong once the cast is read past, and by nothing where that
  is nothing (`SELECT CAST(1 2) FROM t` is `'2'` alone). A fault inside a CASE expression ends the report. In a
  join's `ON` the fault of a bracket is the `ON` alone, and in a block's assignment the value's first token. A bare
  `TRY_CAST` is refused at the token after it, as a bare `CAST` was. After a fault in a FROM list a `)` that closes
  no group is read past and the next clause's fault reported.
- **A parenthesized list of two or more values is a ROW value wherever a value may stand.** It was read only
  beside a comparison or before IN, and was a syntax error everywhere else. `ORDER BY (b, a) DESC` now orders by
  `b DESC, a DESC` and `GROUP BY (a, b)` groups by both — a number among the values is an ordinal, as on its own —
  in a window's ORDER BY too. Rows compare behind any number of parentheses (`((1, 2)) = ((1, 2))`), with
  `BETWEEN` two rows, `IN (((1, 2)), ((3, 4)))`, `= ANY` and `<op> ALL` a query (a NULL on either side leaves the
  pair unequal), and as the two sides of a simple `CASE`. Everywhere else the row is refused in the account's
  words where it was a syntax error: a select item as `Unsupported: SELECT list element with multiple columns.` at
  its alias or its own parenthesis, an operator as `Invalid argument types for function '+': (ROW(NUMBER(1,0),
  NUMBER(1,0)), NUMBER(1,0))` at the operator (`NEGATE` and `UNARY PLUS` for the signs, `GET` for a path or an
  index, placed nowhere for a negated operator), a cast as `invalid type [CAST(ROW(1, 2) AS VARCHAR(134217728))]
  for parameter 'TO_VARCHAR'`, a condition as `Invalid data type [ROW(…)] for predicate [ROW(T.A, T.B)]`, a CASE
  branch as `Can not convert parameter 'ROW(1, 2)' … into expected type [ANY]`, a VALUES row, an array or an
  object literal, and an INSERT or UPDATE value as `Expression type does not match column data type`.
- **EXPLAIN lists the account's operators.** A plan was a `Join` over two scans, a `Sort` over a `Limit`, a `Copy`,
  and zero partitions everywhere. It is now arranged as the account arranges the statement: an `InnerJoin` with a
  `JoinFilter` over its second side for a join on an equality and a `CartesianJoin` for any other — tables written
  with commas are joined by the WHERE's equalities — a `LeftOuterJoin` for LEFT and RIGHT, a `FullOuterJoin`, each
  listing the table that holds fewer rows first; one `SortWithLimit` for ORDER BY with LIMIT, FETCH or TOP; an
  `Aggregate` for DISTINCT and for an aggregate without GROUP BY, with a HAVING that holds no aggregate filtered
  under it; `GroupingSets` for ROLLUP, CUBE and GROUPING SETS; a `UnionAll` under an `Aggregate` for UNION and a
  `SemiJoin` or an `AntiJoin` for INTERSECT and EXCEPT; `Flatten`, `Unpivot`, `ValuesClause` and `Generator` for
  those sources. A view and a common table expression read as the query they stand for, and a sampled table stands
  under an operator that takes an id and is not listed. `Insert`, `Update`, `Delete` and `Merge` stand over what
  they read — a MERGE's join follows its clauses, a multi-table INSERT names every table — and COPY is an `Insert`
  over an `ExternalScan` or an `Unload`. A scan of a table that holds rows reports one partition of 1024 bytes,
  none assigned for a DELETE of every row, and GlobalStats the sums. USING TEXT writes a scan's alias as
  `<table> as <alias>`; USING JSON puts it after the expressions. What the account's optimizer decides beyond the
  statement as written — conditions moved under a join, the order of three and more tables, IN and EXISTS as
  joins, a query answered from metadata — is not reproduced, and expressions stay spelled as written.
- **HOUR, MINUTE and SECOND read a TIME.** `HOUR('03:04:05'::TIME)` failed with `Timestamp '03:04:05' is not
  recognized`; it answers 3, as `EXTRACT(hour FROM …)` always did.
- **TRUNC truncates a zoned timestamp.** `TRUNC(<TIMESTAMP_TZ>, 'month')` and the same over a TIMESTAMP_LTZ failed
  with `Numeric value 'month' is not recognized`; they answer what `DATE_TRUNC` answers.
- **NEXT_DAY and PREVIOUS_DAY take a weekday by its first two letters.** `NEXT_DAY(d, 'mo')` failed with `Unknown
  day of week: mo`; the first two letters now decide, in any case and after leading spaces, and a word that names no
  weekday is `Weekday 'zz' is not recognized`.
- **A position after an emoji is one further on.** A character outside the Basic Multilingual Plane counted once
  in every error position; an account counts the two UTF-16 code units it is written in, so `SELECT '😀' x y` is
  refused at 14, not 13 — in syntax errors, compile errors and an EXECUTE IMMEDIATE's text alike. The lexer now reads
  UTF-16 code units, which also puts the rules that rewrite a text by its tokens back in step after such a character.
- **BEGIN; begins a transaction for the lines after it.** A script opening with `BEGIN;` was read as an unclosed
  scripting block when a later statement had a fault: `BEGIN; SELECT 1 x y` was refused at the end of the input and
  a later statement's fault was dropped. The statements after it are now read as the script's own.
- **A fault inside a query used as a value is refused at its SELECT first.** `SELECT (SELECT 1 x y)` is 'SELECT' at
  8 and then the 'y', once for each such query around the fault; as a call's argument the SELECT stands alone, and
  in a WHERE, HAVING, QUALIFY, GROUP BY, ORDER BY, SET or VALUES the fault inside is not reported and the later
  statements are read.
- **More lines after a script's fault follow an account.** `INSERT` after a select list's comma is the string
  function's name (`SELECT 'foo', INSERT FROM t` is refused at the FROM, not the INSERT); a WHILE … LOOP or FOR … LOOP
  body is one construct, so the line after a fault in it is the token after the loop; a comma where an UPDATE's SET
  item belongs is the statement's one line; a join's USING with no list ends the report; anything run in after IS
  TRUE refuses the IS; and a DROP, SELECT, CREATE, GRANT, DELETE, UPDATE or ALTER run in after a statement, an
  operator after a DROP's name and a second literal after a value let the later statements be read.
- **Syntax-error lines after a script's fault.** Four readings now follow an account: a session SET whose name its
  '=' does not follow is refused at the name (and at the statement's end straight after it) instead of at the
  token after the name; a JOIN with no table after it is refused at the token where the table belongs instead of
  at the JOIN, and what follows is read as if the join had not been written; a word run into a value inside a
  bracket of a select item reports the next fault too (`SELECT (1 x y)` is 'x' then 'y'); and a number or a string
  run in after a complete statement ends the report or lets the later statements be read by what it follows —
  `COMMIT 1; SELECT 1 x y` is the 1 alone again, `SELECT 1 FROM t WHERE a = 1 2; SELECT 1 x y` the 2 and the 'y'.
  The lines a rule reads from a text it rebuilt no longer depend on what was parsed before: that parse has a
  prediction cache of its own, as the refused text's own parse has.
- **A CSV unload writes the text its format describes.** Every unload wrote comma-separated lines with NULL as
  an empty field and `"` around a value holding a comma, whatever the options. It now writes NULL as NULL_IF's
  first value (`\\N` by default, the backslash escaped; `"\N"` in an enclosed text column; nothing for `NULL_IF =
  ()`), honours FIELD_DELIMITER, RECORD_DELIMITER, FIELD_OPTIONALLY_ENCLOSED_BY, ESCAPE,
  ESCAPE_UNENCLOSED_FIELD, EMPTY_FIELD_AS_NULL, DATE_FORMAT, TIME_FORMAT, TIMESTAMP_FORMAT and BINARY_FORMAT,
  encloses texts, dates, times, binaries and semi-structured values but not numbers or booleans, and takes
  every option — COMPRESSION and FILE_EXTENSION too — from a named format or from the stage's own.
- **A doubled slash in a stage path names no file.** `@st//f` was read as `@st` with a `//` comment after it, so
  `LIST @st//f` listed the whole stage and **`REMOVE @st//f` removed every file in it**. Two slashes straight
  inside a stage reference are now its path's: the path matches nothing, in a LIST, a staged-file query and a
  REMOVE.
- **LIST shows an internal stage's files at their stored size**: the bytes padded to the cipher's 16-byte block
  (2 bytes list as 16, 27 as 32), as the account's encrypted stages do.
- **Task and alert runs survive a write-ahead-log restart.** A run the schedule started wrote below the log, so
  its rows and its row of run history were gone after a restart with no checkpoint, and a replayed EXECUTE TASK
  ran the body again with new times. A checkpoint now follows every run.
- **More of an object's history survives a restart.** A warehouse, a user and a role read back the time they
  were created instead of the restart's; a role granted to a role or a user is listed with the time of the
  grant (it showed the holder's creation time), kept across restarts; a task's id and the name a constraint is
  given for want of one are the same after a replay of the log.
- **SHOW PRIMARY KEYS and SHOW UNIQUE KEYS over a table named in full** listed it under the session's database
  and schema; they list it under its own.
- **A recursive CTE's columns are typed from both branches.** They took the anchor's types alone, so `WITH
  RECURSIVE r (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r …)` declared NUMBER(1,0) and a recursive view
  described it so. A column its recursive term computes from itself at another type is now given the widest of
  its family — NUMBER(38) at the scale the branches share, a text at its full length — and so are numbers
  computed from each other; one the term passes through, casts back to the anchor's type or computes at that very
  type keeps it; one computed from nothing of the CTE's, or from other columns alone, is typed as a UNION types
  it. A recursive view's column is NOT NULL when neither branch can put a NULL there, and a column a CTE's column
  list renames keeps its NOT NULL.
- **The query history lists the statement that is running.** A statement joined `QUERY_HISTORY` and its
  `_BY_SESSION`, `_BY_USER` and `_BY_WAREHOUSE` forms only as it ended, so a query over the history never found
  itself. It is listed from its start as RUNNING, under the ID it ends with, its END_TIME the start of the epoch
  and its TOTAL_ELAPSED_TIME the distance back to it. The 62 columns are declared with the account's types — 31
  of them NUMBER(38,0) where they were VARCHAR or INTEGER, the texts VARCHAR(134217728),
  `CREDITS_USED_CLOUD_SERVICES` NUMBER(38,9), `IS_CLIENT_GENERATED_STATEMENT` BOOLEAN and `BIND_VALUES` VARIANT —
  and the queued and blocked times read 0.
- **AT and BEFORE read the open transaction's own changes.** Inside a transaction, `AT(STATEMENT => …)` naming
  one of its own statements read the committed table and missed what the transaction had written. It now reads
  the table as the transaction saw it once that statement had run, and BEFORE as it saw it before; a statement
  that only read, one that wrote another table and one that failed name their own moment too, and so does a
  time (`AT(OFFSET => 0)`). After COMMIT and after ROLLBACK the answers are what they were.
- **TRUNCATE stays inside its transaction.** A TRUNCATE in an explicit transaction committed what the transaction
  had done and then ran on its own; it is now one of the transaction's statements, and a ROLLBACK brings the rows
  back.
- **LAST_TRANSACTION after a statement that runs as its own transaction.** A `CREATE TABLE … AS SELECT`, a table
  CLONE, a CREATE STREAM and a COPY into a table left the last transaction as it was; each now leaves its own. A
  statement refused while it compiles — an INSERT into a table that does not exist — no longer leaves one.
- **The duplicate-row refusal is the account's two lines.** A MERGE joining one target row to several source rows
  answered the sentence and the row on one line, the row as a query prints it; the row now follows on its own
  line as it is stored: a date by its days since 1970, a NUMBER(10,2) by its digits, a timestamp by its
  nanoseconds, a boolean as 1 or 0.
- **DESCRIBE FUNCTION lists the body of a SQL function written without one**, as NULL.
- **DML on a view, a stream or a materialized view is refused in the account's words.** `INSERT INTO v …` was
  `Table 'V' does not exist or not authorized`; it is `INSERT statement's target must be a table` — UPDATE, DELETE,
  MERGE, an INSERT ALL and a COPY alike, each with its own word — a materialized view is `INSERT operations are
  not allowed on materialized views`, and `TRUNCATE TABLE v` is `Object found is of type 'VIEW', not specified
  type 'TABLE'.`
- **DROP ROLE hands what the role owned to the role that dropped it.** Tables, views, sequences, stages,
  routines, schemas and every other owned object kept the dropped role's name as their owner; and a renamed role
  goes on owning its objects under its new name.
- **REVOKE counts the privileges it took back.** `REVOKE SELECT, INSERT ON TABLE t FROM ROLE r` answered `1 objects
  affected.` for the two; a `REVOKE ALL` counts each privilege ALL stands for on that kind of object (11 on a
  table, 2 on a function, 84 on a schema).
- **A word after a relation is its alias.** `SELECT a FROM t COMMENT = 'x'` and `SELECT a FROM t foo = 'x'` were
  syntax errors at the word; they are refused at the `=`, as the account refuses them.
- **CREATE OR ALTER NETWORK POLICY clears what it leaves out.** The lists and the comment a statement did not
  write were kept; the statement tells the whole policy.
- **A name inside an ORDER BY key reads the select list's output first.** `SELECT id * -1 AS x … ORDER BY x + 0`,
  `ORDER BY ABS(x)`, a CASE, a cast or an IN over the alias were `invalid identifier`; an alias now stands for its
  expression wherever a key reads it, and outranks a column of its name — `SELECT id * -1 AS id … ORDER BY id + 0`
  sorts by the negated value, where it sorted by the stored one. The column a star's REPLACE or RENAME gives, an
  alias defined through earlier aliases, an alias inside an aggregate's argument of a grouped query and one that
  names a window call (`ROW_NUMBER() OVER (…) AS rn … ORDER BY rn * -1`) read the same way.
- **A window's ORDER BY and PARTITION BY read an alias inside an expression.** `ROW_NUMBER() OVER (ORDER BY x + 0)`
  beside `id * -1 AS x` ordered every row alike, in a select list and in a QUALIFY.
- **SEQ numbers the input record.** FLATTEN, SPLIT_TO_TABLE and STRTOK_SPLIT_TO_TABLE reported 1 on every row; a
  call over the rows of the relations to its left now numbers them from 1, a row that yields nothing taking its
  number, a chained call numbering the rows before it, and each query block counting afresh.
- **A condition on the left relations is applied before a table function.** In `FROM t, LATERAL FLATTEN(t.v) f
  WHERE t.k > 1` the function is no longer called for the rows `t.k > 1` removes, so an input that would fault in
  one of them does not, and the rows that remain are numbered from 1.
- **A view's missing relation is reported under the view's name.** A view over a dropped table refused as the
  statement's own miss; it is now `Failure during expansion of view 'V': SQL compilation error:` followed by the
  relation's full path, each view in between adding its own name in front.
- **A materialized view keeps the columns it was created with.** `SELECT *` over a base table that gained a column
  answered the new column too. SHOW MATERIALIZED VIEWS lists an invalid view's reason, and `ALTER MATERIALIZED VIEW
  … RESUME` compiles the body first: one that no longer compiles is refused as the body is.
- **A windowed COUNT(DISTINCT x) counts each value once.** `COUNT(DISTINCT v % 2) OVER (PARTITION BY g)` answered
  the partition's row count.
- **A correlated subquery the statistics reduce is answered.** An outer name no row reaches correlates nothing:
  over a `g.id` holding no NULL, `(SELECT SUM(v) FROM g WHERE COALESCE(g.id, fz.id) = 5)` filters g alone — NVL,
  IFNULL, NVL2's third argument and the branch of IFF or CASE a settled condition leaves out read the same way —
  and so does a name inside a condition the statistics settle: `tt.id = fz.id OR tt.id = 1`, `tt.id < fz.id AND
  tt.id = 1`, `tt.id IN (fz.id, 1)` and `IFF(tt.id = fz.id, 'x', value)` over key ranges that never meet. All were
  `Unsupported subquery type cannot be evaluated`; GREATEST, the outer name written first, a nullable column and a
  condition left open keep the refusal.
- **MIN, MAX and COUNT of an outer column beside a subquery's own aggregate.** `SELECT id, (SELECT MAX(g.v) +
  MIN(h.id) FROM g) FROM h` was refused, or answered from the row's own group. MIN and MAX of a bare column of a
  table the outer query reads are that table's least and greatest value — whatever the outer WHERE keeps — and
  COUNT of one counts the subquery's rows, in a select list and in a HAVING. SUM, AVG, DISTINCT, an expression
  argument, a view or a derived table outside and a filter in the subquery stay refused.
- **An aggregate over both rows echoes the outer name as the plan holds it.** `HAVING (SELECT MAX(g.v) FROM g WHERE
  g.id < MAX(fz.id + g.v)) > 55` is `Invalid aggregate function in where clause [MAX(CORRELATION(FZ.ID) + G.V)]`;
  the echo read `MAX(FZ.ID + G.V)`.
- **DESCRIBE STAGE lists the options of the stage's own format.** Every stage listed the CSV options; a JSON stage
  now lists `STRIP_OUTER_ARRAY`, `ENABLE_OCTAL` and the rest of the JSON set, an ORC or a PARQUET stage its own.
  A written `NULL_IF` list reads `[x, y]`, a quote inside a value is listed behind a backslash, a stage that
  names a file format lists `FORMAT_NAME` alone, as written, and `ALTER STAGE … SET FILE_FORMAT` replaces the
  format whole. A format's default TYPE reads `CSV` in DESCRIBE FILE FORMAT too, whatever its own type is.
- **COPY reads STORAGE_INTEGRATION.** `COPY INTO t FROM @s STORAGE_INTEGRATION = si` was `invalid parameter
  'STORAGE_INTEGRATION'`. The name is now looked up before the table, the stage and the credentials the
  statement names, and refused as a missing object when it reaches no integration; an unload into a stage
  answers `COPY statements targeting a stage do not support the STORAGE_INTEGRATION property.`
- **A BOOLEAN pivot column answered NULL for every value.** PIVOT matched a row to its column by text, so `FOR b
  IN (TRUE)` never met a row holding `true`. A row is now placed by the VALUE of its FOR column: a BOOLEAN meets
  `TRUE`, `'true'` and `1`, a `NUMBER(10,2)` holding 1.50 meets `1.5`, and a NULL meets the column written `NULL`.
  Columns are named as on the account — `TRUE` for `IN (true)`, and for `IN (ANY)` or a subquery `FALSE`, `TRUE`,
  `1.5`, `'x'`, `'2024-01-01'`, `NULL` — and `IN (ANY)` orders them by value (numbers no longer as text), the rows
  holding none last; `IN (ANY ORDER BY b DESC)` is read.
- **A column holding nothing but an untyped NULL has no type.** DUAL's `COLUMN1`, a VALUES column written as NULL
  in every row and a derived or view column over the bare word were typed VARCHAR, so `column1 + 1`,
  `COALESCE(column1, 1)` and `SELECT column1 UNION ALL SELECT 1` came out VARCHAR. They now read as the bare word
  does: `NUMBER(19,0)`, `NUMBER(1,0)` and `NUMBER(1,0)`, in SYSTEM$TYPEOF, a CREATE TABLE … AS and the result's
  description, where the column itself is `VARCHAR(0)`.
- **APPROX_PERCENTILE past 1400 values.** Over more values than that the account reads its sketch as a digest, and
  so does APPROX_PERCENTILE_ESTIMATE over a state of more than 1400 centroids: `APPROX_PERCENTILE(v, 0.99)` over
  0 … 1999 is 1979.5 (it was 1979.01), and 0 and 1 reach half a position past the ends. Up to 1400 values the
  answer is the exact interpolated percentile, as before. Over values that are not evenly spaced the two can still
  differ past 1400, where the account has merged neighbours.
- **A taken name is spelled as the statement wrote it.** `CREATE TABLE "lo"` over an existing table answered
  `Object 'lo' already exists.`; it is `Object '"lo"' already exists.`, the name quoted where it needs quotes and
  an inner quote written once, for a table, a view, a materialized view, a dynamic table, a sequence, a stage, a
  file format, a stream, a tag, a task, an alert and a policy alike, and for CLONE, LIKE and a CREATE … AS query.
  A name written with a path is spelled with it (`Object 'DB.S."lo"' already exists.`), and so is the new name of
  a RENAME — `ALTER TABLE t RENAME TO db.s."lo"` named `LO` alone. `ALTER STAGE … RENAME TO` now reads a name
  with a path and places the stage where it says.
- **A clustering key reads back as it was written.** The key was kept without its spacing, so `CLUSTER BY (a + 1)`
  read `a+1` and a CASE key lost every space. GET_DDL, `SHOW TABLES`' `cluster_by` and
  `INFORMATION_SCHEMA.TABLES.CLUSTERING_KEY` now print the statement's own text — spaces, comments and all — under
  `LINEAR` unless the statement named the ordering itself, which GET_DDL then prints too (`cluster by linear(a,b)(`).
- **`ALTER VIEW … SET SECURE` and `UNSET SECURE`** were syntax errors. The view becomes secure, or plain again:
  `IS_SECURE` and GET_DDL say so. The action stands alone, `IF EXISTS` beside it is `Unsupported feature 'IF EXISTS
  in ALTER VIEW SET/UNSET SECURE'.`, and a table that takes the statement stays what it is.
- **DESCRIBE lists the columns of INFORMATION_SCHEMA's own views.** `DESCRIBE VIEW db.INFORMATION_SCHEMA.VIEWS`
  and `DESCRIBE TABLE` over one answered no rows; each of the 63 views now lists its columns with the type, the
  nullability and the description a real account gives it.
- **A table function written without an alias answers to its function's name.** `SELECT flatten.value FROM t,
  TABLE(FLATTEN(…))`, `flatten.*`, `split_to_table.value`, a user-defined table function's own name and
  `result_scan.col` resolve, in WHERE and ORDER BY too, as on the account; they were `Object 'FLATTEN' does not
  exist` or an invalid identifier. An alias takes the name's place, and a quoted lower-case `"flatten"` does not
  reach it.
- **LISTAGG's delimiter is evaluated.** A delimiter that was not a plain string literal was taken as its own source
  text: `LISTAGG(n, 'x' || 'y')` answered `1x' || 'y2`, `LISTAGG(n, UPPER('x'))` `1UPPER('x')2`, a session variable
  or a block's bind its spelling. A constant expression now answers its value (`1xy2`, `1X2`), and a NULL
  delimiter joins with nothing between the values.
- **A table function's type refusal names a named argument as the call spells it:** `invalid type [NUMBER(38,0)] for
  parameter 'input'` for `FLATTEN(input => n)`, where it always said `'INPUT'`.

- **A FOR loop's body may open with LOOP and close with END LOOP.** `FOR j IN 1 TO 3 LOOP … END LOOP;`, over a
  range, a cursor or a RESULTSET, with REVERSE and a label, runs as `DO … END FOR` does; it was a syntax error at
  LOOP. The two spellings do not mix: `LOOP … END FOR` is refused at the FOR and `DO … END LOOP` at the LOOP.
- **A string written between dollar signs is read where a SHOW, a COMMENT ON and a star's ILIKE read a string.**
  `SHOW TABLES LIKE $$C%$$` (every SHOW's LIKE, STARTS WITH and LIMIT … FROM), `COMMENT ON TABLE t IS $$x$$` (every
  COMMENT ON) and `SELECT * ILIKE $$b$$ FROM t` were syntax errors at the string.
- **CALL runs a built-in function.** `CALL UPPER('a')` answers `A` in a column `UPPER`, `CALL SUM(1)` answers 1, and
  the arguments may be any expressions, a subquery included; inside a block `CALL UPPER('a') INTO :r` assigns the
  answer. It was `Unknown function UPPER.`, which a user-defined function still is.
- **A user-defined table function is called after LATERAL.** `FROM t, LATERAL tf(t.id)` reads the row beside it, as
  `TABLE(tf(t.id))` does; it was `Unknown table function tf`.
- **`TABLE(IDENTIFIER(…))` is a call of no table function.** `TABLE(IDENTIFIER('t' || '1'))`, `TABLE(IDENTIFIER())`
  and `TABLE(IDENTIFIER(UPPER('t1')))` answer `Unknown table function IDENTIFIER`, as the whole reference written
  there does, and `TABLE(IDENTIFIER(a))` the unknown column; they were syntax errors inside the parentheses.
- **A plain word in a GRANT's or a REVOKE's routine signature is no type.** `GRANT USAGE ON FUNCTION f1(x) TO ROLE
  r` is `Unsupported data type 'X'.`, on a procedure too, before the routine is looked for; it answered that the
  function does not exist.
- **`SELECT SYSTEM$WAIT(n)`'s column is VARCHAR(134217728),** as SYSTEM$TYPEOF's is; it was VARCHAR(16777216).

- **`ALTER DYNAMIC TABLE … RENAME TO` renames the dynamic table.** It was a syntax error. The table takes its new
  name in its schema, or moves to the one the name places it in and goes on reading the tables its query named;
  what was granted on it and the streams that read it follow. A name a table or a view holds is `Object 'X'
  already exists.`, IF EXISTS forgives the table's absence alone, and a fault after the RENAME is refused at its
  TO. As on the account, the statement renames a plain table too, `ALTER TABLE … RENAME TO` renames a dynamic
  one, and any other action of `ALTER DYNAMIC TABLE` over a plain table is `Object found is of type 'TABLE', not
  specified type 'DYNAMIC_TABLE'.`.
- **A table created from a query takes its options after a list of column names.** `CREATE TABLE t (x) COMMENT =
  'c' AS SELECT 1`, with CLUSTER BY, COPY GRANTS, a retention time or CHANGE_TRACKING there, creates the table; it
  was a syntax error at the option.
- **A procedure is created SECURE.** `CREATE [OR REPLACE] [TEMP] SECURE PROCEDURE` was a syntax error. GET_DDL
  writes `CREATE OR REPLACE SECURE PROCEDURE`, SHOW PROCEDURES answers `is_secure` Y, the flag survives a restart,
  and `ALTER PROCEDURE … UNSET SECURE` is `invalid property 'SECURE' for 'PROCEDURE'`, as on the account.
- **A word that assigns nothing after a property list is read as a property's name.** `CREATE TABLE t (a INT) x` is
  refused at the end of input and `… x y` at the `y`, as the account refuses them, and so after a CREATE SCHEMA, a
  CREATE STAGE, a CREATE SEQUENCE and an `ALTER TABLE … SET` assignment; the word itself was refused.

- **A scripting variable declared VARCHAR, STRING or TEXT without a length binds at VARCHAR(134217728).** A NULL
  it binds into a statement reads `VARCHAR(134217728)[LOB]` and makes a VARCHAR(134217728) column, where it read
  as an untyped NULL (`NULL[LOB]`, VARCHAR(0)) or as VARCHAR(16777216); a variable declared `VARCHAR(16777216)` now
  keeps that width instead of losing its type.
- **A NULL bind picks a user-defined function's overload by its declared type.** With `fo(n NUMBER)` and `fo(v
  VARCHAR)`, `fo(:s)` over a VARCHAR variable holding NULL answers the text overload, as on the account; it
  answered the NUMBER one.
- **A star call's misplaced RENAME or REPLACE is read as the account reads it in four more places.** In a join's
  ON the statement is refused at the ON and nothing else (`SELECT 1 FROM t a JOIN t b ON HASH(* REPLACE (1 AS id)) =
  1` names `'ON'`, where the keyword and a parenthesis were named); in a subquery that is a select item, at the
  subquery's SELECT and the call's parenthesis; under a CAST or a TRY_CAST as under a call; in a MERGE's ON as in a
  WHERE. A word or a number that is no modifier at all after a call's star — `HASH(* x)` — is refused alone, and
  the statement read on as if it were not written, where the token after the call was named as well.
- **Database roles are named as they are.** Sentences and listings spell a role created under a quoted name with
  its quotes — `Role DB."dr_low" successfully created.`, `Object 'DB."dr_low"' already exists.`, the `name` and
  `role` cells of SHOW GRANTS — list it bare and exact as a grantee and in SHOW DATABASE ROLES (`dr_low`), and a
  rename keeps the case it is given.
- **SHOW DATABASE ROLES reads its modifiers.** `LIKE '<pattern>'` before the scope (whatever the case), `IN <db>`
  without the DATABASE word, `STARTS WITH '<prefix>'` and `LIMIT n FROM '<name>'` (both as written) were syntax
  errors. A listing without a database and a TERSE one are refused as the account refuses them: `Unsupported
  feature 'SHOW DATABASE ROLES'; use 'SHOW DATABASE ROLES IN DATABASE <database_name>' instead.`
- **`SYSTEM$GET_TAG_ALLOWED_VALUES('<tag>')`** answers a tag's allowed values as a JSON array in text, `[]` for an
  emptied list and NULL for none; it was an unknown function.
- **An Iceberg table's columns read at the format's widths.** An unsized string of an Iceberg table is
  `VARCHAR(134217728)`, a binary `BINARY(67108864)`, a time or timestamp at scale 6, INT `NUMBER(10,0)` and BIGINT
  `NUMBER(19,0)` in DESCRIBE, SHOW COLUMNS, INFORMATION_SCHEMA and SYSTEM$TYPEOF; they read as a table's
  (`VARCHAR(16777216)`, `NUMBER(38,0)`, scale 9). A table created from a query takes its strings and binaries at the
  same widths and keeps a result's own NUMBER(p,s).
- **A table's column keeps a length declared past 16777216.** `VARCHAR(134217728)` and `VARCHAR(16777217)` read
  back as written in DESCRIBE TABLE, SHOW COLUMNS (`"length":16777217,"byteLength":67108868`, the bytes stopping at
  134217728) and INFORMATION_SCHEMA.COLUMNS; all three printed 16777216. A view's column still stops there, and a
  table created from a query stores such a column at 16777216, where it kept a declared 16777217.
- **DESCRIBE shows the identity generated for a cloud role.** An API integration over AWS lists
  `API_AWS_IAM_USER_ARN` and `API_AWS_EXTERNAL_ID` around its role, a Glue catalog integration `GLUE_AWS_IAM_USER_ARN`
  and `GLUE_AWS_EXTERNAL_ID`, an SNS notification integration `SF_AWS_IAM_USER_ARN` and `SF_AWS_EXTERNAL_ID`, and an
  external volume's S3 location `STORAGE_AWS_IAM_USER_ARN` and `STORAGE_AWS_EXTERNAL_ID` — the ID that was written,
  or the one generated for the volume; a Google API integration and a GCS location name a service account
  (`API_GCP_SERVICE_ACCOUNT`, `STORAGE_GCP_SERVICE_ACCOUNT`). The rows were missing. Frostlake reaches no cloud
  account, so the user is a fixed one and the ID is derived from the object's name, in the account's shape. A Glue
  catalog written without GLUE_REGION reads the account's region, and the REST API's `sf_aws_iam_user_arn` /
  `sf_aws_external_id` carry the same values.
- **The REST API's Iceberg `:refresh` and `:convert-to-managed` refusals carry code 091352.**
- **An unknown type in an Iceberg table's column list is named as written.** `(c nosuchtype)` is `Unsupported data
  type 'nosuchtype' for iceberg tables.`, where a table's is `Unsupported data type 'NOSUCHTYPE'.`
- **ALTER CORTEX SEARCH SERVICE reads every documented form.** `SET TARGET_LAG`, `WAREHOUSE`, `PRIMARY KEY = (…)`,
  `FULL_INDEX_BUILD_INTERVAL_DAYS`, `REQUEST_LOGGING` and `AUTO_SUSPEND`, `UNSET PRIMARY KEY`, `SET` and `UNSET
  ATTRIBUTES`, `SET` and `UNSET TAG` and `REFRESH` were syntax errors or `Unsupported ALTER CORTEX SEARCH SERVICE
  option`; they change what `SHOW CORTEX SEARCH SERVICES` lists and survive a restart. What a SET may not set is
  refused in the account's words before the service is looked for (`invalid value 'soon' for property
  'TARGET_LAG'`, `invalid property 'EMBEDDING_MODEL' for 'CORTEX_SEARCH_SERVICE'`), `ALTER … IF EXISTS` over a
  missing service answers `Cortex Search Service NOSUCH does not exist.`, `DROP … IF EXISTS` over one answers the
  `already dropped` sentence, and a quoted name is a service of its own, in `SEARCH_PREVIEW` too.
- **A privilege held through a database role authorizes.** The database roles granted to a session's roles, and
  the database roles granted to those, are asked with them: `GRANT SELECT ON TABLE t TO DATABASE ROLE d.r` and
  `GRANT DATABASE ROLE d.r TO ROLE reader` lets `reader` read `t`, and a CREATE privilege so held creates. Only
  account roles were asked.
- **A procedure running with owner's rights creates as its owner.** A CREATE in its body asks the privilege of the
  role that owns the procedure, not of the caller, and that role owns what is made; the caller's role was asked and
  recorded. Where the owner holds nothing the refusal says so: `This executable runs with owner's rights. The owner
  role R must have CREATE TABLE granted on SCHEMA D.S.` A caller's-rights body creates as its caller.
- **A new database holds none of the role grants of the one it replaces.** A database role granted to a role or a
  user stays granted while its database is dropped, so UNDROP brings it back; a new database of the same name
  starts with no roles, and a database role made in it under an old name was already granted to whoever held the
  old one.
- **CREATE OR REPLACE asks before it drops.** A sequence, a pipe, a stage, a file format or a tag was dropped before
  the privilege to create its replacement was asked, so a refused replacement lost the object. A replacement asks
  for OWNERSHIP of what it replaces first, then for the CREATE privilege, and changes nothing when either is missing.
- **An EXISTS over the outer query's own aggregate is answered.** `SELECT id, EXISTS (SELECT MAX(fz.id) FROM g WHERE
  g.id = fz.id) FROM fz` was `invalid identifier 'FZ.ID'`. The aggregate reads the outer query's columns alone, so it
  is that query's value and the EXISTS counts its rows as if a constant stood there: TRUE where a row of `g` matches
  and FALSE elsewhere, FALSE over `WHERE FALSE` where an aggregate of the subquery's own would answer one row, and
  under a LIMIT or a set operation it reads no outer row at all. It is answered in a select list and a HAVING, through
  a derived table, a common table expression, a view, CREATE TABLE AS, INSERT … SELECT and an UPDATE's SET. Where
  the value itself is read — under DISTINCT, or by a HAVING naming the item — the EXISTS is `Unsupported subquery
  type cannot be evaluated`. An aggregate of the EXISTS's own that reads the outer row is answered where the select
  groups without it: `EXISTS (SELECT MAX(fz.id + g.v) FROM g GROUP BY g.s)`.
- **A membership over a query that reads the row around it is two-valued.** `id IN (SELECT g.id FROM g WHERE g.k =
  f.k)` and `id = ANY (…)` are TRUE for a match and FALSE for everything else, where a NULL probe or a NULL in the
  rows matched answered NULL; `NOT IN` and `ALL` keep the three values, and so does a query that reads no outer row.
  A tuple so matched follows: `(NULL, 'a') IN (…)` is FALSE. An empty result is FALSE for IN and TRUE for NOT IN
  whatever a probe read from a row holds (`id IN (SELECT 1 WHERE FALSE)` over a NULL id was NULL), and a NULL probe
  that reads no column is UNKNOWN whatever the result: `NULL IN (…)`, `NULL = ANY (…)` and `NULL <> ALL (…)` over an
  empty result or a correlated one.
- **An aggregate in a nested query's ORDER BY is that query's.** `WHERE EXISTS (SELECT 1 FROM g ORDER BY MAX(g.v))`
  was `Invalid aggregate function in where clause [MAX(G.V)]`.
- **LIKE ANY, LIKE ALL and ILIKE ANY take one pattern without parentheses.** `c LIKE ANY 'a%'` and `c LIKE ALL p ||
  '%'` were syntax errors. A query written as LIKE ANY's pattern gives a pattern per row it answers (`'a' LIKE ANY
  (SELECT p FROM patterns)`), and none is FALSE.
- **A leading `!` negates an EXISTS.** `WHERE !EXISTS (SELECT …)` answers as NOT EXISTS does. Before anything else
  the `!` is a call of a function no account has - `SELECT !x` is `Unknown function "!".`, named with the statement's
  other unknown functions - and `model!predict(1)`, a class instance's method, is `Unknown function MODEL!PREDICT.`
  Both were `parse error … near '<EOF>'`.
- **A value where a quantifier takes a query is refused as the account refuses it.** `1 = ANY 1` is `Invalid query
  block: 1.`, `x = ANY y + 1` is `Invalid query block: +.` and `1 = ANY (1, 2)` is `Invalid query block: (.`, before
  a table or a function of the statement is looked for, in a SQL function's body too (`Compilation of SQL UDF
  failed: …`). Each was a syntax error.
- **OVER after what is no call of a function is refused by the value.** `SELECT x OVER () FROM t` is `invalid
  function 'X'`, `SELECT 1.50 OVER ()` is `invalid function '1.5'`, and an operation the account names as a function
  is refused as a call that takes no window is: `x::INT OVER ()` is `Invalid function type [CAST] for window
  function.`, `(x + 1) OVER ()` is `[+]`. Each was a syntax error.
- **An uncaught JavaScript error is placed where the account places it.** A name that is not defined is quoted
  at the start of its statement while nothing has been evaluated since the statement started (`return foo;` is
  position 0, as are `return true && foo;` and `return 2 * foo;`) and at itself afterwards (`return 1 + foo;` is
  11); a name that is called is placed where the operation evaluated last is (`return Math.max(1, nofn());` at
  `max`). A statement over several lines quotes the line the place is on, and each frame is listed at the line of
  the call it had reached. An error that is caught and thrown again, or built into a variable and thrown from it,
  is quoted at the `throw` that sent it out, with the frames of where it arose.
- **A thrown value is told as the account tells it.** An error by its name and message, each only when it is a
  text (`e.message = 5` leaves `Error`); a plain object by the function that built it (`#<K>`, `#<Map>`); an array,
  a date or a typed array by its tag (`[object Array]`); a function by its source.
- **More failures reach SQL in the account's words.** `JSON.parse` refuses a text as the account's engine does,
  caught inside the handler or not (`Expected property name or '}' in JSON at position 1 (line 1 column 2)`), and
  uncaught it quotes the JSON text's own line and column. `toFixed`, `toString` and `toExponential` ranges, a
  repeat count, a BigInt mixed with a number, a failed `in` or `instanceof`, a value that is not iterable, a spread
  of one and a Promise resolver that is no function are worded as the account words them.
- **The handler's API objects have the account's members.** `snowflake` builds a user exception
  (`SnowflakeUserException`), throws an error unredacted (`throwUnredactedError`) and refuses
  `getAccountParameterValue`; a procedure's adds `executeAsync`. `snowflakeLogger` and `_c_snowflake_logger`
  answer what the account's do, and `console` has `context`, `dirxml`, `profile`, `profileEnd`, `table`,
  `timeStamp` and `trace`.
- **A handler reads the session's zone as the account does.** `Intl` tells `UTC` for `UTC` and `Etc/UTC`, and the
  id the account's engine keeps for a renamed zone (`Asia/Calcutta` for `Asia/Kolkata`); a date names its zone in
  full (`Greenwich Mean Time`, `British Summer Time`); the locale is US English whatever the host's is; and a date
  written without a zone is read in the session's.
- **A handler finds the builtins the account's engine has.** `Error.isError`, `Array.fromAsync`,
  `Math.sumPrecise`, `Uint8Array.fromBase64`, `Iterator.concat` and the other additions of the newest language
  edition are there.
- **CREATE FUNCTION and CREATE PROCEDURE take the clauses of a handler's environment.** `TARGET_PATH =
  '@stage/path/x.jar'` keeps a Java or Scala handler's classes as a JAR on the stage, which another routine may
  import (a path that names no JAR, a stage that is not there and a file that already is are refused as on the
  account); `ARTIFACT_REPOSITORY = name` names where a Python routine's packages come from; `SECRETS = ('variable'
  = secret, …)` and `EXTERNAL_ACCESS_INTEGRATIONS = (name, …)` are judged, kept, listed and changed by `ALTER … SET`.
  All five were syntax errors.
- **A description tells a handler routine's properties.** DESCRIBE FUNCTION and DESCRIBE PROCEDURE add `imports`,
  `handler`, `target_path`, `runtime_version` and `packages` for Java and Scala, and for Python the artifact
  repository rows, `installed_packages` and `is_aggregate`; GET_DDL writes `ARTIFACT_REPOSITORY`, `PACKAGES`,
  `IMPORTS` and `TARGET_PATH`, and ends a routine that has no body without an `AS`; INFORMATION_SCHEMA.FUNCTIONS and
  PROCEDURES tell imports and packages as JSON arrays, with the target path, secrets and artifact repository. An
  import from a named stage is kept by the stage's full name, and a JAR on an internal stage now loads.
- **`EXECUTE AS RESTRICTED CALLER`.** A procedure takes it at CREATE and by `ALTER PROCEDURE … EXECUTE AS OWNER |
  CALLER | RESTRICTED CALLER`; the description, the DDL and the REST body tell it.
- **SHOW USER FUNCTIONS and SHOW PROCEDURES write an argument's type alone**, marking one that has a default:
  `FD(NUMBER, DEFAULT VARCHAR) RETURN VARCHAR`. They wrote the argument names. The REST bodies carry each
  argument's `default_value`.
- **A Java handler takes a NUMBER as the number its parameter declares.** `long h(long x)` was `Method not found`
  for a NUMBER argument; a `long`, an `int`, a `double` and a `BigDecimal` parameter are each given the value.
- **A task's run is a session of its own settings.** It starts from the account's defaults with the task's own
  session parameters set - a `DATE_OUTPUT_FORMAT` or a `TIMEZONE` on the task now shapes what its body writes -
  and never from what the session that ran `EXECUTE TASK` has set. Inside it `CURRENT_USER()` is `SYSTEM`, or the
  user of `EXECUTE AS USER`.
- **Tags and contacts on a task.** `CREATE TASK t [WITH] TAG (…) [WITH] CONTACT (purpose = contact, …)` right
  after the name, `ALTER TASK … SET CONTACT` / `UNSET CONTACT`, both written by GET_DDL; and the contacts of a
  table or a task are read by `SNOWFLAKE.CORE.GET_CONTACTS(object_name, object_domain)`.
- **A PIVOT computes its aggregate as a grouped query does.** MIN and MAX of a text, a date or a BOOLEAN answered
  NULL, and so did every aggregate beyond SUM, AVG, COUNT, MIN and MAX — MEDIAN, MODE, LISTAGG, ARRAY_AGG,
  BOOLAND_AGG, COUNT_IF, the variances. A cell no row reaches now holds what the aggregate answers over no rows:
  a COUNT of 0, an empty array, an empty text, where every such cell was NULL (so `DEFAULT ON NULL` no longer
  replaces a zero count). A row whose FOR column is NULL keeps its group instead of failing the query, a pivot
  with no column left to group by answers one row — over no rows too — and a pivoted column declares its
  aggregate's type: BOOLEAN, ARRAY, NUMBER(38,3) for a median, and VARCHAR(134217728) for a text over a written
  list of values.
- **IS_ROLE_IN_SESSION follows the role hierarchy.** It is TRUE for the current role, for every role granted to
  it directly or through another, and for PUBLIC; it compared the name with the current role alone. A name the
  statement writes is read as an identifier (`'public'`, `'"PUBLIC"'`), one a row carries as it is spelled.
  IS_DATABASE_ROLE_IN_SESSION reads its name the same way, takes one identifier of the current database — a name
  qualified by its database is FALSE — and no longer answers for a role of another database.
- **A CHANGES point may be computed.** `AT (OFFSET => -60 * 60)`, `AT (TIMESTAMP => TO_TIMESTAMP_LTZ('…'))`, a
  subquery and a function call over constants are read; anything but a literal, a variable or a cast of one was
  refused as `argument TIMESTAMP to function AT needs to be constant`. A timestamp text written `'2020-01-01
  00:00:00'` is read as a time-travel point, a text or a NULL as an OFFSET is `Invalid data type ['0'] in
  AT(OFFSET => '0')` instead of an internal error, and a CHANGES point naming an unknown statement is `Statement
  … not found`.
- **DESCRIBE MASKING POLICY tells the return type as declared**: `FLOAT` for DOUBLE and REAL, `NUMBER(38,0)` for
  INT, `NUMBER(10,2)`, `VARCHAR(10)`, `TIME(9)`, `TIMESTAMP_NTZ(9)`, and a bare `VARCHAR` or `BINARY` beside
  `VARCHAR(16777216)` written out. It told the written type's name alone (`DOUBLE`, `INT`, `DOUBLEPRECISION`);
  GET_DDL follows.
- **SHOW GRANTS ON DATABASE lists the USAGE each of its database roles holds on it**, which no one granted.
- **A FLOAT VECTOR inside a VARIANT displays its elements as any variant DOUBLE**,
  `[1.000000000000000e+00,1.234567016363144e-01,3.500000000000000e+00]`, where the cell read
  `[1.0,0.1234567016363144,3.5]`.
- **`LIMIT n FROM 'name'` starts where each listing starts it.** SHOW TASKS answers the tasks named at or after
  the name; SHOW STREAMS, TAGS, ALERTS, MASKING POLICIES, ROW ACCESS POLICIES and NETWORK RULES start at the
  first row named at or after it and run on through the listing; tables, views, schemas, databases, roles and
  secrets start after the name, as before. Every listing started after it.
- **CREATE OR ALTER NETWORK RULE tells the whole rule.** Over a rule that exists, a value list or a comment the
  statement leaves out is emptied, a MODE it leaves out changes nothing, and the statement answers `Statement
  executed successfully.`; it kept what it did not name and answered as a CREATE.
- **SHOW NETWORK RULES, SHOW SECRETS and SHOW PASSWORD POLICIES with no scope list the current schema**; they
  listed the current database.
- **`TASK_HISTORY(RESULT_LIMIT => NULL)`** answers the default hundred rows instead of failing.
- **A dynamic table's column list is applied.** `CREATE DYNAMIC TABLE dt (x, y NUMBER(10,2) COMMENT 'c') … AS
  SELECT …` names the table's columns, casts the query's values to a declared type and keeps a column's comment
  — in the table's rows, DESCRIBE, SHOW COLUMNS, INFORMATION_SCHEMA.COLUMNS (which now list a dynamic table's
  columns at all) and GET_DDL. A typed or commented column was a syntax error, and a list of names changed nothing.
- **A dynamic table's query reads the table's own schema.** Read from another schema, an unqualified name in the
  query resolved in the reader's schema, so the table answered another table's rows.
- **ALTER DYNAMIC TABLE … REFRESH answers what the refresh changed**: `dt_name`, `statistics` (`No new data`, or
  `{"insertedRows":1,"copiedRows":2,"deletedRows":1}`), `refreshed_dt_count` and `data_timestamp`, where it
  answered a status sentence.
- **Listings count a dynamic table's and a materialized view's rows**: SHOW TABLES, SHOW DYNAMIC TABLES, SHOW
  OBJECTS and INFORMATION_SCHEMA.TABLES tell the rows a dynamic table's last refresh held (0 until the first
  refresh of one initialized ON_SCHEDULE), and SHOW MATERIALIZED VIEWS the rows the view materialized; each was 0.
  INFORMATION_SCHEMA.TABLES tells IS_DYNAMIC `YES` for a dynamic table, and a clustered table's CLUSTERING_KEY as
  `LINEAR(…)` with AUTO_CLUSTERING_ON `YES`.
- **A create over a name that is taken echoes the name as written**: `CREATE TABLE db.s.t …` over an existing
  table is `Object 'DB.S.T' already exists.` (a plain CREATE, a CTAS, LIKE, CLONE and a dynamic table alike),
  where the bare name was told.
- **Statement sentences**: a `CREATE OR ALTER VIEW` that alters answers `Statement executed successfully.`, a
  `CREATE DYNAMIC TABLE … CLONE` `Table T successfully created.`, and `ALTER DYNAMIC TABLE … SUSPEND | RESUME
  RECLUSTER` of a table that is not there refuses it as a `Table`.
- **`CLUSTER BY LINEAR(a, b)`** is read, in CREATE TABLE, ALTER TABLE and CREATE DYNAMIC TABLE; it was a syntax
  error.
- **An object keeps a tag by the tag itself, not by its name.** Two tags of one name in two schemas were one
  assignment on an object, the later value winning, and a quoted lower-case tag was folded onto its upper-case
  namesake. Each is its own tag now, in TAG_REFERENCES, SYSTEM$GET_TAG and GET_DDL (which lists them by their full
  names). An assignment follows its tag through `ALTER TAG … RENAME TO`, a move to another schema and a renamed
  schema or database; a dropped tag takes its assignments along, UNDROP brings them back, and a tag created or
  replaced under the same name starts without any. A clone of a schema or a database names its own copies of the
  tags cloned with it and keeps naming a tag that lives elsewhere. `CREATE TAG "cc"` beside `CC` is two tags.
- **SYSTEM$GET_TAG follows inheritance**: a table answers the tag its schema or database carries, a column its
  table's, as TAG_REFERENCES tells them.
- **INFORMATION_SCHEMA.TAG_REFERENCES_ALL_COLUMNS** answers the tags reaching every column of a table or view.
- **TAG_REFERENCES refuses a missing object as the account does**: a table by its bare name when written bare and
  by its full name otherwise, with the privilege sentence; a missing schema as a schema; a column by its full name;
  and an argument left out or NULL as `missing required argument [DOMAIN]` or `[OBJECT_NAME]`.
- **CREATE SECURITY INTEGRATION … TYPE = API_AUTHENTICATION** makes the integration a secret authenticates through
  — an OAuth client (`AUTH_TYPE = OAUTH2`) or an AWS role (`AUTH_TYPE = AWS_IAM`) — with ALTER, DROP, SHOW and
  DESCRIBE; one a secret names can be neither dropped nor disabled. It was a syntax error.
- **`API_PROVIDER = PYPI`** makes the API integration an artifact repository names; it takes ENABLED and COMMENT
  alone.
- **ALTER PIPE … SET ERROR_INTEGRATION = name** and **… UNSET ERROR_INTEGRATION | COMMENT |
  PIPE_EXECUTION_PAUSED** run, where the first was a syntax error and UNSET was not read; an unknown property is
  `invalid property 'X' for 'PIPE'`.
- **The current warehouse is the session's own.** A `USE WAREHOUSE` or a `CREATE WAREHOUSE` in one session of the
  HTTP server, or on one direct JDBC connection, moved `CURRENT_WAREHOUSE()` for every other.
- **What makes a warehouse current follows the account**: `CREATE WAREHOUSE IF NOT EXISTS` over an existing
  warehouse makes it current too, and dropping or renaming the current warehouse leaves the session without one.
  `USE WAREHOUSE` of a warehouse that is not there is `Object does not exist, or operation cannot be performed.`
- **SHOW WAREHOUSES tells a started warehouse's health** — `available` ` 100`, `provisioning`, `quiescing` and
  `other` `0` — and a new standard warehouse has `enable_query_acceleration` `true`, a Snowpark-optimized one
  `false`; UNSET puts ENABLE_QUERY_ACCELERATION and QUERY_ACCELERATION_MAX_SCALE_FACTOR back on their defaults.
- **Every grant listing reads LIMIT n.** `SHOW GRANTS LIMIT 1`, `SHOW GRANTS ON TABLE t LIMIT 1`, `… TO ROLE r LIMIT
  1`, the database-role forms and SHOW FUTURE GRANTS were syntax errors at the LIMIT. A count of 0 is `page size
  "0" must be greater than 0 in limit clause`, and a FROM after the count, a STARTS WITH or a WITH PRIVILEGES is
  refused where the account refuses it. The bare `SHOW GRANTS` reads a LIKE as well, which narrows nothing there.
- **SHOW PARAMETERS takes a container without a name**: `IN DATABASE`, `IN SCHEMA` and `IN WAREHOUSE` list the
  session's current one, where each was a syntax error; `IN STREAM` and `IN TASK` answer `This session does not
  have a current stream | task`. A bare name is refused by name (`Cannot show parameters of SqlIdentifier{…}`), and
  a modifier is refused where the account's parser stops.
- **SHOW ACCOUNTS refuses a scope in its own words**: `SHOW ACCOUNTS IN ACCOUNT` is `Unsupported statement type.
  correct syntax: 'SHOW ACCOUNTS'.`, where it was a syntax error at the IN.
- **A listing modifier written twice or out of order is refused at its word**: `SHOW TABLES WITH PRIVILEGES SELECT
  LIMIT 1` at `LIMIT`, `… WITH PRIVILEGES USAGE WITH PRIVILEGES USAGE` at the second `PRIVILEGES`, and a STARTS WITH
  after the privilege list at `STARTS` with a second line at its prefix; each was refused at another token.
- **SHOW COMPUTE POOL INSTANCE FAMILIES lists the account's 27 families**: the reservation-only `GPU_B300_G1_192`
  was missing, and `GPU_NV_S` and `GPU_NV_M` told 24 GiB of GPU memory where the account tells 22.
- **A key constraint takes the account's properties.** `PRIMARY KEY (a) NOT ENFORCED`, `UNIQUE (b) ENABLE
  NOVALIDATE`, `FOREIGN KEY (c) REFERENCES p (id) NOT ENFORCED RELY` and every other mix of `[NOT] ENFORCED`, `[NOT]
  DEFERRABLE`, `INITIALLY DEFERRED | IMMEDIATE`, `ENABLE | DISABLE [VALIDATE | NOVALIDATE]`, `RELY` and `NORELY`,
  in any order, were syntax errors after any key but for RELY and NORELY; they are read now on a PRIMARY KEY, a
  UNIQUE and a FOREIGN KEY, in a column definition, at table level and in ALTER TABLE … ADD. As on the account, **a
  constraint it cannot keep as written is left out without a word**: one that is DEFERRABLE, INITIALLY IMMEDIATE,
  ENABLE or VALIDATE is in no listing, though a primary key written so still makes its column NOT NULL.
- **A foreign key takes MATCH FULL, SIMPLE or PARTIAL** right after its REFERENCES target, where MATCH was a syntax
  error. MATCH FULL is the key as it always was; SIMPLE and PARTIAL leave the key out, as an ON UPDATE or ON DELETE
  rule other than NO ACTION already did. A key left out still needs its own columns and its parent to exist.
- **A foreign key may leave out its parent's columns at table level** — `FOREIGN KEY (a, b) REFERENCES p` — and then
  points at the parent's primary key, or at a unique key of as many columns; it was a syntax error.
- **A constraint in a column definition may be named**: `a INT CONSTRAINT pk_t PRIMARY KEY`, `b INT CONSTRAINT uq_b
  UNIQUE`, `c INT CONSTRAINT fk_c REFERENCES p (id)` and `d INT CONSTRAINT ck_d CHECK (d > 0)` were syntax errors.
- **A table-level PRIMARY KEY keeps the order of its columns as written.** `PRIMARY KEY (a, b)` over a table whose
  columns are `b, a` listed `b` first; SHOW PRIMARY KEYS numbers `key_sequence` by the declaration now, GET_DDL
  writes it so, and a foreign key has to name the key's columns in that order.
- **RELY on a table-level key is kept**: `PRIMARY KEY (a) RELY` and `UNIQUE (b) RELY` report `rely` YES in
  INFORMATION_SCHEMA.TABLE_CONSTRAINTS and SHOW PRIMARY | UNIQUE KEYS, where only a column's own key did.
- **GET_DDL writes a table's keys as the account does**: each on a line of its own after the columns — the unique
  keys, the checks, the primary key, the foreign keys — a single-column UNIQUE and a column's own REFERENCES
  included (the first was written on its column and the second not at all); `constraint NAME` before a key that
  was given a name; `rely ` after one that relies; and a foreign key's parent in full,
  `references DB.SCHEMA.PARENT(ID)`. An identity column ends in `order` or `noorder` and is written NOT NULL only
  when declared so.
- **An identity declared ORDER is told as ordered.** INFORMATION_SCHEMA.COLUMNS.IDENTITY_ORDERED is YES, and
  DESCRIBE TABLE's default and SHOW COLUMNS' autoincrement end in `ORDER`, where every identity read NOORDER; a
  LIKE and a CLONE keep the choice.
- **INFORMATION_SCHEMA's privilege views list the privileges held.** TABLE_PRIVILEGES has a row per privilege on a
  table, a view, a materialized view or a dynamic table, the owner's OWNERSHIP included; OBJECT_PRIVILEGES has the
  same beside the database's schemas, functions and procedures (`F(X NUMBER, Y VARCHAR)`), sequences, streams and
  tasks, and every database; USAGE_PRIVILEGES has the sequences, whose owner holds USAGE. All three answered no
  rows. COLUMNS.INTERVAL_PRECISION, NULL in every row, is typed `NUMBER(38,0)[SB1]`.
- **A directory table lists what its last refresh registered.** `DIRECTORY(@stage)` listed the stage's files as
  they were; on the account a file staged, replaced or removed shows only after `ALTER STAGE … REFRESH`, and so it
  does here. **A script that reads `DIRECTORY(@stage)` right after a PUT or an unload now needs the refresh first**,
  exactly as it does on Snowflake. The refresh answers one row per file whose registration changed —
  `stages/<stage id>/<path>` with `REGISTERED_NEW`, `REGISTERED_UPDATE` or `UNREGISTERED` and its sentence — and no
  row for an unchanged file; `SUBPATH = '<prefix>'` narrows it to the paths that start with the text, and of
  several options the first alone is read. A table turned off forgets its files, one turned on by an ALTER starts
  empty, and one created with `ENABLE = TRUE` registers the files its stage already holds unless
  `REFRESH_ON_CREATE = FALSE`. `MD5` and `ETAG` are the digest of the file's bytes, where both were NULL.
- **DESCRIBE STAGE tells the directory table's last refresh and AUTO_REFRESH.** A table that is on has a
  `LAST_REFRESHED_ON` row before ENABLE and AUTO_REFRESH: `yyyy-MM-dd HH:mm:ss.SSS ±hhmm` at the session's offset,
  empty before the first refresh. AUTO_REFRESH is kept as written, where it always read `false`.
- **INFORMATION_SCHEMA.STAGES tells a directory flag of three states, for every schema.** `DIRECTORY_ENABLED` is
  NULL for a stage never given a DIRECTORY list and TRUE or FALSE once it was (an explicit `ENABLE = FALSE` read
  NULL), and the view lists the stages of every schema of its database, where it listed the session's schema alone.
- **ALTER STAGE … SET takes several properties at once**, a comma between two of them or none — `SET COMMENT = 'c'
  DIRECTORY = (ENABLE = TRUE)`, `SET COPY_OPTIONS = (…) FILE_FORMAT = (…) COMMENT = '…'` — a repeated one counting
  by its last value, and `SET COMMENT = NULL` takes the comment away. A second property was a syntax error.
- **A comma may stand between two options of an option list**: `FILE_FORMAT = (TYPE = CSV, FIELD_DELIMITER = '|')`,
  `COPY_OPTIONS = (ON_ERROR = 'CONTINUE', FORCE = TRUE)`, `DIRECTORY = (ENABLE = TRUE, AUTO_REFRESH = FALSE)` and
  `CREATE FILE FORMAT f TYPE = CSV, SKIP_HEADER = 1` were syntax errors. A comma before the first option, after
  the last or beside another one still is, at the token the account names.
- **A temporary table that takes a permanent table's name has its own table stage and COPY load history.**
  `LIST @%t` through the shared name listed the permanent table's files and `COPY INTO t` skipped what that table
  had loaded. The temporary table's stage now starts empty, a PUT through the name lands in it, and dropping the
  temporary table brings back the permanent table's files and history untouched.
- **SHOW STAGES tells a stage's kind, cloud and secrets.** `type` is `INTERNAL TEMPORARY` or `EXTERNAL TEMPORARY`
  for a temporary stage and `INTERNAL NO CSE` for an internal stage with `ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')`;
  `cloud` is `AWS`, `AZURE` or `GCP` for an external stage; `has_credentials` and `has_encryption_key` are `Y` for
  a stage given CREDENTIALS, a MASTER_KEY or a KMS_KEY_ID. The type read INTERNAL or EXTERNAL alone and both flags
  `N`, so the REST stage body always said `kind: PERMANENT` and `has_credentials: false`.
- **A SHOW's LIKE pattern takes `\_` and `\%` as the character itself.** `SHOW STAGES LIKE 'LK\\_INT'` lists
  `LK_INT` and not `LKXINT`; the backslash was matched as a character, so an escaped pattern listed nothing.
- **A chain of `||` is refused once, over all its operands.** `'x' || (1 = 1) || 'y'` is `Invalid argument types
  for function '||': (VARCHAR(1), BOOLEAN, VARCHAR(1))` at the operator the chain hangs from, the last one of a
  plain chain and the outer one of a parenthesized chain, each operand listed with its own type; it was refused at
  the first operator over two types.
- **TO_ARRAY reads a vector as the array of its elements.** A FLOAT vector's are DOUBLEs:
  `TO_ARRAY([1.5,0.1234567,3]::VECTOR(FLOAT,3))` displays `[1.500000000000000e+00,1.234567016363144e-01,…]` and its
  subscript is the DOUBLE `0.1234567016`, where the array carried the vector's six-decimal text and answered
  `0.123457`.
- **ARRAY_TO_STRING spells a DOUBLE element as a FLOAT is cast to text** (`0.1234567016`, `3`, `1e+20`, not
  `0.12345670163631439`, `3.0`, `1.0E20`), and a nested array or object as the variant itself displays.
- **A privilege is held on one object.** Grants were kept under the upper-cased name, so a grant on `"t"` showed
  on `T` too, a REVOKE on either took it from both, and a renamed `"t"` moved `T`'s grants with it. A grant now
  belongs to the object its name reaches exactly, and to the one routine overload its argument types name. SHOW
  GRANTS spells the name with the quotes it needs (`DB.S."t"`, `DB.S."f"()`), marks a defaulted parameter
  (`DB.S.F(NUMBER, DEFAULT VARCHAR)`), and no longer lists a grant whose object is dropped. `GRANT … ON ALL
  FUNCTIONS | PROCEDURES` reaches each overload and counts it.
- **A grantee without a keyword is a role.** `GRANT SELECT ON TABLE t TO PUBLIC`, `REVOKE … FROM r`, the ALL, FUTURE,
  ON ACCOUNT, OWNERSHIP and WITH GRANT OPTION forms were syntax errors without `ROLE`. `GRANT ROLE r TO x` still
  needs its keyword, as on the account.
- **An object granted on without its kind is a table**: `GRANT INSERT ON t TO ROLE r`.
- **`CREATE OR REPLACE FUNCTION | PROCEDURE f(…) COPY GRANTS RETURNS …` keeps the replaced overload's grants**; it
  was a syntax error. Without OR REPLACE it is refused as on the account.
- **`METADATA$ROW_ID` is the row's own identity, spelled as the account spells it.** It was a counter of change
  events (`1`, `2`, `3`): an UPDATE's pair and a DELETE each took a new number, so a change could not be joined to
  the row it happened to. It is now forty hex digits that stay with the row: the same in every stream over the
  table, in a stream over a view of it and in a CHANGES clause, on an UPDATE's DELETE and INSERT, and on a later
  DELETE. A UNION ALL view's row reads the base row's id, a colon and twenty-eight base64 characters; a join view's
  row forty hex digits of its own. The digits are Frostlake's, not the account's, and are assigned anew when a
  persisted engine restarts.
- **A stream on a view lists `METADATA$ROW_ID` first** (`…, METADATA$ROW_ID, METADATA$ACTION, METADATA$ISUPDATE`),
  whatever the view; a stream on a table and a CHANGES clause keep it last.
- **A change set is typed without statistics.** A number read through a stream or a CHANGES clause is tagged by its
  declared precision (`SYSTEM$TYPEOF` of a `NUMBER(38,0)` holding 1 is `NUMBER(38,0)[SB16]`, of a `NUMBER(4,0)`
  `[SB2]`), where it took the tag of the rows it held. A standard stream on a table declares `METADATA$ACTION`
  `VARCHAR(6)` and `METADATA$ROW_ID` `VARCHAR(40)`, which a result's description and a CTAS over the stream keep.
- **DESCRIBE STREAM, TASK, PIPE and SEQUENCE take a qualified name**, a quoted one and `IDENTIFIER('…')`; each was a
  syntax error past a bare name. A missing pipe is named in full and a missing stream as it was written; a schema
  or a database that is not there is refused by its own name.
- **`SHOW PARAMETERS IN TABLE` lists the table's parameters**, each with its value and the level it comes from
  (`TABLE`, `SCHEMA`, `DATABASE` or the account default); it listed the session's. The table and a task are named
  qualified too, `FOR` reads as `IN`, a view and a stream list nothing, and a table now keeps the
  `MAX_DATA_EXTENSION_TIME_IN_DAYS` and `DEFAULT_DDL_COLLATION` it was given. `LIKE` over a database's or a schema's
  parameters is a pattern: `'CATALOG_SYN_'` names `CATALOG_SYNC`.
- **A select item takes CASE, CAST, CONSTRAINT, CROSS, DEFAULT, INNER, JOIN and WHEN as its alias without AS.**
  `SELECT 1 case`, `SELECT a cast FROM t` and `SELECT 1 join FROM t` were syntax errors; so was a CASE expression
  closed with `END CASE` (`SELECT CASE WHEN a THEN 1 END CASE`), which is that expression aliased CASE — a SQL
  function whose body is written so can now be created and called.
- **A dropped table takes the foreign keys other tables hold on it.** After `DROP TABLE parent` (CASCADE or not),
  `CREATE OR REPLACE TABLE parent`, `ALTER TABLE parent DROP PRIMARY KEY`, or the drop of the schema or database
  that holds it, the child keeps its column and loses the constraint: INFORMATION_SCHEMA.TABLE_CONSTRAINTS, SHOW
  IMPORTED KEYS and GET_DDL no longer list a key to a table that is gone. UNDROP does not bring it back.
- **`CREATE OR REPLACE TABLE T` replaces no table `"t"`.** A table whose name differed only in case was dropped
  in its place; the two now stand side by side, as they do after a plain CREATE.
- **A clone is of the kind its statement writes, and LIKE never takes the source's kind.** `CREATE TRANSIENT TABLE
  c CLONE temp` makes a transient table and `CREATE TEMPORARY TABLE c CLONE transient` a temporary one; `CREATE
  TABLE l LIKE temp` and `LIKE transient` make permanent tables. Each used to inherit the source's kind.
- **INFORMATION_SCHEMA.TABLES reports a temporary table with IS_TRANSIENT NULL and RETENTION_TIME 0**; it said NO
  and 1.
- **`CREATE TABLE case (…)` answers `Table CASE successfully created.`** A table named by a keyword was named
  twice in the sentence (`Table case CASE …`).
- **A failing multi-statement request quotes thirty characters of the statement that failed**, with `...` after
  them when it is longer (`… failed on statement "SELECT * FROM nowhere_xyz12345..."`); it quoted the whole
  statement.
- **A SQL function whose body is a query types its call as the inlined query**, not by its RETURNS clause. Over
  `f(a NUMBER) RETURNS NUMBER AS 'SELECT a + 1'`, `SYSTEM$TYPEOF(f(1))` is `NUMBER(2,0)[SB1]` and `f(n)` over a
  NUMBER(5,2) column `NUMBER(6,2)[SB2]`, as for the expression body `a + 1`; a body that reads a table —
  `SELECT MAX(x) FROM other` — is typed as that scalar subquery (`NUMBER(10,2)`). A table created from such calls
  declares those types. The call was typed `NUMBER(38,0)[SB16]`.
- **A materialized view takes a clustering key.** `CREATE MATERIALIZED VIEW mv CLUSTER BY (x) COMMENT = 'c' AS …`
  (the key on either side of the comment, read over the view's own columns) was a syntax error. `ALTER
  MATERIALIZED VIEW … CLUSTER BY (…)`, `DROP CLUSTERING KEY` and `SUSPEND | RESUME RECLUSTER` are taken, SHOW
  MATERIALIZED VIEWS lists `cluster_by` (`LINEAR(x + 1, y)`) and `automatic_clustering`, INFORMATION_SCHEMA.TABLES
  `CLUSTERING_KEY` and `AUTO_CLUSTERING_ON`, and GET_DDL prints `cluster by (…)`. A persistent database keeps
  the key. Two COMMENT clauses are `duplicate property 'COMMENT';`, where a syntax error was raised.
- **SQL table functions can be overloaded, called by parameter name, and leave out a parameter with a default.**
  `TABLE(f(1))` and `TABLE(f('1'))` reach `f(p NUMBER)` and `f(p VARCHAR)` — two table functions of one name were
  `Unknown table function F` — and `TABLE(f(p => 1))` runs.
- **A set operation settles its column's collation by precedence.** `SELECT c FROM t UNION SELECT c COLLATE 'en-cs'
  FROM t` compares, orders and reports under `en-cs`: a collation a COLLATE names outranks a column's declared one,
  and a collated branch decides over one without. The column was left with no collation.
- **A collation named by COLLATE keeps its rank outside the query that wrote it** — through a derived table, a CTE, a
  view and a set operation: `SELECT … FROM (SELECT c COLLATE 'en-cs' AS x, c FROM t) WHERE x = c` compares under
  `en-cs`, where it was refused as `Incompatible collations`. A table created from such a query declares the
  collation as its own, as before. A CTE's column list and a derived table's alias list keep the collation.
- **A view or a SQL function over `GET_DDL` of an object that is not there is created**, as on the account, and
  hands the refusal to whoever reads the view or calls the function; `CREATE VIEW v AS SELECT GET_DDL('table',
  'missing') AS d` was refused. `GET_DDL('view', 'v')` prints such a view, and `GET_DDL('table', NULL::INT)` is NULL.
- **GET_DDL's result is a VARCHAR of no width**: `SYSTEM$TYPEOF(GET_DDL('table', 't'))` is `VARCHAR[LOB]`, and
  `UPPER` over it too; it was `VARCHAR(134217728)[LOB]`.
- **A wall clock the session zone's clocks skip, written as text, is read where it stands.** In Los Angeles
  `'2024-03-10 02:30:00'::TIMESTAMP_LTZ` is `01:30:00 -0800` and the same text as a TIMESTAMP_TZ `02:30:00 -0700`,
  as on the account; both used to move forward to `03:30:00 -0700`. The digits are kept, at the offset before the
  change at UTC and east of it (`'2024-03-31 01:30:00'` in London is `01:30:00 Z`), and west of UTC at the offset
  after it from the gap's second second on. A TIMESTAMP_NTZ converted to a zoned type still moves forward.
- **`TIMESTAMP_LTZ_FROM_PARTS` and `TIMESTAMP_TZ_FROM_PARTS` build zoned values.** Each answered a bare wall clock:
  no offset in `TO_VARCHAR`, and an epoch counted as if the clock were UTC. The parts now stand in the session's
  zone — `TIMESTAMP_LTZ_FROM_PARTS(2024, 1, 10, 2, 30, 0)` is `02:30:00 -0800` in Los Angeles — a skipped clock
  moves forward, and `TIMESTAMP_TZ_FROM_PARTS` takes a zone as its eighth argument.
- **An invalid regular expression is refused in the account's words** — `trailing \`, `missing ): (`,
  `missing ]: [a`, `no argument for repetition operator: *`, `invalid repetition size: {2,1}`, `unexpected ): )`,
  `invalid character class range: z-a`, `invalid escape sequence: \1` — where Java's own text came through
  (`Unclosed group near index 1`).
- **Patterns the account reads and Java did not, or read otherwise**: a repetition of a repetition (`a**`), a
  brace that opens no count (`a{,2}`, `a{x}`, `a{2`), `\0` for NUL and `\v` for a vertical tab, and a POSIX
  class inside brackets (`[[:alpha:]]`, `[[:^alpha:]]`), which matched its own letters. In the parameters the
  later of `c` and `i` decides: `'ic'` matches case.

- **ALTER SESSION SET judges its whole list before anything applies.** `ALTER SESSION SET LOCK_TIMEOUT = 1,
  QUERY_TAG = 5` used to leave LOCK_TIMEOUT at 1 after refusing the tag. With two faults the refusal follows the
  account's order — a value of the wrong kind before one out of range, NUMBER parameters before STRING and
  BOOLEAN ones, names by their text — not the order written.
- **ALTER SESSION SET reads the values the account reads.** A negative number (`LOCK_TIMEOUT = -1`), a bare word
  (`TIMEZONE = UTC`, `BINARY_OUTPUT_FORMAT = base64`, stored upper-cased), a quoted or dotted name, a
  `$session_variable`, and assignments with no comma between them (`SET LOCK_TIMEOUT = 4 QUERY_TAG = 't'`) were
  syntax errors. `= NULL` puts the parameter back on its default instead of storing the text `null`, and an
  unknown name is echoed as written (`invalid parameter 'nosuch'`).
- **SYSTEM$ABORT_SESSION ends the session it names.** It answered `Session aborted.` and did nothing. It now
  answers the account's `session [<number>] terminated.`, rolls back the session's open transaction, and the
  session's next statement is refused — "Session no longer exists.  New login required to access the service."
  on an in-process connection, the 404 of a released session over HTTP. A number no session has answers the same
  sentence and ends nothing.
- **RESULT_SCAN reads the status row of a statement that is no query.** After a DDL, a SET or UNSET, a USE, an
  ALTER SESSION, a transaction statement, a GRANT or a COMMENT, `RESULT_SCAN(LAST_QUERY_ID())` answered
  `Statement <id> not found`; it now answers the statement's own `status` row, as on the account.
- **SHOW's `arguments` column lists a routine's argument types alone**, `F3(NUMBER, VARCHAR) RETURN VARCHAR`
  where it printed the parameter names too. A table function or a `RETURNS TABLE` procedure prints its columns
  (`RETURN TABLE (A NUMBER, b c VARCHAR)`), a table procedure is flagged in `is_table_function`, and DESCRIBE
  PROCEDURE's `returns` row and INFORMATION_SCHEMA.PROCEDURES spell it the same way instead of `VARCHAR`. A
  table function's quoted return column keeps its case.
- **INFORMATION_SCHEMA.FUNCTIONS and PROCEDURES spell `data_type` in full** — `VARCHAR(134217728)`,
  `VARCHAR(10)`, `NUMBER(38,0)`, `TIMESTAMP_NTZ(9)`, `BINARY(67108864)` — and report a text return type's
  `character_maximum_length`.
- **SHOW listings declare their numeric and timestamp columns as the account does**: sizes, ids and totals
  NUMBER(38,0) (`rows`, `bytes`, `session_id`, a transaction's `id`, a role's grant counts, a sequence's
  `next_value`), small counts NUMBER(9,0) (`min_num_arguments`, `key_sequence`, a warehouse's cluster and query
  counts, a network policy's entry counts), `search_optimization_progress` a number, `stale_after` and
  `dropped_on` TIMESTAMP_LTZ(3). A driver read BIGINT or INTEGER with no precision before, and a table created
  over such a listing's RESULT_SCAN copied the wrong type.
- **A column DEFAULT too wide for its column names the value's storage class** when the INSERT leaves the column
  out (`FIXED[SB2](2,0)` for a DEFAULT 1000 in a NUMBER(2,0)), as the account does; a DEFAULT written in the VALUES
  list keeps the column's class.
- **A call with a number of arguments no overload takes says so.** `SELECT g(1, 2)` over a one-argument `g` was
  `Unknown function G`; it is now `too many arguments for function [G(1, 2)] expected 1, got 2`, and any other
  count no overload takes `Invalid argument types for function 'G': ()`, at the call and over an empty table
  too. A CALL that leaves a required argument out is refused the same way while it compiles.
- **Named arguments that fit no signature are refused in the account's sentence**, `named arguments [A, Z] do
  not match any signature for function F`: a name no parameter has, a quoted name of another case, a required
  parameter left out, for a function with one overload or several and for a CALL. A name written twice is
  `duplicate named argument A for function F`.
- **`TABLE(proc())` converts each value to its declared column's type**, as the account does: 1.7777 under
  `NUMBER(5,2)` reads 1.78 where the CALL keeps 1.7777. A failure of the procedure is reported where its name
  stands (`Stored procedure execution error on line 1 position 20: …`), arguments it does not take are refused
  at its name, and a call written with named arguments is taken.
- **A Snowscript UDF's refusal names the whole expression, re-printed**: `RETURN f2() + 1` is refused as
  `F2() + 1`, an IF's condition as `(NOSUCH(1)) > 0`, `RETURN (SELECT 1)` as `(SELECT 1 AS "1" FROM (VALUES
  (null)) DUAL)`, where the call or the subquery alone was echoed as written.
- **Unqualified names fall back along the session's SEARCH_PATH.** Under the default `$current, $public`, a
  table, a view, a sequence, a function or a procedure the current schema does not hold is found in the
  database's PUBLIC — by a query, an INSERT, UPDATE, DELETE or MERGE, `seq.NEXTVAL`, a function call, a CALL,
  DESCRIBE TABLE and VIEW, GET_DDL, SHOW COLUMNS, a GRANT on a table, and the source of CREATE TABLE … LIKE,
  CLONE and of a stream — where each used to be an unknown object. The current schema still wins, a path set
  with `ALTER SESSION SET SEARCH_PATH` is followed in its own order, and `CURRENT_SCHEMAS()` lists every schema
  of the path. As on the account, a statement that changes or drops an object (ALTER, DROP, TRUNCATE, COMMENT ON),
  the describing of anything but a table or a view, and the body of a view look in the current schema alone.
- **A column DEFAULT over a sequence follows the sequence.** `ALTER SEQUENCE … RENAME TO` used to break every
  INSERT into a table whose DEFAULT named the sequence (`invalid identifier 'S7.NEXTVAL'`). The column is now
  bound to the sequence itself: DESCRIBE TABLE, INFORMATION_SCHEMA.COLUMNS and GET_DDL name it in full under
  its current name (`DB.SCHEMA.S8.NEXTVAL`, a quoted name with its quotes), SHOW COLUMNS names it in its
  `autoincrement` cell, and a table made LIKE it or as its CLONE draws from the same sequence. A cloned schema or
  database carries clones of its sequences, and its tables draw from those; a sequence outside what was cloned
  stays the one drawn from.
- **ALTER SEQUENCE takes the forms the account takes.** `SET COMMENT = '…'`, `UNSET COMMENT`, `SET NOORDER`, the
  step without SET (`INCREMENT BY 5`, `INCREMENT = 5`, `INCREMENT 5`) and several properties in one statement,
  with or without commas, were syntax errors. A property written twice is `duplicate property
  'SEQUENCE_INCREMENT';`, START is `invalid property 'SEQUENCE_START' for 'SEQUENCE'`, and a sequence that is
  NOORDER cannot be made ORDER again (`Invalid operation ALTER NOORDER SEQUENCE TO ORDER`).
- **A sequence nothing has drawn from serves its START value first, whatever its step is changed to.** `CREATE
  SEQUENCE s START = 10`, `ALTER SEQUENCE s SET INCREMENT = 3`, `SELECT s.NEXTVAL` answered 12.
- **A SQL function's body runs where the function lives.** `CURRENT_SCHEMA()` inside it is the function's schema;
  a table function's query reads its own schema's tables; and a name the body cannot resolve is the
  statement's own compilation error, without an `Error in SQL function body:` prefix.

### Added

- **SYSTEM$REFERENCE.** `SYSTEM$REFERENCE('TABLE', 't' [, scope [, privilege, …]])` answers a token,
  `ENT_REF_TABLE_` then sixteen decimal and forty hexadecimal digits, new for every call, after judging the scope, the
  domain, the privileges and the object as the account does; it was an unknown function in a query and its first
  argument in a block (live-verified).
- **ALTER VIEW column policies and tags.** `ALTER VIEW v {ALTER | MODIFY} [COLUMN] c SET | UNSET PROJECTION POLICY`,
  `… COLUMN c SET MASKING POLICY p [USING (c, …)] [FORCE]` and its UNSET, several masking columns in one statement,
  and `… COLUMN c SET | UNSET TAG`: the view's readers see the column masked or refused, POLICY_REFERENCES,
  TAG_REFERENCES_ALL_COLUMNS, GET_DDL and DESCRIBE VIEW report the policies, and a clone or a snapshot keeps them. An
  ALTER TABLE naming the view sets and unsets the projection policy too, and is told `cannot use ALTER TABLE on a view;
  use ALTER VIEW instead` for a masking policy. Each was a syntax error, and the ALTER TABLE a missing table
  (live-verified).
- **EXPLAIN of a CREATE, DROP, ALTER, UNDROP, TRUNCATE, SHOW, DESCRIBE, session variable SET and LIST.** The statement
  is compiled as the account compiles it for a plan and nothing it would do is done: a CREATE TABLE … AS SELECT and a
  materialized view are a bare `Result` over the `CreateTableAsSelect` that names the object over what the query reads,
  every other statement a bare `Result`; a missing object, a name already taken and an unknown session parameter are
  refused as the statement refuses them. USE, CALL, COMMENT, UNSET, REMOVE, EXECUTE, VALUES, a second EXPLAIN and the
  transaction statements stay syntax errors at their first word. Each was a syntax error at its first word
  (live-verified).
- **CREATE OR ALTER MASKING POLICY and CREATE OR ALTER ROW ACCESS POLICY.** The statement makes the policy, or
  changes an existing one to what it writes: its body, which an attached column reads at once, its COMMENT and a
  masking policy's EXEMPT_OTHER_POLICIES, a property left out unset. Another return type or argument list is refused,
  `Property 'FUNCTION_RETURN_TYPE' cannot be changed in CREATE OR ALTER.` / `… 'FUNCTION_SIGNATURE' …`.
- **The HTTP JDBC driver's query timeout and cancel.** `Statement.setQueryTimeout` and `Statement.cancel()` end a
  running statement over HTTP as they do in process, `SQL execution canceled` with SQLSTATE 57014 and code 604. On
  the wire a request carries `queryTimeout`, `POST /api/sessions/{id}/cancel` cancels what the session runs, and a
  canceled statement's answer carries `sqlState` and `errorCode` (`docs/http-api.md`).
- **Snowpark Java geospatial values.** `Row.getGeography` and `Row.getGeometry`, and the `Geography` and `Geometry`
  classes of `com.snowflake.snowpark_java.types`: a GEOGRAPHY cell arrives as a `Geography` over its GeoJSON, a
  GEOMETRY as a `Geometry` with every number written with fifteen decimals and an exponent, `getString` and
  `getVariant` read the text, and another cell fails the cast naming the class Snowpark holds the value as,
  `class java.lang.Long cannot be cast to class com.snowflake.snowpark.types.Geography`.
- **Authentication and packages policies.** CREATE [OR REPLACE] AUTHENTICATION POLICY [IF NOT EXISTS] records the
  policy's methods, client types, security integrations, MFA enrollment and comment, each judged in the account's
  words (`NOPE invalid for Authentication Methods.`); CREATE [OR REPLACE] PACKAGES POLICY [IF NOT EXISTS] … LANGUAGE
  PYTHON its allow, block and creation block lists and comment. ALTER … SET, UNSET and RENAME TO, DROP, SHOW
  AUTHENTICATION POLICIES and SHOW PACKAGES POLICIES, DESCRIBE and an authentication policy's GET_DDL answer as the
  account does, the name is shared with the schema's other policies — CREATE OR REPLACE, ALTER, DROP and DESCRIBE of
  another kind's policy refuse it for its kind — and UNDROP is refused as for a session policy. Each statement was a
  syntax error, or a policy that could never be found. The policies are recorded and never enforced.
- **A session policy's agent scope.** `AGENT_RESTRICTED_SESSION_SCOPE` takes one of the predefined scopes, written as a
  string or a word, which DESCRIBE shows and GET_DDL quotes; a fully qualified name is a scope that does not exist and
  anything else `'x' is not a valid RESTRICTED_SESSION_SCOPE value. …`. NULL and UNSET take it away.
- **Session policies.** CREATE [OR REPLACE] SESSION POLICY [IF NOT EXISTS], ALTER SESSION POLICY (SET, UNSET, RENAME
  TO, SET TAG, UNSET TAG), DROP, SHOW SESSION POLICIES (LIKE, IN, STARTS WITH, LIMIT) and DESCRIBE SESSION POLICY
  record a session policy as the account does: its four timeouts, each a whole number in its range, and its two
  secondary-role lists, which name roles or ALL alone. The name is shared with the schema's other policies.
  GET_DDL('POLICY', …) prints the policy whole, SYSTEM$GET_TAG and TAG_REFERENCES read the SESSION POLICY domain,
  and UNDROP SESSION POLICY is refused as the account refuses it. Each statement was a syntax error, or a policy that
  could never be found. The policy is recorded and never enforced.

- **External volume grants, and SHOW PARAMETERS IN ALERT.** SHOW GRANTS ON EXTERNAL VOLUME, and ON VOLUME, lists a
  volume's OWNERSHIP row under the kind EXTERNAL_VOLUME; GRANT and REVOKE take USAGE on a volume, and GRANT OWNERSHIP
  moves it; the REST API answers a volume's `created_on`, `owner` and `owner_role_type` from that row, where they were
  null. SHOW PARAMETERS IN ALERT lists the parameters a task carries for its runs without the task's own; each was a
  syntax error.

- **TIMESTAMP WITH TIME ZONE, TIMESTAMP WITHOUT TIME ZONE, and COMMENT ON ICEBERG TABLE.** The worded spellings of
  TIMESTAMP_TZ and TIMESTAMP_NTZ are types wherever a type is written — a column, a cast, a routine's signature, an
  Iceberg table's column — with a precision after the words (`TIMESTAMP WITHOUT TIME ZONE(3)`), as TIMESTAMP WITH LOCAL
  TIME ZONE was; each was a syntax error. COMMENT ON ICEBERG TABLE comments on a table as COMMENT ON TABLE does, and is
  refused for an Iceberg table, whose comment ALTER ICEBERG TABLE sets.

- **`TABLE <name>` as a query.** `TABLE t` reads every column of t as `SELECT * FROM t` does, time travel and
  IDENTIFIER() included, wherever a query may stand: a statement, with ORDER BY, LIMIT and FETCH after it, a subquery,
  an IN's, an EXISTS's and a quantifier's query (`1 = ANY (TABLE t)`), a set operation's operand, a derived table, a
  CTE's, a view's, a CTAS's and an INSERT's body. It was a syntax error.

- **The JavaScript procedure API's result set and statement members.** A result set answers `getColumnName`,
  `getColumnType`, `getColumnSqlType`, `getColumnScale`, `isColumnNullable`, the ten `isColumn…` kinds and
  `getColumnDescription` for a column by number or name, before a row is read as after, and `getColumnValueAsString`,
  `getQueryId`, `getNumRowsAffected`, `getSqlcode`, `isDml` and `getOutParameterValues`. A statement answers the same
  column getters, `getQueryId`, `getStatus`, `getPersistedStatus`, the rows its DML inserted, updated, deleted and
  updated through a join more than once, `isDml`, `getOutParameterValues`, `getProcedureStack`, `getRequestId`,
  `getStatementId`, `wait` and `cancel`, refusing `Statement is not executed yet.` before it ran, as the account's do;
  its `getColumnName` refuses a column it does not have, where it answered null.
- **CREATE AGGREGATE FUNCTION**, in Python and Java. A Python handler is a class whose `accumulate` reads each row
  of a group — a NULL argument as `None` — and whose `finish` answers the group's value; a Java handler's
  `initialize()`, `accumulate(state, …)`, `merge(state, state)` and `finish(state)` carry the group's state. CREATE
  requires each of those methods, in the account's order and sentences (`Could not find merge method in function F
  with handler H`, `Failed to find a public method named "finish" with 1 arguments`), and refuses an aggregate of
  another language, one returning a table and one taking no argument. The call groups, filters by HAVING, orders and
  rolls up as a built-in aggregate does, over no rows answers NULL, and is refused under OVER, with DISTINCT or WITHIN
  GROUP (`Unknown function F.`) and in a PIVOT; a refusal echoing the plan names it `EXTENSION_FUNC_A`. SHOW
  FUNCTIONS, DESCRIBE FUNCTION, INFORMATION_SCHEMA.FUNCTIONS and GET_DDL tell it as an aggregate, and the REST API's
  `is_aggregate` creates one instead of answering 501.
- **ALTER SCHEMA ... SWAP WITH and ALTER DATABASE ... SWAP WITH.** Two schemas, or two databases, trade names, each
  keeping its objects and their rows; a swap with itself leaves it as it was, and the schema to swap with is looked
  for first, so a missing one is refused even under IF EXISTS. Both used to be syntax errors.
- **INFORMATION_SCHEMA.COPY_HISTORY.** `TABLE(INFORMATION_SCHEMA.COPY_HISTORY(TABLE_NAME => 't', START_TIME => ts
  [, END_TIME => ts]))` answers the files COPY loaded into a table within the range, in the account's 21 columns, and
  refuses a call short of its table or start time, a NULL table and a text time in the account's sentences.
- **ORDER BY ALL [ASC | DESC] [NULLS FIRST | LAST]** orders a query by every column it answers, left to right: in
  a plain query, a grouped or windowed one, a set operation, a subquery, a view and a block's `SELECT … INTO`. It
  stands alone, and a window's OVER and a WITHIN GROUP refuse it at the ALL, as the account does.
- **SYS_CONNECT_BY_PATH(expr, separator)** in a CONNECT BY query: the path from the root to the row, each value
  after the separator (`/ceo/vp`). Outside a hierarchical query it stays `Unknown function SYS_CONNECT_BY_PATH.`
- **Snowpark `Row`: `toList()`, `clone()`, `getObject(int)`, `getListOfVariant(int)` and `getMapOfVariant(int)`**,
  as the Java API has them; `com.snowflake.snowpark.SnowparkClientException` is the exception a number accessor
  fails with.
- **Statements for kinds with no object here.** DROP and DESCRIBE of a resource monitor, a session, a share, an
  application and its package, a Git repository, a semantic view, a connection and the policy kinds resolve
  the name and miss it as the account does; `DROP EVENT TABLE [IF EXISTS]`; `ALTER EXTERNAL TABLE` judges its
  action, then its name.
- **`USE SECONDARY ROLES`** replaces the roles in use, and CURRENT_SECONDARY_ROLES() answers the setting.
- **`GRANT` and `REVOKE` take IDENTIFIER() names** for the object, a bulk grant's schema or database, and the
  role, the user or the database role that is granted or holds the grant.
- **`SHOW DATASETS`, `SHOW CLASSES` and SHOW of a built-in class's name**; the SNOWFLAKE database holds the
  schemas an account's holds, empty where nothing is modelled.
- **`ALTER WAREHOUSE … UNSET`** puts MAX_CONCURRENCY_LEVEL, the two statement timeouts and the type back on
  their defaults.
- **SEARCH's analyzers and PHRASE mode.** UNICODE_ANALYZER cuts at the word boundaries, NO_OP_ANALYZER does not
  cut, and `SEARCH_MODE` takes PHRASE; both options are judged while the statement compiles.
- **Read-only requests and sessions.** A request may ask to be run read-only (`"readOnly": true`), and a
  session may be opened so: it runs only when every statement is a query, a SHOW, a DESCRIBE or an EXPLAIN that
  reaches no write, and is refused before anything runs otherwise (`docs/http-api.md`).
- **`http.cors.allowedOrigins`** names the pages that may call the HTTP server; a preflight from one is answered
  with the CORS headers.
- **`TIMESTAMP_FROM_PARTS(date, time)`.** The names that build a TIMESTAMP_NTZ also take a date and a time —
  a text is read as either. Another pair is refused by that signature's name, `Invalid argument types for
  function 'TIMESTAMP_NTZ_FROM_DATE_TIME': (TIME(9), DATE)`, and three to five arguments are short of the
  parts: `not enough arguments for function [TIMESTAMP_NTZ_FROM_PARTS(2024, 3, 15)], expected 6, got 3`.
- **The function catalog lists every function the engine calls.** `docs/functions.md` gains a row for each
  of 184 names that had none — the mathematical, conditional, text, conversion, date, array and object,
  aggregate and window functions among them — each row telling what the function answers at its edges.
- **`SEARCH_MODE => 'EXACT'`.** The query must stand in the data as it is written — case apart, the characters
  between its tokens included — with no token running on before it or after it: `'a, b'` is found in `'x a, b!'`
  and not by `'a,b'`, `'a b'` or `', b'`; a trailing delimiter of the query has to be there too; an empty query
  finds a text with no token; nothing is found across two columns. Under the `NO_OP_ANALYZER` the two must be
  equal, and the `UNICODE_ANALYZER` does not take the mode.

- **CREATE [OR REPLACE] EVENT TABLE [IF NOT EXISTS] <name> CLONE <event table>.** Only another event table is a
  source: a table is `Invalid operation Cannot clone an Event table from a table.`, a dynamic table, a view and a
  stream are refused in sentences of their own, and a clone takes no property of its own.

- **`ALTER MATERIALIZED VIEW … RENAME TO` a name with a path.** Only a bare name was read. A qualified one
  places the view in the schema it names, as on an account.
- **`SHOW TERSE LOCKS` and `SHOW TERSE TRANSACTIONS`.** Both were a syntax error; an account takes the word
  and answers the same columns.
- **`ALTER MATERIALIZED VIEW … SET SECURE` and `UNSET SECURE`.** Both were a syntax error; they switch the
  view's `is_secure`, as on an account.
- **Twenty-four more SHOW listings.** `SHOW EXPORTED KEYS`, `SHOW REGIONS`, `SHOW GRANTS ON ACCOUNT` and the
  listings of the kinds the engine has no object of — `RESOURCE MONITORS`, `SHARES`, `CONNECTIONS`, `APPLICATIONS`,
  `APPLICATION PACKAGES`, `INDEXES`, `CHANNELS`, `SNAPSHOTS`, `GIT REPOSITORIES`, `SEMANTIC VIEWS`, the `SESSION`,
  `AUTHENTICATION`, `PACKAGES` and `PRIVACY POLICIES`, `EXTERNAL FUNCTIONS`, `DATA METRIC FUNCTIONS`, `REPLICATION
  DATABASES`, `REPLICATION GROUPS`, `FAILOVER GROUPS`, `REPLICATION ACCOUNTS` and `DELEGATED AUTHORIZATIONS` — were
  syntax errors or a class that does not exist. Each now answers the columns an account declares for it, with their
  types, and reads the scopes and modifiers the account reads for that listing: the account's own things refuse a
  database, schema or table scope by its shape, the schema's things name a schema or a database that does not
  exist, `INDEXES` and `CHANNELS` take a table, the replication listings take a LIKE and an account, and
  `DELEGATED AUTHORIZATIONS` takes `BY USER <name>` or `TO SECURITY INTEGRATION <name>`. The kinds themselves are
  still not created here, so their listings are empty (see `docs/scope.md`). Three answer rows: `SHOW EXPORTED
  KEYS` lists the foreign keys that reference the tables of its scope, as `SHOW IMPORTED KEYS` lists them from the
  other side; `SHOW GRANTS ON ACCOUNT` lists the privileges granted on the account, named by its locator — the
  word `ACCOUNT` there is the account and no longer a table of that name; and `SHOW REGIONS` lists the 48 public
  regions, narrowed by LIKE over the region's identifier, and refuses any scope as an account does.
- **TERSE on the grant listings.** `SHOW TERSE GRANTS`, `SHOW TERSE GRANTS ON …`, `… TO ROLE | USER …` and `… OF
  ROLE …` were syntax errors; an account reads the word and answers the same columns, and so does the engine now.
- **Session parameters on a user and on the account.** `ALTER USER <name> SET <parameter> = <value>` and `ALTER
  ACCOUNT SET …` were syntax errors. Both are read now, with `UNSET` beside them. A user's parameters stand among
  its properties, with or without commas (`ALTER USER u SET COMMENT = 'c' QUERY_TAG = 't'`), a text property takes a
  word or NULL as well as a string, and `ALTER USER SET …` without a name alters the session's own user; the
  account takes every parameter it lists. A session runs with its own setting, else its user's, else the account's,
  and `SHOW PARAMETERS` names the level the value in force came from — SESSION, USER or ACCOUNT; `SHOW PARAMETERS
  IN USER` reports a user's own settings and `SHOW PARAMETERS IN ACCOUNT` also lists the parameters only the
  account carries. The settings survive a restart. A list is judged whole, as the account judges it — a name
  written twice, a name that is no parameter's (`invalid property 'X' for 'USER'`, or `for 'ACCOUNT'`), a value of
  the wrong kind, and for a user that exists a value its parameter does not take — and nothing of it applies when
  any of it is refused. Not modelled yet: the MODIFY privilege an account asks for, and a new session starting
  with its user's AUTOCOMMIT.

### Changed

- **Both JDBC drivers read results as the account's driver reads them, through one shared result set.** The HTTP
  driver's result sets move forward only: `previous`, `absolute`, `first`, `last`, `beforeFirst`, `afterLast` and
  `relative` throw `SQLFeatureNotSupportedException` and `getType` is `TYPE_FORWARD_ONLY`, as in process; a cursor
  closes at commit (`getHoldability` 2). The typed getters convert a cell's text as the account's driver does:
  `getBoolean` reads 1, 0, `true` and `false` (a NUMBER 1 is now true) and refuses any other number or text;
  `getInt`, `getShort` and `getLong` read a FLOAT's whole part and a BOOLEAN as 1 or 0 and refuse a fixed-point
  number with a fraction (by label too, where the in-process driver read 3), a number past their type and a text
  that is no integer, all with `Cannot convert value in the driver from type:… to type:…, value=….` (SQLSTATE
  0A000, code 200038). A label names the first column matching it ignoring case on both drivers, a missing one is
  `Column not found` with SQLSTATE 42703, a missing position `Invalid column index` (22000, 200032); `getRow` keeps
  the last row's number after the rows run out, and a closed result set refuses reads with `Result set has been
  closed.` while `next()` answers false (live-verified).
- **DatabaseMetaData answers the account's driver about the SQL**: NULLs sort high, a DDL statement commits, names
  run to 255 characters, `$` is an extra name character, the keyword and escape-function lists are the account's,
  and `getRowIdLifetime`, `autoCommitFailureClosesAllResultSets`, `generatedKeyAlwaysReturned` and `isWrapperFor`
  are refused as it refuses them; `supportsGetGeneratedKeys` is false on both drivers (live-verified). The product
  and driver keep Frostlake's names; `getURL` is the URL the HTTP connection opened, and `getUserName` the user the
  session runs as.
- **JDBC binds of timestamps, times and java.time values follow the account's driver.** `setTimestamp` without a
  Calendar (and `setObject` of a `Timestamp`) binds the value's moment, so a TIMESTAMP_NTZ column holds that moment
  as the session's zone shows it, where it held the client JVM's wall clock. `setTime` (and `setObject` of a `Time`)
  binds the time its moment shows in UTC. `setObject` of a `java.time` value — `LocalDate`, `LocalDateTime`,
  `LocalTime`, `OffsetDateTime`, `Instant` — is refused `Data type not supported for binding: Object type: class
  java.time.LocalDate.`, as the account's driver refuses it. Bind a `java.sql.Date`, `Time` or `Timestamp` instead.
- **A JSON file's top-level array is one record unless STRIP_OUTER_ARRAY = TRUE.** A COPY load and a staged-file
  query read a file holding `[{…}, {…}]` as ONE record, the whole array, as the account does by default; with
  `STRIP_OUTER_ARRAY = TRUE` in the file format each element is a record, which is what every load did before. Add
  the option to a load or a file format that relied on the split.

- **The results kept for RESULT_SCAN are bounded by their size.** Besides `query.result.cache.size` results per
  session, all kept results together hold at most `query.result.cache.cells` values (2,000,000 by default); past it
  the oldest go, each session's latest result staying. Forty SELECTs of 100,000 rows used to keep 735 MB reachable
  and grow with every statement; they now level off near 220 MB.
- **Window aggregates read their frames without adding each one up again.** Over exact numbers a SUM, AVG or COUNT
  window reads any frame from the partition's prefix sums, a MIN or MAX from a tree over the partition, a
  whole-partition MEDIAN or other aggregate is computed once per partition, and a RANGE frame's offset bounds are
  found by halving the sorted keys. Over 10 000 rows `SUM(v) OVER ()` took 2 s and a `RANGE BETWEEN 5 PRECEDING AND 5
  FOLLOWING` frame 47 s; both now take well under a second, and grow with the rows rather than their square.
- **A join keyed through an earlier join or a self-join runs by key.** An equality reading a relation an earlier
  join brought in (`a JOIN b ON … JOIN c ON b.j = c.cj`, or the same over a comma join's WHERE) and one between the
  two aliases of a self-join (`t x JOIN t y ON x.id = y.parent`) now take the hash path instead of comparing every
  pair of rows. The plan's join stage names the keys it buckets by, as `| HASH J=CJ`.
- **An engine that keeps nothing across a restart no longer leaves stage files behind.** Its internal named stages,
  table stages and user stage live in `<stage.internal.localRoot>/engine-<id>` and go when the engine does, so a
  file PUT on `@~` of a server that does not persist is gone after a restart. An engine with a write-ahead log or
  snapshot persistence keeps them in `stage.internal.localRoot` as before.
- **A handler class a Java routine's code does not hold is reported with the class loader's frames.** `No class
  bytes generated for: G` is the account's `User Error Report` of `java.lang.ClassNotFoundException: G` with the
  three loader frames numbered as the routine's Java runtime (11, 17 or 21) numbers them, for a function, a
  procedure and a table function.
- **The HTTP server holds a request to its host, its origin and its content type.** On a loopback-bound server
  a request with another Host is refused with 421, one from an Origin the operator did not allow with 403, and
  a body of the SQL endpoints that is not declared `application/json` with 415 (`docs/http-api.md`).
- **`DatabaseHttpServer` runs its engine on the configuration file it is given**, a port argument winning over
  the file's port, and `durability.walFile` defaults to `wal.log` under the persistence directory in force.
- **The server chooses its lock by what a request can reach**, read from the parse the run reuses: a query that
  can reach a write runs alone. The remote JDBC driver counts a text's statements from the lexer's tokens, and
  the server has the last word.
- **SYSTEM$CLUSTERING_DEPTH answers a number and SYSTEM$CLUSTERING_INFORMATION the account's report.** The depth
  was a JSON text of the engine's own (`{"average_depth": 1.0, "table_name": …}`); it is the FLOAT `1.0`, and `0.0`
  for a table with no row. The information names `cluster_by_keys` (`LINEAR` and the key list as written),
  `total_partition_count`, `total_constant_partition_count`, `average_overlaps`, `average_depth`, `version` and
  `clustering_errors` over the one partition the engine keeps a table in.
- **SYSTEM$LAST_CHANGE_COMMIT_TIME names a missing object as it was written**: `Object 'NOSUCH' does not exist or not
  authorized.`, where it was completed with the session's database and schema.

- **A file transfer's size is a DECIMAL(10,0).** A GET's `size` and a PUT's `source_size` and `target_size` were
  declared NUMBER(10,0). The client answers a file transfer, and an account's driver names these columns by its
  own type: `getColumnTypeName` is `DECIMAL`, `getColumnType` `java.sql.Types.DECIMAL` though the scale is 0,
  `getColumnClassName` `java.math.BigDecimal` - which is what `getObject` now returns - and the display size 12.
  Both JDBC transports answer so, and the HTTP wire names the columns' type `DECIMAL` where it said `NUMBER`.
  LIST's `size` stays the NUMBER(38,0) it is.
- **`ResultSetMetaData.isSigned` answers true for a number.** The in-process transport answered false for every
  column and the HTTP one false for a NUMBER. As an account's driver answers: true for a NUMBER of any scale and
  for a FLOAT (reported DOUBLE), false for every other type.
- **A CALL converts every argument to its parameter's type, for every language.** A SQL procedure's BOOLEAN
  parameter given `'off'` or `0` holds FALSE (`a::VARCHAR` answered `off`), and a handler receives the converted
  value in its language's own type: a JavaScript handler a number for a text given to a FLOAT parameter, a string
  for a number given to a VARCHAR one, a `Date` for a DATE or a TIMESTAMP, a `Uint8Array` for a BINARY,
  `undefined` for a SQL NULL and the number 1 or 0 for a BOOLEAN; a Python handler an `int`, a `Decimal`, a
  `datetime.date`, `time` or `datetime`, a dict or a list, and `bytes`; a Java or a Scala handler the class its
  method declares (`int`, `long`, `BigDecimal`, `String`, `boolean`, `java.sql.Date`, `String[]`, …), where any
  value but one of exactly that class failed with `argument type mismatch`. A JavaScript procedure that tested
  `A === true` or `A === null` now reads `A === 1` and `A === undefined`, as it does on an account.
- **An ARRAY parameter wraps a variant that is no array.** `CALL p(PARSE_JSON('{"a":1}'))` into an ARRAY parameter
  hands the body `[{"a":1}]`.
- **A BEGIN in a procedure or a block run inside a transaction starts a transaction of its own.** It used to join
  the caller's, and its COMMIT ended that. Its COMMIT and ROLLBACK now end what it wrote itself alone, it sees
  nothing the caller has not committed, and the caller's transaction is open afterwards.
- **Fewer statements end the transaction they are issued in.** Creating a sequence, a function, a procedure, a
  schema, a stage, a file format, a stream, a tag, a masking or a row access policy, a task or a pipe no longer
  commits the open transaction, and neither do ALTER STAGE, ALTER FILE FORMAT, USE and a `CREATE TABLE IF NOT
  EXISTS` over a table that exists: the transaction is still open afterwards, as on an account. Creating a table
  or a view, ALTER, DROP and COMMENT end it as before.
- **What a failed CALL or block leaves in a transaction.** Inside an explicit transaction the statements a
  procedure or a block ran before the one that failed keep their changes, each being a statement of its own;
  the whole CALL or block used to be undone. The transaction's ROLLBACK removes them as before.
- **A JavaScript, Python, Java or Scala procedure that runs with its owner's rights runs where it lives.** The
  statements its handler issues ran in the caller's current database and schema: a bare `INSERT INTO audit_log`
  wrote the caller's table, `CREATE TABLE made (…)` created in the caller's schema and `CALL helper()` found the
  caller's procedure. They now run in the procedure's own database and schema, as a SQL body's statements do, and
  the caller is back where it was when the CALL returns or fails. A handler called from a SQL body and a SQL body
  called from a handler each run in their own home; one that runs with its caller's rights stays where the body
  that called it runs. A handler that only worked because the caller's schema held its tables now misses them:
  qualify the names, or declare the procedure `EXECUTE AS CALLER`.
- **A CSV load reads escapes and the default NULL_IF.** Outside an enclosure a backslash — or whatever
  ESCAPE_UNENCLOSED_FIELD names — now takes the field delimiter, a line break or itself literally: `a\,b` loads
  as the one field `a,b` and `x\\y` as `x\y`, where each character was loaded as written. The text `\N` loads as
  NULL unless the format's NULL_IF names other values or none (`NULL_IF = ()`). A staged-file query reads the
  same way, takes NULL_IF and ESCAPE from its named format, and reads `RECORD_DELIMITER = NONE` as one record per
  file and `FIELD_DELIMITER = NONE` as one field per record.
- **A failed statement's EXECUTION_STATUS is FAILED_WITH_ERROR.** The query-history table functions answered
  `FAILED`; a filter on that value now finds nothing.
- **UPDATE answers one column under ERROR_ON_NONDETERMINISTIC_UPDATE.** With the parameter set no row can be
  multi-joined, and the result is `number of rows updated` alone, as the account's is; without it the result
  keeps both columns.
- **A privilege refusal reads as the account's.** Where a role without a grant was refused with `Permission
  denied: User 'U' with roles […] does not have SELECT privilege on TABLE 't'`, the refusal is now `SQL access
  control error:` / `Insufficient privileges to operate on table 'T'. …`; an UPDATE or a DELETE by a role that
  cannot read the table names SELECT first.

- **A warehouse's `resource_constraint` is listed in upper case throughout** — `MEMORY_1X_X86`, `MEMORY_16X_X86`,
  `MEMORY_64X_X86` — and a refused value is echoed in upper case; the listing spelled `MEMORY_1X_x86`.
- **CREATE ROW ACCESS POLICY answers `Row access policy 'P' is successfully created.`**, the account's sentence.
- **The REST listings of tasks and network rules start a `fromName` page at the named object**, as the SHOW
  statements they relay now do; `tables:using-template` writes its `query` in parentheses, and the query has to
  read INFER_SCHEMA.
- **SHOW and DESCRIBE ARTIFACT REPOSITORY answer the account's eleven columns**: `api_integration` before `type`,
  then `index_url` and `authentication_secret` (both NULL), where nine were answered with the two swapped.
- **The HTTP wire omits `precision` and `scale` for a column whose type has neither** — text, binary, FLOAT,
  BOOLEAN, DATE, VARIANT, OBJECT, ARRAY, VECTOR and an untyped NULL — as the account's own result metadata leaves
  both null there; it used to send 0 for both. A fixed-point NUMBER still sends both, and a TIME or TIMESTAMP its
  fractional digits as `scale`. `http.ColumnData.getPrecision()` and `getScale()` return `Integer` (null when
  absent); `precisionOrZero()` and `scaleOrZero()` read them as before. JDBC metadata is unchanged.

## 0.2.0 — 2026-10-01

### Now refused (SQL that earlier versions accepted)

- **A signature after a DROP or DESCRIBE name is read the way the account reads it.** `DROP TABLE t (TRUE)` no
  longer drops the table: TRUE and FALSE inside the parentheses are syntax errors, and a `(` right after the name
  opens the signature, so `DROP TABLE t (SELECT 1)` is refused at the SELECT instead of running as two statements.
- **A table function called like a scalar function is refused while compiling**, `Unknown function GENERATOR.`,
  for GENERATOR, FLATTEN, SPLIT_TO_TABLE and a user-defined table function alike; the last used to return rows.
- **A FROM-less query's ORDER BY is compiled**: `SELECT 1 AS x ORDER BY nosuch` is an invalid identifier, and a
  subquery written as an ORDER BY key raises its own refusal at its own position.
- **A bare `CHAR`, `CHARACTER` or `NCHAR` cast is `VARCHAR(1)`**, so a longer value is refused as too long.
- **`UNSET TAG` of a tag that does not exist is refused**, for every object kind.
- **SHOW's modifiers are refused where the account's parser stops**: `SHOW TABLES LIMIT` at the end of input,
  `SHOW PRIMARY KEYS LIMIT 1` at the LIMIT, and `SHOW TRANSACTIONS LIKE '…'` is a syntax error.
- **RESULT_SCAN's argument is judged while compiling**, NULL and TRUE refused as no string and a computed
  argument as not constant, and **LAST_QUERY_ID refuses an index beyond ±10,000**. An integer passed to
  RESULT_SCAN reads that statement of the session, as LAST_QUERY_ID of the same index does.
- **Snowflake Scripting statements outside a block are refused at their first word.** A top-level `IF`, `FOR`,
  `WHILE`, `LOOP`, `REPEAT`, `CASE`, `LET`, `AWAIT` or assignment used to run; the account accepts them only inside a
  `BEGIN … END` block, in a script and in `EXECUTE IMMEDIATE` text alike.
- **An aggregate in a subquery's WHERE over the subquery's own columns is refused** even when the outer table has
  columns of the same names; such a query used to be answered.
- **Tag values are judged as the account judges them**: a number, a boolean, a list or an IDENTIFIER() as a tag value
  is an invalid value, and NULL, a plain name or a bind an unsupported type — all before any tag or object is looked
  up. `CREATE … WITH TAG` checks the tag's ALLOWED_VALUES before it creates the object.
- **ALTER TABLE … DROP COLUMN refuses to drop every column** (`cannot drop all the columns of a table`), checks
  the whole list before dropping anything, and reaches only the column a name spells: `DROP COLUMN "a"` beside an
  unquoted `A` is `column 'a' does not exist`.
- **A qualified column reference resolves exactly, like a bare one**: `t1.c` over a table whose only such column is
  a quoted `"c"` is `invalid identifier 'T1.C'`, in every clause and in DML, and a set operation's ORDER BY names an
  output column by its exact spelling.
- **A qualified star's EXCLUDE, RENAME or REPLACE naming a column its relation lacks is refused**
  (`column 'X' does not exist`), braced form included.
- **A scalar function over a star is judged on the columns the star stands for**: `UPPER(*)` over two columns is
  `too many arguments for function [UPPER(ST2.X, ST2.Y)]`, and in a grouped query each of those columns is held to
  the grouping.
- **A day-time interval literal beside NULL, a number or a text is refused while compiling**
  (`NULL + INTERVAL '1' DAY`, `1 + INTERVAL '1' DAY`, `'2024-01-01' + INTERVAL '1' DAY`), as on the account.
- **A CROSS JOIN takes no ON.** `t1 CROSS JOIN g ON t1.id = g.k` is a syntax error at the ON, where the clause
  used to run with the condition applied as a filter.
- **A null treatment inside a window function's parentheses belongs to FIRST_VALUE and LAST_VALUE alone.**
  `LAG(v IGNORE NULLS) OVER (…)` and `NTH_VALUE(v, 1 IGNORE NULLS) OVER (…)` are refused; written after the
  closing parenthesis they all still take it.
- **A `SELECT *` view whose base table has since changed width is refused when it is read**
  (`View definition for '<name>' declared 3 column(s), but view query produces 2 column(s).`), rather than
  answered at the table's current columns. A view with a written column list is unaffected.
- **`ALTER SESSION SET SEARCH_PATH` resolves the schemas it names as it runs**, refusing the first that does not
  exist (`Schema 'NOSUCH' does not exist`); any text used to be stored.
- **A LATERAL table function's arguments are type-checked against the columns they name.** `TABLE(SPLIT_TO_TABLE(t.ts,
  ' '))` over a TIMESTAMP column is `invalid type [TIMESTAMP_NTZ(9)] for parameter '1'` while the statement compiles,
  where the value used to be split as Java spells it. Only a literal argument was checked before.
- **Replacing, dropping, renaming or swapping an object needs OWNERSHIP of it.** CREATE OR REPLACE, DROP, ALTER …
  RENAME TO and SWAP WITH over an object whose owner is not the primary role, a secondary role or a role either
  inherits are refused (`Insufficient privileges to operate on table 'T'. Your primary role R must have OWNERSHIP
  granted on TABLE D.S.T.`), ACCOUNTADMIN included and IF EXISTS no exception; CREATE … IF NOT EXISTS over it
  still succeeds without touching it. A view is named as a view and granted on as a TABLE.
- **A parenthesized FROM group takes one source and the joins after it, never a comma**: `SELECT * FROM (t a, t b)`
  is refused at the group's first token and then at its closing parenthesis. `(t)` and `((a JOIN b ON …))` are
  still groups.
- **Junk after a complete statement is refused at the junk's first word** when the statement cannot take that word,
  however far a parse of the junk would run, whether the text runs or a client's statement-count gate reads it
  first; an alias, a LIMIT or a FETCH is still the statement's own, and after a DESCRIBE, a COPY, a CREATE or an
  ALTER … SET the word is read as a property name, as the account reads it.
- **A LATERAL derived table the account cannot evaluate is refused while compiling**, `Unsupported subquery type
  cannot be evaluated`: a correlated body under LIMIT, a body over a table function of the outer row, and a
  FROM-less body under a LEFT or FULL join with an ON (unless the left side holds one row and nothing outside reads
  the derived table).
- **A window ordered by a column whose value faults raises the fault over one row as over many**: LAG, LEAD, NTILE,
  NTH_VALUE and one-row partitions used to answer.
- **A name two output columns carry is ambiguous where a clause reads it** (`ambiguous column name 'A2'` for
  `SELECT a AS a2, a2 FROM t1 ORDER BY a2`): ORDER BY for any two, the other clauses and a later select item when
  one of the two is an alias. WHERE reads a relation column of the name first.
- **AT / BEFORE(STATEMENT => id) refuses a DDL statement's id** (`Statement <id> cannot be used to specify time for
  time travel query.`) and an unknown one (`Statement <id> not found`).
- **GRANT OWNERSHIP ON PIPE is refused while the pipe runs**; pause it first.
- **A HOST_PORT network rule refuses a value that cannot name a resolvable host** (`invalid value '…' for property
  'VALUE_LIST', Reason: One or more values might be an unresolvable host name. …`), by its shape.
- **`ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = 'AUTO'` is refused**; the zoned formats take AUTO.
- **`GRANT DROP` is refused for every object** (`Invalid object type 'TABLE' for privilege 'DROP'.`): DROP is no
  privilege to grant, since dropping an object takes its OWNERSHIP.
- **A DROP or DESCRIBE sent on its own reads its whole signature the way the account does**: keywords such as TYPE,
  IF or VIEW, the words d, t, ts, fn and oj, parameters after a dotted name and parameters a type does not take
  (`INT(1)`, `VARCHAR(a)`, `VARCHAR(1, 2)`) are refused, so `DROP TABLE t1 (VIEW)` no longer drops the table.
- **A string LIMIT or OFFSET value is a syntax error at its own place, before names and types are judged**, in a
  subquery and in EXECUTE IMMEDIATE's text too. A stored procedure's or a policy's body holding one is refused at
  CREATE; the procedure used to be created.
- **SHOW … IN DATASET <name> and IN MATERIALIZED VIEW <name> are refused where the account's parser stops**, at the
  word after the keyword; `SHOW DATASET` is a syntax error, and `SHOW USER FUNCTIONS IN MODEL m` is refused at the m.
- **Interval values are refused where the account refuses them.** A quoted-unit literal such as `INTERVAL '1 hour'` is
  refused while compiling wherever a value would be compared or chosen: a comparison, BETWEEN, IN, an ordering,
  NULLIF, NVL, IFF, COALESCE, a simple CASE or DECODE. An interval beside a VARIANT, compared or as a conditional's
  branch, is refused as a conversion either way round, and a text arm before an interval arm of a set operation as
  incompatible types; `||`, CONCAT, UPPER, SUBSTR, every LIKE spelling, ARRAY_CONSTRUCT, OBJECT_CONSTRUCT, DATE_TRUNC,
  TO_VARIANT, TO_JSON, DATEADD, DATEDIFF and the unary plus refuse an interval argument; and RESULT_SCAN over a result
  with an interval column is refused while compiling unless it is the bare `SELECT * FROM TABLE(RESULT_SCAN(…))`.
- **A column a USING or NATURAL join merged is ambiguous once a relation joined later carries the same name**: a bare
  reference to it is `ambiguous column name 'X'` while the statement compiles, in every clause and in a later join's
  ON, whether that relation is joined by ON, comma, CROSS or LATERAL. A positional `$n` over several relations is
  ambiguous when two of them are wide enough, and a qualified reference through an alias whose relation lacks the
  column is `invalid identifier 'ALIAS.COL'` even when the column is the key of a later USING or NATURAL join, where
  it used to read the merged key.
- **Join conditions compile with the statement.** An ASOF join's MATCH_CONDITION and ON refuse an unknown column even
  when a side holds no rows, and unknown functions, argument counts, unset binds and ambiguous names as any join
  condition does; a MATCH_CONDITION that is not a `>=`, `>`, `<=` or `<` comparison with each side reading its own
  relation is refused with the `SQL compilation error:` prefix, and one after a join that is not ASOF is a syntax
  error at the keyword. A table function that reads the left side and is joined with USING, or NATURAL over a common
  column, is refused before it runs. An Oracle `(+)` marker is refused under an OR, in an outer-join cycle, when its
  relation is outer-joined to two others, when nothing outer-joins its relation
  (`Column 'T.A(+)' not from an outer joined table.`), and in a conjunct that also reads that relation through an
  unmarked column.
- **Correlated subqueries the account cannot evaluate are refused as unsupported subqueries.** Under EXISTS or IN
  these include a GROUP BY that reads the outer row and an ungrouped aggregate without HAVING that the outer row
  filters (under IN, unless the correlation is an equality), and a correlated filter is refused with outer names on
  both sides of a comparison, LIKE, RLIKE or IS NOT DISTINCT FROM (`hn.a < hn.c`, while `hn.a + hn.c = 3` is still
  answered) or across a BETWEEN's subject and bound, or with an outer subject of IS DISTINCT FROM, NOT IN or NOT
  BETWEEN. Inside a subquery an outer name resolves by its exact spelling, so a quoted `"v"` no longer reaches a
  column V, and a bare name two joined outer relations both carry is `ambiguous column name`.
- **An unset bind variable outside a Snowflake Scripting block is refused while the statement compiles**,
  `Bind variable :1 not set.` at its colon, in every clause, subquery and DML statement, over empty tables and on
  branches no row reaches. A LIMIT, OFFSET or FETCH count written as a bind nothing binds (`LIMIT :n`) is refused the
  same way instead of reading as no limit, and in a view's query or a SQL UDF's body it is
  `Bind variables not allowed in view and UDF definitions.`
- **Predicates and operators refuse operand types while the statement compiles.** A WHERE, join condition, HAVING or
  QUALIFY whose whole type is VARIANT, OBJECT, ARRAY, DATE, TIME, a timestamp, BINARY, VECTOR, MAP or GEOGRAPHY is
  `Invalid data type [T] for predicate [...]`, over empty tables too, and an UPDATE's or DELETE's WHERE and a MERGE's
  ON are type-checked as predicates. `x IN (SELECT …)`, a quantified comparison and a tuple IN over a subquery are
  refused when the two type families do not meet; so are a predicate given to LIKE ANY, LIKE ALL or ILIKE ANY, a value
  operator after a quantified comparison (`Invalid query block: ||.`), and a `:` or `[]` path over a VARCHAR, DATE,
  TIME, timestamp, BINARY or predicate base. The Snowpark Python shim's `df.where()` over local rows of booleans and
  NULLs no longer sends a VARIANT predicate.
- **Faults are no longer hidden by a clause that keeps no row.** In grouped and FROM-less queries an uncorrelated
  scalar subquery that reads a relation is computed ahead of the rows, so its faults,
  `Single-row subquery returns more than one row.` among them, raise even when the HAVING, a LIMIT or the WHERE keeps
  nothing (`SELECT (SELECT v FROM t) AS x WHERE 1 = 0`). The one group an implicit aggregation makes from no rows
  raises a constant item's fault instead of answering NULL (`SELECT 1/0, COUNT(*) FROM t WHERE FALSE`), and a WHERE or
  ungrouped HAVING that reads a select alias whose value faults raises that fault instead of reading NULL.
- **Snowflake Scripting blocks refuse more, most of it while the block compiles and on branches that never run.** A
  SELECT … INTO target that names no variable is `invalid identifier 'ZZ'` at the target; an untyped LET or DECLARE
  whose initialiser reads a bind other than alone or under a cast, or would infer a VARIANT, is
  `variable 'X' cannot have its type inferred from initializer`; a subquery or unknown function on an unevaluated side
  of AND or OR is still refused; and an integer literal wider than 38 digits refuses the whole block before anything
  runs, or a SQL procedure holding one at CREATE. A bind with a quoted name (`:"x"`) is a syntax error, EXECUTE
  IMMEDIATE … USING takes variable names only, and a statement in parentheses assigned to anything but a RESULTSET is
  refused without running.
- **Block expressions are checked for argument types before they run, and SET takes only a constant source.** A
  RETURN, an assignment, a typed default and the IF, ELSEIF, WHILE, REPEAT and searched CASE conditions are refused as
  an EXPRESSION_ERROR in SQL's own words, which `WHEN EXPRESSION_ERROR` catches with SQLCODE 1044, 1038 or 1007, and a
  typed declaration or assignment whose value the plain cast refuses (a BOOLEAN into DATE or FLOAT, a number into
  DATE) is refused with the cast's `invalid type [CAST(…)]`. SET of a session variable refuses a source that fails
  while it folds, or a VARIANT, OBJECT or ARRAY that is not NULL, as
  `Unsupported feature 'assignment from non-constant source expression'.`
- **EXECUTE IMMEDIATE's text is counted before any of it runs**, at the top level against the request's
  MULTI_STATEMENT_COUNT (the JDBC statement's parameter or the HTTP request's `multiStatementCount`, else the
  session's) and inside a block against the session's:
  `Actual statement count 2 did not match the desired statement count 1.` Under 0 the text runs as a multi-statement
  request whose answers reach the client only when the request is that EXECUTE IMMEDIATE alone; otherwise it answers
  `Multiple statements executed successfully.`
- **CREATE FUNCTION and CREATE PROCEDURE take their options in the account's order**: LANGUAGE, null handling,
  volatility, MEMOIZABLE, then properties in any order, with EXECUTE AS last on a procedure. An option out of place is
  a syntax error, and refuses a whole Snowflake Scripting block while it compiles. A SQL or JavaScript routine refuses
  any IMPORTS (`invalid property 'imports'; feature 'dependency import list' not enabled`) and any PACKAGES.
- **A routine's body and handler are checked at CREATE.** A SQL table function's query must match its RETURNS TABLE
  columns in count and type family, TIME and timestamp precisions must match for scalar SQL UDFs too, and a SQL UDF
  query body is typed with its parameters in scope, a parameter winning over a same-named column
  (`(n DATE) RETURNS INT AS 'SELECT n FROM t'` is refused as returning a DATE). A body holding a query that runs out
  of text, a clause after the body's own closing frame, an INTO clause, a misplaced bare stage or a character the
  lexer cannot read is refused, so `x ! = y` is no longer created and run as `x = y`. A Java handler's types must fit
  the SQL signature, a procedure's taking a Snowpark Session first, and a Python function's handler is checked when
  CREATE runs the body, any failure refused as `Python Interpreter Error:`, with a UDTF's method signatures judged in
  the account's sentences.
- **DISTINCT, ALL, WITHIN GROUP and null treatments are refused on calls that cannot take them.** A scalar, a
  window-only function such as NTILE, RANK or ROW_NUMBER, or a schema-qualified user function written with DISTINCT or
  ALL is `invalid use of 'distinct' for function '…'` (or `'all'`), in a Scripting expression too, and LAG, LEAD and
  NTH_VALUE take neither; COUNT_IF, MEDIAN, MODE, APPROX_PERCENTILE, CORR, COVAR_POP and COVAR_SAMP, the REGR_ family
  and the boolean aggregates refuse DISTINCT; WITHIN GROUP is `Function X does not support WITHIN GROUP clause.` on
  all but LISTAGG, ARRAY_AGG and the two ordered percentiles; IGNORE or RESPECT NULLS after the parentheses belongs to
  unquoted FIRST_VALUE, LAST_VALUE, LAG, LEAD and NTH_VALUE alone; and ROW_NUMBER, RANK and the other window functions
  that need an ORDER BY are refused without one in every clause, FROM-less queries included. Each is judged where the
  call is written, ahead of later names.
- **Object DDL refuses repeated properties, missing warehouses and misplaced task settings.** A property named twice
  is `duplicate property 'X';` in user, notebook, Streamlit and ALTER TASK property lists; an alert's WAREHOUSE and
  ALTER TASK … SET WAREHOUSE must name a warehouse that exists, and a bare ERROR_INTEGRATION an integration that
  exists; a predecessor must be a task of its own schema, and a non-root task takes no schedule, no overlapping runs
  and no TASK_AUTO_RETRY_ATTEMPTS; and a task or alert schedule under 10 seconds, over 11,520 minutes or with a
  leading or doubled space is refused. A tag given two different values in one WITH TAG clause is refused on CREATE
  TABLE, VIEW and STREAM, ALTER TAG … SET ALLOWED_VALUES takes no other property after its list, CREATE OR REPLACE
  DATABASE ROLE IF NOT EXISTS is refused as incompatible, bulk grants and revokes on PIPES are refused, and so is
  CREATE PIPE with AWS_SNS_TOPIC but without AUTO_INGEST = TRUE.
- **Function arguments are held to the shape the account requires.** GET_DDL's object type and name must fold to
  constants, so a column or an expression that does not fold is refused while the statement compiles, even over no
  rows (`Invalid value [T0.N] for function '2', parameter EXPORT_DDL: constant arguments expected`). A quoted named
  argument of FLATTEN, SPLIT_TO_TABLE or INFER_SCHEMA is refused as `unexpected argument ["INPUT"] at position 1,`,
  where FLATTEN used to accept it and drop the value, and a CREATE TABLE … USING TEMPLATE column description needs
  NULLABLE beside COLUMN_NAME and TYPE.
- **A PRIMARY KEY declared in CREATE TABLE makes its columns NOT NULL.** That holds for the inline form
  (`x INT PRIMARY KEY`), the out-of-line list (`PRIMARY KEY (x, y)`, where every named column becomes NOT
  NULL) and a named constraint alike, and an explicit `NULL` written beside the key does not win. DESCRIBE,
  SHOW COLUMNS, INFORMATION_SCHEMA and a view over the column all report it, and writing a NULL into such a
  column is now refused: `DML operation to table T1 failed on column X with error: NULL result in a
  non-nullable column`, naming the table the way the statement spelled it. A key added afterwards by
  `ALTER TABLE ... ADD PRIMARY KEY` still leaves nullability alone, even on a table that already holds a
  NULL, while a column added *with* a key is NOT NULL like any other declaration, and a CTAS carries
  neither the key nor its nullability.
- **A Python routine declaring a decommissioned RUNTIME_VERSION is refused at CREATE.** `'3.8'` and `'3.9'`
  answer `Python runtime version 3.9 is decommissioned. Please update your code to Python runtime version
  3.10 or later.` for a function and a procedure alike. The refusal comes after the routine's other
  properties are judged, so a missing HANDLER is still reported first, and it comes before the signature and
  the body are looked at. A version the account never offered stays an invalid value.
- **A SELECT alias is judged by what it projects in every predicate clause.** `SELECT g AS gg FROM t WHERE gg`
  over a VARCHAR column is now "Invalid data type [VARCHAR(10)] for predicate [GG]" while the statement
  compiles, as it already was in HAVING and QUALIFY; a join condition that is a bare alias is refused too, and
  echoed as the expression the alias projects. A real column always wins over a same-named alias. The
  clauses' predicate types are judged in the account's own order: after every clause's names, a join
  condition's type, then the WHERE's, the HAVING's and the QUALIFY's.
- **A positional GROUP BY or ORDER BY key is read from the parsed key**, so parentheses to any depth and a
  minus sign wherever it is written belong to the literal — `-(1)`, `- 1` and `-((1))` are all position -1,
  refused where earlier versions sorted an ORDER BY by the constant, and `-(-1)` folds back to position 1 — and
  the refusal echoes the value the literal folds to: `[9]` for `(9)`, `[10]` for `1e1`, `[2.5]` for `2.50`,
  `[0]` for `-0`.

### Fixed

- **ALTER TABLE … DROP COLUMN no longer shifts later reads.** The dropped column's value is removed from stored
  rows, time-travel history, stream change records and open transactions' buffered rows; ADD COLUMN and CREATE OR
  ALTER TABLE fill a new column into all of them with its DEFAULT (or NULL), and a time-travel read after an ADD no
  longer throws. Dropping one column of a composite PRIMARY KEY drops the whole key.
- **A LATERAL join's ON condition filters the lateral rows.** A LEFT JOIN LATERAL null-extends a left row the
  condition leaves unpaired and applies conjuncts that read left-side columns afterwards, as a filter; LEFT JOIN
  LATERAL without ON, and FULL or RIGHT JOIN LATERAL, answer as the inner join.
- **LAG and LEAD reach the neighbouring row when it equals the current row in every column**, and a row read twice
  in one window input is two rows to ROW_NUMBER, NTILE, ROWS frames, CONDITIONAL_TRUE_EVENT and QUALIFY. NTILE deals
  equal rows into consecutive buckets; PERCENT_RANK and CUME_DIST rank by the whole ORDER BY key in each key's
  direction; LAG and LEAD honour IGNORE NULLS.
- **A stage path is a prefix of the staged files' names, not a directory.** COPY INTO a table, `SELECT … FROM
  @stage/path`, REMOVE and GET read every file whose stage-relative name starts with the path, at any depth and
  case-sensitively, so `@st/file.csv` loads that file (it used to load nothing) and a bare `@st` reaches nested
  files. PATTERN matches the whole stored path, a `FILES` entry continues the written path, an unload refuses only
  over the names it would write, and METADATA$FILENAME names table- and user-stage files as the account does.
- **A scalar function whose one argument is a star takes the columns the star stands for**: `HASH(*)` hashes the
  row (it was one constant for every row), `CONCAT(*)`, `ARRAY_CONSTRUCT(t.* EXCLUDE a)` and `UPPER(*)` read the
  columns written out, and the result is typed from them.
- **A qualified star honours EXCLUDE, ILIKE, REPLACE and RENAME**, beside a window function and over joins.
- **Two columns whose names differ only in case are kept apart everywhere**: quoted and qualified references,
  stars and their modifiers, GROUP BY / GROUPING SETS / GROUPING, USING and NATURAL keys, INSERT / UPDATE / MERGE
  targets and outer references. A quoted GROUP BY key (`GROUP BY "c"`) is no longer refused.
- **String functions read a DATE, TIME or timestamp argument as its display text** (`2020-01-01 10:00:00.000`,
  not `2020-01-01T10:00`), LIKE and ILIKE too, and CONCAT, CONCAT_WS and INSERT over a non-text argument are
  declared VARCHAR(134217728).
- **String functions count a supplementary character (an emoji) once and never split it**: LENGTH, SUBSTR,
  LEFT/RIGHT, CHARINDEX/POSITION, LPAD/RPAD, INSERT, TRANSLATE, EDITDISTANCE, TRIM, RTRIMMED_LENGTH and the REGEXP
  positions. A literal's declared width counts characters, so `'😀😀'` fits a VARCHAR(2), and ASCII answers the
  lead byte of the character's UTF-8 encoding.
- **FLATTEN's THIS column is the container the row came from, as a VARIANT**, not a quoted JSON string, and a
  recursive or PATH-filtered walk reports the nested container at each level.
- **SHOW TABLES, SHOW OBJECTS and INFORMATION_SCHEMA.TABLES report a table's committed row count**, which was 0
  for every table. `bytes` stays 0: the account reports its compressed storage size.
- **Every session has its own CURRENT_SESSION()**: a stable 16-digit number per HTTP session, per JDBC connection
  and for the embedded engine, and the same number names the session in SHOW VARIABLES, SHOW TRANSACTIONS, SHOW
  LOCKS IN ACCOUNT and QUERY_HISTORY's SESSION_ID (now filled). Every HTTP session used to answer one shared id.
- **A request of several statements that opens a transaction and then fails leaves it open in its session**, as
  live does: CURRENT_TRANSACTION() stays set, the session reads its own rows, and COMMIT or ROLLBACK ends it. It used
  to be lost, and SHOW TRANSACTIONS listed it under no session for the server's lifetime.
- **A select item that reads an earlier item's alias, a correlated scalar subquery and a LATERAL body's item are
  declared with the type of what they read**, instead of VARCHAR(16777216); a CTAS, view, CTE or derived table over
  them inherits it.
- **A schema-level SHOW with no IN clause lists what live lists**: TABLES, VIEWS, OBJECTS, COLUMNS, FUNCTIONS,
  PROCEDURES, SEQUENCES, STREAMS, TASKS, FILE FORMATS, TAGS, ALERTS and MATERIALIZED VIEWS follow the session's
  SEARCH_PATH, and the other listings the current schema. `IN DATABASE` / `IN SCHEMA` without a name mean the current
  ones (`SHOW COLUMNS IN DATABASE` used to throw a NullPointerException), and a TABLE scope on any listing but
  COLUMNS and the KEYS is refused by its shape.
- **A multi-table INSERT answers the account's count grid**, one `number of rows inserted into <table>` column per
  target, instead of "Statement executed successfully."
- **A star's REPLACE and RENAME name an output column that later clauses read.** `SELECT * REPLACE (id * -1 AS
  id) FROM t ORDER BY id` sorts by the replaced value and `SELECT * RENAME (id AS k) FROM t ORDER BY k` by the
  renamed one, each as a written alias already did; the renamed name is also in scope for the select items that
  follow the star, so `SELECT * RENAME (id AS k), k FROM t` projects it. A qualified key (`ORDER BY t.id`) still
  names the source column.
- **A star projects two columns of one name by position.** `SELECT t.* FROM (SELECT 1 a, 2 a) t` answers `1 | 2`
  where it used to answer the first column twice, through a derived table, a CTE and a nested star alike.
- **A column keeps the ORDINAL_POSITION it was given.** Dropping one leaves its number behind and the next column
  added takes one past the highest ever used, instead of the columns being renumbered 1..n on every read. The
  positions survive a catalog snapshot.
- **A stored view's refusal is placed in the CREATE statement that declared it**, not in the body on its own, so a
  body that stopped compiling reports the position the account reports.
- **COPY and GET declare the types the account declares for their results**: NUMBER(38,0) for the load counts,
  NUMBER(31,0) for the unload summary, VARCHAR(134217728) and NUMBER(18,0) for DETAILED_OUTPUT, and
  VARCHAR(20480) with a DECIMAL(10,0) size for GET — all were a precisionless INTEGER or the 16MB VARCHAR.
- **An interval literal is a value**: `INTERVAL '1' DAY = INTERVAL '24' HOUR` and `INTERVAL '1' YEAR = INTERVAL '12'
  MONTH` are TRUE, UNION / DISTINCT / GROUP BY keep one row per span, ORDER BY, MIN, MAX, GREATEST and LEAST go by
  span, and TO_VARCHAR / `::VARCHAR` print the account's text (`+1`, `+1.000000000`, `+14`). An interval beside a
  number, a BOOLEAN, a date or an interval of the other family is refused while compiling, and a text on its right
  is read in the interval's fields.
- **GRANT OWNERSHIP moves a task**, alone or with `ON ALL TASKS IN SCHEMA`, and SHOW TASKS names the new owner.
- **SHOW GRANTS TO ROLE orders its rows by the kind of object, then its name, then the privilege**, never by when
  the grants were made.
- **Network policies, network rules, password policies and secrets are granted on like any securable**: each takes
  its own privileges, SHOW GRANTS ON lists the holders, a grant blocks an ownership move, and dropping one needs
  OWNERSHIP. A network policy's owner is reported (SHOW and the REST resource), and a policy attached to a user
  shows as NETWORK_POLICY in SHOW PARAMETERS IN USER, which now takes a LIKE pattern.
- **A RECURSIVE VIEW whose body unions an anchor with a branch reading the view is created and read to a fixed
  point**; GET_DDL shows the body as written. Without the UNION or the RECURSIVE it is still refused.
- **A typed literal of a type the account does not know is refused by its text**, `Unsupported data type literal
  'foo 'x''.`, in a query and in a function or policy body, where it was a syntax error at the string.
- **A row comparison is refused by type**: a row opposite a scalar parses and is refused at the operator, and rows
  of two widths name both ROW types. A row comparison is typed as a predicate.
- **A repeated INTO target is judged after an undeclared name in the same statement**, which is refused first.
- **DIRECTORY(@st.) is a syntax error inside the reference's own frame**, where a missing stage was answered.
- **FIRST_VALUE or LAST_VALUE over a whole one-row input framed from UNBOUNDED PRECEDING answers** where a faulting
  order key used to raise.
- **AT(STATEMENT => id) reads the table WITH that statement's changes and BEFORE without them**; a statement inside
  an explicit transaction counts from its COMMIT. An INSERT's own rows used to be missing.
- **A MERGE answers one count column per kind of action its WHEN clauses write**, whatever happened: a MERGE with
  only a WHEN MATCHED UPDATE answers `number of rows updated` alone. **An UPDATE … FROM counts each target row
  several source rows joined, once, in `number of multi-joined rows updated`** (it answered 0), so the JDBC update
  count sums both columns as Snowflake's driver does; every DML count is NUMBER(19,0).
- **A partitioned unload names its files after itself**, `data_<query id>_<n>_<m>_0.<ext>`, adds files on a rerun
  instead of overwriting them, answers a PARTITION_NAME column, and names the NULL partition `\N`.
- **GET over same-named files in several directories keeps the file the account keeps**, walking them in the
  account's order rather than in path order.
- **Two columns whose names differ only in case are told apart by** COPY's column mapping, CREATE OR ALTER TABLE,
  table-level key column lists, the grouped select-list check and a grouped query's ORDER BY.
- **The session output formats are applied**: DATE_, TIME_, TIMESTAMP_ and TIMESTAMP_NTZ_/LTZ_/TZ_OUTPUT_FORMAT
  govern a cast to text, `||`, TO_VARCHAR and TO_CHAR without a format, a string function's argument, LIKE and a
  value embedded in a VARIANT. LTZ and TZ fall back to TIMESTAMP_OUTPUT_FORMAT, NTZ only once its own is emptied.
- **CURRENT_TRANSACTION() and LAST_TRANSACTION() answer the transaction's id**, the 19-digit number SHOW
  TRANSACTIONS and SHOW LOCKS list and SYSTEM$ABORT_TRANSACTION takes, where they answered an internal counter.
- **LAST_TRANSACTION() and CURRENT_STATEMENT() are each session's own**; one HTTP session read another's.
- **An expired HTTP session's open transaction is rolled back** and its engine state cleared, as a released
  session's is; it stayed running in SHOW TRANSACTIONS.
- **EXPLAIN answers the account's shapes**: ten columns with a GlobalStats row first, and for USING TEXT or USING
  JSON one `content` column holding the plan; another format word is refused and the statement is compiled first.
- **A missing-object refusal carries the account's second sentence naming the privilege the role lacks**:
  `Object 'T' does not exist or not authorized. Your primary role SYSADMIN must have at least one privilege granted
  on TABLE T.` The kind and privilege follow the account kind by kind — TABLE for every table-like relation, POLICY
  for policies, `USAGE or any other privilege` on a DATABASE or SCHEMA, `USAGE granted on ACCOUNT` for users, MONITOR
  (then RESOLVE ALL for a grant) on a network rule; an owner's-rights procedure words it for the owner, and names
  that are not securable objects keep the one sentence. The REST API relays it.
- **Syntax-error reports stack the lines the account stacks in many more shapes**: a later statement after an
  earlier fault (`ALTER TABLE t1 RENAME TO; SELECT 1 x y` names both), a token run into a finished statement
  (which ends the report, as a word does), a block's LET or RETURN run into the next statement, a block's
  SELECT … INTO target list, a statement ending inside a join's ON, a TRY_CAST that cannot be
  read, a FROM-form call such as `EXTRACT('wks' FROM d)` inside parentheses or casts, a star argument's misplaced
  RENAME or REPLACE outside the select list, a fault where a select item's alias belongs, and a faulty DROP or
  DESCRIBE signature.
- **A statement with many chained syntax faults is refused in about a second**; twelve misplaced REPLACE keywords
  or eighteen `n AS n` items could take minutes.
- **A DROP or DESCRIBE signature parameter may be a dotted name of any length** (`DESCRIBE TABLE t1 (a(b.c.e))`);
  it was refused past two parts.
- **A grouped query's ORDER BY key that aggregates a subquery is answered** (`ORDER BY MAX((SELECT …))`) when the
  subquery is uncorrelated, and judged by the correlation rules when it is not; it was refused as not a valid order
  by expression. A subquery's own type refusal now ranks ahead of the enclosing query's items, as the account ranks
  it, and an enclosing query's column is typed while the subquery compiles.
- **LAST_QUERY_ID accepts an index the plan folds to a constant** (`TO_NUMBER('1')`, `'1'::INT`), and refusal
  echoes re-print more of the account's plan: an all-NULL COALESCE, a folded product, a text literal beside a number,
  a unary plus, a VARIANT cast to NUMBER.
- **SYSTEM$TYPEOF of a SUM over a shifted column of a derived table, CTE or view reports the plan's rewritten type**,
  as the direct `SUM(n + 1)` does, and `SUM(n + 0.05)` over a NUMBER(5,2) is NUMBER(22,2).
- **The SNOWFLAKE database's built-in classes exist** (SNOWFLAKE.ML.FORECAST, SNOWFLAKE.CORE.BUDGET and eleven
  more): SHOW … IN <class> <instance> and DROP <class> <instance> resolve the instance, and words the lexer keeps as
  keywords (FLATTEN, CURRENT_DATE, …) name a class in a SHOW scope like any other name.
- **A scripting variable holding NULL keeps its declared type where the account types it** once bound into a
  statement (select items, SYSTEM$TYPEOF, conditionals, casts, CREATE TABLE AS), and binds untyped where the account
  binds no type (VALUES rows, date and time arguments, date arithmetic).
- **Arithmetic on unit-suffixed interval literals follows Snowflake.** Negation keeps the type, scaling by an exact
  number widens the leading precision by the number's digits, two intervals of one family combine into the span of
  both (DAY + HOUR is `INTERVAL DAY(9) TO HOUR`), and a DATE plus a year-month interval stays a DATE. Products and
  quotients keep nothing finer than the type's trailing field (`INTERVAL '1' DAY / 2` is zero days) and division
  truncates toward zero, TIMESTAMP differences too. A day-time interval moves a TIMESTAMP_LTZ by its exact duration
  across a daylight-saving change, and Snowflake Scripting blocks and stored procedures type these literals as a query
  does.
- **A quoted interval text is read the way Snowflake reads it.** Parts not separated by a comma
  (`INTERVAL '1 day 2 hours'`) and a reserved word in place of a unit are syntax errors inside the text, an unknown
  unit is refused on one line (`dayx is not recognized as a date type.`), the text takes comments, fractional amounts
  round half away from zero, and an amount its unit cannot count is refused when a row reaches it, where compiling
  used to fail with a Java `Overflow`. A qualifier that names no type, or a text that does not read, is refused as
  soon as the statement parses, ahead of any unknown name, and refuses a Scripting block whole, even from a branch
  never taken.
- **SUM and AVG over a day-time interval answer an `INTERVAL DAY(9) TO SECOND(9)`** whatever digits the argument
  declares. AVG and an interval divided by a number are cut toward zero at the nanosecond while a product still rounds
  half away from zero, ABS keeps an interval, EXTRACT, DATE_PART, HOUR, MINUTE, SECOND, DAY and DAYOFMONTH read its
  signed components as NUMBER(9,0) (YEAR and MONTH a year-month interval's), and MEDIAN and the ordered percentiles
  read an interval as the whole number its cast gives. An IN list reads its text members in the interval's fields, and
  a text arm after an interval arm of a set operation is read as the interval.
- **A positional column over several relations reads the column its position names.** `$n` resolves through the FROM
  clause's name scopes, a USING or NATURAL join counting as one relation laid out as its merged keys and then each
  side's columns; `t.$n` reads relation t's nth column; ORDER BY and GROUP BY `$n` over a join use that column, where
  ORDER BY sorted by the combined row's nth value and GROUP BY failed; and over staged files a position reads the
  files' fields, never METADATA$FILENAME, a CSV on a named or user stage reading NULL past its fields up to `$4096`. A
  USING join merges only the relations of its own comma-separated item, a bare `SELECT *` projects each same-named
  column's own value over a USING join's two sides and beside other items or a window function, a star beside other
  items over an ON join is no longer refused as ambiguous, and `OBJECT_CONSTRUCT(t.*)` over a join pairs only t's own
  columns.
- **A table function joined with ON joins as an ordinary relation** (INNER, LEFT, RIGHT or FULL, as `TABLE(f(…))` or
  `LATERAL f(…)`) when its arguments read neither the relations to its left nor the row of the query around it; one
  that reads either keeps the account's `Unsupported feature 'lateral table function …'` refusal. One that reads
  nothing of the left side and is joined with NATURAL or LATERAL … USING joins by its key instead of being
  cross-joined, and a `(+)` marker on an unqualified column outer-joins its relation in the two-table comma form, a
  parenthesized conjunction read as its conjuncts.
- **More correlated subqueries are answered.** An outer name the stored statistics pin to one non-NULL value is no
  longer a correlation (`SELECT (SELECT 1 WHERE d.a = 1) FROM (SELECT 1 AS a) d`), the correlation refusals apply only
  to outer names the statistics show varying, and a membership whose filter the statistics prove false, or an IS [NOT]
  DISTINCT FROM whose operands' ranges never meet, is settled before the correlation is judged. A correlated UNION or
  UNION ALL whose operands read the outer row alike is answered under EXISTS or IN (INTERSECT, EXCEPT and MINUS when
  only the first operand is correlated), a derived table reading the outer row only in its select list may be counted,
  filtered and joined, a positive IN in the WHERE keeps a GROUP BY that only removes duplicates, and a QUALIFY with no
  window function is reported ahead of a subquery Snowflake cannot evaluate.
- **A HAVING without aggregates in a query that groups nothing filters rows as a WHERE does**, one row per input row,
  select aliases readable and window functions seeing only the kept rows; `SELECT v FROM t HAVING v = 1` used to be
  refused. A select item's fault is raised only for rows and groups that survive the HAVING, a QUALIFY or a LIMIT
  (`SELECT 1/0 AS x HAVING 1 = 0` answers no row), a HAVING of constants that is not TRUE is settled before any key or
  aggregate is computed, a window call in the HAVING of an ungrouped query is
  `Window function [...] appears outside of SELECT, QUALIFY, and ORDER BY clauses.`, and a grouped INSERT … SELECT or
  CTAS whose item faults names the target column.
- **Comparison-level operators bind at one precedence and group from the left, as on Snowflake**: the comparisons,
  [NOT] LIKE / ILIKE, RLIKE / REGEXP, [NOT] IN, [NOT] BETWEEN, LIKE ANY / ALL, ILIKE ANY and the quantified
  comparisons, so `'a' LIKE 'a' = TRUE` compares the LIKE's result, in queries and Snowflake Scripting alike. A
  parenthesized value before IN is that value, not a one-column row; BETWEEN is three-valued; a text member of an IN
  list over a BOOLEAN reads as the BOOLEAN it spells; LIKE ANY, LIKE ALL and ILIKE ANY without ESCAPE apply the
  operators written after the pattern list to the list (`'a' LIKE ANY ('a') || ''` matches `'a' || ''`); and a flat
  tuple IN is refused at its IN keyword. Refusals re-print VALUES lists, FLATTEN, SPLIT_TO_TABLE and GENERATOR as the
  plan does, and NOT over EXISTS as `NOT EXISTS(SELECT …)`.
- **A VARIANT compared with a typed value converts one side on the row, as Snowflake does**, in comparisons, IN,
  BETWEEN, a simple CASE, IS [NOT] DISTINCT FROM, a join's ON and Snowflake Scripting alike: it is cast to a DATE,
  TIME or timestamp on either side and to a BOOLEAN, ARRAY or OBJECT on its left, while a number on either side, or a
  BOOLEAN, ARRAY or OBJECT on its right, is read as a VARIANT. So `fa.va = fb.n` over a VARIANT 1 and a NUMBER 1 finds
  the row, and a failed cast reads `Failed to cast variant value … to …`. A VARIANT operand of NOT, AND or OR converts
  as a cast to BOOLEAN does, where every VARIANT read as FALSE (`NOT TO_VARIANT(TRUE)` was TRUE), and a failing DOUBLE
  is named in the fifteen-decimal form a client reads.
- **A TIME or timestamp column declares the widest fractional-second precision among its sources**: a set operation's
  arms, whichever comes first (`TIMESTAMP_TZ(0)` with `TIMESTAMP_LTZ(3)` is `TIMESTAMP_TZ(3)`), a recursive CTE's
  anchor and term, the rows of a multi-row `INSERT … VALUES`, and the timestamps a conditional mixes with a string; a
  CTAS over such a set operation keeps every digit. Elsewhere, an empty text literal is VARCHAR(1), a session
  variable's storage tag follows its value (a TIMESTAMP_LTZ one keeping its flavour), and DATEDIFF, TIMEDIFF and
  TIMESTAMPDIFF size their result by the operands' kind as well as the unit, reading a text beside a DATE as a DATE.
- **Snowflake Scripting reports Snowflake's first error and keeps its scopes.** A block judges declared types, names
  declared twice and routine languages first, then binds, assignment targets and cursor names, then bare names one
  statement list at a time; a subquery in a scripting expression reports its unknown names in a query's order; and a
  variable declared in a branch, loop, nested block or handler hides an outer one only until that body ends. An INTO
  clause that is not a block's own SELECT … INTO is `INTO clause is not allowed in this context`, and a word before a
  block query's INTO is the item's alias. In and out of blocks, a SET's or CALL's compilation error is placed in the
  statement's own text, `SET (a, b) = (…)` computes every value before setting any, `SET x = seq.NEXTVAL` draws from
  the sequence, and SYSTEM$WAIT answers `waited N <unit>`.
- **A SQL UDF body is read where the account reads it.** A body that closes its own frame of parentheses and then
  writes a semicolon ends there, for scalar and table functions alike: `1);garbage` answers 1, and `SELECT ABS(1));`
  no longer fails every call with a ParseCancellationException. An untyped NULL column meets every declared type, a
  parenthesised list of values is refused as a ROW, and an unfinished body is refused at the account's place. CREATE
  FUNCTION and CREATE PROCEDURE are judged in the account's phases (the statement, the database and schema, an
  existing routine, then properties, handler and body), so a refused CREATE OR REPLACE keeps the old routine, an
  unknown LANGUAGE is `Unknown function language: COBOL.` before the schema is looked at, SERVICE, ENDPOINT and
  MAX_BATCH_ROWS on a procedure are invalid properties, and EXECUTE AS on a function is
  `Unsupported invocation type for function.`
- **SHOW GRANTS lists the implicit and holder rows it was missing, and bulk grants count what they reach.** TO
  DATABASE ROLE lists the role's own USAGE on its database first, granted by no one; ON ROLE lists a USAGE row for
  each account role holding it, and ON DATABASE ROLE one for each role, database role and user; TO USER carries the
  `role` column, lists privileges granted to the user directly and orders its rows as TO ROLE does; and SHOW FUTURE
  GRANTS orders by grantee, kind, scope and privilege. A GRANT or REVOKE that reaches objects by the count answers
  `Statement executed successfully. N objects affected.`, ALL TABLES and ALL VIEWS pass temporary objects by (ALL
  TABLES event tables too), `GRANT ROLE PUBLIC` grants nothing and says so, database-role messages name the role with
  its database, and SHOW DATABASE ROLES … FROM 'x' starts after the name.
- **CREATE USER and ALTER USER judge a statement's form before the user's existence**, and CREATE USER its ranges,
  TYPE and key policy only for a user it creates, so CREATE USER IF NOT EXISTS over an existing user succeeds and
  changes nothing, a plain CREATE answers `Object 'U' already exists.`, a refused CREATE [OR REPLACE] USER leaves the
  account unchanged, ALTER USER IF EXISTS forgives only a missing user, and a syntax error in an ALTER USER SET or
  UNSET list is reported at the list's first word. SHOW USERS and DESCRIBE USER hide a SERVICE or LEGACY_SERVICE
  user's names and MFA bypass until the type is PERSON again, and SHOW MANAGED ACCOUNTS (18 columns), SHOW ACCOUNTS
  and SHOW ORGANIZATION ACCOUNTS (25, ending with `contract_number`) answer the account's columns.
- **Task and alert statements answer in the account's words.** EXECUTE TASK … RETRY LAST refuses in the account's
  sentences, and TASK_DEPENDENTS of a name that is no task is
  `Invalid value [<name>] for function 'TASK_DEPENDENTS_SCAN', …`; CURRENT_TASK_GRAPHS and COMPLETE_TASK_GRAPHS put
  SCHEDULED_FROM after ATTEMPT_NUMBER and add SCHEDULED_BY_USER; they and ALERT_HISTORY refuse a RESULT_LIMIT above
  10000; and ALERT_HISTORY reports the condition and action query ids and SQL_ERROR_CODE 0 for a run without an error.
  EXECUTE ALERT answers `Alert <NAME> is scheduled to run immediately.`, and a refused CREATE OR REPLACE TASK, CREATE
  OR ALTER ALERT or ALTER ALERT SET leaves the object as it was.
- **A stage's file names are flat, as on the account**: a file `dir` and files under `dir/` coexist for COPY, unloads,
  PUT, LIST, GET, REMOVE, METADATA$FILENAME, directory tables and load history, such a file kept on disk as its
  directory's `%00` entry, so existing stages need no migration. DIRECTORY(@stage)'s FILE_URL is the file's stage file
  URL instead of a local `file://` URL, EXECUTE IMMEDIATE FROM a named stage refuses a missing file as
  `File '/path' not found in stage 'DB.SCHEMA.ST'.`, a stage written bare where no function or CALL takes it is
  refused in the account's two phases, and GET_PRESIGNED_URL refuses a user or table stage, a computed expiry and a
  NULL or empty path and is no longer listed by SHOW FUNCTIONS.
- **GET_DDL prints what a table or view carries, with the account's escapes.** A table spells TEMPORARY or TRANSIENT,
  an event table is `create or replace event table …`, `cluster by (…)` comes before the columns, and the aggregation,
  row access and join policies, the tags and the COMMENT follow on their own lines; a view prints its row access
  policy, tags, COMMENT and column comments the same way, a materialized view takes the account's column-list form, a
  sequence's COMMENT is left out, and a view created RECURSIVE keeps the word. Comments and ALLOWED_VALUES are escaped
  (`\\`, `\"`, `\n`, a doubled `'`), tags keep their defined names and sort by full name, an object name is read
  whole, any NULL argument answers NULL, and a view's or dynamic table's column list comes from planning its query,
  never from running it.
- **Comments, column tags and NULL_IF lists are kept as written.** ALTER TABLE … UNSET COMMENT clears the comment, and
  ALTER VIEW, ALTER MATERIALIZED VIEW and ALTER CONTACT take UNSET COMMENT; a column comment written `'it''s'` is
  stored decoded; a tag in a CREATE TABLE column definition is stored on the column, where TAG_REFERENCES,
  SYSTEM$GET_TAG and GET_DDL see it; and a file format's NULL_IF values are kept whole, so `NULL_IF = ('')` reads back
  unchanged and makes an empty string NULL in COPY with a named format.

### Added

- **MATCH_RECOGNIZE.** `FROM t MATCH_RECOGNIZE (PARTITION BY … ORDER BY … MEASURES … ONE ROW PER MATCH | ALL ROWS
  PER MATCH [SHOW EMPTY MATCHES | OMIT EMPTY MATCHES | WITH UNMATCHED ROWS] AFTER MATCH SKIP … PATTERN (…) DEFINE …)`
  was a syntax error; the clause is now read and answered, over a table, a view, a common table expression, a
  subquery or a table function, with an alias before it and one after. The pattern takes symbols, `^` and `$`,
  groups, `|`, `PERMUTE`, the exclusion `{- … -}` and every quantifier, greedy or reluctant; measures take
  `MATCH_NUMBER`, `MATCH_SEQUENCE_NUMBER`, `CLASSIFIER`, `FIRST`, `LAST`, `PREV`, `NEXT`, `LAG`, `LEAD`, the
  aggregates, `FINAL` / `RUNNING` and `symbol.column`; definitions take `LAG`, `LEAD`, `NEXT`, `FIRST_VALUE`,
  `LAST` and framed aggregates. Empty matches, unmatched rows, the three skip modes and the account's refusals —
  `SELECT with no columns`, a skip symbol not on every path, RUNNING under ONE ROW PER MATCH, a symbol the pattern
  does not hold, a date part's bare word in a measure — answer as measured. `MATCH_RECOGNIZE`, `MEASURES`,
  `DEFINE`, `FINAL` and the clause's other words stay usable as names.
- **ALTER of a masking, row access, projection, aggregation or join policy takes SET BODY, SET / UNSET COMMENT,
  SET / UNSET TAG and property lists**, each refused in the account's words and order when it is wrong; only
  RENAME parsed before.
- **LIST (and LS) reads every stage reference**: the user stage `@~`, a table stage `@%t` (qualified too), a
  path beneath a stage as a prefix, and a qualified named stage. A named stage matches exactly and the `size`
  column is NUMBER(38,0).
- **SHOW listings take the account's scopes**: a bare `IN ACCOUNT` lists account-wide for materialized views,
  hybrid and Iceberg tables, functions and procedures; DATABASES, WAREHOUSES, USERS, ROLES and COMPUTE POOLS
  take a scope; and VIEW, PIPE, SERVICE, APPLICATION, CLASS, ORGANIZATION and CONNECTION read as scope kinds,
  each answered with the account's refusal.
- **TO_CHAR prints a number's format model element by element**, so `X`, `EEEE`, `B`, `%`, quoted literals and
  `TM` after `FX` print as on the account.
- **SHOW EXTERNAL TABLES** parses in every scope form and answers the account's nineteen columns. Nothing is ever
  listed — an external table reads files this engine cannot reach — and a relation scope is refused
  (`Cannot show objects of type EXTERNAL TABLE in TABLE`).
- **`EXCLUDE (default)`** names a column called DEFAULT in a star's exclusion list, where the word used to be a
  syntax error.
- **STRTOK_SPLIT_TO_TABLE**, SPLIT_TO_TABLE's tokenizing sibling: its second argument is a SET of delimiter
  characters, defaulting to a single space, and it never produces an empty token, so consecutive, leading and
  trailing delimiters collapse.
- **INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION, QUERY_HISTORY_BY_USER and QUERY_HISTORY_BY_WAREHOUSE**, each
  listing QUERY_HISTORY's columns for one session, user or warehouse — the caller's by default.
- **Interval literals take a full qualifier, and INTERVAL is a type you can declare.** A literal takes a range of
  fields (`INTERVAL '1 02' DAY TO HOUR`), a fractional SECOND, a plural field (`INTERVAL '10' DAYS` is ten days, where
  it was ten seconds) and leading and fractional precisions, typed, stored and printed as Snowflake does it. The same
  qualifiers spell a column type, a CAST or TRY_CAST target, ALTER TABLE ADD COLUMN and a routine signature, printed
  as the account prints them by DESCRIBE, SHOW COLUMNS, INFORMATION_SCHEMA, GET_DDL and SYSTEM$TYPEOF; text, a
  same-family interval and an exact number convert into one by CAST and on INSERT, UPDATE and CTAS, and two intervals
  of one family meeting in a set operation or a conditional take the first one's fields with the larger digits.
- **A RESULTSET can be filled by any SQL statement**: DML, DDL, SHOW, DESCRIBE, EXPLAIN, LIST, ALTER SESSION, USE,
  SET, a CALL of a SYSTEM$ function, BEGIN alone, or an anonymous block, run when the RESULTSET is filled. One filled
  from DML sets SQLROWCOUNT, SQLFOUND and SQLNOTFOUND as the statement would alone and keeps its count columns'
  NUMBER(19,0) in a FOR row, and one assigned a query folds that query's binds as SQL does. CALL of a SYSTEM$ function
  runs at the top level and in blocks, answering one row in a column named after the function, and a block's own
  SELECT … INTO takes ALL and TOP n (`SELECT TOP 1 a, a + 1 INTO :x, :y FROM t`).
- **CREATE PROCEDURE accepts STRICT, CALLED ON NULL INPUT, RETURNS NULL ON NULL INPUT, IMMUTABLE and VOLATILE.** A
  STRICT procedure called with a NULL argument runs no body: a SQL procedure's CALL is refused with
  `NULL result in a non-nullable column`, a scalar JavaScript or Python procedure answers NULL, a Python table
  procedure is refused naming its handler, and Java and Scala procedures run as before. GET_DDL writes STRICT and
  IMMUTABLE back, and persistence and CLONE keep the clauses. Empty `IMPORTS = ()` and `PACKAGES = ()` are accepted
  for Java, Python and Scala routines, as are `RETURNS <type> NULL` and `COMMENT = $$…$$`, and a repeated property is
  legal, the last one winning.
- **Aggregate and window calls take named arguments where the account does**, computed as the positional call their
  values spell (names ignored, values in written order), under OVER (`MEDIAN(x => n) OVER ()`, FIRST_VALUE, CORR,
  STDDEV, COUNT_IF, …) and for aggregates without it (MEDIAN, AVG, HLL, APPROX_COUNT_DISTINCT, …), with the
  quantifier, WITHIN GROUP, frame and null treatment the positional call takes. A named FIRST_VALUE or LAST_VALUE
  without OVER is refused for its missing window specification, APPROX_PERCENTILE is no longer answered with names,
  and a built-in that takes no named arguments is `function X does not support named arguments` at the call. Every
  aggregate takes ALL as a no-op.
- **CREATE and ALTER USER take DAYS_TO_EXPIRY, MINS_TO_UNLOCK, MINS_TO_BYPASS_MFA, RSA_PUBLIC_KEY and
  RSA_PUBLIC_KEY_2, and UNSET them.** SHOW USERS and DESCRIBE USER count the first three down as the account does and
  list `expires_at_time` and `locked_until_time`; a key (PEM armour allowed, RSA of at least 2048 bits or EC) shows in
  DESCRIBE USER with its SHA256 fingerprint and UTC last-set time, and an unusable key is refused in the account's
  key-policy sentences. `REVOKE DATABASE ROLE … FROM USER` is accepted, REVOKE ROLE and REVOKE DATABASE ROLE take a
  trailing RESTRICT or CASCADE, and bulk grants cover DYNAMIC TABLES, EVENT TABLES, EXTERNAL TABLES, MATERIALIZED
  VIEWS, FILE FORMATS and ALERTS, for ALL and FUTURE.
- **CREATE TASK and ALTER TASK … SET take CONFIG, OVERLAP_POLICY, SUCCESS_INTEGRATION, LOG_LEVEL,
  SERVERLESS_TASK_MIN_STATEMENT_SIZE, EXECUTE AS USER and any session parameter a task carries**, commas allowed
  between options, and UNSET resets them. CONFIG must be a JSON object and shows as written in SHOW TASKS,
  OVERLAP_POLICY is set on root tasks only, EXECUTE AS USER needs a user holding the task's owner role, and every
  value is checked against its parameter in the account's words; the statement's own faults are refused even under IF
  NOT EXISTS. Task and alert schedules take seconds, minutes or hours, the one-letter units S, M and H included.
- **Task graphs take finalizers, predecessor edits and a run configuration.** `FINALIZE = <root>` on CREATE TASK and
  ALTER TASK … SET / UNSET FINALIZE attach a task to a root of its own schema under the account's rules, with SHOW
  TASKS task_relations naming both ends and TASK_DEPENDENTS listing the finalizer; ALTER TASK … ADD AFTER, REMOVE
  AFTER and REMOVE WHEN edit a graph, keeping each predecessor once. EXECUTE TASK … USING CONFIG = '<json>' runs the
  graph with that object merged over the root's CONFIG, EXECUTE TASK … RETRY GRAPH RUN GROUP '<id>' retries a graph
  run, and SYSTEM$GET_TASK_GRAPH_CONFIG([path]) returns the running graph's configuration, or the value the path
  names, inside a task.
- **CREATE STREAM takes AT | BEFORE (STREAM | TIMESTAMP | OFFSET | STATEMENT => …)** and starts at that point with row
  identities, the changes since pending row by row and a stream on a view starting at each table's version; a point is
  refused when the table changed after it with change tracking off. Streams may be created ON STAGE, tracking its
  directory table (ALTER STAGE … SET DIRECTORY now turns it on and off, and ALTER STAGE … REFRESH feeds the stream),
  ON DYNAMIC TABLE, recording each REFRESH, ON EVENT TABLE and WITH TAG. A SHOW_INITIAL_ROWS stream, on a table or now
  a view, reports only the source's rows at its start until it is first consumed, and a stream on a view turns change
  tracking on for the tables it reads.
- **CREATE TAG, CREATE OR ALTER TAG and ALTER TAG … SET take PROPAGATE, ON_CONFLICT and COMMENT after ALLOWED_VALUES,
  in any order**, each once; a repeat or an unknown propagation mode is refused before the tag is looked up. The
  ON_CONFLICT rule is checked against the tag the statement would leave, in the account's
  `Invalid on_conflict strategy: …` sentences: it needs PROPAGATE, ALLOWED_VALUES_SEQUENCE needs allowed values (a
  DROP that empties them, or an UNSET, is refused under it), and a string rule must be one of them while any remain; a
  refused CREATE TAG leaves no tag. ALTER TAG … SET changes only what it names, ALTER TAG IF EXISTS forgives only a
  missing tag, and CREATE OR ALTER TAG over an existing tag resets what it leaves out.
- **BUILD_STAGE_FILE_URL, BUILD_SCOPED_FILE_URL, GET_STAGE_LOCATION, GET_ABSOLUTE_PATH and GET_RELATIVE_PATH are
  implemented**, and with GET_PRESIGNED_URL they read a stage written bare (`@st`, `@db.schema.st`) as the string
  '@st'; a positional CALL argument passes one as written. BUILD_STAGE_FILE_URL answers a URL that does not expire,
  `<server>/api/files/<database>/<schema>/<stage>/<path>`, and BUILD_SCOPED_FILE_URL one valid for 24 hours and new on
  every call, both served by the HTTP server under `/api/files/`; GET_STAGE_LOCATION answers an external stage's URL
  as created and an internal stage's directory as a `file://` URL. The six functions share the account's refusals of a
  NULL, empty, pathed, user, table, missing, non-constant or malformed stage argument, while the statement compiles.
- **INFER_SCHEMA describes the columns of staged files**, CSV and JSON, and Parquet, Avro and ORC with
  frostlake-formats on the classpath, taking LOCATION, FILE_FORMAT, FILES, IGNORE_CASE, MAX_FILE_COUNT,
  MAX_RECORDS_PER_FILE and KIND. Types follow Snowflake's inference, CSV is split as Snowflake splits it
  (multi-character delimiters, enclosed fields across newlines, ESCAPE), FILES entries continue the LOCATION's path
  after one slash, a JSON key named twice is refused unless ALLOW_DUPLICATE is set, and refusals come in Snowflake's
  check order. CREATE TABLE … USING TEMPLATE over it creates the inferred columns, NOT NULL where a Parquet or Avro
  field is required, and a named argument may be a parenthesized list (`FILES => ('a.csv', 'b.csv')`), which every
  other call refuses in Snowflake's words.
- **The internal names DATEADD and DATEDIFF are planned as are callable**: `DATE_ADD<UNITS>TO<DATE|TIMESTAMP|TIME>`
  and `DATE_DIFF<DATE|TIMESTAMP|TIME>IN<UNITS>`, 53 names. Each moves its values to its kind, a VARIANT as a cast does
  (`Failed to cast variant value 5 to DATE`), with Snowflake's result types and refusals; SHOW FUNCTIONS does not list
  them.
- **GET_DDL renders schemas, databases and many more object kinds.** `GET_DDL('SCHEMA', …)` and
  `GET_DDL('DATABASE', …)` return the container's CREATE followed by every object it holds, in the account's order and
  line layout, routines ordered by name and parameter list as one string; streams, tasks, pipes, tags, policies of
  every kind, file formats, alerts, contacts, Streamlit apps and dynamic tables render too, EVENT_TABLE is refused as
  an unsupported feature and any other unknown type as `Invalid object type: '<TYPE>'`. A third argument that reads as
  true fully qualifies every recreated name; it is read only after the object is found, and text that is no boolean
  word is `Invalid value [CAST(… AS BOOLEAN)] …`.
- **Notebooks and Streamlit apps take versions, Git actions and secrets.** Notebooks gain ADD LIVE VERSION FROM LAST,
  COMMIT and ABORT; both kinds gain ADD VERSION [IF NOT EXISTS] [alias] FROM '<stage location>' with aliases and
  comments, SHOW VERSIONS IN NOTEBOOK | STREAMLIT [LIMIT n], PUSH and PULL with their TO branch, credentials, author
  and comment, and SECRETS and UNSET SECRETS; and EXECUTE NOTEBOOK checks for a query warehouse and a live version and
  answers `Statement executed successfully.` without running notebook code. Aliases, parameters and locations are
  judged before the notebook or app is looked up, in the account's sentences, and a Git action on an app with no Git
  source, secrets no external access integration allows, and SHOW ALIAS, SHOW SOURCE and SHOW BRANCH are refused as
  the account refuses them.
- **`CREATE OR ALTER TABLE` and `CREATE OR ALTER VIEW` are accepted.** The object is created when it is not
  there and brought to the written shape when it is: a table gains a column appended at the end, loses one
  missing from the list, and takes a new nullability, column comment, wider VARCHAR, changed NUMBER
  precision, table comment or CLUSTER BY — keeping its rows throughout. A column added before the end, a
  re-ordering, another type, a narrowed VARCHAR, a changed scale, a default set on an existing column or
  dropped from one, and a NOT NULL column added to a table that holds rows are each refused in Snowflake's
  own words. A view takes the new body. Where the account applies what it can before it meets a refusal
  ("Partial updates may have been applied"), Frostlake judges the whole statement first and leaves the
  table untouched. The other object types Snowflake documents for CREATE OR ALTER are not accepted yet.
- **The HyperLogLog STATE family is implemented**: `HLL_ACCUMULATE` builds a group's sketch as a BINARY
  state, `HLL_COMBINE` rolls states up, `HLL_ESTIMATE` reads a cardinality out of one, and `HLL_EXPORT` /
  `HLL_IMPORT` carry a state as the documented object, so a state Snowflake exported can be read here at
  the precision it declares. `HLL_ESTIMATE(HLL_ACCUMULATE(x))` answers exactly what
  `APPROX_COUNT_DISTINCT(x)` answers, since it is the same sketch. A state's BYTES are this engine's: which
  register a value lands in depends on the hash, so a state written here does not match one Snowflake wrote
  byte for byte — the estimates and the exported object are what travel.
- **GETVARIABLE reads a session variable as text.** `SET sv = 5` then `GETVARIABLE('SV')` answers `'5'`. The
  name is matched exactly against the upper-cased name `SET` stores, so `'sv'` and `'"SV"'` read nothing; a
  name no `SET` defined, and a NULL name, answer NULL rather than refusing, where `$sv` refuses an unset
  variable. The result is a string of no width, the name must be constant text, and the call itself is not a
  constant — an argument slot that folds its argument refuses it.
- **`SHOW VARIABLES LIKE '<pattern>'` filters by name**, case-insensitively and with the usual wildcards, and
  **`LS` is accepted as the abbreviation of `LIST`**, as `RM` is of `REMOVE`. The word stays usable as a name.

### Embedding

- **The HTTP server refuses a request naming a session it no longer holds.** `POST /api/execute` answers 404 and
  runs nothing when `sessionId` names a session that idled out, was released or went with a restart, instead of
  running the statement in a fresh session at the server's default database and schema. A request that sets
  `requireSession: false` still gets the fresh session, flagged `newSession: true`. Every Frostlake driver handles
  the refusal; a client that sends neither field and relied on the fresh session now gets the 404.
- **The JDBC driver's HTTP transport keeps its session honest.** It sends `requireSession: true` once the server
  answers `newSession`. A lost session that held nothing is replaced on the connection's database, schema and
  autocommit mode, and the statement runs once more; one that held a transaction or context (a USE, SET, ALTER
  SESSION or temporary object) is reported with `FrostlakeSessionLostException`, SQLState 08003, and the statement
  does not run — except `rollback()`, whose transaction the server already rolled back. Closing releases the session
  with one bounded DELETE that never fails, sent only to a server that answers `newSession`. The `sessionId`
  connection property names a session to join: one the server does not hold is refused, not created.
  `setAutoCommit` takes the new mode on only once the session has, `setSchema` moves what `getSchema` reports, and
  `isValid` honours its timeout.
- **A syntax error wins over the statement-count refusal.** With MULTI_STATEMENT_COUNT at 1, a script holding a
  statement that does not parse is refused for its syntax error — over the in-process JDBC transport, in the HTTP
  driver's own check and in the HTTP handler alike — since the account compiles a script before it counts it.
- **Result metadata follows the account in four more places**: an unaliased `<sequence>.NEXTVAL` item is named
  NEXTVAL and typed NUMBER(19,0); SYSTEM$TYPEOF's column is VARCHAR(134217728); INFORMATION_SCHEMA's LAST_DDL is
  a TIMESTAMP_LTZ; and a view's TABLES row leaves IS_TRANSIENT and RETENTION_TIME empty while VIEW_DEFINITION
  leaves out the COMMENT clause.
- **INFORMATION_SCHEMA's columns carry the account's JDBC types**: text columns are VARCHAR(134217728) and the YES/NO
  flags VARCHAR(3), as are FLATTEN's KEY and PATH and SPLIT_TO_TABLE's VALUE; DIRECTORY() declares its columns as the
  account does; and LAST_DDL_BY names the user who ran the CREATE rather than the owning role.
- **Over the HTTP wire a bare XML value now crosses as its XML text.** `SELECT PARSE_XML('<test>22</test>')`
  answers `<test>22</test>` to an HTTP client, as it already did to an embedded caller and over the JDBC
  driver's in-process transport, and as the account answers. It previously crossed as the internal object
  model `{"$":22,"@":"test"}`, so every driver that reads the wire handed that to its caller unless the
  statement cast the value with `::VARCHAR`. An XML value nested inside an ARRAY or an OBJECT still crosses
  as the object model, which is what the account does too: the rule reads the cell, never the values inside
  a container.
- **Every SELECT runs as one planned operator pipeline.** The planner compiles a SELECT operand whole — the
  refusals its clauses raise while planning come before any row flows — and then runs the source relation through
  the planned stages (joins, WHERE, grouping, HAVING, window functions and QUALIFY, projection, ORDER BY, TOP /
  LIMIT / FETCH, DISTINCT, LATERAL items and PIVOT / UNPIVOT) end to end as one pipeline; a LATERAL item's
  right side is read for its shape while planning, and only a PIVOT over `IN (ANY)` reads values then, as the
  account does. A set operation is a plan over its arms' results (UNION, INTERSECT and EXCEPT stages, then the
  statement's ORDER BY and LIMIT), a FROM-less select a plan over DUAL's one row, a parenthesized join group a
  plan of its own that the join reading it runs, and a table-function source (`FROM TABLE(GENERATOR(…))`) the
  plan's first stage — GENERATOR, FLATTEN and SPLIT_TO_TABLE tell their columns without running, so nothing of
  theirs runs while planning; a table function used to run twice for one `TABLE(…)` source and now runs once.
  UPDATE, DELETE and MERGE are plans too: the target's rows, the WHERE or the match against the FROM / USING
  sources (themselves a plan the match stage reads), and a last stage that writes the rows reaching it; a MERGE
  runs its match, its one-update-per-row check and its WHEN MATCHED and WHEN NOT MATCHED clauses as stages. A
  recursive CTE is a plan over its anchor's rows whose one stage runs the recursion, each step a planned SELECT.
  The FROM sources are stages too: a table's scan (`SCAN[T]`), snapshot or `CHANGES` read, a stream's
  unconsumed records, a table function's run, and a derived table's, a view's, a materialized view's or a
  dynamic table's own plan (`SUBQUERY[d]{…}`, `VIEW[v]{…}`) — each planned for its shape, its rows produced
  only when the pipeline runs, and a join's right side read inside the join stage that runs it; a VALUES
  clause, DUAL, a CTE the WITH clause computed and a prior flow-chain stage's result are in hand. A stage's
  files, a system view and a stream over a view are still read while planning, and read with the `*`.
  A SELECT is planned before it runs at every level — a set operation's arms and a derived table's body
  included — so a body's refusals come before any of its rows, and a view's body parses once however many
  statements read it. `QueryExecutor.describeLastSelectPlan()` (reached through
  `DatabaseEngine.getExecutor()`) reports the last plan's source and stages, for diagnostics; a stage run while
  planning carries a trailing `*`.
- **Frostlake's JDBC driver answers `execute()` as Snowflake's does**, on both transports: DML gives `false` with the
  affected rows, COPY INTO a table the rows loaded, and DDL, USE, SET, transaction statements and unloads 0; queries,
  SHOW, DESCRIBE, EXPLAIN, CALL, LIST and anonymous blocks still answer result sets. `executeUpdate` of a statement
  that answers rows is refused (`Statement '…' cannot be executed using current API.`, 0A000 / 200042) after it runs,
  a multi-statement request walks with `getMoreResults()` as Snowflake's driver does, a batch reports SUCCESS_NO_INFO
  for a row-answering statement, and `getLargeUpdateCount` / `executeLargeUpdate` work. Every HTTP result carries a
  new `jdbcUpdateCount` field; a client of an older server keeps the old behaviour.
- **An interval column no longer reaches a client as an engine object.** Over the HTTP wire it is typed
  `INTERVAL_DAY_TIME` or `INTERVAL_YEAR_MONTH`, with the driver's field code as its scale and each cell its nanoseconds
  or months as a decimal string. Frostlake's JDBC driver reads it as Snowflake's driver does with its default ARROW
  results: a day-time value is its nanoseconds (or, for SECOND, a Duration), a year-month value a Period (`P1Y2M`).
- **Closing a direct (in-process) JDBC connection rolls back the transaction it left open**, as releasing an HTTP
  session does.
- **A persistent database keeps across a restart every catalog object that used to live only in memory**:
  accounts, managed accounts, integrations, external volumes and compute pools; database roles, grant options,
  grantors and future grants; database and schema parameters, retention, transience and managed access; notebooks,
  Streamlit apps, image repositories, services and artifact repositories; alerts and materialized views; and the
  table, warehouse and stage settings the REST APIs added.
- **Those objects also keep their moments across a restart**: the created, updated, resumed and suspended times of
  integrations, external volumes, compute pools, accounts, notebooks, Streamlit apps, image and artifact
  repositories, services, alerts, materialized views and database roles, the time of each grant and future grant,
  and a notebook's or Streamlit app's URL id — through a snapshot, a WAL checkpoint and a WAL replay. A dropped
  external volume, notebook or Streamlit app stays restorable by UNDROP, and ALERT_HISTORY and TASK_HISTORY keep
  their runs through a snapshot or a checkpoint.
- **A DATE or TIMESTAMP before year 1 crosses the HTTP wire with its signed year** (`-1-01-15`), and the driver
  reads it back as that year; the year of the era read year -1 as year 2.
- **SPLIT, STRTOK_TO_ARRAY and REGEXP_SUBSTR_ALL arrive over JDBC as arrays**, not as quoted JSON text.
- **getObject(int, Class) reads through the typed getter of the class** on both transports, as Snowflake's driver
  does: String, the boxed numbers, Boolean, BigDecimal, Timestamp, Date and Time, a NULL reading as that getter's
  answer (0 for Long and Integer); any other class is refused.
- **The HTTP wire sends a TIME or timestamp column's fractional-second precision as its `scale`**, with `precision` 0,
  as Snowflake's result metadata does: TIME(3) sends 3, a bare TIMESTAMP_LTZ or CURRENT_TIMESTAMP 9, a SHOW
  `created_on` 3, and a DATE still 0 for both (docs/http-api.md). The JDBC driver's HTTP transport reads it, so
  `ResultSetMetaData.getScale` answers the declared digits instead of a fixed 9, as the in-process transport and
  Snowflake's driver do; against a server that predates the field it reads 0.
- **The REST API runs the task, stream, notebook and Streamlit requests that answered 501, and its bodies follow the
  account.** Task config, finalize, session_parameters, success_integration, overlap_policy and execute_as_user, a PUT
  that changes predecessors or drops the condition, stream sources of type stage and external_table with
  point_of_time, the notebook `:execute`, `:add-live-version` and `:commit` actions and the Streamlit `:add-version`,
  `:add-version-from-git`, `:push` and `:pull` actions run their SQL, a database role can be revoked from a user,
  users carry the new expiry, unlock, MFA-bypass and key properties, and a database-role listing's `fromName` starts
  after the name. A fetched password reads `********`, a role's, alert's or tag's missing comment reads null, a task's
  schedule reads back as minutes and seconds, grants carry `containing_scope` null and a full `securable`, and RETRY
  LAST and tag-conflict refusals carry the account's codes.
- **The `StageFileReader` SPI gains a default method, `readColumns(file, formatOptions, iceberg)`**, which the
  Parquet, Avro and ORC readers in frostlake-formats use to describe their files' top-level columns.

## 0.1.0 — 2026-09-12

### Embedding

- **The published jar no longer configures logging.** `simplelogger.properties` is not packaged into `frostlake-db` any
  more, so an application embedding it keeps its own logging setup, or slf4j-simple's default of standard error, and the
  engine stops writing `db-engine.log` into the embedder's working directory. The console and HTTP launch scripts in
  `data/` are unchanged — they run from `target/classes`, which still carries the file — and any run can ask for the file
  with `-Dorg.slf4j.simpleLogger.logFile=db-engine.log`. **A process that spawns the server and pipes its output must now
  drain that pipe, or redirect the child's output to a file**: engine logs flow to standard error, and an undrained pipe
  stalls the engine once the operating system's buffer fills, which looks like a mid-run hang rather than a logging
  problem. The slf4j flag alone does not remove the need, since it routes only what goes through slf4j — JVM and GraalVM
  warnings, uncaught stack traces and out-of-memory messages reach standard error whatever slf4j is pointed at.

## 0.0.7 — 2026-08-16

Every behavioural claim below was measured against a live Snowflake account, and is covered by a test
that runs against both Frostlake and that account.

### Now refused (SQL that earlier versions accepted)

- **A reference that does not resolve, wherever it is written.** `ORDER BY`, `GROUP BY`, `HAVING`,
  `QUALIFY`, window partition and order keys, and `WHERE` all raise `invalid identifier` for a name
  that names nothing, at the reference's own position. A qualified reference is checked in full — a
  wrong schema part or a three-part column path no longer resolves by accident.
- **A quoted identifier resolves exactly.** `"a"` used to fold and find column `A`; it now matches its
  own spelling only, in every path — the select list, qualified references, `ORDER BY` keys, quoted
  aliases. The refusal echoes the name as written, keeping the quotes only when the name could not be
  written without them (`"Q"` echoes as `Q`, `"qQ"` keeps its quotes).
- **Misplaced aggregates and window functions.** A window function inside `HAVING` or a `GROUP BY` key,
  an aggregate inside `WHERE`, and a window inside an aggregate are each refused with the account's own
  misplacement sentence. An aggregate in `ORDER BY` or `QUALIFY` now makes the query an aggregate
  query, so an ungrouped select list beside it is refused rather than silently grouped.
- **Conditional branches that cannot fold.** `IFF` / `CASE` / `COALESCE` and family over two type
  families with no common supertype — a `DATE` beside a `TIME`, a `BINARY` beside a `VARCHAR` — are
  compile-time errors, as is a `BINARY` beside a string anywhere in the `CONCAT` / `IFF` family.
- **Concatenating a container with anything.** `OBJECT`, `ARRAY`, `GEOGRAPHY` and `GEOMETRY` take no part
  in `||` — not against a string, a number, a `VARIANT`, or each other. `VARIANT` is unaffected and still
  concatenates to full-width text.
- **Eight temporal arithmetic shapes.** Of the 32 combinations of `DATE` / `TIME` / `TIMESTAMP` / `NUMBER`
  under `+` and `-`, only seven are legal. Note the asymmetry: `NUMBER + DATE` is accepted but
  `NUMBER - DATE` is refused, a `TIMESTAMP` takes no numeric operand on either side, and `TIME` is refused
  against everything including itself.
- **An unknown function name, at compile time.** Previously this was raised only when a row was evaluated,
  so the same statement was accepted or refused depending on whether the table happened to have rows — and
  a `CREATE VIEW` or `CREATE TABLE AS` over an empty table was accepted with an uncompilable body.
- **Decoded bytes that are not text.** `BASE64_DECODE_STRING` and `HEX_DECODE_STRING` refuse input whose
  bytes are not valid UTF-8 (`Invalid UTF8 detected while decoding 'qw=='`) instead of substituting
  replacement characters; the `TRY_` spellings answer `NULL`. `JSON_EXTRACT_PATH_TEXT` refuses a
  statically `OBJECT`-typed first argument, and `TRY_VALIDATE_UTF8` refuses a `BINARY` — both at compile
  time, naming the offending types.
- **`OBJECT_CONSTRUCT` with a `VARIANT` key.**
- **`ALTER TABLE … ADD COLUMN` default values** that do not match the column's type family, and computed
  defaults generally: `ADD COLUMN` takes a bare literal where `CREATE TABLE` accepts an expression.
- **A `BINARY` column with any `DEFAULT`**, and several other DDL shapes a real account rejects
  (duplicate `COMMENT`, transient clone, `COLLATE` after `DEFAULT`, sub-9 precision defaults).

### Changed

- **Result columns now declare computed types instead of placeholders.** Conditionals fold their
  branches by the account's rules (`FLOAT` beats `NUMBER` from either side, `DATE` widens into the
  timestamps, a `NULL` branch contributes nothing); string functions compute a width from their input
  instead of claiming 16MB; each aggregate declares its own type (`COUNT` is `NUMBER(18,0)`, not
  `BIGINT(38,0)`); computed columns like `i + 1` and `LENGTH(t)` are typed; `DATEADD` over a `DATE`
  stays a `DATE`; every integer alias reports `NUMBER(38,0)`; `VARBINARY` reports `BINARY` and bare
  `TIMESTAMP` reports the session's `TIMESTAMP_NTZ`; a set operation declares the type its arms fold
  to, not the leading arm's. A `FLOAT` reports no precision or scale anywhere — including
  `ResultSetMetaData`, which answered 38/9 before.
- **Error precedence and positions match the account.** An unknown function outranks an argument-type
  error; a leading comment no longer shifts an error's position; a dozen syntax-error shapes anchor
  where live anchors (`SELECT case FROM t` points at the next token, a truncated statement runs to end
  of input); a refused window call is echoed canonicalised, and `SELECT constraint` is an invalid
  identifier rather than a syntax error.
- **The unknown-function message** is now `SQL compilation error:\nUnknown function X.` (previously
  `Unknown function: X`), and a qualified name reads `Unknown user-defined function X.`. Code matching on
  the old text needs updating — a matcher inside this repository did.
- **Argument-type refusals now carry a position**, anchored on the operator token, as a real account's do:
  `error line 1 at position 10 Invalid argument types for function '||': (BINARY(4), VARCHAR(4))`.
- **`SHOW COLUMNS` reports `data_type` as Snowflake's JSON descriptor** rather than a type name, and its
  relation-kind keyword is no longer a filter — `TABLE`, `VIEW` and the bare form each resolve a table, a
  view or a materialized view alike. The unnamed listing covers all three kinds, `IN SCHEMA db.schema`
  is accepted, and unset cells are spelled empty rather than `NULL`.
- **`DATE + INTERVAL '1 day'` stays a `DATE`.** The promote-to-timestamp line falls at the day, not the
  month. The two interval spellings genuinely differ for `DAY`: `INTERVAL '5 days'` keeps a `DATE`,
  `INTERVAL '5' DAY` promotes to `TIMESTAMP_NTZ`.
- **Column nullability crosses the wire and the JDBC surface**, so `ResultSetMetaData.isNullable` answers
  from the column rather than guessing.

### Fixed

- **A window ranking over grouped rows ranked every group equal.** `RANK() OVER (ORDER BY SUM(b))` on a
  grouped query returned `1, 1` instead of `1, 2`: a window over grouped rows now evaluates raw
  aggregates over each row's own source group, whether or not the aggregate is also a select item.
- **An exponent literal without a decimal point.** `1e0`, `1E+3`, `1e-3` were syntax errors in every
  argument or clause position — and in a select list `SELECT 1e0` silently parsed as `SELECT 1 AS e0`,
  returning the wrong column name. They now read as fixed-point numbers everywhere (`1e0` is
  `NUMBER(1,0)`, `1e20` prints `100000000000000000000`).
- **Names the account accepts are accepted.** `auto`, `break`, `continue` and a handful of other words
  now work as column, table, alias and CTE names; `POSITION(x IN y)` parses; `ORDER BY` may sort by a
  window call; `GREATEST` / `LEAST` fold a `DATE` beside a `TIMESTAMP` and answer instead of refusing;
  `INTERVAL` accepts `WEEK` and `QUARTER`.
- **A failed `CREATE OR REPLACE VIEW` no longer destroys the existing view.** Every failure mode —
  unnamable column, uncompilable body, wrong-count column list, missing base table, invalid identifier —
  used to drop the old view before validating the new one. The materialized-view path had the same bug.
- **View and CTAS bodies are validated before the object is created**, and the checks now run in the
  account's order: the body compiles first, and its failure is reported ahead of any column-naming or
  column-count complaint. A body refused for an argument type can no longer produce a view with no
  columns.
- **`GREATEST` over a binary and a string** refused with the account's sentence instead of throwing a
  raw `ClassCastException`.
- Numeric literals carry their own precision and scale, so a value like `0.05` is no longer stored as `0`
  through a derived table.
- `INFORMATION_SCHEMA` relations are listable, so container-wide listings no longer undercount.
- A comma join with its equality in `WHERE` now plans like the `ON` spelling (was ~800× slower), and
  large joins hash instead of scanning.

### Added

- `frostlake-ai`, `frostlake-geo`, `frostlake-rt-js`, `frostlake-rt-py` and `frostlake-rt-scala` publish
  alongside `frostlake-db` and `frostlake-formats` — **seven artifacts**, so the engine stays lean and each
  optional pack is opt-in by classpath.
- A JDBC `file:` transport (`jdbc:frostlake:file:<dir>`) — embedded and persistent, WAL-durable by default.
- The DML `DEFAULT` marker: `INSERT … VALUES (DEFAULT)` and `UPDATE … SET x = DEFAULT`.
- Structured `OBJECT` / `ARRAY` / `MAP` type spellings in metadata surfaces.

### Internal

- The codebase now has no lambdas, no streams and no static nested classes, enforced by Checkstyle at
  error severity in CI along with `final` on every never-reassigned local and one statement per line.
- A CI check refuses a build whose downstream projects pin a stale engine version.

## 0.0.6 and earlier

No changelog was kept before 0.0.7. The git history since `v0.0.3` is the record.
