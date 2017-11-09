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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;

/**
 * Created by UI187816 on 20/04/2017.
 */
public abstract class SyncEventSourcingRuntime<E extends SyncEntity> extends DispatchingEventSourcingRuntime<E> {

    protected abstract ExecutorService getExecutorService();

    protected abstract ScheduledExecutorService getScheduler();

    protected abstract String getEntityName();

    @Override
    protected <RS, R extends Request<RS>> void invokeEntity(E entity, R request, BiConsumer<RS, Throwable> callback)
            throws Exception {
        RS response = entity.execute(request);
        //Question MP: why we have null there?
        callback.accept(response, null);
    }

}
