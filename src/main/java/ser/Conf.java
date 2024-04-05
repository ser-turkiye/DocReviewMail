package ser;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Conf {
    public static class Paths {
        public static final String MainPath = "C:/tmp2/bulk/docreview";
    }

    public static class DocReviewSheetIndex {
        public static final Integer Mail = 0;
    }
    public static class DocReviewRowGroups {
        public static final List<Integer> WRevHHideCols = List.of(0);
        public static final String WRevHs = "WRevHRows";
        public static final Integer WRevHColInx = 0;
    }
    public static class ClassIDs{
        public static final String EngineeringAttachments = "3e1fe7b3-3e86-4910-8155-c29b662e71d6";
        public static final String Template = "b9cf43d1-a4d3-482f-9806-44ae64c6139d";
        public static final String SubProcess = "629a28c4-6c36-44d0-90f7-1e5802f038e8";
        public static final String ProjectWorkspace = "32e74338-d268-484d-99b0-f90187240549";
        public static final String EngineeringDocument = "32e74338-d268-484d-99b0-f90187240549";

    }

    public static class Descriptors{
        public static final String ProjectNo = "ccmPRJCard_code";
        public static final String ProjectName = "ccmPRJCard_name";
        public static final String MainDocReference = "MainDocumentReference";
        public static final String DocType = "ccmPrjDocDocType";
        public static final String MainTaskRef = "MainTaskReference";
        public static final String AprvCode = "ccmPrjDocApprCode";
        public static final String DocNumber = "ccmPrjDocNumber";
        public static final String Revision = "ccmPrjDocRevision";
        public static final String ObjectNumber = "ObjectNumber";
        public static final String TemplateName = "ObjectNumberExternal";

    }
    public static class DescriptorLiterals{
        public static final String PrjCardCode = "CCMPRJCARD_CODE";
        public static final String MainTaskReference = "MAINTASKREFERENCE";
        public static final String MainDocReference = "MAINDOCUMENTREFERENCE";
        public static final String ObjectNumberExternal = "OBJECTNUMBER2";
        public static final String DocType = "CCMPRJDOCDOCTYPE";
        public static final String ReferenceNumber = "CCMREFERENCENUMBER";

    }
    public static class Databases{
        public static final String BPM = "BPM";
        public static final String Company = "D_QCON";
        public static final String ProjectWorkspace = "PRJ_FOLDER";
        public static final String EngineeringAttachments = "PRJ_CRS";

    }
    public static class Bookmarks{


        public  static final JSONObject EngDocument() {
            JSONObject rtrn = new JSONObject();
            rtrn.put("ProjectNo", "ccmPRJCard_code");
            rtrn.put("ProjectName", "ccmPRJCard_name");
            rtrn.put("DocNo", "ccmPrjDocNumber");
            rtrn.put("RevNo", "ccmPrjDocRevision");
            rtrn.put("Filename", "@FILE_NAME@");
            rtrn.put("DocTitle", "ObjectName");
            rtrn.put("Discipline", "ccmPrjDocDiscipline");
            rtrn.put("DocumentType", "ccmPrjDocDocType");
            rtrn.put("IssueStatus", "ccmPrjDocIssueStatus");
            rtrn.put("AprvCode", "");
            rtrn.put("AprvDesc", "");

            return rtrn;
        }

        public  static final List<String> Reviews() {

            List<String> rtrn = new ArrayList<>();
            rtrn.add("Step01");
            for(int s2=1;s2<=20;s2++){rtrn.add("Step02_" + (s2<=9?"0":"") + s2);}
            for(int s3=1;s3<= 5;s3++){rtrn.add("Step03_" + (s3<=9?"0":"") + s3);}
            rtrn.add("Step04");

            return rtrn;
        }
    }


}
