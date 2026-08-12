package io.github.ag0708.stridequeue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exhaustive checker for small histories against a bounded sequential FIFO. */
final class QueueLinearizabilityChecker {
    private QueueLinearizabilityChecker() {}

    static boolean isLinearizable(List<Operation> history, int capacity) {
        if (history.size() > Long.SIZE - 1) {
            throw new IllegalArgumentException("history is too large for exhaustive checking");
        }
        long[] predecessors = predecessorMasks(history);
        return search(history, capacity, predecessors, 0L, new ArrayDeque<>(), new HashSet<>());
    }

    private static boolean search(
            List<Operation> history,
            int capacity,
            long[] predecessors,
            long completed,
            Deque<Integer> model,
            Set<StateKey> visited) {
        long completeMask = (1L << history.size()) - 1L;
        if (completed == completeMask) {
            return true;
        }

        StateKey key = new StateKey(completed, List.copyOf(model));
        if (!visited.add(key)) {
            return false;
        }

        for (int index = 0; index < history.size(); index++) {
            long bit = 1L << index;
            if ((completed & bit) != 0L || (predecessors[index] & ~completed) != 0L) {
                continue;
            }

            Deque<Integer> nextModel = new ArrayDeque<>(model);
            if (apply(history.get(index), capacity, nextModel)
                    && search(
                            history,
                            capacity,
                            predecessors,
                            completed | bit,
                            nextModel,
                            visited)) {
                return true;
            }
        }
        return false;
    }

    private static boolean apply(Operation operation, int capacity, Deque<Integer> model) {
        if (operation.kind() == Kind.OFFER) {
            boolean canAccept = model.size() < capacity;
            if (operation.offerResult() != canAccept) {
                return false;
            }
            if (canAccept) {
                model.addLast(operation.argument());
            }
            return true;
        }

        Integer expected = model.peekFirst();
        if (!java.util.Objects.equals(expected, operation.pollResult())) {
            return false;
        }
        if (expected != null) {
            model.removeFirst();
        }
        return true;
    }

    private static long[] predecessorMasks(List<Operation> history) {
        long[] masks = new long[history.size()];
        for (int operation = 0; operation < history.size(); operation++) {
            long invokedAt = history.get(operation).invokedAt();
            for (int candidate = 0; candidate < history.size(); candidate++) {
                if (history.get(candidate).completedAt() < invokedAt) {
                    masks[operation] |= 1L << candidate;
                }
            }
        }
        return masks;
    }

    static final class Recorder {
        private final List<Operation> operations = new ArrayList<>();

        synchronized void recordOffer(int argument, boolean result, long start, long end) {
            operations.add(Operation.offer(argument, result, start, end));
        }

        synchronized void recordPoll(Integer result, long start, long end) {
            operations.add(Operation.poll(result, start, end));
        }

        synchronized List<Operation> snapshot() {
            return List.copyOf(operations);
        }
    }

    enum Kind {
        OFFER,
        POLL
    }

    record Operation(
            Kind kind,
            Integer argument,
            boolean offerResult,
            Integer pollResult,
            long invokedAt,
            long completedAt) {
        static Operation offer(int argument, boolean result, long start, long end) {
            return new Operation(Kind.OFFER, argument, result, null, start, end);
        }

        static Operation poll(Integer result, long start, long end) {
            return new Operation(Kind.POLL, null, false, result, start, end);
        }
    }

    private record StateKey(long completed, List<Integer> contents) {}
}
