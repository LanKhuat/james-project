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

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.jmap.api.change.MailboxChange;
import org.apache.james.jmap.api.change.MailboxChange.Limit;
import org.apache.james.jmap.api.change.MailboxChange.State;
import org.apache.james.jmap.api.change.MailboxChangeRepository;
import org.apache.james.jmap.api.exception.ChangeNotFoundException;
import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.memory.vacation.MemoryMailboxChangeRepository.MailboxChanges.MailboxChangesBuilder.MailboxChangeCollector;
import org.apache.james.mailbox.model.MailboxId;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryMailboxChangeRepository implements MailboxChangeRepository {

    public static class MailboxChanges {

        public static class MailboxChangesBuilder {

            public static class MailboxChangeCollector implements Collector<MailboxChange, MailboxChanges.MailboxChangesBuilder, MailboxChanges> {
                private final Limit limit;

                public MailboxChangeCollector(Limit limit) {
                    this.limit = limit;
                }

                @Override
                public Supplier<MailboxChanges.MailboxChangesBuilder> supplier() {
                    return () -> new MailboxChanges.MailboxChangesBuilder(limit);
                }

                public BiConsumer<MailboxChanges.MailboxChangesBuilder, MailboxChange> accumulator() {
                    return MailboxChanges.MailboxChangesBuilder::add;
                }

                @Override
                public BinaryOperator<MailboxChanges.MailboxChangesBuilder> combiner() {
                    throw new NotImplementedException("Not supported");
                }

                @Override
                public Function<MailboxChanges.MailboxChangesBuilder, MailboxChanges> finisher() {
                    return MailboxChanges.MailboxChangesBuilder::build;
                }

                @Override
                public Set<Characteristics> characteristics() {
                    return Sets.immutableEnumSet(Characteristics.UNORDERED);
                }
            }

            private State state;
            private boolean hasMoreChanges;
            private boolean canAddMoreItem;
            private Limit limit;
            private Set<MailboxId> created;
            private Set<MailboxId> updated;
            private Set<MailboxId> destroyed;

            public MailboxChangesBuilder(Limit limit) {
                this.limit = limit;
                this.hasMoreChanges = false;
                this.canAddMoreItem = true;
                this.created = new HashSet<>();
                this.updated = new HashSet<>();
                this.destroyed = new HashSet<>();
            }

            public MailboxChangesBuilder add(MailboxChange change) {
                if (!canAddMoreItem) {
                    return this;
                }

                Set<MailboxId> createdTemp = new HashSet<>(created);
                Set<MailboxId> updatedTemp = new HashSet<>(updated);
                Set<MailboxId> destroyedTemp = new HashSet<>(destroyed);
                createdTemp.addAll(change.getCreated());
                updatedTemp.addAll(change.getUpdated());
                destroyedTemp.addAll(change.getDestroyed());

                if (createdTemp.size() + updatedTemp.size() + destroyedTemp.size() > limit.getValue()) {
                    hasMoreChanges = true;
                    canAddMoreItem = false;
                    return this;
                }

                state = change.getState();
                this.created.addAll(change.getCreated());
                this.updated.addAll(change.getUpdated());
                this.destroyed.addAll(change.getDestroyed());

                return this;
            }

            public MailboxChanges build() {
                return new MailboxChanges(state, hasMoreChanges, created, updated, destroyed);
            }
        }

        private State newState;
        private final boolean hasMoreChanges;
        private final Set<MailboxId> created;
        private final Set<MailboxId> updated;
        private final Set<MailboxId> destroyed;

        private MailboxChanges(State newState, boolean hasMoreChanges, Set<MailboxId> created, Set<MailboxId> updated, Set<MailboxId> destroyed) {
            this.newState = newState;
            this.hasMoreChanges = hasMoreChanges;
            this.created = created;
            this.updated = updated;
            this.destroyed = destroyed;
        }

        public MailboxChangesBuilder build(Limit limit) {
            return new MailboxChangesBuilder(limit);
        }

        public State getNewState() {
            return newState;
        }

        public boolean hasMoreChanges() {
            return hasMoreChanges;
        }

        public Set<MailboxId> getCreated() {
            return created;
        }

        public Set<MailboxId> getUpdated() {
            return updated;
        }

        public Set<MailboxId> getDestroyed() {
            return destroyed;
        }

        public List<MailboxId> getAllChanges() {
            return ImmutableList.<MailboxId>builder()
                .addAll(created)
                .addAll(updated)
                .addAll(destroyed)
                .build();
        }
    }

    public static final Limit DEFAULT_NUMBER_OF_CHANGES = Limit.of(5);
    private final Multimap<AccountId, MailboxChange> mailboxChangeMap;

    public MemoryMailboxChangeRepository() {
        this.mailboxChangeMap = ArrayListMultimap.create();
    }

    @Override
    public Mono<Void> save(MailboxChange change) {
        Preconditions.checkNotNull(change.getAccountId());
        Preconditions.checkNotNull(change.getState());

        return Mono.just(mailboxChangeMap.put(change.getAccountId(), change)).then();
    }

    @Override
    public Mono<MailboxChanges> getSinceState(AccountId accountId, State state, Optional<Limit> maxChanges) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(state);
        maxChanges.ifPresent(limit -> Preconditions.checkArgument(limit.getValue() > 0, "maxChanges must be a positive integer"));

        return findByState(accountId, state)
            .flatMapMany(currentState -> Flux.fromIterable(mailboxChangeMap.get(accountId))
                .filter(change -> change.getDate().isAfter(currentState.getDate()))
                .sort(Comparator.comparing(MailboxChange::getDate)))
            .collect(new MailboxChangeCollector(maxChanges.orElse(DEFAULT_NUMBER_OF_CHANGES)));
    }

    private Mono<MailboxChange> findByState(AccountId accountId, State state) {
        return Flux.fromIterable(mailboxChangeMap.get(accountId))
            .filter(change -> change.getState().equals(state))
            .switchIfEmpty(Mono.error(new ChangeNotFoundException(state, String.format("State '%s' could not be found", state.getValue()))))
            .single();
    }
}
