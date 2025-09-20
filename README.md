# java-virtual-threads
Performance characteristics of Virtual, Platform  and Fixed number of threads

## Virtual Threads
Virtual threads are lightweight threads introduced in Java to simplify high-concurrency programming. Unlike platform (OS) threads, which consume significant memory and scheduling overhead, virtual threads require only a few KBs of memory and are managed by the JVM.
 * **Platform threads (OS threads):** Typically consume ~1 MB of memory each (1 MB for stack + small amount of Kernel memory for context switching overhead).
 * **Virtual threads:** Consume only a few KBs each, allowing millions of concurrent threads in a modern JVM.

## Platform Threads and OS Context Switching

Platform threads in Java map directly to OS threads (1:1). The operating system is responsible for scheduling and context switching between them.

### When does the OS perform a context switch?

 * Blocking system calls (read, write, futex, etc.)
 * Preemption by the scheduler
 * Timer interrupts or hardware interrupts

### What happens during an OS context switch?

 1. Save the CPU state of the current thread (program counter, registers, stack pointer, etc.).
 2. Save or restore memory mappings, cache/TLB state, etc.
 3. Load the state of the next thread to run.
 4. Transition between user mode and kernel mode as needed.

 *This process is **expensive**: each switch takes hundreds to thousands of nanoseconds, and across millions of switches, the overhead becomes significant.*

## JVM Context Switching with Virtual Threads

Platform threads remain blocked if the Java code makes certain calls (e.g., synchronized, Thread.sleep, database calls, or blocking socket APIs), because the OS has no visibility into them.

To solve this, Java introduced virtual threads, which are scheduled by the JVM on top of a smaller pool of platform threads (called carrier threads).

### How JVM handles blocking operations for virtual threads:

 * When a virtual thread encounters a blocking I/O or known blocking call:
   1. The JVM suspends the virtual thread in user space (no OS involvement).
   2. Its stack is stored in the Java heap.
   3. Another virtual thread can immediately resume on the same carrier thread.

 * Resuming a virtual thread is just restoring Java object state — not a full OS state restore.

*This makes JVM context switches **cheap** compared to OS context switches.*

## When to Use Virtual Threads

 * **I/O-bound workloads:**
    Ideal for applications where threads frequently wait on I/O (e.g., web servers, database clients). Virtual threads free up carrier threads for other work while blocked.

 * **CPU-bound workloads:**
    Virtual threads behave like platform threads since they do not yield until CPU work is finished. They provide little advantage in pure CPU-heavy tasks.

## Limits on Virtual Threads

 * **No fixed upper limit by the JVM.**
 * Each virtual thread requires only:
   * A small Java object (~hundreds of bytes).
   * A stack that grows on demand, stored mostly in the heap (unlike platform threads, which pre-allocate ~1 MB stack).
 * **Practical limits:** Constrained by available heap memory and the amount of work threads perform.

 * In practice:
   * **Platform threads:** Limited to hundreds or thousands.
   * **Virtual threads:** Can scale to hundreds of thousands or even millions.

## Controlling the Number of Threads

To prevent unbounded thread creation:
 * Use a **bounded queue** to hold tasks.
 * Use a **semaphore** to restrict concurrent threads.


