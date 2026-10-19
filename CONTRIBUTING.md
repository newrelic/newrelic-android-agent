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

Release notes for the Android agent are drafted automatically from merged pull requests and reviewed by a person before they are published on docs.newrelic.com. The automation is shared with the hybrid agents and lives in [`.github/actions/release-notes/`](.github/actions/release-notes/README.md); its README explains how each bullet is built.

**Every user-facing PR merged to `develop` should carry exactly one of these labels.** The label decides which section of the published note the PR appears in:

| Label | Section |
|---|---|
| `release-note:feature` | New features |
| `release-note:enhancement` | Improvements and enhancements |
| `release-note:bug` | Bug fixes |
| `release-note:security` | Security updates |

- If the description has a `## Release note` heading (or a `Release note:` line), that text becomes the bullet as written, so say what changed for app developers or their users and, for a bug, what was broken. Otherwise the title is used with its type prefix and Jira key removed and the leading verb put in the past tense. Write `N/A` to use the title.
- Unlabelled `docs`, `test`, `ci`, `chore`, `build` and `style` PRs, and Dependabot PRs, are left out; add a label to include one. Put `[skip-docs]` in the description to leave any PR out.
- The `Release notes - check` check comments on your PR with the section and the exact bullet, and goes red when a user-facing PR into `develop` does not have exactly one `release-note:*` label.
- When a release is published, `release-notes-generate.yml` opens a draft PR on `newrelic/docs-website`. **Edit it before it merges**: each bullet specific and user-facing, in the right section, and mirrored in its frontmatter array; check the "PRs left out" list in the PR description. To rebuild a note, run that workflow by hand with the version.
- Settings are in `.github/release-notes.json`. The workflows need the `BOT_PAT` and `BOT_EMAIL` repository secrets.

## Contributor License Agreement

Keep in mind that when you submit your Pull Request, you'll need to sign the CLA via the click-through using CLA-Assistant. If you'd like to execute our corporate CLA, or if you have any questions, please drop us an email at opensource@newrelic.com.

For more information about CLAs, please check out Alex Russell’s excellent post,
[“Why Do I Need to Sign This?”](https://infrequently.org/2008/06/why-do-i-need-to-sign-this/).

## Slack

We host a public Slack with a dedicated channel for contributors and maintainers of open source projects hosted by New Relic.  If you are contributing to this project, you're welcome to request access to the #oss-contributors channel in the newrelicusers.slack.com workspace.  To request access, please use this [link](https://join.slack.com/t/newrelicusers/shared_invite/zt-1ayj69rzm-~go~Eo1whIQGYnu3qi15ng).
