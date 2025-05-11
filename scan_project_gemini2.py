#!/usr/bin/env python3
"""
Project scanner optimized for LLM code analysis - Plain Text Output
-----------------------------------------------------------------

- Generates a plain text file representing the project structure and key contents.
- Includes LLM instructions, Project Overview, Configuration, Source Highlights,
  Directory Structure, Dependencies, and TODOs/FIXMEs.
- Lists full paths and content for specified file types.
- Notes folders that are empty or contain no scannable items.
- Truncates large files (by size or line count) to keep the output manageable.
  For line-based truncation, it attempts to show a head/tail snippet.
- Removes unnecessary empty lines from the output for a compact format.
- Automatically skips files starting with 'scan_project'.
- Use command-line flags for finer control over verbosity (e.g., --max-depth,
  --max-file-size-kb, --max-content-lines, --include-exts, --ignore-dirs).
"""

import argparse
import os
import re
import sys
from pathlib import Path
from datetime import datetime

# --- Configuration Constants ---
MAX_DEPTH_DEFAULT = 10
MAX_FILE_SIZE_KB_DEF = 32  # More conservative default
MAX_CONTENT_LINES_DEF = 150 # More conservative default
OUTPUT_FILENAME_DEFAULT = "llm_project_context.txt"

# Files/extensions for which content should be included
DEFAULT_FILES_WITH_CONTENT_BY_NAME = {
    'application.properties', 'application.yml', 'application.yaml',
    'build.gradle', 'pom.xml', 'package.json', 'requirements.txt', 'Pipfile',
    '.gitignore', 'README.md', 'CONTRIBUTING.md', 'LICENSE',
    'Dockerfile', 'docker-compose.yml', 'docker-compose.yaml',
    'settings.gradle', 'settings.xml', 'webpack.config.js', 'tsconfig.json',
    'babel.config.js', 'vite.config.js', '.env.example', 'Procfile'
}

DEFAULT_FILES_WITH_CONTENT_BY_EXT = {
    '.java', '.kt', '.scala',  # JVM
    '.py', '.rb', '.php',      # Scripting
    '.js', '.ts', '.jsx', '.tsx', # JavaScript/TypeScript
    '.html', '.htm', '.css', '.scss', '.less', '.vue', '.svelte', # Frontend
    '.c', '.cpp', '.h', '.hpp', '.cs', '.go', '.rs', '.swift', # Compiled
    '.sql', '.sh', '.bash', '.ps1', # Scripts & Data
    '.xml', '.json', '.yaml', '.yml', '.toml', '.ini', '.cfg', '.conf', # Config
    '.md', '.rst' # Documentation
}

# Directories to always ignore
DEFAULT_IGNORE_DIRS = {
    '.git', '__pycache__', 'node_modules', 'bower_components',
    'build', 'dist', 'target', 'out', 'bin', 'obj', '.idea', '.vscode',
    '.DS_Store', 'venv', '.env', 'coverage', '.pytest_cache', '.mypy_cache',
    '.gradle', '.m2', 'vendor'
}

# File patterns to always ignore
DEFAULT_IGNORE_FILES = {
    '*.pyc', '*.pyo', '*.class', '*.jar', '*.war', '*.ear', '*.exe', '*.dll', '*.so',
    '*.o', '*.a', '*.lib', '*.obj', '*.egg-info', '*.dist-info',
    '*.log', '*.tmp', '*.swp', '*.swo', '*.bak', '*.old',
    'package-lock.json', 'yarn.lock', 'composer.lock', 'Gemfile.lock', 'poetry.lock',
    '*.min.js', '*.min.css',
    '*.map'
    # Note: 'scan_project*' is now handled directly in is_ignored
}

# --- Helper Functions ---

def safe_read_file(path: Path, max_size_bytes: int, max_lines: int, head_ratio: float = 0.7, tail_ratio: float = 0.2) -> tuple[str | None, bool]:
    """
    Reads a file, truncating if it's too large (by size or lines).
    For line-based truncation, attempts to show a head/tail snippet of the first `max_lines`.
    Returns (content, is_truncated)
    """
    try:
        file_stat_size = path.stat().st_size
        if file_stat_size == 0:
            return "(empty file)", False

        if file_stat_size > max_size_bytes:
            with path.open('r', encoding='utf-8', errors='ignore') as f:
                content = f.read(max_size_bytes)
            return content + f"\n... (file content truncated: exceeds {max_size_bytes // 1024}KB size limit)", True

        lines_read_up_to_max = []
        is_longer_than_max_lines = False

        with path.open('r', encoding='utf-8', errors='ignore') as f:
            for i, line in enumerate(f):
                if i < max_lines:
                    lines_read_up_to_max.append(line)
                else:
                    is_longer_than_max_lines = True
                    break

        if not lines_read_up_to_max:
            return "(empty file or read error after size check)", False

        if is_longer_than_max_lines:
            head_count = int(max_lines * head_ratio)
            tail_count = int(max_lines * tail_ratio)

            if max_lines <= 5:
                head_count = max_lines
                tail_count = 0
            elif head_count == 0 and max_lines > 0:
                head_count = 1

            if head_count + tail_count > max_lines:
                if tail_count > 0 :
                    head_count = max_lines - tail_count
                    if head_count < 0: head_count = 0
                else:
                    head_count = max_lines

            if head_count < 0: head_count = 0
            if tail_count < 0: tail_count = 0
            if head_count == 0 and tail_count == 0 and max_lines > 0: head_count = 1

            output_parts = []
            if head_count > 0:
                output_parts.append("".join(lines_read_up_to_max[:head_count]))

            num_shown_lines = head_count + tail_count
            message = (f"... (content truncated: showing ~{num_shown_lines} lines "
                       f"[head:{head_count}/tail:{tail_count}] from first {max_lines} lines "
                       f"of a file with >{max_lines} lines)\n")
            output_parts.append(message)

            if tail_count > 0 and len(lines_read_up_to_max) > head_count:
                output_parts.append("".join(lines_read_up_to_max[-tail_count:]))

            return "".join(output_parts), True
        else:
            return "".join(lines_read_up_to_max), False

    except Exception as e:
        return f"(error reading file: {e})", False


def clean_content(text: str) -> str:
    if not text:
        return ""
    normalized_text = text.replace('\r\n', '\n').replace('\r', '\n')
    return re.sub(r'\n\s*\n', '\n\n', normalized_text).strip()

def find_todos_fixmes(file_path: Path, content: str) -> list[str]:
    pattern = re.compile(r"^\s*([#;/\"<!\{\-\*\'\s]*)(TODO|FIXME|XXX|HACK|BUG)(?:[\s:]*)(.*)$", re.IGNORECASE | re.MULTILINE)
    matches = []
    lines = content.splitlines()
    for i, line_text in enumerate(lines):
        match = pattern.search(line_text)
        if match:
            tag = match.group(2).upper()
            message = match.group(3).strip()
            if message:
                matches.append(f"{file_path.as_posix()}:{i+1}: {tag}: {message}")
    return matches

def extract_dependencies(file_path: Path, content: str) -> list[str]:
    dependencies = []
    filename = file_path.name.lower()
    lines = content.splitlines()

    if filename == 'build.gradle' or filename == 'build.gradle.kts':
        pattern = re.compile(r"^\s*(?:implementation|api|compileOnly|runtimeOnly|testImplementation)\s*[\(]?\s*['\"]([^:'\"]+:[^:'\"]+:[^:'\"]+)['\"]\s*[\)]?", re.IGNORECASE)
        for line in lines:
            match = pattern.search(line)
            if match:
                dependencies.append(f"Gradle: {match.group(1)}")
    elif filename == 'pom.xml':
        group_id, artifact_id, version = None, None, None
        for line in lines:
            if "<groupId>" in line and "</groupId>" in line:
                group_id = line.split("<groupId>")[1].split("</groupId>")[0].strip()
            elif "<artifactId>" in line and "</artifactId>" in line:
                artifact_id = line.split("<artifactId>")[1].split("</artifactId>")[0].strip()
            elif "<version>" in line and "</version>" in line:
                version_match = re.search(r"<version>(.*?)</version>", line)
                if version_match:
                    version_text = version_match.group(1).strip()
                    if not (version_text.startswith("${") and version_text.endswith("}")):
                        version = version_text
            if group_id and artifact_id and version:
                dependencies.append(f"Maven: {group_id}:{artifact_id}:{version}")
                group_id, artifact_id, version = None, None, None
            elif "</dependency>" in line:
                group_id, artifact_id, version = None, None, None
    elif filename == 'package.json':
        try:
            import json
            data = json.loads(content)
            for dep_type in ['dependencies', 'devDependencies', 'peerDependencies']:
                if dep_type in data:
                    for pkg, ver in data[dep_type].items():
                        dependencies.append(f"NPM ({dep_type}): {pkg}@{ver}")
        except ImportError:
            dependencies.append("Skipped package.json parsing (json module not found).")
        except json.JSONDecodeError:
            dependencies.append(f"Skipped package.json parsing (invalid JSON for {file_path.name} - possibly due to truncation).")
    elif filename == 'requirements.txt':
        for line in lines:
            line = line.strip()
            if line and not line.startswith('#'):
                dependencies.append(f"Python (pip): {line}")
    return dependencies

def should_include_content(file_path: Path, args: argparse.Namespace) -> bool:
    name_lower = file_path.name.lower()
    if name_lower in args.include_names or file_path.suffix.lower() in args.include_exts:
        return True
    if 'dockerfile' in args.include_names and name_lower == 'dockerfile':
        return True
    return False

def is_ignored(path: Path, args: argparse.Namespace) -> bool:
    path_name = path.name

    if path.is_file():
        # *** NEW: Skip files starting with "scan_project" ***
        if path_name.startswith("scan_project"):
            return True
        if path_name in args.ignore_files:
            return True
        for pattern in args.ignore_file_patterns:
            if re.match(pattern, path_name):
                return True
    elif path.is_dir(): # Check directory specific ignores
        if path_name in args.ignore_dirs:
            return True
        for pattern in args.ignore_dir_patterns:
            if re.match(pattern, path_name):
                return True
    return False

# --- Main Scanning Logic ---
def scan_project(project_path: Path, args: argparse.Namespace) -> str:
    output_lines = []
    project_files_structure = []
    all_todos_fixmes = []
    all_dependencies = []
    processed_files_for_content = set()

    max_bytes = args.max_file_size_kb * 1024

    output_lines.append("## LLM INSTRUCTIONS ##")
    output_lines.append("You are an AI assistant. This file provides a snapshot of a software project.")
    output_lines.append("Your primary goal is to understand the project's current state, identify potential issues or areas for improvement, and suggest concrete next steps.")
    output_lines.append("Consider the project structure, key file contents (which may be truncated using a head/tail snippet approach for longer files), dependencies, and any TODO/FIXME comments.")
    output_lines.append("Suggest actions such as refactoring, adding features, improving documentation, addressing potential bugs, or enhancing security based on the provided context.")
    output_lines.append("Focus on providing actionable and specific recommendations. If information seems missing, you can state what would be helpful to know.")
    output_lines.append("-" * 30)
    output_lines.append("")

    project_name = args.project_name if args.project_name else project_path.name
    output_lines.append("## PROJECT OVERVIEW ##")
    output_lines.append(f"Project Name: {project_name}")
    output_lines.append(f"Project Root: {project_path.resolve().as_posix()}")
    output_lines.append(f"Scan Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S %Z')}")
    if args.project_summary:
        output_lines.append(f"Project Summary: {args.project_summary}")
    else:
        output_lines.append("Project Summary: [No summary provided. LLM should infer purpose from content or ask for clarification.]")
    output_lines.append("-" * 30)
    output_lines.append("")

    output_lines.append("## PROJECT STRUCTURE, CONTENT & HIGHLIGHTS ##")
    output_lines.append("Note: File content may be truncated for brevity (by size or line count). Paths are relative to project root.")
    output_lines.append("For files truncated by line count, a snippet showing the approximate head and tail of the allowed lines is provided.")
    output_lines.append("")

    for root, dirs, files in os.walk(project_path, topdown=True):
        current_path = Path(root)
        relative_path = current_path.relative_to(project_path)

        # Directory filtering must happen before depth check for dirs
        # Filter ignored directories before further processing
        dirs[:] = [d_name for d_name in dirs if not is_ignored(current_path / d_name, args)]
        dirs.sort()


        depth = len(relative_path.parts)
        if depth > args.max_depth:
            project_files_structure.append(f"{'  ' * depth}Halting scan at depth {depth} for {relative_path.as_posix()} (and its subdirectories)")
            dirs[:] = [] # Prune descent into subdirectories of this too-deep directory
            continue


        files.sort()

        level_prefix = '  ' * depth
        if relative_path == Path("."):
            project_files_structure.append(f"Project Root: {project_name}/")
        else:
            project_files_structure.append(f"{level_prefix}Directory: {relative_path.as_posix()}/")

        scannable_items_in_dir = 0
        for filename in files:
            file_path = current_path / filename

            # File ignoring check
            if is_ignored(file_path, args):
                project_files_structure.append(f"{level_prefix}  - {filename} (ignored)")
                continue

            scannable_items_in_dir +=1
            project_files_structure.append(f"{level_prefix}  - {filename}")
            relative_file_path = file_path.relative_to(project_path)


            if should_include_content(file_path, args) and file_path not in processed_files_for_content:
                content, _ = safe_read_file(file_path, max_bytes, args.max_content_lines)
                processed_files_for_content.add(file_path)

                if content is not None:
                    project_files_structure.append(f"{level_prefix}    ``` {file_path.suffix.lower() or 'text'}")
                    project_files_structure.append(clean_content(content))
                    project_files_structure.append(f"{level_prefix}    ```")

                    todos = find_todos_fixmes(relative_file_path, content)
                    if todos:
                        all_todos_fixmes.extend(todos)

                    deps = extract_dependencies(file_path, content)
                    if deps:
                        all_dependencies.extend(deps)
                else:
                    project_files_structure.append(f"{level_prefix}    (Could not read content or file is binary/empty)")

        if not scannable_items_in_dir and not dirs: # if dirs is empty, it means either it was originally empty or all subdirs were ignored
            project_files_structure.append(f"{level_prefix}  (No scannable files in this directory or directory is empty/fully ignored after filtering)")

    output_lines.extend(project_files_structure)
    output_lines.append("-" * 30)
    output_lines.append("")

    output_lines.append("## KEY DEPENDENCIES ##")
    if all_dependencies:
        unique_deps = sorted(list(set(all_dependencies)))
        for dep in unique_deps:
            output_lines.append(f"- {dep}")
    else:
        output_lines.append("No key dependencies automatically extracted or none found in recognized files (possibly due to content truncation).")
    output_lines.append("-" * 30)
    output_lines.append("")

    output_lines.append("## TODOs / FIXMEs ##")
    if all_todos_fixmes:
        for item in all_todos_fixmes:
            output_lines.append(f"- {item}")
    else:
        output_lines.append("No TODO, FIXME, XXX, HACK, or BUG comments found in scanned (and potentially truncated) file contents.")
    output_lines.append("-" * 30)
    output_lines.append("")

    output_lines.append("## LLM PROMPT FOR NEXT STEPS ##")
    output_lines.append("Based on the information provided above:")
    output_lines.append("1. What is your overall understanding of this project's purpose and current state?")
    output_lines.append("2. What are the 3-5 most critical next steps you would recommend for this project's development or improvement? Be specific.")
    output_lines.append("3. Are there any potential issues, risks (e.g., missing error handling, security concerns, outdated dependencies from truncated files), or areas needing refactoring that stand out?")
    output_lines.append("4. What additional information, if any, would help you provide a more comprehensive analysis?")
    output_lines.append("Please provide your analysis and recommendations below.")
    output_lines.append("## END OF PROJECT CONTEXT ##")

    return "\n".join(output_lines)

def main():
    parser = argparse.ArgumentParser(
        description=(
            "Scan a project directory and generate a text file optimized for LLM analysis.\n"
            "File content is truncated by size or line count (head/tail snippet for lines) to manage verbosity.\n"
            "Automatically skips files starting with 'scan_project'.\n"
            "Use flags like --max-depth, --max-file-size-kb, --max-content-lines, --include-exts, \n"
            "--include-names, and --ignore-dirs/files to customize the output."
        ),
        formatter_class=argparse.RawTextHelpFormatter
    )
    parser.add_argument(
        "project_path",
        type=str,
        help="Path to the project directory to scan."
    )
    parser.add_argument(
        "--output-file", "-o",
        type=str,
        default=OUTPUT_FILENAME_DEFAULT,
        help=f"Name of the output text file. Default: {OUTPUT_FILENAME_DEFAULT}"
    )
    # ... (rest of the arguments are the same as before) ...
    parser.add_argument(
        "--project-name", "-n",
        type=str,
        help="Custom name for the project (infers from directory name if not set)."
    )
    parser.add_argument(
        "--project-summary", "-s",
        type=str,
        help="A brief summary of the project's purpose and current state."
    )
    parser.add_argument(
        "--max-depth", "-d",
        type=int,
        default=MAX_DEPTH_DEFAULT,
        help=f"Maximum directory depth to scan. Default: {MAX_DEPTH_DEFAULT}"
    )
    parser.add_argument(
        "--max-file-size-kb", "-fs",
        type=int,
        default=MAX_FILE_SIZE_KB_DEF,
        help=f"Maximum size (in KB) for including file content. Default: {MAX_FILE_SIZE_KB_DEF}KB"
    )
    parser.add_argument(
        "--max-content-lines", "-cl",
        type=int,
        default=MAX_CONTENT_LINES_DEF,
        help=f"Maximum number of lines to include from a file's content (head/tail snippet if truncated). Default: {MAX_CONTENT_LINES_DEF}"
    )
    parser.add_argument(
        "--include-names",
        type=str,
        nargs='*',
        default=list(DEFAULT_FILES_WITH_CONTENT_BY_NAME),
        help=f"Specific filenames to include content from (e.g., 'README.md' 'pom.xml'). Defaults: {', '.join(DEFAULT_FILES_WITH_CONTENT_BY_NAME)}"
    )
    parser.add_argument(
        "--include-exts",
        type=str,
        nargs='*',
        default=list(DEFAULT_FILES_WITH_CONTENT_BY_EXT),
        help=f"File extensions to include content from (e.g., '.java' '.py'). Defaults: {', '.join(DEFAULT_FILES_WITH_CONTENT_BY_EXT)}"
    )
    parser.add_argument(
        "--ignore-dirs",
        type=str,
        nargs='*',
        default=list(DEFAULT_IGNORE_DIRS),
        help=f"Directory names to ignore. Defaults: {', '.join(DEFAULT_IGNORE_DIRS)}"
    )
    parser.add_argument(
        "--ignore-files",
        type=str,
        nargs='*',
        default=list(DEFAULT_IGNORE_FILES),
        help=f"Specific filenames or patterns to ignore (e.g., '*.log', 'temp_data.json'). Defaults: {', '.join(DEFAULT_IGNORE_FILES)}"
    )
    parser.add_argument(
        "--ignore-dir-patterns",
        type=str,
        nargs='*',
        default=[],
        help="Custom regex patterns for directory names to ignore (e.g., '.*_cache')."
    )
    parser.add_argument(
        "--ignore-file-patterns",
        type=str,
        nargs='*',
        default=[],
        help="Custom regex patterns for file names to ignore (e.g., 'temp_.*\\.log'). Note: 'scan_project*' is handled automatically."
    )

    args = parser.parse_args()

    args.include_names = {name.lower() for name in args.include_names}
    args.include_exts = {ext.lower() if ext.startswith('.') else '.' + ext.lower() for ext in args.include_exts}
    args.ignore_dirs = {name.lower() for name in args.ignore_dirs}
    args.ignore_files = {name.lower() for name in args.ignore_files} # scan_project* handled separately

    project_path = Path(args.project_path)
    if not project_path.is_dir():
        print(f"Error: Project path '{args.project_path}' is not a valid directory or does not exist.", file=sys.stderr)
        sys.exit(1)

    print(f"Scanning project: {project_path.resolve()}")
    print(f"Output will be saved to: {Path(args.output_file).resolve()}")
    print(f"Using content limits: max_file_size_kb={args.max_file_size_kb}, max_content_lines={args.max_content_lines}")
    print("Note: Files starting with 'scan_project' will be automatically ignored.")


    try:
        project_context = scan_project(project_path, args)
        output_file_path = Path(args.output_file)
        with output_file_path.open('w', encoding='utf-8') as f:
            f.write(project_context)
        print(f"Project context successfully generated: {output_file_path.resolve()}")
    except Exception as e:
        print(f"An error occurred during scanning: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)

if __name__ == "__main__":
    main()