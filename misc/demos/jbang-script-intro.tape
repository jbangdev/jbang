Source style.tape

# Show intro message
Type "# Lets create a directory with a java example made with jbang"
Enter
Sleep 500ms

# Create directory and navigate to it
Hide
Type "mkdir example; cd example"
Enter
Show

# Initialize a hello.java template
Type "jbang init hello.java"
Enter
Sleep 1200ms

# Run with jbang
Type "# Now we run it using jbang"
Enter
Type "jbang hello.java"
Enter
Sleep 1200ms

# Run file directly
Type "# In a bash or zsh shell you run the file directly"
Enter
Type "./hello.java"
Enter
Sleep 1200ms

# Third party dependencies example
Type "# That was the basics - now lets create an example using 3rd party dependencies"
Enter
Type "jbang init -t cli hellocli.java"
Enter
Sleep 1200ms

# Run with help
Type "# This creates a hello world using picocli and gives you built in help"
Enter
Type "./hellocli.java --help"
Enter
Sleep 1200ms

# Run with arguments
Type "./hellocli.java JBANG!"
Enter
Sleep 1200ms

Type "# That is the basics of jbang!"
Enter
Type "# Download from https://jbang.dev runs in Windows, Linux, OSX, Containers, GitHub actions and more..."
Enter
Sleep 1500ms

# Clean up
Hide
Type "cd .. && rm -rf example"
Enter

# VHS SCRIPT END