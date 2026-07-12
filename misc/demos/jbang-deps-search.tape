Source style.tape

Set WindowBarTitle "jbang deps search"
Set FontSize 18
Set Width 1400
Set Height 800
Set TypingSpeed 50ms

Require jbang

# Launch the TUI
Type "jbang deps search"
Enter
Sleep 3s

# --- Scene 1: Fuzzy search local artifacts ---
Type@80ms "gson"
Sleep 2s

# --- Scene 2: Navigate results and select version ---
Down 3
Sleep 500ms
Up 3
Sleep 500ms

Enter
Sleep 2s

# Navigate versions
Down 2
Sleep 1s

# Back to results
Escape
Sleep 1s

# --- Scene 3: Tab to search Maven Central ---
Tab
Sleep 3s

# --- Scene 4: Clear and paste a Java import ---
Backspace 4
Sleep 300ms
Type@80ms "import com.fasterxml.jackson.databind.ObjectMapper"
Sleep 3s

# Tab to search Central for the FQCN
Tab
Sleep 3s

# Select jackson-databind and show versions
Enter
Sleep 2s

# Exit
Escape
Sleep 500ms
Escape
Sleep 1s
