McPy — Minecraft Python Scripting API

Control your Minecraft world using Python.

McPy is a lightweight Python module that connects to a custom Forge mod and lets you:

control players
place/remove blocks
read chat
automate gameplay

All in real-time.

Features
Place and remove blocks
Control players (gamemode, teleport, health)
Read live chat messages
Change weather and time
Play sounds
Access inventory
Kill entities
Simple JSON socket protocol
Requirements
Minecraft Forge mod (McPy mod running)
Python 3.8+
Minecraft server/client with mod installed
Connection

The McPy mod runs a socket server:

Host: localhost
Port: 25566
Getting Started
1. Install

Just copy mc.py into your project:

git clone https://github.com/OgnjenDev/Mc-Py

or simply drop the file into your script folder.

2. Connect
import mc

mc.connect()
3. Basic Usage
import mc

# Place a block
mc.place_block(0, 64, 0, "diamond_block")

# Get block
print(mc.get_block(0, 64, 0))

# Remove block
mc.remove_block(0, 64, 0)
Player Control
players = mc.get_players()

mc.gamemode(players[0], "creative")
mc.teleport(players[0], 0, 100, 0)

print(mc.get_health(players[0]))
mc.set_health(players[0], 20)
Chat System
if mc.chat == "hello":
    print("Player said hello!")

print(mc.chat.sender)
print(mc.chat.message)
World Control
mc.set_time(1000)
mc.set_weather("rain")
mc.set_difficulty("hard")
Sound
mc.play_sound("entity.experience_orb.pickup", 0, 64, 0)
Example Script
import mc
import time

mc.connect()

while True:
    if mc.chat == "boom":
        players = mc.get_players()
        for p in players:
            mc.teleport(p, 0, 200, 0)
    time.sleep(0.1)
How It Works

McPy communicates with the Forge mod using TCP sockets:

Sends JSON commands
Receives JSON responses
Thread-safe communication

Example:

{
  "cmd": "place_block",
  "args": {
    "x": 0,
    "y": 64,
    "z": 0,
    "block": "minecraft:stone"
  }
}
Notes
Server must be running before connecting
Default connection is localhost:25566
Not designed for public servers (no auth yet)
Roadmap
Events system (onBlockBreak, onJoin)
Async support
Web dashboard
Multiplayer scripting API
Security/authentication
Author

Made by OgnjenDev

Contribute

Pull requests are welcome.

License

MIT License
