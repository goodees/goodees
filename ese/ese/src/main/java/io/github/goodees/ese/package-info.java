/**
 * Small event sourcing library. Provides base classes for implementing event-sourcing based entities, runtime for
 * coordinating execution of requests against the entities, and utility api to work with CompletableFuture.
 *
 * <h2>What is an entity?</h2>
 * <p>An entity is a object that accepts requests and returns responses to them. There are multiple instances of entities,
 * identified by different values of their identity, and the client usually want to address single entity with the request.
 * An example would be an Order, or a bank account, or a  bank transaction. In Domain-Driven-Design terms it represents
 * an aggregate root.
 * <p>The entity manages change to its state via producing events, and by communication with other parts of system - other
 * entities or systems (e. g. speaking to a service over http). This makes it different to JPA entities, that
 * hold their state directly and usually only represent data, do not invoke other business objects.
 * <p>Entity is never part of distributed transaction. The processing in non-transactional. If the entity communicates
 * with third party and this communication fails, entity must compensate for the failure by putting itself in the state
 * that is consistent with the fact that its dependent system failed. The only thing that is guaranteed to be atomic
 * is storing of generated events.
 * <p>An entity has an unique id, represented with a String.
 * <p>Entities extend subclasses {@link io.github.goodees.ese.EventSourcedEntity}, based on their execution style.</p>
 *
 * <h2>Runtimes and invocation of an entity</h2>
 * <p>For every entity class a {@linkplain io.github.goodees.ese.EventSourcingRuntimeBase runtime} will be implemented
 * Runtime cares for creating and destroying entity instances, injecting them with their dependencies to other business
 * objects, such as event storage.
 * <p>Runtime also needs to guarantee that an single entity processes only single request at time (within single application on single JVM).
 * For this, the matching dispatcher in implemented in {@linkplain io.github.goodees.ese.dispatch dispatch package}.
 * <p>Runtimes extends from a {@linkplain io.github.goodees.ese.EventSourcingRuntimeBase base class}, that doesn't prescribe
 * how this isolation should be achieved. However, the actual implementations extends from {@link io.github.goodees.ese.DispatchingEventSourcingRuntime}
 * that uses the aforementioned dispatcher, and in addition to simple invocation also support timeouts and delayed
 * invocations.
 * <p>Since the thread safety of entities is implemented via queues rather than locks in the dispatcher,
 * the client is not blocked, rather requests always return a CompletionStage of a result, even if underlying execution
 * of request within the entity is synchronous.
 * <p>Two approaches for defining entities and runtimes exist, based
 * on which kind of processing is suitable for the entity:
 * <ul>
 * <li>{@link io.github.goodees.ese.AsyncEntity} with its corresponding runtime {@link io.github.goodees.ese.AsyncEventSourcingRuntime}
 * process requests asynchronously returning a {@link java.util.concurrent.CompletableFuture}, that completes when
 * execution finishes. The execution method to implement is {@link io.github.goodees.ese.AsyncEntity#execute(io.github.goodees.ese.Request)}.
 * <li>{@link io.github.goodees.ese.SyncEntity} handled by runtime {@link io.github.goodees.ese.SyncEventSourcingRuntime}
 * handles request synchronously or throw an exception.
 * </ul>
 *
 * <h2>Request processing flow</h2>
 * The flow surrounding the invocation of the entity is described in documentation of
 * {@link io.github.goodees.ese.EventSourcingRuntimeBase#execute(java.lang.String, io.github.goodees.ese.Request)}.
 *
 * The flow of invocation within entity is described in respective execute methods of either {@linkplain io.github.goodees.ese.AsyncEntity#execute(io.github.goodees.ese.Request) AsyncEntity}
 * or {@linkplain io.github.goodees.ese.SyncEntity#execute(io.github.goodees.ese.Request) SyncEntity}.
 * <h3>What is an side effect?</h3>
 * <p>The documentation of execute method refer to term side effect. We understand a side effect as a
 * <strong>reaction to a request, that doesn't change state of entity, rather may change state of some external
 * system</strong> (i.e. making http call, invoking other entity, sending e-mail).</p>
 *
 * <p>We recognize three types of side effects:</p>
 * <ol>
 *     <li>Giving response to caller. This side effect is implemented by returning from execute method, e. g.
 *     {@link io.github.goodees.ese.AsyncEntity#execute(io.github.goodees.ese.Request)} or
 *     {@link io.github.goodees.ese.SyncEntity#execute(io.github.goodees.ese.Request)}</li>
 *     <li>Changing state of external system. These kind of side effects may fail. Since entity is non-transactional, it
 *         needs to handle the failure explicitly, and compensate for the failure by emiting more events, or corrective requests for self.
 *         Usually there are implemented within the flow of {@code execute} methods</li>
 *     <li>Fire and forget side effects, where the failure of the side effect does not affact the state of the entity. Such side effects
 *     are usually perfomed in {@link io.github.goodees.ese.EventSourcedEntity#performPostInvocationActions(java.util.List, java.lang.Throwable)}</li>
 * </ol>
 *
 * @see io.github.goodees.ese.EventSourcingRuntimeBase#execute(java.lang.String, io.github.goodees.ese.Request)
 * @see io.github.goodees.ese.EventSourcedEntity
 * @see io.github.goodees.ese.store.EventStore
 * @see io.github.goodees.ese.Event
 * @see io.github.goodees.ese.AsyncEntity
 * @see io.github.goodees.ese.SyncEntity
 */
package io.github.goodees.ese;

/*-
 * #%L
 * ese
 * %%
 * Copyright (C) 2017 Patrik Duditš
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
