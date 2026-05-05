package com.aliasforge.ui.components;

import com.aliasforge.model.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;

/**
 * ComboBox de plataformas com separadores visuais entre grupos.
 *
 * Itens especiais:
 * - "__GROUP_reliable apis__"  → header de grupo (não selecionável)
 * - "__GROUP_web check__"      → header de grupo (não selecionável)
 * - "__SEP__"                  → separador (não selecionável)
 * - tudo mais                  → displayName de uma Platform
 */
public class GroupedPlatformComboBox extends ComboBox<String> {

    private static final String SEP_PREFIX   = "__GROUP_";
    private static final String SEP_DIVIDER  = "__SEP__";

    public GroupedPlatformComboBox(String defaultValue) {
        build(defaultValue);
    }

    private void build(String defaultValue) {
        getStyleClass().add("af-combo-platform");

        // ── Monta os itens ─────────────────────────────────────────────
        List<String> items = new ArrayList<>();

        // Reliable APIs
        items.add(SEP_PREFIX + Platform.Group.RELIABLE_API.label + "__");
        for (Platform p : Platform.values()) {
            if (p.group == Platform.Group.RELIABLE_API) items.add(p.displayName);
        }

        // Separador
        items.add(SEP_DIVIDER);

        // Web Check
        items.add(SEP_PREFIX + Platform.Group.WEB_CHECK.label + "__");
        for (Platform p : Platform.values()) {
            if (p.group == Platform.Group.WEB_CHECK) items.add(p.displayName);
        }

        getItems().setAll(items);

        // Valor padrão — garante que seja uma plataforma real
        if (defaultValue != null && items.contains(defaultValue)) {
            setValue(defaultValue);
        } else {
            setValue(Platform.MINECRAFT.displayName);
        }

        // ── Cell factory ───────────────────────────────────────────────
        Callback<ListView<String>, ListCell<String>> factory = lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null); setGraphic(null);
                    setDisable(false);
                    setStyle("");
                    return;
                }

                if (item.startsWith(SEP_PREFIX)) {
                    // Header de grupo
                    String label = item
                            .replace(SEP_PREFIX, "")
                            .replace("__", "")
                            .toUpperCase();
                    setText(null);
                    Label lbl = new Label(label);
                    lbl.setStyle(
                            "-fx-text-fill: #4a90d9;" +
                                    "-fx-font-size: 10px;" +
                                    "-fx-font-weight: bold;" +
                                    "-fx-padding: 4px 8px 2px 8px;");
                    setGraphic(lbl);
                    setDisable(true); // não selecionável
                    setStyle("-fx-background-color: #252525;");

                } else if (item.equals(SEP_DIVIDER)) {
                    // Separador
                    setText(null);
                    Separator sep = new Separator();
                    sep.setStyle("-fx-background-color: #383838;");
                    HBox box = new HBox(sep);
                    HBox.setHgrow(sep, Priority.ALWAYS);
                    box.setPadding(new javafx.geometry.Insets(3, 6, 3, 6));
                    setGraphic(box);
                    setDisable(true);
                    setStyle("-fx-background-color: #1e1e1e; -fx-padding: 0;");

                } else {
                    // Plataforma normal
                    setText(item);
                    setGraphic(null);
                    setDisable(false);
                    setStyle("-fx-text-fill: #cccccc; -fx-padding: 3px 8px 3px 16px;");
                }
            }
        };

        setCellFactory(factory);

        // Button cell — mostra só o nome da plataforma selecionada (sem padding extra)
        setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.startsWith(SEP_PREFIX)
                        || item.equals(SEP_DIVIDER)) {
                    setText(null);
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #cccccc;");
                }
            }
        });

        // Impede seleção acidental de headers/separadores ao navegar com teclado
        getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && (newVal.startsWith(SEP_PREFIX) || newVal.equals(SEP_DIVIDER))) {
                getSelectionModel().select(oldVal);
            }
        });
    }

    /**
     * Retorna a Platform selecionada.
     * Nunca retorna null — fallback para MINECRAFT.
     */
    public Platform getSelectedPlatform() {
        String val = getValue();
        if (val == null || val.startsWith(SEP_PREFIX) || val.equals(SEP_DIVIDER)) {
            return Platform.MINECRAFT;
        }
        return Platform.fromString(val);
    }

    /**
     * Seleciona uma plataforma pelo displayName.
     */
    public void selectPlatform(String displayName) {
        if (displayName != null && getItems().contains(displayName)) {
            setValue(displayName);
        }
    }
}