#!/data/data/com.termux.re/files/usr/bin/bash
# Termux RE helper. Usage: bash ai-cli-tools.sh install-claude | claude [args] | codex [args]
set -euo pipefail
if [[ ${PREFIX:-} != /data/data/com.termux.re/files/usr ]]; then
    echo 'Run this helper inside Termux RE (com.termux.re).' >&2
    exit 1
fi
export TMPDIR="$PREFIX/tmp"
mkdir -p "$TMPDIR"
claude_version=2.1.1
claude_root="$HOME/.local/share/termux-re-ai-tools/claude-code-$claude_version"
require_command() {
    if ! command -v "$1" >/dev/null; then
        echo "Missing $1. Install from the Termux RE repository: pkg install $2" >&2
        exit 1
    fi
}
action=${1:-help}
[[ $# -eq 0 ]] || shift
case "$action" in
    install-claude)
        require_command node nodejs-lts
        require_command npm npm
        require_command git git
        require_command rg ripgrep
        # Isolated from global npm installs; this historical JS release has been tested.
        npm install --prefix "$claude_root" --ignore-scripts --save-exact "@anthropic-ai/claude-code@$claude_version"
        echo 'Installed. Run: bash ai-cli-tools.sh claude --version'
        ;;
    claude)
        require_command node nodejs-lts
        require_command rg ripgrep
        entry="$claude_root/node_modules/@anthropic-ai/claude-code/cli.js"
        if [[ ! -f $entry ]]; then
            echo 'First run: bash ai-cli-tools.sh install-claude' >&2
            exit 1
        fi
        export USE_BUILTIN_RIPGREP=0 DISABLE_AUTOUPDATER=1
        exec node "$entry" "$@"
        ;;
    codex)
        if ! dpkg-query -W -f='${Status}' codex 2>/dev/null | grep -qx 'install ok installed'; then
            echo 'Requires the native Android package: pkg install codex ripgrep' >&2
            exit 1
        fi
        exec "$PREFIX/bin/codex" "$@"
        ;;
    *)
        echo 'Usage: bash ai-cli-tools.sh install-claude | claude [args] | codex [args]'
        [[ $action == help ]]
        ;;
esac
