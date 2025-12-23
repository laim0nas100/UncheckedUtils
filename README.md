[![](https://jitpack.io/v/laim0nas100/UncheckedUtils.svg)](https://jitpack.io/#laim0nas100/UncheckedUtils)

# UncheckedUtils – Happy-Path Programming in Java

**UncheckedUtils** is a small, zero-dependency library that lets you write clean, linear, **happy-path** code in Java — without the usual `try-catch` hell, null-check boilerplate, or checked-exception noise.

 **`SafeOpt<T>`** — a monadic container that unifies:
- Optional-like absence (`empty`)
- Error handling (as values, not exceptions)
- Lazy evaluation
- Async execution

All variants behave **identically** under the same `SafeOpt` interface. You compose chains with `map`, `flatMap`, `peek`, etc., and only deal with success or failure at the end.

## Core Idea: Happy Path First

```java
SafeOpt<String> result = SafeOpt.of(getConfigPath())           // may be null → empty
    .map(path -> readFile(path))     // throws → error
    .map(String::trim)
    .flatMap(content -> SafeOpt.ofGet(() -> parseJson(content)))
    .map(json -> json.getString("name"));

String name = result.orElse("Anonymous");  // only here do we handle absence/error
```
Drop-in Optional replacement with extra features.
If **anything** goes wrong (null, exception, validation), the chain short-circuits gracefully. No try-catch, no null checks.
Core Idea - expected exceptions should not be exceptions, but errors or error-like values and processed accordingly, otherwise we don't care how we fail. 
If we expected to not fail, but did anyway - just handle it with default value or abort execution entirely. If it's actually important how we failed, extract the exeption and process it.

## Key Features

- **No try-catch pyramids**
- **No null-check noise**
- **Exceptions as values** (expected paths) or captured automatically (unexpected bugs)
- **Lazy + async** feel like regular chains
- **Zero dependencies**
- **Java 8+** (even better with virtual threads)

### 1. SafeOpt – The Unified Container
- **Absence**: `SafeOpt.empty()` or `ofNullable(null)`
- **Error**: `SafeOpt.error(Throwable)` — exceptions become values
- **Success**: `SafeOpt.of(value)`

All operations automatically propagate empty/error states.

### 2. Factories
```java
SafeOpt.of(value)                    // non-null → present
SafeOpt.ofNullable(value)            // null → empty
SafeOpt.empty()                      
SafeOpt.error(Throwable)
SafeOpt.ofGet(() -> riskyCall())     // captures any throw
SafeOpt.ofLazy(value)                // lazy + memoized chains
SafeOpt.ofFuture(Future<T>)          // lazy Future wrapper
SafeOpt.ofAsync(value).map(val -> blockingCall(val)) // async execution, detailed below
```

### 3. Chaining (Happy Path)
```java
safeOpt
    .map(transform)                  // T → U
    .flatMap(t -> SafeOpt<U>)        // T → SafeOpt<U> (control flow!)
    .peek(consumer)                  // side effects on success
    .filter(predicate)               // absence on false
```

### 4. Terminal Operations (Short-Circuit & Extract)
```java
opt.get()                            // unchecked, throws if empty/error
opt.orElse(defaultValue)
opt.orNull()
opt.orElseThrow()                    // rethrow wrapped error
opt.ifPresent(consumer)
opt.ifError(errorConsumer)
opt.stream()                         // Stream<T> with 0 or 1 element
opt.orElseGet(supplier)              // fallback computation
```

### 5. Throws Are Welcome (For Unexpected Errors)
Inside `map`, `peek`, etc., just throw for bugs:
```java
opt.map(value -> value.getField().toUpperCase())  // NPE → becomes error automatically
```
Expected/alternative paths → use `flatMap`/`empty()`/`error()`.

### 6. Lazy Evaluation
```java
SafeOpt<Params> params = SafeOpt.ofLazy("config.json")
    .map(path -> parseExpensiveConfig(path));  // deferred + memoized

Params p1 = params.orElse(default);  // computes once
Params p2 = params.orElse(default);  // instant, cached
```

### 7. Async Execution
Async execution is integrated seamlessly into SafeOpt, allowing you to treat blocking or long-running operations as part of the happy-path chain without explicit threading or futures management. Use `SafeOpt.ofAsync(T val).map(val-> process(val)` to wrap a blocking call, and the computation runs asynchronously.

The chain remains lazy until a terminal operation, at which point it collapses (blocks if needed). Errors (exceptions, interruptions) are captured as SafeOpt errors.

#### Example: Simple Async Fetch
```java
SafeOpt<String> asyncData = SafeOpt.ofAsync("username").map(name -> fetchFromDatabase(name));  // runs async

String result = asyncData
    .map(String::trim)
    .orElse("default");  // blocks here if not done, then caches
```

- If the async task throws → becomes error.
- Subsequent terminals → instant (memoized).

#### Example: Chaining Async Operations
```java
SafeOpt<User> user = SafeOpt.ofAsync("username").map(name -> loadUserFromDB(name))
    .flatMap(u -> SafeOpt.ofAsync("").map(ignore -> enrichWithDetails(u)));  // nested async if within nesting limit, or same-thread execution

User loaded = user.orElseThrow();  // waits for both to complete
```

This runs the enrich step only if load succeeds, all async. With virtual threads this is incredibly cheap.


### 8. CancelPolicy and SafeScope – Structured Concurrency
Structured Concurrency before the official JDK realease, for earlier versions, integrated into SafeOpt framework.
`CancelPolicy` defines rules for when a scope "completes" (unified with cancellation), such as on error or after a threshold of tasks succeed. `SafeScope` manages hierarchical groups of async tasks, propagating completion/cancellation via policies.

#### CancelPolicy
- Flags: `cancelOnError`, `passError` (propagate original or generic).
- Triggers completion on conditions (e.g., N successful tasks).
- Hierarchical: propagates to child policies.

#### SafeScope
- Groups async SafeOpts with a policy.
- "Required complete" mode: completes after N tasks succeed (uses CountDownLatch).
- Awaiting: `awaitCompletion()` blocks until done (interruptible if enabled).
- Child scopes inherit policy.

#### Example: Parallel Tasks with Threshold Completion
```java
CancelPolicy policy = new CancelPolicy(true, true);  // cancel on error, pass error

SafeScope scope = new SafeScope(policy, 3));  // complete after 3 successes
    SafeOpt<Task> t1 = scope.of(1).map(v -> task(v)).chain(scope.completionListener());
    SafeOpt<Task> t2 = scope.of(2).map(v -> task(v)).chain(scope.completionListener());
    SafeOpt<Task> t3 = scope.of(3).map(v -> task(v)).chain(scope.completionListener());
    SafeOpt<Task> t4 = scope.of(4).map(v -> task(v)).chain(scope.completionListener());
    SafeOpt<Task> t5 = scope.of(5).map(v -> task(v)).chain(scope.completionListener());

// Once 3 succeed, scope completes — remaining tasks short-circuit via policy
scope.awaitCompletion();  // blocks until done

//any calls to t4 or t5 (assuming they finished last) result in either provided "completed" SafeOpt or special error (wrapped in SafeOpt)
}
```

- If 3 tasks succeed → scope marks complete, propagates to cancel/abort others cooperatively.
- Errors → propagate per policy.

This enables "good enough" parallelism without all-or-nothing waits.
