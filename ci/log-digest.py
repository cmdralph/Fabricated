#!/usr/bin/env python3
"""Summarizes a game log's errors (CI only): its ERROR and FATAL lines by logger (count, first message), its WARN
lines the same way (a mod that gives up on something often only warns), the exceptions it names (count), and the first
occurrence of each of the most frequent errors with its stack, so a mod that loads but fails when its code runs shows
why. Leaves out the errors every CI run has: the narrator's, the offline account's (Realms included), the missing
audio devices', an empty server.properties field's, the test mod that fails on purpose, and Cardinal Components'
first-run config.

Usage: ci/log-digest.py <latest.log>
"""
import re
import sys
from collections import Counter

LINE = re.compile(r"^\[[^]]*\] \[[^]]*/(ERROR|FATAL)\] \[([^]/]*)[^]]*\]: (.*)$")
WARN_LINE = re.compile(r"^\[[^]]*\] \[[^]]*/WARN\] \[([^]/]*)[^]]*\]: (.*)$")
ANY_LINE = re.compile(r"^\[[^]]*\] \[")
EXCEPTION = re.compile(r"(?:^|Caused by: )([a-z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_$]+)+(?:Exception|Error)(?:: .*)?)$")
NOISE = re.compile(r"[Nn]arrator|flite|401|[Rr]ealms|user properties|TargetDataLine|OpenAL|audio device|[Mm]icrophone|SoundSystem"
                   r"|sound manager|No key [a-z]+ in MapLike\[\{\}\]|intentional failure for Fabricated CI"
                   r"|cardinal-components-api.properties")
STACKS = 5
FRAMES = 12


def main():
    with open(sys.argv[1], encoding="utf-8", errors="replace") as f:
        lines = f.read().splitlines()
    counts = Counter()
    first = {}
    for i, line in enumerate(lines):
        m = LINE.match(line)
        if not m or NOISE.search(line):
            continue
        logger = m.group(2)
        counts[logger] += 1
        first.setdefault(logger, i)
    print("-- errors by logger (count, first message):")
    for logger, n in counts.most_common(40):
        print(f"{n:5d}  {logger}: {LINE.match(lines[first[logger]]).group(3)[:220]}")
    warnings = Counter()
    first_warning = {}
    for line in lines:
        m = WARN_LINE.match(line)
        if not m or NOISE.search(line):
            continue
        warnings[m.group(1)] += 1
        first_warning.setdefault(m.group(1), m.group(2))
    print("-- warnings by logger (count, first message):")
    for logger, n in warnings.most_common(25):
        print(f"{n:5d}  {logger}: {first_warning[logger][:220]}")
    print("-- exceptions (count):")
    exceptions = Counter(m.group(1)[:200] for line in lines if (m := EXCEPTION.search(line)) and not NOISE.search(line))
    for exception, n in exceptions.most_common(30):
        print(f"{n:5d}  {exception}")
    # The stack that follows an error's first line: its exception, frames and causes, up to the next log line.
    for logger, n in counts.most_common(STACKS):
        stack = []
        for line in lines[first[logger] + 1:]:
            if ANY_LINE.match(line):
                break
            stack.append(line)
        if not stack:
            continue
        print(f"-- first {logger} error with its stack ({n} in all):")
        print("  " + lines[first[logger]][:300])
        frames = 0
        for line in stack:
            if line.lstrip().startswith("at "):
                frames += 1
                if frames > FRAMES:
                    continue
            else:
                frames = 0
            print("    " + line.strip()[:250])


if __name__ == "__main__":
    main()
