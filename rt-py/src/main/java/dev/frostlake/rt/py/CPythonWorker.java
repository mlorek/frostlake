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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * One CPython worker process — the interpreter one engine thread runs its Python in — and the line protocol to it
 * (see {@code cpython_worker.py}). Requests nest: while the worker evaluates a handler it may call back into the
 * engine ({@code session.sql}), and while the engine serves that call it may send the worker requests of its own,
 * so {@link #request} answers callbacks until its own reply arrives. Used by one thread only.
 *
 * <p>A request running longer than {@link #EXECUTION_TIMEOUT_SECONDS} kills the worker, as the GraalPy path
 * cancels its context: callers hold the engine's lock, and one wedged handler must not stall every other thread.
 * What the handler prints goes to the worker's stderr and is logged under {@code dev.frostlake.udf.console}.
 */
final class CPythonWorker {

    /** Longest a single top-level request may run before the worker is treated as wedged. */
    static final long EXECUTION_TIMEOUT_SECONDS = 60;

    private static final Logger logger = LoggerFactory.getLogger(CPythonWorker.class);
    private static final Logger console = LoggerFactory.getLogger("dev.frostlake.udf.console");
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final int STDERR_TAIL_LINES = 40;

    /** The JVM's zone before any Python ran, handed to every worker as TZ, as the GraalPy path does. */
    private static final TimeZone HOST_TIME_ZONE = TimeZone.getDefault();

    private static final ScheduledExecutorService WATCHDOG =
        Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                final Thread thread = new Thread(r, "frostlake-cpython-watchdog");
                thread.setDaemon(true);
                return thread;
            }
        });

    private static Path script;

    private final Process process;
    private final BufferedWriter toWorker;
    private final BufferedReader fromWorker;
    private final Deque<String> stderrTail = new ArrayDeque<>();
    private final Map<String, Object> hosts = new HashMap<>();
    private final Map<String, String> hostHandles = new HashMap<>();
    private String version = "";
    private int minor = -1;
    private int depth;
    private long hostSequence;
    private volatile boolean timedOut;
    private boolean broken;
    /** Whether a request ran since the shared state was last put back (see {@link #restore()}). */
    private boolean ran;

    private CPythonWorker(final Process process) {
        this.process = process;
        this.toWorker = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.fromWorker = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Start a worker with {@code executable} and wait for its greeting.
     *
     * @param executable the CPython interpreter, typically a venv's {@code bin/python}
     * @return the running worker
     */
    static CPythonWorker start(final String executable) {
        // Isolated mode (-I): neither the script's directory nor the working directory goes on sys.path, and the
        // PYTHON* variables and the user's site-packages are ignored, so no file a third party left in a directory
        // the engine shares stands in for a standard-library module. The site-packages of the interpreter named
        // (a venv's own, through its pyvenv.cfg) still load. Measured with a decimal.py planted in java.io.tmpdir:
        // without -I the worker imported it at startup; with it the worker imports the standard library's.
        final ProcessBuilder builder = new ProcessBuilder(executable, "-I", "-u", workerScript().toString());
        builder.environment().put("TZ", HOST_TIME_ZONE.getID());
        final Process process;
        try {
            process = builder.start();
        } catch (final IOException e) {
            throw new IllegalStateException("Could not start the CPython interpreter '" + executable
                + "' (python.executable): " + e.getMessage(), e);
        }
        final CPythonWorker worker = new CPythonWorker(process);
        final Thread pump = worker.startStderrPump();
        final JsonNode hello;
        try {
            final String line = worker.fromWorker.readLine();
            if (line == null) {
                awaitQuietly(pump);
                throw new IllegalStateException("The CPython interpreter '" + executable + "' exited before it was"
                    + " ready" + worker.stderrSummary());
            }
            hello = CPythonCodec.MAPPER.readTree(line).get("hello");
        } catch (final IOException | RuntimeException e) {
            worker.close();
            throw new IllegalStateException("Could not talk to the CPython interpreter '" + executable + "': "
                + e.getMessage() + worker.stderrSummary(), e);
        }
        if (hello == null) {
            worker.close();
            throw new IllegalStateException("The CPython interpreter '" + executable + "' did not greet the engine");
        }
        worker.version = hello.path("version").asString();
        worker.minor = hello.path("minor").asInt(-1);
        logger.debug("Started CPython {} worker {} for thread {}", worker.version, executable,
            Thread.currentThread().getName());
        return worker;
    }

    /**
     * The worker script, copied out of the jar once per JVM so a plain interpreter can run it. It goes into a
     * directory of its own that only the engine's user may enter, not straight into {@code java.io.tmpdir}, which on
     * Linux every local user can write to: nobody else can swap the script or place a module beside it.
     */
    private static synchronized Path workerScript() {
        if (script == null) {
            try (InputStream in = CPythonWorker.class.getResourceAsStream("cpython_worker.py")) {
                if (in == null) {
                    throw new IllegalStateException("cpython_worker.py resource not found");
                }
                final Path directory = privateDirectory();
                directory.toFile().deleteOnExit();
                final Path copy = directory.resolve("cpython_worker.py");
                Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
                copy.toFile().deleteOnExit();
                script = copy;
            } catch (final IOException e) {
                throw new IllegalStateException("Could not write the CPython worker script: " + e.getMessage(), e);
            }
        }
        return script;
    }

    /** A new temporary directory only its owner may read, write or enter, where the file system has such modes. */
    private static Path privateDirectory() throws IOException {
        try {
            return Files.createTempDirectory("frostlake-cpython-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (final UnsupportedOperationException notPosix) {
            // Windows: the temporary directory is the user's own already.
            return Files.createTempDirectory("frostlake-cpython-");
        }
    }

    private Thread startStderrPump() {
        final BufferedReader errors = new BufferedReader(
            new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        final Thread pump = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String line;
                    while ((line = errors.readLine()) != null) {
                        console.info("[python] {}", line);
                        synchronized (stderrTail) {
                            stderrTail.addLast(line);
                            while (stderrTail.size() > STDERR_TAIL_LINES) {
                                stderrTail.removeFirst();
                            }
                        }
                    }
                } catch (final IOException closed) {
                    // The worker went away; nothing more to log.
                }
            }
        }, "frostlake-cpython-stderr");
        pump.setDaemon(true);
        pump.start();
        return pump;
    }

    private static void awaitQuietly(final Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(2));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** The interpreter's version, e.g. {@code 3.12.4}. */
    String version() {
        return version;
    }

    /** The interpreter's Python minor version (3.x gives x). */
    int minor() {
        return minor;
    }

    boolean isAlive() {
        return !broken && process.isAlive();
    }

    void close() {
        broken = true;
        process.destroyForcibly();
    }

    /** Bind a global; an engine object is bound as a handle the worker calls back through. */
    void bind(final String name, final Object value) {
        final ObjectNode message = op("bind");
        message.put("name", name);
        if (CPythonCodec.isHostObject(value)) {
            // A handle of its own per binding: a nested procedure call rebinding the same name must not
            // redirect the outer handler's session. The previous one is dropped once no handler is running.
            final String previous = hostHandles.get(name);
            if (previous != null && depth == 0) {
                hosts.remove(previous);
            }
            final String handle = name + "#" + (++hostSequence);
            hosts.put(handle, value);
            hostHandles.put(name, handle);
            final ObjectNode host = NODES.objectNode();
            host.put("$host", handle);
            message.set("value", host);
        } else {
            message.set("value", CPythonCodec.encode(value));
        }
        request(message);
    }

    /** Enter a routine call's own module namespace, answering the frame's depth (see cpython_worker.py). */
    int enter() {
        return request(op("enter")).path("frame").asInt();
    }

    /** Leave the frame {@link #enter()} answered, the namespace holding what it held before it. */
    void leave(final int frame) {
        final ObjectNode message = op("leave");
        message.put("frame", frame);
        request(message);
    }

    /** Make a helper module of the runtime's from its source, once per worker; it is importable by its name. */
    void module(final String name, final String source) {
        final ObjectNode message = op("module");
        message.put("name", name);
        message.put("source", source);
        request(message);
    }

    /** Put back what routines share between calls (built-ins, environment, the runtime's modules), see cpython_worker.py. */
    void restore() {
        request(op("restore"));
        ran = false;
    }

    /** Whether the worker ran any request since {@link #restore()} last put the shared state back. */
    boolean ranSinceRestore() {
        return ran;
    }

    void eval(final String code) {
        final ObjectNode message = op("eval");
        message.put("code", code);
        request(message);
    }

    /** A global as a plain Java value, or null when unset or None. */
    Object get(final String name) {
        final ObjectNode message = op("get");
        message.put("name", name);
        final JsonNode reply = request(message);
        return reply.has("missing") ? null : CPythonCodec.decode(reply.get("value"));
    }

    private static ObjectNode op(final String op) {
        final ObjectNode message = NODES.objectNode();
        message.put("op", op);
        return message;
    }

    private JsonNode request(final ObjectNode message) {
        if (broken) {
            throw new IllegalStateException("The CPython worker is no longer running");
        }
        if (!"restore".equals(message.path("op").asString())) {
            ran = true;
        }
        final ScheduledFuture<?> alarm = depth == 0 ? WATCHDOG.schedule(new Runnable() {
            @Override
            public void run() {
                timedOut = true;
                process.destroyForcibly();
            }
        }, EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS) : null;
        depth++;
        try {
            send(message);
            while (true) {
                final JsonNode reply = receive();
                if (reply.has("re")) {
                    if (reply.has("error")) {
                        throw error(reply.get("error"));
                    }
                    return reply;
                }
                if ("call".equals(reply.path("op").asString())) {
                    answer(reply);
                    continue;
                }
                throw new IllegalStateException("Unexpected message from the CPython worker: " + reply);
            }
        } finally {
            depth--;
            if (alarm != null) {
                alarm.cancel(false);
            }
        }
    }

    private void send(final JsonNode message) {
        try {
            toWorker.write(CPythonCodec.MAPPER.writeValueAsString(message));
            toWorker.write('\n');
            toWorker.flush();
        } catch (final IOException e) {
            broken = true;
            throw new IllegalStateException(gone("could not be written to"), e);
        }
    }

    private JsonNode receive() {
        final String line;
        try {
            line = fromWorker.readLine();
        } catch (final IOException e) {
            broken = true;
            throw new IllegalStateException(gone("could not be read from"), e);
        }
        if (line == null) {
            broken = true;
            throw new IllegalStateException(gone("exited"));
        }
        try {
            return CPythonCodec.MAPPER.readTree(line);
        } catch (final RuntimeException e) {
            broken = true;
            throw new IllegalStateException("Unreadable message from the CPython worker: " + line, e);
        }
    }

    private String gone(final String what) {
        if (timedOut) {
            return "Python execution timed out after " + EXECUTION_TIMEOUT_SECONDS + " seconds";
        }
        return "The CPython worker " + what + stderrSummary();
    }

    private String stderrSummary() {
        synchronized (stderrTail) {
            if (stderrTail.isEmpty()) {
                return "";
            }
            return ":\n" + String.join("\n", stderrTail);
        }
    }

    private static CPythonError error(final JsonNode failure) {
        final String traceback = failure.path("traceback").asString("");
        if (!traceback.isEmpty()) {
            logger.debug("Python traceback:\n{}", traceback);
        }
        return new CPythonError(failure.path("text").asString("Python error"), failure.path("type").asString(""),
            traceback);
    }

    /** Serve one call the worker made into the engine and send its result (or the failure) back. */
    private void answer(final JsonNode call) {
        final ObjectNode reply = NODES.objectNode();
        reply.put("re", 1);
        try {
            final Object target = hosts.get(call.path("handle").asString());
            final Object result = invoke(target, call.path("method").asString(),
                CPythonCodec.decodeAll(call.get("args")));
            reply.set("ret", CPythonCodec.encode(result));
        } catch (final RuntimeException failure) {
            reply.put("raise", failure.getMessage() != null ? failure.getMessage() : failure.toString());
        }
        send(reply);
    }

    private static Object invoke(final Object target, final String method, final List<Object> args) {
        if (target instanceof SnowparkSession) {
            final SnowparkSession session = (SnowparkSession) target;
            final String argument = args.isEmpty() ? null : String.valueOf(args.get(0));
            if ("sql".equals(method)) {
                return session.sql(argument);
            }
            if ("attempt".equals(method)) {
                return session.attempt(argument);
            }
            if ("tableExists".equals(method)) {
                return session.tableExists(argument);
            }
            if ("table".equals(method)) {
                return session.table(argument);
            }
        }
        throw new IllegalArgumentException("No engine method '" + method + "' on "
            + (target == null ? "an unknown engine object" : target.getClass().getSimpleName()));
    }
}
