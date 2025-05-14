#!/usr/bin/env python3
"""
AST-based Project Scanner for Code Chunking
--------------------------------------------
* Walks a project directory, skipping specified dirs and test sources.
* Parses source code files using tree-sitter to extract meaningful chunks (classes, functions).
* Outputs a JSON object containing:
    - project_name: Name of the scanned project.
    - project_summary: Summary from README or a default.
    - llm_instructions: Predefined instructions for an LLM.
    - code_chunks: An array where each object contains:
        - file_path: Relative path to the file.
        - entity_type: Type of the code entity (e.g., CLASS, FUNCTION, METHOD).
        - entity_name: Name of the entity.
        - start_line: Starting line number of the chunk.
        - end_line: Ending line number of the chunk.
        - code_content: The actual source code of the chunk.
* Result is written to <project_name>_ast_scan.json or a path supplied as a second CLI argument.
"""
from __future__ import annotations
import os
import sys
import json
from pathlib import Path
from tree_sitter import Language, Parser

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

# Files for which we want to attempt AST parsing
AST_PARSE_EXTS = {'.java', '.py', '.js', '.ts', '.go', '.rb', '.cs'} # Add more as needed

TEST_SUFFIXES = {
    'Test.java', 'Tests.java', 'IT.java', 'Spec.java',
    'Test.kt', 'Tests.kt',
    '.test.js', '.spec.js', '.test.jsx',
    '.test.ts', '.spec.ts', '.test.tsx',
    '_test.py', '_spec.rb', '_test.go',
    'Tests.cs'
}

DEFAULT_SUMMARY = (
    "The Multi‑Restaurant Platform is a Spring Boot 3 (Java 21) application structured as multiple Gradle sub‑modules (api, security, common, restaurant, menu, order, payment, print, admin). "
    "It offers multi‑tenant restaurant management with JWT‑secured APIs, Flyway migrations, and Docker deployment descriptors."
)

LLM_INSTRUCTIONS = (
    "You are an expert full stack web/mobile developer. You have an expert knowledge of Java, gradle, maven, spring boot, docker, docker-compose, angular, react, javascript, typescript, html, css and python. I need your assistance in developing and improving my application while being careful not breaking the current working app. "
    "The project already been started and is progressing. the \"Multi‑Restaurant Platform.\" I will guide you. analyze the attached file and decide for the next step. Put modules, paths, every java class, interface, implementation, application. properties, build.gradle files etc. that are critical for the project into your memory. Always give full path of the files at beginning. always ask before proceeding. Always with small steps. each step should include a feature change, an automation testing and git commit message. "
)


# --- Tree-sitter Configuration ---
# IMPORTANT: This path points to where the compiled grammar library should be.
# You will create this file in the setup steps later.
# It's relative to where you run the script, or use an absolute path.
# For simplicity, we'll aim to create it in a 'build' subdirectory next to your grammars.
LANGUAGE_GRAMMAR_BUILD_FILE = 'tree_sitter_grammars/build/my-languages.so' # macOS/Linux
# If on Windows, it would be 'tree_sitter_grammars\\build\\my-languages.dll'

# Define language names as they are known by tree-sitter grammars
LANGUAGE_GRAMMARS_CONFIG = {
    '.java': 'java',
    '.py': 'python',
    '.js': 'javascript',
    '.ts': 'typescript',
    # Add more: '.go': 'go', '.rb': 'ruby', '.cs': 'c_sharp',
}

# Define AST node queries for extracting entities for each language
# Format: (entity_type_name, tree_sitter_query_for_node, name_capture_in_query)
AST_QUERIES = {
    'java': [
        ('CLASS', '(class_declaration name: (identifier) @name)', 'name'),
        ('INTERFACE', '(interface_declaration name: (identifier) @name)', 'name'),
        ('METHOD', '(method_declaration name: (identifier) @name)', 'name'),
        ('CONSTRUCTOR', '(constructor_declaration name: (identifier) @name)', 'name'),
        ('ENUM', '(enum_declaration name: (identifier) @name)', 'name'),
    ],
    'python': [
        ('CLASS', '(class_definition name: (identifier) @name)', 'name'),
        ('FUNCTION', '(function_definition name: (identifier) @name)', 'name'),
    ],
    'javascript': [
        ('CLASS', '(class_declaration name: (identifier) @name)', 'name'),
        ('FUNCTION', '(function_declaration name: (identifier) @name)', 'name'),
        ('METHOD', '(method_definition name: (property_identifier) @name)', 'name'),
        ('ARROW_FUNCTION_VARIABLE', '(lexical_declaration (variable_declarator name: (identifier) @name value: (arrow_function)))', 'name'),
    ],
    'typescript': [
        ('CLASS', '(class_declaration name: (type_identifier) @name)', 'name'),
        ('INTERFACE', '(interface_declaration name: (type_identifier) @name)', 'name'),
        ('FUNCTION', '(function_declaration name: (identifier) @name)', 'name'),
        ('METHOD', '(method_definition name: (property_identifier) @name)', 'name'),
    ],
}

# Loaded tree-sitter language objects
LANGUAGES = {}

def _load_tree_sitter_languages():
    """Loads tree-sitter languages from the compiled library."""
    global LANGUAGES
    grammar_file_path = Path(LANGUAGE_GRAMMAR_BUILD_FILE)
    if not grammar_file_path.exists():
        print(f"Error: Compiled tree-sitter grammar file not found at '{grammar_file_path.resolve()}'", file=sys.stderr)
        print("Please ensure you have compiled the grammars (see setup instructions).", file=sys.stderr)
        return False

    for ext, lang_name in LANGUAGE_GRAMMARS_CONFIG.items():
        if ext not in AST_PARSE_EXTS: # Only load grammars we intend to use for AST parsing
            continue
        try:
            ts_lang_obj = Language(str(grammar_file_path.resolve()), lang_name)
            LANGUAGES[ext] = ts_lang_obj
            print(f"Successfully loaded grammar for '{lang_name}' ({ext})", file=sys.stderr)
        except Exception as e:
            print(f"Error loading grammar for '{lang_name}' ({ext}) from {grammar_file_path}: {e}.", file=sys.stderr)
            print(f"AST parsing for {ext} files will be skipped.", file=sys.stderr)
    return bool(LANGUAGES)

# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

def is_skipped_dir(d: str) -> bool:
    return d in SKIP_DIRS or (d.startswith('.') and d not in {'.github', '.gitlab-ci'})

def is_test_file(p: Path) -> bool:
    name = p.name
    if any(name.endswith(suf) for suf in TEST_SUFFIXES):
        return True
    lowered_parts = [part.lower() for part in p.parts]
    return 'test' in lowered_parts or 'tests' in lowered_parts

def read_file_content_bytes(p: Path) -> bytes | None:
    try:
        with open(p, 'rb') as f: # Read as bytes for tree-sitter
            return f.read()
    except Exception as exc:
        print(f"Error reading file {p}: {exc}", file=sys.stderr)
        return None

def get_node_text(node, content_bytes: bytes) -> str:
    return content_bytes[node.start_byte:node.end_byte].decode('utf-8', 'ignore')

def get_node_name(match, name_capture_key: str, content_bytes: bytes) -> str:
    # A match is a dict: {capture_name: node, ...}
    for node in match.get(name_capture_key, []): # query.captures gives (node, name)
        # query.matches gives (pattern_idx, [{name: node}, ...])
        # Simplified to expect a list of nodes for the name_capture_key
        return get_node_text(node, content_bytes)
    return "Unnamed"


def get_project_summary(root: Path) -> str:
    """Return first ~20 non‑empty lines from README or a default summary."""
    for name in ('README.md', 'readme.md', 'README.txt', 'README.rst', 'readme.rst'):
        rp = root / name
        if rp.exists():
            try:
                with open(rp, 'r', encoding='utf-8', errors='ignore') as f:
                    collected, non_empty_lines_count = [], 0
                    for line_content in f:
                        line_content = line_content.rstrip('\n')
                        collected.append(line_content)
                        if line_content.strip():
                            non_empty_lines_count += 1
                        if non_empty_lines_count >= 20:
                            break
                    return '\n'.join(collected)
            except Exception:
                pass # If error reading, will fall through to default
    return DEFAULT_SUMMARY

# ---------------------------------------------------------------------------
# Core AST Parsing Logic
# ---------------------------------------------------------------------------

def parse_file_with_tree_sitter(file_path: Path, lang_ext: str, content_bytes: bytes) -> list[dict]:
    chunks = []
    ts_lang_obj = LANGUAGES.get(lang_ext)
    if not ts_lang_obj:
        # This case should ideally be caught before calling if _load_tree_sitter_languages is effective
        print(f"Warning: No grammar object found for '{lang_ext}' when trying to parse {file_path}. Skipping.", file=sys.stderr)
        return chunks

    parser = Parser()
    parser.set_language(ts_lang_obj)
    tree = parser.parse(content_bytes)
    root_node = tree.root_node

    queries_for_lang = AST_QUERIES.get(LANGUAGE_GRAMMARS_CONFIG[lang_ext], [])

    for entity_type, query_str, name_capture_key in queries_for_lang:
        try:
            query = ts_lang_obj.query(query_str)
            # Each match is a list of captures for that match.
            # A capture is a dict {capture_name_in_query: node}
            # For tree-sitter Py bindings `query.matches` returns:
            # list of (pattern_index, list of {capture_name: node})
            for _pattern_index, captures_in_match_list in query.matches(root_node):
                # Convert list of dicts to a single dict mapping capture name to list of nodes
                current_match_captures = {}
                main_node_for_match = None

                min_start_byte = float('inf')
                max_end_byte = float('-inf')

                for capture_obj in captures_in_match_list: # capture_obj is like {'name': Node, 'body': Node}
                    # but actual API is {capture_name_as_str: Node}
                    # The API actually gives (node, capture_name_idx_as_str)
                    # Let's stick to the documented format of captures as (Node, str_name)
                    # Correcting based on `query.captures(node)` which yields (Node, str_capture_name)
                    # `query.matches(node)` returns `list[(int, dict[str, Node | list[Node]])]`
                    # Let's re-evaluate how `captures` are handled for a `match`.
                    # If a query is `(class name:(identifier)@name body:(block)@body)`,
                    # one match would give captures like `{'name': Node, 'body': Node}`.
                    pass # Need to handle the actual structure from query.matches better.

                # Re-simplifying based on common usage: use query.captures for specific named captures
                # and then iterate over the primary node type defined in the query for the overall structure.

                # A more robust way: iterate through nodes of the main type
                # For example, if query is '(class_declaration ...)', find all class_declaration nodes
                # and then run sub-queries or access children for names, etc.
                # The current `AST_QUERIES` are designed to capture the entity and its name.

                for captured_node, capture_name_str in query.captures(root_node):
                    # This gives all captures in the file. We need to associate them.
                    # This loop structure needs to be match-oriented.

                    # Let's refine `query.matches` usage
                    # `matches = query.matches(root_node)`
                    # `match` is `(pattern_index, list_of_capture_dicts)`
                    # `capture_dict` is `{'capture_name': Node}`
                    # This part of tree-sitter's Python API can be confusing.
                    # Let's use a common pattern: iterate nodes matching the main part of the query.
                    # To do this effectively, the query itself should capture the main node.
                    # E.g. (class_declaration ... ) @class_def
                    # For now, using the provided AST_QUERIES structure as best as possible.

                    # The provided AST_QUERIES try to capture the 'name'. We need the node for the whole entity.
                    # Assume the query targets the whole entity and `@name` is for the name.
                    # This is an approximation for complex grammars.

                    # We need to iterate matches, not all captures globally.
                    pass # The loop below is the intended match-oriented approach.

            # Corrected loop for matches:
            for _, match_captures_list in query.matches(root_node): # match_captures_list is [{name_str: Node}, ...]
                if not match_captures_list:
                    continue

                # Consolidate captures for the current match
                # A single match can have multiple named captures.
                # e.g. query `(function_definition name: (identifier) @func_name parameters: (parameters) @params)`
                # match_captures_list might look like `[{'func_name': Node1}, {'params': Node2}]`

                # We need to determine the "main node" for the chunk (e.g., the function_definition node itself).
                # This typically requires the query to capture the main node, e.g., `(function_definition ... ) @function`.
                # If the query is just `(identifier) @name` within a function, this gets tricky.
                # The current AST_QUERIES imply the query string IS the main node.

                # Heuristic: The node that spans all captured parts in this match.
                current_match_nodes = [node for cap_dict in match_captures_list for node in cap_dict.values()]
                if not current_match_nodes:
                    continue

                min_node = min(current_match_nodes, key=lambda n: n.start_byte)
                max_node = max(current_match_nodes, key=lambda n: n.end_byte)

                start_byte = min_node.start_byte
                end_byte = max_node.end_byte

                code_content_chunk = content_bytes[start_byte:end_byte].decode('utf-8', 'ignore')
                start_line = min_node.start_point[0] + 1  # 0-indexed to 1-indexed
                end_line = max_node.end_point[0] + 1

                # Extract name using the 'name_capture_key'
                entity_name_str = "Unnamed"
                for cap_dict in match_captures_list:
                    if name_capture_key in cap_dict:
                        entity_name_str = get_node_text(cap_dict[name_capture_key], content_bytes)
                        break # Found the name

                chunks.append({
                    "file_path": str(file_path),
                    "entity_type": entity_type,
                    "entity_name": entity_name_str,
                    "start_line": start_line,
                    "end_line": end_line,
                    "code_content": code_content_chunk,
                })

        except Exception as e:
            print(f"Error processing query for {entity_type} in {file_path} (lang: {LANGUAGE_GRAMMARS_CONFIG[lang_ext]}): {e}", file=sys.stderr)
            print(f"Query was: {query_str}", file=sys.stderr) # For debugging
    return chunks

# ---------------------------------------------------------------------------
# Core scanner logic
# ---------------------------------------------------------------------------

def scan(root: Path, out_path: Path):
    project_name = root.name
    project_summary = get_project_summary(root)
    all_chunks = []

    # Attempt to load languages first
    if not _load_tree_sitter_languages() and any(ext in AST_PARSE_EXTS for ext in LANGUAGE_GRAMMARS_CONFIG):
        print("Warning: AST parsing might be incomplete due to grammar loading issues.", file=sys.stderr)
        # Decide if to proceed or exit, for now, proceed but chunks list might be empty.

    for dirpath, dirnames, filenames in os.walk(root, topdown=True):
        dirnames[:] = [d for d in dirnames if not is_skipped_dir(d)]
        current_dir_path = Path(dirpath)

        for fname in sorted(filenames):
            fpath_abs = current_dir_path / fname
            fpath_rel = fpath_abs.relative_to(root)

            if is_test_file(fpath_rel):
                continue

            file_ext = fpath_abs.suffix.lower()

            if file_ext in AST_PARSE_EXTS and file_ext in LANGUAGES: # Check if grammar loaded
                print(f"AST Parsing: {fpath_rel}", file=sys.stderr)
                content_bytes = read_file_content_bytes(fpath_abs)
                if content_bytes:
                    try:
                        file_chunks = parse_file_with_tree_sitter(fpath_rel, file_ext, content_bytes)
                        all_chunks.extend(file_chunks)
                    except Exception as e:
                        print(f"Critical error during AST parsing of {fpath_rel}: {e}", file=sys.stderr)

            elif file_ext in AST_PARSE_EXTS and file_ext not in LANGUAGES:
                print(f"Skipping AST parsing for {fpath_rel} (grammar not loaded for this extension).", file=sys.stderr)

    # Prepare final JSON output
    output_data = {
        "project_name": project_name,
        "project_summary": project_summary,
        "llm_instructions": LLM_INSTRUCTIONS,
        "code_chunks": all_chunks
    }

    # Write output as JSON
    try:
        with open(out_path, 'w', encoding='utf-8') as out_f:
            json.dump(output_data, out_f, indent=2)
        print(f"\nSuccessfully wrote AST scan to: {out_path.resolve()}", file=sys.stderr)
    except Exception as e:
        print(f"Error writing JSON output to {out_path}: {e}", file=sys.stderr)

# ---------------------------------------------------------------------------
# Entry‑point
# ---------------------------------------------------------------------------
if __name__ == '__main__':
    if len(sys.argv) < 2:
        # If scanning a project that IS the current dir, root_dir_arg.name might be "."
        # Better to use Path.cwd().name for the project name if scanning current dir.
        root_dir_arg = Path.cwd()
        print(f"No project directory specified. Scanning current working directory: {root_dir_arg}", file=sys.stderr)
        project_scan_name = root_dir_arg.name if root_dir_arg.name else "scanned_project"
    else:
        root_dir_arg = Path(sys.argv[1])
        project_scan_name = root_dir_arg.name

    if not root_dir_arg.is_dir():
        sys.exit(f"Error: Provided path '{root_dir_arg}' is not a directory or does not exist.")

    if len(sys.argv) > 2:
        out_file_arg = Path(sys.argv[2])
    else:
        out_file_arg = Path.cwd() / f"{project_scan_name}_ast_scan.json"

    print(f"Scanning project: {root_dir_arg.resolve()}", file=sys.stderr)
    print(f"Output will be written to: {out_file_arg.resolve()}", file=sys.stderr)

    scan(root_dir_arg, out_file_arg)