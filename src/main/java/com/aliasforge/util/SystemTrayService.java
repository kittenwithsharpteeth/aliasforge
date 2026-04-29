package com.aliasforge.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.net.URL;

/**
 * Gerencia o ícone na bandeja do sistema (system tray).
 * Permite minimizar o app para a bandeja e continuar rodando em background.
 */
public class SystemTrayService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SystemTrayService.class);

    private static SystemTrayService instance;

    private SystemTray tray;
    private TrayIcon   trayIcon;
    private boolean    installed = false;

    // Callbacks
    private Runnable onShow;
    private Runnable onExit;
    private Runnable onPause;
    private Runnable onResume;

    private SystemTrayService() {}

    public static synchronized SystemTrayService getInstance() {
        if (instance == null) instance = new SystemTrayService();
        return instance;
    }

    // ── Instalação ─────────────────────────────────────────────────────

    public void install(Runnable onShow, Runnable onExit,
                        Runnable onPause, Runnable onResume) {
        if (!SystemTray.isSupported()) {
            LOGGER.warn("System tray not supported on this platform.");
            return;
        }

        this.onShow   = onShow;
        this.onExit   = onExit;
        this.onPause  = onPause;
        this.onResume = onResume;

        try {
            tray = SystemTray.getSystemTray();

            Image image = loadIcon();
            PopupMenu popup = buildPopupMenu();

            trayIcon = new TrayIcon(image, "AliasForge", popup);
            trayIcon.setImageAutoSize(true);

            trayIcon.addActionListener(e -> {
                if (onShow != null) onShow.run();
            });

            tray.add(trayIcon);
            installed = true;

            LOGGER.info("System tray icon installed.");

        } catch (AWTException e) {
            LOGGER.error("Failed to install system tray icon", e);
        }
    }

    private PopupMenu buildPopupMenu() {
        PopupMenu popup = new PopupMenu();

        MenuItem itemShow = new MenuItem("Open AliasForge");
        itemShow.addActionListener(e -> { if (onShow != null) onShow.run(); });

        MenuItem itemPause = new MenuItem("Pause Checker");
        itemPause.addActionListener(e -> { if (onPause != null) onPause.run(); });

        MenuItem itemResume = new MenuItem("Resume Checker");
        itemResume.addActionListener(e -> { if (onResume != null) onResume.run(); });

        MenuItem itemExit = new MenuItem("Exit");
        itemExit.addActionListener(e -> { if (onExit != null) onExit.run(); });

        popup.add(itemShow);
        popup.addSeparator();
        popup.add(itemPause);
        popup.add(itemResume);
        popup.addSeparator();
        popup.add(itemExit);

        return popup;
    }

    private Image loadIcon() {
        try {
            URL url = getClass().getResource("/icons/icon.png");
            if (url != null) {
                return Toolkit.getDefaultToolkit().getImage(url);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not load tray icon from resources: {}", e.getMessage());
        }
        return createFallbackIcon();
    }

    private Image createFallbackIcon() {
        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(16, 16,
                        java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0x4a90d9));
        g.fillOval(1, 1, 14, 14);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString("A", 4, 12);
        g.dispose();
        return img;
    }

    // ── Controle ───────────────────────────────────────────────────────

    public void uninstall() {
        if (installed && tray != null && trayIcon != null) {
            tray.remove(trayIcon);
            installed = false;
            LOGGER.info("System tray icon removed.");
        }
    }

    public void showMessage(String title, String message) {
        if (installed && trayIcon != null) {
            trayIcon.displayMessage(title, message, TrayIcon.MessageType.INFO);
        }
    }

    public void setTooltip(String text) {
        if (installed && trayIcon != null) {
            trayIcon.setToolTip(text);
        }
    }

    public boolean isInstalled() { return installed; }
    public TrayIcon getTrayIcon() { return trayIcon; }
}