# 0025 — Formatting is gated in both languages: prettier for the client, Checkstyle for the services

Every property of this codebase that matters is gated rather than reported — coverage by JaCoCo
([ADR 0010](0010-two-stage-ci-pipeline.md)), quality and security by SonarCloud
([ADR 0019](0019-ci-based-sonar-analysis-per-module.md)), the pipeline's own wiring by a container action
([ADR 0021](0021-pipeline-invariants-are-checked.md)). Layout was the exception in both languages, and in
both it failed in the same shape: a convention that existed on paper and nothing that checked it.

The client had a `.prettierrc` asking for 100 columns from the day the Angular CLI scaffolded the project,
prettier itself sitting in `devDependencies`, and nothing that ever invoked it — no script, no CI step, no
shared editor hook. **19 of 56 files** were formatted at prettier's default 80 columns instead. The four
Java services had not even that: no ruleset, no plugin, and **553 violations** of the ruleset they were
about to adopt — star imports in some test classes and explicit ones in others, import blocks ordered by
whichever IDE last touched the file, lines running past any width a side-by-side diff can show.

Both are now gated. `npm run format:check` is a step of the client's pipeline
([`client-ci.yaml`](../../.github/workflows/client-ci.yaml)); `maven-checkstyle-plugin` runs in the
`validate` phase of the build all four services inherit
([ADR 0024](0024-shared-build-in-a-parent-pom.md)). A pull request that misformats either language fails
before its tests are considered.

## Why this is gated at all, when SonarCloud is already there

SonarCloud analyses every module on every pull request and blocks the merge, so the reflex is to leave
style to it. It does not close this gap, for two reasons.

Its Java and TypeScript rules are about defects and maintainability — a caught exception that is
swallowed, a method that is too complex, a cognitive-complexity budget. Layout is deliberately not its
subject, and the checks that come closest are advisory.

More decisively, its verdict is computed on **new code**. A file nobody has touched since the last
analysis is never re-judged. Style is precisely the property where the untouched files matter, because
they are what the next file gets written to look like: the 19 client files at 80 columns and the 330
misordered Java import blocks were all in code that no quality gate had any reason to revisit.

A gate on style has to run over the whole tree, every time, which is what both of these do.

## Why a formatter for one language and a linter for the other

The two tools are not the same kind of instrument, and the asymmetry is deliberate rather than historical.

**Prettier is a formatter.** It has one canonical output per input, `--write` produces it, and `--check`
asks whether the file already matches. There is no diagnostic to read and nothing to argue about: the
scripts come in a pair on purpose, so the command that gates a pull request (`format:check`) is the same
one a developer runs locally to fix it (`format`).

**Checkstyle is a linter.** It reports violations and leaves the edit to the author. For Java that is the
only workable choice here, and the reason is that Java's canonical formatter cannot be configured.
`google-java-format` — the tool Spotless would drive — emits 2-space, 100-column Google style and exposes
no options; its AOSP variant is 4-space but still 100 columns, and neither can be tuned. Adopting it means
reformatting every Java file in the monorepo to a shape no IntelliJ in this project produces by default.
Measured rather than assumed: stock `google_checks` reports **3695 violations** on this codebase, of which
**2840 are `Indentation`** and 473 `LineLength`. That is not a measure of how badly the code is written; it
is the whole repository disagreeing with Google about tab width while agreeing with itself.

Checkstyle can be pointed at the style this codebase already has. A formatter that cannot is a
whole-repository rewrite wearing the clothes of a linting decision.

Checkstyle also sees things no formatter does. Of the 553 violations, **36 are not layout at all** — star
imports, an abbreviation used as a word in a name, a lambda parameter named after its type, a variable
declared far from its first use. A formatter would have rewrapped every one of those files and left all 36
in place.

## What each tool is worth here

| Tool | Kind | What it decides | Why it is or is not used |
|---|---|---|---|
| **prettier** | formatter | TypeScript, HTML, JSON, Markdown layout | Used. Already the client's committed convention, already a dependency, one canonical output, and `--check`/`--write` from one config |
| **Checkstyle** | linter | Java layout, imports, naming, declaration distance | Used. The only Java tool that can be pointed at the style already in the tree, and it catches 36 non-layout violations a formatter cannot see |
| **Spotless + `google-java-format`** | formatter | Java layout | Rejected. Unconfigurable output: 2-space/100-column, which is the 3695-violation rewrite above. Also mutates sources during the build |
| **ESLint** | linter | TypeScript correctness and style | Not added. Prettier settles layout, and its stylistic rules are deprecated in favour of exactly that. A second tool for the same property is a second config to disagree with the first |
| **SonarCloud** | analyser | defects, complexity, security | Already used, and not a substitute: no layout rules, and new-code scope leaves old files unjudged |
| **`.editorconfig`** | convention | whitespace basics | Not sufficient. IDEs honour it, nothing checks it, and that is the state this ADR exists to leave |

## Why the Checkstyle ruleset is extracted from the Checkstyle jar

`trackly-shared/checkstyle.xml` is `google_checks.xml` *as shipped inside the pinned Checkstyle 14.0.0
artifact*, not a copy fetched from the Checkstyle repository and not a file written by hand.

A hand-written ruleset is a fifth thing to maintain: every Checkstyle release adds checks and renames
properties, and a ruleset nobody upstream maintains slowly becomes a list of the rules that happened to be
interesting on the day it was written. Downloading `google_checks.xml` at build time avoids that but adds a
network dependency to the first phase of the build and lets the ruleset and the engine that interprets it
move independently — a config from a later release can name a property the pinned engine does not know.

Taking it out of the jar makes both impossible. The ruleset and the engine are one pinned artifact, so they
cannot disagree, and Dependabot's `/trackly-shared` entry proposes them together as a single reviewable
version bump. This is the same argument prettier gets for free by being a dependency with its config in
the repository.

## Why two of its checks are retuned

Only two settings differ from Google's, and both are measurements of this codebase rather than preferences:

| Check         | Google | Here |
|---------------|--------|------|
| `LineLength`  | 100    | 120  |
| `Indentation` | 2/4    | 4/8  |

Retuned to what IntelliJ's stock formatter already produces here (4-space basic offset, 8-space
continuation) and to a width a review diff can hold, the same ruleset reports **553** violations instead of
3695, and not one of them is `Indentation`. Those 553 are the real drift, and they are what this branch
fixes.

The two languages therefore sit at different widths — prettier at 100 columns, Checkstyle at 120. That is
not tidy, and the alternative was worse in both directions: narrowing Java to 100 adds 302 more
`LineLength` violations to fix for no reader's benefit, and widening TypeScript to 120 means reformatting a
client that is already formatted, to settle a number nobody had complained about.

## What the 553 were

| Rule                            | Count |
|---------------------------------|-------|
| `CustomImportOrder`             |   330 |
| `LineLength`                    |   171 |
| `AvoidStarImport`               |    16 |
| `AbbreviationAsWordInName`      |    16 |
| `TextBlockGoogleStyleFormatting`|    14 |
| `VariableDeclarationUsageDistance`, `EmptyLineSeparator`, `LambdaParameterName`, `GoogleMethodName` | 6 |

By service: 206 in `board-service`, 164 in `notification-service`, 106 in `identity-service`, 77 in
`gateway-service` — 77 Java files, fixed one service per commit so each is reviewable on its own.

Not every fix is pure layout, and the exceptions are worth naming because they change source that reads
like behaviour. `AvoidStarImport` expanded 16 wildcard imports into explicit ones.
`AbbreviationAsWordInName` renamed 16 test methods, each by dropping an English article that had become a
second consecutive capital — `countsSegmentsOfANestedPrefix` to `countsSegmentsOfNestedPrefix`.
`LineLength` rewrote 175 `@DisplayName` strings, which are long by design, into `\`-continued text blocks;
their values were compared before and after under the incidental-whitespace rules of JLS 3.10.6 and are
unchanged. No production behaviour changes, and the same 161 unit tests pass before and after the sweep,
with `board-service`'s 20 Testcontainers integration tests and both coverage gates green on top.

## Two Checkstyle defaults that would have made the gate pass on everything

`google_checks` is written to be usable as a report as well as a gate, and both of its accommodations for
that are traps. This is the one respect in which the linter is more dangerous than the formatter: prettier
either matches or does not, while a misconfigured linter has a third state — running, printing, and
passing.

Every module in the ruleset inherits `severity` from `${org.checkstyle.google.severity}`, whose declared
default is `warning`. The plugin fails a build on violations at or above `violationSeverity`, so a ruleset
left at its default emits all 553 violations to the console and exits zero. `<propertyExpansion>` forces
`org.checkstyle.google.severity=error`, and `<violationSeverity>error</violationSeverity>` is what acts on
it.

Its suppression hook is likewise named `org.checkstyle.google.suppressionfilter.config` rather than the
plugin's own default property, so `<suppressionsFileExpression>` points at that name explicitly. Without
it the suppressions file is loaded into a property the ruleset never reads.

Both failures are silent and indistinguishable from a passing build, which is why the next section exists.

## Why the conventions checker asserts the Java gate

By the argument of [ADR 0021](0021-pipeline-invariants-are-checked.md), a gate that can be weakened
without anything failing is not yet a gate. Checkstyle has four such weakenings — remove the plugin, drop
the `check` goal, drop `violationSeverity`, or drop the `propertyExpansion` that lifts the ruleset out of
warning severity — and each produces a green pipeline that gates nothing.

`.github/actions/pipeline-conventions/check.py` now asserts all four against the inherited POM chain, the
same way it asserts the JaCoCo minimum. The severity assertion is written against the ruleset rather than
against a property name: it reads the `configLocation` file, finds every `severity` deferred to a `${...}`
property, and requires the POM to expand each to `error`. That keeps working if the ruleset is ever
replaced with one that names its property something else.

It was verified the way ADR 0021 describes, against five deliberately broken copies of the repository —
plugin deleted, `check` goal unbound, `violationSeverity` removed, `propertyExpansion` removed, and the
ruleset moved out from under `configLocation`. Each is reported against all four services by name, and the
unmodified repository reports nothing.

The client's gate needs no such assertion: `format:check` is a visible step in `client-ci.yaml`, and
deleting a step is a diff a reviewer reads, not a default that quietly reasserts itself.

## Why Checkstyle binds to `validate`

The plugin's `check` goal defaults to the `verify` phase, which in this pipeline is the integration stage —
the slow one, behind Testcontainers. Bound to `validate` instead, style fails before `compile` runs, so
`./mvnw package` rejects a misformatted file in about two seconds and CI's commit stage (ADR 0010) sees it
in the order a developer does. No workflow changed, because the commit stage already runs `./mvnw package`.

The client has no phases to bind to, so its check is a step of its own — the same gate, expressed in the
only place npm offers.

## Considered options

- **Leave style to review.** No tooling, no build cost. Rejected: it is what produced 19 unformatted client
  files and 553 Java violations while the conventions were written down the whole time. Layout is also the
  worst possible use of a human reviewer's attention.
- **Leave style to SonarCloud.** Already in the pipeline, already blocking. Rejected above: no layout rules,
  and new-code scope never revisits the files that set the house style.
- **Spotless with `google-java-format` for Java too, for symmetry with prettier.** Rejected: its output is
  unconfigurable 2-space/100-column Google style — the 3695-violation rewrite, arrived at from the other
  direction — and it mutates sources during the build, putting unrelated lines in every diff.
- **Write a minimal Checkstyle ruleset naming only the checks we want.** Smaller, easier to defend line by
  line. Rejected: another file to maintain, frozen at whatever seemed interesting the day it was written,
  while `google_checks` grows with the engine it ships in.
- **Adopt stock `google_checks` unchanged.** No local decisions to justify. Rejected on the 3695: 2840 of
  them are one disagreement about tab width, restated once per indented line.
- **Add ESLint alongside prettier.** The conventional Angular pairing. Rejected: prettier settles layout and
  ESLint's stylistic rules are deprecated in favour of it, so the overlap would be two configs with one
  subject. Its non-stylistic value is real but overlaps SonarCloud, which is already gating.
- **Introduce Checkstyle at warning severity and tighten later.** The usual way to bring a linter to an
  existing codebase. Rejected: this repository has no other advisory gate, and "later" has no owner. The
  cost of doing it at once is bounded and now paid.

## Consequences

- Both languages fail a pull request on formatting. Java fails inside `./mvnw package` and `./mvnw verify`,
  before compiling, with no new CI job; TypeScript fails in the client pipeline's `Check formatting` step.
- The two are fixed differently, and this is the part a contributor has to know: `npm run format` fixes the
  client in place, while Checkstyle only reports and the edit is yours.
- Import order is the one place IntelliJ's defaults disagree with the Java gate. `CustomImportOrder` wants
  static imports first, then every other import in one alphabetically sorted group with no blank lines
  inside it; IntelliJ ships a layout that separates `java` and `javax` into groups of their own, which is
  where 330 of the 553 violations came from. A developer who has not changed that setting will keep
  producing it, and the gate will keep catching it in two seconds.
- Java test sources are gated too (`includeTestSourceDirectory`), at the same 120 columns as main sources.
  This is the intrusive part: a `@DisplayName` written as a Given/When/Then sentence does not fit, so 175
  of them are now `\`-continued text blocks. The alternative was to suppress `LineLength` under `src/test`,
  leaving the longest lines in the repository ungated to keep the most readable annotations tidy.
- `checkstyle-suppressions.xml` disables `MissingJavadocType` and `MissingJavadocMethod`. `google_checks`
  demands Javadoc on every type and public method, which is the opposite of the convention this repository
  runs on: rationale belongs in these ADRs and in commit messages, where it can be read in full and dated,
  not in a comment above a method that will outlive it.
- The Java gate is inherited, so weakening it weakens all four services at once — and the conventions
  checker reports that against each of them by name, exactly as ADR 0024 records for the coverage minimum.
- Two more version bumps now change how the repository judges itself: `prettier` in the client's
  `package.json` and `checkstyle` in `trackly-shared`. Both are Dependabot-managed, and a bump to either
  can turn a green tree red on style alone.
