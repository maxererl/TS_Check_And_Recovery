import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class Main {
    public static void main(String[] args) {

        // Path for test
        String url = "examples/id/id.m3u8";
        // file:///pathToJavaFile/examples/id/id.m3u8
        // create m3u8 from mp4
        // ffmpeg -i big.mp4 -vf scale=640:240 -g 60 -hls_time 2 -hls_list_size 0 -hls_segment_size 500000 output.m3u8

        // Step 1: Parse the M3U8 file
        String m3u8FilePath;
        M3U8 mainFile;
        try (Scanner sc = new Scanner(System.in)) {
            Optional<M3U8> m3U8Optional;
            do {
                System.out.println("Enter playlist or master m3u8 file path:");
                m3u8FilePath = sc.nextLine();
                m3U8Optional = m3u8FilePath.isBlank() ? Optional.empty() : checkAndParseFile(m3u8FilePath);
            } while (m3U8Optional.isEmpty());
            mainFile = m3U8Optional.get();
        }

        // Step 2: Check availability and integrity of each TS fragment and repair
        mainFile.repair();
        System.out.println("M3U8 playlist repaired.");

        mainFile.printStatus();

        mainFile.updateM3U8();
        System.out.println("Updated M3U8 playlist generated.");
    }

    public static Optional<M3U8> checkAndParseFile(String path) {
        try {
            return Optional.of(new M3U8(path));
        } catch(IOException e) {
            System.err.println("Cannot parse file, please choose different path: " + e.getMessage());
            return Optional.empty();
        }
    }
}