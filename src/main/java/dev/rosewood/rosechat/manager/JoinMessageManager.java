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
                ConfigurationSection locationSection = section.getConfigurationSection(location);
                if (locationSection == null)
                    continue;
                String conditionStr = section.getString(location + ".condition");
                PlaceholderCondition condition = ConditionManager.getCondition(locationSection, conditionStr).parseValues();
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
