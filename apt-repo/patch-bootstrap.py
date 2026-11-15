#!/usr/bin/env python3
"""
Points an existing bootstrap archive at the Termux RE package repository without rebuilding
it. It makes the same changes that patch-termux-packages.sh makes to the apt, termux-keyring
and termux-tools packages, from the same templates in files/, and updates their dpkg file lists,
checksums and conffiles to match, so the bootstrap looks as if it was built from the patched
packages:

- etc/apt/sources.list points at the Termux RE repository.
- The Termux RE repository signing key is added to share/termux-keyring and trusted by apt
  through a symlink in etc/apt/trusted.gpg.d.
- Every mirror file of termux-tools points at the Termux RE repository and chosen_mirrors links
  to the default one, so `pkg` can't switch to the official repositories.

Running it on an already patched bootstrap changes nothing.

Usage: patch-bootstrap.py <input zip> <output zip> <app package name> <repository url>
"""

import hashlib
import os
import re
import sys
import zipfile

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MIRRORS_DIR = "etc/termux/mirrors/"
CHOSEN_MIRRORS = "etc/termux/chosen_mirrors"
KEY_FILE = "share/termux-keyring/termux-re.gpg"
KEY_LINK = "etc/apt/trusted.gpg.d/termux-re.gpg"


def fail(message):
    print("patch-bootstrap.py: " + message, file=sys.stderr)
    sys.exit(1)


def render(template, package_name, repo_url):
    with open(os.path.join(SCRIPT_DIR, "files", template), encoding="utf-8") as f:
        return f.read().replace("@REPO_URL@", repo_url).replace("@PACKAGE_NAME@", package_name).encode("utf-8")


class Bootstrap:
    def __init__(self, path, package_name):
        self.prefix = "/data/data/%s/files/usr" % package_name
        with zipfile.ZipFile(path) as z:
            self.infos = {info.filename: info for info in z.infolist()}
            self.data = {info.filename: z.read(info) for info in z.infolist()}
        self.order = list(self.infos)

        login = self.data.get("bin/login", b"").split(b"\n", 1)[0].decode("utf-8", "replace")
        if not login.startswith("#!" + self.prefix + "/"):
            fail("%s was not built for package name '%s', bin/login starts with '%s'" % (path, package_name, login))

    def text(self, name):
        if name not in self.data:
            fail("%s not found in the bootstrap" % name)
        return self.data[name].decode("utf-8")

    def set_file(self, name, data, like):
        if name not in self.infos:
            info = zipfile.ZipInfo(name, date_time=self.infos[like].date_time)
            info.external_attr = self.infos[like].external_attr
            info.compress_type = zipfile.ZIP_DEFLATED
            self.infos[name] = info
            self.order.append(name)
        self.data[name] = data

    def remove_file(self, name):
        del self.infos[name]
        del self.data[name]
        self.order.remove(name)

    def set_lines(self, name, lines):
        self.data[name] = "".join(line + "\n" for line in lines).encode("utf-8")

    def lines(self, name):
        return self.text(name).splitlines()

    # dpkg database of an installed package: file list, md5sums and conffiles.

    def update_dpkg_info(self, package, files, removed=(), conffiles=None, links=()):
        """Records that package now has files (relative to the prefix, with their content) and
        links, and no longer has the files in removed."""
        info = "var/lib/dpkg/info/" + package
        gone = {self.prefix + "/" + name for name in removed}
        new = [self.prefix + "/" + name for name in list(files) + list(links)]
        file_list = [line for line in self.lines(info + ".list") if line not in gone and line not in new]
        self.set_lines(info + ".list", file_list + new)

        md5_path = lambda name: (self.prefix + "/" + name).lstrip("/")
        gone_md5 = {md5_path(name) for name in list(removed) + list(files)}
        md5sums = [line for line in self.lines(info + ".md5sums") if line.split("  ", 1)[-1] not in gone_md5]
        md5sums += ["%s  %s" % (hashlib.md5(data).hexdigest(), md5_path(name)) for name, data in files.items()]
        self.set_lines(info + ".md5sums", md5sums)

        if conffiles is not None:
            current = self.lines(info + ".conffiles") if info + ".conffiles" in self.data else []
            current = [line for line in current if line not in gone and line not in {self.prefix + "/" + n for n in conffiles}]
            self.set_lines(info + ".conffiles", current + [self.prefix + "/" + name for name in conffiles])

    def set_symlinks(self, links):
        """links maps a link path relative to the prefix to its absolute target."""
        lines = [line for line in self.lines("SYMLINKS.txt")
                 if line.split("←", 1)[-1] not in {"./" + name for name in links}]
        lines += ["%s←./%s" % (target, name) for name, target in links.items()]
        self.set_lines("SYMLINKS.txt", lines)

    def write(self, path):
        tmp = path + ".tmp"
        with zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as z:
            for name in self.order:
                z.writestr(self.infos[name], self.data[name])
        os.replace(tmp, path)


def main():
    if len(sys.argv) != 5:
        fail("Usage: patch-bootstrap.py <input zip> <output zip> <app package name> <repository url>")
    zip_in, zip_out, package_name, repo_url = sys.argv[1:]
    repo_url = repo_url.rstrip("/")
    if not re.fullmatch(r"[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+", package_name):
        fail("Invalid package name '%s'" % package_name)
    if not re.fullmatch(r"https://[A-Za-z0-9.-]+(/[A-Za-z0-9._~-]+)*", repo_url):
        fail("Invalid repository url '%s'" % repo_url)

    b = Bootstrap(zip_in, package_name)

    # apt
    sources = render("sources.list", package_name, repo_url)
    b.set_file("etc/apt/sources.list", sources, like="etc/apt/sources.list")
    b.update_dpkg_info("apt", {"etc/apt/sources.list": sources})

    # termux-keyring
    with open(os.path.join(SCRIPT_DIR, "termux-re.gpg"), "rb") as f:
        key = f.read()
    like = sorted(n for n in b.infos if n.startswith("share/termux-keyring/") and n.endswith(".gpg"))
    if not like:
        fail("No keys found in share/termux-keyring")
    b.set_file(KEY_FILE, key, like=like[0])
    b.update_dpkg_info("termux-keyring", {KEY_FILE: key}, links=[KEY_LINK])

    # termux-tools
    mirror = render("mirror", package_name, repo_url)
    old_mirrors = [n for n in b.order if n.startswith(MIRRORS_DIR) and not n.endswith("/")]
    groups = sorted({n[len(MIRRORS_DIR):].split("/", 1)[0] for n in b.order
                     if n.startswith(MIRRORS_DIR) and "/" in n[len(MIRRORS_DIR):]})
    groups = [g for g in groups if g]
    if not groups or MIRRORS_DIR + "default" not in b.infos:
        fail("No mirrors found in " + MIRRORS_DIR)
    like = MIRRORS_DIR + "default"
    mirrors = {MIRRORS_DIR + "default": mirror}
    mirrors.update({"%s%s/termux-re" % (MIRRORS_DIR, group): mirror for group in groups})
    for name in old_mirrors:
        if name not in mirrors:
            b.remove_file(name)
    for name, data in mirrors.items():
        b.set_file(name, data, like=like)

    change_repo = "bin/termux-change-repo"
    change_repo_data = "".join(line for line in b.text(change_repo).splitlines(True)
                               if 'europe/packages.termux.dev")"' not in line).encode("utf-8")
    b.set_file(change_repo, change_repo_data, like=change_repo)

    b.update_dpkg_info("termux-tools", dict(mirrors, **{change_repo: change_repo_data}),
                       removed=[n for n in old_mirrors if n not in mirrors],
                       conffiles=sorted(mirrors), links=[CHOSEN_MIRRORS])

    b.set_symlinks({
        KEY_LINK: b.prefix + "/" + KEY_FILE,
        CHOSEN_MIRRORS: b.prefix + "/" + MIRRORS_DIR + "default",
    })

    b.write(zip_out)
    print("Patched %s -> %s for '%s' (%d mirror groups)" % (zip_in, zip_out, repo_url, len(groups)))


if __name__ == "__main__":
    main()
