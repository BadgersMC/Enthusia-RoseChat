package dev.rosewood.rosechat.command.command;

import dev.rosewood.rosechat.RoseChat;
import dev.rosewood.rosechat.command.RoseChatCommand;
import dev.rosewood.rosegarden.RosePlugin;
import dev.rosewood.rosegarden.command.argument.ArgumentHandlers;
import dev.rosewood.rosegarden.command.framework.ArgumentsDefinition;
import dev.rosewood.rosegarden.command.framework.CommandContext;
import dev.rosewood.rosegarden.command.framework.CommandInfo;
import dev.rosewood.rosegarden.command.framework.annotation.RoseExecutable;
import org.bukkit.command.CommandSender;

public class AiInspectCommand extends RoseChatCommand {

    public AiInspectCommand(RosePlugin rosePlugin) {
        super(rosePlugin);
    }

    @Override
    protected CommandInfo createCommandInfo() {
        return CommandInfo.builder("inspect")
                .permission("rosechat.debug")
                .arguments(ArgumentsDefinition.builder()
                        .required("message", ArgumentHandlers.GREEDY_STRING)
                        .build())
                .build();
    }

    @RoseExecutable
    public void execute(CommandContext context, String message) {
        RoseChat plugin = (RoseChat) this.rosePlugin;
        CommandSender sender = context.getSender();
        sender.sendMessage("Inspecting message with OpenAI moderation...");

        AiCommand.probe(plugin, message)
                .whenComplete((result, throwable) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        sender.sendMessage("OpenAI moderation API: FAILED - " + AiCommand.failureMessage(throwable));
                        return;
                    }
                    AiCommand.sendProbeResult(sender, result, true);
                }));
    }

}
