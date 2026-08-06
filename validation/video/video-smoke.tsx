import { VideoTrack } from '@livekit/react-native';
import { StyleSheet, View } from 'react-native';
import { Room, RoomEvent, Track } from 'livekit-client';

export async function enableCamera(room: Room): Promise<void> {
  room.on(RoomEvent.TrackSubscribed, () => undefined);
  room.on(RoomEvent.TrackUnsubscribed, () => undefined);
  room.on(RoomEvent.LocalTrackPublished, () => undefined);
  room.on(RoomEvent.LocalTrackUnpublished, () => undefined);
  room.on(RoomEvent.TrackMuted, () => undefined);
  room.on(RoomEvent.TrackUnmuted, () => undefined);
  await room.localParticipant.setCameraEnabled(true);
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
});
