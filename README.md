# ChatManager 6.4

**Advanced Minecraft Chat Moderation & Management**

ChatManager is a configurable chat management plugin for Minecraft Paper servers. It combines automated spam prevention, message filtering, player communication controls, and server utility commands into one integrated plugin.

## Features

### Chat Moderation
- **Phrase filtering:** Detects configured prohibited phrases, advertisements, and unwanted server invitations.
- **Link detection:** Identifies domain patterns and obfuscated links.
- **Duplicate detection:** Prevents repeated messages from flooding chat.
- **Similarity detection:** Compares messages against recent chat history to identify near-duplicate spam.
- **Template spam detection:** Identifies recurring promotional messages with small variations.
- **Unicode normalization:** Helps detect attempts to bypass filters using homoglyphs, leetspeak, punctuation, and spacing changes.
- **Chat cooldowns:** Limits how frequently players can send messages.
- **Caps and flood protection:** Detects excessive capitalization and repeated-character flooding.
- **Permission-based bypasses:** Allows trusted staff to bypass selected moderation checks.

### Player Communication
- Private messaging with `/msg` and `/reply`
- Player ignore and unignore controls
- Toggleable public chat visibility
- Toggleable private messages
- Toggleable connection messages

### Server Utilities
- Broadcast announcements
- View player statistics
- Check player join dates
- Reload plugin configuration
- Optional self-elimination command

## Requirements

- Minecraft Java Edition
- Paper software
- Minecraft / Paper API 1.21.11
- Java 21 or later
- Maven 3.9+ for building from source

## Installation

1. Download the latest compiled jar file.
2. Stop your Minecraft server.
3. Place the JAR in the server's `plugins/` directory.
4. Start the server.
5. Edit `plugins/ChatManager/config.yml` to customize moderation settings.
6. Reload the configuration using `/chatmanager reload`, or restart the server if needed.

**Note:** The ZIP containing the source code is not itself a server-ready plugin JAR.

## Building from Source

Clone or extract the project, navigate to its root directory, and run:

```bash
mvn clean package
```

The compiled plugin JAR will normally be generated in:

```text
target/ChatManager-6.4.jar
```

Install the resulting JAR in your server's `plugins/` directory.

## Commands

| Command | Description |
|---|---|
| `/chatmanager reload` | Reload ChatManager configuration |
| `/broadcast <message>` | Broadcast a server-wide message |
| `/msg <player> <message>` | Send a private message |
| `/reply <message>` | Reply to the last private message |
| `/ignore <player>` | Ignore a player |
| `/unignore <player>` | Stop ignoring a player |
| `/ignorelist` | View ignored players |
| `/togglechat` | Toggle public chat visibility |
| `/toggletells` | Toggle private-message visibility |
| `/toggleconnectionmsgs` | Toggle connection messages |
| `/joindate [player]` | Check a player's first join date |
| `/stats` | View server statistics |
| `/suicide` | Eliminate your own character |

Some commands have aliases configured in `plugin.yml`, including `/cm`, `/bc`, `/tell`, `/w`, `/r`, `/jd`, and `/kill`.

## Permissions

| Permission | Default | Purpose |
|---|---|---|
| `chatmanager.bypass.cooldown` | Operators | Bypass chat cooldown |
| `chatmanager.bypass.duplicate` | Operators | Bypass duplicate detection |
| `chatmanager.bypass.spam` | Operators | Bypass anti-spam checks |
| `chatmanager.bypass.phrases` | Operators | Bypass phrase filtering |
| `chatmanager.notify` | Operators | Receive moderation alerts |
| `chatmanager.color` | Disabled | Use chat colors |
| `chatmanager.broadcast` | Operators | Broadcast messages |
| `chatmanager.ignore` | Everyone | Use ignore commands |
| `chatmanager.ignore.bypass` | Operators | Bypass ignore restrictions |
| `chatmanager.joindate` | Everyone | Use `/joindate` |
| `chatmanager.reload` | Operators | Reload configuration |
| `chatmanager.suicide` | Everyone | Use `/suicide` |
| `chatmanager.whisper` | Everyone | Use private messaging |
| `chatmanager.whisper.spy` | Operators | View private messages |
| `chatmanager.whisper.color` | Disabled | Use colors in private messages |

Additional toggle permissions are defined in `plugin.yml`.

## Configuration

ChatManager generates a `config.yml` file on first startup. The main configuration sections include:

- `chat.enabled` — Enable or disable chat handling.
- `chat.log-to-console` — Control chat logging.
- `chat.block-unicode` — Optionally block non-ASCII characters.
- `chat.allow-color` — Configure chat color support.
- `chat.cooldown` — Configure message cooldown duration and bypass permission.
- `chat.phrases` — Configure phrase filtering, domain detection, and blocked phrases.
- `chat.no-duplicate` — Configure duplicate-message prevention.
- `chat.anti-spam` — Configure similarity detection, message history, caps, flooding, repetition, and template analysis.

For example, the default cooldown configuration is:

```yaml
chat:
  cooldown:
    enabled: true
    time-seconds: 3
    bypass-permission: "chatmanager.bypass.cooldown"
```

Recent-message similarity detection is independently configurable:

```yaml
chat:
  anti-spam:
    recent-similarity:
      enabled: true
      threshold: 0.80
```

A threshold of `0.80` represents an 80% similarity threshold. Adjust it carefully to balance spam prevention against false positives.

**Important:** Preserve the existing configuration structure when editing settings. Back up your configuration before upgrading between versions.

## Development

- **Plugin name:** ChatManager
- **Version:** 6.4
- **Main class:** `org.me.chatManager.ChatManager`
- **Language:** Java
- **Build system:** Maven
- **Target API:** Paper 1.21.11

## License

No license is specified in the supplied project metadata. Contact the project maintainer for permission before redistributing or modifying the plugin for public distribution.

---
