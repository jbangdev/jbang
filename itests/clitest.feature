Feature:

Background:
* def command =
"""
function(line) {
  var proc = karate.fork({ redirectErrorStream: false, useShell: true, line: line });
  proc.waitSync();
  karate.set('out', proc.sysOut);
  karate.set('err', proc.sysErr);
  karate.set('exit', proc.exitCode);
}
"""

Scenario: jbang version
* command('jbang version')
* match out == '#regex (?s)\\d+\\.\\d+\\.\\d+(\\.\\d+)?.*'
* match exit == 0

Scenario: should fail on missing file
* command('jbang notthere.java')
* match out contains 'Could not read script argument notthere.java'
* match exit == 1