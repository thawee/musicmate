# MusicMate product strategy

Date: 2026-10-19. Horizon: the next 90 days. Status: proposed strategy for review.

Owner-confirmed priority: reliable core and repeat usage. Capacity: one developer.

## Strategic choice

Make MusicMate a dependable daily player for people who keep music files on an Android phone and play them on a DLNA music player. Build confidence through reliable playback, accurate output state, and useful library information. Expand acquisition after people demonstrate that they return without developer assistance.

Suggested positioning: **Your music collection, understood and ready to play on your hi-fi.**

### Evidence and assumptions

- The [README](../README.md) describes an Android 14+ player, library manager, DLNA server and controller, USB output, quality analysis, and tag editing. These are existing capabilities, with device-dependent limits.
- Previous owner decisions in the [launch plan](../tasks/todo.md#google-play-launch-plan-2026-10-15-from-the-product-strategy-canvas) establish Apache 2.0, a free GitHub build with all features, and a free Play build with a one-time Pro purchase. The recorded price range is US$5–10, a planning decision rather than a verified live selling price.
- The [compatibility register](RENDERERS.md) currently lists one renderer, HiBy R3, with several results still unspecified. Marketing examples naming other brands do not establish tested compatibility.
- Recent [release notes](../CHANGELOG.md) record problems with initial volume, queue persistence, playback controls, and layout. The owner's HiBy report is direct evidence of a reliability problem. It does not establish how often other customers encounter that problem.
- Downloads, active users, retention, conversion, support cost, marketing budget, and production Play availability have not been established in this review. The strategy assumes limited marketing spend and uses a small voluntary research cohort.

The existing launch checklist remains the distribution reference. This strategy sets the order of work around reliable repeat use. Older implementation notes in that checklist require checking against current code before reuse.

## 1. Vision

People can enjoy the music they own without repeatedly troubleshooting their player, network, or library.

MusicMate helps listeners understand their files, maintain their collection, and play it on equipment they already have. Its values are dependable operation, honest audio information, ownership of files, and control over personal data.

Success means a listener returns to play another album because the previous session worked. A quality report creates interest; dependable listening creates a habit.

## 2. Market segments

Define the audience by the job people need to complete.

| Priority | Situation and job | Desired outcome | Constraints |
|---|---|---|---|
| First | "My music is on my Android phone. I want to play it on my DLNA player without setting up another server." | Find an album, choose the right output, and listen through the queue with predictable controls. | Android 14+, local storage, network discovery, renderer differences, and Android background behavior. |
| Second | "My collection has inconsistent tags and uncertain file quality. I want to fix and understand it without losing files." | Browse accurate metadata, inspect quality clues, and make edits that survive a restart. | Large libraries, unusual formats, storage permissions, and the cost of a destructive edit. |
| Maintain existing support | "I listen through a USB DAC or Bluetooth and want my collection and output information in one place." | Reliable existing playback paths and clear statements about the active output. | Phone, Android, DAC, and codec differences. Additional driver and device coverage can consume the developer's capacity. |

Start with the first segment because it matches the recorded product direction and the owner’s real setup. Limit the initial promise to tested combinations. The second segment supplies useful library improvements and a reason to buy Pro. Preserve existing USB and Bluetooth behavior while deferring a campaign to compete on the broadest hardware coverage.

The first segment remains a hypothesis about demand. Existing users of a NAS or a renderer's own app may have little reason to switch.

### Competitive context

These are publisher-described capabilities checked on 2026-10-19, not independent performance comparisons.

| Alternative | Established offer | Consequence for MusicMate |
|---|---|---|
| BubbleUPnP | Plays local and network media to renderers and includes a phone DLNA media server. | "Your phone is a music server" already has direct competition. Test whether MusicMate's combined library and listening workflow earns a preference. [Publisher](https://bubblesoftapps.com/bubbleupnp/) |
| USB Audio Player PRO | Uses a custom USB audio driver and supports local files and UPnP sources. | Broad USB driver coverage would be an expensive primary contest for one developer. [Publisher](https://www.extreamsd.com/index.php/products/usb-audio-player-pro) |
| Poweramp | Combines a local music library, sound controls, customization, and tag editing. | A large feature list or visual customization alone offers little differentiation. [Publisher](https://powerampapp.com/) |
| Symfonium | Combines local, server, NAS, and cloud libraries with USB and network outputs. | A unified library and output picker also has competition. Favor a focused, well-tested journey over matching every source and platform. [Publisher](https://www.symfonium.app/) |

The opportunity is an inference: a focused workflow may serve some collection owners better. None of these sources proves that MusicMate has better reliability, sound, or usability.

## 3. Relative costs

Compete on specialized value at an accessible one-time price. Preserve the agreed free core and Pro model.

Local files avoid the need to operate a hosted music catalog. Open source supports inspection and contributions. Neither removes the cost of Android maintenance, hardware testing, storage failures, and support.

No competitor cost data is available, so MusicMate cannot claim a lower total cost position. Its practical cost advantage must come from limiting the combinations it promises to support and reusing verified fixes.

Use this weekly allocation as an adjustable planning budget for a five-day week:

| Work | Capacity |
|---|---|
| Diagnose and fix failures in the core listening journey | 2.5 days |
| Reproduce those failures and verify release builds on supported setups | 1 day |
| Observe testers and resolve support reports | 1 day |
| Remove the largest measured browsing or onboarding obstacle | 0.5 day |

Keep one implementation task active at a time. An urgent playback or file-integrity failure takes priority over the planned improvement. Recruitment runs in small batches to keep support within the available day.

## 4. Value propositions

| Segment | Before | How MusicMate helps | After | Alternatives |
|---|---|---|---|---|
| Phone collection to home player | The listener switches apps or troubleshoots a connection before enjoying an album. | Serve phone files, browse the collection, select a tested renderer, and present known or unknown device state accurately. | The listener starts a session and completes it with predictable controls. | BubbleUPnP, the renderer's own app, copying files to the player, or a separate server. |
| Collection maintenance | The owner cannot easily find albums, trust tags, or interpret a claimed high-resolution file. | Show format and DR information, explain quality-analysis evidence, and provide safe tag and organization tools. | The collection becomes easier to browse and maintain, with visible limits on what analysis can establish. | Poweramp's editing tools, a desktop tag editor, a desktop spectrum tool, or manual folders. |
| Existing USB and Bluetooth listening | The listener uses separate tools for collection information and playback. | Maintain supported output paths and show the source and active route clearly. | Existing compatible setups remain convenient to use. | USB Audio Player PRO, Poweramp, Symfonium, or another established player. |

Quality analysis must describe evidence and uncertainty. The [user guide](USER_GUIDE.md#7-song-details-and-tags) explains that an absent spectral cutoff does not prove a file's provenance. Avoid promises to certify a recording or guarantee audible improvement.

## 5. Trade-offs

For these 90 days, defer new streaming-service integrations, cloud synchronization, desktop or iOS clients, additional casting protocols, and a new USB driver program. These multiply dependencies and test combinations before the core journey is dependable.

Defer visual redesigns that do not solve an observed task failure. Fix inaccessible controls, unreadable state, and confusing actions when they prevent listening or maintenance.

Publish compatibility by tested model, firmware, format, and operation. Add at most one further renderer family during the period if a tester or borrowed device makes real verification possible.

Keep quality analysis optional during listening. A large initial analysis job must not prevent a listener from finding and playing an album.

Preserve file portability, the free listening core, and the recorded open-source model. Avoid expanding the Pro boundary during the retention study. These choices keep the research focused on whether the product is useful and dependable.

## 6. Key metrics

### North Star Metric: weekly successful repeat listeners

Count people who complete at least three successful MusicMate listening sessions across at least two days in a rolling seven-day period.

A qualifying session includes at least ten minutes of intended listening, begins on the output the person chose, and has no app-caused interruption requiring a restart or another app. This definition is a proposed measurement rule, not an existing instrumented capability.

For this quarter, evaluate the primary cohort on the tested phone-to-DLNA journey. Record local, USB, and Bluetooth listening separately so those sessions cannot conceal failures in the chosen workflow.

For the first 90 days, measure it in a voluntary cohort through brief weekly check-ins. Report both the count and the cohort size. A renderer's reported position or successful network response alone does not establish audible playback. Mark missing confirmations as unknown and report the response count.

### OMTM: week-four retained successful listeners

Of the first ten people who install MusicMate to use the primary workflow, count how many complete successful sessions on at least two days during days 22–28 after installation.

Proposed learning target: at least six of ten. Keep failed activations in the original denominator. Missing follow-up counts as unconfirmed, not retained. This small cohort provides a decision signal, not an estimate of market-wide retention.

Use three supporting measures to explain the result:

- Activation: how many of the same ten complete their first successful session within 24 hours, with assisted activations identified separately.
- Reliability: failed sessions divided by all reported session attempts, with counts by cause and device combination.
- Effort: developer support hours per activated tester and the number of sessions that require developer intervention.

Record any unintended volume increase, incorrect display of a known volume, crash, lost queue state, or damaged file. A reproducible severe failure in the supported journey blocks the affected release until fixed and checked on the signed APK.

The current [Play listing](../app/src/play/play/listings/en-US/full-description.txt) promises no analytics. Start with voluntary research and user-shared diagnostics. Product tracking or automatic uploads would require a separate product and privacy decision.

## 7. Growth

Use product-led growth through demonstrated usefulness and referrals. During this period, acquisition serves research rather than download targets.

Recruit ten primary-segment users through existing contacts and a small number of relevant renderer communities. Offer a short setup guide, the exact tested combinations, and a clear way to report a failed session.

Use an existing quality-report screenshot as a secondary discovery experiment. Measure whether interested people also return to listen. Interest in a one-time file check alone does not validate the daily-player strategy.

Expand one channel at a time after the week-four retention target is met and severe failures are resolved. Keep paid acquisition deferred until conversion, retention, and support effort are measured. Follow the existing launch checklist for the Play release, with its account and distribution requirements verified at execution time.

### Unit economics

The agreed Pro price range is US$5–10. Exact pricing and regional availability remain unverified. A one-time purchase creates no recurring subscription revenue.

Evaluate sustainability using actual cash receipts and developer time:

`Contribution per paid customer = receipts after platform deductions and refunds − variable support and service costs`

`Period surplus = total paid-customer contribution − fixed operating costs − the owner's chosen value for development time`

Include support for free users when assessing total costs. Track voluntary GitHub users and Play buyers separately where the information is available. Because the GitHub build includes Pro features, some users will choose it permanently. Do not assume every retained user becomes a paying customer or that free usage has no cost.

## 8. Capabilities

| Need | Build or maintain | Use others for | Evidence of readiness |
|---|---|---|---|
| Dependable listening | Clear playback ownership, honest output state, queue persistence, discovery recovery, and bounded volume commands. | Volunteer testers with their own renderers. | Repeatable signed-APK checks of the supported journey and recorded failed-session causes. |
| Safe collection maintenance | Permission recovery, reliable edits, file recovery, and persistent metadata. | Existing tag libraries and MusicBrainz lookup services. | Edits survive restart, and interrupted operations preserve recoverable files. |
| Credible quality information | Explain each result and its uncertainty. Maintain a reference file set. | Known reference recordings and informed tester review. | Reports do not claim provenance from insufficient evidence. |
| Sustainable releases | A short release checklist and regression checks linked to real failures. | Existing build tools and distribution services. | A reproducible build is installed and exercised before expanding its audience. |
| Customer understanding | Short interviews, session diaries, and a weekly failure review. | Participants who consent to the study. | Retention, activation, and support effort have explicit counts and denominators. |

The developer owns all five areas. No hardware partnership, additional hire, or new cloud service is assumed. Contributions are useful when they reduce a demonstrated bottleneck without adding more review work than they save.

## 9. Can't/won't: defensibility

MusicMate has no demonstrated strong moat today. Its Apache 2.0 code can be copied, and competitors already cover much of the feature set.

The assets worth accumulating are device-specific regression fixtures, reproducible failure reports, accurate compatibility records, and trust earned through dependable releases. These lower future support effort and help users decide whether their setup is supported. They remain copyable; execution and maintenance are the advantage.

There is no established network effect. More compatibility reports can improve coverage, but that does not create an exclusive network. Portable tags and files also limit switching costs by design.

Competitors can reproduce successful features. MusicMate can choose a narrower collection-to-player workflow and spend its limited capacity making that experience dependable. Whether customers prefer that focus must be tested.

## 90-day sequence

| Period | Deliverable | Decision at the end |
|---|---|---|
| Days 1–30 | Choose the exact supported phone and renderer combinations. Recheck initial connection, volume, pause, seek, next track, screen-off playback, reconnect, and queue recovery on the signed release. Recruit the ten-person cohort and capture a baseline. | If severe failures remain, continue stabilization and pause wider recruitment. Record who can activate without help. |
| Days 31–60 | Fix the most frequent supported-workflow failure and the largest observed setup obstacle, one at a time. Complete the cohort's four-week follow-up and review why participants return or leave. | At six confirmed retained listeners out of ten, prepare a small expansion. Below that, investigate each failed return before broadening scope. |
| Days 61–90 | Repeat the same journey with a second small cohort if the first passes. Add one renderer family only when verification is available. Update compatibility guidance and test the best acquisition message. | Expand distribution if repeat use persists and support fits the weekly budget. If reliable users still do not return, reconsider the first segment or the primary job. |

The thresholds are proposed decision rules. Review them after the baseline, before inspecting later results, to avoid redefining success around the outcome.

## Critical hypotheses and inexpensive experiments

| Hypothesis | Experiment | Evidence to proceed or change direction |
|---|---|---|
| Phone-owned files plus a DLNA player is a recurring problem worth solving. | Ask five people in that situation to show their last listening setup and explain the last failure. | At least three describe a recurring problem and agree to try MusicMate with their own files. Otherwise revisit the segment before expanding it. |
| Reliability is the main barrier to repeat use. | Observe first use and collect four weeks of short session reports. | Failure reports fall and return usage improves. If technically successful users still leave, investigate navigation, value, and existing alternatives. |
| The combined workflow gives a reason to choose MusicMate. | Have five testers attempt the same album-to-renderer task in MusicMate and their usual tool. | Record completion, intervention, and stated preference. At least three voluntarily choose MusicMate for a later session; an attractive demonstration alone does not pass. |
| Quality reports bring people who also want to listen. | Show an existing report screenshot and a plain playback walkthrough to small comparable groups through one channel. | Compare qualified tester sign-ups and later listening. If report interest produces no listening, treat analysis as a separate job to investigate. |
| One developer can maintain the supported promise. | Log support and rework time for four weeks. | Support fits the allocated day per week. If it does not, narrow supported combinations or reduce the next recruitment batch. |
| Retained users will support the one-time Pro model despite the free GitHub build. | Show the planned offer to five retained testers and record objections. Observe voluntary purchases only when a verified purchase path is available. | Actual purchases and measured support costs can inform economics. Stated purchase intent alone leaves revenue unvalidated. |

These counts are deliberately small learning gates. They are not market-size estimates, statistical proof, or commitments to commercial performance.

## Coherence check and review

The first segment limits the hardware promise. That limit makes meaningful device testing possible for one developer. Reliable sessions make repeat use measurable. Repeat use supplies a basis for referrals and Pro support. The free core and open license preserve the existing product values while increasing the need for low support costs.

The weakest assumption is that people prefer this focused workflow enough to return when established alternatives exist. The cohort and comparison tasks test that assumption directly.

Review at days 30, 60, and 90. Bring session attempts, failures, activation help, retained participants, support time, and reasons for leaving. Choose the next change from that evidence.
