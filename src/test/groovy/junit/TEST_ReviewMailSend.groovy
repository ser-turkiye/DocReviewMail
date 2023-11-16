package junit

import de.ser.doxis4.agentserver.AgentExecutionResult
import org.junit.*
import ser.ReviewMailSend

class TEST_ReviewMailSend {

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
        def agent = new ReviewMailSend();


        binding["AGENT_EVENT_OBJECT_CLIENT_ID"] = "ST03BPM247bd93cf1-cad4-4e4c-8a9e-a9fba3747641182023-11-16T08:05:30.617Z014"

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
