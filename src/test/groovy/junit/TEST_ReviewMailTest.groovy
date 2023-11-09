package junit

import de.ser.doxis4.agentserver.AgentExecutionResult
import org.junit.*
import ser.ReviewMailTest

class TEST_ReviewMailTest {

    Binding binding

    @BeforeClass
    static void initSessionPool() {
        AgentTester.initSessionPool()
    }

    @Before
    void retrieveBinding() {
        binding = AgentTester.retrieveBinding()
    }

    @Test
    void testForAgentResult() {
        def agent = new ReviewMailTest();

        binding["AGENT_EVENT_OBJECT_CLIENT_ID"] = "ST03BPM242b86099e-d767-4cdb-af47-bea5e6b7a230182023-11-06T07:27:59.089Z016"

        def result = (AgentExecutionResult) agent.execute(binding.variables)
        assert result.resultCode == 0
    }

    @Test
    void testForJavaAgentMethod() {
        //def agent = new JavaAgent()
        //agent.initializeGroovyBlueline(binding.variables)
        //assert agent.getServerVersion().contains("Linux")
    }

    @After
    void releaseBinding() {
        AgentTester.releaseBinding(binding)
    }

    @AfterClass
    static void closeSessionPool() {
        AgentTester.closeSessionPool()
    }
}
