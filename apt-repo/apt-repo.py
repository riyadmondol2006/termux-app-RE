#!/usr/bin/env python3
"""
Builds the Termux RE apt repository: merges newly built .deb files into the currently published
repository and writes the complete repository tree, ready to be signed and deployed.

The published repository is the only state. Its InRelease file is verified with the repository
public key, the Packages indexes are checked against it, and every .deb kept from it is checked
against its index, so nothing unsigned ends up in the new repository. The newest version of
each package and architecture is kept, a newly built .deb replaces a published one of the same
version.

Layout, the same as the official Termux repositories:

    dists/stable/Release                         (InRelease and Release.gpg are added by signing)
    dists/stable/main/binary-<arch>/Packages     (also .gz and .xz)
    dists/stable/Contents-<arch>.gz              (which package has which file)
    pool/main/<arch>/<package>_<version>_<arch>.deb

Only the packages that were asked for (the roots) and what they need at runtime (Depends and
Pre-Depends) are kept, per architecture: builds from source also produce build-only dependencies
and -static packages, which would not fit in the 1 GB that GitHub Pages allows. The roots are
the packages of apt-repo/packages.txt for each architecture, those of --roots files and those of
earlier publishes, which are kept in roots.txt in the repository. Build-only dependencies that
are missing are built again when needed. available.txt lists the architectures of every package,
for the message of pkg when a package is missing.

Like aptly, which hosts the official repositories, "all" architecture packages are listed in the
Packages index of every architecture and there is no binary-all index. termux-packages'
build-package.sh -i/-I expects that.

Usage:
    apt-repo.py --url <published repository url> --keyring <public key> --out <dir>
                [--new <dir with .deb files>]... [--package-list <packages.txt>]
                [--roots <file with "<package> <arch>" lines>]... [--remove <package>]...
                [--expect-existing] [--size-report <file>]
                [--index-page <file> --package-name <name> --app-repo-url <url>]
"""

import argparse
import concurrent.futures
import email.utils
import functools
import gzip
import hashlib
import html
import lzma
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import urllib.error
import urllib.request

ARCHITECTURES = ["aarch64", "arm", "i686", "x86_64"]
DISTRIBUTION = "stable"
COMPONENT = "main"
HASHES = [("MD5Sum", "md5"), ("SHA1", "sha1"), ("SHA256", "sha256"), ("SHA512", "sha512")]
USER_AGENT = "termux-re-apt-repo"


def log(message):
    print(message, flush=True)


def fail(message):
    print("apt-repo.py: " + message, file=sys.stderr, flush=True)
    sys.exit(1)


def fetch(url, retries=4):
    """Returns the content of url, or None on 404. Bypasses the cache of the GitHub Pages CDN,
    which would otherwise return files of an earlier deployment for a few minutes."""
    last_error = None
    url += ("&" if "?" in url else "?") + "nocache=" + os.urandom(8).hex()
    for _ in range(retries):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Cache-Control": "no-cache"})
            with urllib.request.urlopen(request, timeout=120) as response:
                return response.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            last_error = e
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            last_error = e
    fail("Failed to download %s: %s" % (url, last_error))


def file_hashes(path):
    digests = {name: hashlib.new(name) for _, name in HASHES}
    size = 0
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            size += len(chunk)
            for d in digests.values():
                d.update(chunk)
    return size, {name: d.hexdigest() for name, d in digests.items()}


def parse_stanzas(text):
    """Parses deb822 stanzas into lists of (field, value) keeping order and multiline values."""
    stanzas = []
    for block in re.split(r"\n\s*\n", text.strip("\n")):
        fields = []
        for line in block.split("\n"):
            if not line:
                continue
            if line[0] in " \t":
                if not fields:
                    fail("Invalid stanza: " + block[:200])
                fields[-1] = (fields[-1][0], fields[-1][1] + "\n" + line)
            else:
                name, sep, value = line.partition(":")
                if not sep:
                    fail("Invalid line '%s' in stanza" % line)
                fields.append((name, value.strip()))
        if fields:
            stanzas.append(fields)
    return stanzas


def field(fields, name, default=None):
    for n, v in fields:
        if n.lower() == name.lower():
            return v
    return default


def version_compare(a, b):
    """Compares Debian versions, returns <0, 0 or >0."""
    if a == b:
        return 0
    if subprocess.run(["dpkg", "--compare-versions", a, "lt", b]).returncode == 0:
        return -1
    return 1


def pool_path(package, version, architecture):
    upstream = version.split(":", 1)[1] if ":" in version else version
    name = re.sub(r"[^A-Za-z0-9.+~_-]", "_", "%s_%s_%s.deb" % (package, upstream, architecture))
    return "pool/%s/%s/%s" % (COMPONENT, architecture, name)


class Deb:
    def __init__(self, control, path, filename, size, hashes, source):
        self.control = control  # control stanza without Filename, Size and hashes
        self.path = path  # local file
        self.filename = filename  # path in the repository
        self.size = size
        self.hashes = hashes
        self.source = source  # "new" or "published"
        self.package = field(control, "Package")
        self.version = field(control, "Version")
        self.architecture = field(control, "Architecture")

    def stanza(self):
        lines = []
        for name, value in self.control:
            lines.append("%s: %s" % (name, value))
        lines.append("Filename: " + self.filename)
        lines.append("Size: %d" % self.size)
        for title, name in HASHES:
            lines.append("%s: %s" % ("MD5sum" if name == "md5" else title, self.hashes[name]))
        return "\n".join(lines) + "\n"


CONTROL_DROP = {"filename", "size", "md5sum", "sha1", "sha256", "sha512"}


def read_new_deb(path):
    result = subprocess.run(["dpkg-deb", "--field", path], capture_output=True, text=True)
    if result.returncode != 0:
        fail("Failed to read %s: %s" % (path, result.stderr.strip()))
    stanzas = parse_stanzas(result.stdout)
    if len(stanzas) != 1:
        fail("Unexpected control file in " + path)
    control = [(n, v) for n, v in stanzas[0] if n.lower() not in CONTROL_DROP]
    for required in ("Package", "Version", "Architecture"):
        if not field(control, required):
            fail("%s has no %s field" % (path, required))
    architecture = field(control, "Architecture")
    if architecture not in ARCHITECTURES + ["all"]:
        fail("%s has unsupported architecture %s" % (path, architecture))
    size, hashes = file_hashes(path)
    return Deb(control, path, pool_path(field(control, "Package"), field(control, "Version"), architecture),
               size, hashes, "new")


def read_published(url, keyring, expect_existing, workdir):
    """Returns the published packages as Deb objects without local files yet."""
    inrelease = fetch(url + "/dists/%s/InRelease" % DISTRIBUTION)
    if inrelease is None:
        if expect_existing:
            fail("The repository at %s has been published before but its InRelease file was not found. "
                 "Refusing to replace it with a repository that only has the new packages." % url)
        log("No repository published at %s yet" % url)
        return []

    inrelease_path = os.path.join(workdir, "InRelease")
    release_path = os.path.join(workdir, "Release.published")
    with open(inrelease_path, "wb") as f:
        f.write(inrelease)
    result = subprocess.run(["gpgv", "--keyring", os.path.abspath(keyring), "--output", release_path, inrelease_path],
                            capture_output=True, text=True)
    if result.returncode != 0:
        fail("The signature of %s/dists/%s/InRelease is not valid:\n%s" % (url, DISTRIBUTION, result.stderr))
    with open(release_path, encoding="utf-8") as f:
        release = parse_stanzas(f.read())[0]

    sha256 = {}
    for line in (field(release, "SHA256") or "").split("\n"):
        parts = line.split()
        if len(parts) == 3:
            sha256[parts[2]] = (parts[0], int(parts[1]))

    published = {}
    for arch in ARCHITECTURES:
        name = "%s/binary-%s/Packages" % (COMPONENT, arch)
        if name not in sha256:
            continue
        data = fetch(url + "/dists/%s/%s" % (DISTRIBUTION, name))
        if data is None or hashlib.sha256(data).hexdigest() != sha256[name][0]:
            fail("%s/dists/%s/%s is missing or does not match the Release file" % (url, DISTRIBUTION, name))
        for stanza in parse_stanzas(data.decode("utf-8")):
            filename = field(stanza, "Filename")
            if filename in published:
                continue
            control = [(n, v) for n, v in stanza if n.lower() not in CONTROL_DROP]
            hashes = {"sha256": field(stanza, "SHA256")}
            published[filename] = Deb(control, None, filename, int(field(stanza, "Size")), hashes, "published")
    log("Found %d published packages at %s" % (len(published), url))
    return list(published.values())


def download_published(url, debs, workdir):
    def download(deb):
        path = os.path.join(workdir, "published", deb.filename)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        data = fetch(url + "/" + deb.filename)
        if data is None:
            fail("Published package %s/%s not found" % (url, deb.filename))
        if hashlib.sha256(data).hexdigest() != deb.hashes["sha256"] or len(data) != deb.size:
            fail("Published package %s/%s does not match its index" % (url, deb.filename))
        with open(path, "wb") as f:
            f.write(data)
        deb.path = path
        deb.size, deb.hashes = file_hashes(path)

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        list(executor.map(download, debs))


def file_list(deb):
    """Returns the paths of the files and symlinks in a .deb, relative to /."""
    process = subprocess.Popen(["dpkg-deb", "--fsys-tarfile", deb.path], stdout=subprocess.PIPE)
    paths = []
    with tarfile.open(fileobj=process.stdout, mode="r|") as tar:
        for member in tar:
            if not member.isdir():
                paths.append(re.sub(r"^\./", "", member.name))
    if process.wait() != 0:
        fail("Failed to list the files of " + deb.path)
    return paths


def write_contents(path, debs, file_lists):
    """Writes a Contents index in the format of the official Termux repositories, which
    command-not-found and apt-file read: "<path> <package>" lines."""
    lines = sorted("%s %s" % (file, deb.package) for deb in debs for file in file_lists[deb.filename])
    with gzip.GzipFile(path, "wb", mtime=0) as f:
        f.write(("".join(line + "\n" for line in lines)).encode("utf-8"))


def dependency_names(deb):
    """Returns the alternatives of each runtime dependency of a package, as lists of names."""
    groups = []
    for name in ("Pre-Depends", "Depends"):
        for group in (field(deb.control, name) or "").split(","):
            alternatives = [a.strip().split(" ")[0].split(":")[0].split("(")[0] for a in group.split("|")]
            alternatives = [a for a in alternatives if a]
            if alternatives:
                groups.append(alternatives)
    return groups


def runtime_closure(selected, roots):
    """Returns, for every architecture, the keys of the selected packages that its roots need at
    runtime."""
    keep = {}
    for arch in ARCHITECTURES:
        keep[arch] = set()
        index = {key[0]: key for key, deb in selected.items() if deb.architecture in (arch, "all")}
        provides = {}
        for name, key in index.items():
            for provided in (field(selected[key].control, "Provides") or "").split(","):
                provided = provided.strip().split(" ")[0]
                if provided:
                    provides.setdefault(provided, name)
        resolve = lambda name: name if name in index else provides.get(name)
        todo = [r for r in (resolve(root) for root in roots.get(arch, ())) if r]
        seen = set()
        while todo:
            name = todo.pop()
            if name in seen:
                continue
            seen.add(name)
            keep[arch].add(index[name])
            for alternatives in dependency_names(selected[index[name]]):
                found = next((r for r in (resolve(a) for a in alternatives) if r), None)
                if found:
                    todo.append(found)
    return keep


def read_package_list(path):
    """Reads apt-repo/packages.txt: package names, in sections that start with a line like
    "[aarch64 arm]" naming the architectures they are for. Returns {arch: set(names)}."""
    roots = {arch: set() for arch in ARCHITECTURES}
    architectures = list(ARCHITECTURES)
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = re.sub(r"#.*", "", line).strip()
            header = re.fullmatch(r"\[([a-z0-9_ ]+)\]", line)
            if header:
                architectures = header.group(1).split()
                unknown = set(architectures) - set(ARCHITECTURES)
                if unknown:
                    fail("Unknown architectures in %s: %s" % (path, " ".join(sorted(unknown))))
                continue
            for name in line.split():
                for arch in architectures:
                    roots[arch].add(name)
    return roots


def read_roots(text):
    """Reads "<package> <arch>..." lines into {arch: set(names)}. Lines without architectures,
    from before packages were kept per architecture, are ignored."""
    roots = {arch: set() for arch in ARCHITECTURES}
    for line in text.splitlines():
        parts = re.sub(r"#.*", "", line).split()
        for arch in parts[1:]:
            if arch in roots:
                roots[arch].add(parts[0])
    return roots


def write_compressed(path, data):
    with open(path, "wb") as f:
        f.write(data)
    with gzip.GzipFile(path + ".gz", "wb", mtime=0) as f:
        f.write(data)
    with lzma.open(path + ".xz", "wb") as f:
        f.write(data)


def write_release(dist_dir, origin):
    files = []
    for root, _, names in os.walk(dist_dir):
        for name in names:
            path = os.path.join(root, name)
            relative = os.path.relpath(path, dist_dir)
            if relative in ("Release", "InRelease", "Release.gpg"):
                continue
            files.append((relative, file_hashes(path)))
    files.sort()
    lines = [
        "Origin: " + origin,
        "Label: " + origin,
        "Suite: " + DISTRIBUTION,
        "Codename: " + DISTRIBUTION,
        "Date: " + email.utils.formatdate(usegmt=True),
        "Architectures: " + " ".join(ARCHITECTURES),
        "Components: " + COMPONENT,
        "Description: Termux RE packages, built for the Termux RE app from termux-packages",
    ]
    for title, name in HASHES:
        lines.append(title + ":")
        for relative, (size, hashes) in files:
            lines.append(" %s %16d %s" % (hashes[name], size, relative))
    with open(os.path.join(dist_dir, "Release"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def write_index_page(debs, path, url, keyring, package_name, app_repo_url, available):
    """Writes the web page of the repository from files/index.html."""
    result = subprocess.run(["gpg", "--show-keys", "--with-colons", keyring], capture_output=True, text=True)
    fingerprints = [line.split(":")[9] for line in result.stdout.splitlines() if line.startswith("fpr:")]
    if result.returncode != 0 or not fingerprints:
        fail("Failed to read the fingerprint of " + keyring)
    fingerprint = " ".join(fingerprints[0][i:i + 4] for i in range(0, len(fingerprints[0]), 4))

    packages = {}
    for deb in debs:
        packages.setdefault(deb.package, {}).setdefault(deb.version, []).append(deb)
    rows = []
    for name in sorted(packages):
        for version, same in sorted(packages[name].items()):
            built_for = {deb.architecture for deb in same}
            architectures = [a for a in ARCHITECTURES if a in available.get(name, ()) and (a in built_for or "all" in built_for)]
            description = (field(same[0].control, "Description") or "").split("\n")[0]
            rows.append("<tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>" % (
                html.escape(name), html.escape(version), html.escape(", ".join(architectures)), html.escape(description)))

    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "files", "index.html"), encoding="utf-8") as f:
        page = f.read()
    for placeholder, value in [
        ("@PACKAGES@", "\n".join(rows)),
        ("@COUNT@", str(len(packages))),
        ("@ARCH_COUNTS@", html.escape(", ".join(
            "%d for %s" % (sum(1 for archs in available.values() if arch in archs), arch) for arch in ARCHITECTURES))),
        ("@DATE@", html.escape(email.utils.formatdate(usegmt=True))),
        ("@FINGERPRINT@", html.escape(fingerprint)),
        ("@REPO_URL@", html.escape(url)),
        ("@APP_REPO_URL@", html.escape(app_repo_url)),
        ("@PACKAGE_NAME@", html.escape(package_name)),
    ]:
        page = page.replace(placeholder, value)
    with open(path, "w", encoding="utf-8") as f:
        f.write(page)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", required=True, help="url of the published repository")
    parser.add_argument("--keyring", required=True, help="public key the published repository is signed with")
    parser.add_argument("--out", required=True, help="directory to write the repository to, must not exist")
    parser.add_argument("--new", action="append", default=[], help="directory with newly built .deb files")
    parser.add_argument("--remove", action="append", default=[], help="package to remove from the repository")
    parser.add_argument("--package-list", help="apt-repo/packages.txt, the packages to keep on each architecture")
    parser.add_argument("--roots", action="append", default=[],
                        help='file with "<package> <arch>..." lines, more packages to keep with their dependencies')
    parser.add_argument("--size-report", help="write the size of the repository per architecture to this file")
    parser.add_argument("--expect-existing", action="store_true",
                        help="fail if no repository is published at --url")
    parser.add_argument("--origin", default="Termux RE")
    parser.add_argument("--index-page", help="write the web page of the repository to this file")
    parser.add_argument("--package-name", default="com.termux.re", help="app package name, for the web page")
    parser.add_argument("--app-repo-url", default="https://github.com/riyadmondol2006/termux-app-RE",
                        help="url of the app repository, for the web page")
    args = parser.parse_args()

    url = args.url.rstrip("/")
    if os.path.exists(args.out):
        fail(args.out + " already exists")
    workdir = tempfile.mkdtemp(prefix="apt-repo-")

    new = []
    for directory in args.new:
        for root, _, names in os.walk(directory):
            for name in sorted(names):
                if name.endswith(".deb"):
                    new.append(read_new_deb(os.path.join(root, name)))
    log("Found %d new .deb files" % len(new))

    published = read_published(url, args.keyring, args.expect_existing, workdir)

    # Newest version of each package and architecture. New packages first, so they win ties.
    selected = {}
    for deb in new + published:
        key = (deb.package, deb.architecture)
        current = selected.get(key)
        if current is None or version_compare(deb.version, current.version) > 0:
            selected[key] = deb
    # Packages asked for in builds and requests, kept in roots.txt, and the default packages.
    requested = {arch: set() for arch in ARCHITECTURES}
    published_roots = fetch(url + "/roots.txt")
    sources = [published_roots.decode("utf-8")] if published_roots is not None else []
    for path in args.roots:
        with open(path, encoding="utf-8") as f:
            sources.append(f.read())
    for text in sources:
        for arch, names in read_roots(text).items():
            requested[arch] |= names
    was_requested = set().union(*requested.values())
    for arch in ARCHITECTURES:
        requested[arch].difference_update(args.remove)
    listed = read_package_list(args.package_list) if args.package_list else {arch: set() for arch in ARCHITECTURES}
    # packages.txt keeps its packages itself, roots.txt only needs the others.
    for arch in ARCHITECTURES:
        requested[arch] -= listed[arch]
    roots = {arch: requested[arch] | listed[arch] for arch in ARCHITECTURES}

    for name in args.remove:
        removed = [key for key in selected if key[0] == name]
        if not removed and name not in was_requested:
            fail("Package '%s' is not in the repository" % name)
        if any(name in names for names in listed.values()):
            fail("Package '%s' is in %s, remove it there" % (name, args.package_list))
        for key in removed:
            log("Removing %s %s (%s)" % (name, selected[key].version, key[1]))
            del selected[key]

    # A package that became "all" or stopped being "all" between versions: keep the newer kind.
    for package in sorted({p for p, a in selected if a == "all"}):
        specific = [key for key in selected if key[0] == package and key[1] != "all"]
        if not specific:
            continue
        all_deb = selected[(package, "all")]
        newest_specific = max((selected[key].version for key in specific),
                              key=functools.cmp_to_key(version_compare))
        if version_compare(all_deb.version, newest_specific) > 0:
            for key in specific:
                del selected[key]
        else:
            del selected[(package, "all")]

    needed_by_arch = runtime_closure(selected, roots)
    needed = set().union(*needed_by_arch.values())
    for name in args.remove:
        users = sorted({key[0] for key in needed for alternatives in dependency_names(selected[key])
                        if name in alternatives})
        if users:
            fail("Package '%s' can't be removed, these packages depend on it: %s" % (name, " ".join(users)))
    for key in sorted(set(selected) - needed):
        if selected[key].source == "published":
            log("Removing %s %s (%s), no package in the repository needs it" % (key[0], selected[key].version, key[1]))
        else:
            log("Not adding %s %s (%s), no package in the repository needs it" % (key[0], selected[key].version, key[1]))
        del selected[key]
    for arch in ARCHITECTURES:
        missing = sorted(r for r in roots[arch] if not any(key[0] == r for key in needed_by_arch[arch]))
        if missing:
            log("Packages asked for on %s that are not in the repository: %s" % (arch, " ".join(missing)))

    keep = list(selected.values())
    download_published(url, [d for d in keep if d.source == "published"], workdir)

    for deb in sorted(new, key=lambda d: (d.package, d.architecture)):
        if selected.get((deb.package, deb.architecture)) is deb:
            log("Adding %s %s (%s)" % (deb.package, deb.version, deb.architecture))
        elif (deb.package, deb.architecture) in selected:
            kept = selected[(deb.package, deb.architecture)]
            if kept.source == "published":
                log("Keeping published %s %s (%s), newer than the new %s" % (deb.package, kept.version, deb.architecture, deb.version))

    os.makedirs(args.out)
    for deb in keep:
        target = os.path.join(args.out, deb.filename)
        os.makedirs(os.path.dirname(target), exist_ok=True)
        shutil.copyfile(deb.path, target)

    dist_dir = os.path.join(args.out, "dists", DISTRIBUTION)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        file_lists = dict(zip((d.filename for d in keep), executor.map(file_list, keep)))
    available = {}
    sizes = {}
    for arch in ARCHITECTURES:
        debs = sorted((selected[key] for key in needed_by_arch[arch]), key=lambda d: d.package)
        for deb in debs:
            available.setdefault(deb.package, []).append(arch)
        sizes[arch] = (sum(d.size for d in debs if d.architecture != "all"), len(debs))
        index_dir = os.path.join(dist_dir, COMPONENT, "binary-" + arch)
        os.makedirs(index_dir)
        write_compressed(os.path.join(index_dir, "Packages"), "\n".join(d.stanza() for d in debs).encode("utf-8"))
        write_contents(os.path.join(dist_dir, "Contents-%s.gz" % arch), debs, file_lists)
        log("%s: %d packages" % (arch, len(debs)))
    write_release(dist_dir, args.origin)

    with open(os.path.join(args.out, "roots.txt"), "w", encoding="utf-8") as f:
        names = sorted(set().union(*requested.values()))
        f.write("".join("%s %s\n" % (n, " ".join(a for a in ARCHITECTURES if n in requested[a])) for n in names))
    with open(os.path.join(args.out, "available.txt"), "w", encoding="utf-8") as f:
        f.write("".join("%s %s\n" % (name, " ".join(archs)) for name, archs in sorted(available.items())))

    shared = sum(d.size for d in keep if d.architecture == "all")
    report = ["| Architecture | Packages | Size |", "| --- | --- | --- |"]
    for arch in ARCHITECTURES:
        report.append("| %s | %d | %.1f MB |" % (arch, sizes[arch][1], sizes[arch][0] / 1e6))
    report.append("| all (shared) | %d | %.1f MB |" % (sum(1 for d in keep if d.architecture == "all"), shared / 1e6))
    log("\n".join(report))
    if args.size_report:
        with open(args.size_report, "w", encoding="utf-8") as f:
            f.write("\n".join(report) + "\n")

    if args.index_page:
        write_index_page(keep, args.index_page, url, args.keyring, args.package_name, args.app_repo_url, available)
    shutil.rmtree(workdir)


if __name__ == "__main__":
    main()
