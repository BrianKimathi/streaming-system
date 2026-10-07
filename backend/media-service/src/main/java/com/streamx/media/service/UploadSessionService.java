package com.streamx.media.service;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.media.config.MediaProperties;
import com.streamx.media.domain.MediaAsset;
import com.streamx.media.domain.MediaFile;
import com.streamx.media.domain.UploadPart;
import com.streamx.media.domain.UploadPurpose;
import com.streamx.media.domain.UploadSession;
import com.streamx.media.domain.UploadSessionStatus;
import com.streamx.media.dto.CompleteUploadResponse;
import com.streamx.media.dto.CreateUploadRequest;
import com.streamx.media.dto.PartUploadResponse;
import com.streamx.media.dto.UploadSessionResponse;
import com.streamx.media.exception.ConflictException;
import com.streamx.media.exception.StorageUnavailableException;
import com.streamx.media.repository.MediaAssetRepository;
import com.streamx.media.repository.MediaFileRepository;
import com.streamx.media.repository.UploadPartRepository;
import com.streamx.media.repository.UploadSessionRepository;
import com.streamx.media.storage.ObjectKeys;
import com.streamx.media.storage.ObjectStat;
import com.streamx.media.storage.ObjectStorage;
import com.streamx.media.upload.UploadPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chunked uploads: OPEN sessions accept parts; complete assembles them (COMPLETED); abort or expiry discards them
 * (ABORTED). COMPLETED and ABORTED are terminal.
 */
@Service
public class UploadSessionService {

    private static final Logger log = LoggerFactory.getLogger(UploadSessionService.class);
    private static final String PART_CONTENT_TYPE = "application/octet-stream";

    private final UploadSessionRepository sessionRepository;
    private final UploadPartRepository partRepository;
    private final MediaFileRepository fileRepository;
    private final MediaAssetRepository assetRepository;
    private final ObjectStorage storage;
    private final UploadPolicy policy;
    private final MediaService mediaService;
    private final MediaFileService fileService;
    private final MediaProperties properties;
    private final TransactionTemplate transactions;

    public UploadSessionService(UploadSessionRepository sessionRepository,
                                UploadPartRepository partRepository,
                                MediaFileRepository fileRepository,
                                MediaAssetRepository assetRepository,
                                ObjectStorage storage,
                                UploadPolicy policy,
                                MediaService mediaService,
                                MediaFileService fileService,
                                MediaProperties properties,
                                PlatformTransactionManager transactionManager) {
        this.sessionRepository = sessionRepository;
        this.partRepository = partRepository;
        this.fileRepository = fileRepository;
        this.assetRepository = assetRepository;
        this.storage = storage;
        this.policy = policy;
        this.mediaService = mediaService;
        this.fileService = fileService;
        this.properties = properties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public UploadSessionResponse create(CreateUploadRequest request, String accountId) {
        UploadPolicy.ValidatedUpload upload = policy.validate(request);
        requireStorage();
        if (upload.purpose() == UploadPurpose.VIDEO) {
            assetRepository.findByContentId(upload.contentId())
                    .filter(AssetStatusUpdater::isBusy)
                    .ifPresent(asset -> {
                        throw new ConflictException("A video for this title is already being processed");
                    });
        }
        UploadSession session = new UploadSession();
        session.setPurpose(upload.purpose());
        session.setContentId(upload.contentId());
        session.setFilename(upload.filename());
        session.setContentType(upload.contentType());
        session.setSizeBytes(upload.sizeBytes());
        session.setChunkSizeBytes(upload.chunkSizeBytes());
        session.setTotalParts(upload.totalParts());
        session.setStatus(UploadSessionStatus.OPEN);
        session.setCreatedBy(accountId);
        UploadSession saved = sessionRepository.save(session);
        log.info("Upload session {} opened: {} {} ({} bytes, {} parts)", saved.getId(), saved.getPurpose(),
                saved.getFilename(), saved.getSizeBytes(), saved.getTotalParts());
        return toResponse(saved, List.of());
    }

    public UploadSessionResponse get(UUID uploadId) {
        UploadSession session = find(uploadId);
        return toResponse(session, partRepository.findPartNumbers(uploadId));
    }

    /** Streams one part straight into storage; re-sending a part overwrites it. */
    public PartUploadResponse putPart(UUID uploadId, int partNumber, long contentLength, InputStream body) {
        UploadSession session = find(uploadId);
        requireOpen(session);
        long expected = UploadPolicy.expectedPartSize(session.getSizeBytes(), session.getChunkSizeBytes(), partNumber);
        if (contentLength < 0) {
            throw new BadRequestException("Content-Length header is required");
        }
        if (contentLength != expected) {
            throw new BadRequestException("Part " + partNumber + " must be exactly " + expected
                    + " bytes (received Content-Length " + contentLength + ")");
        }
        requireStorage();

        String key = ObjectKeys.uploadPart(uploadId, partNumber);
        storage.write(key, body, expected, PART_CONTENT_TYPE);

        if (find(uploadId).getStatus() != UploadSessionStatus.OPEN) {
            storage.delete(key);
            throw new ConflictException("Upload session is no longer open");
        }
        recordPart(uploadId, partNumber, expected);
        sessionRepository.touch(uploadId, LocalDateTime.now());
        return new PartUploadResponse(partNumber, expected, partRepository.findPartNumbers(uploadId));
    }

    private void recordPart(UUID uploadId, int partNumber, long size) {
        if (partRepository.existsByUploadIdAndPartNumber(uploadId, partNumber)) {
            return;
        }
        try {
            partRepository.save(new UploadPart(uploadId, partNumber, size));
        } catch (DataIntegrityViolationException e) {
            // A concurrent retry of the same part recorded it first; the expected size is deterministic.
            log.debug("Part {} of {} already recorded", partNumber, uploadId);
        }
    }

    public CompleteUploadResponse complete(UUID uploadId) {
        UploadSession session = find(uploadId);
        requireOpen(session);
        requireStorage();
        verifyAllPartsStored(session);

        if (session.getPurpose() == UploadPurpose.VIDEO) {
            MediaAsset asset = mediaService.startVideoFromUpload(session);
            return new CompleteUploadResponse(UploadPurpose.VIDEO, null, mediaService.mapToResponse(asset));
        }

        String target = ObjectKeys.file(uploadId, session.getFilename());
        storage.compose(target, partKeys(session), session.getContentType());
        Optional<ObjectStat> assembled = storage.stat(target);
        if (assembled.isEmpty() || assembled.get().size() != session.getSizeBytes()) {
            storage.delete(target);
            throw new ConflictException("The assembled file does not match the declared size; upload it again");
        }

        MediaFile file = transactions.execute(status -> {
            session.setStatus(UploadSessionStatus.COMPLETED);
            sessionRepository.save(session);
            MediaFile created = new MediaFile();
            created.setId(uploadId);
            created.setPurpose(session.getPurpose());
            created.setFilename(session.getFilename());
            created.setContentType(session.getContentType());
            created.setSizeBytes(session.getSizeBytes());
            created.setObjectKey(target);
            created.setContentId(session.getContentId());
            created.setCreatedBy(session.getCreatedBy());
            return fileRepository.save(created);
        });
        discardParts(uploadId);
        log.info("Upload {} completed as public {} file {}", uploadId, session.getPurpose(), target);
        return new CompleteUploadResponse(session.getPurpose(), fileService.toResponse(file), null);
    }

    public UploadSessionResponse abort(UUID uploadId) {
        UploadSession session = find(uploadId);
        if (session.getStatus() == UploadSessionStatus.COMPLETED) {
            throw new ConflictException("Upload session is already completed");
        }
        if (session.getStatus() == UploadSessionStatus.OPEN) {
            requireStorage();
            session.setStatus(UploadSessionStatus.ABORTED);
            sessionRepository.save(session);
            discardParts(uploadId);
        }
        return toResponse(session, List.of());
    }

    @Scheduled(fixedDelayString = "${media.upload-cleanup-interval-ms:3600000}",
            initialDelayString = "${media.upload-cleanup-initial-delay-ms:300000}")
    public void abortStaleSessions() {
        abortSessionsIdleSince(LocalDateTime.now().minusHours(Math.max(1, properties.uploadSessionTtlHours())));
    }

    /** Aborts OPEN sessions without activity since {@code cutoff} and deletes their parts; returns how many. */
    public int abortSessionsIdleSince(LocalDateTime cutoff) {
        if (!storage.isAvailable()) {
            return 0;
        }
        int aborted = 0;
        for (UploadSession session : sessionRepository.findByStatusAndUpdatedAtBefore(UploadSessionStatus.OPEN, cutoff)) {
            try {
                storage.deletePrefix(ObjectKeys.uploadPrefix(session.getId()));
                partRepository.deleteByUploadId(session.getId());
                session.setStatus(UploadSessionStatus.ABORTED);
                sessionRepository.save(session);
                aborted++;
            } catch (Exception e) {
                log.warn("Could not expire upload session {}: {}", session.getId(), e.toString());
            }
        }
        if (aborted > 0) {
            log.info("Aborted {} stale upload session(s)", aborted);
        }
        return aborted;
    }

    private void verifyAllPartsStored(UploadSession session) {
        UUID uploadId = session.getId();
        List<Integer> missing = UploadPolicy.missingParts(session.getTotalParts(), partRepository.findPartNumbers(uploadId));
        if (missing.isEmpty()) {
            Map<String, Long> stored = new HashMap<>();
            for (ObjectStat stat : storage.list(ObjectKeys.uploadPrefix(uploadId))) {
                stored.put(stat.key(), stat.size());
            }
            List<Integer> lost = new ArrayList<>();
            for (int part = 1; part <= session.getTotalParts(); part++) {
                Long size = stored.get(ObjectKeys.uploadPart(uploadId, part));
                long expected = UploadPolicy.expectedPartSize(session.getSizeBytes(), session.getChunkSizeBytes(), part);
                if (size == null || size != expected) {
                    lost.add(part);
                }
            }
            if (!lost.isEmpty()) {
                partRepository.deleteParts(uploadId, lost);
                missing = lost;
            }
        }
        if (!missing.isEmpty()) {
            String listed = missing.stream().limit(20).map(String::valueOf).collect(Collectors.joining(", "));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("missingParts", missing);
            throw new ConflictException("Upload is missing " + missing.size() + " part(s): " + listed
                    + (missing.size() > 20 ? ", ..." : ""), details);
        }
    }

    private void discardParts(UUID uploadId) {
        try {
            storage.deletePrefix(ObjectKeys.uploadPrefix(uploadId));
            partRepository.deleteByUploadId(uploadId);
        } catch (StorageUnavailableException e) {
            log.warn("Could not delete parts of upload {} now: {}", uploadId, e.getMessage());
        }
    }

    private static List<String> partKeys(UploadSession session) {
        List<String> keys = new ArrayList<>(session.getTotalParts());
        for (int part = 1; part <= session.getTotalParts(); part++) {
            keys.add(ObjectKeys.uploadPart(session.getId(), part));
        }
        return keys;
    }

    private UploadSession find(UUID uploadId) {
        return sessionRepository.findById(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload session not found: " + uploadId));
    }

    private static void requireOpen(UploadSession session) {
        if (session.getStatus() != UploadSessionStatus.OPEN) {
            throw new ConflictException("Upload session is " + session.getStatus().name().toLowerCase(Locale.ROOT));
        }
    }

    private void requireStorage() {
        if (!storage.isAvailable()) {
            throw new StorageUnavailableException("Media storage is unavailable; try again shortly");
        }
    }

    private static UploadSessionResponse toResponse(UploadSession session, List<Integer> receivedParts) {
        return new UploadSessionResponse(
                session.getId().toString(),
                session.getPurpose(),
                session.getContentId() == null ? null : session.getContentId().toString(),
                session.getFilename(),
                session.getSizeBytes(),
                session.getChunkSizeBytes(),
                session.getTotalParts(),
                receivedParts,
                session.getStatus(),
                session.getCreatedAt());
    }
}
