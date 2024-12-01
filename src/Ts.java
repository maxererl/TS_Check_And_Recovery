import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Properties;

public class Ts {
    private final String tsUrl;
    private M3U8.Mark mark;

    public Ts(String tsUrl) {
        this.tsUrl = tsUrl;
    }

    private M3U8.Mark mark() {
        if (!this.isExists() || !this.isComplete()) mark = M3U8.Mark.BROKEN;
        else mark = M3U8.Mark.GOOD;
        return mark;
    }

    // Check if TS fragment exists by sending a HEAD request
    private boolean isExists() {
        return FileConnection.isExists(tsUrl);
    }

    // Check if the TS fragment is playable using ffmpeg
    private boolean isComplete() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-v", "error", "-i", tsUrl, "-f", "null", "-").start();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while (errorReader.ready()) {
                System.out.println(errorReader.readLine());
            }
            int exitCode = process.waitFor();
            return exitCode == 0; // Return true if ffmpeg command is successful
        } catch (IOException | InterruptedException e) {
            System.out.println("Error checking TS integrity: " + e.getMessage());
            return false;
        }
    }

    // Downscale higher-resolution TS file to a lower resolution using ffmpeg
    public boolean downscale(Ts problemTs, Properties properties) {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-y", "-i", tsUrl,
                    "-vf", "scale="+((String) properties.get("RESOLUTION")).replace("x", ":"),
                    "-c:v", ((String) properties.get("CODECS")).split(";")[0],
                    "-c:a", ((String) properties.get("CODECS")).split(";")[1],
                    problemTs.tsUrl).redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            process.waitFor();
            // Return true if the downscaling is successful
            if (problemTs.mark() != M3U8.Mark.GOOD) throw new Exception("Bad marker after downscaling");
            System.out.println("Successfully downscaled fragment: " + problemTs.getUrl());
            return true;
        } catch (Exception e) {
            System.err.println("Error downscaling TS fragment: " + problemTs.getUrl()+ ". " + e.getMessage());
            return false;
        }
    }

    public String getUrl() {
        return tsUrl;
    }

    public M3U8.Mark getMark() {
        return (mark == null) ? mark() : mark;
    }
}
