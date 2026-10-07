import React, { useEffect, useRef, useState } from 'react';
import { AlertCircle } from 'lucide-react';

const HLS_MIME = 'application/vnd.apple.mpegurl';

/** Plays an HLS master playlist: native HLS where supported (Safari), otherwise hls.js (loaded on demand). */
export const HlsPlayer: React.FC<{ src: string; className?: string }> = ({ src, className }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const video = videoRef.current;
    if (!video) return undefined;
    setError(null);
    let disposed = false;
    let destroy: (() => void) | null = null;

    if (video.canPlayType(HLS_MIME)) {
      video.src = src;
    } else {
      import('hls.js')
        .then(({ default: Hls }) => {
          if (disposed) return;
          if (!Hls.isSupported()) {
            setError('This browser cannot play HLS video.');
            return;
          }
          const hls = new Hls();
          hls.on(Hls.Events.ERROR, (_event, data) => {
            if (!data.fatal) return;
            const status = data.response?.code ? ` (HTTP ${data.response.code})` : '';
            setError(`Playback failed: ${data.details}${status}`);
            hls.destroy();
          });
          hls.loadSource(src);
          hls.attachMedia(video);
          destroy = () => hls.destroy();
        })
        .catch((err: unknown) => setError(`Could not load the HLS player: ${err instanceof Error ? err.message : String(err)}`));
    }

    return () => {
      disposed = true;
      destroy?.();
      video.removeAttribute('src');
      video.load();
    };
  }, [src]);

  return (
    <div className="space-y-2">
      <video
        ref={videoRef}
        controls
        playsInline
        onError={() => setError((current) => current ?? 'The video could not be played.')}
        className={className ?? 'w-full rounded-lg bg-black aspect-video'}
      />
      {error && (
        <p className="text-[11px] text-rose-300 flex items-center gap-1.5">
          <AlertCircle className="w-3.5 h-3.5 shrink-0" />
          {error}
        </p>
      )}
    </div>
  );
};
