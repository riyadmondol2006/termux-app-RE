#!/usr/bin/env python3
"""
Prints the packages that apt-repo/packages.txt lists for an architecture, separated by spaces.
packages.txt has sections that start with a line like "[aarch64 arm]" naming the architectures
they are for; # starts a comment.

Usage: package-list.py <packages.txt> <arch>
"""

import re
import sys

ARCHITECTURES = ["aarch64", "arm", "i686", "x86_64"]


def main():
    if len(sys.argv) != 3 or sys.argv[2] not in ARCHITECTURES:
        sys.exit(__doc__)
    path, arch = sys.argv[1:]
    names, architectures = [], list(ARCHITECTURES)
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = re.sub(r"#.*", "", line).strip()
            header = re.fullmatch(r"\[([a-z0-9_ ]+)\]", line)
            if header:
                architectures = header.group(1).split()
                if set(architectures) - set(ARCHITECTURES):
                    sys.exit("Unknown architectures in " + line)
            elif arch in architectures:
                names += [n for n in line.split() if n not in names]
    print(" ".join(names))


if __name__ == "__main__":
    main()
