#!/usr/bin/env python3
"""
Project Scanner for Code Files with Token Calculation
----------------------------------------------------------
* Walks an entire project directory, skipping build/IDE/dependency dirs and test sources.
* Collects:
    - Directories (one line each, prefixed with [DIR])
    - Files: processes Java, TypeScript, JavaScript, HTML, CSS, and SCSS files
    - Skips Python files and other non-requested file types
* Includes project overview and appropriate LLM instructions in the output
* Calculates token counts but keeps them out of the output files (reports to terminal)
* Output formats:
    - JSON for structured data
    - Plain text as a backward-compatible option
"""
from __future__ import annotations
import os
import sys
import json
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple
from collections import defaultdict

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

# Define file types to include (excluding Python as requested)
INCLUDE_EXTS = {
    '.java', '.ts', '.js', '.html', '.css', '.scss', '.sql'
    '.properties', '.gradle', '.gitignore'
}

INCLUDE_FILENAMES = {
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
    'Tests.cs'
}

# Default summary used when no README is present or readable.
DEFAULT_SUMMARY = (
    "The Multi‑Restaurant Platform is a Spring Boot 3 (Java 21) application structured as multiple Gradle sub‑modules (api, security, common, restaurant, menu, order, payment, print, admin). "
    "It offers multi‑tenant restaurant management with JWT‑secured APIs, Flyway migrations, and Docker deployment descriptors."
)

LLM_INSTRUCTIONS = (
    "You are an expert full stack web/mobile developer. You have an expert knowledge of Java, gradle, maven, spring boot, docker, docker-compose, angular, react, javascript, typescript, html, css. I need your assistance in developing and improving my application while being careful not breaking the current working app. "
    "The project has already been started and is progressing. It's the \"Multi‑Restaurant Platform\". I will guide you through the next steps. Analyze the code structure, modules, paths, and files that are critical for the project. "
    "Always give full path of the files when referencing them. Always ask before proceeding. We'll work in small steps, where each step should include a feature change, an automation testing, and a git commit message."
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
    # Python files are excluded as requested
    if p.suffix.lower() == '.py':
        return False
    return p.name in INCLUDE_FILENAMES or p.suffix.lower() in INCLUDE_EXTS or is_frontend_html(p)

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

def estimate_tokens(text: str) -> int:
    """Estimate token count based on a simple heuristic.
    GPT tokenizers typically average about 4 characters per token for English text and code.
    """
    if not text:
        return 0
    # Simple estimate: ~4 characters per token on average
    return len(text) // 4

def get_module_name(file_path: str) -> str:
    """Extract module name from file path.
    Assumes a structure like 'backend/module_name/...'
    """
    parts = file_path.split('/')
    if len(parts) >= 2 and parts[0] == 'backend':
        return parts[1]
    return 'root'  # Default module name for files not in a specific module

# ---------------------------------------------------------------------------
# Core scanner logic
# ---------------------------------------------------------------------------

def scan_project(root: Path, out_json_path: Path = None, out_txt_path: Path = None):
    """
    Scan project and generate outputs. Returns statistics to avoid duplicate calculations.
    """
    project_name = root.name
    summary = get_project_summary(root)
    summary_tokens = estimate_tokens(summary)
    llm_instructions_tokens = estimate_tokens(LLM_INSTRUCTIONS)

    total_files = 0
    total_content_size = 0  # in characters
    total_tokens = summary_tokens + llm_instructions_tokens
    file_token_counts = {}
    module_token_counts = defaultdict(int)

    # Collect all data in one pass
    all_directories = []
    all_files = []

    for dirpath, dirnames, filenames in os.walk(root, topdown=True):
        dirnames[:] = [d for d in dirnames if not is_skipped_dir(d)]
        dir_rel = Path(dirpath).relative_to(root)
        dir_str = str(dir_rel if str(dir_rel) != '.' else '.')
        all_directories.append(dir_str)

        for fname in sorted(filenames):
            fpath = Path(dirpath) / fname
            rel = fpath.relative_to(root)
            if is_test_file(rel):
                continue

            total_files += 1
            path_str = str(rel)

            if wants_content(rel):
                content = read_file(fpath)
                content_tokens = estimate_tokens(content)
                content_size = len(content)
                total_content_size += content_size
                total_tokens += content_tokens
                file_token_counts[path_str] = content_tokens

                # Track module token counts
                module_name = get_module_name(path_str)
                module_token_counts[module_name] += content_tokens

                file_entry = {
                    "path": path_str,
                    "type": fpath.suffix.lstrip('.') if fpath.suffix else "unknown",
                    "content": content
                }
                all_files.append((path_str, file_entry, content))
            else:
                # Just record the path without content for files we don't want to process
                file_entry = {
                    "path": path_str,
                    "type": fpath.suffix.lstrip('.') if fpath.suffix else "unknown"
                }
                all_files.append((path_str, file_entry, None))

    # Generate JSON output if requested
    if out_json_path:
        result = {
            "project_name": project_name,
            "summary": summary,
            "directories": all_directories,
            "files": [file_data for _, file_data, _ in all_files],
            "llm_instructions": LLM_INSTRUCTIONS
        }

        with open(out_json_path, 'w', encoding='utf-8') as out:
            json.dump(result, out, indent=2)

    # Generate text output if requested
    if out_txt_path:
        with open(out_txt_path, 'w', encoding='utf-8') as out:
            out.write(f"Project Name: {project_name}\n")
            out.write("Project Summary:\n")
            out.write(summary + "\n")

            for dir_path in all_directories:
                out.write(f"[DIR] {dir_path}\n")

            for path_str, _, content in all_files:
                header = f"=== {path_str} ==="
                if content:
                    out.write(f"{header}\n{content}\n")
                else:
                    out.write(f"{header}\n")

            out.write("LLM Instructions:\n")
            out.write(LLM_INSTRUCTIONS)

    # Return statistics
    return {
        "total_files": total_files,
        "total_content_size": total_content_size,
        "total_tokens": total_tokens,
        "summary_tokens": summary_tokens,
        "code_tokens": total_tokens - summary_tokens - llm_instructions_tokens,
        "llm_instructions_tokens": llm_instructions_tokens,
        "module_token_counts": module_token_counts
    }

def print_statistics(stats):
    """Print token statistics to terminal"""
    print("\n===== TOKEN STATISTICS =====")
    print(f"Total token count: {stats['total_tokens']}")
    print(f"- Summary tokens: {stats['summary_tokens']}")
    print(f"- Code tokens: {stats['code_tokens']}")
    print(f"- LLM instructions tokens: {stats['llm_instructions_tokens']}")
    print(f"\nTotal character count: {stats['total_content_size']}")
    print(f"Total files: {stats['total_files']}")

    print("\nTop 3 modules by token count:")

    # Get top 3 modules by token count
    module_counts = stats['module_token_counts']
    top_modules = sorted(module_counts.items(), key=lambda x: x[1], reverse=True)[:3]
    for i, (module, tokens) in enumerate(top_modules, 1):
        print(f"{i}. Module '{module}': {tokens} tokens ({tokens/stats['total_tokens']*100:.1f}%)")

    # Print total number of modules
    print(f"\nTotal modules: {len(module_counts)}")

# ---------------------------------------------------------------------------
# Entry‑point
# ---------------------------------------------------------------------------
if __name__ == '__main__':
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()
    if not root.is_dir():
        sys.exit('First argument must be a directory to scan.')

    if len(sys.argv) > 2:
        out_file = Path(sys.argv[2])
        use_json = out_file.suffix.lower() == '.json'
    else:
        # Default to JSON format
        out_file = Path.cwd() / f"{root.name}_scan.json"
        use_json = True

    # Determine output paths
    if use_json:
        json_path = out_file
        txt_path = out_file.with_suffix('.txt')
    else:
        json_path = None
        txt_path = out_file

    # Run scan once and get statistics
    stats = scan_project(root, json_path, txt_path)

    # Print statistics once
    print_statistics(stats)

    # Display completion message
    if use_json:
        print(f"\nProject scan completed. Output written to {json_path} and {txt_path}")
    else:
        print(f"\nProject scan completed. Output written to {txt_path}")