import os
import subprocess
import sys

REPO_URL = "https://github.com/OryvexTop/Zest.git"
BRANCH_NAME = "main"
COMMIT_MESSAGE = "feat: implement ZestKnockback Spigot engine & CI workflow"

def run_cmd(cmd, allow_fail=False):
    """Executes a shell command and streams output in real-time."""
    print(f"[*] Executing: {' '.join(cmd) if isinstance(cmd, list) else cmd}")
    result = subprocess.run(cmd, shell=isinstance(cmd, str), capture_output=True, text=True)
    
    if result.returncode != 0 and not allow_fail:
        print(f"[!] Error executing command: {result.stderr.strip()}", file=sys.stderr)
        sys.exit(result.returncode)
    
    return result.stdout.strip()

def push_to_github():
    print("=" * 60)
    print(" GitHub Auto-Pusher for OryvexTop/Zest ")
    print("=" * 60)

    # 1. Check if git is installed
    try:
        run_cmd(["git", "--version"])
    except FileNotFoundError:
        print("[!] Git executable not found on system PATH. Please install Git.", file=sys.stderr)
        sys.exit(1)

    # 2. Initialize repo if not already a git repository
    if not os.path.exists(".git"):
        print("[*] Initializing new Git repository...")
        run_cmd(["git", "init"])
    else:
        print("[+] Existing Git repository detected.")

    # 3. Ensure a valid git branch name
    run_cmd(["git", "branch", "-M", BRANCH_NAME])

    # 4. Handle Remote Origin
    remotes = run_cmd(["git", "remote"], allow_fail=True)
    if "origin" in remotes.splitlines():
        print(f"[*] Updating 'origin' remote to {REPO_URL}...")
        run_cmd(["git", "remote", "set-url", "origin", REPO_URL])
    else:
        print(f"[*] Adding 'origin' remote -> {REPO_URL}...")
        run_cmd(["git", "remote", "add", "origin", REPO_URL])

    # 5. Stage files
    print("[*] Staging project files...")
    run_cmd(["git", "add", "."])

    # 6. Commit changes (skip if working tree is clean)
    status = run_cmd(["git", "status", "--porcelain"])
    if status:
        print(f"[*] Committing changes: '{COMMIT_MESSAGE}'...")
        run_cmd(["git", "commit", "-m", COMMIT_MESSAGE])
    else:
        print("[+] Working directory clean. Nothing new to commit.")

    # 7. Push to GitHub
    print(f"[*] Pushing to origin/{BRANCH_NAME}...")
    try:
        push_output = run_cmd(["git", "push", "-u", "origin", BRANCH_NAME])
        print(push_output)
        print("\n[✔] Successfully pushed code to https://github.com/OryvexTop/Zest")
        print("[*] GitHub Actions will now trigger and build your .jar artifact automatically.")
    except Exception as e:
        print(f"[!] Push failed: {e}")
        print("\n[Hint] If GitHub asks for authentication, ensure you use a Personal Access Token (PAT) or have SSH keys configured.")

if __name__ == "__main__":
    push_to_github()