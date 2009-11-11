package org.jvnet.hudson.annotation_indexer;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class FilterIterator<T> implements Iterator<T> {
    private final Iterator<? extends T> core;
    private T next;
    private boolean fetched;

    protected FilterIterator(Iterator<? extends T> core) {
        this.core = core;
    }

    protected FilterIterator(Iterable<? extends T> core) {
        this(core.iterator());
    }

    private void fetch() {
        while(!fetched && core.hasNext()) {
            T n = core.next();
            if(filter(n)) {
                next = n;
                fetched = true;
            }
        }
    }

    /**
     * Filter out items in the original collection.
     *
     * @return
     *      true to leave this item and return this item from this iterator.
     *      false to hide this item.
     */
    protected abstract boolean filter(T t);

    public boolean hasNext() {
        fetch();
        return fetched;
    }

    public T next() {
        fetch();
        if(!fetched)  throw new NoSuchElementException();
        fetched = false;
        return next;
    }

    public void remove() {
        core.remove();
    }
}
