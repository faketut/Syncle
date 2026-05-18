import time
from livekit import api
import os

# 这里的 Keys 必须与你 Docker 运行 LiveKit 时使用的 --dev 模式默认值一致
# 生产环境下这些应该从环境变量读取
LIVEKIT_API_KEY = "devkey"
LIVEKIT_API_SECRET = "secret"

def generate_token(room_name, participant_name):
    """
    为指定房间和用户生成一个 Access Token
    """
    token = api.AccessToken(LIVEKIT_API_KEY, LIVEKIT_API_SECRET) \
        .with_identity(participant_name) \
        .with_name(participant_name) \
        .with_grants(api.VideoGrants(
            room_join=True,
            room=room_name,
            can_publish=True,
            can_subscribe=True,
            can_publish_data=True # 必须开启，用于发送 20Hz 位置数据
        ))

    return token.to_jwt()

if __name__ == "__main__":
    room = "syncle -office"

    # 为不同的测试角色生成 Token
    participants = ["Me", "Peer_Alpha", "Peer_Beta"]

    print(f"--- LiveKit Tokens for Room: {room} ---")
    for p in participants:
        jwt = generate_token(room, p)
        print(f"\nUser: {p}")
        print(f"Token: {jwt}")
