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


        binding["AGENT_EVENT_OBJECT_CLIENT_ID"] = "ST03BPM24d233c148-fbb2-4809-8039-212b3fe48d01182023-12-11T11:10:43.745Z014"

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
