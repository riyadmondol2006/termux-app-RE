#!/usr/bin/env python3
"""
Makes a local copy of one architecture of the published Termux RE package repository, with
everything checked against the signed InRelease file: the Packages index and every .deb in it.

termux-packages' generate-bootstraps.sh reads a repository without checking signatures or
checksums, and GitHub Pages can serve files of the previous deployment for a few minutes after
a publish, so the Build bootstraps workflow assembles bootstraps from this copy instead. Files
are downloaded with a query string that bypasses the Pages cache.

Usage: mirror-repo.py <repository url> <public key> <arch> <output dir>
"""

import concurrent.futures
import hashlib
import os
import re
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request


def fail(message):
    print("mirror-repo.py: " + message, file=sys.stderr, flush=True)
    sys.exit(1)


def fetch(url, retries=5):
    url += "?nocache=" + os.urandom(8).hex()
    error = None
    for _ in range(retries):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "termux-re-mirror-repo", "Cache-Control": "no-cache"})
            with urllib.request.urlopen(request, timeout=120) as response:
                return response.read()
        except OSError as e:  # URLError, TLS and connection errors
            error = e
    fail("Failed to download %s: %s" % (url, error))


def main():
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    url, keyring, arch, out = sys.argv[1].rstrip("/"), os.path.abspath(sys.argv[2]), sys.argv[3], sys.argv[4]

    with tempfile.TemporaryDirectory() as tmp:
        inrelease = os.path.join(tmp, "InRelease")
        with open(inrelease, "wb") as f:
            f.write(fetch(url + "/dists/stable/InRelease"))
        release = os.path.join(tmp, "Release")
        result = subprocess.run(["gpgv", "--keyring", keyring, "--output", release, inrelease], capture_output=True, text=True)
        if result.returncode != 0:
            fail("The signature of the InRelease file is not valid:\n" + result.stderr)
        with open(release, encoding="utf-8") as f:
            release_text = f.read()

    index = "main/binary-%s/Packages" % arch
    sha256 = re.search(r"^SHA256:\n((?: .*\n)+)", release_text, re.M)
    expected = [line.split()[0] for line in (sha256.group(1) if sha256 else "").splitlines() if line.split()[2:] == [index]]
    if not expected:
        fail("The Release file has no SHA256 for " + index)
    packages = fetch("%s/dists/stable/%s" % (url, index))
    if hashlib.sha256(packages).hexdigest() != expected[0]:
        fail(index + " does not match the signed Release file")
    os.makedirs(os.path.join(out, "dists/stable/main/binary-" + arch))
    with open(os.path.join(out, "dists/stable", index), "wb") as f:
        f.write(packages)

    debs = []
    for stanza in re.split(r"\n\s*\n", packages.decode("utf-8").strip()):
        fields = dict(re.findall(r"^(Filename|SHA256): (.+)$", stanza, re.M))
        if "Filename" not in fields or "SHA256" not in fields:
            fail("Incomplete entry in %s:\n%s" % (index, stanza[:300]))
        debs.append((fields["Filename"], fields["SHA256"]))

    def download(entry):
        filename, checksum = entry
        data = fetch(url + "/" + filename)
        if hashlib.sha256(data).hexdigest() != checksum:
            fail("%s does not match %s" % (filename, index))
        path = os.path.join(out, filename)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
        return len(data)

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        size = sum(executor.map(download, debs))
    print("Copied %s and %d packages (%d MB), all matching the signed InRelease file" % (index, len(debs), size // 1000000))


if __name__ == "__main__":
    main()
