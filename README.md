Google DoubleClick Libraries
----------------------------------------------------------------------

This library supports RTB development for DoubleClick Ad Exchange
in Java. The doubleclick-core module includes DoubleClick's RTB model
and associated facilities such as crypto and metadata.  The second
module, doubleclick-openrtb, provides client-side mapping between
DoubleClick's model/protocol and OpenRTB, and validation support.


BUILDING NOTES
----------------------------------------------------------------------

You need: JDK 7, Maven 3.2, Protocol buffers (protoc) 2.5.0.
Building is supported from the command line with Maven and
from any IDE that can load Maven projects (on Eclipse, use m2e).

Before building this project, you may need to check out, build, and
install in your local Maven repository the following dependency:
[OpenRTB Library][]. This will be published in maven-central later.
Necessary at runtime only for users of doubleclick-openrtb.
You need to check it out in a sibling directory ../openrtb
so relative protoc imports will work.

[OpenRTB Library]: https://github.com/google/openrtb

Recommended to run 'mvn clean install' after checkout, this is
important for the code generation steps that may not be performed
by some IDEs (Eclipse/m2e in particular).

Building via Maven will NOT work with JDK 8, because the projects
use error-prone which is not yet JDK 8-compatible.  You can work
around this by defining the property m2e.version to any value
(error-prone doesn't play well with m2e either, and we cannot use
a proper profile rule for <jdk>!1.8</jdk> because, you guessed,
this also breaks m2e). JDK 8 support is coming soon for error-prone
so this hack for non-Eclipse builds should be temporary.


RELEASE NOTES
----------------------------------------------------------------------

# Version 0.6, Jul 2014

* Initial Open Source release.
