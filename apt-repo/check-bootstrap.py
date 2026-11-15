#!/usr/bin/env python3
"""
Checks a bootstrap archive assembled from the Termux RE package repository before it is
released, and prints a markdown report:

- It was built for the app package name, and apt uses and trusts the Termux RE repository:
  sources.list, the repository key and its symlink, and every termux-tools mirror.
- Every package in it has the version that the repository's (verified) Packages index lists, so
  it matches the repository it was assembled from.
- Optionally, its package list is compared with another bootstrap, like the official one, which
  is reported but doesn't fail the check.

Usage: check-bootstrap.py <bootstrap zip> <app package name> <repository url> <public key>
                          <Packages index> [<bootstrap zip to compare with>]
"""

import re
import sys
import zipfile


def stanzas(text):
    result = {}
    for block in re.split(r"\n\s*\n", text.strip()):
        fields = {}
        for line in block.split("\n"):
            if line and line[0] not in " \t" and ":" in line:
                name, _, value = line.partition(":")
                fields[name] = value.strip()
        if "Package" in fields:
            result[fields["Package"]] = fields
    return result


def installed(zip_file):
    with zipfile.ZipFile(zip_file) as z:
        return stanzas(z.read("var/lib/dpkg/status").decode("utf-8"))


def main():
    if len(sys.argv) not in (6, 7):
        sys.exit(__doc__)
    zip_path, package_name, repo_url, key_path, packages_path = sys.argv[1:6]
    other = sys.argv[6] if len(sys.argv) == 7 else None
    prefix = "/data/data/%s/files/usr" % package_name
    errors = []

    with zipfile.ZipFile(zip_path) as z:
        names = set(z.namelist())
        read = lambda name: z.read(name).decode("utf-8") if name in names else None

        login = (read("bin/login") or "").split("\n", 1)[0]
        if not login.startswith("#!" + prefix + "/"):
            errors.append("bin/login starts with '%s', not built for %s" % (login, package_name))

        sources = read("etc/apt/sources.list")
        if sources != "deb %s stable main\n" % repo_url:
            errors.append("etc/apt/sources.list is %r" % sources)

        with open(key_path, "rb") as f:
            key = f.read()
        key_file = "share/termux-keyring/termux-re.gpg"
        if key_file not in names or z.read(key_file) != key:
            errors.append(key_file + " is missing or not the repository key")
        link = "%s/share/termux-keyring/termux-re.gpg←./etc/apt/trusted.gpg.d/termux-re.gpg" % prefix
        if link not in (read("SYMLINKS.txt") or "").splitlines():
            errors.append("etc/apt/trusted.gpg.d/termux-re.gpg is not linked to the repository key")

        # Scripts and configuration must not use the paths of another app, like com.termux.
        other_prefix = re.compile(r"/data/data/(?!%s/)[A-Za-z0-9_.]+/files/" % re.escape(package_name))
        for name in sorted(names):
            if not re.match(r"(bin|etc|libexec)/[^/].*[^/]$", name):
                continue
            data = z.read(name)
            if b"\0" in data[:4096]:
                continue
            for line in data.decode("utf-8", "replace").splitlines():
                if other_prefix.search(line) and not re.match(r"\s*(#|//|\*|/\*)", line):
                    errors.append("%s uses another app's paths: %s" % (name, line.strip()[:120]))
                    break

        mirrors = sorted(n for n in names if n.startswith("etc/termux/mirrors/") and not n.endswith("/"))
        if "etc/termux/mirrors/default" not in mirrors:
            errors.append("etc/termux/mirrors/default is missing")
        for mirror in mirrors:
            urls = re.findall(r'^\s*(?:MAIN|ROOT|X11)="?([^"\s]+)', read(mirror), re.M)
            if urls != [repo_url]:
                errors.append("%s points at %s" % (mirror, ", ".join(urls) or "nothing"))

    packages = installed(zip_path)
    with open(packages_path, encoding="utf-8") as f:
        index = stanzas(f.read())
    for name, fields in sorted(packages.items()):
        if name not in index:
            errors.append("%s is not in the repository" % name)
        elif index[name]["Version"] != fields.get("Version"):
            errors.append("%s is %s, the repository has %s" % (name, fields.get("Version"), index[name]["Version"]))

    print("**%s**: %d packages, %d mirror files" % (zip_path.rsplit("/", 1)[-1], len(packages), len(mirrors)))
    if other:
        others = installed(other)
        added = sorted(set(packages) - set(others))
        missing = sorted(set(others) - set(packages))
        print()
        print("Compared with %s (%d packages):" % (other.rsplit("/", 1)[-1], len(others)))
        print("- Only here: %s" % (", ".join(added) or "none"))
        print("- Only there: %s" % (", ".join(missing) or "none"))
    if errors:
        print()
        print("Errors:")
        for error in errors:
            print("- " + error)
        sys.exit(1)


if __name__ == "__main__":
    main()
