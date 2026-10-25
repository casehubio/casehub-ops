package io.casehub.ops.api.deployment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type",
              defaultImpl = PatternTrigger.class)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PatternTrigger.class, name = "pattern"),
    @JsonSubTypes.Type(value = LlmEvaluatedTrigger.class, name = "llm-evaluated")
})
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface AdaptationTrigger
    permits PatternTrigger, LlmEvaluatedTrigger {}
