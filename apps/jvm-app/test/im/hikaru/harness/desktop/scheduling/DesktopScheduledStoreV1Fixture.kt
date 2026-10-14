package im.hikaru.harness.desktop.scheduling

/** Original v1 shape: ledger and cursors are absent, including empty defaults. */
internal val desktopScheduledStoreV1Fixture = """
{
  "schemaVersion": 1,
  "owners": [{
    "owner": {"hostId": "local-test", "memberIdentity": null},
    "revision": 1,
    "tasks": [{
      "id": "task-1", "revision": 1, "name": "name", "prompt": "prompt",
      "target": {"type": "cron", "destination": {"type": "projectless"}},
      "schedule": {"type": "daily", "hour": 9, "minute": 0, "zoneId": "UTC"},
      "model": {"providerId": "test-provider", "modelId": "test-model"},
      "status": "Paused"
    }],
    "deletedTaskIds": []
  }]
}
""".trimIndent()
