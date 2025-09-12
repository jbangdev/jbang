#!/bin/bash

find src -name "*.java" | while read -r file; do
    echo "Processing file: $file"
    jbang run FixCommentSpacing.java $file
done
