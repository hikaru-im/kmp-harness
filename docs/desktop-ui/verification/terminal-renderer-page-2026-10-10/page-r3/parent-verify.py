from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

REPO = Path('/Volumes/Data/IdeaProjects/kmp-harness')
BASELINE = Path('/tmp/harness-terminal-renderer-page-baseline-20261010')
DELIVERY = Path('/tmp/harness-terminal-renderer-page-delivery-20261010.json')
PAGE_CAPTURE_DIRECTORY = Path('/tmp/harness-terminal-renderer-page-captures-20261010')
PAGE_CAPTURE_SUITE = 'im.hikaru.harness.desktop.PtyTerminalPageInteractionTest'
# Existing, clean Git files for the separate pnpm frontend; the Kotlin baseline
# intentionally omits them. Preserve their verified bytes when checking the repo.
NON_KOTLIN_FRONTEND_YAML = {
    'backend/ui/pnpm-lock.yaml': 'fc4b9cb193289bd5c7bafbd21c11c5b357767dab2282b1dd797fac5a67ddaaff',
    'backend/ui/pnpm-workspace.yaml': '3e0aa80b6c0105ac7274773d15015ce861a9ee102868f1c7d35bb7e168c7ba20',
    'backend/ui/tea.yaml': '8ac4008ca51aa1859bd7106fe95aaacf1057900b04496413e23c03a215f2fa3b',
}
SUITES = (('session-api', 'im.hikaru.harness.session.api.TerminalColumnMappingContractTest', 3), ('exec-jvm', 'im.hikaru.harness.exec.jvm.JvmTerminalColumnMappingTest', 4), ('shared', 'im.hikaru.harness.client.panels.PtyTerminalControllerTest', 8), ('shared', 'im.hikaru.harness.client.panels.PtyTerminalGridTest', 4), ('shared', 'im.hikaru.harness.client.panels.TerminalTextInputBridgeTest', 4), ('jvm-app', 'im.hikaru.harness.desktop.terminal.DesktopTerminalLaunchProviderTest', 3), ('jvm-app', 'im.hikaru.harness.desktop.terminal.PtyTerminalConnectionIntegrationTest', 4), ('jvm-app', 'im.hikaru.harness.desktop.PtyTerminalPageInteractionTest', 6), ('session-api', 'im.hikaru.harness.session.api.SessionTerminalSemanticHostContractTest', 3), ('exec-jvm', 'im.hikaru.harness.exec.jvm.JvmSessionTerminalSemanticInputTest', 4), ('gateway', 'im.hikaru.harness.api.gateway.session.SessionTerminalSemanticInputGatewayTest', 5), ('jvm-app', 'im.hikaru.harness.desktop.connection.SessionTerminalSemanticInputConnectionTest', 6), ('session-api', 'im.hikaru.harness.session.api.TerminalInputContractTest', 4), ('exec-jvm', 'im.hikaru.harness.exec.jvm.JvmTerminalSemanticInputTest', 12), ('session-api', 'im.hikaru.harness.session.api.SessionTerminalHostContractTest', 4), ('exec-jvm', 'im.hikaru.harness.exec.jvm.JvmSessionTerminalHostTest', 8), ('gateway', 'im.hikaru.harness.api.gateway.session.SessionTerminalsGatewayTest', 6), ('jvm-app', 'im.hikaru.harness.desktop.connection.SessionTerminalsConnectionTest', 6), ('session-api', 'im.hikaru.harness.session.api.SessionTerminalsContractTest', 4), ('exec-jvm', 'im.hikaru.harness.exec.jvm.JvmPtyTerminalSessionsTest', 12), ('exec-jvm', 'im.hikaru.harness.exec.jvm.ExecProcessContractTest', 16), ('session-api', 'im.hikaru.harness.session.api.SessionApiContractTest', 1), ('session-api', 'im.hikaru.harness.session.api.SessionDescendantsContractTest', 5), ('gateway', 'im.hikaru.harness.api.gateway.session.SessionBackgroundMutationsGatewayTest', 6), ('jvm-app', 'im.hikaru.harness.desktop.connection.SessionBackgroundMutationsProcessTest', 2), ('jvm-app', 'im.hikaru.harness.desktop.ThreadPanelsInteractionTest', 9), ('jvm-app', 'im.hikaru.harness.desktop.TerminalReadingInteractionTest', 9), ('jvm-app', 'im.hikaru.harness.desktop.HarnessAppThreadPanelsShellTest', 14), ('shared', 'im.hikaru.harness.client.panels.ThreadPanelsStateTest', 12))
FIXED_METHODS = {'im.hikaru.harness.session.api.SessionTerminalHostContractTest': {'capabilityAndCreateResultsRejectIdentityMismatch', 'hostCreateUnknownRetainsOptionalKeyAndSerializes', 'legacySessionApiReturnsTypedUnavailableWithoutInventingOwner', 'hostResultsPreserveFullTerminalSnapshotIdentity'}, 'im.hikaru.harness.exec.jvm.JvmSessionTerminalHostTest': {'onlyExactLiveSessionCanDiscoverAndStart', 'discoveryAndInventoryNeverStartShellAndCapacityIsBounded', 'cancelBeforeDispatchStartsNothingAndAfterDispatchIsUnknown', 'naturalExitRetainsSnapshotUntilExplicitClose', 'realOwnerChainCreateWriteResizeReadClose', 'rootDisposeStopsOwnedProcessesAndWorkersAndFencesNewStarts', 'foreignRootAndStaleIncarnationCannotMutate', 'sameTextSessionReplacementRejectsOldOwnerAndRetainsCleanup'}, 'im.hikaru.harness.api.gateway.session.SessionTerminalsGatewayTest': {'registrationAndOwnerDisposalRemoveAllTerminalEndpoints', 'forwardsDiscoveryReadsAndEveryMutationExactlyOnce', 'mismatchedResponseIdentityBecomesRequestedUnknown', 'lostCreateAcknowledgementHasNoInventedKeyAndNoRetry', 'missingServiceAndLegacyDefaultsReturnTypedUnavailable', 'cancellationBeforeDispatchAndAfterDispatchRemainDistinct'}, 'im.hikaru.harness.desktop.connection.SessionTerminalsConnectionTest': {'localMissingEndpointAndRemoteDefaultsRemainUnavailable', 'localCreateLostAcknowledgementStartsExactlyOnceAndInventoryFindsIt', 'localMutationsRejectWrongResponseIdentityAndNeverRetry', 'localProductionRegistrationOwnsRealPtyEndToEnd', 'localDtosAndStyledSnapshotRoundTripWithoutNativeObjects', 'localCancellationAfterDeliveryPreservesUnknownAndOwnedCleanup'}, 'im.hikaru.harness.session.api.SessionTerminalsContractTest': {'snapshotAndSizeInvariantsAreEnforced', 'resultsRetainRequestedOwnerAndTerminalIdentity', 'blankIdentityComponentsAreRejected', 'terminalDtosRoundTripWithCompleteIdentity'}, 'im.hikaru.harness.exec.jvm.JvmPtyTerminalSessionsTest': {'staleScopeSessionIncarnationAndTerminalHaveNoSideEffects', 'readingDoesNotCreatePtyAndCapacityIsBounded', 'alternateScreenRestoresPrimary', 'scrollbackAndRawTailAreBounded', 'writeFailureAfterDeliveryIsUnknownAndNeverResent', 'realPtyAndResizeUpdateKernelAndEmulator', 'carriageReturnCursorAndEraseUpdateScreen', 'naturalExitKeepsFinalSnapshotUntilExplicitClose', 'invalidDimensionsInputAndCwdAreRejectedBeforeIo', 'realInputAndSplitUtf8RetainDecoderState', 'blockingReaderClosesAndOwnedWorkersFinish', 'splitAnsiKeepsParserAndSnapshotsDetached'}, 'im.hikaru.harness.exec.jvm.ExecProcessContractTest': {'processLimitRejectsLiveEntriesAndEvictsOnlyCompletedInventory', 'interactiveUtf8InputEofAndOwnerChecksUseTheRealProcess', 'foregroundCancellationKillsProcessAndCallCannotRespawn', 'wrongParentReplacementAndOldIncarnationCannotReadOutput', 'immediateHostDisposeAfterBackgroundTerminalConverges', 'missingAuthorityScopeMismatchWrongContextAndForeignHostCannotSpawn', 'realForegroundCommandPublishesUtf8CwdAndExactTerminalAfterEof', 'malformedRequestAndFailedStartNeverPublishRunning', 'blockedPipeTimesOutWithoutBlockingInventoryAndCanStillBeCancelled', 'backgroundReceiptDoesNotCompleteCommandAndReadsNeverSpawn', 'nonzeroExitAndMergedStderrRemainRealToolFailure', 'timeoutIsFailureAndHostDisposeKillsOwnedBackgroundProcess', 'realCwdConstraintRejectsTraversalAndOutsideSymlinkAndMissingCwd', 'boundedOutputKeepsDrainingAndAdvancesRevisionEvenWithIdenticalTail', 'defaultCommandsHaveClosedStdinAndNeverExposeInputCapability', 'disposeOfBlockedReaderWaitsForRealExitAndRepeatedDisposeConverges'}, 'im.hikaru.harness.session.api.SessionApiContractTest': {'createPromptHistoryAndCancelUseSessionLog'}, 'im.hikaru.harness.session.api.SessionDescendantsContractTest': {'delayedHistoryRejectsReplacementOfParentIntermediateOrChildEvenWithTheSameIds', 'historyFailureAndCancellationAreNotConvertedToUnavailableOrEmpty', 'onlyVerifiedSubagentChainsInTheSameLiveRegistryAreListed', 'delayedHistoryRejectsDisappearingAncestorAndClosedRegisteredSession', 'genuineEmptyAndRealChildHistoryRemainDistinctFromMissingCapability'}, 'im.hikaru.harness.api.gateway.session.SessionBackgroundMutationsGatewayTest': {'registrationAndOwnerDisposalRemoveAllThreeMutations', 'actualCallerCancellationInterruptsEachMutationWithoutRetry', 'forwardsExactInputAndAllTypedResultsOnce', 'serviceFailureAndServiceTimeoutAreUnknownWithoutRetryOrPrivateDetails', 'everyVariantMustMatchBothRequestedIdentities', 'missingServiceAndLegacyDefaultsReturnTypedUnavailable'}, 'im.hikaru.harness.desktop.connection.SessionBackgroundMutationsProcessTest': {'localGatewayCancelStopsTheRealOwnedChildAndRetainsItsOutput', 'localGatewayChainDeliversUtf8BytesAndEofToTheExactRealProcess'}, 'im.hikaru.harness.session.api.TerminalInputContractTest': {'keyPressCharacterInvariantsAreEnforced', 'semanticResultRetainsRequestedIdentityAndByteCount', 'legacyServiceSemanticInputIsTypedUnavailable', 'semanticInputsRoundTripWithoutPlatformObjects'}, 'im.hikaru.harness.exec.jvm.JvmTerminalSemanticInputTest': {'realControlAndAltCharactersReachPtyExactlyOnce', 'finalByteBudgetRejectionKeepsInputOpen', 'realPasteNormalizesNewlinesAndHonorsBracketedMode', 'realEnterBackspaceTabAndEscapeUseCurrentModes', 'semanticInputRejectsEveryStaleIdentityBeforeDelivery', 'boundedWorkerRejectsOverflowAndParserResizeDoNotDeadlock', 'semanticCancellationBeforeAndAfterDeliveryRemainDistinct', 'softAndHardResetPreservePinnedParserInputPolicy', 'lostSemanticAcknowledgementFencesWithoutRetry', 'committedUnicodeIsWholeAndNotLocallyEchoed', 'realFunctionAndKeypadModesUseIndependentEncoders', 'realParserArrowModesAndModifiedThenPlainAreIsolated'}, 'im.hikaru.harness.session.api.SessionTerminalSemanticHostContractTest': {'semanticEndpointPayloadAndCapturedModifiersRetainIdentity', 'defaultSessionApiForwardsSemanticInputExactlyOnce', 'legacyHostAndSessionApiSemanticInputAreTypedUnavailable'}, 'im.hikaru.harness.exec.jvm.JvmSessionTerminalSemanticInputTest': {'hostCapturesModifiersAndDisposesOwnedPtyWorkers', 'hostSemanticCancellationAndNativeFailureHaveDistinctFences', 'realHostSemanticInputUsesParserModesAndExactByteCounts', 'staleOwnerAndSessionReplacementRejectSemanticDispatch'}, 'im.hikaru.harness.api.gateway.session.SessionTerminalSemanticInputGatewayTest': {'foreignAcknowledgementsUseRequestedFullIdentity', 'semanticCancellationAndModifierCaptureHaveNoRetry', 'missingLegacyAndDisposedInputEndpointAreUnavailable', 'serviceReplacementAndLostAcknowledgementRemainUnknown', 'forwardsSemanticVariantsAndEveryResultExactlyOnce'}, 'im.hikaru.harness.desktop.connection.SessionTerminalSemanticInputConnectionTest': {'portableSemanticDtosMapBothWaysWithoutPlatformObjects', 'localSemanticNativeFailureFencesWithoutRetry', 'localSemanticRejectsStaleFullIdentityBeforeWrite', 'missingEndpointAndRemoteSemanticDefaultsRemainUnavailable', 'localSemanticCancellationAndLostAcknowledgementRemainUnknown', 'localSemanticInputOwnsRealPtyEndToEnd'}, 'im.hikaru.harness.session.api.TerminalColumnMappingContractTest': {'mappingCollectionsAreDetachedAcrossSnapshots', 'mappingInvariantsAndLegacyJsonRemainCompatible', 'wideContinuationAndSurrogateOffsetsRetainNativeColumns'}, 'im.hikaru.harness.exec.jvm.JvmTerminalColumnMappingTest': {'realParserMapsAsciiCjkEmojiAndNulToNativeColumns', 'realMappedSnapshotsRemainDetachedAndCleanupIsOwned', 'realStylesHistoryAndLeadingContinuationKeepOffsets', 'realResizeTruncationPreservesNativeSpansAndValidUnicode'}, 'im.hikaru.harness.client.panels.PtyTerminalControllerTest': {'explicitCreateDispatchesOnceAndUnknownDoesNotRetry', 'resizeDeduplicatesAndExitRetainsFinalScreen', 'orderedBoundedInputRejectsOverflowWithoutReplayingEvents', 'unknownFencesInputAndResizeButAllowsExplicitClose', 'ownerConnectionAndFullKeyFenceLateResults', 'closingViewStopsObservationWithoutClosingProcess', 'openingRefreshingAndRebindingNeverCreateTerminal', 'snapshotRevisionHighWaterRejectsStaleOrConflictingData'}, 'im.hikaru.harness.client.panels.PtyTerminalGridTest': {'historyAlternateWrappedCopyAndCursorStayConsistent', 'glyphSelectionAndCopyUseNativeOffsets', 'viewportMetricsClampAndDeduplicateActualSizes', 'legacyMappingFallbackIsAsciiOnly'}, 'im.hikaru.harness.client.panels.TerminalTextInputBridgeTest': {'pasteAndPlatformShortcutsHaveDistinctSemanticActions', 'cancelBlurAndOwnerResetDiscardComposition', 'compositionKeysAndKeyUpCannotDoubleWrite', 'committedUnicodeIsOneTextAndPreeditHasNoWrite'}, 'im.hikaru.harness.desktop.terminal.DesktopTerminalLaunchProviderTest': {'realLocalBindingAndExecutableShellPrepareWithoutSpawn', 'foreignConnectionRelayAndUnsupportedPlatformAreUnavailable', 'shellFallbackAndEnvironmentAreCapturedWithoutDisclosure'}, 'im.hikaru.harness.desktop.terminal.PtyTerminalConnectionIntegrationTest': {'typedMappedScreenHistoryAndKernelResizeReachPageModel', 'lostAcknowledgementAndOwnerReplacementNeverReplayNativeInput', 'hiddenViewReadAndExitRetentionNeverSpawnOrKill', 'controllerCreateTextPasteAndKeysReachExactRealPty'}, 'im.hikaru.harness.desktop.PtyTerminalPageInteractionTest': {'fullHarnessAppOpensReadOnlyAndCreatesOnlyOnRealClick', 'historySelectionCopyAndExitedScreenRemainVisible', 'ownerSwitchHidesOldTerminalAndPreservesOtherPanels', 'focusedTypingPasteKeysAndResizeUseRealController', 'wideAndCompactLightDarkMappedGridRemainUsable', 'unknownUnavailableAndViewCloseHaveHonestActions'}, 'im.hikaru.harness.desktop.ThreadPanelsInteractionTest': {'compactProcessSelectRoutesInputToExactKeyAndDoesNotReuseAnotherDraft', 'refreshAndErrorRetryReadThroughRealControllerAndDisablePendingActions', 'compactLongTitleAndTranscriptRemainReachableByScrolling', 'sameKeyRefreshPreservesUnknownDraftAfterEarlierDeliveryReceipt', 'keyboardSwitchAndCloseTabsClearOnlyLocalPanelState', 'readOnlyDetailNeverMountsConsumerAndBothAuthorizationsAreRequired', 'selectBackAndShowMoreReduceActualPanelActions', 'eofUsesExactProcessAndClosingViewDoesNotCancelIt', 'terminalInputConfirmsDeliveryAndUncertainReplyPreservesDraftInCompactPanel'}, 'im.hikaru.harness.desktop.TerminalReadingInteractionTest': {'sameKeyLoadingErrorRetryPreservesBothAxesAndIdenticalBufferRevisionDoesNotSteal', 'pendingReadIsFencedByOwnerChangeAndTabClose', 'sameIdentityResizeClampsTheUpReaderAndKeepsTheBottomReaderAtTheTail', 'firstLongOutputShowsTheTailThenUpwardScrollSurvivesAppendAndBottomFollowsAgain', 'realHorizontalWheelReachesTheEndMarkerAndFullTextIsNotOmitted', 'capturesTerminalReadingMatrix', 'nonPrefixReplacementClampsAndReadyEmptyDiffersFromUnavailable', 'realUpwardScrollDuringPendingFollowCancelsFollowingForLaterAppendsToo', 'tabSwitchPreservesWhileSelectionIdentityChangeAndOwnerChangeReset'}, 'im.hikaru.harness.desktop.HarnessAppThreadPanelsShellTest': {'fullShell1440x900Light', 'latePromptFailureCannotLeakOrClearDraftsAfterPointerRouteSelection', 'fullShell600x800Light', 'createRouteSameIdAndAcceptedSendCannotResurrectHomeOrSessionDraft', 'editsDuringCreateTransferToRealSessionAndRevisionFencesSameTextEdits', 'fullShell1024x768ResizedNavigatorDark', 'hostIdentityAndLogoutClearEveryDraftScope', 'fullShell600x800Dark', 'fullShell1024x768ResizedNavigatorLight', 'fullShell1440x900Dark', 'sessionAndUncreatedDraftsRestoreAcrossSameOwnerReconnect', 'acceptedExistingSessionSendClearsOnlyItsScope', 'fullShell1024x768Dark', 'fullShell1024x768Light'}, 'im.hikaru.harness.client.panels.ThreadPanelsStateTest': {'closeSelectsNeighbourAndFencesResponsesAcrossReopen', 'activeAndDoneLimitsAndUnknownStatusStayTruthful', 'invalidAncestryAndMismatchedSnapshotsAreRejected', 'terminalAcceptsOnlyNewerExternalRevision', 'interactionRequiresBothRealAuthorizationsAndRevocationTakesEffect', 'selectingWithoutReadableHistoryStaysUnavailable', 'rapidSelectionAndRemovalFenceDetailResults', 'widthPolicyKeepsReferenceMinimumWithinCompactHost', 'scopedLoadsAndLateResponsesAreIgnored', 'foreignActionsAndNestedPayloadScopesCannotPublish', 'loadingErrorAndSuccessfulEmptyRemainDistinct', 'defaultStateIsExplicitlyUnavailable'}}
MODULE_ROOTS = {'gateway': 'modules/api/gateway', 'jvm-app': 'apps/jvm-app', 'shared': 'apps/shared'}
NATIVE_SUITES = {'im.hikaru.harness.desktop.PtyTerminalPageInteractionTest', 'im.hikaru.harness.exec.jvm.JvmTerminalSemanticInputTest', 'im.hikaru.harness.desktop.HarnessAppThreadPanelsShellTest', 'im.hikaru.harness.desktop.TerminalReadingInteractionTest', 'im.hikaru.harness.desktop.terminal.PtyTerminalConnectionIntegrationTest', 'im.hikaru.harness.desktop.ThreadPanelsInteractionTest', 'im.hikaru.harness.exec.jvm.JvmSessionTerminalHostTest', 'im.hikaru.harness.desktop.terminal.DesktopTerminalLaunchProviderTest', 'im.hikaru.harness.exec.jvm.JvmTerminalColumnMappingTest', 'im.hikaru.harness.desktop.connection.SessionTerminalSemanticInputConnectionTest', 'im.hikaru.harness.desktop.connection.SessionTerminalsConnectionTest', 'im.hikaru.harness.exec.jvm.JvmPtyTerminalSessionsTest', 'im.hikaru.harness.exec.jvm.JvmSessionTerminalSemanticInputTest'}
EXPECTED_INPUTS = 2253
EXPECTED_TESTS = 184


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + chr(10))


def paths(attempt):
    assert re.fullmatch(r'[a-z0-9-]+', attempt)
    prefix = '/tmp/harness-terminal-renderer-page-'
    return (Path(prefix + 'source-20261010-' + attempt),
            Path(prefix + 'build-20261010-' + attempt),
            Path(prefix + 'evidence-20261010-' + attempt))


def check(root, inputs, allowed=()):
    for name, digest in inputs.items():
        if name not in allowed:
            assert (root / name).is_file() and sha(root / name) == digest, 'Input drift: ' + name
    expected = set(inputs) | set(allowed)
    actual = set()
    folders = {name.split('/')[0] for name in expected if '/' in name}
    excluded = {'build', '.git', 'node_modules', '__pycache__'}
    for folder in folders:
        for directory, subfolders, names in os.walk(root / folder):
            subfolders[:] = [name for name in subfolders if name not in excluded]
            for name in names:
                path = Path(directory) / name
                if path.suffix in ('.kt', '.yaml') and path.is_file():
                    relative = str(path.relative_to(root))
                    if relative in NON_KOTLIN_FRONTEND_YAML:
                        assert sha(path) == NON_KOTLIN_FRONTEND_YAML[relative], 'Frontend input drift: ' + relative
                    else:
                        actual.add(relative)
    for path in root.iterdir():
        if path.is_file() and (path.suffix in ('.kt', '.yaml') or path.name == 'kotlin'):
            actual.add(path.name)
    assert actual == expected, 'Input set drift: ' + str(dict(added=sorted(actual - expected), missing=sorted(expected - actual)))


def freeze(attempt):
    active = subprocess.run(['ps', '-axo', 'pid,command'], capture_output=True, text=True, check=True).stdout.splitlines()
    assert not any('CodexCLI.app/Contents/MacOS/codex ' in row and '/tmp/harness-terminal-renderer-page-worker-result-' in row for row in active), 'Worker still owns the write set'
    baseline = json.loads((BASELINE / 'baseline.json').read_text())
    delivery = json.loads(DELIVERY.read_text())
    assert delivery['model'] == 'SHUAIAPI/gpt-6.1-sol' and delivery['provider'] == 'opencodex'
    for field in ('builds_run', 'tests_run', 'screenshots_run', 'native_execution_run'):
        assert delivery[field] is False
    for field in ('write_set_released', 'source_writes_stopped'):
        assert delivery[field] is True
    assert delivery['baseline_sha256'] == sha(BASELINE / 'baseline.json')
    assert delivery['contract_sha256'] == baseline['contract_sha256'] == sha(BASELINE / 'contract.md')
    live_contract = REPO / 'docs/desktop-ui/reverse-engineering/terminal-renderer-page-contract-2026-10-10.md'
    assert sha(live_contract) == baseline['contract_sha256']
    allowed = {row['path']: row['before_sha256'] for row in baseline['files']}
    records = []
    for original in delivery.get('files') or delivery.get('changed_files') or []:
        path = Path(original['path'])
        if path.is_absolute():
            path = path.relative_to(REPO)
        assert '..' not in path.parts and (REPO / path).resolve() == REPO / path
        records.append(dict(original, path=str(path)))
    assert len(records) == 22 and {row['path'] for row in records} == set(allowed)
    for row in records:
        assert row['before_sha256'] == allowed[row['path']]
        assert row['after_sha256'] == sha(REPO / row['path'])
    check(REPO, baseline['inputs'], allowed)
    check(Path(baseline['base_source']), baseline['inputs'])
    source, build, evidence = paths(attempt)
    assert not source.exists() and not build.exists() and not evidence.exists()
    shutil.copytree(baseline['base_source'], source, symlinks=True,
                    ignore=shutil.ignore_patterns('build', '.git', 'node_modules', '__pycache__', 'captures'))
    for row in records:
        target = source / row['path']
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(REPO / row['path'], target)
    inputs = {str(path.relative_to(source)): sha(path) for path in sorted(source.rglob('*'))
              if path.is_file() and (path.suffix in ('.kt', '.yaml') or path.name == 'kotlin')}
    assert len(inputs) == EXPECTED_INPUTS
    for module, name, expected in SUITES:
        filename = source / MODULE_ROOTS.get(module, 'modules/' + module) / 'test' / (name.replace('.', '/') + '.kt')
        text = filename.read_text()
        methods = re.findall(r'@Test\s+(?:public\s+)?fun\s+(\w+)\s*\(', text)
        assert len(methods) == expected and len(set(methods)) == expected, str(filename)
        assert len(re.findall(r'@Test\b', text)) == expected
        if name in FIXED_METHODS:
            assert set(methods) == FIXED_METHODS[name], 'Fixed method names differ: ' + name
    manifest = dict(source=str(source), build=str(build), evidence=str(evidence), inputs=inputs,
                    baseline_sha256=sha(BASELINE / 'baseline.json'), delivery_sha256=sha(DELIVERY),
                    overlays=list(allowed), normalized_delivery_files=records, suites=SUITES, model=delivery['model'], provider=delivery['provider'],
                    separate_frontend_yaml_preserved=NON_KOTLIN_FRONTEND_YAML)
    write(Path(str(source) + '-manifest.json'), manifest)
    shutil.copytree(baseline['base_build'], build, symlinks=True, ignore=shutil.ignore_patterns('docs'))
    (source / 'docs/desktop-ui').mkdir(parents=True, exist_ok=True)
    (build / 'docs').symlink_to(source / 'docs', target_is_directory=True)
    evidence.mkdir()
    shutil.copy2(DELIVERY, evidence / 'delivery.json')
    shutil.copy2(DELIVERY.with_suffix('.md'), evidence / 'delivery.md')
    shutil.copy2(Path(str(source) + '-manifest.json'), evidence / 'source-manifest.json')
    shutil.copy2(BASELINE / 'baseline.json', evidence / 'baseline.json')
    shutil.copy2(BASELINE / 'contract.md', evidence / 'contract.md')
    shutil.copy2(Path(__file__), evidence / 'parent-verify.py')
    print(json.dumps(dict(source=str(source), inputs=len(inputs), suites=len(SUITES), expected_tests=EXPECTED_TESTS)), flush=True)


def run(attempt):
    source, build, evidence = paths(attempt)
    manifest = json.loads(Path(str(source) + '-manifest.json').read_text())
    environment = os.environ.copy()
    environment['KOTLIN_CLI_JAVA_OPTIONS'] = '-Duser.home=/tmp/harness-plugin-kotlin-user-home-20261009'
    native = evidence / 'native-tmp'
    native.mkdir(exist_ok=True)
    # Only the native suite needs these JVM flags. Legacy exec fixtures inherit
    # their environment and merge stderr, so JAVA_TOOL_OPTIONS would become
    # observable child output and invalidate their exact-output assertions.
    environment.pop('JAVA_TOOL_OPTIONS', None)
    native_options = '-Djava.awt.headless=true -Djava.io.tmpdir=' + str(native)
    write(evidence / 'environment.json', dict(
        KOTLIN_CLI_JAVA_OPTIONS=environment['KOTLIN_CLI_JAVA_OPTIONS'],
        native_suite_only_JAVA_TOOL_OPTIONS=native_options,
        legacy_exec_inherits_no_JAVA_TOOL_OPTIONS=True, native_suites=sorted(NATIVE_SUITES)))
    results = []
    for module, name, expected in SUITES:
        check(source, manifest['inputs'])
        folder = evidence / name.rsplit('.', 1)[-1]
        folder.mkdir(exist_ok=False)
        command = ['./kotlin', 'test', '--build-dir=' + str(build), '-p', 'jvm', '-m', module,
                   '--include-classes=' + name]
        started = time.time()
        print('START', name, flush=True)
        test_environment = environment.copy()
        if name in NATIVE_SUITES:
            test_environment['JAVA_TOOL_OPTIONS'] = native_options
        if name == PAGE_CAPTURE_SUITE:
            assert not PAGE_CAPTURE_DIRECTORY.exists(), 'Existing page captures must be preserved before running a new batch'
        try:
            with (folder / 'command.log').open('w') as log:
                result = subprocess.run(command, cwd=source, env=test_environment, stdout=log, stderr=subprocess.STDOUT)
        finally:
            if name == PAGE_CAPTURE_SUITE and PAGE_CAPTURE_DIRECTORY.exists():
                assert PAGE_CAPTURE_DIRECTORY.is_dir() and not PAGE_CAPTURE_DIRECTORY.is_symlink()
                shutil.move(str(PAGE_CAPTURE_DIRECTORY), str(folder / 'captures'))
        totals = dict(tests=0, failures=0, errors=0, skipped=0)
        reports, methods = [], []
        for xml in build.rglob('TEST-*.xml'):
            if xml.stat().st_mtime < started:
                continue
            suite = ET.parse(xml).getroot()
            if suite.tag != 'testsuite':
                continue
            cases = list(suite.iter('testcase'))
            assert len(cases) == int(suite.get('tests', 0))
            assert all(case.get('classname') == name for case in cases), str(xml)
            target = folder / xml.name
            assert not target.exists()
            shutil.copy2(xml, target)
            reports.append(str(xml))
            methods.extend(case.get('name') for case in cases)
            for field in totals:
                totals[field] += int(suite.get(field, 0))
            for case in cases:
                for issue in case:
                    if issue.tag in ('error', 'failure'):
                        print('FAIL', case.get('name'), issue.get('message', '')[:2000], flush=True)
        summary = dict(class_name=name, expected=expected, command=command, exit=result.returncode,
                       elapsed_seconds=round(time.time() - started, 2), totals=totals, reports=reports, methods=methods)
        write(folder / 'summary.json', summary)
        results.append(summary)
        write(evidence / 'test-results.json', dict(status='in-progress', suites=results))
        print('RESULT', json.dumps(summary), flush=True)
        check(source, manifest['inputs'])
        fixed_methods_match = (name not in FIXED_METHODS or
                               {method.removesuffix('()') for method in methods} == FIXED_METHODS[name])
        if (result.returncode or not fixed_methods_match
                or totals != dict(tests=expected, failures=0, errors=0, skipped=0)):
            write(evidence / 'test-results.json', dict(status='failed', suites=results))
            print(chr(10).join((folder / 'command.log').read_text().splitlines()[-55:]), flush=True)
            raise SystemExit(1)
    write(evidence / 'test-results.json', dict(status='passed', suites=results, tests=EXPECTED_TESTS))
    for module in ('exec-jvm', 'jvm-app'):
        command = ['./kotlin', 'show', 'dependencies', '-m', module, '-p', 'jvm', '--scope=runtime',
                   '--build-dir=' + str(build)]
        with (evidence / (module + '-runtime-dependencies.log')).open('w') as log:
            result = subprocess.run(command, cwd=source, env=environment, stdout=log, stderr=subprocess.STDOUT)
        assert result.returncode == 0, 'Dependency graph failed: ' + module
    command = ['./kotlin', 'build', '-m', 'jvm-app', '-p', 'jvm', '--build-dir=' + str(build)]
    print('START jvm-app build', flush=True)
    with (evidence / 'jvm-app-build.log').open('w') as log:
        result = subprocess.run(command, cwd=source, env=environment, stdout=log, stderr=subprocess.STDOUT)
    write(evidence / 'jvm-app-build.json', dict(command=command, exit=result.returncode))
    if result.returncode:
        print(chr(10).join((evidence / 'jvm-app-build.log').read_text().splitlines()[-55:]), flush=True)
        raise SystemExit(1)
    check(source, manifest['inputs'])
    check(REPO, manifest['inputs'])
    write(evidence / 'acceptance.json', dict(tests=EXPECTED_TESTS, suites=len(SUITES), failures=0, errors=0, skipped=0,
                                           inputs=len(manifest['inputs']), jvm_app_build_exit=0,
                                           model=manifest['model'], provider=manifest['provider'],
                                           workspace_inputs_verified=True, source_writes_stopped=True,
                                           host_local_integrated=True, standalone_semantic_input_integrated=True, host_semantic_input_integrated=True, terminal_page_integrated=True, offscreen_full_page_verified=False, screenshot_artifacts_pending_review=True, native_window_verified=False, native_ime_verified=False, remote_integrated=False, original_pixel_parity_verified=False))
    print('ACCEPTED', len(SUITES), 'classes /', EXPECTED_TESTS, 'tests; JVM app build; dependencies archived', flush=True)


def archive(attempt):
    source, build, evidence = paths(attempt)
    output = REPO / 'docs/desktop-ui/verification/terminal-renderer-page-2026-10-10' / attempt
    assert not output.exists()
    shutil.copytree(evidence, output, ignore=shutil.ignore_patterns('native-tmp'))
    manifest = json.loads(Path(str(source) + '-manifest.json').read_text())
    for name in manifest['overlays']:
        target = output / 'source' / (name + '.txt')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source / name, target)
    files = {str(path.relative_to(output)): sha(path) for path in sorted(output.rglob('*')) if path.is_file()}
    write(output / 'archive-sha256.json', files)
    print(json.dumps(dict(archive=str(output), files=len(files))), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('action', choices=('freeze', 'run', 'archive'))
    parser.add_argument('--attempt', required=True)
    args = parser.parse_args()
    globals()[args.action](args.attempt)
