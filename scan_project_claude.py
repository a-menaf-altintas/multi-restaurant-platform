#!/usr/bin/env python3
"""
LLM‑Optimized Project Scanner
----------------------------
* Creates a single plain‑text file of your entire codebase for LLM analysis
* Walks the project directory, skipping build/IDE/dependency dirs and test sources
* Collects:
    - Directories (one line each, prefixed with [DIR])
    - Files: prints a header line with the full relative path preceded by "=== "
      and then the verbatim content of source‑code files
    - Other files are listed by header only
* Simple, clean output optimized for LLM context windows
* Result is written to <project_name>_scan.txt or to a path supplied on the CLI
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

# ────────────────────────────────────────────────────────────────────────────
# Configuration
# ────────────────────────────────────────────────────────────────────────────
SKIP_DIRS: set[str] = {
    # VCS
    ".git", ".svn", ".hg",
    # IDE / editor
    ".idea", ".vscode", ".vs",
    # Build output
    "target", "build", "dist", "out", "bin", "obj", "release", "coverage",
    # Dependencies
    "node_modules", "vendor", "bower_components", ".m2",
    # Virtual envs / caches
    "venv", ".venv", "env", ".env", "__pycache__", ".pytest_cache", ".mypy_cache", ".tox",
    # Temp
    "logs", "temp", "tmp",
}

CONTENT_EXTS: set[str] = {
    # Backend
    ".java", ".py", ".rb", ".php", ".go", ".cs", ".cpp", ".h", ".c", ".sql",
    # Frontend
    ".ts", ".js", ".jsx", ".tsx", ".css", ".scss", ".html",
    # Config / misc
    ".properties", ".yml", ".yaml", ".json", ".xml",
    ".md", ".gradle", ".gitignore",
}

CONTENT_FILENAMES: set[str] = {
    "Dockerfile",
    "docker-compose.yml", "docker-compose.yaml",
    "build.gradle", "pom.xml", "package.json",
    "application.properties", "application.yml",
    ".gitignore",
}

FRONTEND_DIR_KEYWORDS = {"frontend", "web", "webapp", "client", "ui", "app", "static", "templates"}

TEST_SUFFIXES = {
    "Test.java", "Tests.java", "IT.java", "Spec.java",
    ".test.js", ".spec.js", ".test.jsx", ".spec.ts",
    "_test.py", "test_", "_spec.rb", "_test.go",
    "Tests.cs",
}

BINARY_EXTS = {
    ".jar", ".war", ".class", ".exe", ".dll", ".so", ".o",
    ".zip", ".gz", ".tar", ".rar", ".7z",
    ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".ico",
    ".pdf", ".doc", ".xls", ".ppt",
    ".db", ".sqlite",
    ".pyc", ".pyo",
}

DEFAULT_SUMMARY = (
    "The Multi‑Restaurant Platform is a Spring Boot 3 (Java 21) application "
    "structured as multiple Gradle sub‑modules (api, security, common, restaurant, menu, order, "
    "payment, print, admin). It offers multi‑tenant restaurant management with JWT‑secured APIs, "
    "Flyway migrations, and Docker deployment descriptors."
)

LLM_INSTRUCTIONS = (
    "Hello LLM, I need your assistance in developing and improving my application while being "
    "careful not to break the current working app …"
)

# ────────────────────────────────────────────────────────────────────────────
# Helpers
# ────────────────────────────────────────────────────────────────────────────
def is_binary_file(p: Path) -> bool:
    return p.suffix.lower() in BINARY_EXTS


def is_skipped_dir(d: str) -> bool:
    return d in SKIP_DIRS or (d.startswith(".") and d not in {".github", ".gitlab-ci"})


def is_test_file(p: Path) -> bool:
    name = p.name
    if any(name.endswith(s) for s in TEST_SUFFIXES):
        return True
    lowered = [part.lower() for part in p.parts]
    return "test" in lowered or "tests" in lowered


def is_frontend_html(p: Path) -> bool:
    return (
        p.suffix.lower() == ".html"
        and not is_test_file(p)
        and any(k in [part.lower() for part in p.parts] for k in FRONTEND_DIR_KEYWORDS)
    )


def wants_content(p: Path) -> bool:
    return (
        p.name in CONTENT_FILENAMES
        or p.suffix.lower() in CONTENT_EXTS
        or is_frontend_html(p)
    )


def read_file(p: Path, max_size_kb: int = 500) -> str:
    try:
        size_kb = p.stat().st_size / 1024
        if size_kb > max_size_kb:
            return f"<File too large: {size_kb:.1f} KB>"
        if is_binary_file(p):
            return f"<Binary file: {p.suffix}>"
        with p.open("r", encoding="utf-8", errors="replace") as f:
            return f.read().rstrip("\n")
    except Exception as exc:  # pylint: disable=broad-except
        return f"<Error reading file: {exc}>"


def get_project_summary(root: Path) -> str:
    for name in ("README.md", "readme.md", "README.txt"):
        rp = root / name
        if rp.exists():
            try:
                lines, non_empty = [], 0
                with rp.open("r", encoding="utf-8", errors="ignore") as f:
                    for line in f:
                        line = line.rstrip("\n")
                        lines.append(line)
                        if line.strip():
                            non_empty += 1
                        if non_empty >= 20:
                            break
                return "\n".join(lines)
            except Exception:  # pylint: disable=broad-except
                pass
    return DEFAULT_SUMMARY


# ────────────────────────────────────────────────────────────────────────────
# Core scanner
# ────────────────────────────────────────────────────────────────────────────
def scan(
    root: Path,
    out_path: Path,
    *,
    max_file_size_kb: int = 500,
    include_tests: bool = False,
    verbose: bool = False,
) -> None:
    project_name = root.name
    summary = get_project_summary(root)

    dir_lines: list[str] = []
    file_sections: list[str] = []

    for dirpath, dirnames, filenames in os.walk(root, topdown=True):
        dirnames[:] = [d for d in dirnames if not is_skipped_dir(d)]
        dir_rel = Path(dirpath).relative_to(root)
        dir_str = str(dir_rel if str(dir_rel) != "." else ".")
        dir_lines.append(f"[DIR] {dir_str}")

        for fname in sorted(filenames):
            # Skip our own scan output files to avoid recursion
            if fname.startswith("scan_project"):
                continue

            fpath = Path(dirpath) / fname
            rel = fpath.relative_to(root)

            if not include_tests and is_test_file(rel):
                continue

            header = f"=== {rel} ==="

            if wants_content(rel):
                content = read_file(fpath, max_file_size_kb)
                file_sections.append(f"{header}\n{content}")
                if verbose:
                    print(f"• included {rel}")
            else:
                file_sections.append(header)
                if verbose:
                    print(f"• listed   {rel}")

    if verbose:
        print(f"\nWriting scan to {out_path}\n")

    with out_path.open("w", encoding="utf-8") as out:
        out.write(f"Project Name: {project_name}\n")
        out.write("Project Summary:\n")
        out.write(summary + "\n")

        for ln in dir_lines:
            out.write(ln + "\n")
        for sec in file_sections:
            out.write(sec + "\n")

        out.write("LLM Instructions:\n")
        out.write(LLM_INSTRUCTIONS)


# ────────────────────────────────────────────────────────────────────────────
# CLI
# ────────────────────────────────────────────────────────────────────────────
def parse_args() -> argparse.Namespace:
    p = argparse.ArgumentParser(
        description="Create a plain‑text scan of a project for LLM analysis"
    )
    p.add_argument(
        "project_dir",
        nargs="?",
        default=os.getcwd(),
        help="Directory to scan (defaults to current directory)",
    )
    p.add_argument(
        "-o",
        "--output",
        help="Output file path (defaults to <project_name>_scan.txt)",
    )
    p.add_argument(
        "--max-size",
        type=int,
        default=500,
        metavar="KB",
        help="Maximum file size to include (KB, default 500)",
    )
    p.add_argument(
        "--include-tests",
        action="store_true",
        help="Include test sources in the scan",
    )
    p.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="Print progress while scanning",
    )
    return p.parse_args()


# ────────────────────────────────────────────────────────────────────────────
# Entry point
# ────────────────────────────────────────────────────────────────────────────
def main() -> None:
    args = parse_args()
    root = Path(args.project_dir).resolve()

    if not root.is_dir():
        sys.exit(f"Error: {root} is not a directory")

    out_file = Path(args.output) if args.output else Path.cwd() / f"{root.name}_scan.txt"

    try:
        scan(
            root,
            out_file,
            max_file_size_kb=args.max_size,
            include_tests=args.include_tests,
            verbose=args.verbose,
        )
        if args.verbose:
            print("✅ Scan complete")
    except KeyboardInterrupt:
        print("\nScan aborted by user")
        sys.exit(1)
    except Exception as exc:  # pylint: disable=broad-except
        print(f"\nError: {exc}")
        if args.verbose:
            import traceback

            traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()

