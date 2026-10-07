package com.streamx.media.hls;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamx.media.config.MediaProperties;
import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaProcessingStatus;
import com.streamx.media.repository.MediaAssetRepository;
import com.streamx.media.service.AssetStatusUpdater;
import com.streamx.media.storage.ObjectKeys;
import com.streamx.media.storage.ObjectStat;
import com.streamx.media.storage.ObjectStorage;
import com.streamx.media.storage.ScratchSpace;
import com.streamx.media.upload.Filenames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Service
public class HlsTranscoderService {

    private static final Logger log = LoggerFactory.getLogger(HlsTranscoderService.class);
    private static final long PROGRESS_WRITE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(3);

    private record Rendition(int height, String videoBitrate, String maxRate, String bufSize, String audioBitrate) {
    }

    record ProbeResult(double durationSeconds, int height, boolean hasAudio) {
    }

    private static final List<Rendition> LADDER = List.of(
            new Rendition(480, "1000k", "1100k", "2000k", "96k"),
            new Rendition(720, "2800k", "3000k", "5600k", "128k"),
            new Rendition(1080, "5000k", "5350k", "10000k", "160k")
    );

    private final ObjectStorage storage;
    private final ScratchSpace scratch;
    private final MediaAssetRepository repository;
    private final AssetStatusUpdater statusUpdater;
    private final ObjectMapper objectMapper;
    private final String ffmpegPath;
    private final String ffprobePath;
    private final int threads;
    private final long timeoutMinutes;

    public HlsTranscoderService(ObjectStorage storage,
                                ScratchSpace scratch,
                                MediaAssetRepository repository,
                                AssetStatusUpdater statusUpdater,
                                ObjectMapper objectMapper,
                                MediaProperties properties) {
        this.storage = storage;
        this.scratch = scratch;
        this.repository = repository;
        this.statusUpdater = statusUpdater;
        this.objectMapper = objectMapper;
        this.ffmpegPath = properties.ffmpegPath();
        this.ffprobePath = properties.ffprobePath();
        this.threads = Math.max(1, properties.transcodeThreads());
        this.timeoutMinutes = properties.transcodeTimeoutMinutes();
    }

    /** Storage key of the master playlist; stream URLs are built per viewer with a stream token. */
    public static String masterPlaylistKey(UUID contentId) {
        return ObjectKeys.hls(contentId, "master.m3u8");
    }

    @Async("transcodeExecutor")
    public void transcode(UUID assetId) {
        MediaAsset asset = repository.findById(assetId).orElse(null);
        if (asset == null || asset.getStatus() != MediaProcessingStatus.PROCESSING || asset.getPendingUploadId() != null) {
            log.debug("Skipping transcode of {}: not ready for processing", assetId);
            return;
        }
        UUID contentId = asset.getContentId();
        Path workDir = null;
        try {
            workDir = scratch.freshDirectory("transcode", assetId);
            Path outputDir = Files.createDirectories(workDir.resolve("hls"));
            Path logFile = workDir.resolve("ffmpeg.log");
            String extension = Filenames.extension(asset.getStoragePath());
            Path input = workDir.resolve(extension.isEmpty() ? "source" : "source." + extension);

            if (asset.getStoragePath() == null || storage.stat(asset.getStoragePath()).isEmpty()) {
                throw new IllegalStateException("Source file is missing; upload the video again");
            }
            storage.downloadToFile(asset.getStoragePath(), input);

            ProbeResult probe = probe(input);
            log.info("Transcoding contentId {} ({}s, {}p, audio={})",
                    contentId, Math.round(probe.durationSeconds()), probe.height(), probe.hasAudio());
            repository.updateProgress(assetId, 0);

            runFfmpeg(assetId, buildFfmpegCommand(input, outputDir, probe), logFile, probe.durationSeconds());
            if (!Files.isRegularFile(outputDir.resolve("master.m3u8"))) {
                throw new IllegalStateException("ffmpeg finished without producing master.m3u8");
            }
            publish(contentId, outputDir);

            MediaAsset current = repository.findById(assetId).orElse(null);
            if (current == null) {
                return;
            }
            current.setStatus(MediaProcessingStatus.COMPLETED);
            current.setDurationSeconds((int) Math.round(probe.durationSeconds()));
            current.setMasterPlaylistUrl(masterPlaylistKey(contentId));
            current.setFailureReason(null);
            current.setProgressPercent(null);
            repository.save(current);
            log.info("HLS transcoding completed for contentId {}", contentId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            statusUpdater.markFailed(assetId, "Transcoding interrupted");
        } catch (Exception e) {
            log.error("HLS transcoding failed for contentId {}", contentId, e);
            statusUpdater.markFailed(assetId, e.getMessage());
        } finally {
            scratch.deleteQuietly(workDir);
        }
    }

    private void runFfmpeg(UUID assetId, List<String> command, Path logFile, double durationSeconds)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectError(logFile.toFile())
                .start();
        Thread progressReader = Thread.ofPlatform().daemon().name("ffmpeg-progress-" + assetId).start(() -> {
            int lastPercent = 0;
            long lastWrite = System.nanoTime();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    OptionalLong micros = FfmpegProgress.outTimeMicros(line);
                    if (micros.isEmpty()) {
                        continue;
                    }
                    int percent = FfmpegProgress.percent(micros.getAsLong(), durationSeconds);
                    long now = System.nanoTime();
                    if (percent > lastPercent && now - lastWrite >= PROGRESS_WRITE_INTERVAL_NANOS) {
                        repository.updateProgress(assetId, percent);
                        lastPercent = percent;
                        lastWrite = now;
                    }
                }
            } catch (Exception e) {
                log.debug("Stopped reading ffmpeg progress for {}: {}", assetId, e.toString());
            }
        });

        if (!process.waitFor(timeoutMinutes, TimeUnit.MINUTES)) {
            process.destroyForcibly();
            progressReader.join(5_000);
            throw new IllegalStateException("ffmpeg timed out after " + timeoutMinutes + " minutes");
        }
        progressReader.join(5_000);
        if (process.exitValue() != 0) {
            throw new IllegalStateException("ffmpeg exited with code " + process.exitValue() + ": " + tail(logFile));
        }
    }

    /*
     * Segments first, then variant playlists, then the master, so a player never sees a playlist whose segments are
     * not uploaded yet. Objects of the previous rendition that the new one does not overwrite are removed last.
     */
    private void publish(UUID contentId, Path outputDir) throws IOException {
        String prefix = ObjectKeys.hlsPrefix(contentId);
        Set<String> previous = new HashSet<>();
        for (ObjectStat stat : storage.list(prefix)) {
            previous.add(stat.key());
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(outputDir)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingInt(HlsTranscoderService::publishOrder)
                            .thenComparing(path -> path.getFileName().toString()))
                    .toList();
        }
        Set<String> published = new HashSet<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            String key = ObjectKeys.hls(contentId, name);
            storage.uploadFile(key, file, name.endsWith(".m3u8") ? "application/vnd.apple.mpegurl" : "video/mp2t");
            published.add(key);
        }
        for (String key : previous) {
            if (!published.contains(key)) {
                storage.delete(key);
            }
        }
    }

    private static int publishOrder(Path file) {
        String name = file.getFileName().toString();
        if (name.equals("master.m3u8")) {
            return 2;
        }
        return name.endsWith(".m3u8") ? 1 : 0;
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

    List<String> buildFfmpegCommand(Path input, Path outputDir, ProbeResult probe) {
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
        cmd.addAll(List.of(ffmpegPath, "-hide_banner", "-nostdin", "-nostats", "-y",
                "-progress", "pipe:1",
                "-i", input.toString(),
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

    private static String tail(Path logFile) {
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = file.length();
            int size = (int) Math.min(1200, length);
            byte[] bytes = new byte[size];
            file.seek(length - size);
            file.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "(no ffmpeg log)";
        }
    }
}
