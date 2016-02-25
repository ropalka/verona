# Verona Project Verifier

Identifies usage of potentially problematic system properties.

See [JEP223](http://openjdk.java.net/projects/verona/) for more information.

Relies on Java 8 and below class file format.

Building
-------------------

> mvn install

Runing
---

> cd target/

> java -cp verona-1.0.0.Beta1-SNAPSHOT.jar org.jboss.verona.Main \[ classFile | jarArchive | jarsDir \]+

License
-------
* [GNU Lesser General Public License Version 2.1](http://www.gnu.org/licenses/lgpl-2.1-standalone.html)

