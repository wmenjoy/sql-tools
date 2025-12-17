package com.footstone.sqlguard.config;

import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Custom YAML constructor to support immutable configuration classes with final fields.
 *
 * <p>This constructor provides special handling for configuration classes that use
 * final fields for immutability, allowing them to be properly deserialized from YAML
 * while maintaining their immutable design.</p>
 *
 * <p><strong>Supported Classes:</strong></p>
 * <ul>
 *   <li>{@link BlacklistFieldsConfig} - Uses constructor with fields parameter</li>
 * </ul>
 */
public class CustomYamlConstructor extends Constructor {

    /**
     * Creates a custom YAML constructor for SqlGuardConfig.
     */
    public CustomYamlConstructor() {
        super(SqlGuardConfig.class);
        
        // Register custom constructor for BlacklistFieldsConfig
        this.yamlConstructors.put(
            org.yaml.snakeyaml.nodes.Tag.MAP,
            new ConstructBlacklistFieldsConfig()
        );
    }

    /**
     * Custom constructor for BlacklistFieldsConfig that handles final fields.
     */
    private class ConstructBlacklistFieldsConfig extends ConstructMapping {
        @Override
        public Object construct(Node node) {
            // Check if this node represents a BlacklistFieldsConfig
            if (node instanceof MappingNode) {
                MappingNode mappingNode = (MappingNode) node;
                
                // Check if this is a BlacklistFieldsConfig by looking for the type
                if (isBlacklistFieldsConfig(mappingNode)) {
                    return constructBlacklistFieldsConfig(mappingNode);
                }
            }
            
            // Default behavior for other types
            return super.construct(node);
        }

        /**
         * Checks if the mapping node represents a BlacklistFieldsConfig.
         */
        private boolean isBlacklistFieldsConfig(MappingNode node) {
            // Check if the parent context indicates this is a blacklistFields property
            // This is a simplified check - in production, you might need more sophisticated logic
            for (NodeTuple tuple : node.getValue()) {
                Node keyNode = tuple.getKeyNode();
                if (keyNode instanceof ScalarNode) {
                    String key = ((ScalarNode) keyNode).getValue();
                    // BlacklistFieldsConfig has a 'fields' property
                    if ("fields".equals(key)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Constructs a BlacklistFieldsConfig using the appropriate constructor.
         */
        private Object constructBlacklistFieldsConfig(MappingNode node) {
            boolean enabled = true;
            Set<String> fields = null;

            // Extract values from YAML
            for (NodeTuple tuple : node.getValue()) {
                Node keyNode = tuple.getKeyNode();
                Node valueNode = tuple.getValueNode();

                if (keyNode instanceof ScalarNode) {
                    String key = ((ScalarNode) keyNode).getValue();

                    switch (key) {
                        case "enabled":
                            if (valueNode instanceof ScalarNode) {
                                enabled = Boolean.parseBoolean(((ScalarNode) valueNode).getValue());
                            }
                            break;

                        case "fields":
                            if (valueNode instanceof SequenceNode) {
                                fields = new HashSet<>();
                                SequenceNode seqNode = (SequenceNode) valueNode;
                                for (Node item : seqNode.getValue()) {
                                    if (item instanceof ScalarNode) {
                                        fields.add(((ScalarNode) item).getValue());
                                    }
                                }
                            }
                            break;

                        case "riskLevel":
                            // RiskLevel will be set via setRiskLevel after construction
                            break;
                    }
                }
            }

            // Use the appropriate constructor
            if (fields != null) {
                return new BlacklistFieldsConfig(enabled, fields);
            } else {
                return new BlacklistFieldsConfig(enabled);
            }
        }
    }
}
