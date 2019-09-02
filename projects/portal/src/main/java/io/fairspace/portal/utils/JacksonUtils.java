package io.fairspace.portal.utils;

import com.fasterxml.jackson.databind.node.ObjectNode;

public class JacksonUtils {
    /**
     * Merges two ObjectNode objects and returns a new ObjectNode instance
     *
     * @param left
     * @param right
     * @return the left object
     */
    public static ObjectNode merge(ObjectNode left, ObjectNode right) {
        var result = left.deepCopy();
        for (var it = right.fields(); it.hasNext(); ) {
            var entry = it.next();

            if (entry.getValue().isObject() && left.path(entry.getKey()).isObject()) {
                result.replace(entry.getKey(), merge((ObjectNode)left.path(entry.getKey()), (ObjectNode)entry.getValue()));
            } else {
                result.replace(entry.getKey(), entry.getValue().deepCopy());
            }
        }

        return result;
    }
}