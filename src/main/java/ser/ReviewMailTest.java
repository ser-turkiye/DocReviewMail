package ser;

import com.ser.blueline.*;
import com.ser.blueline.bpm.IBpmService;
import com.ser.blueline.bpm.IDecision;
import com.ser.blueline.bpm.IProcessInstance;
import com.ser.blueline.bpm.ITask;
import de.ser.doxis4.agentserver.UnifiedAgent;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class ReviewMailTest extends UnifiedAgent {

    ISession ses;
    IDocumentServer srv;
    IBpmService bpm;
    private ProcessHelper helper;
    @Override
    protected Object execute() {
        if (getEventTask() == null)
            return resultError("Null Document object");

        com.spire.license.LicenseProvider.setLicenseKey(Conf.Licences.SPIRE_XLS);

        ses = getSes();
        srv = ses.getDocumentServer();
        bpm = getBpm();
        try {
            this.helper = new ProcessHelper(ses);
            ITask task = getEventTask();

            this.helper = new ProcessHelper(ses);

            IProcessInstance proi = task.getProcessInstance();



            String prjn = proi.getDescriptorValue(Conf.Descriptors.ProjectNo, String.class);
            if(prjn.isEmpty()){
                throw new Exception("Project no is empty.");
            }
            String mdid = proi.getDescriptorValue(Conf.Descriptors.MainDocRef, String.class);
            if(mdid.isEmpty()){
                throw new Exception("Main Doc Ref is empty.");
            }

            IInformationObject prjt = Utils.getProjectWorkspace(prjn, helper);
            if(prjt == null){
                throw new Exception("Project not found [" + prjn + "].");
            }
            IDocument mainDoc = srv.getDocument4ID(mdid , ses);
            if(mainDoc == null){
                throw new Exception("Main Document not found [" + mdid + "].");
            }

            String uniqueId = UUID.randomUUID().toString();
            Collection<ITask> tsks = proi.findTasks();

            JSONObject rvws = new JSONObject();

            Date tbgn = null, tend = null;
            Integer tcnt = 0, ccnt = 0;
            for(ITask ttsk : tsks){
                if(ttsk.getCreationDate() != null
                        && (tbgn == null  || tbgn.after(ttsk.getCreationDate()))){
                    tbgn = ttsk.getCreationDate();
                }
                if(ttsk.getFinishedDate() != null
                        && (tend == null  || tend.before(ttsk.getFinishedDate()))){
                    tend = ttsk.getFinishedDate();
                }
                String tnam = (ttsk.getName() != null ? ttsk.getName() : "");
                String tcod = (ttsk.getCode() != null ? ttsk.getCode() : "");

                tcnt++;

                System.out.println("TASK-Name[" + tcnt + "]:" + tnam);
                System.out.println("TASK-Code[" + tcnt + "]:" + tcod);

                if(tnam.equals("Start Task")
                        || tcod.equals("Step01")){
                    rvws.put("Step01", ttsk);
                    continue;
                }
                if(tnam.equals("Consolidator Review")
                        || tcod.equals("Step03")){
                    ccnt++;
                    rvws.put("Step03_" + (ccnt <= 9 ? "0" : "") + ccnt, ttsk);
                    continue;
                }
                if(tnam.equals("Cross checks & prepare transmittal")
                        || tcod.equals("Step04")){
                    rvws.put("Step04", ttsk);
                    continue;
                }
            }

            System.out.println("Tested.");

        } catch (Exception e) {
            //throw new RuntimeException(e);
            System.out.println("Exception       : " + e.getMessage());
            System.out.println("    Class       : " + e.getClass());
            System.out.println("    Stack-Trace : " + e.getStackTrace() );
            return resultError("Exception : " + e.getMessage());
        }

        System.out.println("Finished");
        return resultSuccess("Ended successfully");
    }
}