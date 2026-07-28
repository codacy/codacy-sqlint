[![Codacy Badge](https://api.codacy.com/project/badge/Grade/a00f953169e94bd3a373ebd8e6dcb69e)](https://www.codacy.com/gh/codacy/codacy-sqlint?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=codacy/codacy-sqlint&amp;utm_campaign=Badge_Grade)
[![Build Status](https://circleci.com/gh/codacy/codacy-sqlint.svg?style=shield&circle-token=:circle-token)](https://circleci.com/gh/codacy/codacy-sqlint)

# Codacy SQLint

This is the docker engine we use at Codacy to have [SQLint](https://github.com/purcell/sqlint) support.
You can also create a docker to integrate the tool and language of your choice!
Check the **Docs** section for more information.

## Usage

You can create the docker by doing:

```
sbt docker:publishLocal
```

The docker is ran with the following command:

```
docker run -it -v $srcDir:/src  <DOCKER_NAME>:<DOCKER_VERSION>
```

## Docs

[Tool Developer Guide](https://support.codacy.com/hc/en-us/articles/207994725-Tool-Developer-Guide)

[Tool Developer Guide - Using Scala](https://support.codacy.com/hc/en-us/articles/207280379-Tool-Developer-Guide-Using-Scala)

## Test

We use the [codacy-plugins-test](https://github.com/codacy/codacy-plugins-test) to test our external tools integration.
You can follow the instructions there to make sure your tool is working as expected.

## Agent Playbook: Updating This Repository End-to-End

This section is written for an AI coding agent (or a human) tasked with updating this repo — most commonly bumping the wrapped [SQLint](https://github.com/purcell/sqlint) version, but also base image / orb / engine-seed dependency bumps. Follow it top to bottom; it tells you what to change, how to build and test locally, and how to interpret CI so you can iterate on failures without guessing.

### 1. What this repository is

This is a **Codacy engine**: a thin Scala wrapper (`src/main/scala/codacy/Engine.scala` and `src/main/scala/codacy/sqlint/SQLint.scala`, built on `codacy-engine-scala-seed`) that packages [SQLint](https://github.com/purcell/sqlint) — a **Ruby gem**, not a Node package — as a Docker image Codacy's platform can run against a customer's SQL files. The wrapper itself simply shells out to the `sqlint` binary (`CommandRunner.exec(List("sqlint") ++ filesToLint, ...)`) and regex-parses its stdout.

Unlike some Codacy engines, `docs/` here is a small, **hand-maintained** structure, not a DocGenerator-produced one — there is no `DocGenerator.scala` or equivalent in this repo:

- `src/main/resources/docs/patterns.json` — declares a single pattern (`allIssues`) and, critically, a top-level `"version"` field. This field is **not just documentation** — `build.sbt` reads it at build time via the `toolVersion` sbt setting and uses it as the `gem install sqlint -v <version>` argument baked into the Docker image. This is the single source of truth for the SQLint version.
- `src/main/resources/docs/description/description.json` — human-readable title/description for the one pattern, used in the Codacy UI. Hand-maintained, rarely needs touching on a version bump.
- `src/main/resources/docs/tool-description.md` — short hand-written blurb about the tool.
- `src/main/resources/docs/tests/test1.sql` and `src/main/resources/docs/multiple-tests/with-config-file/*` — fixtures used by `codacy-plugins-test` (`pattern`/`multiple` style tests) to validate the engine's actual output against real SQL samples.

### 2. Files that encode versions — check all of these on every update

| File | What it controls | What to check |
|---|---|---|
| `src/main/resources/docs/patterns.json` → `"version"` | The SQLint gem version installed into the Docker image (read dynamically by `build.sbt`'s `toolVersion` setting, used in the `gem install --no-document sqlint -v $toolVersion` command) | Bump this field to the target SQLint version. Confirm the version exists on [RubyGems](https://rubygems.org/gems/sqlint) or in [purcell/sqlint releases](https://github.com/purcell/sqlint/releases). This is the **only** place the SQLint version is pinned — there is no separate Dockerfile or requirements file. |
| `build.sbt` → `com.codacy %% codacy-engine-scala-seed` | Codacy's engine SDK/base library | Check [Maven Central](https://mvnrepository.com/artifact/com.codacy/codacy-engine-scala-seed) for newer versions if asked to update it; not tied to SQLint bumps. |
| `build.sbt` → `dockerBaseImage` (currently `amazoncorretto:8-alpine3.17-jre`) | JRE base image the packaged app runs on | Only bump if asked explicitly, or if the alpine/JRE version is flagged as vulnerable/EOL — don't bump opportunistically. |
| `.circleci/config.yml` → `codacy/base` orb | Shared CircleCI steps (checkout, versioning, sbt build, docker publish, tagging) | Check the latest published version in the orb registry, or use `git log -p .circleci/config.yml` to see the bump history as a fallback reference. |
| `.circleci/config.yml` → `codacy/plugins-test` orb | Runs `codacy-plugins-test` in CI after the image is built | Same as above. |
| `project/build.properties` / `project/plugins.sbt` | sbt version / sbt plugins | Rarely needs touching; check only if the build itself fails to load. |

### 3. Step-by-step update procedure

1. **Bump the SQLint version** by editing the `"version"` field in `src/main/resources/docs/patterns.json` (and any CI orb versions in `.circleci/config.yml`, if in scope for the task).
2. There is no docs-generation step to run — `patterns.json` and `description.json` are hand-maintained and there's only one pattern, so no regeneration is needed. Just sanity-check that `description.json`'s `allIssues` entry still matches reality.
3. **Format/compile check:** `sbt "scalafmtCheckAll; scalafmtSbtCheck"` (matches what CI runs in `publish_docker_local`). Run `sbt scalafmt` first if it fails.
4. **Build the Docker image locally**: `sbt docker:publishLocal` (produces `codacy-sqlint:1.0` per `version in Docker`, or override the name/version to match CI: `sbt 'set name := "codacy-sqlint"' 'set Docker / version := "latest"' docker:publishLocal`).
5. **Run `codacy-plugins-test` locally** before pushing — clone https://github.com/codacy/codacy-plugins-test and run it against your local image tag, exercising both the single-pattern test (`docs/tests/test1.sql`) and the multiple-tests fixture (`docs/multiple-tests/with-config-file`).
6. **Iterate on failures**, re-running the relevant test command after each fix.
7. **Commit** the version bump together with any CI/orb changes in one change.
8. **Push and open a PR.** CI (`.circleci/config.yml`) runs `checkout_and_version` -> `publish_docker_local` (scalafmt checks + `docker:publishLocal`) -> `plugins_test` (via the `codacy_plugins_test/run` orb job, with `run_multiple_tests: true`) -> `publish_docker` (master only) -> `tag_version`.
9. **Poll the PR's real CI checks until they all pass — local validation is NOT the finish line.** After every push, run `gh pr checks <pr-url>` and keep re-polling (short sleep while any check is `pending`) until all checks finish. If a check fails, fetch its actual log (CircleCI API/UI for the failing job — don't guess), find the true root cause, fix it, push again (never `--no-verify`, never force-push), and re-poll. Repeat until every check is green. The CI environment's toolchain can differ from your local one, so a clean local run does not guarantee CI passes. Only stop iterating when every check passes, or you hit a genuine product/infra decision that needs a human — in which case explain it in the PR rather than guessing.

### 4. Definition of done

- SQLint version bumped in `src/main/resources/docs/patterns.json` (`"version"` field) — the single source of truth for the installed gem version.
- Any CI orb/base-image bumps in scope reflected in `.circleci/config.yml` / `build.sbt`.
- `scalafmtCheckAll` / `scalafmtSbtCheck` pass locally.
- Docker image builds successfully via `sbt docker:publishLocal`.
- `codacy-plugins-test` passes locally against the freshly built image (single-pattern and multiple-tests fixtures).
- **After pushing and opening/updating the PR, every CI check on it is green.** Poll `gh pr checks <pr-url>` and iterate on any failure (fetch the real CI log, fix, push, re-poll) until all pass — a passing local build is not sufficient, because the CI toolchain can differ from your local one (see step 9).

## What is Codacy?

[Codacy](https://www.codacy.com/) is an Automated Code Review Tool that monitors your technical debt, helps you improve your code quality, teaches best practices to your developers, and helps you save time in Code Reviews.

### Among Codacy’s features:

 - Identify new Static Analysis issues
 - Commit and Pull Request Analysis with GitHub, BitBucket/Stash, GitLab (and also direct git repositories)
 - Auto-comments on Commits and Pull Requests
 - Integrations with Slack, HipChat, Jira, YouTrack
 - Track issues in Code Style, Security, Error Proneness, Performance, Unused Code and other categories

Codacy also helps keep track of Code Coverage, Code Duplication, and Code Complexity.

Codacy supports PHP, Python, Ruby, Java, JavaScript, and Scala, among others.

### Free for Open Source

Codacy is free for Open Source projects.
