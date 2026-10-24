package com.aiassistant.ai.dto;

import com.aiassistant.ai.ReplaceabilityVerdict;

/** 可替代性评估出参（US-04）：三态结论与一段说明。 */
public record ReplaceabilityAssessmentResponse(ReplaceabilityVerdict verdict, String explanation) {
}
