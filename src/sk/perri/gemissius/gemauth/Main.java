package sk.perri.gemissius.gemauth;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.nio.charset.Charset;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Main extends JavaPlugin implements Listener
{
    private Connection conn = null;
    private Location spawnpoint;
    private Map<String, User> users = new HashMap<>();
    private Map<String, BukkitTask> tasks = new HashMap<>();

    @Override
    public void onEnable()
    {
        // config
        if(!getDataFolder().exists())
            getDataFolder().mkdirs();

        getConfig().options().copyDefaults(true);
        saveConfig();

        // DB
        DBConnect();
        createTables();
        // DB ping
        getServer().getScheduler().runTaskTimer(this, () ->
        {
            try
            {
                conn.createStatement().execute("SELECT * FROM users LIMIT 0");
            }
            catch (Exception e)
            {
                DBConnect();
            }
        }, 12000, 12000);

        // spawn
        World w = getServer().getWorld(getConfig().getString("spawnpoint.world"));
        spawnpoint = new Location(w != null ? w : getServer().getWorlds().get(0),
                getConfig().getDouble("spawnpoint.x"),
                getConfig().getDouble("spawnpoint.y"),
                getConfig().getDouble("spawnpoint.z"),
                (float) getConfig().getDouble("spawnpoint.yaw"), (float) getConfig().getDouble("spawnpoint.pitch"));

        getServer().getPluginManager().registerEvents(this, this);


        getLogger().info("[I] GemAuth loaded!");
    }

    private void DBConnect()
    {
        try
        {
            conn = DriverManager.getConnection("jdbc:mysql://" + getConfig().getString("db.host") +
                    ":"+getConfig().getString("db.port")+"/" +getConfig().getString("db.db") + "?useSSL=no&user="+
                    getConfig().getString("db.user")+"&password=" +
                    getConfig().getString("db.pass") + "&useUnicode=true&characterEncoding=UTF-8" +
                    "&autoReconnect=true&failOverReadOnly=false&maxReconnects=5&connectTimeout=2000&socketTimeout=2000");
        }
        catch (SQLException e)
        {
            getLogger().warning("[E] Neviem sa pripojit ku DB! "+e.getMessage());
        }
    }

    private void createTables()
    {
        try
        {
            String SQL_USERS = "CREATE TABLE IF NOT EXISTS `users` ( `nick` VARCHAR(30) NOT NULL , `pass` TEXT NOT NULL , `salt` VARCHAR(10) NOT NULL , `regip` TEXT NOT NULL , `regdate` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP , PRIMARY KEY (`nick`)) ENGINE = MyISAM;";
            conn.createStatement().execute(SQL_USERS);
        }
        catch (SQLException e)
        {
            getLogger().warning("[E] Chyba pri vytvarani tabulky: "+e.getMessage());
        }
    }

    @Override
    public void onDisable()
    {
        try { conn.close(); } catch (SQLException ignore) { }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event)
    {
        event.setJoinMessage("");
        event.getPlayer().teleport(spawnpoint);
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 1200, 12, false, false), false);
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 1200, 127, false, false), false);
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 1200, 128, false, false), false);

        String nick = event.getPlayer().getName();

        getServer().getScheduler().runTaskAsynchronously(this, () ->
        {
            User u = new User(event.getPlayer());
            users.put(nick, u);
            loadPassword(nick, u);
        });

        getServer().getScheduler().runTaskLater(this, () ->
        {
            if(event.getPlayer() != null && users.containsKey(event.getPlayer().getName()) && users.get(event.getPlayer().getName()).getAction() < 10)
                event.getPlayer().kickPlayer(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.timeout")));
        }, getConfig().getInt("timeout"));

        final int[] time = {5};
        tasks.put(nick, getServer().getScheduler().runTaskTimer(this, () ->
        {

            if(event.getPlayer() == null || !users.containsKey(nick) || time[0] > getConfig().getInt("timeout"))
            {
                tasks.get(nick).cancel();
            }

            User u = users.get(nick);

            if(u.getAction() > 10)
                tasks.get(nick).cancel();

            String path = "msg."+(u.getAction() == 0 ? "login." : "register.");

            if(time[0] % 3 == 0)
            {
                event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString(path+"chat")));
                event.getPlayer().sendTitle(ChatColor.translateAlternateColorCodes('&', getConfig().getString(path+"title")),
                        ChatColor.translateAlternateColorCodes('&', getConfig().getString(path+"subtitle")), 10, 30, 10);
            }

            time[0]++;
        }, 100, 20));
    }

    private void loadPassword(String player, User user)
    {
        try
        {
            PreparedStatement ps = conn.prepareStatement("SELECT pass, salt FROM users WHERE nick LIKE ? LIMIT 1");
            ps.setString(1, player);
            ps.execute();

            ResultSet rs = ps.getResultSet();
            if(rs.next())
            {
                user.setPass(rs.getString("pass"), rs.getString("salt"));
            }
            else throw new NullPointerException();

        }
        catch (Exception e)
        {
            byte[] array = new byte[8];
            new Random().nextBytes(array);
            user.setSalt(new String(array, Charset.forName("UTF-8")));
        }
    }
}
