package com.atlasia.ai.service;

import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class WorkflowEngine {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowEngine.class);

    private final RunRepository runRepository;
    private final JsonSchemaValidator schemaValidator;
    private final PmStep pmStep;
    private final QualifierStep qualifierStep;
    private final ArchitectStep architectStep;
    private final DeveloperStep developerStep;
    private final TesterStep testerStep;
    private final WriterStep writerStep;

    public WorkflowEngine(
            RunRepository runRepository,
            JsonSchemaValidator schemaValidator,
            PmStep pmStep,
            QualifierStep qualifierStep,
            ArchitectStep architectStep,
            DeveloperStep developerStep,
            TesterStep testerStep,
            WriterStep writerStep) {
        this.runRepository = runRepository;
        this.schemaValidator = schemaValidator;
        this.pmStep = pmStep;
        this.qualifierStep = qualifierStep;
        this.architectStep = architectStep;
        this.developerStep = developerStep;
        this.testerStep = testerStep;
        this.writerStep = writerStep;
    }

    @Transactional
    public void executeWorkflow(RunEntity runEntity) {
        try {
            String[] repoParts = runEntity.getRepo().split("/");
            String owner = repoParts[0];
            String repo = repoParts[1];
            
            RunContext context = new RunContext(runEntity, owner, repo);

            executePmStep(context);
            executeQualifierStep(context);
            executeArchitectStep(context);
            executeDeveloperStep(context);
            executeTesterStep(context);
            executeWriterStep(context);

            runEntity.setStatus(RunStatus.DONE);
            runEntity.setCurrentAgent(null);
            runRepository.save(runEntity);
            
            logger.info("Workflow completed successfully for run: {}", runEntity.getId());

        } catch (EscalationException e) {
            handleEscalation(runEntity, e);
        } catch (Exception e) {
            handleFailure(runEntity, e);
        }
    }

    private void executePmStep(RunContext context) throws Exception {
        logger.info("Executing PM step for run: {}", context.getRunEntity().getId());
        
        RunEntity runEntity = context.getRunEntity();
        runEntity.setStatus(RunStatus.PM);
        runEntity.setCurrentAgent("PM");
        runRepository.save(runEntity);

        String artifact = pmStep.execute(context);
        schemaValidator.validate(artifact, "ticket_plan.schema.json");
        
        context.setTicketPlan(artifact);
        persistArtifact(runEntity, "PM", "ticket_plan.json", artifact);
    }

    private void executeQualifierStep(RunContext context) throws Exception {
        logger.info("Executing Qualifier step for run: {}", context.getRunEntity().getId());
        
        RunEntity runEntity = context.getRunEntity();
        runEntity.setStatus(RunStatus.QUALIFIER);
        runEntity.setCurrentAgent("QUALIFIER");
        runRepository.save(runEntity);

        String artifact = qualifierStep.execute(context);
        schemaValidator.validate(artifact, "work_plan.schema.json");
        
        context.setWorkPlan(artifact);
        persistArtifact(runEntity, "QUALIFIER", "work_plan.json", artifact);
    }

    private void executeArchitectStep(RunContext context) throws Exception {
        logger.info("Executing Architect step for run: {}", context.getRunEntity().getId());
        
        RunEntity runEntity = context.getRunEntity();
        runEntity.setStatus(RunStatus.ARCHITECT);
        runEntity.setCurrentAgent("ARCHITECT");
        runRepository.save(runEntity);

        String artifact = architectStep.execute(context);
        persistArtifact(runEntity, "ARCHITECT", "architecture_notes.md", artifact);
    }

    private void executeDeveloperStep(RunContext context) throws Exception {
        logger.info("Executing Developer step for run: {}", context.getRunEntity().getId());
        
        RunEntity runEntity = context.getRunEntity();
        runEntity.setStatus(RunStatus.DEVELOPER);
        runEntity.setCurrentAgent("DEVELOPER");
        runRepository.save(runEntity);

        String artifact = developerStep.execute(context);
        persistArtifact(runEntity, "DEVELOPER", "pr_url", artifact);
    }

    private void executeTesterStep(RunContext context) throws Exception {
        logger.info("Executing Tester step for run: {}", context.getRunEntity().getId());
        
        RunEntity runEntity = context.getRunEntity();
        runEntity.setStatus(RunStatus.TESTER);
        runEntity.setCurrentAgent("TESTER");
        runRepository.save(runEntity);

        String artifact = testerStep.execute(context);
        schemaValidator.validate(artifact, "test_report.schema.json");
        
        persistArtifact(runEntity, "TESTER", "test_report.json", artifact);
    }

    private void executeWriterStep(RunContext context) throws Exception {
        logger.info("Executing Writer step for run: {}", context.getRunEntity().getId());
        
        RunEntity runEntity = context.getRunEntity();
        runEntity.setStatus(RunStatus.WRITER);
        runEntity.setCurrentAgent("WRITER");
        runRepository.save(runEntity);

        String artifact = writerStep.execute(context);
        persistArtifact(runEntity, "WRITER", "docs_update", artifact);
    }

    private void persistArtifact(RunEntity runEntity, String agentName, String artifactType, String payload) {
        RunArtifactEntity artifact = new RunArtifactEntity(
            agentName,
            artifactType,
            payload,
            Instant.now()
        );
        runEntity.addArtifact(artifact);
        runRepository.save(runEntity);
    }

    private void handleEscalation(RunEntity runEntity, EscalationException e) {
        logger.warn("Workflow escalated for run: {}", runEntity.getId());
        
        try {
            schemaValidator.validate(e.getEscalationJson(), "escalation.schema.json");
            persistArtifact(runEntity, runEntity.getCurrentAgent(), "escalation.json", e.getEscalationJson());
        } catch (Exception validationError) {
            logger.error("Failed to validate escalation JSON", validationError);
        }
        
        runEntity.setStatus(RunStatus.ESCALATED);
        runRepository.save(runEntity);
    }

    private void handleFailure(RunEntity runEntity, Exception e) {
        logger.error("Workflow failed for run: {}", runEntity.getId(), e);
        
        runEntity.setStatus(RunStatus.FAILED);
        runRepository.save(runEntity);
    }
}
