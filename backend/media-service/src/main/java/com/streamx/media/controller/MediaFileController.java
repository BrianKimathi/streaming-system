package com.streamx.media.controller;

import com.streamx.media.delivery.ObjectDelivery;
import com.streamx.media.domain.MediaFile;
import com.streamx.media.service.MediaFileService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/** Public delivery of uploaded images and trailers (no token); files are immutable once created. */
@RestController
@RequestMapping("/api/v1/media/files")
public class MediaFileController {

    static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final MediaFileService mediaFileService;
    private final ObjectDelivery delivery;

    public MediaFileController(MediaFileService mediaFileService, ObjectDelivery delivery) {
        this.mediaFileService = mediaFileService;
        this.delivery = delivery;
    }

    @GetMapping("/{fileId}/{filename:.+}")
    public void serve(@PathVariable("fileId") String fileId,
                      @PathVariable("filename") String filename,
                      HttpServletRequest request,
                      HttpServletResponse response) throws IOException {
        MediaFile file = mediaFileService.findForDelivery(fileId, filename);
        delivery.write(request, response, file.getObjectKey(), file.getSizeBytes(), file.getContentType(),
                CACHE_CONTROL, "\"" + file.getId() + "\"");
    }
}
