# mc.py — Minecraft Python Scripting Module
# Connects to the McPy Forge mod running on your Minecraft server.
# The mod listens on port 25566 (localhost by default).

import socket
import json
import threading
import time
from typing import Optional


_host: str = "localhost"
_port: int = 25566
_sock: Optional[socket.socket] = None
_lock = threading.Lock()
_connected = False

_last_chat: str = ""
_last_sender: str = ""


def connect(host: str = "localhost", port: int = 25566) -> None:
    global _sock, _host, _port, _connected
    _host = host
    _port = port
    _sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    _sock.connect((_host, _port))
    _connected = True
    print(f"[mc.py] Connected to {host}:{port}")


def disconnect() -> None:
    global _sock, _connected
    if _sock:
        _sock.close()
        _sock = None
    _connected = False


def _ensure_connected():
    global _connected
    if not _connected:
        connect()


def _send(cmd: str, **kwargs) -> dict:
    _ensure_connected()
    payload = json.dumps({"cmd": cmd, "args": kwargs}) + "\n"
    with _lock:
        _sock.sendall(payload.encode())
        response = b""
        while True:
            chunk = _sock.recv(4096)
            response += chunk
            if b"\n" in response or len(chunk) < 4096:
                break
    result = json.loads(response.decode().strip())
    if result.get("status") == "error":
        raise McPyError(result.get("message", "Unknown error"))
    return result


class McPyError(Exception):
    pass

def get_block(x: int, y: int, z: int) -> str:
    return _send("get_block", x=x, y=y, z=z)["block"]


def remove_block(x: int, y: int, z: int) -> None:
    _send("remove_block", x=x, y=y, z=z)


def place_block(x: int, y: int, z: int, block: str) -> None:
    if ":" not in block:
        block = "minecraft:" + block
    _send("place_block", x=x, y=y, z=z, block=block)


def get_players() -> list[str]:
    return _send("get_players")["players"]


def kill(entity: str) -> None:
    _send("kill", entity=entity)


def gamemode(player: str, mode: str) -> None:
    _send("gamemode", player=player, gamemode=mode)


def teleport(entity: str, x: float, y: float, z: float) -> None:
    _send("teleport", entity=entity, x=x, y=y, z=z)


def get_health(player: str) -> float:
    return _send("get_health", player=player)["health"]


def set_health(player: str, value: float) -> None:
    _send("set_health", player=player, value=value)


def get_inventory(player: str) -> list[str]:
    return _send("get_inventory", player=player)["inventory"]


class _ChatProxy:
    def __eq__(self, other):
        result = _send("get_chat")
        global _last_chat, _last_sender
        _last_chat = result["message"]
        _last_sender = result["sender"]
        return _last_chat == other

    def __str__(self):
        result = _send("get_chat")
        return result["message"]

    @property
    def sender(self) -> str:
        _send("get_chat")
        return _last_sender

    @property
    def message(self) -> str:
        return str(self)

chat = _ChatProxy()


def set_time(ticks: int) -> None:
    _send("set_time", ticks=ticks)


def get_time() -> dict:
    return _send("get_time")


def set_weather(weather: str) -> None:
    _send("set_weather", weather=weather)


def get_weather() -> str:
    return _send("get_weather")["weather"]


def set_difficulty(difficulty: str) -> None:
    _send("set_difficulty", difficulty=difficulty)


def play_sound(sound: str, x: float, y: float, z: float) -> None:
    if ":" not in sound:
        sound = "minecraft:" + sound
    _send("play_sound", sound=sound, x=x, y=y, z=z)


def log(message: str) -> None:
    _send("log", message=message)


def version() -> str:
    return _send("version")["version"]


try:
    connect()
except ConnectionRefusedError:
    print("[mc.py] WARNING: Could not auto-connect. Call mc.connect() manually.")
except Exception as e:
    print(f"[mc.py] WARNING: {e}. Call mc.connect() manually.")