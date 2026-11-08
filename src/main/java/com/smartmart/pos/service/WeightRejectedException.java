package com.smartmart.pos.service;

import com.smartmart.pos.domain.CartRuleException;

/**
 * The weight typed for a weighed product was refused. Carries the product, so
 * the cart screen can ask for the weight again without a new scan (FR-POS-2).
 */
public class WeightRejectedException extends CartRuleException {

    private final transient WeightPrompt prompt;

    public WeightRejectedException(String message, WeightPrompt prompt) {
        super(message);
        this.prompt = prompt;
    }

    public WeightPrompt prompt() {
        return prompt;
    }
}
