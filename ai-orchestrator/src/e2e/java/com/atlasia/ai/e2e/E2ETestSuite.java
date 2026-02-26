package com.atlasia.ai.e2e;

import com.atlasia.ai.e2e.scenarios.*;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite
@SuiteDisplayName("Atlasia E2E Test Suite")
@SelectClasses({
    AuthenticationFlowE2ETest.class,
    MfaLoginFlowE2ETest.class,
    OAuth2CallbackFlowE2ETest.class,
    PasswordResetFlowE2ETest.class,
    WebSocketCollaborationE2ETest.class,
    HappyPathCodeGenerationE2ETest.class,
    EnhancedCodeGenerationE2ETest.class
})
public class E2ETestSuite {
}
