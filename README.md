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
 * Use a **semaphore** to restrict concurrent threads. See VirtualThreadsLimits class

## How does Virtual Thread do blocking IO?
A virtual thread runs on top of carrier thread, (platform thread, real OS thread).
At this point, the carrier thread is just executing the Java code on behalf of that virtual thread.

Blocking IO: InputStream.read()
* If this were a normal platform thread, it would block in the kernel until data arrives.
* That would waste the carrier thread for whole duration

But virtual thread avoids this by using **JVM's async I/O Integration**:
* The JVM intercepts the blocking call
* Underneath, it usually uses async APIs from the OS (epoll, kqueue, IOCP, etc.) to register 
the interest in that I/O event.
* Then the JVM unmounts the virtual thread from the carrier thread.

Once the virtual thread is unmounted, the carrier thread is free immediately.
So, 
* The carrier/platform thread leaves the OS thread and becomes reusable.
* The I/O readiness is tracked by JVM/OS event mechanism.
* When I/O completes (data is ready), the virtual thread is placed in a runnable queue and
the JVM schedules the virtual thread back onto some (not necessarily same) carrier thread.

## Fairness: Scheduling virtual threads from multiple Executor Service (handling tasks from multiple queues having unequal load)
JVM scheduler's treats all threads equally, aiming to keep the underlying platform threads (and thus the CPU cores) as busy as possible.. Its goal is throughput, not weighted fairness.

We can control concurrency using semaphors (e.g. 50K threads for Queue1, 10K for Queue2). However, this has no influence on scheduling. The virtual thread scheduler, which is by default a work-stealing ForkJoinPool, sees a single pool of 60,000 runnable virtual threads. Its job is to grab any available virtual thread and run it on a free platform thread. It does not know or care that 50,000 of them originated from Queue1 and 10,000 from Queue2. It just sees work to be done and does it as fast as possible.

To achieve the 5:1 execution ratio you desire, we must enforce this logic at the application level, before the tasks are submitted to be run as virtual threads. We control the rate of submission from your queues, not the scheduling of the threads once they are running. If the threads are in 5:1 ratio in the system, the threads will be treated fairly (each thread will get chance to execute)
