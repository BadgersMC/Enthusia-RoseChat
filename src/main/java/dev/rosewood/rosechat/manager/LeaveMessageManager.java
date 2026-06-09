package dev.rosewood.rosechat.manager;

import dev.rosewood.rosechat.placeholder.ConditionManager;
import dev.rosewood.rosechat.placeholder.CustomPlaceholder;
import dev.rosewood.rosechat.placeholder.condition.PlaceholderCondition;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.config.CommentedFileConfiguration;
import dev.rosewood.rosegarden.manager.Manager;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public class LeaveMessageManager extends Manager {

    private final Map<String, CustomPlaceholder> leaveMessages;

    public LeaveMessageManager(RosePlugin rosePlugin) {
        super(rosePlugin);
        this.leaveMessages = new LinkedHashMap<>();
    }

    @Override
    public void reload() {
        this.leaveMessages.clear();

        File file = new File(this.rosePlugin.getDataFolder(), "leave-messages.yml");
        if (!file.exists())
            this.rosePlugin.saveResource("leave-messages.yml", false);

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
                String conditionStr = locationSection.getString("condition");
                PlaceholderCondition condition = ConditionManager.getCondition(locationSection, conditionStr).parseValues();
                placeholder.add(location, condition);
            }

            this.leaveMessages.put(id, placeholder);
        }
    }

    @Override
    public void disable() {
        this.leaveMessages.clear();
    }

    public Collection<CustomPlaceholder> getLeaveMessages() {
        return this.leaveMessages.values();
    }

}
