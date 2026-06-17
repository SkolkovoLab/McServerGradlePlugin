package dev.cherrypizza.mcserverkit.configreplacer;

import com.github.mustachejava.reflect.SimpleObjectHandler;

public class NotNullMapObjectHandler extends SimpleObjectHandler {

    @Override
    public Object get(String name, Object scope) {
        var result = super.get(name, scope);
        if (result == NOT_FOUND) throw new IllegalStateException("key " + name + " not found");

        return result;
    }
}
