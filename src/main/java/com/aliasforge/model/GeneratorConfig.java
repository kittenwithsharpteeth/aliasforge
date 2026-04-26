package com.aliasforge.model;

/**
 * Configuração completa para geração de usernames.
 * Passada do UI para o NameGenerator.
 */
public class GeneratorConfig {

    public enum Mode { RANDOM, PRONOUNCEABLE }

    // ── Geração ────────────────────────────────────────────────────────
    private int    quantity;       // -1 = infinito
    private int    minLength;
    private int    maxLength;
    private Mode   mode;

    // ── Filtros de texto ───────────────────────────────────────────────
    private String startsWith;
    private String endsWith;
    private String contains;

    // ── Caracteres permitidos ──────────────────────────────────────────
    private boolean useLetters;
    private boolean useNumbers;
    private boolean useUnderscore;
    private boolean usePeriod;
    private String  customChars;   // caracteres customizados adicionados pelo usuário

    // ── Plataforma ─────────────────────────────────────────────────────
    private Platform platform;

    // Construtor com defaults sensatos
    public GeneratorConfig() {
        this.quantity      = 20;
        this.minLength     = 3;
        this.maxLength     = 5;
        this.mode          = Mode.RANDOM;
        this.startsWith    = "";
        this.endsWith      = "";
        this.contains      = "";
        this.useLetters    = true;
        this.useNumbers    = false;
        this.useUnderscore = false;
        this.usePeriod     = false;
        this.customChars   = "";
        this.platform      = Platform.MINECRAFT;
    }

    /** Monta a string de caracteres válidos com base nas opções selecionadas. */
    public String buildCharset() {
        StringBuilder sb = new StringBuilder();
        if (useLetters)    sb.append("abcdefghijklmnopqrstuvwxyz");
        if (useNumbers)    sb.append("0123456789");
        if (useUnderscore) sb.append("_");
        if (usePeriod)     sb.append(".");
        if (!customChars.isEmpty()) sb.append(customChars);
        return sb.isEmpty() ? "abcdefghijklmnopqrstuvwxyz" : sb.toString();
    }

    public boolean isInfinite() { return quantity == -1; }

    // ── Getters e Setters ──────────────────────────────────────────────

    public int      getQuantity()      { return quantity; }
    public int      getMinLength()     { return minLength; }
    public int      getMaxLength()     { return maxLength; }
    public Mode     getMode()          { return mode; }
    public String   getStartsWith()    { return startsWith; }
    public String   getEndsWith()      { return endsWith; }
    public String   getContains()      { return contains; }
    public boolean  isUseLetters()     { return useLetters; }
    public boolean  isUseNumbers()     { return useNumbers; }
    public boolean  isUseUnderscore()  { return useUnderscore; }
    public boolean  isUsePeriod()      { return usePeriod; }
    public String   getCustomChars()   { return customChars; }
    public Platform getPlatform()      { return platform; }

    public void setQuantity(int quantity)           { this.quantity = quantity; }
    public void setMinLength(int minLength)         { this.minLength = minLength; }
    public void setMaxLength(int maxLength)         { this.maxLength = maxLength; }
    public void setMode(Mode mode)                  { this.mode = mode; }
    public void setStartsWith(String startsWith)    { this.startsWith = startsWith; }
    public void setEndsWith(String endsWith)        { this.endsWith = endsWith; }
    public void setContains(String contains)        { this.contains = contains; }
    public void setUseLetters(boolean useLetters)   { this.useLetters = useLetters; }
    public void setUseNumbers(boolean useNumbers)   { this.useNumbers = useNumbers; }
    public void setUseUnderscore(boolean v)         { this.useUnderscore = v; }
    public void setUsePeriod(boolean usePeriod)     { this.usePeriod = usePeriod; }
    public void setCustomChars(String customChars)  { this.customChars = customChars; }
    public void setPlatform(Platform platform)      { this.platform = platform; }
}