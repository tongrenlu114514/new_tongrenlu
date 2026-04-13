---
phase: 2
slug: html
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-14
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 (existing in project) |
| **Config file** | `pom.xml` (existing) |
| **Quick run command** | `mvn test -Dtest=ThbwikiServiceTest -pl tongrenlu-dao -x` |
| **Full suite command** | `mvn test -pl tongrenlu-dao -x` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `mvn test -Dtest=ThbwikiServiceTest -pl tongrenlu-dao -x`
- **After every plan wave:** Run `mvn test -pl tongrenlu-dao -x`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 60 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 2-01-01 | 01 | 1 | ORIGINAL-01.2 | T-2-01 | URL validated before fetch | unit | `mvn test -Dtest=ThbwikiServiceTest#parseTracks_* -pl tongrenlu-dao -x` | ❌ W0 | ⬜ pending |
| 2-01-02 | 01 | 1 | ORIGINAL-01.3 | T-2-02 | Null-safe parsing | unit | `mvn test -Dtest=ThbwikiServiceTest#parseOriginalSource_* -pl tongrenlu-dao -x` | ❌ W0 | ⬜ pending |
| 2-02-01 | 02 | 1 | ORIGINAL-01.2 | T-2-01 | N/A | unit | `mvn test -Dtest=ThbwikiServiceTest#parseAlbumDetail_* -pl tongrenlu-dao -x` | ❌ W0 | ⬜ pending |
| 2-03-01 | 03 | 1 | ORIGINAL-01.2 | — | N/A | manual | Admin API test | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `tongrenlu-dao/src/test/java/info/tongrenlu/service/ThbwikiServiceTest.java` — stubs for ORIGINAL-01.2, ORIGINAL-01.3
- [ ] `tongrenlu-dao/src/test/resources/thbwiki/sample-album.html` — sample HTML for testing
- [ ] `tongrenlu-dao/src/test/resources/thbwiki/sample-track-no-source.html` — edge case: no original source

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Real THBWiki page access | ORIGINAL-01.2 | Network access required | 1. Start app 2. Hit AdminThbwikiController 3. Verify response contains tracks |

*If none: "All phase behaviors have automated verification."*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 60s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending

---

## Threat References

| Threat ID | Pattern | Standard Mitigation |
|-----------|---------|---------------------|
| T-2-01 | URL injection via album URL | Validate URL matches THBWIKI_BASE_URL pattern |
| T-2-02 | Malformed HTML causing NPE | Null-check all parse results |

---

*Phase: 02-html*
*Validation created: 2026-04-14*
