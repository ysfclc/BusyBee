# Fresh Start — App Observer and BusyBee

**Checkpoint date:** 2026-11-22  
**Repository:** `ysfclc/BusyBee`  
**Baseline:** `main` at `05d335794498516b7b7f2c5c3f32ecc9c6b89094`  
**Checkpoint type:** audit and product-boundary reset; documentation only

## 1. Product boundary — locked

- **BusyBee is the end-user product:** the Driver Command Centre and operating platform.
- **App Observer is a research instrument:** it observes selected Android apps, runs controlled experiments, validates capture/perception methods and produces reusable evidence, fixtures and technical findings.
- App Observer is not a driver dashboard, a BusyBee screen, or a second product that drivers should need to operate.
- Shared code is allowed only through deliberate, reviewed integration. Research UI, research storage and experimental capture mechanisms do not automatically become BusyBee production features.
- The two projects currently live in one GitHub repository. Do not create a second repository or duplicate the code merely to make the product boundary clearer.

## 2. What exists on current `main`

### App Observer Lab — preserved survey baseline

Path: `research/app-observer/android`  
Package: `com.ysfclc.appobserver.debug`  
Version: `0.1.0-survey-debug`

This is a limited Android capability survey, not the later multimodal recorder. It reports installed-app metadata, Usage Access summaries, notification metadata, read-only Accessibility event/tree-shape counts and device-level VPN transport state. It does not capture screenshots, packet contents, per-app network flows or HTTPS plaintext. Counters stay local; JSON export is user initiated; the documented build has no INTERNET permission, account login or telemetry.

The recovery record documents a successful CI build/test/signature-verification run (#44655016958) and a fresh debug APK checksum:
`4717cbf851ff3f0ba3c9adb03147b33809dff2f342c9948a28ad058dbf6f29bd`.

This is the known-good survey baseline. Preserve it. Do not silently replace it with a different research experiment.

## 3. Richer App Observer Capture — separate development line

The more capable generic recorder exists under `research/app-observer-capture` on the development lines associated with PR #22 and the reset branch. Its documented capabilities include:

- Dynamic discovery of launchable apps and one manually selected target per session.
- Read-only Accessibility structure/event capture and user-visible screenshot capture where Android permits it.
- On-device OCR/image labelling and provisional screen-concept hypotheses.
- Local evidence/session storage, optional explicit semantic rules and local learning/correction.
- Optional PCAPdroid packet-header metadata only; no raw packet/payload storage or TLS decryption.
- User-initiated evidence export/import and an explicit self-test target.

The documented stable identity is `com.ysfclc.appobserver.capture`, version `0.5.0-multimodal` / versionCode 8. PR #22 records a successful CI candidate (run #44806697182), a persistent signing certificate fingerprint and an APK hash. The same recovery/PR records explicitly say that physical Redmi acceptance, representative app coverage, classifier accuracy, battery/latency behaviour and business-outcome verification have not been completed.

**Do not confuse a successful CI build with a verified on-device research instrument.** Do not change the stable package ID or signing identity without an explicit migration plan.

## 4. Branch and PR situation

- PR [#22](https://github.com/ysfclc/BusyBee/pull/22), “Build machine-first multimodal App Observer and local learning engine”, remains open and draft. Its head is `fix/app-observer-capture-0.2.1`. Its recorded Android CI passed; the reported Vercel status failure was a deployment-rate limit and is not an Android build result.
- Branch `app-observer-intelligence-reset` contains additional work beyond the older capture PR: a generic scene-graph slice, a Rust observation/event contract, a Kotlin observation contract, an observation ledger path in BusyBee, JNI classification and canonical offer-event candidates in the Offer Desk.
- At this checkpoint, the reset branch compares as 103 commits ahead and 3 behind current `main`. It is not the same history as PR #22's head; comparing the two development lines reports divergence (104 commits unique to reset and 225 unique to the PR head, with a common ancestor at the restoration checkpoint).
- Therefore neither “merge PR #22” nor “merge the reset branch” is a safe cleanup operation. The branch histories and their code must be reconciled deliberately.
- Preserve all existing branches and PRs during this audit. No branch deletion, force-push, blind merge or history rewrite is authorised by this checkpoint.

## 5. BusyBee production baseline

Current `main` describes Phase 3 — Driver Data Mirror — as the active product phase. The existing app includes local-first imports, evidence/ledger foundations, notification-based offer capture and driver-facing surfaces. The repository explicitly does not claim verified direct Uber/Bolt API access.

The reset branch's observation-to-event work is directionally consistent with BusyBee's architecture, but its presence on a divergent branch is not proof that it is integrated, reconciled, or verified on current `main`.

BusyBee remains the canonical owner of provider-neutral driver events and product workflows. App Observer may produce observations and reproducible fixtures; it must not become the owner of BusyBee's driver-event taxonomy or production UI.

## 6. Cleanup and delivery sequence

1. **Preserve this baseline.** Keep current `main`, the survey baseline, PR #22 and the reset branch recoverable.
2. **Audit the two App Observer apps independently.** Inventory modules, permissions, persistence/export, package/signing identities, tests, CI and known device limitations. Identify obsolete experiments without deleting them.
3. **Verify the latest richer Observer source.** Run tests/build on the exact current source head and inspect privacy boundaries. Repeat on the target Redmi/Android version before making device-compatibility claims.
4. **Separate research code from BusyBee integration.** Keep capture tooling under `research/app-observer-capture`; keep the canonical observation/event contract in BusyBee's production domain. Port or cherry-pick only reviewed, necessary changes.
5. **Reconcile incrementally onto current `main`.** Use a small, reviewable integration branch/PR. Preserve existing Driver Mirror and Offer Desk data paths and migrations. No wholesale branch merge.
6. **Prove one owned synthetic offer lifecycle end to end.** Capture observed/updated/expired evidence, classify it into canonical BusyBee events, preserve provenance/unknowns/conflicts, and prove replay/idempotency and delayed/out-of-order handling. Disappearance alone must not mean expiry.
7. **Apply validated capabilities to BusyBee.** Expose useful driver outcomes in BusyBee; do not expose App Observer's research console or require drivers to install the research instrument.
8. **Keep the existing BusyBee roadmap order.** The nearby trusted-driver alert idea remains future roadmap work and is not part of this cleanup or implementation slice.

## 7. Explicit non-actions at this checkpoint

- No Android, Rust, database, permission, signing or product code changed.
- No PR was merged or closed.
- No branch was deleted, force-pushed or rewritten.
- No signing key or repository secret was accessed or changed.
- No physical-device acceptance is claimed.
- No new Observer feature or BusyBee feature was started.

## 8. Next engineering gate

Before code changes, complete the source-level audit of the reset branch against current `main`: identify the minimal production contract changes, the research-only files, the migration implications, and the exact tests required. Then implement one isolated, reversible slice and verify it on the exact branch head.
