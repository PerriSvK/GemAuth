package sk.perri.gemissius.gemauthbungee;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainBungee extends Plugin implements Listener
{
    Configuration config;
    Map<String, Integer> players = new HashMap<>();
    ServerInfo authServer;
    List<ServerInfo> lobbies = new ArrayList<>();

    @Override
    public void onEnable()
    {
        if (!getDataFolder().exists())
            getDataFolder().mkdir();

        File configFile = new File(getDataFolder(), "config.yml");

        try
        {
            if (!configFile.exists())
            {

                configFile.createNewFile();

                try (InputStream is = getResourceAsStream("bungeeConfig.yml");
                     OutputStream os = new FileOutputStream(configFile))
                {
                    ByteStreams.copy(is, os);
                }
            }

            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
            ConfigurationProvider.getProvider(YamlConfiguration.class).save(config, configFile);
        }
        catch (IOException e)
        {
            getLogger().warning("[E] Neviem nacitat config: "+e.getCause().toString());
        }

        getProxy().getPluginManager().registerListener(this, this);

        authServer = getProxy().getServerInfo(config.getString("auth-server"));

        config.getStringList("lobby-server").forEach(ls -> lobbies.add(getProxy().getServers().get(ls)));

        getProxy().registerChannel( "auth:channel" );

        getLogger().info("[I] GemAuthBungee loaded!");
    }

    @EventHandler
    public void onJoin(PostLoginEvent event)
    {
        try
        {
            if(event.getPlayer().getReconnectServer() != authServer || event.getPlayer().getServer().getInfo() != authServer)
                throw new Exception("Reconnect");
        }
        catch (Exception e) { event.getPlayer().connect(authServer);}

        players.put(event.getPlayer().getName(), 0);
    }

    @EventHandler
    public void onReconnect(ServerSwitchEvent event)
    {
        if(players.getOrDefault(event.getPlayer().getName(), 0) == 0)
        {
            event.getPlayer().connect(authServer);
        }
    }

    @EventHandler
    public void onPluginMsg(PluginMessageEvent event)
    {
        if ( !event.getTag().equalsIgnoreCase("auth:channel"))
            return;


        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
        String subChannel = in.readUTF();

        //getLogger().info("SubCh: "+subChannel);

        if (subChannel.equalsIgnoreCase( "logged"))
        {
            //getLogger().info("R: "+event.getReceiver().toString());
            players.put(event.getReceiver().toString(), 50);
            getProxy().getPlayer(event.getReceiver().toString()).connect(lobbies.get(0));
        }
    }

    @EventHandler
    public void onQuit(PlayerDisconnectEvent event)
    {
        players.remove(event.getPlayer().getName());
    }
}
