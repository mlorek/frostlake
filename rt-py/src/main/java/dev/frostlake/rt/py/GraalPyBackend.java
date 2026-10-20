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

/** GraalPy embedded in the JVM, through {@link PythonRuntime}: the runtime frostlake has always used. */
final class GraalPyBackend implements PythonBackend {

    GraalPyBackend(final String venv) {
        PythonRuntime.configureVenv(venv);
    }

    @Override
    public String description() {
        return PythonRuntime.EMBEDDED_PYTHON;
    }

    @Override
    public int minorVersion() {
        return 12;
    }

    @Override
    public void bind(final String name, final Object value) {
        PythonRuntime.bind(name, value);
    }

    /** Each argument binds as the Python value its parameter's type maps to (see PythonRuntime.bindArgument). */
    @Override
    public void bindArgument(final Parameter parameter, final String name, final Object value) {
        PythonRuntime.bindArgument(name, value, parameter.getDataType());
    }

    @Override
    public void bindProcedureArgument(final Parameter parameter, final String name, final Object value) {
        PythonRuntime.bindArgument(name, value, parameter.getDataType());
    }

    @Override
    public void eval(final String code) {
        PythonRuntime.eval(code);
    }

    @Override
    public Object global(final String name) {
        return PythonRuntime.toJava(PythonRuntime.global(name));
    }

    @Override
    public void discard() {
        PythonRuntime.discardContext();
    }

    @Override
    public boolean settleAfter(final Throwable failure) {
        return PythonRuntime.settleAfter(failure);
    }

    @Override
    public Object enterModule() {
        return PythonRuntime.enterModule();
    }

    @Override
    public void leaveModule(final Object frame) {
        PythonRuntime.leaveModule((PythonModuleFrame) frame);
    }

    @Override
    public void installModule(final String name, final String source) {
        PythonRuntime.module(name, source);
    }

    @Override
    public void restoreShared() {
        PythonRuntime.restoreShared();
    }

    @Override
    public boolean holdsPolyglotValues() {
        return true;
    }

}
