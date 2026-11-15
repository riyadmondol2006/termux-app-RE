#!/usr/bin/env python3
"""
Writes the bootstrap/ directory of the GitHub Pages site, which hosts the bootstrap archives that
app builds embed:

    bootstrap/latest                     the id of the newest bootstrap
    bootstrap/ids                        the ids that are kept, newest first
    bootstrap/<id>/bootstrap-<arch>.zip
    bootstrap/<id>/SHA256SUMS            checksums of the zips
    bootstrap/<id>/SHA256SUMS.asc        detached signature of SHA256SUMS with the repository key

Every deploy replaces the whole site, so the bootstraps of the published site are copied over,
after checking the signature of their SHA256SUMS and the checksum of every zip, and an optional
new bootstrap is added. Only the newest --keep bootstraps are kept. The new bootstrap's
SHA256SUMS still needs to be signed; its path is printed on the last line.

Usage: site-bootstraps.py --site-url <url> --keyring <public key> --out <dir> [--keep <n>]
                          [--new-id <id> --new-dir <dir with bootstrap-<arch>.zip files>]
"""

import argparse
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request

ARCHITECTURES = ["aarch64", "arm", "i686", "x86_64"]


def fail(message):
    print("site-bootstraps.py: " + message, file=sys.stderr, flush=True)
    sys.exit(1)


def fetch(url, retries=5):
    """Returns the content of url, or None on 404, bypassing the cache of GitHub Pages."""
    url += "?nocache=" + os.urandom(8).hex()
    error = None
    for _ in range(retries):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": "termux-re-site-bootstraps", "Cache-Control": "no-cache"})
            with urllib.request.urlopen(request, timeout=120) as response:
                return response.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            error = e
        except OSError as e:
            error = e
    fail("Failed to download %s: %s" % (url, error))


def checksums(data):
    result = {}
    for line in data.decode("utf-8").splitlines():
        m = re.fullmatch(r"([0-9a-f]{64}) [ *](bootstrap-[a-z0-9_]+\.zip)", line.strip())
        if m:
            result[m.group(2)] = m.group(1)
    return result


def copy_published(site_url, keyring, bootstrap_id, out):
    base = "%s/bootstrap/%s" % (site_url, bootstrap_id)
    sums = fetch(base + "/SHA256SUMS")
    signature = fetch(base + "/SHA256SUMS.asc")
    if sums is None or signature is None:
        fail("%s/SHA256SUMS or its signature is missing" % base)
    with tempfile.TemporaryDirectory() as tmp:
        for name, data in (("SHA256SUMS", sums), ("SHA256SUMS.asc", signature)):
            with open(os.path.join(tmp, name), "wb") as f:
                f.write(data)
        result = subprocess.run(["gpgv", "--keyring", keyring, os.path.join(tmp, "SHA256SUMS.asc"),
                                 os.path.join(tmp, "SHA256SUMS")], capture_output=True, text=True)
        if result.returncode != 0:
            fail("The signature of %s/SHA256SUMS is not valid:\n%s" % (base, result.stderr))
    expected = checksums(sums)
    if sorted(expected) != sorted("bootstrap-%s.zip" % a for a in ARCHITECTURES):
        fail("%s/SHA256SUMS does not list the bootstraps of all architectures" % base)
    target = os.path.join(out, bootstrap_id)
    os.makedirs(target)
    for name, checksum in sorted(expected.items()):
        data = fetch(base + "/" + name)
        if data is None or hashlib.sha256(data).hexdigest() != checksum:
            fail("%s/%s is missing or does not match SHA256SUMS" % (base, name))
        with open(os.path.join(target, name), "wb") as f:
            f.write(data)
    for name, data in (("SHA256SUMS", sums), ("SHA256SUMS.asc", signature)):
        with open(os.path.join(target, name), "wb") as f:
            f.write(data)
    print("Kept bootstrap %s" % bootstrap_id)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--site-url", required=True)
    parser.add_argument("--keyring", required=True)
    parser.add_argument("--out", required=True, help="the bootstrap/ directory to write, must not exist")
    parser.add_argument("--keep", type=int, default=2, help="number of bootstraps to keep, including the new one")
    parser.add_argument("--new-id")
    parser.add_argument("--new-dir")
    args = parser.parse_args()

    site_url = args.site_url.rstrip("/")
    keyring = os.path.abspath(args.keyring)
    if bool(args.new_id) != bool(args.new_dir):
        fail("--new-id and --new-dir go together")
    if args.new_id and not re.fullmatch(r"[0-9A-Za-z][0-9A-Za-z._-]*", args.new_id):
        fail("Invalid bootstrap id '%s'" % args.new_id)
    if os.path.exists(args.out):
        fail(args.out + " already exists")
    os.makedirs(args.out)

    published = fetch(site_url + "/bootstrap/ids")
    ids = published.decode("utf-8").split() if published is not None else []
    if args.new_id:
        if args.new_id in ids:
            fail("Bootstrap %s was published before" % args.new_id)
        ids.insert(0, args.new_id)
    ids = ids[:args.keep]

    signing = None
    for bootstrap_id in ids:
        if bootstrap_id != args.new_id:
            copy_published(site_url, keyring, bootstrap_id, args.out)
            continue
        target = os.path.join(args.out, bootstrap_id)
        os.makedirs(target)
        lines = []
        for arch in ARCHITECTURES:
            name = "bootstrap-%s.zip" % arch
            source = os.path.join(args.new_dir, name)
            if not os.path.isfile(source):
                fail("%s is missing" % source)
            shutil.copyfile(source, os.path.join(target, name))
            with open(source, "rb") as f:
                lines.append("%s  %s" % (hashlib.sha256(f.read()).hexdigest(), name))
        signing = os.path.join(target, "SHA256SUMS")
        with open(signing, "w", encoding="utf-8") as f:
            f.write("".join(line + "\n" for line in lines))
        print("Added bootstrap %s" % bootstrap_id)

    if ids:
        with open(os.path.join(args.out, "ids"), "w", encoding="utf-8") as f:
            f.write("".join(i + "\n" for i in ids))
        with open(os.path.join(args.out, "latest"), "w", encoding="utf-8") as f:
            f.write(ids[0] + "\n")
    if signing:
        print(signing)


if __name__ == "__main__":
    main()
