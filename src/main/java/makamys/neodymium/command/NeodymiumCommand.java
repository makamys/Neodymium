package makamys.neodymium.command;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.commons.lang3.tuple.Pair;

import makamys.neodymium.Neodymium;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.ClientCommandHandler;

public class NeodymiumCommand extends CommandBase {
    
    public static final EnumChatFormatting HELP_COLOR = EnumChatFormatting.BLUE;
    public static final EnumChatFormatting HELP_USAGE_COLOR = EnumChatFormatting.YELLOW;
    public static final EnumChatFormatting HELP_WARNING_COLOR = EnumChatFormatting.YELLOW;
    public static final EnumChatFormatting HELP_EMPHASIS_COLOR = EnumChatFormatting.DARK_AQUA;
    public static final EnumChatFormatting ERROR_COLOR = EnumChatFormatting.RED;
    
    private static Map<String, ISubCommand> subCommands = new HashMap<>();
    
    public static void init() {
        ClientCommandHandler.instance.registerCommand(new NeodymiumCommand());
        registerSubCommand("status", new StatusCommand());
    }
    
    public static void registerSubCommand(String key, ISubCommand command) {
        subCommands.put(key, command);   
    }
    
    @Override
    public String getCommandName() {
        return "neodymium";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "";
    }
    
    public int getRequiredPermissionLevel()
    {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if(args.length > 0) {
            ISubCommand subCommand = subCommands.get(args[0]);
            if(subCommand != null) {
                subCommand.processCommand(sender, args);
                return;
            }
        }
        throw new WrongUsageException(getCommandName() + " <" + String.join("|", subCommands.keySet()) + ">", new Object[0]);
    }
    
    public static void addChatMessage(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }
    
    public static void addColoredChatMessage(ICommandSender sender, String text, EnumChatFormatting color) {
        addColoredChatMessage(sender, text, color, null);
    }
    
    public static void addColoredChatMessage(ICommandSender sender, String text, EnumChatFormatting color, Consumer<ChatComponentText> fixup) {
        ChatComponentText msg = new ChatComponentText(text);
        msg.getChatStyle().setColor(color);
        if(fixup != null) {
            fixup.accept(msg);
        }
        sender.addChatMessage(msg);
    }
    
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, subCommands.keySet().toArray(new String[0])) : null;
    }
    
    public static class StatusCommand implements ISubCommand {

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if(Neodymium.renderer != null) {
                List<String> text = Neodymium.renderer.getDebugText();
                addColoredChatMessage(sender, text.get(0), EnumChatFormatting.LIGHT_PURPLE);
                addChatMessages(sender, text.subList(1, text.size()));
            }
            Pair<List<String>, List<String>> allWarns = Neodymium.checkCompat();
            List<String> warns = allWarns.getLeft();
            List<String> criticalWarns = allWarns.getRight();
            for(String line : warns) {
                addColoredChatMessage(sender, "* " + line, HELP_WARNING_COLOR);
            }
            for(String line : criticalWarns) {
                addColoredChatMessage(sender, "* " + line, ERROR_COLOR);
            }
        }
        
        private static void addChatMessages(ICommandSender sender, Collection<String> messages) {
            for(String line : messages) {
                sender.addChatMessage(new ChatComponentText(line));
            }
        }
        
    }
    
}
