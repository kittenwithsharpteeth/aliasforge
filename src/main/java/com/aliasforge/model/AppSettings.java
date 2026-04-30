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

    // ── Notificações ───────────────────────────────────────────────────
    private boolean notifyOnAvailable   = false;
    private boolean minimizeToTray      = true;

    // ── Sidebar — geração ──────────────────────────────────────────────
    private int     lastQuantity        = 20;
    private boolean lastInfinite        = false;
    private int     lastMinLength       = 3;
    private int     lastMaxLength       = 5;
    private String  lastMode            = "random";
    private String  lastPlatform        = "minecraft";

    // ── Sidebar — filtros ──────────────────────────────────────────────
    private boolean filterStartsWith    = false;
    private String  filterStartsWithVal = "";
    private boolean filterEndsWith      = false;
    private String  filterEndsWithVal   = "";
    private boolean filterContains      = false;
    private String  filterContainsVal   = "";

    // ── Sidebar — caracteres ───────────────────────────────────────────
    private boolean useLetters          = true;
    private boolean useNumbers          = false;
    private boolean useUnderscore       = false;
    private boolean usePeriod           = false;

    // ── Manual verifier ────────────────────────────────────────────────
    private String  lastManualPlatform  = "minecraft";

    public AppSettings() {}

    // ── Getters e Setters — request ────────────────────────────────────

    public int  getDelayBetweenRequestsMs()      { return delayBetweenRequestsMs; }
    public void setDelayBetweenRequestsMs(int v) { this.delayBetweenRequestsMs = v; }

    public int  getRequestTimeoutMs()            { return requestTimeoutMs; }
    public void setRequestTimeoutMs(int v)       { this.requestTimeoutMs = v; }

    public int  getParallelThreads()             { return parallelThreads; }
    public void setParallelThreads(int v)        { this.parallelThreads = v; }

    public int  getMaxRetries()                  { return maxRetries; }
    public void setMaxRetries(int v)             { this.maxRetries = v; }

    public int  getRetryDelaySeconds()           { return retryDelaySeconds; }
    public void setRetryDelaySeconds(int v)      { this.retryDelaySeconds = v; }

    public boolean isNotifyOnAvailable()         { return notifyOnAvailable; }
    public void    setNotifyOnAvailable(boolean v){ this.notifyOnAvailable = v; }

    public boolean isMinimizeToTray()            { return minimizeToTray; }
    public void    setMinimizeToTray(boolean v)  { this.minimizeToTray = v; }

    // ── Getters e Setters — sidebar geração ───────────────────────────

    public int     getLastQuantity()             { return lastQuantity; }
    public void    setLastQuantity(int v)        { this.lastQuantity = v; }

    public boolean isLastInfinite()              { return lastInfinite; }
    public void    setLastInfinite(boolean v)    { this.lastInfinite = v; }

    public int     getLastMinLength()            { return lastMinLength; }
    public void    setLastMinLength(int v)       { this.lastMinLength = v; }

    public int     getLastMaxLength()            { return lastMaxLength; }
    public void    setLastMaxLength(int v)       { this.lastMaxLength = v; }

    public String  getLastMode()                 { return lastMode; }
    public void    setLastMode(String v)         { this.lastMode = v; }

    public String  getLastPlatform()             { return lastPlatform; }
    public void    setLastPlatform(String v)     { this.lastPlatform = v; }

    // ── Getters e Setters — filtros ────────────────────────────────────

    public boolean isFilterStartsWith()          { return filterStartsWith; }
    public void    setFilterStartsWith(boolean v){ this.filterStartsWith = v; }

    public String  getFilterStartsWithVal()      { return filterStartsWithVal; }
    public void    setFilterStartsWithVal(String v){ this.filterStartsWithVal = v; }

    public boolean isFilterEndsWith()            { return filterEndsWith; }
    public void    setFilterEndsWith(boolean v)  { this.filterEndsWith = v; }

    public String  getFilterEndsWithVal()        { return filterEndsWithVal; }
    public void    setFilterEndsWithVal(String v){ this.filterEndsWithVal = v; }

    public boolean isFilterContains()            { return filterContains; }
    public void    setFilterContains(boolean v)  { this.filterContains = v; }

    public String  getFilterContainsVal()        { return filterContainsVal; }
    public void    setFilterContainsVal(String v){ this.filterContainsVal = v; }

    // ── Getters e Setters — caracteres ────────────────────────────────

    public boolean isUseLetters()                { return useLetters; }
    public void    setUseLetters(boolean v)      { this.useLetters = v; }

    public boolean isUseNumbers()                { return useNumbers; }
    public void    setUseNumbers(boolean v)      { this.useNumbers = v; }

    public boolean isUseUnderscore()             { return useUnderscore; }
    public void    setUseUnderscore(boolean v)   { this.useUnderscore = v; }

    public boolean isUsePeriod()                 { return usePeriod; }
    public void    setUsePeriod(boolean v)       { this.usePeriod = v; }

    // ── Getters e Setters — manual ─────────────────────────────────────

    public String  getLastManualPlatform()        { return lastManualPlatform; }
    public void    setLastManualPlatform(String v){ this.lastManualPlatform = v; }
}