# Termux RE feature guide

Termux RE adds tools for appearance, sessions, terminal input, packages, files, development and system monitoring.

Open the terminal drawer (swipe from the middle of the left edge) to find shortcuts at the top: **Themes**, **Settings**, **Tools**, **Packages** and **Files**. **Tools** lists everything, grouped and searchable. You can also long-press the terminal and choose **Tools**, and **Settings** links to the settings of every feature.

Hardware shortcuts need a hardware keyboard. Setting `disable-hardware-keyboard-shortcuts` in `~/.termux/termux.properties` disables them.

## Contents

- [Appearance](#appearance)
  - [Theme Studio](#theme-studio)
  - [Settings redesign](#settings-redesign)
- [Sessions](#sessions)
  - [Session workspaces](#session-workspaces)
  - [Terminal profiles](#terminal-profiles)
  - [Startup automation](#startup-automation)
  - [Session persistence](#session-persistence)
  - [Terminal notifications](#terminal-notifications)
- [Terminal](#terminal)
  - [Terminal search](#terminal-search)
  - [Split screen](#split-screen)
  - [Enhanced copy and paste](#enhanced-copy-and-paste)
- [Keyboard & input](#keyboard--input)
  - [Command palette](#command-palette)
  - [Quick actions](#quick-actions)
  - [Terminal toolbar editor](#terminal-toolbar-editor)
  - [Custom keyboard editor](#custom-keyboard-editor)
  - [Command history](#command-history)
- [Packages](#packages)
  - [Package manager](#package-manager)
  - [Package repositories](#package-repositories)
  - [Package compatibility](#package-compatibility)
- [Files](#files)
  - [File manager](#file-manager)
  - [Backup and restore](#backup-and-restore)
- [Developer tools](#developer-tools)
  - [SSH connections](#ssh-connections)
  - [Git integration](#git-integration)
- [System](#system)
  - [System monitor](#system-monitor)
  - [Floating terminal](#floating-terminal)
  - [Update manager](#update-manager)

## Appearance

### Theme Studio

Theme Studio edits terminal and ANSI colors, fonts, cursor style and background appearance, with a live preview. It can use bundled themes or themes you save yourself.

**Open:** **Tools → Theme Studio**.

**Main actions:**

- Choose a bundled or saved theme, then edit its terminal and ANSI colors.
- Change the font size, cursor shape and cursor blinking. Cursor shapes are block, underline and bar.
- Import a TTF or OTF font, or choose a background image and adjust its dimming.
- Import a theme from JSON or a `colors.properties` file.
- Use **Save** to save a custom theme. Use **Apply** to make its colors, font, cursor and font size active and reload the terminal style.

**Shortcuts:** None.

**Related settings:** Theme Studio writes the active terminal color, font, cursor and font-size configuration. Choosing a background image also saves that choice separately.

**Limits:** Bundled themes are read-only; save a copy before editing one. Font size is 6–96 dp. Theme imports are limited to 256 KiB. Existing color and font files are backed up before they are replaced.

### Settings redesign

Settings has searchable categories and a live terminal preview for appearance changes. It brings general app, terminal, keyboard, session, package, permission, debugging and update options together.

**Open:** Tap **Settings** in the terminal drawer.

**Main actions:**

- Search the visible settings by title or summary.
- Open **Appearance**, **Terminal**, **Keyboard**, **Sessions**, **Packages**, **Permissions**, **Advanced / Debugging**, installed plugin settings, or **About**.
- Use **Check for updates now** to check immediately.

**Shortcuts:** None.

**Related settings:** **Check for updates** controls automatic update checks. Release builds check at most once every six hours when the terminal opens.

**Limits:** Root settings search filters the visible category titles and summaries; it does not search inside every subscreen.

## Sessions

### Session workspaces

Workspaces group terminal sessions without changing the shells themselves. Workspace chips appear above the drawer session list after you create a workspace; **All** shows sessions from every workspace.

**Open:** **Tools → Workspaces**, or use the workspace chips and long-press menus in the drawer.

**Main actions:**

- Create, rename and delete workspaces.
- Long-press a workspace chip for workspace actions.
- Long-press a session to rename, pin or unpin it, move it with **Move to workspace**, view restored history when available, or close it.
- Select **All** or a workspace chip to filter the session list.

**Shortcuts:** `Ctrl+Alt+W` selects the next workspace.

**Related settings:** Workspace membership is included in saved session state when **Restore sessions** is enabled.

**Limits:** Deleting a workspace moves its sessions to **Default**. The default workspace cannot be removed. Workspaces organize sessions; they do not create separate containers or home directories.

### Terminal profiles

A terminal profile is a reusable starting setup for a session. It can set the shell, working directory, startup commands, font size, color-scheme ID and whether it is the default profile.

**Open:** **Tools → Terminal profiles**, **Settings → Sessions → Manage profiles**, or long-press **New session** in the drawer.

**Main actions:**

- Create, start, edit, duplicate and delete profiles.
- Choose the default login shell or an installed bash, zsh, fish or sh shell.
- Set a working directory; `~` and `$HOME` are supported.
- Add commands to run after shell startup and set a profile as the default.
- Long-press **New session** to start with a profile, start without one, create a named session, or open a failsafe session.

**Shortcuts:** None specific to profiles.

**Related settings:** **Settings → Sessions → Manage profiles** opens the same profile manager. Profile commands run after global startup commands.

**Limits:** Profile font size is 4–64 dp. A color-scheme ID is saved with the profile but is not currently applied when its session opens. Startup commands are not run for failsafe, plugin or command sessions. Generated files are kept under `~/.termux/re/`; your shell dotfiles are not rewritten.

### Startup automation

Startup automation runs commands when a normal new shell session starts. Global commands can be combined with the commands in a terminal profile.

**Open:** **Tools → Startup automation**, or the startup-command options under **Settings → Sessions**.

**Main actions:**

- Add the commands you want new sessions to run.
- Use terminal profiles for commands that should apply only to particular kinds of sessions.
- Use the default login shell or a supported installed shell as the session base.

**Shortcuts:** None.

**Related settings:** Global startup commands run first, followed by profile commands. Both run after the shell's own startup files. Profile setup is in **Settings → Sessions → Manage profiles**.

**Limits:** These commands do not run in failsafe, plugin or command sessions. Generated files are stored under `~/.termux/re/`; the feature does not rewrite your existing dotfiles.

### Session persistence

Session persistence restores your session, workspace and split-screen layout after the app restarts. Optional transcript saving also retains recent terminal output for restored sessions.

**Open:** **Settings → Sessions**.

**Main actions:**

- Turn **Restore sessions** on or off; it is on by default.
- Turn **Save transcripts** on or off and choose **Transcript lines**.
- Use **Clear saved history** to remove saved terminal history.
- Long-press a restored session in the drawer to view its restored history when available.

**Shortcuts:** None.

**Related settings:** The transcript default is 1,000 lines and the maximum is 5,000. Saved state includes sessions, workspaces, splits and profile/session metadata.

**Limits:** The app restores sessions and their layout, not the in-memory state of running processes. At most eight sessions are supported.

**Privacy:** **Save transcripts** is off by default because terminal output can contain passwords, tokens and other private information. Saved history remains on the device until you clear it; **Clear saved history** removes it.

### Terminal notifications

Terminal programs can request Android notifications with OSC 9 or OSC 777 `notify` escape sequences. Optional shell integration can also notify you when a long-running command finishes.

**Open:** **Settings → Sessions** for notification options. Notifications themselves appear in Android's notification shade.

**Main actions:**

- Allow terminal programs to send a notification while their session is not visible.
- Tap a notification to return to its session.
- Enable **Notify long-running commands** and choose the command-duration threshold.
- Manage Android notification permission and channel behavior in system settings when needed.

**Shortcuts:** None.

**Related settings:** Terminal notifications are enabled by default, subject to Android permission and channel settings. **Notify long-running commands** is off by default. Its threshold defaults to 10 seconds and accepts 1–86400 seconds. Shell integration supports bash, zsh and fish.

**Limits:** Notifications are sent only when the session is not currently visible. A session's notifications replace one another and are rate-limited to one per second. Notification titles are limited to 100 characters and bodies to 1,000 characters.

## Terminal

### Terminal search

Terminal search finds text in visible output and scrollback, highlights matches and keeps results up to date as new output arrives. It supports plain, case-sensitive and Java regular-expression searches.

**Open:** **Tools → Terminal search**, or press `Ctrl+Shift+F`.

**Main actions:**

- Enter a search term and move between matches.
- Toggle case-sensitive or regular-expression matching.
- Read the match count and the current match position.
- Search wrapped terminal lines as one logical line.
- Press `Escape` to close search.

**Shortcuts:** `Enter` or `F3` moves toward older output. `Shift+Enter` or `Shift+F3` moves toward newer output. `Escape` closes search.

**Related settings:** None.

**Limits:** At most 10,000 matches are retained; the display tells you when results are truncated. Regular expressions use Java syntax. Searching runs in the background, but very large scrollback buffers can still produce many matches.

### Split screen

Split screen shows two terminal sessions side by side or one above the other, depending on the available screen space. Each pane keeps its own running session.

**Open:** **Tools → Split screen**, or press `Ctrl+Shift+O`.

**Main actions:**

- Choose an existing session or create a new one for the second pane.
- Tap a pane to focus it and drag the divider to resize the panes.
- Use the divider or **Tools** options to choose a session, focus the other pane, swap panes or close the split.

**Shortcuts:** `Ctrl+Shift+O` opens split screen.

**Related settings:** The split ratio and secondary session are saved when session restoration is enabled.

**Limits:** Closing the split does not close the other session. The eight-session limit still applies.

### Enhanced copy and paste

Terminal selection supports quick word and line selection, and clipboard history provides a list of recent text for pasting. Clipboard history stores text copied by the app and external text when you paste it into the app.

**Open:** Long-press terminal text to start selection. Double-tap to select a word and tap a third time nearby to select its line. Open **Tools → Clipboard history** for saved clipboard entries.

**Main actions:**

- Tap a clipboard entry to paste it.
- Long-press an entry to paste, copy or delete it.
- Clear all entries or disable clipboard history from its screen.

**Shortcuts:** `Ctrl+Alt+V` pastes.

**Related settings:** Clipboard history is enabled by default. Disabling it clears its saved entries.

**Limits:** Double- and triple-tap selection is not used while terminal mouse tracking is active. Clipboard history keeps up to 50 entries, 100,000 characters per item and 500,000 characters in total. Blank or oversized items are not retained. On Android 10 and later, the app cannot generally monitor clipboard changes made while it is in the background.

**Privacy:** Clipboard entries can contain passwords, tokens and other private text. They are stored in private app preferences and excluded from Android backup. Clear or disable clipboard history if you do not want this text retained.

## Keyboard & input

### Command palette

The command palette is a searchable launcher for session, terminal, feature and saved-command actions. It also includes configured session shortcuts and available tools.

**Open:** **Tools → Command palette**, or press `Ctrl+Shift+P`.

**Main actions:**

- Type in **Search actions and commands** to filter results, including saved command text.
- Tap an action to run it, or press `Enter` to run the first visible result.
- Use **Manage saved commands** and **Add command** to save a named command or text snippet.
- For a saved command, choose **Insert**, **Run**, or **New session**. Saved commands can also be deleted from the manager.

**Shortcuts:** `Ctrl+Shift+P` opens the palette. `Enter` runs the first visible result.

**Related settings:** Configured session shortcuts appear as palette actions. Available feature actions follow the same tools shown in **Tools**.

**Limits:** **Insert** and **Run** need a running current session. **Run** adds Enter to the command; **Insert** only inserts its text. The palette launches actions; it is not a shell command prompt for unsaved input.

### Quick actions

Quick actions puts frequently used actions and saved commands in a compact sheet. Actions are ordered by use, so the ones you use most appear first.

**Open:** **Tools → Quick actions**, or press `Ctrl+Shift+Q`.

**Main actions:**

- Tap an action to run it immediately.
- Use saved commands and choose whether to insert or run them.
- Open **Manage saved commands** to add or delete reusable commands and snippets.

**Shortcuts:** `Ctrl+Shift+Q` opens quick actions.

**Related settings:** Quick actions shares saved commands with the command palette and toolbar editor.

**Limits:** The sheet shows saved commands and up to 12 direct actions. It does not include its own **Quick actions** entry. Actions that operate on a session need a running session.

### Terminal toolbar editor

The toolbar editor chooses which actions appear on the terminal toolbar's custom-button page. The toolbar has three pages: extra keys, text input and custom buttons.

**Open:** **Tools → Terminal toolbar**, or swipe the terminal toolbar to its third page and use its manage action.

**Main actions:**

- Use **Add action** to add a session, terminal, feature or saved-command action.
- Tap a button to remove it, or hold and drag to reorder buttons.
- Reset the toolbar to its defaults.

**Shortcuts:** None. Toolbar buttons are on-screen actions, not hardware-keyboard shortcuts.

**Related settings:** The default custom buttons include new session, keyboard toggle, command palette and quick actions. Saved commands are shared with the command palette and quick actions.

**Limits:** The editor changes the custom-button page, not the first two toolbar pages. Feature buttons only work while their feature is available.

### Custom keyboard editor

The custom keyboard editor changes the extra-keys rows with a live preview. It supports individual keys, literal text, macros and swipe-up popup actions.

**Open:** **Tools → Custom keyboard**.

**Main actions:**

- Choose an extra-keys style: `default`, `arrows-only`, `arrows-all`, `all` or `none`.
- Use **Add row**, **Move row up** and **Remove row** to arrange rows.
- Use **Add key**, **Move key left**, **Move key right**, **Edit key** and **Remove** to arrange keys.
- Set a key or literal text, a space-separated macro, an optional display label, and a swipe-up key, text or macro.
- Tap **Save and reload** to save and apply the keyboard.

**Shortcuts:** None. This edits the on-screen extra-keys keyboard.

**Related settings:** Saving writes `extra-keys` and `extra-keys-style` in `~/.termux/termux.properties`. Unrelated properties are preserved.

**Limits:** The previous properties file is saved as `termux.properties.bak`. If the file changes elsewhere while the editor is open, the editor detects the conflict rather than silently overwriting it. Macros use the extra-keys parser's space-separated syntax, such as `CTRL c`.

### Command history

Command history reads bash, zsh and fish history into one searchable list. You can reuse commands, bookmark them for later and delete individual history occurrences.

**Open:** **Tools → Command history**.

**Main actions:**

- Use **Search shell history** to filter commands and **Bookmarks only** to show favourites.
- Choose **Use** to reuse a command, or **Bookmark** and **Remove bookmark** to manage saved favourites.
- Open **More** to save a command to actions or delete that occurrence from shell history.
- Use **Refresh** after a shell writes new history.

**Shortcuts:** None.

**Related settings:** Saved commands created with **Save to actions** become available in the command palette, quick actions and toolbar editor.

**Limits:** The manager reads `.bash_history`, `.zsh_history` and `.local/share/fish/fish_history`, up to 8 MiB per file. Shells may not write a command until they flush history or exit. Deleting a history occurrence creates a `.bak` file and checks for concurrent changes, but a running shell may write the command again. Duplicate commands are filtered in the display.

**Privacy:** Shell history often contains private paths, arguments, tokens or passwords. Bookmarks remain stored even when normal shell history rotates. Opening this manager reads history; it does not add your searches to shell history.

## Packages

### Package manager

The package manager browses APT packages, shows package details and runs package operations in a visible terminal. It separates all packages, installed packages and available updates.

**Open:** **Tools → Package manager**.

**Main actions:**

- Browse **All**, **Installed** and **Updates**, and search for packages.
- Pull to refresh package lists with `apt update`.
- Open a package to see versions, sizes, dependencies, maintainer, description and homepage from `apt show`.
- Install, reinstall or update a package, or confirm its removal.
- Confirm **Update all** to run `pkg upgrade`.
- Use the package-request link to request a missing package through the project's GitHub issue template.

**Shortcuts:** None.

**Related settings:** Repository choices in **Tools → Repository manager** determine which packages are available.

**Limits:** Package operations run in a terminal so you can see output and prompts. Available details depend on APT metadata. Removing a package can affect other packages that depend on it.

### Package repositories

The repository manager shows the APT source declarations configured on your device. It can add, enable, disable and remove repositories, select mirrors and inspect trusted signing keys.

**Open:** **Tools → Repository manager**.

**Main actions:**

- Inspect each source's file and line, URL, suite and components.
- Enable or disable a source, add a source, or confirm its removal.
- Refresh package lists after changing sources; edits create backups.
- Select a configured mirror group, or choose **Open change-repo** to launch `termux-change-repo`.
- Inspect trusted keys and their fingerprints with `gpg`.

**Shortcuts:** None.

**Related settings:** Mirror selection runs `pkg --check-mirror update`. Repository changes affect the packages and updates shown by the package manager.

**Limits:** A repository URL does not guarantee that its packages work in Termux RE. Official Termux repositories produce an explicit incompatibility warning; do not assume upstream Termux packages are compatible with this fork.

### Package compatibility

Package compatibility checks whether packages contain paths for official Termux rather than Termux RE. It helps explain why a package built for a different app installation prefix may not work.

**Open:** **Tools → Package compatibility**.

**Main actions:**

- Scan installed package file lists for foreign-prefix paths.
- Choose a `.deb` file to inspect its contents with `dpkg-deb --contents`.
- Review grouped paths, with up to 12 examples shown per package.
- Use the package-request link for packages that need a compatible build.

**Shortcuts:** None.

**Related settings:** None.

**Limits:** Official Termux commonly uses `/data/data/com.termux/`, while Termux RE uses `/data/data/com.termux.re/`. This tool reports problems only: it does not rewrite packages, change their prefix or make incompatible packages work.

## Files

### File manager

The file manager browses Termux and shared-storage locations, with file operations, multi-selection and a built-in text editor. It can also open a terminal in the current folder.

**Open:** **Tools → File manager**.

**Main actions:**

- Browse **Home**, **Prefix**, **Storage links** and shared storage, using breadcrumbs to move up or down.
- Search the current folder and sort by name, newest or largest.
- Tap to open a file or folder; long-press to multi-select, or use select all.
- Create a file or folder, then copy, move, paste, rename or recursively delete after confirmation.
- View details, edit, share, open with another app, or open a terminal in the current directory.

**Shortcuts:** None.

**Related settings:** Android shared-storage access may require a storage permission.

**Limits:** The editor accepts regular UTF-8 text files up to 256 KiB and refuses to save over a file that changed elsewhere. Paste refuses existing destinations instead of overwriting them. Changes under `$PREFIX` can break Termux and produce a warning.

### Backup and restore

Backup creates a gzip archive of selected Termux RE data through Android's document picker. Restore validates an archive and shows its contents before replacing the archived paths.

**Open:** **Tools → Backup & restore**.

**Main actions:**

- Choose what to include. By default, Home and app settings/shared preferences are included; Prefix and caches are excluded.
- Choose a destination or source with Android's document picker. The default filename is `termux-re-backup.tre.gz`.
- Watch the foreground-service progress notification or cancel the operation.
- Before confirming a restore, review the app version, included roots, entry and byte counts, and up to 20 example paths.
- After restoring, exit all sessions and restart the app.

**Shortcuts:** None.

**Related settings:** App settings and shared preferences are included by default; Home, Prefix and cache inclusion can be changed for the backup.

**Limits:** Regular files, directories and symbolic links are supported; sockets, devices and FIFOs are skipped. Archives are limited to 100,000 entries and 20 GiB of file data. Restore replaces archived paths but does not delete unrelated files absent from the archive. Cancelling can leave changes that already completed.

**Privacy:** Archives are not encrypted. They can contain private configuration, terminal or shell history, credentials and cryptographic keys, including SSH keys. Store and share backup files accordingly.

## Developer tools

### SSH connections

The SSH manager saves connection details and opens connections in a terminal. It also helps generate keys, install public keys and maintain a managed block in SSH configuration.

**Open:** **Tools → SSH manager**.

**Main actions:**

- Install OpenSSH with `pkg install openssh` if it is missing.
- Add, import, edit, delete and connect to saved hosts.
- Save a label, user, host, port, identity file, jump host and extra options.
- Optionally sync saved hosts to `~/.ssh/config`.
- Generate an ed25519 key with an optional passphrase, copy a public key, or use the key-setup action.

**Shortcuts:** None.

**Related settings:** SSH uses your normal OpenSSH configuration and identity files. Sync rewrites only the marked managed Host block and preserves manual configuration outside it.

**Limits:** This is not an SFTP or graphical file-transfer manager. Only `.pub` public keys are listed for copying. Import adds new labels without replacing saved connections that already exist. Key setup prefers `ssh-copy-id`, with an SSH fallback; connection and authentication prompts appear in the terminal.

### Git integration

Git integration provides a repository dashboard for common status, history, staging, commit, branch, stash and synchronization tasks. Network operations open in a terminal so credentials and prompts remain visible.

**Open:** **Tools → Git integration**.

**Main actions:**

- Choose the current directory, a recent repository or an entered path, or clone a repository. Install Git with `pkg install git` if needed.
- View status and per-file diffs; stage or unstage individual files.
- View, switch and create branches.
- Read the last 50 commits and inspect commit details and statistics.
- Commit staged changes and create a stash, optionally including untracked files.
- Run pull, push, fetch and clone in a visible terminal.

**Shortcuts:** None.

**Related settings:** Git uses the repository's normal Git configuration and credentials.

**Limits:** Commit does not automatically stage everything. The dashboard does not provide full merge, rebase or remote-management screens, and it has no stash-apply or stash-pop interface. Use the terminal for those operations.

## System

### System monitor

System monitor shows CPU, memory, storage, battery and app-owned process information. It can stop processes owned by Termux RE when Android permits it.

**Open:** **Tools → System monitor**.

**Main actions:**

- View CPU and per-core information where Android allows it, or app CPU usage otherwise.
- View RAM, swap, cache, low-memory and app-memory information.
- Inspect internal/shared storage and Prefix, Home and other app-data usage.
- Check battery level, status, power source, temperature, voltage and health.
- Review app-owned processes with PID, CPU, RSS and thread count; confirm **Terminate** to send `SIGTERM` or **Kill** to send `SIGKILL`.

**Shortcuts:** None.

**Related settings:** None.

**Limits:** The display refreshes every 1.5 seconds while visible, with storage usage refreshed every 30 seconds. Android may hide some hardware or per-core information. The monitor cannot inspect or stop arbitrary processes belonging to other apps or the system.

### Floating terminal

Floating terminal displays a new or current terminal session above other apps. Its window and the main terminal activity share the same underlying session.

**Open:** **Tools → Floating terminal**, then grant **Display over other apps** if Android asks for it.

**Main actions:**

- Start a new session or show the current running session.
- Move the window by its title bar and resize it from a corner.
- Minimize to a draggable bubble that snaps to an edge; tap the bubble or notification to restore the window.
- Use the keys row for one-shot CTRL and ALT, ESC, TAB, arrows, HOME, END, PGUP and PGDN.
- Pinch to change and remember the floating terminal font size.
- Tap the terminal for keyboard focus; press Back or touch outside to release focus.

**Shortcuts:** None in the floating window.

**Related settings:** Android's **Display over other apps** permission is required. Floating font size is remembered separately.

**Limits:** The minimum window size is 200×140 dp, and the eight-session limit applies. Closing the floating window does not close its session. The floating view does not provide the main activity's full extra-keys, URL or hardware-shortcut handling.

### Update manager

Update manager checks the project's GitHub releases, downloads a suitable APK and hands installation to Android. It can show release notes, skip a version and verify a published checksum before installation.

**Open:** **Tools → App update manager**, or use the update options in **Settings**.

**Main actions:**

- Compare installed and latest versions and read notes for newer releases.
- Check manually, open the release page, or skip and later unskip a version.
- Download the APK that matches the device's ABI, cancel a download, or reinstall the same latest version when offered.
- Allow **Install unknown apps** if Android requests it, then confirm installation in the system installer.

**Shortcuts:** None.

**Related settings:** **Check for updates** controls automatic checks. **Check for updates now** starts a manual check. Release builds automatically check at most every six hours when the terminal opens.

**Limits:** Downloads continue if the screen closes, but only while the current app process remains alive. Advertised size and SHA-256 are validated when supplied; if no checksum is published, the screen says the APK was not checksum-verified. Android still requires installation confirmation and a matching app signature. This is not a silent update or a Play Store update.
