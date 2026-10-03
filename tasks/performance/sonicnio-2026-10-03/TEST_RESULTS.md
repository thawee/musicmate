# Individual automated test results — 2026-10-03

Source: retained Gradle JUnit XML, normalized in [evidence/junit-results.json](evidence/junit-results.json). This is a recorded run, not a new execution for this documentation task. All 199 cases passed; zero failures, errors or skips.

Durations below are JUnit test execution times. They include setup, assertions and waits and must not be interpreted as request latency, throughput or a performance ranking. Each scenario name identifies the specific test; the linked source contains its exact stimulus and assertions. Relevant suite methodologies and targeted regression setups are explained in [PERFORMANCE.md](../../../PERFORMANCE.md).

## core / SettingsDefaultsTest

1 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:19.937Z.

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `new installs default to listener mode` | PASS | 0.001 |

## core / AlacToWavTest

1 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:19.944Z. [Test source](../../../core/src/test/java/apincer/music/core/codec/AlacToWavTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `sampleCount_fromFfprobeDurationAndTimeBase` | PASS | 0.001 |

## core / FlacToWavTest

3 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:19.948Z. [Test source](../../../core/src/test/java/apincer/music/core/codec/FlacToWavTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `wholeFile_16bit` | PASS | 0.215 |
| `ranges_matchTheSameBytesOfTheWholeFile` | PASS | 0.152 |
| `wholeFile_24bit_isAWavWhoseDataMatchesTheFlacMd5` | PASS | 0.122 |

## core / NioHttpServerFuzzTest

3 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:20.444Z. [Test source](../../../core/src/test/java/apincer/music/core/http/NioHttpServerFuzzTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `randomWebSocketFrames_neverBreakTheServer` | PASS | 6.485 |
| `mutatedRequests_neverBreakTheServer` | PASS | 2.083 |
| `hostileRequests_neverBreakTheServer` | PASS | 0.201 |

Recorded measurement output:

```text
FUZZ ws seed=749121491257666 cases=40
FUZZ seed=749127913864458 cases=1500
```

## core / NioHttpServerSoakTest

1 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:29.218Z. [Test source](../../../core/src/test/java/apincer/music/core/http/NioHttpServerSoakTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `mixedLoad_leavesNoLeaksAndServesCorrectBytes` | PASS | 8.096 |

Recorded measurement output:

```text
SOAK clients=16 seconds=8.0 operations=5560 throughput=606.7 MB/s rangeTTFB p50=9402 us p95=38943 us errors=0
```

## core / NioHttpServerTest

54 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:37.320Z. [Test source](../../../core/src/test/java/apincer/music/core/http/NioHttpServerTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `streamingResponse_clientDisconnect_stopsTheProducer` | PASS | 0.074 |
| `responseCreatedAfterStop_isClosedInsteadOfLeftInTheQueue` | PASS | 0.019 |
| `multipleRanges_areServedAsWholeFile` | PASS | 0.015 |
| `chunkedRequestBody_isRejected` | PASS | 0.012 |
| `webSocket_protocolErrorAfterAQueuedReply_stillClosesTheConnection` | PASS | 0.062 |
| `clientConnectionClose_isHonoured` | PASS | 0.065 |
| `stop_returnsPromptlyWhileAHandlerIsBusy` | PASS | 0.266 |
| `handlerDeadline_startsAfterTheRequestBodyArrives` | PASS | 1.549 |
| `webSocket_serverClose_sendsCloseFrameAndClosesSocket` | PASS | 0.015 |
| `streamingResponse_producerFailure_closesTheConnection` | PASS | 0.022 |
| `jpegBehindAPngName_isServedAsJpeg` | PASS | 0.020 |
| `webSocket_framePipelinedWithUpgrade_isHandledAfterOnOpen` | PASS | 0.096 |
| `midStreamDisconnect_releasesStreamAndConnection` | PASS | 0.123 |
| `stop_releasesOpenStreamsAndConnections` | PASS | 0.378 |
| `startOffset_sendsTheRestOfTheFileAs200` | PASS | 0.070 |
| `ifRangeMismatch_returnsWholeFile` | PASS | 0.016 |
| `stopBeforeRun_keepsTheServerStopped` | PASS | 0.013 |
| `startOffset_winsOverARangeHeader` | PASS | 0.015 |
| `stalledReader_isClosedAfterTheWriteStallTimeout` | PASS | 1.172 |
| `streamingResponse_deliversEveryByteInOrder` | PASS | 0.109 |
| `producerLimit_refusesNewConversionAndReleasesItsSlot` | PASS | 1.574 |
| `suffixRange_returnsLastBytes` | PASS | 0.078 |
| `stats_countRequestsBytesAndConnections` | PASS | 0.154 |
| `imageMimeType_comesFromContentNotExtension` | PASS | 0.092 |
| `post_bodyInSamePacket_isCutToContentLength` | PASS | 0.083 |
| `pipelinedRequests_areAnsweredInOrder` | PASS | 0.081 |
| `keepAlive_servesTwoRequestsOnOneConnection` | PASS | 0.123 |
| `streamingResponse_slowProducer_doesNotSpinTheSelector` | PASS | 1.599 |
| `webSocket_oversizedFrame_isNotDeliveredAsAMessage` | PASS | 0.068 |
| `workerOverload_returns503AndDoesNotStarveAnUpgradedWebSocket` | PASS | 0.081 |
| `get_returnsWholeFile` | PASS | 0.017 |
| `head_returnsLengthWithoutBody_andConnectionStaysUsable` | PASS | 0.072 |
| `webSocket_forceClose_closesSocketAndReleasesConnection` | PASS | 0.020 |
| `rangeStartPastEof_416EndsCleanly_andConnectionIsReusable` | PASS | 0.077 |
| `webSocket_messagesFromOneConnection_areHandledInOrder` | PASS | 0.238 |
| `streamingResponse_parkedProducer_hasAStallDeadline` | PASS | 1.069 |
| `expectContinue_gets100BeforeTheBody` | PASS | 0.099 |
| `notModified_doesNotEvictAnActiveStream` | PASS | 0.889 |
| `range_returnsSlice` | PASS | 0.073 |
| `handlerStall_hasADistinctCloseReason` | PASS | 1.072 |
| `webSocket_handshakeAndEcho` | PASS | 0.070 |
| `streamingResponse_producerPause_doesNotUseSocketStallDeadline` | PASS | 1.573 |
| `http10WithoutKeepAlive_closesAfterTheResponse` | PASS | 0.075 |
| `post_bodyArrivingAfterHeaders_reachesHandlerComplete` | PASS | 0.275 |
| `slowHeaders_areCutOffAtTheHeaderDeadline` | PASS | 1.464 |
| `diagnostics_keepAliveResponses_haveDistinctIdsAndExactBodyCounts` | PASS | 0.087 |
| `simultaneousAdmission_neverExceedsMediaSlots` | PASS | 0.034 |
| `bodylessResponse_hasZeroContentLength_andConnectionIsReusable` | PASS | 0.090 |
| `rangeEndPastEof_isClampedToLastByte` | PASS | 0.065 |
| `streamLimit_refusesNewAudioAndAllowsArtworkWithoutEvictingPlayback` | PASS | 0.162 |
| `openEndedRange_returnsTail` | PASS | 0.069 |
| `rangeStartPastEof_returns416` | PASS | 0.075 |
| `malformedRange_isIgnored` | PASS | 0.072 |
| `longStream_outlivesHeaderAndWriteStallDeadlines` | PASS | 2.124 |

## core / RateLimitingHandlerTest

2 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.236Z. [Test source](../../../core/src/test/java/apincer/music/core/http/RateLimitingHandlerTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `requestsOverTheLimit_get429` | PASS | 0.004 |
| `exemptPaths_areNotCountedOrLimited` | PASS | 0.001 |

## core / SerialExecutorTest

1 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.245Z. [Test source](../../../core/src/test/java/apincer/music/core/http/SerialExecutorTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `overflow_reservesCloseAfterAdmittedMessages` | PASS | 0.004 |

## core / ServerDiagnosticsTest

5 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.251Z. [Test source](../../../core/src/test/java/apincer/music/core/http/ServerDiagnosticsTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `rate_comesFromBytesSinceTheLastCall` | PASS | 0.000 |
| `counterReset_afterRestart_doesNotShowANegativeRate` | PASS | 0.001 |
| `firstCall_hasNoRateYet` | PASS | 0.000 |
| `problems_areListedOnlyWhenPresent` | PASS | 0.000 |
| `idle_saysSo` | PASS | 0.000 |

## core / StreamDiagnosticsTest

1 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.254Z. [Test source](../../../core/src/test/java/apincer/music/core/http/StreamDiagnosticsTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `historyIsBoundedWhileTotalsKeepCounting` | PASS | 0.000 |

## core / StreamingResponseTest

2 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.258Z. [Test source](../../../core/src/test/java/apincer/music/core/http/StreamingResponseTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `sharedBudget_includesPendingBytesAndIsReleasedOnCancellation` | PASS | 0.004 |
| `continuouslyReadyBody_yieldsAfterBudgetAndPreservesPartialWrites` | PASS | 0.008 |

## core / AudioTagTest

1 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.272Z. [Test source](../../../core/src/test/java/apincer/music/core/model/AudioTagTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `copy_createsDistinctCloneWithAllFieldsPreserved` | PASS | 0.001 |

## core / PlaylistEntrySmartTest

4 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.275Z. [Test source](../../../core/src/test/java/apincer/music/core/model/PlaylistEntrySmartTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `testHiResStudioMastersSmartPlaylist` | PASS | 0.007 |
| `testLosslessMasterVaultSmartPlaylist` | PASS | 0.001 |
| `testDrScoreSmartPlaylist` | PASS | 0.000 |
| `testPureDsdArchiveSmartPlaylist` | PASS | 0.000 |

## core / ListeningHistoryTrackerTest

8 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.285Z. [Test source](../../../core/src/test/java/apincer/music/core/playback/ListeningHistoryTrackerTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `unknownDurationNeedsNaturalEndAndDoesNotCompleteAfterSeek` | PASS | 0.002 |
| `subsecondDuplicatePositionsDoNotLoseWholeSeconds` | PASS | 0.000 |
| `skipRecordedOnceAndReplayGetsANewSession` | PASS | 0.001 |
| `ninetyPercentCountsOnceDespiteDuplicateCallbacks` | PASS | 0.000 |
| `seekToEndIsNotACompletedListen` | PASS | 0.000 |
| `unannouncedForwardSeekAndStalledClockCannotInflateListening` | PASS | 0.001 |
| `pauseAndSameTrackHandoffPreserveProgressWithoutCountingDowntime` | PASS | 0.000 |
| `noProgressOrLongTelemetryGapDoesNotCountAsListening` | PASS | 0.000 |

## core / ReplayGainManagerTest

4 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.291Z. [Test source](../../../core/src/test/java/apincer/music/core/playback/ReplayGainManagerTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `testParseGain` | PASS | 0.003 |
| `testParsePeak` | PASS | 0.000 |
| `testGainLinearCalculation` | PASS | 0.000 |
| `testReplayGainInfoDisplay` | PASS | 0.001 |

## core / FileSystemMoveTest

4 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.297Z. [Test source](../../../core/src/test/java/apincer/music/core/provider/FileSystemMoveTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `occupiedDestinationPreservesBothRecordings` | PASS | 0.012 |
| `unusedDestinationReceivesCompleteRecordingAndRemovesSource` | PASS | 0.006 |
| `conversionSkipsOccupiedDestinationsAndReturnsActualOutputPath` | PASS | 0.014 |
| `conversionUsesRequestedDestinationWhenAvailable` | PASS | 0.006 |

## core / MissingFileTest

3 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.338Z. [Test source](../../../core/src/test/java/apincer/music/core/provider/MissingFileTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `accessibleParentWithAbsentLeafConfirmsDeletion` | PASS | 0.005 |
| `existingTrackIsNotMissing` | PASS | 0.003 |
| `missingParentIsUnavailableRatherThanConfirmedDeletion` | PASS | 0.001 |

## core / QueueManagerTest

36 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.351Z. [Test source](../../../core/src/test/java/apincer/music/core/repository/QueueManagerTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `addPlayNext_currentTrack_preservesCurrentIndex` | PASS | 0.009 |
| `getRandomTrack_singleTrack_returnsThatTrack` | PASS | 0.002 |
| `unplayedUsesHistoryQueryAndPersistsSource` | PASS | 0.005 |
| `containsTrack_checksIndexMapCorrectly` | PASS | 0.001 |
| `removeTrack_thenPlaybackMoves_normalNavigationResumes` | PASS | 0.002 |
| `playlistSourceStateAndPersistence` | PASS | 0.002 |
| `getRandomTrack_multipleTracks_returnsValidTrackFromQueue` | PASS | 0.001 |
| `smartQueue_removedSuggestionsDoNotReturnAfterRestart` | PASS | 0.007 |
| `removeTrack_followerAlsoRemoved_nextMovesOn` | PASS | 0.001 |
| `shuffle_removeOrMoveOtherTrack_keepsNextTrack` | PASS | 0.003 |
| `smartQueue_downloadPredicateAndMissingFilesAreRespected` | PASS | 0.008 |
| `shuffle_enqueue_keepsPlayedTracksOutAndPlaysNewOnes` | PASS | 0.002 |
| `addPlayingQueue_existingTrack_movesToEndWithoutDuplicate` | PASS | 0.001 |
| `queueChangeListener_firesOnEditsAndModes_notOnNavigation` | PASS | 0.002 |
| `shuffle_playNext_isNextAndPlayedTracksDoNotReturn` | PASS | 0.009 |
| `removeTrack_playingTrack_thenPlayNext_playsItThenFollower` | PASS | 0.001 |
| `getNextTrack_repeatModeOne_loopsOnNaturalEnd_advancesOnUserSkip` | PASS | 0.002 |
| `removeTrack_playingLastTrack_queueEnds` | PASS | 0.002 |
| `removeTrack_playingLastTrack_repeatAllWraps` | PASS | 0.002 |
| `loadPlayingQueue_temporarilyUnavailableStorage_preservesQueueAndPersistence` | PASS | 0.002 |
| `shuffleOrder_preservesSequenceAcrossPlaybackTransitions` | PASS | 0.003 |
| `removeTrack_playingTrackReportedAgain_isNotReEnqueued` | PASS | 0.002 |
| `removeTrack_playingTrack_nextIsItsFollower` | PASS | 0.001 |
| `addPlayingQueue_currentTrack_updatesCurrentIndexToEnd` | PASS | 0.001 |
| `removeTrack_otherTrack_doesNotDetachPlayback` | PASS | 0.001 |
| `moveTrack_preservesActiveCurrentTrackIndex` | PASS | 0.001 |
| `rediscoverUsesThirtyDayCutoffAndRetainsOldestFirstOrder` | PASS | 0.005 |
| `smartQueue_preservesNextAndPrioritizesManualChoice` | PASS | 0.009 |
| `getRandomTrack_emptyQueue_returnsNull` | PASS | 0.002 |
| `setPlaybackTrack_unknownTrack_autoEnqueuesAndSetsPlaybackIndex` | PASS | 0.002 |
| `removeTrack_lastPlayingTrack_thenEnqueue_playsNewTrack` | PASS | 0.002 |
| `smartQueue_restoresAnchorAndManualFreezesRefill` | PASS | 0.006 |
| `smartQueue_clearDuringLookupCannotRepopulateQueue` | PASS | 0.003 |
| `removeTrack_playingFirstTrack_nextIsNewFirst` | PASS | 0.002 |
| `emptyPlayingQueue_resetsAllState` | PASS | 0.003 |
| `smartQueue_boundsRefillAndAppendsNewCandidates` | PASS | 0.034 |

## core / TagRepositoryPaginationTest

1 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.498Z. [Test source](../../../core/src/test/java/apincer/music/core/repository/TagRepositoryPaginationTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `unpagedFolderOrPlaylistReturnsOnlyRequestedPage` | PASS | 0.001 |

## core / TagRepositoryQueryErrorTest

1 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.501Z. [Test source](../../../core/src/test/java/apincer/music/core/repository/TagRepositoryQueryErrorTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `findMusic_queryFailure_propagatesInsteadOfEmptyList` | PASS | 0.002 |

## core / ClientFormatProfileTest

5 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.505Z. [Test source](../../../core/src/test/java/apincer/music/core/server/ClientFormatProfileTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `toshiba_onlyHidesDsd_untilWeHaveEvidence` | PASS | 0.000 |
| `otherClients_seeEverythingAsIs` | PASS | 0.001 |
| `detection` | PASS | 0.001 |
| `sony_playsFlac_convertsAlac_hidesDsd` | PASS | 0.000 |
| `lg_convertsFlacAndAlac_hidesDsd` | PASS | 0.000 |

## core / ClientRegistryTest

5 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.509Z. [Test source](../../../core/src/test/java/apincer/music/core/server/ClientRegistryTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `sony_fromXAvClientInfo` | PASS | 0.001 |
| `unknown_usesTheFirstToken` | PASS | 0.000 |
| `lg_fromUserAgent` | PASS | 0.001 |
| `firstSighting_isReportedOnce_perAddressAndName` | PASS | 0.001 |
| `otherKnownClients` | PASS | 0.000 |

## core / CoverThumbnailsTest

3 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.518Z. [Test source](../../../core/src/test/java/apincer/music/core/server/CoverThumbnailsTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `fit_neverEnlarges` | PASS | 0.000 |
| `fit_keepsAspectRatio_withinTheBox` | PASS | 0.000 |
| `sampleSize_isTheLargestPowerOfTwoThatStaysAboveTheTarget` | PASS | 0.000 |

## core / ServerHeaderTest

2 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.522Z. [Test source](../../../core/src/test/java/apincer/music/core/server/ServerHeaderTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `noLibraries_noTrailingSeparator` | PASS | 0.001 |
| `followsUpnpDeviceArchitecture_withEngineAsExtraToken` | PASS | 0.001 |

## core / NetworkUtilsTest

2 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.525Z. [Test source](../../../core/src/test/java/apincer/music/core/utils/NetworkUtilsTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `isVirtualOrVpnInterface_identifiesVpnTunnelsCorrectly` | PASS | 0.001 |
| `extractIpAddress_handlesVariousFormats` | PASS | 0.001 |

## core / ThaiEncodingUtilsTest

3 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:48:55.529Z. [Test source](../../../core/src/test/java/apincer/music/core/utils/ThaiEncodingUtilsTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `testFixThaiEncoding_plainEnglishUnchanged` | PASS | 0.001 |
| `testFixThaiEncoding_recoversGarbledText` | PASS | 0.006 |
| `testHasHighAscii_standardAscii` | PASS | 0.000 |

## server-jupnp / MediaReceiverRegistrarServiceTest

2 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:23:14.158Z. [Test source](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/MediaReceiverRegistrarServiceTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `everyDeviceIsAuthorizedAndValidated` | PASS | 0.006 |
| `bindsAsTheMicrosoftRegistrar_withItsThreeActions` | PASS | 0.047 |

## server-jupnp / MediaServerHubTimeParsingTest

5 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:23:14.222Z. [Test source](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/MediaServerHubTimeParsingTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `testFormatDurationForDidl` | PASS | 0.014 |
| `testParseTimeToSeconds_fractionalSeconds` | PASS | 0.001 |
| `testDLNAContentFeatures_bitrateThresholds` | PASS | 0.001 |
| `testParseTimeToSeconds_standardFormats` | PASS | 0.000 |
| `testParseTimeToSeconds_edgeCases` | PASS | 0.000 |

## server-jupnp / BrowsePagingTest

6 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:23:14.241Z. [Test source](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/content/BrowsePagingTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `startPastTheEnd_isEmpty` | PASS | 0.003 |
| `requestedCount_limitsThePage` | PASS | 0.000 |
| `hugeRequestedCount_doesNotOverflow` | PASS | 0.000 |
| `startingIndex_skipsEarlierEntries` | PASS | 0.001 |
| `lastPage_isShort` | PASS | 0.000 |
| `requestedCountZero_meansEverythingFromTheStart` | PASS | 0.000 |

## server-jupnp / BrowseSortTest

4 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:23:14.249Z. [Test source](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/content/BrowseSortTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `unsupportedOrEmptyCriteria_meanNoSort` | PASS | 0.007 |
| `tracks_sortTheSameWay` | PASS | 0.005 |
| `artistThenTitle_andAlbum` | PASS | 0.004 |
| `title_ascendingAndDescending_ignoringCase` | PASS | 0.001 |

## server-jupnp / DidlValuesTest

4 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:23:14.269Z. [Test source](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/content/DidlValuesTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `samsungMediaInfo_isTheDurationInMilliseconds` | PASS | 0.000 |
| `didlBitrate_isBytesPerSecond` | PASS | 0.000 |
| `didlDate_isAnIsoDate` | PASS | 0.000 |
| `dlnaProfile_onlyForFormatsDlnaDefines` | PASS | 0.000 |

## server-jupnp / SamsungFeatureListTest

2 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:23:14.272Z. [Test source](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/content/SamsungFeatureListTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `contentDirectory_exposesXGetFeatureList` | PASS | 0.013 |
| `featureList_pointsSamsungBasicViewAtTheMusicRoot` | PASS | 0.000 |

## server-jupnp / UpnpSearchTest

10 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:23:14.290Z. [Test source](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/content/UpnpSearchTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `artistAlbumGenre_andCreatorAsArtist` | PASS | 0.001 |
| `star_matchesEverything` | PASS | 0.000 |
| `andBindsTighterThanOr_andParenthesesGroup` | PASS | 0.002 |
| `unknownProperty_matchesNothing` | PASS | 0.000 |
| `malformedCriteria_isRejected` | PASS | 0.001 |
| `contains_isCaseInsensitive` | PASS | 0.000 |
| `escapedQuotes_inValues` | PASS | 0.000 |
| `bubbleUpnpStyle_classAndTitle` | PASS | 0.000 |
| `audioClass_matchesTracks_containerClass_matchesNothing` | PASS | 0.000 |
| `negativeOperators_andExists` | PASS | 0.003 |

## server-jupnp / TimeSeekTest

7 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:23:14.301Z. [Test source](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/transport/TimeSeekTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `npt_isHMMSSmmm` | PASS | 0.010 |
| `flac_usesTheSeekPointAtOrBeforeTheTime` | PASS | 0.012 |
| `pastTheEnd_orUnsupportedFormat_isNull` | PASS | 0.006 |
| `parseNpt_rejectsOtherForms` | PASS | 0.001 |
| `parseNpt_secondsAndClockForms` | PASS | 0.003 |
| `flac_withoutSeekTable_isProportional_alignedToTheNextFrame` | PASS | 0.012 |
| `mp3_isProportionalAfterTheId3Tag_alignedToAFrame` | PASS | 0.008 |

## server-jupnp / UpnpRequestCheckTest

3 tests; failures 0, errors 0, skipped 0. Recorded UTC: 2026-10-03T05:23:14.355Z. [Test source](../../../server-jupnp/src/test/java/apincer/music/server/nio/UpnpRequestCheckTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `upnpMethods_areAccepted` | PASS | 0.001 |
| `otherMethods_get405` | PASS | 0.000 |
| `malformedPath_gets400` | PASS | 0.000 |

