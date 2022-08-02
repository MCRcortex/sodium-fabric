package net.caffeinemc.sodium.render.chunk.draw.pool;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;

public class LinkedDeque <T> implements Deque<T>  {
    private Node[] heap = new Node[1000];
    private int heapCount = 0;

    private Node<T> end;
    private static final class Node <E> {
        Node<E> prev;
        E value;
    }

    @Override
    public T pollLast() {
        if (this.end == null)
            return null;
        Node<T> end = this.end;
        this.end = end.prev;
        if (heapCount != heap.length) {
            heap[heapCount++] = end;
        }
        return end.value;
    }

    @Override
    public boolean add(T o) {
        Node<T> n;
        if (heapCount != 0) {
            n = heap[--heapCount];
        } else {
            n = new Node<>();
        }
        n.prev = end;
        end = n;
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        c.forEach(this::add);
        return true;
    }

    @Override
    public void addFirst(T o) {
        throw new IllegalStateException();
    }

    @Override
    public void addLast(T o) {
        throw new IllegalStateException();

    }

    @Override
    public boolean offerFirst(T o) {
        throw new IllegalStateException();
    }

    @Override
    public boolean offerLast(T o) {
        throw new IllegalStateException();
    }

    @Override
    public T removeFirst() {
        throw new IllegalStateException();
    }

    @Override
    public T removeLast() {
        throw new IllegalStateException();
    }

    @Override
    public T pollFirst() {
        throw new IllegalStateException();
    }

    @Override
    public T getFirst() {
        throw new IllegalStateException();
    }

    @Override
    public T getLast() {
        throw new IllegalStateException();
    }

    @Override
    public T peekFirst() {
        throw new IllegalStateException();
    }

    @Override
    public T peekLast() {
        throw new IllegalStateException();
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        throw new IllegalStateException();
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        throw new IllegalStateException();
    }

    @Override
    public boolean offer(T o) {
        throw new IllegalStateException();
    }

    @Override
    public T remove() {
        throw new IllegalStateException();
    }

    @Override
    public T poll() {
        throw new IllegalStateException();
    }

    @Override
    public T element() {
        throw new IllegalStateException();
    }

    @Override
    public T peek() {
        throw new IllegalStateException();
    }

    @Override
    public void clear() {
        throw new IllegalStateException();
    }

    @Override
    public boolean retainAll(@NotNull Collection c) {
        throw new IllegalStateException();
    }

    @Override
    public boolean removeAll(@NotNull Collection c) {
        throw new IllegalStateException();
    }

    @Override
    public void push(T o) {
        throw new IllegalStateException();
    }

    @Override
    public T pop() {
        throw new IllegalStateException();
    }

    @Override
    public boolean remove(Object o) {
        throw new IllegalStateException();
    }

    @Override
    public boolean containsAll(@NotNull Collection c) {
        throw new IllegalStateException();
    }

    @Override
    public boolean contains(Object o) {
        throw new IllegalStateException();
    }

    @Override
    public int size() {
        throw new IllegalStateException();
    }

    @Override
    public boolean isEmpty() {
        throw new IllegalStateException();
    }

    @Override
    public Iterator iterator() {
        throw new IllegalStateException();
    }

    @NotNull
    @Override
    public T[] toArray() {
        throw new IllegalStateException();
    }

    @NotNull
    @Override
    public T[] toArray(@NotNull Object[] a) {
        throw new IllegalStateException();
    }

    @NotNull
    @Override
    public Iterator descendingIterator() {
        throw new IllegalStateException();
    }

    @Override
    public String toString() {
        return "LinkedDeque{}";
    }
}
