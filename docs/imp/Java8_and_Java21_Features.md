# Java 8 & Java 21 Features — Complete Interview Guide

Every feature: **What it is → Why it exists → Example → Interview line**.
Part A = Java 8 (the foundation every interview asks). Part B = Java 9→21 (the modern upgrade).

---

# PART A: JAVA 8 FEATURES

Java 8 (2014) was the biggest release ever — it brought functional programming to Java.

---

## 1. Lambda Expressions

**What:** A short, anonymous function — `(parameters) -> body`.

**Why:** Before Java 8, passing behavior meant writing verbose anonymous classes. Lambdas make code concise.

```java
// Before Java 8 — anonymous class
Runnable r1 = new Runnable() {
    public void run() { System.out.println("Hi"); }
};

// Java 8 — lambda
Runnable r2 = () -> System.out.println("Hi");

// Comparator
list.sort((a, b) -> a.compareTo(b));
```

**Interview line:** "A lambda is an anonymous function that lets me pass behavior as data. It works with functional interfaces and dramatically reduces boilerplate compared to anonymous classes."

---

## 2. Functional Interfaces

**What:** An interface with exactly ONE abstract method. Marked with `@FunctionalInterface`.

**Why:** Lambdas need a target type — a functional interface IS that type.

```java
@FunctionalInterface
interface Calculator {
    int operate(int a, int b);   // single abstract method
}

Calculator add = (a, b) -> a + b;
System.out.println(add.operate(2, 3));  // 5
```

**Built-in functional interfaces (memorize these):**

| Interface | Method | Purpose | Example |
|-----------|--------|---------|---------|
| `Predicate<T>` | test(T)→boolean | condition/filter | `x -> x > 5` |
| `Function<T,R>` | apply(T)→R | transform | `x -> x.length()` |
| `Consumer<T>` | accept(T)→void | consume | `x -> print(x)` |
| `Supplier<T>` | get()→T | supply/produce | `() -> new User()` |
| `BiFunction<T,U,R>` | apply(T,U)→R | 2-input transform | `(a,b) -> a+b` |
| `UnaryOperator<T>` | apply(T)→T | same-type transform | `x -> x*2` |
| `BinaryOperator<T>` | apply(T,T)→T | reduce two | `(a,b) -> a+b` |

**Interview line:** "A functional interface has one abstract method, so a lambda can implement it. Java provides ready-made ones like Predicate, Function, Consumer, and Supplier for common cases."

---

## 3. Method References

**What:** Shorthand for a lambda that just calls an existing method. Uses `::`.

**Four types:**
```java
// 1. Static method:        ClassName::staticMethod
Function<String,Integer> parse = Integer::parseInt;

// 2. Instance method of an object:  object::method
list.forEach(System.out::println);

// 3. Instance method of a class:    ClassName::instanceMethod
list.stream().map(String::toUpperCase);

// 4. Constructor:          ClassName::new
Supplier<ArrayList<String>> factory = ArrayList::new;
```

**Interview line:** "Method references are shorthand for lambdas that just call one method. `String::toUpperCase` is cleaner than `s -> s.toUpperCase()`."

---

## 4. Streams API

**What:** A pipeline to process collections declaratively — filter, map, reduce.

**Why:** Replaces verbose loops with readable, chainable operations. Supports lazy evaluation and parallelism.

**Two operation types:**
- **Intermediate** (lazy, return a stream): `filter`, `map`, `sorted`, `distinct`, `limit`, `peek`
- **Terminal** (trigger execution): `collect`, `forEach`, `count`, `reduce`, `anyMatch`, `findFirst`

```java
List<String> names = List.of("Riaan", "Anu", "Bala", "Anu");

List<String> result = names.stream()
    .distinct()                    // remove dups
    .filter(n -> n.length() > 3)   // keep long names
    .map(String::toUpperCase)      // transform
    .sorted()                      // sort
    .collect(Collectors.toList()); // terminal → [BALA, RIAAN]
```

**Common collectors:**
```java
.collect(Collectors.toList())
.collect(Collectors.toSet())
.collect(Collectors.joining(", "))
.collect(Collectors.groupingBy(Employee::getDept))
.collect(Collectors.counting())
.collect(Collectors.partitioningBy(e -> e.getSalary() > 50000))
.collect(Collectors.averagingDouble(Employee::getSalary))
```

**Interview line:** "Streams let me process collections declaratively — I describe WHAT I want, not HOW to loop. They're lazy: nothing runs until a terminal operation like collect."

---

## 5. Stream vs parallelStream

**What:** `parallelStream()` splits work across multiple threads (uses ForkJoinPool).

```java
long count = list.parallelStream().filter(x -> x > 100).count();
```

**Interview line:** "parallelStream splits the work across CPU cores using the common ForkJoinPool. It helps for large datasets and CPU-heavy operations, but adds overhead — for small collections or I/O-bound work, a sequential stream is faster. Also, the operation must be stateless and thread-safe."

---

## 6. Optional

**What:** A container that may or may not hold a value — replaces `null`.

**Why:** Forces you to handle the "no value" case, avoiding NullPointerException.

```java
Optional<User> user = repository.findById(1);

String name = user.map(User::getName).orElse("Unknown");
user.ifPresent(u -> System.out.println(u.getName()));
User u = user.orElseThrow(() -> new NotFoundException());
```

**Key methods:** `of`, `ofNullable`, `empty`, `isPresent`, `ifPresent`, `map`, `flatMap`, `orElse`, `orElseGet`, `orElseThrow`.

**Interview line:** "Optional makes the possibility of 'no result' explicit in the type. I use map, orElse, and orElseThrow instead of manual null checks."

---

## 7. Default and Static Methods in Interfaces

**What:** Interfaces can now have methods with a body.

**Why:** Lets you add new methods to an interface without breaking existing implementations (backward compatibility). This is how `List.forEach()` was added.

```java
interface Vehicle {
    void start();                              // abstract

    default void honk() {                       // default — has a body
        System.out.println("Beep!");
    }

    static Vehicle create() {                   // static
        return new Car();
    }
}
```

**Interview line:** "Default methods let interfaces evolve without breaking implementers. That's how Java added forEach to the Collection interface without breaking every existing List."

---

## 8. New Date/Time API (java.time)

**What:** Immutable, thread-safe date/time classes replacing the old `Date`/`Calendar`.

**Why:** Old `Date` was mutable, not thread-safe, and confusing (months 0-indexed!).

```java
LocalDate today = LocalDate.now();
LocalDate birthday = LocalDate.of(1995, 5, 20);
LocalDateTime now = LocalDateTime.now();
Period age = Period.between(birthday, today);
Duration d = Duration.ofHours(5);
LocalDate nextWeek = today.plusDays(7);
```

**Key classes:** `LocalDate`, `LocalTime`, `LocalDateTime`, `ZonedDateTime`, `Duration`, `Period`, `Instant`.

**Interview line:** "java.time is immutable and thread-safe, unlike the old mutable Date/Calendar. LocalDate/LocalDateTime for dates, Duration/Period for spans, ZonedDateTime for time zones."

---

## 9. CompletableFuture

**What:** Represents an async computation with chainable callbacks (non-blocking).

**Why:** The old `Future` could only block on `get()`. CompletableFuture lets you chain, combine, and handle errors.

```java
CompletableFuture.supplyAsync(() -> fetchUser(1))       // async task
    .thenApply(user -> user.getName())                   // transform
    .thenAccept(name -> System.out.println(name))        // consume
    .exceptionally(ex -> { log.error("fail", ex); return null; });

// Combine two:
CompletableFuture<String> combined = cf1.thenCombine(cf2, (a, b) -> a + b);
// Wait for all:
CompletableFuture.allOf(cf1, cf2, cf3).join();
```

**Future vs CompletableFuture:**
| Future | CompletableFuture |
|--------|-------------------|
| `get()` blocks | non-blocking callbacks |
| can't chain | chainable (thenApply, thenCompose) |
| can't combine | thenCombine, allOf, anyOf |
| no exception handling | exceptionally, handle |

**Interview line:** "CompletableFuture is non-blocking async with chaining, combining, and error handling — a big upgrade over Future which only lets you block on get()."

---

## 10. Other Java 8 additions

- **`forEach` on Iterable:** `list.forEach(System.out::println);`
- **`Map` enhancements:** `getOrDefault`, `putIfAbsent`, `computeIfAbsent`, `merge`, `forEach`
  ```java
  map.merge(key, 1, Integer::sum);   // count occurrences
  map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
  ```
- **`StringJoiner`:** `String.join(", ", list);`
- **Nashorn** JavaScript engine (removed later)

---

# PART B: JAVA 9 → 21 FEATURES

Modern Java. Java 21 (2023) is the current LTS. Below are the features by version, grouped.

---

## 11. `var` — Local Variable Type Inference (Java 10)

**What:** The compiler infers the type of a local variable.

```java
var list = new ArrayList<String>();   // inferred as ArrayList<String>
var count = 10;                        // int
for (var s : list) { ... }
```

**Rules:** only for local variables (not fields, method params, or return types); must be initialized.

**Interview line:** "var infers the type at compile time for local variables — less boilerplate, but the type is still static and fixed. It's not like JavaScript's dynamic var."

---

## 12. Records (Java 16)

**What:** A concise, immutable data class. Auto-generates constructor, getters, equals, hashCode, toString.

**Why:** Kills boilerplate for data-carrier classes (DTOs, value objects).

```java
public record Employee(int id, String name, double salary) {}

Employee e = new Employee(1, "Riaan", 50000);
e.name();          // auto getter (note: name(), not getName())
e.equals(other);   // auto
e.toString();      // Employee[id=1, name=Riaan, salary=50000.0]
```

**Can add validation (compact constructor):**
```java
public record Employee(int id, String name, double salary) {
    public Employee {
        if (salary < 0) throw new IllegalArgumentException("negative salary");
    }
}
```

**Interview line:** "Records are immutable data classes — one line replaces 40+ lines of boilerplate. Great for DTOs and value objects. All fields are final."

---

## 13. Sealed Classes (Java 17)

**What:** Restrict which classes can extend/implement a class or interface.

**Why:** Gives you control over the inheritance hierarchy — the compiler knows all subtypes (great with pattern matching).

```java
public sealed interface Shape permits Circle, Square, Triangle {}

public final class Circle implements Shape { }
public final class Square implements Shape { }
public final class Triangle implements Shape { }
// No other class can implement Shape
```

**Interview line:** "Sealed classes let me control exactly which types can extend a class or interface. The compiler knows the complete set of subtypes, which makes exhaustive switch/pattern matching safe."

---

## 14. Pattern Matching for `instanceof` (Java 16)

**What:** Combine `instanceof` check + cast in one step.

```java
// Before:
if (obj instanceof String) {
    String s = (String) obj;    // manual cast
    System.out.println(s.length());
}

// Java 16:
if (obj instanceof String s) {  // auto-binds to 's'
    System.out.println(s.length());
}
```

**Interview line:** "Pattern matching for instanceof removes the redundant cast — if the check passes, the variable is auto-bound and typed."

---

## 15. Switch Expressions (Java 14)

**What:** Switch that returns a value, with arrow syntax and no fall-through.

```java
// Old switch (statement, fall-through, break needed)
// New switch (expression, returns a value):
String result = switch (day) {
    case MONDAY, FRIDAY -> "Work";
    case SATURDAY, SUNDAY -> "Rest";
    default -> "Unknown";
};

// With yield for multi-line:
int num = switch (size) {
    case SMALL -> 1;
    case LARGE -> { int x = compute(); yield x; }
};
```

**Interview line:** "Switch expressions return a value, use arrow syntax with no fall-through, and can group cases. No more forgetting break statements."

---

## 16. Pattern Matching for Switch (Java 21)

**What:** Switch on the TYPE of an object, combined with sealed classes for exhaustiveness.

```java
sealed interface Shape permits Circle, Square {}
record Circle(double radius) implements Shape {}
record Square(double side) implements Shape {}

double area = switch (shape) {
    case Circle c -> Math.PI * c.radius() * c.radius();
    case Square s -> s.side() * s.side();
    // no default needed — sealed guarantees all cases covered
};
```

**With guards (when):**
```java
String size = switch (shape) {
    case Circle c when c.radius() > 10 -> "Big circle";
    case Circle c -> "Small circle";
    case Square s -> "Square";
};
```

**Interview line:** "Java 21's pattern matching for switch lets me branch on an object's type with auto-binding. Combined with sealed types and records, the compiler enforces exhaustive handling — no default needed."

---

## 17. Text Blocks (Java 15)

**What:** Multi-line string literals using triple quotes `"""`.

**Why:** Clean multi-line strings (JSON, SQL, HTML) without escaping.

```java
String json = """
    {
        "name": "Riaan",
        "role": "Developer"
    }
    """;

String sql = """
    SELECT id, name FROM employees
    WHERE salary > 50000
    """;
```

**Interview line:** "Text blocks let me write multi-line strings cleanly without \\n and escaped quotes — great for JSON, SQL, and HTML. I use them for system prompts in my Spring AI project."

---

## 18. Virtual Threads (Java 21) — BIG feature

**What:** Lightweight threads managed by the JVM (not the OS). Millions can run concurrently.

**Why:** Platform (OS) threads are heavy (~1MB each) — you can only have a few thousand. Virtual threads are cheap (~few KB) — you can have millions. Perfect for high-concurrency I/O workloads.

```java
// Create a virtual thread:
Thread.startVirtualThread(() -> System.out.println("Hi from virtual thread"));

// Executor with a virtual thread per task:
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 1_000_000; i++) {
        executor.submit(() -> { doIoWork(); });   // a million tasks, no problem
    }
}
```

**Platform vs Virtual threads:**
| Platform Thread | Virtual Thread |
|-----------------|----------------|
| Maps 1:1 to an OS thread | Many virtual threads on few OS threads |
| ~1MB stack | ~few KB |
| Thousands max | Millions |
| Blocking wastes an OS thread | Blocking parks the virtual thread, OS thread is freed |

**Interview line:** "Virtual threads (Java 21) are lightweight JVM-managed threads — you can run millions. When a virtual thread blocks on I/O, it's parked and the underlying OS thread is freed to run others. This gives the simplicity of blocking code with the scalability of reactive — a huge deal for high-concurrency servers."

**Connection to your work:** "This is an alternative to reactive/WebFlux — virtual threads let you write simple blocking-style code that still scales to massive concurrency, without the complexity of Mono/Flux."

---

## 19. Enhanced `Stream` methods (Java 9+)

```java
// takeWhile / dropWhile (Java 9)
Stream.of(1,2,3,4,1).takeWhile(n -> n < 4);   // [1,2,3]
Stream.of(1,2,3,4,1).dropWhile(n -> n < 4);   // [4,1]

// Stream.iterate with condition (Java 9)
Stream.iterate(1, n -> n <= 10, n -> n + 1).forEach(System.out::println);

// Stream.ofNullable (Java 9)
Stream.ofNullable(maybeNull);

// toList() shortcut (Java 16)
list.stream().filter(...).toList();   // instead of .collect(Collectors.toList())
```

---

## 20. Collection Factory Methods (Java 9)

**What:** Create immutable collections in one line.

```java
List<String> list = List.of("a", "b", "c");
Set<Integer> set = Set.of(1, 2, 3);
Map<String,Integer> map = Map.of("a", 1, "b", 2);
```

**Note:** These are IMMUTABLE — adding to them throws UnsupportedOperationException.

**Interview line:** "List.of/Set.of/Map.of create immutable collections concisely. They throw if you try to modify them — good for constants and defensive returns."

---

## 21. Other notable modern features

| Version | Feature | What |
|---------|---------|------|
| Java 9 | **Module System (JPMS)** | `module-info.java` — modularize large apps |
| Java 9 | **JShell** | REPL for quick Java experiments |
| Java 10 | `var` | local type inference |
| Java 11 (LTS) | `String` methods | `isBlank()`, `strip()`, `lines()`, `repeat()` |
| Java 11 | HTTP Client | modern `java.net.http.HttpClient` (async, HTTP/2) |
| Java 14 | Helpful NullPointerExceptions | NPE message says exactly which variable was null |
| Java 15 | Text Blocks | multi-line strings |
| Java 16 | Records, Pattern matching instanceof | |
| Java 17 (LTS) | Sealed classes | |
| Java 21 (LTS) | Virtual threads, Pattern matching for switch, Record patterns | |

**Java 11 String methods:**
```java
"  ".isBlank();           // true
"  hi  ".strip();          // "hi" (Unicode-aware trim)
"a\nb".lines().count();    // 2
"ab".repeat(3);            // "ababab"
```

---

## 22. Record Patterns (Java 21)

**What:** Destructure a record directly in a pattern match.

```java
record Point(int x, int y) {}

// Destructure in instanceof:
if (obj instanceof Point(int x, int y)) {
    System.out.println(x + ", " + y);   // x and y auto-extracted
}

// In switch:
String desc = switch (obj) {
    case Point(int x, int y) when x == y -> "On diagonal";
    case Point(int x, int y) -> "At " + x + "," + y;
    default -> "Not a point";
};
```

**Interview line:** "Record patterns let me destructure a record's components directly in a pattern match — combined with sealed types and switch, this is Java's answer to functional-style pattern matching."

---

# QUICK COMPARISON: Java 8 vs Java 21 mindset

| Aspect | Java 8 | Java 21 |
|--------|--------|---------|
| Functional style | Lambdas, Streams introduced | Mature + pattern matching |
| Data classes | Manual boilerplate | Records |
| Null handling | Optional | Optional + pattern matching |
| Concurrency | CompletableFuture | + Virtual threads |
| Type safety | — | Sealed classes + exhaustive switch |
| Strings | concatenation | Text blocks |
| Immutable collections | manual | List.of / Set.of / Map.of |

---

# MASTER SUMMARY TABLE

| Feature | Version | One-liner |
|---------|---------|-----------|
| Lambda | 8 | anonymous function |
| Functional interface | 8 | one abstract method = lambda target |
| Method reference | 8 | `Class::method` shorthand |
| Streams | 8 | declarative collection pipeline |
| Optional | 8 | null-safe container |
| Default methods | 8 | interface methods with body |
| java.time | 8 | immutable date/time |
| CompletableFuture | 8 | non-blocking async chaining |
| var | 10 | local type inference |
| Records | 16 | immutable data class |
| Sealed classes | 17 | restrict inheritance |
| Pattern matching instanceof | 16 | check + cast in one |
| Switch expressions | 14 | switch returns a value |
| Pattern matching switch | 21 | branch on type, exhaustive |
| Text blocks | 15 | multi-line strings |
| Virtual threads | 21 | millions of lightweight threads |
| Record patterns | 21 | destructure records |
| Collection factories | 9 | List.of/Set.of/Map.of |

---

# LIKELY INTERVIEW QUESTIONS (rapid fire)

1. **Difference between lambda and anonymous class?** → Lambda is concise, has no own `this` (refers to enclosing), works only with functional interfaces.
2. **Intermediate vs terminal stream ops?** → Intermediate are lazy and return a stream; terminal trigger execution.
3. **map vs flatMap?** → map: 1→1 transform; flatMap: flattens nested structures (Stream<List<X>> → Stream<X>).
4. **Optional.orElse vs orElseGet?** → orElse always evaluates its argument; orElseGet uses a Supplier, evaluated only if empty (lazy).
5. **Why records?** → Immutable data carriers with zero boilerplate.
6. **Virtual threads vs platform threads?** → Virtual are JVM-managed, millions possible, cheap; platform map 1:1 to OS threads.
7. **Sealed class use case?** → Controlled hierarchy + exhaustive pattern matching.
8. **Can records extend a class?** → No (they implicitly extend Record), but they CAN implement interfaces.
9. **Is var dynamic typing?** → No — it's compile-time inference; the type is fixed.
10. **Stream vs parallelStream — when parallel?** → Large data, CPU-bound, stateless operations; avoid for small/IO-bound.

---

*Master Java 8 (lambdas, streams, Optional, functional interfaces) deeply — it's asked in EVERY interview. Then layer on the Java 21 highlights: records, sealed classes, pattern matching, and virtual threads.*

---

# PART C: DEEPER DIVES & CONCEPTS OFTEN MISSED

These are the details interviewers probe after the basics.

---

## 23. Lambda — Variable Capture ("effectively final")

**What:** A lambda can use variables from its enclosing scope, but those variables must be **final or effectively final** (never reassigned after initialization).

```java
int factor = 10;                        // effectively final (never changed)
Function<Integer,Integer> multiply = x -> x * factor;   // OK

// int factor = 10; factor = 20;        // ❌ if reassigned, lambda won't compile
```

**Why:** Lambdas may outlive the method (run on another thread later). Java captures the VALUE, so it must not change — otherwise the behavior would be unpredictable.

**Interview line:** "A lambda captures enclosing variables by value, so they must be effectively final. This prevents bugs when the lambda runs later on a different thread."

**Lambda vs anonymous class — `this`:**
- In a **lambda**, `this` refers to the **enclosing class**.
- In an **anonymous class**, `this` refers to the **anonymous class instance itself**.

---

## 24. Stream — `reduce` (aggregation)

**What:** Combines all elements into a single result.

```java
// Sum with identity + accumulator
int sum = numbers.stream().reduce(0, (a, b) -> a + b);   // 0 = identity

// Without identity → returns Optional
Optional<Integer> max = numbers.stream().reduce(Integer::max);

// Concatenate strings
String all = words.stream().reduce("", (a, b) -> a + b);
```

**Interview line:** "reduce folds a stream into one value using an identity and an accumulator function — like sum, max, or concatenation. Without an identity it returns Optional in case the stream is empty."

---

## 25. Stream — `map` vs `flatMap` (very common question)

- **`map`** — transforms each element 1-to-1.
- **`flatMap`** — flattens nested structures (each element → a stream, then merged into one).

```java
// map: List<String> → List<Integer> (lengths)
List<Integer> lengths = words.stream().map(String::length).collect(toList());

// flatMap: List<List<Integer>> → flat List<Integer>
List<List<Integer>> nested = List.of(List.of(1,2), List.of(3,4));
List<Integer> flat = nested.stream()
    .flatMap(List::stream)               // flattens
    .collect(toList());                  // [1, 2, 3, 4]

// flatMap: split sentences into words
List<String> allWords = sentences.stream()
    .flatMap(s -> Arrays.stream(s.split(" ")))
    .collect(toList());
```

**Interview line:** "map is a 1-to-1 transform. flatMap is 1-to-many then flattened — I use it to turn a stream of lists into a single flat stream, or to split each sentence into words."

---

## 26. Primitive Streams (IntStream, LongStream, DoubleStream)

**What:** Specialized streams for primitives — avoid autoboxing overhead and add numeric methods.

```java
IntStream.rangeClosed(1, 5).sum();          // 15
IntStream.range(0, 5).forEach(System.out::println);   // 0..4
int[] arr = {3, 1, 2};
IntStream.of(arr).max();                    // OptionalInt
Arrays.stream(arr).average();               // OptionalDouble

// Convert: object stream → int stream
int total = employees.stream().mapToInt(Employee::getSalary).sum();

// Summary statistics in one pass
IntSummaryStatistics stats = IntStream.of(1,2,3,4).summaryStatistics();
stats.getMax(); stats.getMin(); stats.getAverage(); stats.getSum();
```

**Interview line:** "IntStream/LongStream/DoubleStream avoid boxing and add numeric ops like sum, average, and range. I use mapToInt to go from an object stream to a numeric stream."

---

## 27. `Collectors.toMap` and `groupingBy` with downstream

```java
// toMap: build a map from a stream
Map<Integer,String> idToName = employees.stream()
    .collect(Collectors.toMap(Employee::getId, Employee::getName));

// toMap with merge function (handle duplicate keys)
Map<String,Integer> merged = items.stream()
    .collect(Collectors.toMap(Item::getName, Item::getQty, (a, b) -> a + b));

// groupingBy with a downstream collector
Map<String, Long> countByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDept, Collectors.counting()));

Map<String, Double> avgSalaryByDept = employees.stream()
    .collect(Collectors.groupingBy(Employee::getDept,
             Collectors.averagingDouble(Employee::getSalary)));
```

**Interview line:** "toMap builds a map from a stream — I pass a merge function to handle duplicate keys. groupingBy can take a downstream collector like counting or averagingDouble for aggregate-per-group."

---

## 28. Functional Composition (andThen, compose)

**What:** Chain functions/predicates together.

```java
Function<Integer,Integer> times2 = x -> x * 2;
Function<Integer,Integer> plus3  = x -> x + 3;

times2.andThen(plus3).apply(5);   // (5*2)+3 = 13   (times2 first)
times2.compose(plus3).apply(5);   // (5+3)*2 = 16   (plus3 first)

// Predicate composition
Predicate<Integer> positive = n -> n > 0;
Predicate<Integer> even = n -> n % 2 == 0;
positive.and(even).test(4);   // true
positive.or(even).test(-2);   // true
positive.negate().test(-1);   // true
```

**Interview line:** "Functions compose with andThen (this first) and compose (argument first). Predicates combine with and/or/negate. This lets me build complex logic from simple reusable pieces."

---

## 29. Stream creation & other methods

```java
Stream.of(1, 2, 3);                          // from values
Arrays.stream(array);                         // from array
list.stream();                                // from collection
Stream.iterate(1, n -> n * 2).limit(5);       // infinite → limited: 1,2,4,8,16
Stream.generate(Math::random).limit(3);       // supplier-based
"abc".chars();                                // IntStream of chars

// peek — debug/side-effect in the middle (intermediate)
list.stream().peek(x -> System.out.println("Saw: " + x)).collect(toList());

// anyMatch / allMatch / noneMatch → boolean (short-circuit)
list.stream().anyMatch(x -> x > 5);
list.stream().allMatch(x -> x > 0);
```

**Interview line:** "Streams can be created from collections, arrays, values, or generators like iterate and generate. peek is for debugging mid-pipeline, and anyMatch/allMatch/noneMatch short-circuit to a boolean."

---

## 30. Try-with-resources (Java 7, enhanced Java 9)

**What:** Auto-closes resources implementing `AutoCloseable`. Java 9 allows using an already-declared final variable.

```java
// Java 7+
try (BufferedReader br = new BufferedReader(new FileReader("f.txt"))) {
    return br.readLine();
}   // br.close() called automatically

// Java 9 — can use an existing effectively-final variable
BufferedReader reader = new BufferedReader(...);
try (reader) {   // no need to re-declare
    ...
}
```

**Interview line:** "Try-with-resources auto-closes anything implementing AutoCloseable, even on exceptions — no manual finally block. Java 9 lets me use an existing final variable directly."

---

## 31. Java Module System / JPMS (Java 9)

**What:** A way to modularize large applications with explicit dependencies and encapsulation. Defined in `module-info.java`.

```java
// module-info.java
module com.myapp {
    requires java.sql;              // dependencies
    exports com.myapp.api;          // what's visible to others
}
```

**Why:** Strong encapsulation (hide internal packages), explicit dependencies, smaller runtime images (jlink).

**Interview line:** "The module system (JPMS) adds explicit dependencies and strong encapsulation above packages. Most enterprise apps still use the classpath, but it's used internally to modularize the JDK itself."

---

## 32. `orElse` vs `orElseGet` vs `orElseThrow` (Optional — common trap)

```java
// orElse — argument ALWAYS evaluated (even if value present)
String a = optional.orElse(getDefault());        // getDefault() always runs

// orElseGet — Supplier, evaluated ONLY if empty (lazy)
String b = optional.orElseGet(() -> getDefault()); // getDefault() runs only if empty

// orElseThrow — throw if empty
String c = optional.orElseThrow(() -> new NotFoundException());
```

**Interview line:** "orElse always evaluates its argument, even when the value is present — wasteful if it's expensive. orElseGet takes a Supplier and only runs it when empty. Prefer orElseGet for expensive defaults."

---

## 33. Java 12+ smaller but askable features

```java
// Collectors.teeing (Java 12) — two collectors, combine results
var result = Stream.of(1,2,3,4)
    .collect(Collectors.teeing(
        Collectors.summingInt(i -> i),      // sum
        Collectors.counting(),              // count
        (sum, count) -> sum / (double) count));  // average

// String.transform (Java 12)
String s = "hello".transform(String::toUpperCase);   // "HELLO"

// Files.mismatch, Compact Number Format, etc.
```

**Interview line:** "Java 12 added Collectors.teeing to run two collectors and merge their results in one pass — handy for computing sum and count together to get an average."

---

## 34. Deep dive: Virtual Threads vs Reactive (WebFlux) — likely for your profile

Both solve high concurrency, but differently:

| Reactive (WebFlux, Mono/Flux) | Virtual Threads (Java 21) |
|-------------------------------|---------------------------|
| Non-blocking via callbacks | Blocking code that's cheap to block |
| Complex: Mono/Flux, operators | Simple: write normal sequential code |
| Hard to debug (broken stack traces) | Easy: normal stack traces |
| Steep learning curve | Familiar imperative style |
| Great for streaming | Great for high-concurrency request handling |

**The big idea:** Virtual threads let you write **simple blocking-style code** that still scales to millions of concurrent tasks — because blocking a virtual thread is cheap (it parks and frees the OS thread).

**Interview line:** "Reactive achieves scalability by never blocking, but at the cost of complexity — Mono/Flux and broken stack traces. Virtual threads achieve similar scalability while letting you write simple, readable blocking code. In my contract analyzer I used WebFlux, but Java 21 virtual threads are now a compelling simpler alternative for high-concurrency I/O."

---

## 35. Immutability & why records/final matter (concept tie-together)

**Immutable object** = state can't change after creation. Benefits:
- **Thread-safe** by default (no shared mutable state → no race conditions)
- Safe to cache and share
- Easy to reason about

**How Java supports it:** `final` fields, no setters, records (all fields final), `List.of` (immutable collections).

**Interview line:** "Immutability means no state changes after construction — inherently thread-safe and cache-safe. Records enforce it (all fields final), and List.of gives immutable collections. This is why I keep Spring beans stateless."

---

# FINAL COMPLETENESS CHECK — every concept covered

**Java 8:** ✅ Lambdas, ✅ effectively-final capture, ✅ functional interfaces (all built-ins), ✅ method references (4 types), ✅ Streams (create/intermediate/terminal), ✅ map vs flatMap, ✅ reduce, ✅ primitive streams, ✅ collectors (toMap/groupingBy/teeing), ✅ functional composition, ✅ parallelStream, ✅ Optional (+ orElse vs orElseGet), ✅ default/static interface methods, ✅ java.time, ✅ CompletableFuture (vs Future), ✅ Map enhancements, ✅ try-with-resources.

**Java 9–21:** ✅ Modules, ✅ JShell, ✅ var, ✅ collection factories, ✅ enhanced streams (takeWhile/dropWhile/iterate/ofNullable), ✅ String methods (Java 11), ✅ HTTP client, ✅ helpful NPEs, ✅ switch expressions, ✅ text blocks, ✅ records, ✅ pattern matching instanceof, ✅ sealed classes, ✅ pattern matching switch, ✅ record patterns, ✅ virtual threads, ✅ Collectors.teeing/String.transform.

If the interviewer asks something not here, it's likely a niche/edge feature — but this covers everything commonly (and uncommonly) asked.

---

# PART D: JAVA 25 FEATURES (Latest LTS — September 2025)

Java 25 is the newest Long-Term-Support release. It finalizes several "preview" features from Java 21–24 and adds beginner-friendly simplifications. These are hot interview topics because they're brand new.

---

## 36. Compact Source Files & Instance Main Methods (finalized in Java 25)

**What:** You can now write a runnable Java program **without** the `public class` wrapper, `String[] args`, or `static`. Great for scripts and learning.

**Why:** The old "Hello World" forced beginners to understand `public static void main(String[] args)` and classes on day one. Java 25 removes that ceremony.

```java
// OLD — full ceremony
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello World");
    }
}

// JAVA 25 — compact source file, instance main, no class needed
void main() {
    System.out.println("Hello World");
}
```

**Interview line:** "Java 25 finalized compact source files and instance main methods — you can write `void main()` with no class wrapper and no static. It lowers the entry barrier and is handy for small scripts, while full classes are still used for real applications."

---

## 37. Module Import Declarations (finalized in Java 25)

**What:** Import an entire module's exported packages in one line, instead of many individual imports.

```java
// Instead of many imports:
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
// ...

// Java 25 — import the whole module's API:
import module java.base;
```

**Interview line:** "Module import declarations let me import all exported packages of a module with `import module java.base`, reducing import clutter — especially useful in scripts and quick prototypes."

---

## 38. Primitive Types in Patterns (preview, maturing in Java 25)

**What:** Pattern matching (instanceof and switch) now works with **primitive types**, not just objects.

```java
// switch on a primitive with patterns
Object obj = 42;
String result = switch (obj) {
    case Integer i when i > 100 -> "big int";
    case Integer i -> "small int";
    case String s -> "string";
    default -> "other";
};

// primitive patterns in switch over an int
int status = 2;
String label = switch (status) {
    case 1 -> "Active";
    case 2 -> "Pending";
    default -> "Unknown";
};
```

**Interview line:** "Java is extending pattern matching to primitives, so switch and instanceof patterns work uniformly across primitive and reference types."

---

## 39. Scoped Values (finalized in Java 25)

**What:** A modern, safer replacement for `ThreadLocal` — shares immutable data within a thread (and its child virtual threads) for a bounded scope.

**Why:** `ThreadLocal` is mutable, can leak memory (if not cleared), and doesn't play well with millions of virtual threads. Scoped values are immutable and auto-cleaned when the scope ends.

```java
private static final ScopedValue<String> USER = ScopedValue.newInstance();

// Bind the value only for the duration of the run() block:
ScopedValue.where(USER, "Riaan").run(() -> {
    processRequest();          // anywhere in here, USER.get() == "Riaan"
});
// After the block, the value is automatically gone (no manual cleanup)

void processRequest() {
    System.out.println("Current user: " + USER.get());
}
```

**ThreadLocal vs ScopedValue:**
| ThreadLocal | ScopedValue |
|-------------|-------------|
| Mutable | Immutable |
| Manual `remove()` (leak risk) | Auto-cleared at scope end |
| Heavy with millions of virtual threads | Lightweight, virtual-thread friendly |

**Interview line:** "Scoped values (Java 25) are an immutable, auto-cleaned alternative to ThreadLocal. You bind a value for a bounded scope, and it's automatically shared with child virtual threads and removed when the scope ends — no memory leaks, and it scales with virtual threads."

---

## 40. Structured Concurrency (finalized in Java 25)

**What:** Treats a group of related concurrent tasks as a single unit of work — if one fails, the others are cancelled; the parent waits for all. Pairs perfectly with virtual threads.

**Why:** Traditionally, if you split work into parallel tasks, managing their lifecycle (cancellation, errors, waiting) was manual and error-prone. Structured concurrency makes it clean and reliable.

```java
try (var scope = StructuredTaskScope.open()) {
    var userTask  = scope.fork(() -> fetchUser(id));      // parallel task 1
    var orderTask = scope.fork(() -> fetchOrders(id));    // parallel task 2

    scope.join();                    // wait for BOTH to finish

    // combine results
    return new Dashboard(userTask.get(), orderTask.get());
}   // if one task fails, the other is cancelled automatically
```

**Interview line:** "Structured concurrency (Java 25) groups related concurrent tasks so they succeed or fail together — the parent forks subtasks, waits with join, and if one fails the rest are cancelled. It brings order and reliability to concurrent code, especially with virtual threads. It's like a try-with-resources for concurrency."

---

## 41. Flexible Constructor Bodies (finalized in Java 25)

**What:** You can now run **validation/statements BEFORE calling `super()`** in a constructor.

**Why:** Previously `super()` or `this()` had to be the very first statement — you couldn't validate arguments first. Now you can.

```java
public class Employee extends Person {
    public Employee(String name, int age) {
        if (age < 18) {                       // ✅ validate BEFORE super() — now allowed
            throw new IllegalArgumentException("Must be adult");
        }
        super(name);                          // super call comes after validation
    }
}
```

**Interview line:** "Java 25 allows statements before super() in constructors, so I can validate or transform arguments before the parent constructor runs — previously super() had to be the first line."

---

## 42. Stable Values (preview in Java 25)

**What:** A holder for a value that's set **once** and then treated as a constant — enabling lazy, thread-safe, one-time initialization with performance like a `final` field.

**Why:** Combines the flexibility of lazy initialization with the performance/safety of `final`.

```java
private final StableValue<Logger> logger = StableValue.of();

Logger getLogger() {
    return logger.orElseSet(() -> Logger.create());   // computed once, cached, thread-safe
}
```

**Interview line:** "Stable values give lazy, thread-safe, compute-once initialization that the JVM can optimize like a final constant — safer than double-checked locking."

---

## 43. Other Java 25 highlights (quick mentions)

| Feature | What it does |
|---------|-------------|
| **Generational Shenandoah GC** | Improved low-pause garbage collector (generational mode) |
| **Ahead-of-Time (AOT) improvements** | Faster JVM startup via class loading & linking cache (Project Leyden) |
| **Key Derivation Function API** | Standard crypto API for deriving keys |
| **Vector API (still incubating)** | SIMD math for performance-critical numeric code |
| **Removal of 32-bit x86 support** | JDK focuses on modern 64-bit platforms |

---

## Java version quick reference (LTS releases)

| Version | Year | Type | Headline features |
|---------|------|------|-------------------|
| Java 8 | 2014 | LTS | Lambdas, Streams, Optional, java.time |
| Java 11 | 2018 | LTS | var, HTTP Client, String methods |
| Java 17 | 2021 | LTS | Sealed classes, records, pattern matching |
| Java 21 | 2023 | LTS | Virtual threads, pattern matching switch, record patterns |
| **Java 25** | **2025** | **LTS** | **Structured concurrency, scoped values, compact source files, flexible constructors** |

**Interview line:** "The LTS releases are 8, 11, 17, 21, and now 25. Java 25's theme is simplifying concurrency (structured concurrency + scoped values with virtual threads) and lowering the entry barrier (compact source files, instance main). It finalizes features that were in preview through Java 21–24."

---

## JAVA 25 — RAPID-FIRE Q&A

1. **What's new in Java 25?** → Structured concurrency, scoped values, compact source files & instance main, flexible constructor bodies, module import declarations — all finalized. It's the latest LTS.
2. **Scoped values vs ThreadLocal?** → Scoped values are immutable, auto-cleaned at scope end, and virtual-thread friendly; ThreadLocal is mutable and leak-prone.
3. **What is structured concurrency?** → Grouping related concurrent subtasks so they succeed/fail together — parent forks, joins, and cancels siblings on failure. Like try-with-resources for concurrency.
4. **Can you write Java without a class now?** → Yes — compact source files allow `void main()` with no class wrapper (great for scripts).
5. **What changed with constructors?** → Flexible constructor bodies allow statements (like validation) before super().
6. **Which Java versions are LTS?** → 8, 11, 17, 21, 25.

---

*Java 25 continues the modernization: virtual threads + structured concurrency + scoped values together make high-concurrency code simple AND safe. Know these — they're the freshest interview topics.*
