# k.LAB Component Geospatial

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](http://www.gnu.org/licenses/agpl-3.0)
[![Java Version](https://img.shields.io/badge/Java-21-orange)](https://openjdk.java.net/projects/jdk/21/)
[![Maven Central](https://img.shields.io/badge/Maven-1.0--SNAPSHOT-green)](https://search.maven.org/)

A k.LAB plugin component that provides adapters supporting geospatial data sources and protocols, including WCS, WFS, STAC and others, along with many geospatial algorithms.

The plugin adds the functionalities of the Horton Machine to k.LAB in the form of spatial contextualizers.

## Overview

## Installation

This component is part of the k.LAB services ecosystem. It is available on Maven Central and should be
installed into a k.LAB Resources service using the import schema for Maven components.

```xml
<dependency>
    <groupId>org.integratedmodelling</groupId>
    <artifactId>klab.component.geospatial</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Usage
## Development

### Building the Project

```bash
mvn clean install
```

### Running Tests

```bash
mvn test
```

### Numeric GeoTIFF regression

The exporter regression belongs in
`src/test/java/org/integratedmodelling/geospatial/utils/GeotiffExportTest.java`,
beside the coverage/rendering tests. Run it with:

```bash
./mvnw -Dtest=GeotiffExportTest,GeotiffStreamTest,RasterRendererTest,GeodataIOTest test
```

It independently specifies a 5×4 native XY field and checks every north-up pixel
against its world coordinate, including valid zero and one NaN. Written fixtures
cover projected/geographic CRSs, both geographic axis orders, shifted image origins,
rotation/shear, float64 precision and ASCII-safe Unicode/unit metadata. Separate
stream tests cover generation/close/delete failures and owned temporary-file cleanup.

Native grid y increases northward, while TIFF rows increase southward. The coverage
conversion must map `row = height - 1 - nativeY`. The numeric DoubleScanner exporter
writes float64, widening float inputs without changing values rather than narrowing
double values or converted views. Int64 is explicitly rejected because this scanner
contract cannot establish exact integer preservation; categorical export remains
unsupported. PNG continues to request its existing float32 rendering representation.

The writer uses GeoTools georeferencing and its TIFF metadata encoder, explicitly
retaining NaN nodata and the source `UnitImpl` definition in GDAL band-unit metadata.
Supplied units without a serializable definition fail explicitly instead of being
silently omitted; a genuinely absent measurement unit is not invented from the CRS.
Numeric XML references keep Unicode source text inside the TIFF ASCII tag. GDAL's
historical second unescape requires two escaping layers for reserved punctuation and
literal entity-looking text. Coordinate-system units are not measurement units.

For the independent written-file oracle, prepare a fresh existing evidence directory
and run:

```bash
./mvnw -Dgeotiff.evidence.dir=/absolute/existing/evidence-directory test
python -m pip install 'rasterio>=1.4,<2'
python tools/check_geotiff_reference.py /absolute/existing/evidence-directory
```

The Python reader is optional development tooling, not a component runtime dependency.
Its checks remain active with Python optimization enabled. Ordinary Java tests also
assert written tags/transforms without requiring the optional reader.

The full saved-handle/fresh-Python-process/export/Rasterio throughput test belongs
in `klab-services/python-client/tools/workflows/export_deliverables.py`, with
`tools/geotiff_throughput.py` retained as a compatibility command; its deterministic
scientific/rate guards belong in `python-client/tests/unit/test_geotiff_throughput.py`.
Current source/artifact identities, independent-reader results, live workflow
evidence and contribution-guide reviews are recorded in `docs/GEOTIFF_REVIEW.md`.
Earlier Python throughput results predate the current precision/unit corrections
and are not relabelled as validation of this revised component.

## License

This project is licensed under the GNU Affero General Public License (AGPL) version 3.0. See the [license text](http://www.gnu.org/licenses/agpl-3.0.en.html) for details.

## Contributors

- **Ferdinando Villa** - Lead Developer
    - Email: ferdinando.villa@bc3research.org
    - Organization: Basque Centre for Climate Change (BC3); IKERBASQUE
- **Iñigo Cobian** - Lead Developer
  - Email: inigo.cobian@bc3research.org
  - Organization: Basque Centre for Climate Change (BC3)

## Organization

**Integrated Modelling Partnership**  
Website: [integratedmodelling.org](http://www.integratedmodelling.org)

## Repository

- **Source Code**: [GitHub Repository](https://github.com/integratedmodelling/klab.component.generators)
- **Issue Tracking**: Use GitHub Issues for bug reports and feature requests

## Support

For questions, issues, or contributions, please visit the [k.LAB community resources](http://www.integratedmodelling.org) or create an issue in the GitHub repository.

---

*This component is part of the k.LAB semantic modeling platform for integrated assessment and environmental modeling.*
