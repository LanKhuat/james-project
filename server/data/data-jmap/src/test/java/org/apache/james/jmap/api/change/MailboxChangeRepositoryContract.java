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

import static org.apache.james.jmap.memory.vacation.MemoryMailboxChangeRepository.DEFAULT_NUMBER_OF_CHANGES;
import static org.apache.james.mailbox.fixture.MailboxFixture.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;

import org.apache.james.jmap.api.vacation.AccountId;
import org.junit.jupiter.api.Test;

public interface MailboxChangeRepositoryContract {

    AccountId ACCOUNT_ID = AccountId.fromUsername(BOB);

    MailboxChangeRepository mailboxChangeRepository();

    @Test
    default void saveChangeShouldSuccess() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange change = MailboxChange.createdChange(ACCOUNT_ID, 1L, UUID.randomUUID());

        assertThatCode(() -> repository.save(change)).doesNotThrowAnyException();
    }

    @Test
    default void saveChangeShouldFailWhenNoAccountId() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange change = MailboxChange.createdChange(null, 1L, UUID.randomUUID());

        assertThatThrownBy(() -> repository.save(change)).isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveChangeShouldFailWhenNoState() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange change = MailboxChange.createdChange(null, 1L, UUID.randomUUID());

        assertThatThrownBy(() -> repository.save(change)).isInstanceOf(NullPointerException.class);
    }

    @Test
    default void getChangesShouldSuccess() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange change = MailboxChange.createdChange(ACCOUNT_ID, 1L, UUID.randomUUID());
        repository.save(change);

        assertThat(repository.getFromState(ACCOUNT_ID, 0L, Optional.empty())).containsExactly(change);
    }

    @Test
    default void getChangesShouldReturnEmptyWhenNoNewerState() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange change = MailboxChange.createdChange(ACCOUNT_ID, 1L, UUID.randomUUID());
        repository.save(change);

        assertThat(repository.getFromState(ACCOUNT_ID, 2L, Optional.empty())).isEmpty();
    }

    @Test
    default void getChangesShouldPrioritizeNewerState() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange change1 = MailboxChange.createdChange(ACCOUNT_ID, 1L, UUID.randomUUID());
        MailboxChange change2 = MailboxChange.updatedChange(ACCOUNT_ID, 2L, UUID.randomUUID());
        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getFromState(ACCOUNT_ID, 0L, Optional.empty())).containsExactly(change2, change1);
    }

    @Test
    default void getChangesShouldLimitChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange change1 = MailboxChange.createdChange(ACCOUNT_ID, 1L, UUID.randomUUID());
        MailboxChange change2 = MailboxChange.updatedChange(ACCOUNT_ID, 2L, UUID.randomUUID());
        MailboxChange change3 = MailboxChange.destroyedChange(ACCOUNT_ID, 3L, UUID.randomUUID());
        repository.save(change1);
        repository.save(change2);
        repository.save(change3);

        assertThat(repository.getFromState(ACCOUNT_ID, 0L, Optional.of(2))).containsExactly(change3, change2);
    }

    @Test
    default void getChangesShouldLimitChangesWhenMaxChangesOmitted() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        LongStream.range(0, 20).forEach(value -> {
            MailboxChange change = MailboxChange.createdChange(ACCOUNT_ID, value, UUID.randomUUID());
            repository.save(change);
        });

        assertThat(repository.getFromState(ACCOUNT_ID, 0L, Optional.empty())).hasSize(DEFAULT_NUMBER_OF_CHANGES);
    }
}
