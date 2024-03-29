package sk.perri.gemissius.gemauth;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

public class User
{
    private Player player;
    private String pass, salt, pepper;
    private int action = -1;
    private int tries = 0;

    public User(Player player)
    {
        this.player = player;
        pass = "";
        pepper = String.valueOf(player.getName().toLowerCase().charAt(0))+player.getName().toLowerCase().charAt(1);
    }

    public void setPass(String pass, String salt)
    {
        this.pass = pass;
        this.salt = salt;
        this.action = 0;
    }

    public void setSalt(String salt)
    {
        this.salt = salt;
        this.action = 1;
    }

    public int getAction()
    {
        return action;
    }

    public String getPass()
    {
        return pass;
    }

    public String getSalt()
    {
        return salt;
    }

    public String getPepper()
    {
        return pepper;
    }

    public void logIn()
    {
        action = 50;
        player.getActivePotionEffects().forEach(pe -> player.removePotionEffect(pe.getType()));
        player.setGameMode(GameMode.SURVIVAL);
    }

    public void addTry()
    {
        tries++;
    }

    public int getTries()
    {
        return tries;
    }
}
