# Changelog

Notable changes to Frostlake, newest first. Versions follow the published Maven artifacts
(`dev.frostlake:frostlake-db` and the six optional packs, which release together).

Frostlake's goal is to answer SQL the way a real Snowflake account does. That makes **a new refusal a
fix, not a regression**: SQL that Snowflake rejects and Frostlake accepted was a fidelity bug, and
closing it can break code that relied on the leniency. Those changes are listed first in every release
for exactly that reason.

## 0.2.1 — unreleased

Changes since 0.2.0 accumulate here as they land.

### Now refused (SQL that earlier versions accepted)

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

### Changed

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
