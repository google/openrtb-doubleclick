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
a proper profile rule for `<jdk>!1.8</jdk>` because, you guessed,
this also breaks m2e). JDK 8 support is coming soon for error-prone
so this hack for non-Eclipse builds should be temporary.


RELEASE NOTES
----------------------------------------------------------------------

# Version 0.6.3, 02-08-2014

* Update DoubleClick protocol to v51.
* Map User.gender/age from new UserDemographics data.
* Decrypt HyperlocalSet, keep in a link extension, and map Geo.lat/lon.
* Map Video.mimes and Video.companionad.mimes.
* DoubleClickCrypto: IDFA/Hyperlocal now correct; big general review.
* Fixed mapping of price and bidfloor properties to use currency units
  (which is the standard). Previous code used micros, that was a legacy
  from this code's DoubleClick roots, but was not OpenRTB-compliant.

# Version 0.6.2, 25-07-2014

* DoubleClickCrypto: optimize memory usage in Base64 encoding/decoding;
  some initial fixes for IDFA/Hyperlocal but still broken in some cases.
* Remove dependency on buggy, unsupported opencsv; using custom parser.
* DoubleClickOpenRtbMapper: Fix semi-transparent branded channels.

# Version 0.6.1, 15-07-2014

* Remove depedency from Guice! The libraries still supports all
  JSR-305 DI frameworks, but now only uses javax.inject.
* DoubleClick protocol v50; map app.content.userrating, app.name.
* Build system improvements (Maven, Eclipse, NetBeans).
* Improved OpenRtbSnippetProcessor handling of macro dependencies;
  see new documentation about this in OpenRtbMacros.

# Version 0.6, 10-07-2014

* Initial Open Source release.
