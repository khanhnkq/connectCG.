package org.example.connectcg_be.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VideoOptimizationService {

    public static final long BYPASS_SIZE_THRESHOLD = 5L * 1024 * 1024; // 5MB
    public static final int TARGET_MAX_RESOLUTION = 720;               // 720p

    public record VideoMetadata(int width, int height, double duration, long sizeBytes) {}

    public record VideoProcessingResult(
            boolean wasCompressed,
            File resultVideoFile,
            File thumbnailFile,
            String contentType,
            long sizeBytes
    ) {}

    /**
     * Kiểm tra xem video có cần nén về 720p hay không.
     * Quy tắc: "nếu dưới 5mb hoặc 720p thì khỏi nén".
     * @param sizeBytes Kích thước file video tính bằng bytes
     * @param width Chiều rộng video
     * @param height Chiều cao video
     * @return true nếu cần nén, false nếu bỏ qua (bypass)
     */
    public boolean shouldCompress(long sizeBytes, int width, int height) {
        if (sizeBytes < BYPASS_SIZE_THRESHOLD) {
            log.info("Video size ({} bytes) < 5MB. Bỏ qua nén (Bypass).", sizeBytes);
            return false;
        }

        if (width <= 0 || height <= 0) {
            log.warn("Không xác định được kích thước video ({}x{}). Bỏ qua nén an toàn.", width, height);
            return false;
        }

        // Cạnh nhỏ hơn của video (chiều cao của video ngang hoặc chiều rộng của video dọc)
        int minDimension = Math.min(width, height);
        if (minDimension <= TARGET_MAX_RESOLUTION) {
            log.info("Video resolution ({}x{}, minDimension={}) <= 720p. Bỏ qua nén (Bypass).", width, height, minDimension);
            return false;
        }

        log.info("Video cần nén về 720p: size={} bytes, resolution={}x{}", sizeBytes, width, height);
        return true;
    }

    /**
     * Đọc thông tin width, height, duration từ video bằng ffprobe.
     */
    public VideoMetadata probeVideoMetadata(File videoFile) {
        if (videoFile == null || !videoFile.exists()) {
            return new VideoMetadata(0, 0, 0, 0);
        }

        long sizeBytes = videoFile.length();
        String ffprobeBin = findBinary("ffprobe");
        if (ffprobeBin == null) {
            log.warn("ffprobe không tìm thấy trong hệ thống, bỏ qua probe metadata");
            return new VideoMetadata(0, 0, 0, sizeBytes);
        }

        List<String> command = List.of(
                ffprobeBin,
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height,duration",
                "-of", "csv=p=0:s=x",
                videoFile.getAbsolutePath()
        );

        try {
            Process process = new ProcessBuilder(command).start();
            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.readLine();
            }
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ffprobe timed out probing video: {}", videoFile.getName());
                return new VideoMetadata(0, 0, 0, sizeBytes);
            }

            if (output != null && !output.isBlank()) {
                // Định dạng output: 1920x1080 hoặc 1920x1080x45.200000
                String[] parts = output.trim().split("x");
                int width = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
                int height = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
                double duration = parts.length > 2 ? Double.parseDouble(parts[2]) : 0.0;
                log.info("Probed video [{}]: {}x{}, duration={}s, size={}KB",
                        videoFile.getName(), width, height, duration, sizeBytes / 1024);
                return new VideoMetadata(width, height, duration, sizeBytes);
            }
        } catch (Exception e) {
            log.warn("Lỗi khi chạy ffprobe trên video {}: {}", videoFile.getName(), e.getMessage());
        }

        return new VideoMetadata(0, 0, 0, sizeBytes);
    }

    /**
     * Nén video xuống chuẩn 720p bằng FFmpeg H.264 / AAC.
     * Sử dụng nice -n 10 để bảo vệ an toàn CPU cho VPS 2 cores.
     */
    public boolean compressTo720p(File inputFile, File outputFile) {
        String ffmpegBin = findBinary("ffmpeg");
        if (ffmpegBin == null) {
            log.warn("ffmpeg không có sẵn trong hệ thống, không thể nén video");
            return false;
        }

        List<String> command = new ArrayList<>();
        String niceBin = findBinary("nice");
        if (niceBin != null) {
            command.add(niceBin);
            command.add("-n");
            command.add("10");
        }
        command.add(ffmpegBin);
        command.add("-y");
        command.add("-i");
        command.add(inputFile.getAbsolutePath());
        command.add("-vf");
        command.add("scale='if(gt(iw,ih),min(1280,iw),-2)':'if(gt(iw,ih),-2,min(1280,ih))':force_original_aspect_ratio=decrease");
        command.add("-c:v");
        command.add("libx264");
        command.add("-preset");
        command.add("fast");
        command.add("-crf");
        command.add("26");
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        command.add("-movflags");
        command.add("+faststart");
        command.add(outputFile.getAbsolutePath());

        try {
            log.info("Bắt đầu nén video 720p: {} -> {}", inputFile.getName(), outputFile.getName());
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.error("FFmpeg nén video quá 120s timeout, đã dừng tiến trình");
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                log.info("Nén video 720p thành công! Dung lượng giảm: {}KB -> {}KB (Tiết kiệm {}%)",
                        inputFile.length() / 1024,
                        outputFile.length() / 1024,
                        Math.round((1.0 - (double) outputFile.length() / inputFile.length()) * 100));
                return true;
            } else {
                log.error("FFmpeg nén video thất bại với exit code: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            log.error("Ngoại lệ khi thực thi FFmpeg: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Trích xuất 1 frame ảnh làm thumbnail poster tĩnh (~20KB) từ video.
     */
    public boolean extractThumbnail(File inputFile, File outputThumbFile, double duration) {
        String ffmpegBin = findBinary("ffmpeg");
        if (ffmpegBin == null) {
            log.warn("ffmpeg không có sẵn, không thể trích xuất thumbnail video");
            return false;
        }

        // Lấy frame tại giây 1.0, hoặc nếu video < 1s thì lấy 0.5s hoặc 0s
        String seekTime = "00:00:01.000";
        if (duration > 0 && duration < 1.0) {
            seekTime = String.format("00:00:00.%03d", (int) (duration * 500));
        }

        List<String> command = List.of(
                ffmpegBin,
                "-y",
                "-ss", seekTime,
                "-i", inputFile.getAbsolutePath(),
                "-vframes", "1",
                "-vf", "scale='min(640,iw)':-2",
                "-q:v", "2",
                outputThumbFile.getAbsolutePath()
        );

        try {
            Process process = new ProcessBuilder(command).start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Trích xuất thumbnail video timeout sau 15s");
                return false;
            }
            return process.exitValue() == 0 && outputThumbFile.exists() && outputThumbFile.length() > 0;
        } catch (Exception e) {
            log.warn("Không thể trích xuất thumbnail video {}: {}", inputFile.getName(), e.getMessage());
            return false;
        }
    }

    private String findBinary(String binaryName) {
        // Kiểm tra PATH chuẩn
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                File bin = new File(dir, binaryName);
                if (bin.exists() && bin.canExecute()) {
                    return bin.getAbsolutePath();
                }
            }
        }

        // Kiểm tra các đường dẫn phổ biến
        List<String> commonPaths = List.of(
                "/usr/bin/" + binaryName,
                "/usr/local/bin/" + binaryName,
                "/opt/homebrew/bin/" + binaryName,
                "/bin/" + binaryName
        );
        for (String p : commonPaths) {
            File f = new File(p);
            if (f.exists() && f.canExecute()) {
                return f.getAbsolutePath();
            }
        }
        return null;
    }
}
