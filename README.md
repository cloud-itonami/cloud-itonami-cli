# cloud-itonami-cli

`itonami` — the terminal front end for the operations `cloud-itonami-app`
serves. Run it with no arguments and it opens a REPL; run it with a command
and it does that one thing and exits.

```
itonami                          open the REPL
itonami status                   whether a server is answering, and which store
itonami commands [term …]        what can be named, against how many routes
itonami bots list
itonami auth login --label cli
```

## It starts no JVM, and installs nothing

`bin/itonami` is a `#!/usr/bin/env kbb` launcher: it runs on the kbb engine
(kotoba-lang/org-babashka-nbb, SCI on Node). There is no npm dependency and
no build step: the file you read is the file that runs. Its one library,
`kotoba.lang.text`, is declared in `nbb.edn` — the file the engine actually
reads — with the same sha `deps.edn` pins for the linter. If a second
coordinate ever appears in either file, this paragraph has stopped being true.
One caveat, measured 2026-09-15: the engine resolves `nbb.edn` `:deps` by
shelling out to `bb` once into `.nbb/` — a cold cache with no `bb` on PATH
exits 1 (`bb: command not found`). After that first resolution `bb` is not
consulted again.

Source is `.cljk` (Clojure-shaped Kotoba; ADR-2609111500). The engine
resolves `.cljk` and never `.kotoba`, so a `.kotoba` spelling of these files
cannot be `require`d by the launcher — that is what broke `itonami` between
2026-09-10 and 2026-09-15, and `cljk-origin.edn` records what each file was
before.

## What lives here, and what does not

This repository holds the **client**: the REPL and its editor, the splash, the
slash-command registry, panels, skills discovery, terminal width measurement.
The two deciders — what a command *is*, what a repository's profile may say —
are not Clojure here at all: they are the Kotoba components of
cloud-itonami-commands (`dist/commands.wasm`, `dist/repo_profile.wasm`),
reached through their guest adapters.

`bin/itonami` is a launcher: it answers `--print-data-dir` from its own file,
instantiates both components (asynchronous — the wasm is hashed on load), and
only then loads `bin/itonami_main.cljk`, the front end proper, whose layers
read the repository's `:cli/config` through the profile component at mount.

It does not hold the server. `itonami` resolves a command to a method and a
path, carries the session, and prints what the server said — so it works
against whichever server is bound on the configured host and port.

## The command surface is a dependency, and it is Kotoba

`cloud.itonami.commands.guest` and `cloud.itonami.repo-profile.guest` are
[cloud-itonami-commands](https://github.com/cloud-itonami/cloud-itonami-commands),
pinned by sha in `nbb.edn` (and, for the linter, `deps.edn`). The engine
extracts that dependency onto its classpath; with it come `commands.wasm`,
`repo_profile.wasm` and the amu host that runs them, and `defaults.edn`,
which this launcher still reads as a file. The adapters answer the same
names the Clojure namespaces `cloud.itonami.app.commands` / `.repo-profile`
did (same values, same throws — that repository's parity harness holds them
to it, 588 checks), so the cutover on 2026-09-15 changed one `require`.
Until that day this repository carried copies of the tables and a drift
checker; measured that morning, three of the four copies had drifted. When
the app regenerates the registry, the sha here advances — that is the whole
update.

One pinned file remains, `resources/cloud-itonami-version.edn` (the app's
version, which the splash names), and the checker still guards it:

```bash
kbb --backend sci scripts/verify-pinned-surface.cljk        # 0 match · 1 drift · 2 refused
```

Three outcomes, not two. Exit 2 means the app checkout was not found and
**nothing was compared** — which is not a pass.

## Tests

```bash
for t in editor harness skills splash client; do
  kbb --backend sci --classpath "bin:src:test:resources" test/itonami_${t}_nbb.cljk
done
```

editor 63/206 · harness 16/41 · skills 11/36 · splash 17/55 · client 9 checks.

## Where it came from

Split out of `cloud-itonami/cloud-itonami-app` on 2026-09-07. The seam was
measured before anything moved: these namespaces require nothing from the app,
and the app's only remaining reference to them is `cli.clj` — a front end with
no alias in `deps.edn`, tested but not launched. See
`90-docs/adr/2609075600-the-cli-leaves-the-app.edn`.

### Business capital client

`itonami capital help` exposes public `org/repo` balances, Web3 challenge/login,
unsigned transaction preparation, browser review and receipt confirmation through
`https://app.itonami.cloud/api/capital`. Use `prepare --data action.json` with the
API's typed action schema; `open --project org/repo --intent ID` opens the same
review as the web app. Wallet signatures remain external; the CLI never stores a
wallet private key or executes a financial transaction. Its session cookie is
stored in `~/.cloud-itonami/capital-session.json` with owner-only permissions.
The contracts, transaction validation and ledger belong to
[cloud-itonami-api](https://github.com/cloud-itonami/cloud-itonami-api).
