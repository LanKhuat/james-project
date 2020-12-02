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

package org.apache.james.jmap.change

import java.time.ZonedDateTime
import java.util.UUID

import javax.inject.Inject
import org.apache.james.jmap.api.change.MailboxChange.State
import org.apache.james.jmap.api.change.{MailboxChange, MailboxChangeRepository}
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.mailbox.events.MailboxListener.{Added, Expunged, MailboxACLUpdated, MailboxAdded, MailboxDeletion, MailboxRenamed, ReactiveGroupMailboxListener}
import org.apache.james.mailbox.events.{Event, Group}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

case class MailboxChangeListenerGroup() extends Group {}

case class MailboxChangeListener @Inject() (mailboxChangeRepository: MailboxChangeRepository) extends ReactiveGroupMailboxListener {

  override def reactiveEvent(event: Event): Publisher[Void] =
    event match {
      case mailboxAdded: MailboxAdded => mailboxChangeRepository.save(MailboxChange.of(AccountId.fromUsername(mailboxAdded.getUsername), State.of(UUID.randomUUID()), ZonedDateTime.now(), List(mailboxAdded.getMailboxId).asJava, List().asJava, List().asJava))
      case mailboxRenamed: MailboxRenamed => mailboxChangeRepository.save(MailboxChange.of(AccountId.fromUsername(mailboxRenamed.getUsername), State.of(UUID.randomUUID()), ZonedDateTime.now(), List().asJava, List(mailboxRenamed.getMailboxId).asJava, List().asJava))
      case mailboxACLUpdated: MailboxACLUpdated => mailboxChangeRepository.save(MailboxChange.of(AccountId.fromUsername(mailboxACLUpdated.getUsername), State.of(UUID.randomUUID()), ZonedDateTime.now(), List().asJava, List(mailboxACLUpdated.getMailboxId).asJava, List().asJava))
      case mailboxDeletion: MailboxDeletion => mailboxChangeRepository.save(MailboxChange.of(AccountId.fromUsername(mailboxDeletion.getUsername), State.of(UUID.randomUUID()), ZonedDateTime.now(), List().asJava, List().asJava, List(mailboxDeletion.getMailboxId).asJava))
      case messageAdded: Added => mailboxChangeRepository.save(MailboxChange.of(AccountId.fromUsername(messageAdded.getUsername), State.of(UUID.randomUUID()), ZonedDateTime.now(), List().asJava, List(messageAdded.getMailboxId).asJava, List().asJava))
      case messageDeleted: Expunged => mailboxChangeRepository.save(MailboxChange.of(AccountId.fromUsername(messageDeleted.getUsername), State.of(UUID.randomUUID()), ZonedDateTime.now(), List().asJava, List(messageDeleted.getMailboxId).asJava, List().asJava))
      case _ => SMono.empty
    }

  override def getDefaultGroup: Group = MailboxChangeListenerGroup()
}
