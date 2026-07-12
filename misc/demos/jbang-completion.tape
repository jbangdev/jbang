Source style.tape

Set WindowBarTitle "jbang tab completion"
Set FontSize 20
Set Width 1400
Set Height 800
Set TypingSpeed 40ms

Require jbang

# --- Setup (hidden) ---
Hide
Type "source (jbang completion -s fish | psub)"
Enter
Sleep 1s
Type "cd /tmp/jbang-demo"
Enter
Sleep 1s
Type "clear"
Enter
Sleep 500ms
Show

# ══════════════════════════════════════════════════════
# PART 1: Subcommand Completion
# ══════════════════════════════════════════════════════
Sleep 500ms
Type "# Subcommand completion"
Enter
Sleep 800ms

# Top-level commands
Type "jbang "
Sleep 400ms
Tab
Sleep 3s
Ctrl+C
Sleep 300ms

# Nested subcommands
Type "jbang export "
Sleep 400ms
Tab
Sleep 3s
Ctrl+C
Sleep 500ms

# ══════════════════════════════════════════════════════
# PART 2: JBang App Completion
# ══════════════════════════════════════════════════════
Type "# App completion — files, aliases, directories, Maven GAV, catalogs, GitHub"
Enter
Sleep 800ms

# Show everything: files + aliases + hints
Type "jbang run "
Sleep 400ms
Tab
Sleep 3s
Ctrl+C
Sleep 300ms

# File + directory navigation
Type "jbang run src/"
Sleep 400ms
Tab
Sleep 1s
Tab
Sleep 2s
Ctrl+C
Sleep 300ms

# Alias narrowing
Type "jbang run my"
Sleep 400ms
Tab
Sleep 2s
Ctrl+C
Sleep 300ms

# Catalog browsing
Type "jbang run @"
Sleep 400ms
Tab
Sleep 2s
Ctrl+C
Sleep 200ms
Type "jbang run @jbanghub/"
Sleep 400ms
Tab
Sleep 3s
Ctrl+C
Sleep 300ms

# Maven GAV — sqlline (a runnable SQL CLI)
Type "jbang run sqlline:"
Sleep 400ms
Tab
Sleep 3s
Ctrl+C
Sleep 200ms
Type "jbang run sqlline:sqlline:"
Sleep 400ms
Tab
Sleep 3s
Ctrl+C
Sleep 300ms

# GitHub URL browsing — zlip
Type "jbang run https://github.com/maxandersen/zlip/blob/HEAD/"
Sleep 400ms
Tab
Sleep 5s
Ctrl+C
Sleep 1s
