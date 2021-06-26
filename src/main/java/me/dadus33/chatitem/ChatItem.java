package me.dadus33.chatitem;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import me.dadus33.chatitem.api.APIImplementation;
import me.dadus33.chatitem.api.ChatItemAPI;
import me.dadus33.chatitem.commands.CIReload;
import me.dadus33.chatitem.filters.Log4jFilter;
import me.dadus33.chatitem.json.JSONManipulator;
import me.dadus33.chatitem.json.JSONManipulatorCurrent;
import me.dadus33.chatitem.listeners.ChatEventListener;
import me.dadus33.chatitem.listeners.ChatPacketListener;
import me.dadus33.chatitem.listeners.ChatPacketValidator;
import me.dadus33.chatitem.utils.ProtocolSupportUtil;
import me.dadus33.chatitem.utils.Storage;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class ChatItem extends JavaPlugin {

    public final static int CFG_VER = 12;
    private static ChatItem instance;
    private static Class chatMessageTypeClass;
    private static boolean post17 = false;
    private static boolean post111 = false;
    private static boolean post112 = false;
    private static boolean baseComponentAvailable = true;
    private static boolean viaVersion = false;
    private static boolean protocolSupport = false;
    private ChatEventListener chatEventListener;
    private Log4jFilter filter;
    private Storage storage;
    private ProtocolManager pm;
    private ChatPacketListener packetListener;
    private ChatPacketValidator packetValidator;

    public static void reload(CommandSender sender) {
        ChatItem obj = getInstance();
        obj.pm = ProtocolLibrary.getProtocolManager();
        obj.saveDefaultConfig();
        obj.reloadConfig();
        obj.storage = new Storage(obj.getConfig());
        obj.packetListener.setStorage(obj.storage);
        obj.packetValidator.setStorage(obj.storage);
        obj.chatEventListener.setStorage(obj.storage);
        obj.filter.setStorage(obj.storage);
        APIImplementation api = (APIImplementation) Bukkit.getServicesManager().getRegistration(ChatItemAPI.class).getProvider();
        api.setStorage(obj.storage);
        api.updateLogger();
        if (!obj.storage.RELOAD_MESSAGE.isEmpty())
            sender.sendMessage(obj.storage.RELOAD_MESSAGE);
    }

    public static ChatItem getInstance() {
        return instance;
    }

    public static String getVersion(Server server) {
        final String packageName = server.getClass().getPackage().getName();

        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    public static boolean supportsActionBar() {
        return post17;
    }

    public static boolean supportsShulkerBoxes() {
        return post111;
    }

    public static boolean supportsChatComponentApi() {
        return baseComponentAvailable;
    }

    public static boolean supportsChatTypeEnum() {
        return post112;
    }

    public static JSONManipulator getManipulator() {
        /*
            We used to have 2 kinds of JSONManipulators because of my bad understanding of the 1.7 way of parsing JSON chat
            The interface should however stay as there might be great changes in future versions in JSON parsing (most likely 1.13)
         */
        return new JSONManipulatorCurrent();
        //We just return a new one whenever requested for the moment, should implement a cache of some sort some time though
    }

    public static boolean usesViaVersion() {
        return viaVersion;
    }

    public static boolean usesProtocolSupport() {
        return protocolSupport;
    }

    public static Class getChatMessageTypeClass() {
        return chatMessageTypeClass;
    }

    public void onEnable() {
        //Save the instance (we're basically a singleton)
        instance = this;

        //Load ProtocolManager
        pm = ProtocolLibrary.getProtocolManager();

        //Load config
        saveDefaultConfig();
        storage = new Storage(getConfig());

        //Load API
        APIImplementation api = new APIImplementation(storage);
        Bukkit.getServicesManager().register(ChatItemAPI.class, api, this, ServicePriority.Highest);

        if (isMc18OrLater()) {
            post17 = true; //for actionbar messages ignoring
        }
        if (isMc111OrLater()) {
            post111 = true; //for shulker box filtering
        }
        if (isMc112Orlater()) {
            post112 = true; //for new ChatType enum instead of using bytes
            try {
                chatMessageTypeClass = Class.forName("net.minecraft.server." + getVersion(Bukkit.getServer()) + ".ChatMessageType");
            } catch (ClassNotFoundException e) {
                e.printStackTrace(); //This should never happen anyways, so no need to think of fancy stuff like disabling the plugin
            }
        }

        //Packet listeners
        packetListener = new ChatPacketListener(this, ListenerPriority.LOW, storage, PacketType.Play.Server.CHAT);
        packetValidator = new ChatPacketValidator(this, ListenerPriority.LOWEST, storage, PacketType.Play.Server.CHAT);
        pm.addPacketListener(packetValidator);
        pm.addPacketListener(packetListener);

        if (Bukkit.getPluginManager().getPlugin("ViaVersion") != null) {
            viaVersion = true;
        } else if (Bukkit.getPluginManager().getPlugin("ProtocolSupport") != null) {
            protocolSupport = true;
            ProtocolSupportUtil.initialize();
        }

        //We halt the use of this system for now, until we can solve the infamous getProtocolVersion issue
        //Till then, users of both ViaVersion and ProtocolSupport should do just fine
        /*if(!protocolSupport && !viaVersion) {
            //We only implement our own way of getting protocol versions if we have no other choice
            pm.addPacketListener(new HandshakeListener(this, ListenerPriority.MONITOR, PacketType.Handshake.Client.SET_PROTOCOL));
        }*/

        //Commands
        CIReload rld = new CIReload();
        Bukkit.getPluginCommand("cireload").setExecutor(rld);

        //Bukkit API listeners
        chatEventListener = new ChatEventListener(storage);
        Bukkit.getPluginManager().registerEvents(chatEventListener, this);

        //Check for existence of BaseComponent class (only on spigot)
        try {
            Class.forName("net.md_5.bungee.api.chat.BaseComponent");
        } catch (ClassNotFoundException e) {
            baseComponentAvailable = false;
        }

        //Initialize Log4J filter (remove ugly console messages)
        filter = new Log4jFilter(storage);
    }

    public void onDisable() {
        instance = null;
        post17 = false;
    }

    private boolean isMc18OrLater() {
        switch (getVersion(Bukkit.getServer())) {
            case "v1_7_R1":
            case "v1_7_R2":
            case "v1_7_R3":
            case "v1_7_R4":
                return false;
            default:
                return true;
        }
    }

    private boolean isMc111OrLater() {
        switch (getVersion(Bukkit.getServer())) {
            case "v1_7_R1":
            case "v1_7_R2":
            case "v1_7_R3":
            case "v1_7_R4":
            case "v1_8_R1":
            case "v1_8_R2":
            case "v1_8_R3":
            case "v1_9_R1":
            case "v1_9_R2":
            case "v1_10_R1":
            case "v1_10_R2":
                return false;
            default:
                return true;
        }
    }

    private boolean isMc112Orlater(){
        switch(getVersion(Bukkit.getServer())){
            case "v1_7_R1":
            case "v1_9_R2":
            case "v1_9_R1":
            case "v1_8_R3":
            case "v1_8_R2":
            case "v1_8_R1":
            case "v1_7_R4":
            case "v1_7_R3":
            case "v1_7_R2":
            case "v1_10_R1":
            case "v1_10_R2":
            case "v1_11_R1":
                return false;
            default: return true;
        }
    }

}
