package com.browicy.engine.js;

import org.graalvm.polyglot.io.FileSystem;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.file.AccessMode;
import java.nio.file.DirectoryStream;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * GraalJS-{@link FileSystem}, der ES-Modul-Importe (statisch und dynamisch) über
 * das Browicy-Netzwerk-Backend lädt.
 *
 * <p>GraalJS löst relative Imports gegen die Source-URI des importierenden Moduls auf und
 * reicht dem Dateisystem die Ergebnis-URL als {@link URI} (via {@link #parsePath(URI)})
 * bzw. für aus dem Dateisystem geladene Module den aufgelösten Geschwister-Pfad weiter.
 * Damit die volle URL den Weg durch {@link java.nio.file.Path} übersteht, wird sie in ein
 * zeichen-sicheres Pfad-Format kodiert ({@code u!} + Prozent-Kodierung); alle hier
 * definierten Methoden dekodieren symmetrisch zurück. Relative Pfade (Geschwister-Imports
 * aus dateisystem-geladenen Modulen) werden gegen die URL des zuletzt geparsten
 * "u!"-Referrers aufgelöst — GraalJS lädt Modulgraphen synchron und tiefen-zuerst, die
 * Zugriffe laufen daher strikt auf dem Event-Loop-Thread ab.
 */
final class EsModuleFileSystem implements FileSystem {

    private static final String PREFIX = "u!";
    private static final long MODULE_FETCH_TIMEOUT_MILLIS = 30_000;

    private final JsFetchBackend fetchBackend;
    private final String documentUrl;
    private final java.util.LinkedHashMap<String, byte[]> cache;
    private String referrerUrl;

    EsModuleFileSystem(JsFetchBackend fetchBackend, String documentUrl) {
        this.fetchBackend = fetchBackend;
        this.documentUrl = documentUrl == null ? "" : documentUrl;
        this.cache = new java.util.LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > 512;
            }
        };
    }

    private static String encodeUrl(String url) {
        StringBuilder encoded = new StringBuilder(PREFIX);
        for (int index = 0; index < url.length(); index++) {
            char character = url.charAt(index);
            if ((character >= 'a' && character <= 'z')
                    || (character >= 'A' && character <= 'Z')
                    || (character >= '0' && character <= '9')
                    || character == '.' || character == '-' || character == '_') {
                encoded.append(character);
            } else {
                encoded.append('%').append(String.format("%04x", (int) character));
            }
        }
        return encoded.toString();
    }

    private static String decodeUrl(String path) {
        StringBuilder url = new StringBuilder();
        for (int index = PREFIX.length(); index < path.length(); index++) {
            char character = path.charAt(index);
            if (character == '%' && index + 4 < path.length()) {
                url.append((char) Integer.parseInt(path.substring(index + 1, index + 5), 16));
                index += 4;
            } else {
                url.append(character);
            }
        }
        return url.toString();
    }

    @Override
    public Path parsePath(URI uri) {
        if (!isHttp(uri)) {
            throw new UnsupportedOperationException("Nur HTTP(S)-ES-Module werden unterstützt: " + uri);
        }
        return Path.of(encodeUrl(uri.toString()));
    }

    @Override
    public Path parsePath(String path) {
        if (path.startsWith(PREFIX)) {
            referrerUrl = decodeUrl(path);
        }
        return Path.of(path);
    }

    @Override
    public void checkAccess(Path path, Set<? extends AccessMode> modes, LinkOption... linkOptions)
            throws IOException {
        resolveUrl(path); // validiert nur; Existenz wird beim Lesen geprüft
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        throw new IOException("ES-Modul-Dateisystem ist schreibgeschützt");
    }

    @Override
    public void delete(Path path) throws IOException {
        throw new IOException("ES-Modul-Dateisystem ist schreibgeschützt");
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options,
                                              FileAttribute<?>... attrs) throws IOException {
        URI uri = resolveUrl(path);
        return new ByteChannel(fetchModule(uri));
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path directory,
                                                    DirectoryStream.Filter<? super Path> filter) {
        return new DirectoryStream<>() {
            @Override
            public java.util.Iterator<Path> iterator() {
                return List.<Path>of().iterator();
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public Path toAbsolutePath(Path path) {
        return path.toAbsolutePath();
    }

    @Override
    public Path toRealPath(Path path, LinkOption... linkOptions) {
        return path;
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... linkOptions)
            throws IOException {
        URI uri = resolveUrl(path);
        return Map.of("isRegularFile", true, "size", (long) fetchModule(uri).length);
    }

    private URI resolveUrl(Path path) throws IOException {
        String text = path.toString().replace('\\', '/');
        URI uri;
        if (text.startsWith(PREFIX)) {
            uri = URI.create(decodeUrl(text));
        } else {
            if (text.startsWith("./")) {
                text = text.substring(2);
            }
            String base = referrerUrl != null ? referrerUrl : documentUrl;
            if (base.isEmpty()) {
                throw new NoSuchFileException(path.toString(),
                        null, "Keine Basis-URL für relatives ES-Modul");
            }
            uri = URI.create(base).resolve(text);
        }
        if (!isHttp(uri)) {
            throw new NoSuchFileException(path.toString(),
                    null, "Nur HTTP(S)-ES-Module werden unterstützt: " + uri);
        }
        return uri;
    }

    private static boolean isHttp(URI uri) {
        String scheme = uri.getScheme();
        return scheme != null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    }

    private byte[] fetchModule(URI uri) throws IOException {
        byte[] cached = cache.get(uri.toString());
        if (cached != null) {
            return cached;
        }
        if (fetchBackend == null) {
            throw new IOException("ES-Modul " + uri
                    + " kann nicht geladen werden: kein Netzwerk-Backend");
        }
        JsFetchResponse response;
        try {
            response = fetchBackend.fetch(JsFetchRequest.get(uri))
                    .get(MODULE_FETCH_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("ES-Modul-Ladung unterbrochen: " + uri, interrupted);
        } catch (TimeoutException timeout) {
            throw new IOException("ES-Modul " + uri + " nach "
                    + MODULE_FETCH_TIMEOUT_MILLIS + " ms nicht geladen", timeout);
        } catch (ExecutionException failure) {
            throw new IOException("ES-Modul konnte nicht geladen werden: " + uri,
                    failure.getCause());
        }
        if (response.status() < 200 || response.status() >= 300) {
            throw new IOException("ES-Modul " + uri + ": HTTP " + response.status()
                    + " " + response.statusText());
        }
        byte[] content = response.bodyText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        cache.put(uri.toString(), content);
        return content;
    }

    private static final class ByteChannel implements SeekableByteChannel {
        private final byte[] data;
        private int position;
        private boolean open = true;

        private ByteChannel(byte[] data) {
            this.data = data;
        }

        @Override
        public int read(java.nio.ByteBuffer dst) throws IOException {
            if (!open) {
                throw new ClosedChannelException();
            }
            int count = Math.min(dst.remaining(), data.length - position);
            if (count <= 0) {
                return -1;
            }
            dst.put(data, position, count);
            position += count;
            return count;
        }

        @Override
        public int write(java.nio.ByteBuffer src) {
            throw new UnsupportedOperationException("ES-Modul-Dateisystem ist schreibgeschützt");
        }

        @Override
        public long position() throws IOException {
            if (!open) {
                throw new ClosedChannelException();
            }
            return position;
        }

        @Override
        public SeekableByteChannel position(long newPosition) throws IOException {
            if (!open) {
                throw new ClosedChannelException();
            }
            position = (int) newPosition;
            return this;
        }

        @Override
        public long size() throws IOException {
            if (!open) {
                throw new ClosedChannelException();
            }
            return data.length;
        }

        @Override
        public SeekableByteChannel truncate(long size) {
            throw new UnsupportedOperationException("ES-Modul-Dateisystem ist schreibgeschützt");
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }
    }
}
