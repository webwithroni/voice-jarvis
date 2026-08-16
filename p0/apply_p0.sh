#!/usr/bin/env bash
#
# apply_p0.sh — integrate the Voice Jarvis P0 delta into a local clone
# of webwithroni/voice-jarvis, then print the git commands to push.
#
# Usage:
#   1. Clone your repo and cd into it:
#        git clone https://github.com/webwithroni/voice-jarvis.git
#        cd voice-jarvis
#   2. Run this script, pointing at the extracted p0 folder:
#        bash /path/to/p0/apply_p0.sh
#   3. Review `git status`, then commit & push (commands printed at the end).
#
# The script is idempotent: re-running it is safe.

set -euo pipefail

P0_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# --- sanity: must be run from a voice-jarvis clone root -----------------
if [ ! -f "settings.gradle.kts" ] || [ ! -d "app/src/main/java/com/webwithroni/voicejarvis" ]; then
  echo "ERROR: run this from the ROOT of your voice-jarvis clone" >&2
  echo "       (settings.gradle.kts and app/src/main/... must exist here)." >&2
  exit 1
fi

if [ ! -d "$P0_DIR/app" ]; then
  echo "ERROR: P0 payload not found next to this script ($P0_DIR)." >&2
  exit 1
fi

echo "==> Copying P0 files (new + modified) into the repo"
cp -a "$P0_DIR/app/."       "app/"
cp -a "$P0_DIR/.github/."   ".github/"
cp -a "$P0_DIR/scripts/."   "scripts/"
echo "    done."

# --- additive one-method patch to CapabilityBus.kt ----------------------
echo "==> Patching CapabilityBus.kt (additive availability() method)"
python3 - "app/src/main/java/com/webwithroni/voicejarvis/CapabilityBus.kt" <<'PY'
import sys
path = sys.argv[1]
src = open(path, encoding="utf-8").read()

if "fun availability(" in src:
    print("    already patched — skipping.")
    sys.exit(0)

anchor = "    private fun captureFingerprint(): String? {"
if anchor not in src:
    print("    WARNING: anchor not found; add the availability() method manually")
    print("             (see INTEGRATION.md, section 4).")
    sys.exit(0)

method = '''    /**
     * P0: deterministic availability query for the tool bridge / diagnostics.
     *
     * Delegates to the authoritative CapabilityManager truth-up layer so a
     * capability is never advertised as usable unless it is actually AVAILABLE.
     */
    fun availability(
        action: String
    ): CapabilityAvailability {

        return capabilityManager.availabilityForAction(action)
    }

'''

src = src.replace(anchor, method + anchor, 1)
open(path, "w", encoding="utf-8").write(src)
print("    patched.")
PY

echo ""
echo "==> Integration complete. Review changes:"
echo "      git status"
echo "      git add -A"
echo "      git commit -m \"P0: unit tests, CI quality gates, capability truth-up\""
echo "      git push origin main"
echo ""
echo "    Pushing to main triggers .github/workflows/ci.yml, which runs the"
echo "    24 pending native checks (Robolectric/MockK unit tests + debug APK)."
