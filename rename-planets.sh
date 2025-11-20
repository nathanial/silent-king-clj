#!/bin/bash

# Script to rename planet files in assets/planets with UUIDs
# Can be re-run to rename only new files (those without UUID names)

PLANETS_DIR="assets/planets"

# Check if directory exists
if [ ! -d "$PLANETS_DIR" ]; then
    echo "Error: Directory $PLANETS_DIR does not exist"
    exit 1
fi

# UUID regex pattern (8-4-4-4-12 hexadecimal format)
UUID_PATTERN='^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'

renamed_count=0
skipped_count=0

echo "Processing files in $PLANETS_DIR..."
echo ""

# Process each file in the directory
for file in "$PLANETS_DIR"/*; do
    # Skip if not a file
    if [ ! -f "$file" ]; then
        continue
    fi

    # Get filename without path and extension
    filename=$(basename "$file")
    extension="${filename##*.}"
    basename_no_ext="${filename%.*}"

    # Only process PNGs
    if [ "$extension" != "png" ] && [ "$extension" != "PNG" ]; then
        echo "⏭️  Skipping (not PNG): $filename"
        ((skipped_count++))
        continue
    fi

    # Check if filename (without extension) matches UUID pattern
    if [[ $basename_no_ext =~ $UUID_PATTERN ]]; then
        echo "⏭️  Skipping (already UUID): $filename"
        ((skipped_count++))
    else
        # Generate a new UUID (lowercase)
        new_uuid=$(uuidgen | tr '[:upper:]' '[:lower:]')
        new_filename="$new_uuid.$extension"
        new_path="$PLANETS_DIR/$new_filename"

        # Rename the file
        mv "$file" "$new_path"
        echo "✅ Renamed: $filename → $new_filename"
        ((renamed_count++))
    fi
done

echo ""
echo "Summary:"
echo "  Renamed: $renamed_count files"
echo "  Skipped: $skipped_count files (already UUID or non-PNG)"

