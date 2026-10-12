# Contributing

Contributions are always welcome. Before contributing please read the
[code of conduct](https://github.com/newrelic/.github/blob/main/CODE_OF_CONDUCT.md) and [search the issue tracker](issues); your issue may have already been discussed or fixed in `main`. To contribute,
[fork](https://help.github.com/articles/fork-a-repo/) this repository, commit your changes, and [send a Pull Request](https://help.github.com/articles/using-pull-requests/).

Note that our [code of conduct](https://github.com/newrelic/.github/blob/main/CODE_OF_CONDUCT.md) applies to all platforms and venues related to this project; please follow it in all your interactions with the project and its participants.

## Feature Requests

Feature requests should be submitted in the [Issue tracker](../../issues), with a description of the expected behavior & use case, where they’ll remain closed until sufficient interest, [e.g. :+1: reactions](https://help.github.com/articles/about-discussions-in-issues-and-pull-requests/), has been [shown by the community](../../issues?q=label%3A%22votes+needed%22+sort%3Areactions-%2B1-desc).
Before submitting an Issue, please search for similar ones in the
[closed issues](../../issues?q=is%3Aissue+is%3Aclosed+label%3Aenhancement).

## Pull Requests

1. Ensure any install or build dependencies are removed before the end of the layer when doing a build.
2. Increase the version numbers in any examples files and the README.md to the new version that this Pull Request would represent. The versioning scheme we use is [SemVer](http://semver.org/).
3. You may merge the Pull Request in once you have the sign-off of two other developers, or if you do not have permission to do that, you may request the second reviewer to merge it for you.

## Release notes

Release notes for the Android agent are drafted automatically from merged pull requests and reviewed by a human before they are published on docs.newrelic.com.

**Every PR merged to `develop` must carry exactly one of these labels.** The label decides which section of the published note the PR appears in:

| Label | Section |
|---|---|
| `release-note:feature` | New features |
| `release-note:enhancement` | Improvements and enhancements |
| `release-note:bug` | Bug fixes |
| `release-note:security` | Security updates |

- The bullet is built from your PR. If the description has a `## Release note` heading (or a `Release note:` line), that text is used as written, so say what changed for app developers or their users and, for a bug, what was broken. Otherwise the title is used with its type prefix and Jira key removed and a leading verb put in the past tense (`fix: guard against null filename (NR-1)` becomes "Guarded against null filename."). Write `N/A` to defer to the title.
- Unlabelled PRs whose title type is `docs`, `test`, `ci`, `chore`, `build` or `style`, and Dependabot PRs, are left out as not user-facing; add a `release-note:*` label to include one.
- Use `security:` as the title type for a security fix, or the `release-note:security` label.
- Put `[skip-docs]` anywhere in the PR description to leave a PR out (internal refactors, release chores).
- The `Check PR release notes` check comments on your PR with the section and the exact bullet it will produce, and goes red if there is not exactly one `release-note:*` label. Title style, titles over 70 characters and a missing `## Release note` section are only suggestions. A PR that merges without a label is still included (section guessed from the title, bot comment), but please label it.
- When a release is published, a draft PR is opened on `newrelic/docs-website`. **Edit it before it merges**: check that each bullet is specific and user-facing, sits in the right section, and is mirrored in the frontmatter array.
- Workflows: `.github/workflows/append-docs-release-notes.yml` (per PR) and `.github/workflows/generate-docs-release-notes.yml` (per release). Both need the `BOT_PAT` and `BOT_EMAIL` repository secrets.

## Contributor License Agreement

Keep in mind that when you submit your Pull Request, you'll need to sign the CLA via the click-through using CLA-Assistant. If you'd like to execute our corporate CLA, or if you have any questions, please drop us an email at opensource@newrelic.com.

For more information about CLAs, please check out Alex Russell’s excellent post,
[“Why Do I Need to Sign This?”](https://infrequently.org/2008/06/why-do-i-need-to-sign-this/).

## Slack

We host a public Slack with a dedicated channel for contributors and maintainers of open source projects hosted by New Relic.  If you are contributing to this project, you're welcome to request access to the #oss-contributors channel in the newrelicusers.slack.com workspace.  To request access, please use this [link](https://join.slack.com/t/newrelicusers/shared_invite/zt-1ayj69rzm-~go~Eo1whIQGYnu3qi15ng).
