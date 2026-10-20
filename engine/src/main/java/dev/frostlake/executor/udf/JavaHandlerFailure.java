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

package dev.frostlake.executor.udf;

import java.util.ArrayList;
import java.util.List;

/**
 * How an exception a Java handler raises reaches SQL (live-verified): {@code User Error Report: }, a line
 * {@code Java Stack Trace:}, the exception as Java prints it, and its frames down to the handler's own — a frame
 * of the handler's source reads {@code function_handler_0//H.f(InlineCode.java:1)}, its line the source's own —
 * then {@code  in function F with handler H.f}. Frames below the handler's, the engine's, are not shown, and an
 * exception that arose with no frame of the handler above the engine has none. A cause follows as Java prints
 * one, {@code Caused by: }, with the frames it shares with what it caused counted: {@code ... 1 more}.
 */
public final class JavaHandlerFailure {

    private static final String HEAD = "User Error Report: \nJava Stack Trace:\n";

    /** The name the report places a frame of an inline Java body in. */
    private static final String JAVA_SOURCE = "InlineCode.java";

    /** The major version of a class file compiled for Java 17. */
    private static final int JAVA_17_CLASS_FILE = 61;

    private JavaHandlerFailure() {
    }

    /**
     * @param thrown  what the handler, or its constructor, raised
     * @param handled the handler's class, whose source's classes are the handler's own frames
     * @param where   the sentence's end, {@code  in function F with handler H.f}
     * @return the report
     */
    public static String report(final Throwable thrown, final Class<?> handled, final String where) {
        return report(thrown, handled, where, JAVA_SOURCE, null);
    }

    /**
     * The report of a handler whose source is not an inline Java body: a Scala handler's frames are placed in
     * {@code ScalaUDF}, and an {@code object}'s method is reached through a forwarder the report shows as the
     * frame after the handler's own - {@code function_handler_0//H.f(ScalaUDF)} (live-verified).
     *
     * @param thrown    what the handler raised
     * @param handled   the handler's class, whose source's classes are the handler's own frames
     * @param where     the sentence's end, {@code  in function F with handler H.f}
     * @param source    the name the report places a frame of the handler's source in
     * @param forwarder the frame that follows the handler's own frames, or null when none does
     * @return the report
     */
    public static String report(final Throwable thrown, final Class<?> handled, final String where,
                                final String source, final String forwarder) {
        final StringBuilder text = new StringBuilder(HEAD).append(named(thrown, handled));
        List<String> enclosing = frames(thrown, handled, source, forwarder);
        for (final String frame : enclosing) {
            text.append("\n\tat ").append(frame);
        }
        Throwable cause = thrown.getCause();
        int depth = 0;
        while (cause != null && depth < 8) {
            final List<String> own = frames(cause, handled, source, forwarder);
            int common = 0;
            while (common < own.size() && common < enclosing.size()
                    && own.get(own.size() - 1 - common).equals(enclosing.get(enclosing.size() - 1 - common))) {
                common++;
            }
            text.append("\nCaused by: ").append(named(cause, handled));
            for (int i = 0; i < own.size() - common; i++) {
                text.append("\n\tat ").append(own.get(i));
            }
            if (common > 0) {
                text.append("\n\t... ").append(common).append(" more");
            }
            enclosing = own;
            cause = cause.getCause();
            depth++;
        }
        return text.append('\n').append(where).toString();
    }

    /**
     * The report of a handler class the routine's code does not hold, raised as the routine is created: the
     * class loader's frames, numbered as the account's Java runtime of the routine's level numbers them
     * (live-verified for 11, 17 and 21).
     *
     * @param className the class the handler names, as written
     * @param level     the routine's Java level (see {@link JavaRuntimeLevel#declared}), 0 for the default
     * @param where     the sentence's end
     * @return the report
     */
    public static String missingClass(final String className, final int level, final String where) {
        final int found = level >= 17 ? 445 : 476;
        final int loaded = level >= 21 ? 593 : level >= 17 ? 592 : 589;
        final int delegated = level >= 21 ? 526 : level >= 17 ? 525 : 522;
        return report("""
            java.lang.ClassNotFoundException: %s
            \tat java.base/java.net.URLClassLoader.findClass(URLClassLoader.java:%d)
            \tat java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:%d)
            \tat java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:%d)""".formatted(className, found, loaded,
            delegated), where);
    }

    /**
     * An exception with no frame to show: what an account reports for a class it cannot make an instance of.
     *
     * @param named the exception as Java prints it
     * @param where the sentence's end
     * @return the report
     */
    public static String report(final String named, final String where) {
        return HEAD + named + '\n' + where;
    }

    /**
     * An exception as the report names it: as Java prints it, except that a NullPointerException the virtual
     * machine raised is named without the sentence newer machines compose for it when the handler runs on Java 11,
     * whose machine composes none; a handler of a routine whose RUNTIME_VERSION is 17 or later keeps it
     * (live-verified). The level is that of the class file the handler's source compiled to.
     */
    private static String named(final Throwable thrown, final Class<?> handled) {
        final String message = thrown.getMessage();
        if (thrown.getClass() == NullPointerException.class && message != null && message.contains(" because \"")
                && classFileVersion(handled) < JAVA_17_CLASS_FILE) {
            return thrown.getClass().getName();
        }
        return thrown.toString();
    }

    /** The major version of the class file a handler's inline source compiled to, 0 for a class of a JAR. */
    private static int classFileVersion(final Class<?> handled) {
        final ClassLoader loader = handled.getClassLoader();
        return loader instanceof InMemoryClassLoader
            ? ((InMemoryClassLoader) loader).classFileVersion(handled.getName()) : 0;
    }

    /** A throwable's frames from where it arose down to the handler's last own frame, as the report spells them. */
    private static List<String> frames(final Throwable thrown, final Class<?> handled, final String source,
                                       final String forwarder) {
        final StackTraceElement[] trace = thrown.getStackTrace();
        int last = -1;
        for (int i = 0; i < trace.length; i++) {
            if (own(trace[i], handled)) {
                last = i;
            }
        }
        final List<String> frames = new ArrayList<>();
        for (int i = 0; i <= last; i++) {
            final StackTraceElement frame = trace[i];
            if (own(frame, handled)) {
                frames.add("function_handler_0//" + frame.getClassName() + '.' + frame.getMethodName() + '('
                    + source + ':' + frame.getLineNumber() + ')');
            } else if (frame.getClassName().startsWith(SnowparkStatementFailure.SNOWPARK_PACKAGE)) {
                // Snowpark's classes are loaded beside the handler's, and their frames are named as its are.
                frames.add("function_handler_0//" + frame);
            } else {
                frames.add(frame.toString());
            }
        }
        if (forwarder != null && last >= 0) {
            frames.add(forwarder);
        }
        return frames;
    }

    /** Whether a frame is of a class the handler's own loader defined. */
    private static boolean own(final StackTraceElement frame, final Class<?> handled) {
        final ClassLoader loader = handled.getClassLoader();
        if (loader instanceof InMemoryClassLoader) {
            return ((InMemoryClassLoader) loader).defines(frame.getClassName());
        }
        final String name = handled.getName();
        return frame.getClassName().equals(name) || frame.getClassName().startsWith(name + "$");
    }
}
