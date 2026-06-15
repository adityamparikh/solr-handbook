# Apache Solr: A Practical Engineering Handbook

A code-first walkthrough of Apache Solr 10 for working JVM engineers — Lucene internals, schema design, search and relevance engineering, SolrCloud operations, the Model Context Protocol integration, and an honest comparison with Elasticsearch and OpenSearch.

This repository is the [Quarto](https://quarto.org/) source for the book **and its companion code**. The book sources produce an HTML site, a print-ready PDF, and an EPUB from a single source; the [`code/`](code/) directory is a buildable Gradle project (Solr 10.0.0, SolrJ 10.0.0, Java 21) implementing the book's examples, with unit tests and Testcontainers integration tests that run against a real Solr 10 in Docker.

```bash
cd code
./gradlew build              # compile + unit tests (no Docker needed)
./gradlew integrationTest    # Testcontainers integration tests (requires Docker)
```

## Quick start

```bash
make install       # one-time: Quarto + TinyTeX into ~/.local and ~/Library (no sudo)
make doctor        # verify the toolchain
make               # render HTML + PDF + EPUB into _book/
make preview       # live-reload HTML preview on http://localhost:4848
```

Individual targets: `make html`, `make pdf`, `make epub`.

The install is **user-space only** — Quarto goes to `~/.local/quarto`, TinyTeX goes to `~/Library/TinyTeX`, and TeX Gyre fonts symlink into `~/Library/Fonts`. No `sudo`, no Homebrew cask password prompts. Total disk: ~450 MB.

## Repository layout

```
solr-handbook/
├── _quarto.yml              # book config — chapters, formats, crossref settings
├── index.qmd                # preface (book home page)
├── chapters/
│   ├── 01-why-solr.qmd      # inverted index, Lucene internals, query cost model
│   ├── 02-indexing.qmd      # schema, analyzers, SolrJ, partial updates, reindex
│   ├── 03-search.qmd        # eDisMax, faceting, vector / HNSW, RRF
│   ├── 04-solrcloud.qmd     # ZooKeeper, leader election, recovery, Kubernetes
│   ├── 05-relevance.qmd     # signals, LTR, judgment lists, the relevance loop
│   ├── 06-mcp.qmd           # MCP primitives, Spring AI MCP, GraalVM native image
│   └── 07-alternatives.qmd  # Elasticsearch / OpenSearch comparison, licensing
├── back-matter/
│   ├── closing.qmd
│   ├── references.qmd
│   └── book-index.qmd       # alphabetical topic index
├── Makefile                 # render targets
├── code/                    # companion code — Gradle project, SolrJ 10, Testcontainers
│   ├── src/main/java/solrbook/   # indexing, search, relevance examples from the book
│   └── src/test/java/solrbook/   # unit tests + Testcontainers integration tests
├── solr-10-handbook.md      # archive — pre-Quarto single-file source
└── _book/                   # build output (git-ignored)
```

## Rendered artifacts

`dist/` holds the PDF and EPUB from the last release render. They are rebuilt with `make` at release time and may lag the chapter sources between releases — the `.qmd` files in `chapters/` are the source of truth.

## Output formats

| Target | Command | Output | Notes |
|---|---|---|---|
| HTML book site | `make html` | `_book/index.html` | Default Quarto Book theme; navigable sidebar; search; light/dark theme. |
| Print-ready PDF | `make pdf` | `_book/Apache-Solr-A-Practical-Engineering-Handbook.pdf` | KOMA-Script `scrbook`, Letter, 11pt, TeX Gyre Pagella + Heros + Fira Code. |
| EPUB e-book | `make epub` | `_book/Apache-Solr-A-Practical-Engineering-Handbook.epub` | Kindle / Apple Books / Kobo. Mermaid diagrams render to SVG inside. |

The same `_quarto.yml` produces all three. Figure and table numbering is consistent across formats: `Figure 6.1`, `Table 6.3`, etc.

## Authoring conventions

- **Diagrams.** Most diagrams are [Mermaid](https://mermaid.js.org/) inside ` ```{mermaid} ` Quarto blocks; each carries a `%%| label: fig-NN-slug` and a `%%| fig-cap: "…"` so it shows up in the figure list and is cross-referenceable. A handful of Chapter 6 figures are raster images cropped from the companion conference talk (stored in `chapters/figures/06-mcp/` and referenced as `![caption](…){#fig-…}`) rather than Mermaid.
- **Tables.** Pandoc tables; major ones carry a trailing caption `: Caption text {#tbl-NN-slug}` for cross-referencing.
- **Cross-references in prose.** `@fig-06-mcp-architecture` becomes "Figure 6.1"; `@tbl-06-primitives` becomes "Table 6.1". Section references stay textual (`see §6.3`).
- **Callouts.** Block-quotes prefixed with `**Production tip — …**` or `**Solr 9 difference — …**` for hard-won operational advice and version-drift warnings.
- **Chapter file format.** Each chapter file starts with `# Title` (no "Chapter N:" prefix — Quarto numbers chapters from the order in `_quarto.yml`).

## Prerequisites

- macOS (tested) or Linux with a POSIX shell. Windows via WSL2 should work; not tested.
- [Quarto](https://quarto.org/docs/get-started/) ≥ 1.9 — installed automatically by `make install` to `~/.local/quarto`.
- A TeX distribution for the PDF target — `make install` installs [TinyTeX](https://yihui.org/tinytex/) via Quarto into `~/Library/TinyTeX`, then `tlmgr install`s the packages used by the book (`tex-gyre`, `tex-gyre-math`, `microtype`, `fvextra`, `xurl`, `koma-script`, `collection-fontsrecommended`, `collection-fontsextra`, `biber`, `biblatex`).
- Fonts: TeX Gyre Pagella (main), TeX Gyre Heros (sans), TeX Gyre Cursor (mono). All bundled with TeX Live; the PDF uses the `tgpagella` / `tgheros` / `tgcursor` LaTeX packages (no fontspec / Core Text discovery needed).

Run `make doctor` to verify each piece is present.

### Why the sudo-free install path

The "official" macOS installers (`brew install --cask quarto`, `brew install --cask basictex`) both run system `.pkg` installers that prompt for an admin password. That's fine interactively but breaks in CI, in remote shells, and in any agent-driven setup. The `make install` target avoids both by using the Quarto and TinyTeX project's own user-space tarballs. Re-rendering the book never needs sudo.

## Editing workflow

1. Edit the chapter file in `chapters/` (or `index.qmd` / a back-matter file).
2. `make preview` opens a live-reload HTML view at <http://localhost:4848>. Saves trigger an incremental re-render.
3. Before pushing, run `make` to confirm all three formats build cleanly. Quarto fails the build on broken cross-references, missing labels, or invalid Mermaid syntax.

## License

The text is © 2026 Aditya Parikh, licensed [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Code samples are MIT unless otherwise noted.
