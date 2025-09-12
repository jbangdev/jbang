#!/bin/bash

mvn spotless:apply

find src -name "*.java" | while read -r file; do
    echo "Processing file: $file"
    jbang run FixJBangLine1.java $file
done
