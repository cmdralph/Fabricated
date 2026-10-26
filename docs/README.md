# Fabricated's documentation

Each fact lives in one document, and the others link to it. This table says which document holds what, and when it
changes. Every working session updates `roadmap.md` and `CHANGELOG.md`.

| Document | What it holds | Update it when |
|---|---|---|
| [`../README.md`](../README.md) | NeoForge users' guide: installing, what works, troubleshooting, the config file. It is `main`'s README | What players see changes |
| [`readme/forge.md`](readme/forge.md), [`readme/forge-legacy.md`](readme/forge-legacy.md) | The `forge` and `forge-legacy` branches' READMEs, which `branch-readme.yml` copies to each branch's `.github/README.md` | That platform's status changes |
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md) | The project's layout, building and testing, the code rules, adding a Minecraft version, releasing | The layout, the build or the release steps change |
| [`claude.md`](claude.md) | The working rules, the branch model and a map of the code, for whoever works on it | A rule, the branch model or a seam changes |
| [`roadmap.md`](roadmap.md) | The next release's goals, where each track stands and what comes next, the findings log, what is unverified | Every session |
| [`CHANGELOG.md`](CHANGELOG.md) | What each working session changed and how it was verified, newest first, with the release each entry is in | Every session |
| [`releases/`](releases) | Each release's notes, which the release workflow publishes | A release is cut |
| [`COMPATIBILITY.md`](COMPATIBILITY.md) | What works on NeoForge, per Minecraft version and per Fabric API module, from CI | A CI run shows a change |
| [`MIXINS.md`](MIXINS.md) | Every mixin Fabricated applies, with its target and injection point | A mixin config changes |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | How translation, isolation, caching and security work | The design changes |
| [`API.md`](API.md) | Fabricated's public API for NeoForge and Fabric mods | The API changes |
| [`RESEARCH.md`](RESEARCH.md) | Verified facts about NeoForge, FML and Fabric that the design relies on, and the owner's decisions (§8) | A fact is checked, or the owner decides |
| [`PORTING.md`](PORTING.md) | The Forge and 1.20.1 ports: the branch model, what of the engine carries over, the build, each port's steps | A port's design or a step changes |
