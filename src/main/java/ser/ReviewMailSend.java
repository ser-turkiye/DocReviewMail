package ser;

import com.ser.blueline.*;
import com.ser.blueline.bpm.*;
import de.ser.doxis4.agentserver.UnifiedAgent;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ReviewMailSend extends UnifiedAgent {

    Logger log = LogManager.getLogger();
    String uniqueId;
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
            int cnt=5;
            cnt++;

            (new File(Conf.DocReviewPaths.MainPath)).mkdir();
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

            ILink[] slns = srv.getReferencedRelationships(ses, mainDoc, true);
            for(ILink slnk : slns){
                IInformationObject stgt = slnk.getTargetInformationObject();

                if(stgt.getClassID().equals(Conf.ClassIDs.EngineeringAttachments)
                        && Utils.hasDescriptor((IInformationObject) stgt, Conf.Descriptors.DocType)
                        && stgt.getDescriptorValue(Conf.Descriptors.DocType, String.class).equals("Review-History")){
                    srv.removeRelationship(ses, slnk);
                    continue;
                }
                if(stgt.getClassID().equals(Conf.ClassIDs.EngineeringAttachments)
                        && Utils.hasDescriptor((IInformationObject) stgt, Conf.Descriptors.DocType)
                        && stgt.getDescriptorValue(Conf.Descriptors.DocType, String.class).equals("CRS")){
                    srv.removeRelationship(ses, slnk);
                    continue;
                }
            }

            uniqueId = UUID.randomUUID().toString();
            Collection<ITask> tsks = proi.findTasks();

            JSONObject rvws = new JSONObject();

            Date tbgn = null, tend = null;
            Integer tcnt = 0, ccnt = 0;
            for(ITask ttsk : tsks){
                if(ttsk.getStatus() != TaskStatus.COMPLETED){continue;}

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
                if(ttsk.getLoadedParentTask() != null
                && (tnam.equals("Consolidator Review") || tcod.equals("Step03"))){
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

            JSONObject dbks = new JSONObject();
            long durd  = 0L;
            double durh  = 0.00;
            if(tend != null && tbgn != null) {
                proi.setDescriptorValueTyped("ccmPrjProcStart", tbgn);
                proi.setDescriptorValueTyped("ccmPrjProcFinish", tend);

                long diff = (tend.getTime() > tbgn.getTime() ? tend.getTime() - tbgn.getTime() : tbgn.getTime() - tend.getTime());
                durd = TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS);
                durh = ((TimeUnit.MINUTES.convert(diff, TimeUnit.MILLISECONDS) - (durd * 24 * 60)) * 100 / 60) / 100d;
            }

            proi.setDescriptorValueTyped("ccmPrjProcDurDay", Integer.valueOf(durd + ""));
            proi.setDescriptorValueTyped("ccmPrjProcDurHour", durh );

            dbks.put("DurDay", durd + "");
            dbks.put("DurHour", durh + "");

            proi.commit();

            IInformationObject[] sprs = Utils.getSubProcessies(proi.getID(), helper);

            Integer scnt = 0;
            for(IInformationObject sinf : sprs){
                ITask stsk = (ITask) sinf;

                String snam = (stsk.getName() != null ? stsk.getName() : "");
                String scod = (stsk.getCode() != null ? stsk.getCode() : "");

                if(!snam.equals("Review Document")
                        && !scod.equals("review")){
                    continue;
                }

                scnt++;
                rvws.put("Step02_" + (scnt <= 9 ? "0" : "") + scnt, stsk);
            }

            List<String> wtrs = Conf.Bookmarks.Reviews();
            JSONObject ebks = Conf.Bookmarks.EngDocument();

            for (String ekey : ebks.keySet()) {
                String efld = ebks.getString(ekey);
                if (efld.isEmpty()) {
                    continue;
                }
                String eval = "";

                System.out.println(" [" + ekey + "]  : " + efld);

                if (efld.equals("@FILE_NAME@")) {
                    eval = Utils.nameDocument(mainDoc);
                }
                log.info("Eval1:" + eval);
                if (eval != null && eval.isEmpty() && Utils.hasDescriptor((IInformationObject) proi, efld)) {
                    eval = proi.getDescriptorValue(efld, String.class);
                }
                log.info("Eval2:" + eval);
                if (eval != null && eval.isEmpty() && Utils.hasDescriptor((IInformationObject) mainDoc, efld)) {
                    eval = mainDoc.getDescriptorValue(efld, String.class);
                }
                log.info("Eval3:" + eval);
                if (eval != null){
                    dbks.put(ekey, eval);
                }
            }
            log.info("Eval is Added finished...");
            JSONObject rsts = Utils.getMainDocReviewStatuses(ses, srv, prjn);
            JSONObject ists = Utils.getIssueStatuses(ses, srv, prjn);

            dbks.put("AprvCode", "");
            if(Utils.hasDescriptor((IInformationObject) mainDoc, Conf.Descriptors.AprvCode)){
                dbks.put("AprvCode", mainDoc.getDescriptorValue(Conf.Descriptors.AprvCode, String.class));
            }

            if(dbks.has("AprvCode")
            && rsts.has(dbks.getString("AprvCode"))){
                dbks.put("AprvDesc", rsts.getString(dbks.getString("AprvCode")));
            }
            if(dbks.has("IssueStatus")
            && ists.has(dbks.getString("IssueStatus"))){
                dbks.put("IssueStatus", dbks.getString("IssueStatus")
                + "-" + ists.getString(dbks.getString("IssueStatus")));
            }

            List<String> mails = new ArrayList<>();
            List<String> wrhLines = new ArrayList<>();
            for(String wtro : wtrs){
                if(!rvws.has(wtro)){continue;}
                ITask ctsk = (ITask) rvws.get(wtro);
                IUser cusr = ctsk.getFinishedBy();
                IDecision cdec = ctsk.getDecision();
                Date ddte = ctsk.getFinishedDate();
                String sdte = (ddte == null ? "" : (new SimpleDateFormat("dd/MM/yyyy HH:mm")).format(ddte));
                String fnam = (cusr != null ? cusr.getFullName() : "");
                String tcod = (cdec != null ? cdec.getCode() : "");
                String dcod = (rsts.has(tcod) ? tcod : "");
                String dtxt = (rsts.has(tcod) ? rsts.getString(tcod) : "");
                String cmnt = (Utils.hasDescriptor((IInformationObject) ctsk, "Notes") ? ctsk.getDescriptorValue("Notes", String.class) : "");

                String umail = (cusr == null ? "" : cusr.getEMailAddress());
                if(umail != null && !umail.isEmpty() && !mails.contains(umail)){
                    mails.add(umail);
                }

                dbks.put(wtro + "_Name", ctsk.getName() != null ? ctsk.getName() : "");
                dbks.put(wtro + "_User", fnam);
                dbks.put(wtro + "_AprvDate", sdte);
                dbks.put(wtro + "_AprvText", dcod + (dcod != "" && dtxt != "" ? "-" : "") + dtxt);
                dbks.put(wtro + "_Comments", cmnt);

                if(!wrhLines.contains("Header01")){wrhLines.add("Header01");}
                if(!wrhLines.contains("Header02")){wrhLines.add("Header02");}
                if(!wrhLines.contains(wtro)){wrhLines.add(wtro);}
            }

            //dbks.put("DoxisLink", Conf.DocReview.WebBase + helper.getTaskURL(proi.getID()));
            String mtpn = "DOC_REVIEW_MAIL";
            IDocument mtpl = Utils.getTemplateDocument(prjn, mtpn, helper);
            if(mtpl == null){
                throw new Exception("Template-Document [ " + mtpn + " ] not found.");
            }
            String tplMailPath = Utils.exportDocument(mtpl, Conf.DocReviewPaths.MainPath, mtpn + "[" + uniqueId + "]");
            String mailExcelPath = Utils.saveDocReviewExcel(tplMailPath, Conf.DocReviewSheetIndex.Mail,
                Conf.DocReviewPaths.MainPath + "/" + mtpn + "[" + uniqueId + "].xlsx", dbks
            );

            Utils.removeRows(mailExcelPath, mailExcelPath,
                Conf.DocReviewSheetIndex.Mail,
                Conf.DocReviewRowGroups.WRevHs,
                Conf.DocReviewRowGroups.WRevHColInx,
                Conf.DocReviewRowGroups.WRevHHideCols,
                wrhLines
            );

            String mailHtmlPath = Utils.convertExcelToHtml(mailExcelPath, Conf.DocReviewPaths.MainPath + "/" + mtpn + "[" + uniqueId + "].html");
            String mailPdfPath = Utils.convertExcelToPdf(mailExcelPath, Conf.DocReviewPaths.MainPath + "/" + mtpn + "[" + uniqueId + "].pdf");

            IDocument rvwDoc = Utils.createReviewHistoryAttachment(ses, srv, mainDoc);

            IRepresentation htmt = rvwDoc.addRepresentation(".pdf", "Review History");
            htmt.addPartDocument(mailPdfPath);

            rvwDoc.commit();

            IInformationObjectLinks links = proi.getLoadedInformationObjectLinks();
            links.addInformationObject(rvwDoc.getID());


            ILink lnk1 = srv.createLink(ses, mainDoc.getID(), null, rvwDoc.getID());
            lnk1.commit();


            IDocument cdoc = (IDocument) Utils.getEngineeringCRS(mainDoc.getID(), helper);
            if(cdoc != null){
                this.convertToPDF(cdoc);
                ILink lnk2 = srv.createLink(ses, mainDoc.getID(), null, cdoc.getID());
                lnk2.commit();
            }

            mainDoc.commit();
            proi.commit();

            if(mails.size() > 0) {
                JSONObject mail = new JSONObject();

                mail.put("To", String.join(";", mails));
                mail.put("Subject", "DocReview > " + dbks.getString("DocNo") + " / " + dbks.getString("RevNo"));
                mail.put("BodyHTMLFile", mailHtmlPath);

                try {
                    Utils.sendHTMLMail(ses, srv, mtpn, mail);
                } catch (Exception ex){
                    System.out.println("EXCP [Send-Mail] : " + ex.getMessage());
                }
            }

            System.out.println("Tested.");

        } catch (Exception e) {
            //throw new RuntimeException(e);
            System.out.println("Exception       : " + e.getMessage());
            System.out.println("    Class       : " + e.getClass());
            System.out.println("    Stack-Trace : " + e.getStackTrace() );

            log.error ("Exception       : " + e.getMessage());
            log.error ("    Class       : " + e.getClass());
            log.error ("    Stack-Trace : " + e.getStackTrace().toString() );

           return resultError("Exception : " + e.getMessage());
        }

        System.out.println("Finished");
        return resultSuccess("Ended successfully");
    }

    private void convertToPDF(IDocument doc) throws IOException {
        String excelPath = Utils.exportDocument(doc, Conf.DocReviewPaths.MainPath, "CRS" + "[" + uniqueId + "]");
        String filePathPDF = "";
        if (excelPath != "") {
            log.info("Excel File Path For :" + uniqueId + " is " + excelPath);
            if(excelPath.contains(".pdf")){
                filePathPDF = excelPath;
            }else {
                filePathPDF = Utils.convertExcelToPdf(excelPath, Conf.DocReviewPaths.MainPath + "/" + "CRS" + "[" + uniqueId + "].pdf");
            }
            doc.addRepresentation(".pdf", "PDF View").addPartDocument(filePathPDF);
            doc.setDefaultRepresentation(doc.getRepresentationCount() - 1);
            doc.commit();
        } else {
            log.error("Excel File Path For :" + uniqueId + " is EMPTY");
        }
    }
}