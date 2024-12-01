import java.io.*;
import java.net.*;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

public class FileConnection  {

    public static String resolveAbsolutePath(String url, String ref) {
        // Check that path is url
        try {
            new URI(url).toURL();
            return url;
        } catch (MalformedURLException | URISyntaxException | IllegalArgumentException ignored) {}
        // Check that ref is url
        try {
            new URI(ref).toURL();
            return Path.of(ref.replace("file:///", "")).getParent().resolve(url).toString().replace("\\", "/");
        } catch (MalformedURLException | URISyntaxException | IllegalArgumentException ignored) {}
        try {
            if (!Path.of(url).isAbsolute()) {
                return Path.of(ref).getParent().resolve(url).toString().replace("\\", "/");
            }
        } catch (Exception e) {
            return "http://empty";
        }
        return url;
    }

    public static URL createURL(String url) throws URISyntaxException, MalformedURLException {
        URL result;
        try {
            result = new URI(url).toURL();
        } catch (MalformedURLException e) {
            result = new URI("file:///" + url).toURL();
        }
        return result;
    }

    public static boolean isExists(String path) {
        try {
            URLConnection conn = createURL(path).openConnection();
            conn.setConnectTimeout(350);
            conn.connect();
            return true;
        } catch (IOException | URISyntaxException | InvalidPathException e) {
            System.err.println("Error while connecting to file " + path + ": " + e.getMessage());
            return false;
        }
    }

    public static List<String> readFile(String path) throws IOException, URISyntaxException, InvalidPathException {
        URLConnection conn = createURL(path).openConnection();
        conn.setReadTimeout(350);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            return reader.lines().toList();
        }
    }

    public static BufferedWriter getWriter(String path) throws IOException, URISyntaxException, InvalidPathException {
        path = path.replace("file:///", "");
        try {new URI(path).toURL();} catch (MalformedURLException e) {
            return new BufferedWriter(new FileWriter(path));
        }
        throw new IOException("File cannot be written by this path: " + path);
    }
}
