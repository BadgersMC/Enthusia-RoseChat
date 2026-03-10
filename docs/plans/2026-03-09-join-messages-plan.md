# Join Messages Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a `join-messages.yml` config that lets server admins define custom, condition-based join messages (broadcast to all online players) that replace the vanilla join message.

**Architecture:** A new `JoinMessageManager` loads `join-messages.yml` using the same `ConditionManager`/`CustomPlaceholder` infrastructure already used by `PlaceholderManager`. `PlayerListener.onPlayerJoin` suppresses the vanilla message and broadcasts each entry's `message` component through `RoseChatAPI.parse()` per viewer.

**Tech Stack:** Java 17, Bukkit/Paper API, RoseGarden framework (`CommentedFileConfiguration`, `Manager`, `StringPlaceholders`), existing RoseChat placeholder system (`CustomPlaceholder`, `PlaceholderCondition`, `ConditionManager`).

---

## Task 1: Create the default `join-messages.yml` resource

**Files:**
- Create: `src/main/resources/join-messages.yml`

**Step 1: Create the file**

```yaml
# Join messages are sent to all online players when a player joins the server.
# These replace the vanilla join message.
# The structure mirrors custom-placeholders.yml.
#
# Each top-level key is an entry ID. Multiple entries are all broadcast on join.
#
# 'message' defines what is broadcast to online players.
# It supports conditions, PAPI placeholders (%like_this%), and RoseChat
# custom placeholders ({like_this}).
#
# 'text' and other standard components are also supported but optional.

join-message:
  # The message broadcast to all online players on join.
  # Use RoseChat placeholders like {prefix} and {player}.
  message:
    condition: "%vault_rank%"
    mod:
      - "{prefix}{player} &ahas joined the server! (Mod)"
    admin:
      - "{prefix}{player} &ahas joined the server! (Admin)"
    default:
      - "{prefix}{player} &ajoined the server."
```

**Step 2: Verify it's in the right location**

Run: `ls src/main/resources/`
Expected: `join-messages.yml` appears alongside `channels.yml`, `custom-placeholders.yml`, `plugin.yml`, etc.

**Step 3: Commit**

```bash
git add src/main/resources/join-messages.yml
git commit -m "feat: add default join-messages.yml resource"
```

---

## Task 2: Create `JoinMessageManager`

**Files:**
- Create: `src/main/java/dev/rosewood/rosechat/manager/JoinMessageManager.java`

**Context:** Study `PlaceholderManager.java` (lines 35–76) — `JoinMessageManager.reload()` follows the exact same pattern. `CommentedFileConfiguration.loadConfiguration(file)` reads the YAML. `ConditionManager.getCondition(section, conditionStr).parseValues()` builds a `PlaceholderCondition`. Each condition is stored in a `CustomPlaceholder` under its sub-key name (e.g. `"message"`, `"text"`).

**Step 1: Write the class**

```java
package dev.rosewood.rosechat.manager;

import dev.rosewood.rosechat.placeholder.ConditionManager;
import dev.rosewood.rosechat.placeholder.CustomPlaceholder;
import dev.rosewood.rosechat.placeholder.condition.PlaceholderCondition;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.config.CommentedFileConfiguration;
import dev.rosewood.rosegarden.manager.Manager;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public class JoinMessageManager extends Manager {

    private final Map<String, CustomPlaceholder> joinMessages;

    public JoinMessageManager(RosePlugin rosePlugin) {
        super(rosePlugin);
        this.joinMessages = new HashMap<>();
    }

    @Override
    public void reload() {
        this.joinMessages.clear();

        File file = new File(this.rosePlugin.getDataFolder(), "join-messages.yml");
        if (!file.exists())
            this.rosePlugin.saveResource("join-messages.yml", false);

        CommentedFileConfiguration config = CommentedFileConfiguration.loadConfiguration(file);

        for (String id : config.getKeys(false)) {
            CustomPlaceholder placeholder = new CustomPlaceholder(id);

            ConfigurationSection section = config.getConfigurationSection(id);
            if (section == null)
                continue;

            for (String location : section.getKeys(false)) {
                String conditionStr = section.getString(location + ".condition");
                PlaceholderCondition condition = ConditionManager.getCondition(
                        section.getConfigurationSection(location), conditionStr
                ).parseValues();
                placeholder.add(location, condition);
            }

            this.joinMessages.put(id, placeholder);
        }
    }

    @Override
    public void disable() {
        this.joinMessages.clear();
    }

    public Collection<CustomPlaceholder> getJoinMessages() {
        return this.joinMessages.values();
    }

}
```

**Step 2: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL` with no errors. If you see `ConditionManager cannot be resolved`, check the import path against `PlaceholderManager.java` imports.

**Step 3: Commit**

```bash
git add src/main/java/dev/rosewood/rosechat/manager/JoinMessageManager.java
git commit -m "feat: add JoinMessageManager"
```

---

## Task 3: Register `JoinMessageManager` in `RoseChat`

**Files:**
- Modify: `src/main/java/dev/rosewood/rosechat/RoseChat.java:174–184`

**Context:** `getManagerLoadPriority()` returns the ordered list of managers the RoseGarden framework loads on startup and reload. `JoinMessageManager` should come after `PlaceholderManager` since it uses the same config infrastructure.

**Step 1: Add the import**

In `RoseChat.java`, add to the imports block alongside the other manager imports:

```java
import dev.rosewood.rosechat.manager.JoinMessageManager;
```

**Step 2: Add to `getManagerLoadPriority()`**

Find the method (line ~174) and add `JoinMessageManager.class` after `PlaceholderManager.class`:

```java
@Override
protected List<Class<? extends Manager>> getManagerLoadPriority() {
    return Arrays.asList(
            ChannelManager.class,
            FilterManager.class,
            PlaceholderManager.class,
            JoinMessageManager.class,   // <-- add this line
            PlayerDataManager.class,
            GroupManager.class,
            DiscordEmojiManager.class,
            BungeeManager.class
    );
}
```

**Step 3: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`.

**Step 4: Commit**

```bash
git add src/main/java/dev/rosewood/rosechat/RoseChat.java
git commit -m "feat: register JoinMessageManager"
```

---

## Task 4: Broadcast join messages in `PlayerListener`

**Files:**
- Modify: `src/main/java/dev/rosewood/rosechat/listener/PlayerListener.java:83–90`

**Context:** The existing `onPlayerJoin` method (line 83) runs at default (`NORMAL`) priority and only handles chat suggestions. We extend it to: suppress the vanilla join message, iterate all `JoinMessageManager` entries, resolve the `message` condition per-viewer, parse each line through `RoseChatAPI.parse()`, and send the result.

`RoseChatAPI.parse(sender, viewer, format)` returns `MessageContents`. `RosePlayer.send(MessageContents)` sends it. `PlaceholderCondition.parseToStringList(sender, viewer, placeholders)` returns the list of strings for the resolved condition branch.

**Step 1: Add imports to `PlayerListener.java`**

Add these three imports alongside the existing ones:

```java
import dev.rosewood.rosechat.manager.JoinMessageManager;
import dev.rosewood.rosechat.placeholder.CustomPlaceholder;
import dev.rosewood.rosechat.placeholder.condition.PlaceholderCondition;
```

**Step 2: Replace the existing `onPlayerJoin` method**

The existing method at line 83 is:

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    if (NMSUtil.getVersionNumber() < 19 || !Settings.ALLOW_CHAT_SUGGESTIONS.get())
        return;

    RosePlayer player = new RosePlayer(event.getPlayer());
    player.validateChatCompletion();
}
```

Replace it with:

```java
@EventHandler(priority = EventPriority.NORMAL)
public void onPlayerJoin(PlayerJoinEvent event) {
    RosePlayer joiningPlayer = new RosePlayer(event.getPlayer());

    // Suppress the vanilla join message.
    event.setJoinMessage(null);

    // Broadcast custom join messages to all online players.
    JoinMessageManager joinMessageManager = this.plugin.getManager(JoinMessageManager.class);
    RoseChatAPI api = RoseChatAPI.getInstance();

    for (CustomPlaceholder joinMessage : joinMessageManager.getJoinMessages()) {
        PlaceholderCondition messageCondition = joinMessage.get("message");
        if (messageCondition == null)
            continue;

        for (Player online : Bukkit.getOnlinePlayers()) {
            RosePlayer viewer = new RosePlayer(online);
            List<String> lines = messageCondition.parseToStringList(
                    joiningPlayer, viewer, StringPlaceholders.builder().build());
            for (String line : lines) {
                viewer.send(api.parse(joiningPlayer, viewer, line));
            }
        }
    }

    // Handle chat suggestions (MC 1.19+).
    if (NMSUtil.getVersionNumber() >= 19 && Settings.ALLOW_CHAT_SUGGESTIONS.get())
        joiningPlayer.validateChatCompletion();
}
```

**Step 3: Verify it compiles**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. Common errors:
- `StringPlaceholders.builder()` not found → already imported (`dev.rosewood.rosegarden.utils.StringPlaceholders`)
- `List` not found → already imported (`java.util.List`)
- `Bukkit` not found → already imported (`org.bukkit.Bukkit`)

**Step 4: Build the full jar**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`. Jar appears in `build/libs/`.

**Step 5: Commit**

```bash
git add src/main/java/dev/rosewood/rosechat/listener/PlayerListener.java
git commit -m "feat: broadcast join messages and suppress vanilla join message"
```

---

## Task 5: Manual verification

**Step 1: Deploy to a test server**

Copy `build/libs/RoseChat-*.jar` to your test server's `plugins/` folder. Start or restart the server.

**Step 2: Verify `join-messages.yml` was created**

Check `plugins/RoseChat/join-messages.yml` exists with the default content.

**Step 3: Test join message fires**

Join the server. Confirm:
- No vanilla "Player joined the game" message appears.
- The custom join message from the `default` condition branch appears for all online players.

**Step 4: Test conditions**

Give yourself a rank (e.g. `/lp user <name> parent set mod`), rejoin. Confirm the `mod` branch of the message fires.

**Step 5: Test reload**

Edit `join-messages.yml` (change the default message text). Run `/rosechat reload`. Rejoin. Confirm the updated message appears.

**Step 6: Test empty `join-messages.yml`**

Remove all entries from `join-messages.yml`, reload, rejoin. Confirm no message is sent and no error is logged.

**Step 7: Commit any fixes found during testing**

```bash
git add -p
git commit -m "fix: <describe what you fixed>"
```
