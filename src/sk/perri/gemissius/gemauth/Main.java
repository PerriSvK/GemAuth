package sk.perri.gemissius.gemauth;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.sqlite.util.StringUtils;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class Main extends JavaPlugin implements Listener, CommandExecutor
{
    private Connection conn = null;
    private Location spawnpoint;
    private Map<String, User> users = new HashMap<>();
    private Map<String, BukkitTask> tasks = new HashMap<>();

    @Override
    public void onEnable()
    {
        // config
        if (!getDataFolder().exists())
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
        getDescription().getCommands().keySet().forEach(c -> getCommand(c).setExecutor(this));

        // Logger

        LoginFilter lf =  new LoginFilter();
        lf.registerFilter();

        // plugin messaging channel
        getServer().getMessenger().registerOutgoingPluginChannel(this, "auth:channel");

        getLogger().info("[I] GemAuth loaded!");
    }

    private void sendLoginInfo(String player)
    {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        try
        {
            out.writeUTF("logged");
            out.writeUTF(player);
            Bukkit.getServer().sendPluginMessage(this, "auth:channel", out.toByteArray());
            //getLogger().info("Sent");
        }
        catch (Exception e)
        {
            getLogger().warning("[E] Neviem poslat info bungee: "+e.toString());
        }
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
        catch (Exception ignore){}
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
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 12, false, false));
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SLOW, Integer.MAX_VALUE, 240, false, false));
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.JUMP, Integer.MAX_VALUE, 200, true, false));

        event.getPlayer().setGameMode(GameMode.ADVENTURE);

        String nick = event.getPlayer().getName();

        getServer().getScheduler().runTaskAsynchronously(this, () ->
        {
            User u = new User(event.getPlayer());
            users.put(nick, u);
            loadPassword(nick, u);
        });

        final int[] time = {2};
        tasks.put(nick, getServer().getScheduler().runTaskTimer(this, () ->
        {
            try
            {
                if(time[0] > getConfig().getInt("timeout"))
                    event.getPlayer().kickPlayer(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.timeout")));



                if(event.getPlayer() == null || !users.containsKey(nick) || time[0] > getConfig().getInt("timeout"))
                {
                    tasks.get(nick).cancel();
                }

                User u = users.get(nick);

                try
                {
                    if(u.getAction() > 10)
                        tasks.get(nick).cancel();
                }
                catch (Exception ignore){}


                if(u.getTries() > 5)
                {
                    event.getPlayer().kickPlayer(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.log-b")));
                }

                String path = "msg."+(u.getAction() == 0 ? "login." : "register.");

                if(time[0] % 3 == 0 && u.getAction() < 10)
                {
                    event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString(path+"chat")));
                    event.getPlayer().sendTitle(ChatColor.translateAlternateColorCodes('&', getConfig().getString(path+"title")),
                            ChatColor.translateAlternateColorCodes('&', getConfig().getString(path+"subtitle")), 10, 100, 10);
                }

                time[0]++;
            }
            catch (Exception ignore){}
        }, time[0]*20, 20));
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

    private void updatePassword(String player, User user, String newRawPassword)
    {
        try
        {
            // `users` ( `nick` VARCHAR(30) NOT NULL , `pass` TEXT NOT NULL , `salt` VARCHAR(10) NOT NULL , `regip` TEXT NOT NULL , `regdate`
            PreparedStatement ps = conn.prepareStatement("INSERT INTO users VALUES(?, ?, ?, ?, DEFAULT) ON DUPLICATE KEY UPDATE pass=?, salt=?;");
            ps.setString(1, player);
            ps.setString(3, user.getSalt());
            ps.setString(4, getServer().getPlayer(player).getAddress().getHostName());
            ps.setString(6, user.getSalt());
            String a = pass2str(hashPass(player, user, newRawPassword));
            ps.setString(2, a);
            ps.setString(5, a);

            ps.execute();
        }
        catch (SQLException e)
        {
            getLogger().warning("[E] Chyba pri zapisovani do DB: "+e.getMessage());
        }
    }

    private byte[] hashPass(String player, User user, String rawPassword)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String a = user.getSalt()+rawPassword+user.getPepper()+"*/"+player;
            byte[] hash = digest.digest(a.getBytes(StandardCharsets.UTF_8));
            return hash;
        }
        catch (NoSuchAlgorithmException e)
        {
            getLogger().warning("[E] Neviem zahashovat heslo! "+e.toString());
            return null;
        }
    }

    private String pass2str(byte[] hash)
    {
        return String.format("%064x", new BigInteger(1, hash));
    }

    public void logIn(Player player, String rawPassword)
    {
        User user = users.get(player.getName());

        if(user.getAction() > 10)
        {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.log-d")));
            return;
        }

        if(user.getPass().equalsIgnoreCase(""))
        {
            // Reg 1st
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.log-a")));
            return;
        }

        String tPass = pass2str(hashPass(player.getName(), user, rawPassword));

        if(user.getPass().equals(tPass))
        {
            // OK
            user.logIn();
            if(tasks.containsKey(player.getName()))
                tasks.get(player.getName()).cancel();

            sendLoginInfo(player.getName());
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.log-c")));
            getLogger().info(player.getName()+" - login OK");
        }
        else
        {
            // Wrong pass
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.log-b")));
            user.addTry();
            getLogger().info(player.getName()+" - zle heslo");
        }
    }

    public void register(Player player, String rPass1, String rPass2)
    {
        User user = users.get(player.getName());

        if(!user.getPass().equalsIgnoreCase(""))
        {
            // Login 1st
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.reg-a")));
            return;
        }

        String p1, p2;
        p1 = pass2str(hashPass(player.getName(), user, rPass1));
        p2 = pass2str(hashPass(player.getName(), user, rPass2));

        if(!p1.equals(p2))
        {
            // Don't match
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.reg-b")));
        }
        else if(p1.length() < 6)
        {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.reg-d")));
        }
        else
        {
            // OK
            user.setPass(p1, user.getSalt());
            updatePassword(player.getName(), user, rPass1);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("msg.reg-c")));
            getLogger().info(player.getName()+" - reg OK");
        }
    }

    @EventHandler
    public void onCommandPre(PlayerCommandPreprocessEvent event)
    {
        String[] args = event.getMessage().split(" ");

        if(args[0].toLowerCase().startsWith("/log") || args[0].equalsIgnoreCase("/l"))
        {
            if(args.length == 2)
            {
                String rPass = args[1];

                logIn(event.getPlayer(), rPass);

                StringBuilder res = new StringBuilder(args[0]);
                res.append(" ").append(new String(new char[args[1].length()]).replace("\0", "*"));
                getLogger().info(event.getPlayer().getName()+": "+res.toString());
                event.setMessage(res.toString());
            }
            else
            {
                event.setMessage(args[0]);
            }

            event.setCancelled(true);
        }
        else if(args[0].toLowerCase().startsWith("/reg"))
        {
            if(args.length == 3)
            {
                register(event.getPlayer(), args[1], args[2]);

                StringBuilder res = new StringBuilder(args[0]);
                res.append(" ").append(new String(new char[args[1].length()]).replace("\0", "*"));
                res.append(" ").append(new String(new char[args[2].length()]).replace("\0", "*"));

                getLogger().info(event.getPlayer().getName()+": "+res.toString());
                event.setMessage(res.toString());
            }
            else
            {
                event.setMessage(args[0]);
            }

            event.setCancelled(true);
        }
        else
        {
            event.setCancelled(users.containsKey(event.getPlayer().getName()) && users.get(event.getPlayer().getName()).getAction() < 10);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
    {
        getLogger().info("CMD: "+label+" -> "+ StringUtils.join(Arrays.asList(args), ", "));
        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event)
    {
        users.remove(event.getPlayer().getName());
        event.setQuitMessage("");
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event)
    {
        event.setCancelled(event.getEntity() instanceof Player && users.containsKey(event.getEntity().getName()) && users.get(event.getEntity().getName()).getAction() < 10);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event)
    {
        event.setCancelled(event.getEntity() instanceof Player && users.containsKey(event.getEntity().getName()) && users.get(event.getEntity().getName()).getAction() < 10);
    }
}
