import { VideoTrack } from '@livekit/react-native';
import { VideoView, useVideoPlayer } from 'expo-video';
import { StyleSheet, View } from 'react-native';
import { Room, RoomEvent, Track } from 'livekit-client';

const sampleVideo = 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4';

export async function enableCamera(room: Room): Promise<void> {
  room.on(RoomEvent.TrackSubscribed, () => undefined);
  room.on(RoomEvent.TrackUnsubscribed, () => undefined);
  room.on(RoomEvent.LocalTrackPublished, () => undefined);
  room.on(RoomEvent.LocalTrackUnpublished, () => undefined);
  room.on(RoomEvent.TrackMuted, () => undefined);
  room.on(RoomEvent.TrackUnmuted, () => undefined);
  await room.localParticipant.setCameraEnabled(true);
}

export async function switchCamera(room: Room): Promise<void> {
  const publication = room.localParticipant.getTrackPublication(Track.Source.Camera);
  const videoTrack = publication?.videoTrack;
  if (!videoTrack) throw new Error('Camera track is unavailable');
  await videoTrack.restartTrack({ facingMode: 'environment' });
}

export async function publishWatchState(room: Room): Promise<void> {
  room.on(RoomEvent.DataReceived, (_payload) => undefined);
  await room.localParticipant.publishData(new Uint8Array([1, 2, 3]), {
    reliable: true,
    topic: 'aeris.watch.v1',
  });
}

export function WatchTogetherSmoke() {
  const player = useVideoPlayer(sampleVideo, (instance) => {
    instance.audioMixingMode = 'mixWithOthers';
    instance.currentTime = 0;
    instance.pause();
  });

  return (
    <VideoView
      player={player}
      style={styles.movie}
      contentFit="contain"
      nativeControls={false}
      surfaceType="textureView"
    />
  );
}

export function VideoSmoke({ room }: { room: Room }) {
  const publication = room.localParticipant.getTrackPublication(Track.Source.Camera);

  if (!publication?.track || publication.isMuted) {
    return <View />;
  }

  const trackRef = {
    participant: room.localParticipant,
    publication,
    source: Track.Source.Camera,
  };

  return <VideoTrack trackRef={trackRef} style={styles.video} />;
}

const styles = StyleSheet.create({
  video: {
    flex: 1,
  },
  movie: {
    width: 320,
    height: 180,
  },
});
