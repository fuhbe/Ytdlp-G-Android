import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

/** Exercises the same ZIP implementation and actual runtime archives as startup. */
public final class ZipBootstrapProbe {
    public static void main(String[] args) throws Exception {
        for (String filename : args) {
            int entries = 0;
            long bytes = 0;
            try (ZipFile zip = new ZipFile(new File(filename))) {
                Enumeration<ZipArchiveEntry> list = zip.getEntries();
                byte[] buffer = new byte[32768];
                while (list.hasMoreElements()) {
                    ZipArchiveEntry entry = list.nextElement();
                    entries++;
                    if (!entry.isDirectory()) {
                        try (InputStream input = zip.getInputStream(entry)) {
                            int n;
                            while ((n = input.read(buffer)) != -1) bytes += n;
                        }
                    }
                }
            }
            if (entries == 0 || bytes == 0) throw new AssertionError("Empty runtime archive");
            System.out.println("PASS ZIP entries=" + entries + " bytes=" + bytes);
        }
    }
}
