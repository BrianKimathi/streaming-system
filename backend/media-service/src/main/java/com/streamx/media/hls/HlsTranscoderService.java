package com.streamx.media.hls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.repository.MediaAssetRepository;
import com.streamx.media.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class HlsTranscoderService {

    private static final Logger log = LoggerFactory.getLogger(HlsTranscoderService.class);
    private static final int MAX_FAILURE_REASON_LENGTH = 1900;

    private record Rendition(int height, String videoBitrate, String maxRate, String bufSize, String audioBitrate) {
    }

    record ProbeResult(double durationSeconds, int height, boolean hasAudio) {
    }

    private static final List<Rendition> LADDER = List.of(
            new Rendition(480, "1000k", "1100k", "2000k", "96k"),
            new Rendition(720, "2800k", "3000k", "5600k", "128k"),
            new Rendition(1080, "5000k", "5350k", "10000k", "160k")
    );

    private final StorageService storageService;
    private final MediaAssetRepository repository;
    private final ObjectMapper objectMapper;
    private final String ffmpegPath;
    private final String ffprobePath;
    private final int threads;
    private final long timeoutMinutes;

    public HlsTranscoderService(StorageService storageService,
                                MediaAssetRepository repository,
                                ObjectMapper objectMapper,
                                @Value("${media.ffmpeg-path:ffmpeg}") String ffmpegPath,
                                @Value("${media.ffprobe-path:ffprobe}") String ffprobePath,
                                @Value("${media.transcode-threads:2}") int threads,
                                @Value("${media.transcode-timeout-minutes:240}") long timeoutMinutes) {
        this.storageService = storageService;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
        this.threads = threads;
        this.timeoutMinutes = timeoutMinutes;
    }

    public static String hlsDirectory(UUID contentId) {
        return "hls/" + contentId;
    }

    public static String masterPlaylistUrl(UUID contentId) {
        return "/api/v1/media/" + contentId + "/hls/master.m3u8";
    }

    @Async("transcodeExecutor")
    public void transcode(UUID assetId) {
        MediaAsset asset = repository.findById(assetId).orElse(null);
        if (asset == null) {
            log.warn("Transcode requested for unknown media asset {}", assetId);
            return;
        }

        UUID contentId = asset.getContentId();
        String hlsDir = hlsDirectory(contentId);
        Path logFile = storageService.resolve("raw/" + contentId + "/transcode.log");

        try {
            Path input = storageService.resolve(asset.getStoragePath());
            if (!Files.isRegularFile(input)) {
                throw new IllegalStateException("Source file is missing: " + asset.getStoragePath());
            }

            ProbeResult probe = probe(input);
            log.info("Transcoding contentId {} ({}s, {}p, audio={})",
                    contentId, Math.round(probe.durationSeconds()), probe.height(), probe.hasAudio());

            storageService.deleteRecursively(hlsDir);
            Path outputDir = storageService.resolve(hlsDir);
            Files.createDirectories(outputDir);

            List<String> command = buildFfmpegCommand(input, outputDir, probe);
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start();

            if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("ffmpeg timed out after " + timeoutMinutes + " minutes");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg exited with code " + process.exitValue() + ": " + tail(logFile));
            }
            if (!Files.isRegularFile(outputDir.resolve("master.m3u8"))) {
                throw new IllegalStateException("ffmpeg finished without producing master.m3u8");
            }

            asset.setStatus(MediaProcessingStatus.COMPLETED);
            asset.setDurationSeconds((int) Math.round(probe.durationSeconds()));
            asset.setMasterPlaylistUrl(masterPlaylistUrl(contentId));
            asset.setFailureReason(null);
            repository.save(asset);
            log.info("HLS transcoding completed for contentId {}", contentId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            markFailed(asset, "Transcoding interrupted");
        } catch (Exception e) {
            log.error("HLS transcoding failed for contentId {}", contentId, e);
            markFailed(asset, e.getMessage());
        }
    }

    ProbeResult probe(Path input) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                ffprobePath, "-v", "error",
                "-show_entries", "format=duration:stream=codec_type,height",
                "-of", "json", input.toString())
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(2, TimeUnit.MINUTES) || process.exitValue() != 0) {
            process.destroyForcibly();
            throw new IllegalStateException("ffprobe could not read the uploaded file; is it a valid video?");
        }

        JsonNode root = objectMapper.readTree(output);
        int height = 0;
        boolean hasAudio = false;
        for (JsonNode stream : root.path("streams")) {
            String type = stream.path("codec_type").asText();
            if ("video".equals(type) && height == 0) {
                height = stream.path("height").asInt(0);
            } else if ("audio".equals(type)) {
                hasAudio = true;
            }
        }
        if (height <= 0) {
            throw new IllegalStateException("No video stream found in uploaded file");
        }
        double duration = root.path("format").path("duration").asDouble(0);
        return new ProbeResult(duration, height, hasAudio);
    }

    private List<String> buildFfmpegCommand(Path input, Path outputDir, ProbeResult probe) {
        List<Rendition> renditions = new ArrayList<>();
        for (Rendition rendition : LADDER) {
            if (rendition.height() <= probe.height()) {
                renditions.add(rendition);
            }
        }
        if (renditions.isEmpty()) {
            int evenHeight = Math.max(2, probe.height() - (probe.height() % 2));
            renditions.add(new Rendition(evenHeight, "700k", "770k", "1400k", "96k"));
        }

        StringBuilder filter = new StringBuilder();
        if (renditions.size() == 1) {
            filter.append("[0:v]scale=-2:").append(renditions.get(0).height()).append("[v0]");
        } else {
            filter.append("[0:v]split=").append(renditions.size());
            for (int i = 0; i < renditions.size(); i++) {
                filter.append("[s").append(i).append(']');
            }
            for (int i = 0; i < renditions.size(); i++) {
                filter.append(";[s").append(i).append("]scale=-2:")
                        .append(renditions.get(i).height()).append("[v").append(i).append(']');
            }
        }

        List<String> cmd = new ArrayList<>();
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            cmd.addAll(List.of("nice", "-n", "10"));
        }
        cmd.addAll(List.of(ffmpegPath, "-hide_banner", "-nostdin", "-y", "-i", input.toString(),
                "-filter_complex", filter.toString()));

        StringBuilder streamMap = new StringBuilder();
        for (int i = 0; i < renditions.size(); i++) {
            Rendition r = renditions.get(i);
            cmd.addAll(List.of("-map", "[v" + i + "]"));
            cmd.addAll(List.of("-b:v:" + i, r.videoBitrate(), "-maxrate:v:" + i, r.maxRate(), "-bufsize:v:" + i, r.bufSize()));
            if (probe.hasAudio()) {
                cmd.addAll(List.of("-map", "0:a:0", "-b:a:" + i, r.audioBitrate()));
            }
            if (i > 0) {
                streamMap.append(' ');
            }
            streamMap.append("v:").append(i);
            if (probe.hasAudio()) {
                streamMap.append(",a:").append(i);
            }
            streamMap.append(",name:").append(r.height()).append('p');
        }

        cmd.addAll(List.of(
                "-c:v", "libx264", "-preset", "veryfast", "-profile:v", "main", "-pix_fmt", "yuv420p",
                "-g", "48", "-keyint_min", "48", "-sc_threshold", "0",
                "-threads", String.valueOf(threads)));
        if (probe.hasAudio()) {
            cmd.addAll(List.of("-c:a", "aac", "-ac", "2", "-ar", "48000"));
        }
        cmd.addAll(List.of(
                "-f", "hls",
                "-hls_time", "6",
                "-hls_playlist_type", "vod",
                "-hls_flags", "independent_segments",
                "-hls_segment_filename", outputDir.resolve("%v_%03d.ts").toString(),
                "-master_pl_name", "master.m3u8",
                "-var_stream_map", streamMap.toString(),
                outputDir.resolve("%v.m3u8").toString()));
        return cmd;
    }

    private void markFailed(MediaAsset asset, String reason) {
        String message = reason == null ? "Unknown transcoding error" : reason;
        if (message.length() > MAX_FAILURE_REASON_LENGTH) {
            message = message.substring(message.length() - MAX_FAILURE_REASON_LENGTH);
        }
        asset.setStatus(MediaProcessingStatus.FAILED);
        asset.setFailureReason(message);
        asset.setMasterPlaylistUrl(null);
        repository.save(asset);
    }

    private static String tail(Path logFile) {
        try {
            String content = Files.readString(logFile, StandardCharsets.UTF_8);
            return content.length() > 1200 ? content.substring(content.length() - 1200) : content;
        } catch (IOException e) {
            return "(no ffmpeg log)";
        }
    }
}
