#!/bin/bash

# Script to create package directories for a given Java file path within the standard Maven/Gradle layout
# Usage: ./create_package.sh <full_path_to_java_file>
# Example: ./create_package.sh backend/security/src/main/java/com/multirestaurantplatform/security/repository/UserRepository.java

if [ -z "$1" ]; then
  echo "Usage: $0 <full_path_to_java_file>"
  echo "Example: $0 backend/security/src/main/java/com/yourcompany/yourmodule/YourClass.java"
  exit 1
fi

FILE_PATH="$1"

# Extract the directory path from the file path
DIR_PATH=$(dirname "$FILE_PATH")

# Check if the directory path is valid (contains src/main/java or src/test/java)
if [[ "$DIR_PATH" != *"src/main/java"* ]] && [[ "$DIR_PATH" != *"src/test/java"* ]]; then
  echo "Error: Path does not seem to be a standard source directory (expecting 'src/main/java' or 'src/test/java')."
  echo "Provided path: $FILE_PATH"
  exit 1
fi

# Create the directory path including intermediate directories (-p)
mkdir -p "$DIR_PATH"

if [ $? -eq 0 ]; then
  echo "Directory structure ensured: $DIR_PATH"
else
  echo "Error creating directory structure: $DIR_PATH"
  exit 1
fi

exit 0