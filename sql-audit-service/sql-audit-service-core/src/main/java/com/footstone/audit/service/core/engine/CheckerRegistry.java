package com.footstone.audit.service.core.engine;

import com.footstone.sqlguard.audit.checker.AbstractAuditChecker;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class CheckerRegistry {
    private final Map<String, AbstractAuditChecker> checkerMap;
    private final List<AbstractAuditChecker> allCheckers;

    public CheckerRegistry(List<AbstractAuditChecker> checkers) {
        this.allCheckers = checkers;
        this.checkerMap = checkers.stream()
                .collect(Collectors.toMap(AbstractAuditChecker::getCheckerId, Function.identity()));
    }

    public List<AbstractAuditChecker> getAllCheckers() {
        return allCheckers;
    }

    public Optional<AbstractAuditChecker> getCheckerById(String checkerId) {
        return Optional.ofNullable(checkerMap.get(checkerId));
    }
}
