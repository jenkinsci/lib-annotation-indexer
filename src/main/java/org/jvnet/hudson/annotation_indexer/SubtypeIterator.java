package org.jvnet.hudson.annotation_indexer;

import java.util.Iterator;
import java.util.NoSuchElementException;

final class SubtypeIterator<T,U extends T> implements Iterator<U> {
    private final Iterator<? extends T> core;
    private final Class<U> type;
    private T next;
    private boolean fetched;

    SubtypeIterator(Iterator<? extends T> core, Class<U> type) {
        this.core = core;
        this.type = type;
    }

    private void fetch() {
        while(!fetched && core.hasNext()) {
            T n = core.next();
            if(type.isInstance(n)) {
                next = n;
                fetched = true;
            }
        }
    }

    public boolean hasNext() {
        fetch();
        return fetched;
    }

    public U next() {
        fetch();
        if(!fetched)  throw new NoSuchElementException();
        fetched = false;
        return type.cast(next);
    }

    public void remove() {
        core.remove();
    }
}
