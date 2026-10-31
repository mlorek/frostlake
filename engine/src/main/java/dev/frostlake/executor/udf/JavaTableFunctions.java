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

import dev.frostlake.config.S3PathResolver;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.storage.Row;
import dev.frostlake.values.BinaryValue;
import dev.frostlake.values.VariantValue;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Runs a table function written in Java (live-verified). Its HANDLER names a class: a public {@code process}
 * method taking the function's arguments answers a {@code java.util.stream.Stream} of output objects for each
 * input row, a public {@code endPartition()} - when the class has one - answers more once a partition's rows
 * are through, and a {@code getOutputClass()} names the output class, whose public fields are the declared
 * columns by name, whatever their case and order. One instance of the class, made with its zero-argument
 * constructor, reads one partition.
 *
 * <p>What is wrong with the class is refused when the function is created: a {@code process} of another
 * arity, one that answers no {@code Stream}, a missing {@code getOutputClass()}, a declared column the output
 * class has no public field for. A {@code null} where a {@code Stream} is due, an argument the method's type
 * cannot hold and whatever the handler raises are the call's.
 */
public final class JavaTableFunctions {

    private static final JavaFunctionCompiler COMPILER = new JavaFunctionCompiler();

    /** Names the state that holds a statement's own load of a table function's class an IMPORTS jar holds. */
    private static final String JAR_TABLE_HANDLER_CLASS = "jar-table-handler-class";

    /** Names the state that holds a statement's own load of a table function's class its inline body holds. */
    private static final String INLINE_TABLE_HANDLER_CLASS = "inline-table-handler-class";

    /** Static helpers only — never instantiated. */
    private JavaTableFunctions() {
    }

    /**
     * Judges a table function's handler as creating the function does.
     *
     * @param function   the function
     * @param catalog    the catalog an IMPORTS reference is resolved in, or null
     * @param s3Resolver resolves an IMPORTS reference in external storage, or null
     */
    public static void check(final Function function, final Catalog catalog, final S3PathResolver s3Resolver) {
        handlerOf(function, loaded(function, catalog, s3Resolver));
    }

    /**
     * Judges a class another language compiled as a table function's handler: the account holds a Scala table
     * function to the Java rules.
     *
     * @param function the function
     * @param handled  the handler's class
     */
    public static void check(final Function function, final Class<?> handled) {
        handlerOf(function, handled);
    }

    /**
     * Runs the handler over one partition of the function's input.
     *
     * @param function   the function
     * @param rows       the partition's rows, each the function's arguments
     * @param catalog    the catalog an IMPORTS reference is resolved in, or null
     * @param s3Resolver resolves an IMPORTS reference in external storage, or null
     * @return what the handler wrote
     */
    public static TablePartitionRows run(final Function function, final List<List<Object>> rows,
                                         final Catalog catalog, final S3PathResolver s3Resolver) {
        return run(function, loaded(function, catalog, s3Resolver), rows);
    }

    /**
     * Runs a handler class another language compiled over one partition of the function's input.
     *
     * @param function the function
     * @param handled  the handler's class
     * @param rows     the partition's rows, each the function's arguments
     * @return what the handler wrote
     */
    public static TablePartitionRows run(final Function function, final Class<?> handled,
                                         final List<List<Object>> rows) {
        final String where = where(function);
        final JavaTableHandler handler = handlerOf(function, handled);
        final boolean needsInstance = !Modifier.isStatic(handler.process().getModifiers())
            || handler.endPartition() != null && !Modifier.isStatic(handler.endPartition().getModifiers());
        final Object instance = needsInstance ? JavaHandlerInstances.create(handler.handled(), where) : null;
        final List<List<Row>> written = new ArrayList<>();
        for (final List<Object> arguments : rows) {
            final Object[] javaArguments = JavaHandlerArguments.of(handler.process().getParameterTypes(),
                function.getParameters(), arguments, where);
            written.add(rowsOf(COMPILER.invokeHandler(handler.handled(), handler.process(),
                Modifier.isStatic(handler.process().getModifiers()) ? null : instance, javaArguments, where),
                "process", function, handler, where));
        }
        final List<Row> atEnd = handler.endPartition() == null ? new ArrayList<Row>()
            : rowsOf(COMPILER.invokeHandler(handler.handled(), handler.endPartition(),
                Modifier.isStatic(handler.endPartition().getModifiers()) ? null : instance, new Object[0], where),
                "endPartition", function, handler, where);
        return new TablePartitionRows(written, atEnd);
    }

    static String where(final Function function) {
        return " in function " + function.getName() + " with handler " + function.getHandler();
    }

    /** The class an inline body or an IMPORTS JAR holds for the handler, or the report of a class neither has. */
    static Class<?> loaded(final Function function, final Catalog catalog, final S3PathResolver s3Resolver) {
        final String where = where(function);
        final String className = function.getHandler();
        try {
            if (function.getImports().isEmpty()) {
                return inlineClass(function, className, where, Collections.<File>emptyList());
            }
            if (function.getBody() != null && !function.getBody().trim().isEmpty()) {
                // An inline body beside imports compiles with their jars on its class path and runs beside them; a
                // body that holds no handler class leaves the handler to the jars (live-verified).
                try {
                    return inlineClass(function, className, where,
                        JarHandlerLoader.localJars(function.getImports(), catalog, s3Resolver));
                } catch (final RuntimeException notInTheBody) {
                    if (!String.valueOf(notInTheBody.getMessage()).startsWith("No class bytes generated for")) {
                        throw notInTheBody;
                    }
                }
            }
            return importedClass(function, className, catalog, s3Resolver);
        } catch (final RuntimeException unreadable) {
            if (String.valueOf(unreadable.getMessage()).startsWith("No class bytes generated for")) {
                throw new RuntimeException(JavaHandlerFailure.missingClass(className,
                    JavaRuntimeLevel.declared(function.getRuntimeVersion()), where), unreadable);
            }
            throw unreadable;
        }
    }

    /**
     * The class an inline body holds for a table function's handler, kept as a jar's is (see {@link #importedClass}):
     * what its static fields hold carries from one partition of the call to the next, over the rows a LATERAL call is
     * made for, and to no other call (live-verified: a counter it keeps reads 1, 2 and 3 over three rows).
     */
    private static Class<?> inlineClass(final Function function, final String className, final String where,
                                        final List<File> jars) {
        final AutoCloseable kept = StatementHandlerStates.state(function, INLINE_TABLE_HANDLER_CLASS,
            new HandlerStateFactory() {
                @Override
                public AutoCloseable create() {
                    return new JavaHandlerClass(COMPILER.load(function.getBody(), className, null,
                        JavaRuntimeLevel.of(function.getRuntimeVersion()), where, true, jars));
                }
            });
        return kept != null ? ((JavaHandlerClass) kept).handled()
            : COMPILER.load(function.getBody(), className, null, JavaRuntimeLevel.of(function.getRuntimeVersion()),
                where, true, jars);
    }

    /**
     * The class an IMPORTS jar holds for a table function's handler: loaded anew for the statement that is running
     * and the place that calls the function, so what its static fields hold carries from one partition of the call
     * to the next - over the rows a LATERAL call is made for - and to nothing else: not to another call of the
     * function in the statement, nor to a later statement (live-verified). Outside a statement, a class of its own.
     */
    private static Class<?> importedClass(final Function function, final String className, final Catalog catalog,
                                          final S3PathResolver s3Resolver) {
        final AutoCloseable kept = StatementHandlerStates.state(function, JAR_TABLE_HANDLER_CLASS,
            new HandlerStateFactory() {
                @Override
                public AutoCloseable create() {
                    return new JavaHandlerClass(JarHandlerLoader.loadAnew(function.getImports(), className, catalog,
                        s3Resolver));
                }
            });
        return kept != null ? ((JavaHandlerClass) kept).handled()
            : JarHandlerLoader.loadAnew(function.getImports(), className, catalog, s3Resolver);
    }

    /** The handler's members, or the refusal of a class that cannot be a table function's. */
    private static JavaTableHandler handlerOf(final Function function, final Class<?> handled) {
        final String where = where(function);
        final int arity = function.getParameters() == null ? 0 : function.getParameters().size();
        final Method outputClass = publicMethod(handled, "getOutputClass", 0);
        if (outputClass == null) {
            throw new RuntimeException("Failed to find a public method named \"getOutputClass\" with 0 arguments\n"
                + "The getOutputClass() method is required for Java UDTFs" + where);
        }
        // The output class is asked of the handler's class, never of an instance of it (live-verified).
        if (!Modifier.isStatic(outputClass.getModifiers())) {
            throw new RuntimeException(JavaHandlerFailure.missingMethod("getOutputClass",
                "The getOutputClass() method must be static and return a java.lang.Class", where));
        }
        final Method process = publicMethod(handled, "process", arity);
        if (process == null) {
            throw new RuntimeException("Failed to find a public method named \"process\" with " + arity
                + " arguments" + where);
        }
        // Overloads of process that take as many arguments as the function leave the call undecided (live-verified).
        if (definitions(handled, "process", arity) > 1) {
            throw new RuntimeException("Cannot determine which implementation of handler \"process\" to invoke since"
                + " there are multiple definitions with " + arity + " arguments" + where);
        }
        if (process.getReturnType() != java.util.stream.Stream.class) {
            throw new RuntimeException("The return type of the \"process\" method is \""
                + process.getReturnType().getName() + "\" but must be java.util.stream.Stream" + where);
        }
        // An endPartition the class has is an instance's, public, and answers a Stream (live-verified).
        final Method endPartition = endPartitionOf(handled);
        if (endPartition != null && (!Modifier.isPublic(endPartition.getModifiers())
                || Modifier.isStatic(endPartition.getModifiers())
                || endPartition.getReturnType() != java.util.stream.Stream.class)) {
            throw new RuntimeException("Optional endPartition() method must be public, non-static, and return a"
                + " java.util.stream.Stream" + where);
        }
        final Class<?> output = (Class<?>) invoke(handled, outputClass, null, where);
        final List<Parameter> declared = function.getReturnColumns();
        final Field[] columns = new Field[declared.size()];
        final StringBuilder missing = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            columns[i] = output == null ? null : publicField(output, declared.get(i).getName());
            if (columns[i] == null) {
                missing.append(missing.length() == 0 ? "" : ", ")
                    .append(declared.get(i).getName().toUpperCase(Locale.ROOT));
            }
        }
        if (missing.length() > 0) {
            throw new RuntimeException("The output class \"" + (output == null ? "null" : output.getSimpleName())
                + "\" is missing fields with the following names: " + missing
                + ". Make sure the member fields are public" + where);
        }
        return new JavaTableHandler(handled, process, endPartition, columns);
    }

    /** The class's endPartition taking no arguments, public or not, or null when it has none. */
    private static Method endPartitionOf(final Class<?> handled) {
        final Method inherited = publicMethod(handled, "endPartition", 0);
        if (inherited != null) {
            return inherited;
        }
        for (final Method method : handled.getDeclaredMethods()) {
            if ("endPartition".equals(method.getName()) && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }

    /** How many public methods of a name take a number of arguments. */
    private static int definitions(final Class<?> handled, final String name, final int arity) {
        int definitions = 0;
        for (final Method method : handled.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arity) {
                definitions++;
            }
        }
        return definitions;
    }

    static Method publicMethod(final Class<?> handled, final String name, final int arity) {
        for (final Method method : handled.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arity) {
                return method;
            }
        }
        return null;
    }

    /** A class's public field of a column's name, whatever the case either is written in. */
    private static Field publicField(final Class<?> output, final String column) {
        for (final Field field : output.getFields()) {
            if (field.getName().equalsIgnoreCase(column)) {
                return field;
            }
        }
        return null;
    }

    private static Object invoke(final Class<?> handled, final Method method, final Object receiver,
                                 final String where) {
        return COMPILER.invokeHandler(handled, method, receiver, new Object[0], where);
    }

    /** The rows of the output objects a handler method answered, each column the declared type's value. */
    private static List<Row> rowsOf(final Object answered, final String method, final Function function,
                                    final JavaTableHandler handler, final String where) {
        // The handler's own type: what its methods answer is a stream by the language's rules, read here in turn.
        if (!(answered instanceof java.util.stream.Stream)) {
            throw new RuntimeException("Return value from UDTF \"" + method
                + "\" method is null instead of a java.util.stream.Stream object" + where);
        }
        final List<Parameter> declared = function.getReturnColumns();
        final List<Row> rows = new ArrayList<>();
        try {
            final Iterator<?> outputs = ((java.util.stream.Stream<?>) answered).iterator();
            while (outputs.hasNext()) {
                final Object output = outputs.next();
                final List<Object> values = new ArrayList<>(declared.size());
                for (int i = 0; i < declared.size(); i++) {
                    final Field field = handler.columns()[i];
                    field.setAccessible(true);
                    values.add(sqlValue(HandlerResults.fromJava(field.get(output), declared.get(i).getDataType(),
                        function.getName(), function.getHandler())));
                }
                rows.add(new Row(values));
            }
        } catch (final IllegalAccessException unreadable) {
            throw new RuntimeException("Failed to read a Java table function's output" + where, unreadable);
        } catch (final RuntimeException raised) {
            // What the handler's stream raises while it is read is the handler's, reported as its own frames.
            if (raised.getCause() instanceof InvocationTargetException || isReport(raised)) {
                throw raised;
            }
            throw new RuntimeException(JavaHandlerFailure.report(raised, handler.handled(), where), raised);
        }
        return rows;
    }

    private static boolean isReport(final RuntimeException raised) {
        final String message = String.valueOf(raised.getMessage());
        return message.startsWith("User Error Report:") || message.contains(" in function ");
    }

    /** A converted value as a column holds it: a whole number a long, the engine's own values as they are. */
    private static Object sqlValue(final Object converted) {
        if (converted == null || converted instanceof BinaryValue || converted instanceof VariantValue
                || converted instanceof TemporalAccessor || converted instanceof Number
                && !(converted instanceof Integer) || converted instanceof String || converted instanceof Boolean) {
            return converted;
        }
        if (converted instanceof Integer) {
            return Long.valueOf(((Integer) converted).longValue());
        }
        return converted.toString();
    }
}
