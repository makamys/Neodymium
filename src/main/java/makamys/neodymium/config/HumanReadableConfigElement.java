package makamys.neodymium.config;

import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Property;

public class HumanReadableConfigElement extends ConfigElement {

    public HumanReadableConfigElement(Property prop) {
        super(prop);
    }
    
    @Override
    public String getName() {
        return decamelize(super.getName());
    }
    
    private static String decamelize(String s) {
        boolean[] spaceField = new boolean[s.length()];
        
        for(int i = 0; i < s.length(); i++) {
            if(i > 0 && i < s.length() && !Character.isUpperCase(s.charAt(i - 1)) && Character.isUpperCase(s.charAt(i))) {
                spaceField[i] = true;
            } else if(i > 2 && Character.isUpperCase(s.charAt(i - 2)) && Character.isUpperCase(s.charAt(i - 1)) && !Character.isUpperCase(s.charAt(i))) {
                spaceField[i - 1] = true;
            }
        }
        
        String out = "";
        for(int i = 0; i < s.length(); i++) {
            if(spaceField[i]) {
                out += " ";
            }
            out += i == 0 ? Character.toUpperCase(s.charAt(i)) : s.charAt(i);
        }
        
        return out;
    }

}
