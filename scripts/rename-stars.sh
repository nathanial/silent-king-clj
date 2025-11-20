#!/bin/bash

# Script to rename star files in assets/stars with UUIDs
# Can be re-run to rename only new files (those without UUID names)

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cd "$SCRIPT_DIR/.."

STARS_DIR="assets/stars"

# Check if directory exists
if [ ! -d "$STARS_DIR" ]; then
    echo "Error: Directory $STARS_DIR does not exist"
    exit 1
fi

# UUID regex pattern (8-4-4-4-12 hexadecimal format)
UUID_PATTERN='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'

renamed_count=0
skipped_count=0

echo "Processing files in $STARS_DIR..."
echo ""

# Process each file in the directory
for file in "$STARS_DIR"/*; do
    # Skip if not a file
    if [ ! -f "$file" ]; then
        continue
    fi

    # Get filename without path and extension
    filename=$(basename "$file")
    extension="${filename##*.}"
    basename_no_ext="${filename%.*}"

    # Check if filename (without extension) matches UUID pattern
    if [[ $basename_no_ext =~ $UUID_PATTERN ]]; then
        echo "⏭️  Skipping (already UUID): $filename"
        ((skipped_count++))
    else
        # Generate a new UUID (lowercase)
        new_uuid=$(uuidgen | tr '[:upper:]' '[:lower:]')
        new_filename="$new_uuid.$extension"
        new_path="$STARS_DIR/$new_filename"

        # Rename the file
        mv "$file" "$new_path"
        echo "✅ Renamed: $filename → $new_filename"
        ((renamed_count++))
    fi
done

echo ""
echo "Summary:"
echo "  Renamed: $renamed_count files"
echo "  Skipped: $skipped_count files (already have UUID names)"
