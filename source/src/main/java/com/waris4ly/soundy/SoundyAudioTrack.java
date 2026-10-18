package com.waris4ly.soundy;

import com.sedmelluq.discord.lavaplayer.container.mpeg.MpegAudioTrack;
import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.http.HttpStreamTools;
import com.sedmelluq.discord.lavaplayer.tools.io.ChainedInputStream;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.NonSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.apache.http.client.methods.HttpGet;

import java.io.InputStream;
import java.net.URI;

public class SoundyAudioTrack extends DelegatedAudioTrack {

    private final SoundySourceManager sourceManager;

    public SoundyAudioTrack(SoundyAudioTrackInfo trackInfo, SoundySourceManager sourceManager) {
        super(trackInfo);
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        SoundyAudioTrackInfo info = (SoundyAudioTrackInfo) trackInfo;

        // Stream URLs are signed and expire, so they are resolved on every play
        // rather than cached.
        StreamData stream;
        try (HttpInterface http = sourceManager.getHttpInterface()) {
            stream = sourceManager.getService().getStream(http, info.getTrackId());
        }

        if (stream.getProtocol() == StreamData.Protocol.PROGRESSIVE) {
            processProgressive(stream, executor);
        } else {
            processHlsFmp4(stream, executor);
        }
    }

    private void processProgressive(StreamData stream, LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            try (PersistentHttpStream httpStream = new PersistentHttpStream(
                    httpInterface, new URI(stream.getUrl()), Long.MAX_VALUE)) {
                processDelegate(new Mp3AudioTrack(trackInfo, httpStream), executor);
            }
        }
    }

    /**
     * SoundCloud's remaining format is fragmented-MP4 HLS: a media playlist whose
     * EXT-X-MAP init segment (ftyp + moov) is followed by moof + mdat fragments.
     * Concatenated in playlist order they form a regular fragmented MP4 stream,
     * which lavaplayer's MpegAudioTrack decodes.
     *
     * Seeking is not supported: the playlist carries no sidx box, so the fragmented
     * MP4 reader has no seek index and ignores seek requests.
     */
    private void processHlsFmp4(StreamData stream, LocalAudioTrackExecutor executor) throws Exception {
        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            SoundyHlsPlaylistParser.Playlist playlist = SoundyHlsPlaylistParser.parse(
                    httpInterface, new URI(stream.getUrl()));

            ChainedInputStream chainedStream = new ChainedInputStream(
                    new Fmp4StreamProvider(httpInterface, playlist));

            processDelegate(
                    new MpegAudioTrack(trackInfo, new NonSeekableInputStream(chainedStream)),
                    executor);
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new SoundyAudioTrack((SoundyAudioTrackInfo) trackInfo, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }

    /**
     * Serves the init segment first, then the media segments in playlist order.
     * Returning null terminates the chained stream.
     */
    private static class Fmp4StreamProvider implements ChainedInputStream.Provider {
        private final HttpInterface httpInterface;
        private final SoundyHlsPlaylistParser.Playlist playlist;
        private int position = 0;

        private Fmp4StreamProvider(HttpInterface httpInterface, SoundyHlsPlaylistParser.Playlist playlist) {
            this.httpInterface = httpInterface;
            this.playlist = playlist;
        }

        @Override
        public InputStream next() {
            String url;
            if (position == 0) {
                url = playlist.initUrl;
            } else if (position <= playlist.segments.size()) {
                url = playlist.segments.get(position - 1).url;
            } else {
                return null;
            }

            position++;
            return HttpStreamTools.streamContent(httpInterface, new HttpGet(url));
        }
    }
}
