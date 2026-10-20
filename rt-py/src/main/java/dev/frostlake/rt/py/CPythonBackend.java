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
 * CPython in worker processes, one per engine thread that runs Python (as GraalPy keeps one context per thread).
 * Real CPython means the packages a venv installs work as they do on Snowflake, native extensions included, on
 * every platform the interpreter runs on.
 */
final class CPythonBackend implements PythonBackend {

    private final String executable;
    private final ThreadLocal<CPythonWorker> workers = new ThreadLocal<>();
    private volatile String version;
    private volatile int minor = -1;

    CPythonBackend(final String executable) {
        this.executable = executable;
    }

    private CPythonWorker worker() {
        CPythonWorker worker = workers.get();
        if (worker == null || !worker.isAlive()) {
            worker = CPythonWorker.start(executable);
            workers.set(worker);
            version = worker.version();
            minor = worker.minor();
        }
        return worker;
    }

    @Override
    public String description() {
        if (version == null) {
            worker();
        }
        return version + " (CPython)";
    }

    @Override
    public int minorVersion() {
        if (minor < 0) {
            worker();
        }
        return minor;
    }

    @Override
    public void bind(final String name, final Object value) {
        worker().bind(name, value);
    }

    @Override
    public void bindArgument(final Parameter parameter, final String name, final Object value) {
        worker().bind(name, CPythonCodec.argument(parameter.getDataType(), value));
    }

    @Override
    public void bindProcedureArgument(final Parameter parameter, final String name, final Object value) {
        bindArgument(parameter, name, value);
    }

    @Override
    public void eval(final String code) {
        worker().eval(code);
    }

    @Override
    public Object global(final String name) {
        return worker().get(name);
    }

    @Override
    public void discard() {
        final CPythonWorker worker = workers.get();
        workers.remove();
        if (worker != null) {
            worker.close();
        }
    }

    /**
     * A Python exception the worker reported ({@link CPythonError}) left it answering, and the worker is kept; any
     * other failure (the worker died, timed out or broke the protocol) may have left it mid-request, and it is
     * stopped.
     */
    @Override
    public boolean settleAfter(final Throwable failure) {
        final CPythonWorker worker = workers.get();
        if (failure instanceof CPythonError && worker != null && worker.isAlive()) {
            return true;
        }
        discard();
        return false;
    }

    @Override
    public Object enterModule() {
        final CPythonWorker worker = worker();
        return new Object[] {worker, Integer.valueOf(worker.enter())};
    }

    /** A frame of a worker since stopped has nothing to put back. */
    @Override
    public void leaveModule(final Object frame) {
        final Object[] held = (Object[]) frame;
        final CPythonWorker worker = workers.get();
        if (worker == held[0] && worker.isAlive()) {
            worker.leave(((Integer) held[1]).intValue());
        }
    }

    @Override
    public void installModule(final String name, final String source) {
        worker().module(name, source);
    }

    /** Only a worker that ran a request since the last statement ended has anything to put back. */
    @Override
    public void restoreShared() {
        final CPythonWorker worker = workers.get();
        if (worker != null && worker.isAlive() && worker.ranSinceRestore()) {
            worker.restore();
        }
    }

    @Override
    public boolean holdsPolyglotValues() {
        return false;
    }

}
