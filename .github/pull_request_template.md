## What changed

<!-- One or two sentences. Link the ADR if this changes a recorded decision. -->

## Integration checklist

- [ ] Branched from an up-to-date `main`, and this branch is less than a day old
- [ ] `cd <service> && ./mvnw verify` passes locally
- [ ] Incomplete work is hidden (not wired up, or behind a flag), so `main` stays release-ready
- [ ] Docs updated (`README.md`, `CONTEXT.md`, `docs/adr/`) if behaviour or a decision changed

<!--
Review is post-integration where practical: merge as soon as `CI required` is green, and review
after. Open as a draft only if you want feedback before integrating.
-->
