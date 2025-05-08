#!/usr/bin/env python3
"""
Project scanner optimized for LLM code analysis - JSON Output
------------------------------------------------------------

- Generates a JSON object representing the project structure.
- Includes a detailed project summary (header) and LLM instructions (footer).
- Includes essential file content (Java, SQL, Configs, etc.), skips test file content.
- Focuses on essential file types for software development.
- Truncates large files to keep the output size manageable.
"""
from __future__ import annotations
import os
import sys
import json
from pathlib import Path

# ---------------- default constants -----------------------------------------
MAX_DEPTH_DEFAULT = 15      # Max directory depth to scan (increased slightly for deep paths like db/migration)
MAX_FILE_SIZE_KB_DEF = 1024  # Size limit for file content in KB (increased slightly for potentially larger source files)
# -----------------------------------------------------------------------------

# Essential file extensions to include (prioritized)
ESSENTIAL_EXTENSIONS = {
    # Code / Source files
    '.java', '.kt', '.scala', '.groovy', # JVM
    '.js', '.ts', '.jsx', '.tsx',       # JavaScript/TypeScript
    '.py', '.rb', '.php', '.go', '.rs', '.swift', '.c', '.cpp', '.h', '.cs',
    '.sql', # SQL schema, migrations, queries

    # Build & Configuration
    '.gradle', '.properties', '.yml', '.yaml', '.json', '.xml', '.toml',

    # Web & Markup
    '.html', '.css', '.scss', '.less',
    '.md', # Markdown files like READMEs

    # Scripts
    '.sh', '.bash', '.ps1',

    # Docker & Infrastructure
    '.dockerfile', 'Dockerfile', # Dockerfiles (case-insensitive for 'Dockerfile')
    '.tf', '.tfvars',

    # Git related (often useful for context)
    '.gitignore', '.gitattributes',

    # Common data formats (if not too large)
    '.txt', '.csv'
}

# Important configuration filenames (exact match, higher priority than extension)
# These will be included even if their extension isn't in ESSENTIAL_EXTENSIONS or if they have no extension
ESSENTIAL_CONFIG_FILES = {
    'build.gradle', 'settings.gradle', 'gradle.properties', # Gradle
    'pom.xml', # Maven
    'package.json', 'package-lock.json', 'yarn.lock', 'pnpm-lock.yaml', # Node
    'tsconfig.json', 'webpack.config.js', 'babel.config.js', 'vite.config.js', '.eslintrc.json', '.prettierrc.json',
    'application.properties', 'application.yml', 'application.yaml', # Spring Boot
    'application-dev.properties', 'application-prod.properties', # Spring Boot profiles
    'bootstrap.properties', 'bootstrap.yml',
    'docker-compose.yml', 'docker-compose.yaml', 'Dockerfile',
    'pyproject.toml', 'requirements.txt',
    'README.md', 'CONTRIBUTING.MD', 'LICENSE', # Common project files (case-insensitive for README.md)
    # SQL migration files are handled by .sql extension, but if they had no extension, they could be listed here.
}

# Directories to skip (common build, tool, and large asset directories)
SKIP_DIRS = {
    # Version control
    '.git', '.svn', '.hg',
    # IDE
    '.idea', '.vscode', '.vs', 'nbproject', '.project', '.settings',
    # Build outputs
    'target', 'build', 'dist', 'out', 'bin', 'obj', 'release', 'coverage',
    # Dependencies
    'node_modules', 'vendor', 'bower_components', '.m2',
    # Python virtual environments
    'venv', '.venv', 'env', '.env', 'ENV', # Common venv names
    '__pycache__', '.pytest_cache', '.mypy_cache', '.tox',
    # Large assets / logs - be cautious if essential assets might be skipped
    'logs', 'temp', 'tmp', 'data', 'uploads',
    # 'static/assets', # If your static assets are code (e.g. JS libs not in node_modules) be careful
    # OS specific
    '.DS_Store', 'Thumbs.db',
    # Specific tools
    '.gradle', '.sass-cache', 'jekyll-cache'
}

# Test file patterns - include in structure but skip content
TEST_FILE_PATTERNS = {
    'Test.java', 'Tests.java', 'IT.java', 'Spec.java', # Java/Groovy
    '.test.js', '.spec.js', '.test.ts', '.spec.ts', # JS/TS
    'test_', '_test.py', # Python
    '_test.go', # Go
    'test.sh'
}
# Suffixes for test files (alternative to patterns)
TEST_FILE_SUFFIXES = {
    'Test.java', 'Tests.java', 'IT.java', 'Spec.java', # Java/Groovy
    'Test.kt', 'Tests.kt', 'Spec.kt', # Kotlin
    '.test.js', '.spec.js', '.test.jsx',
    '.test.ts', '.spec.ts', '.test.tsx',
    '_test.py',
    '_spec.rb',
    '_test.go',
    'Tests.cs'
}

def _add_to_nested_dict(nested_dict: dict, path_parts: list[str], content: str | dict) -> None:
    current_level = nested_dict
    for i, part in enumerate(path_parts[:-1]):
        if isinstance(current_level.get(part), str): # If a file exists with the same name as a directory
            # This indicates a potential conflict or unusual structure.
            # For now, we'll overwrite/assume it's a directory.
            # A more robust solution might involve renaming or structuring differently.
            current_level[part] = {}
        elif part not in current_level or not isinstance(current_level[part], dict):
            current_level[part] = {}
        current_level = current_level[part]
    final_part = path_parts[-1]
    current_level[final_part] = content

def is_skipped_dir(dirname: str, dir_path: Path) -> bool:
    # Skip hidden dirs by default unless specifically allowed (e.g. .github)
    if dirname.startswith('.') and dirname not in {'.github', '.gitlab-ci'}:
        return True
    return dirname in SKIP_DIRS


def is_test_file(file_path: Path) -> bool:
    filename = file_path.name
    # Check patterns (substrings)
    if any(pattern in filename for pattern in TEST_FILE_PATTERNS):
        return True
    # Check suffixes
    if any(filename.endswith(suffix) for suffix in TEST_FILE_SUFFIXES):
        return True
    # Check if 'test' is a major component of the path, e.g., src/test/java
    # Be careful not to misidentify files like "latest.txt"
    parts = [part.lower() for part in file_path.parts]
    if 'test' in parts or 'tests' in parts:
        # Further check: ensure 'src' is also in the path before 'test' to be more specific
        # e.g. src/test/java vs. mytestproject/src/main/java
        try:
            src_index = parts.index('src')
            test_index = parts.index('test') if 'test' in parts else parts.index('tests')
            if src_index < test_index:
                return True
        except ValueError: # 'src' or 'test'/'tests' not in parts
            pass
    return False


def is_essential_file(file_path: Path, script_full_path: Path | None) -> bool:
    if not file_path.is_file(): # Ensure it's actually a file
        return False
    if script_full_path and file_path.exists() and script_full_path.exists() and file_path.samefile(script_full_path):
        return False

    filename = file_path.name
    file_ext = file_path.suffix.lower()

    # Check against exact config file names (case-sensitive for some, allow common README variations)
    if filename in ESSENTIAL_CONFIG_FILES: return True
    if filename.upper() in {'README.MD', 'LICENSE', 'CONTRIBUTING.MD'}: return True # Common important files
    if filename.lower() == 'dockerfile': return True # Dockerfile often has no extension but is essential

    if file_ext in ESSENTIAL_EXTENSIONS:
        return True

    return False

def read_file_content(file_full_path: Path, max_kb: int, skip_content: bool) -> str:
    if skip_content:
        return "<Test file content deliberately skipped>"
    try:
        file_size = file_full_path.stat().st_size
        if file_size == 0:
            return "<Empty file>"
        if max_kb > 0 and file_size > max_kb * 1024:
            truncate_msg = f"\n\n... [File truncated at {max_kb}KB (original size: {file_size / 1024:.2f}KB)] ..."
            max_bytes_to_read = max_kb * 1024
        else:
            truncate_msg = ""
            max_bytes_to_read = -1 # Read all
        with open(file_full_path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read(max_bytes_to_read)
            if truncate_msg:
                last_newline = content.rfind('\n')
                if last_newline != -1: # Try to truncate at a full line
                    content = content[:last_newline]
                content += truncate_msg
            return content
    except FileNotFoundError:
        return f"<File not found: {file_full_path}>"
    except Exception as e:
        return f"<Error reading file {file_full_path}: {e}>"

def get_project_summary_from_readme_file(root_dir: Path) -> str:
    readme_filenames = ['README.md', 'README.txt', 'readme.md'] # Common README filenames
    summary = ""
    for readme_name in readme_filenames:
        readme_path = root_dir / readme_name
        if readme_path.exists() and readme_path.is_file():
            try:
                with open(readme_path, 'r', encoding='utf-8', errors='ignore') as rf:
                    lines = []
                    non_empty_lines_count = 0
                    for i, line in enumerate(rf):
                        if non_empty_lines_count >= 20:  # Read about 20 non-empty lines
                            break
                        stripped_line = line.strip()
                        if stripped_line:
                            non_empty_lines_count += 1
                        # Stop at major markdown headings (##, ###) after the first few lines if it's not the title
                        if non_empty_lines_count > 3 and (stripped_line.startswith('## ') or stripped_line.startswith('### ')):
                            break
                        lines.append(line)
                    summary = "".join(lines).strip()
                    if summary: # If we got something, return it
                        return summary
            except Exception:
                # If reading one README fails, try the next, or fall through to default.
                pass
    return summary # Return whatever was found, or empty string if nothing


def get_default_project_summary(project_name: str) -> str:
    # This is the detailed summary derived from the user's README.md.pdf
    # It will be used as a fallback if no command-line summary is given
    # and if get_project_summary_from_readme_file doesn't find a good summary.
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

def scan_project_to_json(
    root_dir_str: str,
    output_file_str: str,
    project_summary_cmd_arg: str | None = None,
    max_file_size_kb: int = MAX_FILE_SIZE_KB_DEF,
    max_scan_depth: int = MAX_DEPTH_DEFAULT,
    output_indent: int | None = 2 # Default to readable JSON, use None for compact
) -> None:
    root_dir = Path(root_dir_str).resolve()
    script_full_path = Path(__file__).resolve() if Path(__file__).is_file() else None
    project_name = root_dir.name

    # Determine project summary:
    # 1. Use command-line argument if provided.
    # 2. Else, try to parse from a local README.md file.
    # 3. Else, use the detailed hardcoded default summary.
    final_project_summary = project_summary_cmd_arg
    if not final_project_summary:
        final_project_summary = get_project_summary_from_readme_file(root_dir)
    if not final_project_summary: # If still no summary
        final_project_summary = get_default_project_summary(project_name)


    project_data = {
        "project_name": project_name,
        "project_summary": final_project_summary,
        "scan_parameters": {
            "max_depth": max_scan_depth,
            "max_file_size_kb": max_file_size_kb,
        },
        "structure": {},
        "llm_instructions": (
            "Hello LLM, I need your assistance in developing and improving my application, the \"Multi-Restaurant Platform.\" I will guide you on the current stage of development, and I expect you to act as a senior full-stack developer, leveraging your knowledge of the technologies involved and the project details I provide.\\n\\n**1. Introduction to the Multi-Restaurant Platform**\\n\\nThe \"Multi-Restaurant Platform\" is a comprehensive, Docker-containerized Spring Boot application designed to serve as a complete solution for restaurant management, online ordering, and delivery services. Its core capability is to support multiple distinct restaurants on a single platform, each with its own configurable menus, dedicated administrators, and operational settings.\\n\\n**Key Features:**\\n* **Multi-restaurant Support:** Enables onboarding and management of numerous restaurants.\\n* **User Authentication & Authorization:** Secure, role-based access control (Roles: ADMIN, RESTAURANT_ADMIN, CUSTOMER) using JWT (JSON Web Tokens).\\n* **Menu Management:** Allows restaurants to create, customize, and manage their menus, including categories and individual food/beverage items.\\n* **Order Processing System:** Facilitates handling of customer orders through various statuses from placement to delivery/completion.\\n* **Payment Integration:** Designed for payment processing, initially with a mock Stripe implementation, with plans for full Stripe integration.\\n* **WebSocket Printing System:** Enables automated, real-time printing of receipts and kitchen tickets directly from the browser.\\n* **Content Management System (CMS):** Includes a built-in CMS for managing platform-wide content.\\n* **Admin Dashboard:** Provides comprehensive tools for platform administrators to configure and manage the system.\\n\\n**Technology Stack:**\\n* **Programming Language:** Java 21\\n* **Framework:** Spring Boot 3.x (The project aims to use recent versions like 3.2.5 or higher, potentially up to 3.4.x as mentioned in project documentation)\\n* **Security:** Spring Security, JWT\\n* **Data Persistence:** Spring Data JPA\\n* **Database:** PostgreSQL (for production environments), H2 (for development and testing)\\n* **Database Migration:** Flyway\\n* **Real-time Communication:** WebSockets\\n* **Build Tool:** Gradle\\n* **Containerization:** Docker\\n* **Utilities:** Lombok\\n\\n**2. Development Roadmap and Plan**\\n\\nThis roadmap outlines the key phases and steps involved in building and enhancing the Multi-Restaurant Platform. I will inform you of the current phase and step we are working on.\\n\\n**Phase 0: Project Setup & Foundation**\\n* **Step 0.1: Local Development Environment Setup**\\n    * Install Java 21 SDK, Gradle, Docker, and an IDE (e.g., IntelliJ IDEA, Eclipse).\\n    * Set up PostgreSQL and H2 database instances.\\n    * Clone the project repository and ensure a clean build.\\n* **Step 0.2: Version Control Strategy**\\n    * Confirm Git branching strategy (e.g., Gitflow, feature branches).\\n* **Step 0.3: Project Structure Review**\\n    * Understand the multi-module Gradle setup (`backend`, `api`, `common`, `security`, `restaurant`, `menu`, `order`, `payment`, `print`, `admin`).\\n    * Review root `build.gradle` and `settings.gradle`.\\n* **Step 0.4: Initial Database Schema with Flyway**\\n    * Review/Implement initial Flyway migration scripts (e.g., `backend/api/src/main/resources/db/migration/V1_init_schema.sql`, `V2_initial_data.sql`).\\n\\n**Phase 1: Core Backend Modules - Entities, Repositories, Services, Initial APIs**\\n* **Step 1.1: `common` Module**\\n    * Define base entities, DTOs, utility classes, and exception handling.\\n* **Step 1.2: User Management & `security` Module**\\n    * Implement User entity (including roles: ADMIN, RESTAURANT_ADMIN, CUSTOMER).\\n    * Set up Spring Security configuration.\\n    * Implement JWT generation and validation services.\\n    * Develop user registration and login APIs.\\n    * Define basic role-based access controls.\\n* **Step 1.3: `restaurant` Module**\\n    * Define Restaurant entity (details, address, contact, etc.).\\n    * Implement RestaurantRepository, RestaurantService.\\n    * Develop basic CRUD APIs for restaurant management (Admin/Restaurant_Admin restricted).\\n* **Step 1.4: `menu` Module**\\n    * Define Menu, MenuCategory, MenuItem entities (linked to Restaurant).\\n    * Implement Repositories and Services for menu management.\\n    * Develop APIs for creating and managing menus (Restaurant_Admin restricted), and viewing menus (public/customer).\\n* **Step 1.5: `order` Module**\\n    * Define Order and OrderItem entities (linked to Customer, Restaurant, MenuItems).\\n    * Implement OrderRepository, OrderService (including order status management).\\n    * Develop APIs for placing orders (Customer), viewing order history (Customer, Restaurant_Admin), and managing orders (Restaurant_Admin).\\n* **Step 1.6: `payment` Module (Mock Implementation First)**\\n    * Define Payment entity (linked to Order).\\n    * Implement mock PaymentService and PaymentController.\\n    * Integrate mock payment flow into the order process.\\n* **Step 1.7: `admin` Module**\\n    * Define entities and services for platform-level administration (e.g., managing platform settings, overseeing restaurants).\\n    * Develop APIs for admin functionalities.\\n\\n**Phase 2: API Refinement & Documentation**\\n* **Step 2.1: API Design Consistency**\\n    * Ensure all API endpoints follow RESTful best practices.\\n    * Standardize request/response formats.\\n* **Step 2.2: OpenAPI/Swagger Integration**\\n    * Integrate `springdoc-openapi-starter-webmvc-ui`.\\n    * Annotate all controllers and DTOs for comprehensive API documentation.\\n    * Ensure Swagger UI (`/swagger-ui.html`) is functional and accurate.\\n\\n**Phase 3: Real-time Features & Advanced Functionality**\\n* **Step 3.1: WebSocket Printing System (`print` module)**\\n    * Configure Spring WebSockets.\\n    * Implement WebSocket endpoints for sending print jobs (receipts, kitchen tickets) to connected clients (e.g., a restaurant's printer station).\\n    * Integrate with the order module to trigger printing upon order confirmation/status changes.\\n* **Step 3.2: Full Payment Integration (Stripe)**\\n    * Replace mock payment implementation with actual Stripe API integration.\\n    * Handle payment intents, webhooks for payment status updates.\\n    * Securely manage Stripe API keys.\\n* **Step 3.3: Content Management System (CMS)**\\n    * Design and implement entities for basic CMS features (e.g., managing static pages, announcements).\\n    * Develop APIs for CMS content.\\n\\n**Phase 4: Testing & Quality Assurance**\\n* **Step 4.1: Unit Testing**\\n    * Write JUnit 5 tests for all service methods and utility classes.\\n    * Aim for high test coverage.\\n* **Step 4.2: Integration Testing**\\n    * Write integration tests for API endpoints using `spring-boot-starter-test` (e.g., `MockMvc`).\\n    * Test interactions between different modules and with the database (H2 for testing).\\n* **Step 4.3: Security Testing**\\n    * Test authentication and authorization mechanisms thoroughly.\\n    * Consider basic penetration testing.\\n\\n**Phase 5: Containerization & Deployment Preparation**\\n* **Step 5.1: Dockerfile Optimization**\\n    * Review and optimize the `Dockerfile` for multi-stage builds, image size, and security.\\n* **Step 5.2: `docker-compose.yml` Configuration**\\n    * Set up `docker-compose.yml` for local development and testing, including services like PostgreSQL.\\n    * Manage environment variables for different deployment stages.\\n* **Step 5.3: Database Migrations for Production**\\n    * Ensure Flyway migrations are robust and tested for PostgreSQL.\\n\\n**Phase 6: CI/CD (Continuous Integration/Continuous Deployment)**\\n* **Step 6.1: CI Pipeline Setup**\\n    * Configure a CI pipeline (e.g., GitHub Actions, Jenkins) to automate builds, run tests, and perform static analysis on every push/merge.\\n* **Step 6.2: CD Pipeline Setup (Optional for now)**\\n    * Plan for automated deployment to staging/production environments.\\n\\n**Phase 7: Frontend Development (Placeholder - to be detailed later)**\\n* **Step 7.1: Technology Selection** (e.g., React, Angular, Vue.js)\\n* **Step 7.2: UI/UX Design**\\n* **Step 7.3: Frontend Component Development**\\n* **Step 7.4: API Integration**\\n\\n**Phase 8: Production Deployment & Monitoring**\\n* **Step 8.1: Cloud Provider Setup / On-Premise Deployment**\\n* **Step 8.2: Logging Configuration**\\n    * Implement structured logging (e.g., Logback, SLF4j).\\n    * Set up centralized logging if applicable (e.g., ELK stack).\\n* **Step 8.3: Monitoring & Alerting**\\n    * Integrate Spring Boot Actuator for health checks and metrics.\\n    * Set up monitoring tools (e.g., Prometheus, Grafana) and alerting.\\n\\n**Phase 9: Ongoing Maintenance & Feature Enhancements**\\n* **Step 9.1: Bug Fixing and Performance Optimization.**\\n* **Step 9.2: Adding new features based on user feedback and business requirements.** (e.g., delivery tracking, user reviews, promotional offers, advanced analytics for restaurants).\\n\\n**3. Instructions for the LLM**\\n\\nTo effectively assist me, please adhere to the following:\\n* **Contextual Awareness:** I will provide you with a JSON file (`multi-restaurant-platform_scan.json`) that contains a snapshot of the project's structure, key file contents, and a summary. Please consider this your primary source of truth for the project's current state and architecture.\\n* **Current Step Focus:** I will specify the current phase and step from the roadmap above that we are working on. Please focus your advice and code generation on this specific step.\\n* **Code Generation:** When providing code examples, please ensure they are consistent with Java 21, Spring Boot 3.x, and the other technologies listed in the tech stack. Refer to existing code patterns in the provided JSON if available.\\n* **Best Practices:** Offer advice based on industry best practices, security considerations, and performance optimization.\\n* **Clarity and Explanation:** Explain your suggestions and code clearly, especially the reasoning behind architectural decisions or complex logic.\\n* **Iterative Development:** We will work iteratively. I may ask for refinements or alternative solutions.\\n\\nBy following these guidelines, we can have a productive and efficient collaboration."
        )
    }

    file_counter = 0
    for dir_path_str, dirnames, filenames in os.walk(root_dir, topdown=True):
        current_dir_path = Path(dir_path_str)
        relative_dir_path = current_dir_path.relative_to(root_dir)
        current_depth = len(relative_dir_path.parts)

        # Prune directories based on depth BEFORE further processing
        if current_depth >= max_scan_depth: # >= because depth 0 is root. max_depth 0 means only root files.
            dirnames[:] = []  # Don't go deeper into subdirectories
            if current_depth > max_scan_depth: # If we are already beyond max_depth for files in this dir
                 continue

        # Filter dirnames in place to prevent walking into skipped dirs
        dirnames[:] = [d for d in dirnames if not is_skipped_dir(d, current_dir_path / d)]

        for filename in filenames:
            file_path = current_dir_path / filename
            if not is_essential_file(file_path, script_full_path):
                continue

            relative_file_path_str = str(file_path.relative_to(root_dir))
            path_parts = list(Path(relative_file_path_str).parts)

            should_skip_content = is_test_file(file_path)
            content = read_file_content(file_path, max_file_size_kb, should_skip_content)

            # Optionally, skip adding file if content is a placeholder for error/empty
            # if content.startswith("<") and content.endswith(">") and content != "<Empty file>" and not should_skip_content :
            #     print(f"Skipping file due to read error/placeholder: {relative_file_path_str}")
            #     continue

            _add_to_nested_dict(project_data["structure"], path_parts, content)
            file_counter +=1

    print(f"Scan complete. Processed {file_counter} essential files.")
    try:
        with open(output_file_str, 'w', encoding='utf-8') as f:
            json.dump(project_data, f, indent=output_indent)
        print(f"Project scan (JSON) complete → {Path(output_file_str).resolve()}")
    except Exception as e:
        print(f"Error writing JSON output to {output_file_str}: {e}")

if __name__ == "__main__":
    args = sys.argv[1:]
    root_directory = args[0] if len(args) > 0 else os.getcwd()
    output_filepath = args[1] if len(args) > 1 else os.path.join(root_directory, f"{Path(root_directory).name}_scan.json")

    # Project summary from command line (optional)
    project_summary_cli_arg = args[2] if len(args) > 2 else None

    default_kb_arg = MAX_FILE_SIZE_KB_DEF
    default_depth_arg = MAX_DEPTH_DEFAULT

    try:
        max_kb_arg = int(args[3]) if len(args) > 3 else default_kb_arg
    except (IndexError, ValueError):
        if len(args) > 3 : print(f"Warning: Invalid value for max_kb '{args[3]}'. Using default: {default_kb_arg}KB")
        max_kb_arg = default_kb_arg

    try:
        max_depth_arg = int(args[4]) if len(args) > 4 else default_depth_arg
    except (IndexError, ValueError):
        if len(args) > 4 : print(f"Warning: Invalid value for max_depth '{args[4]}'. Using default: {default_depth_arg}")
        max_depth_arg = default_depth_arg

    # Default to human-readable JSON. Change to None for most compact output for LLM.
    json_indent_setting = 2 # or 4 for more spacing, or None for compact

    scan_project_to_json(
        root_directory,
        output_filepath,
        project_summary_cmd_arg=project_summary_cli_arg,
        max_file_size_kb=max_kb_arg,
        max_scan_depth=max_depth_arg,
        output_indent=json_indent_setting
    )