package org.teamzetaverse.launcher.minecraft;

import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;

public record InstalledGame(
    JsonObject versionJson,
    List<Path> classpath,
    Path librariesDir,
    Path assetsDir,
    String assetIndexId,
    Path javaExecutable,
    String loggingArgument
) {
}
