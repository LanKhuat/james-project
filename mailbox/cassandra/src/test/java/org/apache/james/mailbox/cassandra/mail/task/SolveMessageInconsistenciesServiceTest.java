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

package org.apache.james.mailbox.cassandra.mail.task;

import static org.apache.james.backends.cassandra.Scenario.Builder.fail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdDAO;
import org.apache.james.mailbox.cassandra.mail.CassandraMessageIdToImapUidDAO;
import org.apache.james.mailbox.cassandra.mail.task.SolveMessageInconsistenciesService.Context;
import org.apache.james.mailbox.cassandra.modules.CassandraMessageModule;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.ComposedMessageIdWithMetaData;
import org.apache.james.task.Task;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

public class SolveMessageInconsistenciesServiceTest {

    private static final CassandraId MAILBOX_ID = CassandraId.timeBased();
    private static final CassandraMessageId MESSAGE_ID_1 = new CassandraMessageId.Factory().fromString("d2bee791-7e63-11ea-883c-95b84008f979");
    private static final CassandraMessageId MESSAGE_ID_2 = new CassandraMessageId.Factory().fromString("eeeeeeee-7e63-11ea-883c-95b84008f979");
    private static final MessageUid MESSAGE_UID_1 = MessageUid.of(1L);
    private static final MessageUid MESSAGE_UID_2 = MessageUid.of(2L);
    private static final ModSeq MOD_SEQ_1 = ModSeq.of(1L);
    private static final ModSeq MOD_SEQ_2 = ModSeq.of(2L);

    private static final ComposedMessageIdWithMetaData MESSAGE_1 = ComposedMessageIdWithMetaData.builder()
        .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_1, MESSAGE_UID_1))
        .modSeq(MOD_SEQ_1)
        .flags(new Flags())
        .build();

    private static final ComposedMessageIdWithMetaData MESSAGE_1_WITH_SEEN_FLAG = ComposedMessageIdWithMetaData.builder()
        .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_1, MESSAGE_UID_1))
        .modSeq(MOD_SEQ_1)
        .flags(new Flags(Flags.Flag.SEEN))
        .build();

    private static final ComposedMessageIdWithMetaData MESSAGE_1_WITH_MOD_SEQ_2 = ComposedMessageIdWithMetaData.builder()
        .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_1, MESSAGE_UID_1))
        .modSeq(MOD_SEQ_2)
        .flags(new Flags(Flags.Flag.SEEN))
        .build();

    private static final ComposedMessageIdWithMetaData MESSAGE_2 = ComposedMessageIdWithMetaData.builder()
        .composedMessageId(new ComposedMessageId(MAILBOX_ID, MESSAGE_ID_2, MESSAGE_UID_2))
        .modSeq(MOD_SEQ_2)
        .flags(new Flags())
        .build();

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(
            CassandraSchemaVersionModule.MODULE,
            CassandraMessageModule.MODULE));

    CassandraMessageIdToImapUidDAO imapUidDAO;
    CassandraMessageIdDAO messageIdDAO;
    SolveMessageInconsistenciesService testee;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        imapUidDAO = new CassandraMessageIdToImapUidDAO(cassandra.getConf(), new CassandraMessageId.Factory());
        messageIdDAO = new CassandraMessageIdDAO(cassandra.getConf(), new CassandraMessageId.Factory());
        testee = new SolveMessageInconsistenciesService(imapUidDAO, messageIdDAO);
    }

    @Test
    void fixMessageInconsistenciesShouldReturnCompletedWhenNoData() {
        assertThat(testee.fixMessageInconsistencies(new Context()).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void fixMessageInconsistenciesShouldReturnCompletedWhenConsistentData() {
        imapUidDAO.insert(MESSAGE_1).block();
        messageIdDAO.insert(MESSAGE_1).block();

        assertThat(testee.fixMessageInconsistencies(new Context()).block())
            .isEqualTo(Task.Result.COMPLETED);
    }

    @Test
    void fixMailboxInconsistenciesShouldNotAlterStateWhenEmpty() {
        testee.fixMessageInconsistencies(new Context()).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block()).isEmpty();
            softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block()).isEmpty();
        });
    }

    @Test
    void fixMailboxInconsistenciesShouldNotAlterStateWhenConsistent() {
        imapUidDAO.insert(MESSAGE_1).block();
        messageIdDAO.insert(MESSAGE_1).block();

        testee.fixMessageInconsistencies(new Context()).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                .containsExactlyInAnyOrder(MESSAGE_1);
            softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                .containsExactlyInAnyOrder(MESSAGE_1);
        });
    }

    @Nested
    class ImapUidScanningTest {

        @Test
        void fixMessageInconsistenciesShouldReturnCompletedWhenInconsistentData() {
            imapUidDAO.insert(MESSAGE_1).block();

            assertThat(testee.fixMessageInconsistencies(new Context()).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void fixMessageInconsistenciesShouldResolveInconsistentData() {
            imapUidDAO.insert(MESSAGE_1).block();

            testee.fixMessageInconsistencies(new Context()).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieve(MESSAGE_ID_1, Optional.of(MAILBOX_ID)).collectList().block())
                    .containsExactly(MESSAGE_1);
                softly.assertThat(messageIdDAO.retrieve(MAILBOX_ID, MESSAGE_UID_1).block().get())
                    .isEqualTo(MESSAGE_1);
            });
        }

        @Test
        void fixMessageInconsistenciesShouldReturnCompletedWhenInconsistentFlags() {
            imapUidDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_1_WITH_SEEN_FLAG).block();

            assertThat(testee.fixMessageInconsistencies(new Context()).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void fixMessageInconsistenciesShouldResolveInconsistentFlags() {
            imapUidDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_1_WITH_SEEN_FLAG).block();

            testee.fixMessageInconsistencies(new Context()).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieve(MESSAGE_ID_1, Optional.of(MAILBOX_ID)).collectList().block())
                    .containsExactly(MESSAGE_1);
                softly.assertThat(messageIdDAO.retrieve(MAILBOX_ID, MESSAGE_UID_1).block().get())
                    .isEqualTo(MESSAGE_1);
            });
        }

        @Test
        void fixMessageInconsistenciesShouldReturnCompletedWhenInconsistentModSeq() {
            imapUidDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_1_WITH_MOD_SEQ_2).block();

            assertThat(testee.fixMessageInconsistencies(new Context()).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void fixMessageInconsistenciesShouldResolveInconsistentModSeq() {
            imapUidDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_1_WITH_MOD_SEQ_2).block();

            testee.fixMessageInconsistencies(new Context()).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieve(MESSAGE_ID_1, Optional.of(MAILBOX_ID)).collectList().block())
                    .containsExactly(MESSAGE_1);
                softly.assertThat(messageIdDAO.retrieve(MAILBOX_ID, MESSAGE_UID_1).block().get())
                    .isEqualTo(MESSAGE_1);
            });
        }

        @Nested
        class FailureTesting {
            @Test
            void fixMessageInconsistenciesShouldReturnPartialWhenError(CassandraCluster cassandra) {
                imapUidDAO.insert(MESSAGE_1).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .forever()
                        .whenQueryStartsWith("INSERT INTO messageIdTable (mailboxId,uid,modSeq,messageId,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags) VALUES (:mailboxId,:uid,:modSeq,:messageId,:flagAnswered,:flagDeleted,:flagDraft,:flagFlagged,:flagRecent,:flagSeen,:flagUser,:userFlags)"));

                assertThat(testee.fixMessageInconsistencies(new Context()).block())
                    .isEqualTo(Task.Result.PARTIAL);
            }

            @Test
            void fixMessageInconsistenciesShouldReturnPartialWhenPartialError(CassandraCluster cassandra) {
                imapUidDAO.insert(MESSAGE_1).block();
                imapUidDAO.insert(MESSAGE_2).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("INSERT INTO messageIdTable (mailboxId,uid,modSeq,messageId,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags) VALUES (:mailboxId,:uid,:modSeq,:messageId,:flagAnswered,:flagDeleted,:flagDraft,:flagFlagged,:flagRecent,:flagSeen,:flagUser,:userFlags)"));

                assertThat(testee.fixMessageInconsistencies(new Context()).block())
                    .isEqualTo(Task.Result.PARTIAL);
            }

            @Test
            void fixMessageInconsistenciesShouldResolveSuccessPartially(CassandraCluster cassandra) {
                imapUidDAO.insert(MESSAGE_1).block();
                imapUidDAO.insert(MESSAGE_2).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("INSERT INTO messageIdTable (mailboxId,uid,modSeq,messageId,flagAnswered,flagDeleted,flagDraft,flagFlagged,flagRecent,flagSeen,flagUser,userFlags) VALUES (:mailboxId,:uid,:modSeq,d2bee791-7e63-11ea-883c-95b84008f979,:flagAnswered,:flagDeleted,:flagDraft,:flagFlagged,:flagRecent,:flagSeen,:flagUser,:userFlags)"));

                testee.fixMessageInconsistencies(new Context()).block();

                SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(imapUidDAO.retrieve(MESSAGE_ID_2, Optional.of(MAILBOX_ID)).collectList().block())
                        .containsExactly(MESSAGE_2);
                    softly.assertThat(messageIdDAO.retrieve(MAILBOX_ID, MESSAGE_UID_2).block().get())
                        .isEqualTo(MESSAGE_2);
                });
            }
        }
    }

    @Nested
    class MessageIdScanningTest {

        @Test
        void fixMessageInconsistenciesShouldReturnCompletedWhenInconsistentData() {
            messageIdDAO.insert(MESSAGE_1).block();

            assertThat(testee.fixMessageInconsistencies(new Context()).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void fixMessageInconsistenciesShouldResolveInconsistentData() {
            messageIdDAO.insert(MESSAGE_1).block();

            testee.fixMessageInconsistencies(new Context()).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .isEmpty();
                softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                    .isEmpty();
            });
        }

        @Test
        void fixMessageInconsistenciesShouldReturnCompletedWhenPartialInconsistentData() {
            messageIdDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_2).block();

            imapUidDAO.insert(MESSAGE_1).block();

            assertThat(testee.fixMessageInconsistencies(new Context()).block())
                .isEqualTo(Task.Result.COMPLETED);
        }

        @Test
        void fixMessageInconsistenciesShouldResolvePartialInconsistentData() {
            messageIdDAO.insert(MESSAGE_1).block();
            messageIdDAO.insert(MESSAGE_2).block();

            imapUidDAO.insert(MESSAGE_1).block();

            testee.fixMessageInconsistencies(new Context()).block();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_1);
                softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                    .containsExactly(MESSAGE_1);
            });
        }

        @Nested
        class FailureTesting {
            @Test
            void fixMessageInconsistenciesShouldReturnPartialWhenError(CassandraCluster cassandra) {
                messageIdDAO.insert(MESSAGE_1).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .forever()
                        .whenQueryStartsWith("DELETE FROM messageIdTable WHERE mailboxId=:mailboxId AND uid=:uid"));

                assertThat(testee.fixMessageInconsistencies(new Context()).block())
                    .isEqualTo(Task.Result.PARTIAL);
            }

            @Test
            void fixMessageInconsistenciesShouldReturnPartialWhenPartialError(CassandraCluster cassandra) {
                messageIdDAO.insert(MESSAGE_1).block();
                messageIdDAO.insert(MESSAGE_2).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("DELETE FROM messageIdTable WHERE mailboxId=:mailboxId AND uid=:uid"));

                assertThat(testee.fixMessageInconsistencies(new Context()).block())
                    .isEqualTo(Task.Result.PARTIAL);
            }

            @Test
            void fixMessageInconsistenciesShouldResolveSuccessPartially(CassandraCluster cassandra) {
                messageIdDAO.insert(MESSAGE_1).block();
                messageIdDAO.insert(MESSAGE_2).block();

                cassandra.getConf()
                    .registerScenario(fail()
                        .times(1)
                        .whenQueryStartsWith("DELETE FROM messageIdTable WHERE mailboxId=:mailboxId AND uid=:uid;"));

                testee.fixMessageInconsistencies(new Context()).block();

                SoftAssertions.assertSoftly(softly -> {
                    softly.assertThat(imapUidDAO.retrieveAllMessages().collectList().block())
                        .isEmpty();
                    softly.assertThat(messageIdDAO.retrieveAllMessages().collectList().block())
                        .containsExactly(MESSAGE_1);
                });
            }
        }
    }

    @Test
    void fixMailboxInconsistenciesShouldNotUpdateContextWhenNoData() {
        Context context = new Context();

        testee.fixMessageInconsistencies(context).block();

        assertThat(context.snapshot()).isEqualToComparingFieldByFieldRecursively(new Context().snapshot());
    }

    @Test
    void fixMessageInconsistenciesShouldUpdateContextWhenConsistentData() {
        Context context = new Context();

        imapUidDAO.insert(MESSAGE_1).block();
        messageIdDAO.insert(MESSAGE_1).block();

        testee.fixMessageInconsistencies(context).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.builder()
                .processedImapUidEntries(1)
                .processedMessageIdEntries(1)
                .build()
                .snapshot());
    }

    @Test
    void fixMessageInconsistenciesShouldUpdateContextWhenOrphanImapUidMessage() {
        Context context = new Context();

        imapUidDAO.insert(MESSAGE_1).block();

        testee.fixMessageInconsistencies(context).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.builder()
                .processedImapUidEntries(1)
                .addedMessageIdEntries(1)
                .addFixedInconsistencies(MESSAGE_1.getComposedMessageId())
                .build()
                .snapshot());
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenInconsistentModSeq() {
        Context context = new Context();

        imapUidDAO.insert(MESSAGE_1).block();
        messageIdDAO.insert(MESSAGE_1_WITH_MOD_SEQ_2).block();

        testee.fixMessageInconsistencies(context).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.builder()
                .processedImapUidEntries(1)
                .processedMessageIdEntries(1)
                .updatedMessageIdEntries(1)
                .addFixedInconsistencies(MESSAGE_1.getComposedMessageId())
                .build()
                .snapshot());
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenInconsistentFlags() {
        Context context = new Context();

        imapUidDAO.insert(MESSAGE_1).block();
        messageIdDAO.insert(MESSAGE_1_WITH_SEEN_FLAG).block();

        testee.fixMessageInconsistencies(context).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.builder()
                .processedImapUidEntries(1)
                .processedMessageIdEntries(1)
                .updatedMessageIdEntries(1)
                .addFixedInconsistencies(MESSAGE_1.getComposedMessageId())
                .build()
                .snapshot());
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenOrphanMessageIdMessage() {
        Context context = new Context();

        messageIdDAO.insert(MESSAGE_1).block();

        testee.fixMessageInconsistencies(context).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.builder()
                .processedMessageIdEntries(1)
                .removedMessageIdEntries(1)
                .addFixedInconsistencies(MESSAGE_1.getComposedMessageId())
                .build()
                .snapshot());
    }

    @Test
    void fixMailboxInconsistenciesShouldUpdateContextWhenError(CassandraCluster cassandra) {
        Context context = new Context();

        messageIdDAO.insert(MESSAGE_1).block();

        cassandra.getConf()
            .registerScenario(fail()
                .times(1)
                .whenQueryStartsWith("DELETE FROM messageIdTable WHERE mailboxId=:mailboxId AND uid=:uid;"));

        testee.fixMessageInconsistencies(context).block();

        assertThat(context.snapshot())
            .isEqualTo(Context.builder()
                .processedMessageIdEntries(1)
                .errors(MESSAGE_1.getComposedMessageId())
                .build()
                .snapshot());
    }
}
