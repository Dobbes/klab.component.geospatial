# Native GeoTIFF correction and review record

## Scope and reviewed state

- Repository: `integratedmodelling/klab.component.geospatial`.
- New branch: `fix/geotiff-scientific-export`.
- Integration target/base: `main`, `8d4ef8665016761eb12a504a304f02eb3b55f1c7`.
- Authorized local changes preserved from `fix/geotiff-orientation-metadata`:
  README, Geotools coverage conversion, GeodataIO export/lifecycle, new native writer
  and scientific regressions. The review checkpoint was local and uncommitted;
  the user subsequently authorized publication as a component PR and merge.
- User review guide: `C:\Users\lumsd\Downloads\KLAB_CLI_Contribution_Review.md`.
  Inferred maintainer preferences are not official policy or acceptance guarantees.

The user requested a fresh branch and iterative independent review/fixes until ready.

## Capability and boundaries

Native numeric GeoTIFF associates k.LAB's north-increasing grid y with north-up
image rows, retains valid zero and NaN nodata/mask, preserves supplied source units
and avoids float64 narrowing. GeoTools still defines CRS/affine encoding; native
bytes are generated server-side, without Python-side flips or scientific re-encoding.
This is a single-raster DoubleScanner exporter, not new window/multishard/temporal
or categorical protocols. Int64 exactness requires a separate integer scanner
contract and is explicitly rejected. Float64 is the output representation even
when the input storage is float32; values are widened, not rounded down.

## Review iteration 1 — Needs fixes

An independent read-only reviewer inspected the new writer against GeoTools 34.4,
ImageIO-Ext 2.0.2 and GDAL source contracts. Findings:

1. **Important, reproduced unit defect:** TIFF ASCII serialization truncates Unicode
   characters; the existing ASCII-only test missed this. Unicode/superscript source
   text was changed on serialization. Numeric XML references now preserve it.
2. **Written-georeferencing evidence gap:** coverage coordinates alone did not prove
   serialized CRS/affine behavior. Actual written cases now cover geographic axis
   orders, shifted image origin and rotated/sheared affine transforms, read by the
   independent Rasterio/GDAL consumer.
3. **GDAL escaping contract, resolved by reproduction:** GDAL unescapes a second
   time after XML parsing. Single escaping truncated source units at a bare `&`.
   The writer now follows GDAL's documented double-escaping convention for reserved
   punctuation; Unicode numeric references remain in the outer XML layer. Ordinary
   DOM tests check the specified intermediate value; Rasterio checks exact final
   unit strings, including literal `&amp;`, decimal/hex entity-looking text.

Additional implementation review reproduced float64 values being exported as
float32. Numeric conversion now retains doubles and the DoubleScanner exporter
writes float64. The `16777217.125` regression checks a value not representable in
float32, with valid zero and a missing cell. Int64 is rejected before scanner reads.

Owned stream cleanup now attempts deletion after close failure and suppresses a
secondary delete failure onto the primary IOException. Generation failures remove
the temporary file; no empty successful stream is returned for a null export.

## Current verification

Current candidate checks are local dirty-tree evidence, with LF-normalized Java
source hashes and exact command vectors in the external test records. No older
component build/live result is credited to these revised sources.

- `mvnw.cmd -B -ntp -o -Dgeotiff.evidence.dir=<fresh-external-dir> test`:
  **22 passed**, zero failures/errors/skips in `geotiff-tests-final1/maven.log`.
  PNG renderer and GeoJSON regressions passed alongside new TIFF/lifecycle cases.
- `mvnw.cmd -B -ntp -o -DskipTests package`: **Passed**, genuine component archive
  produced; artifact/source identity recorded in `geotiff-package-final1/check.json`.
- Independent Rasterio 1.5.2 / GDAL 3.12.2 checks: **eight actual Java-written TIFFs
  passed**, every pixel/value/center, CRS, affine, dtype, nodata/mask and supplied
  unit checked in `geotiff-tests-final1/independent-raster.json`. The repository-source
  optional reader was run with `python -O`; a deliberately corrupted copy was
  rejected under optimization, leaving original fixtures unchanged.
- Cases: EPSG:3857 float32/float64, Unicode and literal entities; EPSG:4326 latitude-
  and longitude-first CRSs; nonzero grid origin; rotation/shear. Valid zero and one
  NaN are checked separately from masks in all cases.
- Java GeoTools' convenience `gridToWorld` path rounded geographic positions to
  float; the written file's independent double-coordinate checks were correct.
  Tests use double transformation without relaxing the scientific tolerance.
- The first failing tests and GDAL-unit diagnostics are retained. An external JSON
  report initially failed because Windows' default text encoding cannot represent
  superscripts; UTF-8 output fixed the harness, with checks rerun successfully.

JNI GDAL plugins are unavailable in the Java test process and emit their existing
optional-provider warning. These TIFF tests use ImageIO/GeoTools; independent GDAL
validation uses Rasterio's separate GDAL installation. No skipped test is counted
as passing native-GDAL Java execution.

## Rebuilt component and live integration

- Current archive:
  `target/klab.component.geospatial-component-1.0-SNAPSHOT.jar`, SHA-256
  `c38628db17df9356f8febcfeff4c671e66bc8bca348aad58676e8a1d9c5fe810`.
- Client: merged `feature/python-client` commit
  `a11462ddcf7aa0e2643c1f44faad95e8dd9fed36`, clean checkout.
- Explicit compatible server: `244883d7b04f1eae2cc22e799575d33a9bdb2a43`, clean.
- Pinned worldview: `608bef150ced0a109db98a5aad64ba4461beaa54`.
- Both owned TEST runs first passed actual GeoJSON and one-sample saved-handle/
  fresh-process GeoTIFF functional acceptance. Native computation/readback and the
  unchanged independent north-up value/coordinate, zero, NaN/mask and unit oracle
  passed through the rebuilt genuine component.
- Serial full recomputation/export: **4/4 verified**, no failures, 21.325 measured
  wall seconds. Bounded-concurrency repeated export (2 workers): **14/14 verified**,
  no failures, 16.241 wall seconds. Verified warmups and repeated-mode seed are
  excluded from rates. These are small 20-cell synthetic workflow measurements,
  not capacity or a controlled before/after optimization claim.
- Every live TIFF is float64; scanner values are preserved, not narrowed.
  Source unit `degree_angle`, valid zero and one NaN with the correct nodata mask
  are independently checked by Rasterio/GDAL.
- Both personal test sessions and all five test-owned JVM/authority exits were
  confirmed; cleanup exit code 0. Evidence:
  `geotiff-final-live-serial1` and `geotiff-final-live-repeated1`, including run reports,
  exact commands, source/archive hashes, worker metadata and untouched TIFFs.

## Independent review iteration 2

A complete independent read-only review found **no demonstrated within-scope
implementation defect** and provisionally accepted the architecture/scientific
behavior, but required current-revision tests/package and live evidence before
recommending readiness. Those evidence gaps are now closed by the results above.
Optional improvements were implemented: ordinary Java tests assert written
transforms/entity-tag contents even without external capture; the optional independent
reader is reproducible at `tools/check_geotiff_reference.py`; obsolete float-only
comments were corrected and personal operational instructions removed from this
maintainer-facing record.

## Final independent review — Ready for maintainer review

**No demonstrated blocking or important defect remains in the reviewed scope.**
The independent read-only reviewer applied the same contribution guide to the
complete authorized contribution, including the optional reader and documentation.
All 31 current Java source hashes matched the final test/package records and both
live records. The current archive independently matched the recorded SHA-256 and
the live-deployed artifact.

The reviewer rechecked all eight captured TIFF fixtures under optimization and the
actual corrupted-copy negative control. It also revalidated all 22 retained live
TIFF outputs, including measured samples, warmups and functional samples, against
their recorded hashes and the independent scientific expectations. Both final runs
recorded session cleanup and owned stack exit code 0. No source files, evidence or
remote work were modified by the reviewer.

### Findings and disposition

- Unicode truncation: resolved by ASCII numeric references; exact GDAL units pass.
- GDAL double-unescape/punctuation truncation: resolved by the documented convention;
  Unicode, reserved punctuation and literal entity-looking source text pass.
- Precision narrowing: resolved by double conversion/float64 output; exact precision
  regression passes. Int64 and unsupported provided unit definitions fail explicitly.
- Written georeference evidence: closed by projected/geographic, both axis orders,
  origin-offset and rotation/shear tests plus the independent reader.
- Lifecycle: generation/close/delete failures have regressions and preserve primary
  failures. Runtime try-with-resources consumes/closes the owned export stream.
- Regression/reproduction clarity: ordinary Maven tests now assert the written
  transforms/tags; the optional independent reader is in the repository and documented.
- The remaining nonblocking phrase "committed-source" was corrected to
  "repository-source" to accurately describe the uncommitted review checkpoint.

### Smallest next actions and contribution boundary

No further implementation fix, build or live rerun was required by the final review.
The user subsequently authorized commit/push, a component PR and merge; publication
state and exact commit/check results are recorded in that PR's history/discussion.
This review recommendation itself is not official maintainer acceptance. No component
distribution or broad separate-deployment/provider-accuracy claim is made.

### Suggested contribution summary

Correct native numeric GeoTIFF orientation, precision, NaN missingness, measurement-
unit metadata and temporary-file ownership. Native y maps to north-up image rows;
DoubleScanner exports float64 without narrowing; zero stays valid and NaN gets the
correct mask. Source text follows ASCII-safe/GDAL-compatible XML conventions.
Validation: 22 component tests, eight independently read written fixtures, an
optimized corruption negative control, and current-artifact fresh-process live
workflows (4/4 serial, 14/14 repeated exports), with source/artifact identity and
cleanup confirmed. Int64/categorical and new window/multishard protocols remain
outside this bounded exporter contract.
