import os
import subprocess
import sys
import getpass

def run(cmd, env=None):
    """Run shell command with live stdout."""
    res = subprocess.run(cmd, shell=True, text=True, capture_output=True, env=env)
    if res.returncode != 0:
        print(f"[!] Error: {res.stderr.strip()}")
        return False, res.stderr.strip()
    return True, res.stdout.strip()

def main():
    print("=" * 60)
    print(" 🚀 One-Click GitHub Pusher (ZestKnockback)")
    print("=" * 60)

    # 1. Ask for Target Username / Repo & Token (to bypass 403 / auth blocks)
    default_user = "muvixo"
    username = input(f"Enter your active GitHub username [{default_user}]: ").strip() or default_user
    repo_name = input("Enter repository name [Zest]: ").strip() or "Zest"
    
    print("\n💡 Note: To push without popups, generate a GitHub Personal Access Token (PAT):")
    print("   Go to: https://github.com/settings/tokens (classic) -> select 'repo' scope")
    token = getpass.getpass("Enter your GitHub Personal Access Token (PAT): ").strip()

    if not token:
        remote_url = f"https://github.com/{username}/{repo_name}.git"
    else:
        # Embed token directly into the HTTPS clone URL for seamless authentication
        remote_url = f"https://{username}:{token}@github.com/{username}/{repo_name}.git"

    print("\n[*] Initializing and preparing files...")

    # 2. Configure Git config if missing
    run('git config user.name "Muvixo"')
    run('git config user.email "contact@muvixo.dev"')

    # 3. Init or refresh local git
    if not os.path.exists(".git"):
        run("git init")

    run("git branch -M main")

    # 4. Set Remote
    run("git remote remove origin")
    success, err = run(f'git remote add origin "{remote_url}"')

    # 5. Add, Commit, Push
    print("[*] Staging all project files & GitHub Action workflows...")
    run("git add .")

    print("[*] Committing...")
    run('git commit -m "feat: complete ZestKnockback Spigot engine build pipeline"')

    print(f"[*] Pushing code to {username}/{repo_name} (main branch)...")
    success, out = run("git push -u origin main --force")

    if success:
        print("\n" + "=" * 60)
        print(" [✔] PUSH SUCCESSFUL!")
        print(f" 📦 Repo Link: https://github.com/{username}/{repo_name}")
        print(f" ⚙️ GitHub Actions: https://github.com/{username}/{repo_name}/actions")
        print("=" * 60)
        print("Your .jar file is now building automatically on GitHub Actions.")
    else:
        print("\n[!] Push failed. Make sure the repository exists under this account and the token is valid.")

if __name__ == "__main__":
    main()