package com.streamx.media.support;

import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import com.streamx.media.exception.StorageUnavailableException;
import com.streamx.media.storage.ObjectStat;
import com.streamx.media.storage.ObjectStorage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/** Backs a Mockito mock of {@link ObjectStorage} with a map so tests can exercise full flows without MinIO. */
public final class InMemoryStorage {

    public final Map<String, byte[]> objects = new ConcurrentSkipListMap<>();

    private InMemoryStorage() {
    }

    public static InMemoryStorage attach(ObjectStorage mock) {
        InMemoryStorage memory = new InMemoryStorage();
        when(mock.isAvailable()).thenReturn(true);
        when(mock.stat(anyString())).thenAnswer(inv -> memory.stat(inv.getArgument(0)));
        when(mock.read(anyString(), anyLong(), anyLong())).thenAnswer(inv ->
                memory.read(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(mock.list(anyString())).thenAnswer(inv -> memory.list(inv.getArgument(0)));
        doAnswer(inv -> {
            InputStream in = inv.getArgument(1);
            long size = inv.getArgument(2);
            byte[] data = in.readNBytes((int) size);
            if (data.length != size) {
                throw new BadRequestException("Request body ended early");
            }
            memory.objects.put(inv.getArgument(0), data);
            return null;
        }).when(mock).write(anyString(), any(), anyLong(), any());
        doAnswer(inv -> {
            memory.objects.put(inv.getArgument(0), Files.readAllBytes(inv.<Path>getArgument(1)));
            return null;
        }).when(mock).uploadFile(anyString(), any(), any());
        doAnswer(inv -> {
            Files.write(inv.<Path>getArgument(1), memory.require(inv.getArgument(0)));
            return null;
        }).when(mock).downloadToFile(anyString(), any());
        doAnswer(inv -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            for (String source : inv.<List<String>>getArgument(1)) {
                byte[] part = memory.objects.get(source);
                if (part == null) {
                    throw new StorageUnavailableException("Missing source " + source);
                }
                out.writeBytes(part);
            }
            memory.objects.put(inv.getArgument(0), out.toByteArray());
            return null;
        }).when(mock).compose(anyString(), any(), any());
        doAnswer(inv -> {
            memory.objects.remove(inv.<String>getArgument(0));
            return null;
        }).when(mock).delete(anyString());
        doAnswer(inv -> {
            String prefix = inv.getArgument(0);
            memory.objects.keySet().removeIf(key -> key.startsWith(prefix));
            return null;
        }).when(mock).deletePrefix(anyString());
        return memory;
    }

    public void put(String key, byte[] data) {
        objects.put(key, data);
    }

    public boolean hasPrefix(String prefix) {
        return objects.keySet().stream().anyMatch(key -> key.startsWith(prefix));
    }

    private Optional<ObjectStat> stat(String key) {
        byte[] data = objects.get(key);
        return data == null ? Optional.empty()
                : Optional.of(new ObjectStat(key, data.length, "etag-" + Arrays.hashCode(data), Instant.EPOCH));
    }

    private InputStream read(String key, long offset, long length) {
        byte[] data = require(key);
        int from = (int) offset;
        int to = length < 0 ? data.length : (int) Math.min(data.length, offset + length);
        return new ByteArrayInputStream(Arrays.copyOfRange(data, from, to));
    }

    private List<ObjectStat> list(String prefix) {
        return objects.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .map(key -> stat(key).orElseThrow())
                .toList();
    }

    private byte[] require(String key) {
        byte[] data = objects.get(key);
        if (data == null) {
            throw new ResourceNotFoundException("File not found");
        }
        return data;
    }
}
