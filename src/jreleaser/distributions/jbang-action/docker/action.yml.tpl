name: 'Java Scripting w/jbang'
description: 'Run Java as scripts with https://jbang.dev from your GitHub repo'
branding:
  icon: hash
  color: red
inputs:
  jbangargs:
      description: 'Arguments for jbang'
      required: false
  script:
      description: 'Script file to run'
      required: true
  scriptargs:
      description: 'arguments to pass on to the script'
      required: false
  trust:
      description: "if present will be added to trust before running jbang"
      required: false
runs:
  using: 'docker'
  image: 'docker://ghcr.io/jbangdev/jbang-action:{{projectVersion}}'
 
