# Design

## `MichaelScottQueue`

The queue starts with one sentinel node. `head` points to the consumed prefix and `tail` points at or behind the last linked node.

- A producer links one new node with CAS. That link is the enqueue linearization point.
- A producer that sees a linked successor advances the lagging tail before retrying.
- A consumer advances `head` with CAS. That CAS is the dequeue linearization point.
- Nodes are never reused. JVM garbage collection removes the address-reuse ABA problem.

A delayed thread cannot stop another thread from completing an operation, so the algorithm is lock-free.

## `MpmcArrayQueue`

The capacity is a power of two. Each logical slot has an element and a sequence number.

- A producer may claim slot `p` only when its sequence equals `p`.
- Publishing the element then release-storing sequence `p + 1` makes the slot visible.
- A consumer may claim slot `c` only when its sequence equals `c + 1`.
- Clearing the element then release-storing sequence `c + capacity` makes the slot reusable.

Acquire reads of sequence numbers pair with those release stores. Producer and consumer cursor CAS operations impose a single FIFO reservation order.

The implementation is linearizable but not strictly lock-free. A producer paused after reserving the next FIFO slot can delay consumers until it publishes that slot.

## `MpscArrayQueue`

Multiple producers CAS a shared reservation cursor. The single consumer owns its cursor and therefore does not need a dequeue CAS.

The producer limit caches `consumer + capacity`. Producers refresh it only near full capacity. This removes a consumer-cursor read from the common offer path.

`offerBatch` reserves a contiguous range with one CAS and publishes each slot in order. `pollBatch` stages values in the destination, clears the range, then publishes one consumer-cursor update. Failed validation cannot partially change queue state.

## Memory layout

Hot long cursors are 128 bytes apart in one primitive array. This prevents those values from occupying the same 64-byte cache line on conventional HotSpot layouts. MPMC slot sequence counters support compact and 64-byte-spaced layouts; compact is the default because it performed better in the recorded workload.

## Boundaries

- `null` is reserved for an empty poll and cannot be inserted.
- Array capacities must be powers of two and at least two.
- `MpscArrayQueue.poll` and `pollBatch` must have one calling consumer thread.
- Array cursors use signed 64-bit positions. Counter rollover is not tested.
- Iteration, blocking waits, removal by value, and dynamic resizing are outside the API.

## Algorithm references

- Maged Michael and Michael Scott, [Simple, Fast, and Practical Non-Blocking and Blocking Concurrent Queue Algorithms](https://www.cs.rochester.edu/research/synchronization/pseudocode/queues.html)
- Dmitry Vyukov, [Bounded MPMC queue](https://www.1024cores.net/home/lock-free-algorithms/queues/bounded-mpmc-queue)
- OpenJDK, [`VarHandle` memory-ordering specification](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/invoke/VarHandle.html)

The code is an independent Java implementation of the published algorithms and invariants; no third-party queue source is included.
