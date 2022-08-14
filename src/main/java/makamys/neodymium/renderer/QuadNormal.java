package makamys.neodymium.renderer;

import org.lwjgl.util.vector.Vector3f;

public enum QuadNormal {
    NONE, POSITIVE_X, NEGATIVE_X, POSITIVE_Y, NEGATIVE_Y, POSITIVE_Z, NEGATIVE_Z;

    public static QuadNormal fromVector(Vector3f normal) {
        if(normal.getX() == 0f) {
            if(normal.getY() == 0f) {
                if(normal.getZ() > 0) {
                    return POSITIVE_Z;
                } else if(normal.getZ() < 0) {
                    return NEGATIVE_Z;
                }
            } else if(normal.getZ() == 0f) {
                if(normal.getY() > 0) {
                    return POSITIVE_Y;
                } else if(normal.getY() < 0) {
                    return NEGATIVE_Y;
                }
            }
        } else if(normal.getY() == 0f) {
            if(normal.getZ() == 0f) {
                if(normal.getX() > 0) {
                    return POSITIVE_X;
                } else if(normal.getX() < 0) {
                    return NEGATIVE_X;
                }
            }
        }
        return NONE;
    }
}
