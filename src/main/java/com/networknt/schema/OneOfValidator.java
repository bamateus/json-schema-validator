/*
 * Copyright (c) 2016 Network New Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.networknt.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class OneOfValidator extends BaseJsonValidator implements JsonValidator {
    private static final Logger logger = LoggerFactory.getLogger(RequiredValidator.class);

    private List<JsonSchema> schemas = new ArrayList<JsonSchema>();

    public OneOfValidator(String schemaPath, JsonNode schemaNode, JsonSchema parentSchema, ObjectMapper mapper) {
        super(schemaPath, schemaNode, parentSchema, ValidatorTypeCode.ONE_OF);
        int size = schemaNode.size();
        for (int i = 0; i < size; i++) {
            schemas.add(new JsonSchema(mapper, getValidatorType().getValue(), schemaNode.get(i), parentSchema));
        }

        parseErrorCode(getValidatorType().getErrorCodeKey());
    }

    public Set<ValidationMessage> validate(JsonNode node, JsonNode rootNode, String at) {
        debug(logger, node, rootNode, at);

        int numberOfValidSchema = 0;
        Set<ValidationMessage> errors = new HashSet<>();

        // ------- Custom behaviour that should be migrated when the Custom Validators are supported -------
        // Please refer to https://github.com/networknt/json-schema-validator/issues/32 for more details.

        // The "@type" value on the request payload.
        JsonNode nodeType = node.findValue("@type");
        if(nodeType != null) {
            // The relation between the "@type" value on the request payload and the schema is through the schema "title" attribute.
            // On the schemas list, we have to return only the schema that matches with the "@type" value.
            JsonSchema schema = schemas.stream().filter(s -> getSchemaNodeTitle(s).equals(nodeType.asText())).findAny().orElse(null);
            // If no match was found, we follow the default behaviour validating against all schemas.
            if(schema != null) {
                Set<ValidationMessage> schemaErrors = schema.validate(node, rootNode, at);
                if (!schemaErrors.isEmpty()) {
                    errors.addAll(schemaErrors);
                }
                return errors;
            }
        }

        // ------------------------------------------------------------------------------------------------

        for (JsonSchema schema : schemas) {
            Set<ValidationMessage> schemaErrors = schema.validate(node, rootNode, at);
            if (schemaErrors.isEmpty()) {
                numberOfValidSchema++;
                errors = new HashSet<>();
            }
            if (numberOfValidSchema == 0) {
                errors.addAll(schemaErrors);
            }
            if (numberOfValidSchema > 1) {
                break;
            }
        }

        if (numberOfValidSchema > 1) {
        	errors = new HashSet<>();
        	errors.add(buildValidationMessage(at, ""));
        }
        
        return errors;
    }

    private String getSchemaNodeTitle(JsonSchema schema) {
        // oneOf node $ref value.
        JsonNode schemaNodeRef = schema.getSchemaNode().get("$ref");
        if(schemaNodeRef == null) {
            return null;
        }

        // Node that is referenced by the $ref value on the oneOf node.
        JsonNode schemaNode = schema.getRefSchemaNode(schemaNodeRef.asText());
        if(schemaNode == null) {
            return null;
        }

        // The title of the node.
        JsonNode schemaNodeTitle = schemaNode.get("title");
        if(schemaNodeTitle == null) {
            return null;
        }

        return schemaNodeTitle.asText();
    }
}
