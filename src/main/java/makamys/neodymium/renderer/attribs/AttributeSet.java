package makamys.neodymium.renderer.attribs;

import org.lwjgl.opengl.GL20;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AttributeSet {
    private final List<VertexAttribute> attributes = new ArrayList<>();
    private int stride = 0;

    public void addAttribute(String name, int size, int elementSize, int type) {
        int index = attributes.size();
        attributes.add(new VertexAttribute(name, index, size, elementSize, type));
        stride += elementSize * size;
    }

    public int stride() {
        return stride;
    }

    public void enable() {
        int offset = 0;
        for (int i = 0, size = attributes.size(); i < size; i++) {
            VertexAttribute attribute = attributes.get(i);
            GL20.glVertexAttribPointer(i, attribute.size, attribute.type, false, stride, offset);
            offset += attribute.size * attribute.elementSize;
            GL20.glEnableVertexAttribArray(i);
        }
    }

    public void addDefines(Map<String, String> defines) {
        for (VertexAttribute attribute: attributes) {
            defines.put("ATTRIB_" + attribute.name, Integer.toString(attribute.index));
        }
    }

    public static class VertexAttribute {
        public final String name;
        public final int index;
        public final int size;
        public final int elementSize;
        public final int type;

        private VertexAttribute(String name, int index, int size, int elementSize, int type) {
            this.name = name;
            this.index = index;
            this.size = size;
            this.elementSize = elementSize;
            this.type = type;
        }
    }
}
