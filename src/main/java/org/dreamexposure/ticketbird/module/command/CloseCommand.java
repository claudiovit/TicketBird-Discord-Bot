package org.dreamexposure.ticketbird.module.command;

import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.TextChannel;
import discord4j.core.spec.TextChannelEditSpec;
import org.dreamexposure.ticketbird.Main;
import org.dreamexposure.ticketbird.database.DatabaseManager;
import org.dreamexposure.ticketbird.message.MessageManager;
import org.dreamexposure.ticketbird.objects.command.CommandInfo;
import org.dreamexposure.ticketbird.objects.guild.GuildSettings;
import org.dreamexposure.ticketbird.objects.guild.Ticket;
import org.dreamexposure.ticketbird.utils.GeneralUtils;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.function.Consumer;

public class CloseCommand implements ICommand {

    /**
     * Gets the command this Object is responsible for.
     *
     * @return The command this Object is responsible for.
     */
    @Override
    public String getCommand() {
        return "Close";
    }

    /**
     * Gets the short aliases of the command this object is responsible for.
     * </br>
     * This will return an empty ArrayList if none are present
     *
     * @return The aliases of the command.
     */
    @Override
    public ArrayList<String> getAliases() {
        return new ArrayList<>();
    }

    /**
     * Gets the info on the command (not sub command) to be used in help menus.
     *
     * @return The command info.
     */
    @Override
    public CommandInfo getCommandInfo() {
        CommandInfo info = new CommandInfo("Close");
        info.setDescription("Closes the ticket this command is used in.");
        info.setExample("=Close");

        return info;
    }

    /**
     * Issues the command this Object is responsible for.
     *
     * @param args  The command arguments.
     * @param event The event received.
     * @return <code>true</code> if successful, else <code>false</code>.
     */
    @SuppressWarnings("ConstantConditions")
    @Override
    public Boolean issueCommand(String[] args, MessageCreateEvent event, GuildSettings settings) {
        String channelName = event.getMessage().getChannel().ofType(TextChannel.class).block().getName();
        //Channel name format [prefix]-ticket-[number]
        try {
            int ticketNumber;
            if (channelName.split("-").length == 2) {
                //Ticket has not had a project set
                ticketNumber = Integer.valueOf(channelName.split("-")[1]);
            } else {
                ticketNumber = Integer.valueOf(channelName.split("-")[2]);
            }
            Ticket ticket = DatabaseManager.getManager().getTicket(settings.getGuildID(), ticketNumber);

            if (ticket != null) {
                Guild guild = event.getGuild().block();
                //Check if already closed..
                if (!event.getMessage().getChannel().ofType(TextChannel.class).block().getCategory().block().getId().equals(settings.getCloseCategory())) {
                    //Not closed, lets close it.
                    Consumer<TextChannelEditSpec> editChannel = spec -> spec.setParentId(settings.getCloseCategory());
                    event.getMessage().getChannel().ofType(TextChannel.class).flatMap(c -> c.edit(editChannel)).subscribe();

                    //Update database info
                    ticket.setCategory(settings.getCloseCategory());
                    ticket.setLastActivity(System.currentTimeMillis());
                    DatabaseManager.getManager().updateTicket(ticket);

                    //Remove command message
                    MessageManager.deleteMessage(event.getMessage());

                    //Send message! :D
                    if (ticket.getCreator() == null) {
                        MessageManager.sendMessageAsync(MessageManager.getMessage("Ticket.Close.Success", "%creator%", "NO " +
                                "CREATOR", settings), event);
                    } else {
                        if (guild.getMemberById(ticket.getCreator()).onErrorResume(e -> Mono.empty()).block() != null) {
                            MessageManager.sendMessageAsync(MessageManager.getMessage("Ticket.Close.Success", "%creator%",
                                    guild.getMemberById(ticket.getCreator()).block().getMention(), settings), event);
                        } else {
                            MessageManager.sendMessageAsync(MessageManager.getMessage("Ticket.Close.Success", "%creator%",
                                    Main.getClient().getUserById(ticket.getCreator()).onErrorResume(e -> Mono.empty()).block().getMention(), settings), event);
                        }
                    }

                    //Lets update the static message!
                    GeneralUtils.updateStaticMessage(event.getGuild().block(), settings);
                }
            } else {
                //Not a ticket/invalid ticket.
                MessageManager.sendMessageAsync(MessageManager.getMessage("Ticket.Close.InvalidChannel", settings), event);
            }
        } catch (NumberFormatException | IndexOutOfBoundsException ignore) {
            //Not a ticket channel.
            MessageManager.sendMessageAsync(MessageManager.getMessage("Ticket.Close.InvalidChannel", settings), event);
        }

        return false;
    }
}