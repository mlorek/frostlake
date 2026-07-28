# Copyright 2026 MLorek
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Pure-Python emulation of the `snowflake.snowpark` client API, installed into sys.modules by the
# engine before a Python procedure body runs. Snowflake stored procedures import snowpark and receive
# a Session; locally the same imports resolve here, and the Session delegates every SQL round trip to
# the engine through the bound Java facade (columnNames()/columnTypes()/data() on its result set).
#
# The DataFrame is EAGER underneath: a pending SQL text is executed on first use and every transform
# (select/join/union/...) operates on materialized rows in Python. Snowpark's laziness is observable
# only through side-effect ordering, which the procedures do not rely on beyond "session.sql(...) runs
# by .collect()". Coverage is the surface the vendor procedures actually use; anything else raises a
# clear error naming the missing piece rather than failing obscurely.

import builtins as _builtins
import re as _re
import datetime as _datetime
import json as _json
import math as _math
import sys as _sys
import types as _types_mod


def _frostlake_install_snowpark():
    _sf_mod = _types_mod.ModuleType('snowflake')
    _sp_mod = _types_mod.ModuleType('snowflake.snowpark')
    _fn_mod = _types_mod.ModuleType('snowflake.snowpark.functions')
    _ty_mod = _types_mod.ModuleType('snowflake.snowpark.types')
    _se_mod = _types_mod.ModuleType('snowflake.snowpark.session')
    _ex_mod = _types_mod.ModuleType('snowflake.snowpark.exceptions')

    # ---------------------------------------------------------------- types --

    class DataType:
        def __repr__(self):
            return type(self).__name__ + '()'

    class StringType(DataType):
        def __init__(self, length=None):
            self.length = length

    class BinaryType(DataType):
        pass

    class BooleanType(DataType):
        pass

    class ByteType(DataType):
        pass

    class ShortType(DataType):
        pass

    class IntegerType(DataType):
        pass

    class LongType(DataType):
        pass

    class FloatType(DataType):
        pass

    class DoubleType(DataType):
        pass

    class DecimalType(DataType):
        def __init__(self, precision=38, scale=0):
            self.precision = precision
            self.scale = scale

    class DateType(DataType):
        pass

    class TimeType(DataType):
        pass

    class TimestampTimeZone:
        DEFAULT = 'default'
        NTZ = 'ntz'
        LTZ = 'ltz'
        TZ = 'tz'

    class TimestampType(DataType):
        def __init__(self, timezone=TimestampTimeZone.DEFAULT):
            self.tz = timezone

    class NullType(DataType):
        pass

    class VariantType(DataType):
        pass

    class ArrayType(DataType):
        def __init__(self, element_type=None, structured=False):
            self.element_type = element_type
            self.structured = structured

    class MapType(DataType):
        def __init__(self, key_type=None, value_type=None, structured=False):
            self.key_type = key_type
            self.value_type = value_type
            self.structured = structured

    class StructField:
        def __init__(self, column_identifier, datatype, nullable=True):
            self.name = column_identifier
            self.datatype = datatype
            self.nullable = nullable

        def __repr__(self):
            return 'StructField(%r, %r)' % (self.name, self.datatype)

    class StructType(DataType):
        def __init__(self, fields=None):
            self.fields = list(fields or [])

        def __iter__(self):
            return iter(self.fields)

        def __len__(self):
            return len(self.fields)

        @property
        def names(self):
            return [f.name for f in self.fields]

    def _sql_type_to_datatype(sql_name):
        n = (sql_name or '').upper()
        if n.startswith('TIMESTAMP'):
            return TimestampType()
        if n in ('VARCHAR', 'CHAR', 'STRING', 'TEXT'):
            return StringType()
        if n in ('NUMBER', 'INT', 'INTEGER', 'BIGINT', 'SMALLINT', 'TINYINT', 'BYTEINT', 'DECIMAL', 'NUMERIC'):
            return LongType()
        if n in ('FLOAT', 'FLOAT4', 'FLOAT8', 'DOUBLE', 'REAL'):
            return DoubleType()
        if n == 'BOOLEAN':
            return BooleanType()
        if n == 'DATE':
            return DateType()
        if n == 'TIME':
            return TimeType()
        if n == 'VARIANT':
            return VariantType()
        if n == 'ARRAY':
            return ArrayType()
        if n == 'OBJECT':
            return MapType(StringType(), VariantType())
        if n == 'BINARY':
            return BinaryType()
        return StringType()

    # ------------------------------------------------------------ exceptions --

    class SnowparkClientException(Exception):
        def __init__(self, message, error_code=None):
            super().__init__(message)
            self.message = message
            self.error_code = error_code

    class SnowparkSQLException(SnowparkClientException):
        pass

    # ------------------------------------------------------- value plumbing --

    def _parse_timestamp(value):
        if value is None or isinstance(value, _datetime.datetime):
            return value
        if isinstance(value, (int, float)):
            # Snowflake's numeric-to-timestamp auto-detection: large magnitudes are epoch millis.
            seconds = value / 1000.0 if abs(value) >= 31536000000 else float(value)
            return _datetime.datetime.fromtimestamp(seconds, _datetime.timezone.utc).replace(tzinfo=None)
        text = str(value).strip()
        if not text:
            return None
        try:
            return _datetime.datetime.fromisoformat(text.replace('Z', '+00:00')).replace(tzinfo=None)
        except ValueError:
            return value

    def _parse_date(value):
        if value is None or isinstance(value, _datetime.date):
            return value
        try:
            return _datetime.date.fromisoformat(str(value)[:10])
        except ValueError:
            return value

    def _from_variant_text(value):
        if not isinstance(value, str):
            return value
        text = value.strip()
        if text == '' or text == 'null':
            return None
        try:
            return _json.loads(text)
        except ValueError:
            return value

    def _from_sql_value(value, sql_type):
        n = (sql_type or '').upper()
        if n in ('VARIANT', 'ARRAY', 'OBJECT'):
            return _from_variant_text(value)
        if n.startswith('TIMESTAMP'):
            return _parse_timestamp(value)
        if n == 'DATE':
            return _parse_date(value)
        return value

    def _json_ready(value):
        if isinstance(value, (_datetime.datetime, _datetime.date)):
            return str(value)
        return str(value)

    def _sql_string_literal(text):
        return "'" + text.replace('\\', '\\\\').replace("'", "''") + "'"

    def _sql_literal(value):
        if value is None:
            return 'NULL'
        if isinstance(value, bool):
            return 'TRUE' if value else 'FALSE'
        if isinstance(value, int):
            return str(value)
        if isinstance(value, float):
            if _math.isnan(value) or _math.isinf(value):
                return 'NULL'
            if value == int(value) and abs(value) < 1e15:
                return str(int(value))
            return repr(value)
        if isinstance(value, _datetime.datetime):
            return _sql_string_literal(value.strftime('%Y-%m-%d %H:%M:%S.%f')) + '::TIMESTAMP_NTZ'
        if isinstance(value, _datetime.date):
            return _sql_string_literal(value.isoformat()) + '::DATE'
        if isinstance(value, (list, tuple, dict)):
            return 'PARSE_JSON(' + _sql_string_literal(_json.dumps(value, default=_json_ready)) + ')'
        if hasattr(value, 'item'):
            # numpy scalar
            return _sql_literal(value.item())
        return _sql_string_literal(str(value))

    def _hash_key(value):
        if isinstance(value, (list, tuple, dict)):
            return _json.dumps(value, sort_keys=True, default=_json_ready)
        return value

    def _apply_type(dtype, value):
        if value is None or dtype is None:
            return value
        if isinstance(dtype, StringType):
            if isinstance(value, str):
                return value
            if isinstance(value, bool):
                return 'true' if value else 'false'
            if isinstance(value, float) and value == int(value) and not _math.isinf(value):
                return str(int(value))
            if isinstance(value, (dict, list)):
                return _json.dumps(value, default=_json_ready)
            return str(value)
        if isinstance(dtype, (IntegerType, LongType, ByteType, ShortType)):
            try:
                return int(value)
            except (TypeError, ValueError):
                try:
                    return int(float(value))
                except (TypeError, ValueError):
                    return None
        if isinstance(dtype, (FloatType, DoubleType, DecimalType)):
            try:
                return float(value)
            except (TypeError, ValueError):
                return None
        if isinstance(dtype, BooleanType):
            return bool(value)
        if isinstance(dtype, TimestampType):
            return _parse_timestamp(value)
        if isinstance(dtype, DateType):
            return _parse_date(value)
        if isinstance(dtype, ArrayType):
            if isinstance(value, str):
                value = _from_variant_text(value)
            if value is None:
                return None
            if not isinstance(value, list):
                value = list(value)
            if dtype.element_type is not None and isinstance(dtype.element_type, StringType):
                out = []
                for element in value:
                    if element is None:
                        out.append(None)
                    elif isinstance(element, str):
                        out.append(element)
                    elif isinstance(element, (dict, list)):
                        out.append(_json.dumps(element, default=_json_ready))
                    else:
                        out.append(str(element))
                return out
            return value
        return value

    # ----------------------------------------------------------------- Row --

    class Row:
        def __init__(self, names, values):
            object.__setattr__(self, '_names', list(names))
            object.__setattr__(self, '_values', list(values))

        def __getitem__(self, key):
            if isinstance(key, int):
                return self._values[key]
            target = str(key).upper()
            for i, name in enumerate(self._names):
                if name.upper() == target:
                    return self._values[i]
            raise KeyError(key)

        def __getattr__(self, name):
            if name.startswith('_'):
                raise AttributeError(name)
            try:
                return self[name]
            except KeyError:
                raise AttributeError(name)

        def __len__(self):
            return len(self._values)

        def __iter__(self):
            return iter(self._values)

        def as_dict(self, recursive=False):
            return dict(zip(self._names, self._values))

        def asDict(self, recursive=False):
            return self.as_dict(recursive)

        def __repr__(self):
            parts = []
            for name, value in zip(self._names, self._values):
                parts.append('%s=%r' % (name, value))
            return 'Row(%s)' % ', '.join(parts)

    # -------------------------------------------------------------- Column --

    _EXPR_COUNTER = [0]
    _TMP_COUNTER = [0]

    class Column:
        """A per-row expression: `evaluate(env)` computes the value from a dict of UPPER name -> value."""

        def __init__(self, evaluate, name=None):
            self._evaluate = evaluate
            self._name = name

        def _out_name(self):
            if self._name:
                return self._name.upper()
            _EXPR_COUNTER[0] += 1
            return 'EXPR_%d' % _EXPR_COUNTER[0]

        def __getitem__(self, key):
            def _field(env, self=self, key=key):
                base = self._evaluate(env)
                if base is None:
                    return None
                if isinstance(base, dict):
                    return base.get(key)
                if isinstance(base, (list, tuple)) and isinstance(key, int):
                    return base[key] if 0 <= key < len(base) else None
                return None
            return Column(_field, self._name)

        def cast(self, dtype):
            def _cast(env, self=self, dtype=dtype):
                return _apply_type(dtype, self._evaluate(env))
            return Column(_cast, self._name)

        def astype(self, dtype):
            return self.cast(dtype)

        def alias(self, name):
            out = Column(self._evaluate, name)
            if hasattr(self, '_agg'):
                out._agg = self._agg
            return out

        def over(self, window_spec=None):
            if getattr(self, '_window_fn', None) is None and getattr(self, '_agg', None) is None:
                raise SnowparkClientException(
                    '.over(...) is only supported on row_number() and aggregate columns in the local engine')

            def _fail(env):
                raise SnowparkClientException('window columns are only usable in with_column in the local engine')
            out = Column(_fail, self._name)
            if getattr(self, '_window_fn', None) is not None:
                out._window_fn = self._window_fn
            if getattr(self, '_agg', None) is not None:
                out._agg_window = self._agg
            out._window_spec = window_spec if window_spec is not None else WindowSpec([], [])
            return out

        def __call__(self, *args, **kwargs):
            raise SnowparkClientException(
                "'" + str(self._name) + "' resolved to a Column, not a method — this DataFrame method is "
                "not supported in the local engine")

        def as_(self, name):
            return self.alias(name)

        def name(self, name):
            return self.alias(name)

        def _binary(self, other, op):
            def _cmp(env, self=self, other=other, op=op):
                left = self._evaluate(env)
                right = other._evaluate(env) if isinstance(other, Column) else other
                if op in ('==', '!='):
                    if left is None or right is None:
                        return None
                    return (left == right) if op == '==' else (left != right)
                if op in ('&', '|'):
                    if op == '&':
                        return bool(left) and bool(right) if left is not None and right is not None else None
                    return bool(left) or bool(right) if left is not None and right is not None else None
                if left is None or right is None:
                    return None
                if op == '>':
                    return left > right
                if op == '>=':
                    return left >= right
                if op == '<':
                    return left < right
                return left <= right
            return Column(_cmp)

        def __eq__(self, other):
            return self._binary(other, '==')

        def __ne__(self, other):
            return self._binary(other, '!=')

        def __and__(self, other):
            return self._binary(other, '&')

        def __or__(self, other):
            return self._binary(other, '|')

        def __gt__(self, other):
            return self._binary(other, '>')

        def __ge__(self, other):
            return self._binary(other, '>=')

        def __lt__(self, other):
            return self._binary(other, '<')

        def __le__(self, other):
            return self._binary(other, '<=')

        def _arith(self, other, op, reflected=False):
            def _calc(env, self=self, other=other, op=op, reflected=reflected):
                a = self._evaluate(env)
                b = other._evaluate(env) if isinstance(other, Column) else other
                if reflected:
                    a, b = b, a
                if a is None or b is None:
                    return None
                if op == '+':
                    return a + b
                if op == '-':
                    return a - b
                if op == '*':
                    return a * b
                return a / b
            return Column(_calc)

        def __add__(self, other):
            return self._arith(other, '+')

        def __radd__(self, other):
            return self._arith(other, '+', True)

        def __sub__(self, other):
            return self._arith(other, '-')

        def __rsub__(self, other):
            return self._arith(other, '-', True)

        def __mul__(self, other):
            return self._arith(other, '*')

        def __rmul__(self, other):
            return self._arith(other, '*', True)

        def __truediv__(self, other):
            return self._arith(other, '/')

        def __rtruediv__(self, other):
            return self._arith(other, '/', True)

        def __invert__(self):
            def _not(env, self=self):
                value = self._evaluate(env)
                return None if value is None else not bool(value)
            return Column(_not)

        def __hash__(self):
            return id(self)

        def is_null(self):
            def _isnull(env, self=self):
                return self._evaluate(env) is None
            return Column(_isnull)

        def isNull(self):
            return self.is_null()

    class CaseColumn(Column):
        """when(cond, val)[.when(...)].otherwise(default) — evaluated per row, first match wins."""

        def __init__(self, branches, default=None, name=None):
            self._branches = branches
            self._default = default

            def _case(env, branches=branches, default=default):
                for cond, val in branches:
                    c = cond._evaluate(env) if isinstance(cond, Column) else cond
                    if c:
                        return val._evaluate(env) if isinstance(val, Column) else val
                return default._evaluate(env) if isinstance(default, Column) else default
            Column.__init__(self, _case, name)

        def when(self, condition, value):
            return CaseColumn(self._branches + [(condition, value)], self._default, self._name)

        def otherwise(self, value):
            return CaseColumn(self._branches, value, self._name)

    class _Star:
        """`df.col('*')` / select('*'): expand to every column of the DataFrame being selected."""
        pass

    def _column_ref(name):
        target = str(name).upper()

        def _ref(env, target=target):
            return env.get(target)
        return Column(_ref, target)

    def _to_column(value):
        if isinstance(value, Column) or isinstance(value, _Star):
            return value
        if isinstance(value, str):
            if value == '*':
                return _Star()
            return _column_ref(value)
        raise SnowparkClientException('Cannot use %r as a column' % (value,))

    def _parse_simple_condition(text):
        """`<column> <op> <literal>` over local rows (e.g. "row_number = 1"); None when not that shape."""
        m = _re.match(r"^\s*([A-Za-z_][A-Za-z0-9_]*)\s*(=|!=|<>|>=|<=|>|<)\s*('[^']*'|-?\d+(?:\.\d+)?)\s*$", text)
        if m is None:
            return None
        column = _column_ref(m.group(1))
        op = m.group(2)
        raw = m.group(3)
        if raw.startswith("'"):
            value = raw[1:-1]
        elif '.' in raw:
            value = _builtins.float(raw)
        else:
            value = _builtins.int(raw)
        if op == '=':
            return column == value
        if op in ('!=', '<>'):
            return column != value
        if op == '>':
            return column > value
        if op == '>=':
            return column >= value
        if op == '<':
            return column < value
        return column <= value

    # ------------------------------------------------------------ functions --

    def col(name):
        return _to_column(name)

    def lit(value):
        def _lit(env, value=value):
            return value
        return Column(_lit, None)

    def parse_json(column):
        inner = _to_column(column)

        def _parse(env, inner=inner):
            return _from_variant_text(inner._evaluate(env))
        return Column(_parse, None)

    def coalesce(*columns):
        cols = [_to_column(c) for c in columns]

        def _coalesce(env, cols=cols):
            for c in cols:
                value = c._evaluate(env)
                if value is not None:
                    return value
            return None
        return Column(_coalesce, None)

    def array_construct(*columns):
        cols = [_to_column(c) for c in columns]

        def _array(env, cols=cols):
            return [c._evaluate(env) for c in cols]
        return Column(_array, None)

    def current_timestamp():
        def _now(env):
            return _datetime.datetime.now()
        return Column(_now, None)

    def array_max(column):
        inner = _to_column(column)

        def _amax(env, inner=inner):
            value = inner._evaluate(env)
            if isinstance(value, str):
                value = _from_variant_text(value)
            if not value:
                return None
            non_null = [v for v in value if v is not None]
            return max(non_null) if non_null else None
        return Column(_amax, None)

    def array_size(column):
        inner = _to_column(column)

        def _asize(env, inner=inner):
            value = inner._evaluate(env)
            if isinstance(value, str):
                value = _from_variant_text(value)
            return None if value is None else len(value)
        return Column(_asize, None)

    def array_contains(variant, array_column):
        inner = _to_column(array_column)

        def _contains(env, inner=inner, variant=variant):
            probe = variant._evaluate(env) if isinstance(variant, Column) else variant
            value = inner._evaluate(env)
            if isinstance(value, str):
                value = _from_variant_text(value)
            return None if value is None else probe in value
        return Column(_contains, None)

    def array_intersection(a, b):
        col_a = _to_column(a)
        col_b = _to_column(b)

        def _intersect(env, col_a=col_a, col_b=col_b):
            left = col_a._evaluate(env)
            right = col_b._evaluate(env)
            if isinstance(left, str):
                left = _from_variant_text(left)
            if isinstance(right, str):
                right = _from_variant_text(right)
            if left is None or right is None:
                return None
            right_keys = [_hash_key(v) for v in right]
            return [v for v in left if _hash_key(v) in right_keys]
        return Column(_intersect, None)

    def to_variant(column):
        inner = _to_column(column)
        return Column(inner._evaluate, inner._name)

    def is_null(column):
        return _to_column(column).is_null()

    def when(condition, value):
        return CaseColumn([(condition, value)])

    def least(*columns):
        cols = [_to_column(c) for c in columns]

        def _least(env, cols=cols):
            values = [c._evaluate(env) for c in cols]
            for v in values:
                if v is None:
                    return None
            return _builtins.min(values)
        return Column(_least, None)

    def upper(column):
        inner = _to_column(column)

        def _upper(env, inner=inner):
            v = inner._evaluate(env)
            return None if v is None else _builtins.str(v).upper()
        return Column(_upper, None)

    def object_construct_keep_null(*columns):
        cols = [(_to_column(c) if not isinstance(c, str) or True else c) for c in columns]
        # snowpark passes alternating key, value expressions; keys are usually lit('name').
        pairs = [_to_column(c) for c in columns]

        def _obj(env, pairs=pairs):
            out = {}
            for i in range(0, _builtins.len(pairs) - 1, 2):
                key = pairs[i]._evaluate(env)
                out[_builtins.str(key)] = pairs[i + 1]._evaluate(env)
            return out
        return Column(_obj, None)

    def approx_percentile(column, percentile):
        inner = _to_column(column)

        def _fail(env):
            raise SnowparkClientException('approx_percentile is only usable inside group_by(...).agg(...)')
        out = Column(_fail, None)
        out._agg = ('approx_percentile', inner, _builtins.float(percentile))
        return out

    def row_number():
        def _fail(env):
            raise SnowparkClientException('row_number() must be applied with .over(Window...)')
        out = Column(_fail, None)
        out._window_fn = 'row_number'
        return out

    def flatten(*args, **kwargs):
        # Importable so handler modules load; the DataFrame surface has no lateral-flatten join.
        raise SnowparkClientException(
            'snowpark flatten() is not supported in the local engine; run the flatten in SQL via session.sql')

    def _aggregate_factory(fn_name):
        def _factory(column):
            inner = _to_column(column)

            def _fail(env, fn_name=fn_name):
                raise SnowparkClientException(
                    "snowpark aggregate '" + fn_name + "' is only usable inside group_by(...).agg(...) in the local engine")
            out = Column(_fail, None)
            out._agg = (fn_name, inner)
            return out
        return _factory

    def _combine_aggregate(fn_name, values, extra=None):
        # Explicit builtins: proc bodies run in the SAME eval globals and `from ...functions import
        # max` rebinds the bare name to the shim's own aggregate factory.
        present = [v for v in values if v is not None]
        if fn_name == 'count':
            return _builtins.len(present)
        if not present:
            return None
        if fn_name == 'max':
            return _builtins.max(present)
        if fn_name == 'min':
            return _builtins.min(present)
        if fn_name == 'sum':
            return _builtins.sum(present)
        if fn_name == 'approx_percentile':
            ordered = sorted(present)
            idx = _builtins.int(_builtins.round((_builtins.len(ordered) - 1) * (extra or 0.5)))
            return ordered[idx]
        return _builtins.sum(present) / _builtins.float(_builtins.len(present))

    # ---------------------------------------------------------------- UDTF --

    class UserDefinedTableFunction:
        def __init__(self, handler, output_schema, input_types=None, name=None):
            self.handler = handler
            self.output_schema = output_schema
            self.input_types = list(input_types or [])
            self.name = name

        def __call__(self, *cols):
            return TableFunctionCall(self, [_to_column(c) for c in cols])

    class TableFunctionCall:
        def __init__(self, udtf_def, arg_columns):
            self.udtf_def = udtf_def
            self.arg_columns = arg_columns

    def udtf(handler=None, output_schema=None, input_types=None, name=None, **kwargs):
        if handler is None:
            def _decorator(cls):
                return UserDefinedTableFunction(cls, output_schema, input_types, name)
            return _decorator
        return UserDefinedTableFunction(handler, output_schema, input_types, name)

    # ----------------------------------------------------------- DataFrame --

    class GroupedFrame:
        def __init__(self, dataframe, keys):
            self._df = dataframe
            self._keys = keys

        def count(self):
            """COUNT(*) per group, as snowpark's group_by(...).count(): keys + a COUNT column."""
            df = self._df
            key_idx = [df._index_of(k) for k in self._keys]
            groups = {}
            order = []
            for row in df._rows:
                key = tuple(_hash_key(row[i]) for i in key_idx)
                if key not in groups:
                    groups[key] = []
                    order.append(key)
                groups[key].append(row)
            names = [df._names[i] for i in key_idx] + ['COUNT']
            rows = []
            for key in order:
                members = groups[key]
                rows.append([members[0][i] for i in key_idx] + [_builtins.len(members)])
            return DataFrame(df._session, names=names, types=[None] * _builtins.len(names), rows=rows)

        def agg(self, *agg_columns):
            df = self._df
            key_idx = [df._index_of(k) for k in self._keys]
            aggs = []
            for a in agg_columns:
                if not hasattr(a, '_agg'):
                    raise SnowparkClientException(
                        'group_by(...).agg(...) supports max/min/sum/avg/count columns in the local engine')
                aggs.append(a)
            groups = {}
            order = []
            for row in df._rows:
                key = tuple(_hash_key(row[i]) for i in key_idx)
                if key not in groups:
                    groups[key] = []
                    order.append(key)
                groups[key].append(row)
            names = [df._names[i] for i in key_idx] + [a._out_name() for a in aggs]
            rows = []
            for key in order:
                members = groups[key]
                out = [members[0][i] for i in key_idx]
                for a in aggs:
                    tag = a._agg
                    fn_name = tag[0]
                    inner = tag[1]
                    extra = tag[2] if _builtins.len(tag) > 2 else None
                    values = [inner._evaluate(df._env(m)) for m in members]
                    out.append(_combine_aggregate(fn_name, values, extra))
                rows.append(out)
            return DataFrame(df._session, names=names, types=[None] * len(names), rows=rows)

    class DataFrameWriter:
        def __init__(self, dataframe):
            self._dataframe = dataframe
            self._mode = 'errorifexists'

        def mode(self, save_mode):
            self._mode = str(save_mode).lower()
            return self

        def save_as_table(self, table_name, mode=None, column_order=None, table_type=None, **kwargs):
            df = self._dataframe
            df._materialize()
            session = df._session
            effective_mode = (mode or self._mode).lower()
            if effective_mode == 'overwrite':
                try:
                    session._execute('TRUNCATE TABLE ' + table_name)
                except BaseException:
                    session._execute(_create_table_sql(table_name, df))
            elif not session._table_exists(table_name):
                session._execute(_create_table_sql(table_name, df))
            if not df._rows:
                return
            columns = ', '.join(df._names)
            batch = []
            for row in df._rows:
                rendered = []
                for value in row:
                    rendered.append(_sql_literal(value))
                batch.append('SELECT ' + ', '.join(rendered))
                if len(batch) >= 200:
                    session._execute('INSERT INTO %s (%s) %s' % (table_name, columns, ' UNION ALL '.join(batch)))
                    batch = []
            if batch:
                session._execute('INSERT INTO %s (%s) %s' % (table_name, columns, ' UNION ALL '.join(batch)))

        def saveAsTable(self, table_name, mode=None, column_order=None, table_type=None, **kwargs):
            return self.save_as_table(table_name, mode, column_order, table_type, **kwargs)

    def _python_type_to_sql(values):
        for value in values:
            if value is None:
                continue
            if isinstance(value, bool):
                return 'BOOLEAN'
            if isinstance(value, int):
                return 'NUMBER'
            if isinstance(value, float):
                return 'FLOAT'
            if isinstance(value, _datetime.datetime):
                return 'TIMESTAMP_NTZ'
            if isinstance(value, _datetime.date):
                return 'DATE'
            if isinstance(value, (list, dict)):
                return 'VARIANT'
            return 'VARCHAR'
        return 'VARCHAR'

    def _create_table_sql(table_name, df):
        parts = []
        for i, name in enumerate(df._names):
            column_values = [row[i] for row in df._rows]
            parts.append('%s %s' % (name, _python_type_to_sql(column_values)))
        return 'CREATE TABLE IF NOT EXISTS %s (%s)' % (table_name, ', '.join(parts))

    class DataFrame:
        def __init__(self, session, sql=None, names=None, types=None, rows=None):
            self._session = session
            self._pending_sql = sql
            self._names = names
            self._types = types
            self._rows = rows

        # -- plumbing --

        def _materialize(self):
            if self._pending_sql is None:
                return self
            sql = self._pending_sql
            self._pending_sql = None
            names, sql_types, rows = self._session._query(sql)
            self._names = names
            self._types = sql_types
            self._rows = rows
            return self

        def _env(self, row):
            return dict(zip(self._names, row))

        def _derived(self, names, types, rows):
            return DataFrame(self._session, names=list(names), types=list(types), rows=rows)

        def _sql_source(self):
            """SQL text producing this frame: the pending query, or — once rows are local — a
            session-temporary table the rows are spilled into, so SQL-string operations
            (select_expr, complex where) can always compose."""
            if self._pending_sql is not None:
                return self._pending_sql
            self._materialize()
            _TMP_COUNTER[0] += 1
            name = '__shim_df_' + _builtins.str(_TMP_COUNTER[0])
            self._session._execute(
                'CREATE OR REPLACE TEMPORARY TABLE ' + name + ' ('
                + ', '.join('%s %s' % (n, _python_type_to_sql([row[i] for row in self._rows]))
                            for i, n in enumerate(self._names)) + ')')
            DataFrameWriter(self).mode('append').save_as_table(name)
            return 'SELECT * FROM ' + name

        # -- metadata --

        @property
        def columns(self):
            self._materialize()
            return list(self._names)

        @property
        def schema(self):
            self._materialize()
            fields = []
            for name, sql_type in zip(self._names, self._types):
                fields.append(StructField(name, _sql_type_to_datatype(sql_type)))
            return StructType(fields)

        def col(self, name):
            return _to_column(name)

        def __getattr__(self, name):
            if name.startswith('_') or name.startswith('__'):
                raise AttributeError(name)
            return _column_ref(name)

        def __getitem__(self, name):
            return _column_ref(name)

        def with_column(self, name, column):
            self._materialize()
            col = _to_column(column)
            if getattr(col, '_window_spec', None) is not None:
                return self._with_window_column(str(name).upper(), col)
            target = str(name).upper()
            names = list(self._names)
            types = list(self._types)
            replace_at = -1
            for i, n in enumerate(names):
                if n.upper() == target:
                    replace_at = i
                    break
            rows = []
            for row in self._rows:
                value = col._evaluate(self._env(row))
                out = list(row)
                if replace_at >= 0:
                    out[replace_at] = value
                else:
                    out.append(value)
                rows.append(out)
            if replace_at < 0:
                names.append(target)
                types.append(None)
            return self._derived(names, types, rows)

        def withColumn(self, name, column):
            return self.with_column(name, column)

        def _with_window_column(self, target, col):
            spec = col._window_spec
            part_idx = [self._index_of(k) for k in spec.partition_keys]
            order_idx = [self._index_of(k) for k in spec.order_keys]
            groups = {}
            for i, row in enumerate(self._rows):
                key = tuple(_hash_key(row[j]) for j in part_idx)
                groups.setdefault(key, []).append(i)
            values = [None] * _builtins.len(self._rows)

            def _order_key(i):
                out = []
                for j in order_idx:
                    v = self._rows[i][j]
                    out.append((v is None, v))
                return out
            if getattr(col, '_agg_window', None) is not None:
                # Windowed aggregate: the partition's combined value on EVERY member row.
                tag = col._agg_window
                fn_name = tag[0]
                inner = tag[1]
                extra = tag[2] if _builtins.len(tag) > 2 else None
                for indices in groups.values():
                    member_values = [inner._evaluate(self._env(self._rows[i])) for i in indices]
                    combined = _combine_aggregate(fn_name, member_values, extra)
                    for i in indices:
                        values[i] = combined
                names = list(self._names) + [target]
                types = list(self._types) + [None]
                rows = []
                for i, row in enumerate(self._rows):
                    rows.append(list(row) + [values[i]])
                return self._derived(names, types, rows)
            for indices in groups.values():
                ordered = sorted(indices, key=_order_key)
                for rank, i in enumerate(ordered):
                    values[i] = rank + 1
            names = list(self._names) + [target]
            types = list(self._types) + [None]
            rows = []
            for i, row in enumerate(self._rows):
                rows.append(list(row) + [values[i]])
            return self._derived(names, types, rows)

        def union_by_name(self, other):
            self._materialize()
            other._materialize()
            indices = [other._index_of(n) for n in self._names]
            rows = [list(r) for r in self._rows]
            for row in other._rows:
                rows.append([row[i] for i in indices])
            return self._derived(self._names, self._types, rows)

        def unionByName(self, other):
            return self.union_by_name(other)

        def group_by(self, *keys):
            self._materialize()
            flat = []
            for k in keys:
                if isinstance(k, (list, tuple)):
                    flat.extend(k)
                else:
                    flat.append(k)
            return GroupedFrame(self, [str(k) for k in flat])

        def groupBy(self, *keys):
            return self.group_by(*keys)

        # -- actions --

        def collect(self, **kwargs):
            self._materialize()
            return [Row(self._names, row) for row in self._rows]

        def count(self):
            self._materialize()
            return len(self._rows)

        def first(self):
            self._materialize()
            return Row(self._names, self._rows[0]) if self._rows else None

        def to_pandas(self, **kwargs):
            self._materialize()
            import pandas
            return pandas.DataFrame(data=[list(r) for r in self._rows], columns=list(self._names))

        def toPandas(self, **kwargs):
            return self.to_pandas(**kwargs)

        def show(self, n=10, **kwargs):
            self._materialize()
            for row in self._rows[:n]:
                print(dict(zip(self._names, row)))

        def cache_result(self):
            self._materialize()
            return self

        def cacheResult(self):
            return self.cache_result()

        # -- transformations --

        def select(self, *exprs):
            self._materialize()
            if len(exprs) == 1 and isinstance(exprs[0], (list, tuple)):
                exprs = tuple(exprs[0])
            if len(exprs) == 1 and isinstance(exprs[0], TableFunctionCall):
                return self._apply_udtf(exprs[0])
            columns = [_to_column(e) for e in exprs]
            out_names = []
            evaluators = []
            for c in columns:
                if isinstance(c, _Star):
                    for i, name in enumerate(self._names):
                        out_names.append(name)
                        evaluators.append(_column_ref(name))
                else:
                    out_names.append(c._out_name())
                    evaluators.append(c)
            out_rows = []
            for row in self._rows:
                env = self._env(row)
                out_rows.append([e._evaluate(env) for e in evaluators])
            return self._derived(out_names, [None] * len(out_names), out_rows)

        def _apply_udtf(self, call):
            definition = call.udtf_def
            handler = definition.handler()
            out_fields = list(definition.output_schema.fields)
            out_names = [f.name.upper() for f in out_fields]
            out_rows = []

            def _emit(yielded):
                if yielded is None:
                    return
                for produced in yielded:
                    values = list(produced)
                    for i, field in enumerate(out_fields):
                        if i < len(values):
                            values[i] = _apply_type(field.datatype, values[i])
                    while len(values) < len(out_fields):
                        values.append(None)
                    out_rows.append(values)

            for row in self._rows:
                env = self._env(row)
                args = []
                for i, arg_col in enumerate(call.arg_columns):
                    value = arg_col._evaluate(env)
                    if i < len(definition.input_types):
                        value = _apply_type(definition.input_types[i], value)
                    args.append(value)
                _emit(handler.process(*args))
            if hasattr(handler, 'end_partition'):
                _emit(handler.end_partition())
            return self._derived(out_names, [None] * len(out_names), out_rows)

        def drop(self, *cols):
            self._materialize()
            if len(cols) == 1 and isinstance(cols[0], (list, tuple)):
                cols = tuple(cols[0])
            targets = set()
            for c in cols:
                targets.add((c._name if isinstance(c, Column) else str(c)).upper())
            keep = [i for i, name in enumerate(self._names) if name.upper() not in targets]
            names = [self._names[i] for i in keep]
            types = [self._types[i] for i in keep]
            rows = [[row[i] for i in keep] for row in self._rows]
            return self._derived(names, types, rows)

        def filter(self, condition):
            if isinstance(condition, str):
                # A SQL-text condition composes onto the (not yet materialized) source query, exactly
                # like snowpark pushing the filter into the generated SQL. Once rows are local there is
                # no SQL to compose onto — evaluating an arbitrary SQL fragment in python is out.
                if self._pending_sql is not None:
                    return DataFrame(self._session,
                                     sql='SELECT * FROM (' + self._pending_sql + ') WHERE ' + condition)
                simple = _parse_simple_condition(condition)
                if simple is not None:
                    return self.filter(simple)
                return DataFrame(self._session,
                                 sql='SELECT * FROM (' + self._sql_source() + ') WHERE ' + condition)
            self._materialize()
            if not isinstance(condition, Column):
                raise SnowparkClientException(
                    'DataFrame.filter/where supports Column conditions only in the local engine')
            rows = []
            for row in self._rows:
                if condition._evaluate(self._env(row)):
                    rows.append(row)
            return self._derived(self._names, self._types, rows)

        def where(self, condition):
            return self.filter(condition)

        def select_expr(self, *exprs):
            flat = []
            for e in exprs:
                if isinstance(e, (list, tuple)):
                    flat.extend(e)
                else:
                    flat.append(e)
            # SQL-expression select composes onto the source query (spilling local rows to a temp
            # table first when needed), like snowpark's generated SQL.
            return DataFrame(self._session,
                             sql='SELECT ' + ', '.join(str(e) for e in flat)
                                 + ' FROM (' + self._sql_source() + ')')

        def selectExpr(self, *exprs):
            return self.select_expr(*exprs)

        def distinct(self):
            return self.drop_duplicates()

        def limit(self, n):
            self._materialize()
            return self._derived(self._names, self._types, self._rows[:n])

        def drop_duplicates(self, *subset):
            self._materialize()
            names = []
            for s in subset:
                if isinstance(s, (list, tuple)):
                    names.extend(str(x) for x in s)
                else:
                    names.append(str(s))
            if names:
                indices = [self._index_of(n) for n in names]
            else:
                indices = list(range(len(self._names)))
            seen = set()
            rows = []
            for row in self._rows:
                key = tuple(_hash_key(row[i]) for i in indices)
                if key not in seen:
                    seen.add(key)
                    rows.append(row)
            return self._derived(self._names, self._types, rows)

        def dropDuplicates(self, *subset):
            return self.drop_duplicates(*subset)

        def fillna(self, value, subset=None):
            self._materialize()
            if subset is None:
                targets = list(range(_builtins.len(self._names)))
            else:
                if isinstance(subset, str):
                    subset = [subset]
                targets = [self._index_of(n) for n in subset]
            rows = []
            for row in self._rows:
                out = list(row)
                for i in targets:
                    if out[i] is None:
                        out[i] = value
                rows.append(out)
            return self._derived(self._names, self._types, rows)

        def order_by(self, *cols):
            self._materialize()
            flat = []
            for c in cols:
                if isinstance(c, (list, tuple)):
                    flat.extend(c)
                else:
                    flat.append(c)
            indices = [self._index_of(c._name if isinstance(c, Column) else _builtins.str(c)) for c in flat]

            def _key(row):
                out = []
                for i in indices:
                    v = row[i]
                    out.append((v is None, v))
                return out
            rows = sorted((list(r) for r in self._rows), key=_key)
            return self._derived(self._names, self._types, rows)

        def orderBy(self, *cols):
            return self.order_by(*cols)

        def sort(self, *cols):
            return self.order_by(*cols)

        def _index_of(self, name):
            target = str(name).upper()
            for i, n in enumerate(self._names):
                if n.upper() == target:
                    return i
            raise SnowparkClientException('Column %r not found among %r' % (name, self._names))

        def join(self, right, on=None, how='inner', **kwargs):
            self._materialize()
            right._materialize()
            join_how = str(how or 'inner').lower().replace('_', '')
            if isinstance(on, Column):
                return self._join_condition(right, on, join_how)
            if isinstance(on, str):
                on_names = [on]
            else:
                on_names = [str(x) for x in (on or [])]
            left_idx = [self._index_of(n) for n in on_names]
            right_idx = [right._index_of(n) for n in on_names]
            right_keep = [i for i in range(len(right._names)) if i not in right_idx]

            index = {}
            for row in right._rows:
                key = tuple(_hash_key(row[i]) for i in right_idx)
                index.setdefault(key, []).append(row)

            if join_how == 'leftanti':
                rows = []
                for row in self._rows:
                    key = tuple(_hash_key(row[i]) for i in left_idx)
                    if key not in index:
                        rows.append(list(row))
                return self._derived(self._names, self._types, rows)

            names = list(self._names) + [right._names[i] for i in right_keep]
            types = list(self._types) + [right._types[i] for i in right_keep]
            rows = []
            for row in self._rows:
                key = tuple(_hash_key(row[i]) for i in left_idx)
                matches = index.get(key)
                if matches:
                    for match in matches:
                        rows.append(list(row) + [match[i] for i in right_keep])
                elif join_how in ('left', 'leftouter'):
                    rows.append(list(row) + [None] * len(right_keep))
            return self._derived(names, types, rows)

        def _join_condition(self, right, condition, join_how):
            names = list(self._names) + list(right._names)
            types = list(self._types) + list(right._types)
            rows = []
            for left_row in self._rows:
                matched = False
                for right_row in right._rows:
                    env = dict(zip(right._names, right_row))
                    env.update(dict(zip(self._names, left_row)))
                    if condition._evaluate(env):
                        matched = True
                        rows.append(list(left_row) + list(right_row))
                if not matched and join_how in ('left', 'leftouter'):
                    rows.append(list(left_row) + [None] * len(right._names))
            return self._derived(names, types, rows)

        def union_all(self, other):
            self._materialize()
            other._materialize()
            return self._derived(self._names, self._types,
                                 [list(r) for r in self._rows] + [list(r) for r in other._rows])

        def unionAll(self, other):
            return self.union_all(other)

        def union(self, other):
            return self.union_all(other).drop_duplicates()

        @property
        def write(self):
            return DataFrameWriter(self)

        @property
        def na(self):
            raise SnowparkClientException('DataFrame.na is not supported in the local engine')

        # PythonResultSet-style cursor compatibility for older handler code.
        def next(self):
            self._materialize()
            cursor = getattr(self, '_cursor', -1) + 1
            self._cursor = cursor
            return cursor < len(self._rows)

        def get(self, key):
            self._materialize()
            cursor = getattr(self, '_cursor', -1)
            if cursor < 0 or cursor >= len(self._rows):
                raise SnowparkClientException('No current row. Call next() first.')
            return Row(self._names, self._rows[cursor])[key]

    # -------------------------------------------------------------- Session --

    class _SessionReader:
        def __init__(self, session):
            self._session = session

        def table(self, name):
            return self._session.table(name)

    class Session:
        def __init__(self, java_session):
            self._java = java_session

        def _query(self, sql, params=None):
            if params:
                sql = _substitute_params(sql, params)
            try:
                result = self._java.sql(sql)
            except BaseException as e:
                # Host exceptions from the Java facade are FOREIGN objects, not python Exception
                # subclasses; only BaseException matches them.
                raise SnowparkSQLException(str(e))
            names = [str(n).upper() for n in result.columnNames()]
            sql_types = [str(t) for t in result.columnTypes()]
            rows = []
            for java_row in result.data():
                row = []
                for i in range(len(names)):
                    row.append(_from_sql_value(java_row[i], sql_types[i]))
                rows.append(row)
            return names, sql_types, rows

        def _execute(self, sql):
            try:
                self._java.sql(sql)
            except BaseException as e:
                raise SnowparkSQLException(str(e))

        def _table_exists(self, name):
            try:
                self._java.sql('SELECT 1 FROM ' + name + ' LIMIT 0')
                return True
            except BaseException:
                return False

        def sql(self, query, params=None):
            if params:
                query = _substitute_params(query, params)
            return DataFrame(self, sql=query)

        def table(self, name):
            return DataFrame(self, sql='SELECT * FROM ' + name)

        def create_dataframe(self, data, schema=None):
            if hasattr(data, 'columns') and hasattr(data, 'itertuples'):
                names = [str(c).upper() for c in data.columns]
                rows = []
                for record in data.itertuples(index=False, name=None):
                    row = []
                    for value in record:
                        if hasattr(value, 'item'):
                            value = value.item()
                        if isinstance(value, float) and _math.isnan(value):
                            value = None
                        row.append(value)
                    rows.append(row)
                return DataFrame(self, names=names, types=[None] * len(names), rows=rows)
            if schema is None:
                raise SnowparkClientException('create_dataframe needs a schema for non-pandas data')
            if isinstance(schema, StructType):
                names = [f.name.upper() for f in schema.fields]
            else:
                names = [str(n).upper() for n in schema]
            rows = []
            for item in data:
                if isinstance(item, dict):
                    rows.append([item.get(n) if n in item else item.get(n.lower()) for n in names])
                elif isinstance(item, Row):
                    rows.append([item[n] for n in names])
                else:
                    rows.append(list(item))
            return DataFrame(self, names=names, types=[None] * len(names), rows=rows)

        def createDataFrame(self, data, schema=None):
            return self.create_dataframe(data, schema)

        @property
        def read(self):
            return _SessionReader(self)

        def close(self):
            pass

        def get_current_database(self):
            return self._query('SELECT CURRENT_DATABASE()')[2][0][0]

        def get_current_schema(self):
            return self._query('SELECT CURRENT_SCHEMA()')[2][0][0]

    def _substitute_params(sql, params):
        out = []
        in_string = False
        param_iter = iter(params)
        i = 0
        while i < len(sql):
            ch = sql[i]
            if in_string:
                out.append(ch)
                if ch == "'":
                    if i + 1 < len(sql) and sql[i + 1] == "'":
                        out.append("'")
                        i += 1
                    else:
                        in_string = False
            elif ch == "'":
                in_string = True
                out.append(ch)
            elif ch == '?':
                try:
                    out.append(_sql_literal(next(param_iter)))
                except StopIteration:
                    out.append(ch)
            else:
                out.append(ch)
            i += 1
        return ''.join(out)

    class WindowSpec:
        def __init__(self, partition_keys=None, order_keys=None):
            self.partition_keys = list(partition_keys or [])
            self.order_keys = list(order_keys or [])

        def partition_by(self, *cols):
            return WindowSpec(self.partition_keys + [str(c) for c in cols], self.order_keys)

        def order_by(self, *cols):
            return WindowSpec(self.partition_keys, self.order_keys + [str(c) for c in cols])

        partitionBy = partition_by
        orderBy = order_by

    class Window:
        @staticmethod
        def partition_by(*cols):
            return WindowSpec([str(c) for c in cols], [])

        @staticmethod
        def order_by(*cols):
            return WindowSpec([], [str(c) for c in cols])

        partitionBy = partition_by
        orderBy = order_by

    # ----------------------------------------------------- module wiring --

    _ty_mod.DataType = DataType
    _ty_mod.StringType = StringType
    _ty_mod.BinaryType = BinaryType
    _ty_mod.BooleanType = BooleanType
    _ty_mod.ByteType = ByteType
    _ty_mod.ShortType = ShortType
    _ty_mod.IntegerType = IntegerType
    _ty_mod.LongType = LongType
    _ty_mod.FloatType = FloatType
    _ty_mod.DoubleType = DoubleType
    _ty_mod.DecimalType = DecimalType
    _ty_mod.DateType = DateType
    _ty_mod.TimeType = TimeType
    _ty_mod.TimestampType = TimestampType
    _ty_mod.TimestampTimeZone = TimestampTimeZone
    _ty_mod.NullType = NullType
    _ty_mod.VariantType = VariantType
    _ty_mod.ArrayType = ArrayType
    _ty_mod.MapType = MapType
    _ty_mod.StructField = StructField
    _ty_mod.StructType = StructType
    # Non-standard names some handlers import; permissive stand-ins.
    _ty_mod.LTZ = TimestampTimeZone.LTZ
    _ty_mod.NTZ = TimestampTimeZone.NTZ

    _fn_mod.col = col
    _fn_mod.lit = lit
    _fn_mod.parse_json = parse_json
    _fn_mod.coalesce = coalesce
    _fn_mod.array_construct = array_construct
    _fn_mod.current_timestamp = current_timestamp
    _fn_mod.array_max = array_max
    _fn_mod.array_size = array_size
    _fn_mod.array_contains = array_contains
    _fn_mod.array_intersection = array_intersection
    _fn_mod.to_variant = to_variant
    _fn_mod.is_null = is_null
    _fn_mod.udtf = udtf
    _fn_mod.flatten = flatten
    _fn_mod.row_number = row_number
    _fn_mod.when = when
    _fn_mod.least = least
    _fn_mod.upper = upper
    _fn_mod.object_construct_keep_null = object_construct_keep_null
    _fn_mod.approx_percentile = approx_percentile
    _fn_mod.max = _aggregate_factory('max')
    _fn_mod.min = _aggregate_factory('min')
    _fn_mod.sum = _aggregate_factory('sum')
    _fn_mod.avg = _aggregate_factory('avg')
    _fn_mod.count = _aggregate_factory('count')

    _ex_mod.SnowparkClientException = SnowparkClientException
    _ex_mod.SnowparkSQLException = SnowparkSQLException

    _se_mod.Session = Session

    _sp_mod.Session = Session
    _sp_mod.DataFrame = DataFrame
    _sp_mod.Row = Row
    _sp_mod.Column = Column
    _sp_mod.Window = Window
    _sp_mod.functions = _fn_mod
    _sp_mod.types = _ty_mod
    _sp_mod.session = _se_mod
    _sp_mod.exceptions = _ex_mod
    _sp_mod.__version__ = '1.30.0'

    _sf_mod.snowpark = _sp_mod

    _sys.modules['snowflake'] = _sf_mod
    _sys.modules['snowflake.snowpark'] = _sp_mod
    _sys.modules['snowflake.snowpark.functions'] = _fn_mod
    _sys.modules['snowflake.snowpark.types'] = _ty_mod
    _sys.modules['snowflake.snowpark.session'] = _se_mod
    _sys.modules['snowflake.snowpark.exceptions'] = _ex_mod


if 'snowflake.snowpark' not in _sys.modules:
    _frostlake_install_snowpark()
