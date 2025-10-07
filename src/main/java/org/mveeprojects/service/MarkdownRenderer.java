package org.mveeprojects.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;

@Service
public class MarkdownRenderer {

    public String renderJsonToMarkdown(JsonNode jsonNode) {
        StringBuilder markdown = new StringBuilder();

        if (jsonNode.isObject()) {
            renderObject(jsonNode, markdown, 0);
        } else if (jsonNode.isArray()) {
            renderArray(jsonNode, markdown, 0);
        } else {
            markdown.append(jsonNode.asText());
        }

        return markdown.toString();
    }

    private void renderObject(JsonNode objectNode, StringBuilder markdown, int depth) {
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();

        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();

            // Add indentation based on depth
            markdown.append("  ".repeat(depth));

            if (value.isObject()) {
                markdown.append("**").append(key).append(":**\n");
                renderObject(value, markdown, depth + 1);
            } else if (value.isArray()) {
                markdown.append("**").append(key).append(":**\n");
                renderArray(value, markdown, depth + 1);
            } else {
                markdown.append("**").append(key).append(":** ");
                if (value.isTextual()) {
                    markdown.append(value.asText());
                } else {
                    markdown.append("`").append(value.toString()).append("`");
                }
                markdown.append("\n");
            }
        }
    }

    private void renderArray(JsonNode arrayNode, StringBuilder markdown, int depth) {
        for (int i = 0; i < arrayNode.size(); i++) {
            JsonNode item = arrayNode.get(i);
            markdown.append("  ".repeat(depth)).append("• ");

            if (item.isObject()) {
                markdown.append("\n");
                renderObject(item, markdown, depth + 1);
            } else if (item.isArray()) {
                markdown.append("\n");
                renderArray(item, markdown, depth + 1);
            } else {
                if (item.isTextual()) {
                    markdown.append(item.asText());
                } else {
                    markdown.append("`").append(item.toString()).append("`");
                }
                markdown.append("\n");
            }
        }
    }
}
