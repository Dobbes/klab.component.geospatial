package org.integratedmodelling.geospatial.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageMetadata;
import java.awt.image.DataBuffer;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridCoverageFactory;
import org.geotools.coverage.util.CoverageUtilities;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.transform.AffineTransform2D;
import org.eclipse.imagen.TiledImage;
import org.integratedmodelling.geospatial.library.GeodataIO;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Grid;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Tile;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.runtime.scale.space.EnvelopeImpl;
import org.integratedmodelling.klab.runtime.scale.space.ProjectionImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeotiffExportTest {
  @TempDir Path directory;

  private void capture(File file, String name) throws Exception {
    var target = System.getProperty("geotiff.evidence.dir");
    if (target != null) {
      var destination = Path.of(target);
      assertTrue(Files.isDirectory(destination), "Evidence directory must be explicitly prepared");
      Files.copy(file.toPath(), destination.resolve(name));
    }
  }

  private void assertWrittenTransform(File file, double[] expected) throws Exception {
    try (var input = new java.io.FileInputStream(file)) {
      var reader = new GeoTiffReader(input);
      try {
        assertEquals(3857, CRS.lookupEpsgCode(reader.getCoordinateReferenceSystem(), true));
        var transform = reader.getOriginalGridToWorld(org.geotools.api.referencing.datum.PixelInCell.CELL_CORNER);
        for (var pixel : new double[][] {{0, 0}, {5, 0}, {0, 4}, {5, 4}}) {
          var point = new double[2];
          transform.transform(pixel, 0, point, 0, 1);
          assertEquals(expected[2] + pixel[0] * expected[0] + pixel[1] * expected[1], point[0], 1e-8);
          assertEquals(expected[5] + pixel[0] * expected[3] + pixel[1] * expected[4], point[1], 1e-8);
        }
      } finally {
        reader.dispose();
      }
    }
  }

  private record Fixture(Observation observation, Storage.DoubleScanner scanner, ContextScope scope) {}

  private Fixture fixture() {
    return fixture(Storage.Type.FLOAT, new double[] {
        0, 1, 2, 3, 10, 11, 12, 13, 20, Double.NaN, 22, 23, 30, 31, 32, 33, 40, 41, 42, 43});
  }

  private Fixture fixture(Storage.Type type, double[] values) {
    var observation = mock(Observation.class);
    var observable = mock(Observable.class);
    when(observation.getObservable()).thenReturn(observable);
    when(observable.getName()).thenReturn("Reference angle");
    var space = mock(Space.class);
    var tile = mock(Tile.class);
    var grid = mock(Grid.class);
    when(space.as(Tile.class)).thenReturn(tile);
    when(tile.getGrid()).thenReturn(grid);
    when(grid.getXCells()).thenReturn(5L);
    when(grid.getYCells()).thenReturn(4L);
    when(space.getShape()).thenReturn(List.of(5L, 4L));
    when(space.getEnvelope()).thenReturn(EnvelopeImpl.create(200000, 200500, 6000000, 6000400,
        new ProjectionImpl("EPSG:3857")));
    var scale = mock(Scale.class);
    when(scale.getSpace()).thenReturn(space);
    var shard = mock(Storage.Shard.class);
    when(shard.getGeometry()).thenReturn(scale);
    when(shard.getShardingStrategy()).thenReturn(new Data.ShardingStrategy(
        Data.FillCurve.D2_XY, 1, 0, 0, type));
    var scanner = mock(Storage.DoubleScanner.class);
    when(scanner.shard()).thenReturn(shard);
    when(scanner.size()).thenReturn(20L);
    // Native XY order; y is south-to-north. Values are independently specified.
    var index = new AtomicInteger();
    when(scanner.get()).thenAnswer(call -> values[index.getAndIncrement()]);
    var scope = mock(ContextScope.class, RETURNS_DEEP_STUBS);
    when(scope.getDigitalTwin().getStorageManager().getStorage(observation).getKey()).thenReturn(null);
    return new Fixture(observation, scanner, scope);
  }

  private GridCoverage2D coverage() {
    var fixture = fixture();
    return Geotools.stateToCoverage(fixture.observation(), fixture.scanner(),
        DataBuffer.TYPE_FLOAT, Float.NaN, fixture.scope(), false);
  }

  @Test void nativeYMapsToNorthUpRowsAtEveryWorldCoordinate() throws Exception {
    var coverage = coverage();
    try {
      var raster = coverage.getRenderedImage().getData();
      for (int row = 0; row < 4; row++) {
        for (int column = 0; column < 5; column++) {
          var world = coverage.getGridGeometry().gridToWorld(
              new org.geotools.coverage.grid.GridCoordinates2D(column, row));
          assertEquals(200050 + column * 100, world.getOrdinate(0), 1e-8);
          assertEquals(6000350 - row * 100, world.getOrdinate(1), 1e-8);
          float actual = raster.getSampleFloat(column, row, 0);
          if (column == 2 && row == 2) assertTrue(Float.isNaN(actual));
          else assertEquals(column * 10 + 3 - row, actual);
        }
      }
      assertEquals(0, raster.getSampleFloat(0, 3, 0));
      assertTrue(Double.isNaN(CoverageUtilities.getNoDataProperty(coverage).getAsSingleValue()));
    } finally {
      coverage.dispose(true);
    }
  }

  @Test void writtenTiffPreservesNanAndSourceBandUnit() throws Exception {
    var coverage = coverage();
    var file = directory.resolve("reference.tiff").toFile();
    try {
      NativeGeoTiffWriter.write(file, coverage, "degree_angle & <source>");
      var reader = new it.geosolutions.imageioimpl.plugins.tiff.TIFFImageReaderSpi().createReaderInstance();
      try (var input = ImageIO.createImageInputStream(file)) {
        reader.setInput(input);
        var metadata = (TIFFImageMetadata) reader.getImageMetadata(0);
        var nodata = metadata.getRootIFD().getTIFFField(42113);
        assertNotNull(nodata);
        assertTrue(Double.isNaN(Double.parseDouble(nodata.getAsString(0))));
        var unit = metadata.getRootIFD().getTIFFField(42112).getAsString(0);
        var xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            new org.xml.sax.InputSource(new java.io.StringReader(unit)));
        assertEquals("degree_angle &amp; &lt;source&gt;", xml.getElementsByTagName("Item").item(0).getTextContent());
        assertTrue(unit.chars().allMatch(character -> character < 128));
        var raster = reader.read(0).getRaster();
        assertEquals(3, raster.getSampleFloat(0, 0, 0));
        assertEquals(0, raster.getSampleFloat(0, 3, 0));
        assertTrue(Float.isNaN(raster.getSampleFloat(2, 2, 0)));
        capture(file, "projected-float.tiff");
      } finally {
        reader.dispose();
      }
    } finally {
      coverage.dispose(true);
    }
  }

  @Test void absentMeasurementUnitIsNotInventedFromProjectedCrs() throws Exception {
    var coverage = coverage();
    try {
      var file = directory.resolve("no-unit.tiff").toFile();
      NativeGeoTiffWriter.write(file, coverage, null);
      var reader = new it.geosolutions.imageioimpl.plugins.tiff.TIFFImageReaderSpi().createReaderInstance();
      try (var input = ImageIO.createImageInputStream(file)) {
        reader.setInput(input);
        assertNull(((TIFFImageMetadata) reader.getImageMetadata(0)).getRootIFD().getTIFFField(42112));
      } finally {
        reader.dispose();
      }
    } finally {
      coverage.dispose(true);
    }
  }

  @Test void doubleStoragePreservesValuesThatFloatCannotRepresent() throws Exception {
    var fixture = fixture(Storage.Type.DOUBLE, new double[] {
        0, 1, 2, 16777217.125, 10, 11, 12, 13, 20, Double.NaN, 22, 23, 30, 31, 32, 33, 40, 41, 42, 43});
    var file = directory.resolve("double.tiff").toFile();
    new GeodataIO().exportObservation(file, fixture.observation(), fixture.scanner(), "tiff", fixture.scope());
    var reader = new it.geosolutions.imageioimpl.plugins.tiff.TIFFImageReaderSpi().createReaderInstance();
    try (var input = ImageIO.createImageInputStream(file)) {
      reader.setInput(input);
      var raster = reader.read(0).getRaster();
      assertEquals(DataBuffer.TYPE_DOUBLE, raster.getDataBuffer().getDataType());
      assertEquals(16777217.125, raster.getSampleDouble(0, 0, 0));
      assertTrue(Double.isNaN(raster.getSampleDouble(2, 2, 0)));
      capture(file, "projected-double.tiff");
    } finally {
      reader.dispose();
    }
  }

  @Test void unicodeSourceUnitSurvivesTiffAsciiXmlMetadata() throws Exception {
    var coverage = coverage();
    var file = directory.resolve("unicode.tiff").toFile();
    try {
      NativeGeoTiffWriter.write(file, coverage, "µmol m⁻² s⁻¹ & <source>");
      var reader = new it.geosolutions.imageioimpl.plugins.tiff.TIFFImageReaderSpi().createReaderInstance();
      try (var input = ImageIO.createImageInputStream(file)) {
        reader.setInput(input);
        var unit = ((TIFFImageMetadata) reader.getImageMetadata(0)).getRootIFD().getTIFFField(42112).getAsString(0);
        var xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            new org.xml.sax.InputSource(new java.io.StringReader(unit)));
        assertEquals("µmol m⁻² s⁻¹ &amp; &lt;source&gt;", xml.getElementsByTagName("Item").item(0).getTextContent());
        assertTrue(unit.chars().allMatch(character -> character < 128));
        capture(file, "unicode-unit.tiff");
      } finally {
        reader.dispose();
      }
    } finally {
      coverage.dispose(true);
    }
  }

  @Test void int64IsRejectedBeforeAConvertedScannerCanLosePrecision() {
    var fixture = fixture(Storage.Type.LONG, new double[20]);
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException.class,
        () -> new GeodataIO().exportObservation(directory.resolve("int64.tiff").toFile(),
            fixture.observation(), fixture.scanner(), "tiff", fixture.scope()));
    verify(fixture.scanner(), never()).get();
    assertFalse(directory.resolve("int64.tiff").toFile().exists());
  }

  @Test void suppliedButUnserializableUnitIsNotSilentlyDropped() {
    var fixture = fixture();
    when(fixture.observation().getObservable().getUnit())
        .thenReturn(mock(org.integratedmodelling.klab.api.data.mediation.Unit.class));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException.class,
        () -> new GeodataIO().exportObservation(directory.resolve("unsupported-unit.tiff").toFile(),
            fixture.observation(), fixture.scanner(), "tiff", fixture.scope()));
    assertFalse(directory.resolve("unsupported-unit.tiff").toFile().exists());
  }

  @Test void literalEntityTextIsNotReinterpretedAsAUnit() throws Exception {
    var coverage = coverage();
    try {
      var file = directory.resolve("entities.tiff").toFile();
      NativeGeoTiffWriter.write(file, coverage, "&amp; &#176; &#xB0;");
      var reader = new it.geosolutions.imageioimpl.plugins.tiff.TIFFImageReaderSpi().createReaderInstance();
      try (var input = ImageIO.createImageInputStream(file)) {
        reader.setInput(input);
        var unit = ((TIFFImageMetadata) reader.getImageMetadata(0)).getRootIFD().getTIFFField(42112).getAsString(0);
        var xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(
            new org.xml.sax.InputSource(new java.io.StringReader(unit)));
        assertEquals("&amp;amp; &amp;#176; &amp;#xB0;", xml.getElementsByTagName("Item").item(0).getTextContent());
        assertTrue(unit.chars().allMatch(character -> character < 128));
      } finally {
        reader.dispose();
      }
      capture(file, "literal-entities.tiff");
    } finally {
      coverage.dispose(true);
    }
  }

  @Test void geographicAxisOrdersHaveTheSameWrittenWorldCoordinates() throws Exception {
    for (boolean latitudeFirst : new boolean[] {false, true}) {
      var base = coverage();
      var properties = new java.util.HashMap<String, Object>();
      CoverageUtilities.setNoDataProperty(properties, Double.NaN);
      var transform = latitudeFirst
          ? new AffineTransform(0, .1, -.1, 0, 49.95, 10.05)
          : new AffineTransform(.1, 0, 0, -.1, 10.05, 49.95);
      var coverage = new GridCoverageFactory().create("geographic", base.getRenderedImage(),
          CRS.decode("EPSG:4326", !latitudeFirst), new AffineTransform2D(transform),
          base.getSampleDimensions(), null, properties);
      try {
        var file = directory.resolve(latitudeFirst ? "latfirst.tiff" : "lonfirst.tiff").toFile();
        NativeGeoTiffWriter.write(file, coverage, null);
        try (var input = new java.io.FileInputStream(file)) {
          var reader = new GeoTiffReader(input);
          try {
            assertEquals(4326, CRS.lookupEpsgCode(reader.getCoordinateReferenceSystem(), true));
            // Use double-coordinate transformation; gridToWorld's convenience
            // position path narrows geographic coordinates to float in GeoTools.
            var point = new double[2];
            reader.getOriginalGridToWorld(org.geotools.api.referencing.datum.PixelInCell.CELL_CENTER)
                .transform(new double[] {0, 0}, 0, point, 0, 1);
            boolean readLatitudeFirst = CRS.getAxisOrder(reader.getCoordinateReferenceSystem()) == CRS.AxisOrder.NORTH_EAST;
            assertEquals(10.05, point[readLatitudeFirst ? 1 : 0], 1e-8);
            assertEquals(49.95, point[readLatitudeFirst ? 0 : 1], 1e-8);
            capture(file, latitudeFirst ? "geographic-latfirst.tiff" : "geographic-lonfirst.tiff");
          } finally {
            reader.dispose();
          }
        }
      } finally {
        coverage.dispose(false);
        base.dispose(true);
      }
    }
  }

  @Test void shiftedGridOriginAndRotatedAffineArePreservedInWrittenTiffs() throws Exception {
    var base = coverage();
    var properties = new java.util.HashMap<String, Object>();
    CoverageUtilities.setNoDataProperty(properties, Double.NaN);
    try {
      var image = base.getRenderedImage();
      var shifted = new TiledImage(7, -3, image.getWidth(), image.getHeight(), 7, -3,
          image.getSampleModel(), image.getColorModel());
      shifted.setData(image.getData().createTranslatedChild(7, -3));
      var shiftedCoverage = new GridCoverageFactory().create("shifted", shifted,
          base.getCoordinateReferenceSystem2D(), new AffineTransform2D(100, 0, 0, -100, 199350, 6000050),
          base.getSampleDimensions(), null, properties);
      try {
        assertEquals(7, shiftedCoverage.getGridGeometry().getGridRange2D().x);
        assertEquals(-3, shiftedCoverage.getGridGeometry().getGridRange2D().y);
        var file = directory.resolve("shifted.tiff").toFile();
        NativeGeoTiffWriter.write(file, shiftedCoverage, null);
        assertWrittenTransform(file, new double[] {100, 0, 200000, 0, -100, 6000400});
        capture(file, "shifted-origin.tiff");
      } finally {
        shiftedCoverage.dispose(true);
      }
      var rotatedCoverage = new GridCoverageFactory().create("rotated", image,
          base.getCoordinateReferenceSystem2D(), new AffineTransform2D(100, 20, 10, -100, 200055, 6000360),
          base.getSampleDimensions(), null, properties);
      try {
        var file = directory.resolve("rotated.tiff").toFile();
        NativeGeoTiffWriter.write(file, rotatedCoverage, null);
        assertWrittenTransform(file, new double[] {100, 10, 200000, 20, -100, 6000400});
        capture(file, "rotated-affine.tiff");
      } finally {
        rotatedCoverage.dispose(false);
      }
    } finally {
      base.dispose(true);
    }
  }

  @Test void serverTemporaryExportIsDeletedWhenStreamCloses() throws Exception {
    File[] file = {null};
    var exporter = new GeodataIO() {
      @Override public File exportObservation(File target, Observation observation,
          Storage.DoubleScanner scanner, String format, ContextScope scope) {
        file[0] = target;
        try { java.nio.file.Files.write(target.toPath(), new byte[] {1, 2, 3}); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        return target;
      }
    };
    try (var stream = exporter.exportGeotiffContinuous(mock(Observation.class), mock(Storage.DoubleScanner.class), mock(ContextScope.class))) {
      assertTrue(file[0].exists());
      assertArrayEquals(new byte[] {1, 2, 3}, stream.readAllBytes());
    }
    assertFalse(file[0].exists());
  }
}
