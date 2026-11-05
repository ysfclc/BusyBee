# BusyBee Control Repository Setup

BusyBee is the phase-import control repository. It receives complete project ZIP snapshots under `incoming/`, validates them, and imports each snapshot into a private target repository whose name is derived from the ZIP filename.

## Required repository secret

The import workflow expects an Actions secret named:

- `REPO_CREATE_TOKEN`

Configure it in **Settings → Secrets and variables → Actions → New repository secret**.

Use a dedicated GitHub token owned by the account that should own the target repositories. It must be able to create private repositories for that account and push commits to those repositories. For a classic personal access token, the `repo` scope is required for private repositories. Prefer a fine-grained token with the minimum repository/account permissions supported by GitHub for repository creation and content writes.

Do not paste the token into repository files, workflow YAML, issues, commits or chat. Rotate it if it is exposed.

## Android signing (optional)

To produce a signed release APK, configure all four secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

If any are missing, the workflow builds a debug APK. Never commit the keystore or signing passwords.

## Import contract

- Place exactly one complete project archive at `incoming/<project-name>.zip`.
- The filename becomes the target private repository name.
- Use lowercase letters, digits, dots, underscores and hyphens; start and end with an alphanumeric character.
- The archive may contain a single enclosing directory; the workflow flattens it.
- The workflow rejects unsafe paths, symlinks, duplicate normalised paths, oversized entries, likely secrets and empty snapshots.
- It removes common caches, local environment files, signing material and generated Android binaries.
- The workflow imports a **complete snapshot**, replacing the target repository's tracked tree while preserving Git history.
- Do not upload a patch-only ZIP.
- The workflow builds an APK only when both `rust/` and `android/` project roots are present.

## Workflow validation

The import workflow runs actionlint before processing an archive. The current baseline had one failed run caused by ShellCheck findings in three embedded scripts; Phase 1 addresses those findings. Check the Actions tab after a workflow change and do not treat a green resolve job as a successful import.
