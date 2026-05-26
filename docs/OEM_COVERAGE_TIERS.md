# OEM Coverage Tiers — What Each QA Path Actually Tests

> Source of truth for the cross-OEM testing strategy from `STRATEGY.md` §7.
> Updated 2026-05-26 when the emulator matrix CI started working.

The cross-OEM moat is the strategic asset. But each QA path covers a
different layer of "OEM behavior" — and conflating them is how the
"why doesn't my Pixel test catch Xiaomi bugs" trap happens.

---

## 🟢 Tier 1 — Emulator Matrix CI (`.github/workflows/emulator-matrix.yml`)

**What it actually is:** AOSP / Google APIs system images running on
GitHub-hosted Linux runners with KVM-accelerated x86_64 emulators,
matrix over API levels 29 / 31 / 33 / 34 / 35.

**What it catches:**
- API-level / target-SDK regressions (Android 10 → 15 behavior shifts)
- New permission gating in newer Androids (e.g., POST_NOTIFICATIONS
  on 13+, restricted background activity launches on 14+)
- App startup / inflate crashes (smoke test = install + launch + 8s
  process alive + grep FATAL EXCEPTION)
- General Compose / Kotlin compatibility on each API

**What it does NOT catch:**
- Any OEM customization: Xiaomi HyperOS, Samsung One UI, OPPO ColorOS
- Vendor service kill behavior (MIUI Optimization, Knox)
- Vendor-specific Accessibility quirks
- Real GPU/LiteRT path (emulators run swiftshader, no real OpenCL
  drivers — APK falls back to CPU)
- Battery / thermal behavior under load

**When to trust it:** every PR / push to main. It's fast (~15min per
matrix), free (GitHub Actions runners), and deterministic. Treat it
as "regression seatbelt", not "OEM coverage".

**Cost:** $0 (free on public repos)

---

## 🟡 Tier 2 — Firebase Test Lab (real devices, cloud)

**What it actually is:** Google's real-device test farm. Real Pixel,
Samsung, Xiaomi, Huawei (some models), OPPO. Includes the actual
vendor ROM + Play services on real hardware.

**What it catches (that Tier 1 misses):**
- Pixel-specific GPU driver behavior (LiteRT-LM on Tensor)
- Some Samsung One UI variants (Galaxy S22/S23/S24 supported)
- Real OpenCL stacks → catches the `libOpenCL.so missing` story
- Battery / thermal under load

**What it does NOT catch:**
- Chinese-market-only ROMs (no MIUI / HyperOS / EMUI in the test lab)
- Brand-new flagships before Firebase adds them
- Heavily-modified vendor builds

**When to trust it:** before any major release where you suspect a
new-Android-API or new-vendor regression. Free tier gives ~15 virtual
runs and ~10 physical runs per day.

**Cost:** Free tier covers most needs. Beyond that: $1/hr/device.

**Status (2026-05-26):** NOT YET integrated into PokeClaw CI. P1
backlog item: wire `gcloud firebase test android run` into the
emulator-matrix workflow's matrix-summary job for the build pushed to
main, target the 2-3 most-requested devices in open OEM issues.

---

## 🟡 Tier 3 — Samsung Remote Test Lab

**What it actually is:** Samsung's free real-device lab. Real Galaxy
A52, S22, Note, foldables — actual One UI on real Knox hardware,
controllable via browser, time-limited per session.

**What it catches (uniquely vs Tier 2):**
- One UI version variants Firebase doesn't carry (older A-series,
  newer foldables before they hit Firebase)
- Knox-affected behaviors (battery optimization, background launch
  limits, accessibility quirks)
- Real Samsung user state for #17 / #16 (Galaxy A52 5G) reproduction

**Constraints:** Account signup required (Samsung Developer Program,
free). Sessions are 30 min, then re-queue. Browser-only — no SSH or
adb over network without extra setup.

**Cost:** $0

**Status (2026-05-26):** NOT YET signed up. P1 backlog.

---

## 🟡 Tier 4 — Physical second-hand devices (Xiaomi / OPPO / realme)

**What it actually is:** Buy ~$80-150 used Xiaomi / OPPO / realme on
eBay; plug into the QA bench; run the same `scripts/emulator-smoke.sh`
plus actual chat / task / monitor flows.

**Why this tier exists:** Firebase Test Lab and Samsung RTL DO NOT
carry MIUI / HyperOS / ColorOS / RealmeUI. The open OEM issues that
matter most for PokeClaw (#42 HyperOS Accessibility kill, #23 Redmi 14
Pro UI crash, #48 Xiaomi 23013RK75C) only repro on real Xiaomi.

**What you'd buy (priority order):**
1. **Xiaomi Redmi Note 12 or 13 (~$120)** — covers #42 #48 #23 + 30%
   of real-world reports
2. **Samsung Galaxy A52 5G (~$100)** — covers #17 #16 reporters
3. **realme RMX3823 (~$100)** — covers #50 reporter
4. **OPPO Find X-series (~$200)** — broader ColorOS coverage

**Cost:** ~$200-400 one-time for 2-3 covering devices. Maintenance time
~1hr/week (charge, update, run smoke).

**Status (2026-05-26):** Pixel 8 Pro + Pixel 3 (secondhand) + Xiaomi
Redmi Note 10 Pro already on the QA bench. No Samsung, no realme, no
OPPO, no HyperOS-era Xiaomi yet.

---

## 🔴 Tier 5 — Community delegation

**What it actually is:** Users who own the OEM device file an issue +
attach a `debug-report.zip`. Author triages from the zip's `summary.txt`
(now includes ABI / RAM / OpenCL probe / backend health per #41 / #14)
plus `app_logs/`.

**Why this tier matters:** It's the ONLY tier that catches genuinely
weird vendor builds — custom ROMs (LineageOS, Magisk modules), regional
variants, root-modified phones, super-recent OEM-update breakage.

**Reporter contract** (codified in `docs/community-issue-replies.md`):
- Reporter must run the latest signed release
- Reporter must attach `debug-report.zip` from Settings → About → Share
  Debug Report
- Author replies within 24 hr (D8 in `ARCHITECTURE_DECISIONS.md`)

**Cost:** $0 + author's reply time. Round-trip per issue: 1-3 days.

**Status (2026-05-26):** 7 OEM issues currently in this loop after
v0.7.0 release. Replies posted, waiting for retest attachments.

---

## Coverage matrix — which tier catches which issue type

|                                  | T1 emu | T2 Firebase | T3 Samsung RTL | T4 physical | T5 community |
|----------------------------------|:------:|:-----------:|:--------------:|:-----------:|:------------:|
| API-level regression             | ✅      | ✅           | ✅              | partial     | ✅            |
| Compose / Kotlin inflate         | ✅      | ✅           | ✅              | ✅           | ✅            |
| Pixel GPU / LiteRT path          | ❌      | ✅           | —              | ✅           | partial      |
| Samsung One UI variants          | ❌      | partial     | ✅              | ✅           | ✅            |
| Xiaomi MIUI / HyperOS            | ❌      | ❌           | ❌              | ✅           | ✅            |
| realme / OPPO ColorOS            | ❌      | ❌           | ❌              | ✅ if bought | ✅            |
| Custom ROMs / Magisk             | ❌      | ❌           | ❌              | rare        | ✅            |
| Battery / thermal regressions    | ❌      | partial     | ✅              | ✅           | rare          |
| Brand-new flagship Android       | partial| late        | partial        | partial     | ✅            |

---

## Decision policy: which tier to add for what reason

- **Tier 1 is the baseline** — keep it green. If matrix red, no commit
  goes to main.
- **Tier 2** for the next regression that surfaces on a non-Pixel
  Firebase-supported device (Samsung S22/23/24, Xiaomi 13).
- **Tier 3** when a Samsung A-series or Note user files a repro that
  Firebase can't host.
- **Tier 4** is the moat. STRATEGY.md positions PokeClaw as the
  cross-OEM neutral mobile-agent harness; the moat is genuine only if
  Tier 4 owns at least 2-3 vendor categories.
- **Tier 5** is permanent — never replace, only feed.

---

## Cost summary

| Tier | Setup cost | Ongoing cost | Time per release |
|------|-----------:|-------------:|------------------:|
| 1    | $0         | $0           | auto (~15min)     |
| 2    | 30min      | free tier    | ~1hr config       |
| 3    | 30min      | $0           | ~1hr per repro    |
| 4    | $200-400   | ~1hr/week    | ~30min smoke      |
| 5    | $0         | author time  | ~10min per issue  |

Total for full coverage: **~$300 one-time + ~2hr/week**. That's the
price of the cross-OEM moat.
