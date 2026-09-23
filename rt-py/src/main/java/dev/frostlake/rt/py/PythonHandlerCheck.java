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

import org.graalvm.polyglot.Value;

/**
 * A Python function's HANDLER judged against its signature once the body has run at CREATE, as the account
 * judges it (live-verified). The handler is a name the body defines, or {@code module.attribute}: everything
 * before the last dot is imported as a module by that exact name and the attribute is read from it, so a class of
 * the body is no module ({@code HANDLER = 'C.h'} is {@code ModuleNotFoundError: No module named 'C'}), an empty
 * module name is {@code ValueError: Empty module name} and an attribute the module lacks is the AttributeError
 * Python raises for it. Any failure while resolving it is refused as a {@code Python Interpreter Error} naming the
 * exception. Then:
 * <ul>
 *   <li>a scalar function's handler is read through its {@code __code__}: an object without one is refused with
 *       the AttributeError CPython raises ({@code 'int' object has no attribute '__code__'}, {@code type object
 *       'C' has no attribute '__code__'} for a class, which the embedded interpreter words otherwise), and one
 *       taking another number of positional arguments than the signature declares is refused — defaults count,
 *       keyword-only and {@code **kwargs} do not, and a {@code *args} handler takes any number;</li>
 *   <li>a table function's handler is a class. An {@code __init__} with code is judged first: it must take a
 *       positional {@code self}, no keyword-only parameter and nothing else. Then its {@code process} method: it
 *       must take a positional parameter at all, named {@code self}, then the signature's arguments. Then an
 *       {@code end_partition}, which must have code, take a positional parameter named {@code self}, no
 *       keyword-only parameter and nothing else.</li>
 * </ul>
 * Each sentence here is completed by the caller with {@code in function <NAME> with handler <handler>}.
 */
final class PythonHandlerCheck {

    /**
     * Python that binds {@code __fl_handler} to the dotted handler named by {@code __fl_handler_name}, resolved
     * the way CREATE resolves it, for a call to invoke.
     */
    static final String RESOLVE_DOTTED = """
        import sys as __fl_sys
        __fl_module_name, __fl_dot, __fl_attribute = __fl_handler_name.rpartition('.')
        __import__(__fl_module_name)
        __fl_handler = getattr(__fl_sys.modules[__fl_module_name], __fl_attribute)
        """;

    /** Reports what the handler is, as a tuple the Java side reads; bound to the names set before it runs. */
    private static final String INSPECTION = """
        def __fl_code_of(target):
            try:
                return target.__code__
            except AttributeError as absent:
                if isinstance(target, type):
                    return "AttributeError: type object '" + target.__name__ + "' has no attribute '__code__'"
                return 'AttributeError: ' + str(absent)
        def __fl_inspect_handler(name, table, declared):
            import sys
            if name == '':
                return ('missing',)
            try:
                if '.' in name:
                    module_name, dot, attribute = name.rpartition('.')
                    __import__(module_name)
                    target = getattr(sys.modules[module_name], attribute)
                else:
                    target = globals().get(name)
                    if target is None:
                        return ('missing',)
            except BaseException as failure:
                return ('interpreter', type(failure).__name__ + ': ' + str(failure))
            if not table:
                code = __fl_code_of(target)
                if isinstance(code, str):
                    return ('interpreter', code)
                if code.co_flags & 4 or code.co_argcount == declared:
                    return ('ok',)
                return ('refused', 'Python function is defined with %d arguments, but UDF definition contains'
                        ' %d arguments' % (code.co_argcount, declared))
            init = getattr(getattr(target, '__init__', None), '__code__', None)
            if init is not None:
                if init.co_argcount == 0:
                    return ('refused', 'The __init__ method cannot be static. Missing "self" argument')
                if init.co_kwonlyargcount > 0:
                    return ('refused', '__init__ definition should not include keyword arguments, but found %d'
                            ' arguments' % init.co_kwonlyargcount)
                if init.co_argcount != 1:
                    return ('refused', '__init__ should be defined with only one argument "self", but found %d'
                            ' arguments' % init.co_argcount)
            process = getattr(target, 'process', None)
            if process is None:
                return ('no-process',)
            code = __fl_code_of(process)
            if isinstance(code, str):
                return ('interpreter', code)
            if code.co_argcount == 0:
                return ('refused', 'The process method cannot be static. Missing "self" argument')
            if code.co_varnames[0] != 'self':
                return ('refused', 'process should have "self" as the first positional argument, but found'
                        ' argument %s instead' % code.co_varnames[0])
            if not code.co_flags & 4 and code.co_argcount != declared + 1:
                if declared == 0:
                    return ('refused', 'process should be defined with only one argument "self", but found %d'
                            ' arguments' % code.co_argcount)
                return ('refused', 'UDF definition contains %d arguments, so the process method should be defined'
                        ' with %d arguments, including "self", but found %d arguments'
                        % (declared, declared + 1, code.co_argcount))
            end = getattr(target, 'end_partition', None)
            if end is None:
                return ('ok',)
            code = __fl_code_of(end)
            if isinstance(code, str):
                return ('interpreter', code)
            if code.co_argcount == 0:
                return ('refused', 'The end_partition method cannot be static. Missing "self" argument')
            if code.co_varnames[0] != 'self':
                return ('refused', 'end_partition should have "self" as the first positional argument, but found'
                        ' argument %s instead' % code.co_varnames[0])
            if code.co_kwonlyargcount > 0:
                return ('refused', 'end_partition definition should not include keyword arguments, but found %d'
                        ' arguments' % code.co_kwonlyargcount)
            if code.co_argcount != 1:
                return ('refused', 'end_partition should be defined with only one argument "self", but found %d'
                        ' arguments' % code.co_argcount)
            return ('ok',)
        __fl_handler_report = __fl_inspect_handler(__fl_handler_name, __fl_handler_table, __fl_handler_declared)
        """;

    private PythonHandlerCheck() {
    }

    /**
     * Why the handler cannot serve the function, without the sentence's closing {@code in function ...}, or null
     * when it can — and null too when the handler cannot be inspected, which leaves it to the first call.
     *
     * @param handlerName the HANDLER as written
     * @param table       whether the function returns a table
     * @param declared    how many arguments the signature declares
     */
    static String refusal(final String handlerName, final boolean table, final int declared) {
        final Value report;
        try {
            PythonRuntime.bind("__fl_handler_name", handlerName);
            PythonRuntime.bind("__fl_handler_table", Boolean.valueOf(table));
            PythonRuntime.bind("__fl_handler_declared", Integer.valueOf(declared));
            PythonRuntime.eval(INSPECTION);
            report = PythonRuntime.global("__fl_handler_report");
        } catch (final RuntimeException uninspectable) {
            PythonRuntime.discardContext();
            return null;
        }
        if (report == null || !report.hasArrayElements()) {
            return null;
        }
        final String kind = report.getArrayElement(0).asString();
        if ("missing".equals(kind)) {
            return table ? "Could not find handler class" : "Could not find handler";
        }
        if ("no-process".equals(kind)) {
            return "Could not find process method";
        }
        if ("interpreter".equals(kind)) {
            return "Python Interpreter Error:\n" + report.getArrayElement(1).asString();
        }
        if ("refused".equals(kind)) {
            return report.getArrayElement(1).asString();
        }
        return null;
    }

    /** Whether a HANDLER names an attribute of a module rather than a name the body defines. */
    static boolean isDotted(final String handlerName) {
        return handlerName != null && handlerName.indexOf('.') >= 0;
    }
}
