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
{"$vec": {"t": "f" or "i", "v": [...]}} (a VECTOR, handed over as a memoryview of 32-bit elements),
{"$o": [[key, value], ...]} (a dict, keeping its order), {"$host": name} (an engine object) and
{"$rs": {...}} (a result set), {"$outcome": {...}} (a statement's result or refusal). Anything the handler prints goes to stderr, which the engine logs.
"""
import array as _array
import datetime as _dt
import decimal as _decimal
import json as _json
import os as _os
import platform as _platform
import sys as _sys
import traceback as _traceback
import types as _types

# The worker's functions and classes take their module from this name, which marks them the runtime's own (see
# _owned); routines run under "__main__" in a namespace of their own (_GLOBALS).
__name__ = "__frostlake_worker"

_IN = _os.fdopen(_os.dup(0), "r", encoding="utf-8", newline="\n")
_OUT = _os.fdopen(_os.dup(1), "w", encoding="utf-8", newline="\n")
_os.dup2(2, 1)
_devnull = _os.open(_os.devnull, _os.O_RDONLY)
_os.dup2(_devnull, 0)
_sys.stdin = open(_os.devnull, "r")
_sys.stdout = _os.fdopen(1, "w", encoding="utf-8", buffering=1)
# The engine starts the worker isolated (-I), which ignores PYTHONIOENCODING, so stderr is made UTF-8 here.
_sys.stderr.reconfigure(encoding="utf-8", errors="backslashreplace")

# The worker's own references to what its protocol is made of, bound before any routine runs: a routine that replaces
# json.dumps (to give it a default serializer, say), json.loads or decimal.Decimal changes the module's member, not
# these, and so cannot change the lines the worker writes or the values it reads. The encoder and decoder are
# the worker's own instances, so json's shared default ones play no part either.
_ENCODE = _json.JSONEncoder(allow_nan=False, separators=(",", ":")).encode
_DECODE = _json.JSONDecoder().decode
_ARRAY = _array.array
_DATETIME = _dt.datetime
_DATE = _dt.date
_TIME = _dt.time
_DECIMAL = _decimal.Decimal
_FORMAT_EXCEPTION = _traceback.format_exception

_GLOBALS = {"__name__": "__main__", "__builtins__": __builtins__}
# The names a routine's namespace starts with; each call runs in a namespace of its own (op "enter"), and what
# the namespace held before is put back when the call is through (op "leave"). _FRAMES holds those saved states.
_BASE = dict(_GLOBALS)
_FRAMES = []
# The runtime's helper modules (op "module"), made once per worker and importable by name.
_MODULES = {}
# What routines share between calls, as it stood when the worker started (op "restore" puts it back after each
# outermost statement, as PythonRuntime.RUNTIME_SOURCE does on GraalPy): the built-in names, the environment, the
# modules a helper module installed on sys.modules, and what the runtime's own code is made of - the helper modules,
# this worker's module, every class and function they define (nested ones too) and their members.
import builtins as _builtins
_BUILTINS_BASE = dict(_builtins.__dict__)
_ENVIRON_BASE = dict(_os.environ)
_INSTALLED = {}
# (holder, kind, members, bases) per guarded module or class, and (function, code, defaults, kwdefaults, cells) per
# guarded function; _GUARDED_IDS keeps each guarded once (what it names stays alive in these lists).
_GUARDED = []
_FUNCTIONS = []
_GUARDED_IDS = set()
_MISSING = object()
# What else routines share, as it stood when the worker started (see _snapshot), and op "restore" puts back:
# the standard library's modules then imported - (name, module, class, members) each -, the names then on sys.modules,
# the working directory and the decimal context. Through the standard library's own functions, bound here.
_STDLIB = []
_MODULES_BASE = set()
_GETCWD = _os.getcwd
_CHDIR = _os.chdir
_SETCONTEXT = _decimal.setcontext
_CWD_BASE = None
_DECIMAL_BASE = None


def _owned(value):
    # What the runtime's sources define: they run as modules named __frostlake_..., and so does this worker.
    return str(getattr(value, "__module__", "")).startswith("__frostlake")


def _guard(holder):
    if id(holder) in _GUARDED_IDS:
        return
    _GUARDED_IDS.add(id(holder))
    _GUARDED.append((holder, type(holder), dict(holder.__dict__), getattr(holder, "__bases__", None)))
    for value in list(holder.__dict__.values()):
        _guard_member(value)


def _guard_member(value):
    if isinstance(value, (staticmethod, classmethod)):
        value = value.__func__
    if isinstance(value, property):
        for part in (value.fget, value.fset, value.fdel):
            _guard_member(part)
    elif isinstance(value, type):
        if _owned(value):
            _guard(value)
    elif isinstance(value, _types.FunctionType) and _owned(value):
        if id(value) in _GUARDED_IDS:
            return
        _GUARDED_IDS.add(id(value))
        cells = [_cell(cell) for cell in value.__closure__ or ()]
        _FUNCTIONS.append((value, value.__code__, value.__defaults__, value.__kwdefaults__, cells))
        # A class defined inside a function (the snowpark emulation's install function defines them all) is
        # reachable only through the closures of the functions that use it.
        for held in cells:
            _guard_member(held)


def _cell(cell):
    try:
        return cell.cell_contents
    except ValueError:
        return _MISSING


def _guard_classes():
    # Every class the runtime's sources define, wherever it lives: a routine finds them all through
    # object.__subclasses__(), not only those a guarded module or closure holds.
    seen = set()
    pending = [object]
    while pending:
        for sub in type.__subclasses__(pending.pop()):
            if id(sub) not in seen:
                seen.add(id(sub))
                pending.append(sub)
                if _owned(sub):
                    _guard(sub)


def _put_back_members(current, pristine):
    for key, value in pristine.items():
        if current.get(key, _MISSING) is not value:
            current[key] = value


def _put_back(holder, kind, pristine, bases):
    # A member a routine added goes, as one it replaced or deleted comes back: an added Session.__getattribute__
    # would otherwise answer for session.sql in every later statement.
    if type(holder) is not kind:
        holder.__class__ = kind
    current = holder.__dict__
    for key in [k for k in current if k not in pristine]:
        delattr(holder, key)
    for key, value in pristine.items():
        if current.get(key, _MISSING) is not value:
            setattr(holder, key, value)
    if bases is not None and holder.__bases__ != bases:
        holder.__bases__ = bases


def _put_back_function(function, code, defaults, kwdefaults, cells):
    if function.__code__ is not code:
        function.__code__ = code
    if function.__defaults__ is not defaults:
        function.__defaults__ = defaults
    if function.__kwdefaults__ is not kwdefaults:
        function.__kwdefaults__ = kwdefaults
    for cell, value in zip(function.__closure__ or (), cells):
        if _cell(cell) is not value:
            if value is _MISSING:
                del cell.cell_contents
            else:
                cell.cell_contents = value


def _standard_library():
    # The standard library's modules on sys.modules: the built-in and frozen ones and those whose file lies in the
    # standard library's directory, but not in the site-packages beneath it. The builtins module is put back whole on
    # its own, and the worker's module and the runtime's helper modules are guarded (see _guard). A module's dunder
    # members are the import system's: importing importlib renames _frozen_importlib importlib._bootstrap.
    import sysconfig
    paths = sysconfig.get_paths()
    stdlib = _os.path.join(paths["stdlib"], "")
    packages = tuple(_os.path.join(paths[key], "") for key in ("purelib", "platlib"))
    found = []
    for name, module in list(_sys.modules.items()):
        if (not issubclass(type(module), _types.ModuleType) or name in ("__main__", "builtins")
                or name.startswith("__frostlake")):
            continue
        members = module.__dict__
        origin = getattr(members.get("__spec__"), "origin", None)
        path = members.get("__file__")
        if origin in ("built-in", "frozen") or (isinstance(path, str) and path.startswith(stdlib)
                                                and not path.startswith(packages)):
            found.append((name, module, type(module), dict((key, value) for key, value in members.items()
                                                            if not (key.startswith("__") and key.endswith("__")))))
    return found


def _snapshot():
    global _CWD_BASE, _DECIMAL_BASE
    _STDLIB.extend(_standard_library())
    _MODULES_BASE.update(_sys.modules)
    _CWD_BASE = _GETCWD()
    _DECIMAL_BASE = _decimal.getcontext().copy()


def _imported(key, value):
    # What an import puts on sys.modules: a module with a spec, or what its parent module holds under that name - a
    # native module's submodules (pyexpat.errors) and the names a module gives them there (xml.parsers.expat.errors).
    # A module a routine made itself (types.ModuleType) has no spec, and anything else is no module at all.
    if issubclass(type(value), _types.ModuleType) and value.__dict__.get("__spec__") is not None:
        return True
    parent, dot, child = key.rpartition(".")
    holder = _sys.modules.get(parent) if dot else None
    return issubclass(type(holder), _types.ModuleType) and holder.__dict__.get(child, _MISSING) is value


def _put_back_standard_library():
    # A member a routine replaced or deleted comes back, as does the module on sys.modules; a member added stays, as
    # a module gains them as it runs (a package its submodules, a cache its entries). What a routine put on
    # sys.modules that no import made goes, a real module stays: numpy cannot be imported twice in one process.
    for name, module, kind, pristine in _STDLIB:
        if type(module) is not kind:
            module.__class__ = kind
        _put_back_members(module.__dict__, pristine)
    modules = _sys.modules
    for name, module, kind, pristine in _STDLIB:
        if modules.get(name, _MISSING) is not module:
            modules[name] = module
    for key in [k for k in modules if k not in _MODULES_BASE and k not in _INSTALLED]:
        if not _imported(key, modules[key]):
            del modules[key]
    try:
        moved = _GETCWD() != _CWD_BASE
    except OSError:
        moved = True
    if moved:
        _CHDIR(_CWD_BASE)
    _SETCONTEXT(_DECIMAL_BASE.copy())


def _restore():
    _put_back_standard_library()
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
    for holder, kind, pristine, bases in _GUARDED:
        _put_back(holder, kind, pristine, bases)
    for guarded in _FUNCTIONS:
        _put_back_function(*guarded)
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
    _OUT.write(_ENCODE(message))
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
    if isinstance(value, _DECIMAL):
        if value.is_nan():
            return {"$f": "nan"}
        if value.is_infinite():
            return {"$f": "inf" if value > 0 else "-inf"}
        return {"$dec": str(value)}
    if isinstance(value, _DATETIME):
        if value != value:
            return None
        if value.tzinfo is not None and value.utcoffset() is not None:
            return {"$dtz": value.isoformat()}
        return {"$dt": value.replace(tzinfo=None).isoformat()}
    if isinstance(value, _DATE):
        return {"$d": value.isoformat()}
    if isinstance(value, _TIME):
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
        return _DECIMAL(value["$dec"])
    if "$f" in value:
        return float(value["$f"])
    if "$dt" in value:
        return _DATETIME.fromisoformat(value["$dt"])
    if "$dtz" in value:
        return _DATETIME.fromisoformat(value["$dtz"])
    if "$d" in value:
        return _DATE.fromisoformat(value["$d"])
    if "$t" in value:
        return _TIME.fromisoformat(value["$t"])
    if "$b" in value:
        return bytes.fromhex(value["$b"])
    if "$json" in value:
        return _DECODE(value["$json"])
    if "$vec" in value:
        # A VECTOR, as Snowflake's type mapping hands one to a Python handler: a memoryview, here of 32-bit floats
        # ("f") or ints ("i"), which indexes, iterates and answers tolist() as the elements.
        vector = value["$vec"]
        return memoryview(_ARRAY(vector["t"], [float(v) if vector["t"] == "f" else v for v in vector["v"]]))
    if "$host" in value:
        return _JavaObject(value["$host"])
    if "$rs" in value:
        return _JavaResult(value["$rs"])
    if "$outcome" in value:
        return _Outcome(value["$outcome"])
    return dict((k, _decode(v)) for k, v in value.items())


def _error(failure):
    # Never fails, as the reply must go out whatever the exception is: a failure escaping here would end the worker,
    # or, under a procedure's session.sql, leave the request unanswered. An exception whose str() fails reads
    # as GraalPy words it, a type whose name cannot be read by the name type itself holds, or else by its repr.
    kind = type(failure)
    try:
        name = kind.__name__
        if not isinstance(name, str):
            raise TypeError("__name__ is no str")
    except BaseException:
        try:
            name = type.__dict__["__name__"].__get__(kind)
        except BaseException:
            name = _repr_or(kind, "BaseException")
    try:
        message = str(failure)
    except BaseException:
        message = "<exception str() failed>"
    text = name + ": " + message if message else name
    try:
        trace = "".join(_FORMAT_EXCEPTION(kind, failure, failure.__traceback__))
    except BaseException:
        trace = text
    return {"type": name, "message": message, "text": text, "traceback": trace}


def _repr_or(value, fallback):
    try:
        text = repr(value)
        return text if isinstance(text, str) else fallback
    except BaseException:
        return fallback


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
            _guard_classes()
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
        message = _DECODE(line)
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
        except BaseException as unencodable:
            # Nothing was written: the line is encoded whole before it goes out. The error reply holds text only.
            _send({"re": 1, "error": _error(unencodable)})


# What the standard library holds is taken before the worker's module is guarded, which keeps what it took.
_snapshot()
# This worker's own module, under which its functions and classes (_JavaObject, _call, ...) run, is a module
# routines reach (sys.modules["__main__"]) and so is guarded too.
_guard(_sys.modules["__main__"])
_send({"hello": {"version": _platform.python_version(), "major": _sys.version_info[0],
                 "minor": _sys.version_info[1], "implementation": _platform.python_implementation(),
                 "executable": _sys.executable}})
_serve()
