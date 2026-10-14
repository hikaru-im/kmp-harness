# Plugin local update delivery

status=implemented; model=SHUAIAPI/gpt-6.1-sol; actual_model=gpt-6.1-sol; provider=opencodex.

tests_run=false; builds_run=false; write_set_released=true.

Baseline: /tmp/harness-subagent-history-parent-candidate-20261008-r1-manifest.json (2204 inputs).
Manifest SHA256: e164caf5e3cb9dc37f462ec56ba3b85c6963096b8ff078c26ab00fce2bc48fa6

## Implementation

- updateLocal reimports only registered source inside existing CAS/file-lock transaction; identity/order/enabled preserved; inventory budgets and final lease checks reused.
- Optional default-off capability, typed frozen-target controller action, real desktop adapter and source-update detail control implemented.
- New mutation clears previous receipt/notice; existing error/explicit-refresh/owner-scope fences retained.

## Exact file hashes

| File | Before SHA256 (fixed manifest) | After SHA256 |
| --- | --- | --- |
| modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopUserPlugins.kt | b01b83d7ff70dff1956b70df4aaf4e5e2c8712bd16b71b6536250c115be1c90f | 5d53a6f5cc46edee854c3fba7f29fb6fdca48f0c38068cedca5bbee71f266239 |
| apps/shared/src/im/hikaru/harness/client/plugins/PluginManagement.kt | d5b42bfa17ccb314ece547b55e8c4c6f6522fd135d88aac2db2f8087f23f7ca0 | aa79e4eb97296d405e91064e272ae8f169ea9379ece7f3e0c0de850d0d0ebfba |
| apps/shared/src/im/hikaru/harness/client/plugins/PluginModels.kt | 1690f5a31ce7371edbc9667a0ffd32e02f603b029ea8324bc586dfbcf68430c4 | 4d2751c266c524c041deb70141da53f03c220ab7634092af6c66eacdf82f46f0 |
| apps/shared/src/im/hikaru/harness/client/plugins/InstalledPluginsController.kt | 2a57204afda98452526d2c52051a5752b4f2ff17a042e2ac323ee64dce475c08 | 2fde1cc7a2b2cb2b096f88d82cd015da6a05c233a9ec6ddc1cee360450018c86 |
| apps/shared/src/im/hikaru/harness/client/plugins/InstalledPluginsPage.kt | c7a880f9a627344b1ed2872f3744c6b347060c779efe3f644775d9050a024ee7 | 25817fa819d209cfb21a35d3087a474ed2815262801702bf6f6e5a96db9836b8 |
| apps/jvm-app/src/im/hikaru/harness/desktop/plugins/DesktopInstalledPluginRepository.kt | ec4be3c6bf5aea94988cae9815656be0563952d0c109d32c6d472d3d30ce5c72 | 1fcfe3383f7f3cfb7a4fef4de47f1e7c95624bd281987b029cc487b927205838 |
| modules/bundle/desktop/test/im/hikaru/harness/bundle/desktop/DesktopUserPluginsTest.kt | 6312d25abc7ff106c2e647f0c4add32c2a6dcaedcdfc635fdadb2bb144152fe4 | 328a60770a973d11cd87b4caf9f3385ba763fb32b1cac98d929a908e9f0bb8dd |
| apps/jvm-app/test/im/hikaru/harness/desktop/plugins/DesktopInstalledPluginRepositoryTest.kt | 2ed0cf2a83faf94026bad700caf6a612bd6c119f83a1d3943f62e3284eefb00d | 15f06763f399a0851b7f878cc6a2848d3327c8f255de6752cd1382352b7b22f6 |
| apps/jvm-app/test/im/hikaru/harness/desktop/PluginLocalUpdateInteractionTest.kt | null (new) | a94b95578f2a6401ce02ad4cbd48d712063b9cc6f71fdde950cd068c8af4dd66 |

## Test source inventory

16 new @Test methods (service 4, repository 2, UI 10); 42 total methods in the three changed classes. No build/test was executed.

### DesktopUserPluginsTest

21 total; 4 new; baseline 17.

All current test names:

- localUpdatePreservesIdentityOrderEnabledAndRestoresExactSnapshot (new)
- invalidLocalUpdatesKeepSavedBytesAndInstructionsIncludingMissingSourceAndTarget (new)
- localUpdateRejectsSymlinkInvalidTextAndLimitsWithoutReplacingOldSnapshot (new)
- localUpdateRevalidatesCombinedEnabledBudgetAndFinalAccessLease (new)
- missingInventoryReadCreatesNothingAndHasByteShaRevision
- roundTripConsumesOnlyEnabledImmutableSnapshotsAndRemovalOnlyRemovesRegistration
- identicalNamesFromDifferentSourcesAreDistinctButSameCanonicalSourceCannotBeRegisteredTwice
- explicitLocalSkillDirectoriesAndLimitedFrontmatterHaveNoInventedDescription
- unsupportedAssetsAreDescriptorsAndNeverExposeRawConfigurationOrExecuteScripts
- casUsesExactBytesAcrossInstancesAndDetectsExternalValidEdits
- corruptInventoryCannotBeOverwrittenEvenWithItsActualShaAndErrorsDoNotLeakContents
- inventoryRejectsUnknownFieldsDuplicateIdsAndTamperedProvenanceOrSnapshots
- symlinksInSourceDocumentParentAndLockAreRejected
- unsafeOrUnsupportedExplicitPathsAndInvalidUtf8AreRejectedBeforeAnyWrite
- relativeSourceAndDocumentPathsAreRejectedByServiceEvenWithoutUiValidation
- sourceSizeSkillCountAndComposedInstructionBoundsAreEnforcedWithoutTruncation
- registrationCannotWriteInsideSourceAndRequiresAtLeastOneNonemptySkill
- staleOperationAndCancellationBeforeFinalReplaceLeaveInventoryUntouched
- externalEditDuringMutationConflictsAndIsNeverAutomaticallyRetried
- sameJvmMultipleInstancesAndConcurrentRequestsHaveExactlyOneCasWinner
- existingFileChannelLockContentionSuspendsAndCanBeCancelledWithoutOverlappingLockFailure
### DesktopInstalledPluginRepositoryTest

11 total; 2 new; baseline 9.

All current test names:

- localUpdateReceiptDetailAndFreshAdapterReadSameSavedSnapshot (new)
- localUpdateChangesNextRealAgentRequestAndDisabledUpdateStaysUninjected (new)
- unboundAccessRejectsEveryReadAndWriteWithoutCreatingInventory
- relayForeignHostAndDifferentConnectionToSameHostRejectEveryReadAndWrite
- replacementGenerationRejectsOldScopeAndClearsItsListedRevision
- generationChangedDuringRealHostDescribeRejectsInFlightReadsAndWrites
- generationReplacementCancelsWritesWaitingOnRealInventoryLockWithoutCommitting
- registrationDetailEnableDisableAndRemovalPersistAcrossFreshServicesAndAdapters
- staleRevisionRejectsAllMutationKindsAcrossIndependentRealServices
- revisionRemainsTheListedSnapshotAfterExternalWriteAndCannotAuthorizeStaleUi
- adapterMutationsUpdateNextRealAgentRequestSystemAfterEnableDisableAndRemoval
### PluginLocalUpdateInteractionTest

10 total; 10 new; baseline 0.

All current test names:

- sourceUpdatePreservesEnabledAndReloadsVersionAndSkillMetadata (new)
- failedSourceKeepsSnapshotRequiresRefreshAndNextBusyClearsOldReceipt (new)
- updateTargetIsFrozenWhileBackAndAnotherDetailAreSelected (new)
- generationRevocationDuringActualFileLockWaitCannotCommitOrPublishReceipt (new)
- controllerCloseCancelsUpdateWaitingOnActualFileLock (new)
- lateRealHostDescribeCannotPublishOldDetailAfterOwnerReplacement (new)
- forgedUnselectedAndErrorDetailUpdatesCannotWrite (new)
- optionalAdapterDefaultHidesUpdateAndRejectsDispatch (new)
- committedUpdateKeepsReceiptWhenPostWriteReadsFailAndRefreshRecovers (new)
- capturesRealSourceUpdateMatrixWithControlsAndCompleteLastSkillGeometry (new)

Expanded existing repository guards:

- unboundAccessRejectsEveryReadAndWriteWithoutCreatingInventory
- relayForeignHostAndDifferentConnectionToSameHostRejectEveryReadAndWrite
- replacementGenerationRejectsOldScopeAndClearsItsListedRevision
- generationChangedDuringRealHostDescribeRejectsInFlightReadsAndWrites
- generationReplacementCancelsWritesWaitingOnRealInventoryLockWithoutCommitting
- staleRevisionRejectsAllMutationKindsAcrossIndependentRealServices

## Screenshot source and verification handoff

8 PNG captures are implemented: 320×600 and 640×800, light/dark, before/after. Set HARNESS_PLUGIN_LOCAL_UPDATE_CAPTURE_DIR for parent execution. Worker generated 0 PNGs because tests are prohibited.

Source assertions cover actual root dimensions, controls, complete final-skill text, no ellipsis, measured scroll ends, marker bounds and clipping. Capture records PNG SHA and per-stage geometry. Assertions remain runtime-unverified.

Scoped git diff --check returned 0. All nine files are untracked, so each also received git diff --no-index --check against /dev/null: exit 1 for differences, no diagnostics. Baseline file hashes match the fixed manifest; existing test names remain present.

Recommended parent classes:

- im.hikaru.harness.bundle.desktop.DesktopUserPluginsTest
- im.hikaru.harness.desktop.plugins.DesktopInstalledPluginRepositoryTest
- im.hikaru.harness.desktop.PluginLocalUpdateInteractionTest
- PluginManagementInteractionTest
- PluginManagementControllerTest
- InstalledPluginsControllerTest

## Unresolved limits

- Compilation and runtime assertions are unverified by this worker; parent must freeze exact inputs and run the targeted suites.
- Eight PNGs and measured geometry artifacts require parent test execution; none were generated by the worker.
- Offscreen captures do not establish original pixel parity, remote host behavior or process-crash guarantees.

Write set is released. No further source or delivery writes follow this handoff.
