package makamys.neodymium.renderer;

import java.util.Comparator;

import net.minecraft.entity.Entity;
import net.minecraft.world.ChunkCoordIntPair;

public class Comparators {
    public static final MeshDistanceComparator MESH_DISTANCE_COMPARATOR = new MeshDistanceComparator();
    
    public static class NeoChunkComparator implements Comparator<NeoChunk> {
        Entity player;
        
        public NeoChunkComparator(Entity player) {
            this.player = player;
        }
        
        @Override
        public int compare(NeoChunk p1, NeoChunk p2) {
            int distSq1 = distSq(p1);
            int distSq2 = distSq(p2);
            return distSq1 < distSq2 ? -1 : distSq1 > distSq2 ? 1 : 0;
        }
        
        int distSq(NeoChunk p) {
            return (int)(
                    Math.pow(((p.x * 16) - player.chunkCoordX), 2) +
                    Math.pow(((p.z * 16) - player.chunkCoordZ), 2)
                    );
        }
    }
    
    public static class ChunkCoordDistanceComparator implements Comparator<ChunkCoordIntPair> {
        double x, y, z;
        
        public ChunkCoordDistanceComparator(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        
        @Override
        public int compare(ChunkCoordIntPair p1, ChunkCoordIntPair p2) {
            int distSq1 = distSq(p1);
            int distSq2 = distSq(p2);
            return distSq1 < distSq2 ? -1 : distSq1 > distSq2 ? 1 : 0;
        }
        
        int distSq(ChunkCoordIntPair p) {
            return (int)(
                    Math.pow(((p.chunkXPos * 16) - x), 2) +
                    Math.pow(((p.chunkZPos * 16) - z), 2)
                    );
        }
    }
    
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
}
