package com.ravi.controlcenter.model;

import java.util.List;

public record LearningPattern(
    String id,
    String title,
    String category,
    String problem,
    String concept,
    String implementation,
    List<String> services,
    String codeAnchor,
    String tradeOffs
) {
}
