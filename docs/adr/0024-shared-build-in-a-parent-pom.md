# 0024 — The service build is shared through a parent POM

Every service carried its own copy of the same build. The `<build>` section was **byte-identical** across
`board-service`, `notification-service` and `gateway-service` — 187 lines, same checksum — and
`identity-service` differed only by four JaCoCo excludes. All four repeated the same surefire/failsafe
split, the same seven JaCoCo executions behind the 0.85 and 0.90 gates of
[ADR 0010](0010-two-stage-ci-pipeline.md), the same `build-info` wiring and the same BOM imports.

The cost was not the duplication itself but what it did to change. One Spring Boot release opened four
pull requests against four copies of one declaration — #55, #56, #57 and #58, four CI runs and four
reviews for a single version bump — and a change to the shared coverage gate meant four edits that
nothing checked for agreement.

`trackly-shared/` holds that build once. It is a POM-only project that every service inherits from, and
this amends [ADR 0001](0001-monorepo.md), whose consequences recorded "no shared build".

## Why `relativePath` rather than a published artifact

A parent POM is a Maven artifact, so the reflex is to publish it — GitHub Packages offers a Maven registry
per repository, authenticated with `GITHUB_TOKEN` and needing no third-party system. That reflex is wrong
here, and the reason is the monorepo of ADR 0001.

Publishing makes the parent a released dependency. A change to the shared build would have to be merged
and published before any service could consume it, splitting one logical change across two pull requests
and leaving a window where the repository's own services disagree about their build. It would also put
registry credentials in every build for a file that is already on disk.

`<relativePath>../trackly-shared/pom.xml</relativePath>` reads it from the working tree instead. Nothing
is published, installed or versioned, and a change to the shared build lands together with the services it
changes, in one commit and one review. This is the same argument ADR 0001 makes for the repository as a
whole, applied one level down. Were the services ever split into separate repositories, GitHub Packages
would become the right answer and this decision would need revisiting.

It is a **parent, not an aggregator**: `trackly-shared` declares no `<modules>`. A reactor would want to
build all four services together, which is precisely what the change detection of
[ADR 0009](0009-change-detection-matrix-ci.md) exists to avoid. Each service still builds alone, from its
own directory, with its own wrapper.

## What stays per service

Inheritance is for what is genuinely common, not for everything that repeats. A service keeps its
`sonar.projectKey`, its own dependencies, and any build configuration that is actually its own:
`identity-service` overrides the JaCoCo excludes covering its configuration classes and signing-key
plumbing, and `gateway-service` keeps the Spring Cloud BOM as its only consumer.

The three database-backed services still declare their own persistence stack — Spring Data JDBC, Flyway,
PostgreSQL, Testcontainers — rather than inheriting it. Those declarations already carry no versions, so
hoisting them would save repetition at the cost of a service no longer stating what it uses, and would
hand the gateway a data stack it has no use for.

Each service now declares only its `artifactId`; `groupId` and `version` are inherited, which ties every
service to one version. That is the coupling this repository already assumes — four services, one
pipeline, released together.

## Considered options

- **Leave the duplication.** Honest about what each service uses, and no inheritance to reason about.
  Rejected: it is what produced four pull requests for one bump, and it lets the four coverage gates drift
  apart with nothing to notice.
- **Publish `trackly-shared` to GitHub Packages.** The conventional shape, and the only one that works
  across repositories. Rejected above: it splits one change into two pull requests and adds credentials to
  every build to read a file that is already checked out.
- **A root aggregator POM with `<modules>`.** Conventional Maven, and gives one command that builds
  everything. Rejected: a reactor builds all four services on every change, discarding the change
  detection of ADR 0009 and putting four Testcontainers runs on every pull request.
- **Hoist the persistence stack into a second parent too.** Would remove another thirteen repeated
  declarations from three services. Rejected: they are version-free already, so the repetition is a
  service stating its own dependencies rather than a copied build.

## Consequences

- The four service POMs fall from 1304 lines to 415; `trackly-shared` is 248. The gateway's POM is 65
  lines, down from 275.
- A shared build change is now one edit. The pipeline invariant added in
  [ADR 0021](0021-pipeline-invariants-are-checked.md) requires the parent to sit in `ci.yaml`'s path
  filters, so that one edit rebuilds all four services rather than none.
- Dependabot needs an entry for `/trackly-shared`, and it is the entry that matters: the service POMs
  declare no versions at all, so without it the shared build would freeze on whatever it was extracted
  with. The per-service `maven` entries remain, for versions a service declares for itself.
- A service can still set its own coverage numbers by overriding the plugin, as ADR 0010 assumed, but the
  numbers it inherits are now shared. Weakening them in the parent weakens all four at once, which the
  conventions checker reports against every service it affects.
- The refactor was verified by comparing each service's effective POM before and after: the resolved
  plugin configuration (406 lines, 466 for `identity-service`) and the resolved dependency sets are
  byte-identical, and `./mvnw verify` passes for all four with both coverage gates met.
