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

package dev.frostlake.functions.table;

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.TableFunction;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Contact;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The SNOWFLAKE.CORE.GET_CONTACTS(object_name, object_domain) table function: the contacts attached to one
 * object, a row per purpose in the purposes' order, each with the contact's e-mail and URL and the domain the
 * contact is attached at. A table and a task hold contacts here; an object of another domain answers no rows.
 */
public class GetContactsFunction extends TableFunction {

    private static final String[] COLUMN_NAMES = {"PURPOSE", "EMAIL", "URL", "USER", "LEVEL"};

    private final Catalog catalog;

    public GetContactsFunction(final Catalog catalog) {
        super("GET_CONTACTS");
        this.catalog = catalog;
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Validated in execute, where both positional arguments are in hand.
    }

    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        throw new RuntimeException("GET_CONTACTS requires an object name and an object domain");
    }

    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        TagReferencesFunction.requireArguments("GET_CONTACTS", positionalArgs);
        final List<ResultSetColumn> columns = new ArrayList<ResultSetColumn>();
        for (final String name : COLUMN_NAMES) {
            columns.add(new ResultSetColumn(name, StringType.VARCHAR));
        }
        final String domain = positionalArgs.get(1).toString().trim().toUpperCase(Locale.ROOT);
        final List<Row> rows = new ArrayList<Row>();
        for (final Map.Entry<String, String> attached : contactsOf(positionalArgs.get(0).toString(), domain)
                .entrySet()) {
            final Contact contact = contact(attached.getValue());
            if (contact != null) {
                rows.add(new Row(Arrays.asList((Object) attached.getKey(), contact.getEmailDistributionList(),
                    contact.getUrl(), null, domain)));
            }
        }
        return new ResultSet(columns, rows);
    }

    /** The contacts an object holds, by purpose in order; none for a domain that holds none here. */
    private Map<String, String> contactsOf(final String objectName, final String domain) {
        final String[] parts = SqlIdentifiers.canonicalTextParts(objectName);
        final Schema owner = catalog.requireOwningSchema(QualifiedName.of(parts));
        final String name = parts[parts.length - 1];
        if ("TABLE".equals(domain)) {
            return new TreeMap<String, String>(owner.getTable(name).getContacts());
        }
        if ("TASK".equals(domain)) {
            return owner.getTask(name).getContacts();
        }
        return new TreeMap<String, String>();
    }

    private Contact contact(final String qualifiedName) {
        try {
            return catalog.findContact(qualifiedName);
        } catch (final RuntimeException gone) {
            return null;
        }
    }
}
