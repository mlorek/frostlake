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

/**
 * What a Python handler raised, reported as the account reports it (live-verified):
 *
 * <pre>
 *   Python Interpreter Error:
 *   Traceback (most recent call last):
 *     File "_udf_code.py", line 3, in f
 *       raise ValueError('none given')
 *   ValueError: none given
 *    in function PY_S with handler f
 * </pre>
 *
 * <p>The handler's source is compiled as the file {@code _udf_code.py}, so the traceback names the source's own lines
 * and leaves out the runtime's frames; a source that does not compile is reported by its line, the text and the
 * caret under the fault, with no traceback. A procedure's report names a function too. The source is compiled once
 * and kept for every later call.
 *
 * <p>The source runs as a module of its own, named after the handler as the account names it: {@code h_module} for
 * handler {@code h}, its file {@code _udf_code.py}, registered on {@code sys.modules}, and its globals holding the
 * module's own names and none of the runtime's (live-verified). What it defines is handed to the runtime's namespace,
 * which calls it.
 */
final class PythonFailureReport {

    /** The global the handler's source is bound under. */
    private static final String SOURCE = "__fl_udf_source";

    /** The global the name of the module the source runs as is bound under. */
    private static final String MODULE = "__fl_udf_module_name";

    /** The global the guarded code leaves its report in. */
    private static final String SLOT = "__fl_failure";

    /** Python that runs the bound source as the handler's module, handing what it defines to this namespace. */
    private static final String RUN_SOURCE = """
        import sys as __fl_sys, types as __fl_types, importlib.machinery as __fl_machinery
        __fl_codes = __import__('builtins').__dict__.setdefault('__fl_udf_codes', {})
        if __fl_udf_source not in __fl_codes:
            __fl_codes[__fl_udf_source] = compile(__fl_udf_source, '_udf_code.py', 'exec')
        __fl_module = __fl_types.ModuleType(__fl_udf_module_name)
        __fl_module.__file__ = '_udf_code.py'
        __fl_module.__cached__ = None
        __fl_module.__spec__ = __fl_machinery.ModuleSpec(__fl_udf_module_name,
            __fl_machinery.SourceFileLoader(__fl_udf_module_name, '/_udf_code.py'), origin='/_udf_code.py')
        __fl_module.__loader__ = __fl_module.__spec__.loader
        __fl_sys.modules[__fl_udf_module_name] = __fl_module
        exec(__fl_codes[__fl_udf_source], __fl_module.__dict__)
        globals().update([__fl_item for __fl_item in __fl_module.__dict__.items()
            if not __fl_item[0].startswith('__')])
        """;

    /** Python that words the report of {@code __fl_e}, the exception the guarded code caught. */
    private static final String REPORT = """
        import linecache as __fl_lines, traceback as __fl_trace
        __fl_lines.cache['_udf_code.py'] = (len(__fl_udf_source), None, __fl_udf_source.splitlines(True),
            '_udf_code.py')
        __fl_frames = [__fl_f for __fl_f in __fl_trace.extract_tb(__fl_e.__traceback__)
            if __fl_f.filename == '_udf_code.py']
        __fl_failure = (('Traceback (most recent call last):\\n' + ''.join(__fl_trace.format_list(__fl_frames)))
            if __fl_frames else '') + ''.join(__fl_trace.format_exception_only(type(__fl_e), __fl_e))
        """;

    private PythonFailureReport() {
    }

    /**
     * Bind the handler's source and run it as its module, reported as {@link #wording} reads it should it fail.
     *
     * @param source  the handler's source, dedented
     * @param handler the handler as written
     */
    static void runSource(final String source, final String handler) {
        PythonInterpreter.bind(SOURCE, source);
        PythonInterpreter.bind(MODULE, moduleName(handler));
        PythonInterpreter.eval(guarded(RUN_SOURCE));
    }

    /** The module a handler's source runs as: the handler's first name and {@code _module}, {@code Gen_module}. */
    private static String moduleName(final String handler) {
        final String written = handler == null ? "" : handler;
        final int dot = written.indexOf('.');
        return (dot < 0 ? written : written.substring(0, dot)) + "_module";
    }

    /**
     * Run code that calls into the handler's source bound by {@link #runSource}, reported should it fail.
     *
     * @param code the Python, one or more lines
     */
    static void run(final String code) {
        PythonInterpreter.eval(guarded(code));
    }

    /** The code wrapped so a failure leaves its report before it is raised on. */
    private static String guarded(final String code) {
        return SLOT + " = None\ntry:\n" + indented(code) + "except BaseException as __fl_e:\n" + indented(REPORT)
            + "    raise\n";
    }

    private static String indented(final String code) {
        final StringBuilder out = new StringBuilder();
        for (final String line : code.split("\n")) {
            if (!line.isEmpty()) {
                out.append("    ").append(line).append('\n');
            }
        }
        return out.toString();
    }

    /**
     * The report of what the last guarded code raised, or null when it left none.
     *
     * @param name    the routine's name
     * @param handler the handler as written
     * @return the refusal, or null
     */
    static String wording(final String name, final String handler) {
        final Object reported;
        try {
            reported = PythonInterpreter.global(SLOT);
        } catch (final RuntimeException unreadable) {
            return null;
        }
        if (!(reported instanceof String)) {
            return null;
        }
        return "Python Interpreter Error:\n" + reported + " in function " + name + " with handler " + handler;
    }
}
