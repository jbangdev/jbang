///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS https://github.com/blocoio/faker/tree/1.2.8

import io.bloco.faker.Faker;

Faker fake = new Faker("da-DK");
System.out.println("Fake output: " + fake.name.firstName());

