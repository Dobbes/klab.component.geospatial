package org.integratedmodelling.geospatial.utils;

import it.geosolutions.imageio.plugins.tiff.TIFFField;
import it.geosolutions.imageio.plugins.tiff.TIFFTag;
import it.geosolutions.imageioimpl.plugins.tiff.TIFFImageMetadata;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.io.imageio.geotiff.CRS2GeoTiffMetadataAdapter;
import org.geotools.coverage.grid.io.imageio.geotiff.GeoTiffConstants;
import org.geotools.coverage.util.CoverageUtilities;
import org.geotools.gce.geotiff.GeoTiffWriter;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.matrix.XAffineTransform;

/** GeoTools georeferencing with explicit NaN nodata and source band-unit metadata. */
public final class NativeGeoTiffWriter {
  private NativeGeoTiffWriter() {}

  public static void write(File file, GridCoverage2D coverage, String unit) throws IOException {
    var geometry = coverage.getGridGeometry();
    var crs = geometry.getCoordinateReferenceSystem2D();
    var metadata = new CRS2GeoTiffMetadataAdapter(crs).parseCoordinateReferenceSystem();
    var transform = new AffineTransform((AffineTransform) geometry.getGridToCRS2D());
    transform.concatenate(CoverageUtilities.CENTER_TO_CORNER);
    // Match GeoTools' normal axis-order handling; pixel locations remain unchanged.
    if (XAffineTransform.getSwapXY(transform) == -1 || CRS.getAxisOrder(crs) == CRS.AxisOrder.NORTH_EAST) {
      transform.preConcatenate(CoverageUtilities.AXES_SWAP);
    }
    var range = geometry.getGridRange2D();
    if (range.x != 0 || range.y != 0) {
      transform.concatenate(AffineTransform.getTranslateInstance(range.x, range.y));
    }
    metadata.addGeoShortParam(GeoTiffConstants.GTRasterTypeGeoKey, GeoTiffConstants.RasterPixelIsArea);
    metadata.setModelTransformation(transform);
    var nodata = CoverageUtilities.getNoDataProperty(coverage);
    if (nodata != null) {
      // GeoTiffWriter.write omits NaN; its public encoder can correctly retain it.
      metadata.setNoData(nodata.getAsSingleValue());
    }
    metadata.setTiffTagsMetadata(Map.of("305", "k.LAB (www.integratedmodelling.org)"));
    var writer = new it.geosolutions.imageioimpl.plugins.tiff.TIFFImageWriterSpi().createWriterInstance();
    try (var output = ImageIO.createImageOutputStream(file)) {
      if (output == null) throw new IOException("Cannot create GeoTIFF output stream");
      var parameters = writer.getDefaultWriteParam();
      var imageMetadata = (TIFFImageMetadata) GeoTiffWriter.createGeoTiffIIOMetadata(writer,
          ImageTypeSpecifier.createFromRenderedImage(coverage.getRenderedImage()), metadata, parameters);
      if (unit != null && !unit.isBlank()) {
        var tag = new TIFFTag("GDAL_METADATA", 42112, 1 << TIFFTag.TIFF_ASCII);
        imageMetadata.getRootIFD().addTIFFField(new TIFFField(tag, TIFFTag.TIFF_ASCII, 1,
            new String[] {unitMetadata(unit)}));
      }
      writer.setOutput(output);
      writer.write(null, new IIOImage(coverage.getRenderedImage(), null, imageMetadata), parameters);
    } finally {
      writer.dispose();
    }
  }

  private static String unitMetadata(String unit) throws IOException {
    var text = new StringWriter();
    try {
      var xml = XMLOutputFactory.newFactory().createXMLStreamWriter(text);
      xml.writeStartElement("GDALMetadata");
      xml.writeStartElement("Item");
      xml.writeAttribute("name", "UNITTYPE");
      xml.writeAttribute("sample", "0");
      xml.writeAttribute("role", "unittype");
      // TIFF ASCII fields truncate Unicode characters. Numeric XML references
      // keep the tag ASCII while preserving the exact source text for readers.
      for (int offset = 0; offset < unit.length(); ) {
        int character = unit.codePointAt(offset);
        if (character > 0x7f) {
          xml.writeEntityRef("#x" + Integer.toHexString(character));
        } else {
          // GDAL_METADATA values historically use two XML-escaping layers:
          // GDAL unescapes again after parsing XML. Preserve literal punctuation
          // and entity-looking source text rather than letting that pass truncate it.
          String value = switch (character) {
            case '&' -> "&amp;";
            case '<' -> "&lt;";
            case '>' -> "&gt;";
            case '"' -> "&quot;";
            default -> new String(Character.toChars(character));
          };
          xml.writeCharacters(value);
        }
        offset += Character.charCount(character);
      }
      xml.writeEndElement();
      xml.writeEndElement();
      xml.close();
      return text.toString();
    } catch (XMLStreamException e) {
      throw new IOException("Cannot encode source measurement unit", e);
    }
  }
}
