package dev.luzifer.ui.view.viewmodel;

import dev.luzifer.Main;
import dev.luzifer.ui.view.ViewModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.java.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Log
public class SpicetifyViewModel implements ViewModel {

  private static final String SPICETIFY_UPDATE_COMMAND = "spicetify update";
  private static final String SPICETIFY_APPLY_COMMAND = "spicetify apply";
  private static final String SPICETIFY_THEME_COMMAND = "spicetify config current_theme ";
  private static final String SPICETIFY_INSTALL_WINDOWS_COMMAND =
      "iwr -useb https://raw.githubusercontent.com/spicetify/spicetify-cli/master/install.ps1 | iex";
  private static final String SPICETIFY_INSTALL_WINDOWS_MARKETPLACE_COMMAND =
      "iwr -useb https://raw.githubusercontent.com/spicetify/spicetify-marketplace/main/resources/install.ps1 | iex";
  private static final String SPICETIFY_INSTALL_OTHER_COMMAND =
      "curl -fsSL https://raw.githubusercontent.com/spicetify/spicetify-cli/master/install.sh | sh";
  private static final String SPICETIFY_INSTALL_OTHER_MARKETPLACE_COMMAND =
      "curl -fsSL https://raw.githubusercontent.com/spicetify/spicetify-marketplace/main/resources/install.sh | sh";

  private static final Executor EXECUTOR = Executors.newWorkStealingPool();

  private final StringProperty currentThemeProperty = new SimpleStringProperty();
  private final DoubleProperty progressProperty = new SimpleDoubleProperty(0);
  private final IntegerProperty progressMaxProperty = new SimpleIntegerProperty(0);
  private final BooleanProperty updateBeforeApplyProperty = new SimpleBooleanProperty(true);
  private final BooleanProperty notInstalledProperty = new SimpleBooleanProperty(false);
  private final BooleanProperty marketplaceProperty = new SimpleBooleanProperty(false);

  private int progressCount = 0;

  public ObservableList<String> getThemes() {
    List<Path> themes = Main.getThemes();
    return FXCollections.observableList(
        themes.stream().map(Path::getFileName).map(Path::toString).toList());
  }

  public void applyTheme() {
    determineMaxProgress();
    if (updateBeforeApplyProperty.get()) {
      updateSpicetify();
    }
    setCurrentTheme();
    applySpicetify();
  }

  public void resetProgress() {
    progressCount = 0;
    progressProperty.set(0);
  }

  public void checkSpicetifyInstalled() {
    notInstalledProperty.set(!Main.isSpicetifyInstalled());
  }

  public BooleanProperty updateBeforeApplyProperty() {
    return updateBeforeApplyProperty;
  }

  public BooleanProperty notInstalledProperty() {
    return notInstalledProperty;
  }

  public DoubleProperty progressPropertty() {
    return progressProperty;
  }

  public StringProperty currentThemeProperty() {
    return currentThemeProperty;
  }

  public BooleanProperty marketplaceProperty() {
    return marketplaceProperty;
  }

  private void setCurrentTheme() {
    String command = SPICETIFY_THEME_COMMAND + currentThemeProperty.get();
    executeCommand(command);
  }

  private void updateSpicetify() {
    executeCommand(SPICETIFY_UPDATE_COMMAND);
  }

  private void applySpicetify() {
    executeCommand(SPICETIFY_APPLY_COMMAND);
  }

  private void increaseProgress() {
    var maxValue = progressMaxProperty.get();
    progressProperty.set((double) ++progressCount / maxValue);
  }

  private void determineMaxProgress() {
    if (updateBeforeApplyProperty.get()) {
      progressMaxProperty.set(3);
    } else {
      progressMaxProperty.set(2);
    }
  }

  private boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("windows");
  }

  public void install() {
    progressMaxProperty.set(2);
    increaseProgress();

    if (isWindows()) {
      executeCommand(
          marketplaceProperty.get()
              ? SPICETIFY_INSTALL_WINDOWS_MARKETPLACE_COMMAND
              : SPICETIFY_INSTALL_WINDOWS_COMMAND);
    } else {
      executeCommand(
          marketplaceProperty.get()
              ? SPICETIFY_INSTALL_OTHER_MARKETPLACE_COMMAND
              : SPICETIFY_INSTALL_OTHER_COMMAND);
    }
  }

  private void executeCommand(String command) {
    try {
      Process process = createProcess(command);
      log.info("Executing command: " + command);
      EXECUTOR.execute(
          () -> {
            try {
              process.waitFor();
              logOutput(process);
              increaseProgress();
            } catch (InterruptedException e) {
              log.severe("Failed to execute command: " + command);
              throw new RuntimeException(e);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
    } catch (Exception e) {
      log.severe("Failed to execute command: " + command);
      throw new RuntimeException(e);
    }
  }

  private Process createProcess(String command) throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder(prepareCommand(command));
    return processBuilder.start();
  }

  private void logOutput(Process process) throws IOException {
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
      String line;
      log.info("Output of the command is:");
      while ((line = reader.readLine()) != null) {
        System.out.println(line);
      }
    }
  }

  private List<String> prepareCommand(String command) {
    if (isWindows()) {
      return List.of("powershell.exe", "-Command", command);
    } else {
      return List.of(command.split(" "));
    }
  }
}
