#!/bin/bash

export GJF_JAR=com.google.googlejavaformat:google-java-format:1.28.0

find . -name "*.java" | while read -r file; do
    echo "Processing file: $file"
    jbang run $GJF_JAR --aosp -r $file
    jbang run FixCommentSpacing.java $file
done
