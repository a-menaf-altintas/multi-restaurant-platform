#python3 scan_project_claudeToJson.py -includeTests                # Include test files in scan#!/usr/bin/env python3
"""
Advanced Project Scanner with Filtering Options
-----------------------------------------------
* Walks a project directory with customizable filtering options:
  - Filter by file extension with '-includeOnly'
  - Filter by module with '-includeOnlyModule'
  - Include only file names in path with '-includeOnlyFileNamesInPath'
  - Specify scanning frontend or backend with '-scanArea'
  - Include test files with '-includeTests'
* Outputs structured JSON or plain text with:
  - Project overview
  - LLM instructions
  - Token and character count statistics
* Designed for preparing code for LLM analysis

Usage examples:
  python3 scan_project_claudeToJson.py                           # Scan entire project
  python3 scan_project_claudeToJson.py -includeOnly .java,.ts    # Only Java and TypeScript files
  python3 scan_project_claudeToJson.py -includeOnlyModule order  # Only files in order module
  python3 scan_project_claudeToJson.py -includeOnlyFileNamesInPath      # Only file names in path, no content
  python3 scan_project_claudeToJson.py -scanArea frontend        # Only scan frontend folder
  python3 scan_project_claudeToJson.py -scanArea backend         # Only scan backend folder
"""
from __future__ import annotations
import os
import sys
import json
import argparse
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

# Default file types to include
DEFAULT_INCLUDE_EXTS = {
    '.java', '.ts', '.js', '.html', '.css', '.scss',
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

def wants_content(p: Path, include_exts: Set[str], include_only_module: Optional[str], scan_area: Optional[str]) -> bool:
    """
    Determine if the file should be included based on extension and module filters.

    Args:
        p: Path object for the file
        include_exts: Set of file extensions to include
        include_only_module: If specified, only include files in this module
        scan_area: If specified, only include files in 'frontend' or 'backend'

    Returns:
        Boolean indicating if the file should be included
    """
    # Skip python files by default
    if p.suffix.lower() == '.py':
        return False

    # Check scan area filter (frontend/backend)
    path_str = str(p)
    if scan_area:
        if scan_area.lower() == 'frontend' and not path_str.startswith('frontend/'):
            return False
        elif scan_area.lower() == 'backend' and not path_str.startswith('backend/'):
            return False

    # Check module filter
    if include_only_module:
        if scan_area and scan_area.lower() == 'frontend':
            module_path = f"frontend/{include_only_module}/"
        else:
            module_path = f"backend/{include_only_module}/"

        if module_path not in path_str:
            return False

    # Check extension filter
    return (p.name in INCLUDE_FILENAMES or
            p.suffix.lower() in include_exts or
            is_frontend_html(p))

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

def get_module_name(file_path: str, scan_area: Optional[str] = None) -> str:
    """Extract module name from file path.
    Assumes a structure like 'backend/module_name/...' or 'frontend/module_name/...'

    Args:
        file_path: Path string
        scan_area: If specified, affects which folder is considered for module names

    Returns:
        Module name as string
    """
    parts = file_path.split('/')
    if len(parts) >= 2:
        if parts[0] == 'backend' or parts[0] == 'frontend':
            return parts[1]
    return 'root'  # Default module name for files not in a specific module

# ---------------------------------------------------------------------------
# Core scanner logic
# ---------------------------------------------------------------------------

def scan_project(root: Path, out_json_path: Path = None, out_txt_path: Path = None,
                 include_only: Optional[str] = None, include_only_module: Optional[str] = None,
                 include_only_file_names_in_path: bool = False, scan_area: Optional[str] = None,
                 include_tests: bool = False):
    """
    Scan project and generate outputs with filtering options.

    Args:
        root: Path object to the project root
        out_json_path: Path for JSON output
        out_txt_path: Path for text output
        include_only: Comma-separated list of file extensions to include
        include_only_module: Only include files in this module
        include_only_file_names_in_path: If True, only include file names in path (no content)
        scan_area: If specified, only include files in 'frontend' or 'backend'
        include_tests: If True, include test files in the scan

    Returns:
        Dictionary of statistics about the scan
    """
    project_name = root.name
    summary = get_project_summary(root)
    summary_tokens = estimate_tokens(summary)
    llm_instructions_tokens = estimate_tokens(LLM_INSTRUCTIONS)

    # Process include_only argument
    include_exts = DEFAULT_INCLUDE_EXTS
    if include_only:
        include_exts = {ext.strip() for ext in include_only.split(',')}
        # Ensure extensions start with a dot
        include_exts = {ext if ext.startswith('.') else f'.{ext}' for ext in include_exts}

    # Validate scan_area if specified
    if scan_area and scan_area.lower() not in ['frontend', 'backend']:
        print(f"Warning: Invalid scan area '{scan_area}'. Must be 'frontend' or 'backend'. Ignoring this filter.")
        scan_area = None

    # Create scan configuration info
    scan_config = {
        "include_exts": list(include_exts),
        "include_only_module": include_only_module,
        "include_only_file_names_in_path": include_only_file_names_in_path,
        "scan_area": scan_area,
        "include_tests": include_tests
    }

    total_files = 0
    included_files = 0
    total_content_size = 0  # in characters
    total_tokens = summary_tokens + llm_instructions_tokens
    file_token_counts = {}
    module_token_counts = defaultdict(int)

    # Collect all data in one pass
    all_directories = []
    all_files = []

    # Organize files by directory for file names in path mode
    files_by_directory = defaultdict(list)

    for dirpath, dirnames, filenames in os.walk(root, topdown=True):
        dirnames[:] = [d for d in dirnames if not is_skipped_dir(d)]
        dir_rel = Path(dirpath).relative_to(root)
        dir_str = str(dir_rel if str(dir_rel) != '.' else '.')

        # Skip directories that don't match the scan area filter
        if scan_area:
            if scan_area.lower() == 'frontend' and not (dir_str == '.' or dir_str.startswith('frontend')):
                continue
            elif scan_area.lower() == 'backend' and not (dir_str == '.' or dir_str.startswith('backend')):
                continue

        # Skip directories that don't match the module filter
        if include_only_module:
            module_prefix = f"{scan_area or 'backend'}/{include_only_module}"
            if not (dir_str == '.' or dir_str.startswith(module_prefix)):
                continue

        all_directories.append(dir_str)

        # Keep track of files in this directory for file names in path mode
        dir_files = []

        for fname in sorted(filenames):
            fpath = Path(dirpath) / fname
            rel = fpath.relative_to(root)

            # Skip test files unless include_tests is True
            if not include_tests and is_test_file(rel):
                continue

            total_files += 1
            path_str = str(rel)

            if wants_content(rel, include_exts, include_only_module, scan_area):
                included_files += 1

                # Add to directory's file list for file names in path mode
                if include_only_file_names_in_path:
                    dir_files.append(fname)

                if include_only_file_names_in_path:
                    # Only include file path, not content
                    file_entry = {
                        "path": path_str,
                        "type": fpath.suffix.lstrip('.') if fpath.suffix else "unknown"
                    }
                    all_files.append((path_str, file_entry, None))
                else:
                    # Include file path and content
                    content = read_file(fpath)
                    content_tokens = estimate_tokens(content)
                    content_size = len(content)
                    total_content_size += content_size
                    total_tokens += content_tokens
                    file_token_counts[path_str] = content_tokens

                    # Track module token counts - determine module based on path
                    module_name = get_module_name(path_str, scan_area)
                    module_token_counts[module_name] += content_tokens

                    file_entry = {
                        "path": path_str,
                        "type": fpath.suffix.lstrip('.') if fpath.suffix else "unknown",
                        "content": content
                    }
                    all_files.append((path_str, file_entry, content))

        # Store files for this directory
        if include_only_file_names_in_path:
            if dir_files:
                files_by_directory[dir_str] = dir_files
            else:
                files_by_directory[dir_str] = ["nofileexist"]

    # Generate JSON output if requested
    if out_json_path:
        result = {
            "project_name": project_name,
            "summary": summary,
            "scan_config": scan_config,
            "directories": all_directories,
            "files": [file_data for _, file_data, _ in all_files],
            "llm_instructions": LLM_INSTRUCTIONS
        }

        # Add files_by_directory if in file names in path mode
        if include_only_file_names_in_path:
            result["files_by_directory"] = dict(files_by_directory)

        with open(out_json_path, 'w', encoding='utf-8') as out:
            json.dump(result, out, indent=2)

    # Generate text output if requested
    if out_txt_path:
        with open(out_txt_path, 'w', encoding='utf-8') as out:
            out.write(f"Project Name: {project_name}\n")
            out.write("Project Summary:\n")
            out.write(summary + "\n")

            out.write("\nScan Configuration:\n")
            out.write(f"- Include extensions: {', '.join(scan_config['include_exts'])}\n")
            if include_only_module:
                out.write(f"- Include only module: {include_only_module}\n")
            if include_only_file_names_in_path:
                out.write("- Including only file names in path\n")
            if scan_area:
                out.write(f"- Scan area: {scan_area}\n")
            if include_tests:
                out.write("- Including test files\n")

            for dir_path in all_directories:
                out.write(f"[DIR] {dir_path}\n")

                # Add file names for this directory if in file names in path mode
                if include_only_file_names_in_path and dir_path in files_by_directory:
                    files = files_by_directory[dir_path]
                    file_list = " ".join([f"/{f}" for f in files])
                    out.write(f"Files: {file_list}\n")

            # Only write file sections if not in file names in path mode
            if not include_only_file_names_in_path:
                for path_str, _, content in all_files:
                    header = f"=== {path_str} ==="
                    if content:
                        out.write(f"{header}\n{content}\n")
                    else:
                        out.write(f"{header}\n")

            out.write("\nLLM Instructions:\n")
            out.write(LLM_INSTRUCTIONS)

    # Return statistics
    return {
        "total_files": total_files,
        "included_files": included_files,
        "total_content_size": total_content_size,
        "total_tokens": total_tokens,
        "summary_tokens": summary_tokens,
        "code_tokens": total_tokens - summary_tokens - llm_instructions_tokens,
        "llm_instructions_tokens": llm_instructions_tokens,
        "module_token_counts": module_token_counts,
        "scan_config": scan_config
    }

def print_statistics(stats):
    """Print token statistics to terminal"""
    print("\n===== TOKEN STATISTICS =====")

    # Print scan configuration
    print("Scan Configuration:")
    print(f"- Include extensions: {', '.join(stats['scan_config']['include_exts'])}")
    if stats['scan_config']['include_only_module']:
        print(f"- Include only module: {stats['scan_config']['include_only_module']}")
    if stats['scan_config']['include_only_file_names_in_path']:
        print("- Including only file names in path (no content)")
    if stats['scan_config']['scan_area']:
        print(f"- Scan area: {stats['scan_config']['scan_area']}")
    if stats['scan_config']['include_tests']:
        print("- Including test files")

    # Print file statistics
    print(f"\nTotal files scanned: {stats['total_files']}")
    print(f"Files included in output: {stats['included_files']}")

    # Print token statistics
    if not stats['scan_config']['include_only_file_names_in_path']:
        print(f"\nTotal token count: {stats['total_tokens']}")
        print(f"- Summary tokens: {stats['summary_tokens']}")
        print(f"- Code tokens: {stats['code_tokens']}")
        print(f"- LLM instructions tokens: {stats['llm_instructions_tokens']}")
        print(f"\nTotal character count: {stats['total_content_size']}")

        # Print module statistics
        if stats['module_token_counts']:
            print("\nTop 3 modules by token count:")
            module_counts = stats['module_token_counts']
            top_modules = sorted(module_counts.items(), key=lambda x: x[1], reverse=True)[:3]
            for i, (module, tokens) in enumerate(top_modules, 1):
                print(f"{i}. Module '{module}': {tokens} tokens ({tokens/stats['total_tokens']*100:.1f}%)")
            print(f"\nTotal modules: {len(module_counts)}")
    else:
        print("\nToken statistics not available when only including file names in path.")

# ---------------------------------------------------------------------------
# Entry‑point
# ---------------------------------------------------------------------------
if __name__ == '__main__':
    # Set up argument parser
    parser = argparse.ArgumentParser(description='Advanced project scanner with filtering options')
    parser.add_argument('root_dir', nargs='?', default=os.getcwd(),
                        help='Root directory to scan (default: current directory)')
    parser.add_argument('output_file', nargs='?',
                        help='Output file path (default: <root_dir>_scan.json)')
    parser.add_argument('-includeOnly',
                        help='Comma-separated list of file extensions to include (e.g., ".java,.ts")')
    parser.add_argument('-includeOnlyModule',
                        help='Only include files in this module (e.g., "order")')
    parser.add_argument('-includeOnlyFileNamesInPath', action='store_true',
                        help='Include only file names grouped by directory path, not content')
    parser.add_argument('-scanArea', choices=['frontend', 'backend'],
                        help='Specify area to scan: frontend or backend')
    parser.add_argument('-includeTests', action='store_true',
                        help='Include test files in the scan (normally excluded)')

    args = parser.parse_args()

    # Process root directory
    root = Path(args.root_dir)
    if not root.is_dir():
        sys.exit(f'Error: {args.root_dir} is not a directory.')

    # Determine output paths
    if args.output_file:
        out_file = Path(args.output_file)
        use_json = out_file.suffix.lower() == '.json'
    else:
        # Default to JSON format
        out_file = Path.cwd() / f"{root.name}_scan.json"
        use_json = True

    if use_json:
        json_path = out_file
        txt_path = out_file.with_suffix('.txt')
    else:
        json_path = None
        txt_path = out_file

    # Run scan with filter options
    stats = scan_project(
        root=root,
        out_json_path=json_path,
        out_txt_path=txt_path,
        include_only=args.includeOnly,
        include_only_module=args.includeOnlyModule,
        include_only_file_names_in_path=args.includeOnlyFileNamesInPath,
        scan_area=args.scanArea,
        include_tests=args.includeTests
    )

    # Print statistics
    print_statistics(stats)

    # Display completion message
    if use_json:
        print(f"\nProject scan completed. Output written to {json_path} and {txt_path}")
    else:
        print(f"\nProject scan completed. Output written to {txt_path}")