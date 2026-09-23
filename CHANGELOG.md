# Changelog

Notable changes to Frostlake, newest first. Versions follow the published Maven artifacts
(`dev.frostlake:frostlake-db` and the six optional packs, which release together).

Frostlake's goal is to answer SQL the way a real Snowflake account does. That makes **a new refusal a
fix, not a regression**: SQL that Snowflake rejects and Frostlake accepted was a fidelity bug, and
closing it can break code that relied on the leniency. Those changes are listed first in every release
for exactly that reason.

## 0.2.0 — unreleased

Changes since 0.1.0 accumulate here as they land.

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
