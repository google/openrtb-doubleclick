Google DoubleClick Libraries
----------------------------------------------------------------------

This library supports RTB development for DoubleClick Ad Exchange
in Java. The doubleclick-core module includes DoubleClick's RTB model
and associated facilities such as crypto and metadata.  The second
module, doubleclick-openrtb, provides client-side mapping between
DoubleClick's model/protocol and OpenRTB, and validation support.

See our [wiki](https://github.com/google/openrtb-doubleclick/wiki)
to get started! Use the Github issue tracker for bugs, RFEs or any
support. Check the [changelog](CHANGELOG.md) for detailed release notes.


WHEN TO ARCHIVE THE REPO
----------------------------------------------------------------------

This fork exists only because [the original google extension protobuf](https://github.com/google/openrtb-doubleclick/blob/ff94fb97c134f4b8c7f153a1f31f3daabd590087/doubleclick-openrtb/src/main/protobuf/openrtb-adx.proto) doesn't support the `SourceExt.SupplyChain` extension.

As soon as it's not the case (hopefully it's the upcoming 2.0.2 version), please use the original library and archive this repo.


BUILDING NOTES
----------------------------------------------------------------------

You need: JDK 8, Maven 3.2, Protocol buffers (protoc) 3.5.1.
Building is supported from the command line with Maven and
from any IDE that can load Maven projects.

On Eclipse, the latest m2e is recommended but it can't run the code
generation step, so you need to run a "mvn install" from the command
line after checkout or after any mvn clean.


ARTIFACT NOTES
----------------------------------------------------------------------

Currently, there is no CI/CD pipeline for this fork as it hopefully won't change until very short period of time when it is removed.
Please, publish the artifact manually.