package com.aliasforge.model;

/**
 * Configurações persistidas da Custom API.
 * Salvo junto com AppSettings em settings.json via Gson.
 *
 * Suporta dois modos de detecção:
 * - STATUS_CODE  → interpreta o HTTP status code retornado
 * - BODY_SCRAPING → lê o body e procura por strings configuradas
 *
 * Quando ambos estão habilitados, STATUS_CODE é tentado primeiro;
 * se inconclusivo (ex: sempre retorna 200), cai no BODY_SCRAPING.
 */
public class CustomApiSettings {

    public enum DetectionMode {
        STATUS_CODE,
        BODY_SCRAPING,
        BOTH   // status code primeiro, scraping como fallback
    }

    // ── Endpoint ───────────────────────────────────────────────────────
    private String  endpointUrl        = "";          // ex: https://api.example.com/users/
    private int     delayMs            = 1000;
    private int     timeoutMs          = 12000;
    private String  customHeaders      = "";          // JSON: {"Authorization": "Bearer TOKEN"}
    private boolean enabled            = false;

    // ── Modo de detecção ───────────────────────────────────────────────
    private DetectionMode detectionMode = DetectionMode.STATUS_CODE;

    // ── Status code mode ───────────────────────────────────────────────
    // Qual status code indica que o username está disponível
    private int availableStatusCode    = 404;
    // Qual status code indica que o username está ocupado
    private int takenStatusCode        = 200;
    // Status code que indica rate limit (0 = não configurado)
    private int rateLimitStatusCode    = 429;

    // ── Body scraping mode ─────────────────────────────────────────────
    // String que, se encontrada no body, indica que o username está DISPONÍVEL
    private String availableBodyString = "";   // ex: "user not found", "404", "does not exist"
    // String que, se encontrada no body, indica que o username está OCUPADO
    private String takenBodyString     = "";   // ex: "profile", "followers", "og:title"
    // Quantas linhas do body ler (0 = tudo — cuidado com respostas grandes)
    private int    bodyReadLines       = 80;

    public CustomApiSettings() {}

    // ── Validação básica ───────────────────────────────────────────────

    /**
     * Retorna true se as configurações mínimas estão preenchidas
     * para permitir habilitar a Custom API.
     */
    public boolean isConfigured() {
        if (endpointUrl == null || endpointUrl.isBlank()) return false;

        return switch (detectionMode) {
            case STATUS_CODE  -> availableStatusCode > 0 && takenStatusCode > 0;
            case BODY_SCRAPING -> isBodyScrapingConfigured();
            case BOTH         -> availableStatusCode > 0 && takenStatusCode > 0
                    && isBodyScrapingConfigured();
        };
    }

    private boolean isBodyScrapingConfigured() {
        // Pelo menos um dos dois (available OU taken) precisa estar configurado
        boolean hasAvailable = availableBodyString != null && !availableBodyString.isBlank();
        boolean hasTaken     = takenBodyString     != null && !takenBodyString.isBlank();
        return hasAvailable || hasTaken;
    }

    // ── Getters e Setters ──────────────────────────────────────────────

    public String        getEndpointUrl()         { return endpointUrl; }
    public void          setEndpointUrl(String v) { this.endpointUrl = v; }

    public int           getDelayMs()             { return delayMs; }
    public void          setDelayMs(int v)        { this.delayMs = v; }

    public int           getTimeoutMs()           { return timeoutMs; }
    public void          setTimeoutMs(int v)      { this.timeoutMs = v; }

    public String        getCustomHeaders()       { return customHeaders; }
    public void          setCustomHeaders(String v){ this.customHeaders = v; }

    public boolean       isEnabled()              { return enabled; }
    public void          setEnabled(boolean v)    { this.enabled = v; }

    public DetectionMode getDetectionMode()       { return detectionMode; }
    public void          setDetectionMode(DetectionMode v) { this.detectionMode = v; }

    public int           getAvailableStatusCode()       { return availableStatusCode; }
    public void          setAvailableStatusCode(int v)  { this.availableStatusCode = v; }

    public int           getTakenStatusCode()           { return takenStatusCode; }
    public void          setTakenStatusCode(int v)      { this.takenStatusCode = v; }

    public int           getRateLimitStatusCode()       { return rateLimitStatusCode; }
    public void          setRateLimitStatusCode(int v)  { this.rateLimitStatusCode = v; }

    public String        getAvailableBodyString()       { return availableBodyString; }
    public void          setAvailableBodyString(String v){ this.availableBodyString = v; }

    public String        getTakenBodyString()           { return takenBodyString; }
    public void          setTakenBodyString(String v)   { this.takenBodyString = v; }

    public int           getBodyReadLines()             { return bodyReadLines; }
    public void          setBodyReadLines(int v)        { this.bodyReadLines = v; }
}