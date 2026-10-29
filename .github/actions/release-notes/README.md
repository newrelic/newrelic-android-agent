# Release notes for the mobile agents

Shared automation that writes docs-website release notes, in the
[release-notes template](https://github.com/newrelic/docs-website/blob/develop/templates/release-notes-template.mdx)
format, for the Android and iOS agents and the hybrid agents (React Native, Cordova, Capacitor,
Flutter, Unity, .NET MAUI). A person edits every note before it is published.

Every note has the template's layout: the frontmatter `subject`, `releaseDate`, `version`, the
`features`, `enhancements`, `bugs` and `security` lists, then `downloadLink` (no `category:`), and a
`### New features`, `### Improvements and enhancements`, `### Bug fixes` or `### Security updates`
heading for each section that has bullets. Each frontmatter list item is the bullet's summary: the
whole bullet when it is 200 characters or fewer, else its first sentence, else the text before its
first colon, else the first 200 characters cut at a word with an ellipsis. Summaries are worked out
again from the bullet text every time a note is built, so editing a bullet on the next draft is
enough.

- **`append`**, on every merged PR: builds the PR's bullet and adds it to the agent's `<agent>-next`
  branch on `newrelic/docs-website`, behind a draft "next release notes" PR that is never merged.
- **`generate`**, on every release: reconciles the accumulated bullets, every PR between the previous
  release tag and the new one, and (optionally) the version's `CHANGELOG.md` entries, writes the
  release `.mdx`, opens it as a draft docs-website PR (ready for review with `readyForReview`), and
  removes the released bullets from the next branch. With `fork-pat`, the release branch lives on
  the bot's fork of docs-website, so people without write access to docs-website can edit the note
  (see "Editing a release note on the fork").
- **`check`**, on every open PR: comments with the section and the bullet the PR will become, and
  fails when a user-facing PR does not carry exactly one `release-note:*` label. With
  `labelledOnly`, a PR with no label passes, and the comment says it is left out.
- **`ready`**, run by hand once a person has checked a release's note: marks its draft docs-website
  PR ready for review and comments with who checked it. It runs as the bot that opened the PR, so it
  works for people without write access to docs-website.

Everything agent-specific lives in the agent's repo (`.github/release-notes.json` and a skeleton);
nothing in this folder names an agent.

## How a bullet is built

1. **Text:** a `## Release note` heading (any level, "note" or "notes") followed by a paragraph in the
   PR description, or a `Release note: ...` line, is used as written. Otherwise the title is used with
   its Conventional Commit type and Jira key removed and the leading verb put in the past tense
   (`fix: guard against null filename (NR-1)` becomes "Guarded against null filename."). `N/A` in
   the section means "use the title". A paragraph of two or more lines that all start with `- ` or
   `* ` is a list: each line becomes its own bullet, all in the PR's one section and all linking the
   PR (the native-agent bumps write one line per updated agent). A single `- ` line, or prose mixed
   with list lines, is one bullet.
2. **Section:** the PR's `release-note:*` label:

   | Label | Section |
   |---|---|
   | `release-note:feature` | New features |
   | `release-note:enhancement` | Improvements and enhancements (native agent version bumps go here) |
   | `release-note:bug` | Bug fixes (say what was broken) |
   | `release-note:security` | Security updates |

   With no label (or more than one) the section is guessed from the title type, the PR gets a bot
   comment, and the docs PR lists it under "Bullets that need extra attention". With
   `labelledOnly`, a PR with no label is left out instead (see 3); one with several is still included
   and flagged.
3. **Left out:** PRs with `[skip-docs]` in the description; release, back-port, revert and
   `main`/`develop` sync PRs; and, unless labelled, `docs`, `test`, `ci`, `chore`, `build` and
   `style` PRs and Dependabot PRs. With `labelledOnly`, also every PR with no `release-note:*`
   label: a label added after the merge still counts, because `generate` reads each PR's labels
   again at release time. Every PR in a release that gets no bullet is listed in the docs PR.

## Adopting it in an agent repo

1. **Config.** Add `.github/release-notes.json` (fields below).
2. **Skeleton.** Commit `<skeleton>` (usually `<agent>-agent-next.mdx` at the repo root), copied from
   `test/agents/<agent>/` in this folder or from the example under "Skeleton" below. Its `subject:`
   must match the config. Anything after `{/* release-note:end */}` ends every note (Android's Support
   statement); the hybrids have nothing there. If the repo publishes root files (an npm package with
   no `files` field, a git-installed plugin, a pub package), exclude the skeleton from the package.
3. **Callers.** Add the four workflows below, with the repo's own PR base branch.
4. **Dry run.** Run `generate` with `dry-run: true` for the latest real release (see "Trying it
   out") and compare the job summary with the note already on docs-website.
5. **Labels.** Create the four labels:
   ```bash
   for l in feature enhancement bug security; do
     gh label create "release-note:$l" --repo newrelic/<repo> --color 0E8A16 --description "Release notes section"
   done
   ```
6. **Native agent bumps.** Make the repo's native-agent update workflow add the
   `release-note:enhancement` label to its PR (the label must exist first) and a `## Release note`
   section that repeats its CHANGELOG lines word for word, so the release sees one change, not two.
7. **Switch over.** Replace the old docs job of the release workflow with the call to
   `release-notes-generate.yml` (below), and delete the old `.github/scripts/generate-docs.sh`.
8. **Secrets.** `BOT_PAT` (contents and pull-requests write on `newrelic/docs-website`) and
   `BOT_EMAIL`. Optionally `BOT_FORK_PAT`, for editable release notes (see "Editing a release note on
   the fork"). The optional repo variable `DOCS_REPO` points the callers at a docs-website fork.

### `.github/release-notes.json`

```json
{
  "subject": "React Native agent",
  "docsFolder": "react-native-release-notes",
  "filePrefix": "react-native-agent-",
  "nextBranch": "react-native-next",
  "tagPrefix": "v",
  "skeleton": "react-native-agent-next.mdx",
  "downloadLink": "https://www.npmjs.com/package/{npmPackage}/v/{version}",
  "changelog": "CHANGELOG.md"
}
```

| Field | Required | Meaning |
|---|---|---|
| `subject` | yes | Frontmatter `subject:`, e.g. `Flutter agent`. Letters, digits, spaces and `. + -`. Must match the skeleton's `subject:`. |
| `docsFolder` | yes | Folder under docs-website's `src/content/docs/release-notes/mobile-release-notes/`. |
| `filePrefix` | yes | The release file is prefix + version digits + `.mdx`: `cordova-agent-` gives `cordova-agent-7112.mdx`. |
| `nextBranch` | yes | Accumulation branch on docs-website; ends in `-next`. `flutter-next` gives release branches `flutter_release_notes_1-3-3`. |
| `tagPrefix` | yes | Text before the version in release tags: `v`, or `""` for `7.8.4`-style tags. Only `<tagPrefix>X.Y.Z` tags are releases. |
| `skeleton` | yes | Path of the committed skeleton. Its file name is also the accumulating file's name on docs-website. |
| `downloadLink` | no | `https://` template. `{version}` is `1.9.3`, `{tag}` is the tag (`v1.9.3`), `{npmPackage}` is `name` in `package.json` at the tag. Left out: no `downloadLink:` line. |
| `changelog` | no | CHANGELOG to reconcile against. Left out: none is read. |
| `fallbackText` | no | Bullet used when a release has no bullets at all. Default `Updated version.` |
| `prLinks` | no | `true` or `false`. Default `true`: each bullet ends with its PR link, `([#123](…))`. `false` leaves the links out of the published note only; the next draft and the docs PR text keep them. |
| `labelledOnly` | no | `true` or `false`. Default `false`. `true`: only PRs with a `release-note:*` label become bullets (for repos whose release and sync PRs the skip rules don't recognise); `check` passes unlabelled PRs and says they are left out. |
| `readyForReview` | no | `true` or `false`. Default `false`: `generate` opens the docs PR as a draft, for `ready` to mark later. `true` opens it ready for review, and its text says why. A PR that is already open keeps its state. |

`config.sh` validates all of this first in every action, and in the `check` action on every PR, and
names the field at fault.

iOS (plain `7.7.11` tags, unlabelled release and sync PRs, notes without PR links) adds
`"tagPrefix": ""`, `"labelledOnly": true` and `"prLinks": false`.

The CHANGELOG parser takes the version's section from a heading of any level whose text starts with
the version (`# 7.1.12`, `## 1.6.13`, `### 1.3.2`, `## v1.9.2 (date)`, `## [1.3.2] - date`) to the
next such heading. Other headings inside it name the section: security, fix/bug, feature or "New in
this release"; anything else is an enhancement. List items are entries; indented lines under an item
continue it.

### Skeleton

```mdx
---
subject: React Native agent
releaseDate: 'YYYY-MM-DD'
version: next
features: []
enhancements: []
bugs: []
security: []
downloadLink: 'https://www.npmjs.com/package/newrelic-react-native-agent/v/X.Y.Z'
---

{/* release-note:feature */}
## New features

{/* release-note:enhancement */}
## Improvements and enhancements

{/* release-note:bug */}
## Bug fixes

{/* release-note:security */}
## Security updates

{/* release-note:end */}
```

The skeleton only starts the next draft. Its frontmatter values and `##` headings are placeholders:
the release note always gets the layout described at the top, whatever an older skeleton has (a
`category:` line, another key order).

### Callers

Each action downloads this repository at `develop` (a few seconds). Replace `main` with the repo's PR
base branch.

`.github/workflows/release-notes-append.yml`:

```yaml
name: Release notes - append

on:
  pull_request:
    types: [closed]
    branches: [main]

permissions:
  contents: read
  pull-requests: write # the label-request comment on an unlabelled PR

jobs:
  append:
    # Fork PRs get no secrets and Dependabot merges a read-only token; the release run covers both.
    # Job level, so a PR closed without merging never takes the concurrency slot.
    if: >-
      github.event.pull_request.merged == true
      && github.event.pull_request.head.repo.full_name == github.repository
      && github.actor != 'dependabot[bot]'
    concurrency:
      group: docs-release-notes-next
      cancel-in-progress: false
    runs-on: ubuntu-latest
    timeout-minutes: 15
    steps:
      - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4
        with:
          ref: ${{ github.event.pull_request.base.ref }}
          fetch-depth: 0 # tags, for the already-released check
          persist-credentials: false
      - uses: newrelic/newrelic-android-agent/.github/actions/release-notes/append@develop
        with:
          bot-pat: ${{ secrets.BOT_PAT }}
          bot-email: ${{ secrets.BOT_EMAIL }}
          docs-repo: ${{ vars.DOCS_REPO || 'newrelic/docs-website' }}
```

`.github/workflows/release-notes-check.yml`:

```yaml
name: Release notes - check

on:
  pull_request:
    types: [opened, edited, reopened, synchronize, labeled, unlabeled]
    branches: [main]

permissions:
  contents: read
  pull-requests: write # the report comment

concurrency:
  group: release-notes-check-${{ github.event.pull_request.number }}
  cancel-in-progress: false # a cancelled run could leave its comment half-posted

jobs:
  check:
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4
        with:
          persist-credentials: false
      - uses: newrelic/newrelic-android-agent/.github/actions/release-notes/check@develop
```

`.github/workflows/release-notes-generate.yml` comes in two shapes, depending on how the repo
releases.

**The release is created by a workflow** (React Native, Capacitor, Cordova, Unity). A release created
with `GITHUB_TOKEN` fires no `release` event, so the release workflow calls this one:

```yaml
name: Release notes - generate

on:
  workflow_call:
    inputs:
      version:
        description: "Release version, e.g. 1.9.3 (its tag must exist)"
        type: string
        required: true
  workflow_dispatch:
    inputs:
      version:
        description: "Release version, e.g. 1.9.3 (its tag must exist)"
        required: true
      overwrite:
        description: "Replace an existing release-notes branch or file for this version (discards edits on it)"
        type: boolean
        default: false
      dry-run:
        description: "Only build the note into the job summary"
        type: boolean
        default: false

permissions:
  contents: write # POST /releases/generate-notes needs it

jobs:
  generate:
    concurrency:
      group: docs-release-notes-next
      cancel-in-progress: false
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4
        with:
          fetch-depth: 0 # tags and history
          persist-credentials: false
      - uses: newrelic/newrelic-android-agent/.github/actions/release-notes/generate@develop
        with:
          version: ${{ inputs.version }}
          overwrite: ${{ inputs.overwrite || false }}
          dry-run: ${{ inputs.dry-run || false }}
          bot-pat: ${{ secrets.BOT_PAT }}
          fork-pat: ${{ secrets.BOT_FORK_PAT }}
          bot-email: ${{ secrets.BOT_EMAIL }}
          docs-repo: ${{ vars.DOCS_REPO || 'newrelic/docs-website' }}
```

and the release workflow's old docs job becomes:

```yaml
  update-documentation:
    needs: release
    uses: ./.github/workflows/release-notes-generate.yml
    with:
      version: ${{ needs.release.outputs.version }}
    secrets: inherit
```

**A person publishes the release** (Android, iOS, Flutter, .NET MAUI). The `release: published` event
fires, so the caller listens for it, and `release-date: published` dates the note from the publish
date, also when the workflow is run by hand:

```yaml
name: Release notes - generate

on:
  release:
    types: [published]
  workflow_dispatch:
    inputs:
      version:
        description: "Release version, e.g. 7.8.4 (its tag must exist)"
        required: true
      overwrite:
        description: "Replace an existing release-notes branch or file for this version (discards edits on it)"
        type: boolean
        default: false
      dry-run:
        description: "Only build the note into the job summary"
        type: boolean
        default: false

permissions:
  contents: write # POST /releases/generate-notes needs it

jobs:
  generate:
    # Pre-releases (nightly-*) are not releases.
    if: github.event_name == 'workflow_dispatch' || github.event.release.prerelease == false
    concurrency:
      group: docs-release-notes-next
      cancel-in-progress: false
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4
        with:
          fetch-depth: 0 # tags and history
          persist-credentials: false
      - uses: newrelic/newrelic-android-agent/.github/actions/release-notes/generate@develop
        with:
          version: ${{ inputs.version || github.event.release.tag_name }}
          release-date: published
          overwrite: ${{ inputs.overwrite || false }}
          dry-run: ${{ inputs.dry-run || false }}
          bot-pat: ${{ secrets.BOT_PAT }}
          fork-pat: ${{ secrets.BOT_FORK_PAT }}
          bot-email: ${{ secrets.BOT_EMAIL }}
          docs-repo: ${{ vars.DOCS_REPO || 'newrelic/docs-website' }}
```

A `release` event runs the workflow file as it is at the tagged commit, so the file must be on the
branch releases are tagged from before the first release that should use it.

**A cancelled generate run must be run again by hand.** `append` and `generate` share the
concurrency group `docs-release-notes-next`, so a release never races a bullet push. GitHub keeps
only one *pending* run per group: when another run queues behind a waiting one, the waiting run is
cancelled. A cancelled append costs nothing, because the release run builds any missing bullet. A
cancelled generate leaves the release without a docs PR and its bullets on the next branch. If a
generate run shows as cancelled, run `release-notes-generate.yml` by hand with that version.

`.github/workflows/release-notes-ready.yml`, run from the repo's Actions tab after checking a
release's note:

```yaml
name: Release notes - ready

on:
  workflow_dispatch:
    inputs:
      version:
        description: "Release whose docs-website note you've checked, e.g. 1.9.3"
        required: true

permissions:
  contents: read

jobs:
  ready:
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4
        with:
          persist-credentials: false
      - uses: newrelic/newrelic-android-agent/.github/actions/release-notes/ready@develop
        with:
          version: ${{ inputs.version }}
          bot-pat: ${{ secrets.BOT_PAT }}
          docs-repo: ${{ vars.DOCS_REPO || 'newrelic/docs-website' }}
```

It finds the open docs PR on the version's release branch (`<agent>_release_notes_1-9-3`), marks it
ready for review, and comments "Marked ready for review by @<you>" with a link to the run. A PR that
is already ready is left alone. Like every `workflow_dispatch` workflow, it shows in the Actions tab
only once it is on the default branch.

### Editing a release note on the fork

docs-website gives write access only to bot accounts, so release branches it holds can't be edited
by the people who check the notes. With `fork-pat`, `generate` pushes each release's branch to the
bot's fork of docs-website instead and opens the draft PR from there; fork collaborators can push to
it. The next drafts (`<agent>-next`) stay on docs-website: nobody edits those.

One-time setup, signed in as the bot account:

1. Fork `newrelic/docs-website` into the bot account, keeping the name `docs-website`.
2. In the fork's **Settings → Collaborators**, invite the people who check release notes.
3. Create a fine-grained token: resource owner the bot account, repository access only the fork,
   **Contents** and **Workflows** read and write. Each branch is built on docs-website's latest
   `develop`, so its push carries upstream commits the fork lacks, and GitHub refuses those that
   touch `.github/workflows/` without Workflows. Actions are off in forks, so nothing runs there.
4. Add it as the repository secret `BOT_FORK_PAT` in each agent repo, next to `BOT_PAT`, and pass
   `fork-pat: ${{ secrets.BOT_FORK_PAT }}` to `generate` (the callers above do).

`BOT_PAT` still opens and updates the PR on docs-website. A repo without `BOT_FORK_PAT` keeps its
branches on docs-website, and an already open PR is always updated where its branch is.

To edit a note: open the docs PR, follow its branch to the fork, and edit the file there (the GitHub
web editor works) or push to the branch with git. Then run `release-notes-ready.yml`. GitHub opens
these PRs without "allow edits by maintainers" (`BOT_PAT` has no access to the bot's fork; the live
check showed `maintainerCanModify: false`), so the docs team suggests changes rather than pushing
them. If GitHub ever refuses the request outright, generate retries without it and says so.

## Action inputs

`append`, `check` and `generate` take `github-token` (default: the run's token), `config` (default
`.github/release-notes.json`) and `dry-run` (default `false`).

| Action | Other inputs |
|---|---|
| `append` | `bot-pat`, `bot-email`, `docs-repo` (default `newrelic/docs-website`), `pr-number` (default: the event's PR) |
| `check` | none |
| `generate` | `version` (required; `1.9.3` or `v1.9.3`), `overwrite`, `release-date` (`YYYY-MM-DD`, or `published` for the GitHub release's publish date; default: the tagged commit's date), `bot-pat`, `bot-email`, `docs-repo`, `fork-pat` (default empty: release branches stay on docs-repo) |
| `ready` | `version` (required; `1.9.3` or `v1.9.3`), `bot-pat` (required), `docs-repo`, `config`. No `github-token` or `dry-run`: it reads nothing but the docs PR and changes only its draft state and comments. |

`bot-pat` reaches only the steps that talk to docs-website, and `fork-pat` only generate's two that
publish (or preview) the release branch; the steps that read PR text use `github-token`. With
`dry-run: true` nothing on docs-website changes and `bot-pat` is not needed: the note (or the bullet)
and the docs PR text go to the job summary. A real `generate` run never overwrites a release branch
or file that differs from what it built unless `overwrite` is `true`.

`generate` writes the note in the template layout described at the top, whatever the skeleton's
frontmatter or headings. `prLinks`, `labelledOnly` and `readyForReview` come from the config, not
from inputs, so one setting covers every caller.

## Trying it out

- **Dry run on an adoption PR.** `workflow_dispatch` only works once a workflow is on the default
  branch, so add a temporary workflow to the PR and delete it before merging:

  ```yaml
  name: Release notes - dry run (delete before merging)
  on:
    pull_request:
  permissions:
    contents: write
    pull-requests: read
  jobs:
    dry-run:
      runs-on: ubuntu-latest
      steps:
        - uses: actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5 # v4
          with:
            fetch-depth: 0
            persist-credentials: false
        - uses: newrelic/newrelic-android-agent/.github/actions/release-notes/generate@develop
          with:
            version: <latest released version>
            dry-run: true
        - uses: newrelic/newrelic-android-agent/.github/actions/release-notes/append@develop
          with:
            pr-number: <a merged PR>
            dry-run: true
  ```

- **Against a docs-website fork.** Set the repo variable `DOCS_REPO` to the fork, set `BOT_PAT` to a
  token with write access to it, and create a `documentation` label in the fork. Merge test PRs and
  run `generate` by hand.

## Changing this code

Every merge to `develop` reaches every adopted repo on its next run, so:

- Run `bash .github/actions/release-notes/test/run.sh`,
  `bash .github/actions/release-notes/test/lint-actions.sh` and
  `shellcheck --severity=warning -x .github/actions/release-notes/scripts/*.sh .github/actions/release-notes/test/*.sh`.
  CI (`release-notes-tests.yml`) runs them too, plus dry runs on Android's real history.
- `test/test_agents.sh` builds a note for every agent's sample config from the same inputs and
  compares it with `test/expected/<agent>.mdx`. After an intended change, run it with
  `UPDATE_EXPECTED=1` and review the diff of `test/expected/` in the PR.
- Add new config fields as optional, with a default, so no repo has to change in step.
- The scripts are bash 3.2 compatible (macOS) and run in the C locale.
- To undo a change, revert it on `develop`.
