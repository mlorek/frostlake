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
"""Frostlake's CPython worker: runs Python handler code for the engine when python.runtime=cpython.

The engine starts one worker per thread that runs Python and talks to it over the worker's stdin and
stdout, one JSON object per line. A request carries an "op"; a reply carries "re". Calls nest: while an
eval runs, the handler may call back into the engine (session.sql), and while the engine serves that
call it may send this worker requests of its own (a Python UDF the SQL calls), so each side waits for
its reply by serving whatever arrives first.

Values that JSON lacks travel as one-key objects: {"$dec": "1.25"}, {"$f": "nan"}, {"$dt": iso},
{"$dtz": iso with offset}, {"$d": iso}, {"$t": iso}, {"$b": hex}, {"$json": text} (semi-structured),
{"$o": [[key, value], ...]} (a dict, keeping its order), {"$host": name} (an engine object) and
{"$rs": {...}} (a result set), {"$outcome": {...}} (a statement's result or refusal). Anything the handler prints goes to stderr, which the engine logs.
"""
import datetime as _dt
import decimal as _decimal
import json as _json
import os as _os
import platform as _platform
import sys as _sys
import traceback as _traceback
import types as _types

_IN = _os.fdopen(_os.dup(0), "r", encoding="utf-8", newline="\n")
_OUT = _os.fdopen(_os.dup(1), "w", encoding="utf-8", newline="\n")
_os.dup2(2, 1)
_devnull = _os.open(_os.devnull, _os.O_RDONLY)
_os.dup2(_devnull, 0)
_sys.stdin = open(_os.devnull, "r")
_sys.stdout = _os.fdopen(1, "w", encoding="utf-8", buffering=1)

_GLOBALS = {"__name__": "__main__", "__builtins__": __builtins__}
# The names a routine's namespace starts with; each call runs in a namespace of its own (op "enter"), and what
# the namespace held before is put back when the call is through (op "leave"). _FRAMES holds those saved states.
_BASE = dict(_GLOBALS)
_FRAMES = []
# The runtime's helper modules (op "module"), made once per worker and importable by name.
_MODULES = {}
# What routines share between calls, as it stood when the worker started (op "restore" puts it back after each
# outermost statement, as PythonRuntime.RUNTIME_SOURCE does on GraalPy): the built-in names, the environment, the
# modules a helper module installed on sys.modules, and the members of the helper modules and their classes.
import builtins as _builtins
_BUILTINS_BASE = dict(_builtins.__dict__)
_ENVIRON_BASE = dict(_os.environ)
_INSTALLED = {}
_GUARDED = []
_MISSING = object()


def _guard(holder):
    for kept, _ in _GUARDED:
        if kept is holder:
            return
    _GUARDED.append((holder, dict(holder.__dict__)))
    if isinstance(holder, _types.ModuleType):
        for value in list(holder.__dict__.values()):
            if isinstance(value, type) and str(getattr(value, "__module__", "")).startswith("__frostlake"):
                _guard(value)


def _put_back_members(current, pristine):
    for key, value in pristine.items():
        if current.get(key, _MISSING) is not value:
            current[key] = value


def _put_back_class(holder, pristine):
    for key, value in pristine.items():
        if holder.__dict__.get(key, _MISSING) is not value:
            setattr(holder, key, value)


def _restore():
    current = _builtins.__dict__
    for key in [k for k in current if k not in _BUILTINS_BASE]:
        del current[key]
    _put_back_members(current, _BUILTINS_BASE)
    if dict(_os.environ) != _ENVIRON_BASE:
        for key in [k for k in _os.environ if k not in _ENVIRON_BASE]:
            del _os.environ[key]
        for key, value in _ENVIRON_BASE.items():
            if _os.environ.get(key) != value:
                _os.environ[key] = value
    for key, installed in _INSTALLED.items():
        if _sys.modules.get(key) is not installed:
            _sys.modules[key] = installed
    for holder, pristine in _GUARDED:
        if isinstance(holder, type):
            _put_back_class(holder, pristine)
        else:
            _put_back_members(holder.__dict__, pristine)
_CODE = {}


class _JavaError(Exception):
    """An engine-side failure raised to the handler, e.g. a SQL error from session.sql."""


class _JavaObject(object):
    """An engine object (the Snowpark session facade); every method call is a call into the engine."""

    __slots__ = ("_handle",)

    def __init__(self, handle):
        self._handle = handle

    def __getattr__(self, name):
        if name.startswith("__"):
            raise AttributeError(name)
        handle = self._handle

        def method(*args):
            return _call(handle, name, args)
        return method

    def __repr__(self):
        return "<engine object " + self._handle + ">"


class _JavaResult(object):
    """A result set the engine returned, read the way the snowpark emulation reads one."""

    def __init__(self, payload):
        self._names = payload.get("columnNames", [])
        self._types = payload.get("columnTypes", [])
        self._data = [[_decode(v) for v in row] for row in payload.get("data", [])]

    def columnNames(self):
        return list(self._names)

    def columnTypes(self):
        return list(self._types)

    def data(self):
        return self._data

    def collect(self):
        return [list(row) for row in self._data]

    def count(self):
        return len(self._data)


class _Outcome(object):
    """What a statement the handler issued came to: its result, or the engine's refusal as text."""

    def __init__(self, payload):
        self._refusal = payload.get("refusal")
        result = payload.get("result")
        self._result = _decode(result) if result is not None else None

    def refused(self):
        return self._refusal is not None

    def refusal(self):
        return self._refusal

    def result(self):
        return self._result


def _send(message):
    _OUT.write(_json.dumps(message, allow_nan=False, separators=(",", ":")))
    _OUT.write("\n")
    _OUT.flush()


def _key(value):
    # The engine keys a dict by the text of its key, as the GraalPy path does (String.valueOf).
    if isinstance(value, bool):
        return "true" if value else "false"
    if value is None:
        return "null"
    if isinstance(value, str):
        return value
    return str(value)


def _encode(value):
    if value is None or isinstance(value, (bool, str)):
        return value
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        if value != value:
            return {"$f": "nan"}
        if value in (float("inf"), float("-inf")):
            return {"$f": "inf" if value > 0 else "-inf"}
        return value
    if isinstance(value, _decimal.Decimal):
        if value.is_nan():
            return {"$f": "nan"}
        if value.is_infinite():
            return {"$f": "inf" if value > 0 else "-inf"}
        return {"$dec": str(value)}
    if isinstance(value, _dt.datetime):
        if value != value:
            return None
        if value.tzinfo is not None and value.utcoffset() is not None:
            return {"$dtz": value.isoformat()}
        return {"$dt": value.replace(tzinfo=None).isoformat()}
    if isinstance(value, _dt.date):
        return {"$d": value.isoformat()}
    if isinstance(value, _dt.time):
        return {"$t": value.replace(tzinfo=None).isoformat()}
    if isinstance(value, (bytes, bytearray, memoryview)):
        return {"$b": bytes(value).hex()}
    if isinstance(value, dict):
        return {"$o": [[_key(k), _encode(v)] for k, v in value.items()]}
    if isinstance(value, (list, tuple, set, frozenset)):
        return [_encode(v) for v in value]
    if isinstance(value, _JavaObject):
        return {"$host": value._handle}
    if isinstance(value, _JavaResult):
        return [_encode(row) for row in value.data()]
    # numpy and pandas scalars and arrays, without importing either.
    tolist = getattr(value, "tolist", None)
    if callable(tolist):
        try:
            return _encode(tolist())
        except Exception:
            pass
    try:
        if value != value:
            return None
    except Exception:
        pass
    try:
        iterator = iter(value)
    except TypeError:
        return str(value)
    return [_encode(v) for v in iterator]


def _decode(value):
    if isinstance(value, list):
        return [_decode(v) for v in value]
    if not isinstance(value, dict):
        return value
    if "$o" in value:
        return dict((k, _decode(v)) for k, v in value["$o"])
    if "$dec" in value:
        return _decimal.Decimal(value["$dec"])
    if "$f" in value:
        return float(value["$f"])
    if "$dt" in value:
        return _dt.datetime.fromisoformat(value["$dt"])
    if "$dtz" in value:
        return _dt.datetime.fromisoformat(value["$dtz"])
    if "$d" in value:
        return _dt.date.fromisoformat(value["$d"])
    if "$t" in value:
        return _dt.time.fromisoformat(value["$t"])
    if "$b" in value:
        return bytes.fromhex(value["$b"])
    if "$json" in value:
        return _json.loads(value["$json"])
    if "$host" in value:
        return _JavaObject(value["$host"])
    if "$rs" in value:
        return _JavaResult(value["$rs"])
    if "$outcome" in value:
        return _Outcome(value["$outcome"])
    return dict((k, _decode(v)) for k, v in value.items())


def _error(failure):
    message = str(failure)
    name = type(failure).__name__
    return {"type": name, "message": message, "text": name + ": " + message if message else name,
            "traceback": "".join(_traceback.format_exception(type(failure), failure, failure.__traceback__))}


def _call(handle, method, args):
    _send({"op": "call", "handle": handle, "method": method, "args": [_encode(a) for a in args]})
    reply = _serve(until_reply=True)
    if "raise" in reply:
        raise _JavaError(reply["raise"])
    return _decode(reply.get("ret"))


def _handle(message):
    op = message.get("op")
    if op == "bind":
        _GLOBALS[message["name"]] = _decode(message.get("value"))
        return {"re": 1, "ok": True}
    if op == "eval":
        code = message["code"]
        compiled = _CODE.get(code)
        if compiled is None:
            compiled = compile(code, "<udf>", "exec")
            _CODE[code] = compiled
        exec(compiled, _GLOBALS)
        return {"re": 1, "ok": True}
    if op == "enter":
        _FRAMES.append(dict(_GLOBALS))
        _GLOBALS.clear()
        _GLOBALS.update(_BASE)
        return {"re": 1, "frame": len(_FRAMES) - 1}
    if op == "leave":
        frame = message["frame"]
        if frame < len(_FRAMES):
            saved = _FRAMES[frame]
            del _FRAMES[frame:]
            _GLOBALS.clear()
            _GLOBALS.update(saved)
        return {"re": 1, "ok": True}
    if op == "module":
        name = message["name"]
        if name not in _MODULES:
            before = set(_sys.modules)
            made = _types.ModuleType(name)
            exec(compile(message["source"], "<" + name + ">", "exec"), made.__dict__)
            _MODULES[name] = made
            _sys.modules[name] = made
            _guard(made)
            for key in [k for k in _sys.modules if k not in before and k != name]:
                installed = _sys.modules[key]
                if isinstance(installed, _types.ModuleType) and getattr(installed, "__spec__", None) is None:
                    _INSTALLED[key] = installed
                    _guard(installed)
            _INSTALLED[name] = made
        return {"re": 1, "ok": True}
    if op == "restore":
        _restore()
        return {"re": 1, "ok": True}
    if op == "get":
        name = message["name"]
        if name not in _GLOBALS:
            return {"re": 1, "missing": True}
        return {"re": 1, "value": _encode(_GLOBALS[name])}
    raise ValueError("unknown request " + repr(op))


def _serve(until_reply=False):
    while True:
        line = _IN.readline()
        if not line:
            _sys.exit(0)
        message = _json.loads(line)
        if "re" in message:
            if until_reply:
                return message
            continue
        try:
            reply = _handle(message)
        except BaseException as failure:
            reply = {"re": 1, "error": _error(failure)}
        try:
            _send(reply)
        except (TypeError, ValueError) as unencodable:
            _send({"re": 1, "error": _error(unencodable)})


_send({"hello": {"version": _platform.python_version(), "major": _sys.version_info[0],
                 "minor": _sys.version_info[1], "implementation": _platform.python_implementation(),
                 "executable": _sys.executable}})
_serve()
