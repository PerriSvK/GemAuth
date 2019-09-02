package sk.perri.gemissius.gemauth;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Main extends JavaPlugin
{
    private Connection conn = null;

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
            getLogger().warning("[E] Neviem sa pripojit ku DB!");
        }
    }
}
