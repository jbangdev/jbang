///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.apache.commons:commons-lang3:3.12.0

import org.apache.commons.lang3.StringUtils;

System.out.println(StringUtils.center("Hello World", 20));
