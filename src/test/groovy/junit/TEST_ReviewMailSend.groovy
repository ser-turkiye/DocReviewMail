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


        binding["AGENT_EVENT_OBJECT_CLIENT_ID"] = "ST03BPM2491cc9af4-10cb-4778-a19e-79fc3e5e1381182023-11-20T12:06:06.640Z0214"

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
