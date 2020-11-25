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

package org.apache.james.jmap.memory.vacation;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.james.jmap.api.change.MailboxChange;
import org.apache.james.jmap.api.change.MailboxChangeRepository;
import org.apache.james.jmap.api.vacation.AccountId;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class MemoryMailboxChangeRepository implements MailboxChangeRepository {

    public static final int DEFAULT_NUMBER_OF_CHANGES = 10;
    private final Multimap<AccountId, MailboxChange> mailboxChangeMap;

    public MemoryMailboxChangeRepository() {
        this.mailboxChangeMap = ArrayListMultimap.create();
    }

    @Override
    public void save(MailboxChange change) {
        Preconditions.checkNotNull(change.getAccountId());
        Preconditions.checkNotNull(change.getState());

        mailboxChangeMap.put(change.getAccountId(), change);
    }

    //TODO: return newest state in the list of changes
    @Override
    public List<MailboxChange> getFromState(AccountId accountId, Long state, Optional<Integer> maxChanges) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(state);
        maxChanges.ifPresent(integer -> Preconditions.checkArgument(integer > 0, "maxChanges must be a positive integer"));

        return mailboxChangeMap.get(accountId)
            .stream()
            .filter(change -> change.getState() > state)
            .sorted((c1, c2) -> c2.getState().compareTo(c1.getState()))
            .limit(maxChanges.orElse(DEFAULT_NUMBER_OF_CHANGES))
            .collect(Collectors.toList());
    }
}
