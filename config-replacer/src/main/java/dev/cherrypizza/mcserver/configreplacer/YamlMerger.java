package dev.cherrypizza.mcserver.configreplacer;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class YamlMerger {

    public static void merge(String[] args) {

    }


    public static void deepMergeYaml(File dst, File src, File writeTo) throws IOException {
        var yaml = new Yaml();
        try (FileReader dstReader = new FileReader(dst, StandardCharsets.UTF_8);
             FileReader srcReader = new FileReader(src, StandardCharsets.UTF_8)) {

            Object dstYaml = yaml.load(dstReader);
            Object srcYaml = yaml.load(srcReader);

            Object resultYaml = deepMergeYaml(dstYaml, srcYaml);

            String resultString = yaml.dump(resultYaml);

            Files.writeString(writeTo.toPath(), resultString, StandardCharsets.UTF_8);
        }
    }

    @SuppressWarnings("unchecked")
    public static Object deepMergeYaml(Object dst, Object src) {
        // Политика: src перебивает dst при конфликте скалярных значений

        if (dst instanceof Map && src instanceof Map) {
            Map<String, Object> dstMap = (Map<String, Object>) dst;
            Map<String, Object> srcMap = (Map<String, Object>) src;

            // Создаём новый LinkedHashMap, чтобы сохранить порядок ключей
            Map<String, Object> result = new LinkedHashMap<>();

            // Сначала кладём все значения из destination
            result.putAll(dstMap);

            // Затем мержим значения из source (рекурсивно)
            for (Map.Entry<String, Object> entry : srcMap.entrySet()) {
                String key = entry.getKey();
                Object srcValue = entry.getValue();
                Object dstValue = result.get(key);

                result.put(key, deepMergeYaml(dstValue, srcValue));
            }

            return result;
        }

        if (dst instanceof List && src instanceof List) {
            // Самое простое поведение — полная замена списка
            // Если нужен конкат/мерж списков — нужно будет переписать эту ветку
            return src;
        }

        // Для всех остальных случаев (включая null) — берём значение из src,
        // если оно не null, иначе оставляем dst
        return src != null ? src : dst;
    }

    // Для удобства — вариант с try-with-resources и обработкой исключений
    public static void deepMergeYamlSafe(File dst, File src, File writeTo) {
        try {
            deepMergeYaml(dst, src, writeTo);
        } catch (IOException e) {
            throw new RuntimeException("Failed to merge YAML files: " + dst + " + " + src, e);
        }
    }
}
