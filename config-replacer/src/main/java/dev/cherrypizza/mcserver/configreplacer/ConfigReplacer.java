package dev.cherrypizza.mcserver.configreplacer;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ConfigReplacer {
    private static final MustacheFactory MUSTACHE_FACTORY;

    static {
        var mf = new DefaultMustacheFactory();
        mf.setObjectHandler(new NotNullMapObjectHandler());
        MUSTACHE_FACTORY = mf;
    }

    private static final Pattern ENV_READER = Pattern.compile("\\$\\{(?<env>[\\w-]+)(:(?<fallback>.*?))?}");

    public static void replace(String[] args) throws IOException {
        var sourceDir = new File(args[0]);
        var targetDir = new File(args[1]);
        var properties = loadConfigFromEnv(new File(args[2]));

        try (Stream<Path> stream = Files.walk(sourceDir.toPath())) {
            stream
                    .filter(Files::isRegularFile)
                    .forEach(sourcePath -> {
                        try {
                            Path relative = sourceDir.toPath().relativize(sourcePath);
                            Path targetPath = targetDir.toPath().resolve(relative);
                            Files.createDirectories(targetPath.getParent());
                            compile(sourcePath.toFile(), targetPath.toFile(), properties);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });

        }
    }

    public static String preprocessVarsYaml(File file) throws IOException {
        String content = Files.readString(file.toPath());

        StringBuilder result = new StringBuilder();

        Matcher matcher = ENV_READER.matcher(content);

        int lastEnd = 0;
        while (matcher.find()) {
            // Добавляем текст до найденного выражения
            result.append(content, lastEnd, matcher.start());

            String varName = matcher.group("env");
            String value = System.getenv(varName);  // или ваша другая функция получения переменной

            // Если переменная не найдена — оставляем как есть или кидаем ошибку
            if (value == null) {
                value = matcher.group("fallback");
                if (value == null)
                    throw new IllegalStateException("Env " + varName + " not present and default value is empty");
            }
            result.append(value);

            lastEnd = matcher.end();
        }

        // Добавляем остаток строки после последнего заменённого места
        result.append(content, lastEnd, content.length());

        return result.toString();
    }

    public static Map<String, Object> loadConfigFromEnv(File configFile) throws IOException {
        var yamlValue = preprocessVarsYaml(configFile);
        return new Yaml().load(yamlValue);
    }


    public static void compile(File input, File output, Map<String, Object> params) throws IOException {
        var writer = new StringWriter();

        try (
                var fileReader = new FileInputStream(input);
                var inputStreamReader = new InputStreamReader(fileReader, StandardCharsets.UTF_8);
                var reader = new BufferedReader(inputStreamReader);
        ) {

            Mustache mustache = MUSTACHE_FACTORY.compile(reader, input.getPath());
            mustache.execute(writer, params).flush();
        }
        Files.writeString(output.toPath(), writer.toString());
    }
}
