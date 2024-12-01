import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

public class M3U8 {
    private final String m3u8Path;
    private final List<M3u8Slave> m3u8SlavesList = new ArrayList<>();

    public M3U8(String m3u8FilePath) throws IOException {
        // Parsing m3u8.
        List<String> M3u8Lines;
        // Normalizing path
        m3u8FilePath = FileConnection.resolveAbsolutePath(m3u8FilePath, System.getProperty("user.dir"));
        try {
            M3u8Lines = FileConnection.readFile(m3u8FilePath);
        } catch(IOException | URISyntaxException e) {
            throw new IOException("Error while reading main m3u8 file:\n" + e.getMessage());
        }
        m3u8Path = m3u8FilePath;

        List<String> propList = M3u8Lines.stream().filter(line -> line.startsWith("#EXT-X-STREAM-INF:")).toList();
        List<String> m3u8VariantsUrls = M3u8Lines.stream().filter(line -> line.endsWith(".m3u8")).collect(Collectors.toList());
        if (m3u8VariantsUrls.isEmpty()) m3u8VariantsUrls.add(m3u8FilePath);

        for (int i = 0; i < propList.size(); i++) {
            String[] props = propList.get(i).replaceAll("#EXT-X-STREAM-INF:", "").replaceAll(",(?=[^\"=]+\",?)", ";").replace("\"", "").split(",");
            Properties property = new Properties();
            for (String prop : props) {
                String[] splitProp = prop.split("=");
                property.put(splitProp[0], splitProp[1]);
            }
            m3u8VariantsUrls.set(i, FileConnection.resolveAbsolutePath(m3u8VariantsUrls.get(i), m3u8FilePath));
            m3u8SlavesList.add(new M3u8Slave(m3u8VariantsUrls.get(i), property));
        }
        Collections.sort(m3u8SlavesList);
    }

    public enum Mark {
        GOOD,
        BROKEN,
        EMPTY
    }

    public void markM3u8Slaves() {
        m3u8SlavesList.forEach(M3u8Slave::mark);
    }

    public void repair() {
        if (m3u8SlavesList.stream().anyMatch(m3u8 -> m3u8.getMark() != Mark.GOOD)) {
            for (int i = 0; i < m3u8SlavesList.size() - 1; ++i) {
                m3u8SlavesList.get(i).repairM3u8Slave();
            }
        }
    }

    private Ts getHighResTs(M3u8Slave curResM3u8, int tsIndex) {
        for (int i = m3u8SlavesList.indexOf(curResM3u8); i < m3u8SlavesList.size(); i++) {
            Ts ts = m3u8SlavesList.get(i).fragments.get(tsIndex);
            if (ts.getMark() == Mark.GOOD) return ts;
        }
        return null;
    }

    public void printStatus() {
        StringBuilder status = new StringBuilder();
        m3u8SlavesList.forEach(m3u8 -> {
            status.append(m3u8.m3u8SlavePath).append(" ").append(m3u8.getMark()).append("\n");
            m3u8.fragments.forEach(ts -> status.append("\t").append(ts.getUrl()).append(" ").append(ts.getMark()).append("\n"));
        });
        System.out.println(status);
    }

    // Update M3U8 file with available or lower-quality TS fragments
    public void updateM3U8() {
        try (BufferedWriter writer = FileConnection.getWriter(m3u8Path.replaceFirst("\\.", "_updated."))) {
            writer.write("#EXTM3U\n");
            writer.write("#EXT-X-VERSION:3\n");

            for (M3u8Slave m3u8 : m3u8SlavesList) {
                if (m3u8.getMark() != Mark.GOOD) continue;
                writer.write("#EXT-X-STREAM-INF:");
                Properties prop = m3u8.properties;
                List<String> propStrList = new ArrayList<>();
                prop.forEach((k, v) -> {
                    if (v.toString().contains(",")) v = "\"" + v + "\"";
                    propStrList.add(k +"="+ v);
                });
                writer.write(String.join(",", propStrList)+"\n");
                writer.write(m3u8.m3u8SlavePath + "\n");
            }
            writer.write("#EXT-X-ENDLIST\n");
        } catch (IOException | URISyntaxException e) {
            System.err.println("Error updating M3U8 file: " + e.getMessage());
            File file = new File(m3u8Path.replaceFirst("\\.", "_updated."));
            if (file.exists()) file.delete();
        }
    }

    // A private class for work with secondary (slave) m3u8
    private class M3u8Slave implements Comparable<M3u8Slave> {
        private final String m3u8SlavePath;
        private final Properties properties;
        private Mark mark = null;
        private final List<Ts> fragments;


        private M3u8Slave(String m3u8SlavePath, Properties properties) {
            this.m3u8SlavePath = m3u8SlavePath;
            this.properties = properties;

            // Parse the m3u8 slave
            List<String> lines = new ArrayList<>();
            try {
                lines = FileConnection.readFile(m3u8SlavePath);
            } catch(Exception e) {
                System.err.println("Error while parsing m3u8 slave:\n" + e.getMessage());
            }
            fragments = lines.stream()
                    .filter(line -> line.endsWith(".ts"))
                    .map(path -> FileConnection.resolveAbsolutePath(path, m3u8SlavePath))
                    .map(Ts::new).toList();
            if (fragments.isEmpty()) {
                System.err.println("m3u8 slave instance does not contains any links to .ts. Path: " + m3u8SlavePath);
            }
        }

        @Override
        public int compareTo(M3u8Slave slave) {
            if (this.equals(slave)) return 0;
            try {
                String[] res1 = this.properties.getProperty("RESOLUTION").split("x");
                String[] res2 = slave.properties.getProperty("RESOLUTION").split("x");
                int pixels1 = Integer.parseInt(res1[0]) * Integer.parseInt(res1[1]);
                int pixels2 = Integer.parseInt(res2[0]) * Integer.parseInt(res2[1]);
                if (pixels1 < pixels2) {
                    return -1;
                } else if (pixels1 > pixels2) {
                    return 1;
                }
                return 0;
            } catch (NullPointerException e) {
                return -1;
            }
        }

        private void repairM3u8Slave() {
            for (int j = 0; j < fragments.size(); j++) {
                Ts ts = fragments.get(j);
                if (ts.getMark() == Mark.GOOD) {
                    System.out.println("Ts fragment is good: " + ts.getUrl());
                    continue;
                }
                // Attempt to recover the fragment from higher quality
                Ts highResTs = getHighResTs(this, j);
                if (highResTs == null) {
                    System.err.println("There is no good high res ts. Path: " + ts.getUrl());
                    return;
                }
                if (!highResTs.downscale(ts, properties)) return;
            }
            mark();
        }

        public Mark mark() {
            if (fragments.isEmpty()) return mark = Mark.EMPTY;
            if (fragments.stream().map(Ts::getMark).anyMatch(mark -> mark != Mark.GOOD)) return mark = Mark.BROKEN;
            return mark = Mark.GOOD;
        }

        public Mark getMark() {
            return (mark == null) ? mark() : mark;
        }
    }

}
