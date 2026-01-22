#!/bin/bash
# =============================================================================
# spawn-project.sh - SaaS Factory Template Spawning Script
# =============================================================================
# Creates a new SaaS project from this template with proper naming and config.
#
# Usage:
#   ./scripts/spawn-project.sh <project-name> <destination-path> [options]
#
# Example:
#   ./scripts/spawn-project.sh imagekit ~/projects/imagekit
#   ./scripts/spawn-project.sh my-crm /tmp/my-crm --package com.acme
#
# Options:
#   --package <base-package>  Base Java package (default: com.<project-name>)
#   --skip-git               Don't initialize git repository
#   --dry-run                Show what would be done without making changes
#
# Author: SaaS Factory Template
# Version: 1.0.0
# =============================================================================

set -euo pipefail

# =============================================================================
# CONFIGURATION
# =============================================================================

# Colors for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m' # No Color

# Source template values (what we're replacing FROM)
readonly SOURCE_PACKAGE="com.learning"
readonly SOURCE_PROJECT_NAME="cloud-infra"       # Used in SSM paths: /cloud-infra/dev/...
readonly SOURCE_PROJECT_DISPLAY="AWS-Infra"      # Used in README and display
readonly SOURCE_ARTIFACT_NAME="AWS-Infra"        # Main pom.xml artifactId
readonly SOURCE_GROUP_ID="com.learning"
readonly SOURCE_ARTIFACT_PREFIX="aws-infra"      # Service jar names

# Directories to exclude from copy
readonly EXCLUDE_DIRS=(
    ".git"
    "node_modules"
    "target"
    "dist"
    ".terraform"
    "*.tfstate"
    "*.tfstate.backup"
    ".idea"
    "*.iml"
    ".vscode"
    "__pycache__"
    ".DS_Store"
    "*.log"
    "tfplan"
)

# =============================================================================
# HELPER FUNCTIONS
# =============================================================================

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "\n${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${CYAN}▶ $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

log_substep() {
    echo -e "  ${CYAN}→${NC} $1"
}

show_usage() {
    cat << EOF
${CYAN}SaaS Factory Template Spawning Script${NC}

${YELLOW}Usage:${NC}
    $0 <project-name> <destination-path> [options]

${YELLOW}Arguments:${NC}
    project-name       Name of new project (lowercase, alphanumeric, hyphens allowed)
    destination-path   Directory where new project will be created

${YELLOW}Options:${NC}
    --skip-git         Don't initialize git repository
    --dry-run          Show what would be done without making changes
    --help, -h         Show this help message

${YELLOW}Examples:${NC}
    $0 imagekit ~/projects/imagekit
    $0 my-crm /tmp/my-crm
    $0 pure-dam ./pure-dam --skip-git

${YELLOW}What it does:${NC}
    1. Copies template files (excluding build artifacts, node_modules, .git)
    2. Updates Maven artifact names in pom.xml files
    3. Configures Terraform variables for new project
    4. Updates Angular configuration
    5. Initializes a fresh git repository

${YELLOW}Note:${NC}
    Java packages remain as 'com.learning'. Use IDE refactoring if you want
    to change them (IntelliJ: Refactor → Rename Package).

EOF
}

validate_project_name() {
    local name="$1"
    
    # Check if empty
    if [[ -z "$name" ]]; then
        log_error "Project name cannot be empty"
        return 1
    fi
    
    # Check format (lowercase, alphanumeric, hyphens)
    if [[ ! "$name" =~ ^[a-z][a-z0-9-]*[a-z0-9]$ ]] && [[ ! "$name" =~ ^[a-z]$ ]]; then
        log_error "Project name must be lowercase, start with a letter, and contain only letters, numbers, and hyphens"
        return 1
    fi
    
    # Check length
    if [[ ${#name} -gt 50 ]]; then
        log_error "Project name must be 50 characters or less"
        return 1
    fi
    
    return 0
}

validate_destination() {
    local dest="$1"
    
    # Check if path already exists
    if [[ -e "$dest" ]]; then
        log_error "Destination path already exists: $dest"
        return 1
    fi
    
    # Check if parent directory exists
    local parent_dir
    parent_dir=$(dirname "$dest")
    if [[ ! -d "$parent_dir" ]]; then
        log_error "Parent directory does not exist: $parent_dir"
        return 1
    fi
    
    # Check if parent is writable
    if [[ ! -w "$parent_dir" ]]; then
        log_error "Parent directory is not writable: $parent_dir"
        return 1
    fi
    
    return 0
}

# Convert project name to various formats
to_pascal_case() {
    # Convert hyphen-separated to PascalCase (e.g., my-project → MyProject)
    echo "$1" | awk -F'-' '{
        for(i=1; i<=NF; i++) {
            $i = toupper(substr($i,1,1)) substr($i,2)
        }
        printf "%s", $1
        for(i=2; i<=NF; i++) printf "%s", $i
    }'
}

to_camel_case() {
    local pascal
    pascal=$(to_pascal_case "$1")
    echo "${pascal,}"
}

to_snake_case() {
    echo "$1" | tr '-' '_'
}

to_package_name() {
    # Remove hyphens for package name
    echo "$1" | tr -d '-'
}

# =============================================================================
# MAIN FUNCTIONS
# =============================================================================

copy_template_files() {
    local source_dir="$1"
    local dest_dir="$2"
    
    log_step "Copying template files"
    
    # Build rsync exclude patterns
    local exclude_args=()
    for pattern in "${EXCLUDE_DIRS[@]}"; do
        exclude_args+=(--exclude="$pattern")
    done
    
    # Copy files using rsync
    if [[ "$DRY_RUN" == "true" ]]; then
        log_substep "[DRY-RUN] Would copy files from $source_dir to $dest_dir"
        # Use subshell to avoid SIGPIPE from head causing script to fail
        (rsync -av --dry-run "${exclude_args[@]}" "$source_dir/" "$dest_dir/" 2>/dev/null || true) | head -30
        echo "  ... (truncated, use actual run to copy all files)"
    else
        mkdir -p "$dest_dir"
        rsync -av "${exclude_args[@]}" "$source_dir/" "$dest_dir/"
        log_info "Copied template files to $dest_dir"
    fi
}

update_pom_files() {
    local dest_dir="$1"
    local new_project="$2"
    
    log_step "Updating Maven POM files"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_substep "[DRY-RUN] Would update POM files with new project name: $new_project"
        log_info "POM files would be updated (groupId kept as com.learning)"
        return
    fi
    
    local new_project_pascal
    new_project_pascal=$(to_pascal_case "$new_project")
    
    local pom_files
    pom_files=$(find "$dest_dir" -name "pom.xml" -type f)
    
    for pom_file in $pom_files; do
        log_substep "Processing: ${pom_file#$dest_dir/}"
        
        # NOTE: We keep groupId as com.learning (users can refactor via IDE)
        # Only update project-specific references
        
        # Update main artifact name (AWS-Infra → NewProject)
        sed -i '' "s|${SOURCE_ARTIFACT_NAME}|${new_project_pascal}|g" "$pom_file" 2>/dev/null || \
        sed -i "s|${SOURCE_ARTIFACT_NAME}|${new_project_pascal}|g" "$pom_file"
        
        # Update project name references (cloud-infra → new-project)
        sed -i '' "s|${SOURCE_PROJECT_NAME}|${new_project}|g" "$pom_file" 2>/dev/null || \
        sed -i "s|${SOURCE_PROJECT_NAME}|${new_project}|g" "$pom_file"
        
        # Update artifact prefix (aws-infra → new-project)
        sed -i '' "s|${SOURCE_ARTIFACT_PREFIX}|${new_project}|g" "$pom_file" 2>/dev/null || \
        sed -i "s|${SOURCE_ARTIFACT_PREFIX}|${new_project}|g" "$pom_file"
    done
    
    log_info "Updated $(echo "$pom_files" | wc -l | tr -d ' ') POM files (groupId kept as com.learning)"
}

update_java_packages() {
    local dest_dir="$1"
    local new_package="$2"
    
    log_step "Java packages"
    
    # NOTE: We intentionally do NOT rename Java packages or directories.
    # The package structure (com.learning) is kept as-is.
    # Users can easily refactor using IDE tools (IntelliJ: Refactor → Rename Package)
    # which is more reliable than sed-based replacements.
    
    log_info "Java packages kept as com.learning (use IDE refactoring if needed)"
}

update_terraform_config() {
    local dest_dir="$1"
    local new_project="$2"
    
    log_step "Updating Terraform configuration"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_substep "[DRY-RUN] Would update Terraform config with project: $new_project"
        log_info "Terraform configuration would be updated"
        return
    fi
    
    local tf_dir="$dest_dir/terraform"
    
    # Update terraform.tfvars
    local tfvars_file="$tf_dir/terraform.tfvars"
    if [[ -f "$tfvars_file" ]]; then
        if [[ "$DRY_RUN" == "true" ]]; then
            log_substep "[DRY-RUN] Would update project_name in $tfvars_file"
        else
            sed -i '' "s|project_name.*=.*\"${SOURCE_PROJECT_NAME}\"|project_name = \"${new_project}\"|g" "$tfvars_file" 2>/dev/null || \
            sed -i "s|project_name.*=.*\"${SOURCE_PROJECT_NAME}\"|project_name = \"${new_project}\"|g" "$tfvars_file"
            log_substep "Updated terraform.tfvars"
        fi
    fi
    
    # Update variables.tf (default value for project_name)
    local vars_tf="$tf_dir/variables.tf"
    if [[ -f "$vars_tf" ]]; then
        if [[ "$DRY_RUN" == "true" ]]; then
            log_substep "[DRY-RUN] Would update default project_name in $vars_tf"
        else
            sed -i '' "s|default.*=.*\"${SOURCE_PROJECT_NAME}\"|default     = \"${new_project}\"|g" "$vars_tf" 2>/dev/null || \
            sed -i "s|default.*=.*\"${SOURCE_PROJECT_NAME}\"|default     = \"${new_project}\"|g" "$vars_tf"
            log_substep "Updated variables.tf"
        fi
    fi
    
    # Update main.tf (SSM parameter paths)
    local main_tf="$tf_dir/main.tf"
    if [[ -f "$main_tf" ]]; then
        if [[ "$DRY_RUN" == "true" ]]; then
            log_substep "[DRY-RUN] Would update SSM paths in main.tf"
        else
            sed -i '' "s|/${SOURCE_PROJECT_NAME}/|/${new_project}/|g" "$main_tf" 2>/dev/null || \
            sed -i "s|/${SOURCE_PROJECT_NAME}/|/${new_project}/|g" "$main_tf"
            log_substep "Updated main.tf"
        fi
    fi
    
    # Remove terraform state files (they belong to source project)
    if [[ "$DRY_RUN" != "true" ]]; then
        rm -f "$tf_dir/terraform.tfstate"*
        rm -f "$tf_dir/tfplan"
        rm -rf "$tf_dir/.terraform"
        rm -f "$tf_dir/.terraform.lock.hcl"
        log_substep "Cleaned Terraform state files"
    fi
    
    log_info "Terraform configuration updated"
}

update_angular_config() {
    local dest_dir="$1"
    local new_project="$2"
    
    log_step "Updating Angular configuration"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_substep "[DRY-RUN] Would update Angular config with project: $new_project"
        log_info "Angular configuration would be updated"
        return
    fi
    
    local new_project_display
    new_project_display=$(to_pascal_case "$new_project")
    
    local frontend_dir="$dest_dir/frontend"
    
    # Update angular.json
    local angular_json="$frontend_dir/angular.json"
    if [[ -f "$angular_json" ]]; then
        if [[ "$DRY_RUN" == "true" ]]; then
            log_substep "[DRY-RUN] Would update project name in angular.json"
        else
            sed -i '' "s|\"frontend\"|\"${new_project}\"|g" "$angular_json" 2>/dev/null || \
            sed -i "s|\"frontend\"|\"${new_project}\"|g" "$angular_json"
            log_substep "Updated angular.json"
        fi
    fi
    
    # Update package.json
    local package_json="$frontend_dir/package.json"
    if [[ -f "$package_json" ]]; then
        if [[ "$DRY_RUN" == "true" ]]; then
            log_substep "[DRY-RUN] Would update name in package.json"
        else
            sed -i '' "s|\"name\":.*\"frontend\"|\"name\": \"${new_project}-frontend\"|g" "$package_json" 2>/dev/null || \
            sed -i "s|\"name\":.*\"frontend\"|\"name\": \"${new_project}-frontend\"|g" "$package_json"
            log_substep "Updated package.json"
        fi
    fi
    
    # Update environment files
    for env_file in "$frontend_dir/src/environments/"*.ts; do
        if [[ -f "$env_file" ]]; then
            if [[ "$DRY_RUN" != "true" ]]; then
                sed -i '' "s|${SOURCE_PROJECT_DISPLAY}|${new_project_display}|g" "$env_file" 2>/dev/null || \
                sed -i "s|${SOURCE_PROJECT_DISPLAY}|${new_project_display}|g" "$env_file"
            fi
        fi
    done
    
    log_info "Angular configuration updated"
}

update_docker_compose() {
    local dest_dir="$1"
    local new_project="$2"
    
    log_step "Updating Docker Compose configuration"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_substep "[DRY-RUN] Would update docker-compose files and container names"
        log_info "Docker Compose configuration would be updated"
        return
    fi
    
    # List of container names to prefix (to avoid conflicts when running multiple projects)
    local containers=(
        "eureka-server"
        "gateway-service"
        "backend-service"
        "auth-service"
        "platform-service"
        "openfga"
        "openfga-migrate"
        "otel-collector"
        "postgres"
        "redis"
    )
    
    # Update all docker-compose files
    for compose_file in "$dest_dir"/docker-compose*.yml; do
        if [[ -f "$compose_file" ]]; then
            local basename
            basename=$(basename "$compose_file")
            
            # Update project name references
            sed -i '' "s|${SOURCE_PROJECT_NAME}|${new_project}|g" "$compose_file" 2>/dev/null || \
            sed -i "s|${SOURCE_PROJECT_NAME}|${new_project}|g" "$compose_file"
            
            # Prefix container names to avoid conflicts
            for container in "${containers[@]}"; do
                # Only prefix if not already prefixed
                sed -i '' "s|container_name: ${container}$|container_name: ${new_project}-${container}|g" "$compose_file" 2>/dev/null || \
                sed -i "s|container_name: ${container}$|container_name: ${new_project}-${container}|g" "$compose_file"
            done
            
            log_substep "Updated $basename"
        fi
    done
    
    log_info "Docker Compose configuration updated with prefixed container names"
}

update_readme() {
    local dest_dir="$1"
    local new_project="$2"
    
    log_step "Updating README files"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_substep "[DRY-RUN] Would update README.md"
        log_info "README files would be updated"
        return
    fi
    
    local new_project_display
    new_project_display=$(to_pascal_case "$new_project")
    
    # Update main README
    local readme_file="$dest_dir/README.md"
    if [[ -f "$readme_file" ]]; then
        sed -i '' "s|SaaS Factory Template|${new_project_display}|g" "$readme_file" 2>/dev/null || \
        sed -i "s|SaaS Factory Template|${new_project_display}|g" "$readme_file"
        
        sed -i '' "s|${SOURCE_PROJECT_DISPLAY}|${new_project_display}|g" "$readme_file" 2>/dev/null || \
        sed -i "s|${SOURCE_PROJECT_DISPLAY}|${new_project_display}|g" "$readme_file"
        
        log_substep "Updated README.md"
    fi
    
    log_info "README files updated"
}

update_project_config() {
    local dest_dir="$1"
    local new_project="$2"
    
    log_step "Updating project.config"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_substep "[DRY-RUN] Would update project.config"
        log_info "project.config would be updated"
        return
    fi
    
    local new_project_display
    new_project_display=$(to_pascal_case "$new_project")
    
    local config_file="$dest_dir/project.config"
    if [[ -f "$config_file" ]]; then
        # Update PROJECT_NAME
        sed -i '' "s|^PROJECT_NAME=.*|PROJECT_NAME=${new_project}|g" "$config_file" 2>/dev/null || \
        sed -i "s|^PROJECT_NAME=.*|PROJECT_NAME=${new_project}|g" "$config_file"
        
        # Update PROJECT_DISPLAY_NAME
        sed -i '' "s|^PROJECT_DISPLAY_NAME=.*|PROJECT_DISPLAY_NAME=\"${new_project_display}\"|g" "$config_file" 2>/dev/null || \
        sed -i "s|^PROJECT_DISPLAY_NAME=.*|PROJECT_DISPLAY_NAME=\"${new_project_display}\"|g" "$config_file"
        
        log_substep "Updated project.config"
    fi
    
    log_info "project.config updated"
}

update_application_yml() {
    local dest_dir="$1"
    local new_project="$2"
    
    log_step "Updating application.yml files"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_substep "[DRY-RUN] Would update SSM paths in application.yml files"
        log_info "Application YAML files would be updated"
        return
    fi
    
    local yml_files
    yml_files=$(find "$dest_dir" -name "application*.yml" -o -name "application*.yaml" 2>/dev/null | grep -v node_modules || true)
    
    for yml_file in $yml_files; do
        if [[ -f "$yml_file" ]]; then
            # Update SSM parameter paths
            sed -i '' "s|/${SOURCE_PROJECT_NAME}/|/${new_project}/|g" "$yml_file" 2>/dev/null || \
            sed -i "s|/${SOURCE_PROJECT_NAME}/|/${new_project}/|g" "$yml_file"
            
            log_substep "Updated ${yml_file#$dest_dir/}"
        fi
    done
    
    log_info "Application YAML files updated"
}

update_project_documentation() {
    local dest_dir="$1"
    local new_project="$2"
    
    log_step "Updating project documentation"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_substep "[DRY-RUN] Would update project references in documentation"
        log_info "Documentation would be updated"
        return
    fi
    
    # List of documentation files to update
    local doc_files=(
        "$dest_dir/terraform/README.md"
        "$dest_dir/scripts/README.md"
        "$dest_dir/scripts/terraform/README.md"
    )
    
    for doc_file in "${doc_files[@]}"; do
        if [[ -f "$doc_file" ]]; then
            # Replace cloud-infra with new project name
            sed -i '' "s|cloud-infra|${new_project}|g" "$doc_file" 2>/dev/null || \
            sed -i "s|cloud-infra|${new_project}|g" "$doc_file"
            log_substep "Updated ${doc_file#$dest_dir/}"
        fi
    done
    
    # Update shell scripts with project defaults
    local script_files=(
        "$dest_dir/scripts/terraform/delete-ssm.sh"
    )
    
    for script_file in "${script_files[@]}"; do
        if [[ -f "$script_file" ]]; then
            sed -i '' "s|cloud-infra|${new_project}|g" "$script_file" 2>/dev/null || \
            sed -i "s|cloud-infra|${new_project}|g" "$script_file"
            log_substep "Updated ${script_file#$dest_dir/}"
        fi
    done
    
    log_info "Project documentation updated"
}
initialize_git() {
    local dest_dir="$1"
    local new_project="$2"
    local source_dir="$3"
    
    log_step "Initializing Git repository"
    
    if [[ "$DRY_RUN" == "true" ]]; then
        log_substep "[DRY-RUN] Would initialize git repository"
        return
    fi
    
    cd "$dest_dir"
    git init
    git add .
    git commit -m "Initial commit: ${new_project} spawned from SaaS Factory Template"
    
    # Add template as upstream remote for syncing updates
    log_substep "Adding template as 'upstream' remote for future sync"
    git remote add upstream "$source_dir"
    
    log_info "Git repository initialized with upstream remote"
}

print_summary() {
    local dest_dir="$1"
    local new_project="$2"
    
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}  ✅ PROJECT SUCCESSFULLY CREATED!${NC}"
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "  ${CYAN}Project:${NC}     $new_project"
    echo -e "  ${CYAN}Location:${NC}    $dest_dir"
    echo -e "  ${CYAN}Package:${NC}     com.learning (use IDE refactoring to change)"
    echo ""
    echo -e "${YELLOW}Next Steps:${NC}"
    echo ""
    echo -e "  1. ${CYAN}Deploy AWS Infrastructure (Cognito)${NC}"
    echo -e "     cd $dest_dir/terraform"
    echo -e "     terraform init"
    echo -e "     terraform apply"
    echo ""
    echo -e "  2. ${CYAN}Start Backend Services${NC}"
    echo -e "     cd $dest_dir"
    echo -e "     docker-compose up -d"
    echo ""
    echo -e "  3. ${CYAN}Start Frontend (Development)${NC}"
    echo -e "     cd $dest_dir/frontend"
    echo -e "     npm install"
    echo -e "     npm start"
    echo ""
    echo -e "  4. ${CYAN}Create System Admin${NC}"
    echo -e "     ./scripts/bootstrap-system-admin.sh admin@example.com \"Password123!\""
    echo ""
    echo -e "  5. ${CYAN}Access the Application${NC}"
    echo -e "     http://localhost:4200"
    echo ""
    echo -e "${YELLOW}Syncing Template Updates:${NC}"
    echo ""
    echo -e "  When the template (AWS-Infra) gets bug fixes or new features:"
    echo ""
    echo -e "     cd $dest_dir"
    echo -e "     git fetch upstream"
    echo -e "     git merge upstream/main --allow-unrelated-histories"
    echo -e "     # Resolve any conflicts, then:"
    echo -e "     git push origin main"
    echo ""
    echo -e "${GREEN}═══════════════════════════════════════════════════════════════════════════${NC}"
}

# =============================================================================
# MAIN ENTRY POINT
# =============================================================================

main() {
    local project_name=""
    local dest_path=""
    local skip_git=false
    DRY_RUN=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --help|-h)
                show_usage
                exit 0
                ;;
            --skip-git)
                skip_git=true
                shift
                ;;
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            -*)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
            *)
                if [[ -z "$project_name" ]]; then
                    project_name="$1"
                elif [[ -z "$dest_path" ]]; then
                    dest_path="$1"
                else
                    log_error "Unexpected argument: $1"
                    show_usage
                    exit 1
                fi
                shift
                ;;
        esac
    done
    
    # Validate required arguments
    if [[ -z "$project_name" ]] || [[ -z "$dest_path" ]]; then
        log_error "Missing required arguments"
        show_usage
        exit 1
    fi
    
    # Validate project name
    if ! validate_project_name "$project_name"; then
        exit 1
    fi
    
    # Resolve destination to absolute path
    dest_path=$(cd "$(dirname "$dest_path")" && pwd)/$(basename "$dest_path")
    
    # Validate destination
    if ! validate_destination "$dest_path"; then
        exit 1
    fi
    
    # Get source directory (where this script lives)
    local script_dir
    script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    local source_dir
    source_dir="$(dirname "$script_dir")"
    
    # Confirmation banner
    echo ""
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo -e "${CYAN}  SaaS Factory Template - Project Spawner${NC}"
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "  ${YELLOW}Source:${NC}      $source_dir"
    echo -e "  ${YELLOW}Project:${NC}     $project_name"
    echo -e "  ${YELLOW}Destination:${NC} $dest_path"
    echo -e "  ${YELLOW}Skip Git:${NC}    $skip_git"
    echo -e "  ${YELLOW}Dry Run:${NC}     $DRY_RUN"
    echo ""
    
    if [[ "$DRY_RUN" == "true" ]]; then
        echo -e "${YELLOW}  [DRY-RUN MODE] No changes will be made${NC}"
        echo ""
    fi
    
    # Execute steps
    copy_template_files "$source_dir" "$dest_path"
    update_pom_files "$dest_path" "$project_name"
    update_java_packages "$dest_path" "$project_name"
    update_terraform_config "$dest_path" "$project_name"
    update_angular_config "$dest_path" "$project_name"
    update_docker_compose "$dest_path" "$project_name"
    update_application_yml "$dest_path" "$project_name"
    update_readme "$dest_path" "$project_name"
    update_project_config "$dest_path" "$project_name"
    update_project_documentation "$dest_path" "$project_name"
    
    if [[ "$skip_git" != "true" ]] && [[ "$DRY_RUN" != "true" ]]; then
        initialize_git "$dest_path" "$project_name" "$source_dir"
    fi
    
    if [[ "$DRY_RUN" != "true" ]]; then
        print_summary "$dest_path" "$project_name"
    else
        echo ""
        echo -e "${YELLOW}[DRY-RUN] No changes were made. Remove --dry-run to execute.${NC}"
        echo ""
    fi
}

# Run main function
main "$@"

