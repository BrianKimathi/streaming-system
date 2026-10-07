import axios from 'axios';
import { errorMessage } from '../api/client';
import { adminService } from '../services/adminService';
import type { UploadCompleteResponse, UploadPurpose, UploadSession } from '../types';

export type UploadPhase = 'preparing' | 'uploading' | 'finalizing';

export interface UploadProgress {
  phase: UploadPhase;
  uploadedBytes: number;
  totalBytes: number;
  percent: number;
  partsDone: number;
  totalParts: number;
}

export interface ChunkedUploadOptions {
  file: File;
  purpose: UploadPurpose;
  /** Required for VIDEO uploads: the movie or episode id. */
  contentId?: string;
  signal: AbortSignal;
  onProgress?: (progress: UploadProgress) => void;
}

export class UploadCancelledError extends Error {
  constructor() {
    super('Upload cancelled');
    this.name = 'UploadCancelledError';
  }
}

export const isUploadCancelled = (err: unknown): err is UploadCancelledError => err instanceof UploadCancelledError;

const MAX_PART_ATTEMPTS = 3;
const RETRY_BASE_DELAY_MS = 1500;
const RESUME_KEY_PREFIX = 'streamx_admin_upload:';

const CONTENT_TYPES_BY_EXTENSION: Record<string, string> = {
  mp4: 'video/mp4',
  m4v: 'video/x-m4v',
  mov: 'video/quicktime',
  mkv: 'video/x-matroska',
  webm: 'video/webm',
  avi: 'video/x-msvideo',
  mpg: 'video/mpeg',
  mpeg: 'video/mpeg',
  ts: 'video/mp2t',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
};

export function fileExtension(name: string): string {
  const dot = name.lastIndexOf('.');
  return dot >= 0 ? name.slice(dot + 1).toLowerCase() : '';
}

/** Browsers leave `File.type` empty for some containers (e.g. .mkv on Windows); fall back to the extension. */
export function guessContentType(file: File): string {
  return file.type || CONTENT_TYPES_BY_EXTENSION[fileExtension(file.name)] || 'application/octet-stream';
}

function resumeKey(file: File, purpose: UploadPurpose, contentId?: string): string {
  return `${RESUME_KEY_PREFIX}${purpose}:${contentId ?? '-'}:${file.name}:${file.size}:${file.lastModified}`;
}

function isCancel(err: unknown, signal: AbortSignal): boolean {
  return signal.aborted || axios.isCancel(err) || (axios.isAxiosError(err) && err.code === 'ERR_CANCELED');
}

function isRetriable(err: unknown): boolean {
  if (!axios.isAxiosError(err)) return false;
  const status = err.response?.status;
  if (status === undefined) return true;
  return status >= 500 || status === 408 || status === 429;
}

function sleep(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new UploadCancelledError());
      return;
    }
    const onAbort = () => {
      window.clearTimeout(timer);
      reject(new UploadCancelledError());
    };
    const timer = window.setTimeout(() => {
      signal.removeEventListener('abort', onAbort);
      resolve();
    }, ms);
    signal.addEventListener('abort', onAbort, { once: true });
  });
}

/** Reuses the server session of an earlier interrupted upload of the same file, otherwise opens a new one. */
async function openSession(options: ChunkedUploadOptions, key: string): Promise<UploadSession> {
  const { file, purpose, contentId } = options;
  const savedId = localStorage.getItem(key);
  if (savedId) {
    try {
      const existing = await adminService.getUpload(savedId);
      if (existing.status === 'OPEN' && existing.sizeBytes === file.size && existing.purpose === purpose) return existing;
    } catch (err) {
      if (isCancel(err, options.signal)) throw new UploadCancelledError();
    }
    localStorage.removeItem(key);
  }
  const session = await adminService.createUpload({
    filename: file.name,
    contentType: guessContentType(file),
    sizeBytes: file.size,
    purpose,
    ...(contentId ? { contentId } : {}),
  });
  localStorage.setItem(key, session.uploadId);
  return session;
}

/**
 * Uploads a file through the media service's chunked upload API: each part is a raw PUT of at most
 * `chunkSizeBytes` (keeps every request under Cloudflare's body limit), failed parts are retried with backoff,
 * and parts the server already has are skipped so an interrupted upload of the same file resumes.
 * Aborting `signal` cancels the upload and discards the server session.
 */
export async function chunkedUpload(options: ChunkedUploadOptions): Promise<UploadCompleteResponse> {
  const { file, signal, onProgress } = options;
  const totalBytes = file.size;
  if (totalBytes === 0) throw new Error(`"${file.name}" is empty.`);

  const key = resumeKey(file, options.purpose, options.contentId);
  onProgress?.({ phase: 'preparing', uploadedBytes: 0, totalBytes, percent: 0, partsDone: 0, totalParts: 0 });

  let session: UploadSession;
  try {
    session = await openSession(options, key);
  } catch (err) {
    if (isCancel(err, signal)) throw new UploadCancelledError();
    throw err;
  }

  const { uploadId, chunkSizeBytes, totalParts } = session;
  const partSize = (n: number) => (n < totalParts ? chunkSizeBytes : totalBytes - chunkSizeBytes * (totalParts - 1));
  let received = new Set(session.receivedParts ?? []);
  const receivedBytes = () => [...received].reduce((sum, n) => sum + partSize(n), 0);
  let doneBytes = receivedBytes();

  const emit = (phase: UploadPhase, inFlightBytes = 0) => {
    const uploadedBytes = Math.min(totalBytes, doneBytes + inFlightBytes);
    onProgress?.({
      phase,
      uploadedBytes,
      totalBytes,
      percent: Math.min(100, Math.floor((uploadedBytes / totalBytes) * 100)),
      partsDone: received.size,
      totalParts,
    });
  };

  const sendPart = async (partNumber: number) => {
    const size = partSize(partNumber);
    const start = (partNumber - 1) * chunkSizeBytes;
    const chunk = file.slice(start, start + size, 'application/octet-stream');
    for (let attempt = 1; ; attempt++) {
      try {
        await adminService.uploadPart(uploadId, partNumber, chunk, signal, (loaded) => emit('uploading', Math.min(loaded, size)));
        return;
      } catch (err) {
        if (isCancel(err, signal)) throw new UploadCancelledError();
        if (!isRetriable(err) || attempt >= MAX_PART_ATTEMPTS) {
          throw new Error(`Part ${partNumber} of ${totalParts} failed: ${errorMessage(err, 'upload failed')}`);
        }
        emit('uploading');
        await sleep(RETRY_BASE_DELAY_MS * 2 ** (attempt - 1), signal);
      }
    }
  };

  const uploadMissing = async () => {
    for (let n = 1; n <= totalParts; n++) {
      if (received.has(n)) continue;
      await sendPart(n);
      received.add(n);
      doneBytes += partSize(n);
      emit('uploading');
    }
  };

  try {
    emit('uploading');
    await uploadMissing();
    emit('finalizing');
    let result: UploadCompleteResponse;
    try {
      result = await adminService.completeUpload(uploadId, signal);
    } catch (err) {
      if (isCancel(err, signal) || !axios.isAxiosError(err) || err.response?.status !== 409) throw err;
      // The server is missing parts (e.g. one was lost after a retry): send them once more and complete again.
      const fresh = await adminService.getUpload(uploadId);
      if (fresh.status !== 'OPEN') throw err;
      received = new Set(fresh.receivedParts ?? []);
      doneBytes = receivedBytes();
      if (received.size >= totalParts) throw err;
      await uploadMissing();
      emit('finalizing');
      result = await adminService.completeUpload(uploadId, signal);
    }
    localStorage.removeItem(key);
    return result;
  } catch (err) {
    if (isCancel(err, signal) || isUploadCancelled(err)) {
      localStorage.removeItem(key);
      adminService.abortUpload(uploadId).catch(() => undefined);
      throw new UploadCancelledError();
    }
    throw err;
  }
}
