# Fabricated's documentation

Each fact lives in one document, and the others link to it. This table says which document holds what, and when it
changes. The documents say what is true now, without the history of how a decision came about; `roadmap.md` changes
with every step of the work.

| Document | What it holds | Update it when |
|---|---|---|
| [`../README.md`](../README.md) | The users' guide: installing, what works, troubleshooting, the config file | What players see changes |
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md) | The project's layout, building and testing, the code rules, adding a Minecraft version, releasing | The layout, the build or the release steps change |
| [`claude.md`](claude.md) | The working rules, the branch model and a map of the code, for whoever works on it | A rule, the branch model or a seam changes |
| [`roadmap.md`](roadmap.md) | The goals, the branch model, what comes next, the open findings, what is unverified, the performance baselines | Every step of the work |
| [`FINDINGS.md`](FINDINGS.md) | Every fixed or closed finding, and the documented known limits: its cause, why the tests missed it, the fix and how it was verified | A finding is fixed or closed (it moves here from `roadmap.md`) |
| [`CHANGELOG.md`](CHANGELOG.md) | User-facing history, newest first: what changed and how it was verified | Every change players or contributors see |
| [`releases/`](releases) | Each release's notes, which the release workflow publishes | A release is cut |
| [`COMPATIBILITY.md`](COMPATIBILITY.md) | The support matrix for every loader and Minecraft version (the source of truth), and per platform what works per Fabric API module, from CI | A CI run shows a change |
| [`MIXINS.md`](MIXINS.md) | Every mixin Fabricated applies, with its target and injection point | A mixin config changes |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | How translation, isolation, caching and security work | The design changes |
| [`API.md`](API.md) | Fabricated's public API for NeoForge and Fabric mods | The API changes |
| [`RESEARCH.md`](RESEARCH.md) | Verified facts about NeoForge, FML and Fabric that the design relies on, and the platform decisions (§8) | A fact is checked, or a platform decision changes |
| [`PORTING.md`](PORTING.md) | The Forge, 1.20.1 and 1.19.2 ports: the branch model, what of the engine carries over, the build, each port's steps and where it stands | A port's design or a step changes |
