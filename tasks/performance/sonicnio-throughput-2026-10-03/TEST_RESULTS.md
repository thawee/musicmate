# Throughput follow-up: individual test results

Recorded final Gradle run: 162 core + 43 UPnP = 205 passing cases, zero failures/errors/skips. Durations are test execution time, not streaming latency. [Methods and comparison](../../../PERFORMANCE.md#10-throughput-follow-up). [Normalized results](evidence/junit-results.json).

## core / SettingsDefaultsTest

Recorded UTC: 2026-10-03T06:43:12.976Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/SettingsDefaultsTest.kt).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `new installs default to listener mode` | PASS | 0.001 |

## core / AlacToWavTest

Recorded UTC: 2026-10-03T06:43:12.980Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/codec/AlacToWavTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `sampleCount_fromFfprobeDurationAndTimeBase` | PASS | 0.000 |

## core / FlacToWavTest

Recorded UTC: 2026-10-03T06:43:12.985Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/codec/FlacToWavTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `wholeFile_16bit` | PASS | 0.190 |
| `ranges_matchTheSameBytesOfTheWholeFile` | PASS | 0.122 |
| `wholeFile_24bit_isAWavWhoseDataMatchesTheFlacMd5` | PASS | 0.116 |

## core / FileResponseTest

Recorded UTC: 2026-10-03T06:43:13.415Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/http/FileResponseTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `changingTurnBudget_preservesFileOffsetsAndExactTail` | PASS | 0.023 |

## core / NioHttpServerFuzzTest

Recorded UTC: 2026-10-03T06:43:13.441Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/http/NioHttpServerFuzzTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `randomWebSocketFrames_neverBreakTheServer` | PASS | 7.108 |
| `mutatedRequests_neverBreakTheServer` | PASS | 0.924 |
| `hostileRequests_neverBreakTheServer` | PASS | 0.193 |

## core / NioHttpServerSoakTest

Recorded UTC: 2026-10-03T06:43:21.670Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/http/NioHttpServerSoakTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `mixedLoad_leavesNoLeaksAndServesCorrectBytes` | PASS | 8.038 |

## core / NioHttpServerTest

Recorded UTC: 2026-10-03T06:43:29.713Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/http/NioHttpServerTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `streamingResponse_clientDisconnect_stopsTheProducer` | PASS | 0.069 |
| `responseCreatedAfterStop_isClosedInsteadOfLeftInTheQueue` | PASS | 0.067 |
| `multipleRanges_areServedAsWholeFile` | PASS | 0.010 |
| `chunkedRequestBody_isRejected` | PASS | 0.065 |
| `webSocket_protocolErrorAfterAQueuedReply_stillClosesTheConnection` | PASS | 0.068 |
| `clientConnectionClose_isHonoured` | PASS | 0.014 |
| `stop_returnsPromptlyWhileAHandlerIsBusy` | PASS | 0.279 |
| `handlerDeadline_startsAfterTheRequestBodyArrives` | PASS | 1.579 |
| `webSocket_serverClose_sendsCloseFrameAndClosesSocket` | PASS | 0.088 |
| `streamingResponse_producerFailure_closesTheConnection` | PASS | 0.082 |
| `jpegBehindAPngName_isServedAsJpeg` | PASS | 0.089 |
| `webSocket_framePipelinedWithUpgrade_isHandledAfterOnOpen` | PASS | 0.055 |
| `midStreamDisconnect_releasesStreamAndConnection` | PASS | 0.080 |
| `stop_releasesOpenStreamsAndConnections` | PASS | 0.384 |
| `startOffset_sendsTheRestOfTheFileAs200` | PASS | 0.025 |
| `ifRangeMismatch_returnsWholeFile` | PASS | 0.074 |
| `stopBeforeRun_keepsTheServerStopped` | PASS | 0.081 |
| `startOffset_winsOverARangeHeader` | PASS | 0.088 |
| `stalledReader_isClosedAfterTheWriteStallTimeout` | PASS | 1.119 |
| `streamingResponse_deliversEveryByteInOrder` | PASS | 0.030 |
| `producerLimit_refusesNewConversionAndReleasesItsSlot` | PASS | 1.633 |
| `suffixRange_returnsLastBytes` | PASS | 0.192 |
| `stats_countRequestsBytesAndConnections` | PASS | 0.134 |
| `imageMimeType_comesFromContentNotExtension` | PASS | 0.077 |
| `post_bodyInSamePacket_isCutToContentLength` | PASS | 0.036 |
| `pipelinedRequests_areAnsweredInOrder` | PASS | 0.087 |
| `keepAlive_servesTwoRequestsOnOneConnection` | PASS | 0.039 |
| `streamingResponse_slowProducer_doesNotSpinTheSelector` | PASS | 1.527 |
| `webSocket_oversizedFrame_isNotDeliveredAsAMessage` | PASS | 0.084 |
| `workerOverload_returns503AndDoesNotStarveAnUpgradedWebSocket` | PASS | 0.107 |
| `get_returnsWholeFile` | PASS | 0.101 |
| `head_returnsLengthWithoutBody_andConnectionStaysUsable` | PASS | 0.034 |
| `webSocket_forceClose_closesSocketAndReleasesConnection` | PASS | 0.068 |
| `rangeStartPastEof_416EndsCleanly_andConnectionIsReusable` | PASS | 0.093 |
| `webSocket_messagesFromOneConnection_areHandledInOrder` | PASS | 0.198 |
| `streamingResponse_parkedProducer_hasAStallDeadline` | PASS | 1.095 |
| `expectContinue_gets100BeforeTheBody` | PASS | 0.094 |
| `notModified_doesNotEvictAnActiveStream` | PASS | 0.858 |
| `range_returnsSlice` | PASS | 0.090 |
| `handlerStall_hasADistinctCloseReason` | PASS | 1.073 |
| `webSocket_handshakeAndEcho` | PASS | 0.027 |
| `streamingResponse_producerPause_doesNotUseSocketStallDeadline` | PASS | 1.582 |
| `http10WithoutKeepAlive_closesAfterTheResponse` | PASS | 0.047 |
| `post_bodyArrivingAfterHeaders_reachesHandlerComplete` | PASS | 0.273 |
| `slowHeaders_areCutOffAtTheHeaderDeadline` | PASS | 1.471 |
| `diagnostics_keepAliveResponses_haveDistinctIdsAndExactBodyCounts` | PASS | 0.101 |
| `simultaneousAdmission_neverExceedsMediaSlots` | PASS | 0.041 |
| `bodylessResponse_hasZeroContentLength_andConnectionIsReusable` | PASS | 0.011 |
| `rangeEndPastEof_isClampedToLastByte` | PASS | 0.012 |
| `streamLimit_refusesNewAudioAndAllowsArtworkWithoutEvictingPlayback` | PASS | 0.084 |
| `openEndedRange_returnsTail` | PASS | 0.029 |
| `rangeStartPastEof_returns416` | PASS | 0.018 |
| `malformedRange_isIgnored` | PASS | 0.020 |
| `longStream_outlivesHeaderAndWriteStallDeadlines` | PASS | 2.076 |

## core / RateLimitingHandlerTest

Recorded UTC: 2026-10-03T06:43:47.490Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/http/RateLimitingHandlerTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `requestsOverTheLimit_get429` | PASS | 0.008 |
| `exemptPaths_areNotCountedOrLimited` | PASS | 0.002 |

## core / SerialExecutorTest

Recorded UTC: 2026-10-03T06:43:47.505Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/http/SerialExecutorTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `overflow_reservesCloseAfterAdmittedMessages` | PASS | 0.005 |

## core / ServerDiagnosticsTest

Recorded UTC: 2026-10-03T06:43:47.515Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/http/ServerDiagnosticsTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `rate_comesFromBytesSinceTheLastCall` | PASS | 0.002 |
| `counterReset_afterRestart_doesNotShowANegativeRate` | PASS | 0.000 |
| `firstCall_hasNoRateYet` | PASS | 0.001 |
| `problems_areListedOnlyWhenPresent` | PASS | 0.000 |
| `idle_saysSo` | PASS | 0.000 |

## core / StreamDiagnosticsTest

Recorded UTC: 2026-10-03T06:43:47.519Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/http/StreamDiagnosticsTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `historyIsBoundedWhileTotalsKeepCounting` | PASS | 0.001 |

## core / StreamingResponseTest

Recorded UTC: 2026-10-03T06:43:47.521Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/http/StreamingResponseTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `shortProducer_flushesFirstBytesBeforePausingAndStillFailsLengthCheck` | PASS | 0.004 |
| `selectorDrainingWhileProducerWaitsForQueueSpace_preservesEveryByte` | PASS | 0.002 |
| `sharedBudget_includesPendingBytesAndIsReleasedOnCancellation` | PASS | 0.002 |
| `continuouslyReadyBody_yieldsAfterBudgetAndPreservesPartialWrites` | PASS | 0.005 |
| `pausedProducer_partialBatchIsDrainedBeforeParking` | PASS | 0.001 |
| `smallWrites_areBatchedWithoutChangingBytesOrFinalTail` | PASS | 0.003 |
| `partialBatch_isReleasedWhenProducerFailsOrIsCancelled` | PASS | 0.001 |

## core / AudioTagTest

Recorded UTC: 2026-10-03T06:43:47.540Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/model/AudioTagTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `copy_createsDistinctCloneWithAllFieldsPreserved` | PASS | 0.001 |

## core / PlaylistEntrySmartTest

Recorded UTC: 2026-10-03T06:43:47.543Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/model/PlaylistEntrySmartTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `testHiResStudioMastersSmartPlaylist` | PASS | 0.006 |
| `testLosslessMasterVaultSmartPlaylist` | PASS | 0.000 |
| `testDrScoreSmartPlaylist` | PASS | 0.000 |
| `testPureDsdArchiveSmartPlaylist` | PASS | 0.000 |

## core / ListeningHistoryTrackerTest

Recorded UTC: 2026-10-03T06:43:47.552Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/playback/ListeningHistoryTrackerTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `unknownDurationNeedsNaturalEndAndDoesNotCompleteAfterSeek` | PASS | 0.001 |
| `subsecondDuplicatePositionsDoNotLoseWholeSeconds` | PASS | 0.000 |
| `skipRecordedOnceAndReplayGetsANewSession` | PASS | 0.000 |
| `ninetyPercentCountsOnceDespiteDuplicateCallbacks` | PASS | 0.000 |
| `seekToEndIsNotACompletedListen` | PASS | 0.000 |
| `unannouncedForwardSeekAndStalledClockCannotInflateListening` | PASS | 0.000 |
| `pauseAndSameTrackHandoffPreserveProgressWithoutCountingDowntime` | PASS | 0.000 |
| `noProgressOrLongTelemetryGapDoesNotCountAsListening` | PASS | 0.000 |

## core / ReplayGainManagerTest

Recorded UTC: 2026-10-03T06:43:47.555Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/playback/ReplayGainManagerTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `testParseGain` | PASS | 0.002 |
| `testParsePeak` | PASS | 0.000 |
| `testGainLinearCalculation` | PASS | 0.000 |
| `testReplayGainInfoDisplay` | PASS | 0.001 |

## core / FileSystemMoveTest

Recorded UTC: 2026-10-03T06:43:47.559Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/provider/FileSystemMoveTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `occupiedDestinationPreservesBothRecordings` | PASS | 0.017 |
| `unusedDestinationReceivesCompleteRecordingAndRemovesSource` | PASS | 0.011 |
| `conversionSkipsOccupiedDestinationsAndReturnsActualOutputPath` | PASS | 0.021 |
| `conversionUsesRequestedDestinationWhenAvailable` | PASS | 0.005 |

## core / MissingFileTest

Recorded UTC: 2026-10-03T06:43:47.615Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/provider/MissingFileTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `accessibleParentWithAbsentLeafConfirmsDeletion` | PASS | 0.002 |
| `existingTrackIsNotMissing` | PASS | 0.002 |
| `missingParentIsUnavailableRatherThanConfirmedDeletion` | PASS | 0.001 |

## core / QueueManagerTest

Recorded UTC: 2026-10-03T06:43:47.624Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/repository/QueueManagerTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `addPlayNext_currentTrack_preservesCurrentIndex` | PASS | 0.006 |
| `getRandomTrack_singleTrack_returnsThatTrack` | PASS | 0.001 |
| `unplayedUsesHistoryQueryAndPersistsSource` | PASS | 0.003 |
| `containsTrack_checksIndexMapCorrectly` | PASS | 0.002 |
| `removeTrack_thenPlaybackMoves_normalNavigationResumes` | PASS | 0.001 |
| `playlistSourceStateAndPersistence` | PASS | 0.001 |
| `getRandomTrack_multipleTracks_returnsValidTrackFromQueue` | PASS | 0.001 |
| `smartQueue_removedSuggestionsDoNotReturnAfterRestart` | PASS | 0.003 |
| `removeTrack_followerAlsoRemoved_nextMovesOn` | PASS | 0.001 |
| `shuffle_removeOrMoveOtherTrack_keepsNextTrack` | PASS | 0.002 |
| `smartQueue_downloadPredicateAndMissingFilesAreRespected` | PASS | 0.009 |
| `shuffle_enqueue_keepsPlayedTracksOutAndPlaysNewOnes` | PASS | 0.001 |
| `addPlayingQueue_existingTrack_movesToEndWithoutDuplicate` | PASS | 0.001 |
| `queueChangeListener_firesOnEditsAndModes_notOnNavigation` | PASS | 0.001 |
| `shuffle_playNext_isNextAndPlayedTracksDoNotReturn` | PASS | 0.001 |
| `removeTrack_playingTrack_thenPlayNext_playsItThenFollower` | PASS | 0.001 |
| `getNextTrack_repeatModeOne_loopsOnNaturalEnd_advancesOnUserSkip` | PASS | 0.001 |
| `removeTrack_playingLastTrack_queueEnds` | PASS | 0.000 |
| `removeTrack_playingLastTrack_repeatAllWraps` | PASS | 0.001 |
| `loadPlayingQueue_temporarilyUnavailableStorage_preservesQueueAndPersistence` | PASS | 0.001 |
| `shuffleOrder_preservesSequenceAcrossPlaybackTransitions` | PASS | 0.000 |
| `removeTrack_playingTrackReportedAgain_isNotReEnqueued` | PASS | 0.000 |
| `removeTrack_playingTrack_nextIsItsFollower` | PASS | 0.001 |
| `addPlayingQueue_currentTrack_updatesCurrentIndexToEnd` | PASS | 0.001 |
| `removeTrack_otherTrack_doesNotDetachPlayback` | PASS | 0.000 |
| `moveTrack_preservesActiveCurrentTrackIndex` | PASS | 0.001 |
| `rediscoverUsesThirtyDayCutoffAndRetainsOldestFirstOrder` | PASS | 0.005 |
| `smartQueue_preservesNextAndPrioritizesManualChoice` | PASS | 0.004 |
| `getRandomTrack_emptyQueue_returnsNull` | PASS | 0.001 |
| `setPlaybackTrack_unknownTrack_autoEnqueuesAndSetsPlaybackIndex` | PASS | 0.001 |
| `removeTrack_lastPlayingTrack_thenEnqueue_playsNewTrack` | PASS | 0.000 |
| `smartQueue_restoresAnchorAndManualFreezesRefill` | PASS | 0.004 |
| `smartQueue_clearDuringLookupCannotRepopulateQueue` | PASS | 0.004 |
| `removeTrack_playingFirstTrack_nextIsNewFirst` | PASS | 0.001 |
| `emptyPlayingQueue_resetsAllState` | PASS | 0.001 |
| `smartQueue_boundsRefillAndAppendsNewCandidates` | PASS | 0.021 |

## core / TagRepositoryPaginationTest

Recorded UTC: 2026-10-03T06:43:47.710Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/repository/TagRepositoryPaginationTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `unpagedFolderOrPlaylistReturnsOnlyRequestedPage` | PASS | 0.001 |

## core / TagRepositoryQueryErrorTest

Recorded UTC: 2026-10-03T06:43:47.712Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/repository/TagRepositoryQueryErrorTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `findMusic_queryFailure_propagatesInsteadOfEmptyList` | PASS | 0.001 |

## core / ClientFormatProfileTest

Recorded UTC: 2026-10-03T06:43:47.714Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/server/ClientFormatProfileTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `toshiba_onlyHidesDsd_untilWeHaveEvidence` | PASS | 0.001 |
| `otherClients_seeEverythingAsIs` | PASS | 0.000 |
| `detection` | PASS | 0.000 |
| `sony_playsFlac_convertsAlac_hidesDsd` | PASS | 0.000 |
| `lg_convertsFlacAndAlac_hidesDsd` | PASS | 0.000 |

## core / ClientRegistryTest

Recorded UTC: 2026-10-03T06:43:47.717Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/server/ClientRegistryTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `sony_fromXAvClientInfo` | PASS | 0.000 |
| `unknown_usesTheFirstToken` | PASS | 0.001 |
| `lg_fromUserAgent` | PASS | 0.000 |
| `firstSighting_isReportedOnce_perAddressAndName` | PASS | 0.000 |
| `otherKnownClients` | PASS | 0.001 |

## core / CoverThumbnailsTest

Recorded UTC: 2026-10-03T06:43:47.720Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/server/CoverThumbnailsTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `fit_neverEnlarges` | PASS | 0.000 |
| `fit_keepsAspectRatio_withinTheBox` | PASS | 0.000 |
| `sampleSize_isTheLargestPowerOfTwoThatStaysAboveTheTarget` | PASS | 0.000 |

## core / ServerHeaderTest

Recorded UTC: 2026-10-03T06:43:47.721Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/server/ServerHeaderTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `noLibraries_noTrailingSeparator` | PASS | 0.001 |
| `followsUpnpDeviceArchitecture_withEngineAsExtraToken` | PASS | 0.000 |

## core / NetworkUtilsTest

Recorded UTC: 2026-10-03T06:43:47.723Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/utils/NetworkUtilsTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `isVirtualOrVpnInterface_identifiesVpnTunnelsCorrectly` | PASS | 0.000 |
| `extractIpAddress_handlesVariousFormats` | PASS | 0.001 |

## core / ThaiEncodingUtilsTest

Recorded UTC: 2026-10-03T06:43:47.725Z. [Exact test stimuli/assertions](../../../core/src/test/java/apincer/music/core/utils/ThaiEncodingUtilsTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `testFixThaiEncoding_plainEnglishUnchanged` | PASS | 0.000 |
| `testFixThaiEncoding_recoversGarbledText` | PASS | 0.004 |
| `testHasHighAscii_standardAscii` | PASS | 0.001 |

## server-jupnp / MediaReceiverRegistrarServiceTest

Recorded UTC: 2026-10-03T06:43:11.569Z. [Exact test stimuli/assertions](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/MediaReceiverRegistrarServiceTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `everyDeviceIsAuthorizedAndValidated` | PASS | 0.003 |
| `bindsAsTheMicrosoftRegistrar_withItsThreeActions` | PASS | 0.023 |

## server-jupnp / MediaServerHubTimeParsingTest

Recorded UTC: 2026-10-03T06:43:11.600Z. [Exact test stimuli/assertions](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/MediaServerHubTimeParsingTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `testFormatDurationForDidl` | PASS | 0.007 |
| `testParseTimeToSeconds_fractionalSeconds` | PASS | 0.000 |
| `testDLNAContentFeatures_bitrateThresholds` | PASS | 0.001 |
| `testParseTimeToSeconds_standardFormats` | PASS | 0.000 |
| `testParseTimeToSeconds_edgeCases` | PASS | 0.000 |

## server-jupnp / BrowsePagingTest

Recorded UTC: 2026-10-03T06:43:11.610Z. [Exact test stimuli/assertions](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/content/BrowsePagingTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `startPastTheEnd_isEmpty` | PASS | 0.003 |
| `requestedCount_limitsThePage` | PASS | 0.000 |
| `hugeRequestedCount_doesNotOverflow` | PASS | 0.001 |
| `startingIndex_skipsEarlierEntries` | PASS | 0.000 |
| `lastPage_isShort` | PASS | 0.000 |
| `requestedCountZero_meansEverythingFromTheStart` | PASS | 0.000 |

## server-jupnp / BrowseSortTest

Recorded UTC: 2026-10-03T06:43:11.615Z. [Exact test stimuli/assertions](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/content/BrowseSortTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `unsupportedOrEmptyCriteria_meanNoSort` | PASS | 0.002 |
| `tracks_sortTheSameWay` | PASS | 0.002 |
| `artistThenTitle_andAlbum` | PASS | 0.001 |
| `title_ascendingAndDescending_ignoringCase` | PASS | 0.001 |

## server-jupnp / DidlValuesTest

Recorded UTC: 2026-10-03T06:43:11.622Z. [Exact test stimuli/assertions](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/content/DidlValuesTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `samsungMediaInfo_isTheDurationInMilliseconds` | PASS | 0.000 |
| `didlBitrate_isBytesPerSecond` | PASS | 0.000 |
| `didlDate_isAnIsoDate` | PASS | 0.000 |
| `dlnaProfile_onlyForFormatsDlnaDefines` | PASS | 0.000 |

## server-jupnp / SamsungFeatureListTest

Recorded UTC: 2026-10-03T06:43:11.623Z. [Exact test stimuli/assertions](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/content/SamsungFeatureListTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `contentDirectory_exposesXGetFeatureList` | PASS | 0.005 |
| `featureList_pointsSamsungBasicViewAtTheMusicRoot` | PASS | 0.000 |

## server-jupnp / UpnpSearchTest

Recorded UTC: 2026-10-03T06:43:11.634Z. [Exact test stimuli/assertions](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/content/UpnpSearchTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `artistAlbumGenre_andCreatorAsArtist` | PASS | 0.001 |
| `star_matchesEverything` | PASS | 0.000 |
| `andBindsTighterThanOr_andParenthesesGroup` | PASS | 0.001 |
| `unknownProperty_matchesNothing` | PASS | 0.000 |
| `malformedCriteria_isRejected` | PASS | 0.001 |
| `contains_isCaseInsensitive` | PASS | 0.000 |
| `escapedQuotes_inValues` | PASS | 0.000 |
| `bubbleUpnpStyle_classAndTitle` | PASS | 0.000 |
| `audioClass_matchesTracks_containerClass_matchesNothing` | PASS | 0.000 |
| `negativeOperators_andExists` | PASS | 0.001 |

## server-jupnp / TimeSeekTest

Recorded UTC: 2026-10-03T06:43:11.640Z. [Exact test stimuli/assertions](../../../server-jupnp/src/test/java/apincer/music/server/jupnp/transport/TimeSeekTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `npt_isHMMSSmmm` | PASS | 0.004 |
| `flac_usesTheSeekPointAtOrBeforeTheTime` | PASS | 0.009 |
| `pastTheEnd_orUnsupportedFormat_isNull` | PASS | 0.002 |
| `parseNpt_rejectsOtherForms` | PASS | 0.001 |
| `parseNpt_secondsAndClockForms` | PASS | 0.002 |
| `flac_withoutSeekTable_isProportional_alignedToTheNextFrame` | PASS | 0.011 |
| `mp3_isProportionalAfterTheId3Tag_alignedToAFrame` | PASS | 0.008 |

## server-jupnp / UpnpRequestCheckTest

Recorded UTC: 2026-10-03T06:43:11.678Z. [Exact test stimuli/assertions](../../../server-jupnp/src/test/java/apincer/music/server/nio/UpnpRequestCheckTest.java).

| Test / scenario | Result | Execution time (s) |
|---|---|---:|
| `upnpMethods_areAccepted` | PASS | 0.001 |
| `otherMethods_get405` | PASS | 0.000 |
| `malformedPath_gets400` | PASS | 0.000 |

