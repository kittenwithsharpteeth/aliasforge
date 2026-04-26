package com.aliasforge.model;

/**
 * Todas as configurações persistidas do app em JSON.
 * Salvo em ~/.aliasforge/settings.json via Gson.
 */
public class AppSettings {

    // ── Request settings ───────────────────────────────────────────────
    private int  delayBetweenRequestsMs = 420;
    private int  requestTimeoutMs       = 8000;
    private int  parallelThreads        = 1;
    private int  maxRetries             = 3;
    private int  retryDelaySeconds      = 60;

    // ── UI preferences ─────────────────────────────────────────────────
    private String lastPlatform         = "minecraft";
    private String lastGeneratorMode    = "RANDOM";
    private int    lastQuantity         = 20;
    private int    lastMinLength        = 3;
    private int    lastMaxLength        = 5;

    // ── Notificações ───────────────────────────────────────────────────
    private boolean notifyOnAvailable   = true;
    private boolean minimizeToTray      = false;

    // Construtor com defaults
    public AppSettings() {}

    // ── Getters e Setters ──────────────────────────────────────────────

    public int  getDelayBetweenRequestsMs()          { return delayBetweenRequestsMs; }
    public void setDelayBetweenRequestsMs(int v)     { this.delayBetweenRequestsMs = v; }

    public int  getRequestTimeoutMs()                { return requestTimeoutMs; }
    public void setRequestTimeoutMs(int v)           { this.requestTimeoutMs = v; }

    public int  getParallelThreads()                 { return parallelThreads; }
    public void setParallelThreads(int v)            { this.parallelThreads = v; }

    public int  getMaxRetries()                      { return maxRetries; }
    public void setMaxRetries(int v)                 { this.maxRetries = v; }

    public int  getRetryDelaySeconds()               { return retryDelaySeconds; }
    public void setRetryDelaySeconds(int v)          { this.retryDelaySeconds = v; }

    public String getLastPlatform()                  { return lastPlatform; }
    public void   setLastPlatform(String v)          { this.lastPlatform = v; }

    public String getLastGeneratorMode()             { return lastGeneratorMode; }
    public void   setLastGeneratorMode(String v)     { this.lastGeneratorMode = v; }

    public int  getLastQuantity()                    { return lastQuantity; }
    public void setLastQuantity(int v)               { this.lastQuantity = v; }

    public int  getLastMinLength()                   { return lastMinLength; }
    public void setLastMinLength(int v)              { this.lastMinLength = v; }

    public int  getLastMaxLength()                   { return lastMaxLength; }
    public void setLastMaxLength(int v)              { this.lastMaxLength = v; }

    public boolean isNotifyOnAvailable()             { return notifyOnAvailable; }
    public void    setNotifyOnAvailable(boolean v)   { this.notifyOnAvailable = v; }

    public boolean isMinimizeToTray()                { return minimizeToTray; }
    public void    setMinimizeToTray(boolean v)      { this.minimizeToTray = v; }
}