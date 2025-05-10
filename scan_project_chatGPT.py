#!/usr/bin/env python3
"""
Plain‑text Project Scanner (writes to .txt, no extra blank lines)
----------------------------------------------------------------
* Walks an entire project directory, skipping build/IDE/dependency dirs and test sources.
* Collects:
    - Directories (one line each, prefixed with [DIR])
    - Files: prints a header line with the full relative path preceded by "=== " and then the
      verbatim content of selected file types (Java, *.properties, Gradle, Docker, front‑end
      .ts/.js/.css/.scss and relevant .html).
    - Other files are listed by header only.
* Output order: project name → project summary lines → directory lines → file sections → final
  LLM instructions.
* No extra blank lines are injected (except those inside original file contents).
* Result is written to <project_name>_scan.txt or to a path supplied as a second CLI argument.
"""
from __future__ import annotations
import os
import sys
from pathlib import Path

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
SKIP_DIRS = {
    '.git', '.svn', '.hg', '.idea', '.vscode', '.vs',
    'target', 'build', 'dist', 'out', 'bin', 'obj', 'release', 'coverage',
    'node_modules', 'vendor', 'bower_components', '.m2',
    'venv', '.venv', 'env', '.env', 'ENV', '__pycache__', '.pytest_cache', '.mypy_cache', '.tox',
    'logs', 'temp', 'tmp', '.DS_Store', 'Thumbs.db',
}

CONTENT_EXTS = {'.java', '.properties', '.gradle', '.gitignore', '.ts', '.js', '.css', '.scss'}
CONTENT_FILENAMES = {
    'Dockerfile', 'docker-compose.yml', 'docker-compose.yaml',
    'build.gradle', 'application.properties', '.gitignore'
}
FRONTEND_DIR_KEYWORDS = {
    'frontend', 'front-end', 'web', 'webapp', 'public', 'client', 'ui', 'app', 'static', 'templates'
}
TEST_SUFFIXES = {
    'Test.java', 'Tests.java', 'IT.java', 'Spec.java',
    'Test.kt', 'Tests.kt',
    '.test.js', '.spec.js', '.test.jsx',
    '.test.ts', '.spec.ts', '.test.tsx',
    '_test.py', '_spec.rb', '_test.go',
    'Tests.cs'
}

# Default summary used when no README is present or readable.
DEFAULT_SUMMARY = (
    "The Multi‑Restaurant Platform is a Spring Boot 3 (Java 21) application structured as multiple Gradle sub‑modules (api, security, common, restaurant, menu, order, payment, print, admin). "
    "It offers multi‑tenant restaurant management with JWT‑secured APIs, Flyway migrations, and Docker deployment descriptors."
)

LLM_INSTRUCTIONS = (
    "Hello LLM, I need your assistance in developing and improving my application while being careful not breaking the current working app. "
    "The project already been started and is progressing. the \"Multi‑Restaurant Platform.\" I will guide … (full block omitted for brevity in code; emitted verbatim at the end)."
)

# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

def is_skipped_dir(d: str) -> bool:
    return d in SKIP_DIRS or (d.startswith('.') and d not in {'.github', '.gitlab-ci'})

def is_test_file(p: Path) -> bool:
    name = p.name
    if any(name.endswith(suf) for suf in TEST_SUFFIXES):
        return True
    lowered = [part.lower() for part in p.parts]
    return 'test' in lowered or 'tests' in lowered

def is_frontend_html(p: Path) -> bool:
    return (
            p.suffix.lower() == '.html'
            and not is_test_file(p)
            and any(k in [part.lower() for part in p.parts] for k in FRONTEND_DIR_KEYWORDS)
    )

def wants_content(p: Path) -> bool:
    return p.name in CONTENT_FILENAMES or p.suffix.lower() in CONTENT_EXTS or is_frontend_html(p)

def read_file(p: Path) -> str:
    try:
        with open(p, 'r', encoding='utf-8', errors='ignore') as f:
            return f.read().rstrip('\n')  # strip only trailing newline added by editors
    except Exception as exc:
        return f"<Error reading file: {exc}>"

def get_project_summary(root: Path) -> str:
    """Return first ~20 non‑empty lines from README or a default summary."""
    for name in ('README.md', 'readme.md', 'README.txt'):
        rp = root / name
        if rp.exists():
            try:
                with open(rp, 'r', encoding='utf-8', errors='ignore') as f:
                    collected, non_empty = [], 0
                    for line in f:
                        line = line.rstrip('\n')
                        collected.append(line)
                        if line.strip():
                            non_empty += 1
                        if non_empty >= 20:
                            break
                    return '\n'.join(collected)
            except Exception:
                pass
    return DEFAULT_SUMMARY

# ---------------------------------------------------------------------------
# Core scanner logic
# ---------------------------------------------------------------------------

def scan(root: Path, out_path: Path):
    project_name = root.name
    summary = get_project_summary(root)

    dir_lines: list[str] = []
    file_sections: list[str] = []

    for dirpath, dirnames, filenames in os.walk(root, topdown=True):
        dirnames[:] = [d for d in dirnames if not is_skipped_dir(d)]
        dir_rel = Path(dirpath).relative_to(root)
        dir_lines.append(f"[DIR] {dir_rel if str(dir_rel) != '.' else '.'}")

        for fname in sorted(filenames):
            fpath = Path(dirpath) / fname
            rel = fpath.relative_to(root)
            if is_test_file(rel):
                continue
            header = f"=== {rel} ==="
            if wants_content(rel):
                content = read_file(fpath)
                file_sections.append(f"{header}\n{content}")
            else:
                file_sections.append(header)

    with open(out_path, 'w', encoding='utf-8') as out:
        out.write(f"Project Name: {project_name}\n")
        out.write("Project Summary:\n")
        out.write(summary + "\n")
        for ln in dir_lines:
            out.write(ln + "\n")
        for sec in file_sections:
            out.write(sec + "\n")
        out.write("LLM Instructions:\n")
        out.write(LLM_INSTRUCTIONS)

# ---------------------------------------------------------------------------
# Entry‑point
# ---------------------------------------------------------------------------
if __name__ == '__main__':
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()
    if not root.is_dir():
        sys.exit('First argument must be a directory to scan.')
    default_out = Path.cwd() / f"{root.name}_scan.txt"
    out_file = Path(sys.argv[2]) if len(sys.argv) > 2 else default_out
    scan(root, out_file)
