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
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A contact's properties as CREATE CONTACT and ALTER CONTACT … SET write them and ALTER CONTACT … UNSET clears them
 * (live-verified). COMMENT, URL and EMAIL_DISTRIBUTION_LIST take a string. The address is ONE address whatever its
 * name suggests — a comma-separated list, an address with no dotted domain, a one-letter top level and any address
 * with a space are {@code Invalid email address(es): [x].} — and the URL is a URI with a scheme, {@code mailto:}
 * included ({@code invalid URL: [x]}); the empty string is either one's and clears it. USERS takes a list of user
 * names, each a user that exists ({@code invalid value 'U' for property 'USER'}); the users a contact names are not
 * kept. A string given for USERS, or a list for another, is {@code invalid type of property}, any other name {@code
 * invalid property 'NAME' for 'CONTACT'}, and a contact reached by both its URL and its address {@code Email, url or
 * users/email_list, only one could be set.}
 */
final class ContactProperties {

    private static final Pattern EMAIL = Pattern.compile("[^\\s@]+@[^\\s@,]+\\.[A-Za-z]{2,}");

    private ContactProperties() {
    }

    /**
     * Write one property onto a contact, judged as it is read.
     *
     * @param contact  the contact
     * @param property the property as the statement writes it
     * @param catalog  the catalog, where the users a USERS list names are looked for
     */
    static void set(final Contact contact, final FrostlakeParser.ContactPropertyContext property,
                    final Catalog catalog) {
        final String key = property.optionKey().getText().toUpperCase(Locale.ROOT);
        if (!"USERS".equals(key) && !isString(key)) {
            throw invalid(key);
        }
        if ("USERS".equals(key)) {
            if (property.stringText() != null) {
                throw invalidType(property.stringText().getText(), key);
            }
            if (property.stringLiteralList() != null) {
                for (final TerminalNode name : property.stringLiteralList().STRING_LITERAL()) {
                    final String user = SqlStringLiterals.decode(name.getText());
                    if (!catalog.hasUser(user)) {
                        throw new RuntimeException(SqlCompilationError.of("invalid value '" + user
                            + "' for property 'USER'"));
                    }
                }
            }
            return;
        }
        if (property.stringText() == null) {
            final StringBuilder listed = new StringBuilder();
            if (property.stringLiteralList() != null) {
                for (final TerminalNode value : property.stringLiteralList().STRING_LITERAL()) {
                    listed.append(listed.length() > 0 ? ", " : "").append(SqlStringLiterals.decode(value.getText()));
                }
            }
            throw invalidType("[" + listed + "]", key);
        }
        set(contact, key, ParseTreeText.stringValue(property.stringText()));
    }

    /**
     * Write one string property onto a contact, judged as it is read.
     *
     * @param contact the contact
     * @param name    the property's name as written
     * @param value   its text
     */
    static void set(final Contact contact, final String name, final String value) {
        final String key = name.toUpperCase(Locale.ROOT);
        if ("COMMENT".equals(key)) {
            contact.setComment(value);
        } else if ("URL".equals(key)) {
            if (!value.isEmpty() && !isUrl(value)) {
                throw new RuntimeException(SqlCompilationError.of("invalid URL: [" + value + "]"));
            }
            contact.setUrl(value);
        } else if ("EMAIL_DISTRIBUTION_LIST".equals(key)) {
            if (!value.isEmpty() && !EMAIL.matcher(value).matches()) {
                throw new RuntimeException("Invalid email address(es): [" + value + "].");
            }
            contact.setEmailDistributionList(value);
        } else {
            throw invalid(key);
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
        if ("COMMENT".equals(key)) {
            contact.setComment(null);
        } else if ("URL".equals(key)) {
            contact.setUrl(null);
        } else if ("EMAIL_DISTRIBUTION_LIST".equals(key)) {
            contact.setEmailDistributionList(null);
        } else if (!"USERS".equals(key)) {
            throw invalid(key);
        }
    }

    /**
     * Refuse a contact reached both by its URL and by its address. Each property is judged as it is read, so an
     * unusable address is refused before this pairing is.
     *
     * @param contact the contact as the statement leaves it
     */
    static void requireOneWay(final Contact contact) {
        if (contact.getUrl() != null && !contact.getUrl().isEmpty() && contact.getEmailDistributionList() != null
                && !contact.getEmailDistributionList().isEmpty()) {
            throw new RuntimeException("Email, url or users/email_list, only one could be set.");
        }
    }

    private static boolean isString(final String key) {
        return "COMMENT".equals(key) || "URL".equals(key) || "EMAIL_DISTRIBUTION_LIST".equals(key);
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
