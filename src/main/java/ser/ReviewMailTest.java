package ser;

import com.ser.blueline.*;
import com.ser.blueline.bpm.*;
import de.ser.doxis4.agentserver.UnifiedAgent;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ReviewMailTest extends UnifiedAgent {
    Logger log = LogManager.getLogger();
    String uniqueId;
    private ProcessHelper processHelper;
    @Override
    protected Object execute() {
        if (getEventTask() == null)
            return resultError("Null Document object");

        Utils.session = getSes();
        Utils.bpm = getBpm();
        Utils.server = Utils.session.getDocumentServer();
        Utils.loadDirectory(Conf.Paths.MainPath);

        try {
            processHelper = new ProcessHelper(Utils.session);
            JSONObject scfg = Utils.getSystemConfig();
            if(scfg.has("LICS.SPIRE_XLS")){
                com.spire.license.LicenseProvider.setLicenseKey(scfg.getString("LICS.SPIRE_XLS"));
            }

            ITask task = getEventTask();
            String taskCode = task.getCode();
            taskCode = (taskCode == null ? "" : taskCode);

            //IValueDescriptor[] iDesc = task.getInternalDescriptorList();

            IProcessInstance proi = task.getProcessInstance();

            //IValueDescriptor[] internalDesc = proi.getInternalDescriptorList();

            String prjn = proi.getDescriptorValue(Conf.Descriptors.ProjectNo, String.class);
            if(prjn.isEmpty()){
                throw new Exception("Project no is empty.");
            }
            String mdid = proi.getDescriptorValue(Conf.Descriptors.MainDocReference, String.class);
            if(mdid.isEmpty()){
                throw new Exception("Main Doc Ref is empty.");
            }

            IInformationObject prjt = Utils.getProjectWorkspace(prjn, processHelper);
            if(prjt == null){
                throw new Exception("Project not found [" + prjn + "].");
            }
            IDocument mainDoc = Utils.server.getDocument4ID(mdid , Utils.session);
            if(mainDoc == null){
                throw new Exception("Main Document not found [" + mdid + "].");
            }

            ILink[] slns = Utils.server.getReferencedRelationships(Utils.session, mainDoc, true);
            for(ILink slnk : slns){
                IInformationObject stgt = slnk.getTargetInformationObject();

                if(stgt.getClassID().equals(Conf.ClassIDs.EngineeringAttachments)
                        && Utils.hasDescriptor((IInformationObject) stgt, Conf.Descriptors.DocType)
                        && stgt.getDescriptorValue(Conf.Descriptors.DocType, String.class).equals("Review-History")){
                    Utils.server.removeRelationship(Utils.session, slnk);
                }
                if(stgt.getClassID().equals(Conf.ClassIDs.EngineeringAttachments)
                        && Utils.hasDescriptor((IInformationObject) stgt, Conf.Descriptors.DocType)
                        && stgt.getDescriptorValue(Conf.Descriptors.DocType, String.class).equals("CRS")){
                    Utils.server.removeRelationship(Utils.session, slnk);
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

                log.info("TASK-Name[" + tcnt + "]:" + tnam);
                log.info("TASK-Code[" + tcnt + "]:" + tcod);

                if(tnam.equals("Start Task")
                        || tcod.equals("Step01")){
                    rvws.put("Step01", ttsk);
                    continue;
                }
                if(ttsk.getLoadedParentTask() != null
                        && (tnam.equals("Consolidator Review") || tcod.equals("Step03"))){

                    IWorkbasket cwbk = ttsk.getCurrentWorkbasket();
                    String wbnm = (cwbk != null ? cwbk.getFullName() : "");
                    if(wbnm != null && !wbnm.equals("System")) {
                        ccnt++;
                        rvws.put("Step03_" + (ccnt <= 9 ? "0" : "") + ccnt, ttsk);
                        continue;
                    }
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

            IInformationObject[] sprs = Utils.getSubProcessies(proi.getID(), processHelper);

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
                if(efld.isEmpty()){continue;}
                String eval = "";

                log.info(" [" + ekey + "]  : " + efld);

                if(efld.equals("@FILE_NAME@")){
                    eval = Utils.nameDocument(mainDoc);
                }
                if(eval.isEmpty() && Utils.hasDescriptor((IInformationObject) proi, efld)){
                    eval = proi.getDescriptorValue(efld, String.class);
                }
                if(eval.isEmpty() && Utils.hasDescriptor((IInformationObject) mainDoc, efld)){
                    eval = mainDoc.getDescriptorValue(efld, String.class);
                }
                dbks.put(ekey, eval);
            }

            JSONObject rsts = Utils.getMainDocReviewStatuses(prjn);
            JSONObject ists = Utils.getIssueStatuses(prjn);

            dbks.put("AprvCode", "");
            if(!taskCode.equals("NoMail") && Utils.hasDescriptor((IInformationObject) mainDoc, Conf.Descriptors.AprvCode)){
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
                IWorkbasket cwbk = ctsk.getCurrentWorkbasket();
                IUser cusr = ctsk.getFinishedBy();
                IDecision cdec = ctsk.getDecision();
                Date ddte = ctsk.getFinishedDate();
                String sdte = (ddte == null ? "" : (new SimpleDateFormat("dd/MM/yyyy HH:mm")).format(ddte));
                String fnam = (cusr != null ? cusr.getFullName() : "");
                String wbnm = (cwbk != null ? cwbk.getFullName() : "");

                String anam = fnam;

                String tcod = (cdec != null ? cdec.getCode() : "");
                String dcod = (rsts.has(tcod) ? tcod : "");
                String dtxt = (rsts.has(tcod) ? rsts.getString(tcod) : "");
                String cmnt = (Utils.hasDescriptor((IInformationObject) ctsk, "Notes") ? ctsk.getDescriptorValue("Notes", String.class) : "");

                String umail = (cusr == null ? "" : cusr.getEMailAddress());
                if(umail != null && !umail.isEmpty() && !mails.contains(umail)){
                    mails.add(umail);
                }

                dbks.put(wtro + "_Name", ctsk.getName() != null ? ctsk.getName() : "");
                dbks.put(wtro + "_User", wbnm);
                dbks.put(wtro + "_Cmpl", fnam);
                dbks.put(wtro + "_AprvDate", sdte);
                dbks.put(wtro + "_AprvText", dcod + (dcod != "" && dtxt != "" ? "-" : "") + dtxt);
                dbks.put(wtro + "_Comments", cmnt);

                if(!wrhLines.contains("Header01")){wrhLines.add("Header01");}
                if(!wrhLines.contains("Header02")){wrhLines.add("Header02");}
                if(!wrhLines.contains(wtro)){wrhLines.add(wtro);}
            }

            //dbks.put("DoxisLink", Conf.DocReview.WebBase + helper.getTaskURL(proi.getID()));
            String mtpn = "DOC_REVIEW_MAIL";
            IDocument mtpl = Utils.getTemplateDocument(prjt, mtpn);
            if(mtpl == null){
                throw new Exception("Template-Document [ " + mtpn + " ] not found.");
            }
            String tplMailPath = Utils.exportDocument(mtpl, Conf.Paths.MainPath, mtpn + "[" + uniqueId + "]");
            String mailExcelPath = Utils.saveDocReviewExcel(tplMailPath, Conf.DocReviewSheetIndex.Mail,
                    Conf.Paths.MainPath + "/" + mtpn + "[" + uniqueId + "].xlsx", dbks
            );

            Utils.removeRows(mailExcelPath, mailExcelPath,
                    Conf.DocReviewSheetIndex.Mail,
                    Conf.DocReviewRowGroups.WRevHs,
                    Conf.DocReviewRowGroups.WRevHColInx,
                    Conf.DocReviewRowGroups.WRevHHideCols,
                    wrhLines
            );

            String mailHtmlPath = Utils.convertExcelToHtml(mailExcelPath, Conf.Paths.MainPath + "/" + mtpn + "[" + uniqueId + "].html");
            String mailPdfPath = Utils.convertExcelToPdf(mailExcelPath, Conf.Paths.MainPath + "/" + mtpn + "[" + uniqueId + "].pdf");

            String docType = (!taskCode.equals("NoMail") ? "Review-History" : "History");
            Utils.deleteSubAttachments( mainDoc.getID(), "History", processHelper);
            IDocument rvwDoc = Utils.createSubAttachment(mainDoc, docType);

            IRepresentation htmt = rvwDoc.addRepresentation(".pdf", docType);
            htmt.addPartDocument(mailPdfPath);

            rvwDoc.commit();

            IInformationObjectLinks links = proi.getLoadedInformationObjectLinks();
            links.addInformationObject(rvwDoc.getID());


            ILink lnk1 = Utils.server.createLink(Utils.session, mainDoc.getID(), null, rvwDoc.getID());
            lnk1.commit();


            IDocument cdoc = (IDocument) Utils.getEngineeringCRS(mainDoc.getID(), processHelper);
            if(cdoc != null && !taskCode.equals("NoMail")){
                this.convertToPDF(cdoc);
                ILink lnk2 = Utils.server.createLink(Utils.session, mainDoc.getID(), null, cdoc.getID());
                lnk2.commit();
            }

            mainDoc.commit();
            proi.commit();

            if(mails.size() > 0 && !taskCode.equals("NoMail")) {
                JSONObject mail = new JSONObject();

                mail.put("To", String.join(";", mails));
                mail.put("Subject", "Review History For " + dbks.getString("DocNo") + " / " + dbks.getString("RevNo"));
                mail.put("BodyHTMLFile", mailHtmlPath);

                try {
                    Utils.sendHTMLMail(mail);
                }catch(Exception ex){
                    log.error("EXCP [Send-Mail] : " + ex.getMessage());
                }
            }

            log.info("Tested.");

        } catch (Exception e) {
            //throw new RuntimeException(e);

            log.error ("Exception       : " + e.getMessage());
            log.error ("    Class       : " + e.getClass());
            log.error ("    Stack-Trace : " + e.getStackTrace().toString() );

            return resultError("Exception : " + e.getMessage());
        }

        log.info("Finished");
        return resultSuccess("Ended successfully");
    }

    private void convertToPDF(IDocument doc) throws IOException {
        String excelPath = Utils.exportDocument(doc , Conf.Paths.MainPath , "CRS" + "[" + uniqueId + "]");

        if(excelPath.contains(".xlsx")) {
            log.info("Excel File Path For :" + uniqueId + " is " + excelPath);
            String filePathPDF = Utils.convertExcelToPdf(excelPath, Conf.Paths.MainPath + "/" + "CRS" + "[" + uniqueId + "].pdf");

            doc.addRepresentation(".pdf", "PDF View").addPartDocument(filePathPDF);
            doc.setDefaultRepresentation(doc.getRepresentationCount()-1);
            doc.commit();

        }else
            log.error("Excel File Path For :" + uniqueId + " is EMPTY" );
    }
}