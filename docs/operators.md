# Frostlake SQL Operators

This document is the catalog of SQL operators supported by the Frostlake engine, the operator
counterpart of [functions.md](functions.md). Every operator the engine parses AND executes is
listed here; when adding or removing one, update this file in the same change. Semantics follow
live Snowflake wherever they have been measured — the notes call out the measured edges.

## Arithmetic operators

| Operator | Form | Notes |
|---|---|---|
| `+` | `a + b` | Addition. Also date/timestamp `+ INTERVAL` arithmetic (`d + INTERVAL '1 day'`). |
| `-` | `a - b` | Subtraction, incl. `- INTERVAL`. |
| `*` | `a * b` | Multiplication. |
| `/` | `a / b` | Division. Division by zero is an error (`DIV0`/`DIV0NULL` are the guarded forms). |
| `%` | `a % b` | Modulo (the `MOD` function is its twin). |
| `+`, `-` (unary) | `-a`, `+a` | Sign. A NUMBER keeps its type; a text or a VARIANT operand converts to a FLOAT (`+'5'` is 5, `-'3'` is -3.0); a BOOLEAN, DATE/TIME/TIMESTAMP, BINARY, OBJECT, ARRAY or GEOGRAPHY operand is refused while the statement compiles, at the sign, under Snowflake's names — `Invalid argument types for function 'NEGATE': (BOOLEAN)` / `'UNARY PLUS': (DATE)`. |

Numeric results carry Snowflake's derived precision/scale rules for NUMBER operands (the
live-verified supertype fold), and DOUBLE contaminates as in Snowflake.

**A text in arithmetic reads as a number of a fixed width** (all live-verified). A VARCHAR column or
expression beside an exact NUMBER reads as `NUMBER(18,5)`: `t + 0` declares `NUMBER(19,5)` and answers
`5.00000`, `t / 1` `NUMBER(24,11)`, `t % 2` `NUMBER(18,5)`, `t * n` over a NUMBER(10,2) `NUMBER(28,7)`,
and the value is rounded HALF_UP to five decimals before the operation (`'1.123456' + 0` is `1.12346`).
A text LITERAL reads as `NUMBER(18, s)` with its own scale (`'5' + 1` is `NUMBER(19,0)` and `6`,
`'2.5' + 1` `NUMBER(19,1)` and `3.5`). A text beside a text, a FLOAT, a VARIANT or a bare NULL is a
FLOAT, as is a VARIANT beside anything and a signed text (`-t`). The reading refuses at row time: a text
spelling no number is `Numeric value 'x' is not recognized`, and so is one whose five-decimal reading
overflows the eighteen digits it is parsed into (`'99999999999999'`, `'1e14'`, `'1e30'`); one that parses
but carries more than thirteen whole digits is `Numeric value '12345678901234' is out of range`.
`MOD`, `DIV0` and `DIV0NULL` read their arguments the same way (`MOD(t, 2)` is `NUMBER(18,5)` `1.00000`,
`MOD('5', 2)` `NUMBER(2,0)`), and so does the rounding family once it is given a scale (`ROUND(t, 1)` is
`NUMBER(19,1)`; a one-argument `ROUND(t)`, `ABS(t)`, `SIGN(t)` or `SQRT(t)` is a FLOAT read at its full
digits).

`INTERVAL` literals take the quoted form only: `INTERVAL '1 day, 2 hours'` (plural units and
comma-separated parts inside the string, bare numbers default to seconds) or
`INTERVAL '<n>' <singular unit>`. An unquoted amount (`INTERVAL 10 DAY`) is a syntax error, and a
plural unit word after the string is an alias, not a unit — both live-verified.

## String operators

| Operator | Form | Notes |
|---|---|---|
| `\|\|` | `a \|\| b` | Concatenation (NULL-propagating, like the `CONCAT` function). |

## Comparison operators

| Operator | Form | Notes |
|---|---|---|
| `=` | `a = b` | Equality. |
| `!=`, `<>` | `a != b` | Inequality — both spellings lex to the same operator. |
| `<`, `<=`, `>`, `>=` | `a < b` | Ordering. |
| `IS [NOT] NULL` | `a IS NULL` | Two-valued (never UNKNOWN). |
| `IS [NOT] DISTINCT FROM` | `a IS DISTINCT FROM b` | NULL-safe equality. |
| `[NOT] BETWEEN` | `a BETWEEN x AND y` | Inclusive range. |
| `[NOT] LIKE` | `a LIKE p [ESCAPE e]` | `%`/`_` wildcards, optional ESCAPE. |
| `[NOT] ILIKE` | `a ILIKE p [ESCAPE e]` | Case-insensitive LIKE. |
| `LIKE ANY / LIKE ALL / ILIKE ANY` | `a LIKE ANY (p1, p2, …) [ESCAPE e]` | Multi-pattern forms. Snowflake has ONLY these three: `NOT LIKE ANY/ALL` and `ILIKE ALL` are compile errors there, so Frostlake deliberately omits them (live-verified). |
| `[NOT] RLIKE`, `[NOT] REGEXP` | `a RLIKE p` | Whole-string regex match (the `REGEXP_LIKE` function family is the callable twin). |
| `[NOT] IN` | see below | Membership, in all of Snowflake's shapes. |

All comparisons propagate NULL as UNKNOWN (three-valued logic), while GROUP BY / DISTINCT /
set-operator deduplication treat NULLs as equal — the live-measured split.

A comparison reads its two operands into a common type before deciding, so equal values match across
the families: a number compares by VALUE whatever its scale (`1` equals `1.00`), a DATE compares
against a TIMESTAMP by INSTANT with the date read as its own midnight, a boolean reads against the
number beside it, and a string is read as the number or temporal it is compared with. Text that
cannot be read that way is an ERROR rather than an inequality, naming the family of the other
operand — `'ab' = 1` raises `Numeric value 'ab' is not recognized`, `'ab' = <date>` raises
`Date 'ab' is not recognized`, and a TIMESTAMP or TIME operand names itself the same way
(live-verified). Every surface that routes through the comparison inherits all of this: `IN`, a
simple `CASE`, and `DECODE`.

### IN shapes

| Shape | Example | Notes |
|---|---|---|
| Scalar list | `a IN (1, 2, 3)` | Three-valued (a NULL probe or NULL member yields UNKNOWN). |
| Scalar subquery | `a IN (SELECT x FROM t)` | Three-valued — a NULL probe is UNKNOWN even over an empty result (live-measured). |
| Tuple row list | `(a, b) IN ((1, 2), (3, 4))` | Three-valued. |
| Tuple subquery | `(a, b) IN (SELECT x, y FROM t)` | TWO-valued, unlike the other shapes (live-measured). |
| Tuple flat list | `(a, b) IN (1, 2)` | Parses but is a compile-time type error (ROW vs scalars), matching Snowflake. |

## Logical operators

| Operator | Form | Notes |
|---|---|---|
| `AND` | `p AND q` | Three-valued. |
| `OR` | `p OR q` | Three-valued. |
| `NOT` | `NOT p` | Three-valued. |

## Subquery operators

| Operator | Form | Notes |
|---|---|---|
| `[NOT] EXISTS` | `EXISTS (SELECT …)` | Correlated or not. |
| Scalar subquery | `(SELECT …)` in expression position | Must produce one column; more than one row is a runtime error. |
| Quantified comparison | `a > ANY (SELECT …)`, `a = ALL (SELECT …)` | Quantifiers `ANY`, `SOME`, `ALL` with every comparison operator; NULL propagation follows the measured three-valued rules. |

## Set operators

| Operator | Form | Notes |
|---|---|---|
| `UNION [ALL]` | `q1 UNION q2` | Plain UNION deduplicates treating NULLs as equal. |
| `UNION [ALL] BY NAME` | `q1 UNION ALL BY NAME q2` | Aligns columns by NAME rather than position. |
| `INTERSECT [ALL]` | `q1 INTERSECT q2` | |
| `EXCEPT [ALL]` | `q1 EXCEPT q2` | `EXCEPT` is an UNRESERVED word (usable as an alias) — the parser disambiguates by the next token, as Snowflake does (live-measured). |
| `MINUS [ALL]` | `q1 MINUS q2` | Snowflake synonym for EXCEPT; `MINUS` is reserved. |

## Semi-structured access and cast operators

| Operator | Form | Notes |
|---|---|---|
| `:` | `v:path`, `v:a.b`, `v:a:b` | VARIANT/OBJECT path access; `.` and `:` may continue the path interchangeably. |
| `.` | `v:a.b`, `obj.field` | Field access within a semi-structured path. |
| `[]` | `v[0]`, `v:arr[2]` | Array subscript (and object key subscript with a string). |
| `::` | `expr::TYPE` | Cast. Parameterized types (`::NUMBER(10,2)`) and the `RENAME FIELDS` / `ADD FIELDS` structured-type modifiers are accepted; `CAST`/`TRY_CAST` are the function-shaped twins. |
| `{…}`, `[…]` | `{'k': v}`, `[1, 2]` | Object and array construction literals (sugar for `OBJECT_CONSTRUCT` / `ARRAY_CONSTRUCT`). |

Path steps into a VARIANT preserve the value's variant type (a JSON number stays a number); date,
time, timestamp and binary values keep their types inside containers, per the measured container
typing.

## Special operators

| Operator | Form | Notes |
|---|---|---|
| `(+)` | `WHERE t1.k = t2.k (+)` | Oracle-style outer-join marker on a column reference — rewritten to the corresponding OUTER JOIN, as Snowflake does. |
| `->` | `FILTER(arr, x -> x > 1)` | Lambda body separator in higher-order function arguments (`TRANSFORM`, `FILTER`, `REDUCE`, …) — valid only there. |
| `**` | `GREATEST(** [1, 5, 3])` | Argument splatting: splices a CONSTANT array literal (or `ARRAY_CONSTRUCT` call) into the enclosing argument list. A runtime expression operand is rejected, and there is no object-literal or bare `SELECT **` spread — all live-verified. |
| `$N` | `SELECT $1 FROM @stage`, `SELECT $2 FROM t` | Positional column reference — staged-file fields in COPY/stage queries, ordinal columns in table queries; an unaliased `$N` item is itself named `$N`. |
| `?`, `:name` | `WHERE a = ?` | Bind parameters (JDBC positional `?`, named/positional `:x` binds); substituted client-side by the drivers and `EXECUTE IMMEDIATE … USING`. |
