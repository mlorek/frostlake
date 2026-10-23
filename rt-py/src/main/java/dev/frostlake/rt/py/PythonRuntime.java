/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.rt.py;

import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.udf.TruffleLogBridge;
import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.DataType;
import dev.frostlake.types.TypeCategory;
import dev.frostlake.types.VectorElementType;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantText;
import dev.frostlake.values.VariantUndefined;
import dev.frostlake.values.VariantValue;
import dev.frostlake.values.VectorValue;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * The embedded Python runtime shared by the Python UDF, procedure and table-function executors:
 * GraalPy (Python 3.12) on the GraalVM polyglot API. Snowflake runs CPython 3.x, so the engine needs a
 * Python 3 interpreter — the previous Jython embedding implemented Python 2.7 and could not even parse
 * the annotations, f-strings and assignment expressions that ordinary Snowflake handlers use.
 *
 * <p>A polyglot {@link Context} is NOT thread-safe and costs over a second to build, so one is kept per
 * thread — matching the per-thread interpreter the Jython path used, and for the same reason: building
 * one per row made Python UDFs orders of magnitude slower than a built-in. {@link Source} objects are
 * cached per thread as well so an unchanged body is parsed once per thread rather than per row. Each routine's
 * call runs in a module namespace of its own on that interpreter (see {@link #enterModule()}).
 */
public final class PythonRuntime {

    /** Python version implemented by the embedded interpreter, for diagnostics. */
    static final String EMBEDDED_PYTHON = "3.12 (GraalPy)";

    /**
     * ONE polyglot engine shared by every Python context, held for the JVM's lifetime.
     *
     * <p>This is load-bearing, not an optimization. A context built without an explicit engine gets its
     * OWN engine; once that engine becomes unreachable, polyglot closes it from a reference queue that
     * is drained inside {@code PolyglotEngineImpl.createContext} — i.e. while some UNRELATED thread is
     * building a context. Closing a Python context blocks on Python-level locks, so that cleanup could
     * run on a thread holding the engine monitor and deadlock the whole process (observed: a JavaScript
     * procedure's {@code ScriptEngine.put} froze forever finalizing a collected GraalPy engine). A single
     * strongly-held engine is never collected, so that cleanup path never runs mid-statement.
     */
    private static final Engine SHARED_ENGINE = buildSharedEngine();

    private static Engine buildSharedEngine() {
        final Engine.Builder builder = Engine.newBuilder()
            .option("engine.WarnInterpreterOnly", "false");
        // Truffle logs flow into SLF4J (or to -Dpolyglot.log.file when the user set one).
        TruffleLogBridge.installOn(builder);
        return builder.build();
    }

    /**
     * GraalPy venv whose site-packages the contexts import from (numpy/pandas — the local stand-in for
     * Snowflake's {@code PACKAGES=(...)}), or null for the standard library only. Set once from
     * {@code EngineConfig} before the first Python execution; contexts created earlier keep the
     * environment they were built with.
     */
    private static volatile String venvDirectory;

    /**
     * The JVM default time zone before any Python ran. GraalPy's {@code import pandas} (via its time
     * machinery) SETS the process-wide default to GMT as a side effect, silently shifting every later
     * {@code LocalDateTime.now()} — engine {@code CURRENT_TIMESTAMP} values jumped by the UTC offset
     * mid-run, so rows written after the first pandas import sorted BEFORE rows written earlier.
     * {@link #eval} restores this zone after every execution.
     */
    private static final TimeZone HOST_TIME_ZONE = TimeZone.getDefault();

    /**
     * EVERY context ever built, held strongly for the JVM's lifetime. If a context becomes
     * unreachable (its thread died, or {@link #discardContext} dropped it), polyglot closes it
     * INLINE from a reference queue drained during a LATER {@code createContext} — on whatever
     * thread is creating. Since contexts started importing pandas, such a close can block FOREVER
     * in {@code threading._shutdown} on a lock leaked by the abandoned thread (observed: the test
     * main thread wedged inside {@code Engine$ContextReference.clean} while building a fresh
     * context). Retaining every context means that cleanup path never runs; the leak is bounded by
     * the number of threads that ever ran Python.
     */
    private static final List<Context> ALL_CONTEXTS = new CopyOnWriteArrayList<>();

    /** This thread's interpreter, built on first use (see {@link #context()}). */
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    /** The runtime's own module on this thread's interpreter (see {@link #RUNTIME_SOURCE}). */
    private static final ThreadLocal<Value> RUNTIME = new ThreadLocal<>();

    /** The helper modules made on this thread's interpreter, by name (see {@link #module}). */
    private static final ThreadLocal<Map<String, Value>> MODULES = new ThreadLocal<Map<String, Value>>() {
        @Override
        protected Map<String, Value> initialValue() {
            return new LinkedHashMap<>();
        }
    };

    /**
     * Whether this thread's interpreter has run anything since the state it shares between calls was last put back
     * (see {@link #restoreShared}): only a routine changes that state, so a statement that ran none leaves nothing
     * to put back, and putting it back costs a pass over the built-in names, the environment and every guarded
     * module on each statement otherwise.
     */
    private static final ThreadLocal<Boolean> RAN = new ThreadLocal<>();

    /**
     * The runtime's own module, made on each interpreter as it is built: {@code enter} empties the interpreter's
     * module namespace for a routine about to run and answers what it held, {@code leave} puts that back, and
     * {@code module} makes a helper module of the runtime's from its source. A namespace keeps only the names the
     * interpreter itself defines in it, the dunder ones. {@code restore} puts back what the interpreter shares
     * between calls as it stood when the runtime was made, or a helper module once it was made: the built-in names
     * and the environment whole, the modules the runtime made - a helper module and those it installed on
     * {@code sys.modules}, which no file holds - under their names there, and what the runtime's own code is made
     * of. That is each module the runtime made (this one included) and every class and function its sources define,
     * whether a module holds it, a closure does (the snowpark emulation defines its classes inside a function and
     * names them by Snowpark's modules, {@code snowflake.snowpark.relational_grouped_dataframe}, so those count as
     * the runtime's too) or only {@code object.__subclasses__()} finds it: a member a routine
     * replaced or deleted is put back and one it added is removed - an added {@code Session.__getattribute__} would
     * otherwise answer for {@code session.sql} in every later statement - and so for a class's bases, a module's
     * class and a function's code, defaults and closure cells. The cpython worker ({@code cpython_worker.py}) does
     * the same, function for function. {@code parse} builds an argument's Python value from its text through the
     * runtime's own references to {@code json}, {@code decimal}, {@code datetime} and {@code array}, bound as it is
     * made, as the worker decodes one: a routine that replaced {@code json.loads} or {@code decimal.Decimal} then
     * changes no argument a routine it calls receives, as no routine's change reaches another on the account.
     * The standard library's modules imported by then are put back too, but only
     * in part: a member a routine replaced or deleted comes back - a dunder one aside, which the import system
     * sets ({@code import importlib} renames {@code _frozen_importlib}) -, and the module on {@code sys.modules}, while
     * one added stays, as the modules gain members as they run (a package its submodules, a cache its entries). The
     * runtime imports {@code array}, {@code datetime}, {@code decimal} and {@code json} first, as the worker does for
     * its own use, so that the same modules are covered on both backends; one first imported later is not, as a
     * routine may have changed it before it is seen. What a routine put on {@code sys.modules} that no import made
     * goes (a module without a spec, or no module at all, unless its parent module holds it under that name, as
     * {@code pyexpat} holds {@code pyexpat.errors}), while a real module a routine imported stays: numpy cannot be
     * imported twice in one process. The working directory and the decimal context are put back as well.
     */
    private static final String RUNTIME_SOURCE = """
        import types as _types
        import builtins as _builtins
        import os as _os
        import sys as _sys
        import array as _array
        import datetime as _datetime
        import decimal as _decimal
        import json as _json
        _namespace = None
        _base = None
        _builtins_base = None
        _environ_base = None
        _installed = {}
        _guarded = []
        _functions = []
        _guarded_ids = set()
        _missing = object()
        _stdlib = []
        _modules_base = set()
        _getcwd = _os.getcwd
        _chdir = _os.chdir
        _setcontext = _decimal.setcontext
        _cwd_base = None
        _decimal_base = None
        def bind(namespace, runtime):
            global _namespace, _base, _builtins_base, _environ_base
            _namespace = namespace
            _base = dict((k, v) for k, v in namespace.items() if k.startswith('__') and k.endswith('__'))
            _builtins_base = dict(_builtins.__dict__)
            _environ_base = dict(_os.environ)
            _snapshot()
            _guard(runtime)
        def _standard_library():
            import sysconfig
            paths = sysconfig.get_paths()
            stdlib = _os.path.join(paths['stdlib'], '')
            packages = tuple(_os.path.join(paths[key], '') for key in ('purelib', 'platlib'))
            found = []
            for name, module in list(_sys.modules.items()):
                if (not issubclass(type(module), _types.ModuleType) or name in ('__main__', 'builtins')
                        or name.startswith('__frostlake')):
                    continue
                members = module.__dict__
                origin = getattr(members.get('__spec__'), 'origin', None)
                path = members.get('__file__')
                if origin in ('built-in', 'frozen') or (isinstance(path, str) and path.startswith(stdlib)
                                                        and not path.startswith(packages)):
                    found.append((name, module, type(module), dict((key, value) for key, value in members.items()
                                                            if not (key.startswith('__') and key.endswith('__')))))
            return found
        def _snapshot():
            global _cwd_base, _decimal_base
            _stdlib.extend(_standard_library())
            _modules_base.update(_sys.modules)
            _cwd_base = _getcwd()
            _decimal_base = _decimal.getcontext().copy()
        def _imported(key, value):
            if issubclass(type(value), _types.ModuleType) and value.__dict__.get('__spec__') is not None:
                return True
            parent, dot, child = key.rpartition('.')
            holder = _sys.modules.get(parent) if dot else None
            return issubclass(type(holder), _types.ModuleType) and holder.__dict__.get(child, _missing) is value
        def _put_back_standard_library():
            for name, module, kind, pristine in _stdlib:
                if type(module) is not kind:
                    module.__class__ = kind
                _put_back_members(module.__dict__, pristine)
            modules = _sys.modules
            for name, module, kind, pristine in _stdlib:
                if modules.get(name, _missing) is not module:
                    modules[name] = module
            for key in [k for k in modules if k not in _modules_base and k not in _installed]:
                if not _imported(key, modules[key]):
                    del modules[key]
            try:
                moved = _getcwd() != _cwd_base
            except OSError:
                moved = True
            if moved:
                _chdir(_cwd_base)
            _setcontext(_decimal_base.copy())
        _decode = _json.JSONDecoder().decode
        _decimal_type = _decimal.Decimal
        _date_type = _datetime.date
        _time_type = _datetime.time
        _datetime_type = _datetime.datetime
        _array_type = _array.array
        def parse(kind, text):
            if kind == 'json':
                return _decode(text)
            if kind == 'decimal':
                return _decimal_type(text)
            if kind == 'int':
                return int(text)
            if kind == 'date':
                return _date_type.fromisoformat(text)
            if kind == 'time':
                return _time_type.fromisoformat(text)
            if kind == 'datetime':
                return _datetime_type.fromisoformat(text)
            if kind == 'bytes':
                return bytes.fromhex(text)
            if kind == 'f':
                return memoryview(_array_type('f', [float(e) for e in text.split(',')]))
            if kind == 'i':
                return memoryview(_array_type('i', [int(e) for e in text.split(',')]))
            raise ValueError('unknown kind ' + kind)
        def enter():
            saved = dict(_namespace)
            _namespace.clear()
            _namespace.update(_base)
            return saved
        def leave(saved):
            _namespace.clear()
            _namespace.update(saved)
        def _owned(value):
            module = str(getattr(value, '__module__', ''))
            return module.startswith('__frostlake') or module.startswith('snowflake.snowpark')
        def _guard(holder):
            if id(holder) in _guarded_ids:
                return
            _guarded_ids.add(id(holder))
            _guarded.append((holder, type(holder), dict(holder.__dict__), getattr(holder, '__bases__', None)))
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
                if id(value) in _guarded_ids:
                    return
                _guarded_ids.add(id(value))
                cells = [_cell(cell) for cell in value.__closure__ or ()]
                _functions.append((value, value.__code__, value.__defaults__, value.__kwdefaults__, cells))
                for held in cells:
                    _guard_member(held)
        def _cell(cell):
            try:
                return cell.cell_contents
            except ValueError:
                return _missing
        def _guard_classes():
            seen = set()
            pending = [object]
            while pending:
                for sub in type.__subclasses__(pending.pop()):
                    if id(sub) not in seen:
                        seen.add(id(sub))
                        pending.append(sub)
                        if _owned(sub):
                            _guard(sub)
        def module(name, source):
            before = set(_sys.modules)
            made = _types.ModuleType(name)
            exec(compile(source, '<' + name + '>', 'exec'), made.__dict__)
            _guard(made)
            for key in [k for k in _sys.modules if k not in before]:
                installed = _sys.modules[key]
                if isinstance(installed, _types.ModuleType) and getattr(installed, '__spec__', None) is None:
                    _installed[key] = installed
                    _guard(installed)
            _guard_classes()
            return made
        def _put_back_members(current, pristine):
            for key, value in pristine.items():
                if current.get(key, _missing) is not value:
                    current[key] = value
        def _put_back(holder, kind, pristine, bases):
            if type(holder) is not kind:
                holder.__class__ = kind
            current = holder.__dict__
            for key in [k for k in current if k not in pristine]:
                delattr(holder, key)
            for key, value in pristine.items():
                if current.get(key, _missing) is not value:
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
                    if value is _missing:
                        del cell.cell_contents
                    else:
                        cell.cell_contents = value
        def restore():
            _put_back_standard_library()
            current = _builtins.__dict__
            for key in [k for k in current if k not in _builtins_base]:
                del current[key]
            _put_back_members(current, _builtins_base)
            if dict(_os.environ) != _environ_base:
                for key in [k for k in _os.environ if k not in _environ_base]:
                    del _os.environ[key]
                for key, value in _environ_base.items():
                    if _os.environ.get(key) != value:
                        _os.environ[key] = value
            for key, installed in _installed.items():
                if _sys.modules.get(key) is not installed:
                    _sys.modules[key] = installed
            for holder, kind, pristine, bases in _guarded:
                _put_back(holder, kind, pristine, bases)
            for guarded in _functions:
                _put_back_function(*guarded)
        """;

    /** Makes {@link #RUNTIME_SOURCE} a module bound to the namespace it runs in, leaving no name of its own there. */
    private static final String INSTALL_RUNTIME = """
        import types as __fl_types
        __fl_runtime = __fl_types.ModuleType('__frostlake_runtime')
        exec(compile(__fl_runtime_source, '<frostlake>', 'exec'), __fl_runtime.__dict__)
        del __fl_types, __fl_runtime_source
        __fl_runtime.bind(globals(), __fl_runtime)
        """;

    /** A new interpreter, with the runtime's own module made on it. */
    private static Context newContext() {
        final Context.Builder builder = Context.newBuilder("python")
            // Handlers receive engine objects (a session facade, semi-structured values) and may
            // import from the bundled standard library, so host access and IO stay open — this is
            // an embedded test engine, executing SQL the caller already controls.
            .allowAllAccess(true)
            .engine(SHARED_ENGINE)
            // No async-action thread per context. GraalPy otherwise starts a helper thread that must
            // ENTER the context to run finalizers/weakref callbacks; while a caller is blocked inside
            // the same context on a Python-level lock, that helper cannot get in and the lock is never
            // released — a hang that also strands every thread queued behind the engine's own lock
            // (observed with abandoned harness threads: a worker parked in PLock.acquireBlocking for
            // 14 minutes on 0.5s of CPU). Actions run inline at safepoints instead.
            .option("python.NoAsyncActions", "true")
            // Do not install process-wide signal handlers from an embedded library.
            .option("python.InstallSignalHandlers", "false")
            // The venv's native modules (numpy/pandas .so) otherwise log one "Python C API is
            // considered experimental" warning per module load; the limitation is known and
            // accepted, so drop the noise.
            .option("python.WarnExperimentalFeatures", "false")
            // Hand Python the host's zone explicitly: without TZ, GraalPy's tzset falls back to GMT
            // and (see HOST_TIME_ZONE) mutates the JVM default when pandas imports.
            .environment("TZ", HOST_TIME_ZONE.getID());
        final String venv = venvDirectory;
        if (venv != null) {
            // Pointing the interpreter's executable into the venv is how GraalPy activates one when
            // embedded: site.py finds pyvenv.cfg next to the executable and adds the venv's
            // site-packages to sys.path. ForceImportSite makes that site initialization run even
            // though the context is embedded rather than launched.
            builder.option("python.Executable", venv + "/bin/python");
            builder.option("python.ForceImportSite", "true");
            // Native extension modules (numpy/pandas .so) otherwise bind to the FIRST context in
            // the process; contexts are per-thread AND recreated after a failure (discardContext),
            // so the second context to import numpy died with "a second GraalPy context attempted
            // to load a native module". IsolateNativeModules loads a private copy per context.
            builder.option("python.IsolateNativeModules", "true");
            // Isolated loading relocates each copy with the patchelf BINARY, resolved via PATH.
            // The venv carries one (pip install patchelf), so put its bin directory in front.
            final String hostPath = System.getenv("PATH");
            builder.environment("PATH", venv + "/bin" + (hostPath == null ? "" : ":" + hostPath));
        }
        final Context context = builder.build();
        ALL_CONTEXTS.add(context);
        return context;
    }

    /** Makes the runtime's own module on a new interpreter, answering it. */
    private static Value installRuntime(final Context context) {
        final Value bindings = context.getBindings("python");
        bindings.putMember("__fl_runtime_source", RUNTIME_SOURCE);
        context.eval(Source.create("python", INSTALL_RUNTIME));
        final Value runtime = bindings.getMember("__fl_runtime");
        bindings.removeMember("__fl_runtime");
        return runtime;
    }

    private static final ThreadLocal<Map<String, Source>> SOURCES = new ThreadLocal<Map<String, Source>>() {
        @Override
        protected Map<String, Source> initialValue() {
            return new LinkedHashMap<>();
        }
    };

    /** Longest a single Python execution may run before its context is treated as wedged. */
    private static final long EXECUTION_TIMEOUT_SECONDS = 60;

    /** Watchdog that cancels a wedged Python execution. One daemon thread for the whole JVM. */
    private static final ScheduledExecutorService WATCHDOG =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                final Thread thread = new Thread(r, "frostlake-python-watchdog");
                thread.setDaemon(true);
                return thread;
            }
        });

    private PythonRuntime() {
    }

    /**
     * Configure the GraalPy venv packages become importable from (see {@link #venvDirectory}). A null,
     * blank, or non-existent directory leaves the runtime on the standard library alone — a missing venv
     * must not break every Python handler that never imports third-party packages.
     */
    public static void configureVenv(final String venvDir) {
        if (venvDir == null || venvDir.trim().isEmpty()) {
            return;
        }
        final String trimmed = venvDir.trim();
        if (!new java.io.File(trimmed, "pyvenv.cfg").isFile()) {
            return;
        }
        venvDirectory = trimmed;
    }

    /** This thread's Python context, created on first use; every execution on it passes here (see {@link #RAN}). */
    public static Context context() {
        Context context = CONTEXT.get();
        if (context == null) {
            context = newContext();
            RUNTIME.set(installRuntime(context));
            CONTEXT.set(context);
        }
        RAN.set(Boolean.TRUE);
        return context;
    }

    /**
     * Enter a module namespace of its own for one routine's call (live-verified): the routine sees only the names its
     * own body defines, none another routine's body left, whichever engine ran it, and a routine called while another
     * runs - a function a procedure's query calls - leaves its caller's names as they were. Nothing a call defines
     * outlives it.
     *
     * @return the frame, to be left with {@link #leaveModule(PythonModuleFrame)} once the call is through
     */
    static PythonModuleFrame enterModule() {
        final Context context = context();
        return new PythonModuleFrame(context, RUNTIME.get().invokeMember("enter"));
    }

    /**
     * Leave a frame {@link #enterModule()} entered, its namespace holding again what it held before. A frame whose
     * interpreter was thrown away meanwhile has nothing to put back, and an interpreter that cannot put it back is
     * thrown away.
     *
     * @param frame the frame, or null when none was entered
     */
    static void leaveModule(final PythonModuleFrame frame) {
        if (frame == null || frame.getContext() != CONTEXT.get()) {
            return;
        }
        try {
            RUNTIME.get().invokeMember("leave", frame.getSaved());
        } catch (final RuntimeException unrestorable) {
            discardContext();
        }
    }

    /**
     * An outermost statement on this thread has ended: what its routines changed in the state the interpreter shares
     * between calls - the built-in names, the environment, the runtime's helper modules and the snowpark emulation's
     * classes, the members of the standard library's modules imported by then, what a routine put on
     * {@code sys.modules}, the working directory and the decimal context - is put back (see {@link #RUNTIME_SOURCE}),
     * so that no routine reaches the next statement's, whichever engine runs it, through them. A thread with no
     * interpreter, or whose interpreter ran nothing since the state was last put back, has nothing to put back, and an
     * interpreter that cannot put it back is thrown away.
     */
    static void restoreShared() {
        if (CONTEXT.get() == null || RUNTIME.get() == null || !Boolean.TRUE.equals(RAN.get())) {
            return;
        }
        RAN.set(Boolean.FALSE);
        try {
            RUNTIME.get().invokeMember("restore");
        } catch (final RuntimeException unrestorable) {
            discardContext();
        }
    }

    /**
     * A helper module of the runtime's on this thread's interpreter, made from its source on first use: what it
     * defines lives in no routine's namespace, so no routine sees it or disturbs it.
     *
     * @param name   the module's name
     * @param source the module's source
     * @return the module
     */
    static Value module(final String name, final String source) {
        context();
        final Map<String, Value> modules = MODULES.get();
        Value module = modules.get(name);
        if (module == null) {
            module = RUNTIME.get().invokeMember("module", name, source);
            modules.put(name, module);
        }
        return module;
    }

    /** Bind a second name to what a global holds. */
    static void alias(final String alias, final String name) {
        final Value bindings = context().getBindings("python");
        bindings.putMember(alias, bindings.getMember(name));
    }

    /**
     * Throw away this thread's context so the next call starts from a clean interpreter.
     *
     * <p>Needed because a Python-level lock can be LEAKED: if an execution is interrupted or abandoned
     * mid-{@code import} (or inside {@code logging}, which locks too), GraalPy's lock stays acquired with
     * no owner left alive, and every later call in that context blocks forever on it. Host harnesses do
     * abandon worker threads, so a context that has seen a failure is treated as unusable rather than
     * reused. Closing can itself block, so it runs on the watchdog thread — the caller never waits.
     */
    public static void discardContext() {
        final Context poisoned = CONTEXT.get();
        CONTEXT.remove();
        RUNTIME.remove();
        MODULES.remove();
        SOURCES.remove();
        RAN.remove();
        if (poisoned == null) {
            return;
        }
        // Close on a THROWAWAY thread, never the shared watchdog: a close can block indefinitely on a
        // leaked Python lock (pandas threading state), and a wedged watchdog would disable every eval
        // alarm. The watchdog only arms a bounded interrupt that unwinds a stuck close; the context
        // itself stays strongly retained in ALL_CONTEXTS either way.
        final Thread closer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    poisoned.close(true);
                } catch (final RuntimeException alreadyGoneOrWedged) {
                    // A wedged context cannot always be closed; leaving it retained is enough.
                }
            }
        }, "frostlake-python-context-closer");
        closer.setDaemon(true);
        closer.start();
        WATCHDOG.schedule(new Runnable() {
            @Override
            public void run() {
                closer.interrupt();
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Settle this thread's context after a failed execution. A failure raised in the handler's code — Python's own
     * exception, or the engine's raised through it — unwinds through every {@code finally} and {@code with} on its
     * way out and leaves no lock held, so the context is kept: a context replaced under another language's frame (a
     * JavaScript procedure's {@code snowflake.execute}) failed the next Python execution there on the polyglot
     * runtime's own sharing check. A cancelled, interrupted or exhausted execution, an exit, an internal failure, and
     * a failure that is no polyglot one at all, may have left the interpreter mid-step, and the context is thrown away
     * ({@link #discardContext()}).
     *
     * @param failure what the execution threw
     * @return whether the context was kept, so that the failure's guest values can still be read
     */
    public static boolean settleAfter(final Throwable failure) {
        if (failure instanceof PolyglotException) {
            final PolyglotException polyglot = (PolyglotException) failure;
            if (!polyglot.isCancelled() && !polyglot.isInterrupted() && !polyglot.isInternalError()
                    && !polyglot.isResourceExhausted() && !polyglot.isExit()) {
                return true;
            }
        }
        discardContext();
        return false;
    }

    /**
     * Bind a global visible to the executed source. A SQL NULL binds as Python {@code None}: handing
     * the polyglot binding a Java null produces a FOREIGN null instead, so {@code type(v).__name__} read
     * "foreign" and {@code v is None} was false — handlers guard on exactly that.
     */
    public static void bind(final String name, final Object value) {
        if (value == null) {
            eval(name + " = None\n");
            return;
        }
        context().getBindings("python").putMember(name, value);
    }

    /** Read a global left behind by the executed source, or null when it is unset. */
    public static Value global(final String name) {
        final Value bindings = context().getBindings("python");
        return bindings.hasMember(name) ? bindings.getMember(name) : null;
    }

    /**
     * Execute Python source in this thread's context, reusing a cached {@link Source} per body.
     *
     * <p>Guarded by a watchdog that cancels the context after {@link #EXECUTION_TIMEOUT_SECONDS}. A leaked
     * Python lock (see {@link #discardContext()}) otherwise blocks the calling thread FOREVER, and callers
     * hold the engine's lock while running a UDF — one wedged thread stalled 16 others in a vendor test
     * run. Cancellation turns that into an ordinary statement error, which callers already handle.
     */
    public static Value eval(final String code) {
        final Map<String, Source> cache = SOURCES.get();
        Source source = cache.get(code);
        if (source == null) {
            source = Source.newBuilder("python", code, "<udf>").buildLiteral();
            cache.put(code, source);
        }
        final Context context = context();
        final ScheduledFuture<?> alarm = WATCHDOG.schedule(new Runnable() {
            @Override
            public void run() {
                try {
                    context.close(true);   // cancels the in-flight execution on the blocked thread
                } catch (final RuntimeException alreadyUnwinding) {
                    // Best effort.
                }
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        try {
            return context.eval(source);
        } finally {
            alarm.cancel(false);
            // Undo any process-global time-zone mutation the executed Python caused (pandas import
            // sets the JVM default to GMT) so the engine's clock stays consistent across the run.
            if (!HOST_TIME_ZONE.equals(TimeZone.getDefault())) {
                TimeZone.setDefault(HOST_TIME_ZONE);
            }
        }
    }

    /**
     * Bind a semi-structured argument (ARRAY / OBJECT / VARIANT, carried by the engine as JSON text) as
     * NATIVE Python dict/list values, so a handler can index it, call {@code .get()} or iterate {@code .items()}.
     * Binding the raw JSON string would hand the handler a str, and handler code fails with
     * "AttributeError: 'str' object has no attribute 'get'". Returns false when the value is not JSON
     * text, so the caller binds it unchanged.
     */
    public static boolean bindJson(final String name, final Object value) {
        if (value instanceof VariantValue) {
            // The JSON of the value, not the display form: a variant STRING displays its content unquoted
            // and an XML variant displays as XML, and json.loads can read neither — nor the bare token an
            // array's SQL NULL element is written as, which arrives as None (live-verified).
            bindParsed(name, "json", VariantText.standard(VariantUndefined.withNulls(((VariantValue) value).node())));
            return true;
        }
        if (!(value instanceof CharSequence)) {
            return false;
        }
        try {
            ArrayFunctionHelper.MAPPER.readTree(value.toString());
        } catch (final RuntimeException notJson) {
            return false;
        }
        bindParsed(name, "json", value.toString());
        return true;
    }

    /**
     * Bind a BINARY argument (passed as its hex text) as a native Python {@code bytes} value, the
     * type a Snowflake Python UDF receives for BINARY. Binding the host object instead would give
     * the handler an opaque foreign value.
     */
    public static boolean bindBytes(final String name, final String hex) {
        bindParsed(name, "bytes", hex);
        return true;
    }

    /**
     * Bind a global to the Python value the runtime's {@code parse} builds from a text (see {@link #RUNTIME_SOURCE}).
     *
     * @param name the global
     * @param kind what the text is: {@code json}, {@code decimal}, {@code int}, {@code date}, {@code time},
     *             {@code datetime}, {@code bytes}, or a VECTOR's array type code {@code f} or {@code i}
     * @param text the text
     */
    private static void bindParsed(final String name, final String kind, final String text) {
        final Context context = context();
        context.getBindings("python").putMember(name, RUNTIME.get().invokeMember("parse", kind, text));
    }

    /**
     * Bind a procedure's argument as the Python value its handler receives for the parameter's type
     * (live-verified): a whole NUMBER an {@code int} of any width and a NUMBER with a scale a
     * {@code decimal.Decimal} at that scale, a DATE a {@code datetime.date}, a TIME a {@code datetime.time} and a
     * TIMESTAMP a {@code datetime.datetime} — without a zone for NTZ, at the session's wall clock for LTZ and with
     * its own offset for TZ, to the microsecond —, a semi-structured value a dict, a list or the scalar it holds,
     * a BINARY {@code bytes} and a VECTOR a {@code memoryview} of its 32-bit elements (see {@link #bindVector}). A
     * BOOLEAN, a FLOAT and a text bind as they are.
     *
     * @param name  the parameter's name
     * @param value the argument, converted to the parameter's SQL type
     * @param type  the parameter's declared type
     */
    public static void bindArgument(final String name, final Object value, final DataType type) {
        if (value == null) {
            bind(name, null);
            return;
        }
        final boolean semiStructured = type != null && type.getCategory() == TypeCategory.SEMI_STRUCTURED;
        if ((value instanceof VariantValue || semiStructured && value instanceof CharSequence)
                && bindJson(name, value)) {
            return;
        }
        if (semiStructured && (value instanceof TemporalAccessor || value instanceof BinaryValue)) {
            // A semi-structured argument holding a DATE, a TIME, a timestamp or a BINARY is the text the VARIANT
            // shows, '2024-01-02 10:11:12.000', as a member of an ARRAY or an OBJECT is (live-verified).
            bind(name, ArrayFunctionHelper.toNode(ArrayFunctionHelper.MAPPER, value).asText());
            return;
        }
        if (value instanceof BinaryValue) {
            bindBytes(name, ((BinaryValue) value).toHex());
            return;
        }
        if (value instanceof VectorValue) {
            bindVector(name, (VectorValue) value);
            return;
        }
        if (value instanceof BigDecimal) {
            final BigDecimal number = (BigDecimal) value;
            bindParsed(name, number.scale() > 0 ? "decimal" : "int", number.toPlainString());
            return;
        }
        if (value instanceof LocalDate) {
            bindParsed(name, "date", value.toString());
            return;
        }
        if (value instanceof LocalTime) {
            bindParsed(name, "time", ((LocalTime) value).truncatedTo(ChronoUnit.MICROS).format(PYTHON_TIME));
            return;
        }
        if (value instanceof LocalDateTime) {
            bindParsed(name, "datetime",
                ((LocalDateTime) value).truncatedTo(ChronoUnit.MICROS).format(PYTHON_DATE_TIME));
            return;
        }
        if (value instanceof OffsetDateTime || value instanceof ZonedDateTime) {
            final OffsetDateTime zoned = (value instanceof OffsetDateTime ? (OffsetDateTime) value
                : ((ZonedDateTime) value).toOffsetDateTime()).truncatedTo(ChronoUnit.MICROS);
            final boolean keepsItsZone = type != null && type.getName() != null
                && type.getName().toUpperCase(Locale.ROOT).endsWith("_TZ");
            // A TIMESTAMP_TZ keeps its offset; a TIMESTAMP_LTZ is the session's wall clock at that instant.
            bindParsed(name, "datetime", keepsItsZone ? zoned.format(PYTHON_ZONED)
                : zoned.atZoneSameInstant(SessionZone.current()).toLocalDateTime().format(PYTHON_DATE_TIME));
            return;
        }
        bind(name, value);
    }

    /**
     * Bind a VECTOR argument as the Python value Snowflake's SQL-Python type mapping names for it, a {@code memoryview}
     * (docs.snowflake.com/en/developer-guide/udf-stored-procedure-data-type-mapping; its format is not documented, and
     * is taken to be the elements' own): one over an {@code array} of 32-bit floats ({@code 'f'}) for a
     * {@code VECTOR(FLOAT, n)} and of 32-bit ints ({@code 'i'}) for a {@code VECTOR(INT, n)}, which a handler indexes,
     * iterates, measures and turns into a list ({@code tolist()}) as it would the elements themselves. The cpython
     * worker builds the same from its {@code $vec}. Binding the engine's value instead handed the handler a foreign
     * object it could not index.
     */
    static void bindVector(final String name, final VectorValue vector) {
        final boolean integral = vector.getElementType() == VectorElementType.INT;
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < vector.dimension(); i++) {
            if (i > 0) {
                text.append(',');
            }
            // A float32 element is exact as a double, and Double.toString's text (NaN and Infinity too) reads back
            // through float() to that same double.
            text.append(integral ? Long.toString((long) vector.element(i)) : Double.toString(vector.element(i)));
        }
        bindParsed(name, integral ? "i" : "f", text.toString());
    }

    private static final DateTimeFormatter PYTHON_TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter PYTHON_DATE_TIME =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS");
    private static final DateTimeFormatter PYTHON_ZONED =
        DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSxxx");

    /**
     * A Python return value as the plain Java value the engine stores. Scalars map to their Java
     * equivalents, {@code datetime}/{@code date}/{@code time} to the matching {@code java.time} type, and
     * dict/list/tuple to Map/List so the caller can render them as the JSON text VARIANT uses. A Python
     * object with no Java equivalent degrades to its {@code str()}.
     */
    public static Object toJava(final Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.fitsInLong()) {
            return value.asLong();
        }
        if (value.fitsInDouble()) {
            return value.asDouble();
        }
        // Temporal checks come BEFORE the container checks: GraalPy's datetime is neither a hash nor an
        // array, but ordering them explicitly keeps the intent clear.
        if (value.isDate() && value.isTime()) {
            return LocalDateTime.of(value.asDate(), value.asTime());
        }
        if (value.isDate()) {
            return value.asDate();
        }
        if (value.isTime()) {
            return value.asTime();
        }
        if (value.hasHashEntries()) {
            final Map<String, Object> map = new LinkedHashMap<>();
            final Value iterator = value.getHashKeysIterator();
            while (iterator.hasIteratorNextElement()) {
                final Value key = iterator.getIteratorNextElement();
                map.put(String.valueOf(toJava(key)), toJava(value.getHashValue(key)));
            }
            return map;
        }
        if (value.hasArrayElements()) {
            final List<Object> list = new ArrayList<>();
            for (long i = 0; i < value.getArraySize(); i++) {
                list.add(toJava(value.getArrayElement(i)));
            }
            return list;
        }
        if (value.hasIterator()) {
            final List<Object> list = new ArrayList<>();
            final Value iterator = value.getIterator();
            while (iterator.hasIteratorNextElement()) {
                list.add(toJava(iterator.getIteratorNextElement()));
            }
            return list;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        return value.toString();
    }

    /** {@code str(datetime)}; see {@link PythonValueText#pythonDatetimeStr(LocalDateTime)}. */
    public static String pythonDatetimeStr(final LocalDateTime dt) {
        return PythonValueText.pythonDatetimeStr(dt);
    }

    /** {@code str(time)}; see {@link PythonValueText#pythonTimeStr}. */
    public static String pythonTimeStr(final LocalTime t) {
        return PythonValueText.pythonTimeStr(t);
    }

    /** See {@link PythonValueText#temporalInContainer}. */
    public static Object temporalInContainer(final Object value) {
        return PythonValueText.temporalInContainer(value);
    }
}
