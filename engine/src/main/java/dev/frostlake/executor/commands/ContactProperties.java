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
package dev.frostlake.executor.commands;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.TerminalNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A contact's properties as CREATE CONTACT and ALTER CONTACT … SET write them and ALTER CONTACT … UNSET clears them
 * (live-verified). COMMENT, URL and EMAIL_DISTRIBUTION_LIST take a string. The address is ONE address whatever its
 * name suggests — a comma-separated list, an address with no dotted domain, a one-letter top level and any address
 * with a space are {@code Invalid email address(es): [x].} — and the URL is a URI with a scheme, {@code mailto:}
 * included ({@code invalid URL: [x]}); the empty string is either one's and clears it. USERS takes a list of user
 * names, each a user that exists ({@code invalid value 'U' for property 'USER'}), kept once each by the name the
 * account holds, as SHOW CONTACTS lists them. A string given for USERS or a list for COMMENT is {@code invalid type of
 * property}, a list for the URL or the address {@code Invalid value specified for property}, any other name {@code
 * invalid property 'NAME' for 'CONTACT'}, and a contact reached by more than one of its URL, its address and its users
 * {@code Email, url or users/email_list, only one could be set.} A statement's properties are judged together before
 * any is written, one fault after another whatever order they are written in: a name, a string for USERS, a list for
 * the address, then for the URL, a user that does not exist, a list for COMMENT, an unusable address, then URL.
 */
final class ContactProperties {

    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@,]+\\.[A-Za-z]{2,}");
    private static final String USERS = "USERS";
    private static final String URL = "URL";
    private static final String ADDRESS = "EMAIL_DISTRIBUTION_LIST";
    private static final String COMMENT = "COMMENT";

    private ContactProperties() {
    }

    /**
     * Judge a statement's properties together, before any is written (see the class comment for the order).
     *
     * @param properties the properties as the statement writes them
     * @param catalog    the catalog, where the users a USERS list names are looked for
     */
    static void judge(final List<FrostlakeParser.ContactPropertyContext> properties, final Catalog catalog) {
        for (final FrostlakeParser.ContactPropertyContext property : properties) {
            final String key = keyOf(property);
            if (!USERS.equals(key) && !isString(key)) {
                throw invalid(key);
            }
        }
        for (final FrostlakeParser.ContactPropertyContext property : properties) {
            if (USERS.equals(keyOf(property)) && property.stringText() != null) {
                throw invalidType(property.stringText().getText(), USERS);
            }
        }
        requireString(properties, ADDRESS);
        requireString(properties, URL);
        for (final FrostlakeParser.ContactPropertyContext property : properties) {
            if (USERS.equals(keyOf(property)) && property.stringLiteralList() != null) {
                for (final TerminalNode name : property.stringLiteralList().STRING_LITERAL()) {
                    final String user = SqlStringLiterals.decode(name.getText());
                    if (!catalog.hasUser(user)) {
                        throw new RuntimeException(SqlCompilationError.of("invalid value '" + user
                            + "' for property 'USER'"));
                    }
                }
            }
        }
        for (final FrostlakeParser.ContactPropertyContext property : properties) {
            if (COMMENT.equals(keyOf(property)) && property.stringText() == null) {
                final StringBuilder listed = new StringBuilder();
                if (property.stringLiteralList() != null) {
                    for (final TerminalNode value : property.stringLiteralList().STRING_LITERAL()) {
                        listed.append(listed.length() > 0 ? ", " : "").append(SqlStringLiterals.decode(
                            value.getText()));
                    }
                }
                throw invalidType("[" + listed + "]", COMMENT);
            }
        }
        for (final FrostlakeParser.ContactPropertyContext property : properties) {
            final String value = stringOf(property, ADDRESS);
            if (value != null && !value.isEmpty() && !EMAIL.matcher(value).matches()) {
                throw new RuntimeException("Invalid email address(es): [" + value + "].");
            }
        }
        for (final FrostlakeParser.ContactPropertyContext property : properties) {
            final String value = stringOf(property, URL);
            if (value != null && !value.isEmpty() && !isUrl(value)) {
                throw new RuntimeException(SqlCompilationError.of("invalid URL: [" + value + "]"));
            }
        }
    }

    /**
     * Write one property, judged already (see {@link #judge}), onto a contact.
     *
     * @param contact  the contact
     * @param property the property as the statement writes it
     * @param catalog  the catalog, which holds the users a USERS list names
     */
    static void set(final Contact contact, final FrostlakeParser.ContactPropertyContext property,
                    final Catalog catalog) {
        final String key = keyOf(property);
        if (USERS.equals(key)) {
            final List<String> users = new ArrayList<>();
            if (property.stringLiteralList() != null) {
                for (final TerminalNode name : property.stringLiteralList().STRING_LITERAL()) {
                    final String held = catalog.getUser(SqlStringLiterals.decode(name.getText())).getName();
                    if (!users.contains(held)) {
                        users.add(held);
                    }
                }
            }
            contact.setUsers(users);
            return;
        }
        final String value = ParseTreeText.stringValue(property.stringText());
        if (COMMENT.equals(key)) {
            contact.setComment(value);
        } else if (URL.equals(key)) {
            contact.setUrl(value);
        } else {
            contact.setEmailDistributionList(value);
        }
    }

    /**
     * Clear one property of a contact; a property it does not hold is cleared all the same.
     *
     * @param contact the contact
     * @param name    the property's name as written
     */
    static void unset(final Contact contact, final String name) {
        final String key = name.toUpperCase(Locale.ROOT);
        if (COMMENT.equals(key)) {
            contact.setComment(null);
        } else if (URL.equals(key)) {
            contact.setUrl(null);
        } else if (ADDRESS.equals(key)) {
            contact.setEmailDistributionList(null);
        } else if (USERS.equals(key)) {
            contact.setUsers(null);
        } else {
            throw invalid(key);
        }
    }

    /**
     * Refuse a contact reached by more than one of its URL, its address and its users.
     *
     * @param contact the contact as the statement leaves it
     */
    static void requireOneWay(final Contact contact) {
        int ways = contact.getUsers().isEmpty() ? 0 : 1;
        if (contact.getUrl() != null && !contact.getUrl().isEmpty()) {
            ways++;
        }
        if (contact.getEmailDistributionList() != null && !contact.getEmailDistributionList().isEmpty()) {
            ways++;
        }
        if (ways > 1) {
            throw new RuntimeException("Email, url or users/email_list, only one could be set.");
        }
    }

    /** Refuses a list written for the property {@code key}, which takes a string. */
    private static void requireString(final List<FrostlakeParser.ContactPropertyContext> properties,
                                      final String key) {
        for (final FrostlakeParser.ContactPropertyContext property : properties) {
            if (key.equals(keyOf(property)) && property.stringText() == null) {
                throw new RuntimeException("Invalid value specified for property '" + key + "'");
            }
        }
    }

    /** The string a property writes when it is the property {@code key}, or null. */
    private static String stringOf(final FrostlakeParser.ContactPropertyContext property, final String key) {
        if (!key.equals(keyOf(property)) || property.stringText() == null) {
            return null;
        }
        return ParseTreeText.stringValue(property.stringText());
    }

    private static String keyOf(final FrostlakeParser.ContactPropertyContext property) {
        return property.optionKey().getText().toUpperCase(Locale.ROOT);
    }

    private static boolean isString(final String key) {
        return COMMENT.equals(key) || URL.equals(key) || ADDRESS.equals(key);
    }

    private static boolean isUrl(final String value) {
        try {
            return new URI(value).getScheme() != null;
        } catch (final URISyntaxException notOne) {
            return false;
        }
    }

    private static RuntimeException invalid(final String key) {
        return new RuntimeException(SqlCompilationError.of("invalid property '" + key + "' for 'CONTACT'"));
    }

    private static RuntimeException invalidType(final String written, final String key) {
        return new RuntimeException(SqlCompilationError.of("invalid type of property '" + written + "' for '" + key
            + "'"));
    }
}
