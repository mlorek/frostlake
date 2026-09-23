# Frostlake SQL Functions

This document is the catalog of SQL functions supported by the Frostlake engine. Every function the
engine implements should be listed here. When adding a new function, register it in
`FunctionRegistry`, add a dedicated test class, and add a row below.

## Snowflake argument-type strictness

Frostlake mirrors Snowflake's COMPILE-TIME argument rules, so a query that the engine accepts is a
query the account accepts. All of the following are live-verified rejections, not engine limitations:

- **Temporal functions reject VARCHAR — including string CONSTANTS.** `DATE_TRUNC`, `EXTRACT`,
  `DATE_PART`, `LAST_DAY`, `MONTHS_BETWEEN` and the part extractors (`YEAR`, `MONTH`, `DAY`,
  `DAYOFMONTH`, `HOUR`, …) all reject `'2024-04-08'`; cast it (`'2024-04-08'::DATE`). The coercing
  functions — `DATEADD`, `DATEDIFF`, `TO_DATE`, `TO_TIMESTAMP` — accept text as before.
- **`TRY_CAST` and the `TRY_TO_<TYPE>` family need a VARCHAR source.** `TRY_TO_NUMBER(123)` and
  `TRY_TO_BINARY(NULL)` are compile errors ("Function TRY_CAST cannot be used with arguments of
  types …"); `TRY_CAST(NULL AS NUMBER)` and `TRY_TO_BINARY(NULL::VARCHAR)` are fine. The
  SEMI-STRUCTURED types are rejected the same way: `TRY_CAST(<variant> AS DATE)` (and OBJECT / ARRAY
  sources, for every target type) is a compile error, while the plain `::` cast of the same value
  succeeds.
- **`COALESCE` needs at least two arguments.** `COALESCE(1)` fails "not enough arguments for function
  [COALESCE(1)], expected 2, got 1" (`GREATEST` / `LEAST` do accept one).
- **`TO_DATE` / `TRY_TO_DATE` reject a NUMBER.** `TO_DATE(1631711999)` is "invalid type … for
  parameter 'TO_DATE'"; the `DATE()` alias of the same function reads the epoch, and a numeric STRING
  (`TO_DATE('1631711999')`) works under every spelling.
- **An explicit date/time format is applied as written, and a mismatch is
  `Can't parse '<input>' as date|time|timestamp with format '<format>'`** (input and model echoed
  verbatim). `TO_DATE` / `TO_TIME` / `TO_TIMESTAMP*` with a second argument — any spelling but `AUTO`,
  the empty string included — read the input against that model only: `TO_DATE('2020-01-15',
  'YYYY/MM/DD')` and `TO_DATE('2020-01-15', '')` are refused, `TO_TIME('10:00 PM', 'HH12:MI AM')` is
  22:00:00, `TO_DATE('20200115', 'YYYYMMDD')` is a date and not an epoch, a missing field is
  1970-01-01 00:00:00 (`TO_DATE('15', 'DD')` is 1970-01-15), elements and month names match in any
  case, and February 30th or hour 24 are refused. The `TRY_` spellings answer NULL instead. A NULL
  format is a NULL answer; a format that is not a string — a NUMBER, BOOLEAN, DATE or VARIANT, literal
  or column, or an untyped `NULL` under `TO_DATE` / `TO_TIME` — is the compile-time "Format argument
  for function 'TO_DATE' needs to be a string" (the TIMESTAMP spellings name their flavour,
  `TO_TIMESTAMP` saying `TO_TIMESTAMP_NTZ`).
- **Beside a NUMBER, a TIMESTAMP conversion's second argument is a SCALE: a constant integer from 0 to
  9.** Any other literal — `TO_TIMESTAMP(n, '3')`, `(n, NULL)`, `(n, 1.5)`, `(n, TRUE)` — is the
  compile-time "argument 2 to function TO_TIMESTAMP_NTZ needs to be an integer, found: ''3''" (the
  literal echoed inside its own quotes, NULL as `'null'`); a column or an expression is "… needs to be
  constant, found 'ST.K'"; and an integer outside 0 to 9 is "Invalid value [12] for function
  'TO_TIMESTAMP_NTZ' at position 2". None carries a position. The `_LTZ` / `_TZ` spellings name
  themselves, `TO_TIMESTAMP` and `TRY_TO_TIMESTAMP` name `TO_TIMESTAMP_NTZ`, and a `TRY_` twin gives
  this sentence ahead of its TRY_CAST one. A FLOAT source keeps its "invalid type" sentence.
- **`TO_NUMBER` decoration needs a format model.** `TO_NUMBER('1,234.56')` and
  `TO_NUMBER('$1,234.567', 10, 2)` fail "Numeric value '…' is not recognized" — a (precision, scale)
  pair is not a format. With a model the input must FIT it: `TO_NUMBER('405.958', '9,999.99')` fails
  "Can't parse '405.958' as number with format '9,999.99'" (more fraction digits than the model), as
  does `TO_NUMBER('123', '99')` (more digits), and a grouped input needs a grouped model. `FX` switches
  the elements after it to exact matching, the input written as the model prints it in fill mode, sign
  position included: `TO_NUMBER(' 1', 'FX9')` is 1 and `TO_NUMBER('1', 'FX9')` fails. The model is
  checked before the input is read, `TO_DOUBLE` and `TO_DECFLOAT` alike, and one that is not a model is
  refused naming the target in live's words — `Bad input format model '9e9' for FIXED: invalid numeric
  format keyword: 'e9'`, `for REAL` from `TO_DOUBLE`, `for DECFLOAT` from `TO_DECFLOAT` — with its own
  sentence for an invalid character, a repeated or conflicting element, mixed hexadecimal / TM / digit
  elements and a model with no digit element. The `TRY_` twins answer NULL for all of them.
- **`TO_CHAR` / `TO_VARCHAR` print a number element by element.** Under a fixed-position model a `9`
  prints a leading zero as a space and a `0` as itself, a whole part of zero prints one `0` (a space after
  `B`), and trailing fraction zeros under `9` print as spaces; a group separator prints once a digit
  before it has, `%` multiplies the value by 100, literals print as written, and `_`, `FX`, `FM` and `B`
  print nothing. The sign — a space or `-`, or `+` / `-` from a leading `S` — floats to just before the
  first digit printed (the point when `B` blanks the whole part), and a leading `$` with it; an `S` after a
  digit and an `MI` print where they stand: `TO_CHAR(-7, 'MI99')` is `'- 7'`, `TO_CHAR(12, '9S9')` is
  `'1+2'`, `TO_CHAR(1, '9-9')` is `' - 1'`. `EE` … `EEEEEEE` normalise the value (`TO_CHAR(1.5,
  '9.9EEEE')` is `' 1.5E+00'`, `EE` pads its exponent to five places); `X` prints hexadecimal digits in
  its own case with no sign place, a negative value as its two's complement unless `S` or `MI` prints the
  sign (`TO_CHAR(255, 'XX')` is `'FF'`); `TM9`, `TME` and `TM` print the value minimally (`TO_CHAR(12345,
  'TME')` is `'1.2345E4'`, `TM` a FLOAT in whichever notation is shorter). `FM` removes the spaces
  numeric elements print. An empty model prints nothing, and one that is no model is refused as `Bad
  output format model '9Q' for FIXED: invalid numeric format keyword: 'Q'`, with the input model's other
  sentences.
- **The array family's needle must be VARIANT-coercible.** `ARRAY_CONTAINS('a', […])`,
  `ARRAY_POSITION('a', […])`, `ARRAY_REMOVE([…], 'a')` and `ARRAYS_OVERLAP([…], 'a')` are
  argument-type errors — NUMBER, BOOLEAN, ARRAY and an explicit `::VARIANT` coerce, VARCHAR / DATE /
  BINARY do not.
- **The `BOOL*` family rejects BOOLEAN.** `BOOLAND`/`BOOLOR`/`BOOLXOR`/`BOOLNOT` are defined over
  NUMBER / VARCHAR / VARIANT truthiness, so `BOOLAND(TRUE, FALSE)` is an argument-type error while
  `BOOLAND(1, 0)` works.
- **Raw crypto takes BINARY.** `ENCRYPT_RAW` / `DECRYPT_RAW` reject hex VARCHARs — wrap with
  `TO_BINARY`; only their optional `method` argument is text.
- **The MAP family needs a MAP**, not a plain OBJECT: cast with `::MAP(k, v)`, or build one with
  `MAP_CONSTRUCT`. Those positions accept a MAP and *nothing else* — a plain OBJECT, a VARIANT, an
  ARRAY, a structured `OBJECT(x INT)`, a VARCHAR, a NUMBER and an untyped `NULL` each give
  `Invalid argument types for function 'MAP_KEYS': (…)` naming their own type. A NULL argument must
  carry the type: `NULL::MAP(k, v)`.
- **`UNIFORM` is strictly three-argument** (`UNIFORM(5, 10, RANDOM())`), and its type follows its bounds:
  exact bounds make a `NUMBER` as wide as the wider bound's integer digits, two at the least, beside the
  larger scale, drawn at that scale — `UNIFORM(1, 10, g)` is `NUMBER(2,0)`, `UNIFORM(1.5, 10, g)` draws one
  decimal and `UNIFORM(0.0, 1.0, g)` only 0 and 1 — while a FLOAT or text bound draws a FLOAT.
- **`RANDOM`'s seed and `UNIFORM`'s bounds must be constants**, which is what the plan folds before the
  statement runs: literals, session variables and deterministic calls over them (`RANDOM(ABS(-5))`,
  `RANDOM(UNIFORM(1, 10, 5))`). A column, a subquery, a window, an aggregate, a call with no arguments
  (`PI()`, `CURRENT_DATE()`), a `RANDOM` or `SEQ` call and an untyped NULL the plan converts
  (`ABS(NULL)`) are refused: `argument 1 to function RANDOM needs to be constant, found 'RT.N'`.
- **`RANDOM`'s seed converts as a cast to FIXED does, and must not be NULL.**
  - A fractional seed rounds half away from zero: `RANDOM(1.5)` is `RANDOM(2)`, and so are `RANDOM('1.5')` and
    `RANDOM(PARSE_JSON('1.5'))`.
  - A VARIANT seed reads through its member, a boolean as 1 or 0; anything else fails with
    `Failed to cast variant value "x" to FIXED`. An ARRAY or OBJECT seed is an argument-type error.
  - A NULL seed, SQL or JSON, is refused when a row's value is read:
    `Invalid parameter value: NULL. Reason: seed must not be NULL`.
  - A null test over RANDOM is settled without reading it, so `RANDOM(NULL) IS NOT NULL` is TRUE.
- **`TO_CHAR` over a VARCHAR takes no format** — the two-argument form is an arity error.
- **`TRANSFORM` / `FILTER` lambdas take exactly one parameter** (only `REDUCE` takes two).
- **`GET`, `GET_PATH`, `TYPEOF`, `TO_JSON`, `TO_XML`, `XMLGET` need semi-structured input**, and
  colon path access over a declared VARCHAR column is the same `GET` error.
- **A FILE is not an OBJECT to the expression layer.** Even though a FILE value *is* an object of file
  metadata, `TYPEOF(f)`, `OBJECT_KEYS(f)` and `f:RELATIVE_PATH` are all compile errors naming the FILE
  type (`Invalid argument types for function 'GET': (FILE, VARCHAR(13))`). The `FL_GET_*` accessors are
  the only supported way in. Equality, `IS NULL`, `COUNT` and `SELECT DISTINCT` over a FILE all work,
  but a FILE may not be a `GROUP BY` / `ORDER BY` / `PARTITION BY` key, an argument to a text function
  (`LENGTH(f)`), a numeric function (`ABS(f)`) or a summing aggregate (`SUM(f)`, `MEDIAN(f)`,
  `STDDEV(f)`), a `||` or arithmetic operand, or a cast source — see [Where a FILE may not
  go](#where-a-file-may-not-go).

## Semi-structured / array functions

### The three nulls of the VARIANT layer

Snowflake's semi-structured layer distinguishes **three** nulls, and Frostlake models all three:

- **SQL NULL** — the ordinary absent value. A MISSING key or an out-of-range index reads as SQL NULL.
- **JSON null** (`NULL_VALUE`) — a real VARIANT *value*, produced by `PARSE_JSON('null')` and by any
  `null` inside parsed JSON. `TYPEOF` is `'NULL_VALUE'`, `IS NULL` is FALSE, `IS_NULL_VALUE` is TRUE and
  `TO_JSON` is the text `null`. It only collapses to SQL NULL where it leaves the semi-structured world
  (arithmetic, `||`, casts, ordinary scalar functions).
- **`undefined`** — what a **SQL NULL becomes when it enters an ARRAY**. `ARRAY_CONSTRUCT(1, NULL, 2)`
  renders `[1,undefined,2]` while `PARSE_JSON('[1,null,2]')` keeps `[1,null,2]`, and the two arrays
  compare **unequal**. Every producer that puts a SQL NULL into an array makes one: the `[…]` literal,
  `ARRAY_APPEND` / `ARRAY_PREPEND` / `ARRAY_INSERT` (including its gap padding) / `ARRAY_REPEAT` /
  `TRANSFORM`, the `**` spread, and `PARSE_JSON` of the bare `undefined` token. An `undefined` **never
  escapes the variant layer as a value** — extracting one (`GET`, `col[i]`, a path, a lambda binding)
  always yields SQL NULL, so `TYPEOF` and `IS_NULL_VALUE` of it are SQL NULL and `IS NULL` is TRUE.
  It lives only in an ARRAY: as an OBJECT member it degrades to a JSON null
  (`PARSE_JSON('{"a":undefined}')` is `{"a":null}`) and as a whole value it is SQL NULL
  (`PARSE_JSON('undefined')` is SQL NULL). `ARRAY_SIZE` counts it, `ARRAY_COMPACT` removes it (and a
  JSON null too), and `FLATTEN` **skips** it while keeping the original `INDEX` positions.

### How VARIANT values order and compare

Two VARIANT values order by the KIND of their member first — **BOOLEAN < NUMBER < STRING < OBJECT < ARRAY <
JSON null**, with an array's `undefined` hole after the null — and within the kind by its own rule: `false`
before `true`; numbers by VALUE whatever their notation (`9` before `10`, `1` equal to `1.0` and `1e0`,
`-Infinity` below every finite number and `NaN` above `Infinity`); strings by their code points (`"10"` before
`"9"`, `"Z"` before `"a"`); arrays element by element in this same order, a prefix first (`[] < [0,5] < [1] <
[1,2] < ["a"]`); objects member by member over their keys in sorted order, where at each position the LARGER
key sorts first and equal keys compare their values, a prefix first (`{} < {"b":0} < {"a":0} < {"a":0,"c":0} <
{"a":0,"b":0} < {"a":0,"b":1} < {"a":1,"b":0}`). The same order answers `ORDER BY`, `MIN` / `MAX`, `GREATEST`
/ `LEAST`, the comparison operators and the percentiles' ordering key, and its equality answers `=`, `IN`,
`DISTINCT`, `GROUP BY` and the set operations — so `PARSE_JSON('1') = PARSE_JSON('1.0')` and
`{"a":1,"b":2} = {"b":2,"a":1}` are TRUE, `1 = "1"` is FALSE, a JSON null equals a JSON null, and `COUNT(DISTINCT v)`
over `1`, `1.0`, `"1"`, `1e0` is 2. A SQL NULL stays outside the order (`NULLS LAST` / `FIRST` as usual), so
`ORDER BY v` lists the JSON null just before it. All live-verified.

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `PARSE_JSON` / `TRY_PARSE_JSON` | `PARSE_JSON(text)` | VARIANT | Reads JSON text into a VARIANT; TRY_ answers NULL where PARSE_JSON errors. **The parameter is a VARCHAR, so a VARIANT argument is coerced to VARCHAR first — which unwraps a variant STRING to its raw content.** That is what separates `PARSE_JSON(v:k)` over `{"k":"[1,2]"}`, an ARRAY, from `PARSE_JSON('"[1,2,3]"')`, which stays the VARCHAR `[1,2,3]`: the argument's type decides, never its content, so a string that merely reads as JSON is still a string. Re-parsing the JSON null gives SQL NULL, because the coercion flattens it. Parsing is lenient in the same places Snowflake's is (`\'`, an invalid escape such as a regex `\d`). An array HOLE is an `undefined` element — `[1,,2]` is `[1,undefined,2]` and a TRAILING comma adds one, so `[1,2,]` holds THREE elements — while an array written of only commas holds one per comma (`[,]` is one). An OBJECT has no holes: `{"a":,"b":1}` is refused as a misplaced comma, though a trailing comma is forgiven. An empty or blank document is SQL NULL (not the JSON null), and a lone `.` is 0. **Every KEYWORD is matched case-insensitively** — `True`, `FALSE`, `NULL`, `Undefined`, and the non-finite `nan` / `inf` / `infinity`, which read as the DOUBLE NaN and ±Infinity (`+NaN` is read, `-NaN` is refused; both signs work on the infinities). A word that is not a keyword still refuses with `unknown keyword "X", pos N`. An exponent past the double range is the NUMBER Infinity rather than a string, and a non-finite renders BARE (`NaN`, `Infinity`) wherever a VARIANT is written. An UNQUOTED attribute name is read when it is a plain ASCII identifier `[A-Za-z_][A-Za-z0-9_]*` and only then — `{a:1}`, `{_:1}`, `{a1b:1}` yes; a leading digit, a `$`, a `@`, a `/` or a non-ASCII letter no — and its case is kept. A word in NAME position is a name whatever it spells, so `{true:1}` and `{nan:1}` are objects with those attribute names. |
| `TO_VARIANT` | `TO_VARIANT(expr)` — same as `expr::VARIANT` | VARIANT | Wraps a value as a typed VARIANT. **A VARCHAR becomes a variant STRING and is never parsed** — the same rule the container builders follow, so `ARRAY_CONSTRUCT('[1,2]')` is `["[1,2]"]` and its element reads back a VARCHAR: `TYPEOF('[1,2,3]'::VARIANT)` is `VARCHAR`, it renders as `"[1,2,3]"`, and `FLATTEN` over it yields no rows — use `PARSE_JSON` to read the text as JSON. An already semi-structured value passes through unchanged, and temporal / binary values keep their native type inside the variant. |
| `TYPEOF` | `TYPEOF(variant)` | STRING | The exact type of the value inside a VARIANT: OBJECT, ARRAY, VARCHAR, INTEGER, DECIMAL, DOUBLE, BOOLEAN, NULL_VALUE, BINARY, DATE, TIME, TIMESTAMP_NTZ, XML (an XML-shaped object — see `PARSE_XML`). As in Snowflake, a literal or a declared non-variant column is an argument-type error (`Invalid argument types for function 'TYPEOF'`); wrap with `TO_VARIANT` first. SQL NULL in, NULL out. |
| `GET` | `GET(variant, key_or_index)` | VARIANT | The named field of an object / the indexed element of an array; SQL NULL when absent. The first argument must be semi-structured (a VARCHAR literal or column is rejected, as in Snowflake). |
| `GET_PATH` | `GET_PATH(variant, path)` | VARIANT | Path extraction (`'a.b[0]'`); SQL NULL when the path is absent. Same first-argument strictness as `GET`. `GET_IGNORE_CASE` is the case-insensitive field form. |
| `TO_JSON` | `TO_JSON(variant)` | STRING | The JSON text of a semi-structured value (a JSON null yields the text `null`). A VARCHAR literal or column is rejected, as in Snowflake — wrap with `TO_VARIANT` to serialize a scalar. |
| `TO_VARCHAR` (of a VARIANT) | `TO_VARCHAR(variant)` — same as `variant::VARCHAR` | STRING | Turning a VARIANT into a VARCHAR **unwraps a variant STRING to its content**: over `{"k":"[3,4]"}`, `TO_VARCHAR(v:k)` is the five characters `[3,4]`, not the quoted seven. Every other variant renders its own JSON text, and the JSON null becomes SQL NULL. Turning a VARCHAR into a VARCHAR does nothing at all — `'"[1,2,3]"'::VARCHAR` keeps its own quotes and stays nine characters — so the SOURCE's type is what decides. |
| `TO_GEOGRAPHY` / `TRY_TO_GEOGRAPHY` | `TO_GEOGRAPHY(input)` | GEOGRAPHY | **Requires the optional `frostlake-geo` module** (all geo functions below do). Parses WKT or GeoJSON into a spherical GEOGRAPHY (SRID 4326), displayed as GeoJSON with sorted keys; TRY_ returns NULL instead of erroring. Live-verified sphere: R = 6371010 m exactly. A GEOGRAPHY value may then only be used where Snowflake allows one — see [Where a GEOGRAPHY or GEOMETRY may not go](#where-a-geography-or-geometry-may-not-go); those rules are enforced by the ENGINE, module or no module. |
| `TO_GEOMETRY` / `TRY_TO_GEOMETRY` | `TO_GEOMETRY(input [, srid])` | GEOMETRY | Parses WKT or GeoJSON into a planar GEOMETRY (default SRID 0). |
| `ST_MAKEPOINT` (`ST_POINT`) / `ST_MAKEGEOMPOINT` (`ST_GEOM_POINT`) | `ST_MAKEPOINT(lon, lat)` | GEOGRAPHY / GEOMETRY | Point constructors. |
| `ST_ASTEXT` (`ST_ASWKT`) / `ST_ASGEOJSON` / `ST_ASWKB` (`ST_ASBINARY`) | `ST_ASTEXT(g)` | STRING / OBJECT / BINARY | Renderers: normalized WKT (`LINESTRING(0 0,1 0)`), the GeoJSON object, little-endian WKB. `ST_GEOGRAPHYFROMWKT`/`ST_GEOMETRYFROMWKT` (and `…FROMTEXT`) parse back. |
| `ST_X` / `ST_Y` / `ST_SRID` / `ST_DIMENSION` / `ST_NPOINTS` (`ST_NUMPOINTS`) / `ST_XMIN`/`ST_XMAX`/`ST_YMIN`/`ST_YMAX` | `ST_X(g)` … | NUMBER | Accessors; `ST_X`/`ST_Y` raise Snowflake's type error on non-points. |
| `ST_DISTANCE` / `ST_LENGTH` / `ST_AREA` / `ST_PERIMETER` | `ST_DISTANCE(g1, g2)` … | DOUBLE | Great-circle meters on Snowflake's sphere for GEOGRAPHY (spherical-excess areas), planar units for GEOMETRY; mixing kinds raises the argument-type error. `HAVERSINE(lat1,lon1,lat2,lon2)` returns km on R = 6371. |
| `ST_CONTAINS` / `ST_WITHIN` / `ST_INTERSECTS` / `ST_DISJOINT` / `ST_DWITHIN` | `ST_CONTAINS(g1, g2)` … | BOOLEAN | Predicates (point-in-polygon with holes, segment crossings, distance bound in meters). |
| `ST_CENTROID` | `ST_CENTROID(g)` | same kind | Center point (planar weighting — an approximation of Snowflake's spherical centroid). |
| `PARSE_XML` | `PARSE_XML(text [, disable_auto_convert])` | VARIANT (XML) | Parses an XML document into the XML-in-VARIANT model `{"$": content, "@": tag, "@attr": value, "childname": index}`; `TYPEOF` reports `XML` and the value stringifies as compact XML. Numeric element text and attribute values auto-convert (leading zeros/`+`/bare `.` keep the text; exponents become DOUBLE) unless the second argument is TRUE. Empty input is NULL; malformed XML errors with `Error parsing XML: …`. XML-ness is structural: `PARSE_JSON` of the same shape is XML too. |
| `CHECK_XML` | `CHECK_XML(text [, disable_auto_convert])` | STRING | NULL when the input is NULL, empty or valid XML; otherwise the parse-error message. |
| `TO_XML` | `TO_XML(variant)` | STRING | Compact XML for an XML-shaped variant; every other variant renders in Snowflake's `<SnowflakeData type="...">` scheme (object keys become elements, array elements `<e>`, JSON null self-closes with `xsi:nil="true"`). A VARCHAR argument is a compile-time error — wrap with `TO_VARIANT`/`PARSE_XML` first. |
| `XMLGET` | `XMLGET(xml, tag [, instance])` | VARIANT (XML) | The 0-based `instance`-th direct child element with the given tag (case-sensitive); NULL when absent, out of range, negative, or the value is not an XML element. A VARCHAR first argument is a compile-time error. |
| `ARRAYS_ZIP` | `ARRAYS_ZIP(a1, a2, …)` | ARRAY | Array of objects pairing same-index elements under keys `$1`, `$2`, …; shorter arrays pad with JSON null. An `undefined` element degrades to a JSON null on the way into the object. NULL/non-array input yields NULL. |
| `ARRAYS_TO_OBJECT` | `ARRAYS_TO_OBJECT(keys, values)` | OBJECT | Object pairing the key array with the value array by index (sizes must match; NULL keys drop the pair). An `undefined` value degrades to a JSON null member. |
| `ARRAY_REPEAT` | `ARRAY_REPEAT(value, n)` | ARRAY | An array of `n` copies of the value. A SQL NULL value repeats as `undefined`; a JSON null repeats as `null`. |
| `JSON_EXTRACT_PATH_TEXT` | `JSON_EXTRACT_PATH_TEXT(json, path)` | STRING | Parses the JSON text and returns the value at the dot/bracket path as unquoted text; NULL when absent. |
| `AS_CHAR` / `AS_NUMBER` / `AS_DECIMAL` / `AS_REAL` / `AS_INTEGER` | `AS_CHAR(v)` … | varies | Variant getters: the value when the variant holds that type, else NULL (`AS_CHAR`=`AS_VARCHAR`, `AS_REAL`=`AS_DOUBLE`). `AS_NUMBER`/`AS_DECIMAL(v[,p[,s]])` round (HALF_UP) to scale `s` (default NUMBER(38,0), so 2.5 becomes 3) and error past the precision; `AS_INTEGER` is NULL for a fractional value — all live-verified. |
| `AS_BINARY` / `AS_DATE` / `AS_TIME` / `AS_TIMESTAMP_NTZ` (`_LTZ`/`_TZ` alias) | `AS_DATE(v)` … | varies | NULL unless the RUNTIME value is already that type — this engine's variant members carry temporals/binary as text, so variant members yield NULL. |
| `IS_CHAR` / `IS_DECIMAL` / `IS_DOUBLE` / `IS_REAL` | `IS_CHAR(v)` … | BOOLEAN | Variant type predicates (`IS_DOUBLE` is TRUE for any number, as in Snowflake; `IS_CHAR`=`IS_VARCHAR`). |
| `IS_BINARY` / `IS_DATE` (`IS_DATE_VALUE`) / `IS_TIME` / `IS_TIMESTAMP_NTZ` (`_LTZ`/`_TZ` alias) | `IS_DATE(v)` … | BOOLEAN | TRUE only for a typed runtime value of that kind; variant members (text-carried) are FALSE. |
| `ARRAY_POSITION` | `ARRAY_POSITION(value, array)` | NUMBER | 0-based index of the first array element equal to `value`; NULL if not found or if `array` is NULL. A bare VARCHAR `value` is a compile error (cast it `::VARIANT`), matching Snowflake; numbers coerce. A SQL NULL `value` matches only an `undefined` element, a JSON null `value` only a JSON null. |
| `ARRAY_SORT` | `ARRAY_SORT(array [, sort_ascending [, nulls_first]])` | ARRAY | Sorts the array. `sort_ascending` defaults to TRUE; `nulls_first` defaults to FALSE for ascending order and TRUE for descending order. `nulls_first` governs the `undefined` elements ONLY — a JSON null is a *value*, ranked above every other variant type, so it sorts last ascending and first descending regardless of the flag. Two numeric elements compare by value; others compare lexically. |
| `ARRAY_MIN` | `ARRAY_MIN(array)` | VARIANT | Smallest element as a VARIANT (a string element displays quoted), ignoring `undefined` elements; a JSON null is a value ranked above every other type, so it is never the minimum unless it is the only value. NULL if the array is empty, all `undefined`, or NULL. |
| `ARRAY_MAX` | `ARRAY_MAX(array)` | VARIANT | Largest element as a VARIANT (a string element displays quoted), ignoring `undefined` elements; a JSON null ranks above every other type, so `ARRAY_MAX(PARSE_JSON('[1,null,2]'))` is the JSON null. NULL if the array is empty, all `undefined`, or NULL. |
| `ARRAY_COMPACT` | `ARRAY_COMPACT(array)` | ARRAY | Returns the array with every null element removed — BOTH `undefined` and JSON null go. Only the top level is compacted. |
| `ARRAY_CONSTRUCT_COMPACT` | `ARRAY_CONSTRUCT_COMPACT([expr, …])` | ARRAY | Builds an array from the arguments, omitting every SQL NULL, so the result may be shorter than the argument list. A VARIANT JSON null is a value, not a SQL NULL, and is kept. With no arguments, or when all are NULL, returns an empty array. |
| `ARRAY_REVERSE` | `ARRAY_REVERSE(array)` | ARRAY | Returns the array with its elements in reverse order. |
| `ARRAY_TO_STRING` | `ARRAY_TO_STRING(array, separator)` | VARCHAR | Joins the elements as text with `separator` between them. NULL `array` **or NULL `separator`** returns NULL; an `undefined` element renders as an empty string (its separators are kept), so `['a', NULL, 'b']` with `','` gives `'a,,b'`. (Snowflake raises on a JSON null element; Frostlake renders it empty too.) |
| `ARRAY_INSERT` | `ARRAY_INSERT(array, pos, element)` | ARRAY | Inserts `element` at 0-based index `pos`, shifting later elements right. `pos == size` appends; a negative `pos` counts from the back (`-1` inserts before the last element). A `pos` past the end pads the gap with `undefined`, as does a SQL NULL `element`. |
| `ARRAY_GENERATE_RANGE` | `ARRAY_GENERATE_RANGE(start, stop [, step])` | ARRAY | Array of integers from `start` (inclusive) to `stop` (exclusive). `step` defaults to 1 and may be negative. |
| `TO_ARRAY` | `TO_ARRAY(expr)` | ARRAY | NULL returns NULL; an existing array is returned unchanged; any other scalar is wrapped in a single-element array. |
| `TO_OBJECT` | `TO_OBJECT(expr)` | OBJECT | NULL returns NULL; an OBJECT (or a VARIANT containing an OBJECT) is returned unchanged; any other, non-object input is an error. |
| `MAP_CONSTRUCT` | `MAP_CONSTRUCT(key, value [, key, value …])` | MAP | Builds a MAP from alternating key/value arguments. Arguments must PAIR UP: an odd count is `not enough arguments for function [MAP_CONSTRUCT], expected 4, got 3` (the count named is the next EVEN one) and fewer than two is the same error with the call quoted back. A statically NULL argument — the untyped `NULL` literal — is rejected in either half, `Function MAP_CONSTRUCT does not support NULL argument type`; at RUN time a NULL key drops the whole pair while a NULL value is kept as a JSON null member (`{"a":null}`, which `MAP_SIZE` counts). A duplicate key is `Duplicate field key 'a'`. Members serialize key-sorted, like every OBJECT. |
| `MAP_CAT` | `MAP_CAT(map1, map2)` | MAP | Merge (concatenation) of two MAPs — the map holding every key of both inputs; on a key present in both, `map2`'s value wins. A NULL on EITHER side makes the whole result NULL (it is **not** treated as an empty map); an *empty* map merges normally. Members serialize key-sorted. |
| `MAP_KEYS` | `MAP_KEYS(map)` | ARRAY | The map's keys as an array, sorted by codepoint rather than in insertion order (`MAP_CONSTRUCT('Z','x','a','y','B','z')` gives `["B","Z","a"]`). A NULL map returns NULL; an empty map returns `[]`. The result is a STRUCTURED array, so `ARRAY_SIZE(MAP_KEYS(m))` works while `ARRAY_TO_STRING(MAP_KEYS(m), ',')` is an argument-type error. |
| `MAP_ENTRIES` | `MAP_ENTRIES(map)` | ARRAY | The map's entries as an array of `{"key": …, "value": …}` objects (lower-case member names), in key order. A NULL map returns NULL; an empty map returns `[]`. Each entry is navigable like any object: `MAP_ENTRIES(m)[0]:key`. |
| `MAP_SIZE` | `MAP_SIZE(map)` | NUMBER | The number of entries. A NULL map returns NULL, an empty map 0, and an entry whose *value* is a JSON null still counts. |
| `MAP_CONTAINS_KEY` | `MAP_CONTAINS_KEY(key, map)` | BOOLEAN | Whether the map holds `key` — note the MAP is the SECOND argument, the one place in the family where it is not first. A missing key and an empty map are both FALSE; a NULL on either side is NULL, not FALSE. |
| `MAP_DELETE` | `MAP_DELETE(map, key [, key …])` | MAP | The map without the named keys. A key the map does not hold is silently ignored, a repeated key is harmless, and a NULL key removes nothing. A NULL map returns NULL. |
| `MAP_PICK` | `MAP_PICK(map, key [, key …])` / `MAP_PICK(map, keyArray)` | MAP | The map narrowed to the named keys, passed either as varargs or as a single ARRAY (two real overloads; Snowflake does not let them mix). A key the map does not hold is skipped rather than added as a null, a repeated key is harmless, and a NULL key picks nothing — so `MAP_PICK(m, NULL)` is the EMPTY map, not NULL. Only a NULL *map* returns NULL. |
| `MAP_INSERT` | `MAP_INSERT(map, key, value [, updateFlag])` | MAP | The map with one entry added or replaced. Inserting a key the map already holds is the run-time error `Duplicate field key 'a'` unless `updateFlag` is TRUE (an absent flag, an explicit FALSE and an explicit NULL all raise). Its NULL rules differ from `OBJECT_INSERT`'s: a NULL *key* returns NULL (OBJECT_INSERT returns the map unchanged) and a NULL *value* is STORED as a JSON null member (OBJECT_INSERT omits the pair). A NULL map returns NULL. |
| `OBJECT_INSERT` | `OBJECT_INSERT(object, key, value [, update_flag])` | OBJECT | Inserts (or, with `update_flag = TRUE`, upserts) a key-value pair. A SQL NULL `key` or `value` OMITS the pair from the result — an already-present key is removed — while a JSON null (`PARSE_JSON('null')`) is stored as a real null member. Without the update flag, inserting a key that already exists is an error. Object constants (`{'k': v, …}`) follow the same SQL-NULL-dropping rule as `OBJECT_CONSTRUCT`. |
| `OBJECT_PICK` | `OBJECT_PICK(object, key1 [, key2, …])` | OBJECT | Object holding only the named keys (absent keys are skipped). Keys serialize sorted, like every OBJECT. |
| `TRANSFORM` | `TRANSFORM(array, <lambda>)` | ARRAY | Higher-order: applies the ONE-parameter lambda `<element> -> <expr>` (an index-carrying two-parameter lambda is a compile error, matching Snowflake) to each element, returning the array of results. NULL / non-array input returns NULL. |
| `FILTER` | `FILTER(array, <lambda>)` | ARRAY | Higher-order: keeps the elements for which the lambda `<element> [, <index>] -> <boolean>` returns TRUE. NULL / non-array input returns NULL. The lambda takes exactly ONE parameter; a two-parameter form is a compile error, matching Snowflake. |
| `REDUCE` | `REDUCE(array, initial, <lambda>)` | VARIANT | Higher-order: folds the array left-to-right with `(<accumulator>, <element>) -> <expr>`, starting from `initial`; returns the final accumulator. NULL / non-array input returns NULL. |

## Numeric / math functions

### What SIGN and WIDTH_BUCKET DECLARE

Both derive a width from an argument, and from different ones.

`SIGN(x)` is `NUMBER(2,0)` over an exact argument, whatever width that argument carries — the sign
takes a digit of its own — and FLOAT over anything with no width to widen from: an approximate, a
VARCHAR, a VARIANT.

`WIDTH_BUCKET(value, min, max, count)` is `NUMBER(max(2, digits(count)), 0)`. The overflow bucket
buys no digit: 99 buckets can answer 100 and still declares two. The count is read from any CONSTANT
expression — a folded `500+500`, a string `'100'`, a fraction that rounds — which is wider than the
literal-only rule `BASE64_ENCODE`'s line length and the rounding family's scale follow. Only a count
the row decides falls back, to `NUMBER(38,0)`.

A REVERSED range (`min > max`) is legal and buckets downwards, closed at the top rather than the
bottom, with 0 and `count + 1` swapping ends. A count that is not positive, or a range whose ends are
equal, is refused with both bounds printed unscaled.

### What the rounding family DECLARES

`CEIL`, `FLOOR`, `ROUND`, `TRUNC` and `ABS` derive their result type from the argument rather than
declaring a fixed one. For an input `NUMBER(p, si)` and a target scale `s` (0 when none is written):

| | declared type |
| --- | --- |
| `s >= si` | the input type UNCHANGED — nothing is being dropped |
| otherwise | `NUMBER(min(38, p + 1 + max(0, -s - (p - si))), max(s, 0))` |
| `ABS` | `NUMBER(min(38, max(p, si + 2)), si)` |

The single extra digit is for the carry a round-up can produce, which is why `CEIL` over a
`NUMBER(37,0)` stays `NUMBER(37,0)`. A negative scale buys further digits, but only once it reaches
past the integer digits the input already has; a scale span of more than 38 digits is
"Invalid parameter value: `<s>`. Reason: Scale too large".

The scale is read as a LITERAL, not as a value: `CEIL(n, 1)` declares a scale of one, while
`CEIL(n, 1+0)`, `CEIL(n, '1')`, `CEIL(n, 1.7)` and `CEIL(n, <column>)` all fall back to
`NUMBER(min(38, max(18, p + 1)), si)` even where they round identically. A NULL scale — or a NULL
rounding mode — makes the whole call NULL rather than meaning zero. An approximate input passes
straight through as a FLOAT. A VARCHAR input given a scale reads as `NUMBER(18,5)` — `ROUND(t, 1)` declares `NUMBER(19,1)`,
`ROUND(t, 7)` stays `NUMBER(18,5)` and answers `5.00000` — where the one-argument forms and `ABS` read it as a
FLOAT; a VARIANT is a FLOAT either way (see the arithmetic operators' text rule in `operators.md`).

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `TRUNC` | `TRUNC(num [, scale])` / `TRUNC(date_or_time, part)` | NUMBER / temporal | Overloaded as in Snowflake, and the FIRST argument decides which: numeric truncation toward zero to `scale` digits (default 0), or — over a DATE/TIME/TIMESTAMP beside a unit — `DATE_TRUNC(part, expr)`. A temporal ALONE is refused, `Invalid argument types for function 'TRUNC': (DATE)`, and a unit that is not a string literal is `Date/time component [TO_CHAR(1) ]for function TRUNC needs to be an identifier or a string literal.` — both as live. Over a number the second argument is always a scale, so `TRUNC(n, 'MONTH')` is "Numeric value 'MONTH' is not recognized" and `TRUNC(1.5, d)` is the family refusal `(NUMBER(2,1), DATE)`. |
| `CEIL` | `CEIL(num [, scale])` | NUMBER / FLOAT | Rounds up, to whole numbers by default and to `scale` decimal places when one is given; a negative scale rounds to a power of ten. |
| `FLOOR` | `FLOOR(num [, scale])` | NUMBER / FLOAT | Rounds down, otherwise identical to `CEIL`. |
| `ROUND` | `ROUND(num [, scale [, mode]])` | NUMBER / FLOAT | Rounds to `scale` decimal places (default 0). `mode` is `HALF_AWAY_FROM_ZERO` (the default) or `HALF_TO_EVEN`, matched case-insensitively and never trimmed. It must be a constant: a string literal, a session variable, or `\|\|` / `CONCAT` / `CONCAT_WS` / `UPPER` / `LOWER` over those, folded while the statement compiles. Anything else (`TRIM`, a cast, `IFF`, a column, a number inside the join) is refused as not constant, and a folded word that names no mode is refused at position -1. Over a FLOAT or VARIANT input there is no `mode` slot (too many arguments), and a constant NULL argument makes the call NULL before the mode or that arity is judged. |
| `ABS` | `ABS(num)` | NUMBER / FLOAT | The absolute value, keeping the input's own scale. |
| `BITCOUNT` | `BITCOUNT(n)` | NUMBER | The number of set bits in the integer value. |
| `NEGATE` | `NEGATE(n)` | NUMBER | The negative of the input. |
| `DIV0` | `DIV0(a, b)` | NUMBER | Divides `a` by `b`, but returns 0 when `b` is 0 instead of raising a divide-by-zero error. Any NULL argument (with an otherwise normal divisor) yields NULL, matching standard division. |
| `DIV0NULL` | `DIV0NULL(a, b)` | NUMBER | Like `DIV0`, but additionally returns 0 when the divisor `b` is NULL. A NULL dividend `a` with a normal, non-zero divisor still yields NULL. |
| `ACOSH` | `ACOSH(x)` | DOUBLE | Inverse hyperbolic cosine, `ln(x + sqrt(x*x - 1))` (domain `x >= 1`). NULL yields NULL. |
| `ASINH` | `ASINH(x)` | DOUBLE | Inverse hyperbolic sine, `ln(x + sqrt(x*x + 1))`. NULL yields NULL. |
| `ATANH` | `ATANH(x)` | DOUBLE | Inverse hyperbolic tangent, `0.5 * ln((1 + x) / (1 - x))` (domain `-1 < x < 1`). NULL yields NULL. |
| `GETBIT` | `GETBIT(integer, position)` | NUMBER | The value (0 or 1) of the bit at the 0-based `position`, counting from the least significant bit; a position beyond the value's set bits yields 0. Either NULL argument yields NULL; a negative position is an error. |

## Conditional functions

Three-valued logic over numeric/boolean inputs: non-zero is TRUE, zero is FALSE, and NULL is UNKNOWN.

**What a conditional's fold presents** (`COALESCE`, `IFF`, `NVL`, `IFNULL`, `NVL2`, `CASE`, `DECODE`,
`GREATEST`, `LEAST`; `NULLIF` keeps its first argument's own type) — the chosen branch is handed back
*converted to the fold*, exactly as Snowflake plans every branch as a cast, and every rule below is
live-verified:

- an exact NUMBER beside a wider NUMBER is presented at the folded scale (`COALESCE(i, n)` over an
  INT and a NUMBER(10,2) is `1.00`); a FLOAT beside any exact number makes the fold FLOAT and the value
  a double (`COALESCE(n, f)` is `1.5`, `IFF(TRUE, i, f)` is `1.0`);
- a DATE beside a TIMESTAMP is the midnight timestamp (`COALESCE(d, ts)` is `2020-01-01 00:00:00.000`),
  an NTZ beside an LTZ is the same wall clock in the session zone typed TIMESTAMP_LTZ, a chosen text is
  read as the fold's type (`IFF(FALSE, d, '2021-01-01')` is the date) and refused as `Date 'abc' is not
  recognized` (`Time`, `Timestamp`) when it reads as none;
- a BOOLEAN beside a number is a BOOLEAN whichever was written first (`COALESCE(1, b)` is TRUE,
  `IFF(FALSE, b, 0)` FALSE, `-1` and `1.5` TRUE); beside a STRING the first branch keeps the lead — a
  boolean first reads the chosen text strictly (`'yes'` TRUE, `'x'` / `'2'` / `''` are `Boolean value 'x'
  is not recognized`), a string first prints the boolean as `true`;
- a string COLUMN beside an exact number folds to NUMBER(18,5) (`IFF(FALSE, s, 1.5)` is `1.50000`), beside
  a FLOAT to FLOAT, beside a DATE / TIME / TIMESTAMP to that type — the text converted when chosen and
  refused with the family's row-time sentence (`Numeric value 'abc' is not recognized`) otherwise; a
  string LITERAL beside a number measures as the number it spells (`COALESCE('1.5', 1)` is NUMBER(2,1)),
  and one that spells no number keeps the NUMBER(18,5) fold and fails the row; beside a BOOLEAN or a
  VARIANT the string leads and the fold is the unbounded VARCHAR;
- two strings fold to the wider length (`VARCHAR(5)` beside `VARCHAR(10)` is `VARCHAR(10)`), the
  values untouched.

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `BOOLAND` | `BOOLAND(expr1, expr2)` | BOOLEAN | Logical AND. TRUE when both are true; FALSE when either is false (even if the other is NULL); NULL otherwise. Defined over NUMBER/VARCHAR/VARIANT truthiness — a BOOLEAN argument is a compile error, matching Snowflake. |
| `BOOLOR` | `BOOLOR(expr1, expr2)` | BOOLEAN | Logical OR. TRUE when either is true (even if the other is NULL); FALSE when both are false; NULL otherwise. Defined over NUMBER/VARCHAR/VARIANT truthiness — a BOOLEAN argument is a compile error, matching Snowflake. |
| `BOOLNOT` | `BOOLNOT(expr1)` | BOOLEAN | Logical NOT. NULL returns NULL, true returns FALSE, false returns TRUE. Defined over NUMBER/VARCHAR/VARIANT truthiness — a BOOLEAN argument is a compile error, matching Snowflake. |
| `BOOLXOR` | `BOOLXOR(expr1, expr2)` | BOOLEAN | Logical exclusive-OR. TRUE when exactly one input is true; FALSE when both are true or both are false; NULL when either input is NULL. Defined over NUMBER/VARCHAR/VARIANT truthiness — a BOOLEAN argument is a compile error, matching Snowflake. |
| `LEAST_IGNORE_NULLS` | `LEAST_IGNORE_NULLS(expr, …)` | VARIANT | The smallest non-NULL argument, ignoring NULLs (plain `LEAST` returns NULL when any argument is NULL). NULL only when every argument is NULL. |
| `GREATEST_IGNORE_NULLS` | `GREATEST_IGNORE_NULLS(expr, …)` | VARIANT | The largest non-NULL argument, ignoring NULLs (plain `GREATEST` returns NULL when any argument is NULL). NULL only when every argument is NULL. |

| `REGR_VALX` | `REGR_VALX(y, x)` | NUMBER | `x` when BOTH arguments are non-NULL, else NULL. |
| `REGR_VALY` | `REGR_VALY(y, x)` | NUMBER | `y` when BOTH arguments are non-NULL, else NULL. |

## String / regexp functions

Two rules hold across the whole family (all live-verified):

- **A character is a code point.** Lengths, cuts, pads and positions count characters, so an emoji or a
  mathematical letter outside the Basic Multilingual Plane is ONE: `LENGTH('😀')` is 1, `SUBSTR('😀ab', 2)` is
  `ab`, `LPAD('😀', 3, 'x')` is `xx😀`, `CHARINDEX('a', '😀a')` is 2, and a cut never splits a character. A
  string literal's declared width counts the same way (`'😀'` is VARCHAR(1)), and a declared `VARCHAR(n)` holds
  n characters (`'😀😀'` fits a VARCHAR(2)). `ASCII` answers the lead byte of the character's UTF-8 encoding
  (`ASCII('é')` is 195, `ASCII('😀')` 240); `OCTET_LENGTH` counts bytes.
- **A DATE, TIME or timestamp argument is read as its display text**, the text `||` and `::VARCHAR` give it:
  `LENGTH(ts)` over `2020-01-01 10:00:00` is 23 (`2020-01-01 10:00:00.000`), `CONTAINS(ts, 'T')` is FALSE, a
  TIME reads without a fraction, and LIKE matches the same text. `NORMALIZE` alone reads a temporal by its
  value. A `CONCAT`, `CONCAT_WS` or `INSERT` joining such a value (or a number, a BOOLEAN or a VARIANT) is
  declared VARCHAR(134217728).

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `INSERT` | `INSERT(base, position, length, insert)` | VARCHAR / BINARY | `SUBSTR(base, 1, position - 1) \|\| insert \|\| SUBSTR(base, position + length)`, which is how Snowflake plans it, SUBSTR's windows included: `length` = 0 inserts without removing, a `position` past the end appends, a `position` of 0 or -1 keeps the whole base behind the insertion (`INSERT('abc', 0, 1, 'x')` is `xabc`) and a negative one counts back from the end (`INSERT('abc', -2, 1, 'x')` is `xc`). Any NULL argument yields NULL. Two BINARY arguments splice bytes (`INSERT(X'6162', 1, 1, X'63')` is `6362`, a `BINARY(5)`); a BINARY beside another family is refused as that plan — `Invalid argument types for function '\|\|': (BINARY(8388608), VARCHAR(1), BINARY(8388608))`, and a BINARY position or length by the `-` / `+` inside it. |
| `LEFT` | `LEFT(string, length)` | VARCHAR | The leftmost `length` characters of `string` (the whole string if `length` exceeds it, empty if negative). Parses as a function even alongside `LEFT JOIN`. A NULL string yields NULL. |
| `RIGHT` | `RIGHT(string, length)` | VARCHAR | The rightmost `length` characters of `string` (the whole string if `length` exceeds it, empty if negative). Parses as a function even alongside `RIGHT JOIN`. A NULL string yields NULL. |
| `CONCAT_WS` | `CONCAT_WS(separator, value [, value …])` | VARCHAR | Joins the values with `separator`. Any NULL value makes the result NULL (NULLs are not skipped); a NULL separator does too, once there are two values to separate. A lone value never reads the separator, so `CONCAT_WS(NULL, 'x')` is `'x'`. |
| `SEARCH` | `SEARCH(data, query [, ANALYZER => '…'] [, SEARCH_MODE => 'OR'\|'AND'])` | BOOLEAN | Full-text token search: both sides tokenize on non-alphanumeric boundaries, case-insensitively; OR mode (default) is TRUE when any query token occurs in the data, AND when all do. `data` may be a single value or a tuple `(col1, col2)`, matching across every element. NULL data/query yields NULL. ANALYZER must name one Snowflake ships — `DEFAULT_ANALYZER`, `UNICODE_ANALYZER` or `NO_OP_ANALYZER`; any other name is an "Object … does not exist" error (default tokenization applies either way). |
| `EDITDISTANCE` | `EDITDISTANCE(string1, string2 [, max_distance])` | NUMBER(9,0) | The Levenshtein distance. With a maximum it is capped there: the maximum is rounded half away from zero, a negative one answers 0 and a NULL one NULL, a text maximum is read as a number, and one past a 32-bit integer is `Numeric value '2147483648' is out of range`. Either string NULL yields NULL. |
| `JAROWINKLER_SIMILARITY` | `JAROWINKLER_SIMILARITY(string1, string2)` | NUMBER | Jaro-Winkler similarity as an integer 0–100, truncated rather than rounded (`JAROWINKLER_SIMILARITY('ab', 'ac')` is 66). Both strings are lower-cased with the full Unicode mapping and compared by character, so the score is case-insensitive but sensitive to whitespace. Equal characters pair within `max(0, longer length / 2 - 1)` of each other, half the out-of-order pairs rounded down are transpositions, and nothing paired, an empty string on either side included, scores 0. The Winkler bonus of 0.1 per shared leading character, at most four, applies only from a Jaro similarity of 0.7. Either NULL yields NULL. |
| `PARSE_URL` | `PARSE_URL(url [, permissive])` | OBJECT | Parses `url` into a VARIANT OBJECT with keys in alphabetical order `fragment, host, parameters, path, port, query, scheme` (Snowflake object-key ordering) (`port` a string, `parameters` an object of query key/value pairs, missing parts JSON null; the leading `/` is stripped from `path` and any userinfo folds into `host`). A parse failure raises; with `permissive` = 1 it instead returns an object with only an `error` key. NULL url yields NULL. |
| `PARSE_IP` | `PARSE_IP(ip_address, type [, permissive])` | OBJECT | Parses an IPv4 address (optionally CIDR `addr/prefix`) into a VARIANT OBJECT with keys `family, host, ip_fields, ip_type, ipv4, ipv4_range_end, ipv4_range_start, netmask_prefix_length, snowflake$type`. `type` is `INET` or `CIDR` (echoed lowercased as `ip_type`); `ipv4` and the range are 32-bit integers and a bare address defaults to `/32`. IPv6 support is minimal (`family` 6 with best-effort `hex_ipv6` fields). A malformed address raises; with `permissive` = 1 it instead returns an object with only an `error` key. NULL address yields NULL. |
| `STRTOK_TO_ARRAY` | `STRTOK_TO_ARRAY(string [, delimiters])` | ARRAY | Tokenizes `string` on any character in `delimiters` (each character its own delimiter; default a single space), returning a VARIANT ARRAY of the non-empty tokens. A NULL string or NULL delimiter yields NULL. |
| `REGEXP_SUBSTR_ALL` | `REGEXP_SUBSTR_ALL(subject, pattern [, position [, occurrence [, parameters [, group_num]]]])` | ARRAY | A VARIANT ARRAY with an element for every match at or after `position`, starting from the `occurrence`-th. With the `e` flag or an explicit `group_num` each element is the matching capture group instead of the whole match. No matches yields an empty array; any NULL argument yields NULL. |
| `LIKE` | `LIKE(subject, pattern [, escape])` | BOOLEAN | The function-call form of the `subject LIKE pattern [ESCAPE escape]` operator: case-sensitive SQL wildcard match (`%` = any run of characters, `_` = one character). Without an escape nothing is an escape (`'a_' LIKE 'a\_'` is FALSE); with one, it makes whatever character follows it literal. A pattern ending in its escape is refused with `Escape character at end of LIKE pattern` where it is matched in full, but a literal run with at most a leading or trailing run of `%` is compared directly and keeps that escape as a character (`'ab!' LIKE '%b!' ESCAPE '!'` is TRUE). A NULL subject, pattern or escape yields NULL. |
| `ILIKE` | `ILIKE(subject, pattern [, escape])` | BOOLEAN | The function-call form of the `subject ILIKE pattern [ESCAPE escape]` operator: case-insensitive SQL wildcard match, the escape as for LIKE. Every ILIKE pattern is matched in full, so one ending in its escape is always refused. A NULL subject, pattern or escape yields NULL. |
| `COLLATE` | `COLLATE(string, 'spec')` or `string COLLATE 'spec'` | VARCHAR | The string itself, carrying the named collation into whatever compares it: `'a' COLLATE 'en-ci' = 'A'` is TRUE. The spec is hyphen-separated specifiers read case-insensitively — an optional locale first (`en`, `fr_CA`, any word that is not an option), then `cs`/`ci`, `as`/`ai`, `ps`/`pi`, `fl`/`fu`, `upper`/`lower`, `trim`/`ltrim`/`rtrim`; `''` is no collation, and a sensitivity option needs a locale. An explicit COLLATE outranks a column's collation, which outranks none; two different ones at one rank are refused (`Incompatible collations`). Comparisons, `IN`, `BETWEEN`, `LIKE` / `ILIKE` and `IS [NOT] DISTINCT FROM` honour it, and a locale orders linguistically (`a < A < b`). The operand must be a string and the spec a string literal. `ORDER BY`, `GROUP BY`, `DISTINCT` and the string functions still compare in binary. |
| `COLLATION` | `COLLATION(expr)` | VARCHAR | The collation `expr` carries, lower-cased: an explicit COLLATE, a collated column, or what `\|\|`, `CONCAT`, `IFF`, `COALESCE`, `UPPER`, `SUBSTR`, `MAX` and the like inherit from their arguments. NULL when it carries none, as after a cast. |
| `SOUNDEX` | `SOUNDEX(text)` | VARCHAR | The American Soundex code: the first character exactly as written, in its own case and whatever it is (`SOUNDEX('robert')` is `r163`, `SOUNDEX(' robert')` is ` 616`), then the codes of up to three later letters, zero-padded. Only ASCII letters carry a code (BFPV 1, CGJKQSXZ 2, DT 3, L 4, MN 5, R 6); a code equal to the one written before it is skipped, and when the first character is a letter its own code counts as written (`SOUNDEX('Pfister')` is `P236`). The vowels AEIOU and Y write nothing but separate equal codes; H, W and every other character (accented letters, digits, punctuation, spaces) are passed over, so `SOUNDEX('Ashcraft')` is `A261`. The empty string is `0000`, NULL yields NULL, and a non-text argument is read as its text (`SOUNDEX(123)` is `1000`). Declared VARCHAR(7). |
| `SOUNDEX_P123` | `SOUNDEX_P123(text)` | VARCHAR | SOUNDEX, except that the first letter's own code does not count as written, so a letter after it that shares the code is coded: `SOUNDEX_P123('Pfister')` is `P123` and `SOUNDEX_P123('Lloyd')` is `L430`, where SOUNDEX answers `P236` and `L300`. Declared VARCHAR(7). |

| `RTRIMMED_LENGTH` | `RTRIMMED_LENGTH(s)` | NUMBER | The length of the string without trailing blanks. |
| `RANDSTR` | `RANDSTR(length, generator)` | STRING | A deterministic random alphanumeric string seeded by the generator value. |
| `NORMALIZE` | `NORMALIZE(x, lo, hi)` | DOUBLE | Snowflake's min-max normalization `(x - lo) / (hi - lo)`, unclamped; DATE/TIME/TIMESTAMP normalize on their linear scales, and `lo >= hi` errors. NOT Unicode normalization (live-verified). |
| `TRY_VALIDATE_UTF8` | `TRY_VALIDATE_UTF8(x)` | STRING | The input as text when its bytes are valid UTF-8, else NULL. A BINARY argument is refused at compile time — the function reads text, not bytes. |
| `VALIDATE_UTF8` | `VALIDATE_UTF8(x)` | STRING | The input as text when its bytes are valid UTF-8. Indistinguishable from `TRY_VALIDATE_UTF8` on every reachable input: a BINARY is refused before evaluation and no accepted argument can carry invalid UTF-8. |
| `TRY_PARSE_IP` | `TRY_PARSE_IP(ip, family)` | VARIANT | `PARSE_IP` that returns NULL instead of erroring. With type `CIDR`, a prefixed address must be the network address (host bits zero) and an unprefixed one reports a null netmask with no range — as in Snowflake. |

## Date / time additions

### The component vocabulary EXTRACT and DATE_PART share

Both read ONE list, measured word for word down the whole abbreviation ladder. `NANOSECOND` and every
spelling of it reads; `MILLISECOND` and `MICROSECOND` do not, in any spelling, though the `EPOCH_`
family carries both precisions. `SS`, `WKS`, `ISOWEEK` and `ISODOW` are not components at all, while
`W`, `WY`, `YYY`, `YEAROFWEEK`, `YEAROFWEEKISO`, `TIMEZONE_HOUR` and `TIMEZONE_MINUTE` are.

A word that names no component is refused with the function's OWN parameter name, quoting the word
exactly as it arrived:

```
EXTRACT(ss FROM ts)   invalid value [ss] for parameter 'EXTRACT date/time part'
DATE_PART(ss, ts)     invalid value [SS] for parameter 'DATE_PART date/time part'
DATE_PART('ss', ts)   invalid value [ss] for parameter 'DATE_PART date/time part'
```

DATE_PART's bareword is upper-cased only because its first argument is a unit SLOT, which resolves
the name before the function sees it; a string literal and EXTRACT's part keep the case written.


| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `MONTHS_BETWEEN` | `MONTHS_BETWEEN(date1, date2)` | NUMBER | Months between two dates by the Oracle/Snowflake formula `(y1-y2)*12 + (m1-m2) + (day1-day2)/31`, whole when the day numbers match or both dates are month-ends. The result carries Snowflake's NUMBER(27,6) scale — the day fraction is rounded HALF_UP to six decimals (live-verified 1.580645, never a raw double). Both arguments must be temporal: a VARCHAR is rejected. |
| `DAYOFWEEKISO` / `WEEKISO` / `YEAROFWEEK` / `YEAROFWEEKISO` | `DAYOFWEEKISO(t)` … | NUMBER | ISO-8601 week parts (Monday = 1; engine weeks are ISO weeks). |
| `TIME_SLICE` | `TIME_SLICE(t, n, unit [, 'START'\|'END'])` | temporal | The start (default) or end of the n-unit bucket containing `t`; second→week buckets align on the epoch (weeks on Monday), month/quarter/year on the calendar. A DATE input stays a DATE. |
| `DATE_ADD<UNITS>TO<KIND>` | `DATE_ADDDAYSTODATE(amount, value)` … | DATE / TIMESTAMP / TIME | The shifts `DATEADD` is planned as, callable by name: `DATE_ADD<UNITS>TODATE` for `YEARS`, `QUARTERS`, `MONTHS`, `WEEKS`, `DAYS`, `HOURS`, `MINUTES`, `SECONDS`; `…TOTIMESTAMP` for those and `MILLIS`, `MICROS`, `NANOS`; `…TOTIME` for `HOURS` down to `NANOS`. The value is moved to the kind first — a timestamp or a text read as a DATE (a sub-day shift keeps the day it lands in: `DATE_ADDHOURSTODATE(30, d)` is the next day), a DATE or a text as a TIMESTAMP_NTZ(9) (a TIMESTAMP_TZ/LTZ keeps its flavour), a timestamp's time of day or a text read as a time as a TIME(9) — then shifted as `DATEADD` shifts, the amount rounded half away from zero. A VARIANT moves as a cast to the kind moves it: its text reads, a DATE held in it reaches only DATE and a timestamp only TIMESTAMP, and a number beside DATE or TIME, a boolean or a container fails `Failed to cast variant value 5 to DATE`. A value that does not move to the kind is refused, `Invalid argument types for function 'DATE_ADDHOURSTOTIME': (NUMBER(1,0), DATE)` (a TIME moved to a TIMESTAMP: `incompatible types: [TIME(9)] and [TIMESTAMP_NTZ(9)]`); named arguments and `OVER` are refused. Not listed by `SHOW FUNCTIONS`. |
| `DATE_DIFF<KIND>IN<UNITS>` | `DATE_DIFFDATEINDAYS(from, to)` … | NUMBER | The differences `DATEDIFF` is planned as, callable by name: `DATE_DIFFDATEIN<UNITS>` and `DATE_DIFFTIMESTAMPIN<UNITS>` for `YEARS`, `QUARTERS`, `MONTHS`, `WEEKS`, `DAYS`, `HOURS`, `MINUTES`, `SECONDS`, `MILLISECONDS`, `MICROSECONDS`, `NANOSECONDS`; `DATE_DIFFTIMEIN<UNITS>` for `HOURS` down. `DATEDIFF(unit, from, to)` over both values moved to the kind — the same argument order, so `DATE_DIFFDATEINDAYS(a, b)` counts from `a` to `b` — and as wide as the kind and unit make it: DATE NUMBER(9,0) to `DAYS`, (18,0) to `MILLISECONDS`, (38,0) beyond; TIMESTAMP (9,0) to `HOURS`, (18,0) for `MINUTES`/`SECONDS`, (38,0) from `MILLISECONDS`; TIME (9,0) to `MILLISECONDS`, (18,0) beyond. `DATEDIFF` itself is typed the same way by the kind its operands are planned in, and reads a text beside a DATE as a DATE: `DATEDIFF(hour, TO_DATE('2020-01-01'), '2020-01-02 10:00:00')` is 24. Refusals and VARIANT values as for the shifts. Not listed by `SHOW FUNCTIONS`. |

## Conversion functions

The `TRY_TO_*` variants are the non-throwing forms of their `TO_*` bases: they return NULL instead of raising on unparseable input, and NULL on NULL input.

A text converts to BOOLEAN **strictly**, by cast and by `TO_BOOLEAN` alike: `'true'` / `'t'` / `'yes'` /
`'y'` / `'on'` / `'1'` and their negatives, any case, trimmed — and anything else (`'x'`, `'2'`, `'1.0'`,
`''`) is `Boolean value 'x' is not recognized`; a number is its zero test (`1.5::BOOLEAN` is TRUE). A
text that reads as no time is `Time 'abc' is not recognized`, the same shape the DATE and TIMESTAMP
readers use. A TIME or TIMESTAMP declared with a precision keeps it: `SYSTEM$TYPEOF` over a
`TIMESTAMP_NTZ(3)` column, a `::TIME(3)` cast or a fold over either reads the declared digits, the storage
tag follows their range (`TIMESTAMP_NTZ(3)[SB8]`, `TIME(3)[SB4]`, nine digits `SB16` / `SB8`), a value written
into the column is truncated to them, and `DATEADD` over any flavour answers the family's nine.

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `TO_DOUBLE` / `TRY_TO_DOUBLE` | `TO_DOUBLE(expr [, format])` | FLOAT | A number as a double. A text is read as the FLOAT cast reads it — trimmed, `'NaN'` / `'inf'` / `'Infinity'` accepted — and one that spells no number is `Numeric value 'abc' is not recognized`, the text echoed trimmed (`TO_DOUBLE(' ')` names `''`). A text source may carry a numeric format model: its group separators and currency are accepted only when the model declares them (`TO_DOUBLE('1,234.5', '9,999.9')` is 1234.5), its width applies (`TO_DOUBLE('123', '99')` is `Can't parse '123' as number with format '99'`), a `TM` model reads any number, and a model that is not one is refused naming `REAL` (see the `TO_NUMBER` note above). A number beside a format is too many arguments, and a format that is no string is refused. `TRY_TO_DOUBLE` answers NULL where `TO_DOUBLE` would fail on the value. |
| `TO_UUID` | `TO_UUID(s)` | UUID | The canonical lowercase UUID form. Only the hyphenated 8-4-4-4-12 form is accepted (hyphenless or braced input is an error, as in Snowflake); `TRY_TO_UUID` returns NULL instead of erroring. |
| `BINARY_AS_STRING` | `BINARY_AS_STRING(bin)` | STRING | The binary input's bytes interpreted as UTF-8 text. |
| `STRING_AS_BINARY` | `STRING_AS_BINARY(s)` | BINARY | The string's UTF-8 bytes as BINARY. |
| `TO_BINARY` | `TO_BINARY(expr [, format])` | BINARY | Decodes a string into a real BINARY value: `HEX` (default), `BASE64`, or `UTF-8` (the string's UTF-8 bytes). A BINARY input passes through. Results display as uppercase hex. **Hex text is BARE** — there is no `0x` prefix, and one is refused as `The following string is not a legal hex-encoded value: '0x…'`, the same sentence odd-length or non-hex text gets. The `X'48656C6C6F'` literal is unaffected; its own syntax carries the marker. |
| `TO_DECFLOAT` | `TO_DECFLOAT(expr [, format])` | FLOAT | A number as Snowflake's 38-digit decimal float, carried here as a DOUBLE — the engine's DECFLOAT stand-in, so the 38 digits, the range past a double and the `DECFLOAT(38)` type are not modelled. Unlike `TO_DOUBLE` it takes a BOOLEAN (`TO_DECFLOAT(TRUE)` is 1) and a VARIANT's boolean. A text that spells no number is `Numeric value 'abc' is not recognized`, a VARIANT's DOUBLE `DecFloat not supported`, any other VARIANT that is no number or numeric text `Failed to cast variant value [1] to DECFLOAT`, and an infinite FLOAT `Decfloat out of representable range, operation: TO_DECFLOAT(inf)`; a text is echoed trimmed (`TO_DECFLOAT(' ')` names `''`). A text source may carry a numeric format model, read as `TO_NUMBER` reads one — its width applies (`TO_DECFLOAT('123', '99')` is `Can't parse '123' as number with format '99'`) and a model that is not one is refused naming `DECFLOAT`. A DATE / TIME / TIMESTAMP / BINARY / OBJECT / ARRAY source is refused while the statement compiles (`invalid type [TO_DECFLOAT(T.D)] for parameter 'TO_DECFLOAT'`), and a format beside a source that is no text is too many arguments. |
| `TRY_TO_DECFLOAT` | `TRY_TO_DECFLOAT(expr [, format])` | FLOAT | `TO_DECFLOAT` answering NULL where it fails on the value: a text that spells no number, a text its format model cannot read, a model that is not one. It reads text as `TO_DECFLOAT` does, not as `TRY_TO_DOUBLE`: `TRY_TO_DECFLOAT('inf')`, `('nan')`, `('1d')` and `('0x10')` are NULL. A source that is not text is refused while the statement compiles, as for every TRY_TO_ conversion: a BOOLEAN, a NUMBER, a VARIANT or a NULL with `Function TRY_CAST cannot be used with arguments of types BOOLEAN and DECFLOAT(38)`, and a DATE, a TIME, a TIMESTAMP, a BINARY, an ARRAY or an OBJECT with `invalid type [TRY_TO_DECFLOAT(T.D)] for parameter 'TO_DECFLOAT'`. A DECFLOAT source passes, and so does a FLOAT one, because a DECFLOAT is carried as a FLOAT. |
| `TIME` | `TIME(expr)` | TIME | The TIME synonym, which is not `TO_TIME`: a text is read as `TO_TIME` reads it (`TIME(TRUE)` is "Time 'true' is not recognized"), a DATE is its midnight, a timestamp its wall clock, and a NUMBER, an OBJECT, an ARRAY or a VARIANT holding no time fails as `Failed to cast variant value 123 to TIME`. A TIME argument is refused `incompatible types: [TIME(9)] and [TIMESTAMP_LTZ(9)]` and a BINARY by the argument types; it takes no format, so `TIME('10:00', 'HH24:MI')` is too many arguments and `TIME()` is `Invalid argument types for function 'TIME': ()`. |
| `TRY_TO_TIME` | `TRY_TO_TIME(expr [, format])` | TIME | Non-throwing `TO_TIME`. The optional format is applied as in `TO_TIME`; an input the model cannot read is NULL. The source must be a VARCHAR (Snowflake implements these as TRY_CAST): an untyped `NULL` or a numeric source is a compile error — cast it (`NULL::VARCHAR`) first. |
| `TO_TIMESTAMP` | `TO_TIMESTAMP(expr [, format \| scale])` | TIMESTAMP | Converts to the flavour the session's `TIMESTAMP_TYPE_MAPPING` names — TIMESTAMP_NTZ unless it names `TIMESTAMP_LTZ` or `TIMESTAMP_TZ` — and holds the value as that flavour's own spelling does. A NUMBER is a SECONDS epoch, or counts 10^-scale seconds under an integer scale from 0 to 9; a string of digits is an epoch whose unit its magnitude picks (`'20200115'` is 20,200,115 seconds, not a date — every cast, `::DATE` included, reads digits the same way); other text is read by the format, or by AUTO detection. |
| `TO_TIMESTAMP_NTZ` | `TO_TIMESTAMP_NTZ(expr [, format \| scale])` | TIMESTAMP_NTZ | A wall clock: an epoch's UTC wall clock, and text's own digits — an offset the text writes is dropped. |
| `TO_TIMESTAMP_LTZ` | `TO_TIMESTAMP_LTZ(expr [, format \| scale])` | TIMESTAMP_LTZ | An INSTANT, shown in the session's zone at that instant's offset: an epoch (a number, or a string of digits) is its instant, text with no offset names a wall clock in the session's zone, and text with an offset is re-expressed in it — `TO_TIMESTAMP_LTZ('2020-01-15 10:00:00 +02:00')` is `2020-01-15 00:00:00.000 -0800` under America/Los_Angeles, and a stored value re-renders when the zone changes. A VARIANT is read by what it holds: a number as a number (seconds, never unit-detected), a string as text, the JSON null as NULL; any other variant is `Failed to cast variant value true to TIMESTAMP_LTZ`. |
| `TO_TIMESTAMP_TZ` | `TO_TIMESTAMP_TZ(expr [, format \| scale])` | TIMESTAMP_TZ | An instant at an offset it KEEPS: text keeps the offset it was written with (the session's when it writes none), a NUMBER takes the session's offset, and a string of digits stays at UTC — under America/Los_Angeles `TO_TIMESTAMP_TZ(1579046400)` is `2020-01-14 16:00:00.000 -0800` and `TO_TIMESTAMP_TZ('1579046400')` is `2020-01-15 00:00:00.000 Z`. A VARIANT is read as by `TO_TIMESTAMP_LTZ`. |
| `TRY_TO_TIMESTAMP` | `TRY_TO_TIMESTAMP(expr [, format \| scale])` | TIMESTAMP | Non-throwing `TO_TIMESTAMP`, following the session's `TIMESTAMP_TYPE_MAPPING` as it does, value and declared type alike. A string of digits is an epoch whose unit its magnitude picks. The source must be a VARCHAR (Snowflake implements these as TRY_CAST): an untyped `NULL` or a numeric source is a compile error — cast it (`NULL::VARCHAR`) first. |
| `TRY_TO_TIMESTAMP_LTZ` | `TRY_TO_TIMESTAMP_LTZ(expr [, format \| scale])` | TIMESTAMP_LTZ | Non-throwing `TO_TIMESTAMP_LTZ`: the same instant in the session's zone, NULL for text it cannot read. |
| `TRY_TO_TIMESTAMP_TZ` | `TRY_TO_TIMESTAMP_TZ(expr [, format \| scale])` | TIMESTAMP_TZ | Non-throwing `TO_TIMESTAMP_TZ`: the same kept offset, NULL for text it cannot read. |
| `TRY_TO_BINARY` | `TRY_TO_BINARY(expr [, format])` | BINARY | Non-throwing `TO_BINARY` (HEX default / BASE64 / UTF-8). The source must be a VARCHAR (Snowflake implements these as TRY_CAST): an untyped `NULL` or a numeric source is a compile error — cast it (`NULL::VARCHAR`) first. |
| `TRY_TO_DECIMAL` / `TRY_TO_NUMERIC` | `TRY_TO_DECIMAL(expr [, precision, scale])` | NUMBER | Synonyms of `TRY_TO_NUMBER` (as `TO_DECIMAL` / `TO_NUMERIC` are of `TO_NUMBER`). |

## Context functions

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `CURRENT_VERSION` | `CURRENT_VERSION()` | VARCHAR | The version of the running engine (from the build metadata). |
| `CURRENT_CLIENT` | `CURRENT_CLIENT()` | VARCHAR | A fixed, stable client identifier (`Frostlake`). |
| `CURRENT_AVAILABLE_ROLES` | `CURRENT_AVAILABLE_ROLES()` | VARCHAR | JSON array (as text) of every role the session can use: active roles, the user's granted roles, and the closure over roles granted to roles, alphabetically. |
| `GET_DDL` | `GET_DDL('<object_type>', '<object_name>' [, <use_fully_qualified_names>])` | VARCHAR | Reconstructs the `CREATE OR REPLACE` text of a catalog object as the account prints it. `SCHEMA` and `DATABASE` are recursive: the container's statement, then every object it holds of these kinds — tags, sequences, tables and other table-like relations, views, file formats, functions and procedures, streams, pipes, tasks, policies, contacts, Streamlit apps, alerts — each as GET_DDL renders it alone; stages, secrets, network rules, notebooks and repositories are not recreated, and a database's text leaves out INFORMATION_SCHEMA. Single objects: `TABLE`, `VIEW`, `MATERIALIZED VIEW` and `DYNAMIC TABLE` (interchangeable), `SEQUENCE`, `FUNCTION`, `PROCEDURE`, `STREAM`, `TASK`, `PIPE`, `TAG`, `POLICY`, `FILE FORMAT`, `ALERT`, `CONTACT`, `STREAMLIT`. The third argument, read as a boolean, spells every recreated object's name fully qualified. The arguments must be constants — a column, or text the compiler cannot fold, is refused while the statement compiles. A NULL argument returns NULL. |
| `POLICY_REFERENCES` (table function) | `TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => '<p>'))` or `TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => '<o>', REF_ENTITY_DOMAIN => 'TABLE'\|'VIEW'))` | table | Every policy attached to one object, or every object one policy reaches. A column-level policy (masking, projection) fills `REF_COLUMN_NAME`; an object-level one (row access, aggregation, join) leaves it null and lists its argument columns in `REF_ARG_COLUMN_NAMES`. One of the two argument shapes is required. |
| `DATA_METRIC_FUNCTION_REFERENCES` (table function) | `TABLE(INFORMATION_SCHEMA.DATA_METRIC_FUNCTION_REFERENCES(REF_ENTITY_NAME => '<o>', REF_ENTITY_DOMAIN => 'TABLE'))` or `(METRIC_NAME => 'SNOWFLAKE.CORE.<m>')` | table | The data metric functions attached to an object, or the objects one metric measures. Two cells cannot be reproduced without inventing account state: `REF_ID` (a per-attachment UUID) answers null, and `REF_ARGUMENTS` omits the internal column id live embeds. |
| `PROJECTION_CONSTRAINT` | `PROJECTION_CONSTRAINT(ALLOW => <boolean>)` | OBJECT | The verdict a PROJECTION POLICY's body returns — `{"allow":true,"enforcement":"FAIL"}`. It is an ordinary callable function, not policy-only syntax, and the argument MUST be named: the positional call is refused wherever it appears. |
| `AGGREGATION_CONSTRAINT` | `AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => <n>)` | OBJECT | The minimum group size an AGGREGATION POLICY imposes. Callable ONLY from a policy body — a bare call is refused, unlike its two siblings. |
| `NO_AGGREGATION_CONSTRAINT` | `NO_AGGREGATION_CONSTRAINT()` | OBJECT | The aggregation-policy body that imposes nothing; answers `{}`. Callable anywhere. |
| `JOIN_CONSTRAINT` | `JOIN_CONSTRAINT(JOIN_REQUIRED => <boolean>)` | BOOLEAN | Whether a JOIN POLICY requires its table to be joined. Callable anywhere; a NULL argument is refused rather than propagated. |
| `UUID_STRING` | `UUID_STRING()` / `UUID_STRING(uuid_namespace, name)` | VARCHAR | Zero-arg: a fresh unique UUID. Two-arg: the RFC 4122 **version-5** named UUID (SHA-1 of the namespace UUID's bytes plus the name's UTF-8 text) — bit-exact with Snowflake; a numeric name is used as its text. NULL if either argument is NULL. |

| `CURRENT_IP_ADDRESS` | `CURRENT_IP_ADDRESS()` | STRING | The client IP (loopback for the embedded engine). |
| `CURRENT_ROLE_TYPE` | `CURRENT_ROLE_TYPE()` | STRING | Always `ROLE` (account-level roles only). |
| `CURRENT_SCHEMAS` | `CURRENT_SCHEMAS()` | STRING | The active search path as a JSON array of the current schema. |
| `CURRENT_SECONDARY_ROLES` | `CURRENT_SECONDARY_ROLES()` | STRING | The active secondary roles (none in the embedded engine). |
| `CURRENT_ACCOUNT` | `CURRENT_ACCOUNT()` | STRING | The account **locator**, upper-cased. Configured with `account.id` (default `ABC12345`) or the `?account=` URL parameter. |
| `CURRENT_REGION` | `CURRENT_REGION()` | STRING | The account's region, **bare** (`AWS_US_EAST_1`). The `<region_group>.<region>` spelling (`PUBLIC.AWS_US_EAST_1`) belongs to an organization spanning multiple region groups, so it is reachable by configuring `snowflake.region` with the prefix rather than being the default. Also settable with `?region=`. |
| `CURRENT_ORGANIZATION_NAME` | `CURRENT_ORGANIZATION_NAME()` | STRING | The organization the account belongs to, upper-cased. Configured with `organization.name` (default `ABCORG`) or the `?organization=` URL parameter. |
| `CURRENT_ACCOUNT_NAME` | `CURRENT_ACCOUNT_NAME()` | STRING | The account's **name**, upper-cased — a different identifier from `CURRENT_ACCOUNT()`, which answers the **locator**. Configured with `account.name` (defaults to `account.id`) or `?accountName=`. |
| `LAST_QUERY_ID` | `LAST_QUERY_ID([index])` | VARCHAR | The id of a statement the session ran: -1, the default, is the most recent and -2 the one before it, while a positive index counts from the session's first statement. 0, NULL and an index past either end of the history answer NULL; the engine keeps a session's first and last thousand ids. The index is a NUMBER(18,0), so `'1'` reads as 1 and a BOOLEAN is refused as an argument type at the call. It must be constant — a literal, signed or parenthesised, a numeric text, NULL or a session variable — and anything else is refused while the statement compiles as `argument 1 to function LAST_QUERY_ID needs to be constant, found '…'`, echoed from the plan (`CAST(1.5 AS NUMBER(18,0))`, `TO_NUMBER('abc', 18, 0)`, `1 + 0`); an index past 10,000 either way is `Value for parameter 1 exceeds maximum allowable value (10,000).`, even over no rows. |
| `CURRENT_STATEMENT` | `CURRENT_STATEMENT()` | VARCHAR | The text of the statement the session is running — each session's own. |
| `CURRENT_TRANSACTION` | `CURRENT_TRANSACTION()` | VARCHAR | The open transaction's id as decimal text — the 19-digit number SHOW TRANSACTIONS and SHOW LOCKS list and `SYSTEM$ABORT_TRANSACTION` takes — or NULL outside a transaction. |
| `LAST_TRANSACTION` | `LAST_TRANSACTION()` | VARCHAR | The id of the last transaction the session ended, committed or rolled back, as decimal text; NULL before the session ends one. Each session's own. |
| `GETVARIABLE` | `GETVARIABLE('<name>')` | VARCHAR | The session variable of that name, as text — `SET sv = 5` then `GETVARIABLE('SV')` answers `'5'`. The name is matched EXACTLY against the upper-cased name SET stores, so `'sv'` and `'"SV"'` read nothing; a name no SET defined, and a NULL name, answer NULL rather than refusing, where `$sv` refuses an unset variable. The result is a string of no width, and the name must be constant text — a column or a number is refused while the statement compiles. |
| `INVOKER_ROLE` | `INVOKER_ROLE()` | STRING | The invoking role (the current role here). |
| `IS_DATABASE_ROLE_IN_SESSION` | `IS_DATABASE_ROLE_IN_SESSION(name)` | BOOLEAN | TRUE when the session's current role holds the database role `db.role` (a bare name is in the current database), granted directly, through a role it inherits or through another database role; FALSE for any other name, an unknown one included. The bare word NULL or a number is refused while the statement compiles (`invalid argument for function [IS_DATABASE_ROLE_IN_SESSION] unexpected argument [NULL] at position 0,`). |
| `IS_ROLE_IN_SESSION` | `IS_ROLE_IN_SESSION(name)` | BOOLEAN | TRUE when the named role is the session's current role. |
| `SEQ1` | `SEQ1([sign])` | NUMBER(3,0) | The row's 0-based ordinal in one byte. Unsigned (`sign` 0, the default) it runs 0..127 and wraps back to 0; signed (`sign` 1) it wraps 127 → -128. See the note below. |
| `SEQ2` | `SEQ2([sign])` | NUMBER(5,0) | The same, two bytes: 0..32767 wrapping to 0, or 32767 → -32768. |
| `SEQ4` | `SEQ4([sign])` | NUMBER(10,0) | The same, four bytes. |
| `SEQ8` | `SEQ8([sign])` | NUMBER(19,0) | The same, eight bytes — wide enough that no practical row count reaches the wrap. |

### What the SEQ family counts

The value belongs to the **row**, not to the call: two `SEQ4()` calls in one row return the same
number, so `SEQ4() + SEQ4()` yields 0, 2, 4, 6 rather than 0+1, 2+3. The widths agree with each
other too — `SEQ4()` and `SEQ8()` in the same row report the same ordinal, differing only where they
wrap.

Rows are numbered as an operator receives them, so a `WHERE` that has already run has removed rows
first: over `(1..8)`, `SELECT x, SEQ4() FROM t WHERE x > 4` returns `5|0 6|1 7|2 8|3`. A `SELECT`
with no `FROM` is row 0.

The `sign` argument must be 0 or 1 — `2` is `Invalid parameter value: 2. Reason: sign must be 0 or 1`
and NULL is refused with its own sentence — but anything whose numeric value lands on one of them is
accepted, including the string `'1'`. It changes only where the count wraps, never where it starts
and never the declared type: `SEQ1(1)` reaches -128 and is still NUMBER(3,0).

**Frostlake is contiguous where Snowflake only promises distinctness.** On a real account the values
come from whatever unit of parallelism produced the row — a 16-row join returned 16 values spread
between 7 and 127, not 0..15 — which is why Snowflake's own documentation says not to use SEQ as a
key generator and to use `ROW_NUMBER` instead. That artefact is not reproducible and not stable
between runs, so Frostlake numbers rows 0, 1, 2, … in the order the operator produces them. Every
single-source shape matches live exactly. One further divergence: under a real `GROUP BY`, an
aggregated SEQ (`SELECT MIN(SEQ4()) … GROUP BY g`) is numbered by the scan on Snowflake and within
the group in Frostlake; ungrouped, where the group is the whole input, the two agree.

## Aggregate functions

**A star argument** (`COUNT(*)`, `HASH_AGG(t.*)`, `COUNT(* EXCLUDE (a))`, `COUNT(* ILIKE 'a%')`) expands to
the in-scope columns and the call is then an ordinary multi-argument one — the written-out list is what the
arity rule judges (`ARRAY_AGG(t.*)` over three columns is `too many arguments for function [ARRAY_AGG(T.A,
T.B, T.C)] expected 1, got 3`; a star that expands to nothing is `not enough arguments for function
[COUNT()], expected 1, got 0`). A qualified star names one relation (by alias, or by name when unaliased;
`Object 'X' does not exist or not authorized.` otherwise), an EXCLUDE drops columns (`column 'X' does not
exist`, `duplicate column name 'X'` when it misnames one), an ILIKE keeps the matching ones; RENAME and REPLACE
are a select item's modifiers and a syntax error here. `COUNT(*)` bare counts rows; every other star — and
`COUNT(a, b)` written out — counts the rows in which every listed column is non-NULL. The windowed forms
(`COUNT(t.*) OVER ()`, `HASH_AGG(* EXCLUDE (a)) OVER ()`) expand the same way. All live-verified.

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `OBJECT_AGG` | `OBJECT_AGG(key, value)` | OBJECT | Aggregates key/value pairs into a single OBJECT. Pairs with a NULL key or NULL value are omitted; scalar value types are preserved and keys are sorted (canonical form). The VALUE takes a NUMBER, a BOOLEAN, a FLOAT or a VARIANT and refuses a VARCHAR, a DATE / TIME / TIMESTAMP or a BINARY at compile time with the argument-type list (`OBJECT_AGG(t, t)` is `Invalid argument types for function 'OBJECT_AGG': (VARCHAR(10), VARCHAR(10))`, `OBJECT_AGG(t, d)` `(VARCHAR(10), DATE)`); the KEY takes any family, spelled as text (`1.50`, `2020-01-01`, `true`, a VARIANT's text). Live-verified. |
| `ARRAY_UNION_AGG` | `ARRAY_UNION_AGG(array)` | ARRAY | MULTISET union of the input arrays: each element keeps the MAXIMUM multiplicity it has in any single input array (`[2,2,2]` ∪ `[2,2]` → `[2,2,2]`), first-seen order — live-verified. |
| `ARRAY_UNIQUE_AGG` | `ARRAY_UNIQUE_AGG(expr)` | VARIANT | Returns an ARRAY of the DISTINCT non-NULL scalar input values (one element per row). An empty result renders as `[]`. |
| `HASH_AGG` | `HASH_AGG(expr)` | NUMBER | A single order-independent signed 64-bit hash over the group's multiset of rows (per-row `HASH` values summed commutatively), sharing `HASH`'s canonical encoding — so a scaled and an unscaled number aggregate to the same hash. Deterministic within this engine but not equal to Snowflake's proprietary value; an empty group yields 0 (live-verified). |
| `MAX_BY` | `MAX_BY([DISTINCT] value, sort_key [, N])` | VARIANT / ARRAY | Two-argument form: the `value` from the row with the maximum `sort_key` (NULL sort keys ignored; a tie is won by the FIRST row, live-verified). Three-argument form: an ARRAY of up to `N` values ordered by descending `sort_key`; `DISTINCT` dedups the values. |
| `MIN_BY` | `MIN_BY([DISTINCT] value, sort_key [, N])` | VARIANT / ARRAY | Two-argument form: the `value` from the row with the minimum `sort_key` (NULL sort keys ignored; a tie is won by the FIRST row, live-verified). Three-argument form: an ARRAY of up to `N` values ordered by ascending `sort_key`; `DISTINCT` dedups the values. |
| `MODE` | `MODE(expr)` | the input type | The most frequent non-NULL value; NULL over no input. A tie goes to the value seen FIRST — the account documents a tie as "one of them" and hands back whichever value its hash table lists first, which shifts with the family, the plan and the insertion order (`{1.50, 2.50}` is 1.50 in either order, `{2, 4}` the second inserted, a VARIANT `{2, 4}` 4 in either order, a DATE pair the later date), so no rule reproduces it and a tie can only be pinned as membership in the tied set. Equal values share a count however they are carried (1.50 and 1.5, a Long and a BigDecimal). Whole-partition only as a window. |
| `COUNT_IF` | `COUNT_IF(condition)` | NUMBER(13,0) | The rows whose condition is TRUE. The argument must be a BOOLEAN: the account plans the call as `SUM(IFF(CAST(x AS BOOLEAN), 1, 0))` and it is the IFF that refuses anything else, in its own name and at the call — `COUNT_IF(n)` over a NUMBER(10,2) is `Invalid argument types for function 'IFF': (NUMBER(10,2), NUMBER(1,0), NUMBER(1,0))`, a VARCHAR, FLOAT, DATE or VARIANT the same in its type's name — and `COUNT_IF(DISTINCT b)` is `invalid use of 'distinct' for function 'COUNT_IF(DISTINCT T.B)'`. All live-verified. |
| `BOOLOR_AGG` / `BOOLAND_AGG` / `BOOLXOR_AGG` | `BOOLOR_AGG(expr)` | BOOLEAN | OR / AND across the group; XOR is TRUE when EXACTLY ONE input is true (three trues are FALSE). NULLs are skipped, an all-NULL group is NULL. The account plans them over `TO_BOOLEAN(x)`, so a BOOLEAN reads as itself, an exact NUMBER as `x <> 0`, and a FLOAT, DATE / TIME / TIMESTAMP, BINARY, OBJECT or ARRAY is refused while the statement compiles, in the conversion's words and without a position: `invalid type [TO_BOOLEAN(T.F)] for parameter 'TO_BOOLEAN'`. A VARCHAR or VARIANT is read at row time: a text that is no TO_BOOLEAN form is `Boolean value 'x' is not recognized`, a VARIANT member that is not a boolean `Failed to cast variant value 1 to BOOLEAN`. All live-verified. |
| `APPROX_COUNT_DISTINCT` / `HLL` / `APPROXIMATE_COUNT_DISTINCT` | `APPROX_COUNT_DISTINCT([DISTINCT] expr [, expr …])` | NUMBER(18,0) | The approximate number of distinct values — of distinct TUPLES over several arguments, a tuple with any NULL member not counted (over `(1.5, 7), (1.5, NULL), (NULL, 7), (NULL, NULL), (1.5, 7)` the answer is 1); exact for small groups, HyperLogLog beyond. The STATE family below carries the same sketch. Live-verified. |
| `HLL_ACCUMULATE` | `HLL_ACCUMULATE([DISTINCT] expr [, expr …])` | BINARY | The group's HyperLogLog state, countable later rather than now: `HLL_ESTIMATE(HLL_ACCUMULATE(x))` answers exactly what `APPROX_COUNT_DISTINCT(x)` answers, tuples and all. A group with no value at all is an EMPTY state, whose estimate is 0. |
| `HLL_COMBINE` | `HLL_COMBINE(state)` | BINARY | One state standing for the union of the group's states, so a sketch can be built per partition and rolled up afterwards. |
| `HLL_ESTIMATE` | `HLL_ESTIMATE(state)` | NUMBER(18,0) | The cardinality a state stands for. A NULL state answers NULL; an empty one answers 0. |
| `HLL_EXPORT` | `HLL_EXPORT(state)` | OBJECT | The state as an object — `{"precision": p, "sparse": {"indices": […], "maxLzCounts": […]}, "version": 4}` while few registers are set, `{"dense": […], "precision": p, "version": 4}` once many are. |
| `HLL_IMPORT` | `HLL_IMPORT(object)` | BINARY | The state an exported object stands for, read at the precision the object declares — including one Snowflake exported, which is what makes the object the interchange form. A text argument is refused by its type. |
| `LISTAGG` | `LISTAGG([DISTINCT] expr [, delimiter]) [WITHIN GROUP (ORDER BY …)]` | VARCHAR(134217728) | The group's values joined as text, NULLs skipped; a DATE, NUMBER, BOOLEAN, FLOAT or VARIANT joins as its text and a numeric delimiter as its digits. A BINARY value or delimiter is refused at compile time with the argument-type list, both declared widths spelled (`LISTAGG(bin, ',')` is `Invalid argument types for function 'LISTAGG': (BINARY(8388608), VARCHAR(1))`), as an OBJECT or ARRAY is. Live-verified. |
| `APPROX_TOP_K` | `APPROX_TOP_K(expr [, k [, counters]])` | ARRAY | The `k` most frequent non-NULL values (default 1) as `[value, count]` pairs, count descending and the most recently counted first among equals, from a Space-Saving summary of `counters` counters (default 100000): a value already counted increments its counter, a new value arriving when the summary is full takes over the least counter (the least recently touched among equals) and inherits its count plus one — `APPROX_TOP_K(n, 2, 1)` over four values reports the last one with count 4. `[]` over no input; a value keeps its own type inside the pair (a NUMBER renders as a number, a DATE as its text, a VARIANT as itself). Both limits are constants between 1 and 100000, judged at compile time in any integral spelling (`2.0`, `1e1`, `'2'`, `(2)`, `+2`): `Invalid value [0] for function 'APPROX_TOP_K', parameter 1: Number of items must be a positive integer`, `Invalid value [100001] for function 'APPROX_TOP_K', parameter 2: Number of counters cannot be larger than 100000`, `Invalid value [null] …`, `argument 2 to function APPROX_TOP_K needs to be constant, found 'T.I'`, and a non-integral constant gets live's unfilled template verbatim (`Invalid value [CAST(1.5 AS NUMBER(18,0))] for function '{1}', parameter {2}: {3}`, `[TO_NUMBER('x', 18, 0)]` for a string). A whole-partition window (bare, PARTITION BY, unbounded ROWS); an ORDER BY's cumulative and sliding frames are refused like MEDIAN's. All live-verified. |
| `APPROX_TOP_K_ACCUMULATE` | `APPROX_TOP_K_ACCUMULATE(expr, counters)` | OBJECT | The whole summary as a state: `{"counters":10,"datatype":"FIXED","precision":10,"scale":2,"state":[[value, count], …],"type":"approx_top_k"}`, pairs in the ranking order above. The datatype is the input's DECLARED family in the internal vocabulary (`FIXED`, `TEXT`, `REAL`, `DATE`, `BOOLEAN`, `VARIANT`, …) — an expression's own type, so `n + 1` over a NUMBER(10,2) is `FIXED` 11/2 and a literal `1.5` is 2/1 — with a NUMBER's precision and scale and 38/0 for every other family; a state that saw no value reports ONE counter and the 38/0 width whatever was asked, and `MISSING` when nothing types it. Both arguments are required (`not enough arguments for function [APPROX_TOP_K_ACCUMULATE(T.N)], expected 2, got 1`); the limit follows APPROX_TOP_K's rules as parameter 1 ("Number of counters"). |
| `APPROX_TOP_K_COMBINE` | `APPROX_TOP_K_COMBINE(state [, counters])` | OBJECT | Merges states: equal values add their counts with no inheritance, then the summary is trimmed to `counters` (default the inputs' shared limit) by dropping the smallest counts, the most recently touched first. Every input must carry the same limit (`Invalid parameter value: ApproxTopK state. Reason: combining states with different numbers of counters`); the datatype and width are the first state's. NULLs are skipped, nothing seen is the empty `MISSING` state with one counter, and a non-state refuses with `… Reason: invalid type`. |
| `APPROX_TOP_K_ESTIMATE` | `APPROX_TOP_K_ESTIMATE(state [, k])` | ARRAY | A scalar: the first `k` pairs (default 1) of a state, in its ranking. NULL → NULL; a non-state → `Invalid parameter value: ApproxTopK state. Reason: invalid type`; `k` is judged as APPROX_TOP_K's limits are but numbered parameter 1 and called "Number of counters" (`Invalid value [0] for function 'APPROX_TOP_K_ESTIMATE', parameter 1: Number of counters must be a positive integer`), and a column is refused with the unfilled template (`Invalid value [T.K] for function '{1}', parameter {2}: {3}`). |
| `APPROX_PERCENTILE_ACCUMULATE` | `APPROX_PERCENTILE_ACCUMULATE(expr)` | OBJECT | The digest of a numeric input as a state: `{"state":[value, 1.0, value, 1.0, …],"type":"tdigest","version":1}`, values ascending, each followed by its weight, every number a DOUBLE (so `TO_VARCHAR` of the state spells `1.500000000000000e+00`, and a result cell the compact `1.5`). Uncompressed: every value is its own centroid of weight 1, which is what the account returns below roughly a thousand values, where it begins merging neighbours. NULLs are skipped and an empty input is `{"state":[],"type":"tdigest","version":1}`. DATE, TIME, TIMESTAMP, BOOLEAN and BINARY are refused at compile time (`Invalid argument types for function 'APPROX_PERCENTILE_ACCUMULATE': (DATE)`), a VARCHAR or VARIANT at row time when a value spells no number (`Numeric value 'a' is not recognized`, `Failed to cast variant value {"a":1} to REAL`). Whole-partition only as a window. |
| `APPROX_PERCENTILE_COMBINE` | `APPROX_PERCENTILE_COMBINE(state)` | OBJECT | Merges digest states, every centroid kept with its weight and re-sorted by mean. NULLs are skipped, nothing seen is the empty state, and a non-state refuses with `First argument of the function must be an object which maps the key 'state' to an array`. |
| `APPROX_PERCENTILE_ESTIMATE` | `APPROX_PERCENTILE_ESTIMATE(state, percentile)` | FLOAT | A scalar: the value at a fraction of a state. Over centroids that all weigh 1 it is the exact interpolated percentile at position `p × (n − 1)` — the number `APPROX_PERCENTILE` gives over the same values. A state holding any other weight is read as a digest: the target rank `p × total weight` is placed among the centroids' mean positions (`weight before + weight / 2`) and interpolated linearly, extrapolated along the end segments — `[1 ×1, 9 ×3]` is 3 at 0.25, 0.6 at 0.1 and 13.4 at 0.9, a single centroid is its own mean. NULL state, NULL fraction or empty state → NULL; a non-state → the sentence above; the fraction is a constant in [0, 1] (`Invalid value [1.5] for function 'APPROX_PERCENTILE_ESTIMATE', parameter 2: Percentile must be between 0 and 1 inclusive.`, `argument 2 to function APPROX_PERCENTILE_ESTIMATE needs to be constant, found 'T.P'`). All live-verified. |
| `GROUPING_ID` | `GROUPING_ID(e1, …, en)` | NUMBER | The integer bit-vector describing the current ROLLUP/CUBE/GROUPING SETS row: `e1` is the most significant bit, each `ei` is 1 when it is rolled up (aggregated over) and 0 when grouped by. Equivalent to `GROUPING(e1, …, en)` for the same arguments; evaluates to 0 in a plain GROUP BY. |

### What the numeric aggregates DECLARE

Over an input `NUMBER(p, s)`, each family widens differently, and none of the steps follows from the
arithmetic — they are live-measured one width at a time:

| | declared type |
| --- | --- |
| `SUM` | `NUMBER(min(38, p + 12), s)` |
| `AVG` | `NUMBER(min(38, p + 18), s + 6)` |
| `MEDIAN`, `PERCENTILE_CONT(f) WITHIN GROUP (ORDER BY …)` | `NUMBER(min(38, p + 3), s + 3)` |
| `PERCENTILE_DISC(f) WITHIN GROUP (ORDER BY …)`, `MODE` | the input type UNCHANGED |
| `VARIANCE` / `VAR_POP` / `VAR_SAMP` | `NUMBER(38, min(12, 2s + 6))` — the scale SQUARES, then stops |
| `STDDEV` and its kin, `APPROX_PERCENTILE` | `FLOAT`, even over an exact input |
| `LISTAGG` | `VARCHAR(134217728)` |

An approximate input passes straight through as a FLOAT for all of them.

A **VARCHAR or VARIANT** input is a family of its own for the three ordered percentiles. `MEDIAN`,
`PERCENTILE_CONT` and `PERCENTILE_DISC` convert every value to a WHOLE number — `NUMBER(9,0)`,
whatever length the text declares — rounding half away from zero, and order the inputs by their OWN
type (text as text, so `'10'` sorts before `'8'`; a VARIANT member by its kind) before the percentile is
taken between the converted neighbours: `MEDIAN` over `'1.5'` and `'2.25'` is `2.000`, over `'10'`,
`'9'`, `'8'` it is `8.000`. A VARIANT member converts the same way — a boolean as 1 / 0 (`MEDIAN` over `true` and `7` is
`4.000`), a string member spelling no number refused as the cast it is (`Failed to cast variant value "x" to FIXED`)
— and the result is tagged by its declared width alone (`NUMBER(12,3)[SB8]`, `NUMBER(9,0)[SB4]`), never by the
key's statistics. So `MEDIAN` and `PERCENTILE_CONT` declare `NUMBER(12,3)` and `PERCENTILE_DISC`
`NUMBER(9,0)` over any such input, and one value that spells no number or rounds past nine digits refuses
the whole set (`Numeric value 'x' is not recognized`, `Numeric value '1234567890' is out of range`),
whichever position it would take. The computing aggregates (`SUM`, `AVG`, the variance family) take the
same input on the FLOAT tier instead — a VARIANT of 2 and 4 gives the FLOAT `2` for `VARIANCE`, not a scaled
`2.000000` — and `MODE`, `MIN` and `MAX` keep the text, or the VARIANT, as it is.

The split between the two percentiles is about what they RETURN: `PERCENTILE_DISC` hands back an actual
value from the input, so it hands back its type; `PERCENTILE_CONT` interpolates between two of them and
widens exactly as `MEDIAN` does — `MEDIAN` being `PERCENTILE_CONT(0.5)`.

Both percentiles are typed from the **WITHIN GROUP** expression, never from the fraction:
`PERCENTILE_CONT(0)`, `(0.25)` and `(0.123456789)` over the same column all declare the same width, and
the ORDER BY's direction is immaterial. An ORDER BY over an expression types from that expression, so
`WITHIN GROUP (ORDER BY n * 2)` widens whatever `n * 2` declares.

Over a WINDOW every aggregate declares what it declares as an aggregate, with one exception: **`AVG`
forks on the shape of its OVER clause.** A bare `ORDER BY` — the one spelling that leaves the default
RANGE frame in force — gives `AVG` the aggregate's own width, while every other spelling, an explicit
frame included, adds fifteen digits and three decimals instead of eighteen and six.

## FILE functions

Snowflake's `FILE` type and the 16 functions over it. Everything below is live-verified on a real
account (2026-08-03).

A FILE value is an object of file metadata, built by `TO_FILE`. For a real staged file it carries
exactly six fields, alphabetically ordered:

```
{"CONTENT_TYPE":"text/plain","ETAG":"b7dddf722cfdc51710087d369f8d9e6b",
 "LAST_MODIFIED":"Mon, 03 Aug 2026 11:24:13 GMT","RELATIVE_PATH":"hello.txt",
 "SIZE":24,"STAGE":"@DB.SCHEMA.SSE"}
```

`ETAG` is the file's MD5 hex (equal to the `md5` column of `LIST @stage`), `LAST_MODIFIED` is an
RFC-1123 date in GMT with a **zero-padded** day (`LIST` formats the same instant unpadded, so the two
are not interchangeable), and `STAGE` is fully qualified and upper-cased however the caller wrote it
(the user stage renders `@"~"`, a table stage renders just `@TBL`). Frostlake's stages are real
directories, so every field is read from the actual file — nothing is synthesised from the path text.

Three properties drive the family:

- **`CONTENT_TYPE` comes from the file NAME's extension, never from its bytes.** The decisive live
  pair: PNG bytes staged as `png_named.txt` report `text/plain`, and plain text staged as
  `text_named.png` reports `image/png`. An unknown or absent extension is `application/octet-stream`.
- **`FL_GET_FILE_TYPE` and the five `FL_IS_*` classify on `CONTENT_TYPE` alone**, by exact match
  against a CLOSED set — no `image/*` prefix rule, case-sensitive, untrimmed, and MIME parameters are
  not stripped (`IMAGE/PNG`, `" image/png"` and `application/gzip;charset=utf-8` are all `unknown`).
  Membership inside a family is arbitrary and was measured, not derived: `audio/mpeg` is audio but
  `audio/mp4` is not; `text/plain` is a document but `text/tab-separated-values` is not;
  `application/gzip` — what a real `.gz` file gets, so staged gzip files DO classify compressed — is
  in the set but the `application/x-gzip` spelling is not, and neither is
  `application/x-7z-compressed` (a staged `.7z` classifies `unknown`).
- **The sets OVERLAP, so `FL_IS_x(f)` is NOT `FL_GET_FILE_TYPE(f) = 'x'`.** `video/x-msvideo` (an
  `.avi`) is in both the video and the audio set: `FL_IS_VIDEO` and `FL_IS_AUDIO` are both TRUE while
  `FL_GET_FILE_TYPE` answers `video`.

The NULL rule splits the family in two: the eight plain getters propagate NULL, while
`FL_GET_FILE_TYPE(NULL)` is the string `'unknown'` and every `FL_IS_*(NULL)` is FALSE — never NULL.

`FL_GET_SCOPED_FILE_URL` and `FL_GET_STAGE_FILE_URL` are plain descriptor FIELDS. A descriptor that
`TO_FILE` built from a stage path does not carry them, so **both return NULL** — live, that holds even
on a stage created with `DIRECTORY = (ENABLE = TRUE)`. They return a value only when a caller-supplied
metadata object set the field. NULL here is Snowflake's own answer, not an engine shortfall; use
`BUILD_STAGE_FILE_URL` / `BUILD_SCOPED_FILE_URL` (below) if you want a URL.

Writing to a `FILE` column goes through `TO_FILE`, so a stage-path string is resolved against the stage
on write (and fails when it names nothing) while a metadata object is validated but not resolved.
`TO_FILE(...)` inside a `VALUES` clause is rejected by Snowflake; `INSERT … SELECT TO_FILE(...)` works.

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `TO_FILE` | `TO_FILE(stage_and_path)`, `TO_FILE(stage, relative_path)`, `TO_FILE(metadata_object)` | FILE | Builds the file-metadata object. The path form **validates existence** and fails `Remote file '@st/x.txt' was not found. …`; the two-argument form simply joins with `/` (no `@` is added, no doubled separator collapsed); the object form validates structure but does **not** resolve the file. NULL in, NULL out. |
| `TRY_TO_FILE` | same as `TO_FILE` | FILE | Identical, except every failure — missing file, unknown metadata field, missing required field, a `LAST_MODIFIED` that is not RFC-1123, or no stage-and-path/URL identity — yields NULL instead of an error. |
| `FL_GET_CONTENT_TYPE` | `FL_GET_CONTENT_TYPE(file)` | VARCHAR | The MIME type, derived from the file name's extension. |
| `FL_GET_ETAG` | `FL_GET_ETAG(file)` | VARCHAR | The file's MD5 hex. |
| `FL_GET_FILE_TYPE` | `FL_GET_FILE_TYPE(file)` | VARCHAR | `image`, `video`, `audio`, `document`, `compressed` or `unknown`. **Returns `'unknown'` for NULL**, not NULL. |
| `FL_GET_LAST_MODIFIED` | `FL_GET_LAST_MODIFIED(file)` | TIMESTAMP_TZ | When the staged file was last modified. |
| `FL_GET_RELATIVE_PATH` | `FL_GET_RELATIVE_PATH(file)` | VARCHAR | The path within the stage, sub-directories kept, no leading slash. |
| `FL_GET_SCOPED_FILE_URL` | `FL_GET_SCOPED_FILE_URL(file)` | VARCHAR | The descriptor's `SCOPED_FILE_URL` field. **NULL for any file built from a stage path** — see above. |
| `FL_GET_SIZE` | `FL_GET_SIZE(file)` | NUMBER | Size in bytes. |
| `FL_GET_STAGE` | `FL_GET_STAGE(file)` | VARCHAR | The stage, fully qualified and upper-cased (`@DB.SCHEMA.NAME`; `@"~"` for the user stage, `@TBL` for a table stage). |
| `FL_GET_STAGE_FILE_URL` | `FL_GET_STAGE_FILE_URL(file)` | VARCHAR | The descriptor's `STAGE_FILE_URL` field. **NULL for any file built from a stage path** — see above. |
| `FL_IS_AUDIO` | `FL_IS_AUDIO(file)` | BOOLEAN | Content type is an audio type — **including `video/x-msvideo`**. FALSE for NULL. |
| `FL_IS_COMPRESSED` | `FL_IS_COMPRESSED(file)` | BOOLEAN | Content type is an archive type (a staged `.gz` gets `application/gzip`, which is in the set). FALSE for `application/x-gzip` and `application/x-7z-compressed`. FALSE for NULL. |
| `FL_IS_DOCUMENT` | `FL_IS_DOCUMENT(file)` | BOOLEAN | Content type is a text/office/PDF/JSON/XML type. FALSE for NULL. |
| `FL_IS_IMAGE` | `FL_IS_IMAGE(file)` | BOOLEAN | Content type is an image type. FALSE for NULL. |
| `FL_IS_VIDEO` | `FL_IS_VIDEO(file)` | BOOLEAN | Content type is a video type. FALSE for NULL. |

### Stage functions

Six functions take a NAMED stage as their first argument and answer URLs and locations of its files. The stage
is written bare — `BUILD_STAGE_FILE_URL(@st, 'f.csv')`, `@schema.st`, `@db.schema.st`, `@"ST"` — or as a
string literal (`'@st'`, `'@"my stage"'`), or passed in a session variable; the account reads the bare form as
the string `'@st'`, and its refusals echo the call that way (`[BUILD_STAGE_FILE_URL('@st')]`). A bare stage is
taken there and as a positional argument of CALL, which passes it on as the text written — a path, `@~`, `@%t` and
a stage that does not exist included (`CALL p(@st/dir/f.csv)` hands the procedure `'@st/dir/f.csv'`); anything after
it in that argument is a syntax error at the next token (`CALL p(@st || 'x')` at `||`), and a named argument
(`x => @st`) takes none. Everywhere else it is refused, in two phases:

- while the text parses, the whole request refused and none of its statements run — a stage outside every call's
  argument list (`SELECT (@st)`, `1 IN (@st)`), a path after it (`@st/dir`) and a blank inside it (`@ st`,
  `@"my stage"`) are syntax errors at the stage — as a call's argument, a path or a blank after `@` adds a second
  line at the call's parenthesis, and a blank inside a quoted part is first reported as `parse error … near
  '<EOF>'` at the end of the text;
- while its statement compiles, ahead of every name the statement holds and after the statements before it in a
  request of several have run — as an argument of any other call (`UPPER(@st)` → `error line 1 at position 7` /
  `invalid argument for function [UPPER] unexpected argument [@st] at position 0,`, positioned at the call, a
  schema-qualified call named by its own name; the stage function's own second argument likewise). Inside
  `TABLE(…)` the stage is named by its own name alone, a path allowed (`TABLE(FLATTEN(@db.s.st/x))` →
  `[FLATTEN] unexpected argument [ST]`), and a window function that is no aggregate (`LAG(@st) OVER (…)`) is
  placed at `error line 0 at position -1`.

A routine's body is judged when it is created, in the frame it compiles in, its places its own: a SQL UDF's
expression, a table function's query and a masking or row access policy's body inside parentheses of their own —
`Compilation of SQL UDF failed: ` before the refusal and a place on the body's first line moved by one (`AS
'UPPER(@st)'` → `error line 1 at position 1`) — and a scalar UDF's query or block and a procedure's block as a
statement of their own. None of them is created.

All six refuse the same stage arguments the same way, the function's name in each sentence:

| First argument | Refusal |
| --- | --- |
| `NULL`, `''`, a variable holding NULL | `SQL compilation error: Argument 1 to function 'F' cannot be null or empty.` |
| `TRUE`, `1`, a variable holding a number | `SQL compilation error:` / `Argument number 1 for function 'F' needs to be a string literal.` |
| a column, `'@' \|\| 'st'`, `UPPER('@st')`, `NULL::VARCHAR` — anything but a literal or a variable | `SQL compilation error:` / `argument 1 to function F needs to be constant, found 'X'` |
| `'st'`, `'  @st'` | `… does not start with '@'. Please specify stage name as '@<stage_name>'.` |
| `'@'` | `SQL compilation error:` / `missing stage name in URL: @` |
| `'@st/d'`, `'@~/x'` | `… should only contain the stage name and not a path.` |
| `@~`, `@%t` (existing table or not) | `… provides a user or table stage. These stage kinds are not supported by this function.` |
| `'@st.'` | `SQL compilation error:` / `syntax error line 1 at position 3 unexpected '<EOF>'.` |
| `'@.st'`, `'@s..st'` | `SQL compilation error:` / `syntax error line 1 at position 0 unexpected '.'.` (`'@s..st'`: position 2) — at the dot where a name part should begin |
| `@nosuch`, `'@"st"'` for a stage `ST` | `SQL compilation error: Stage '@nosuch' provided to the function 'F' does not exist or is not authorized.` |

The name is resolved exactly (an unquoted part folds to upper case, a quoted one keeps its case) and blanks after
it are allowed; a name has three parts at most (`'@st.x.y.z'` → `syntax error line 1 at position 6 unexpected
'.'.`). Every rule is judged while the statement compiles, so it holds over a table with no rows. A call of the
wrong size is refused by its count, positioned at the call (`not enough arguments for function
[GET_ABSOLUTE_PATH('@st')], expected 2, got 1`), except GET_PRESIGNED_URL of exactly four arguments, which live
refuses with the bare sentence `Invalid number of arguments`; five or more are `too many arguments … expected 4`.
`SHOW FUNCTIONS` lists none of the six, as live lists none.

The account's URLs name its own host and its cloud storage. Frostlake's point at its HTTP server
(`http.host`/`http.port`), which serves them: `/presigned/<token>/<file name>` for GET_PRESIGNED_URL,
`/api/files/…` for the other two URL functions (see `docs/http-api.md`). An internal stage's location is its
engine-managed directory as a `file://` URL ending in a slash, where the account answers a cloud URL of its
own; an external stage's is the URL it was created with.

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `GET_PRESIGNED_URL` | `GET_PRESIGNED_URL(@stage, relative_path [, expiration_seconds])` | VARCHAR | A URL that downloads the staged file without a session until it expires: 3600 seconds by default. The expiry is a numeric literal, negated or not (`-(-60)`), or a session variable holding a whole number from 1 to 604800 (`60.0` and `1e2` are whole); `604801`, `-1`, `0.5`, `'60'`, `TRUE` and `NULL` are refused `GET_PRESIGNED_URL expiry in seconds is invalid. Must be between 0 and 604,800 (1 week).`, zero `Presigned URL expiry in seconds is invalid based on the stage credential type. Must be between 1 and 604,800.`, and a computed one (`60::INT`, `+60`, `30 + 30`) `argument 3 to function GET_PRESIGNED_URL needs to be constant, found '…'` (`CAST(60 AS NUMBER(38,0))`, `UNARY PLUS(60)`). A literal NULL or empty path is refused `Argument 2 to function 'GET_PRESIGNED_URL' cannot be null or empty.`, a numeric one `Argument number 2 … needs to be a string literal.`; a path read from a column answers NULL for NULL. The URL is `/presigned/<token>/<file name>` on the engine's HTTP server, the opaque token signed with a key drawn when the process starts — so a URL outlives neither its expiry nor the process. As in Snowflake it is made whether or not the file exists; fetching a missing file answers `404` with an XML `NoSuchKey` error, an expired URL `403 AccessDenied`. A `..` segment is a name like any other and cannot climb out of the stage. |
| `BUILD_STAGE_FILE_URL` | `BUILD_STAGE_FILE_URL(@stage, relative_path)` | VARCHAR | The file URL of a staged file, which does not expire: `<server>/api/files/<database>/<schema>/<stage>/<path>`. Each name is spelled as SQL spells it — bare when it can be, quoted otherwise (`%22my%20stage%22`) — and every part is percent-encoded in lower-case hex, a slash inside the path too (`dir/f 1.csv` → `dir%2ff%201.csv`); ASCII letters and digits, `-`, `_` and `~` stay as they are, and every other byte of the UTF-8 text is encoded. A dot stays in a path the statement folds (a literal, `'x' \|\| 'y.csv'`, `UPPER('y.csv')`) and is encoded `%2e` in one read from a column, as live spells them. Made whether or not the file exists; a NULL path answers NULL, an empty one the stage's own URL. |
| `BUILD_SCOPED_FILE_URL` | `BUILD_SCOPED_FILE_URL(@stage, relative_path [, use_privatelink_host])` | VARCHAR | A scoped URL of a staged file, `<server>/api/files/<query id>/<number>/<token>`, valid for 24 hours. Every call makes a different URL; the calls of one statement share the query-id segment (a query-id-shaped value of the statement, not the id LAST_QUERY_ID reports). The account encrypts the path into the token; Frostlake signs the file's local path as for GET_PRESIGNED_URL and base64-encodes it. The third argument must be the literal TRUE or FALSE — anything else, a column or an expression included, is refused `BUILD_SCOPED_FILE_URL operation expects valid boolean for privatelink argument.` — and changes nothing, since the engine has no private-link host. A NULL path answers NULL. |
| `GET_STAGE_LOCATION` | `GET_STAGE_LOCATION(@stage)` | VARCHAR | Where the stage keeps its files: an external stage's URL exactly as created (`s3://b/some/path` gains no slash), an internal stage's directory as a `file://` URL ending in a slash. |
| `GET_ABSOLUTE_PATH` | `GET_ABSOLUTE_PATH(@stage, relative_path)` | VARCHAR(n) | The stage's location with the path appended as written — no slash is added, so `s3://b/some/path` and `a.csv` make `s3://b/some/patha.csv`. Nothing is checked against the files. Declared as wide as the location and the path's declared width together (an empty path counts one character); a path of no known width leaves it a bare VARCHAR. A NULL path answers NULL. |
| `GET_RELATIVE_PATH` | `GET_RELATIVE_PATH(@stage, absolute_path)` | VARCHAR | The absolute path with the stage's location taken off its front, compared case-sensitively character by character: a slash the location lacks stays (`/a/b.csv`); the location itself and the empty string answer the empty string. Any other path is refused while the row is read — `Absolute file path 'x' does not belong to stage '@"DB"."SCHEMA"."ST"' whose location is '…'`, a location longer than 50 characters cut to its first 50 and `...`. A NULL path answers NULL. |

### Where a FILE may not go

Snowflake rejects a FILE at COMPILE time in seven positions, and Frostlake now rejects each with the
same message. All live-verified (keys / ordering aggregates / concatenation / casts 2026-08-03; the
text, numeric, aggregate and arithmetic rows 2026-08-04).

| Position | Rejected | Message |
|---|---|---|
| Grouping / sorting keys | `GROUP BY f`, `ORDER BY f`, `OVER (PARTITION BY f)`, `OVER (ORDER BY f)` — including by ordinal (`GROUP BY 1`), by SELECT alias, through a conditional (`GROUP BY IFF(TRUE, f, f)`) and through a derived table or CTE | `Expressions of type FILE cannot be used as GROUP BY keys` (likewise `ORDER BY` / `PARTITION BY`) |
| Ordering aggregates | `MAX(f)`, `MIN(f)`, `MODE(f)` — with `DISTINCT`, `OVER (…)` and inline in `QUALIFY` alike | `Function MAX does not support FILE argument type` |
| Text functions | every argument position of all 52: `LENGTH(f)`, `UPPER(f)`, `SUBSTR(f, 1, 3)`, `REPLACE`, `SPLIT_PART`, `LPAD`, `TRANSLATE`, `REGEXP_*`, `PARSE_URL`, … — including the NUMBER positions (`SPLIT_PART(s, ',', f)`, `LPAD(s, f, '.')`) | `Invalid argument types for function 'LENGTH': (FILE)` — every argument is listed, so the offending position is visible |
| Numeric functions | every argument position of all 42: `ABS(f)`, `ROUND(f, 2)`, `ROUND(n, f)`, `POWER`, `MOD`, `LOG`, the trigonometric family, the `BIT*` family, `HAVERSINE`, `WIDTH_BUCKET`, … | `Invalid argument types for function 'ABS': (FILE)` |
| Summing / ordering-by-value aggregates | `SUM(f)`, `AVG(f)`, `BITAND_AGG` / `BITOR_AGG` / `BITXOR_AGG`, `LISTAGG(f)`, `OBJECT_AGG` in **both** halves; `MEDIAN(f)` and `PERCENTILE_CONT` / `PERCENTILE_DISC … WITHIN GROUP (ORDER BY f)`; the moment family `STDDEV` / `VARIANCE` / `VAR_POP` / `VAR_SAMP` / `SKEW` / `KURTOSIS` / `REGR_R2`. Windowed forms too. | Three sentences: `Invalid argument types for function 'SUM': (FILE)`; `incompatible types: [FILE] and [NUMBER(9,0)]` for MEDIAN and the percentiles; `Invalid argument types for function '*': (FILE, FILE)` for the moment family, which never names itself |
| Concatenation and arithmetic | `'x' \|\| f`, `f \|\| f`, `CONCAT(f, 'x')`, `CONCAT_WS('-', 'x', f)`; `f + 1`, `1 + f`, `f - 1`, `f * 2`, `f / 2`, `f % 2`, `-f` | `Invalid argument types for function '\|\|': (VARCHAR(1), FILE)` — `CONCAT_WS` reports itself as `CONCAT`; arithmetic names the operator symbol, and unary minus names `NEGATE` |
| Casts *from* FILE | every target: `f::VARCHAR`, `CAST(f AS NUMBER/OBJECT/VARIANT/ARRAY/BINARY/DATE/…)`, `TRY_CAST`, and the `TO_*` functions | `invalid type [CAST(F AS VARCHAR)] for parameter 'TO_VARCHAR'` — the parameter names the conversion the TARGET implies (`FLOAT` → `TO_DOUBLE`, `TIMESTAMP` → `TO_TIMESTAMP_NTZ`) |

Casts *to* FILE are rejected too (`NULL::FILE` → `invalid type [CAST(NULL AS FILE)] for parameter
'TO_FILE'`); `TO_FILE(f)` over an existing FILE is fine. `OBJECT_CONSTRUCT(f, 1)` is rejected as
`Function OBJECT_CONSTRUCT does not support FILE argument type for keys`, while
`OBJECT_CONSTRUCT('a', f)` nests the file quite happily.

Each rejection reads the **declared** type and fires at plan time, so it holds through a derived table,
a CTE and a conditional over FILE branches, and over an empty input.

**Everything else still works**, and deliberately so — every rule keys off the FILE type itself, never
"semi-structured", and the two families genuinely diverge in both directions: `ARRAY_AGG(f)` is
accepted where `ARRAY_AGG` of a *structured* value is not, and `OBJECT_AGG`'s value half takes an
OBJECT while refusing a FILE. (The overlap with [Where an OBJECT or ARRAY may not
go](#where-an-object-or-array-may-not-go) is the text, numeric, summing, arithmetic and
ordering-aggregate rows, which use the identical sentences with the type name swapped.)
Live-accepted over a FILE: `f = f` and the ordering comparisons, `IS NULL`, `SELECT DISTINCT f`, `COUNT(f)`,
`COUNT(DISTINCT f)`, `ANY_VALUE(f)`, `ARRAY_AGG(f)`, `ARRAY_UNIQUE_AGG(f)`, `HASH_AGG(f)`,
`MAX_BY(f, n)` / `MIN_BY(n, f)`, `APPROX_COUNT_DISTINCT(f)`, `HASH(f)`, `COUNT(f) OVER ()`,
`FIRST_VALUE(f)` / `LAG(f)`, `JOIN ON a.f = b.f`, `UNION` / `INTERSECT` / `MINUS`, `f IN (…)`,
`IFF` / `COALESCE` / `NVL` / `GREATEST` / `LEAST` / `CASE` over FILE branches, and
`ARRAY_CONSTRUCT(f)` / `OBJECT_CONSTRUCT('k', f)`.

Still **not** reproduced, each measured live but left to a later change: the digest and encoding
functions (`MD5(f)`, `SHA1(f)`, `SHA2(f)`, `HEX_ENCODE(f)`, `BASE64_ENCODE(f)`, `COMPRESS`, `ENCRYPT`
— all `Invalid argument types for function 'MD5': (FILE)`, and all of which reject an OBJECT the same
way, so declaring them changes the OBJECT surface too); the date/time functions (`DATEADD`,
`DATEDIFF`, `DATE_TRUNC(day, f)` → `Function DATE_TRUNC does not support FILE argument type`,
`YEAR(f)` → `Function EXTRACT does not support FILE argument type`); and the boolean positions
(`NOT f`, `f AND TRUE`, `IFF(f, 1, 2)`, `WHERE f` → `Invalid data type [FILE] for predicate [FT.F]`,
`f LIKE 'x%'`, `BOOLAND_AGG(f)` → `invalid type [TO_BOOLEAN(FT.F)] for parameter 'TO_BOOLEAN'`).

### Where an OBJECT or ARRAY may not go

Snowflake will not ORDER a semi-structured value. Live-verified 2026-08-03 over a populated OBJECT
column and ARRAY column:

| Position | Rejected | Message |
|---|---|---|
| Ordering aggregates | `MAX(o)`, `MIN(o)`, `MODE(o)` and the ARRAY forms — with `DISTINCT` and `OVER (…)` alike | `Function MAX does not support OBJECT argument type` (SQLSTATE 22000); a structured column names its whole type, `OBJECT(x VARCHAR(16777216))` |
| The `\|\|` operator | `'x' \|\| o`, `o \|\| 'x'`, `o \|\| o`, and the ARRAY forms | `Invalid argument types for function '\|\|': (VARCHAR(1), OBJECT)` (SQLSTATE 42P13, code 1044) |
| Text-joining functions | `CONCAT('x', o)`, `CONCAT_WS(',', s, o)`, `LISTAGG(o)`, `LISTAGG(s, o)` — `DISTINCT` and `WITHIN GROUP` alike | `Invalid argument types for function 'CONCAT': (VARCHAR(1), OBJECT)`; `CONCAT_WS` reports itself as `CONCAT` |
| The scalar string family | `UPPER(o)`, `LOWER`, `LENGTH`, `LEN`, `OCTET_LENGTH`, `TRIM` / `LTRIM` / `RTRIM`, `LPAD` / `RPAD`, `SUBSTR`, `LEFT` / `RIGHT`, `REPLACE`, `TRANSLATE`, `SPLIT` / `SPLIT_PART` / `STRTOK` / `STRTOK_TO_ARRAY`, `CONTAINS` / `STARTSWITH` / `ENDSWITH` / `CHARINDEX` / `POSITION`, `INITCAP`, `REVERSE`, `REPEAT`, `SOUNDEX` / `SOUNDEX_P123`, `EDITDISTANCE`, `JAROWINKLER_SIMILARITY`, `ASCII` / `CHAR` / `CHR` / `UNICODE`, `SPACE` / `RANDSTR` / `NORMALIZE`, `PARSE_URL` / `PARSE_IP` / `TRY_PARSE_IP`, `TRY_VALIDATE_UTF8` / `VALIDATE_UTF8`, `INSERT`, `BIT_LENGTH`, `RTRIMMED_LENGTH`, and the whole `REGEXP_*` family | `Invalid argument types for function 'UPPER': (OBJECT)` — the message lists **every** argument's type |
| Summing aggregates | `SUM(o)`, `AVG(o)`, `BITAND_AGG` / `BITOR_AGG` / `BITXOR_AGG`, and the ARRAY forms — `DISTINCT` and `OVER (…)` alike | `Invalid argument types for function 'SUM': (OBJECT)` (SQLSTATE 42P13, code 1044) |
| Ordering-by-value aggregates | `MEDIAN(o)`, `PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY o)`, `PERCENTILE_DISC(…)` | `incompatible types: [OBJECT] and [NUMBER(9,0)]` (SQLSTATE **42846**, code 1010) — a different sentence *and* a different SQLSTATE |
| Moment aggregates | `STDDEV` / `STDDEV_POP` / `STDDEV_SAMP`, `VARIANCE` / `VARIANCE_POP` / `VARIANCE_SAMP`, `VAR_POP` / `VAR_SAMP`, `SKEW`, `KURTOSIS`, `REGR_R2` | `Invalid argument types for function '*': (OBJECT, OBJECT)` — the aggregate never names **itself**; it reaches its internal sum of squares first, and the one offending argument is listed **twice** |
| The scalar numeric family | `ABS(o)`, `CEIL` / `FLOOR` / `ROUND` / `TRUNC`, `SQRT` / `CBRT` / `SQUARE`, `EXP` / `LN` / `LOG` / `POWER`, `MOD`, `SIGN`, `FACTORIAL`, `DEGREES` / `RADIANS`, `ACOS` / `ASIN` / `ATAN` / `ATAN2` / `COS` / `SIN` / `TAN` / `COT` and the hyperbolics, `HAVERSINE`, `UNIFORM`, `WIDTH_BUCKET`, `DIV0`, `ZEROIFNULL`, `BITAND` / `BITOR` / `BITXOR` / `BITNOT` / `BITSHIFTLEFT` / `BITSHIFTRIGHT` / `GETBIT` | `Invalid argument types for function 'ABS': (OBJECT)` — same shape as the string family |
| Arithmetic operators | `o + 1`, `1 + o`, `o - 1`, `o * 2`, `o / 2`, `o % 2`, `o + o`, `d + o`, `s + o`, `-o` | `Invalid argument types for function '+': (OBJECT, NUMBER(1,0))`; unary minus reports itself as `'NEGATE'` |

`UPPER(o)` is the case that made this worth fixing: Frostlake did not merely stringify it, it
uppercased the JSON **keys** and answered `{"K":"V1"}` — a corrupted structure that still read as an
object. `SUM(o)` is the other: it answered `0.0`, a plausible number a caller may go on to sum,
average or compare, and worse than an error for exactly that reason.

**The message is not one message.** Three shapes were measured, and they disagree on the sentence, the
SQLSTATE and the vendor code alike, so they cannot be parameterised into one — see the table above.

**Every argument position refuses one, not merely the string- or number-shaped ones.** Live rejects
`SPLIT_PART(o, ',', 1)`, `SPLIT_PART(s, o, 1)` and `SPLIT_PART(s, ',', o)` alike, the same for
`LPAD(o, 20, '.')` / `LPAD(s, o, '.')` / `LPAD(s, 20, o)`, and the same for `ROUND(o, 2)` /
`ROUND(n, o)`, `ATAN2(o, 1)` / `ATAN2(1, o)` and `WIDTH_BUCKET(1, o, 10, 3)`.

One call can use **two** shapes at once: `PERCENTILE_CONT(o) WITHIN GROUP (ORDER BY n)` is
`Invalid argument types for function 'PERCENTILE_CONT': (OBJECT)` — the fraction is an ordinary
argument — while `PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY o)` is
`incompatible types: [OBJECT] and [NUMBER(9,0)]`, because the clause carries the value being
accumulated. The `NUMBER(9,0)` is a **constant**: `PERCENTILE_CONT(0.9)` and
`PERCENTILE_DISC(0.9999)` name exactly the same type, so it is not derived from the fraction.

Neighbours deliberately stay **accepted**, each measured: `SEARCH(o, 'k')` takes semi-structured data
on purpose (it returned `TRUE` live), `ARRAY_TO_STRING(a, ',')` takes an ARRAY in position 0 while
refusing one in position 1, and `REGR_COUNT(o, n)` / `REGR_AVGX(o, n)` / `REGR_SXX(o, n)` accept an
OBJECT in the `y` position they never read.

Which positions a function constrains, and **which of the three shapes** it uses, is declared by the
function itself (`BuiltInFunction.semiStructuredRejection`), so a function's **aliases** are covered
for free — `SUBSTR` and `SUBSTRING` are one registered object, and so are `VARIANCE_POP` and
`VAR_POP`. A function that declares nothing constrains nothing, so the rule only ever covers what has
been measured against a live account.

One measured divergence is deliberately not copied: Snowflake reports the name it **desugars** to
rather than the one written — `LEFT(o, 1)` reports `'SUBSTR'`, `REPEAT(o, 2)` reports `'LENGTH'`,
`SPACE(o)` reports `'LPAD'` with a rewritten argument list, `DIV0NULL(o, 2)` reports `'DIV0'` with a
widened second argument, `DATEADD(day, o, d)` reports `'DATE_ADDDAYSTODATE'`, and `s || s || o` lists
three flattened operand types where Frostlake's binary parse lists two. Frostlake reports the function
actually written with the arguments actually written. `AVG(o)` is the same case and live is itself
inconsistent about it: the bare form reports `'SUM'` (an average desugars to a sum over a count) while
`AVG(DISTINCT o)` and `AVG(o) OVER (…)` report `'AVG'`, so reporting the written name matches live in
two of its three forms.

The rule reads the **declared** type, never the runtime value, and that distinction is the whole shape
of it:

- `MAX(v)` over a **VARIANT is accepted even when the VARIANT holds an object** — live, `MAX(vo)`
  returns `{"x": 2}` and `MAX(va)` returns `[1,2]`.
- The same value cast is rejected: `MAX(vo::OBJECT)` fails, while `MAX(o::VARIANT)` succeeds. The same
  holds numerically: `SUM(v::OBJECT)`, `MEDIAN(v::OBJECT)` and `STDDEV(v::OBJECT)` each reject in
  their own shape.
- For the numeric family the VARIANT case is **not simply accepted** — it is accepted at *compile*
  time and fails at *run* time when the VARIANT actually holds an object (`SUM(vo)` is
  `Failed to cast variant value {"x":1} to REAL` live, `MEDIAN(vo)` names `FIXED`). Those are
  different outcomes and both are correct; turning the runtime case into a compile rejection would be
  over-broad. `SUM(o::VARIANT)` is the same story — the cast moves the failure, it does not remove it.
- It fires at compile time, so `MAX(o) FROM t WHERE 1 = 0` rejects on an empty input.
- A structured **MAP** counts as semi-structured throughout, even though `MapType` is deliberately not
  an `ObjectType` subclass: `MAX(sm)`, `'x' || sm`, `UPPER(sm)`, `SUM(sm)`, `MEDIAN(sm)` and `-sm` all
  reject and all name the whole `MAP(VARCHAR(16777216), NUMBER(38,0))` type.
- A conditional over semi-structured branches *is* semi-structured (`SYSTEM$TYPEOF(IFF(TRUE, o, o))`
  reports OBJECT), so `MAX(IFF(TRUE, o, o))`, `MAX(COALESCE(o, o))`, `MAX(GREATEST(o, o))` and
  `MAX(CASE WHEN … THEN o ELSE o END)` all reject.
- The constructor family is statically OBJECT / ARRAY rather than VARIANT — `OBJECT_CONSTRUCT`,
  `OBJECT_INSERT`, `TO_OBJECT`, `ARRAY_CONSTRUCT`, `ARRAY_CAT`, `SPLIT`, `OBJECT_KEYS`, `TO_ARRAY`
  and the rest — so aggregating their results rejects too. `PARSE_JSON`, `TO_VARIANT`, colon-path
  access and array indexing all report VARIANT and stay accepted.

**Everything else still works.** The family SPLITS rather than dividing on "semi-structured", so only
these three functions reject. Live-accepted over an OBJECT and an ARRAY alike: `GROUP BY o`,
`ORDER BY o`, `OVER (PARTITION BY o)`, `SELECT DISTINCT o`, `COUNT(o)`, `COUNT(DISTINCT o)`,
`ANY_VALUE(o)`, `ARRAY_AGG(o)`, `ARRAY_UNIQUE_AGG(o)`, `OBJECT_AGG(k, o)`, `HASH_AGG(o)`,
`APPROX_COUNT_DISTINCT(o)`, `MAX_BY(o, id)` / `MIN_BY(o, id)`, `FIRST_VALUE(o)` / `LAG(o)`,
`o = o`, `JOIN ON a.o = b.o`, `o IN (…)`, `UNION` / `INTERSECT` / `MINUS`, and
`CAST(o AS VARCHAR)` / `TO_VARCHAR(o)` / `TO_CHAR(o)`. Several of those acceptances are the **plain**
types' alone — see [Where a STRUCTURED type may not
go](#where-a-structured-type-may-not-go-although-a-plain-object-or-array-may).

**An explicit conversion is legal where implicit coercion is not** — the trap this rule must not fall
into. `CAST(o AS VARCHAR)`, `o::VARCHAR`, `TO_VARCHAR(o)`, `TO_CHAR(o)` and `TO_JSON(o)` all produce
VARCHAR live, and the result then flows into the whole family: `UPPER(o::VARCHAR)` returns
`{"K":"V1"}` and `TO_VARCHAR(o) || 'x'` returns `{"k":"v1"}x`. Reading *into* the value works the same
way — `o:k`, `a[0]` and `GET(o, 'k')` all report VARIANT, so `UPPER(o:k)` is accepted.

Live rejections **not** yet reproduced (tracked as follow-up work):

- The **desugared-plan** message shape, where live reports an internal rewrite Frostlake does not
  have: `CORR(o, n)` / `COVAR_POP` / `COVAR_SAMP` / `REGR_SLOPE` /
  `REGR_INTERCEPT` / `REGR_AVGY` / `REGR_SYY` are `invalid type [TO_DOUBLE(IFF(T.N IS NULL,
  SYSTEM$NULL_TO_OBJECT(NULL), T.O))] for parameter 'TO_DOUBLE'`. `REGR_SXY(o, n)` reports
  `'*': (NUMBER(38,0), OBJECT)` — the operands in the order the internal product uses them, not the
  offending type twice — and `APPROX_PERCENTILE(o, 0.5)` reports
  `'APPROX_PERCENTILE_ACCUMULATE'`.
- The conversion family, which rejects with the same "invalid type … for parameter" shape:
  `TO_NUMBER(o)`, `TO_DOUBLE(o)`, `TO_DECIMAL(o)`, `TO_BOOLEAN(o)`, `TO_DATE(o)`,
  `CAST(o AS NUMBER)` / `o::NUMBER`, and `TO_CHAR(n, o)` (`Format argument for function 'TO_CHAR'
  needs to be a string`).
- The **type-unification** shape, where the OBJECT wins and the *other* argument is what gets
  rejected: `LEAST(o, n)` and `NVL(o, 1)` are `Can not convert parameter 'T.N' of type
  [NUMBER(38,0)] into expected type [OBJECT]`, and `NULLIFZERO(o)` rejects its implicit `0` the same
  way.
- Unary **plus** is a node of its own and refuses like the minus, under its own name: `+o` is
  `Invalid argument types for function 'UNARY PLUS': (OBJECT)`, positioned at the sign; `+'5'` reads
  the text as a FLOAT 5, exactly as `-'5'` does.
- Positions the plan-time walk does not reach: a `WHERE` predicate (`WHERE o + 1 > 0`) and a
  `HAVING` clause (`HAVING SUM(o) > 0`) still fail at run time with Frostlake's own message rather
  than at compile time with Snowflake's.
- The **runtime** half of the VARIANT story: `SUM(vo)` over a VARIANT holding an object answers
  `0.0` here where live fails `Failed to cast variant value {"x":1} to REAL`. The compile-time rule
  correctly leaves VARIANT alone; what remains is the accumulator's own coercion.
- The pattern-matching **operator** forms (`o LIKE '%k%'`, `ILIKE`, `RLIKE`, `REGEXP`, `LIKE ANY`),
  which are parsed as operators rather than function calls and are validated only in predicate
  positions.
- The hash / encoding / crypto / parse family (`MD5(o)`, `SHA1`, `SHA2`, `HEX_ENCODE`,
  `BASE64_ENCODE`, `BASE64_DECODE_STRING`, `ENCRYPT`, `COMPRESS`, `PARSE_JSON(o)`,
  `TRY_PARSE_JSON`, `PARSE_XML`, `CHECK_JSON`), and `ARRAY_TO_STRING(a, o)` in its separator
  position.
- The other types live refuses in the same positions — BINARY (`UPPER(bin)`, `'x' || bin`,
  `CONCAT('x', bin)`, while `LENGTH(bin)` / `SUBSTR(bin, 1, 1)` / `CONCAT(bin, bin)` stay accepted
  through their BINARY overloads); and BOOLEAN / DATE, which live refuses from `SUM` itself
  (`SUM(b)` is `'SUM': (BOOLEAN)`, `SUM(d)` is `'SUM': (DATE)`) — a strictness rule of its own, not
  this one.

### Where a STRUCTURED type may not go, although a plain OBJECT or ARRAY may

`OBJECT` and `OBJECT(x VARCHAR)` are **different types** in Snowflake, and they diverge: a structured
value is refused in places the plain semi-structured one sails through. Everything in the section
above applies to a structured value as well (naming its whole parameterised type — `MAX(so)` is
`Function MAX does not support OBJECT(x VARCHAR(16777216)) argument type`); what follows is the
**extra** refusal it collects. All live-verified 2026-08-04 over populated `OBJECT(x VARCHAR)`,
`ARRAY(INT)` and `MAP(VARCHAR, INT)` columns sitting beside plain `OBJECT` / `ARRAY` / `VARIANT` ones
— each of the three kinds probed separately, because they were not assumed to agree.

| Position | Rejected for a STRUCTURED value | Accepted for a plain OBJECT / ARRAY | Message |
|---|---|---|---|
| Cast to text | `CAST(so AS VARCHAR)`, `so::VARCHAR`, `CAST(so AS TEXT/STRING/NVARCHAR/CHAR(5)/VARCHAR(20))`, `TRY_CAST(so AS VARCHAR)` | `CAST(o AS VARCHAR)` → `{"k":"v1"}` | `invalid type [CAST(SO AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'` (SQLSTATE **22023**, code 1007) — a TRY_CAST renders **without** its target, `[TRY_CAST(SO)]` |
| The text conversions | `TO_VARCHAR(so)`, `TO_CHAR(so)` | `TO_VARCHAR(o)`, `TO_CHAR(a)` | `invalid type [TO_VARCHAR(SO)] for parameter 'TO_VARCHAR'` — the parameter is the name as **written**, so `TO_CHAR` reports `'TO_CHAR'` |
| Collecting aggregates | `ARRAY_AGG(so)` — `DISTINCT`, `WITHIN GROUP`, `OVER ()`, `GROUP BY` and an empty input alike — `ARRAY_UNIQUE_AGG(so)`, `OBJECT_AGG(k, so)` | `ARRAY_AGG(o)`, `ARRAY_UNIQUE_AGG(o)`, `OBJECT_AGG(k, o)` | `Invalid argument types for function 'ARRAY_AGG': (OBJECT(x VARCHAR(16777216)))` (SQLSTATE 42P13) |
| The VARIANT-reading scalars | `TO_JSON(so)`, `TO_XML(so)`, `TYPEOF(so)`, the whole `IS_*` family, `AS_VARCHAR` / `AS_CHAR` / `AS_BOOLEAN` / `AS_DATE` / `AS_TIME` / `AS_BINARY` / `AS_DOUBLE` / `AS_REAL` / `AS_OBJECT` / `AS_ARRAY`, `ARRAY_TO_STRING(sa, ',')` | every one of them over `o` / `a` | `Invalid argument types for function 'TO_JSON': (OBJECT(x VARCHAR(16777216)))` |
| The constructors | `ARRAY_CONSTRUCT(so)` in **any** position, `ARRAY_CONSTRUCT_COMPACT`, `OBJECT_CONSTRUCT('a', so)`, `OBJECT_CONSTRUCT_KEEP_NULL` | `ARRAY_CONSTRUCT(1, o)`, `OBJECT_CONSTRUCT('a', o)` | `Function ARRAY_CONSTRUCT does not support OBJECT(x VARCHAR(16777216)) argument type` (SQLSTATE 22000) — a structured **key**, `OBJECT_CONSTRUCT(so, 1)`, appends ` for keys` and carries code 2270 |

**The KEY positions are NOT part of this**, and that is the assumption most worth resisting. Live
accepts, for all three structured kinds: `GROUP BY so`, `ORDER BY so`, `SELECT DISTINCT so`,
`OVER (PARTITION BY so)`, `OVER (ORDER BY so)`, `UNION` / `INTERSECT` / `MINUS`,
`JOIN ON a.so = b.so`, `so IN (…)`, `QUALIFY`, `COUNT(so)`, `COUNT(DISTINCT so)`,
`APPROX_COUNT_DISTINCT(so)`, `ANY_VALUE(so)`, `HASH(so)` / `HASH_AGG(so)`, `MAX_BY(so, n)` /
`MIN_BY(so, n)`, and `FIRST_VALUE(so)` / `LAG(so)`. The **accessors** are untouched too: `GET`,
`GET_PATH`, `so:x`, `so['x']`, `sa[0]`, `OBJECT_KEYS`, `OBJECT_INSERT` / `OBJECT_DELETE` /
`OBJECT_PICK`, `ARRAY_SIZE` / `ARRAY_APPEND` / `ARRAY_CAT` / `ARRAY_SORT` / `ARRAY_SLICE` /
`ARRAY_COMPACT` / `ARRAY_DISTINCT` / `ARRAY_MIN`, `MAP_KEYS` / `MAP_SIZE`, `FLATTEN` and
`SYSTEM$TYPEOF`.

That accessor sentence holds for the structured **OBJECT** and **ARRAY**; a **MAP** is narrower than it
reads, and the difference cuts both ways (measured 2026-08-04 over one table carrying an
`OBJECT(x INT)` column and a `MAP(VARCHAR, INT)` column). `MAP_KEYS` / `MAP_SIZE` take a MAP and refuse
a structured OBJECT — `MAP_KEYS(so)` is `Invalid argument types for function 'MAP_KEYS':
(OBJECT(x NUMBER(38,0)))` — while `OBJECT_KEYS` runs the other way: `OBJECT_KEYS(so)` returns `["x"]`
and `OBJECT_KEYS(m)` is an argument-type error. `ARRAY_SIZE` refuses **both** (`ARRAY_SIZE(so)`,
`ARRAY_SIZE(m)`). `GET`, `m['k']` and `m:k` do read a MAP.

**An explicit conversion is the way out**, and it really changes the answer rather than dressing it
up: `so::VARIANT`, `so::OBJECT` and `TO_VARIANT(so)` are all legal, and every rejected call above
succeeds over the converted value — `ARRAY_AGG(so::VARIANT)`, `TO_JSON(so::OBJECT)`,
`CAST(so::VARIANT AS VARCHAR)`. Casting to a **structured** target stays legal too
(`CAST(so AS OBJECT(x VARCHAR))`, `CAST(sa AS ARRAY(INT))`, `CAST(sm AS MAP(VARCHAR, INT))`); only the
TEXT targets refuse.

`TRY_CAST` is the way out as well, but through a **narrower door**, and the two spellings genuinely
disagree. `TRY_CAST` rejects a semi-structured source for every scalar target, which is why it is
listed above — but a semi-structured **target** it accepts, strictly by family (live-verified
2026-08-04 over all six shapes, one statement at a time):

| `TRY_CAST` target | Accepted sources | Refused |
|---|---|---|
| `VARIANT` | every semi-structured one — `o`, `a`, `v`, `so`, `sa`, `sm` | every scalar one: `TRY_CAST(s AS VARIANT)`, `(n …)`, `(b …)`, `(d …)` |
| `OBJECT` | the OBJECT family — plain `OBJECT`, `OBJECT(…)`, `MAP(…)` | `TRY_CAST(a AS OBJECT)` (`'TO_OBJECT'`), `(sa …)` (`Unsupported data type 'STRUCTURED_ARRAY'.`), `(v …)` (the TRY_CAST-arguments sentence) |
| `ARRAY` | the ARRAY family — plain `ARRAY`, `ARRAY(…)` | `TRY_CAST(o AS ARRAY)` and `(v …)` (TRY_CAST-arguments), `(so …)` / `(sm …)` (`Unsupported data type 'STRUCTURED_OBJECT'.` / `'MAP'`) |

A **VARIANT** source is the sharp edge and reaches only a `VARIANT` target — measured on both a column
and a `PARSE_JSON` expression, so it is about the type and not about how the value arrived. The
accepted casts return the value **unchanged**: `TRY_CAST(o AS VARIANT) = o` is TRUE and `TYPEOF` still
reports what went in. Failure still yields NULL rather than an error
(`TRY_CAST(OBJECT_CONSTRUCT('x','notanumber') AS OBJECT(x INT))` → NULL).

Plain **`CAST` and `::` are looser** over the very same pairs, and cannot share this rule:
`CAST(o AS ARRAY)` wraps into `[{"k":"v1"}]` and `CAST(v AS OBJECT)` succeeds, where both `TRY_CAST`
spellings error. `CAST` and `::` agreed with each other on all eighteen pairs probed; `TRY_CAST` did
not join them.

The whole parameterised type is named, in every shape, and the shapes were measured one at a time: the
zero-field `OBJECT()`, a multi-field `OBJECT(x NUMBER(38,0), y VARCHAR(16777216))`, a nested
`ARRAY(OBJECT(x NUMBER(38,0)))`, a `MAP(VARCHAR(16777216), ARRAY(NUMBER(38,0)))`, and a `NOT NULL`
field (`OBJECT(x VARCHAR(16777216) NOT NULL)`). Like the rule above it reads the **declared** type
through derived tables, CTEs, views, `IFF` / `COALESCE` / `CASE`, and a producing `CAST` — and fires
at compile time, so an empty input still rejects. Which positions a function constrains is declared by
the function itself (`BuiltInFunction.structuredRejection`, consulted only where
`semiStructuredRejection` says nothing), so **aliases come free**: `IS_CHAR` is the `IS_VARCHAR`
object, `AS_CHAR` the `AS_VARCHAR` one, and `TO_VARCHAR` the `TO_CHAR` one.

Neighbours deliberately stay **out** of this rule because they are not divergences — they refuse a
plain OBJECT too, and so belong to the section above if they are ever implemented: `PARSE_JSON`,
`CHECK_JSON`, `CHECK_XML`, and `AS_INTEGER` / `AS_DECIMAL` / `AS_NUMBER` / `AS_TIMESTAMP_*`, which use
a different sentence again (`invalid type [ARRAY] for parameter 'AS_INTEGER(variantValue...)'`).

Live rejections **not** yet reproduced here:

- The scalar **cast targets**, which reject for a plain OBJECT and a structured one alike:
  `CAST(so AS NUMBER/FLOAT/BOOLEAN/DATE/TIME/TIMESTAMP/BINARY/GEOGRAPHY)` and the matching `TO_*`
  functions all give `invalid type [CAST(ST.SO AS NUMBER(38,0))] for parameter 'TO_NUMBER'`. Not a
  divergence, so it is listed with the conversion family above.
- `CAST(so AS ARRAY)` / `CAST(sa AS OBJECT)` / `CAST(sm AS ARRAY)`, which are a cross-family cast:
  `Unsupported data type 'STRUCTURED_OBJECT'.` (SQLSTATE 42601) — naming the FAMILY, except that a MAP
  names `'MAP'`.
- The `TRY_CAST` **over-acceptances** that the family table above leaves standing, all measured
  2026-08-04. Frostlake is too LOOSE here, which is the opposite direction from the rest of this list:
  a MAP source reaches a scalar target (`TRY_CAST(sm AS NUMBER)` → live `'TO_NUMBER'`) and a plain
  ARRAY target (`TRY_CAST(sm AS ARRAY)` → live `Unsupported data type 'MAP'.`), because MAP is not in
  the source rule's reject list at all; a VARCHAR source reaches every semi-structured target
  (`TRY_CAST(s AS VARIANT/OBJECT/ARRAY)`); and a **structured** target takes any source at all, where
  live refuses the cross-family ones with `incompatible types: [ARRAY] and [STRUCTURED_OBJECT]`,
  `[STRUCTURED_ARRAY] and [MAP]`, `[TEXT] and [STRUCTURED_OBJECT]`. Each needs its own live sentence,
  so none is a one-line addition to the reject list.
- `ARRAY_CONTAINS` / `ARRAY_POSITION` over a structured array, which live answers with a THIRD shape
  keyed on the other argument (`Invalid type [VARIANT] for function 'ARRAY_CONTAINS' at position 1`,
  code 1039) — it is the structured overload's element type complaining, not the array's.
- The MAP family's **key- and value-type** checks, which need the map's declared `MAP(k, v)`
  parameters and not merely its family. Live refuses a key whose type is not the map's —
  `MAP_CONTAINS_KEY(1, <MAP(VARCHAR,INT)>)` and `MAP_CONTAINS_KEY('1', <MAP(NUMBER,VARCHAR)>)` are both
  `Function MAP_CONTAINS_KEY cannot be used with arguments of types … and …` (SQLSTATE 22023, code
  1065), as are `MAP_DELETE(m, 1)`, `MAP_DELETE(m, ARRAY_CONSTRUCT('a','b'))` and `MAP_PICK(m, 1)` —
  and refuses a `MAP_CAT` whose two value types differ (`Invalid argument types for function
  'MAP_CAT': (MAP(…NUMBER…), MAP(…VARCHAR…))`). Frostlake accepts all of them. Reproducing this needs
  the inferencer to carry structured type PARAMETERS through an expression, which it does not yet do
  (`SYSTEM$TYPEOF` of a MAP reports plain `OBJECT`), and a half-rule keyed only on the cases where the
  parameters happen to be known would falsely reject the ones where they are not.

### What the MAP family does not model

Frostlake stores a MAP as an OBJECT, so its **keys are member names** — text. Two consequences, both
measured live 2026-08-04 and both left standing:

- A **NUMBER-keyed** map reads back with string keys: `MAP_KEYS(MAP_CONSTRUCT(3,'c',1,'a',2,'b'))` is
  `["1","2","3"]` where live gives `[1,2,3]` (live renders the map itself with string keys either way,
  `{"1":"a","2":"b","3":"c"}`, so only the extracted keys differ).
- Values are **not unified to one type**. Live derives a single value type from the arguments and
  coerces to it, so `MAP_CONSTRUCT('a',1,'b','x')` types as `MAP(VARCHAR(1), NUMBER(18,5))` and fails
  at run time with `Numeric value 'x' is not recognized`; the same is true of `MAP_INSERT(<MAP(…,INT)>,
  'b', 'x')`. Frostlake keeps each value as written.

### Where a GEOGRAPHY or GEOMETRY may not go

`GEOGRAPHY` and `GEOMETRY` are **domain** types: they coerce to nothing. Not to the VARCHAR or NUMBER a
text or numeric function reads, and — unlike a plain OBJECT — not to the VARIANT the semi-structured
surface reads either. So live refuses them in **more** positions than it refuses an OBJECT, including
the two positions an OBJECT sails through: the grouping / sorting **keys**, and **comparison**.

Everything below is live-verified 2026-08-04 over one table carrying a populated `GEOGRAPHY` column, a
populated `GEOMETRY` column and a populated `OBJECT` column. **The two geo types agree everywhere** —
every rejection reproduced with the type name swapped and nothing else changed — so they are one family
(`GeoTypes.isGeo`).

| Position | Rejected | Message |
|---|---|---|
| **GROUP BY keys** | `GROUP BY g`, the ordinal `GROUP BY 1`, a SELECT alias, `ROLLUP(g)` / `CUBE(gm)` / `GROUPING SETS ((g))`, a geo key among others, `IFF(TRUE, g, g)` | `Expressions of type GEOGRAPHY cannot be used as GROUP BY keys` (SQLSTATE 42804, code **92102**) |
| **ORDER BY keys** | `ORDER BY g`, the ordinal, the alias, `DESC NULLS LAST`, the clause inside `OVER (…)`, a derived table's or CTE's `ORDER BY` | …`as ORDER BY keys` (42804, code **92103**) |
| **PARTITION BY keys** | `OVER (PARTITION BY g …)`, including through `QUALIFY` | …`as PARTITION BY keys` (42804, code **92104**) |
| **Comparison** | `a.g = b.g`, `g = TO_GEOGRAPHY(…)`, `SELECT g = g`, `g <> g`, `g >= …`, `g < …`, `g = 'POINT(1 1)'`, `g IN (…)` / `NOT IN`, `EQUAL_NULL(g, g)`, `NULLIF(g, g)`, `IS DISTINCT FROM` | `Invalid argument types for function '=': (GEOGRAPHY, GEOGRAPHY)` (42P13, code 1044); `IN` names `'IN'` and lists the subject followed by every value |
| **Every cast** | `CAST(g AS VARCHAR)`, `g::VARCHAR`, `TRY_CAST`, `CAST(g AS VARIANT / OBJECT / ARRAY / NUMBER / BOOLEAN / BINARY)`, and even the identity `CAST(g AS GEOGRAPHY)` and the sibling `CAST(gm AS GEOGRAPHY)` | `invalid type [CAST(GT.G AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'` (SQLSTATE **22023**, code 1007) |
| **Every conversion function** | `TO_VARCHAR`, `TO_CHAR`, `TO_NUMBER` / `TO_DECIMAL`, `TO_BOOLEAN`, `TO_DATE` / `TO_TIME` / `TO_TIMESTAMP*`, `TO_BINARY`, `TO_VARIANT`, `TO_OBJECT`, `TO_ARRAY` and the `TRY_TO_*` spellings | same shape; the parameter is the CANONICAL conversion (`TRY_TO_NUMBER(g)` reports `'TO_NUMBER'`), with `TO_CHAR` reporting itself. Every argument is rendered: `TO_CHAR(g, 'x')` |
| **The VARIANT-reading surface** | `TYPEOF(g)`, `TO_JSON`, `IS_OBJECT` / the `IS_*` family, `AS_VARCHAR` / the `AS_*` family, `OBJECT_KEYS`, `GET(g, 'type')` and the `g:type` sugar, `GET_PATH`, `OBJECT_INSERT`, `ARRAY_SIZE`, `ARRAY_APPEND`, `ARRAY_TO_STRING(a, g)` | `Invalid argument types for function 'TYPEOF': (GEOGRAPHY)` (42P13) |
| **The constructors** | `ARRAY_CONSTRUCT(g)` in any position, `ARRAY_CONSTRUCT_COMPACT`, `OBJECT_CONSTRUCT('a', g)`; and `OBJECT_CONSTRUCT(g, 1)` as a **key** | `Function ARRAY_CONSTRUCT does not support GEOGRAPHY argument type` (22000, code 2016); the key form appends ` for keys` (code 2270) |
| **The text and numeric families** | the whole 52-function string family and the whole 42-function numeric family, in every argument position | `Invalid argument types for function 'UPPER': (GEOGRAPHY)` — identical to the OBJECT shape |
| **The aggregates** | `MAX` / `MIN` / `MODE` (`does not support … argument type`, 22000); `SUM` / `AVG` / `LISTAGG` / `BITAND_AGG` (argument-type list); `MEDIAN` and `PERCENTILE_CONT/DISC … WITHIN GROUP (ORDER BY g)` (`incompatible types: [GEOGRAPHY] and [NUMBER(9,0)]`, 42846); `STDDEV` / `VARIANCE` / `SKEW` / `KURTOSIS` (`'*': (GEOGRAPHY, GEOGRAPHY)`) | as for OBJECT, with the type name swapped |
| **The aggregates that OBJECT passes** | `ARRAY_AGG(g)`, `ARRAY_UNIQUE_AGG`, `OBJECT_AGG` (**both** halves), `HASH_AGG`, `MAX_BY` / `MIN_BY`, `APPROX_COUNT_DISTINCT` / `HLL` | argument-type list — and this row is a **divergence**: every one of them returns a value over an OBJECT |
| **The offset window functions** | `LAG(g)`, `LEAD(gm)`, `NTH_VALUE(g, 1)` | `Invalid argument types for function 'LAG': (GEOGRAPHY)` — while `FIRST_VALUE(g)` and `LAST_VALUE(g)` are **accepted** |
| **Operators** | `'x' \|\| g`, `g + 1`, `g * g`, `-g` (reports `'NEGATE'`) | as for OBJECT |

**Everything else still works**, and each acceptance was measured rather than inferred — they are what
the rules above are bounded against: `SELECT DISTINCT g`, `SELECT DISTINCT g, gm`,
`COUNT(g)` / `COUNT(DISTINCT g)` / `COUNT(g) OVER ()` / `COUNT(DISTINCT g, n)`, `ANY_VALUE(g)`,
`HASH(g)`, `FIRST_VALUE` / `LAST_VALUE`, `IFF` / `COALESCE` / `NVL` / `IFNULL` / `NVL2` / `DECODE` /
`CASE` (all of which report a GEOGRAPHY result column), `g IS NULL`, `UNION` / `UNION ALL` /
`INTERSECT` / `EXCEPT`, `HAVING COUNT(g) > 0`, and `TO_GEOGRAPHY(g)` / `TO_GEOMETRY(g)`.

That a geo value **de-duplicates but does not compare** looks contradictory and is not: `DISTINCT`,
`COUNT(DISTINCT …)` and the set operators group by value without any written comparison, while
`GROUP BY`, `ORDER BY` and `=` are refused. Frostlake keeps the same split — the key rules and the
comparison rule are separate checks, and neither touches the de-duplicating operators.

**`ST_ASWKT` / `ST_ASTEXT` / `ST_ASEWKT` / `ST_ASGEOJSON` / `ST_ASWKB` are the only routes to a geo
value's text.** Because the VARIANT cast is refused too, there is no `g::VARIANT` escape hatch of the
kind a structured value has. And those accessors re-open the key positions:
`GROUP BY ST_ASWKT(g)` and `ORDER BY ST_ASWKT(g)` both work.

The rules live in the **engine**, not in the optional `frostlake-geo` module: a `GEOGRAPHY` column can
be declared with the module absent, so a rejection that depended on the module being on the classpath
would be no rejection at all. They read the **declared** type and fire at plan time, so an empty table
rejects exactly as a populated one does, and they read through derived tables, CTEs, aliases and the
NULL-choosing conditionals. Which positions a function constrains is declared by the function itself
(`BuiltInFunction.geoRejection`), whose default is the **union** of the two semi-structured answers —
refused wherever *either* a plain OBJECT or a merely structured value is refused — with overrides only
where geo genuinely diverges.

Live rejections **not** yet reproduced (tracked as follow-up work):

- An explicit `JOIN … ON a.g = b.g`. The comparison rule reaches projections and `WHERE` predicates;
  the join condition is evaluated through a different seam with no plan-time hook. `WHERE a.g = b.g`
  — the implicit-join spelling — *is* rejected.
- `g IN (SELECT …)` (live reports `'='` rather than `'IN'`, and the subquery's projected type is not
  statically known here), `g BETWEEN … AND …` (live reports `'>='` with two of the three operands, and
  which two in the mixed cases was not measured), the simple `CASE g WHEN …` form (a different
  sentence again, `Can not convert parameter 'GT.G' of type [GEOGRAPHY] into expected type [ANY]`),
  and `HAVING MAX(g) IS NOT NULL`.
- `<>` reports `'!='` here: the two spellings are one operator by the time an AST exists.
- The **desugared-plan** names, for the same reason as the OBJECT section: live reports
  `'HLL_ACCUMULATE'` for `APPROX_COUNT_DISTINCT(g)`, `'SUBSTR'` for `LEFT(g, 1)`, `'SUM'` for
  `AVG(g)`, and `'DATE_ADDDAYSTOTIMESTAMP'` for `DATEADD(day, 1, g)`. Frostlake reports the function
  written. Live also renders a cast or conversion from its analysed plan — `CAST(GT.G AS
  VARCHAR(134217728))` — where Frostlake renders it as written, `CAST(G AS VARCHAR)`.
- The date family (`DATEADD(day, 1, g)`, `YEAR(g)` → `Function EXTRACT does not support GEOGRAPHY
  argument type`, `DATE_TRUNC(day, g)`), the hash family (`MD5(g)`), `PARSE_JSON(g)`, `BOOLAND(g, …)`,
  the logical `g AND TRUE`, and `CORR(g, n)` / the regression family with their internal shapes.
- The reverse direction, a cast **into** a geo type from something that is not one:
  `'POINT(1 1)'::GEOGRAPHY`, `CAST(s AS GEOMETRY)` and `NULL::GEOGRAPHY` are all
  `invalid type [CAST('POINT(1 1)' AS GEOGRAPHY)] for parameter 'TO_GEOGRAPHY'` live — a rule about
  the cast TARGET, not about where a geo value may be used.

## VECTOR functions

Snowflake's `VECTOR(FLOAT | INT, n)` type and the 12 functions over it. Everything below is
live-verified on a real account (2026-08-02).

Two properties drive the whole family:

- **Elements are 32-BIT, arithmetic is 64-bit.** A `VECTOR(FLOAT, n)` holds float32 elements and a
  `VECTOR(INT, n)` holds int32 elements (an out-of-range integer WRAPS: `[2147483648,0,0]::VECTOR(INT,3)`
  is `[-2147483648,0,0]`). The functions read those elements, compute in float64, and narrow only the
  ELEMENTS of a vector RESULT — so `VECTOR_NORMALIZE([1,2,3]::VECTOR(FLOAT,3))` is
  `[0.26726124,0.5345225,0.80178374]` (float32) while `VECTOR_L2_DISTANCE([1,2,3],[4,5,6])` is the full
  float64 `5.196152422706632`.
- **Element type and dimension are part of the STATIC type**, checked at COMPILE time. A dimension
  mismatch, a mixed `FLOAT`/`INT` pair, a plain ARRAY where a vector is required and an untyped `NULL`
  are all `Invalid argument types for function '<NAME>': (...)`, firing even over zero rows. A TYPED
  null (`NULL::VECTOR(FLOAT,3)`) is accepted and yields SQL NULL.

`x::VECTOR(t, n)` converts an array of the right length; a wrong length or a vector of another type is
`Vector value being cast to a vector is not an array or vector, or has incorrect dimension or element
type`, and unusable elements are `Array-like value being cast to a {float|integer} vector has elements
that are not {real numbers|integers}`. As with the semi-structured types, a vector expression is
rejected in a `VALUES` clause (`Invalid data type [VECTOR(FLOAT, 3)] in VALUES clause`) — use
`INSERT ... SELECT`. `TYPEOF` of a vector is `VECTOR`.

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `VECTOR_COSINE_SIMILARITY` | `VECTOR_COSINE_SIMILARITY(v1, v2)` | FLOAT | `Σ(a·b)` divided by the product of the two magnitudes. `[1,2,3]` against itself is `1.0`, against `[4,5,6]` `0.9746318461970762`; against the ZERO vector it is `NaN` (the zero magnitude is not special-cased). INT operands give the same double. |
| `VECTOR_L1_DISTANCE` | `VECTOR_L1_DISTANCE(v1, v2)` | FLOAT | Manhattan distance — the sum of the absolute per-element differences; `[1,2,3]` to `[4,5,6]` is `9.0`. |
| `VECTOR_L2_DISTANCE` | `VECTOR_L2_DISTANCE(v1, v2)` | FLOAT | Euclidean distance `sqrt(Σ(a−b)²)`; `[1,2,3]` to `[4,5,6]` is `5.196152422706632`. |
| `VECTOR_INNER_PRODUCT` | `VECTOR_INNER_PRODUCT(v1, v2)` | FLOAT | Dot product `Σ(a·b)`; `[1,2,3]·[4,5,6]` is `32.0`. Accumulated in float64, so an INT pair can exceed 32-bit range. |
| `VECTOR_NORMALIZE` | `VECTOR_NORMALIZE(v)` | VECTOR(FLOAT, n) | The unit vector (each element divided by the magnitude), ALWAYS float-elemented (an INT vector normalizes to a FLOAT one). The zero vector normalizes to itself (`[0.0,0.0,0.0]`), NOT to `NaN`. |
| `VECTOR_TRUNC` (`VECTOR_TRUNCATE`) | `VECTOR_TRUNC(v, n)` | VECTOR(same element type, n) | The first `n` dimensions, keeping the element type. `n` is a COMPILE-time constant read in 32 bits: `0` gives `[]`, a column / an expression / a non-integral literal is "needs to be constant", and too large is "Requested truncation dimension `n` for VECTOR_TRUNC should be less than or equal to the dimension of the provided vector (`d`)." with its digits grouped. A negative `n` compiles, and each row then sizes the result as four bytes an element in 32 bits: a negative size fails with the account's internal error, a size past 16777216 is too long to return, and any other reads `[]`. |
| `IS_VECTOR` | `IS_VECTOR(x)` | BOOLEAN | TRUE for a vector, FALSE for every other value VARIANT can hold (a plain ARRAY, an OBJECT, a VARIANT, a NUMBER, a BOOLEAN). **NULL in is NULL out, not FALSE.** A VARCHAR or temporal argument is an argument-type error. |
| `VECTOR_SUM` | `VECTOR_SUM(v)` | VECTOR(same element type, n) | Aggregate: element-wise SUM across the group's rows, keeping the element type. |
| `VECTOR_AVG` | `VECTOR_AVG(v)` | VECTOR(FLOAT, n) | Aggregate: element-wise MEAN, always float-elemented. |
| `VECTOR_MIN` | `VECTOR_MIN(v)` | VECTOR(same element type, n) | Aggregate: element-wise MINIMUM — each DIMENSION is minimised on its own, so the result need not be any input row. |
| `VECTOR_MAX` | `VECTOR_MAX(v)` | VECTOR(same element type, n) | Aggregate: element-wise MAXIMUM, likewise per dimension. |

The four aggregates SKIP NULL rows (they do not count towards `VECTOR_AVG`'s divisor) and return SQL
NULL — not a zero vector — for a group with no non-NULL row. `SNOWFLAKE.CORTEX.EMBED_TEXT_768` /
`_1024`, the usual way vectors are produced, are out of scope (they need a model, not arithmetic).

## Window functions

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `CONDITIONAL_TRUE_EVENT` | `CONDITIONAL_TRUE_EVENT(expr) OVER ([PARTITION BY …] ORDER BY …)` | NUMBER | A running count that starts at 0 in each partition and increments by 1 on every row (in ORDER BY order, up to and including the current) on which `expr` evaluates to TRUE. A row where `expr` is not TRUE (including NULL) carries the current count. |
| `CONDITIONAL_CHANGE_EVENT` | `CONDITIONAL_CHANGE_EVENT(expr) OVER ([PARTITION BY …] ORDER BY …)` | NUMBER | A running count that starts at 0 in each partition and increments by 1 each time `expr`'s value differs from the previous row's value (in ORDER BY order, up to and including the current). A step in which either the current or previous value is NULL is not counted as a change. |

## Cryptographic functions

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `HASH` | `HASH(expr [, …])` | NUMBER | A signed 64-bit hash (FNV-1a) over a type-tagged canonical encoding of the arguments, mixed in order. **The values are not Snowflake's — never compare one across engines — but which arguments hash ALIKE is, and that relation is live-verified.** Numbers hash by VALUE with scale and declared type ignored, so `HASH(1)`, `HASH(1.00)` and `HASH(1.0::FLOAT)` are one hash while the string `'1'` is another. BOOLEAN joins the number class (TRUE hashes as 1, FALSE as 0, neither as `'true'`), and so does every temporal, as its epoch offset in its own unit — a DATE by DAYS, a TIME by seconds since midnight, a TIMESTAMP by epoch seconds — so `DATE '1970-01-02'` and `TIME '00:00:01'` both hash as 1 while a DATE and the TIMESTAMP at its midnight do not collide. A BINARY hashes as the string of its bytes (`X'31'` = `'1'`), a VARIANT as the value it holds (except a VARIANT boolean or JSON null, which keep their own class), and a container as its canonical JSON — so object key order does not matter, array order does, and an array never equals the string of its own text. Arity and order matter: `HASH(1)`, `HASH(1, NULL)` and `HASH(NULL)` are three different hashes. |
| `MD5_BINARY` / `SHA1_BINARY` / `SHA2_BINARY` | `MD5_BINARY(msg)` … | BINARY | The raw digest bytes as BINARY (`SHA2_BINARY` takes an optional bit size, 256 default). |
| `TRY_DECRYPT` / `TRY_DECRYPT_RAW` | `TRY_DECRYPT(value, passphrase [, aad [, method]])` … | BINARY | The non-throwing forms: NULL on a bad key, payload, tag or method. |
| `ENCRYPT` | `ENCRYPT(value, passphrase [, aad [, method]])` | BINARY | Passphrase AES encryption of the value (a string's UTF-8 bytes or a BINARY value's bytes); the result is the mode's IV followed by the ciphertext, as BINARY. The AAD is bound into a GCM tag, and any other mode refuses one (an empty AAD too). `method` is `<algorithm>-<mode>[/pad:<padding>]`: `AES` with `GCM` (the default, and what a NULL method means), `CBC`, `ECB`, `CTR`, `OFB` or `CFB`, and `PKCS` (the default) or `NONE` padding for CBC and ECB. The words match without regard to case but `pad:` is written in lower case; a misspelling is `Malformed encryption method parameter: …`, and an unknown algorithm, mode or padding is refused by name. |
| `DECRYPT` | `DECRYPT(value, passphrase [, aad [, method]])` | BINARY | Reverses `ENCRYPT` under the same AAD and method. The input must be BINARY; the decrypted plaintext bytes come back as BINARY (compare with `TO_BINARY(text, 'UTF-8')` to read text). Input shorter than the mode's IV (and a GCM tag) is not the method's input; a tag or padding that does not verify fails the decryption; CTR, OFB and CFB verify nothing. |
| `ENCRYPT_RAW` | `ENCRYPT_RAW(value, key, iv [, aad [, method]])` | VARIANT (OBJECT) | Raw-key AES encryption in any mode the method names — GCM (the default), CBC, ECB, CTR, OFB, CFB. The value/key/iv/aad arguments must be BINARY — a hex VARCHAR is a compile error, matching Snowflake, so wrap them in `TO_BINARY`; `key` is used verbatim (16/24/32 bytes → AES-128/192/256) and `iv` must be exactly its mode's size: 12 bytes for GCM, 16 for CBC/CTR/OFB/CFB, none for ECB. A NULL `iv` is not a missing argument — one is drawn at random and returned. Returns an OBJECT `{ciphertext, iv}` plus `tag` for an authenticating mode (hex BINARY; `iv` is NULL for ECB). The method is read as `ENCRYPT`'s is, then the key's size, the IV's size, and whether the mode takes AAD. |
| `DECRYPT_RAW` | `DECRYPT_RAW(value, key, iv [, aad [, method [, tag]]])` | BINARY | Reverses `ENCRYPT_RAW`, in any mode it encrypts in. The optionals nest from the left (live-measured): the 4th argument is always `aad`, the 5th `method`, and the AEAD `tag` sits strictly 6th — an authenticating call that never reaches it fails "Decryption mode requires an AEAD tag as parameter", and a wrong-length tag fails "Wrong AEAD tag size. Expected 16, but got N"; a mode that does not authenticate needs no tag. Anything the cipher itself refuses — a wrong key, IV, AAD or tag, or a padding that does not read — is one sentence: "Decryption failed. Check encrypted data, key, AAD, or AEAD tag." The value/key/iv/aad/tag arguments must be BINARY (wrap hex text in `TO_BINARY`); only `method` is a VARCHAR. |

## Encoding / compression functions

| Function | Signature | Returns | Notes |
|---|---|---|---|
| `COMPRESS` | `COMPRESS(input, method)` | BINARY | Compresses the input (a string's UTF-8 bytes or a BINARY value's bytes). The method is **mandatory** — there is no default, and one argument is a compile error ("not enough arguments … expected 2, got 1"). Methods: `snappy`, `zlib`, `zstd`, `bz2` — case-insensitive, surrounding whitespace ignored; anything else (`deflate`, `raw_deflate`, `gzip`, `bzip2`, …) raises `Unknown compression method '<name>'`. See [compression levels](#compression-levels) below. |
| `DECOMPRESS_STRING` | `DECOMPRESS_STRING(input, method)` | STRING | Inverse of `COMPRESS`; the input must be BINARY (a VARCHAR argument is rejected, as in Snowflake). The method is mandatory; a level suffix is accepted and ignored. |
| `DECOMPRESS_BINARY` | `DECOMPRESS_BINARY(input, method)` | BINARY | Inverse of `COMPRESS`; same input and method rules. |
| `HEX_ENCODE` | `HEX_ENCODE(input [, case])` | STRING | Hex of the input's bytes (a string's UTF-8 bytes or a BINARY value's bytes). The optional case flag is 1 for upper (the default) and 0 for lower; any other value is `Numeric value is out of range, error: 2` at row time. |
| `HEX_DECODE_STRING` | `HEX_DECODE_STRING(input)` | STRING | Decodes hex and returns the bytes as UTF-8 text. Bytes that spell no UTF-8 string are refused — `Invalid UTF8 detected while decoding 'AB'`, quoting the input as written — rather than substituting replacement characters. `TRY_HEX_DECODE_STRING` is the non-throwing form and answers NULL. |
| `HEX_DECODE_BINARY` | `HEX_DECODE_BINARY(input)` | BINARY | Decodes BARE hex into a BINARY value — a `0x` prefix is refused, as it is by `TO_BINARY` and a `::BINARY` cast. `TRY_HEX_DECODE_BINARY` is the non-throwing form and answers NULL instead. |
| `BASE64_ENCODE` | `BASE64_ENCODE(input [, max_line_length [, alphabet]])` | STRING | Base64 of the input's bytes (a string's UTF-8 bytes or a BINARY value's bytes). `max_line_length` wraps the output every N characters with none left trailing — 0, the default, does not wrap; a fractional length rounds (8.7 wraps at nine); NULL makes the whole call NULL; and an integral numeric LITERAL outside `[0, 2147483647]` is refused while the statement compiles, though the same value written as an expression (`0 - 1`) runs. Both optional arguments must be row-independent — a column is "needs to be constant". `alphabet` gives the characters standing in for `+`, `/` and the `=` padding, at most three, none of which may already appear in base64. |
| `BASE64_DECODE_STRING` | `BASE64_DECODE_STRING(input [, alphabet])` | STRING | Decodes base64 and returns the bytes as UTF-8 text. Bytes that spell no UTF-8 string are refused — `Invalid UTF8 detected while decoding 'qw=='`, quoting the input as written — rather than substituting replacement characters. Takes the same optional `alphabet` second argument as `BASE64_DECODE_BINARY`. `TRY_BASE64_DECODE_STRING` is the non-throwing form and answers NULL. |
| `BASE64_DECODE_BINARY` | `BASE64_DECODE_BINARY(input [, alphabet])` | BINARY | Decodes base64 into a BINARY value. The optional alphabet must match the one the value was encoded with, or the input is not base64 at all — `The following string is not a legal base64-encoded value: '$%8='`. `TRY_BASE64_DECODE_BINARY` is the non-throwing form. |

### Compression levels

The `method` argument of `COMPRESS` / `DECOMPRESS_STRING` / `DECOMPRESS_BINARY` takes an optional
level in parentheses — `'zlib(1)'`, `'ZSTD( 19 )'`, `'bz2(9)'`. The level must be a non-negative
integer that fits in a signed 32-bit int; `0` means the method's default (identical to omitting it);
a level above the algorithm's maximum clamps rather than erroring. Anything else — a negative or
fractional level, empty or unbalanced parentheses — is reported as an unknown *method*, quoting the
whole string: `COMPRESS(x, 'zlib(-1)')` fails with `Unknown compression method 'zlib(-1)'`.
Decompression is level-independent, so the suffix is parsed and ignored there.

Compressed output is byte-identical to Snowflake's for `snappy` at every level, for `zlib` at the
default level and levels 2-9, and for `zstd` at its default level (0-3). `bz2` and the levels
`zlib(1)` and `zstd(19)`+ produce well-formed but not byte-identical streams — Frostlake reads
Snowflake's payloads and Snowflake reads Frostlake's, both verified against a live account.

## Table functions

`DIRECTORY(@stage)` — the directory table of a stage: one row of file-level metadata per staged file
(`RELATIVE_PATH`, `SIZE`, `LAST_MODIFIED`, `MD5`, `ETAG`, `FILE_URL`), `FILE_URL` the file's stage file URL as
`BUILD_STAGE_FILE_URL(@stage, RELATIVE_PATH)` spells it (the engine's HTTP server serves it). Related: staged files
can be queried directly — `SELECT $1, $2, metadata$filename FROM @stage[/path] [(FILE_FORMAT => 'name',
PATTERN => 'regex')]` — with CSV fields as $1..$n and record formats (JSON, XML, …) as a single
VARIANT $1.

**Whether a table function accepts `NAME => value` is a property of the function, not of table
functions in general** — the three below sit at three different points. FLATTEN takes named arguments;
SPLIT_TO_TABLE takes none at all, not even the names its parameters are documented under; GENERATOR
ignores anything it cannot use, positional arguments included, and answers zero rows rather than an
error.

| Function | Signature | Returns | Description |
| --- | --- | --- | --- |
| `FLATTEN` | `TABLE(FLATTEN(input [, path [, outer [, recursive [, mode]]]]))` or any of those five by name (`INPUT`, `PATH`, `OUTER`, `RECURSIVE`, `MODE`) | table | Expands semi-structured data into `SEQ`, `KEY`, `PATH`, `INDEX`, `VALUE`, `THIS`. `INPUT` must be VARIANT / OBJECT / ARRAY — a VARCHAR is refused (`invalid type [VARCHAR(7)] for parameter 'INPUT'`), so JSON text needs `PARSE_JSON`. A VARIANT holding a scalar expands to nothing. `OUTER` and `RECURSIVE` take booleans, not the strings `'true'`/`'false'`. `RECURSIVE` descends only through what the `MODE` emitted. `PATH` prefixes the reported path. `SEQ` numbers the input record, so one call's rows all share it. |
| `SPLIT_TO_TABLE` | `TABLE(SPLIT_TO_TABLE(string, delimiter))` | table | Splits `string` into `SEQ`, `INDEX` (1-based), `VALUE` — one row per part, empty parts kept. **Positional only, and both arguments required**: `SPLIT_TO_TABLE(STRING => …)` is refused, and so is a one-argument call. A NULL on either side yields no rows; an empty delimiter does not split. |
| `STRTOK_SPLIT_TO_TABLE` | `TABLE(STRTOK_SPLIT_TO_TABLE(string [, delimiters]))` | table | Tokenizes `string` into `SEQ`, `INDEX` (1-based), `VALUE` — one row per token. The second argument is a SET of delimiter CHARACTERS, each splitting on its own, and defaults to a single space; an empty token is never produced, so consecutive, leading and trailing delimiters collapse. **Positional only**: `STRTOK_SPLIT_TO_TABLE(STRING => …)` is refused. A NULL on either side yields no rows; an empty delimiter set does not split. |
| `GENERATOR` | `TABLE(GENERATOR(ROWCOUNT => n))` or `TABLE(GENERATOR(TIMELIMIT => seconds))` | table | Generates rows carrying **no columns** — project `SEQ4()`, literals or expressions over them. An argument it does not recognise is ignored rather than refused, so `GENERATOR()`, `GENERATOR(3)` and `GENERATOR(NOSUCH => 3)` all produce zero rows. |
| `TO_QUERY` | `TABLE(TO_QUERY(sql_text [, name => value, …]))` | table | Compiles `sql_text` into a query and returns its result set as a table source. **The text's parameter is named `SQL`** — `TO_QUERY(SQL => '…')` runs and `TO_QUERY(INPUT => '…')` is refused. Every other named argument binds into a `:name` placeholder inside the text, and a bind VALUE must be a string (`v => 5` fails `argument needs to be a string: '0'`); an unsupplied placeholder binds as NULL. |
| `RESULT_SCAN` | `TABLE(RESULT_SCAN([query_id]))` or `TABLE(RESULT_SCAN(QUERY_ID => query_id))` | table | The result set of an earlier statement, with its columns — including a `SHOW`'s (lower-case names) and a DDL statement's single `status` column. `LAST_QUERY_ID()` names the previous statement and `LAST_QUERY_ID(-2)` the one before it. No argument scans the last query, and a second argument is ignored. The argument is judged as written while the statement compiles: a string, a session or bind variable, a scalar subquery or a LAST_QUERY_ID call is read as a value; a whole number — `-1`, `(3)`, `-(-3)`, `1.0`, `2E0`, or a session variable holding one — names the statement `LAST_QUERY_ID` would, past 10,000 either way `Value for parameter 1 exceeds maximum allowable value (10,000).`; a NULL, a boolean, a fraction or a binary fails `argument needs to be a string: '<line>'`; and anything computed fails `argument <line> to function <column> needs to be constant, found '<token>'`, naming where its top token stands — the operator, the call's first word (`TO_VARCHAR`, `CAST`, `CASE`), or an array or object literal whole. A column fails `Invalid result query ID, found '<name>'` at its position. An unknown id fails `Statement <id> not found`, and one that names no statement `Statement NULL not found`. |
| `INFER_SCHEMA` | `TABLE(INFER_SCHEMA(LOCATION => '@stage[/path]', FILE_FORMAT => 'format_name' [, FILES => 'f' \| ('f1', 'f2', …)] [, IGNORE_CASE => TRUE \| FALSE] [, MAX_FILE_COUNT => n] [, MAX_RECORDS_PER_FILE => n] [, KIND => 'STANDARD' \| 'ICEBERG']))` | table | The column definitions of staged files, one row per column: `COLUMN_NAME`, `TYPE` (spelled `NUMBER(4, 2)`, `TEXT`, `REAL`, `TIMESTAMP_NTZ`, …), `NULLABLE`, `EXPRESSION` (`$3::DATE` for CSV; for the other formats `$1:name::TEXT`, or `GET_IGNORE_CASE($1, 'NAME')::TEXT` under `IGNORE_CASE`), `FILENAMES` and `ORDER_ID` (a NUMBER(4,0) from 0). Reads CSV (named `c1`, `c2`, … unless `PARSE_HEADER = TRUE`) and JSON; Parquet, Avro and ORC need the `frostlake-formats` module; XML is refused (`Invalid file format XML`). **Every argument must be named**, by an unquoted name (`"LOCATION" =>` is `unexpected argument ["LOCATION"] at position 1,`), and LOCATION and FILE_FORMAT are required; each property takes a constant, an expression being refused with its plan text (`invalid value 'UPPER('ffh')' for property 'FILE_FORMAT'`). LOCATION reads files by path prefix at any depth; FILES names files by the LOCATION's path, one slash and the entry (`@st/two` and `'small.csv'` name `two/small.csv`, a missing one refuses the call); both read in path order, and an empty stage answers no rows. Not listed by `SHOW FUNCTIONS`. The rows can feed `CREATE TABLE … USING TEMPLATE (SELECT ARRAY_AGG(OBJECT_CONSTRUCT(*)) FROM TABLE(INFER_SCHEMA(…)))`, whose descriptions must each carry `COLUMN_NAME`, `TYPE` and `NULLABLE` (`Invalid template: TYPE field is missing in {"COLUMN_NAME":"a"}`) in a non-empty array (`Invalid template: template must be a non-null JSON array`). See [How INFER_SCHEMA types a column](#how-infer_schema-types-a-column). |
| `QUERY_HISTORY` | `TABLE(INFORMATION_SCHEMA.QUERY_HISTORY([END_TIME_RANGE_START, END_TIME_RANGE_END, RESULT_LIMIT, …]))` | table | Recent queries, newest first. **Must be qualified with `INFORMATION_SCHEMA`** (or `<db>.INFORMATION_SCHEMA`) — the bare name answers `Invalid identifier QUERY_HISTORY`. Note the positional order: the first parameter is a TIMESTAMP, not a limit, so `QUERY_HISTORY(2)` fails `invalid type [NUMBER(1,0)] for parameter 'END_TIME_RANGE_START'`. `RESULT_LIMIT` defaults to 100 and may not exceed 10,000. `QUERY_HISTORY_BY_SESSION` / `_BY_USER` / `_BY_WAREHOUSE` are the scoped variants. |
| `QUERY_HISTORY_BY_SESSION` | `TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION([SESSION_ID => id, RESULT_LIMIT => n, …]))` | table | `QUERY_HISTORY`'s columns for one session's statements — the caller's when no `SESSION_ID` is given; the id may also be passed first by position, and an unknown one lists nothing. Same qualification rule and `RESULT_LIMIT` as `QUERY_HISTORY`. |
| `QUERY_HISTORY_BY_USER` | `TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_USER([USER_NAME => 'name', RESULT_LIMIT => n, …]))` | table | `QUERY_HISTORY`'s columns for one user's statements — the calling user's by default; the name may also be passed first by position. Same qualification rule and `RESULT_LIMIT` as `QUERY_HISTORY`. |
| `QUERY_HISTORY_BY_WAREHOUSE` | `TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_WAREHOUSE([WAREHOUSE_NAME => 'name', RESULT_LIMIT => n, …]))` | table | `QUERY_HISTORY`'s columns for the statements one warehouse ran — the current warehouse's by default; the name may also be passed first by position. Same qualification rule and `RESULT_LIMIT` as `QUERY_HISTORY`. |
| `ALERT_HISTORY` | `TABLE(INFORMATION_SCHEMA.ALERT_HISTORY([SCHEDULED_TIME_RANGE_START => ts, SCHEDULED_TIME_RANGE_END => ts, RESULT_LIMIT => n, ALERT_NAME => 'name']))` | table | One row per evaluation of an alert in the account, newest `SCHEDULED_TIME` first, plus a `SCHEDULED` row for the next evaluation of every started alert due within eight days: `NAME`, `DATABASE_NAME`, `SCHEMA_NAME`, `CONDITION`, `CONDITION_QUERY_ID`, `ACTION`, `ACTION_QUERY_ID`, `STATE` (`TRIGGERED`, `CONDITION_FALSE`, `CONDITION_FAILED`, `ACTION_FAILED`, `SCHEDULED`), `SQL_ERROR_CODE`, `SQL_ERROR_MESSAGE`, `SCHEDULED_TIME`, `COMPLETED_TIME`, `SCHEDULED_FROM` (`SCHEDULE` or `EXECUTE ALERT`), `RUNBOOK`, `WAS_AUTO_SUSPENDED` (always FALSE), `CONFIG` (always NULL). Every argument is optional and named; `RESULT_LIMIT` is at most 10000 (default 100; above it the call is refused with `Value for parameter RESULT_LIMIT exceeds maximum allowable value (10,000).`, below one it answers no rows) and `ALERT_NAME` matches an unqualified name case-insensitively. Only under `INFORMATION_SCHEMA`: a bare `ALERT_HISTORY()` is an invalid identifier. An evaluation that ran names its condition query in `CONDITION_QUERY_ID` and, when the action ran, its action query in `ACTION_QUERY_ID` (ids the engine makes up, stable per run), and a run without an error reports `SQL_ERROR_CODE` 0, a failed run NULL (its failure is in `SQL_ERROR_MESSAGE`). |
| `TASK_HISTORY` | `TABLE(INFORMATION_SCHEMA.TASK_HISTORY([RESULT_LIMIT => n, TASK_NAME => 'name', ERROR_ONLY => TRUE, …]))` | table | Completed and scheduled task runs: `QUERY_ID`, `NAME`, `DATABASE_NAME`, `SCHEMA_NAME`, `QUERY_TEXT`, `CONDITION_TEXT`, `STATE`, `ERROR_CODE`, `ERROR_MESSAGE`, `SCHEDULED_TIME`, `QUERY_START_TIME`, `NEXT_SCHEDULED_TIME`, `COMPLETED_TIME`, `ROOT_TASK_ID`, `GRAPH_VERSION`, `RUN_ID`, `RETURN_VALUE`, `SCHEDULED_FROM`. Same qualification rule and the same positional trap as `QUERY_HISTORY` (its first positional parameter is `SCHEDULED_TIME_RANGE_START`). |
| `TASK_DEPENDENTS` | `TABLE(INFORMATION_SCHEMA.TASK_DEPENDENTS(TASK_NAME => 'name' [, RECURSIVE => TRUE \| FALSE]))` | table | The named task as the first row, then its child tasks — every descendant with `RECURSIVE => TRUE` (the default), the direct children with `FALSE` — and a root's finalizer task, a direct dependent in both modes, in the columns `CREATED_ON`, `NAME`, `DATABASE_NAME`, `SCHEMA_NAME`, `OWNER`, `COMMENT`, `WAREHOUSE`, `SCHEDULE`, `PREDECESSORS` (fully qualified), `STATE`, `DEFINITION`, `CONDITION`. The name may be qualified; a bare one resolves in the current schema. A name that is no task is refused as `Invalid value [<name>] for function 'TASK_DEPENDENTS_SCAN', parameter 1: must be a valid task name`. INFORMATION_SCHEMA-qualified only. |
| `CURRENT_TASK_GRAPHS` | `TABLE(INFORMATION_SCHEMA.CURRENT_TASK_GRAPHS([RESULT_LIMIT => n] [, ROOT_TASK_NAME => 'name']))` | table | The graph runs scheduled within the next eight days — one `SCHEDULED` row per resumed root task with a schedule — newest scheduled time first: `ROOT_TASK_NAME`, `DATABASE_NAME`, `SCHEMA_NAME`, `STATE`, `FIRST_ERROR_TASK_NAME`, `FIRST_ERROR_CODE`, `FIRST_ERROR_MESSAGE`, `SCHEDULED_TIME`, `QUERY_START_TIME`, `NEXT_SCHEDULED_TIME`, `ROOT_TASK_ID`, `GRAPH_VERSION`, `RUN_ID`, `ATTEMPT_NUMBER`, `SCHEDULED_FROM`, `CONFIG`, `GRAPH_RUN_GROUP_ID`, `BACKFILL_INFO`, `SCHEDULED_BY_USER` (NULL: the engine records no user). The engine runs a graph to completion when it starts, so no run is ever `EXECUTING`. `RESULT_LIMIT` is at most 10000 (default 1000; above it the call is refused with `Value for parameter RESULT_LIMIT exceeds maximum allowable value (10,000).`, and a limit of zero is taken as absent — so is a negative one, which the account itself fails with an internal error); `ROOT_TASK_NAME` is an unqualified name, matched case-insensitively. INFORMATION_SCHEMA-qualified only. |
| `COMPLETE_TASK_GRAPHS` | `TABLE(INFORMATION_SCHEMA.COMPLETE_TASK_GRAPHS([RESULT_LIMIT => n] [, ROOT_TASK_NAME => 'name'] [, ERROR_ONLY => TRUE \| FALSE]))` | table | The completed graph runs, newest completion first, in `CURRENT_TASK_GRAPHS`' columns plus `COMPLETED_TIME`. A graph run is one run of a root task with the runs of its descendants that started from it (at or after the root's run, before its next run); it is `FAILED` when any task's last run in it failed, the earliest such failure being its first error, and `SUCCEEDED` otherwise. `ERROR_ONLY => TRUE` keeps the failed runs. `GRAPH_VERSION` and `ATTEMPT_NUMBER` are always 1. INFORMATION_SCHEMA-qualified only. |
| `TAG_REFERENCES` | `TABLE(INFORMATION_SCHEMA.TAG_REFERENCES(object_name, object_domain))` | table | The tags attached to one object, one row per tag: `TAG_DATABASE`, `TAG_SCHEMA`, `TAG_NAME`, `TAG_VALUE`, `LEVEL`, `OBJECT_DATABASE`, `OBJECT_SCHEMA`, `OBJECT_NAME`, `DOMAIN`, `COLUMN_NAME`, `APPLY_METHOD`. Both arguments are required and **positional only** — a named one is refused, and the refusal names the function `[INFORMATION_SCHEMA]`. A bare `object_name` resolves against the current database and schema, and is read as identifiers are: unquoted parts fold to upper case, a double-quoted part (`'"MyWh"'`) is kept as written. `TABLE` is the domain for every table-like object, views included — `VIEW` is refused. `COLUMN` takes a four-part name whose last part is the column. Inheritance is followed: a column reports the tags of its table, schema and database, a table-like or other schema object those of its schema and database, a schema those of its database, each inherited row with `APPLY_METHOD` `INHERITED` and `LEVEL` naming the domain the tag is set on (a tag set at several levels is reported once, from the nearest). Resolved domains: `TABLE`, `COLUMN`, `SCHEMA`, `DATABASE`, `ALERT`, `STAGE`, `STREAM`, `TASK`, `FUNCTION`, `PROCEDURE`, `WAREHOUSE`, `COMPUTE POOL`, `USER`, `ROLE`; the other documented domains answer no rows, an undocumented one is refused. |

### How INFER_SCHEMA types a column

- **A CSV record** ends at the RECORD_DELIMITER (a newline by default, a carriage return before it joining it)
  and a field at the FIELD_DELIMITER, either of several characters. A field that starts with the
  FIELD_OPTIONALLY_ENCLOSED_BY character runs to the closing one across delimiters and newlines (a doubled one is one
  character, ESCAPE takes the next literally, spaces may follow it); in any other field a quote is an ordinary
  character and ESCAPE_UNENCLOSED_FIELD (a backslash by default) takes a delimiter, a newline or itself literally.
  The header is split without that escape, and a name that is empty (after TRIM_SPACE) is refused, `Error with CSV
  header: empty string in the header is not allowed`, checked with the duplicate rule name by name. In a data
  record, a character after a closing enclosure is refused, `Found character 'y' instead of field delimiter ','`
  (`record delimiter '\n'` for the header's last field, or without PARSE_HEADER the first record's) — in the
  header's words, `Error with CSV header: error caused more fields in data than fields in header.`, when the record
  read on from there is wider than the header — and so is an enclosure still open at the end of the file,
  `matching enclosing character '"' not found before end of file`; the header takes such a character into the
  name, and an enclosure it leaves open closes at the end of the file. The record refusals name the line and
  character, and `Row 1 starts at line 2` when the record began on an earlier line; the header-name refusals name
  only the file.
- **A CSV field** is a NUMBER when it is a plain decimal (`+7`, `1.`, `.5`, and a point alone as a zero; not ` 5`
  with a space): its integer digits counted without leading zeros, its scale every digit after the point, so `007`
  is NUMBER(1, 0) and `0.10` NUMBER(3, 2), and any zero NUMBER(1, 0). Past 38 digits, in scientific notation with a
  digit before the exponent (`1e2`, `1.e5`; `.e5` is TEXT), hexadecimal (`0x1F`), `NaN` or `inf` it is a REAL (TEXT
  when a double overflows, a REAL still when it is too small for one); `true`/`false`/`t`/`f`/`yes`/`no`/`y`/`n`/
  `on`/`off` are BOOLEAN; `YYYY-MM-DD`, `MM/DD/YYYY` and `DD-MON-YYYY` are DATE, and the first two followed by a time
  — its fraction of any length, or a point alone — TIMESTAMP_NTZ whatever the zone (`31-JAN-2020 10:00` and a time
  with `AM` are TEXT); `HH:MI`, `HH:MI:SS` and a fraction of any length, each part of one or two digits, are TIME,
  on a twelve-hour clock with `AM` or `PM` too (`10:00 PM`, `9:30 am`; `13:00 PM` is TEXT), and with a zone only
  after a fraction's point (`10:00:00.1+07:00`, `10:00:00.1 Z`; `10:00:00+07:00` is TEXT); anything else TEXT. An
  unenclosed empty field (EMPTY_FIELD_AS_NULL, TRUE by default) and a NULL_IF marker (`\N` by default) are NULLs
  and type nothing; an enclosed `""` is an empty text.
- **A JSON value** keeps its own family: a number written with an exponent is a REAL (even `1e400`), and so is one
  past 38 digits unless it overflows a double, which is TEXT; any other is a NUMBER with its written scale (`0.00`
  is NUMBER(3, 2)); a string is DATE, TIME or TIMESTAMP_NTZ when it reads as
  one and TEXT otherwise, never a number or a BOOLEAN; arrays and objects are ARRAY and OBJECT. Each record's
  keys are taken in sorted (binary) order, and a key's ORDER_ID is its index among the sorted keys of the first
  record that has it, so keys first met in different records can share one; rows come in ORDER_ID order, ties in
  the order first met. A record that
  is not an object refuses the call (`Schema Inference failed: FIXED detected instead of an OBJECT…`; an array
  points at `STRIP_OUTER_ARRAY`), and so does a key named twice in one object at any depth (`Error parsing JSON:
  duplicate object attribute "a"`, at the second key's closing quote) unless the file format sets
  `ALLOW_DUPLICATE = TRUE`, which keeps the key's last value.
- **Within a file** a column's type takes each next value in when it is of its own family (a NUMBER widening to
  the larger integer part and scale, capped at 38 digits) or when the value is text the type still reads — TEXT
  reads anything, a REAL any plain decimal (`1`, `1.5`), a BOOLEAN `1` and `0`, a DATE a timestamp. So order matters: a DATE then a
  timestamp stays DATE, a timestamp then a DATE is TEXT; anything else falls to TEXT.
- **Across files** the types meet by family alone — the same family merges, different ones are TEXT (a DATE file
  beside a timestamp one) — and a column without a value in one file takes the other's type. Which file's
  ORDER_ID a column shared by differently laid-out files keeps is not fixed on the account; here it is the first
  file in path order.
- **Parquet, Avro and ORC** declare their columns: NULLABLE is FALSE only for a column every file declares
  required (under `IGNORE_CASE` a folded column keeps its first name's nullability; ORC never declares one), and
  declared types that differ in any way — two DECIMALs of different precision or scale included — across files,
  or between two names folded together by `IGNORE_CASE`, are VARIANT. Parquet: integers NUMBER(38, 0), DECIMAL
  NUMBER(p, s), FLOAT/DOUBLE REAL, BOOLEAN BOOLEAN, STRING/ENUM/JSON TEXT, other byte arrays BINARY, DATE, TIME, TIMESTAMP_NTZ (TIMESTAMP_LTZ for a UTC timestamp under `USE_LOGICAL_TYPE =
  TRUE`), groups VARIANT. Avro reads the physical type only (a date or timestamp is NUMBER(38, 0), a decimal
  BINARY), arrays ARRAY, records, maps and enums VARIANT. ORC: integers NUMBER(38, 0), FLOAT/DOUBLE REAL,
  STRING/CHAR/VARCHAR TEXT, DECIMAL NUMBER(p, s), DATE, TIMESTAMP TIMESTAMP_NTZ, BOOLEAN, BINARY, LIST ARRAY,
  STRUCT/MAP/UNION VARIANT. A file that is not of the format declares nothing: it adds no column and no FILENAMES
  entry, but, as a file lacking every column, leaves each one NULLABLE.
- **`KIND => 'ICEBERG'`** refuses every conflict — what would otherwise fall to TEXT, or to VARIANT for Parquet,
  Avro and ORC, and a non-string JSON value after a TEXT one (`Incompatible data types detected: REAL and FIXED.`,
  the newcomer named first) — and types Parquet the Iceberg way — INT, LONG, FLOAT, DOUBLE, TIMESTAMP_LTZ(6)
  (TIMESTAMP_NTZ(6) for a timestamp not adjusted to UTC), TIME(6) — refusing a nested
  column (`Encountered unsupported logical type NESTED Datatype for physical type UNKNOWN`).
- **CSV header refusals**: a name that is empty or given twice (`Error with CSV header: duplicated column names "a"
  is not allowed in the header`), and a record short of or past the header, each naming the file.

`SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('task')` is **not** a table function, despite belonging to
this family: it is a scalar returning a status sentence, so it goes in a select list and
`TABLE(SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS(…))` is refused. Cancelling a task that is not
running is not an error — it answers "Task &lt;name&gt; has no currently running executions…" — while an
unknown task raises "Task &lt;name&gt; not found or not authorized…".

`SYSTEM$GET_TASK_GRAPH_CONFIG([path])` is a scalar too, answered only inside a task — anywhere else it raises
"Function SYSTEM$GET_TASK_GRAPH_CONFIG must be called from within a task." It reads the configuration of the
graph run the task belongs to: the root task's `CONFIG`, with the object an `EXECUTE TASK … USING CONFIG` gave
merged over it field by field (a field both hold takes the run's value, an object both hold merges the same
way). It answers VARCHAR: with no argument the whole configuration as compact JSON, keys in the order they were
written; with a path (dot-separated keys, `[n]` indexes) the value it names — a string's content, a number as its
decimal value (`1.50` stays `1.50`, `1e2` becomes `1E+2`, in the whole configuration too), a boolean as written, an object or an array as compact JSON — and NULL for a path that names nothing or a graph
without a configuration.

## Cortex AI functions

Snowflake's Cortex functions, supplied by the optional **`frostlake-ai`** module (the engine itself
does not carry them — without that jar on the classpath they are unknown functions, which is also what
a Snowflake account without the feature answers). They are answered by a locally-hosted **Ollama**
model configured through `frostlake.properties`:

```properties
ai.ollama.url=http://localhost:11434     # base URL of the Ollama server
ai.ollama.model=llama3.2                 # model for the text functions
ai.ollama.embedModel=nomic-embed-text    # model for the embedding-backed functions
ai.ollama.timeoutMs=60000                # per-request timeout
```

Every key is also readable as a system property of the same name. Nothing contacts the server until
one of these functions is actually called.

**The names are not uniformly spelled, and this matters.** The original set exists ONLY under the
`SNOWFLAKE.CORTEX` schema — a bare `SENTIMENT('x')` is an unknown function on a real account, and is
one here too. The `AI_` family is reachable both ways. And `AI_SIMILARITY`, `AI_AGG` and
`AI_SUMMARIZE_AGG` go the other way again: bare only, with no `SNOWFLAKE.CORTEX` spelling at all. The
"Spelling" column below records which applies.

Every value a model produces is a model's opinion, not a computed answer: the same prompt can be
worded differently on two runs, and a different model will answer differently again. What is fixed is
the SHAPE — the return type, the JSON keys, NULL for a NULL input, and NULL for an empty group.

| Function | Spelling | Signature | Returns | Description |
| --- | --- | --- | --- | --- |
| `COMPLETE` | qualified | `COMPLETE(model, prompt [, options])` | VARCHAR | The model's reply as plain text. `model` names an Ollama model rather than a Snowflake-hosted one. |
| `TRY_COMPLETE` | qualified | `TRY_COMPLETE(model, prompt [, options])` | VARCHAR | `COMPLETE`, but a failed call is NULL instead of an error. |
| `SENTIMENT` | qualified | `SENTIMENT(text)` | FLOAT | Sentiment from -1 (negative) to 1 (positive). An unreadable reply settles at 0 rather than failing the row. |
| `SUMMARIZE` | qualified | `SUMMARIZE(text)` | VARCHAR | A short summary of the text. |
| `TRANSLATE` | qualified | `TRANSLATE(text, from, to)` | VARCHAR | The text in the target language. An empty `from` means "work it out". Not to be confused with the bare string function `TRANSLATE(subject, characters, translations)`, which is unaffected. |
| `EXTRACT_ANSWER` | qualified | `EXTRACT_ANSWER(text, question)` | ARRAY | The answer to the question, taken from the text, as an array of `{answer, score}` — read it as `EXTRACT_ANSWER(…)[0]:answer`. One candidate is returned. |
| `CLASSIFY_TEXT` | qualified | `CLASSIFY_TEXT(text, categories [, options])` | OBJECT | `{"label": "<one of the categories>"}`. The answer always comes from the list supplied. |
| `COUNT_TOKENS` | qualified | `COUNT_TOKENS(model, text)` | NUMBER | How many tokens the text costs. Counted locally (words and punctuation), deterministic and free — no model call. |
| `ENTITY_SENTIMENT` | qualified | `ENTITY_SENTIMENT(text [, entities])` | OBJECT | The earlier per-entity form of `AI_SENTIMENT`, answering with the same `categories` object. |
| `SPLIT_TEXT_RECURSIVE_CHARACTER` | qualified | `SPLIT_TEXT_RECURSIVE_CHARACTER(text, format, chunk_size [, overlap] [, separators])` | ARRAY | The text cut into chunks no longer than `chunk_size`, each carrying `overlap` characters of its predecessor. **No model call** — chunking is arithmetic over the text, so it is deterministic and free. `format` picks which boundaries are preferred (`none`, `markdown`, `html`, `latex`); an explicit separator array overrides it. |
| `SPLIT_TEXT_MARKDOWN_HEADER` | qualified | `SPLIT_TEXT_MARKDOWN_HEADER(text, headers_to_split_on, chunk_size [, overlap])` | ARRAY | Markdown cut at its headings into objects carrying the headings in force plus the text under `chunk`: `{'#': 'h1', '##': 'h2'}` yields `{"h1": "Title", "h2": "Sub", "chunk": "…"}`. A heading closes every deeper one. **No model call.** |
| `AI_SUMMARIZE` | qualified | `AI_SUMMARIZE(text [, options] [, return_error_details])` | VARCHAR | The newer spelling of SUMMARIZE, with the AI_ family's wider arity. |
| `AI_TRANSLATE` | qualified | `AI_TRANSLATE(text, from, to [, return_error_details])` | VARCHAR | The newer spelling of TRANSLATE. |
| `AI_COUNT_TOKENS` | qualified | `AI_COUNT_TOKENS(function_name, …)` | NUMBER | Prices a call to `function_name`, so where the text sits depends on it — after the model for `AI_COMPLETE`, immediately after the name for `AI_SENTIMENT`. Counted locally, like COUNT_TOKENS. |
| `EMBED_TEXT_768` | qualified | `EMBED_TEXT_768(model, text)` | VECTOR(FLOAT, 768) | The text as a vector, ready for `VECTOR_COSINE_SIMILARITY`. A shorter answer from the local model is zero-padded and a longer one truncated, so the value always matches its declared width. |
| `EMBED_TEXT_1024` | qualified | `EMBED_TEXT_1024(model, text)` | VECTOR(FLOAT, 1024) | The same, at 1024 dimensions. |
| `SEARCH_PREVIEW` | qualified | `SEARCH_PREVIEW(service_name, query_json)` | VARCHAR | Queries a Cortex search service; answers with the JSON text `{"results": [...], "request_id": "..."}`, ready for `PARSE_JSON(...)['results']`. The query object takes `query` (required), `columns` and `limit` (default 10). Ranked by embedding similarity against the service's search column. |
| `AI_COMPLETE` | both | `AI_COMPLETE(model, prompt [, options])` | VARCHAR | The newer spelling of `COMPLETE`. |
| `AI_CLASSIFY` | both | `AI_CLASSIFY(input, categories [, config])` | OBJECT | `{"labels": ["<category>"]}` — an ARRAY, where `CLASSIFY_TEXT` answers a single `label`. |
| `AI_FILTER` | both | `AI_FILTER(predicate [, value])` | BOOLEAN | TRUE or FALSE for a question asked in English, so a `WHERE` clause can read `WHERE AI_FILTER('is this about shipping?', body)`. A hedged answer is FALSE, never NULL — it is a predicate. |
| `AI_SENTIMENT` | both | `AI_SENTIMENT(text [, categories])` | OBJECT | Sentiment per aspect: `{"categories": [{"name": …, "sentiment": …}]}`, where sentiment is one of `positive`, `negative`, `neutral`, `mixed`, `unknown`. Without a category list the one entry is named `overall`. |
| `AI_SIMILARITY` | bare only | `AI_SIMILARITY(a, b)` | FLOAT | How alike two texts are, as the cosine similarity of their embeddings: -1 to 1, and 1 for a text against itself. |
| `AI_AGG` | bare only | `AI_AGG(text, instruction)` | VARCHAR | **Aggregate.** One answer across a group, under an instruction the caller writes: `AI_AGG(body, 'List the recurring themes in one sentence.')`. An empty group, and an all-NULL group, answer NULL. |
| `AI_SUMMARIZE_AGG` | bare only | `AI_SUMMARIZE_AGG(text)` | VARCHAR | **Aggregate.** One summary across every row of the group. An empty group, and an all-NULL group, answer NULL. |

The `SNOWFLAKE.CORTEX` schema carries 42 distinct names on a real account; the table above covers 21 of
them. The rest are out of scope in two groups. **Staged FILE or binary input** — `PARSE_DOCUMENT`,
`AI_PARSE_DOCUMENT`, `AI_TRANSCRIBE`, `DOCUMENT_EXTRACT`, `AI_MULTI_EMBED`. **Service and agent
orchestration, not a scalar in any local sense** — `AGENT_PREVIEW`, `AGENT_RUN`, `DATA_AGENT_RUN`,
`ANALYST_PREVIEW`, `CORTEX_SENSE_PREVIEW`, `REST_API`, `THREAD_MESSAGES`, `FINETUNE`.

Still open, and implementable: the dimensioned `AI_EMBED_512`/`_768`/`_1024` and
`AI_SIMILARITY_512`/`_768`/`_1024` spellings, `AI_REDACT` and `AI_EXTRACT`. Also unmodelled: the options
OBJECT that `COMPLETE`/`TRY_COMPLETE` take as a third argument, which live turns into an OBJECT result
carrying `choices` and `usage` — here they always answer plain text and ignore it.

### Cortex search services

The DDL lives in the ENGINE, not the pack — a service can be created, listed, described, altered and
dropped on a bare engine, and only `SEARCH_PREVIEW` against one needs `frostlake-ai`. This mirrors how
the engine keeps the GEOGRAPHY type surface while `frostlake-geo` supplies the functions.

```sql
CREATE [OR REPLACE] CORTEX SEARCH SERVICE [IF NOT EXISTS] <name>
  ON <search_column>
  [ATTRIBUTES <col> [, <col> …]]
  WAREHOUSE = <wh>
  TARGET_LAG = '<lag>'
  [EMBEDDING_MODEL = '<model>']
  [COMMENT = '<comment>']
  AS [(] <query> [)]

SHOW CORTEX SEARCH SERVICES [LIKE '<pattern>'] [IN {DATABASE | SCHEMA} <name>]
{DESCRIBE | DESC} CORTEX SEARCH SERVICE <name>
ALTER CORTEX SEARCH SERVICE [IF EXISTS] <name> {SET COMMENT = '<c>' | UNSET COMMENT}
ALTER CORTEX SEARCH SERVICE [IF EXISTS] <name> {SUSPEND | RESUME} [INDEXING | SERVING]
DROP CORTEX SEARCH SERVICE [IF EXISTS] <name>
```

`WAREHOUSE` and `TARGET_LAG` are required — a missing one is `Missing option(s): [WAREHOUSE]`, not a
syntax error — and the options may be written in any order. `ON` must follow the name. The searched
column and every attribute column must be projected by the defining query; one that is not is an
`invalid identifier`. Without `EMBEDDING_MODEL` the service reports Snowflake's default,
`snowflake-arctic-embed-m-v1.5`. `SHOW` answers with a real account's 20 columns.
`SUSPEND` and `RESUME` act on the layer they name, or on both without one; `SHOW` reports a suspended
layer's `indexing_state` / `serving_state` as `SUSPENDED` (a running one is `ACTIVE` / `RUNNING`).

Nothing is indexed ahead of time, so a `SEARCH_PREVIEW` embeds the service's rows on every call. That
is fine for the data volumes an emulator sees and would not be for a warehouse; the alternative,
keeping an index warm behind a SQL function, would mean a background indexer the engine deliberately
does not have.


## Temporal context functions: two renderings of one instant

The clock is read once per STATEMENT (live-verified), and that one instant is rendered two ways:

| Function | Zone | Type |
| --- | --- | --- |
| `CURRENT_TIMESTAMP` / `LOCALTIMESTAMP` / `CURRENT_DATE` / `CURRENT_TIME` / `GETDATE` / `NOW` | session | TIMESTAMP_LTZ (TIMESTAMP_NTZ for the date/time pair) |
| `SYSDATE` | **UTC** | TIMESTAMP_NTZ |

So `SYSDATE() = CURRENT_TIMESTAMP()` is FALSE unless the session is on UTC — measured with the session
on `America/Los_Angeles`, `SYSDATE()` answered `2026-08-07 13:57:23.484` against
`CURRENT_TIMESTAMP()`'s `2026-08-07 06:57:23.484 -0700`: the same instant, seven hours apart.

Within one statement every call agrees with itself — an `INSERT … SELECT CURRENT_TIMESTAMP()` over five
rows stores one distinct value, not five. Across statements the clock advances, including between two
statements of a single `BEGIN … COMMIT`.

## Naming a warehouse

A statement that names a warehouse is refused when that warehouse does not exist, phrased two ways:

```
CREATE TASK … WAREHOUSE = nosuch_wh           -> Nonexistent warehouse NOSUCH_WH was specified.
CREATE DYNAMIC TABLE … WAREHOUSE = nosuch_wh  -> Warehouse 'NOSUCH_WH' does not exist.
CREATE CORTEX SEARCH SERVICE … WAREHOUSE = …  -> Warehouse 'NOSUCH_WH' does not exist.
```

`CREATE USER … DEFAULT_WAREHOUSE = nosuch_wh` is **accepted** — it stores a preference rather than
naming a warehouse the statement uses, and live does not check it.


## User TYPE and which properties it may carry

Measured against a real account for all three types:

| Property | PERSON | SERVICE | LEGACY_SERVICE |
| --- | --- | --- | --- |
| `FIRST_NAME` / `MIDDLE_NAME` / `LAST_NAME` | ok | refused | refused |
| `PASSWORD` | ok | refused | **ok** |
| `MUST_CHANGE_PASSWORD` | ok | refused | **ok** |
| `EMAIL`, `DISPLAY_NAME`, `DEFAULT_WAREHOUSE`, `COMMENT` | ok | ok | ok |

The two service types are not one set — a legacy service user keeping its password is the point of the
type. The refusal is the same sentence at CREATE and at ALTER, naming the type it applies to:

```
SQL execution error: Cannot set FIRST_NAME on users with TYPE=LEGACY_SERVICE.
```

## Aliases

The following names are Snowflake-compatible aliases that behave identically to an existing function:

| Alias | Equivalent to |
| --- | --- |
| `TRUNCATE` | `TRUNC` |
| `VECTOR_TRUNCATE` | `VECTOR_TRUNC` |
| `REGEXP_EXTRACT_ALL` | `REGEXP_SUBSTR_ALL` |
| `RLIKE` | `REGEXP_LIKE` (also usable as the infix operators `<subject> [NOT] RLIKE <pattern>` and `<subject> [NOT] REGEXP <pattern>`) |
| `POW` | `POWER` |
| `VARIANCE_POP` | `VAR_POP` (aggregate) |
| `VARIANCE_SAMP` | `VAR_SAMP` (aggregate) |
| `DATE` | `TO_DATE` — but unlike `TO_DATE` it also accepts an epoch NUMBER |
| `TIMESTAMPADD` | `DATEADD` (also `TIMEADD`) |
| `TIMESTAMPDIFF` | `DATEDIFF` (also `TIMEDIFF`) |
| `DAYOFMONTH` | `DAY` |
| `LOCALTIME` | `CURRENT_TIME` |
| `SYSTIMESTAMP` | `SYSDATE` |
| `BIT_AND` / `BIT_OR` / `BIT_XOR` / `BIT_NOT` / `BIT_SHIFTLEFT` / `BIT_SHIFTRIGHT` | `BITAND` / `BITOR` / `BITXOR` / `BITNOT` / `BITSHIFTLEFT` / `BITSHIFTRIGHT` |
| `ARRAYAGG` / `OBJECTAGG` | `ARRAY_AGG` / `OBJECT_AGG` |
| `HLL` / `APPROXIMATE_COUNT_DISTINCT` | `APPROX_COUNT_DISTINCT` |
| `BITANDAGG` / `BIT_ANDAGG` / `BIT_AND_AGG` (and OR/XOR forms) | `BITAND_AGG` family |
| `DATEFROMPARTS` / `TIMEFROMPARTS` / `TIMESTAMPFROMPARTS` (+ `NTZ`/`LTZ`/`TZ` and underscore forms) | `DATE_FROM_PARTS` / `TIME_FROM_PARTS` / `TIMESTAMP_FROM_PARTS` |
| `TRY_TO_TIMESTAMP_NTZ` | `TRY_TO_TIMESTAMP` |
