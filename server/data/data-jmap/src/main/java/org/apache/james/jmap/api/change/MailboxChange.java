/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.jmap.api.change;

import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.apache.james.jmap.api.vacation.AccountId;

public class MailboxChange {

    private final AccountId accountId;
    private final Long state; //TODO: sort with UUID?
    private final Date date;
    private final Optional<UUID> created;
    private final Optional<UUID> updated;
    private final Optional<UUID> destroyed;

    private MailboxChange(AccountId accountId, Long state, Optional<UUID> created, Optional<UUID> updated, Optional<UUID> destroyed) {
        this.accountId = accountId;
        this.state = state;
        this.date = new Date();
        this.created = created;
        this.updated = updated;
        this.destroyed = destroyed;
    }

    public static MailboxChange createdChange(AccountId accountId, Long state, UUID created) {
        return new MailboxChange(accountId, state, Optional.of(created), Optional.empty(), Optional.empty());
    }

    public static MailboxChange updatedChange(AccountId accountId, Long state, UUID updated) {
        return new MailboxChange(accountId, state, Optional.empty(), Optional.of(updated), Optional.empty());
    }

    public static MailboxChange destroyedChange(AccountId accountId, Long state, UUID destroyed) {
        return new MailboxChange(accountId, state, Optional.empty(), Optional.empty(), Optional.of(destroyed));
    }

    public AccountId getAccountId() {
        return accountId;
    }

    public Long getState() {
        return state;
    }

    public Date getDate() {
        return date;
    }

    public Optional<UUID> getCreated() {
        return created;
    }

    public Optional<UUID> getUpdated() {
        return updated;
    }

    public Optional<UUID> getDestroyed() {
        return destroyed;
    }
}
