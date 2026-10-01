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

package dev.frostlake.metastore;

import dev.frostlake.metastore.model.Account;
import dev.frostlake.metastore.model.ManagedAccount;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The organization's other accounts and the reader accounts this account manages. The engine serves one account;
 * these are the records CREATE ACCOUNT and CREATE MANAGED ACCOUNT leave, listed by SHOW ACCOUNTS and SHOW MANAGED
 * ACCOUNTS. Names are matched as the catalog matches role names, case-insensitively.
 */
public final class AccountDirectory {

    private final Map<String, Account> accounts = new LinkedHashMap<>();
    private final Map<String, ManagedAccount> managedAccounts = new LinkedHashMap<>();
    private int locatorSequence;

    private static String key(final String name) {
        return name.toUpperCase(Locale.ROOT);
    }

    /** The account of that name, dropped or not, or null. */
    public synchronized Account account(final String name) {
        return accounts.get(key(name));
    }

    /** Every account created, dropped ones included, in creation order. */
    public synchronized List<Account> accounts() {
        return new ArrayList<>(accounts.values());
    }

    /** Records an account. */
    public synchronized void putAccount(final Account account) {
        accounts.put(key(account.getName()), account);
    }

    /** The managed account of that name, or null. */
    public synchronized ManagedAccount managedAccount(final String name) {
        return managedAccounts.get(key(name));
    }

    /** Every managed account, in creation order. */
    public synchronized List<ManagedAccount> managedAccounts() {
        return new ArrayList<>(managedAccounts.values());
    }

    /** Records a managed account. */
    public synchronized void putManagedAccount(final ManagedAccount account) {
        managedAccounts.put(key(account.getName()), account);
    }

    /** Removes a managed account, answering whether there was one of that name. */
    public synchronized boolean removeManagedAccount(final String name) {
        return managedAccounts.remove(key(name)) != null;
    }

    /** The last locator number handed out. */
    public synchronized int getLocatorSequence() {
        return locatorSequence;
    }

    /** Continues locator numbering after a restored directory's last one. */
    public synchronized void setLocatorSequence(final int locatorSequence) {
        this.locatorSequence = locatorSequence;
    }

    /** A new locator, unique within this engine: {@code FL} and six digits. */
    public synchronized String nextLocator() {
        locatorSequence++;
        return String.format(Locale.ROOT, "FL%06d", locatorSequence);
    }
}
