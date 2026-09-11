# Data Structures & Logic — Interview Prep (Round 2)
## For a Java / Spring Boot / Microservices / AWS / OracleDB profile

Each data structure: **What it is → When to use → Java class → Key operations + complexity → Interview Q&A**.
At the end: logic patterns and how DS shows up in your real work (Spring/AWS/Oracle).

---

# PART 1: CORE DATA STRUCTURES

---

## 1. Array

**What:** Fixed-size, contiguous block of elements accessed by index.

**When to use:** Fast random access by index; size known in advance.

**Java:** `int[] arr = new int[5];`

| Operation | Complexity |
|-----------|-----------|
| Access by index | O(1) |
| Search (unsorted) | O(n) |
| Insert/Delete (middle) | O(n) — shifting |

**Q: Array vs ArrayList?**
> Array is fixed-size and can hold primitives. ArrayList is resizable, holds only objects (autoboxing for primitives), and is backed by an array internally. ArrayList grows by ~50% when full.

---

## 2. ArrayList (Dynamic Array)

**What:** Resizable array. Java's `List` backed by an array.

**When to use:** You need indexed access + a growable list. Most common list choice.

**Java:** `List<Integer> list = new ArrayList<>();`

| Operation | Complexity |
|-----------|-----------|
| get(i) / set(i) | O(1) |
| add (end) | O(1) amortized |
| add/remove (middle) | O(n) |
| contains / indexOf | O(n) |

**Q: What happens when ArrayList is full?**
> It creates a new array ~1.5× the size, copies elements over (O(n) once), then continues. This is why add() is "amortized O(1)".

---

## 3. LinkedList

**What:** Nodes connected by pointers (doubly-linked in Java).

**When to use:** Frequent insert/delete at the ends or middle (with a known node); implementing queues/deques.

**Java:** `LinkedList<Integer> list = new LinkedList<>();`

| Operation | Complexity |
|-----------|-----------|
| addFirst / addLast / removeFirst / removeLast | O(1) |
| get(i) | O(n) — must walk |
| insert/delete at known node | O(1) |

**Q: ArrayList vs LinkedList — when to use which?**
> ArrayList for random access and iteration (cache-friendly, O(1) get). LinkedList for frequent add/remove at the ends. In practice, ArrayList wins most of the time due to memory locality — I'd default to ArrayList unless I specifically need queue/deque behavior.

---

## 4. HashMap

**What:** Key-value store using hashing. O(1) average lookup.

**When to use:** Fast lookup by key; counting/frequency; caching in memory.

**Java:** `Map<String, Integer> map = new HashMap<>();`

| Operation | Complexity |
|-----------|-----------|
| put / get / remove | O(1) average, O(n) worst (collisions) |

**Q: How does HashMap work internally?**
> An array of buckets. `hashCode()` decides the bucket index, `equals()` finds the exact key within a bucket. Collisions are stored as a linked list, which converts to a balanced (red-black) tree when a bucket exceeds 8 entries (Java 8+), improving worst case to O(log n).

**Q: What if you override equals() but not hashCode()?**
> Equal objects may land in different buckets, so HashMap/HashSet break — you can't find keys you inserted. Always override both together.

**Q: HashMap vs Hashtable vs ConcurrentHashMap?**
> Hashtable is legacy and fully synchronized (slow). HashMap is not thread-safe. ConcurrentHashMap is thread-safe with segment/bucket-level locking, so it allows concurrent reads and scoped writes — the modern choice for concurrent maps.

---

## 5. HashSet

**What:** Collection of unique elements (backed by a HashMap).

**When to use:** "Have I seen this?" / removing duplicates / membership tests.

**Java:** `Set<Integer> set = new HashSet<>();`

| Operation | Complexity |
|-----------|-----------|
| add / contains / remove | O(1) average |

**Q: HashSet vs LinkedHashSet vs TreeSet?**
> HashSet — no order, fastest. LinkedHashSet — insertion order, O(1). TreeSet — sorted order, O(log n), backed by a red-black tree.

---

## 6. TreeMap / TreeSet

**What:** Sorted map/set backed by a Red-Black tree (self-balancing BST).

**When to use:** You need keys/elements kept in sorted order, or range queries (floor, ceiling, headMap, tailMap).

**Java:** `TreeMap<Integer,String> map = new TreeMap<>();`

| Operation | Complexity |
|-----------|-----------|
| put / get / remove | O(log n) |
| firstKey / lastKey / floor / ceiling | O(log n) |

**Q: When TreeMap over HashMap?**
> When I need sorted iteration or range operations (e.g., "all entries between X and Y"). HashMap has no ordering.

---

## 7. Stack (LIFO)

**What:** Last-In-First-Out. Push/pop from the top.

**When to use:** Undo, backtracking, matching brackets, expression evaluation, DFS.

**Java (preferred):** `Deque<Integer> stack = new ArrayDeque<>();` (use `push`, `pop`, `peek`)
> Avoid the legacy `Stack` class (it's synchronized and extends Vector).

| Operation | Complexity |
|-----------|-----------|
| push / pop / peek | O(1) |

**Real example (valid parentheses):** push opening brackets, pop and match on closing.

---

## 8. Queue (FIFO) & Deque

**What:** First-In-First-Out. Deque = double-ended queue (both ends).

**When to use:** BFS, task scheduling, producer-consumer, sliding window.

**Java:** `Queue<Integer> q = new LinkedList<>();` or `Deque<Integer> dq = new ArrayDeque<>();`

| Operation | Complexity |
|-----------|-----------|
| offer / poll / peek | O(1) |

**Q: How does a queue apply to your work?**
> Kafka is essentially a distributed, durable queue/log. In-app, I've used BlockingQueue with thread pools (ExecutorService) for producer-consumer patterns.

---

## 9. PriorityQueue (Heap)

**What:** Elements ordered by priority; min-heap by default in Java.

**When to use:** "Top K" problems, Dijkstra's shortest path, scheduling by priority, merging sorted streams.

**Java:** `PriorityQueue<Integer> pq = new PriorityQueue<>();` (min-heap)
For max-heap: `new PriorityQueue<>(Collections.reverseOrder())`

| Operation | Complexity |
|-----------|-----------|
| peek (min/max) | O(1) |
| offer / poll | O(log n) |

**Real example (Kth largest element):** keep a min-heap of size K; the root is the Kth largest.
```java
PriorityQueue<Integer> minHeap = new PriorityQueue<>();
for (int n : nums) {
    minHeap.offer(n);
    if (minHeap.size() > k) minHeap.poll();  // keep only K largest
}
return minHeap.peek();  // Kth largest
```

---

## 10. Tree / Binary Search Tree (BST)

**What:** Hierarchical nodes; BST keeps left < root < right.

**When to use:** Sorted data with fast search/insert; hierarchies; indexes.

| Operation (balanced BST) | Complexity |
|--------------------------|-----------|
| search / insert / delete | O(log n) |

**Q: How does this relate to databases (OracleDB)?**
> Database indexes are typically **B-Trees** (a generalization of BSTs with many children per node). They keep data sorted and allow O(log n) lookups, which is why an indexed column query is fast. This is a great point to make given your OracleDB skill.

**Traversals:**
- **Inorder** (left, root, right) → gives sorted order for a BST
- **Preorder** (root, left, right) → copying a tree
- **Postorder** (left, right, root) → deleting a tree
- **Level-order** (BFS) → uses a queue

---

## 11. Graph

**What:** Nodes (vertices) + edges. Directed/undirected, weighted/unweighted.

**When to use:** Networks, dependencies, routing, social connections, microservice call graphs.

**Representations:** Adjacency list (common) or adjacency matrix.

**Traversals:**
- **BFS** (queue) — shortest path in unweighted graphs
- **DFS** (stack/recursion) — cycle detection, topological sort

**Q: Where do graphs show up in microservices?**
> Service dependency graphs, and cycle detection (like Spring's circular dependency detection). Distributed tracing (Zipkin) builds a call graph across services.

---

# PART 2: COMPLEXITY (Big-O) — MUST KNOW

| Notation | Name | Example |
|----------|------|---------|
| O(1) | Constant | HashMap get, array index |
| O(log n) | Logarithmic | Binary search, balanced BST |
| O(n) | Linear | Single loop, list search |
| O(n log n) | Linearithmic | Efficient sorts (merge, quick avg) |
| O(n²) | Quadratic | Nested loops, bubble sort |
| O(2ⁿ) | Exponential | Naive recursion (fib), subsets |

**How to talk about it:** "This is O(n) time because I scan the array once, and O(n) space because I use a HashMap of size n. I traded space for time to avoid the O(n²) brute force."

---

# PART 3: KEY LOGIC PATTERNS (say the trigger, then solve)

| Pattern | Trigger phrase | Tool |
|---------|---------------|------|
| Two Pointers | "sorted array", "compare ends", "pair" | two indices moving inward |
| Sliding Window | "longest/shortest substring/subarray" | expand right, shrink left |
| HashMap lookup | "count", "frequency", "have I seen it", "pair sum" | HashMap / HashSet |
| Fast & Slow pointers | "cycle", "middle of list" | two pointers at different speeds |
| Binary Search | "sorted + find/count" | lo/hi/mid, O(log n) |
| BFS / DFS | "tree/graph traversal", "shortest path", "connected" | queue / recursion |
| Heap (Top K) | "Kth largest/smallest", "top N" | PriorityQueue of size K |
| Stack | "matching", "nesting", "undo", "next greater" | Deque as stack |
| Dynamic Programming | "ways to", "min/max path", "overlapping subproblems" | memo array / table |
| Recursion / Backtracking | "all combinations/permutations" | recurse + undo |

**Golden rule:** HashMap + Two Pointers solve ~60% of interview problems. Master those first.

---

# PART 4: HIGH-FREQUENCY CODING PROBLEMS (know these cold)

These come up constantly. (Full solutions are in `problem_solving.md`.)

1. Two Sum → HashMap of complements
2. Reverse a string / linked list → two pointers / 3-pointer rewire
3. Valid parentheses → Stack
4. First non-repeating character → LinkedHashMap frequency
5. Find duplicates / anagram → HashSet / int[26]
6. Kth largest element → min-heap of size K
7. Merge two sorted lists → two pointers
8. Longest substring without repeats → sliding window
9. Max subarray sum → Kadane's
10. Detect cycle in linked list → fast & slow pointers
11. Binary search / first-last occurrence → O(log n)
12. Level-order tree traversal → BFS with queue

---

# PART 5: HOW DATA STRUCTURES SHOW UP IN YOUR ACTUAL STACK

Interviewers love when you connect DS to real work:

| Data Structure | In your stack |
|----------------|---------------|
| **HashMap** | Redis cache (key→value), in-memory lookups, HTTP header maps |
| **Queue** | Kafka topics (distributed log/queue), thread-pool task queues |
| **B-Tree** | OracleDB indexes → why indexed queries are O(log n) |
| **Heap / PriorityQueue** | Task scheduling, rate limiting priorities |
| **Graph** | Microservice dependency graph, Zipkin trace spans, Spring bean dependency resolution |
| **Set** | Deduplication, unique constraint checks before DB insert |
| **LinkedHashMap** | LRU cache implementation (access-ordered) |

**Strong line to say:**
> "Data structures aren't just interview trivia — I use them daily. Redis is a big HashMap, Kafka is a distributed queue, and Oracle indexes are B-Trees, which is exactly why adding an index turns a full table scan into an O(log n) lookup."

---

# PART 6: LIKELY QUESTIONS FOR YOUR PROFILE

**Q: Implement an LRU cache.**
> Use a `LinkedHashMap` with access-order = true, override `removeEldestEntry`. Or combine a HashMap (O(1) lookup) + doubly linked list (O(1) eviction). This ties to your Redis/caching experience.

```java
class LRUCache<K,V> extends LinkedHashMap<K,V> {
    private final int capacity;
    LRUCache(int capacity) {
        super(capacity, 0.75f, true);  // true = access order
        this.capacity = capacity;
    }
    protected boolean removeEldestEntry(Map.Entry<K,V> eldest) {
        return size() > capacity;   // evict oldest when over capacity
    }
}
```

**Q: How would you find duplicate records in a large dataset?**
> In memory: HashSet. In a DB (Oracle): `GROUP BY column HAVING COUNT(*) > 1`. For huge data: process in batches or use external sort. Mentioning both the DS answer and the SQL answer shows range.

**Q: Design a rate limiter.**
> Redis counter with TTL (fixed window) or a sliding window log. Data structure: a counter (HashMap-like) with expiry. This maps directly to what you built in your project.

**Q: How does Spring detect circular dependencies?**
> It tracks beans currently under construction (a set / stack). If it tries to create a bean already in that set, it detects the cycle → this is graph cycle detection.

---

# PART 7: THE 6-STEP APPROACH (say this out loud in the interview)

1. **Clarify** — input type, size, edge cases (null? empty? duplicates? sorted?)
2. **Example** — walk one small input → output by hand
3. **Approach** — "This is a ___ problem, so I'll use ___" (name the pattern)
4. **Code** — narrate as you write; state types and loop bounds
5. **Dry-run** — trace your example through the code
6. **Complexity** — state time and space Big-O + any tradeoff

Interviewers score you on **hearing your reasoning**, not just the final code. Think out loud.

---

# FINAL CHECKLIST BEFORE THE INTERVIEW

- [ ] Know Big-O of every collection operation (table in Part 2)
- [ ] Can implement: reverse string/list, Two Sum, valid parentheses, BFS/DFS, binary search
- [ ] Can explain HashMap internals + equals/hashCode contract
- [ ] Can implement an LRU cache (ties to your Redis work)
- [ ] Can connect DS to your stack (Kafka=queue, Oracle index=B-Tree, Redis=HashMap)
- [ ] Practice thinking OUT LOUD — narrate the trigger + approach + complexity

---

*You've got this. Master HashMap + two pointers + BFS/DFS, connect them to your real stack, and narrate your reasoning.*
