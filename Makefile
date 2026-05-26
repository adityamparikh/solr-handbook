# Apache Solr: A Practical Engineering Handbook — build targets
#
# Quick reference:
#   make            same as `make all`  — render HTML + PDF + EPUB to _book/
#   make html       Quarto HTML book site (default Quarto target)
#   make pdf        Print-ready PDF via xelatex
#   make epub       EPUB e-book
#   make preview    Live-reload HTML preview on http://localhost:4848
#   make clean      Delete _book/ output
#   make install    Install Quarto on macOS via Homebrew (one-time)
#   make doctor     Verify quarto + xelatex toolchain
#
# Prereqs: Quarto >= 1.5, a TeX distribution with xelatex (BasicTeX or full MacTeX),
# and the `fira-code`, `tex-gyre-pagella`, and `tex-gyre-heros` fonts available
# to xelatex.
#
# IMPORTANT: per-format targets are destructive to other formats.
# `make html`, `make pdf`, and `make epub` each clean _book/ before rendering
# and emit only their own format — running them in sequence does NOT yield all
# three artifacts coexisting. Use `make all` (a single `quarto render`
# invocation) when you want HTML + PDF + EPUB together in _book/.

# Prefer user-space Quarto / TinyTeX installs if present (no sudo needed).
# These paths are populated by `make install`.
QUARTO ?= $(HOME)/.local/quarto/bin/quarto
TLBIN  := $(HOME)/Library/TinyTeX/bin/universal-darwin
export PATH := $(HOME)/.local/quarto/bin:$(TLBIN):$(PATH)

QUARTO_VERSION ?= 1.9.37

.PHONY: all html pdf epub preview clean install install-quarto install-tex install-fonts doctor

# Render every format declared in _quarto.yml — html + pdf + epub.
all:
	$(QUARTO) render

html:
	$(QUARTO) render --to html

pdf:
	$(QUARTO) render --to pdf

epub:
	$(QUARTO) render --to epub

preview:
	$(QUARTO) preview --to html

clean:
	rm -rf _book/ .quarto/

# ---------------------------------------------------------------
# Sudo-free install: Quarto tarball to ~/.local/quarto + TinyTeX
# managed by Quarto (~/Library/TinyTeX). No Homebrew, no sudo.
# ---------------------------------------------------------------
install: install-quarto install-tex install-fonts
	@echo
	@echo "Install complete. Run 'make doctor' to verify, then 'make' to build."

install-quarto:
	@mkdir -p $(HOME)/.local/quarto
	@if [ ! -x "$(QUARTO)" ]; then \
	  echo "Downloading Quarto $(QUARTO_VERSION) (~220MB)..."; \
	  curl -fL --progress-bar \
	    https://github.com/quarto-dev/quarto-cli/releases/download/v$(QUARTO_VERSION)/quarto-$(QUARTO_VERSION)-macos.tar.gz \
	    -o /tmp/quarto.tar.gz && \
	  tar -xzf /tmp/quarto.tar.gz -C $(HOME)/.local/quarto --strip-components=1 && \
	  rm /tmp/quarto.tar.gz; \
	else \
	  echo "Quarto already installed at $(QUARTO)"; \
	fi
	@$(QUARTO) --version

install-tex:
	@if [ ! -d "$(TLBIN)" ]; then \
	  echo "Installing TinyTeX (~250MB)..."; \
	  $(QUARTO) install tinytex --no-prompt; \
	else \
	  echo "TinyTeX already installed at $(TLBIN)"; \
	fi
	@$(TLBIN)/tlmgr install \
	  tex-gyre tex-gyre-math microtype fvextra xurl koma-script \
	  collection-fontsrecommended collection-fontsextra biber biblatex 2>&1 | tail -3

# Expose TeX Gyre OTFs to macOS so they're visible to any system app (Pages,
# Preview, etc.) — not strictly needed for the build since the PDF uses the
# tgpagella/tgheros/tgcursor LaTeX packages, but keeps fonts consistent.
install-fonts:
	@mkdir -p $(HOME)/Library/Fonts
	@ln -sf $(HOME)/Library/TinyTeX/texmf-dist/fonts/opentype/public/tex-gyre/*.otf $(HOME)/Library/Fonts/ 2>/dev/null || true
	@echo "Symlinked TeX Gyre OTFs into ~/Library/Fonts."

doctor:
	@echo "Quarto:"
	@$(QUARTO) --version 2>/dev/null || echo "  NOT FOUND — run 'make install'"
	@echo
	@echo "xelatex:"
	@$(TLBIN)/xelatex --version 2>/dev/null | head -1 || echo "  NOT FOUND — run 'make install-tex'"
	@echo
	@echo "tlmgr:"
	@$(TLBIN)/tlmgr --version 2>/dev/null | head -1 || echo "  NOT FOUND"
	@echo
	@echo "Mermaid CLI (optional):"
	@command -v mmdc >/dev/null && mmdc --version || echo "  NOT FOUND (optional)"
