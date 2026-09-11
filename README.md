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

`bin/itonami` runs under [nbb](https://github.com/babashka/nbb). There is no
npm dependency, no maven dependency, and no build step: the file you read is
the file that runs. `deps.edn` exists for the linter and to make the empty
production dependency set visible — if something appears in `:deps`, the claim
in this paragraph has stopped being true.

## What lives here, and what does not

This repository holds the **client**: the REPL and its editor, the splash, the
slash-command registry, panels, skills discovery, terminal width measurement,
and the two namespaces that decide what a command *is*
(`cloud.itonami.app.commands`, `cloud.itonami.app.repo-profile`).

It does not hold the server. `itonami` resolves a command to a method and a
path, carries the session, and prints what the server said — so it works
against whichever server is bound on the configured host and port.

## The three pinned files, and why they need a gate

`resources/` carries three files this repository does not author:

    cloud-itonami-app.commands.edn      derived from the app's own route table
    cloud-itonami-app.cli-aliases.edn   the app's alias table
    cloud-itonami-app.defaults.edn      the app's configuration defaults

They are the app's command surface, pinned here so the CLI runs without an app
checkout beside it. A pinned copy of a *generated* file is the thing that
drifts, so:

```bash
kbb --backend sci scripts/verify-pinned-surface.cljk        # 0 match · 1 drift · 2 refused
```

Three outcomes, not two. Exit 2 means the app checkout was not found and
**nothing was compared** — which is not a pass. A checker that cannot find its
input and stays quiet reports a pass for every future drift.

## Tests

```bash
for t in editor harness skills splash client; do
  kbb --backend sci --classpath "bin:src:test:resources" test/itonami_${t}_nbb.cljs
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
