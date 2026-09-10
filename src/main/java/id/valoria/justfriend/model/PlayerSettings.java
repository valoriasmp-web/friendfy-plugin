package id.valoria.justfriend.model;

public final class PlayerSettings {
    public boolean neverAlone = true;
    public boolean requests = true;
    public boolean volunteer = false;
    public boolean tracker = true;
    public boolean compass = true;
    public boolean buddyTp = true;
    public boolean sound = true;
    public boolean dnd = false;
    public int socialXp = 0;
    public int reputation = 0;
    public long lastNotice = 0L;

    public PlayerSettings copy() {
        PlayerSettings c = new PlayerSettings();
        c.neverAlone = neverAlone; c.requests = requests; c.volunteer = volunteer;
        c.tracker = tracker; c.compass = compass; c.buddyTp = buddyTp;
        c.sound = sound; c.dnd = dnd; c.socialXp = socialXp;
        c.reputation = reputation; c.lastNotice = lastNotice;
        return c;
    }
}
