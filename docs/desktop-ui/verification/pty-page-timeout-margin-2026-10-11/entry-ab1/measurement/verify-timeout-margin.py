#!/usr/bin/env python3
"""Re-runnable check for batch AB.

Asserts that every marginal real-PTY readiness gate in
apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt uses a bounded 30_000 ms
timeout and that no leftover 10_000 ms gate remains.
"""
import pathlib
import sys

TARGET = 'apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt'
WIDE = 'timeoutMillis = 30_000'
NARROW = 'timeoutMillis = 10_000'
MIN_WIDE = 23


def find_root(start):
    for parent in [start, *start.parents]:
        if (parent / TARGET).is_file():
            return parent
    raise SystemExit('repo root not found')


def main():
    root = find_root(pathlib.Path(__file__).resolve().parent)
    text = (root / TARGET).read_text(encoding='utf-8')
    wide = text.count(WIDE)
    narrow = text.count(NARROW)
    problems = 0
    print('file: ' + TARGET)
    print(WIDE + ': ' + str(wide))
    print(NARROW + ': ' + str(narrow))
    if wide < MIN_WIDE:
        print('PROBLEM: expected at least ' + str(MIN_WIDE) + ' widened waits, found ' + str(wide))
        problems += 1
    if narrow != 0:
        print('PROBLEM: ' + str(narrow) + ' leftover 10_000 ms waits remain')
        problems += 1
    print('PROBLEMS: ' + str(problems))
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
