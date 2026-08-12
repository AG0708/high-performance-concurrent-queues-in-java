# Resume bullets

- Built a Java concurrent queue library with lock-free and fixed-capacity FIFO implementations using atomic operations and release/acquire memory ordering.
- Added batched reads and writes that reached 381M queue operations/sec, 2.7x `ArrayBlockingQueue`, and verified FIFO ordering with randomized histories, stress tests, and JCStress.

The benchmark used three producers, one consumer, batches of 32, and an Apple M5. Keep that scope attached to the numeric claim.
