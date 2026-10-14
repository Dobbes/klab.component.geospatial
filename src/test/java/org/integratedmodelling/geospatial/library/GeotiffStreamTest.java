package org.integratedmodelling.geospatial.library;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeotiffStreamTest {
  @TempDir Path directory;

  @Test void closeFailureStillDeletesTheOwnedFile() throws Exception {
    var file = directory.resolve("owned.tiff");
    Files.write(file, new byte[] {1});
    var failure = new IOException("close failed");
    var input = new ByteArrayInputStream(new byte[] {1}) {
      @Override public void close() throws IOException { throw failure; }
    };
    var stream = GeodataIO.deleteOnClose(input, file);
    assertSame(failure, assertThrows(IOException.class, stream::close));
    assertFalse(Files.exists(file));
  }

  @Test void cleanupFailureDoesNotMaskThePrimaryCloseFailure() throws Exception {
    var owned = directory.resolve("nonempty");
    Files.createDirectory(owned);
    Files.write(owned.resolve("child"), new byte[] {1});
    var failure = new IOException("close failed");
    var input = new ByteArrayInputStream(new byte[] {1}) {
      @Override public void close() throws IOException { throw failure; }
    };
    var stream = GeodataIO.deleteOnClose(input, owned);
    assertSame(failure, assertThrows(IOException.class, stream::close));
    assertEquals(1, failure.getSuppressed().length);
    assertInstanceOf(DirectoryNotEmptyException.class, failure.getSuppressed()[0]);
  }

  @Test void cleanupFailureAloneIsReported() throws Exception {
    var owned = directory.resolve("nonempty");
    Files.createDirectory(owned);
    Files.write(owned.resolve("child"), new byte[] {1});
    var stream = GeodataIO.deleteOnClose(new ByteArrayInputStream(new byte[] {1}), owned);
    assertThrows(DirectoryNotEmptyException.class, stream::close);
  }

  @Test void failedGenerationDeletesItsTemporaryFileAndPreservesFailure() {
    File[] target = {null};
    var failure = new IllegalStateException("generation failed");
    var exporter = new GeodataIO() {
      @Override public File exportObservation(File file, Observation observation,
          Storage.DoubleScanner scanner, String format, ContextScope scope) {
        target[0] = file;
        throw failure;
      }
    };
    assertSame(failure, assertThrows(IllegalStateException.class, () -> exporter.exportGeotiffContinuous(
        mock(Observation.class), mock(Storage.DoubleScanner.class), mock(ContextScope.class))));
    assertFalse(target[0].exists());
  }

  @Test void emptyGenerationDeletesItsTemporaryFileAndReportsIoFailure() {
    File[] target = {null};
    var exporter = new GeodataIO() {
      @Override public File exportObservation(File file, Observation observation,
          Storage.DoubleScanner scanner, String format, ContextScope scope) {
        target[0] = file;
        return null;
      }
    };
    assertThrows(KlabIOException.class, () -> exporter.exportGeotiffContinuous(
        mock(Observation.class), mock(Storage.DoubleScanner.class), mock(ContextScope.class)));
    assertFalse(target[0].exists());
  }
}
