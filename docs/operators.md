# Frostlake SQL Operators

This document is the catalog of SQL operators supported by the Frostlake engine, the operator
counterpart of [functions.md](functions.md). Every operator the engine parses AND executes is
listed here; when adding or removing one, update this file in the same change. Semantics follow
live Snowflake wherever they have been measured — the notes call out the measured edges.

## Arithmetic operators

| Operator | Form | Notes |
|---|---|---|
| `+` | `a + b` | Addition. Also date/timestamp `+ INTERVAL` arithmetic (`d + INTERVAL '1 day'`). |
| `-` | `a - b` | Subtraction, incl. `- INTERVAL`. A TIMESTAMP minus a TIMESTAMP is an `INTERVAL DAY(9) TO SECOND(9)` (see below). |
| `*` | `a * b` | Multiplication. |
| `/` | `a / b` | Division. Division by zero is an error (`DIV0`/`DIV0NULL` are the guarded forms). |
| `%` | `a % b` | Modulo (the `MOD` function is its twin). |
| `+`, `-` (unary) | `-a`, `+a` | Sign. A negated NUMBER keeps its type, while a unary plus gives an exact NUMBER at least two integer digits, its scale kept and capped at 38 (`+1` is NUMBER(2,0), `+1.5` NUMBER(3,1), `+99` NUMBER(2,0), `+NULL` NUMBER(2,0)); a text or a VARIANT operand converts to a FLOAT (`+'5'` is 5, `-'3'` is -3.0); a BOOLEAN, DATE/TIME/TIMESTAMP, BINARY, OBJECT, ARRAY or GEOGRAPHY operand is refused while the statement compiles, at the sign, under Snowflake's names — `Invalid argument types for function 'NEGATE': (BOOLEAN)` / `'UNARY PLUS': (DATE)`. An interval negates to its own type and takes no plus (`'UNARY PLUS': (INTERVAL DAY(9))`). |

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

**A TIMESTAMP minus a TIMESTAMP is an `INTERVAL DAY(9) TO SECOND(9)`**, whatever the two flavours; beside an
LTZ or a TZ an NTZ is read at the session's time zone (all live-verified). The interval converts to an
exact number of seconds (`CAST(ts - ts2 AS NUMBER)`, `TO_NUMBER`, rounded half away from zero to the
target's scale; past its digits `Interval out of representable range, type: FIXED[SB2](3,0){not null} value:
+1 01:00:00.000000000`) and to text (`::VARCHAR`, `TO_CHAR`, `TO_VARCHAR`: `+1 01:00:00.000000000`). A cast to
any other family (DATE, TIME, a TIMESTAMP, FLOAT, BOOLEAN, BINARY, VARIANT, OBJECT, ARRAY) is refused while
the statement compiles, echoing the plan (`invalid type [CAST(DATE_DIFFTIMESTAMPTOINTERVAL(FAM.TS2, FAM.TS)
AS DATE)] for parameter 'TO_DATE'`), and so is any TRY_CAST of one. An interval moves a timestamp (`ts ±
interval` and `interval + ts` keep the flavour at nine digits; a DATE becomes a TIMESTAMP_NTZ), meets
another (`±`), scales by an exact number (`* n`, `n *`, `/ n`; dividing by zero is `Interval division by
zero`) and negates; intervals compare, group and take MIN/MAX. Any other pairing with an interval — a
number, a text, a VARIANT or a bare NULL added, a FLOAT or another interval as a factor or divisor, `%`, a
TIME, an interval minus a timestamp, the two interval families mixed, a quoted-string interval — is refused at
the operator.

The unit-suffixed literals below take the same arithmetic, typed by their fields (all live-verified): two
intervals of one family meet in the span of both (`INTERVAL '1' DAY + INTERVAL '1' HOUR` is an `INTERVAL
DAY(9) TO HOUR`, `YEAR + MONTH` a `YEAR(9) TO MONTH`) with a leading precision one past the wider operand's,
up to nine; a factor or divisor keeps the fields and widens the leading precision by the number's digits
(`INTERVAL '1' DAY(2) * 200` is a `DAY(5)`); a product is rounded half away from zero to the nanosecond or
the month and either result keeps nothing finer than its trailing field (`INTERVAL '1' DAY / 2` is zero
days, `INTERVAL '3' MONTH / 2` one month). A DATE plus a year-month interval stays a DATE, plus a day-time
one it becomes a TIMESTAMP_NTZ(9), in either order; a day-time interval moves a TIMESTAMP_LTZ by its exact
duration, where the quoted-string `'1 day'` moves it by a calendar day. A result its leading precision
cannot hold is `Interval out of representable range after <plus|minus|multiply|divide>, type:
INTERVAL_DAY_TIME[SB16](9,6){not null}` (the operation named, the type as the storage layer spells it). A Snowflake Scripting block
and a stored procedure type them the same way (`RETURN TO_VARCHAR(INTERVAL '1' DAY / 2)` is `+0`), a variable
the block reads scaling one as the number it holds.

`INTERVAL` literals take two quoted forms (all live-verified). The in-string one, `INTERVAL '1 day, 2
hours'`, reads comma-separated parts, each an optionally signed number (a fraction rounds half away from
zero to a whole count) and an optional unit word, singular or plural (none means seconds); a token out of
place is a syntax error counted inside the text (`INTERVAL '1 day 2 hours'` is `syntax error line 1 at
position 6 unexpected '2'.`), a reserved word such as `select` or `null` included, and an unknown unit word is
`<word> is not recognized as a date type.`. The text takes the statement's comments (`--`, `//`, `/* */`; a
block comment left open is `parse error … near '<EOF>'.`), and an amount its unit cannot count is refused
when a row reaches it (`Number out of representable range: type FIXED[SB4](9,0){not null}, value 1e+09` for a
day, week, month, quarter, year or hour amount of a billion; minutes, seconds and finer stay under 10^18, `FIXED[SB8](18,0)`, the amount compared as a double).
The unit-suffixed one, `INTERVAL '<text>' <qualifier>`, is a typed value: the qualifier is a field — YEAR,
MONTH, DAY, HOUR, MINUTE or SECOND, or its plural — with an optional leading precision (a SECOND also a
fractional one: `SECOND(2,3)`), or a range of fields (`DAY TO HOUR`, `DAY(3) TO SECOND(3)`, `YEAR TO
MONTH`), and the text is read by those fields (`'1 02:03:04.5' DAY TO SECOND`, `'-1-2' YEAR TO MONTH`,
`'1.5' SECOND`) when a row reaches the literal, refused in the account's words when it does not fit them.
`INTERVAL '1' DAY(2)` is an `INTERVAL DAY(2)`, stored in eight bytes where a `DAY(9)` takes sixteen. A
qualifier that names no type is `Invalid specification for type INTERVAL: INTERVAL <fields>` (`INTERVAL '1'
DAY(0)` gives `… INTERVAL DAY`) while the statement compiles; it and a quoted-string text with a token out of place
refuse the statement ahead of any unknown name it holds, the first in the text winning, while an unknown unit word
is judged with the names. An unquoted amount (`INTERVAL 10 DAY`) is a syntax error, and WEEK, QUARTER
and the sub-second words are no fields: after the string they are an alias.

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
| Row comparison | `(a, b) = (1, 2)`, `(a, b) < (c, d)` | Two row constructors of one width with `=`, `!=`/`<>`, `<`, `<=`, `>`, `>=`. Equality reads the whole row: one differing pair makes it FALSE, and a NULL decides only when nothing else does. The ordering operators compare lexicographically, stopping at the first differing pair. A row opposite a scalar, or two rows of different widths, is refused while the statement compiles. |
| `IS [NOT] NULL` | `a IS NULL` | Two-valued (never UNKNOWN). |
| `IS [NOT] DISTINCT FROM` | `a IS DISTINCT FROM b` | NULL-safe equality. |
| `[NOT] BETWEEN` | `a BETWEEN x AND y` | Inclusive range. |
| `[NOT] LIKE` | `a LIKE p [ESCAPE e]` | `%`/`_` wildcards, optional ESCAPE. Without ESCAPE nothing is an escape; the escape makes whatever follows it literal. A pattern ending in its escape is refused with `Escape character at end of LIKE pattern` where it is matched in full; a literal run with at most a leading or trailing run of `%` is compared directly and keeps that escape as a character. |
| `[NOT] ILIKE` | `a ILIKE p [ESCAPE e]` | Case-insensitive LIKE. Every pattern is matched in full, so one ending in its escape is always refused. |
| `LIKE ANY / LIKE ALL / ILIKE ANY` | `a LIKE ANY (p1, p2, …) [ESCAPE e]` | Multi-pattern forms. Snowflake has ONLY these three: `NOT LIKE ANY/ALL` and `ILIKE ALL` are compile errors there, so Frostlake deliberately omits them (live-verified). A pattern ending in its escape is refused where it is reached: LIKE ALL matches each pattern in written order and stops at the first miss, ILIKE ANY checks every pattern before matching, and LIKE ANY answers from its directly compared patterns first and checks the others only when none of those matched. |
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
