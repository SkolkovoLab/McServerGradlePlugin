package dev.cherrypizza.mcserver.configreplacer;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class ConfigReplacerMain {


    /**
     * 1 - Папка с которой работаем
     * 2 - Папка с результатом
     * 3 - Стандартный env.yml файл
     * <p>
     * Так же берёт значения из ENV.
     */
    public static void main(String[] args) throws IOException {
        var commonArgs = Arrays.copyOfRange(args, 1, args.length);

        if (Objects.equals(args[0], "replace")) {
            ConfigReplacer.replace(commonArgs);
        } else if (Objects.equals(args[0], "merge")) {
            YamlMerger.merge(commonArgs);
        } else {
            System.err.println("Unknown command: " + (args.length > 0 ? args[0] : "<none>") + " (expected 'replace' or 'merge')");
        }
    }

}
