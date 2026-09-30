package dev.rosewood.rosechat.command.command;

import dev.rosewood.rosechat.RoseChat;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import org.bukkit.command.CommandSender;

public class AiTestCommand extends RoseChatCommand {

    public AiTestCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("test")
                .permission("rosechat.debug")
                .arguments(ArgumentsDefinition.builder().build())
                .build();
    }

    @RoseExecutable
    public void execute(CommandContext context) {
        RoseChat plugin = (RoseChat) this.rosePlugin;
        CommandSender sender = context.getSender();
        sender.sendMessage("Running OpenAI moderation diagnostic...");

        AiCommand.probe(plugin, "hello from RoseChat AI diagnostic")
                .whenComplete((result, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        sender.sendMessage("OpenAI moderation API: FAILED - " + AiCommand.failureMessage(throwable));
                        return;
                    }
                    AiCommand.sendProbeResult(sender, result, false);
                }));
    }

}
