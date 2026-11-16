---
layout: post
title: "Scheduler Threads Without a Prayer"
date: 2026-10-30
entry_type: note
subtype: diary
projects: [casehubio/casehub-ops, casehubio/casehub-desiredstate]
tags: [cdi, quarkus, reconciliation, concurrency, desired-state, deployment, architecture]
---

# Scheduler Threads Without a Prayer

The reconciliation loop connected to Podman, listed volumes, queried containers — and then threw `ContextNotActiveException` the moment it tried to persist state. Everything worked except the part that mattered.

The stack trace pointed at `ReconciliationLoop$TenantLoop.lambda$scheduleIntervalGroups$0`, which immediately told me the problem wasn't in the JPA stores themselves. Those stores are CDI beans with `@Transactional` on their methods. They work fine when called from an HTTP request or a Vert.x event loop thread. The exception said the request context wasn't active — which meant whoever was calling them was doing so from outside the CDI-managed world.

## The Thread That CDI Forgot

The reconciliation loop is the beating heart of a desired-state system. Every few seconds (or on events, or on interval-grouped timers), it reads the desired graph, reads actual state from adapters, computes a transition plan, executes it, and persists the result. Five distinct trigger paths all funnel into the same `reconcile()` method.

Here's the problem. The loop creates its own thread pool:

```java
this.scheduler = Executors.newScheduledThreadPool(poolSize, r -> {
    Thread t = new Thread(r, "reconciliation-scheduler");
    t.setDaemon(true);
    return t;
});
```

Plain `java.util.concurrent` threads. No CDI awareness. No request scope. When the scheduler fires and the callback eventually calls `JpaReconciliationStateStore.store()`, the `@Transactional` interceptor tries to obtain an `EntityManager` — and finds nothing. No request context means no `EntityManager` means no persistence.

This is a category of bug that hides well. The reconciliation loop ran perfectly with the in-memory state store. It ran perfectly with the in-memory fault count store. It only broke when real JPA implementations showed up on the classpath during end-to-end deployment verification. The gap between "unit tests pass" and "the system works" was exactly one CDI request context wide.

![CDI request context gap — scheduler threads vs managed threads](images/cdi-context-gap.svg)

## Why Not Just Slap @ActivateRequestContext On It?

The obvious answer doesn't work here. `@ActivateRequestContext` is a CDI interceptor binding — it only fires on CDI-managed beans. `ReconciliationLoop` is a plain Java class in `runtime-core`, a module that deliberately has no CDI dependency. It's created by a producer method in the Quarkus-specific `runtime` module, but the class itself knows nothing about CDI, Arc, or request contexts.

This is by design. The desired-state runtime is a generic library. It runs in Quarkus via `RuntimeBeans`, in Spring via `DesiredStateRuntimeAutoConfiguration`, and in plain unit tests via its builder. Making it CDI-aware would break two of those three.

The codebase had two precedents for this kind of problem. In the AML module, `@ActivateRequestContext` works because the annotated methods live on actual CDI beans — Quartz scheduler threads call into them and the interceptor fires. In the Agency module, `ManagedExecutor` replaces the plain executor so context propagation happens automatically. Neither pattern applied cleanly here. The loop isn't a CDI bean, and replacing `ScheduledExecutorService` with `ManagedExecutor` wouldn't help — there's no request context to propagate at scheduling time.

## The Strategy Pattern That Doesn't Know What It's Wrapping

The fix is a `Consumer<Runnable>` called `schedulerContextRunner`. The loop accepts it as a constructor parameter, defaults to `Runnable::run`, and wraps every scheduler callback through it:

```java
resyncFuture = scheduler.scheduleAtFixedRate(
    () -> schedulerContextRunner.accept(this::reconcile),
    resyncMillis, resyncMillis, TimeUnit.MILLISECONDS);
```

The `runtime-core` module never sees CDI. It just accepts a function that wraps its callbacks. The Quarkus runtime module provides the implementation:

```java
private static Consumer<Runnable> cdiRequestContextRunner() {
    return task -> {
        ManagedContext requestContext = Arc.container().requestContext();
        if (requestContext.isActive()) {
            task.run();
        } else {
            requestContext.activate();
            try { task.run(); }
            finally { requestContext.deactivate(); }
        }
    };
}
```

The `isActive()` check matters. When reconciliation fires from an event-driven path where a request context already exists, we don't want to activate a second one and deactivate it out from under the caller. The Spring configuration passes `null`, which defaults to `Runnable::run`.

## The Deeper Problem the Fix Uncovered

I fixed the CDI bug because it was blocking scaffold#83 — deploying Level 01 from the UI. With the fix in place, I went to verify that Level 01 actually works end-to-end. It doesn't. Not because of another bug, but because Level 01 has never run with real services. Ever.

The topology YAML for Level 01 declares four nodes:

```yaml
nodes:
  manifest:
    spec:
      image: casehubio/manifest:latest
  eidos:
    spec:
      image: casehubio/eidos:latest
  engine:
    spec:
      image: casehubio/engine:latest
  my-llm:
    spec:
      image: casehubio/my-llm-app:latest
```

None of these images exist. Not locally, not in GHCR, not anywhere. The topology tests run with `allow-image-fallback=true`, which substitutes `nginx:alpine` for every image reference. Four nginx containers start, respond to health checks with 200, and the test declares success. No CaseHub code runs. No LLM processes a prompt. No case engine processes a work item.

But the problem is worse than missing images. Eidos is a framework — a library for agent identity, capabilities, and disposition. You put it on your classpath. You don't deploy it as a container. Same for manifest. Same for engine. The Level 01 topology models a fictional microservices decomposition of what is actually a single application with libraries on its classpath.

![Level 01 reality — fictional decomposition vs actual deployment](images/level01-reality.svg)

## One Image, Many Personalities

The fix is architectural: build a single universal CaseHub runtime image with every module on the classpath. At deploy time, a YAML descriptor says what this instance is — which capabilities to activate, which agent identity to assume, which channels to open.

Same image, different personality. Level 01 deploys one container from `casehubio/casehub-runtime:latest` with a descriptor that says "I'm an LLM agent with engine and neocortex active." Level 03 deploys three containers from the same image with different descriptors. No per-service image builds. No coordinating four repos to produce four artifacts before you can test anything.

For now, every module wires up at startup regardless of the descriptor. That's fine — it's dev mode. Startup takes an extra second; nobody cares. The optimisation path is clear: full Quarkus augmentation once, then stamp out thin per-topology images by selecting subsets of the pre-wired artifacts. But that's a production concern, not a "can we actually test Level 01" concern.

This changes the ops testing story completely. One image build in CI, pushed once to GHCR. Every topology test across ops and scaffold consumes it. The `ImageResolver` already has the tiers — LOCAL, REGISTRY, FALLBACK — they just need a real image to resolve to instead of nginx:alpine.

The CDI context fix got me into this rabbit hole. The rabbit hole was worth more than the fix.
