package ser;

import com.ser.blueline.*;
import com.ser.blueline.bpm.IBpmService;
import com.ser.blueline.bpm.IDecision;
import com.ser.blueline.bpm.IProcessInstance;
import com.ser.blueline.bpm.ITask;
import de.ser.doxis4.agentserver.UnifiedAgent;
import org.json.JSONObject;

import java.io.File;
import java.text.DecimalFormat;
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

            Date tbgn = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse("08/11/2023 14:11:00");
            Date tend = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").parse("01/11/2023 08:30:00");

            long durd  = 0L, diff = 0L;
            double durh  = 0.0;
            if(tend != null && tbgn != null) {
                diff = (tend.getTime() > tbgn.getTime() ? tend.getTime() - tbgn.getTime() : tbgn.getTime() - tend.getTime());
                durd = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
                durh = (TimeUnit.MINUTES.convert(diff, TimeUnit.MILLISECONDS) - (durd * 24 * 60)) / 60d;
                durh = Double.valueOf((new DecimalFormat("#.##")).format(durh));
            }


            System.out.println("Duration-Day    : " + durd);
            System.out.println("Duration-Hour   : " + durh);


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