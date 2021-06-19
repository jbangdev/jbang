#!/usr/bin/env bash

########################
# include the magic
########################
. ./demo-magic.sh


########################
# Configure the options
########################

#
# speed at which to simulate typing. bigger num = faster
#
TYPE_SPEED=40

#
# custom prompt
#
# see http://www.tldp.org/HOWTO/Bash-Prompt-HOWTO/bash-prompt-escape-sequences.html for escape sequences
#
#DEMO_PROMPT="$ "
DEMO_CMD_COLOR=$WHITE
DEMO_COMMENT_COLOR=$GREEN

# text color

# hide the evidence
clear

pei "# Lets create a directory with a java example made with jbang"
pei "mkdir example; cd example"
pei "jbang init hello.java"
pei "# Now we run it using jbang"
pei "jbang hello.java"
pei "# In a bash or zsh shell you run the file directly"
pei "./hello.java"
pei "# That was the basics - now lets create an example using 3rd party dependencies"
pei "jbang init -t cli hellocli.java"
pei "# This creates a hello world using picocli and gives you built in help"
pei "./hellocli.java --help"
pei "./hellocli.java JBANG!"
pei "# Lets now edit it in IDE of choice - in this case vim"
p "vim \`jbang edit hellocli.java\`"
vim `jbang edit hellocli.java`
pei "./hellocli.java JBang with a longer command line"
pei "# That is the basics of jbang!"
pei "# Download from https://jbang.dev runs in Windows, Linux, OSX, Containers, GitHub actions and more..."
# run command behind
cd .. && rm -rf example

# show a prompt so as not to reveal our true nature after
# the demo has concluded
# p ""