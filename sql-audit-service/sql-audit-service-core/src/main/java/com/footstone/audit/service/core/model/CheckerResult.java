package com.footstone.audit.service.core.model;

import com.footstone.sqlguard.audit.model.RiskScore;

public record CheckerResult(
    String checkerId,
    RiskScore riskScore,
    String errorMessage
) {
    public static CheckerResult success(String checkerId, RiskScore riskScore) {
        return new CheckerResult(checkerId, riskScore, null);
    }
    
    public static CheckerResult failed(String checkerId, Throwable ex) {
        String msg = ex.getMessage();
        if (msg == null) {
            msg = ex.getClass().getSimpleName();
        }
        return new CheckerResult(checkerId, null, msg);
    }
    
    public boolean isSuccess() {
        return errorMessage == null;
    }
}
