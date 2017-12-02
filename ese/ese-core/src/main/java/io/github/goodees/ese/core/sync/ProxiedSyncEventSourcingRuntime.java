/*
 * Copyright 2017 Patrik Duditš.
 *
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
 */
package io.github.goodees.ese.core.sync;

import io.github.goodees.ese.core.EntityInvocationHandler;
import io.github.goodees.ese.core.Request;
import io.github.goodees.ese.core.dispatch.DispatchingEventSourcingRuntime;
import io.github.goodees.ese.core.matching.RequestHandler;
import io.github.goodees.ese.core.store.EventLog;
import io.github.goodees.ese.core.store.SnapshotStore;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author patrik
 */
public abstract class ProxiedSyncEventSourcingRuntime<E extends ProxiedSyncEntity<R>,R> {
    protected static final long RETRY_NOW = DispatchingEventSourcingRuntime.RETRY_NOW;
    protected static final long RETRY_NEVER = DispatchingEventSourcingRuntime.RETRY_NEVER;
    private Logger logger = LoggerFactory.getLogger(getClass());

    private final Class<?>[] requestHandlerClass;
    private final DispatchingDelegate delegate = new DispatchingDelegate();
    
    protected ProxiedSyncEventSourcingRuntime(Class<R> requestHandlerClass) {
        if (!requestHandlerClass.isInterface()) {
            throw new IllegalArgumentException("Request Handler class must be an interface. Provided class "+requestHandlerClass.getSimpleName());
        }
        this.requestHandlerClass = new Class<?>[] {requestHandlerClass};
        
    }
    
    public R execute(String id) {
        return (R) Proxy.newProxyInstance(getClass().getClassLoader(), requestHandlerClass, (p,m,a) -> {
            try {
                return execute(id, m, a).get();
            } catch (ExecutionException e) {
                logger.info("Rethrowing",e.getCause());
                throw e.getCause();
            }
        });
    }

    protected CompletableFuture<Object> execute(String id, Method m, Object[] a) {
        return delegate.execute(id, new InvocationRequest(m, a));
    }
                
    public R execute(String id, long timeout, TimeUnit unit) {
        return (R) Proxy.newProxyInstance(getClass().getClassLoader(), requestHandlerClass, (p,m,a) -> {
            try {
                return execute(id, m, a).get(timeout, unit);
            } catch (ExecutionException e) {
                logger.info("Rethrowing",e.getCause());
                throw e.getCause();
            } catch (Exception e) {
                logger.info("Rethrowing",e);
                throw e;
            }
        });
    }        

    protected abstract E instantiate(String entityId);

    protected abstract void dispose(E entity);

    protected abstract SnapshotStore getSnapshotStore();

    protected abstract EventLog getEventLog();

    protected abstract boolean shouldStoreSnapshot(E entity, int eventsSinceSnapshot);
       
    protected static class InvocationRequest implements Request<Object> {

        protected final Method m;
        protected final Object[] arguments;
        
        protected InvocationRequest(Method m, Object[] arguments) {
            this.m = m;
            this.arguments = arguments;
        }
    }
    
    /**
     * ExecutorService, that will handle entity invocations. Should usually be backed by multiple threads, or can use
     * Java EE 7 Concurrency Utilities' managed resources from application server.
     * @return instance of executor service
     */
    protected abstract ExecutorService getExecutorService();

    /**
     * ScheduledExecutorService, that will handle delayed delivery and timeouts of the requests. Must not use same
     * thread pool like {@link #getExecutorService()}, because in case that pool is overflowing, the timeouts would
     * not get executed
     * @return instance of ScheduledExecutorService
     */
    protected abstract ScheduledExecutorService getScheduler();

    /**
     * The abstract name for the entities the runtime is servicing. Used for better log messages, and log destinations.
     * @return short name describing the entities
     */
    protected abstract String getEntityName();

/**
     * Specify whether and when the request should be retried in case of failure.
     * Should return redelivery delay in milliseconds. Returning {@code 0} means to retry immediately, returning less
     * than {@code 0} means not to retry.
     * <p>If retry is greater than zero, the runtime may execute other requests before the wait period expires</p>
     * @param id identity of the entity
     * @param methodName name of the method that failed
     * @param error the throwable the request failed with
     * @param attempts number of attempts for execution of that request. Greater than {@code 1}.
     * @return negative for failing the request, zero for immediate retry, positive for delay in ms until next attempt
     * @see DispatcherConfiguration#retryDelay(String, Request, Throwable, int)  for exactly the same information
     */
    protected long retryDelay(String id, String methodName, Throwable error, int attempts) {
        if (attempts < 5) {
            return RETRY_NOW;
        } else {
            logger.warn("Entity {} failed processing request {} after {} attempts", id, methodName, attempts, error);
            return RETRY_NEVER;
        }
    }
    
    protected EntityInvocationHandler<E> invocationHandler() {
        return delegate.invocationHandler();
    }
    
    public static abstract class WithAsyncInterface<E extends ProxiedSyncEntity<R>,R,A> extends ProxiedSyncEventSourcingRuntime<E, R> {

        private final Class<A> asyncRequestHandlerClass;
        private final Map<Method, Method> mapping = new HashMap<>();
        private final Class<R> requestHandlerClass;
        
        protected WithAsyncInterface(Class<R> requestHandlerClass, Class<A> asyncRequestHandlerClass) {
            super(requestHandlerClass);
            this.requestHandlerClass = requestHandlerClass;
            if (!asyncRequestHandlerClass.isInterface()) {
                throw new IllegalArgumentException("Async request handler must be also an interface");
            }
            this.asyncRequestHandlerClass = asyncRequestHandlerClass;
            checkAsyncInterface();
        }
        
        public A executeAsync(String id) {
           return (A) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{asyncRequestHandlerClass}, (p,m,a) -> { 
               return execute(id, mapping.get(m), a);
           });
        }
        
        private void checkAsyncInterface() {
            for (Method m : asyncRequestHandlerClass.getMethods()) {
                try {
                    if (!m.getReturnType().isAssignableFrom(CompletableFuture.class) || m.getReturnType().equals(Object.class)) {
                        throw new IllegalArgumentException("All methods of async request handler must return CompletableFuture. "
                                + "Offending method: "+m.toGenericString());
                    }
                    //CompletableFuture<T>
                    Class<?> targetType = (Class<?>)((ParameterizedType) m.getGenericReturnType()).getActualTypeArguments()[0];
                    Method originalMethod = requestHandlerClass.getMethod(m.getName(), m.getParameterTypes());
                    if (originalMethod.getReturnType().equals(void.class) ? !targetType.equals(Void.class) // more primitive handling needed
                            : !targetType.isAssignableFrom(originalMethod.getReturnType())) {
                        throw new IllegalArgumentException("Return type is not compatible for "+m.toGenericString()+
                                ". Original method returns "+originalMethod.getGenericReturnType().getTypeName());
                    }
                    mapping.put(m, originalMethod);
                } catch (NoSuchMethodException | SecurityException ex) {
                    throw new IllegalStateException("Cannot inspect request handler classes", ex);
                }
            }
        }
    }
    
    
    class DispatchingDelegate extends DispatchingEventSourcingRuntime<E> {

        @Override
        protected ExecutorService getExecutorService() {
            return ProxiedSyncEventSourcingRuntime.this.getExecutorService();
        }

        @Override
        protected ScheduledExecutorService getScheduler() {
            return ProxiedSyncEventSourcingRuntime.this.getScheduler();
        }

        @Override
        protected String getEntityName() {
            return ProxiedSyncEventSourcingRuntime.this.getEntityName();
        }
        
        protected EntityInvocationHandler<E> invocationHandler() {
            return invocationHandler;
        }

        @Override
        protected <RS, R1 extends Request<RS>> void invokeEntity(E entity, R1 request, BiConsumer<RS, Throwable> callback) throws Exception {
            if (!(request instanceof InvocationRequest)) {
                callback.accept(null, new IllegalArgumentException("Someone managed to call internal dispatcher of "+ProxiedSyncEventSourcingRuntime.this.getClass().getSimpleName()+" directly."));        
            }
            InvocationRequest r = (InvocationRequest)request;
            R handler = entity.requestHandler();
            if (handler == null) {
                callback.accept(null, new NullPointerException("Entity "+entity.getIdentity()+" returned null command handler"));
            } else {
                try {
                    callback.accept((RS) r.m.invoke(handler, r.arguments),null);
                } catch (InvocationTargetException ite) {
                    logger.info("Caught", ite);
                    callback.accept(null, ite.getTargetException());
                } catch (Throwable t) {
                     logger.info("Caught", t);
                    callback.accept(null, t);
                }
            }
        }

        @Override
        protected E instantiate(String entityId) {
            return ProxiedSyncEventSourcingRuntime.this.instantiate(entityId);
        }

        @Override
        protected void dispose(E entity) {
            ProxiedSyncEventSourcingRuntime.this.dispose(entity);
        }

        @Override
        protected SnapshotStore getSnapshotStore() {
            return ProxiedSyncEventSourcingRuntime.this.getSnapshotStore();
        }

        @Override
        protected EventLog getEventLog() {
            return ProxiedSyncEventSourcingRuntime.this.getEventLog();
        }

        @Override
        protected boolean shouldStoreSnapshot(E entity, int eventsSinceSnapshot) {
            return ProxiedSyncEventSourcingRuntime.this.shouldStoreSnapshot(entity, eventsSinceSnapshot);
        }

        @Override
        protected long retryDelay(String id, Request<?> request, Throwable error, int attempts) {
            return ProxiedSyncEventSourcingRuntime.this.retryDelay(id, ((InvocationRequest)request).m.getName(), error, attempts);
        }
       
        
        
    }
}
