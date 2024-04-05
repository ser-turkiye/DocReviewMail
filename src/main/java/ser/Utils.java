package ser;

import com.ser.blueline.*;
import com.ser.blueline.bpm.*;
import com.ser.blueline.metaDataComponents.IArchiveClass;
import com.ser.blueline.metaDataComponents.IArchiveFolderClass;
import com.ser.blueline.metaDataComponents.IStringMatrix;
import com.ser.foldermanager.IElement;
import com.ser.foldermanager.IElements;
import com.ser.foldermanager.IFolder;
import com.ser.foldermanager.INode;
import com.spire.xls.FileFormat;
import com.spire.xls.Workbook;
import com.spire.xls.Worksheet;
import com.spire.xls.core.spreadsheet.HTMLOptions;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Utils {
    static Logger log = LogManager.getLogger();
    static ISession session = null;
    static IDocumentServer server = null;
    static IBpmService bpm;
    static void loadDirectory(String path) {
        (new File(path)).mkdir();
    }
    public static boolean hasDescriptor(IInformationObject object, String descName){
        IDescriptor[] descs = session.getDocumentServer().getDescriptorByName(descName, session);
        List<String> checkList = new ArrayList<>();
        for(IDescriptor ddsc : descs){
            checkList.add(ddsc.getId());
        }

        String[] descIds = new String[0];
        if(object instanceof IFolder){
            String classID = object.getClassID();
            IArchiveFolderClass folderClass = session.getDocumentServer().getArchiveFolderClass(classID , session);
            descIds = folderClass.getAssignedDescriptorIDs();
        }else if(object instanceof IDocument){
            IArchiveClass documentClass = ((IDocument) object).getArchiveClass();
            descIds = documentClass.getAssignedDescriptorIDs();
        }else if(object instanceof ITask){
            IProcessType processType = ((ITask) object).getProcessType();
            descIds = processType.getAssignedDescriptorIDs();
        }else if(object instanceof IProcessInstance){
            IProcessType processType = ((IProcessInstance) object).getProcessType();
            descIds = processType.getAssignedDescriptorIDs();
        }

        List<String> descList = Arrays.asList(descIds);
        for(String dId : descList){
            if(checkList.contains(dId)){return true;}
        }
        return false;
    }
    static JSONObject getSystemConfig() throws Exception {
        return getSystemConfig(null);
    }
    static JSONObject getSystemConfig(IStringMatrix mtrx) throws Exception {
        if(mtrx == null){
            mtrx = server.getStringMatrix("CCM_SYSTEM_CONFIG", session);
        }
        if(mtrx == null) throw new Exception("SystemConfig Global Value List not found");

        List<List<String>> rawTable = mtrx.getRawRows();

        String srvn = session.getSystem().getName().toUpperCase();
        JSONObject rtrn = new JSONObject();
        for(List<String> line : rawTable) {
            String name = line.get(0);
            if(!name.toUpperCase().startsWith(srvn + ".")){continue;}
            name = name.substring(srvn.length() + ".".length());
            rtrn.put(name, line.get(1));
        }
        return rtrn;
    }
    static IInformationObject getEngineeringCRS(String refn, ProcessHelper helper) {
        StringBuilder builder = new StringBuilder();
        builder.append("TYPE = '").append(Conf.ClassIDs.EngineeringAttachments).append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.DocType).append(" = '").append("CRS").append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.ReferenceNumber).append(" = '").append(refn).append("'");
        String whereClause = builder.toString();
        log.info("Where Clause: " + whereClause);

        IInformationObject[] informationObjects = helper.createQuery(new String[]{Conf.Databases.EngineeringAttachments}, whereClause , "",1, true);
        if(informationObjects.length < 1) {return null;}
        return informationObjects[0];
    }
    static JSONObject getMainDocReviewStatuses(String prjn) throws Exception {
        IStringMatrix mtrx = getMainDocReviewStatusMatrix();
        if(mtrx == null) throw new Exception("MainDoc Review Status Global Value List not found");
        List<List<String>> rawTable = mtrx.getRawRows();

        JSONObject rtrn = new JSONObject();
        for(List<String> line : rawTable) {
            //if(!line.get(0).equals(prjn)){continue;}
            if(rtrn.has(line.get(1))){continue;}
            rtrn.put(line.get(1), line.get(2));
        }
        return rtrn;
    }
    static JSONObject getIssueStatuses(String prjn) throws Exception {
        IStringMatrix mtrx = getIssueStatusMatrix();
        if(mtrx == null) throw new Exception("Issue Status Global Value List not found");
        List<List<String>> rawTable = mtrx.getRawRows();

        JSONObject rtrn = new JSONObject();
        for(List<String> line : rawTable) {
            if(!line.get(0).equals(prjn)){continue;}
            rtrn.put(line.get(1), line.get(2));
        }
        return rtrn;
    }
    static void sendHTMLMail(JSONObject pars) throws Exception {
        JSONObject mcfg = Utils.getMailConfig();

        String host = mcfg.getString("host");
        String port = mcfg.getString("port");
        String protocol = mcfg.getString("protocol");
        String sender = mcfg.getString("sender");
        String subject = "";
        String mailTo = "";
        String mailCC = "";
        String attachments = "";

        if(pars.has("From")){
            sender = pars.getString("From");
        }
        if(pars.has("To")){
            mailTo = pars.getString("To");
        }
        if(pars.has("CC")){
            mailCC = pars.getString("CC");
        }
        if(pars.has("Subject")){
            subject = pars.getString("Subject");
        }
        if(pars.has("AttachmentPaths")){
            attachments = pars.getString("AttachmentPaths");
        }


        Properties props = new Properties();
        props.put("mail.debug","true");
        props.put("mail.smtp.debug", "true");

        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);

        String start_tls = (mcfg.has("start_tls") ? mcfg.getString("start_tls") : "");
        if(start_tls.equals("true")) {
            props.put("mail.smtp.starttls.enable", start_tls);
        }

        String auth = mcfg.getString("auth");
        props.put("mail.smtp.auth", auth);
        jakarta.mail.Authenticator authenticator = null;
        if(!auth.equals("false")) {
            String auth_username = mcfg.getString("auth.username");
            String auth_password = mcfg.getString("auth.password");

            if (host.contains("gmail")) {
                props.put("mail.smtp.socketFactory.port", port);
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");
            }
            if (protocol != null && protocol.contains("TLSv1.2"))  {
                props.put("mail.smtp.ssl.protocols", protocol);
                props.put("mail.smtp.ssl.trust", "*");
                props.put("mail.smtp.socketFactory.port", port);
                props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
                props.put("mail.smtp.socketFactory.fallback", "false");
            }
            authenticator = new jakarta.mail.Authenticator(){
                @Override
                protected jakarta.mail.PasswordAuthentication getPasswordAuthentication(){
                    return new jakarta.mail.PasswordAuthentication(auth_username, auth_password);
                }
            };
        }
        props.put("mail.mime.charset","UTF-8");
        Session sess = (authenticator == null ? Session.getDefaultInstance(props) : Session.getDefaultInstance(props, authenticator));

        MimeMessage message = new MimeMessage(sess);
        message.setFrom(new InternetAddress(sender.replace(";", ",")));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(mailTo.replace(";", ",")));
        message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(mailCC.replace(";", ",")));
        message.setSubject(subject);

        Multipart multipart = new MimeMultipart("mixed");

        BodyPart htmlBodyPart = new MimeBodyPart();
        htmlBodyPart.setContent(getHTMLFileContent(pars.getString("BodyHTMLFile")) , "text/html"); //; charset=UTF-8
        multipart.addBodyPart(htmlBodyPart);

        String[] atchs = attachments.split("\\;");
        for (String atch : atchs){
            if(atch.isEmpty()){continue;}
            BodyPart attachmentBodyPart = new MimeBodyPart();
            attachmentBodyPart.setDataHandler(new DataHandler((DataSource) new FileDataSource(atch)));

            String fnam = Paths.get(atch).getFileName().toString();
            if(pars.has("AttachmentName." + fnam)){
                fnam = pars.getString("AttachmentName." + fnam);
            }

            attachmentBodyPart.setFileName(fnam);
            multipart.addBodyPart(attachmentBodyPart);

        }

        message.setContent(multipart);
        Transport.send(message);

    }
    static IStringMatrix getMailConfigMatrix() throws Exception {
        IStringMatrix rtrn = server.getStringMatrix("CCM_MAIL_CONFIG", session);
        if (rtrn == null) throw new Exception("MailConfig Global Value List not found");
        return rtrn;
    }
    static String getHTMLFileContent (String path) throws Exception {
        String rtrn = new String(Files.readAllBytes(Paths.get(path)));
        rtrn = rtrn.replace("\uFEFF", "");
        rtrn = rtrn.replace("ï»¿", "");
        return rtrn;
    }
    static JSONObject getMailConfig() throws Exception {
        return getMailConfig(null);
    }
    static JSONObject getMailConfig(IStringMatrix mtrx) throws Exception {
        if(mtrx == null){
            mtrx = getMailConfigMatrix();
        }
        if(mtrx == null) throw new Exception("MailConfig Global Value List not found");
        List<List<String>> rawTable = mtrx.getRawRows();

        JSONObject rtrn = new JSONObject();
        for(List<String> line : rawTable) {
            rtrn.put(line.get(0), line.get(1));
        }
        return rtrn;
    }
    static IStringMatrix getMainDocReviewStatusMatrix() throws Exception {
        IStringMatrix rtrn = server.getStringMatrix("MainDocReviewStatus", session);
        if (rtrn == null) throw new Exception("MainDocReviewStatus Global Value List not found");
        return rtrn;
    }
    static IStringMatrix getIssueStatusMatrix() throws Exception {
        IStringMatrix rtrn = server.getStringMatrix("CCM_QCON_ISSUE-STATUSES", session);
        if (rtrn == null) throw new Exception("IssueStatus Global Value List not found");
        return rtrn;
    }
    static IStringMatrix getWorkbasketMatrix(ISession ses, IDocumentServer srv) throws Exception {
        IStringMatrix rtrn = srv.getStringMatrixByID("Workbaskets", ses);
        if (rtrn == null) throw new Exception("Workbaskets Global Value List not found");
        return rtrn;
    }
    static IDocument getTemplateDocument(IInformationObject info, String tpltName) throws Exception {
        List<INode> nods = ((IFolder) info).getNodesByName("Templates");
        IDocument rtrn = null;
        for(INode node : nods){
            IElements elms = node.getElements();

            for(int i=0;i<elms.getCount2();i++) {
                IElement nelement = elms.getItem2(i);
                String edocID = nelement.getLink();
                IInformationObject tplt = info.getSession().getDocumentServer().getInformationObjectByID(edocID, info.getSession());
                if(tplt == null){continue;}

                if(!hasDescriptor(tplt, Conf.Descriptors.TemplateName)){continue;}

                String etpn = tplt.getDescriptorValue(Conf.Descriptors.TemplateName, String.class);
                if(etpn == null || !etpn.equals(tpltName)){continue;}

                rtrn = (IDocument) tplt;
                break;
            }
            if(rtrn != null){break;}
        }
        if(rtrn != null && server != null && session != null) {
            rtrn = server.getDocumentCurrentVersion(session, rtrn.getID());
        }
        return rtrn;
    }
    static IInformationObject getProjectWorkspace(String prjn, ProcessHelper helper) {
        StringBuilder builder = new StringBuilder();
        builder.append("TYPE = '").append(Conf.ClassIDs.ProjectWorkspace).append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.PrjCardCode).append(" = '").append(prjn).append("'");
        String whereClause = builder.toString();
        log.info("Where Clause: " + whereClause);

        IInformationObject[] informationObjects = helper.createQuery(new String[]{Conf.Databases.ProjectWorkspace} , whereClause , "", 1, false);
        if(informationObjects.length < 1) {return null;}
        return informationObjects[0];
    }
    public static void removeRows(String spth, String tpth, Integer shtIx, String prfx, Integer colIx, List<Integer> hlst, List<String> tlst) throws IOException {

        FileInputStream tist = new FileInputStream(spth);
        XSSFWorkbook twrb = new XSSFWorkbook(tist);

        Sheet tsht = twrb.getSheetAt(shtIx);
        JSONObject rows = Utils.getRowGroups(tsht, prfx, colIx);

        for (String pkey : rows.keySet()) {
            Row crow = (Row) rows.get(pkey);
            crow.getCell(colIx).setBlank();

            if(tlst.contains(pkey)){
                continue;
            }

            crow.setZeroHeight(true);
            //deleteRow(tsht, crow.getRowNum());
        }

        for(Integer hcix : hlst){
            tsht.setColumnHidden(hcix, true);
        }

        FileOutputStream tost = new FileOutputStream(tpth);
        twrb.write(tost);
        tost.close();

    }
    public static String saveDocReviewExcel(String templatePath, Integer shtIx, String tpltSavePath, JSONObject pbks) throws IOException {

        FileInputStream tist = new FileInputStream(templatePath);
        XSSFWorkbook twrb = new XSSFWorkbook(tist);

        Sheet tsht = twrb.getSheetAt(shtIx);
        for (Row trow : tsht){
            for(Cell tcll : trow){
                if(tcll.getCellType() != CellType.STRING){continue;}
                String clvl = tcll.getRichStringCellValue().getString();
                String clvv = updateCell(clvl, pbks);
                if(!clvv.equals(clvl)){
                    tcll.setCellValue(clvv);
                }

                if(clvv.indexOf("[[") != (-1) && clvv.indexOf("]]") != (-1)
                        && clvv.indexOf("[[") < clvv.indexOf("]]")){
                    String znam = clvv.substring(clvv.indexOf("[[") + "[[".length(), clvv.indexOf("]]"));
                    if(pbks.has(znam)){
                        tcll.setCellValue(znam);
                        String lurl = pbks.getString(znam);
                        if(!lurl.isEmpty()) {
                            Hyperlink link = twrb.getCreationHelper().createHyperlink(HyperlinkType.URL);
                            link.setAddress(lurl);
                            tcll.setHyperlink(link);
                        }
                    }
                }
            }
        }
        FileOutputStream tost = new FileOutputStream(tpltSavePath);
        twrb.write(tost);
        tost.close();
        return tpltSavePath;
    }
    public static String convertExcelToPdf(String excelPath, String pdfPath)  {
        Workbook workbook = new Workbook();
        workbook.loadFromFile(excelPath);
        workbook.getConverterSetting().setSheetFitToPage(true);
        workbook.saveToFile(pdfPath, FileFormat.PDF);

        return pdfPath;
    }
    public static String convertExcelToHtml(String excelPath, String htmlPath)  {
        Workbook workbook = new Workbook();
        workbook.loadFromFile(excelPath);
        Worksheet sheet = workbook.getWorksheets().get(0);
        HTMLOptions options = new HTMLOptions();
        options.setImageEmbedded(true);
        sheet.saveToHtml(htmlPath, options);
        return htmlPath;
    }
    static void deleteSubAttachments(String mainDocId, String docType, ProcessHelper helper)  {
        IArchiveClass ac = server.getArchiveClass(Conf.ClassIDs.EngineeringAttachments, session);
        IDatabase db = session.getDatabase(ac.getDefaultDatabaseID());

        StringBuilder builder = new StringBuilder();
        builder.append("TYPE = '").append(Conf.ClassIDs.EngineeringAttachments).append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.MainDocReference).append(" = '").append(mainDocId).append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.DocType).append(" = '").append(docType).append("'");
        String whereClause = builder.toString();
        log.info("Where Clause: " + whereClause);

        IInformationObject[] subs = helper.createQuery(new String[]{db.getDatabaseName()} , whereClause, "",0, false);
        for(IInformationObject ssub : subs){
            server.deleteInformationObject(session, ssub);
        }
    }
    static IDocument createSubAttachment(IDocument mainDoc, String dType) throws Exception {

        IArchiveClass ac = server.getArchiveClass(Conf.ClassIDs.EngineeringAttachments, session);
        IDatabase db = session.getDatabase(ac.getDefaultDatabaseID());

        IDocument rtrn = server.getClassFactory().getDocumentInstance(db.getDatabaseName(), ac.getID(), "0000" , session);
        rtrn.commit();

        rtrn.setDescriptorValue(Conf.Descriptors.DocType, dType);
        rtrn.setDescriptorValue(Conf.Descriptors.MainDocReference, mainDoc.getID());

        rtrn.setDescriptorValue(Conf.Descriptors.ProjectNo,
                mainDoc.getDescriptorValue(Conf.Descriptors.ProjectNo));
        rtrn.setDescriptorValue(Conf.Descriptors.ProjectName,
                mainDoc.getDescriptorValue(Conf.Descriptors.ProjectName));

        rtrn.setDescriptorValue(Conf.Descriptors.DocNumber,
                mainDoc.getDescriptorValue(Conf.Descriptors.DocNumber));
        rtrn.setDescriptorValue(Conf.Descriptors.Revision,
                mainDoc.getDescriptorValue(Conf.Descriptors.Revision));

        String atnr = (new CounterHelper(session, rtrn.getClassID())).getCounterStr();

        rtrn.setDescriptorValue(Conf.Descriptors.ObjectNumber,
                "RVH-" + atnr);

        rtrn.commit();

        return rtrn;
    }
    public static boolean hasDescriptor_old01(IInformationObject infObj, String dscn) throws Exception {
        IValueDescriptor[] vds = infObj.getDescriptorList();
        for(IValueDescriptor vd : vds){
            if(vd.getName().equals(dscn)){return true;}
        }
        return false;
    }
    public static String nameDocument(IDocument document) throws Exception {
        IDocumentPart partDocument = document.getPartDocument(document.getDefaultRepresentation() , 0);
        return partDocument.getFilename();
    }

    public static String exportDocument(IDocument document, String exportPath, String fileName) throws IOException {
        String rtrn ="";
        IDocumentPart partDocument = document.getPartDocument(document.getDefaultRepresentation() , 0);
        String fName = (!fileName.isEmpty() ? fileName : partDocument.getFilename());
        fName = fName.replaceAll("[\\\\/:*?\"<>|]", "_");
        try (InputStream inputStream = partDocument.getRawDataAsStream()) {
            IFDE fde = partDocument.getFDE();
            if (fde.getFDEType() == IFDE.FILE) {
                rtrn = exportPath + "/" + fName + "." + ((IFileFDE) fde).getShortFormatDescription();

                try (FileOutputStream fileOutputStream = new FileOutputStream(rtrn)){
                    byte[] bytes = new byte[2048];
                    int length;
                    while ((length = inputStream.read(bytes)) > -1) {
                        fileOutputStream.write(bytes, 0, length);
                    }
                }
            }
        }
        return rtrn;
    }
    public static String updateCell(String str, JSONObject bookmarks){
        StringBuffer rtr1 = new StringBuffer();
        String tmp = str + "";
        Pattern ptr1 = Pattern.compile( "\\{([\\w\\.]+)\\}" );
        Matcher mtc1 = ptr1.matcher(tmp);
        while(mtc1.find()) {
            String mk = mtc1.group(1);
            String mv = "";
            if(bookmarks.has(mk)){
                mv = bookmarks.getString(mk);
            }
            mtc1.appendReplacement(rtr1,  mv);
        }
        mtc1.appendTail(rtr1);
        tmp = rtr1.toString();

        return tmp;
    }
    static IInformationObject[] getSubProcessies(String mainDocId, ProcessHelper helper)  {
        StringBuilder builder = new StringBuilder();
        builder.append("TYPE = '").append(Conf.ClassIDs.SubProcess).append("'")
                .append(" AND ")
                .append(Conf.DescriptorLiterals.MainTaskReference).append(" = '").append(mainDocId).append("'");
        String whereClause = builder.toString();
        log.info("Where Clause: " + whereClause);

        return helper.createQuery(new String[]{Conf.Databases.BPM}, whereClause, "ModificationDate", 0, false);
    }
    public static JSONObject getRowGroups(Sheet sheet, String prfx, Integer colIx)  {
        JSONObject rtrn = new JSONObject();
        for (Row row : sheet) {
            Cell cll1 = row.getCell(colIx);
            if(cll1 == null){continue;}

            String cval = cll1.getRichStringCellValue().getString();
            if(cval.isEmpty()){continue;}

            if(!cval.startsWith("[&" + prfx + ".")
                    || !cval.endsWith("&]")){continue;}

            String znam = cval.substring(("[&" + prfx + ".").length(), cval.length() - ("]&").length());
            rtrn.put(znam, row);

        }
        return rtrn;
    }

}
