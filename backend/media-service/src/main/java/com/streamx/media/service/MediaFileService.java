package com.streamx.media.service;

import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.media.config.MediaProperties;
import com.streamx.media.domain.MediaFile;
import com.streamx.media.domain.UploadPurpose;
import com.streamx.media.dto.MediaFileResponse;
import com.streamx.media.exception.StorageUnavailableException;
import com.streamx.media.repository.MediaFileRepository;
import com.streamx.media.storage.ObjectStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MediaFileService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MediaFileRepository repository;
    private final ObjectStorage storage;
    private final MediaProperties properties;

    public MediaFileService(MediaFileRepository repository, ObjectStorage storage, MediaProperties properties) {
        this.repository = repository;
        this.storage = storage;
        this.properties = properties;
    }

    public MediaFileResponse toResponse(MediaFile file) {
        return new MediaFileResponse(file.getId().toString(), file.getPurpose(), file.getFilename(),
                file.getContentType(), file.getSizeBytes(), publicUrl(file), file.getCreatedAt());
    }

    public String publicUrl(MediaFile file) {
        return properties.publicUrl("/api/v1/media/files/" + file.getId() + "/" + file.getFilename());
    }

    @Transactional(readOnly = true)
    public Page<MediaFileResponse> list(UploadPurpose purpose, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(MAX_PAGE_SIZE, size)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<MediaFile> files = purpose == null ? repository.findAll(pageable) : repository.findByPurpose(purpose, pageable);
        return files.map(this::toResponse);
    }

    @Transactional
    public void delete(UUID id) {
        MediaFile file = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("File not found: " + id));
        if (!storage.isAvailable()) {
            throw new StorageUnavailableException("Media storage is unavailable; try again shortly");
        }
        storage.delete(file.getObjectKey());
        repository.delete(file);
    }

    /** The public URL carries both id and file name; a mismatch is treated as not found. */
    @Transactional(readOnly = true)
    public MediaFile findForDelivery(String id, String filename) {
        UUID fileId;
        try {
            fileId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("File not found");
        }
        return repository.findById(fileId)
                .filter(file -> file.getFilename().equals(filename))
                .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    }
}
