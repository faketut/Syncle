import os
from livekit import api

# LiveKit dev-mode defaults (match `docker run livekit/livekit-server --dev`).
# DO NOT use these credentials in production. Override via env vars:
#   LIVEKIT_API_KEY=... LIVEKIT_API_SECRET=... python generate_token.py
LIVEKIT_API_KEY = os.environ.get("LIVEKIT_API_KEY", "devkey")
LIVEKIT_API_SECRET = os.environ.get("LIVEKIT_API_SECRET", "secret")


def generate_token(room_name, participant_name):
    """Generate a LiveKit AccessToken JWT for the given room/participant."""
    token = api.AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET) \
        .with_identity(participant_name) \
        .with_name(participant_name) \
        .with_grants(api.VideoGrants(
            room_join=True,
            room=room_name,
            can_publish=True,
            can_subscribe=True,
            can_publish_data=True,  # required for 20Hz position data
        ))

    return token.to_jwt()


if __name__ == "__main__":
    # Must match AuthRepository.fetchSandboxConnectionDetails default room name.
    room = "syncle-office"

    participants = ["Me", "Peer_Alpha", "Peer_Beta"]

    print(f"--- LiveKit Tokens for Room: {room} ---")
    for p in participants:
        jwt = generate_token(room, p)
        print(f"\nUser: {p}")
        print(f"Token: {jwt}")
