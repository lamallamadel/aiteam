/**
 * A2A Protocol Contract Testing Framework
 * 
 * <p>This package provides comprehensive contract testing for the Agent-to-Agent (A2A)
 * protocol used by the Atlasia orchestrator to communicate with external agents.</p>
 * 
 * <h2>Contract Test Classes</h2>
 * 
 * <ul>
 *   <li>{@link com.atlasia.ai.contract.A2AContractTest} - Unit tests validating model 
 *       structures against contract definitions</li>
 *   <li>{@link com.atlasia.ai.contract.A2AContractIntegrationTest} - Integration tests 
 *       validating HTTP endpoints against contracts using MockMvc</li>
 *   <li>{@link com.atlasia.ai.contract.ContractValidationTest} - Tests for validation 
 *       utilities and pattern matchers</li>
 *   <li>{@link com.atlasia.ai.contract.A2AContractTestBase} - Base class providing 
 *       common setup and mock data</li>
 *   <li>{@link com.atlasia.ai.contract.ContractValidationHelper} - Utility class for 
 *       contract validation and pattern matching</li>
 * </ul>
 * 
 * <h2>Contract Definitions</h2>
 * 
 * <p>Contract JSON files are located in {@code src/test/resources/contracts/}:</p>
 * 
 * <ul>
 *   <li>{@code agent-card-schema.json} - AgentCard structure and validation</li>
 *   <li>{@code capability-discovery.json} - Capability-based agent discovery</li>
 *   <li>{@code multi-capability-discovery.json} - Multi-capability discovery with scoring</li>
 *   <li>{@code task-submission.json} - Task submission payloads and responses</li>
 *   <li>{@code binding-verification.json} - Agent binding verification responses</li>
 * </ul>
 * 
 * <h2>Tested Components</h2>
 * 
 * <ul>
 *   <li>{@link com.atlasia.ai.service.A2ADiscoveryService.AgentCard} - Agent card model</li>
 *   <li>{@link com.atlasia.ai.service.A2ADiscoveryService#discover(java.util.Set)} - 
 *       Capability discovery</li>
 *   <li>{@link com.atlasia.ai.service.AgentBindingService.AgentBinding} - Binding model</li>
 *   <li>{@link com.atlasia.ai.service.AgentBindingService#verifyBinding(com.atlasia.ai.service.AgentBindingService.AgentBinding)} - 
 *       Binding verification</li>
 *   <li>{@link com.atlasia.ai.controller.A2AController} - A2A HTTP endpoints</li>
 * </ul>
 * 
 * <h2>Running Tests</h2>
 * 
 * <pre>
 * # All contract tests
 * mvn test -Dtest=A2AContract*
 * 
 * # Unit tests only
 * mvn test -Dtest=A2AContractTest
 * 
 * # Integration tests only
 * mvn test -Dtest=A2AContractIntegrationTest
 * 
 * # Validation helper tests
 * mvn test -Dtest=ContractValidationTest
 * </pre>
 * 
 * <h2>Framework Features</h2>
 * 
 * <ul>
 *   <li><b>Schema Validation</b> - Validates data structures against JSON schemas</li>
 *   <li><b>Pattern Matching</b> - Regex-based validation for UUIDs, versions, timestamps</li>
 *   <li><b>Type Checking</b> - Ensures correct field types (String, Integer, Set, etc.)</li>
 *   <li><b>Capability Matching</b> - Validates agent capability coverage and scoring</li>
 *   <li><b>Binding Verification</b> - Tests HMAC signatures and expiry validation</li>
 *   <li><b>HTTP Contract Tests</b> - Validates REST endpoints with REST-assured</li>
 * </ul>
 * 
 * <h2>Contract Versioning</h2>
 * 
 * <p>Contracts follow semantic versioning:</p>
 * <ul>
 *   <li><b>Major</b> - Breaking changes to request/response structure</li>
 *   <li><b>Minor</b> - Additive changes (new optional fields)</li>
 *   <li><b>Patch</b> - Documentation or matcher improvements</li>
 * </ul>
 * 
 * <p>Current version: 1.0.0</p>
 * 
 * @since 1.0
 * @see com.atlasia.ai.service.A2ADiscoveryService
 * @see com.atlasia.ai.service.AgentBindingService
 * @see com.atlasia.ai.controller.A2AController
 */
package com.atlasia.ai.contract;
