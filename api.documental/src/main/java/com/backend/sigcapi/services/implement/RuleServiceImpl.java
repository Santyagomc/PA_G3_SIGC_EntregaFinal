package com.backend.sigcapi.services.implement;

import java.util.List;
import org.springframework.stereotype.Service;

import com.backend.sigcapi.dto.RequestDTO;
import com.backend.sigcapi.dto.RuleDTO;
import com.backend.sigcapi.entities.Rule;
import com.backend.sigcapi.entities.Standard;
import com.backend.sigcapi.repositories.RuleRepository;
import com.backend.sigcapi.repositories.StandardRepository;
import com.backend.sigcapi.services.IExpertEngineService;
import com.backend.sigcapi.services.IRuleService;

@Service
public class RuleServiceImpl implements IRuleService {

    private final RuleRepository repository;
    private final StandardRepository standardRepository;
    private final IExpertEngineService expertEngineService;

    public RuleServiceImpl(RuleRepository repository,
                           StandardRepository standardRepository,
                           IExpertEngineService expertEngineService) {
        this.repository = repository;
        this.standardRepository = standardRepository;
        this.expertEngineService = expertEngineService;
    }

    @Override
    public List<Rule> all() {
        return repository.findAll();
    }

    @Override
    public Rule save(RuleDTO ruleDto) {
        Standard standard = standardRepository.findById(ruleDto.standardId())
                .orElseThrow(() -> new RuntimeException("Estándar no encontrado."));

        Rule rule = new Rule();
        rule.setFullName(ruleDto.fullName());
        rule.setOperator(ruleDto.operator());
        rule.setStandard(standard);
        rule.setSuggestion(ruleDto.suggestion());
        rule.setValue(ruleDto.value());
        rule.setIsBlocking(ruleDto.isBlocking());

        return repository.save(rule);
    }

    @Override
    public List<String> consultLegalDiagnosis(RequestDTO request, int documentSize) {
        List<Rule> rules = repository.findAll();
        // El motor procesa si se rompe alguna regla de las Leyes 1437 o 1581
        return expertEngineService.executeRulesBroken(request, documentSize, rules);
    }
}