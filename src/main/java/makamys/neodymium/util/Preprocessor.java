package makamys.neodymium.util;

import java.util.Map;
import java.util.Set;

public class Preprocessor {
    
    public static String preprocess(String text, Map<String, String> defines) {
        String[] lines = text.replaceAll("\\r\\n", "\n").split("\\n");
        
        IfElseBlockStatus ifElseBlockStatus = IfElseBlockStatus.NONE;
        boolean ifElseConditionMet = false;
        
        for(int i = 0; i < lines.length; i++) {
            String line = lines[i];
            
            boolean commentLine = false;
            
            if(line.startsWith("#ifdef ")) {
                ifElseBlockStatus = IfElseBlockStatus.IF;
                ifElseConditionMet = defines.containsKey(line.split(" ")[1]);
                commentLine = true;
            } else if(line.startsWith("#else")) {
                ifElseBlockStatus = IfElseBlockStatus.ELSE;
                commentLine = true;
            } else if(line.startsWith("#endif")) {
                ifElseBlockStatus = IfElseBlockStatus.NONE;
                commentLine = true;
            } else {
                if(ifElseBlockStatus == IfElseBlockStatus.IF && !ifElseConditionMet) {
                    commentLine = true;
                }
                if(ifElseBlockStatus == IfElseBlockStatus.ELSE && ifElseConditionMet) {
                    commentLine = true;
                }
            }
            
            if(commentLine) {
                lines[i] = "//" + line;
            } else {
                for (Map.Entry<String, String> define: defines.entrySet()) {
                    line = line.replace(define.getKey(), define.getValue());
                }
                lines[i] = line;
            }
        }
        
        return String.join("\n", lines);
    }
    
    public static enum IfElseBlockStatus {
        NONE, IF, ELSE;
    }
    
}
