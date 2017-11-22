package io.github.goodees.ese;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class MapBasedWorkingMemory<E extends EventSourcedEntity> implements EntityInvocationHandler.WorkingMemory<E> {
    private ConcurrentMap<String, E> map = new ConcurrentHashMap<>();
    @Override
    public E lookup(String id, Function<String, E> instantiator) {
        return map.computeIfAbsent(id, instantiator);
    }

    @Override
    public void remove(String id) {
        map.remove(id);
    }
}
