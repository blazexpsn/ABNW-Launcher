package org.teamzetaverse.launcher.util;

import java.util.ArrayList;
import java.util.List;

public final class CommandLine {
    private CommandLine() {
    }

    public static List<String> split(final String line) {
        List<String> arguments = new ArrayList<>();
        if (line == null) {
            return arguments;
        }
        StringBuilder current = new StringBuilder();
        boolean inArgument = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else if (c == '\\' && quote == '"' && i + 1 < line.length() && (line.charAt(i + 1) == '"' || line.charAt(i + 1) == '\\')) {
                    current.append(line.charAt(++i));
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                inArgument = true;
            } else if (Character.isWhitespace(c)) {
                if (inArgument) {
                    arguments.add(current.toString());
                    current.setLength(0);
                    inArgument = false;
                }
            } else if (c == '\\' && i + 1 < line.length() && (line.charAt(i + 1) == '"' || line.charAt(i + 1) == '\'')) {
                current.append(line.charAt(++i));
                inArgument = true;
            } else {
                current.append(c);
                inArgument = true;
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException("Unclosed " + quote + " quote in: " + line);
        }
        if (inArgument) {
            arguments.add(current.toString());
        }
        return arguments;
    }
}
