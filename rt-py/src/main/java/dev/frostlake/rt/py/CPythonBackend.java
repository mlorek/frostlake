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
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * CPython in worker processes, one per engine thread that runs Python (as GraalPy keeps one context per thread).
 * Real CPython means the packages a venv installs work as they do on Snowflake, native extensions included, on
 * every platform the interpreter runs on. A worker whose thread has ended is stopped when the next worker starts,
 * and by a sweep every {@link #SWEEP_SECONDS} seconds in between, so a server that runs its statements on threads
 * that come and go keeps no process for a thread that is gone.
 */
final class CPythonBackend implements PythonBackend {

    /** How long the workers of threads that have ended may wait for a new worker's start to stop them. */
    private static final long SWEEP_SECONDS = 10;

    private final String executable;
    private final ThreadLocal<CPythonWorker> workers = new ThreadLocal<>();
    /** Each worker started and not stopped, with the thread it serves: only that thread can reach it. */
    private final Map<CPythonWorker, Thread> owners = new IdentityHashMap<>();
    private volatile String version;
    private volatile int minor = -1;

    CPythonBackend(final String executable) {
        this.executable = executable;
        CPythonWorker.every(SWEEP_SECONDS, new Runnable() {
            @Override
            public void run() {
                try {
                    sweep();
                } catch (final RuntimeException unstopped) {
                    // The schedule outlives a sweep that failed: a worker it could not stop ends with the JVM.
                }
            }
        });
    }

    private CPythonWorker worker() {
        CPythonWorker worker = workers.get();
        if (worker == null || !worker.isAlive()) {
            if (worker != null) {
                release(worker);
            }
            sweep();
            worker = CPythonWorker.start(executable);
            synchronized (owners) {
                owners.put(worker, Thread.currentThread());
            }
            workers.set(worker);
            version = worker.version();
            minor = worker.minor();
        }
        return worker;
    }

    /** Stop the workers whose threads have ended: no call can reach them again. */
    private void sweep() {
        final List<CPythonWorker> ended = new ArrayList<>();
        synchronized (owners) {
            final Iterator<Map.Entry<CPythonWorker, Thread>> entries = owners.entrySet().iterator();
            while (entries.hasNext()) {
                final Map.Entry<CPythonWorker, Thread> entry = entries.next();
                if (!entry.getValue().isAlive()) {
                    ended.add(entry.getKey());
                    entries.remove();
                }
            }
        }
        for (final CPythonWorker worker : ended) {
            worker.close();
        }
    }

    /** Forget a worker of this thread's and stop it. */
    private void release(final CPythonWorker worker) {
        synchronized (owners) {
            owners.remove(worker);
        }
        worker.close();
    }

    /** How many of the workers started are running. */
    int runningWorkers() {
        int running = 0;
        synchronized (owners) {
            for (final CPythonWorker worker : owners.keySet()) {
                if (worker.isAlive()) {
                    running++;
                }
            }
        }
        return running;
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
            release(worker);
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
    public Object resumeModule(final String key) {
        final CPythonWorker worker = worker();
        final JsonNode entered = worker.resume(key);
        return new Object[] {worker, Integer.valueOf(entered.path("frame").asInt()),
            Boolean.valueOf(entered.path("kept").asBoolean())};
    }

    @Override
    public boolean resumed(final Object frame) {
        final Object[] held = (Object[]) frame;
        return held.length > 2 && Boolean.TRUE.equals(held[2]);
    }

    @Override
    public void keepModule(final String key) {
        final CPythonWorker worker = workers.get();
        if (worker != null && worker.isAlive()) {
            worker.keep(key);
        }
    }

    /** A worker since stopped kept nothing, and one that cannot forget is stopped. */
    @Override
    public void forgetModule(final String key) {
        final CPythonWorker worker = workers.get();
        if (worker != null && worker.isAlive()) {
            try {
                worker.forget(key);
            } catch (final RuntimeException unforgettable) {
                discard();
            }
        }
    }

    @Override
    public void installModule(final String name, final String source) {
        worker().module(name, source);
    }

    /**
     * Only a worker that ran a request since the last statement ended has anything to put back; one that cannot put
     * it back is thrown away, as the GraalPy path discards its context, so the next statement starts a clean one.
     */
    @Override
    public void restoreShared() {
        final CPythonWorker worker = workers.get();
        if (worker != null && worker.isAlive() && worker.ranSinceRestore()) {
            try {
                worker.restore();
            } catch (final RuntimeException unrestorable) {
                discard();
            }
        }
    }

    @Override
    public boolean holdsPolyglotValues() {
        return false;
    }

}
