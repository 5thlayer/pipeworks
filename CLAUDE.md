# Pipeworks

A 5thlayer Module: a mod its Consumers, other Modules or a pack, read from the local maven repository by version. See `GLOSSARY.md` for the domain glossary.

## Workflow

Commit on the current branch; open a feature branch only when the user asks for one. Nothing is pushed without the user's word. Asking a session to steward a PR is the user's word for that PR's pushes, its rebases onto `main`, and its merge, under the `steward` skill.

Anything that changes the Module's behaviour gets a `/code-review`: the project skill in `.claude/skills/code-review`, from mattpocock/skills, never the built-in review of the same name. Doc and plumbing changes skip it: that covers `CLAUDE.md`, `GLOSSARY.md`, ADRs, `docs/`, `.claude/`, submodule bumps, and tooling or CI config.

## Commits

Conventional commits: `<type>(<optional scope>): <summary>`, with the summary in the imperative and lower case. The types in use are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci` and `chore`. A breaking change marks its type with `!` (`feat!: ...`), and its release bumps the minor (ADR 0001). A commit that closes an issue ends its body with `Closes #<n>`.

## Testing

`sh ./gradlew build` runs the JUnit tests, on a plain JVM with no Minecraft. `sh ./gradlew runGameTestServer` runs the game tests headless, a real player on a real server with only Groundworks and the libworks runtime the Module nests beside it, and names each one it ran; it fails if it ran none. The `skillworks:gametest-loop` skill runs a subset, `--tests 'pipeworks:<glob>' 1`, one glob a run, so a ticket's new game tests share a name prefix that selects them all. JEI, EMI and Jade are on the dev client only. The libworks plugin's `checkPlaysAlone`, which `build` runs, fails on another Module's id, namespace or required dependency in the Module's code or resources (libworks ADR 0005), `PlaysAloneTest` on a tag the code reads that the game tests don't check, and `PlaysAloneTests` on a tag it reads or its data names that only another mod fills (FactoryWorks ADR-0127). Game-test code and test-only resources live in the `gametest` source set and never ship (libworks ADR 0011): `PipeworksGameTests` hooks itself in from `FMLConstructModEvent`, and `src/main` never names game-test code. Where shipped code must report to a test, `main` holds a hook with no test vocabulary that the game-test side fills. A new game test class is registered by a line in `PipeworksGameTests.registerTests`, and its tests stand on the `gametest/platform` structure that the libworks plugin's `sh ./gradlew buildGametestStructures` writes into `src/gametest/resources`. A test that needs a machine fills `gametest/TestPort`, a block registered only in the Module's own runs, and waits a tick after placing it, since a block entity's `onLoad` runs on the next tick. CI (`.github/workflows/ci.yml`, libworks' `module-ci.yml` at the plugin's tag) runs both on every push and never publishes. Data only the Module's own runs load lives in `src/gametest/resources`, which never ships (libworks ADR 0004): `DevPacks` switches on `gametest_pack/` in the game tests and `dev_pack/` in the dev client when the Module has one, the datapacks that stand in for what a Consumer fills; it has none today. Packs a test switches on itself, such as one per batch, go under `gametest_packs/<name>/`.

Vanilla's code, with NeoForge's patches, is in `build/moddev/artifacts/minecraft-patched-*-sources.jar`; NeoForge's own classes (`net/neoforged/...`) are in `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/<version>/*/neoforge-<version>-sources.jar`, `<version>` being `neoforge_version` in `gradle.properties` (the cache keeps older ones). Read either with `unzip -p <jar> <path>/X.java`, and check a claim about vanilla or NeoForge behaviour there before stating it.

The `skillworks:quicklaunch` skill opens the dev client into the most recent save in `run/saves`, one client per checkout.

## Releases

A change a Consumer can use or will notice adds its line under `## Unreleased` in `CHANGELOG.md` as it lands. Before bumping `mod_version`, publishing to `~/.m2`, tagging a release or uploading to Modrinth or CurseForge, read `docs/agents/releases.md`: releases go through the libworks plugin's `release` task, which uploads last with its `upload` task when `gradle.properties` names a project, and a published version never changes, in `~/.m2` or on either site. ADR 0001, inherited from 5thlayer/libworks, sets the version bumps.

A release that must reach a Consumer in another checkout, a Module or a pack that pins the jar, follows the `release-train` skill: one owning session per checkout, and pushes only on the user's word.

## Agent skills

### Issue tracker

Issues and specs live in this repo's GitHub Issues, managed with the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

The five canonical triage roles, used verbatim as label strings. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `GLOSSARY.md` and `docs/adr/` at the repo root. See `docs/agents/domain.md`.
