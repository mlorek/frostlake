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

package dev.frostlake.functions.scalar.context;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.model.Schema;

/**
 * How GET_DDL spells the name of each object it recreates: bare, or — when its third argument asks for fully
 * qualified names — prefixed with the database and schema that hold the object. Only the recreated object's own
 * name is qualified; a name the DDL merely refers to (a view's base table, a stream's source, a pipe's COPY target)
 * keeps the spelling it was stored with.
 *
 * <p>Every part is quoted only where it must be, its own quotes doubled. A routine is the exception when bare: its
 * name is always double-quoted then ({@code "B"()}), where qualified it is spelled like any other part
 * ({@code DB.S.B()}, {@code DB.S."_z"()}).
 *
 * <p>The third argument is read as a boolean the first time a name is spelled, which is after the object has been
 * found: the account refuses a missing object ahead of a value that is no boolean.
 */
final class DdlNames {

    /** The third argument as the call passed it, or null when it has none. */
    private final Object flag;
    /** The flag read as a boolean, once a name has been spelled. */
    private Boolean qualified;

    /**
     * @param flag the call's third argument, or null when it has none
     */
    DdlNames(final Object flag) {
        this.flag = flag;
    }

    /**
     * A schema object's name: {@code NAME}, or {@code DB.SCHEMA.NAME}.
     *
     * @param schema the schema holding the object
     * @param name the object's canonical name
     * @return the name as the DDL spells it
     */
    String object(final Schema schema, final String name) {
        final String spelled = SqlIdentifiers.spellCanonicalEscaped(name);
        return qualified() ? path(schema) + "." + spelled : spelled;
    }

    /**
     * A function's or procedure's name: always double-quoted when bare, spelled part by part when qualified.
     *
     * @param schema the schema holding the routine
     * @param name the routine's canonical name
     * @return the name as the DDL spells it
     */
    String routine(final Schema schema, final String name) {
        return qualified() ? path(schema) + "." + SqlIdentifiers.spellCanonicalEscaped(name) : "\"" + name + "\"";
    }

    /**
     * A schema's own name in its CREATE SCHEMA: {@code SCHEMA}, or {@code DB.SCHEMA}.
     *
     * @param schema the schema
     * @return the name as the DDL spells it
     */
    String schema(final Schema schema) {
        return qualified() ? path(schema) : SqlIdentifiers.spellCanonicalEscaped(schema.getName());
    }

    /** {@code DB.SCHEMA}, each part quoted only where it must be. */
    static String path(final Schema schema) {
        return SqlIdentifiers.spellCanonicalEscaped(schema.getDatabaseName()) + "."
            + SqlIdentifiers.spellCanonicalEscaped(schema.getName());
    }

    /** The third argument as a boolean, read once. */
    private boolean qualified() {
        if (qualified == null) {
            qualified = flag != null && readFlag(flag);
        }
        return qualified;
    }

    /**
     * The third argument cast to a boolean as the account casts it: a number is true unless zero, a text one of the
     * boolean words in any case but with nothing around it. Any other text is refused.
     */
    private static boolean readFlag(final Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        final String text = value.toString();
        switch (text.toUpperCase()) {
            case "TRUE":
            case "T":
            case "YES":
            case "Y":
            case "ON":
            case "1":
                return true;
            case "FALSE":
            case "F":
            case "NO":
            case "N":
            case "OFF":
            case "0":
                return false;
            default:
                throw new RuntimeException(SqlCompilationError.of("Invalid value [CAST('" + text.replace("'", "''")
                    + "' AS BOOLEAN)] for function '2', parameter IMPORT_DDL: constant arguments expected"));
        }
    }
}
