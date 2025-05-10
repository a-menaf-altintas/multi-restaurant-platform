#!/usr/bin/env python3
"""
Project scanner optimized for LLM code analysis - Plain Text Output
-----------------------------------------------------------------

- Generates a plain text file representing the project structure and key contents.
- Includes Project Name, Project Summary, and LLM instructions.
- Lists full paths and content for specified file types (Java, configs, frontend).
- Notes folders that are empty or contain no scannable items.
- Truncates large files to keep the output size manageable.
- Removes unnecessary empty lines from the output for a compact format.
"""
from __future__ import annotations
import os
import sys
from pathlib import Path
import argparse # For more robust argument parsing
import re # For cleaning up empty lines

# ---------------- default constants -----------------------------------------
MAX_DEPTH_DEFAULT = 15      # Max directory depth to scan
MAX_FILE_SIZE_KB_DEF = 1024  # Size limit for file content in KB
# -----------------------------------------------------------------------------

# Files/extensions for which content should be included
FILES_WITH_CONTENT_BY_NAME = {
    'application.properties', 'build.gradle', '.gitignore',
    'Dockerfile', 'dockerfile', # Dockerfile (case-insensitive check handled by .name being in this set)
    'docker-compose.yml', 'docker-compose.yaml'
}
FILES_WITH_CONTENT_BY_EXT = {
    '.java', '.ts', '.js', '.css', '.scss',
    '.dockerfile' # e.g., backend.dockerfile
}
HTML_EXTENSION = '.html'

# Directories to skip
SKIP_DIRS = {
    '.git', '.svn', '.hg', '.idea', '.vscode', '.vs', 'nbproject', '.project', '.settings',
    'target', 'build', 'dist', 'out', 'bin', 'obj', 'release', 'coverage',
    'node_modules', 'vendor', 'bower_components', '.m2',
    'venv', '.venv', 'env', '.env', 'ENV', '__pycache__', '.pytest_cache', '.mypy_cache', '.tox',
    'logs', 'temp', 'tmp', 'data', 'uploads', '.DS_Store', 'Thumbs.db',
    '.gradle', '.sass-cache', 'jekyll-cache'
}

# Test file patterns - used to identify test HTML files
TEST_FILE_PATTERNS = {
    'Test.java', 'Tests.java', 'IT.java', 'Spec.java', '.test.js', '.spec.js',
    '.test.ts', '.spec.ts', 'test_', '_test.py', '_test.go', 'test.sh'
}
TEST_FILE_SUFFIXES = {
    'Test.java', 'Tests.java', 'IT.java', 'Spec.java', 'Test.kt', 'Tests.kt',
    'Spec.kt', '.test.js', '.spec.js', '.test.jsx', '.test.ts', '.spec.ts',
    '.test.tsx', '_test.py', '_spec.rb', '_test.go', 'Tests.cs'
}

def is_skipped_dir(dirname: str, dir_path: Path) -> bool:
    if dirname.startswith('.') and dirname not in {'.github', '.gitlab-ci'}:
        return True
    return dirname in SKIP_DIRS

def is_test_file(file_path: Path) -> bool:
    filename = file_path.name
    if any(pattern in filename for pattern in TEST_FILE_PATTERNS):
        return True
    if any(filename.endswith(suffix) for suffix in TEST_FILE_SUFFIXES):
        return True
    parts = [part.lower() for part in file_path.parts]
    if 'test' in parts or 'tests' in parts:
        try:
            src_index = parts.index('src')
            test_idx = parts.index('test') if 'test' in parts else parts.index('tests')
            if src_index < test_idx:
                return True
        except ValueError:
            return True
    return False

def read_file_content(file_full_path: Path, max_kb: int) -> str:
    try:
        file_size = file_full_path.stat().st_size
        if file_size == 0:
            return "<Empty file>"
        if max_kb > 0 and file_size > max_kb * 1024:
            truncate_msg = f"\n\n... [File truncated at {max_kb}KB (original size: {file_size / 1024:.2f}KB)] ..."
            max_bytes_to_read = max_kb * 1024
        else:
            truncate_msg = ""
            max_bytes_to_read = -1
        with open(file_full_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read(max_bytes_to_read)
            if truncate_msg:
                last_newline = content.rfind('\n')
                if last_newline != -1:
                    content = content[:last_newline]
                content += truncate_msg
            return content
    except FileNotFoundError:
        return f"<File not found: {file_full_path}>"
    except Exception as e:
        return f"<Error reading file {file_full_path}: {e}>"

def get_project_summary_from_readme_file(root_dir: Path) -> str:
    readme_filenames = ['README.md', 'README.txt', 'readme.md']
    summary = ""
    for readme_name in readme_filenames:
        readme_path = root_dir / readme_name
        if readme_path.exists() and readme_path.is_file():
            try:
                with open(readme_path, 'r', encoding='utf-8', errors='ignore') as rf:
                    lines = []
                    non_empty_lines_count = 0
                    for i, line in enumerate(rf):
                        if non_empty_lines_count >= 20: break
                        stripped_line = line.strip()
                        if stripped_line: non_empty_lines_count += 1
                        if non_empty_lines_count > 3 and (stripped_line.startswith('## ') or stripped_line.startswith('### ')): break
                        lines.append(line)
                    summary = "".join(lines).strip()
                    if summary: return summary
            except Exception: pass
    return summary

def get_default_project_summary(project_name: str) -> str:
    return f"""Project Name: {project_name} (Multi-Restaurant Platform)

Overview:
The Multi-Restaurant Platform is a Docker-containerized Spring Boot application that provides a complete solution for restaurant management, online ordering, and delivery services. It supports multiple restaurants, each with their own menus, administrators, and configurations.

Key Features:
- Multi-restaurant Support: Manage multiple restaurants on a single platform.
- User Authentication: Secure JWT-based authentication and role-based authorization (Roles: ADMIN, RESTAURANT_ADMIN, CUSTOMER).
- Menu Management: Create and manage restaurant menus, categories, and individual items.
- Order Processing: Handle customer orders with various statuses.
- Payment Integration: Designed to process payments with Stripe (currently using a mock implementation).
- WebSocket Printing System: Automated receipt and kitchen ticket printing directly from the browser.
- Content Management: Built-in CMS for platform content.
- Admin Dashboard: Comprehensive admin tools for system configuration.

Tech Stack:
- Programming Language: Java 21
- Framework: Spring Boot 3.x (e.g., 3.2.5, adaptable to newer 3.x versions)
- Security: Spring Security, JWT
- Data Persistence: Spring Data JPA
- Database: PostgreSQL (production), H2 (development/testing)
- Database Migration: Flyway
- Real-time Communication: WebSockets
- Build Tool: Gradle
- Containerization: Docker
- Utilities: Lombok
"""

LLM_INSTRUCTIONS = """

LLM Instructions:
Hello LLM, I need your assistance in developing and improving my application while being careful not breaking the current working app. The project already been started and is progressing. the "Multi-Restaurant Platform." I will guide you on the current stage of development, and I expect you to act as a senior full-stack developer, leveraging your knowledge of the technologies involved and the project details I provide.

**1. Introduction to the Multi-Restaurant Platform**

The "Multi-Restaurant Platform" is a comprehensive, Docker-containerized Spring Boot application designed to serve as a complete solution for restaurant management, online ordering, and delivery services. Its core capability is to support multiple distinct restaurants on a single platform, each with its own configurable menus, dedicated administrators, and operational settings.

**Key Features:**
* **Multi-restaurant Support:** Enables onboarding and management of numerous restaurants.
* **User Authentication & Authorization:** Secure, role-based access control (Roles: ADMIN, RESTAURANT_ADMIN, CUSTOMER) using JWT (JSON Web Tokens).
* **Menu Management:** Allows restaurants to create, customize, and manage their menus, including categories and individual food/beverage items.
* **Order Processing System:** Facilitates handling of customer orders through various statuses from placement to delivery/completion.
* **Payment Integration:** Designed for payment processing, initially with a mock Stripe implementation, with plans for full Stripe integration.
* **WebSocket Printing System:** Enables automated, real-time printing of receipts and kitchen tickets directly from the browser.
* **Content Management System (CMS):** Includes a built-in CMS for managing platform-wide content.
* **Admin Dashboard:** Provides comprehensive tools for platform administrators to configure and manage the system.

**Technology Stack:**
* **Programming Language:** Java 21
* **Framework:** Spring Boot 3.x (The project aims to use recent versions like 3.2.5 or higher, potentially up to 3.4.x as mentioned in project documentation)
* **Security:** Spring Security, JWT
* **Data Persistence:** Spring Data JPA
* **Database:** PostgreSQL (for production environments), H2 (for development and testing)
* **Database Migration:** Flyway
* **Real-time Communication:** WebSockets
* **Build Tool:** Gradle
* **Containerization:** Docker
* **Utilities:** Lombok

**2. Development Roadmap and Plan**

This roadmap outlines the key phases and steps involved in building and enhancing the Multi-Restaurant Platform. While presented sequentially, actual development might iterate or address steps out of this specific order based on project priorities (e.g., API documentation might be initiated early, or specific modules prioritized differently). I will inform you of the primary focus, but always use the provided project files as the definitive source for the current state of implemented features.

**Phase 0: Project Setup & Foundation**
* **Step 0.1: Local Development Environment Setup**
    * Install Java 21 SDK, Gradle, Docker, and an IDE (e.g., IntelliJ IDEA, Eclipse).
    * Set up PostgreSQL and H2 database instances.
    * Clone the project repository and ensure a clean build.
* **Step 0.2: Version Control Strategy**
    * Confirm Git branching strategy (e.g., Gitflow, feature branches).
* **Step 0.3: Project Structure Review**
    * Understand the multi-module Gradle setup (`backend`, `api`, `common`, `security`, `restaurant`, `menu`, `order`, `payment`, `print`, `admin`).
    * Review root `build.gradle` and `settings.gradle`.
* **Step 0.4: Initial Database Schema with Flyway**
    * Review/Implement initial Flyway migration scripts (e.g., `backend/api/src/main/resources/db/migration/V1_init_schema.sql`, `V2_initial_data.sql`).

**Phase 1: Core Backend Modules - Entities, Repositories, Services, Initial APIs**
* **Step 1.1: `common` Module**
* **Step 1.2: User Management & `security` Module**
* **Step 1.3: `restaurant` Module**
* **Step 1.4: `menu` Module**
* **Step 1.5: `order` Module**
* **Step 1.6: `payment` Module (Mock Implementation First)**
* **Step 1.7: `admin` Module**
(Note: Verify the implementation status of these modules from the provided project files, as not all may be started or completed at any given time.)

**Phase 2: API Refinement & Documentation**
* **Step 2.1: API Design Consistency**
* **Step 2.2: OpenAPI/Swagger Integration (Note: This may have already been started or be partially implemented; please check project files for current status.)**

**Phase 3: Real-time Features & Advanced Functionality**
* **Step 3.1: WebSocket Printing System (`print` module)**
* **Step 3.2: Full Payment Integration (Stripe)**
* **Step 3.3: Content Management System (CMS)**

**Phase 4: Testing & Quality Assurance**
* **Step 4.1: Unit Testing**
* **Step 4.2: Integration Testing**
* **Step 4.3: Security Testing**

**Phase 5: Containerization & Deployment Preparation**
* **Step 5.1: Dockerfile Optimization**
* **Step 5.2: `docker-compose.yml` Configuration**
* **Step 5.3: Database Migrations for Production**

**Phase 6: CI/CD (Continuous Integration/Continuous Deployment)**
* **Step 6.1: CI Pipeline Setup**
* **Step 6.2: CD Pipeline Setup (Optional for now)**

**Phase 7: Frontend Development (Placeholder - to be detailed later)**

**Phase 8: Production Deployment & Monitoring**

**Phase 9: Ongoing Maintenance & Feature Enhancements**

**3. Instructions for the LLM**

To effectively assist me, please adhere to the following:
* **Contextual Awareness:** I will provide you with a text file that contains a snapshot of the project's structure, key file contents, and a summary. Please consider this your primary source of truth for the project's current state and architecture.
* **Current Step Focus:** I will specify the current phase and step from the roadmap above that we are working on. Please focus your advice and code generation on this specific step.
* **Code Generation:** When providing code examples, please ensure they are consistent with Java 21, Spring Boot 3.x, and the other technologies listed in the tech stack. Refer to existing code patterns in the provided text file if available.
* **Best Practices:** Offer advice based on industry best practices, security considerations, and performance optimization.
* **Clarity and Explanation:** Explain your suggestions and code clearly, especially the reasoning behind architectural decisions or complex logic.
* **Iterative Development:** We will work iteratively. I may ask for refinements or alternative solutions.

By following these guidelines, we can have a productive and efficient collaboration. Try to guess the current state of the project so that we can continue from there. Ask to provide you the relavant files like java classes, build.gradle, settings.gradle, application.properties, dockerfile etc. And guide me with the next steps. Carefully check content of the project what is currently done. first create the step and ask me if i want to proceed with your suggestion. Dont ask me what specific aspect of the project I'd like to discuss next. you decide and ask me if your plan is correct and if i want to proceed with your plan
"""


def scan_project_to_text(
        root_dir_str: str,
        output_file_str: str,
        project_summary_cmd_arg: str | None = None,
        max_file_size_kb: int = MAX_FILE_SIZE_KB_DEF,
        max_scan_depth: int = MAX_DEPTH_DEFAULT
) -> None:
    root_dir = Path(root_dir_str).resolve()
    script_full_path = Path(__file__).resolve() if Path(__file__).is_file() else None
    project_name = root_dir.name

    final_project_summary = project_summary_cmd_arg
    if not final_project_summary:
        final_project_summary = get_project_summary_from_readme_file(root_dir)
    if not final_project_summary:
        final_project_summary = get_default_project_summary(project_name)

    processed_files_for_text = []
    empty_folder_reports = [] # Will store strings without trailing newlines
    files_processed_count = 0

    for dir_path_str, dirnames, filenames in os.walk(root_dir, topdown=True):
        current_dir_path = Path(dir_path_str)
        relative_dir_path = current_dir_path.relative_to(root_dir)
        current_depth = len(relative_dir_path.parts)

        if current_depth > max_scan_depth :
            dirnames[:] = []
            filenames[:] = []
            continue

        dirnames[:] = [d for d in dirnames if not is_skipped_dir(d, current_dir_path / d)]

        if not filenames and not dirnames:
            if str(relative_dir_path) != '.':
                empty_folder_reports.append(
                    f"Folder: {relative_dir_path.as_posix()} (nothing implemented yet for this folder as it is empty or contains no scannable items)"
                ) # No \n here

        for filename in filenames:
            if current_depth == max_scan_depth and relative_dir_path.name == filename:
                pass
            elif current_depth >= max_scan_depth :
                if len(relative_dir_path.parts) > max_scan_depth : continue

            file_path = current_dir_path / filename
            if script_full_path and file_path.exists() and script_full_path.exists() and file_path.samefile(script_full_path):
                continue

            relative_file_path = file_path.relative_to(root_dir)
            file_ext = file_path.suffix.lower()

            should_read_content = False
            if file_path.name in FILES_WITH_CONTENT_BY_NAME:
                should_read_content = True
            elif file_ext in FILES_WITH_CONTENT_BY_EXT:
                should_read_content = True
            elif file_ext == HTML_EXTENSION:
                if not is_test_file(file_path):
                    should_read_content = True

            if should_read_content:
                content_to_store = read_file_content(file_path, max_file_size_kb)
                processed_files_for_text.append({
                    'path': str(relative_file_path.as_posix()),
                    'content': content_to_store
                })
            files_processed_count +=1

    processed_files_for_text.sort(key=lambda x: x['path'])
    empty_folder_reports.sort()

    output_parts = []
    output_parts.append(f"Project Name: {project_name}\n")
    output_parts.append(f"Project Summary:\n{final_project_summary.strip()}\n")

    if not processed_files_for_text and not empty_folder_reports:
        output_parts.append("No scannable files or empty folders found with the current criteria.\n")
    else:
        if processed_files_for_text:
            output_parts.append("-" * 40 + " FILES " + "-" * 40 + "\n")
            for item in processed_files_for_text:
                output_parts.append(f"File: {item['path']}\n")
                output_parts.append(f"{item['content'].strip()}\n")

        if empty_folder_reports:
            output_parts.append("-" * 40 + " EMPTY FOLDERS " + "-" * 40 + "\n")
            for report_text in empty_folder_reports:
                output_parts.append(report_text + "\n")

    output_parts.append(LLM_INSTRUCTIONS.strip() + "\n")

    # Join all parts and then clean up excessive newlines
    raw_output_text = "".join(output_parts)

    # Replace sequences of two or more newlines (possibly with whitespace between them) with a single newline.
    # This makes the output compact, removing all purely empty lines used for spacing.
    compact_output_text = re.sub(r'(\n\s*){2,}', '\n', raw_output_text)

    # Ensure the final text is stripped of any leading/trailing whitespace and ends with exactly one newline.
    final_output_text = compact_output_text.strip() + '\n'

    try:
        with open(output_file_str, 'w', encoding='utf-8') as f:
            f.write(final_output_text)
        print(f"Project scan (Plain Text) complete → {Path(output_file_str).resolve()}")
        print(f"Total files encountered (relevant for listing, not skipped by dir rules): {files_processed_count}")
        print(f"Files with content included: {len(processed_files_for_text)}")
        print(f"Empty folders reported: {len(empty_folder_reports)}")

    except Exception as e:
        print(f"Error writing Plain Text output to {output_file_str}: {e}")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Scan project directory and output structure/content to a plain text file.")
    parser.add_argument("root_directory", nargs='?', default=os.getcwd(),
                        help="The root directory of the project to scan (default: current directory).")
    parser.add_argument("output_filepath", nargs='?',
                        help="The path to the output text file (default: [project_name]_scan.txt in root_directory).")
    parser.add_argument("--summary", type=str, default=None,
                        help="Custom project summary string to use instead of deriving from README or default.")
    parser.add_argument("--max_kb", type=int, default=MAX_FILE_SIZE_KB_DEF,
                        help=f"Maximum file size in KB for content inclusion (default: {MAX_FILE_SIZE_KB_DEF}KB).")
    parser.add_argument("--max_depth", type=int, default=MAX_DEPTH_DEFAULT,
                        help=f"Maximum directory depth to scan (default: {MAX_DEPTH_DEFAULT}).")

    args = parser.parse_args()

    root_dir = Path(args.root_directory)
    output_filepath = args.output_filepath
    if output_filepath is None:
        output_filepath = root_dir / f"{root_dir.name}_scan.txt"
    else:
        output_filepath = Path(output_filepath)

    scan_project_to_text(
        str(root_dir),
        str(output_filepath),
        project_summary_cmd_arg=args.summary,
        max_file_size_kb=args.max_kb,
        max_scan_depth=args.max_depth
    )