# Reactive (WebFlux/Reactor) & Stream Syntax — Every Operator Explained

The methods you see chained with dots (`.map`, `.doOnNext`, `.subscribeOn`, `.then`,
`.flatMap`, `.compute...`) come from **three different worlds**. This guide groups
and explains each one with a plain-English meaning + example, so you can read and
explain any chained code.

---

## First: which "world" does each method belong to?

| World | Types | Examples of methods |
|-------|-------|---------------------|
| **Project Reactor (WebFlux)** | `Mono<T>`, `Flux<T>` | subscribeOn, doOnNext, doOnComplete, then, thenMany, switchIfEmpty, flatMapMany, defer |
| **Java 8 Streams** | `Stream<T>` | map, filter, collect, reduce, forEach |
| **Java 8 Functional / Collections** | `Function`, `Map` | apply, andThen, compute, computeIfAbsent, merge |

They all use dot-chaining, which is why they look similar — but Reactor is for
**async streams**, Streams are for **collections**, and Function/Map methods are
for **transformations/maps**.

---

# PART 1: PROJECT REACTOR (Mono / Flux) — the WebFlux operators

Remember: `Mono<T>` = 0 or 1 async value. `Flux<T>` = 0 to N async values.
**Nothing runs until something subscribes.**

---

## Threading & scheduling

### `.subscribeOn(Schedulers.boundedElastic())`
**Meaning:** "Run this whole chain on a specific thread pool."
`boundedElastic()` is a pool designed for **blocking** work (DB calls, file I/O, PDF parsing) — it keeps that blocking work OFF the precious event-loop threads.

```java
Mono.fromCallable(() -> ingestionService.ingestDocument(file))  // blocking work
    .subscribeOn(Schedulers.boundedElastic());   // runs on the blocking-safe pool
```

**Schedulers types:**
| Scheduler | Use for |
|-----------|---------|
| `boundedElastic()` | Blocking I/O (DB, files, HTTP) — up to ~10×CPU threads |
| `parallel()` | CPU-bound work (fixed threads = CPU cores) |
| `single()` | One shared thread |
| `immediate()` | Current thread |

**Interview line:** "subscribeOn tells Reactor which thread pool to run on. boundedElastic is for blocking calls so they don't freeze the event loop."

### `.publishOn(scheduler)`
**Meaning:** switches the thread for operators that come AFTER it (subscribeOn affects the whole chain; publishOn affects downstream).

---

## Creating a Mono/Flux

### `Mono.just(value)` / `Flux.just(a, b, c)`
Wrap ready values.
```java
Mono.just("hello");            // a Mono that emits "hello"
Flux.just("a", "b", "c");      // a Flux that emits a, b, c
```

### `Mono.fromCallable(() -> ...)`
Wrap a **blocking** method into a Mono (runs lazily on subscribe).
```java
Mono.fromCallable(() -> repository.findById(1));   // blocking DB call wrapped
```

### `Mono.empty()` / `Flux.empty()`
Emit nothing (completes immediately). Used for "no value" cases.

### `Flux.defer(() -> ...)`
**Meaning:** "Build the Flux lazily — only when someone subscribes." Ensures the code inside runs fresh per subscription.
```java
Flux.defer(() -> {
    List<Document> docs = vectorStore.similaritySearch(...);  // runs on subscribe, not now
    return Flux.fromIterable(docs);
});
```

---

## Transforming

### `.map(x -> ...)`
**Meaning:** transform each value **synchronously**, 1-to-1.
```java
Mono.just("hello").map(String::toUpperCase);   // → "HELLO"
```

### `.flatMap(x -> anotherMono)`
**Meaning:** transform each value into **another Mono/Flux** (async operation), then flatten.
Use when the transform itself returns a Mono/Flux (e.g., another DB/API call).
```java
userMono.flatMap(user -> orderService.findOrders(user.getId()));  // returns Mono<Orders>
```

### `.flatMapMany(x -> aFlux)`
**Meaning:** transform a **Mono** into a **Flux** (1 value → many).
```java
rateLimiterService.isAllowed(userId)          // Mono<Boolean>
    .flatMapMany(allowed -> Flux.just("token1", "token2"));  // → Flux<String>
```
(Your `RagService` uses this to go from the rate-limit check Mono into the streaming Flux.)

### `map vs flatMap (the key difference)`
- `map`: transform returns a plain value → `x -> y`
- `flatMap`: transform returns a Mono/Flux → `x -> Mono<y>` (for async chaining)

---

## Side effects (do something without changing the data)

### `.doOnNext(x -> ...)`
**Meaning:** "Do a side effect for EACH value as it passes through" — the value still flows on unchanged. Great for logging or collecting.
```java
chatClient.prompt().user(q).stream().content()
    .doOnNext(token -> responseBuilder.append(token));   // collect each token, still streams it
```

### `.doOnComplete(() -> ...)`
**Meaning:** "Run this AFTER the last value has been emitted (stream finished successfully)." Used for cleanup/caching after streaming ends.
```java
.doOnComplete(() -> {
    cacheService.cacheResponse(key, responseBuilder.toString()).subscribe();  // cache after done
});
```

### `.doOnError(err -> ...)`
Run a side effect if an error occurs (e.g., log it).

### `.doOnSubscribe(...)` / `.doFinally(...)`
Run when subscription starts / when the stream ends for ANY reason (complete, error, cancel).

**doOnNext vs map:** `map` transforms and returns a new value; `doOnNext` just does a side effect and passes the SAME value along.

---

## Combining / sequencing

### `.then(anotherMono)`
**Meaning:** "Ignore this result, then run the next Mono." Used to sequence operations where you only care that the first finished.
```java
chatHistoryService.saveMessage(..., "user", question)      // save 1
    .then(chatHistoryService.saveMessage(..., "assistant", answer))  // then save 2
```

### `.thenMany(aFlux)`
Like `then`, but continues with a **Flux** afterward.
```java
cacheService.cacheResponse(key, fallback)   // Mono<Boolean>
    .thenMany(Flux.just(fallback));         // then emit the fallback as a Flux
```

### `.switchIfEmpty(alternativeMono)`
**Meaning:** "If the source emitted NOTHING (empty), use this alternative instead." Perfect for cache-miss logic.
```java
cacheService.getCachedResponse(key)          // Mono — empty if cache miss
    .flatMapMany(cached -> Flux.just(cached)) // cache HIT → return it
    .switchIfEmpty(executeRagPipeline(...));  // cache MISS → run the pipeline
```
(This is exactly how your RagService decides cache-hit vs cache-miss.)

### `.zipWith(...)` / `Mono.zip(a, b)`
Combine multiple Monos in parallel and merge their results.

---

## Consuming / triggering

### `.subscribe()`
**Meaning:** "Actually START the pipeline now" (fire-and-forget). Without it, nothing runs.
```java
cacheService.invalidateContractCache(1L).subscribe();   // trigger it, don't wait
```

### `.block()`
**Meaning:** "Wait synchronously for the result." Turns reactive back into blocking — avoid in WebFlux request paths; OK in tests or `boundedElastic` threads.
```java
filePart.transferTo(file).block();   // wait for the file write to finish
```

### `.content()`
Specific to Spring AI's ChatClient — extracts the text `Flux<String>` from the streaming response.
```java
chatClient.prompt().user(q).stream().content();   // Flux<String> of tokens
```

---

## Filtering / error handling

### `.filter(x -> condition)`
Only pass values matching the condition (same idea as Stream filter).

### `.onErrorResume(err -> fallbackMono)`
If an error occurs, switch to a fallback instead of failing.

### `.onErrorReturn(defaultValue)`
If an error occurs, emit a default value.

### `.timeout(Duration)`
Fail if no value arrives within the given time.

---

# PART 2: JAVA 8 STREAMS — the collection operators

These operate on `Stream<T>` (collections), NOT async. (Full detail in Java8_and_Java21 doc.)

| Method | Meaning | Example |
|--------|---------|---------|
| `.filter(p)` | keep matching elements | `.filter(x -> x > 5)` |
| `.map(f)` | transform each 1-to-1 | `.map(String::length)` |
| `.flatMap(f)` | transform + flatten nested | `.flatMap(List::stream)` |
| `.sorted()` | sort | `.sorted(Comparator...)` |
| `.distinct()` | remove duplicates | |
| `.limit(n)` / `.skip(n)` | take first n / skip n | |
| `.collect(...)` | terminal — gather results | `.collect(Collectors.toList())` |
| `.reduce(...)` | terminal — fold to one value | `.reduce(0, Integer::sum)` |
| `.forEach(...)` | terminal — do for each | `.forEach(System.out::println)` |
| `.count()` | terminal — count | |
| `.anyMatch/allMatch/noneMatch` | terminal — boolean | `.anyMatch(x -> x > 5)` |
| `.findFirst()` / `.findAny()` | terminal — Optional | |

**Example from your project (joining chunks):**
```java
String context = relevantDocs.stream()
    .map(Document::getContent)                    // each doc → its text
    .collect(Collectors.joining("\n\n---\n\n"));  // join into one string
```

---

# PART 3: FUNCTIONAL INTERFACE & MAP METHODS

## Functional interface methods

### `.apply(x)`
**Meaning:** run a `Function` / `TokenTextSplitter` / transformer.
```java
Function<Integer,Integer> doubler = x -> x * 2;
doubler.apply(5);                       // 10

// In your project:
List<Document> chunks = splitter.apply(documents);   // TokenTextSplitter.apply()
```

### `.andThen(g)` / `.compose(g)`
Chain functions. `f.andThen(g)` = f first; `f.compose(g)` = g first.
```java
times2.andThen(plus3).apply(5);   // (5*2)+3 = 13
```

### `.test(x)` (Predicate) / `.accept(x)` (Consumer) / `.get()` (Supplier)
The single method of each built-in functional interface.

## Map methods (the `compute...` family you saw)

### `.computeIfAbsent(key, k -> value)`
**Meaning:** "If the key is missing, compute and insert a value; then return it." Great for building maps of lists.
```java
map.computeIfAbsent(firstLetter, k -> new ArrayList<>()).add(word);
// if key not present → create empty list, add word; if present → just add word
```

### `.compute(key, (k, v) -> newValue)`
Recompute the value for a key (whether present or not).

### `.computeIfPresent(key, (k, v) -> newValue)`
Recompute only if the key already exists.

### `.merge(key, value, (old, new) -> combined)`
**Meaning:** "Insert value if key absent; otherwise combine old + new." Classic for counting.
```java
map.merge(word, 1, Integer::sum);   // count occurrences: +1 each time
```

### `.getOrDefault(key, default)`
Return the value, or a default if the key is missing.
```java
int count = map.getOrDefault(key, 0);
```

### `.putIfAbsent(key, value)`
Insert only if the key isn't already there.

---

# THE BIG PICTURE: reading a chained reactive method

Take this real line from your `RagService` and read it operator by operator:

```java
return rateLimiterService.isAllowed(request.userId())      // Mono<Boolean>
    .flatMapMany(allowed -> {                              // Mono → Flux
        if (!allowed) return Flux.just("Rate limit exceeded.");
        return cacheService.getCachedResponse(cacheKey)    // Mono<String>, empty if miss
            .flatMapMany(cached -> Flux.just(cached))      // cache HIT → emit it
            .switchIfEmpty(executeRagPipeline(request, cacheKey)); // cache MISS → run pipeline
    });
```

**Read it as:**
1. Check rate limit (async) → get a `Mono<Boolean>`
2. `flatMapMany`: turn that into a stream of tokens
3. If not allowed → emit an error message
4. Otherwise check the cache (async)
5. If cache has a value → emit it (`flatMapMany`)
6. `switchIfEmpty`: if cache was empty → run the full RAG pipeline instead

And the pipeline itself:
```java
chatClient.prompt().system(prompt).user(question).stream().content()  // Flux<String> tokens
    .doOnNext(responseBuilder::append)          // collect each token as it streams
    .doOnComplete(() -> {                        // after the last token:
        cacheService.cacheResponse(...).subscribe();   // cache the full answer
        chatHistoryService.saveMessage(...).subscribe();
    });
```

---

# QUICK CHEAT SHEET

| You see... | It means... | World |
|-----------|-------------|-------|
| `.subscribeOn(boundedElastic())` | run on blocking-safe thread pool | Reactor |
| `.doOnNext(...)` | side effect per value, value flows on | Reactor |
| `.doOnComplete(...)` | run after stream finishes | Reactor |
| `.then(...)` / `.thenMany(...)` | sequence: after this, do that | Reactor |
| `.switchIfEmpty(...)` | if empty, use alternative | Reactor |
| `.flatMap(...)` | transform to another Mono/Flux (async) | Reactor |
| `.flatMapMany(...)` | Mono → Flux | Reactor |
| `.subscribe()` | trigger execution (fire-and-forget) | Reactor |
| `.block()` | wait synchronously for result | Reactor |
| `.defer(...)` | build lazily on subscribe | Reactor |
| `.map(...)` | transform 1-to-1 (sync) | Stream & Reactor |
| `.filter(...)` | keep matching | Stream & Reactor |
| `.collect(...)` | gather stream results | Stream |
| `.reduce(...)` | fold to one value | Stream |
| `.apply(x)` | run a Function | Functional |
| `.computeIfAbsent(...)` | insert-if-missing then return | Map |
| `.merge(k, v, fn)` | insert or combine (counting) | Map |
| `.getOrDefault(k, d)` | value or default | Map |

---

# INTERVIEW ONE-LINERS

- **"What's boundedElastic?"** → A Reactor thread pool for blocking work, keeping it off the event loop.
- **"map vs flatMap in Reactor?"** → map transforms to a plain value; flatMap transforms to another Mono/Flux for async chaining.
- **"Why doOnNext + doOnComplete?"** → doOnNext collects each streamed token; doOnComplete caches the full result after streaming ends.
- **"What's switchIfEmpty for?"** → Cache-miss fallback — if the cache Mono is empty, run the real pipeline.
- **"Why .subscribe() at the end?"** → In reactive, nothing runs until subscribed; it's fire-and-forget for background side tasks.
- **"computeIfAbsent vs merge?"** → computeIfAbsent builds a value if the key is missing (e.g., a list); merge combines old and new values (e.g., counting).

---

*These operators are just "verbs" for async streams (Reactor), collections (Stream), and maps. Once you know which world a method belongs to, the chain reads like a sentence.*
