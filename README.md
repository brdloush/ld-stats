# ld-stats

Standalone CLI-friendly [babashka](https://github.com/borkdude/babashka) script that downloads and filters a list of feature flags off a provided [LaunchDarkly](https://launchdarkly.com/) account and environment.

The output of this script provides some basic statistics about each flag, such as number of usages in code or last modification time.

Result of the script can have either `json` or `csv` form (see `--format` CLI option) and it can either be printed to STDOUT (default) or saved into file (`--output-file` CLI option).

## Usage

The script currently supports following flags:
```
./target/ld-stats --help
  -k, --ld-api-key APIKEY                    LaunchDarkly API key. May also be provided via environment property LD_API_KEY.
  -e, --environment ENVIRONMENT              LaunchDarkly environment, such as "production"
  -m, --modified-before-months MONTHS  1     Only include FFs which has not been modified in previous MONTHS months.
  -w, --without-usages-only                  Only include FFs with no code usages.
  -t, --with-tag TAG                         Only with provived TAG will be returned.
  -f, --format FORMAT                  :csv  Output format. Either "csv" or "json".
  -o, --output-file FILENAME                 Output to file with provided FILENAME. If not specified, output is sent to STDOUT.
  -h, --help                                 Shows this usage information.
  -d, --debug                                Debug logging. Print more detailed errors, including API responses.
```

Basic use:
```bash
target/ld-stats -e production --without-usages-only --modified-before-months 6 -o /tmp/ffs-without-usages-not-modified-in-last-half-year.csv
```

## Building uberscript locally

- clone this repository
- make sure that babashka is installed on your machine
- execute `./build-uberscript.sh`
- you can now execute the uberscript via `./target/ld-stats`

## Docker usage

If you don't want to install babashka locally, have some issues with it or are in a hurry, you can build & run `ld-stats` using docker. Of course, there's even a prebuilt [docker image on Docker Hub](https://hub.docker.com/repository/docker/brdloush/ld-stats), so you can also use `brdloush/ld-stats:latest` instead of building the image yourself. 

1) Build the image
```bash
docker build -t ld-stats .
```

2) Prepare some local shell helper variables/aliases. You can put them in your `.bashrc`, `.zshrc` or similar:
```bash
export LD_API_KEY=your-secret-api-key
alias ld-stats='docker run -e LD_API_KEY=$LD_API_KEY --rm ld-stats'
```

3) Use the `ld-stats` alias. When passing optionsm be sure NOT to use output to file (`--output-file` option) as the file would be store inside docker container, not on your local machine. (of course, you can use docker volumes together with `--output-file`, no problem with that)

```bash
ld-stats --environment production --without-usages-only --modified-before-months 6 > /tmp/ffs-without-usages-not-modified-in-last-half-year.csv
```

# Development

All the code is currently present in single `ld-stats.core` namespace.

In spite of the fact that this is a shell script, developing it using clojure and REPL-driven development is a joy.

If you have `babashka` installed on your machine, you can simply start the nrepl server `bb --nrepl-server` and connect to `localhost:1667` nrepl port from any clojure editor of your choice. ([Calva](https://calva.io/), [Cursive](https://cursive-ide.com/) to name a few)

Alternatively, you can use `leiningen` nrepl by issueing `lein repl` and then connecting to whatever nrepl port was assigned to it. Calva's `CIDER Jack-In` functionality (using `Leiningen` project type) works as well.

## License

Copyright © 2020 Tomas Brejla

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
