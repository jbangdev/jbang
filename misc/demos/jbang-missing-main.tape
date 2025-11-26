Source style.tape

Set WindowBarTitle "jbang auto-detects main class if not specified"
Require jbang
Type "jbang org.jbox2d:jbox2d-testbed:2.2.1.1" Enter
Sleep 2s

Enter
Enter

Type "jbang -m org.jbox2d.testbed.framework.TestbedMain \" Enter
Type "org.jbox2d:jbox2d-testbed:2.2.1.1"
Sleep 3s