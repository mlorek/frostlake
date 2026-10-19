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

package dev.frostlake.executor;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Privilege;
import dev.frostlake.metastore.model.RelationKind;
import dev.frostlake.metastore.model.Schema;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * SYSTEM$REFERENCE(domain, name [, scope [, privilege, …]]): a token standing for an object, {@code ENT_REF_TABLE_}
 * then sixteen decimal digits, an underscore and forty upper-case hexadecimal digits, new for every call. The
 * arguments are judged in this order (live-verified):
 *
 * <ul>
 *   <li>a NULL domain, name or privilege is {@code invalid number of arguments for [1], expected 2, got {4}} at
 *       live's unwritten place, a NULL scope standing for SESSION;</li>
 *   <li>the scope is SESSION, CALL or PERSISTENT, else {@code Scope X not valid. Scope must be SESSION, CALL or
 *       PERSISTENT.}; then the domain, else {@code Domain X not valid for reference creation with scope SESSION} —
 *       EXTERNAL TABLE among them in a SESSION;</li>
 *   <li>each privilege is one, else {@code Invalid privilege X.}, never OWNERSHIP, and one the domain takes: USAGE
 *       on a table is {@code Invalid object type 'TABLE' for privilege 'USAGE'.}, a write on a view {@code Privilege
 *       'INSERT' is not allowed on domain 'VIEW' during 'SESSION' reference creation.}; a materialized view has no
 *       privileges unless some are given;</li>
 *   <li>a function's and a procedure's name carries its argument types, else the signature is a syntax error at its
 *       end; a table, a view or a stream must be there, of the domain named ({@code Object type VIEW does not match
 *       the specified type TABLE for reference creation}), and so must a warehouse.</li>
 * </ul>
 */
final class ObjectReferenceTokens {

    private static final Set<String> SCOPES = new HashSet<>(Arrays.asList("SESSION", "CALL", "PERSISTENT"));

    /** The domains a reference is made for; a function's and a procedure's take a signature. */
    private static final Set<String> DOMAINS = new HashSet<>(Arrays.asList(
        "TABLE", "VIEW", "STREAM", "MATERIALIZED VIEW", "EXTERNAL TABLE", "DYNAMIC TABLE", "ICEBERG TABLE",
        "FUNCTION", "PROCEDURE", "WAREHOUSE", "DATABASE", "SCHEMA", "STAGE", "PIPE", "TASK", "SECRET",
        "API INTEGRATION", "EXTERNAL ACCESS INTEGRATION", "SECURITY INTEGRATION", "COMPUTE POOL"));

    /** The privileges a reference to a table may carry. */
    private static final Set<String> TABLE_PRIVILEGES = new HashSet<>(Arrays.asList(
        "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "REBUILD", "EVOLVE SCHEMA", "APPLYBUDGET"));

    /** The privileges a view's reference may carry of a table's. */
    private static final Set<String> VIEW_PRIVILEGES = new HashSet<>(Arrays.asList("SELECT", "REFERENCES"));

    private static final SecureRandom DRAW = new SecureRandom();

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private ObjectReferenceTokens() {
    }

    /**
     * A reference to the object the arguments name.
     *
     * @param catalog the catalog the name is read in
     * @param args    the call's arguments
     * @return the token
     */
    static String create(final Catalog catalog, final List<Object> args) {
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i) == null && i != 2) {
                throw new RuntimeException(SqlCompilationError.at(0, -1, "invalid number of arguments for ["
                    + (i + 1) + "], expected " + Math.max(2, i + 1) + ", got {4}"));
            }
        }
        final String scope = args.size() > 2 && args.get(2) != null ? upper(args.get(2)) : "SESSION";
        if (!SCOPES.contains(scope)) {
            throw new RuntimeException("Scope " + scope + " not valid. Scope must be SESSION, CALL or PERSISTENT.");
        }
        final String domain = upper(args.get(0));
        if (!DOMAINS.contains(domain) || "EXTERNAL TABLE".equals(domain) && "SESSION".equals(scope)) {
            throw new RuntimeException("Domain " + domain + " not valid for reference creation with scope " + scope);
        }
        for (int i = 3; i < args.size(); i++) {
            requirePrivilege(domain, scope, upper(args.get(i)));
        }
        requireSignature(domain, args.get(1).toString());
        if ("MATERIALIZED VIEW".equals(domain) && args.size() < 4) {
            throw new RuntimeException("No default privileges defined for reference domain MATERIALIZED_VIEW. Please"
                + " provide allowed privileges during reference creation.");
        }
        requireObject(catalog, domain, args.get(1).toString());
        return token(domain);
    }

    /**
     * A function's or a procedure's reference names it with its argument types; a name without them stops the
     * signature's reading at its end (live-verified).
     */
    private static void requireSignature(final String domain, final String written) {
        if (("FUNCTION".equals(domain) || "PROCEDURE".equals(domain)) && written.indexOf('(') < 0) {
            throw new RuntimeException(SqlCompilationError.of("syntax error line 1 at position " + written.length()
                + " unexpected '<EOF>'."));
        }
    }

    private static void requirePrivilege(final String domain, final String scope, final String privilege) {
        if ("OWNERSHIP".equals(privilege)) {
            throw new RuntimeException("A reference cannot be created with OWNERSHIP privilege.");
        }
        if (!isPrivilege(privilege)) {
            throw new RuntimeException(SqlCompilationError.at(1, 4, "Invalid privilege " + privilege + "."));
        }
        if ("TABLE".equals(domain) && !TABLE_PRIVILEGES.contains(privilege)) {
            throw new RuntimeException(SqlCompilationError.of("Invalid object type '" + domain + "' for privilege '"
                + privilege + "'."));
        }
        if ("VIEW".equals(domain) && TABLE_PRIVILEGES.contains(privilege) && !VIEW_PRIVILEGES.contains(privilege)) {
            throw new RuntimeException("Privilege '" + privilege + "' is not allowed on domain '" + domain
                + "' during '" + scope + "' reference creation.");
        }
    }

    /** Whether a word names a privilege, its words joined as the grants spell them. */
    private static boolean isPrivilege(final String privilege) {
        try {
            Privilege.valueOf(privilege.replace(' ', '_'));
            return true;
        } catch (final IllegalArgumentException none) {
            return TABLE_PRIVILEGES.contains(privilege) || "USAGE".equals(privilege);
        }
    }

    /** A table, a view, a stream or a warehouse the reference names is there, of the domain named. */
    private static void requireObject(final Catalog catalog, final String domain, final String written) {
        if ("WAREHOUSE".equals(domain)) {
            final String name = SqlIdentifiers.canonicalText(written);
            if (!catalog.hasWarehouseExact(name)) {
                throw new RuntimeException(SqlCompilationError.doesNotExist("Warehouse", name));
            }
            return;
        }
        final RelationKind wanted = "TABLE".equals(domain) ? RelationKind.TABLE
            : "VIEW".equals(domain) ? RelationKind.VIEW : "STREAM".equals(domain) ? RelationKind.STREAM : null;
        if (wanted == null) {
            return;
        }
        final String[] parts = SqlIdentifiers.identifierReferenceParts(written);
        final String noun = domain.charAt(0) + domain.substring(1).toLowerCase(Locale.ROOT);
        final RelationKind held;
        try {
            final Schema schema = catalog.requireOwningSchema(QualifiedName.of(parts));
            held = schema.relationKindOf(parts[parts.length - 1]);
        } catch (final RuntimeException noSchema) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(noun, QualifiedName.join(parts)), noSchema);
        }
        if (held == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist(noun, QualifiedName.join(parts)));
        }
        if (held != wanted) {
            throw new RuntimeException("Object type " + held.spelling() + " does not match the specified type "
                + domain + " for reference creation");
        }
    }

    /** A new token for a domain: its name, sixteen decimal digits and forty hexadecimal ones. */
    private static String token(final String domain) {
        final StringBuilder token = new StringBuilder("ENT_REF_").append(domain.replace(' ', '_')).append('_');
        for (int i = 0; i < 16; i++) {
            token.append(DRAW.nextInt(10));
        }
        token.append('_');
        for (int i = 0; i < 40; i++) {
            token.append(HEX[DRAW.nextInt(16)]);
        }
        return token.toString();
    }

    private static String upper(final Object value) {
        return value.toString().trim().toUpperCase(Locale.ROOT);
    }
}
