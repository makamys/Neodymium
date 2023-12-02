package makamys.neodymium.renderer;

import java.util.Comparator;

public class Comparators {
    public static final MeshDistanceComparator MESH_DISTANCE_COMPARATOR = new MeshDistanceComparator();
    public static final RegionDistanceComparator REGION_DISTANCE_COMPARATOR = new RegionDistanceComparator();
    
    public static class MeshDistanceComparator implements Comparator<Mesh> {
        double x, y, z;
        boolean inverted;
        
        public Comparator<? super Mesh> setInverted(boolean inverted) {
            this.inverted = inverted;
            return this;
        }

        public MeshDistanceComparator setOrigin(double x, double y, double z) {
            this.x = x / 16.0;
            this.y = y / 16.0;
            this.z = z / 16.0;
            return this;
        }

        @Override
        public int compare(Mesh a, Mesh b) {
            if(a.pass < b.pass) {
                return -1;
            } else if(a.pass > b.pass) {
                return 1;
            } else {
                double distSqA = a.distSq(x, y, z);
                double distSqB = b.distSq(x, y, z);
                
                int mult = inverted ? -1 : 1;
                
                if(distSqA > distSqB) {
                    return 1 * mult;
                } else if(distSqA < distSqB) {
                    return -1 * mult;
                } else {
                    return 0;
                }
            }
        }
        
    }
    
    public static class RegionDistanceComparator implements Comparator<NeoRegion> {
        double x, y, z;

        public RegionDistanceComparator setOrigin(double x, double y, double z) {
            this.x = x / (NeoRegion.SIZE * 16.0);
            this.y = y / (NeoRegion.SIZE * 16.0);
            this.z = z / (NeoRegion.SIZE * 16.0);
            return this;
        }

        @Override
        public int compare(NeoRegion a, NeoRegion b) {
            double distSqA = a.distSq(x, y, z);
            double distSqB = b.distSq(x, y, z);
            
            if(distSqA > distSqB) {
                return 1;
            } else if(distSqA < distSqB) {
                return -1;
            } else {
                return 0;
            }
        }
    }
}
