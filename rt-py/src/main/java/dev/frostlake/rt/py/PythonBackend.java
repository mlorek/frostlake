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

import dev.frostlake.metastore.model.Parameter;

/**
 * One Python interpreter behind {@link PythonInterpreter}: GraalPy embedded in the JVM ({@link GraalPyBackend}) or
 * CPython in a worker process ({@link CPythonBackend}). Each keeps one interpreter per thread, whose globals persist
 * between calls on that thread, which is what the executors' generated source relies on.
 */
interface PythonBackend {

    /** The interpreter, for messages: {@code 3.12 (GraalPy)}, {@code 3.12.4 (CPython)}. */
    String description();

    /** The interpreter's Python minor version (3.x gives x). */
    int minorVersion();

    /** Bind a global to a plain value (a string, a number, a boolean) or to an engine object the source calls. */
    void bind(String name, Object value);

    /** Bind a UDF or UDTF argument, presented as the handler expects the type its parameter declares. */
    void bindArgument(Parameter parameter, String name, Object value);

    /** Bind a stored procedure's argument. */
    void bindProcedureArgument(Parameter parameter, String name, Object value);

    /** Run Python source in this thread's interpreter. */
    void eval(String code);

    /** A global as a plain Java value (Map, List, scalars, java.time), or null when it is unset or None. */
    Object global(String name);

    /** Throw this thread's interpreter away, so the next call starts from a fresh one. */
    void discard();

    /**
     * Settle this thread's interpreter after a failed execution: kept when the failure left it sound, discarded
     * otherwise.
     *
     * @param failure what the execution threw
     * @return whether the interpreter was kept, so that the failure's guest values can still be read
     */
    boolean settleAfter(Throwable failure);

    /**
     * Enter a module namespace of its own for one routine's call: the routine sees only the names its own body
     * defines, and a routine called while another runs leaves its caller's names as they were.
     *
     * @return the frame, to be left with {@link #leaveModule(Object)}
     */
    Object enterModule();

    /**
     * Leave a frame {@link #enterModule()} entered, the namespace holding again what it held before.
     *
     * @param frame the frame, or null when none was entered
     */
    void leaveModule(Object frame);

    /**
     * Make a helper module of the runtime's from its source, once per interpreter: what it defines lives in no
     * routine's namespace. The module is importable by its name.
     *
     * @param name   the module's name
     * @param source the module's source
     */
    void installModule(String name, String source);

    /**
     * An outermost statement on this thread has ended: put back what its routines changed in the state the
     * interpreter shares between calls (the built-in names, the environment, the runtime's helper modules, the
     * standard library's modules, what a routine put on {@code sys.modules}, the working directory and the decimal
     * context).
     */
    void restoreShared();

    /** Whether globals read through {@link PythonRuntime} as polyglot values are this backend's (GraalPy). */
    boolean holdsPolyglotValues();

}
