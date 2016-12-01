Evolver
====================================================

A library written in Scala to migrate Cassandra schemas that can be used from command line or in code/tests.

Usage
-----

Evolver uses cql scripts to run the migration. Conventions:
- all migration files should have the .cql extension
- the name of the .cql file represents the version the keyspace will get to after running this migration.
- hence the lexicographical order of the file names is also the order of the versions
- contain as many cql statements as you want per file

### Init

Let's say you have an existing Cassandra keyspace which you've been migrating manually and you want to start evolving with Evolver.

    java -jar evolver.Evolver init -h localhost -p 9042 -k test -v mig_1.0.3

or in Scala code:

    val evolver = new Evolver("localhost", 9042, "test")
    evolver.init(Some("mig_1.0.3"))

You will of course replace 'test' with your keyspace and 'localhost' and 9042 with your Cassandra host and port.
This will create a table evolver_log that will make this keyspace look like it was evolved at version 'mig_1.0.3'.
No other cql statements are run, your schema remains the same.

### Evolve

Later on, your keyspace needs to evolve to version 1.0.4.
You have a .cql file in your migration scripts folder which with the name 'mig_1.0.4.cql'

    java -jar evolver.Evolver evolve -h localhost -p 9042 -k test -f <cql_folder>

or in Scala code:

    val evolver = new Evolver("localhost", 9042, "test")
    evolver.evolve(new File ("./cqls"))

Boom. You're done. Use it in your tests.

